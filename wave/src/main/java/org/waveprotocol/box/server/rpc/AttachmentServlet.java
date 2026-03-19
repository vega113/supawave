/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.apache.commons.io.FilenameUtils;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.waveprotocol.box.server.util.HttpSanitizers;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.logging.Level;

/**
 * Serves attachments from a provided store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 *
 */

@SuppressWarnings("serial")
@Singleton
@MultipartConfig
public class AttachmentServlet extends HttpServlet {
  public static String ATTACHMENT_URL = "/attachment";
  public static String THUMBNAIL_URL = "/thumbnail";

  public static String THUMBNAIL_PATTERN_FORMAT_NAME = "png";
  public static String THUMBNAIL_PATTERN_DEFAULT = "default";

  private static final Log LOG = Log.get(AttachmentServlet.class);

  private final AttachmentService service;
  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;
  private final String thumbnailPattternsDirectory;
  private final File thumbnailPatternsDir;
  private final String contentDispositionMode; // sanitized | hashed (default: sanitized)

  @Inject
  private AttachmentServlet(AttachmentService service, WaveletProvider waveletProvider,
      SessionManager sessionManager, Config config) {
    this.service = service;
    this.waveletProvider = waveletProvider;
    this.sessionManager = sessionManager;
    String dir = null;
    File dirFile = null;
    try {
      if (config != null && config.hasPath("core.thumbnail_patterns_directory")) {
        dir = config.getString("core.thumbnail_patterns_directory");
        dirFile = (dir == null || dir.isBlank()) ? null : new File(dir);
        if (dirFile != null && (!dirFile.exists() || !dirFile.isDirectory() || !dirFile.canRead())) {
          LOG.warning("thumbnail patterns directory invalid or unreadable: '" + dir + "' (patterns will use generated fallback)");
          dirFile = null;
        }
      }
    } catch (Exception e) {
      LOG.warning("Failed to read thumbnail patterns directory from config; using generated fallback", e);
      dirFile = null;
    }
    this.thumbnailPattternsDirectory = dir != null ? dir : "";
    this.thumbnailPatternsDir = dirFile;
    String mode = "sanitized";
    try {
      if (config.hasPath("server.attachments.contentDispositionMode")) {
        mode = config.getString("server.attachments.contentDispositionMode");
      }
    } catch (Exception ignore) {
      // default stays sanitized
    }
    this.contentDispositionMode = mode;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // One-orchestrator / single-exit flow: delegate steps to helpers,
    // then finalize the response once.
    int status = HttpServletResponse.SC_OK;
    String errorLog = null;

    AttachmentId attachmentId = resolveAttachmentId(request);
    if (attachmentId == null) {
      status = HttpServletResponse.SC_NOT_FOUND;
      errorLog = "Missing attachmentId";
    }

    AttachmentMetadata metadata = null;
    WaveletName waveletName = null;
    if (status == HttpServletResponse.SC_OK) {
      String waveRefStr = getWaveRefFromRequest(request);
      metadata = service.getMetadata(attachmentId);
      WaveletName resolved = resolveWaveletName(metadata, waveRefStr);
      if (resolved == null) {
        status = HttpServletResponse.SC_NOT_FOUND;
        errorLog = "No metadata and missing waveRef";
      } else {
        waveletName = resolved;
      }
    }

    if (status == HttpServletResponse.SC_OK) {
      if (!isAuthorized(waveletName, sessionManager.getLoggedInUser(request.getSession(false)))) {
        status = HttpServletResponse.SC_FORBIDDEN;
        errorLog = "User not authorized";
      }
    }

    if (status == HttpServletResponse.SC_OK) {
      // Backfill metadata if needed (old attachments)
      metadata = ensureMetadata(metadata, attachmentId, waveletName, getFileNameFromRequest(request));
    }

    AttachmentData data = null;
    String contentType = null;
    if (status == HttpServletResponse.SC_OK) {
      Content content = resolveContentAndType(request.getRequestURI(), metadata, attachmentId);
      if (content.status != HttpServletResponse.SC_OK) {
        status = content.status;
        errorLog = content.errorMessage;
      } else {
        data = content.data;
        contentType = content.contentType;
      }
    }

    finalizeResponse(response, status, errorLog, metadata, data, contentType, attachmentId);
  }

  // ---- Helpers ----

  private AttachmentId resolveAttachmentId(HttpServletRequest request) {
    return getAttachmentIdFromRequest(request);
  }

  private WaveletName resolveWaveletName(AttachmentMetadata metadata, String waveRefStr) {
    if (metadata != null) {
      return AttachmentUtil.waveRef2WaveletName(metadata.getWaveRef());
    }
    if (waveRefStr != null) {
      return AttachmentUtil.waveRef2WaveletName(waveRefStr);
    }
    return null;
  }

  private boolean isAuthorized(WaveletName waveletName, ParticipantId user) {
    try {
      return waveletProvider.checkAccessPermission(waveletName, user);
    } catch (WaveServerException e) {
      LOG.warning("Problem while authorizing user: " + user + " for wavelet: " + waveletName, e);
      return false;
    }
  }

  private AttachmentMetadata ensureMetadata(AttachmentMetadata metadata, AttachmentId id,
                                            WaveletName waveletName, String fileName) {
    if (metadata != null) return metadata;
    try {
      return service.buildAndStoreMetadataWithThumbnail(id, waveletName, fileName, null);
    } catch (IOException e) {
      LOG.warning("Failed to backfill attachment metadata for id=" + id, e);
      return null;
    }
  }

  private static final class Content {
    final int status; final String errorMessage; final AttachmentData data; final String contentType;
    Content(int s, String msg, AttachmentData d, String ct) { status=s; errorMessage=msg; data=d; contentType=ct; }
    static Content ok(AttachmentData d, String ct) { return new Content(HttpServletResponse.SC_OK, null, d, ct); }
  }

  private Content resolveContentAndType(String uri, AttachmentMetadata metadata, AttachmentId id) {
    if (uri.startsWith(ATTACHMENT_URL)) {
      AttachmentData data;
      try {
        data = service.getAttachment(id);
      } catch (IOException e) {
        LOG.warning("Failed to load attachment data id=" + id, e);
        return new Content(HttpServletResponse.SC_NOT_FOUND, "Attachment data not found", null, null);
      }
      return (data != null) ? Content.ok(data, metadata.getMimeType())
          : new Content(HttpServletResponse.SC_NOT_FOUND, "Attachment data not found", null, null);
    } else if (uri.startsWith(THUMBNAIL_URL)) {
      if (metadata.hasImageMetadata()) {
        AttachmentData thumb;
        try {
          thumb = service.getThumbnail(id);
        } catch (IOException e) {
          LOG.warning("Failed to load thumbnail id=" + id, e);
          return new Content(HttpServletResponse.SC_NOT_FOUND, "Thumbnail not found", null, null);
        }
        return (thumb != null) ? Content.ok(thumb, AttachmentService.THUMBNAIL_MIME_TYPE)
            : new Content(HttpServletResponse.SC_NOT_FOUND, "Thumbnail not found", null, null);
      } else {
        AttachmentData patt;
        try {
          patt = getThumbnailByContentType(metadata.getMimeType());
        } catch (IOException e) {
          LOG.warning("Failed to load default thumbnail for mime=" + metadata.getMimeType(), e);
          patt = null;
        }
        return (patt != null) ? Content.ok(patt, THUMBNAIL_PATTERN_FORMAT_NAME)
            : new Content(HttpServletResponse.SC_NOT_FOUND, "No thumbnail pattern", null, null);
      }
    } else {
      return new Content(HttpServletResponse.SC_NOT_FOUND, "Unknown path", null, null);
    }
  }

  private void finalizeResponse(HttpServletResponse response,
                                int status,
                                String errorLog,
                                AttachmentMetadata metadata,
                                AttachmentData data,
                                String contentType,
                                AttachmentId attachmentId) throws IOException {
    if (status != HttpServletResponse.SC_OK) {
      if (errorLog != null) {
        LOG.fine("AttachmentServlet.doGet: " + errorLog + (attachmentId != null ? (" id=" + attachmentId) : ""));
      }
      response.sendError(status);
      return;
    }
    response.setContentType(contentType);
    response.setContentLength((int) data.getSize());
    String cd;
    if ("hashed".equalsIgnoreCase(contentDispositionMode)) {
      cd = HttpSanitizers.buildHashedContentDispositionAttachment(metadata.getFileName());
    } else {
      cd = HttpSanitizers
          .buildContentDispositionAttachment(metadata.getFileName());
    }
    response.setHeader("Content-Disposition", cd);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setDateHeader("Last-Modified", Calendar.getInstance().getTimeInMillis());
    AttachmentUtil.writeTo(data.getInputStream(), response.getOutputStream());
    LOG.info("Fetched attachment with id '" + attachmentId + "'");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
      IOException {
    try {
      if (!isMultipartRequest(request)) {
        LOG.severe("Request contents type is not supported by the servlet.");
        response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
            "Request contents type is not supported by the servlet.");
        return;
      }

      UploadRequest uploadRequest = readUploadRequest(request);
      if (uploadRequest.attachmentId == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No attachment Id in the request.");
        return;
      }
      if (uploadRequest.waveRefStr == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No wave reference in request.");
        return;
      }
      if (uploadRequest.filePart == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file in request.");
        return;
      }

      WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(uploadRequest.waveRefStr);
      ParticipantId user = sessionManager.getLoggedInUser(request.getSession(false));
      if (!isAuthorized(waveletName, user)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      String submittedFileName = uploadRequest.filePart.getSubmittedFileName();
      String fileName = submittedFileName != null ? FilenameUtils.getName(submittedFileName) : "";
      try (InputStream fileStream = uploadRequest.filePart.getInputStream()) {
        service.storeAttachment(uploadRequest.attachmentId, fileStream, waveletName, fileName, user);
      }

      response.setStatus(HttpServletResponse.SC_CREATED);
      LOG.fine(String.format("The file with name: %s and id: %s was created successfully.", fileName,
          uploadRequest.attachmentId));
      response.getWriter().print("OK");
      response.flushBuffer();
    } catch (Exception e) {
      LOG.severe("Upload error", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "An error occurred while upload the file : " + e.getMessage());
    }
  }

  private boolean isMultipartRequest(HttpServletRequest request) {
    String contentType = request.getContentType();
    return contentType != null && contentType.toLowerCase().startsWith("multipart/");
  }

  private UploadRequest readUploadRequest(HttpServletRequest request) throws IOException, ServletException {
    AttachmentId id = null;
    String waveRefStr = null;
    Part filePart = null;
    Collection<Part> parts = request.getParts();
    for (Part part : parts) {
      String submittedFileName = part.getSubmittedFileName();
      if (submittedFileName == null) {
        String value = new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if ("attachmentId".equals(part.getName())) {
          id = AttachmentId.deserialise(value);
        } else if ("waveRef".equals(part.getName())) {
          waveRefStr = value;
        }
      } else {
        filePart = part;
      }
    }
    return new UploadRequest(id, waveRefStr, filePart);
  }

  private static final class UploadRequest {
    final AttachmentId attachmentId;
    final String waveRefStr;
    final Part filePart;

    UploadRequest(AttachmentId attachmentId, String waveRefStr, Part filePart) {
      this.attachmentId = attachmentId;
      this.waveRefStr = waveRefStr;
      this.filePart = filePart;
    }
  }

  private static AttachmentId getAttachmentIdFromRequest(HttpServletRequest request) {
    String id = getAttachmentIdStringFromRequest(request);
    if (id == null) { return null; }
    try {
      return AttachmentId.deserialise(id);
    } catch (InvalidIdException ex) {
      LOG.log(Level.FINE, "Invalid attachment id format", ex);
      return null;
    }
  }

  private static String getAttachmentIdStringFromRequest(HttpServletRequest request) {
    String pathInfo = request.getPathInfo();
    if (pathInfo == null || pathInfo.isEmpty()) { LOG.fine("Rejecting attachment path: reason=emptyPath"); return null; }
    if (pathInfo.charAt(0) != '/') { LOG.fine("Rejecting attachment path: reason=noLeadingSlash path=" + mask(pathInfo)); return null; }
    String raw = pathInfo.substring(1);
    // Reject encoded traversal in the original URI (defense in depth against double-encoding)
    try {
      String uri = String.valueOf(request.getRequestURI()).toLowerCase();
      if (uri.contains("%2f") || uri.contains("%5c") || uri.contains("%2e%2e") || uri.contains("%2e/")) {
        LOG.fine("Rejecting attachment path: reason=encodedTraversal uri=" + mask(uri));
        return null;
      }
    } catch (Throwable ignore) {}
    if (raw.length() == 0) { LOG.fine("Rejecting attachment path: reason=emptyId"); return null; }
    if (raw.length() > 512) { LOG.fine("Rejecting attachment path: reason=tooLong len=" + raw.length()); return null; }
    if (raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0
        || raw.indexOf(':') >= 0 || raw.indexOf('?') >= 0 || raw.indexOf('#') >= 0 || raw.indexOf('%') >= 0) { LOG.fine("Rejecting attachment path: reason=illegalChars path=" + mask(raw)); return null; }
    if (!raw.equals(raw.trim())) { LOG.fine("Rejecting attachment path: reason=surroundingWhitespace path=" + mask(raw)); return null; }
    int firstSlash = raw.indexOf('/');
    if (firstSlash >= 0) {
      if (raw.indexOf('/', firstSlash + 1) >= 0) { LOG.fine("Rejecting attachment path: reason=extraSegments path=" + mask(raw)); return null; }
      String domain = raw.substring(0, firstSlash);
      String rid = raw.substring(firstSlash + 1);
      if (".".equals(domain) || "..".equals(domain) || ".".equals(rid) || "..".equals(rid)) { LOG.fine("Rejecting attachment path: reason=dotSegment path=" + mask(raw)); return null; }
      if (!isLikelyDomain(domain)) { LOG.fine("Rejecting attachment path: reason=invalidDomain domain=" + mask(domain)); return null; }
      if (rid.isEmpty()) { LOG.fine("Rejecting attachment path: reason=emptyResourceId path=" + mask(raw)); return null; }
    } else {
      if (".".equals(raw) || "..".equals(raw)) { LOG.fine("Rejecting attachment path: reason=dotOnly path=" + mask(raw)); return null; }
    }
    return raw;
  }

  private static boolean isLikelyDomain(String domain) {
    if (domain == null || domain.isEmpty() || domain.length() > 253) return false;
    if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) return false;
    String[] labels = domain.split("\\.");
    for (String label : labels) {
      if (label.isEmpty() || label.length() > 63) return false;
      for (int i = 0; i < label.length(); i++) {
        char c = label.charAt(i);
        boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
        if (!ok) return false;
      }
      if (label.charAt(0) == '-' || label.charAt(label.length()-1) == '-') return false;
    }
    return true;
  }

  private static String mask(String s) {
    if (s == null) return "null";
    int len = s.length();
    if (len <= 6) return "***";
    return s.substring(0, 3) + "***" + s.substring(len - 2);
  }

  private AttachmentData getThumbnailByContentType(String contentType) throws IOException {
    try {
      String safe = safeFileNameForContentType(contentType);
      if (thumbnailPatternsDir != null) {
        File file = new File(thumbnailPatternsDir, safe);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
          file = new File(thumbnailPatternsDir, THUMBNAIL_PATTERN_DEFAULT);
        }
        if (file.exists() && file.isFile() && file.canRead()) {
          final File thumbFile = file;
          return new AttachmentData() {
            @Override public InputStream getInputStream() throws IOException { return new FileInputStream(thumbFile); }
            @Override public long getSize() { return thumbFile.length(); }
          };
        } else {
          LOG.fine("Thumbnail pattern file not found; generating fallback image");
        }
      } else {
        LOG.fine("Thumbnail patterns directory not configured/usable; generating fallback image");
      }
    } catch (Throwable t) {
      LOG.fine("Thumbnail selection encountered an error; generating fallback image", t);
    }
    return generatedPatternPng();
  }

  private static String safeFileNameForContentType(String contentType) {
    if (contentType == null) return THUMBNAIL_PATTERN_DEFAULT;
    String s = contentType.trim().toLowerCase();
    if (s.isEmpty()) return THUMBNAIL_PATTERN_DEFAULT;
    StringBuilder sb = new StringBuilder(Math.min(s.length(), 100));
    for (int i = 0; i < s.length() && sb.length() < 100; i++) {
      char c = s.charAt(i);
      boolean safe = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == '_';
      sb.append(safe ? c : '_');
    }
    String out = sb.toString();
    return out.isEmpty() ? THUMBNAIL_PATTERN_DEFAULT : out;
  }

  private static AttachmentData generatedPatternPng() throws IOException {
    int w = org.waveprotocol.box.server.attachment.AttachmentService.THUMBNAIL_PATTERN_WIDTH;
    int h = org.waveprotocol.box.server.attachment.AttachmentService.THUMBNAIL_PATTERN_HEIGHT;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = img.createGraphics();
    try {
      g.setColor(new Color(230,230,230));
      g.fillRect(0,0,w,h);
      g.setColor(new Color(200,200,200));
      for (int y = 0; y < h; y += 6) { g.fillRect(0, y, w, 3); }
    } finally { g.dispose(); }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, THUMBNAIL_PATTERN_FORMAT_NAME, baos);
    byte[] bytes = baos.toByteArray();
    return new AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
      @Override public long getSize() { return bytes.length; }
    };
  }

  private static String getFileNameFromRequest(HttpServletRequest request) {
    String fileName = request.getParameter("fileName");
    return fileName != null ? fileName : "";
  }

  private static String getWaveRefFromRequest(HttpServletRequest request) {
    String waveRefStrEncoded = request.getParameter("waveRef");
    String waveRefStr = null;
    if (waveRefStrEncoded != null) {
      try {
        waveRefStr = URLDecoder.decode(waveRefStrEncoded, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        LOG.warning("Problem decoding: " + waveRefStrEncoded, e);
      }
    }
    return waveRefStr;
  }
}

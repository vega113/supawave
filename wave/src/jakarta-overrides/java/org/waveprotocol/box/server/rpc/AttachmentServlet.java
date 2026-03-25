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
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;

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

  // Public for test/container construction on Jakarta path
  @Inject
  public AttachmentServlet(AttachmentService service, WaveletProvider waveletProvider,
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
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    AttachmentId attachmentId = getAttachmentIdFromRequest(request);
    if (attachmentId == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    String fileName = getFileNameFromRequest(request);
    // Note: waveRef is intentionally ignored for authorization. Authorization must
    // be derived from stored metadata to prevent spoofing access by supplying an
    // arbitrary waveRef for a foreign attachment.

    AttachmentMetadata metadata = service.getMetadata(attachmentId);
    if (metadata == null) {
      // Do not trust user-supplied waveRef for legacy attachments. Without
      // metadata we cannot safely determine ownership — return 404.
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(metadata.getWaveRef());

    WebSession ws = WebSessions.from(request, false);
    ParticipantId user = sessionManager.getLoggedInUser(ws);
    boolean isAuthorized = false;
    try {
      isAuthorized = waveletProvider.checkAccessPermission(waveletName, user);
    } catch (WaveServerException e) {
      LOG.warning("Problem while authorizing user: " + user + " for wavelet: " + waveletName, e);
    }
    if (!isAuthorized) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    // Metadata must exist for authorization; never create it from request parameters.

    String contentType;
    AttachmentData data;
    // Use request URI prefix to determine endpoint (compatible with EE10 mapping)
    String uri = request.getRequestURI();
    if (uri.startsWith(ATTACHMENT_URL)) {
      contentType = metadata.getMimeType();
      data = service.getAttachment(attachmentId);
      if (data == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
    } else if (uri.startsWith(THUMBNAIL_URL)) {
      if (metadata.hasImageMetadata()) {
        contentType = AttachmentService.THUMBNAIL_MIME_TYPE;
        data = service.getThumbnail(attachmentId);
        if (data == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
      } else {
        contentType = THUMBNAIL_PATTERN_FORMAT_NAME;
        data = getThumbnailByContentType(metadata.getMimeType());
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    response.setContentType(contentType);
    response.setContentLength((int) data.getSize());
    response.setHeader("Content-Disposition", "attachment; filename=\"" + metadata.getFileName() + "\"");
    response.setStatus(HttpServletResponse.SC_OK);
    response.setDateHeader("Last-Modified", Calendar.getInstance().getTimeInMillis());
    try (InputStream in = data.getInputStream(); var os = response.getOutputStream()) {
      AttachmentUtil.writeTo(in, os);
      os.flush();
    }
  }

  private boolean isAuthorized(WaveletName waveletName, ParticipantId user) {
    try {
      return waveletProvider.checkAccessPermission(waveletName, user);
    } catch (WaveServerException e) {
      LOG.warning("Problem while authorizing user: " + user + " for wavelet: " + waveletName, e);
      return false;
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      if (!isMultipartRequest(request)) {
        LOG.severe("Request contents type is not supported by the servlet.");
        response.sendError(
            HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
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
      WebSession ws = WebSessions.from(request, false);
      ParticipantId user = sessionManager.getLoggedInUser(ws);
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
      LOG.fine(
          String.format(
              "The file with name: %s and id: %s was created successfully.",
              fileName,
              uploadRequest.attachmentId));
      response.getWriter().print("OK");
      response.flushBuffer();
    } catch (Exception e) {
      LOG.severe("Upload error", e);
      response.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "An error occurred while upload the file : " + e.getMessage());
    }
  }

  private boolean isMultipartRequest(HttpServletRequest request) {
    String contentType = request.getContentType();
    return contentType != null && contentType.toLowerCase().startsWith("multipart/");
  }

  private UploadRequest readUploadRequest(HttpServletRequest request)
      throws IOException, ServletException {
    AttachmentId id = null;
    String waveRefStr = null;
    Part filePart = null;
    Collection<Part> parts = request.getParts();
    for (Part part : parts) {
      String submittedFileName = part.getSubmittedFileName();
      if (submittedFileName == null) {
        String value = new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if ("attachmentId".equals(part.getName())) {
          try {
            id = AttachmentId.deserialise(value);
          } catch (InvalidIdException e) {
            LOG.warning("Problem deserializing attachment id from multipart request", e);
          }
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
    if (id == null) return null;
    try {
      return AttachmentId.deserialise(id);
    } catch (InvalidIdException ex) {
      if (LOG.isFineLoggable()) LOG.log(Level.FINE, "Invalid attachment id format", ex);
      return null;
    }
  }

  private static String getAttachmentIdStringFromRequest(HttpServletRequest request) {
    String pathInfo = request.getPathInfo();
    if (pathInfo == null || pathInfo.isEmpty()) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=emptyPath");
      return null;
    }
    // Expect '/<id>' where <id> is either 'id' or 'domain/id'. Reject anything else.
    if (pathInfo.charAt(0) != '/') {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=noLeadingSlash path=" + mask(pathInfo));
      return null;
    }
    String raw = pathInfo.substring(1);
    // Reject if the original request URI contains encoded separators/traversal
    // We check the raw URI to defeat double-encoding tricks that a container might normalize later.
    try {
      String uri = String.valueOf(request.getRequestURI());
      String u = uri.toLowerCase();
      if (u.contains("%2f") || u.contains("%5c") || u.contains("%2e%2e") || u.contains("%2e/")) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=encodedTraversal uri=" + mask(uri));
        return null;
      }
    } catch (Throwable ignore) {}
    // Hard upper bound to avoid pathological inputs
    if (raw.length() == 0) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=emptyId");
      return null;
    }
    if (raw.length() > 512) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=tooLong len=" + raw.length());
      return null;
    }
    // Disallow dangerous characters and reserved delimiters that should never appear in an id
    if (raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0
        || raw.indexOf(':') >= 0 || raw.indexOf('?') >= 0 || raw.indexOf('#') >= 0 || raw.indexOf('%') >= 0) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=illegalChars path=" + mask(raw));
      return null;
    }
    // Reject leading/trailing whitespace to avoid ambiguous ids
    if (!raw.equals(raw.trim())) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=surroundingWhitespace path=" + mask(raw));
      return null;
    }
    // Allow at most one '/': supports domain/id; reject more segments
    int firstSlash = raw.indexOf('/');
    if (firstSlash >= 0) {
      if (raw.indexOf('/', firstSlash + 1) >= 0) { // more than one segment separator
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=extraSegments path=" + mask(raw));
        return null;
      }
      String domain = raw.substring(0, firstSlash);
      String rid = raw.substring(firstSlash + 1);
      if (".".equals(domain) || "..".equals(domain) || ".".equals(rid) || "..".equals(rid)) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=dotSegment path=" + mask(raw));
        return null;
      }
      // Basic domain sanity: labels of [a-z0-9-], no leading/trailing '-', at least one alnum
      if (!isLikelyDomain(domain)) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=invalidDomain domain=" + mask(domain));
        return null;
      }
      if (rid.isEmpty()) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=emptyResourceId path=" + mask(raw));
        return null;
      }
    } else {
      if (".".equals(raw) || "..".equals(raw)) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=dotOnly path=" + mask(raw));
        return null;
      }
    }
    return raw;
  }

  private static boolean isLikelyDomain(String domain) {
    if (domain == null || domain.isEmpty() || domain.length() > 253) return false;
    // Disallow consecutive dots or starting/ending with '.'
    if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) return false;
    String[] labels = domain.split("\\.");
    for (String label : labels) {
      if (label.isEmpty() || label.length() > 63) return false;
      // label must be alnum or hyphen, not starting/ending with '-'
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
    return generateFileTypeThumbnail(contentType);
  }

  private static String safeFileNameForContentType(String contentType) {
    if (contentType == null) return THUMBNAIL_PATTERN_DEFAULT;
    String s = contentType.trim().toLowerCase();
    if (s.isEmpty()) return THUMBNAIL_PATTERN_DEFAULT;
    // Replace path separators and any non safe char with '_'
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
    return generateFileTypeThumbnail(null);
  }

  /**
   * Generates a colored file-type thumbnail with a label based on the MIME type.
   * This provides visual differentiation for non-image attachments (PDF, video, etc.).
   */
  private static AttachmentData generateFileTypeThumbnail(String contentType) throws IOException {
    int w = org.waveprotocol.box.server.attachment.AttachmentService.THUMBNAIL_PATTERN_WIDTH;
    int h = org.waveprotocol.box.server.attachment.AttachmentService.THUMBNAIL_PATTERN_HEIGHT;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
          java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
          java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      // Determine color and label based on content type
      Color bgColor;
      String label;
      if (contentType == null) {
        bgColor = new Color(117, 117, 117); // gray
        label = "FILE";
      } else {
        String ct = contentType.toLowerCase();
        if (ct.equals("application/pdf")) {
          bgColor = new Color(229, 57, 53); // red
          label = "PDF";
        } else if (ct.startsWith("video/")) {
          bgColor = new Color(123, 31, 162); // purple
          label = "VIDEO";
        } else if (ct.startsWith("audio/")) {
          bgColor = new Color(0, 137, 123); // teal
          label = "AUDIO";
        } else if (ct.startsWith("text/") || ct.equals("application/json")
            || ct.equals("application/xml")) {
          bgColor = new Color(84, 110, 122); // blue-gray
          label = "TEXT";
        } else if (ct.contains("spreadsheet") || ct.contains("excel")) {
          bgColor = new Color(46, 125, 50); // green
          label = "XLS";
        } else if (ct.contains("presentation") || ct.contains("powerpoint")) {
          bgColor = new Color(230, 81, 0); // orange
          label = "PPT";
        } else if (ct.contains("document") || ct.contains("word") || ct.contains("rtf")) {
          bgColor = new Color(21, 101, 192); // blue
          label = "DOC";
        } else if (ct.contains("zip") || ct.contains("tar") || ct.contains("compress")
            || ct.contains("rar") || ct.contains("7z")) {
          bgColor = new Color(121, 85, 72); // brown
          label = "ZIP";
        } else {
          bgColor = new Color(117, 117, 117); // gray
          label = "FILE";
        }
      }

      // Draw rounded rect background
      g.setColor(bgColor);
      g.fillRoundRect(0, 0, w, h, 8, 8);

      // Draw document icon shape (simplified)
      g.setColor(new Color(255, 255, 255, 180));
      int iconW = 24;
      int iconH = 30;
      int iconX = (w - iconW) / 2;
      int iconY = (h - iconH) / 2 - 6;
      g.fillRoundRect(iconX, iconY, iconW, iconH, 3, 3);
      // Folded corner
      g.setColor(bgColor);
      int foldSize = 7;
      int[] xPoints = {iconX + iconW - foldSize, iconX + iconW, iconX + iconW};
      int[] yPoints = {iconY, iconY, iconY + foldSize};
      g.fillPolygon(xPoints, yPoints, 3);
      g.setColor(new Color(255, 255, 255, 120));
      int[] xFold = {iconX + iconW - foldSize, iconX + iconW - foldSize, iconX + iconW};
      int[] yFold = {iconY, iconY + foldSize, iconY + foldSize};
      g.fillPolygon(xFold, yFold, 3);

      // Draw label text
      g.setColor(Color.WHITE);
      g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
      java.awt.FontMetrics fm = g.getFontMetrics();
      int textW = fm.stringWidth(label);
      g.drawString(label, (w - textW) / 2, h - 6);
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
      try { waveRefStr = URLDecoder.decode(waveRefStrEncoded, "UTF-8"); }
      catch (UnsupportedEncodingException e) { LOG.warning("Problem decoding: " + waveRefStrEncoded, e); }
    }
    return waveRefStr;
  }
}

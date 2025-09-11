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

import com.google.inject.Singleton;
import com.typesafe.config.Config;
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
import org.waveprotocol.box.server.authentication.JakartaSessionAdapters;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;

@SuppressWarnings("serial")
@Singleton
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

    ParticipantId user = sessionManager.getLoggedInUser(JakartaSessionAdapters.fromRequest(request, false));
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
    AttachmentUtil.writeTo(data.getInputStream(), response.getOutputStream());
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
    // Hard upper bound to avoid pathological inputs
    if (raw.length() == 0) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=emptyId");
      return null;
    }
    if (raw.length() > 512) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=tooLong len=" + raw.length());
      return null;
    }
    // Disallow dangerous characters
    if (raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
      if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=illegalChars path=" + mask(raw));
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
    } else {
      if (".".equals(raw) || "..".equals(raw)) {
        if (LOG.isFineLoggable()) LOG.fine("Rejecting attachment path: reason=dotOnly path=" + mask(raw));
        return null;
      }
    }
    return raw;
  }

  private static String mask(String s) {
    if (s == null) return "null";
    int len = s.length();
    if (len <= 6) return "***";
    return s.substring(0, 3) + "***" + s.substring(len - 2);
  }

  private AttachmentData getThumbnailByContentType(String contentType) throws IOException {
    if (thumbnailPatternsDir != null) {
      File file = new File(thumbnailPatternsDir, contentType.replaceAll("/", "_"));
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
    return generatedPatternPng();
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
      try { waveRefStr = URLDecoder.decode(waveRefStrEncoded, "UTF-8"); }
      catch (UnsupportedEncodingException e) { LOG.warning("Problem decoding: " + waveRefStrEncoded, e); }
    }
    return waveRefStr;
  }
}

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
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
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
import org.waveprotocol.wave.util.logging.Log;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.Calendar;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.logging.Level;

/**
 * Serves attachments from a provided store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 *
 */

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
    // rely on stored metadata to prevent spoofing by passing a waveRef for a
    // different wave.

    AttachmentMetadata metadata = service.getMetadata(attachmentId);
    if (metadata == null) {
      // Do not synthesize metadata from request parameters; without existing
      // metadata we cannot safely tie the attachment to a wave.
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(metadata.getWaveRef());

    ParticipantId user = sessionManager.getLoggedInUser(request.getSession(false));
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

    // Never build metadata from request context in GET path.

    String contentType;
    AttachmentData data;
    // Enforce strict endpoint match using servlet mapping instead of URI prefix.
    String servletPath = request.getServletPath();
    if (ATTACHMENT_URL.equals(servletPath)) {
      contentType = metadata.getMimeType();
      data = service.getAttachment(attachmentId);
      if (data == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
    } else if (THUMBNAIL_URL.equals(servletPath)) {
      if (metadata.hasImageMetadata()) {
        contentType = AttachmentService.THUMBNAIL_MIME_TYPE;
        data = service.getThumbnail(attachmentId);
        if (data == null) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      } else {
        contentType = THUMBNAIL_PATTERN_FORMAT_NAME;
        data = getThumbnailByContentType(metadata.getMimeType());
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (data == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    response.setContentType(contentType);
    response.setContentLength((int)data.getSize());
    response.setHeader("Content-Disposition", "attachment; filename=\"" + metadata.getFileName() + "\"");
    response.setStatus(HttpServletResponse.SC_OK);
    response.setDateHeader("Last-Modified", Calendar.getInstance().getTimeInMillis());
    AttachmentUtil.writeTo(data.getInputStream(), response.getOutputStream());

    LOG.info("Fetched attachment with id '" + attachmentId + "'");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
      IOException {
    // Process only multipart requests.
    if (ServletFileUpload.isMultipartContent(request)) {
      // Create a factory for disk-based file items.
      FileItemFactory factory = new DiskFileItemFactory();

      // Create a new file upload handler.
      ServletFileUpload upload = new ServletFileUpload(factory);

      // Parse the request.
      try {
        @SuppressWarnings("unchecked")
        List<FileItem> items = upload.parseRequest(request);
        AttachmentId id = null;
        String waveRefStr = null;
        FileItem fileItem = null;
        for (FileItem item : items) {
          // Process only file upload - discard other form item types.
          if (item.isFormField()) {
            if (item.getFieldName().equals("attachmentId")) {
              id = AttachmentId.deserialise(item.getString());
            }
            if (item.getFieldName().equals("waveRef")) {
              waveRefStr = item.getString();
            }
          } else {
            fileItem = item;
          }
        }

        if (id == null) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No attachment Id in the request.");
          return;
        }
        if (waveRefStr == null) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No wave reference in request.");
          return;
        }

        WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(waveRefStr);
        ParticipantId user = sessionManager.getLoggedInUser(request.getSession(false));
        boolean isAuthorized = waveletProvider.checkAccessPermission(waveletName, user);
        if (!isAuthorized) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
          return;
        }

        // Get only the file name not whole path.
        if (fileItem != null && fileItem.getName()  != null) {
          String fileName = FilenameUtils.getName(fileItem.getName());
          service.storeAttachment(id, fileItem.getInputStream(), waveletName, fileName, user);
          response.setStatus(HttpServletResponse.SC_CREATED);
          String msg =
              String.format("The file with name: %s and id: %s was created successfully.",
                  fileName, id);
          LOG.fine(msg);
          response.getWriter().print("OK");
          response.flushBuffer();
        }
      } catch (Exception e) {
        LOG.severe("Upload error", e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "An error occurred while upload the file : " + e.getMessage());
      }
    } else {
      LOG.severe("Request contents type is not supported by the servlet.");
      response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
          "Request contents type is not supported by the servlet.");
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
    if (raw.length() == 0) { LOG.fine("Rejecting attachment path: reason=emptyId"); return null; }
    if (raw.length() > 512) { LOG.fine("Rejecting attachment path: reason=tooLong len=" + raw.length()); return null; }
    if (raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) { LOG.fine("Rejecting attachment path: reason=illegalChars path=" + mask(raw)); return null; }
    int firstSlash = raw.indexOf('/');
    if (firstSlash >= 0) {
      if (raw.indexOf('/', firstSlash + 1) >= 0) { LOG.fine("Rejecting attachment path: reason=extraSegments path=" + mask(raw)); return null; }
      String domain = raw.substring(0, firstSlash);
      String rid = raw.substring(firstSlash + 1);
      if (".".equals(domain) || "..".equals(domain) || ".".equals(rid) || "..".equals(rid)) { LOG.fine("Rejecting attachment path: reason=dotSegment path=" + mask(raw)); return null; }
    } else {
      if (".".equals(raw) || "..".equals(raw)) { LOG.fine("Rejecting attachment path: reason=dotOnly path=" + mask(raw)); return null; }
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
      try {
        waveRefStr = URLDecoder.decode(waveRefStrEncoded, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        LOG.warning("Problem decoding: " + waveRefStrEncoded, e);
      }
    }
    return waveRefStr;
  }
}

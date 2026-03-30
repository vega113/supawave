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
import com.google.inject.name.Named;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Profile servlet for editing the current user's profile and serving
 * profile images.
 *
 * <p>URL patterns:
 * <ul>
 *   <li>{@code GET /profile/edit} - Returns the profile edit page HTML</li>
 *   <li>{@code GET /profile/edit/data} - Returns current user's profile as JSON</li>
 *   <li>{@code POST /profile/edit} - Updates profile fields (JSON body)</li>
 *   <li>{@code POST /profile/edit/image} - Uploads profile image (multipart)</li>
 *   <li>{@code DELETE /profile/edit/image} - Removes custom profile image</li>
 *   <li>{@code GET /profile/image/{attachmentId}} - Serves a profile image</li>
 *   <li>{@code GET /profile/card/{address}} - Returns a user's public profile card as JSON</li>
 * </ul>
 */
@SuppressWarnings("serial")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public final class ProfileServlet extends HttpServlet {
  private static final Log LOG = Log.get(ProfileServlet.class);
  private static final int MAX_BIO_LENGTH = 200;
  private static final int MAX_NAME_LENGTH = 50;
  private static final Set<String> ALLOWED_PROFILE_IMAGE_MIME_TYPES = Set.of(
      "image/png",
      "image/jpeg",
      "image/gif",
      "image/webp");

  private final AccountStore accountStore;
  private final SessionManager sessionManager;
  private final String domain;
  private final ProfilesFetcher profilesFetcher;

  @Inject
  public ProfileServlet(AccountStore accountStore,
                        SessionManager sessionManager,
                        @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                        ProfilesFetcher profilesFetcher) {
    this.accountStore = accountStore;
    this.sessionManager = sessionManager;
    this.domain = domain;
    this.profilesFetcher = profilesFetcher;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null) pathInfo = "";

    if (pathInfo.startsWith("/image/")) {
      handleGetProfileImage(req, resp, pathInfo);
      return;
    }

    if (pathInfo.startsWith("/card/")) {
      handleGetProfileCard(req, resp, pathInfo);
      return;
    }

    // All other paths require authentication
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    if (caller == null) return;

    if ("/edit/data".equals(pathInfo)) {
      handleGetProfileData(resp, caller);
    } else if ("/edit".equals(pathInfo) || "/edit/".equals(pathInfo)) {
      handleGetEditPage(resp, caller);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    if (caller == null) return;

    String pathInfo = req.getPathInfo();
    if (pathInfo == null) pathInfo = "";

    if ("/edit/image".equals(pathInfo)) {
      handleUploadImage(req, resp, caller);
    } else if ("/edit".equals(pathInfo) || "/edit/".equals(pathInfo)) {
      handleUpdateProfile(req, resp, caller);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    if (caller == null) return;

    String pathInfo = req.getPathInfo();
    if ("/edit/image".equals(pathInfo)) {
      handleDeleteImage(resp, caller);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  // =========================================================================
  // GET /profile/edit — Profile edit page HTML
  // =========================================================================

  private void handleGetEditPage(HttpServletResponse resp, HumanAccountData caller)
      throws IOException {
    resp.setContentType("text/html;charset=utf-8");
    resp.setCharacterEncoding("UTF-8");
    String imageUrl = resolveImageUrl(caller);
    resp.getWriter().write(HtmlRenderer.renderProfileEditPage(
        caller.getId().getAddress(), domain, imageUrl, caller));
  }

  // =========================================================================
  // GET /profile/edit/data — Current user profile JSON
  // =========================================================================

  private void handleGetProfileData(HttpServletResponse resp, HumanAccountData caller)
      throws IOException {
    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    writeProfileJson(w, caller, true);
    w.flush();
  }

  // =========================================================================
  // POST /profile/edit — Update profile fields
  // =========================================================================

  private void handleUpdateProfile(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException {
    String body = readBody(req);
    String firstName = extractJsonField(body, "firstName");
    String lastName = extractJsonField(body, "lastName");
    String bio = extractJsonField(body, "bio");
    String showLastSeenStr = extractJsonField(body, "showLastSeen");

    if (firstName != null) {
      if (firstName.length() > MAX_NAME_LENGTH) firstName = firstName.substring(0, MAX_NAME_LENGTH);
      caller.setFirstName(firstName.trim());
    }
    if (lastName != null) {
      if (lastName.length() > MAX_NAME_LENGTH) lastName = lastName.substring(0, MAX_NAME_LENGTH);
      caller.setLastName(lastName.trim());
    }
    if (bio != null) {
      if (bio.length() > MAX_BIO_LENGTH) bio = bio.substring(0, MAX_BIO_LENGTH);
      caller.setBio(bio.trim());
    }
    if (showLastSeenStr != null) {
      caller.setShowLastSeen("true".equalsIgnoreCase(showLastSeenStr));
    }

    try {
      accountStore.putAccount(caller);
      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to update profile for " + caller.getId(), e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save profile");
    }
  }

  // =========================================================================
  // POST /profile/edit/image — Upload profile image
  // =========================================================================

  private void handleUploadImage(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException, ServletException {
    String contentType = req.getContentType();
    if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
      resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      return;
    }

    Part filePart = null;
    for (Part part : req.getParts()) {
      if (part.getSubmittedFileName() != null) {
        filePart = part;
        break;
      }
    }
    if (filePart == null) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "No file uploaded");
      return;
    }

    String mimeType = normalizeMimeType(filePart.getContentType());
    if (!isAllowedProfileImageMimeType(mimeType)) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Only PNG, JPEG, GIF, and WEBP images are allowed");
      return;
    }

    // Generate a unique attachment ID for the profile image
    String attachmentId = "profile_" + caller.getId().getAddress().replace("@", "_at_")
        + "_" + System.currentTimeMillis();

    // Store the image data in-memory in the account as a base64-encoded data URL
    // For simplicity, we store the attachment ID as a reference key
    // The actual image bytes are stored using the profile image store mechanism
    try {
      byte[] imageBytes;
      try (InputStream is = filePart.getInputStream()) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
          baos.write(buf, 0, n);
          if (baos.size() > 5 * 1024 * 1024) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Image too large (max 5MB)");
            return;
          }
        }
        imageBytes = baos.toByteArray();
      }

      // Store as base64 data URL in the profile image attachment ID field
      // This avoids the complexity of the wave attachment system for profile images
      String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
      String dataUrl = "data:" + mimeType + ";base64," + base64;

      caller.setProfileImageAttachmentId(dataUrl);
      accountStore.putAccount(caller);

      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true,\"imageUrl\":" + jsonStr(dataUrl) + "}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to store profile image for " + caller.getId(), e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to store image");
    }
  }

  // =========================================================================
  // DELETE /profile/edit/image — Remove custom profile image
  // =========================================================================

  private void handleDeleteImage(HttpServletResponse resp, HumanAccountData caller)
      throws IOException {
    caller.setProfileImageAttachmentId(null);
    try {
      accountStore.putAccount(caller);
      setJsonUtf8(resp);
      String defaultUrl = profilesFetcher.fetchProfile(caller.getId().getAddress()).getImageUrl();
      resp.getWriter().write("{\"ok\":true,\"imageUrl\":" + jsonStr(defaultUrl) + "}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to remove profile image for " + caller.getId(), e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove image");
    }
  }

  // =========================================================================
  // GET /profile/image/{id} — Serve profile image
  // =========================================================================

  private void handleGetProfileImage(HttpServletRequest req, HttpServletResponse resp,
      String pathInfo) throws IOException {
    // Profile images stored as data URLs are served directly from the account data
    // This endpoint serves as a proxy for profile images
    String address = pathInfo.substring("/image/".length());
    if (address.isEmpty()) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    try {
      ParticipantId pid = ParticipantId.ofUnsafe(address);
      AccountData acct = accountStore.getAccount(pid);
      if (acct == null || !acct.isHuman()) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      HumanAccountData human = acct.asHuman();
      String imageData = human.getProfileImageAttachmentId();
      if (imageData == null || !imageData.startsWith("data:")) {
        // Redirect to Gravatar
        resp.sendRedirect(profilesFetcher.fetchProfile(address).getImageUrl());
        return;
      }

      // Parse data URL: data:image/png;base64,XXXXX
      int commaIdx = imageData.indexOf(',');
      if (commaIdx < 0) {
        resp.sendRedirect(profilesFetcher.fetchProfile(address).getImageUrl());
        return;
      }
      String header = imageData.substring(5, commaIdx);
      String mimeType = normalizeMimeType(header.split(";")[0]);
      if (!isAllowedProfileImageMimeType(mimeType)) {
        resp.sendRedirect(profilesFetcher.fetchProfile(address).getImageUrl());
        return;
      }
      byte[] bytes = java.util.Base64.getDecoder().decode(imageData.substring(commaIdx + 1));

      resp.setContentType(mimeType);
      resp.setHeader("X-Content-Type-Options", "nosniff");
      resp.setContentLength(bytes.length);
      resp.setHeader("Cache-Control", "public, max-age=3600");
      resp.getOutputStream().write(bytes);
    } catch (PersistenceException e) {
      LOG.severe("Failed to serve profile image for " + address, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  // =========================================================================
  // GET /profile/card/{address} — Public profile card JSON
  // =========================================================================

  private void handleGetProfileCard(HttpServletRequest req, HttpServletResponse resp,
      String pathInfo) throws IOException {
    // Require authentication to view profile cards
    WebSession session = WebSessions.from(req, false);
    ParticipantId viewer = sessionManager.getLoggedInUser(session);
    if (viewer == null) {
      sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      return;
    }

    String address = pathInfo.substring("/card/".length());
    if (address.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing address");
      return;
    }

    try {
      ParticipantId pid = ParticipantId.ofUnsafe(address);
      AccountData acct = accountStore.getAccount(pid);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
        return;
      }
      HumanAccountData human = acct.asHuman();
      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      writeProfileJson(w, human, false);
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to fetch profile card for " + address, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private HumanAccountData getAuthenticatedUser(HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      String pathInfo = req.getPathInfo();
      if (pathInfo != null && (pathInfo.contains("/data") || pathInfo.contains("/image"))) {
        sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      } else {
        resp.sendRedirect("/auth/signin?r=/userprofile/edit");
      }
      return null;
    }
    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Account not found");
        return null;
      }
      return acct.asHuman();
    } catch (PersistenceException e) {
      LOG.severe("Failed to look up account for " + user, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return null;
    }
  }

  private String resolveImageUrl(HumanAccountData account) {
    String profileImageId = account.getProfileImageAttachmentId();
    if (profileImageId != null && !profileImageId.isEmpty()) {
      if (profileImageId.startsWith("data:")) {
        String mimeType = extractMimeTypeFromDataUrl(profileImageId);
        if (isAllowedProfileImageMimeType(mimeType)) {
          return profileImageId;
        }
      } else {
        return "/userprofile/image/" + profileImageId;
      }
    }
    // Use the profile fetcher to get the default image URL
    return profilesFetcher.fetchProfile(account.getId().getAddress()).getImageUrl();
  }

  private void writeProfileJson(PrintWriter w, HumanAccountData h, boolean includeSensitive) {
    w.append("{\"address\":").append(jsonStr(h.getId().getAddress()));
    w.append(",\"firstName\":").append(jsonStr(h.getFirstName()));
    w.append(",\"lastName\":").append(jsonStr(h.getLastName()));
    w.append(",\"bio\":").append(jsonStr(h.getBio()));
    w.append(",\"imageUrl\":").append(jsonStr(resolveImageUrl(h)));
    if (includeSensitive) {
      w.append(",\"email\":").append(jsonStr(h.getEmail()));
      w.append(",\"showLastSeen\":").append(String.valueOf(h.isShowLastSeen()));
    }
    // Last seen: show if viewer is the user (includeSensitive) or if user permits it
    if (includeSensitive || h.isShowLastSeen()) {
      w.append(",\"lastSeenTime\":").append(String.valueOf(h.getLastActivityTime()));
    }
    // Registration time is always public
    w.append(",\"registrationTime\":").append(String.valueOf(h.getRegistrationTime()));
    w.append('}');
  }

  private static String extractMimeTypeFromDataUrl(String dataUrl) {
    if (dataUrl == null || !dataUrl.startsWith("data:")) return null;
    int commaIdx = dataUrl.indexOf(',');
    if (commaIdx <= 5) return null;
    String header = dataUrl.substring(5, commaIdx);
    return normalizeMimeType(header.split(";")[0]);
  }

  private static boolean isAllowedProfileImageMimeType(String mimeType) {
    return mimeType != null && ALLOWED_PROFILE_IMAGE_MIME_TYPES.contains(mimeType);
  }

  private static String normalizeMimeType(String mimeType) {
    if (mimeType == null) return null;
    String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
    int separator = normalized.indexOf(';');
    if (separator >= 0) {
      normalized = normalized.substring(0, separator).trim();
    }
    return normalized;
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(256);
    char[] buf = new char[512];
    int n;
    BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 8192) break;
    }
    return sb.toString();
  }

  private static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    // Skip whitespace after colon
    int pos = colon + 1;
    while (pos < json.length() && json.charAt(pos) == ' ') pos++;
    if (pos >= json.length()) return null;
    char next = json.charAt(pos);
    if (next == '"') {
      // String value
      int qEnd = json.indexOf('"', pos + 1);
      if (qEnd < 0) return null;
      return json.substring(pos + 1, qEnd);
    } else if (next == 't' || next == 'f') {
      // Boolean value
      if (json.startsWith("true", pos)) return "true";
      if (json.startsWith("false", pos)) return "false";
    } else if (next == 'n') {
      return null; // null value
    }
    return null;
  }

  private static void setJsonUtf8(HttpServletResponse resp) {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
  }

  private static void sendJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    setJsonUtf8(resp);
    resp.getWriter().write("{\"error\":" + jsonStr(message) + "}");
  }

  private static String jsonStr(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
        default:   sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }
}

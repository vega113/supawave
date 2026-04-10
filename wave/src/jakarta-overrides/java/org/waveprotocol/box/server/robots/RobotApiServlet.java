/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package org.waveprotocol.box.server.robots;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.robot.CapabilityFetchException;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtInsufficientScopeException;
import org.waveprotocol.box.server.authentication.jwt.JwtRequestAuthenticator;
import org.waveprotocol.box.server.authentication.jwt.JwtScopes;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;
import com.google.inject.name.Named;
import org.waveprotocol.box.server.CoreSettingsNames;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * REST API for robot management.
 *
 * <p>All endpoints require JWT Bearer authentication via the {@code Authorization} header.
 * Tokens are issued by {@code /robot/dataapi/token} (session-based or client_credentials).
 *
 * <pre>
 * GET    /api/robots              — list owned robots
 * POST   /api/robots              — register new robot
 * GET    /api/robots/{id}         — get robot details
 * PUT    /api/robots/{id}/url     — update callback URL
 * PUT    /api/robots/{id}/description — update description
 * POST   /api/robots/{id}/rotate  — rotate consumer secret
 * POST   /api/robots/{id}/verify  — test bot (fetch capabilities)
 * POST   /api/robots/{id}/refresh — clear cached capabilities (re-fetched on next event)
 * PUT    /api/robots/{id}/paused  — pause/unpause
 * DELETE /api/robots/{id}         — soft delete
 * </pre>
 */
@SuppressWarnings("serial")
@Singleton
public final class RobotApiServlet extends HttpServlet {
  private static final Log LOG = Log.get(RobotApiServlet.class);
  private static final int MAX_BODY_SIZE = 16 * 1024;
  private static final String JSON = "application/json";

  private final String domain;
  private final JwtRequestAuthenticator jwtAuthenticator;
  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final RobotCapabilityFetcher capabilityFetcher;
  private final String activeRobotApiUrl;

  @Inject
  public RobotApiServlet(
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      JwtRequestAuthenticator jwtAuthenticator,
      AccountStore accountStore,
      RobotRegistrar robotRegistrar,
      RobotCapabilityFetcher capabilityFetcher,
      Config config) {
    this.domain = domain;
    this.jwtAuthenticator = jwtAuthenticator;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.capabilityFetcher = capabilityFetcher;
    this.activeRobotApiUrl = PublicBaseUrlResolver.resolve(config) + RobotsGateway.DATA_API_RPC_PATH;
  }

  // ── Authentication ──────────────────────────────────────────────────

  private ParticipantId authenticate(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    return authenticate(req, resp, Set.of());
  }

  private ParticipantId authenticate(HttpServletRequest req, HttpServletResponse resp,
      Set<String> requiredScopes) throws IOException {
    try {
      return jwtAuthenticator.authenticate(
          req.getHeader("Authorization"), JwtTokenType.DATA_API_ACCESS, JwtAudience.DATA_API,
          requiredScopes);
    } catch (JwtInsufficientScopeException e) {
      LOG.warning("Insufficient scope for requested operation", e);
      sendError(resp, 403, "Insufficient permissions for this operation", "AUTH_INSUFFICIENT_SCOPES");
      return null;
    } catch (JwtValidationException e) {
      LOG.warning("JWT authentication failed", e);
      sendError(resp, 401, "Invalid authentication token", "AUTH_INVALID");
      return null;
    }
  }

  // ── Route dispatch ──────────────────────────────────────────────────

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = authenticate(req, resp, Set.of(JwtScopes.DATA_READ));
    if (user == null) return;

    String sub = subPath(req);
    if (sub.isEmpty()) {
      handleListRobots(resp, user);
    } else if (!sub.contains("/")) {
      handleGetRobot(resp, user, sub);
    } else {
      sendError(resp, 404, "Not found", "NOT_FOUND");
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = authenticate(req, resp, Set.of(JwtScopes.DATA_WRITE));
    if (user == null) return;

    String sub = subPath(req);
    if (sub.isEmpty()) {
      handleRegister(req, resp, user);
    } else {
      int slash = sub.indexOf('/');
      if (slash < 0) {
        sendError(resp, 404, "Not found", "NOT_FOUND");
        return;
      }
      String robotId = sub.substring(0, slash);
      String action = sub.substring(slash + 1);
      if ("rotate".equals(action)) {
        handleRotateSecret(resp, user, robotId);
      } else if ("verify".equals(action)) {
        handleVerify(resp, user, robotId);
      } else if ("refresh".equals(action)) {
        handleRefreshCapabilities(resp, user, robotId);
      } else {
        sendError(resp, 404, "Not found", "NOT_FOUND");
      }
    }
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = authenticate(req, resp, Set.of(JwtScopes.DATA_WRITE));
    if (user == null) return;

    String sub = subPath(req);
    int slash = sub.indexOf('/');
    if (slash < 0) {
      sendError(resp, 404, "Not found", "NOT_FOUND");
      return;
    }
    String robotId = sub.substring(0, slash);
    String field = sub.substring(slash + 1);
    switch (field) {
      case "url" -> handleUpdateUrl(req, resp, user, robotId);
      case "description" -> handleUpdateDescription(req, resp, user, robotId);
      case "paused" -> handleSetPaused(req, resp, user, robotId);
      default -> sendError(resp, 404, "Not found", "NOT_FOUND");
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = authenticate(req, resp, Set.of(JwtScopes.DATA_WRITE));
    if (user == null) return;

    String sub = subPath(req);
    if (sub.isEmpty() || sub.contains("/")) {
      sendError(resp, 404, "Not found", "NOT_FOUND");
      return;
    }
    handleDelete(resp, user, sub);
  }

  // ── Handlers ────────────────────────────────────────────────────────

  private void handleListRobots(HttpServletResponse resp, ParticipantId user) throws IOException {
    List<RobotAccountData> robots;
    try {
      robots = accountStore.getRobotAccountsOwnedBy(user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Failed to load robots", "INTERNAL_ERROR");
      return;
    }
    if (robots == null) {
      robots = List.of();
    }
    StringBuilder json = new StringBuilder(1024);
    json.append("[");
    for (int i = 0; i < robots.size(); i++) {
      if (i > 0) json.append(",");
      json.append(robotToJson(robots.get(i), true));
    }
    json.append("]");
    sendJson(resp, 200, json.toString());
  }

  private void handleGetRobot(HttpServletResponse resp, ParticipantId user, String robotId)
      throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotId, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    sendJson(resp, 200, robotToJson(robot, true));
  }

  private void handleRegister(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String body = readBody(req, resp);
    if (body == null) return;

    JsonObject obj;
    try {
      obj = JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      sendError(resp, 400, "Invalid JSON body", "VALIDATION_ERROR");
      return;
    }

    String username = jsonString(body, "username");
    String description = jsonString(body, "description");
    long tokenExpiry = Math.max(0L, jsonLong(body, "tokenExpiry", 3600L));

    // callbackUrl is optional but must be a string when provided
    String callbackUrl = "";
    if (obj.has("callbackUrl") && !obj.get("callbackUrl").isJsonNull()) {
      JsonElement urlEl = obj.get("callbackUrl");
      if (!urlEl.isJsonPrimitive() || !urlEl.getAsJsonPrimitive().isString()) {
        sendError(resp, 400, "callbackUrl must be a string", "VALIDATION_ERROR");
        return;
      }
      callbackUrl = urlEl.getAsString();
    }

    if (Strings.isNullOrEmpty(username)) {
      sendError(resp, 400, "username is required", "VALIDATION_ERROR");
      return;
    }

    ParticipantId robotId;
    try {
      robotId = RegistrationSupport.checkNewRobotUsername(domain, username);
    } catch (InvalidParticipantAddress e) {
      sendError(resp, 400, e.getMessage(), "VALIDATION_ERROR");
      return;
    }

    try {
      String location = callbackUrl.trim();
      RobotAccountData registered =
          robotRegistrar.registerNew(robotId, location, user.getAddress(), tokenExpiry);
      if (!Strings.isNullOrEmpty(description)) {
        try {
          registered = robotRegistrar.updateDescription(robotId, description.trim());
        } catch (RobotRegistrationException | PersistenceException e) {
          LOG.warning("Robot registered but description update failed: " + e.getMessage());
        }
      }
      // Return the secret in the response (only time it's visible)
      StringBuilder json = new StringBuilder(256);
      json.append("{");
      json.append("\"id\":").append(jv(registered.getId().getAddress()));
      json.append(",\"secret\":").append(jv(registered.getConsumerSecret()));
      json.append(",\"status\":\"active\"");
      json.append(",\"callbackUrl\":").append(jv(Strings.nullToEmpty(registered.getUrl())));
      json.append(",\"description\":").append(jv(Strings.nullToEmpty(registered.getDescription())));
      json.append(",\"tokenExpirySeconds\":").append(registered.getTokenExpirySeconds());
      json.append(",\"createdAt\":").append(jv(formatTimestamp(registered.getCreatedAtMillis())));
      json.append(",\"lastActiveAt\":")
          .append(jv(formatTimestamp(registered.getLastActiveAtMillis())));
      json.append("}");
      sendJson(resp, 201, json.toString());
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "REGISTRATION_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Robot registration failed", "INTERNAL_ERROR");
    }
  }

  private void handleUpdateUrl(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user, String robotIdStr) throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    String body = readBody(req, resp);
    if (body == null) return;
    JsonObject obj;
    try {
      obj = JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      sendError(resp, 400, "Invalid JSON body", "VALIDATION_ERROR");
      return;
    }
    if (!obj.has("url") || obj.get("url").isJsonNull()) {
      sendError(resp, 400, "url is required", "VALIDATION_ERROR");
      return;
    }
    JsonElement urlEl = obj.get("url");
    if (!urlEl.isJsonPrimitive() || !urlEl.getAsJsonPrimitive().isString()) {
      sendError(resp, 400, "url must be a string", "VALIDATION_ERROR");
      return;
    }
    String url = urlEl.getAsString().trim();
    if (url.isEmpty()) {
      sendError(resp, 400, "url is required", "VALIDATION_ERROR");
      return;
    }
    try {
      RobotAccountData updated = robotRegistrar.updateUrl(robot.getId(), url);
      if (updated == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, robotToJson(updated, true));
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "UPDATE_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "URL update failed", "INTERNAL_ERROR");
    }
  }

  private void handleUpdateDescription(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user, String robotIdStr) throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    String body = readBody(req, resp);
    if (body == null) return;
    JsonObject obj;
    try {
      obj = JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      sendError(resp, 400, "Invalid JSON body", "VALIDATION_ERROR");
      return;
    }
    if (!obj.has("description") || obj.get("description").isJsonNull()) {
      sendError(resp, 400, "description field is required", "VALIDATION_ERROR");
      return;
    }
    JsonElement descEl = obj.get("description");
    if (!descEl.isJsonPrimitive() || !descEl.getAsJsonPrimitive().isString()) {
      sendError(resp, 400, "description must be a string", "VALIDATION_ERROR");
      return;
    }
    String description = descEl.getAsString().trim();
    try {
      RobotAccountData updated = robotRegistrar.updateDescription(robot.getId(), description);
      if (updated == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, robotToJson(updated, true));
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "UPDATE_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Description update failed", "INTERNAL_ERROR");
    }
  }

  private void handleRotateSecret(HttpServletResponse resp, ParticipantId user, String robotIdStr)
      throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    try {
      RobotAccountData rotated = robotRegistrar.rotateSecret(robot.getId());
      if (rotated == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      // Include the new secret in response (only time it's visible)
      StringBuilder json = new StringBuilder(256);
      json.append("{");
      appendRobotFields(json, rotated, true);
      json.append(",\"secret\":").append(jv(rotated.getConsumerSecret()));
      json.append("}");
      sendJson(resp, 200, json.toString());
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "ROTATE_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Secret rotation failed", "INTERNAL_ERROR");
    }
  }

  private void handleVerify(HttpServletResponse resp, ParticipantId user, String robotIdStr)
      throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    if (Strings.isNullOrEmpty(robot.getUrl())) {
      sendError(resp, 400, "Set a callback URL before testing", "VALIDATION_ERROR");
      return;
    }
    try {
      // Fetch capabilities via network call, then atomically apply to a fresh store read
      // to avoid overwriting concurrent changes (secret rotation, description edits, etc.)
      RobotAccountData refreshed = capabilityFetcher.fetchCapabilities(robot, activeRobotApiUrl);
      RobotAccountData verified = robotRegistrar.markVerified(robot.getId(), refreshed.getCapabilities());
      if (verified == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, robotToJson(verified, true));
    } catch (CapabilityFetchException e) {
      sendError(resp, 502, "Verification failed: " + e.getMessage(), "VERIFY_ERROR");
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "VERIFY_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Verification failed", "INTERNAL_ERROR");
    }
  }

  private void handleRefreshCapabilities(HttpServletResponse resp, ParticipantId user,
      String robotIdStr) throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    try {
      RobotAccountData updated = robotRegistrar.refreshCapabilities(robot.getId());
      if (updated == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, robotToJson(updated, true));
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "REFRESH_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Capability refresh failed", "INTERNAL_ERROR");
    }
  }

  private void handleSetPaused(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user, String robotIdStr) throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    String body = readBody(req, resp);
    if (body == null) return;
    String pausedStr = jsonString(body, "paused");
    if (!"true".equalsIgnoreCase(pausedStr) && !"false".equalsIgnoreCase(pausedStr)) {
      sendError(resp, 400, "paused must be true or false", "VALIDATION_ERROR");
      return;
    }
    boolean paused = "true".equalsIgnoreCase(pausedStr);
    try {
      RobotAccountData updated = robotRegistrar.setPaused(robot.getId(), paused);
      if (updated == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, robotToJson(updated, true));
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "UPDATE_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Pause update failed", "INTERNAL_ERROR");
    }
  }

  private void handleDelete(HttpServletResponse resp, ParticipantId user, String robotIdStr)
      throws IOException {
    RobotAccountData robot;
    try {
      robot = findOwnedRobot(robotIdStr, user.getAddress());
    } catch (PersistenceException e) {
      sendError(resp, 500, "Lookup failed", "INTERNAL_ERROR");
      return;
    }
    if (robot == null) {
      sendError(resp, 404, "Robot not found or not owned by you", "NOT_FOUND");
      return;
    }
    try {
      // Soft delete: atomically pause and clear callback URL in one store write
      RobotAccountData result = robotRegistrar.softDelete(robot.getId());
      if (result == null) {
        sendError(resp, 404, "Robot not found", "NOT_FOUND");
        return;
      }
      sendJson(resp, 200, "{\"deleted\":true,\"paused\":true,\"id\":" + jv(robot.getId().getAddress()) + "}");
    } catch (RobotRegistrationException e) {
      sendError(resp, 400, e.getMessage(), "DELETE_ERROR");
    } catch (PersistenceException e) {
      sendError(resp, 500, "Deletion failed", "INTERNAL_ERROR");
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────

  /**
   * Looks up a robot by ID and verifies ownership.
   *
   * @return the robot account, or {@code null} if not found or not owned by {@code ownerAddress}
   * @throws PersistenceException if the backing store fails — callers should return 500
   */
  private RobotAccountData findOwnedRobot(String robotIdStr, String ownerAddress)
      throws PersistenceException {
    try {
      // robotIdStr might be just "botname" or "botname@domain"
      ParticipantId robotId;
      if (robotIdStr.contains("@")) {
        robotId = ParticipantId.of(robotIdStr);
      } else {
        robotId = ParticipantId.of(robotIdStr + "@" + domain);
      }
      AccountData account = accountStore.getAccount(robotId);
      if (account != null && account.isRobot()) {
        RobotAccountData robot = account.asRobot();
        if (Objects.equals(ownerAddress, robot.getOwnerAddress())) {
          return robot;
        }
      }
      return null;
    } catch (InvalidParticipantAddress e) {
      LOG.warning("Invalid robot ID format: " + robotIdStr);
      return null;
    }
  }

  private String subPath(HttpServletRequest req) {
    String path = req.getPathInfo();
    if (path == null || path.equals("/")) return "";
    if (path.startsWith("/")) path = path.substring(1);
    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
    return path;
  }

  private String readBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int contentLength = req.getContentLength();
    if (contentLength > MAX_BODY_SIZE) {
      sendError(resp, 413, "Request body too large", "BODY_TOO_LARGE");
      return null;
    }
    byte[] bytes = req.getInputStream().readNBytes(MAX_BODY_SIZE + 1);
    if (bytes.length > MAX_BODY_SIZE) {
      sendError(resp, 413, "Request body too large", "BODY_TOO_LARGE");
      return null;
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private String robotToJson(RobotAccountData robot, boolean detailed) {
    StringBuilder json = new StringBuilder(256);
    json.append("{");
    appendRobotFields(json, robot, detailed);
    json.append("}");
    return json.toString();
  }

  private void appendRobotFields(StringBuilder json, RobotAccountData robot, boolean detailed) {
    json.append("\"id\":").append(jv(robot.getId().getAddress()));
    json.append(",\"status\":").append(jv(robot.isPaused() ? "paused" : "active"));
    json.append(",\"description\":").append(jv(Strings.nullToEmpty(robot.getDescription())));
    json.append(",\"callbackUrl\":").append(jv(Strings.nullToEmpty(robot.getUrl())));
    json.append(",\"verified\":").append(robot.isVerified());
    json.append(",\"createdAt\":").append(jv(formatTimestamp(robot.getCreatedAtMillis())));
    json.append(",\"updatedAt\":").append(jv(formatTimestamp(robot.getUpdatedAtMillis())));
    json.append(",\"lastActiveAt\":").append(jv(formatTimestamp(robot.getLastActiveAtMillis())));
    if (detailed) {
      json.append(",\"tokenExpirySeconds\":").append(robot.getTokenExpirySeconds());
      json.append(",\"maskedSecret\":").append(jv(maskSecret(robot.getConsumerSecret())));
    }
  }

  private void sendJson(HttpServletResponse resp, int status, String json) throws IOException {
    resp.setStatus(status);
    resp.setContentType(JSON);
    resp.setCharacterEncoding("UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (PrintWriter w = resp.getWriter()) {
      w.write(json);
    }
  }

  private void sendError(HttpServletResponse resp, int status, String message, String code)
      throws IOException {
    resp.setStatus(status);
    resp.setContentType(JSON);
    resp.setCharacterEncoding("UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (PrintWriter w = resp.getWriter()) {
      w.write("{\"error\":" + jv(message) + ",\"code\":" + jv(code) + "}");
    }
  }

  private static String jv(String value) {
    if (value == null) return "null";
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static String jsonString(String json, String key) {
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      JsonElement el = obj.get(key);
      return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static long jsonLong(String json, String key, long defaultVal) {
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      JsonElement el = obj.get(key);
      return (el != null && !el.isJsonNull()) ? el.getAsLong() : defaultVal;
    } catch (Exception e) {
      return defaultVal;
    }
  }

  private String maskSecret(String secret) {
    if (Strings.isNullOrEmpty(secret)) return "";
    if (secret.length() <= 4) return secret.substring(0, 1) + "\u2026" + secret.substring(secret.length() - 1);
    if (secret.length() <= 8) return secret.substring(0, 2) + "\u2026" + secret.substring(secret.length() - 2);
    return secret.substring(0, 4) + "\u2026" + secret.substring(secret.length() - 4);
  }

  private String formatTimestamp(long millis) {
    if (millis <= 0L) return "";
    return Instant.ofEpochMilli(millis).toString();
  }
}

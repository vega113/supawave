/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Token endpoint that issues a DATA_API_ACCESS JWT.
 *
 * <p>Supports two authentication paths:
 * <ul>
 *   <li><b>Session-based</b> (default): the user is logged in via browser session.</li>
 *   <li><b>client_credentials</b>: robots authenticate directly with their
 *       consumer secret via {@code grant_type=client_credentials},
 *       {@code client_id} (robot address), and {@code client_secret}.</li>
 * </ul>
 *
 * <p>Returns JSON:
 * <pre>{"access_token": "...", "token_type": "bearer", "expires_in": 3600}</pre>
 */
@SuppressWarnings("serial")
@Singleton
public final class DataApiTokenServlet extends HttpServlet {
  private static final Log LOG = Log.get(DataApiTokenServlet.class);
  private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L;
  /** 100 years in seconds, used as "no expiry" for tokens with tokenExpirySeconds == 0. */
  private static final long NO_EXPIRY_LIFETIME_SECONDS = 100L * 365 * 24 * 3600;
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

  private final SessionManager sessionManager;
  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;
  private final AccountStore accountStore;

  @Inject
  public DataApiTokenServlet(SessionManager sessionManager,
                             JwtKeyRing keyRing,
                             Clock clock,
                             @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer,
                             AccountStore accountStore) {
    this.sessionManager = sessionManager;
    this.keyRing = keyRing;
    this.clock = clock;
    this.issuer = issuer;
    this.accountStore = accountStore;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String grantType = req.getParameter("grant_type");

    if (GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
      handleClientCredentials(req, resp);
    } else {
      handleSessionBased(req, resp);
    }
  }

  /** Path A: User is logged in via browser session -- issue token for that user. */
  private void handleSessionBased(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "not_authenticated");
      return;
    }

    long issuedAt = clock.instant().getEpochSecond();
    long expiresAt = issuedAt + DEFAULT_TOKEN_LIFETIME_SECONDS;

    String token = issueToken(user.getAddress(), issuedAt, expiresAt);
    sendTokenResponse(resp, token, DEFAULT_TOKEN_LIFETIME_SECONDS);
  }

  /** Path B: Robot authenticates with client_id and client_secret. */
  private void handleClientCredentials(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String clientId = req.getParameter("client_id");
    String clientSecret = req.getParameter("client_secret");

    if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_request",
          "client_id and client_secret are required");
      return;
    }

    ParticipantId robotId;
    try {
      robotId = ParticipantId.of(clientId);
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_client",
          "Invalid client_id format");
      return;
    }

    AccountData account;
    try {
      account = accountStore.getAccount(robotId);
    } catch (PersistenceException e) {
      LOG.severe("Failed to look up account for " + clientId, e);
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server_error",
          "Failed to look up account");
      return;
    }

    if (account == null || !account.isRobot()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Unknown robot account");
      return;
    }

    RobotAccountData robotAccount = account.asRobot();

    if (!clientSecret.equals(robotAccount.getConsumerSecret())) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Invalid client_secret");
      return;
    }

    long tokenExpirySeconds = robotAccount.getTokenExpirySeconds();
    long lifetimeSeconds = tokenExpirySeconds > 0 ? tokenExpirySeconds : NO_EXPIRY_LIFETIME_SECONDS;

    long issuedAt = clock.instant().getEpochSecond();
    long expiresAt = issuedAt + lifetimeSeconds;

    String token = issueToken(robotAccount.getId().getAddress(), issuedAt, expiresAt);
    sendTokenResponse(resp, token, lifetimeSeconds);
  }

  private String issueToken(String subject, long issuedAt, long expiresAt) {
    JwtClaims claims = new JwtClaims(
        JwtTokenType.DATA_API_ACCESS,
        issuer,
        subject,
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        EnumSet.of(JwtAudience.DATA_API),
        Set.of(),
        issuedAt,
        issuedAt,
        expiresAt,
        0L);

    return keyRing.issuer().issue(claims);
  }

  private void sendTokenResponse(HttpServletResponse resp, String token, long expiresIn) throws IOException {
    resp.setContentType(JSON_CONTENT_TYPE);
    resp.setStatus(HttpServletResponse.SC_OK);
    try (PrintWriter writer = resp.getWriter()) {
      writer.write("{\"access_token\": \"" + token + "\", "
          + "\"token_type\": \"bearer\", "
          + "\"expires_in\": " + expiresIn + "}");
    }
  }

  private void sendError(HttpServletResponse resp, int status, String error) throws IOException {
    sendError(resp, status, error, null);
  }

  private void sendError(HttpServletResponse resp, int status, String error, String description)
      throws IOException {
    resp.setStatus(status);
    resp.setContentType(JSON_CONTENT_TYPE);
    try (PrintWriter writer = resp.getWriter()) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"error\": \"").append(error).append("\"");
      if (description != null) {
        sb.append(", \"error_description\": \"").append(description).append("\"");
      }
      sb.append("}");
      writer.write(sb.toString());
    }
  }
}

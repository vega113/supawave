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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.OAuthValidator;
import org.waveprotocol.box.server.robots.util.JakartaHttpRequestMessage;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.util.CharBase64;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** Jakarta servlet responsible for the Data API OAuth flow. */
@SuppressWarnings("serial")
@Singleton
public final class DataApiOAuthServlet extends HttpServlet {
  public static final String DATA_API_OAUTH_PATH = "/robot/dataapi/oauth";
  private static final Log LOG = Log.get(DataApiOAuthServlet.class);
  private static final String ANONYMOUS_TOKEN = "anonymous";
  private static final String ANONYMOUS_TOKEN_SECRET = "anonymous";
  private static final String HTML_CONTENT_TYPE = "text/html";
  private static final String PLAIN_CONTENT_TYPE = "text/plain";
  private static final int TOKEN_LENGTH = 8;
  private static final int XSRF_TOKEN_TIMEOUT_HOURS = 12;

  private final String requestTokenPath;
  private final String authorizeTokenPath;
  private final String accessTokenPath;
  private final String allTokensPath;
  private final OAuthServiceProvider serviceProvider;
  private final OAuthValidator validator;
  private final DataApiTokenContainer tokenContainer;
  private final SessionManager sessionManager;
  private final TokenGenerator tokenGenerator;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public DataApiOAuthServlet(@Named("request_token_path") String requestTokenPath,
                             @Named("authorize_token_path") String authorizeTokenPath,
                             @Named("access_token_path") String accessTokenPath,
                             @Named("all_tokens_path") String allTokensPath,
                             OAuthServiceProvider serviceProvider,
                             OAuthValidator validator,
                             DataApiTokenContainer tokenContainer,
                             SessionManager sessionManager,
                             TokenGenerator tokenGenerator) {
    this.requestTokenPath = requestTokenPath;
    this.authorizeTokenPath = authorizeTokenPath;
    this.accessTokenPath = accessTokenPath;
    this.allTokensPath = allTokensPath;
    this.serviceProvider = serviceProvider;
    this.validator = validator;
    this.tokenContainer = tokenContainer;
    this.sessionManager = sessionManager;
    this.tokenGenerator = tokenGenerator;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    routeRequest(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    routeRequest(req, resp);
  }

  private void routeRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    if (pathInfo.equals(requestTokenPath)) {
      doRequestToken(req, resp);
    } else if (pathInfo.equals(authorizeTokenPath)) {
      doAuthorizeToken(req, resp);
    } else if (pathInfo.equals(accessTokenPath)) {
      doExchangeToken(req, resp);
    } else if (pathInfo.equals(allTokensPath)) {
      doAllTokens(req, resp);
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private void doRequestToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthMessage message = new JakartaHttpRequestMessage(req, req.getRequestURL().toString());
    OAuthConsumer consumer =
        new OAuthConsumer("", ANONYMOUS_TOKEN, ANONYMOUS_TOKEN_SECRET, serviceProvider);
    OAuthAccessor accessor = new OAuthAccessor(consumer);
    try {
      validator.validateMessage(message, accessor);
    } catch (OAuthException | URISyntaxException e) {
      LOG.info("Invalid OAuth message", e);
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    accessor = tokenContainer.generateRequestToken(consumer);

    resp.setContentType(OAuth.FORM_ENCODED);
    try (ServletOutputStream out = resp.getOutputStream()) {
      OAuth.formEncode(OAuth.newList(
          OAuth.OAUTH_TOKEN, accessor.requestToken,
          OAuth.OAUTH_TOKEN_SECRET, accessor.tokenSecret,
          OAuth.OAUTH_CALLBACK_CONFIRMED, "true"), out);
    }
    resp.setStatus(HttpServletResponse.SC_OK);
  }

  private void doAuthorizeToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthMessage message = new JakartaHttpRequestMessage(req, req.getRequestURL().toString());
    try {
      message.requireParameters(OAuth.OAUTH_CALLBACK, OAuth.OAUTH_TOKEN);
    } catch (OAuthProblemException e) {
      LOG.info("Parameter absent", e);
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      return;
    }

    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendRedirect(sessionManager.getLoginUrl(
          DATA_API_OAUTH_PATH + authorizeTokenPath + "?" + req.getQueryString()));
      return;
    }

    try {
      tokenContainer.getRequestTokenAccessor(message.getToken());
    } catch (OAuthProblemException e) {
      LOG.info("Trying to load a non existing token for authorization", e);
      resp.sendError(e.getHttpStatusCode(), e.getMessage());
      return;
    }

    if (req.getMethod().equals("GET")) {
      doAuthorizeTokenGet(req, resp, user);
    } else if (req.getMethod().equals("POST")) {
      doAuthorizeTokenPost(req, resp, user, message);
    } else {
      throw new IllegalStateException("Unexpected HTTP method for authorization: " + req.getMethod());
    }
  }

  private void doAuthorizeTokenGet(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    Preconditions.checkNotNull(user, "User must be supplied");
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType(HTML_CONTENT_TYPE + ";charset=UTF-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(HtmlRenderer.renderOAuthAuthorizeTokenPage(
        user.getAddress(), getOrGenerateXsrfToken(user)));
  }

  private void doAuthorizeTokenPost(HttpServletRequest req, HttpServletResponse resp,
                                    ParticipantId user, OAuthMessage message)
      throws IOException {
    Preconditions.checkNotNull(user, "User must be supplied");

    if (Strings.isNullOrEmpty(req.getParameter("token"))
        || !req.getParameter("token").equals(xsrfTokens.get(user))) {
      LOG.warning("Request without a valid XSRF token from " + req.getRemoteAddr() + " for user " + user);
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid XSRF token");
      return;
    }

    if (req.getParameter("cancel") != null) {
      try {
        tokenContainer.rejectRequestToken(message.getToken());
      } catch (OAuthProblemException e) {
        LOG.info("Rejecting a request token failed", e);
        resp.sendError(e.getHttpStatusCode(), e.getMessage());
        return;
      }
      resp.setContentType(PLAIN_CONTENT_TYPE);
      resp.getWriter().append("No access granted, you can now close this page.");
      resp.setStatus(HttpServletResponse.SC_OK);
      return;
    } else if (req.getParameter("agree") == null) {
      LOG.warning("Bad request when authorizing a token from " + req.getRemoteAddr() + " for user " + user);
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    OAuthAccessor accessor;
    try {
      accessor = tokenContainer.authorizeRequestToken(message.getToken(), user);
    } catch (OAuthProblemException e) {
      LOG.info("Authorizing a request token failed", e);
      resp.sendError(e.getHttpStatusCode(), e.getMessage());
      return;
    }

    String callback = message.getParameter(OAuth.OAUTH_CALLBACK);
    callback = OAuth.addParameters(callback, OAuth.OAUTH_TOKEN, accessor.requestToken);
    resp.sendRedirect(callback);
  }

  private void doExchangeToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthMessage message = new JakartaHttpRequestMessage(req, req.getRequestURL().toString());

    OAuthAccessor accessor;
    try {
      accessor = tokenContainer.getRequestTokenAccessor(message.getToken());
    } catch (OAuthProblemException e) {
      LOG.info("Request token unknown", e);
      resp.sendError(e.getHttpStatusCode(), e.getMessage());
      return;
    }

    try {
      validator.validateMessage(message, accessor);
    } catch (OAuthException | URISyntaxException e) {
      LOG.info("The message does not conform to OAuth", e);
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    OAuthAccessor authorizedAccessor;
    try {
      authorizedAccessor = tokenContainer.generateAccessToken(accessor.requestToken);
    } catch (OAuthProblemException e) {
      LOG.info("Request token unknown", e);
      resp.sendError(e.getHttpStatusCode(), e.getMessage());
      return;
    }

    resp.setContentType(OAuth.FORM_ENCODED);
    try (ServletOutputStream out = resp.getOutputStream()) {
      OAuth.formEncode(OAuth.newList(
          OAuth.OAUTH_TOKEN, authorizedAccessor.accessToken,
          OAuth.OAUTH_TOKEN_SECRET, authorizedAccessor.tokenSecret,
          OAuth.OAUTH_CALLBACK_CONFIRMED, "true"), out);
    }
    resp.setStatus(HttpServletResponse.SC_OK);
  }

  private void doAllTokens(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthMessage message = new JakartaHttpRequestMessage(req, req.getRequestURL().toString());
    String requestToken = message.getToken();
    if (requestToken == null) {
      OAuthConsumer consumer =
          new OAuthConsumer("", ANONYMOUS_TOKEN, ANONYMOUS_TOKEN_SECRET, serviceProvider);
      OAuthAccessor accessor = tokenContainer.generateRequestToken(consumer);
      String url = accessor.consumer.serviceProvider.userAuthorizationURL
          + "?oauth_token=" + accessor.requestToken + "&oauth_callback="
          + req.getRequestURL().toString() + "&hd=default";
      resp.sendRedirect(url);
    } else {
      OAuthAccessor accessor;
      try {
        accessor = tokenContainer.getRequestTokenAccessor(requestToken);
      } catch (OAuthProblemException e) {
        LOG.info("Request token unknown", e);
        resp.sendError(e.getHttpStatusCode(), e.getMessage());
        return;
      }
      OAuthAccessor authorizedAccessor;
      try {
        authorizedAccessor = tokenContainer.generateAccessToken(accessor.requestToken);
      } catch (OAuthProblemException e) {
        LOG.info("Request token unknown", e);
        resp.sendError(e.getHttpStatusCode(), e.getMessage());
        return;
      }
      String authorizationCode = authorizedAccessor.requestToken + " "
          + authorizedAccessor.accessToken + " " + authorizedAccessor.tokenSecret;
      String base64AuthCode = CharBase64.encode(authorizationCode.getBytes());
      resp.setCharacterEncoding("UTF-8");
      resp.setContentType(HTML_CONTENT_TYPE + ";charset=UTF-8");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.getWriter().write(HtmlRenderer.renderOAuthAuthorizationCodePage(base64AuthCode));
    }
  }

  private String getOrGenerateXsrfToken(ParticipantId user) {
    String token = xsrfTokens.get(user);
    if (token == null) {
      token = tokenGenerator.generateToken(TOKEN_LENGTH);
      xsrfTokens.put(user, token);
    }
    return token;
  }
}

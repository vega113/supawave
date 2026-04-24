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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.authentication.HttpRequestBasedCallbackHandler;
import org.waveprotocol.box.server.authentication.ParticipantPrincipal;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.authentication.email.AuthEmailService.DispatchResult;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwt;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtCookie;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthConfig;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthProvider;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.id.WaveIdentifiers;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Jakarta variant of AuthenticationServlet.
 * - Uses jakarta.servlet.* APIs.
 * - Supports both jakarta and javax X509 certificate request attributes for
 *   compatibility across containers.
 */
@SuppressWarnings("serial")
@Singleton
public class AuthenticationServlet extends HttpServlet {
  private static final String DEFAULT_REDIRECT_URL = "/";
  public static final String RESPONSE_STATUS_NONE = "NONE";
  public static final String RESPONSE_STATUS_FAILED = "FAILED";
  public static final String RESPONSE_STATUS_SUCCESS = "SUCCESS";
  // PKCS#9 email address OID
  private static final String OID_EMAIL = "1.2.840.113549.1.9.1";

  private static final Log LOG = Log.get(AuthenticationServlet.class);

  private final AccountStore accountStore;
  private final Configuration configuration;
  private final SessionManager sessionManager;
  private final BrowserSessionJwtIssuer browserSessionJwtIssuer;
  private final AuthEmailService authEmailService;
  private final AnalyticsRecorder analyticsRecorder;
  private final String domain;
  private final boolean isClientAuthEnabled;
  private final String clientAuthCertDomain;
  private final boolean isRegistrationDisabled;
  private final boolean isLoginPageDisabled;
  private final boolean secureCookiesByDefault;
  private boolean failedClientAuth = false;
  private final String analyticsAccount;
  private final boolean passwordResetEnabled;
  private final boolean magicLinkEnabled;
  private final boolean emailConfirmationEnabled;
  private final FeatureFlagService featureFlagService;
  private final SocialAuthConfig socialAuthConfig;

  @Inject
  public AuthenticationServlet(AccountStore accountStore,
                               Configuration configuration,
                               SessionManager sessionManager,
                               @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                               Config config,
                               BrowserSessionJwtIssuer browserSessionJwtIssuer,
                               AuthEmailService authEmailService,
                               AnalyticsRecorder analyticsRecorder,
                               FeatureFlagService featureFlagService,
                               SocialAuthConfig socialAuthConfig) {
    this(accountStore, configuration, sessionManager, domain, config, browserSessionJwtIssuer,
        authEmailService, analyticsRecorder, featureFlagService, socialAuthConfig, true);
  }

  public AuthenticationServlet(AccountStore accountStore,
                               Configuration configuration,
                               SessionManager sessionManager,
                               @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                               Config config,
                               BrowserSessionJwtIssuer browserSessionJwtIssuer,
                               AuthEmailService authEmailService,
                               AnalyticsRecorder analyticsRecorder) {
    this(accountStore, configuration, sessionManager, domain, config, browserSessionJwtIssuer,
        authEmailService, analyticsRecorder, null, null, true);
  }

  private AuthenticationServlet(AccountStore accountStore,
                               Configuration configuration,
                               SessionManager sessionManager,
                               String domain,
                               Config config,
                               BrowserSessionJwtIssuer browserSessionJwtIssuer,
                               AuthEmailService authEmailService,
                               AnalyticsRecorder analyticsRecorder,
                               FeatureFlagService featureFlagService,
                               SocialAuthConfig socialAuthConfig,
                               boolean ignored) {
    Preconditions.checkNotNull(accountStore, "AccountStore is null");
    Preconditions.checkNotNull(configuration, "Configuration is null");
    Preconditions.checkNotNull(sessionManager, "Session manager is null");

    this.accountStore = accountStore;
    this.configuration = configuration;
    this.sessionManager = sessionManager;
    this.browserSessionJwtIssuer = browserSessionJwtIssuer;
    this.authEmailService = authEmailService;
    this.analyticsRecorder = analyticsRecorder;
    this.domain = domain.toLowerCase();
    this.isClientAuthEnabled = config.getBoolean("security.enable_clientauth");
    this.clientAuthCertDomain = config.getString("security.clientauth_cert_domain").toLowerCase();
    this.isRegistrationDisabled = config.getBoolean("administration.disable_registration");
    this.isLoginPageDisabled = config.getBoolean("administration.disable_loginpage");
    this.secureCookiesByDefault = config.getBoolean("security.enable_ssl");
    this.analyticsAccount = config.hasPath("core.analytics_account") ?
        config.getString("core.analytics_account") : "";
    this.passwordResetEnabled = !config.hasPath("core.password_reset_enabled")
        || config.getBoolean("core.password_reset_enabled");
    this.magicLinkEnabled = config.hasPath("core.magic_link_enabled")
        && config.getBoolean("core.magic_link_enabled");
    this.emailConfirmationEnabled = config.hasPath("core.email_confirmation_enabled")
        && config.getBoolean("core.email_confirmation_enabled");
    this.featureFlagService = featureFlagService;
    this.socialAuthConfig = socialAuthConfig;
  }

  private static X509Certificate[] getClientCerts(HttpServletRequest req) {
    Object o = req.getAttribute("jakarta.servlet.request.X509Certificate");
    if (o == null) {
      o = req.getAttribute("javax.servlet.request.X509Certificate");
    }
    if (o instanceof X509Certificate[]) return (X509Certificate[]) o;
    return null;
  }

  private LoginContext login(BufferedReader reader) throws LoginException, IOException {
    // Strict, size‑bounded read of the x-www-form-urlencoded POST body.
    final int MAX_LEN = 8192;
    StringBuilder sb = new StringBuilder(256);
    char[] buf = new char[512];
    int n, total = 0;
    while ((n = reader.read(buf)) != -1) {
      total += n;
      if (total > MAX_LEN) {
        throw new LoginException("Authentication request too large");
      }
      sb.append(buf, 0, n);
    }
    if (sb.length() == 0) {
      throw new LoginException("Empty authentication request body");
    }

    Subject subject = new Subject();
    MultiMap<String> parameters = new MultiMap<>();
    // Jetty 12: prefer Charset overload
    UrlEncoded.decodeTo(sb.toString(), parameters, java.nio.charset.StandardCharsets.UTF_8);
    CallbackHandler callbackHandler = new HttpRequestBasedCallbackHandler(parameters);
    LoginContext context = new LoginContext("Wave", subject, callbackHandler, configuration);
    context.login();
    return context;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    LoginContext context;
    Subject subject;
    ParticipantId loggedInAddress = null;

    if (isClientAuthEnabled) {
      boolean skipClientAuth = false;
      try {
        X509Certificate[] certs = getClientCerts(req);
        if (certs == null) {
          if (isLoginPageDisabled) {
            throw new IllegalStateException(
                "No client X.509 certificate provided (you need to get a certificate"
                    + " from your systems manager and import it into your browser).");
          } else {
            failedClientAuth = true;
            skipClientAuth = true;
            doGet(req, resp);
          }
        }

        if (!skipClientAuth) {
          failedClientAuth = false;
          subject = new Subject();
          for (X509Certificate cert : certs) {
            X500Principal principal = cert.getSubjectX500Principal();
            subject.getPrincipals().add(principal);
          }
          loggedInAddress = getLoggedInUser(subject);
        }
      } catch (InvalidParticipantAddress e1) {
        throw new IllegalStateException(
            "The user provided valid authentication information, but the username"
                + " isn't a valid user address.");
      }
    }

    if (!isLoginPageDisabled && loggedInAddress == null) {
      try {
        context = login(req.getReader());
      } catch (LoginException e) {
        String message = "The username or password you entered is incorrect.";
        String responseType = RESPONSE_STATUS_FAILED;
        LOG.info("User authentication failed: " + e.getLocalizedMessage());
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType("text/html;charset=utf-8");
        resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain, message,
            responseType, isLoginPageDisabled, analyticsAccount,
            passwordResetEnabled, magicLinkEnabled, socialProviderLinks()));
        return;
      }

      subject = context.getSubject();
      try {
        loggedInAddress = getLoggedInUser(subject);
      } catch (InvalidParticipantAddress e1) {
        throw new IllegalStateException(
            "The user provided valid authentication information, but the username"
                + " isn't a valid user address.");
      }

      if (loggedInAddress == null) {
        try { context.logout(); } catch (LoginException ignored) {}
        throw new IllegalStateException(
            "The user provided valid authentication information, but we don't "
                + "know how to map their identity to a wave user address.");
      }
    }

    if (loggedInAddress == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      resp.setContentType("text/html;charset=utf-8");
      resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain,
          "Sign in is not available for this deployment.",
          RESPONSE_STATUS_FAILED, isLoginPageDisabled, analyticsAccount,
          passwordResetEnabled, magicLinkEnabled, socialProviderLinks()));
      return;
    }

    // Check email confirmation if enabled
    if (emailConfirmationEnabled) {
      try {
        AccountData acct = accountStore.getAccount(loggedInAddress);
        if (acct != null && acct.isHuman()) {
          HumanAccountData human = acct.asHuman();
          if (HumanAccountData.STATUS_SUSPENDED.equals(human.getStatus())) {
            renderSuspendedAccountMessage(resp);
            return;
          }
          if (!human.isEmailConfirmed()) {
            String message = buildUnconfirmedEmailMessage(
                authEmailService.sendConfirmationEmail(req, human));
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("text/html;charset=utf-8");
            resp.getWriter().write(HtmlRenderer.renderActivationRequiredAuthenticationPage(
                domain, message, analyticsAccount, isLoginPageDisabled,
                passwordResetEnabled, magicLinkEnabled));
            return;
          }
          long now = System.currentTimeMillis();
          human.setLastLoginTime(now);
          human.setLastActivityTime(now);
        }
      } catch (PersistenceException e) {
        LOG.severe("Failed to check email confirmation for " + loggedInAddress, e);
      }
    }

    // Check if user is suspended (reject login)
    if (loggedInAddress != null) {
      try {
        AccountData acct = accountStore.getAccount(loggedInAddress);
        if (acct != null && acct.isHuman()) {
          HumanAccountData human = acct.asHuman();
          if (HumanAccountData.STATUS_SUSPENDED.equals(human.getStatus())) {
            renderSuspendedAccountMessage(resp);
            return;
          }
          long now = System.currentTimeMillis();
          human.setLastLoginTime(now);
          human.setLastActivityTime(now);
          accountStore.putAccount(acct);
        }
      } catch (PersistenceException e) {
        LOG.severe("Failed to check account status for " + loggedInAddress, e);
      }
    }

    rotateSession(req);
    WebSession session = WebSessions.from(req, true);
    sessionManager.setLoggedInUser(session, loggedInAddress);
    issueBrowserSessionJwtCookie(req, resp, loggedInAddress);
    LOG.info("Authenticated user " + loggedInAddress);
    redirectLoggedInUser(req, resp);
  }

  private void rotateSession(HttpServletRequest req) {
    try {
      req.getSession(true);
      req.changeSessionId();
    } catch (IllegalStateException ignored) {
    }
  }

  private void issueBrowserSessionJwtCookie(HttpServletRequest req,
                                            HttpServletResponse resp,
                                            ParticipantId subject) {
    String token = browserSessionJwtIssuer.issue(subject);
    boolean secureCookie = BrowserSessionJwtCookie.shouldUseSecureCookie(
        req.isSecure(),
        req.getHeader("X-Forwarded-Proto"),
        secureCookiesByDefault);
    resp.addHeader("Set-Cookie",
        BrowserSessionJwtCookie.headerValue(token, browserSessionJwtIssuer.tokenLifetimeSeconds(), secureCookie));
  }

  private String buildUnconfirmedEmailMessage(DispatchResult dispatchResult) {
    return switch (dispatchResult) {
      case SENT -> "Your email has not been confirmed. We sent a fresh activation email.";
      case THROTTLED, FAILED ->
          "Your email has not been confirmed. Check your inbox or try again in a few minutes.";
    };
  }

  private void renderSuspendedAccountMessage(HttpServletResponse resp) throws IOException {
    String message = "Your account has been suspended. Contact your administrator.";
    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain, message,
        RESPONSE_STATUS_FAILED, isLoginPageDisabled, analyticsAccount,
        passwordResetEnabled, magicLinkEnabled, socialProviderLinks()));
  }

  private ParticipantId getLoggedInUser(Subject subject) throws InvalidParticipantAddress {
    String address = null;
    for (Principal p : subject.getPrincipals()) {
      if (p instanceof ParticipantPrincipal) {
        address = ((ParticipantPrincipal) p).getName();
        break;
      } else if (p instanceof X500Principal) {
        return attemptClientCertificateLogin((X500Principal) p);
      }
    }
    return address == null ? null : ParticipantId.of(address);
  }

  private ParticipantId attemptClientCertificateLogin(X500Principal p)
      throws RuntimeException, InvalidParticipantAddress {
    String distinguishedName = p.getName();
    try {
      LdapName ldapName = new LdapName(distinguishedName);
      for (Rdn rdn : ldapName.getRdns()) {
        if (rdn.getType().equals(OID_EMAIL)) {
          String email = decodeEmailFromCertificate((byte[]) rdn.getValue());
          if (email.endsWith("@" + clientAuthCertDomain)) {
            Preconditions.checkState(WaveIdentifiers.isValidIdentifier(email),
                "The decoded email is not a valid wave identifier");
            ParticipantId id = ParticipantId.of(email);
            if (!RegistrationSupport.doesAccountExist(accountStore, id)) {
              if (!isRegistrationDisabled) {
                if (!RegistrationSupport.createAccount(accountStore, id, null)) {
                  return null;
                }
                recordUsersRegisteredAnalytics();
              } else {
                throw new InvalidNameException(
                    "User doesn't already exist, and registration disabled by administrator");
              }
            }
            return id;
          }
        }
      }
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    } catch (InvalidNameException ex) {
      throw new InvalidParticipantAddress(distinguishedName,
          "Certificate does not contain a valid distinguished name");
    }
    return null;
  }

  private void recordUsersRegisteredAnalytics() {
    try {
      analyticsRecorder.incrementUsersRegistered(System.currentTimeMillis());
    } catch (RuntimeException e) {
      LOG.warning("Failed to record usersRegistered analytics", e);
    }
  }

  private String decodeEmailFromCertificate(byte[] encoded) throws UnsupportedEncodingException {
    Preconditions.checkState(encoded.length < 130, "The email address is longer than expected");
    return new String(encoded, 2, encoded.length - 2, "ascii");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setCharacterEncoding("UTF-8");
    req.setCharacterEncoding("UTF-8");
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);

    if (user != null) {
      redirectLoggedInUser(req, resp);
    } else {
      if (isClientAuthEnabled && !failedClientAuth) {
        X509Certificate[] certs = getClientCerts(req);
        if (certs != null) {
          doPost(req, resp);
          return;
        }
      }

      resp.setContentType("text/html;charset=utf-8");
      String registeredParam = req.getParameter("registered");
      boolean isRegistrationSuccess = "1".equals(registeredParam);
      if (!isLoginPageDisabled) {
        resp.setStatus(HttpServletResponse.SC_OK);
      } else {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      }
      String initMessage = "";
      String initResponseType = RESPONSE_STATUS_NONE;
      if (isRegistrationSuccess) {
        initMessage = "Account created! Sign in to get started.";
        initResponseType = RESPONSE_STATUS_SUCCESS;
      }
      rememberSocialReturnTarget(req);
      resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain, initMessage,
          initResponseType, isLoginPageDisabled, analyticsAccount,
          passwordResetEnabled, magicLinkEnabled, socialProviderLinks()));
    }
  }

  private List<HtmlRenderer.SocialProviderLink> socialProviderLinks() {
    if (featureFlagService == null || socialAuthConfig == null
        || isLoginPageDisabled
        || !featureFlagService.isGloballyEnabled(SocialAuthServlet.SOCIAL_AUTH_FLAG)) {
      return java.util.Collections.emptyList();
    }
    List<HtmlRenderer.SocialProviderLink> links = new ArrayList<>();
    for (SocialAuthProvider provider : SocialAuthProvider.values()) {
      if (socialAuthConfig.isConfigured(provider)) {
        links.add(new HtmlRenderer.SocialProviderLink(
            provider.label(), "/auth/social/" + provider.id()));
      }
    }
    return links;
  }

  private void rememberSocialReturnTarget(HttpServletRequest req) {
    String returnTarget = req.getParameter("r");
    if (Strings.isNullOrEmpty(returnTarget)) {
      HttpSession session = req.getSession(false);
      if (session != null) {
        session.removeAttribute(AuthRedirects.SOCIAL_AUTH_RETURN_SESSION_ATTR);
      }
      return;
    }
    req.getSession(true).setAttribute(
        AuthRedirects.SOCIAL_AUTH_RETURN_SESSION_ATTR,
        AuthRedirects.sanitizeLocalRedirect(returnTarget));
  }

  private void redirectLoggedInUser(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    Preconditions.checkState(sessionManager.getLoggedInUser(WebSessions.from(req, false)) != null,
        "The user is not logged in");
    String query = req.getQueryString();
    String encoded = extractQueryParam(query, "r");
    if (encoded == null || encoded.isEmpty()) {
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
      return;
    }
    String candidate;
    try {
      candidate = URLDecoder.decode(encoded, "UTF-8");
    } catch (IllegalArgumentException iae) {
      LOG.fine("Rejecting redirect target due to decode failure");
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
      return;
    }
    if (candidate.length() > 2048 || candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) {
      LOG.fine("Rejecting redirect target due to length or control characters");
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
      return;
    }
    // Extra guardrails against encoded traversal/backslashes and mixed-encoding tricks
    String encLc = encoded.toLowerCase(java.util.Locale.ROOT);
    if (encLc.contains("%0d") || encLc.contains("%0a")) {
      LOG.fine("Rejecting redirect target containing encoded CR/LF");
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
      return;
    }
    if (candidate.indexOf('\\') >= 0 || encLc.contains("%5c") || containsEncodedPathTraversal(encLc)) {
      LOG.fine("Rejecting redirect target due to traversal/backslash content");
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
      return;
    }
    try {
      URI u = new URI(candidate).normalize();
      boolean hasAuthority = (u.getRawAuthority() != null) || (u.getHost() != null);
      boolean hasScheme = u.getScheme() != null;
      String raw = u.toString();
      boolean looksSchemeRelative = raw.startsWith("//");
      boolean startsWithSlash = u.getPath() != null && u.getPath().startsWith("/");
      boolean containsTraversal =
          u.getPath() != null && (u.getPath().contains("/../") || u.getPath().contains("/./"));
      if (hasScheme || hasAuthority || looksSchemeRelative || !startsWithSlash || containsTraversal) {
        LOG.fine("Rejecting redirect target due to invalid scheme/authority/path: " + raw);
        resp.sendRedirect(DEFAULT_REDIRECT_URL);
        return;
      }
      resp.sendRedirect(raw);
    } catch (URISyntaxException use) {
      LOG.fine("Rejecting redirect target due to invalid URI syntax: " + candidate, use);
      resp.sendRedirect(DEFAULT_REDIRECT_URL);
    }
  }

  private static String extractQueryParam(String query, String name) {
    if (query == null || name == null) return null;
    int i = 0;
    while (i <= query.length()) {
      int amp = query.indexOf('&', i);
      String pair = (amp == -1) ? query.substring(i) : query.substring(i, amp);
      if (!pair.isEmpty()) {
        int eq = pair.indexOf('=');
        String k = (eq == -1) ? pair : pair.substring(0, eq);
        if (k.equals(name)) {
          return (eq == -1) ? "" : pair.substring(eq + 1);
        }
      }
      if (amp == -1) break;
      i = amp + 1;
    }
    return null;
  }

  private static boolean containsEncodedPathTraversal(String encLc) {
    // Detect common encodings of dot segments and separators in any mix
    // %2e = '.', %2f = '/', %5c = '\\'
    return encLc.contains("%2e%2e") || // ..
           encLc.contains("%2f%2e") || // /.
           encLc.contains("%2e%2f") || // ./
           encLc.contains("%5c%2e") || // \
           encLc.contains("%2e%5c") || // .\
           encLc.contains("%2f%2e%2e") ||
           encLc.contains("%5c%2e%2e") ||
           encLc.contains("%2e%2e%2f") ||
           encLc.contains("%2e%2e%5c");
  }
}

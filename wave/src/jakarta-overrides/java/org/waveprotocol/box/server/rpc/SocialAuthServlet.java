package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtCookie;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.oauth.PkceUtil;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthConfig;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthException;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthProfile;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthProvider;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthService;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

@SuppressWarnings("serial")
@Singleton
public final class SocialAuthServlet extends HttpServlet {
  public static final String SOCIAL_AUTH_FLAG = "social-auth";
  private static final long PENDING_TTL_MILLIS = 10L * 60L * 1000L;
  private static final long START_RATE_LIMIT_WINDOW_MILLIS = 10L * 60L * 1000L;
  private static final int START_RATE_LIMIT_MAX_ATTEMPTS = 30;
  private static final int START_RATE_LIMIT_MAX_BUCKETS = 4096;
  private static final String AUTH_SESSION_ATTR = "socialAuth.pendingAuthorization";
  private static final String PROFILE_SESSION_ATTR = "socialAuth.pendingProfile";
  private static final String DEFAULT_FAILURE =
      "Social sign-in could not be completed. Check your provider account and try again.";
  private static final String INVALID_USERNAME_MESSAGE =
      "Choose a username using letters, numbers, underscores, and periods.";

  private static final Log LOG = Log.get(SocialAuthServlet.class);

  private final AccountStore accountStore;
  private final FeatureFlagService featureFlagService;
  private final SessionManager sessionManager;
  private final BrowserSessionJwtIssuer browserSessionJwtIssuer;
  private final SocialAuthConfig socialAuthConfig;
  private final SocialAuthService socialAuthService;
  private final WelcomeWaveCreator welcomeWaveCreator;
  private final AnalyticsRecorder analyticsRecorder;
  private final SecureRandom secureRandom;
  private final String domain;
  private final boolean secureCookiesByDefault;
  private final boolean registrationDisabled;
  private final boolean loginPageDisabled;
  private final boolean passwordResetEnabled;
  private final boolean magicLinkEnabled;
  private final String analyticsAccount;
  private final Object accountCreationLock = new Object();
  private final Object startRateLimitLock = new Object();
  private final Map<String, RateLimitBucket> startRateLimits = new HashMap<>();

  @Inject
  public SocialAuthServlet(AccountStore accountStore,
      FeatureFlagService featureFlagService,
      SessionManager sessionManager,
      BrowserSessionJwtIssuer browserSessionJwtIssuer,
      SocialAuthConfig socialAuthConfig,
      SocialAuthService socialAuthService,
      WelcomeWaveCreator welcomeWaveCreator,
      AnalyticsRecorder analyticsRecorder,
      SecureRandom secureRandom,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      Config config) {
    this.accountStore = accountStore;
    this.featureFlagService = featureFlagService;
    this.sessionManager = sessionManager;
    this.browserSessionJwtIssuer = browserSessionJwtIssuer;
    this.socialAuthConfig = socialAuthConfig;
    this.socialAuthService = socialAuthService;
    this.welcomeWaveCreator = welcomeWaveCreator;
    this.analyticsRecorder = analyticsRecorder;
    this.secureRandom = secureRandom;
    this.domain = domain.toLowerCase(Locale.ROOT);
    this.secureCookiesByDefault = config.getBoolean("security.enable_ssl");
    this.registrationDisabled = config.getBoolean("administration.disable_registration");
    this.loginPageDisabled = config.getBoolean("administration.disable_loginpage");
    this.passwordResetEnabled = !config.hasPath("core.password_reset_enabled")
        || config.getBoolean("core.password_reset_enabled");
    this.magicLinkEnabled = config.hasPath("core.magic_link_enabled")
        && config.getBoolean("core.magic_link_enabled");
    this.analyticsAccount = config.hasPath("core.analytics_account")
        ? config.getString("core.analytics_account") : "";
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    resp.setCharacterEncoding("UTF-8");
    String path = req.getPathInfo();
    if (path != null && path.startsWith("/callback/")) {
      handleCallback(req, resp);
      return;
    }
    SocialAuthProvider provider = SocialAuthProvider.fromPath(path);
    if (provider == null) {
      renderFailure(resp, HttpServletResponse.SC_NOT_FOUND, DEFAULT_FAILURE);
      return;
    }
    startAuthorization(provider, req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    if (!"/complete".equals(req.getPathInfo())) {
      renderFailure(resp, HttpServletResponse.SC_NOT_FOUND, DEFAULT_FAILURE);
      return;
    }
    completeSignup(req, resp);
  }

  private void startAuthorization(SocialAuthProvider provider, HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    boolean enabled = featureFlagService.isGloballyEnabled(SOCIAL_AUTH_FLAG)
        || (user != null && featureFlagService.isEnabled(SOCIAL_AUTH_FLAG, user.getAddress()));
    if (!enabled || !socialAuthConfig.isConfigured(provider)) {
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    if (!allowRateLimitedRequest("start", req.getRemoteAddr())) {
      renderFailure(resp, 429, DEFAULT_FAILURE);
      return;
    }
    String state = newToken();
    String nonce = newToken();
    String verifier = PkceUtil.newVerifier(secureRandom);
    String redirect = AuthRedirects.sanitizeLocalRedirect(req.getParameter("r"));
    HttpSession session = req.getSession(true);
    session.setAttribute(AUTH_SESSION_ATTR, new PendingAuthorization(
        provider.id(), state, nonce, verifier, redirect, user != null ? user.getAddress() : null,
        System.currentTimeMillis()));
    resp.sendRedirect(socialAuthService.authorizationUrl(provider, state, nonce,
        PkceUtil.challenge(verifier)));
  }

  private void handleCallback(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (!allowRateLimitedRequest("callback", req.getRemoteAddr())) {
      renderFailure(resp, 429, DEFAULT_FAILURE);
      return;
    }
    SocialAuthProvider provider = SocialAuthProvider.fromPath(req.getPathInfo());
    HttpSession session = req.getSession(false);
    PendingAuthorization pending = session == null
        ? null : (PendingAuthorization) session.getAttribute(AUTH_SESSION_ATTR);
    if (pending == null || provider == null || !pending.provider.equals(provider.id())
        || !constantTimeEquals(pending.state, req.getParameter("state")) || pending.isExpired()) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    session.removeAttribute(AUTH_SESSION_ATTR);
    String code = req.getParameter("code");
    if (code == null || code.isBlank() || req.getParameter("error") != null) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    SocialAuthProfile profile;
    try {
      profile = socialAuthService.fetchProfile(provider, code, pending.codeVerifier, pending.nonce);
    } catch (SocialAuthException e) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    if (!profile.isEmailVerified() || normalizeEmail(profile.getEmail()).isBlank()) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    try {
      AccountData linked = accountStore.getAccountBySocialIdentity(
          profile.getProvider(), profile.getSubject());
      if (linked != null && linked.isHuman()) {
        ParticipantId id = linked.getId();
        if (pending.loggedInUser != null && !id.getAddress().equals(pending.loggedInUser)) {
          clearSocialSession(session);
          renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
          return;
        }
        if (!featureFlagService.isEnabled(SOCIAL_AUTH_FLAG, id.getAddress())) {
          clearSocialSession(session);
          renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
          return;
        }
        if (!canSignIn(linked.asHuman())) {
          clearSocialSession(session);
          renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
          return;
        }
        updateLoginTimestamps(linked);
        signInAndRedirect(req, resp, id, pending.redirect);
        return;
      }
      if (pending.loggedInUser != null) {
        ParticipantId id = ParticipantId.ofUnsafe(pending.loggedInUser);
        if (!featureFlagService.isEnabled(SOCIAL_AUTH_FLAG, id.getAddress())) {
          clearSocialSession(session);
          renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
          return;
        }
        AccountData account = accountStore.getAccount(id);
        if (account != null && account.isHuman() && verifiedEmailMatches(account.asHuman(), profile)) {
          accountStore.linkSocialIdentity(id, toSocialIdentity(profile));
          signInAndRedirect(req, resp, id, pending.redirect);
          return;
        }
        clearSocialSession(session);
        renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
        return;
      }
      AccountData existingEmail = accountStore.getAccountByEmail(normalizeEmail(profile.getEmail()));
      if (existingEmail != null) {
        clearSocialSession(session);
        renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
        return;
      }
      rotateSession(req);
      HttpSession rotated = req.getSession(true);
      String csrf = newToken();
      rotated.setAttribute(PROFILE_SESSION_ATTR, new PendingProfile(
          profile.getProvider(), provider.label(), profile.getSubject(),
          normalizeEmail(profile.getEmail()), profile.getDisplayName(), csrf,
          pending.redirect, System.currentTimeMillis()));
      writeUsernamePage(resp, provider.label(), "",
          AuthenticationServlet.RESPONSE_STATUS_NONE, csrf);
    } catch (Exception e) {
      LOG.warning("Social sign-in failed: " + e.getClass().getName());
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DEFAULT_FAILURE);
    }
  }

  private void completeSignup(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (!allowRateLimitedRequest("complete", req.getRemoteAddr())) {
      renderFailure(resp, 429, DEFAULT_FAILURE);
      return;
    }
    HttpSession session = req.getSession(false);
    PendingProfile pending = session == null
        ? null : (PendingProfile) session.getAttribute(PROFILE_SESSION_ATTR);
    if (pending == null || pending.isExpired()
        || !constantTimeEquals(pending.csrf, req.getParameter("csrf"))) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    if (registrationDisabled || !featureFlagService.isGloballyEnabled(SOCIAL_AUTH_FLAG)) {
      clearSocialSession(session);
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
      return;
    }
    ParticipantId id;
    try {
      id = RegistrationSupport.checkNewUsername(domain, req.getParameter("address"));
    } catch (InvalidParticipantAddress e) {
      writeUsernamePage(resp, pending.providerLabel, INVALID_USERNAME_MESSAGE,
          AuthenticationServlet.RESPONSE_STATUS_FAILED, pending.csrf);
      return;
    }
    if (RegistrationSupport.doesAccountExist(accountStore, id)) {
      writeUsernamePage(resp, pending.providerLabel, "Account already exists",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, pending.csrf);
      return;
    }
    try {
      HumanAccountDataImpl account = new HumanAccountDataImpl(id);
      account.setEmail(pending.email);
      account.setEmailConfirmed(true);
      account.setRegistrationTime(System.currentTimeMillis());
      SocialIdentity socialIdentity = new SocialIdentity(
          pending.provider, pending.subject, pending.email, pending.displayName,
          System.currentTimeMillis());
      account.addOrReplaceSocialIdentity(socialIdentity);
      persistSocialAccountWithOwnerAssignment(account, socialIdentity);
      try {
        analyticsRecorder.incrementUsersRegistered(System.currentTimeMillis());
      } catch (RuntimeException e) {
        LOG.warning("Failed to record usersRegistered analytics", e);
      }
      try {
        welcomeWaveCreator.createWelcomeWave(id);
      } catch (Exception ignored) {
      }
      clearSocialSession(session);
      signInAndRedirect(req, resp, id, pending.redirect);
    } catch (PersistenceException e) {
      LOG.warning("Failed to create social account: " + e.getClass().getName());
      renderFailure(resp, HttpServletResponse.SC_FORBIDDEN, DEFAULT_FAILURE);
    }
  }

  private void persistSocialAccountWithOwnerAssignment(HumanAccountDataImpl account,
      SocialIdentity socialIdentity) throws PersistenceException {
    synchronized (accountCreationLock) {
      if (accountStore.getAccountCount() == 0) {
        account.setRole(HumanAccountData.ROLE_OWNER);
      }
      accountStore.putAccountWithUniqueSocialIdentity(account, socialIdentity);
    }
  }

  private void signInAndRedirect(HttpServletRequest req, HttpServletResponse resp, ParticipantId id,
      String redirect) throws IOException {
    rotateSession(req);
    WebSession session = WebSessions.from(req, true);
    sessionManager.setLoggedInUser(session, id);
    issueBrowserSessionJwtCookie(req, resp, id);
    resp.sendRedirect(AuthRedirects.sanitizeLocalRedirect(redirect));
  }

  private void issueBrowserSessionJwtCookie(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId subject) {
    String token = browserSessionJwtIssuer.issue(subject);
    boolean secureCookie = BrowserSessionJwtCookie.shouldUseSecureCookie(
        req.isSecure(), req.getHeader("X-Forwarded-Proto"), secureCookiesByDefault);
    resp.addHeader("Set-Cookie",
        BrowserSessionJwtCookie.headerValue(token, browserSessionJwtIssuer.tokenLifetimeSeconds(),
            secureCookie));
  }

  private void rotateSession(HttpServletRequest req) {
    try {
      req.getSession(true);
      req.changeSessionId();
    } catch (IllegalStateException ignored) {
    }
  }

  private void updateLoginTimestamps(AccountData account) throws PersistenceException {
    long now = System.currentTimeMillis();
    accountStore.updateHumanLoginTimestamps(account.getId(), now, now);
  }

  private boolean canSignIn(HumanAccountData human) {
    return !HumanAccountData.STATUS_SUSPENDED.equals(human.getStatus())
        && human.isEmailConfirmed();
  }

  private boolean verifiedEmailMatches(HumanAccountData human, SocialAuthProfile profile) {
    String localEmail = normalizeEmail(human.getEmail());
    String providerEmail = normalizeEmail(profile.getEmail());
    return human.isEmailConfirmed() && !localEmail.isBlank() && localEmail.equals(providerEmail);
  }

  private SocialIdentity toSocialIdentity(SocialAuthProfile profile) {
    return new SocialIdentity(profile.getProvider(), profile.getSubject(),
        normalizeEmail(profile.getEmail()), profile.getDisplayName(), System.currentTimeMillis());
  }

  private void writeUsernamePage(HttpServletResponse resp, String providerLabel,
      String message, String responseType, String csrf) throws IOException {
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(HtmlRenderer.renderSocialUsernamePage(
        domain, providerLabel, "", message, responseType, csrf, analyticsAccount));
  }

  private void renderFailure(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(HtmlRenderer.renderSocialFailurePage(
        domain, message, analyticsAccount, loginPageDisabled, passwordResetEnabled,
        magicLinkEnabled));
  }

  private void clearSocialSession(HttpSession session) {
    if (session == null) {
      return;
    }
    session.removeAttribute(AUTH_SESSION_ATTR);
    session.removeAttribute(PROFILE_SESSION_ATTR);
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private boolean allowRateLimitedRequest(String scope, String remoteAddress) {
    String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    String key = scope + ":" + address;
    long now = System.currentTimeMillis();
    synchronized (startRateLimitLock) {
      if (startRateLimits.size() >= START_RATE_LIMIT_MAX_BUCKETS) {
        startRateLimits.entrySet().removeIf(
            entry -> now - entry.getValue().windowStartedAtMillis >= START_RATE_LIMIT_WINDOW_MILLIS);
      }
      if (!startRateLimits.containsKey(key)
          && startRateLimits.size() >= START_RATE_LIMIT_MAX_BUCKETS) {
        return false;
      }
      RateLimitBucket bucket = startRateLimits.get(key);
      if (bucket == null
          || now - bucket.windowStartedAtMillis >= START_RATE_LIMIT_WINDOW_MILLIS) {
        startRateLimits.put(key, new RateLimitBucket(now, 1));
        return true;
      }
      if (bucket.attempts >= START_RATE_LIMIT_MAX_ATTEMPTS) {
        return false;
      }
      bucket.attempts++;
      return true;
    }
  }

  private static final class PendingAuthorization implements Serializable {
    final String provider;
    final String state;
    final String nonce;
    final String codeVerifier;
    final String redirect;
    final String loggedInUser;
    final long createdAtMillis;

    PendingAuthorization(String provider, String state, String nonce, String codeVerifier,
        String redirect, String loggedInUser, long createdAtMillis) {
      this.provider = provider;
      this.state = state;
      this.nonce = nonce;
      this.codeVerifier = codeVerifier;
      this.redirect = redirect;
      this.loggedInUser = loggedInUser;
      this.createdAtMillis = createdAtMillis;
    }

    boolean isExpired() {
      return System.currentTimeMillis() - createdAtMillis > PENDING_TTL_MILLIS;
    }
  }

  private static final class PendingProfile implements Serializable {
    final String provider;
    final String providerLabel;
    final String subject;
    final String email;
    final String displayName;
    final String csrf;
    final String redirect;
    final long createdAtMillis;

    PendingProfile(String provider, String providerLabel, String subject, String email,
        String displayName, String csrf, String redirect, long createdAtMillis) {
      this.provider = provider;
      this.providerLabel = providerLabel;
      this.subject = subject;
      this.email = email;
      this.displayName = displayName;
      this.csrf = csrf;
      this.redirect = redirect;
      this.createdAtMillis = createdAtMillis;
    }

    boolean isExpired() {
      return System.currentTimeMillis() - createdAtMillis > PENDING_TTL_MILLIS;
    }
  }

  private static final class RateLimitBucket {
    final long windowStartedAtMillis;
    int attempts;

    RateLimitBucket(long windowStartedAtMillis, int attempts) {
      this.windowStartedAtMillis = windowStartedAtMillis;
      this.attempts = attempts;
    }
  }
}

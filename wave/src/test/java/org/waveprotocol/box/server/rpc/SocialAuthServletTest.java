package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthConfig;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthException;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthHttpClient;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthService;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class SocialAuthServletTest {
  private static final String DOMAIN = "example.com";
  private FeatureFlagService featureFlagService;

  @After
  public void tearDown() {
    if (featureFlagService != null) {
      featureFlagService.shutdown();
    }
  }

  @Test
  public void startAuthorizationRequiresGlobalFlag() throws Exception {
    Fixture fixture = newFixture(false);
    RequestContext request = request("/github", Map.of(), newSession(new HashMap<>()));
    ResponseContext response = response();

    fixture.servlet.doGet(request.req, response.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    verify(response.resp, never()).sendRedirect(anyString());
  }

  @Test
  public void startAuthorizationRespectsDisabledLoginPage() throws Exception {
    Fixture fixture = newFixture(true, new LinkedHashMap<>(), true);
    RequestContext request = request("/github", Map.of(), newSession(new HashMap<>()));
    ResponseContext response = response();

    fixture.servlet.doGet(request.req, response.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    verify(response.resp, never()).sendRedirect(anyString());
  }

  @Test
  public void callbackRejectsInvalidStateAndClearsPendingAuthorization() throws Exception {
    Fixture fixture = newFixture(true);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);

    assertTrue(sessionAttributes.containsKey("socialAuth.pendingAuthorization"));

    RequestContext callback = request("/callback/github", Map.of("state", "wrong"), session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, callbackResponse.status);
    assertFalse(sessionAttributes.containsKey("socialAuth.pendingAuthorization"));
  }

  @Test
  public void callbackThenCompleteCreatesNoPasswordAccountWithChosenUsername()
      throws Exception {
    Fixture fixture = newFixture(true);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of("r", "/welcome"), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);
    String state = queryParam(startResponse.redirect, "state");

    RequestContext callback = request("/callback/github",
        Map.of("state", state, "code", "provider-code"), session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertTrue(callbackResponse.body().contains("Choose your SupaWave username"));
    assertFalse(callbackResponse.body().contains("octo@example.com"));
    String csrf = hiddenValue(callbackResponse.body(), "csrf");
    RequestContext complete = request("/complete",
        Map.of("csrf", csrf, "address", "octo"), session);
    ResponseContext completeResponse = response();
    fixture.servlet.doPost(complete.req, completeResponse.resp);

    assertEquals("/welcome", completeResponse.redirect);
    AccountData account = fixture.accountStore.getAccount(ParticipantId.ofUnsafe("octo@example.com"));
    assertNotNull(account);
    assertEquals(null, account.asHuman().getPasswordDigest());
    assertTrue(account.asHuman().isEmailConfirmed());
    assertEquals("octo@example.com", account.asHuman().getEmail());
    assertEquals(HumanAccountData.ROLE_OWNER, account.asHuman().getRole());
    assertEquals(1, account.asHuman().getSocialIdentities().size());
    assertEquals("github", account.asHuman().getSocialIdentities().get(0).getProvider());
    verify(fixture.sessionManager).setLoggedInUser(any(WebSession.class),
        eq(ParticipantId.ofUnsafe("octo@example.com")));
    verify(callback.req).changeSessionId();
    verify(complete.req).changeSessionId();
  }

  @Test
  public void callbackThenCompleteUsesSessionReturnTargetFromSignInPage()
      throws Exception {
    Fixture fixture = newFixture(true);
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put(AuthRedirects.SOCIAL_AUTH_RETURN_SESSION_ATTR,
        "/wave/example.com/w+abc?pane=1");
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);
    String state = queryParam(startResponse.redirect, "state");

    RequestContext callback = request("/callback/github",
        Map.of("state", state, "code", "provider-code"), session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);
    String csrf = hiddenValue(callbackResponse.body(), "csrf");
    RequestContext complete = request("/complete",
        Map.of("csrf", csrf, "address", "octo"), session);
    ResponseContext completeResponse = response();
    fixture.servlet.doPost(complete.req, completeResponse.resp);

    assertEquals("/wave/example.com/w+abc?pane=1", completeResponse.redirect);
    assertFalse(sessionAttributes.containsKey(AuthRedirects.SOCIAL_AUTH_RETURN_SESSION_ATTR));
  }

  @Test
  public void completeKeepsSocialAccountUserWhenAccountsAlreadyExist() throws Exception {
    Fixture fixture = newFixture(true);
    HumanAccountDataImpl existing =
        new HumanAccountDataImpl(ParticipantId.ofUnsafe("existing@example.com"));
    fixture.accountStore.putAccount(existing);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of("r", "/wave/"), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);
    String state = queryParam(startResponse.redirect, "state");
    RequestContext callback = request("/callback/github",
        Map.of("state", state, "code", "provider-code"), session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);
    String csrf = hiddenValue(callbackResponse.body(), "csrf");

    RequestContext complete = request("/complete",
        Map.of("csrf", csrf, "address", "octo"), session);
    ResponseContext completeResponse = response();
    fixture.servlet.doPost(complete.req, completeResponse.resp);

    assertEquals("/wave/", completeResponse.redirect);
    AccountData account = fixture.accountStore.getAccount(ParticipantId.ofUnsafe("octo@example.com"));
    assertNotNull(account);
    assertEquals(HumanAccountData.ROLE_USER, account.asHuman().getRole());
  }

  @Test
  public void completeRejectsInvalidCsrf() throws Exception {
    Fixture fixture = newFixture(true);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    String csrf = preparePendingProfile(fixture, session);

    RequestContext complete = request("/complete",
        Map.of("csrf", csrf + "-bad", "address", "octo"), session);
    ResponseContext completeResponse = response();
    fixture.servlet.doPost(complete.req, completeResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, completeResponse.status);
    assertEquals(null, fixture.accountStore.getAccount(ParticipantId.ofUnsafe("octo@example.com")));
  }

  @Test
  public void callbackRejectsExistingMixedCaseEmailWithoutAutoLinking() throws Exception {
    Fixture fixture = newFixture(true);
    HumanAccountDataImpl existing =
        new HumanAccountDataImpl(ParticipantId.ofUnsafe("existing@example.com"));
    existing.setEmail("Octo@Example.com");
    existing.setEmailConfirmed(true);
    fixture.accountStore.putAccount(existing);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);

    RequestContext callback = request("/callback/github",
        Map.of("state", queryParam(startResponse.redirect, "state"), "code", "provider-code"),
        session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, callbackResponse.status);
    assertFalse(sessionAttributes.containsKey("socialAuth.pendingProfile"));
    assertEquals(0, existing.getSocialIdentities().size());
  }

  @Test
  public void loggedInUserCannotBeSwitchedToDifferentLinkedSocialAccount() throws Exception {
    Fixture fixture = newFixture(true);
    ParticipantId alice = ParticipantId.ofUnsafe("alice@example.com");
    ParticipantId bob = ParticipantId.ofUnsafe("bob@example.com");
    HumanAccountDataImpl bobAccount = new HumanAccountDataImpl(bob);
    bobAccount.setEmail("octo@example.com");
    bobAccount.setEmailConfirmed(true);
    bobAccount.addOrReplaceSocialIdentity(
        new SocialIdentity("github", "12345", "octo@example.com", "Octo Cat", 1234L));
    fixture.accountStore.putAccount(bobAccount);
    when(fixture.sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(alice);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);

    RequestContext callback = request("/callback/github",
        Map.of("state", queryParam(startResponse.redirect, "state"), "code", "provider-code"),
        session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, callbackResponse.status);
    verify(fixture.sessionManager, never()).setLoggedInUser(any(WebSession.class), eq(bob));
  }

  @Test
  public void loggedInLinkingRechecksFeatureFlagBeforePersisting() throws Exception {
    Fixture fixture = newFixture(true);
    ParticipantId alice = ParticipantId.ofUnsafe("alice@example.com");
    HumanAccountDataImpl aliceAccount = new HumanAccountDataImpl(alice);
    aliceAccount.setEmail("octo@example.com");
    aliceAccount.setEmailConfirmed(true);
    fixture.accountStore.putAccount(aliceAccount);
    when(fixture.sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(alice);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);

    fixture.featureFlagStore.save(new FeatureFlag(
        "social-auth", "Social sign-in", false, Map.of()));
    featureFlagService.refreshCache();

    RequestContext callback = request("/callback/github",
        Map.of("state", queryParam(startResponse.redirect, "state"), "code", "provider-code"),
        session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, callbackResponse.status);
    assertEquals(0, aliceAccount.getSocialIdentities().size());
    verify(fixture.sessionManager, never()).setLoggedInUser(any(WebSession.class), eq(alice));
  }

  @Test
  public void linkedAccountDenylistOverridesGlobalFlag() throws Exception {
    ParticipantId bob = ParticipantId.ofUnsafe("bob@example.com");
    Fixture fixture = newFixture(true, Map.of(bob.getAddress(), false));
    HumanAccountDataImpl bobAccount = new HumanAccountDataImpl(bob);
    bobAccount.setEmail("octo@example.com");
    bobAccount.setEmailConfirmed(true);
    bobAccount.addOrReplaceSocialIdentity(
        new SocialIdentity("github", "12345", "octo@example.com", "Octo Cat", 1234L));
    fixture.accountStore.putAccount(bobAccount);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);

    RequestContext callback = request("/callback/github",
        Map.of("state", queryParam(startResponse.redirect, "state"), "code", "provider-code"),
        session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, callbackResponse.status);
    verify(fixture.sessionManager, never()).setLoggedInUser(any(WebSession.class), eq(bob));
  }

  @Test
  public void completeRejectsProviderSubjectAlreadyLinkedBeforeCreate() throws Exception {
    Fixture fixture = newFixture(true);
    Map<String, Object> sessionAttributes = new HashMap<>();
    HttpSession session = newSession(sessionAttributes);
    String csrf = preparePendingProfile(fixture, session);
    HumanAccountDataImpl existing =
        new HumanAccountDataImpl(ParticipantId.ofUnsafe("existing@example.com"));
    existing.addOrReplaceSocialIdentity(
        new SocialIdentity("github", "12345", "octo@example.com", "Octo Cat", 1234L));
    fixture.accountStore.putAccount(existing);

    RequestContext complete = request("/complete",
        Map.of("csrf", csrf, "address", "octo"), session);
    ResponseContext completeResponse = response();
    fixture.servlet.doPost(complete.req, completeResponse.resp);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, completeResponse.status);
    assertEquals(null, fixture.accountStore.getAccount(ParticipantId.ofUnsafe("octo@example.com")));
  }

  @Test
  public void callbackAttemptsAreRateLimited() throws Exception {
    Fixture fixture = newFixture(true);
    ResponseContext lastResponse = null;
    for (int i = 0; i < 31; i++) {
      RequestContext callback = request("/callback/github", Map.of("state", "wrong"),
          newSession(new HashMap<>()));
      lastResponse = response();
      fixture.servlet.doGet(callback.req, lastResponse.resp);
    }

    assertEquals(429, lastResponse.status);
  }

  @Test
  public void rateLimiterRejectsNewBucketsAboveGlobalCap() throws Exception {
    Fixture fixture = newFixture(true);
    ResponseContext lastResponse = null;
    for (int i = 0; i < 4097; i++) {
      RequestContext callback = request("/callback/github", Map.of("state", "wrong"),
          newSession(new HashMap<>()), "198.51." + (i / 256) + "." + (i % 256));
      lastResponse = response();
      fixture.servlet.doGet(callback.req, lastResponse.resp);
    }

    assertEquals(429, lastResponse.status);
  }

  private String preparePendingProfile(Fixture fixture, HttpSession session) throws Exception {
    RequestContext start = request("/github", Map.of(), session);
    ResponseContext startResponse = response();
    fixture.servlet.doGet(start.req, startResponse.resp);
    String state = queryParam(startResponse.redirect, "state");
    RequestContext callback = request("/callback/github",
        Map.of("state", state, "code", "provider-code"), session);
    ResponseContext callbackResponse = response();
    fixture.servlet.doGet(callback.req, callbackResponse.resp);
    return hiddenValue(callbackResponse.body(), "csrf");
  }

  private Fixture newFixture(boolean socialAuthEnabled) throws Exception {
    return newFixture(socialAuthEnabled, new LinkedHashMap<>());
  }

  private Fixture newFixture(boolean socialAuthEnabled, Map<String, Boolean> allowedUsers)
      throws Exception {
    return newFixture(socialAuthEnabled, allowedUsers, false);
  }

  private Fixture newFixture(boolean socialAuthEnabled, Map<String, Boolean> allowedUsers,
      boolean disableLoginPage) throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    featureFlagStore.save(new FeatureFlag(
        "social-auth", "Social sign-in", socialAuthEnabled, allowedUsers));
    featureFlagService = new FeatureFlagService(featureFlagStore);
    MemoryStore accountStore = new MemoryStore();
    SessionManager sessionManager = mock(SessionManager.class);
    BrowserSessionJwtIssuer jwtIssuer = mock(BrowserSessionJwtIssuer.class);
    when(jwtIssuer.issue(any(ParticipantId.class))).thenReturn("browser-jwt");
    when(jwtIssuer.tokenLifetimeSeconds()).thenReturn(1209600L);
    Config config = ConfigFactory.parseString(
        "core.public_url = \"https://wave.example.com\"\n"
            + "core.analytics_account = \"\"\n"
            + "core.social_auth.github.client_id = \"github-client\"\n"
            + "core.social_auth.github.client_secret = \"github-secret\"\n"
            + "core.social_auth.github.redirect_uri = \"\"\n"
            + "core.social_auth.google.client_id = \"google-client\"\n"
            + "core.social_auth.google.client_secret = \"google-secret\"\n"
            + "core.social_auth.google.redirect_uri = \"\"\n"
            + "security.enable_ssl = false\n"
            + "administration.disable_registration = false\n"
            + "administration.disable_loginpage = " + disableLoginPage + "\n"
            + "core.password_reset_enabled = true\n"
            + "core.magic_link_enabled = false");
    SocialAuthService service = new SocialAuthService(
        new SocialAuthConfig(config),
        new RecordingHttpClient(),
        Clock.fixed(Instant.parse("2026-04-24T10:00:00Z"), ZoneOffset.UTC));
    SocialAuthServlet servlet = new SocialAuthServlet(
        accountStore,
        featureFlagService,
        sessionManager,
        jwtIssuer,
        new SocialAuthConfig(config),
        service,
        mock(WelcomeWaveCreator.class),
        new AnalyticsRecorder(),
        new SecureRandom(new byte[] {1, 2, 3, 4}),
        DOMAIN,
        config,
        new Object());
    return new Fixture(servlet, accountStore, sessionManager, featureFlagStore);
  }

  private static HttpSession newSession(Map<String, Object> attributes) {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(anyString())).thenAnswer(
        invocation -> attributes.get(invocation.getArgument(0)));
    doAnswer(invocation -> {
      attributes.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(session).setAttribute(anyString(), any());
    doAnswer(invocation -> {
      attributes.remove(invocation.getArgument(0));
      return null;
    }).when(session).removeAttribute(anyString());
    return session;
  }

  private static RequestContext request(String path, Map<String, String> parameters,
      HttpSession session) {
    return request(path, parameters, session, "198.51.100.9");
  }

  private static RequestContext request(String path, Map<String, String> parameters,
      HttpSession session, String remoteAddr) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getPathInfo()).thenReturn(path);
    when(req.getParameter(anyString())).thenAnswer(
        invocation -> parameters.get(invocation.getArgument(0)));
    when(req.getSession(false)).thenReturn(session);
    when(req.getSession(true)).thenReturn(session);
    when(req.getRemoteAddr()).thenReturn(remoteAddr);
    when(req.getHeader("X-Forwarded-Proto")).thenReturn(null);
    when(req.isSecure()).thenReturn(false);
    return new RequestContext(req);
  }

  private static ResponseContext response() throws Exception {
    HttpServletResponse resp = mock(HttpServletResponse.class);
    ResponseContext context = new ResponseContext(resp);
    when(resp.getWriter()).thenReturn(new PrintWriter(context.writer));
    doAnswer(invocation -> {
      context.status = invocation.getArgument(0);
      return null;
    }).when(resp).setStatus(anyInt());
    doAnswer(invocation -> {
      context.redirect = invocation.getArgument(0);
      return null;
    }).when(resp).sendRedirect(anyString());
    return context;
  }

  private static String queryParam(String url, String name) {
    String query = URI.create(url).getRawQuery();
    for (String part : query.split("&")) {
      int equals = part.indexOf('=');
      if (equals < 0) {
        continue;
      }
      String key = URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8);
      if (name.equals(key)) {
        return URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static String hiddenValue(String html, String name) {
    Matcher matcher = Pattern.compile("name=\"" + Pattern.quote(name)
        + "\" value=\"([^\"]+)\"").matcher(html);
    assertTrue("Missing hidden field " + name, matcher.find());
    return matcher.group(1);
  }

  private static final class Fixture {
    final SocialAuthServlet servlet;
    final MemoryStore accountStore;
    final SessionManager sessionManager;
    final MemoryFeatureFlagStore featureFlagStore;

    Fixture(SocialAuthServlet servlet, MemoryStore accountStore, SessionManager sessionManager,
        MemoryFeatureFlagStore featureFlagStore) {
      this.servlet = servlet;
      this.accountStore = accountStore;
      this.sessionManager = sessionManager;
      this.featureFlagStore = featureFlagStore;
    }
  }

  private static final class RequestContext {
    final HttpServletRequest req;

    RequestContext(HttpServletRequest req) {
      this.req = req;
    }
  }

  private static final class ResponseContext {
    final HttpServletResponse resp;
    final StringWriter writer = new StringWriter();
    int status = HttpServletResponse.SC_OK;
    String redirect;

    ResponseContext(HttpServletResponse resp) {
      this.resp = resp;
    }

    String body() {
      return writer.toString();
    }
  }

  private static final class RecordingHttpClient implements SocialAuthHttpClient {
    @Override
    public String postForm(String url, Map<String, String> form, Map<String, String> headers)
        throws SocialAuthException {
      return "{\"access_token\":\"gh-token\"}";
    }

    @Override
    public String get(String url, Map<String, String> headers) throws SocialAuthException {
      if (url.endsWith("/user/emails")) {
        return "[{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":true}]";
      }
      return "{\"id\":12345,\"login\":\"octocat\",\"name\":\"Octo Cat\"}";
    }
  }
}

package org.waveprotocol.box.server.authentication.oauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.typesafe.config.ConfigFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class SocialAuthServiceTest {
  @Test
  public void googleAuthorizationUrlIncludesNonceAndPkceChallenge() {
    SocialAuthService service = newService(new RecordingHttpClient());

    String url = service.authorizationUrl(SocialAuthProvider.GOOGLE, "state value",
        "nonce value", "challenge value");

    assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
    assertTrue(url.contains("client_id=google-client"));
    assertTrue(url.contains("redirect_uri=https%3A%2F%2Fwave.example.com%2Fauth%2Fsocial%2Fcallback%2Fgoogle"));
    assertTrue(url.contains("scope=openid+email+profile"));
    assertTrue(url.contains("state=state+value"));
    assertTrue(url.contains("nonce=nonce+value"));
    assertTrue(url.contains("code_challenge=challenge+value"));
    assertTrue(url.contains("code_challenge_method=S256"));
  }

  @Test
  public void githubAuthorizationUrlDoesNotIncludeNonce() {
    SocialAuthService service = newService(new RecordingHttpClient());

    String url = service.authorizationUrl(SocialAuthProvider.GITHUB, "state value",
        "nonce value", "challenge value");

    assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"));
    assertTrue(url.contains("client_id=github-client"));
    assertTrue(url.contains("scope=read%3Auser+user%3Aemail"));
    assertTrue(url.contains("state=state+value"));
    assertTrue(url.contains("code_challenge=challenge+value"));
    assertFalse(url.contains("nonce="));
  }

  @Test
  public void fetchGitHubProfileUsesPkceVerifierAndVerifiedPrimaryEmail()
      throws Exception {
    RecordingHttpClient client = new RecordingHttpClient();
    SocialAuthService service = newService(client);

    SocialAuthProfile profile = service.fetchProfile(
        SocialAuthProvider.GITHUB, "provider-code", "verifier-123", "unused-nonce");

    assertEquals("github", profile.getProvider());
    assertEquals("12345", profile.getSubject());
    assertEquals("octo@example.com", profile.getEmail());
    assertEquals("Octo Cat", profile.getDisplayName());
    assertTrue(profile.isEmailVerified());
    assertEquals("provider-code", client.lastPostForm.get("code"));
    assertEquals("verifier-123", client.lastPostForm.get("code_verifier"));
    assertEquals("github-client", client.lastPostForm.get("client_id"));
    assertEquals("github-secret", client.lastPostForm.get("client_secret"));
    assertEquals("application/json", client.lastPostHeaders.get("Accept"));
    assertEquals(2, client.getUrls.size());
    assertEquals("Bearer gh-token", client.getHeaders.get(0).get("Authorization"));
  }

  @Test
  public void fetchGitHubProfileRejectsMissingVerifiedPrimaryEmail() throws Exception {
    RecordingHttpClient client = new RecordingHttpClient();
    client.emailsResponse = "[{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":false}]";
    SocialAuthService service = newService(client);

    try {
      service.fetchProfile(SocialAuthProvider.GITHUB, "provider-code", "verifier-123",
          "unused-nonce");
      fail("Expected GitHub profile without verified primary email to be rejected");
    } catch (SocialAuthException expected) {
      assertEquals("No verified primary email", expected.getMessage());
    }
  }

  @Test
  public void fetchProfileWrapsMalformedProviderJson() throws Exception {
    RecordingHttpClient client = new RecordingHttpClient();
    client.tokenResponse = "not json";
    SocialAuthService service = newService(client);

    try {
      service.fetchProfile(SocialAuthProvider.GITHUB, "provider-code", "verifier-123",
          "unused-nonce");
      fail("Expected malformed provider JSON to be rejected");
    } catch (SocialAuthException expected) {
      assertEquals("Provider profile could not be verified", expected.getMessage());
    }
  }

  @Test
  public void defaultHttpClientRejectsPlainHttpProviderUrl() throws Exception {
    DefaultSocialAuthHttpClient client = new DefaultSocialAuthHttpClient(
        new SocialAuthConfig(ConfigFactory.parseString("core.public_url = \"https://wave.example.com\"")));

    try {
      client.get("http://example.com/oauth/token", Map.of());
      fail("Expected plaintext provider URL to be rejected");
    } catch (SocialAuthException expected) {
      assertEquals("Provider request must use HTTPS", expected.getMessage());
    }
  }

  private static SocialAuthService newService(RecordingHttpClient client) {
    return new SocialAuthService(new SocialAuthConfig(ConfigFactory.parseString(
        "core.public_url = \"https://wave.example.com\"\n"
            + "core.social_auth.google.client_id = \"google-client\"\n"
            + "core.social_auth.google.client_secret = \"google-secret\"\n"
            + "core.social_auth.google.redirect_uri = \"\"\n"
            + "core.social_auth.github.client_id = \"github-client\"\n"
            + "core.social_auth.github.client_secret = \"github-secret\"\n"
            + "core.social_auth.github.redirect_uri = \"\"")),
        client,
        Clock.fixed(Instant.parse("2026-04-24T10:00:00Z"), ZoneOffset.UTC));
  }

  private static final class RecordingHttpClient implements SocialAuthHttpClient {
    String tokenResponse = "{\"access_token\":\"gh-token\"}";
    String userResponse = "{\"id\":12345,\"login\":\"octocat\",\"name\":\"Octo Cat\"}";
    String emailsResponse = "["
        + "{\"email\":\"other@example.com\",\"primary\":false,\"verified\":true},"
        + "{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":true}"
        + "]";
    Map<String, String> lastPostForm = Map.of();
    Map<String, String> lastPostHeaders = Map.of();
    List<String> getUrls = new ArrayList<>();
    List<Map<String, String>> getHeaders = new ArrayList<>();

    @Override
    public String postForm(String url, Map<String, String> form, Map<String, String> headers) {
      lastPostForm = new LinkedHashMap<>(form);
      lastPostHeaders = new LinkedHashMap<>(headers);
      return tokenResponse;
    }

    @Override
    public String get(String url, Map<String, String> headers) {
      getUrls.add(url);
      getHeaders.add(new LinkedHashMap<>(headers));
      if (url.endsWith("/user/emails")) {
        return emailsResponse;
      }
      return userResponse;
    }
  }
}

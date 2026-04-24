package org.waveprotocol.box.server.authentication.oauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SocialAuthService {
  private static final String GITHUB_USER_URL = "https://api.github.com/user";
  private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
  private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
  private static final long GOOGLE_JWKS_CACHE_MILLIS = 5L * 60L * 1000L;
  private static final long GOOGLE_JWKS_MIN_REFRESH_MILLIS = 60L * 1000L;

  private final SocialAuthConfig config;
  private final SocialAuthHttpClient httpClient;
  private final GoogleIdTokenVerifier googleVerifier;

  @Inject
  public SocialAuthService(SocialAuthConfig config, SocialAuthHttpClient httpClient,
      Clock clock) {
    this.config = config;
    this.httpClient = httpClient;
    this.googleVerifier = new GoogleIdTokenVerifier(
        new CachingGoogleJwksProvider(httpClient, clock), clock);
  }

  public boolean isConfigured(SocialAuthProvider provider) {
    return config.isConfigured(provider);
  }

  public String authorizationUrl(SocialAuthProvider provider, String state, String nonce,
      String codeChallenge) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", config.clientId(provider));
    params.put("redirect_uri", config.redirectUri(provider));
    params.put("response_type", "code");
    params.put("scope", provider.scope());
    params.put("state", state);
    params.put("code_challenge", codeChallenge);
    params.put("code_challenge_method", "S256");
    if (provider == SocialAuthProvider.GOOGLE) {
      params.put("nonce", nonce);
    }
    return provider.authorizationEndpoint() + "?" + encodeForm(params);
  }

  public SocialAuthProfile fetchProfile(SocialAuthProvider provider, String code,
      String codeVerifier, String nonce) throws SocialAuthException {
    try {
      Map<String, String> form = new LinkedHashMap<>();
      form.put("client_id", config.clientId(provider));
      form.put("client_secret", config.clientSecret(provider));
      form.put("code", code);
      form.put("redirect_uri", config.redirectUri(provider));
      form.put("grant_type", "authorization_code");
      form.put("code_verifier", codeVerifier);
      String tokenJson = httpClient.postForm(provider.tokenEndpoint(), form,
          provider == SocialAuthProvider.GITHUB ? Map.of("Accept", "application/json") : Map.of());
      JsonObject token = JsonParser.parseString(tokenJson).getAsJsonObject();
      if (provider == SocialAuthProvider.GOOGLE) {
        if (!token.has("id_token")) {
          throw new SocialAuthException("Provider did not return an ID token");
        }
        return googleVerifier.verify(token.get("id_token").getAsString(), config.clientId(provider),
            nonce);
      }
      String accessToken = token.has("access_token") ? token.get("access_token").getAsString() : "";
      if (accessToken.isBlank()) {
        throw new SocialAuthException("Provider did not return an access token");
      }
      return fetchGitHubProfile(accessToken);
    } catch (SocialAuthException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SocialAuthException("Provider profile could not be verified", e);
    }
  }

  private SocialAuthProfile fetchGitHubProfile(String accessToken) throws SocialAuthException {
    try {
      Map<String, String> headers = Map.of(
          "Authorization", "Bearer " + accessToken,
          "Accept", "application/vnd.github+json");
      JsonObject user = JsonParser.parseString(httpClient.get(GITHUB_USER_URL, headers))
          .getAsJsonObject();
      String subject = user.has("id") ? user.get("id").getAsString() : "";
      String displayName = user.has("name") && !user.get("name").isJsonNull()
          ? user.get("name").getAsString()
          : (user.has("login") ? user.get("login").getAsString() : null);
      JsonArray emails = JsonParser.parseString(httpClient.get(GITHUB_EMAILS_URL, headers))
          .getAsJsonArray();
      String verifiedEmail = null;
      for (var element : emails) {
        JsonObject email = element.getAsJsonObject();
        boolean primary = email.has("primary") && email.get("primary").getAsBoolean();
        boolean verified = email.has("verified") && email.get("verified").getAsBoolean();
        if (primary && verified && email.has("email")) {
          verifiedEmail = email.get("email").getAsString();
          break;
        }
      }
      if (subject.isBlank() || verifiedEmail == null || verifiedEmail.isBlank()) {
        throw new SocialAuthException("No verified primary email");
      }
      return new SocialAuthProfile("github", subject, verifiedEmail, displayName, true);
    } catch (SocialAuthException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SocialAuthException("Provider profile could not be verified", e);
    }
  }

  private static String encodeForm(Map<String, String> form) {
    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, String> entry : form.entrySet()) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
              StandardCharsets.UTF_8));
    }
    return body.toString();
  }

  private static final class CachingGoogleJwksProvider implements JwksProvider {
    private final SocialAuthHttpClient httpClient;
    private final Clock clock;
    private String cachedJwks;
    private long expiresAtMillis;
    private long lastRefreshAtMillis;

    CachingGoogleJwksProvider(SocialAuthHttpClient httpClient, Clock clock) {
      this.httpClient = httpClient;
      this.clock = clock;
    }

    @Override
    public synchronized String jwksJson() throws SocialAuthException {
      long now = clock.millis();
      if (cachedJwks != null && now < expiresAtMillis) {
        return cachedJwks;
      }
      return refreshJwksJson();
    }

    @Override
    public synchronized String refreshJwksJson() throws SocialAuthException {
      long now = clock.millis();
      cachedJwks = httpClient.get(GOOGLE_JWKS_URL, Map.of());
      lastRefreshAtMillis = now;
      expiresAtMillis = now + GOOGLE_JWKS_CACHE_MILLIS;
      return cachedJwks;
    }
  }
}

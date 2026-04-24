package org.waveprotocol.box.server.authentication.oauth;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;

public final class SocialAuthConfig {
  private final Config config;
  private final String publicBaseUrl;

  @Inject
  public SocialAuthConfig(Config config) {
    this.config = config;
    this.publicBaseUrl = PublicBaseUrlResolver.resolve(config);
  }

  public boolean isConfigured(SocialAuthProvider provider) {
    return !clientId(provider).isBlank() && !clientSecret(provider).isBlank();
  }

  public String clientId(SocialAuthProvider provider) {
    return read(provider, "client_id");
  }

  public String clientSecret(SocialAuthProvider provider) {
    return read(provider, "client_secret");
  }

  public String redirectUri(SocialAuthProvider provider) {
    String configured = read(provider, "redirect_uri");
    if (!configured.isBlank()) {
      return configured;
    }
    return publicBaseUrl + "/auth/social/callback/" + provider.id();
  }

  public int timeoutSeconds() {
    String path = "core.social_auth.http_timeout_seconds";
    if (!config.hasPath(path)) {
      return 10;
    }
    int seconds = config.getInt(path);
    return seconds > 0 ? seconds : 10;
  }

  private String read(SocialAuthProvider provider, String field) {
    String path = "core.social_auth." + provider.id() + "." + field;
    if (!config.hasPath(path)) {
      return "";
    }
    String value = config.getString(path);
    return value == null ? "" : value.trim();
  }
}

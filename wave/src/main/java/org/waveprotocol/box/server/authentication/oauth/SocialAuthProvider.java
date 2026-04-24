package org.waveprotocol.box.server.authentication.oauth;

import java.util.Locale;

public enum SocialAuthProvider {
  GOOGLE("google", "Google", "openid email profile",
      "https://accounts.google.com/o/oauth2/v2/auth",
      "https://oauth2.googleapis.com/token"),
  GITHUB("github", "GitHub", "read:user user:email",
      "https://github.com/login/oauth/authorize",
      "https://github.com/login/oauth/access_token");

  private final String id;
  private final String label;
  private final String scope;
  private final String authorizationEndpoint;
  private final String tokenEndpoint;

  SocialAuthProvider(String id, String label, String scope, String authorizationEndpoint,
      String tokenEndpoint) {
    this.id = id;
    this.label = label;
    this.scope = scope;
    this.authorizationEndpoint = authorizationEndpoint;
    this.tokenEndpoint = tokenEndpoint;
  }

  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  public String scope() {
    return scope;
  }

  public String authorizationEndpoint() {
    return authorizationEndpoint;
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public static SocialAuthProvider fromPath(String path) {
    if (path == null) {
      return null;
    }
    String normalized = path.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    int slash = normalized.indexOf('/');
    if (slash >= 0) {
      normalized = normalized.substring(slash + 1);
    }
    for (SocialAuthProvider provider : values()) {
      if (provider.id.equals(normalized)) {
        return provider;
      }
    }
    return null;
  }
}

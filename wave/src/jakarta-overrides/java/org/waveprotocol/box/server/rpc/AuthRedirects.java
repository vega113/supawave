package org.waveprotocol.box.server.rpc;

import java.net.URI;
import java.net.URISyntaxException;

final class AuthRedirects {
  private static final String DEFAULT_REDIRECT_URL = "/";
  static final String SOCIAL_AUTH_RETURN_SESSION_ATTR = "socialAuth.returnTarget";

  private AuthRedirects() {
  }

  static String sanitizeLocalRedirect(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return DEFAULT_REDIRECT_URL;
    }
    if (candidate.length() > 2048 || candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0
        || candidate.indexOf('\\') >= 0) {
      return DEFAULT_REDIRECT_URL;
    }
    try {
      URI uri = new URI(candidate).normalize();
      boolean hasAuthority = (uri.getRawAuthority() != null) || (uri.getHost() != null);
      boolean hasScheme = uri.getScheme() != null;
      String raw = uri.toString();
      boolean startsWithSlash = uri.getPath() != null && uri.getPath().startsWith("/");
      boolean containsTraversal =
          uri.getPath() != null && (uri.getPath().contains("/../") || uri.getPath().contains("/./"));
      if (hasScheme || hasAuthority || raw.startsWith("//") || !startsWithSlash
          || containsTraversal) {
        return DEFAULT_REDIRECT_URL;
      }
      return raw;
    } catch (URISyntaxException e) {
      return DEFAULT_REDIRECT_URL;
    }
  }
}

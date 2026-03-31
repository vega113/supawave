package org.waveprotocol.box.server.authentication.email;

import com.typesafe.config.Config;

public final class PublicBaseUrlResolver {

  private PublicBaseUrlResolver() {
  }

  public static String resolve(Config config) {
    if (config.hasPath("core.public_url")) {
      String configuredUrl = stripTrailingSlash(config.getString("core.public_url").trim());
      if (!configuredUrl.isEmpty()) {
        return configuredUrl;
      }
    }

    String configuredAddress = readFrontendAddress(config);
    String scheme = readFrontendScheme(config);
    return stripTrailingSlash(scheme + "://" + configuredAddress);
  }

  private static String readFrontendAddress(Config config) {
    if (config.hasPath("core.http_frontend_public_address")) {
      String publicAddress = config.getString("core.http_frontend_public_address").trim();
      if (!publicAddress.isEmpty()) {
        return publicAddress;
      }
    }
    if (config.hasPath("core.http_frontend_addresses")
        && !config.getStringList("core.http_frontend_addresses").isEmpty()) {
      String addr = config.getStringList("core.http_frontend_addresses").get(0).trim();
      if (!addr.isEmpty()) {
        return addr;
      }
    }
    if (config.hasPath("core.default_http_frontend_address")) {
      String addr = config.getString("core.default_http_frontend_address").trim();
      if (!addr.isEmpty()) {
        return addr;
      }
    }
    return "wave.example.test";
  }

  private static String readFrontendScheme(Config config) {
    boolean sslEnabled = config.hasPath("security.enable_ssl") && config.getBoolean("security.enable_ssl");
    return sslEnabled ? "https" : "http";
  }

  private static String stripTrailingSlash(String value) {
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }
}

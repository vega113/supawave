package org.waveprotocol.box.server.authentication.jwt;

import java.util.Locale;

public enum JwtAudience {
  BROWSER("browser"),
  WEBSOCKET("websocket"),
  ROBOT("robot"),
  DATA_API("data-api"),
  GADGET("gadget");

  private final String claimValue;

  JwtAudience(String claimValue) {
    this.claimValue = claimValue;
  }

  public String claimValue() {
    return claimValue;
  }

  public static JwtAudience fromClaimValue(String claimValue) {
    String normalized = normalize(claimValue);
    for (JwtAudience audience : values()) {
      if (audience.claimValue.equals(normalized)) {
        return audience;
      }
    }
    throw new IllegalArgumentException("Unknown JWT audience: " + claimValue);
  }

  private static String normalize(String claimValue) {
    if (claimValue == null || claimValue.trim().isEmpty()) {
      throw new IllegalArgumentException("claimValue must be provided");
    }
    return claimValue.trim().toLowerCase(Locale.ROOT);
  }
}

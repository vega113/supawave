package org.waveprotocol.box.server.authentication.jwt;

import java.util.Locale;

public enum JwtTokenType {
  BROWSER_SESSION("browser-session"),
  ROBOT_ASSERTION("robot-assertion"),
  ROBOT_ACCESS("robot-access"),
  DATA_API_ACCESS("data-api-access");

  private final String claimValue;

  JwtTokenType(String claimValue) {
    this.claimValue = claimValue;
  }

  public String claimValue() {
    return claimValue;
  }

  public static JwtTokenType fromClaimValue(String claimValue) {
    String normalized = normalize(claimValue);
    for (JwtTokenType tokenType : values()) {
      if (tokenType.claimValue.equals(normalized)) {
        return tokenType;
      }
    }
    throw new IllegalArgumentException("Unknown JWT token type: " + claimValue);
  }

  private static String normalize(String claimValue) {
    if (claimValue == null || claimValue.trim().isEmpty()) {
      throw new IllegalArgumentException("claimValue must be provided");
    }
    return claimValue.trim().toLowerCase(Locale.ROOT);
  }
}

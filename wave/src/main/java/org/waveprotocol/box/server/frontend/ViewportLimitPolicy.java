package org.waveprotocol.box.server.frontend;

import java.util.Locale;

/** Shared limit policy for viewport-scoped fragment windows. */
public final class ViewportLimitPolicy {
  public static final String DIRECTION_FORWARD = "forward";
  public static final String DIRECTION_BACKWARD = "backward";

  private static volatile int defaultLimit = 5;
  private static volatile int maxLimit = 50;

  private ViewportLimitPolicy() {
  }

  public static void setLimits(int configuredDefaultLimit, int configuredMaxLimit) {
    int normalizedDefault = configuredDefaultLimit <= 0 ? 1 : configuredDefaultLimit;
    int normalizedMax =
        configuredMaxLimit < normalizedDefault ? normalizedDefault : configuredMaxLimit;
    maxLimit = normalizedMax;
    defaultLimit = normalizedDefault;
  }

  public static int getDefaultLimit() {
    return defaultLimit;
  }

  public static int getMaxLimit() {
    return maxLimit;
  }

  public static int resolveLimit(String rawLimit) {
    if (rawLimit == null) {
      return defaultLimit;
    }
    try {
      return resolveLimit(Integer.parseInt(rawLimit.trim()));
    } catch (NumberFormatException e) {
      return defaultLimit;
    }
  }

  public static int resolveLimit(int requestedLimit) {
    if (requestedLimit <= 0) {
      return defaultLimit;
    }
    return Math.min(requestedLimit, maxLimit);
  }

  public static String normalizeDirection(String rawDirection) {
    if (rawDirection == null) {
      return DIRECTION_FORWARD;
    }
    String normalized = rawDirection.trim().toLowerCase(Locale.ROOT);
    return DIRECTION_BACKWARD.equals(normalized) ? DIRECTION_BACKWARD : DIRECTION_FORWARD;
  }
}

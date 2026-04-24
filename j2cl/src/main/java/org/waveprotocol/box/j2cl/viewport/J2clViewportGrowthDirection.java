package org.waveprotocol.box.j2cl.viewport;

import java.util.Locale;

public final class J2clViewportGrowthDirection {
  public static final String FORWARD = "forward";
  public static final String BACKWARD = "backward";

  private J2clViewportGrowthDirection() {
  }

  /** Defaults unknown or missing directions to forward growth, matching the server API default. */
  public static String normalize(String direction) {
    if (direction == null) {
      return FORWARD;
    }
    String normalized = direction.trim().toLowerCase(Locale.ROOT);
    return BACKWARD.equals(normalized) ? BACKWARD : FORWARD;
  }

  public static boolean isBackward(String direction) {
    return BACKWARD.equals(direction);
  }
}

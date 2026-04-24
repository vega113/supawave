package org.waveprotocol.box.j2cl.viewport;

public final class J2clViewportGrowthDirection {
  public static final String FORWARD = "forward";
  public static final String BACKWARD = "backward";

  private J2clViewportGrowthDirection() {
  }

  /** Defaults unknown or missing directions to forward growth, matching the server API default. */
  public static String normalize(String direction) {
    return BACKWARD.equals(direction) ? BACKWARD : FORWARD;
  }

  public static boolean isBackward(String direction) {
    return BACKWARD.equals(direction);
  }
}

package com.google.gwt.core.shared;

/** Minimal server-side stub to satisfy Timing/StatRenderer during Jakarta tests. */
public final class GWT {
  private GWT() {}

  public static boolean isClient() {
    return false;
  }
}

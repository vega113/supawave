package com.google.gwt.user.client;

public abstract class Timer {
  private boolean cancelled;
  private int delayMillis = -1;

  public abstract void run();

  public void schedule(int delayMillis) {
    this.delayMillis = delayMillis;
    this.cancelled = false;
  }

  public void cancel() {
    this.cancelled = true;
  }

  public boolean wasCancelled() {
    return cancelled;
  }

  public int getDelayMillis() {
    return delayMillis;
  }
}

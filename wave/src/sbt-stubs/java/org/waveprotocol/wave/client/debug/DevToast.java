/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package org.waveprotocol.wave.client.debug;

/**
 * No-op stub for SBT compilation. The real implementation uses GWT
 * DOM widgets for on-screen debug toasts.
 */
public final class DevToast {
  private DevToast() {}
  public static void show(String message) {}
  public static void showOnce(String key, String message) {}
}

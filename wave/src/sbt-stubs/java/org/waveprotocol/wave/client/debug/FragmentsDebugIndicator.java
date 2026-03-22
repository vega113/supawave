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
 * No-op stub for SBT compilation. The real implementation lives in
 * wave/src/main/java and uses GWT widgets; this stub provides the
 * API surface used by server-side code (ViewChannelImpl,
 * ClientStatsRawFragmentsApplier) without pulling in GWT DOM deps.
 */
public final class FragmentsDebugIndicator {
  private FragmentsDebugIndicator() {}
  public static void onRanges(int count) {}
  public static void setApplierCounters(int applied, int rejected) {}
}

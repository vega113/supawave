/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.wave.concurrencycontrol.channel;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal counters/timers for fragments emission and client applier. All
 * metrics are opt‑in (setEnabled(true)). Consumers should prefer these for
 * coarse health signals and augment with system metrics where needed.
 */
public final class FragmentsMetrics {
  private static volatile boolean enabled = false;
  public static void setEnabled(boolean e) { enabled = e; }
  public static boolean isEnabled() { return enabled; }

  public static final AtomicLong emissionCount = new AtomicLong();
  public static final AtomicLong emissionErrors = new AtomicLong();
  public static final AtomicLong emissionRanges = new AtomicLong();
  public static final AtomicLong emissionFallbacks = new AtomicLong();
  public static final AtomicLong computeFallbacks = new AtomicLong();
  public static final AtomicLong viewportAmbiguity = new AtomicLong();
  public static final AtomicLong applierEvents = new AtomicLong();
  public static final AtomicLong applierDurationsMs = new AtomicLong();
  /**
   * Count of invalid fragments rejected by a client applier. A fragment is
   * considered invalid when its segment id is null, bounds are negative, or
   * {@code from > to}. This is a coarse indicator of upstream range issues; it
   * should normally remain at (or near) zero under healthy inputs.
   *
   * Usage guidance:
   * - A steady increase suggests malformed ranges are being emitted; inspect
   *   server selection and payload construction.
   * - Spiky increases can occur during canaries while evolving payload shape.
   * - See also RealRawFragmentsApplier.getRejectedCount() for per‑instance
   *   counts and consider correlating with logs for sampling details.
   */
  public static final AtomicLong applierRejected = new AtomicLong();
  public static final AtomicLong httpRequests = new AtomicLong();
  public static final AtomicLong httpOk = new AtomicLong();
  public static final AtomicLong httpErrors = new AtomicLong();
  // Client-side requester counters
  public static final AtomicLong requesterSends = new AtomicLong();
  public static final AtomicLong requesterCoalesced = new AtomicLong();

  private FragmentsMetrics() {}
}

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

package org.waveprotocol.box.server.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer for feature flags providing cached lookups and periodic refresh.
 *
 * <p>Call {@link #isEnabled(String, String)} to check whether a flag is active
 * for a given participant. Evaluation order:
 * <ol>
 *   <li>If the user has an explicit entry in {@code allowedUsers}, that value wins
 *       (regardless of the global toggle).</li>
 *   <li>Otherwise, the flag's global {@code enabled} field is used.</li>
 * </ol>
 */
@Singleton
public final class FeatureFlagService {
  private static final Logger LOG = Logger.getLogger(FeatureFlagService.class.getName());

  /** Refresh interval in seconds. */
  private static final long REFRESH_INTERVAL_SECONDS = 30;

  private final FeatureFlagStore store;
  private volatile Map<String, FeatureFlag> cache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler;

  @Inject
  public FeatureFlagService(FeatureFlagStore store) {
    this.store = store;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "FeatureFlagRefresh");
      t.setDaemon(true);
      return t;
    });
    // Initial load
    refreshCache();
    // Schedule periodic refresh
    scheduler.scheduleWithFixedDelay(
        this::refreshCache,
        REFRESH_INTERVAL_SECONDS,
        REFRESH_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  /**
   * Checks whether a feature flag is enabled for the given participant.
   *
   * @param flagName      the feature flag name
   * @param participantId the participant address (e.g. "user@example.com")
   * @return true if the participant has an enabled override, or if no override exists and the
   *         flag is globally enabled
   */
  public boolean isEnabled(String flagName, String participantId) {
    return isEnabledInSnapshot(cache.get(flagName), participantId);
  }

  /**
   * Returns only the flag's global enabled state, ignoring per-user allowlist entries.
   */
  public boolean isGloballyEnabled(String flagName) {
    FeatureFlag flag = cache.get(flagName);
    return flag != null && flag.isEnabled();
  }

  /**
   * Returns the names of all flags that are enabled for the given participant.
   */
  public List<String> getEnabledFlagNames(String participantId) {
    // Snapshot the volatile to avoid mixing cache generations during iteration.
    Map<String, FeatureFlag> snapshot = cache;
    List<String> result = new ArrayList<>();
    for (FeatureFlag flag : snapshot.values()) {
      if (isEnabledInSnapshot(flag, participantId)) {
        result.add(flag.getName());
      }
    }
    return result;
  }

  /** Evaluates a flag against a participant without re-reading the volatile cache. */
  private static boolean isEnabledInSnapshot(FeatureFlag flag, String participantId) {
    if (flag == null) return false;
    if (participantId != null) {
      Boolean allowed = flag.getAllowedUsers().get(participantId);
      if (allowed != null) {
        return allowed;
      }
    }
    return flag.isEnabled();
  }

  /**
   * Forces a cache refresh from the store.
   */
  public void refreshCache() {
    try {
      List<FeatureFlag> all = KnownFeatureFlags.mergeWithStored(store.getAll());
      Map<String, FeatureFlag> newCache = new ConcurrentHashMap<>();
      for (FeatureFlag f : all) {
        newCache.put(f.getName(), f);
      }
      cache = newCache;
    } catch (PersistenceException e) {
      LOG.log(Level.WARNING, "Failed to refresh feature flag cache", e);
    }
  }

  public void shutdown() {
    scheduler.shutdownNow();
    try {
      scheduler.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.http.client.Request;

import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.model.id.WaveId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks unread mention counts by periodically querying the search service
 * for waves matching "mentions:me" and counting those with unread blips.
 *
 * <p>The count represents "mentioned waves with unread messages" — waves
 * where the current user is @mentioned AND the wave has any unread blips.
 * This matches Telegram's @N badge semantics.
 */
public final class MentionUnreadTracker {

  /** Listener notified when the unread mention count changes. */
  public interface Listener {
    void onUnreadMentionCountChanged(int count);
  }

  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 10; // cap navigation set at 1000 waves
  private static final int POLL_INTERVAL_MS = 15000;
  private static final int STALE_REQUEST_TIMEOUT_MS = 30000;

  private final SearchService searchService;
  private final TimerService scheduler;
  private final boolean enabled;

  private Listener listener;
  private Request pendingRequest;
  private double pendingRequestStartTime;
  private List<WaveId> unreadMentionWaves = Collections.emptyList();
  private Map<WaveId, Integer> perWaveUnreadCounts = Collections.emptyMap();
  private int totalUnreadCount = 0;
  private int cursor = -1;
  private WaveId currentWaveId;

  private final IncrementalTask pollTask = new IncrementalTask() {
    @Override
    public boolean execute() {
      poll();
      return true;
    }
  };

  public MentionUnreadTracker(SearchService searchService, TimerService scheduler,
      boolean mentionBadgeEnabled, boolean mentionsSearchEnabled) {
    this.searchService = searchService;
    this.scheduler = scheduler;
    this.enabled = mentionBadgeEnabled && mentionsSearchEnabled;
  }

  /** Starts the polling loop. No-op if the feature is disabled. */
  public void start() {
    if (!enabled) {
      return;
    }
    scheduler.scheduleRepeating(pollTask, 0, POLL_INTERVAL_MS);
  }

  /** Stops polling and cancels any in-flight request. */
  public void destroy() {
    scheduler.cancel(pollTask);
    cancelPending();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * Returns the number of unread mention waves collected across all fetched pages.
   * This equals the size of the navigable set, so the badge is always consistent
   * with what {@link #getNextUnreadMentionWaveId()} can reach. Pagination is
   * capped at MAX_PAGES * PAGE_SIZE waves.
   */
  public int getUnreadMentionCount() {
    return totalUnreadCount;
  }

  /**
   * Returns the next unread-mention wave ID, advancing the internal cursor.
   * Skips the currently-selected wave. Returns null if no unread mentions.
   */
  public WaveId getNextUnreadMentionWaveId() {
    List<WaveId> waves = unreadMentionWaves;
    if (waves.isEmpty()) {
      return null;
    }
    int size = waves.size();
    // Try to find a wave that isn't the current one
    for (int i = 0; i < size; i++) {
      cursor = (cursor + 1) % size;
      WaveId candidate = waves.get(cursor);
      if (!candidate.equals(currentWaveId)) {
        return candidate;
      }
    }
    // All waves are the current wave (single-entry edge case)
    return null;
  }

  /**
   * Returns the unread mention count for a specific wave, or 0 if unknown.
   */
  public int getUnreadCountForWave(WaveId id) {
    Integer count = perWaveUnreadCounts.get(id);
    return count != null ? count : 0;
  }

  /** Informs the tracker which wave the user is currently viewing. */
  public void setCurrentWaveId(WaveId id) {
    this.currentWaveId = id;
  }

  private void poll() {
    if (pendingRequest != null) {
      double elapsed = scheduler.currentTimeMillis() - pendingRequestStartTime;
      if (elapsed < STALE_REQUEST_TIMEOUT_MS) {
        // A multi-page scan is already in flight; let it complete rather than
        // canceling and restarting from page 0, which would prevent the badge
        // from ever refreshing on slow connections with many mention waves.
        return;
      }
      // Request has been in flight too long — treat as stale and start fresh.
      cancelPending();
    }
    fetchPage(0, new ArrayList<SearchService.DigestSnapshot>());
  }

  private void fetchPage(final int offset, final List<SearchService.DigestSnapshot> accumulated) {
    final Request[] thisRequest = new Request[1];
    thisRequest[0] = searchService.search("mentions:me unread:true", offset, PAGE_SIZE,
        new SearchService.Callback() {
          @Override
          public void onSuccess(int total, List<SearchService.DigestSnapshot> snapshots) {
            if (pendingRequest != thisRequest[0]) {
              return; // This request was superseded (e.g. by cancelPending); ignore results.
            }
            pendingRequest = null;
            accumulated.addAll(snapshots);
            boolean hasMore = accumulated.size() < total && snapshots.size() == PAGE_SIZE;
            boolean withinCap = accumulated.size() < MAX_PAGES * PAGE_SIZE;
            if (hasMore && withinCap) {
              fetchPage(offset + PAGE_SIZE, accumulated);
            } else {
              handleResults(accumulated);
            }
          }

          @Override
          public void onFailure(String message) {
            if (pendingRequest != thisRequest[0]) {
              return; // This request was superseded; ignore stale failure.
            }
            pendingRequest = null;
            // Keep last known state on failure; next poll will retry.
          }
        });
    pendingRequest = thisRequest[0];
    pendingRequestStartTime = scheduler.currentTimeMillis();
  }

  private void handleResults(List<SearchService.DigestSnapshot> snapshots) {
    List<WaveId> newUnread = new ArrayList<>();
    Map<WaveId, Integer> newPerWaveCounts = new HashMap<>();
    for (SearchService.DigestSnapshot snapshot : snapshots) {
      if (snapshot.getUnreadCount() > 0) {
        newUnread.add(snapshot.getWaveId());
        newPerWaveCounts.put(snapshot.getWaveId(), snapshot.getUnreadCount());
      }
    }

    int oldCount = totalUnreadCount;
    // Adjust cursor: if current wave is still in the new list, keep position
    if (cursor >= 0 && cursor < unreadMentionWaves.size()) {
      WaveId currentCursorWave = unreadMentionWaves.get(cursor);
      int newIndex = newUnread.indexOf(currentCursorWave);
      cursor = newIndex >= 0 ? newIndex : -1;
    } else {
      cursor = -1;
    }

    unreadMentionWaves = Collections.unmodifiableList(newUnread);
    perWaveUnreadCounts = Collections.unmodifiableMap(newPerWaveCounts);
    // Use the collected count so the badge exactly matches the navigable set.
    totalUnreadCount = newUnread.size();

    if (oldCount != totalUnreadCount && listener != null) {
      listener.onUnreadMentionCountChanged(totalUnreadCount);
    }
  }

  private void cancelPending() {
    if (pendingRequest != null) {
      pendingRequest.cancel();
      pendingRequest = null;
    }
  }
}

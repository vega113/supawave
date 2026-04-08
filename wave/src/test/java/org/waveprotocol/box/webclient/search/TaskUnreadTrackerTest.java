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

import junit.framework.TestCase;

import com.google.gwt.http.client.Request;

import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Schedulable;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.model.id.WaveId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link TaskUnreadTracker}.
 *
 * <p>This test resides in the webclient package alongside the production code.
 * It is excluded from the SBT unit test suite (which excludes all webclient
 * and wave.client code) and runs during GWT test compilation.
 */
public final class TaskUnreadTrackerTest extends TestCase {

  private SimpleTimerService scheduler;
  private FakeSearchService searchService;
  private int lastNotifiedCount = -1;

  private final TaskUnreadTracker.Listener testListener =
      new TaskUnreadTracker.Listener() {
        @Override
        public void onUnreadTaskCountChanged(int count) {
          lastNotifiedCount = count;
        }
      };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    scheduler = new SimpleTimerService();
    searchService = new FakeSearchService();
    lastNotifiedCount = -1;
  }

  private TaskUnreadTracker createTracker(boolean badgeEnabled, boolean taskSearchEnabled) {
    TaskUnreadTracker tracker =
        new TaskUnreadTracker(searchService, scheduler, badgeEnabled, taskSearchEnabled);
    tracker.setListener(testListener);
    return tracker;
  }

  public void testDisabledWhenBothFlagsOff() {
    TaskUnreadTracker tracker = createTracker(false, false);
    assertFalse(tracker.isEnabled());
    assertFalse(tracker.isBadgeEnabled());
  }

  public void testEnabledWhenTaskSearchOnlyOn() {
    // Tracker polls for per-wave badges even when the toolbar badge is off.
    TaskUnreadTracker tracker = createTracker(false, true);
    assertTrue(tracker.isEnabled());
    assertFalse(tracker.isBadgeEnabled());
  }

  public void testEnabledWhenBadgeOnlyOn() {
    // Tracker polls to drive the toolbar badge even when task-search is off.
    TaskUnreadTracker tracker = createTracker(true, false);
    assertTrue(tracker.isEnabled());
    assertTrue(tracker.isBadgeEnabled());
  }

  public void testCountsUnreadTaskWaves() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    // 3 snapshots returned; only the 2 with unread > 0 count toward the badge
    searchService.respondSuccess(2, makeSnapshots(
        snap("example.com/w+aaa", 5),
        snap("example.com/w+bbb", 0),
        snap("example.com/w+ccc", 2)
    ));

    assertEquals(2, tracker.getUnreadTaskCount());
    assertEquals(2, lastNotifiedCount);
  }

  public void testPaginationCollectsAllWaves() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    // First page: exactly PAGE_SIZE=100 results -> tracker should request next page
    SnapSpec[] page1Specs = new SnapSpec[100];
    for (int i = 0; i < 100; i++) {
      page1Specs[i] = snap(String.format("example.com/w+p1%03d", i), 1);
    }
    searchService.respondSuccess(150, makeSnapshots(page1Specs));

    // Tracker should now have issued a second search; provide the remainder
    SnapSpec[] page2Specs = new SnapSpec[50];
    for (int i = 0; i < 50; i++) {
      page2Specs[i] = snap(String.format("example.com/w+p2%03d", i), 1);
    }
    searchService.respondSuccess(150, makeSnapshots(page2Specs));

    // Badge and navigable set must both equal the total collected (150)
    assertEquals(150, tracker.getUnreadTaskCount());
    assertEquals(150, lastNotifiedCount);

    // Verify the tracker fetched page 1 at offset 0 and page 2 at offset 100
    assertEquals(2, searchService.requestedOffsets.size());
    assertEquals(Integer.valueOf(0), searchService.requestedOffsets.get(0));
    assertEquals(Integer.valueOf(100), searchService.requestedOffsets.get(1));
  }

  public void testEmptyResultsGivesZero() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    searchService.respondSuccess(0, Collections.<SearchService.DigestSnapshot>emptyList());
    assertEquals(0, tracker.getUnreadTaskCount());
  }

  public void testCursorCyclesThroughWaves() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    searchService.respondSuccess(3, makeSnapshots(
        snap("example.com/w+aaa", 3),
        snap("example.com/w+bbb", 1),
        snap("example.com/w+ccc", 2)
    ));

    WaveId first = tracker.getNextUnreadTaskWaveId();
    WaveId second = tracker.getNextUnreadTaskWaveId();
    WaveId third = tracker.getNextUnreadTaskWaveId();
    WaveId fourth = tracker.getNextUnreadTaskWaveId();

    assertNotNull(first);
    assertNotNull(second);
    assertNotNull(third);
    assertFalse(first.equals(second));
    assertFalse(second.equals(third));
    assertEquals(first, fourth);
  }

  public void testCursorSkipsCurrentWave() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    searchService.respondSuccess(2, makeSnapshots(
        snap("example.com/w+aaa", 3),
        snap("example.com/w+bbb", 1)
    ));

    tracker.setCurrentWaveId(WaveId.deserialise("example.com/w+aaa"));
    WaveId next = tracker.getNextUnreadTaskWaveId();
    assertEquals(WaveId.deserialise("example.com/w+bbb"), next);
  }

  public void testSingleWaveMatchingCurrentReturnsNull() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    searchService.respondSuccess(1, makeSnapshots(
        snap("example.com/w+aaa", 3)
    ));

    tracker.setCurrentWaveId(WaveId.deserialise("example.com/w+aaa"));
    assertNull(tracker.getNextUnreadTaskWaveId());
  }

  public void testFailureKeepsLastState() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    searchService.respondSuccess(2, makeSnapshots(
        snap("example.com/w+aaa", 3),
        snap("example.com/w+bbb", 1)
    ));
    assertEquals(2, tracker.getUnreadTaskCount());

    scheduler.runPending();
    searchService.respondFailure("timeout");
    assertEquals(2, tracker.getUnreadTaskCount());
  }

  public void testQueryIncludesUnreadFilter() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();
    assertEquals("tasks:me unread:true", searchService.lastQuery);
  }

  public void testPollSkipsWhenScanInFlight() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending(); // fires first poll -> search in flight

    // Second poll tick fires while first request is still pending
    scheduler.runPending(); // should be a no-op (scan in flight)

    // The original in-flight request is still the active one; responding to it
    // should update the badge normally.
    searchService.respondSuccess(1, makeSnapshots(snap("example.com/w+aaa", 3)));
    assertEquals(1, tracker.getUnreadTaskCount());
    assertEquals(1, lastNotifiedCount);
  }

  public void testDestroyStopsPolling() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    tracker.destroy();
    assertFalse(scheduler.hasPending());
  }

  public void testStaleRequestWatchdogCancelsHungScan() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending(); // fires first poll -> request in flight

    // Poll while in-flight but not yet stale -- no additional request issued
    scheduler.runPending();
    assertEquals(1, searchService.requestedOffsets.size());

    // Advance time past the stale threshold
    scheduler.advanceTime(31000);

    // Next poll should cancel the stale request and issue a fresh one at offset 0
    scheduler.runPending();
    assertEquals(2, searchService.requestedOffsets.size());
    assertEquals(Integer.valueOf(0), searchService.requestedOffsets.get(1));

    // The fresh request resolves normally and updates the badge
    searchService.respondSuccess(1, makeSnapshots(snap("example.com/w+fresh", 2)));
    assertEquals(1, tracker.getUnreadTaskCount());
    assertEquals(1, lastNotifiedCount);
  }

  public void testDeduplicationRemovesDuplicateWaveIds() {
    TaskUnreadTracker tracker = createTracker(true, true);
    tracker.start();
    scheduler.runPending();

    // Same wave ID returned twice (e.g. appears on two overlapping pages)
    searchService.respondSuccess(3, makeSnapshots(
        snap("example.com/w+aaa", 3),
        snap("example.com/w+aaa", 3),  // duplicate
        snap("example.com/w+bbb", 1)
    ));

    // Badge and navigable set must count unique waves only
    assertEquals(2, tracker.getUnreadTaskCount());
    assertEquals(2, lastNotifiedCount);
  }

  // --- Helpers ---

  private static SnapSpec snap(String waveId, int unread) {
    return new SnapSpec(waveId, unread);
  }

  private static List<SearchService.DigestSnapshot> makeSnapshots(SnapSpec... specs) {
    List<SearchService.DigestSnapshot> list = new ArrayList<SearchService.DigestSnapshot>();
    for (SnapSpec spec : specs) {
      list.add(new SearchService.DigestSnapshot(
          "Title", "Snippet",
          WaveId.deserialise(spec.waveId),
          new org.waveprotocol.wave.model.wave.ParticipantId("test@example.com"),
          Collections.<org.waveprotocol.wave.model.wave.ParticipantId>emptyList(),
          System.currentTimeMillis(),
          spec.unreadCount,
          spec.unreadCount + 5));
    }
    return list;
  }

  private static final class SnapSpec {
    final String waveId;
    final int unreadCount;
    SnapSpec(String waveId, int unreadCount) {
      this.waveId = waveId;
      this.unreadCount = unreadCount;
    }
  }

  /** Minimal timer service that captures tasks for manual execution. */
  private static final class SimpleTimerService implements TimerService {
    private final List<IncrementalTask> pendingTasks = new ArrayList<IncrementalTask>();
    private double fakeTime = 0;

    @Override public void schedule(Task task) { }
    @Override public void schedule(IncrementalTask process) { pendingTasks.add(process); }
    @Override public void scheduleDelayed(Task task, int minimumTime) { }
    @Override public void scheduleDelayed(IncrementalTask process, int minimumTime) {
      pendingTasks.add(process);
    }
    @Override public void scheduleRepeating(IncrementalTask process, int minimumTime, int interval) {
      pendingTasks.add(process);
    }
    @Override public void cancel(Schedulable job) { pendingTasks.remove(job); }
    @Override public boolean isScheduled(Schedulable job) { return pendingTasks.contains(job); }
    @Override public double currentTimeMillis() { return fakeTime; }
    @Override public int elapsedMillis() { return (int) fakeTime; }

    void advanceTime(double ms) { fakeTime += ms; }

    void runPending() {
      List<IncrementalTask> snapshot = new ArrayList<IncrementalTask>(pendingTasks);
      for (IncrementalTask task : snapshot) { task.execute(); }
    }

    boolean hasPending() { return !pendingTasks.isEmpty(); }
  }

  /** Fake search service that captures callbacks for manual triggering. */
  private static final class FakeSearchService implements SearchService {
    private Callback pendingCallback;
    private String lastQuery;
    final List<Integer> requestedOffsets = new ArrayList<Integer>();

    @Override
    public Request search(String query, int index, int numResults, Callback callback) {
      this.lastQuery = query;
      this.requestedOffsets.add(index);
      this.pendingCallback = callback;
      return new Request() {
        @Override public void cancel() { pendingCallback = null; }
        @Override public boolean isPending() { return pendingCallback != null; }
      };
    }

    void respondSuccess(int total, List<DigestSnapshot> snapshots) {
      if (pendingCallback != null) {
        Callback cb = pendingCallback;
        pendingCallback = null;
        cb.onSuccess(total, snapshots);
      }
    }

    void respondFailure(String message) {
      if (pendingCallback != null) {
        Callback cb = pendingCallback;
        pendingCallback = null;
        cb.onFailure(message);
      }
    }
  }
}

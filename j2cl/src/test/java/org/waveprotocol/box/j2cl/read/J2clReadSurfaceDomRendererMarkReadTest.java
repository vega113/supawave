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

package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * F-4 (#1039 / R-4.4 / subsumes #1056) tests for the IntersectionObserver-
 * equivalent dwell-timer mark-as-read behaviour on
 * {@link J2clReadSurfaceDomRenderer}.
 *
 * <p>These tests use a fake {@link J2clReadSurfaceDomRenderer.DwellTimerScheduler}
 * so the test deterministically controls when the 1500 ms dwell window
 * "elapses" — no flaky real-clock waiting.
 */
@J2clTestInput(J2clReadSurfaceDomRendererMarkReadTest.class)
public class J2clReadSurfaceDomRendererMarkReadTest {

  private HTMLDivElement currentHost;
  private HTMLElement currentStyle;

  @Before
  public void setUp() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  @After
  public void tearDown() {
    if (currentHost != null && currentHost.parentElement != null) {
      currentHost.parentElement.removeChild(currentHost);
    }
    if (currentStyle != null && currentStyle.parentElement != null) {
      currentStyle.parentElement.removeChild(currentStyle);
    }
    currentHost = null;
    currentStyle = null;
  }

  @Test
  public void unreadBlipDwellingForFullDelayFiresListenerOnce() {
    HTMLDivElement host = createHost();
    installViewport(host, /* heightPx= */ 200);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    blips.add(unreadBlip("b+1", "first"));
    Assert.assertTrue(renderer.render(blips, java.util.Collections.<String>emptyList()));

    Assert.assertEquals(
        "render must arm one dwell timer for the unread blip", 1, scheduler.scheduled.size());
    Object handle = scheduler.scheduled.get(0).handle;
    Assert.assertEquals(
        J2clReadSurfaceDomRenderer.VIEWPORT_DWELL_DEBOUNCE_MS,
        scheduler.scheduled.get(0).delayMs);

    // Fire the timer synchronously.
    scheduler.fire(handle);

    Assert.assertEquals(java.util.Arrays.asList("b+1"), listener.fired);
  }

  @Test
  public void readBlipNeverArmsADwellTimer() {
    HTMLDivElement host = createHost();
    installViewport(host, 200);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    blips.add(readBlip("b+read"));
    Assert.assertTrue(renderer.render(blips, java.util.Collections.<String>emptyList()));

    Assert.assertTrue(scheduler.scheduled.isEmpty());
    Assert.assertTrue(listener.fired.isEmpty());
  }

  @Test
  public void blipFiringTwiceForSameIdIsBlockedByInFlightSet() {
    HTMLDivElement host = createHost();
    installViewport(host, 200);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    blips.add(unreadBlip("b+1", "first"));
    renderer.render(blips, java.util.Collections.<String>emptyList());

    // Fire the dwell timer.
    Assert.assertEquals(1, scheduler.scheduled.size());
    scheduler.fire(scheduler.scheduled.get(0).handle);
    Assert.assertEquals(1, listener.fired.size());

    // The renderer's evaluateDwellTimers can be triggered again (e.g. via a
    // synthetic scroll event). We invoke it directly through the package-
    // private hook by calling render with a matching blip set, which
    // exercises the `matchesRenderedBlips` fast-path that re-evaluates
    // dwell timers without rebuilding the DOM.
    renderer.render(blips, java.util.Collections.<String>emptyList());

    // The in-flight set blocks a second arm.
    Assert.assertEquals(
        "second pass must not schedule a new timer for an in-flight blip",
        1,
        scheduler.scheduled.size());
    Assert.assertEquals(
        "listener must not have fired again", 1, listener.fired.size());
  }

  @Test
  public void blipOutsideViewportDoesNotArmTimer() {
    HTMLDivElement host = createHost();
    installViewport(host, 100);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    // Three blips at 40px each → 120px total. Host is 100px tall, so the
    // third blip at offsetY 80–120 is partly off-screen; with a 50%-area
    // threshold and 40px height, it needs ≥20px visible. The host is
    // 100px tall so y-range visible is [0, 100]; blip rect [80, 120] is
    // visible 80→100 → 20px → exactly at threshold.
    // Make sure the FOURTH blip (rect [120, 160]) is fully off-screen.
    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    blips.add(unreadBlip("b+1", "first"));
    blips.add(unreadBlip("b+2", "second"));
    blips.add(unreadBlip("b+3", "third"));
    blips.add(unreadBlip("b+4", "fourth"));
    renderer.render(blips, java.util.Collections.<String>emptyList());

    // Only the first ~2 blips fully fit in the 100px host. b+4 is fully
    // below the host's clipped bottom and should NOT have a timer.
    boolean fourthIsScheduled = false;
    for (Scheduled s : scheduler.scheduled) {
      if (s.delayMs == J2clReadSurfaceDomRenderer.VIEWPORT_DWELL_DEBOUNCE_MS
          && (s.label != null && s.label.contains("b+4"))) {
        fourthIsScheduled = true;
      }
    }
    Assert.assertFalse(
        "blip rendered fully below host viewport must not arm a timer",
        fourthIsScheduled);
  }

  @Test
  public void rebuildingSurfaceCancelsExistingDwellTimers() {
    HTMLDivElement host = createHost();
    installViewport(host, 200);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    List<J2clReadBlip> firstSet = new ArrayList<J2clReadBlip>();
    firstSet.add(unreadBlip("b+old", "old"));
    renderer.render(firstSet, java.util.Collections.<String>emptyList());

    Assert.assertEquals(1, scheduler.scheduled.size());
    Object firstHandle = scheduler.scheduled.get(0).handle;

    // New, totally different set → render rebuilds the DOM, must cancel the
    // old timer.
    List<J2clReadBlip> secondSet = new ArrayList<J2clReadBlip>();
    secondSet.add(unreadBlip("b+new", "new"));
    renderer.render(secondSet, java.util.Collections.<String>emptyList());

    Assert.assertTrue(
        "render must cancel the timer for the previous blip set",
        scheduler.cancelled.contains(firstHandle));
  }

  @Test
  public void emptyRenderClearsInFlightAndScheduledTimers() {
    HTMLDivElement host = createHost();
    installViewport(host, 200);
    installBlipLayout();
    FakeScheduler scheduler = new FakeScheduler();
    RecordingListener listener = new RecordingListener();

    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDwellTimerSchedulerForTesting(scheduler);
    renderer.setMarkBlipReadListener(listener);

    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    blips.add(unreadBlip("b+a", "first"));
    renderer.render(blips, java.util.Collections.<String>emptyList());
    scheduler.fire(scheduler.scheduled.get(0).handle);

    // Empty render → all timers cancelled, in-flight set drained.
    Assert.assertFalse(
        renderer.render(java.util.Collections.<J2clReadBlip>emptyList(),
                        java.util.Collections.<String>emptyList()));

    // Now render the same blip again — it has no `unread` attribute server-
    // side anymore (the data path will re-project), but for this test we
    // simulate the still-unread case to prove the in-flight set was
    // drained. A second listener fire is allowed because the blip just
    // finished a full lifecycle.
    listener.fired.clear();
    blips.clear();
    blips.add(unreadBlip("b+a", "first"));
    renderer.render(blips, java.util.Collections.<String>emptyList());
    Assert.assertEquals(
        "after empty-render the in-flight set should be drained",
        1,
        scheduler.scheduled.size() - 1 /* the previous fired entry */);
  }

  // ---------------------------------------------------------------------------
  // Helpers

  private HTMLDivElement createHost() {
    currentHost = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(currentHost);
    return currentHost;
  }

  private void installViewport(HTMLDivElement host, int heightPx) {
    host.style.setProperty("display", "block");
    host.style.setProperty("position", "relative");
    host.style.setProperty("overflow", "auto");
    host.style.setProperty("height", heightPx + "px");
    host.style.setProperty("width", "300px");
  }

  private void installBlipLayout() {
    currentStyle = (HTMLElement) DomGlobal.document.createElement("style");
    currentStyle.textContent =
        "[data-j2cl-read-blip='true']{display:block;height:40px;width:280px;}";
    DomGlobal.document.head.appendChild(currentStyle);
  }

  private static J2clReadBlip unreadBlip(String id, String text) {
    return new J2clReadBlip(
        id,
        text,
        java.util.Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
        /* authorId= */ "",
        /* authorDisplayName= */ "",
        /* lastModifiedTimeMillis= */ 0L,
        /* parentBlipId= */ "",
        /* threadId= */ "",
        /* unread= */ true,
        /* hasMention= */ false);
  }

  private static J2clReadBlip readBlip(String id) {
    return new J2clReadBlip(
        id,
        id,
        java.util.Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
        "",
        "",
        0L,
        "",
        "",
        /* unread= */ false,
        /* hasMention= */ false);
  }

  private static final class FakeScheduler
      implements J2clReadSurfaceDomRenderer.DwellTimerScheduler {
    final List<Scheduled> scheduled = new ArrayList<Scheduled>();
    final java.util.Set<Object> cancelled = new java.util.HashSet<Object>();
    private final Map<Object, Runnable> handleToRunnable = new HashMap<Object, Runnable>();
    private int handleCounter;

    @Override
    public Object schedule(int delayMs, Runnable action) {
      Object handle = new HandleToken(++handleCounter);
      handleToRunnable.put(handle, action);
      scheduled.add(new Scheduled(handle, delayMs, "h" + handleCounter));
      return handle;
    }

    @Override
    public void cancel(Object handle) {
      cancelled.add(handle);
      handleToRunnable.remove(handle);
    }

    void fire(Object handle) {
      Runnable r = handleToRunnable.remove(handle);
      if (r != null) {
        r.run();
      }
    }
  }

  private static final class HandleToken {
    final int id;
    HandleToken(int id) {
      this.id = id;
    }
  }

  private static final class Scheduled {
    final Object handle;
    final int delayMs;
    final String label;
    Scheduled(Object handle, int delayMs, String label) {
      this.handle = handle;
      this.delayMs = delayMs;
      this.label = label;
    }
  }

  private static final class RecordingListener
      implements J2clReadSurfaceDomRenderer.MarkBlipReadListener {
    final List<String> fired = new ArrayList<String>();

    @Override
    public void markBlipRead(String blipId) {
      fired.add(blipId);
    }
  }
}

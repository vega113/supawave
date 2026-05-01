package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.Event;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.KeyboardEventInit;
import elemental2.dom.NodeList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clMentionRange;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

@J2clTestInput(J2clReadSurfaceDomRendererTest.class)
public class J2clReadSurfaceDomRendererTest {
  private HTMLDivElement currentHost;
  private HTMLElement currentStyle;

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
  public void enhanceExistingSurfaceWiresServerRenderedBlipsAndInlineThreads() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\" data-wave-id=\"example.com/w+1\">"
            + "<div class=\"conversation\" data-conv-id=\"c+root\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div></div></div></div>";

    Assert.assertTrue(new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface());

    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    Assert.assertNotNull(surface);
    Assert.assertEquals(
        2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);

    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    Assert.assertNotNull(toggle);
    Assert.assertEquals("true", toggle.getAttribute("aria-expanded"));
    Assert.assertEquals(
        "Collapse inline reply thread 1 (inline)", toggle.getAttribute("aria-label"));
    Assert.assertEquals("−", toggle.textContent);

    HTMLElement inlineThread =
        (HTMLElement) host.querySelector(".inline-thread[data-thread-id='t+inline']");
    HTMLElement rootThread =
        (HTMLElement) host.querySelector(".thread[data-thread-id='t+root']");
    Assert.assertEquals("list", rootThread.getAttribute("role"));
    Assert.assertEquals("group", inlineThread.getAttribute("role"));
    Assert.assertEquals("listitem", blip(host, "b+root").getAttribute("role"));
    Assert.assertEquals("article", blip(host, "b+reply").getAttribute("role"));
    Assert.assertEquals("inline reply thread 1 (inline)", inlineThread.getAttribute("aria-label"));

    toggle.click();

    Assert.assertEquals("true", inlineThread.getAttribute("data-j2cl-thread-collapsed"));
    Assert.assertEquals("false", toggle.getAttribute("aria-expanded"));
    Assert.assertEquals("Expand inline reply thread 1 (inline)", toggle.getAttribute("aria-label"));
    Assert.assertEquals("+", toggle.textContent);
  }

  @Test
  public void enhanceExistingSurfaceIsIdempotentForThreadToggles() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div></div>";
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    Assert.assertTrue(renderer.enhanceExistingSurface());

    Assert.assertEquals(1, host.querySelectorAll(".j2cl-read-thread-toggle").length);
    Assert.assertEquals(1, host.querySelectorAll("[data-j2cl-read-blip-bound='true']").length);
  }

  @Test
  public void failedReEnhancementPreservesFocusedCollapseState() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    reply.focus();
    surface.removeAttribute("data-j2cl-read-surface");
    surface.classList.remove("wave-content");

    Assert.assertFalse(renderer.enhanceExistingSurface());
    toggle.click();

    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertEquals("true", after.getAttribute("aria-current"));
  }

  @Test
  public void renderAfterFailedReEnhancementRebuildsDetachedHost() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadBlip> blips = Arrays.asList(new J2clReadBlip("b+root", "Root text"));

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));
    host.innerHTML = "";
    Assert.assertFalse(renderer.enhanceExistingSurface());

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));

    Assert.assertNotNull(host.querySelector("[data-j2cl-read-surface='true']"));
    Assert.assertEquals("Root text", renderedText(blip(host, "b+root")));
  }

  @Test
  public void enhanceExistingSurfaceReturnsFalseForEmptyHost() {
    assumeBrowserDom();
    Assert.assertFalse(new J2clReadSurfaceDomRenderer(createHost()).enhanceExistingSurface());
  }

  // F-3.S4 (#1038, R-5.6 F.6 — review-1077 Bug 1): blips carrying the
  // tombstone/deleted=true annotation written by
  // J2clRichContentDeltaFactory.blipDeleteRequest must be skipped by the
  // read surface so the user sees the deletion immediately when the
  // delta lands. The flag flows through J2clSelectedWaveProjector
  // (documentIsDeleted) onto J2clReadBlip.isDeleted(); this test
  // exercises the renderer's filter directly.
  @Test
  public void renderSkipsBlipsCarryingTombstoneDeletedAnnotation() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip live =
        new J2clReadBlip(
            "b+live",
            "Visible",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            /* authorId= */ "",
            /* authorDisplayName= */ "",
            /* lastModifiedTimeMillis= */ 0L,
            /* parentBlipId= */ "",
            /* threadId= */ "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false);
    J2clReadBlip tombstoned =
        new J2clReadBlip(
            "b+tombstoned",
            "Should be hidden",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            /* authorId= */ "",
            /* authorDisplayName= */ "",
            /* lastModifiedTimeMillis= */ 0L,
            /* parentBlipId= */ "",
            /* threadId= */ "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ true);

    Assert.assertTrue(
        renderer.render(Arrays.asList(live, tombstoned), Collections.<String>emptyList()));

    Assert.assertNotNull(
        "live blip must remain in the read surface", blip(host, "b+live"));
    Assert.assertNull(
        "tombstoned blip must NOT render on the read surface",
        blip(host, "b+tombstoned"));
    Assert.assertEquals(
        "exactly one blip should be mounted after filtering",
        1,
        host.querySelectorAll("[data-j2cl-read-blip='true']").length);
  }

  // J-UI-6 (#1084, R-5.4): persisted task done state must surface as
  // data-task-completed on the rendered <wave-blip> so the F-3.S2
  // strikethrough CSS applies after reload + live updates from other
  // clients. Without the renderer write the body would render unstyled
  // even though the task/done annotation is correct on disk.
  @Test
  public void renderSetsDataTaskCompletedAttributeWhenTaskDone() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip done =
        new J2clReadBlip(
            "b+done",
            "Pin retry",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "bob@example.com",
            /* taskDueTimestamp= */ 1714560000000L,
            /* bodyItemCount= */ 17);

    Assert.assertTrue(
        renderer.render(Arrays.asList(done), Collections.<String>emptyList()));

    HTMLElement element = blip(host, "b+done");
    Assert.assertNotNull(element);
    Assert.assertTrue(
        "task done blip must carry data-task-completed",
        element.hasAttribute("data-task-completed"));
    Assert.assertEquals(
        "bob@example.com", element.getAttribute("data-task-assignee"));
    Assert.assertEquals(
        "1970-01-01 lookup is wrong; renderer must format from epoch ms",
        "2024-05-01",
        element.getAttribute("data-task-due-date"));
    Assert.assertEquals("17", element.getAttribute("data-blip-doc-size"));
  }

  @Test
  public void renderOmitsTaskAttributesWhenOpen() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    // Open task: no annotations recorded. Renderer must NOT write the
    // attributes — otherwise the strikethrough CSS would paint despite
    // the task being open.
    J2clReadBlip open = new J2clReadBlip("b+open", "Body");

    Assert.assertTrue(
        renderer.render(Arrays.asList(open), Collections.<String>emptyList()));

    HTMLElement element = blip(host, "b+open");
    Assert.assertNotNull(element);
    Assert.assertFalse(
        "open task blip must NOT carry data-task-completed",
        element.hasAttribute("data-task-completed"));
    Assert.assertFalse(element.hasAttribute("data-task-assignee"));
    Assert.assertFalse(element.hasAttribute("data-task-due-date"));
    Assert.assertFalse(element.hasAttribute("data-blip-doc-size"));
  }

  @Test
  public void rerenderRemovesDataTaskCompletedWhenTaskReopens() {
    // Same-wave update where task/done flips back to false must clear the
    // data-task-completed attribute. Without the explicit clear branch,
    // the F-2 fast-path equality check (now extended in J-UI-6 to include
    // task state) would still rebuild the surface, but the per-blip
    // attribute would carry over via the host element reuse path.
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    J2clReadBlip done =
        new J2clReadBlip(
            "b+toggle",
            "Body",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
    J2clReadBlip reopened = done.withTaskDone(false);

    Assert.assertTrue(
        renderer.render(Arrays.asList(done), Collections.<String>emptyList()));
    Assert.assertTrue(
        blip(host, "b+toggle").hasAttribute("data-task-completed"));

    Assert.assertTrue(
        renderer.render(Arrays.asList(reopened), Collections.<String>emptyList()));

    Assert.assertFalse(
        "reopened task blip must NOT carry data-task-completed",
        blip(host, "b+toggle").hasAttribute("data-task-completed"));
  }

  @Test
  public void renderWindowSetsDataTaskCompletedFromWindowEntry() {
    // The dominant production path is renderWindow over the flat render
    // — this test guards the same data-task-completed write on the
    // window-render path so the strikethrough/checkmark works on reload
    // when a wave is opened with an existing task/done annotation.
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadWindowEntry done =
        J2clReadWindowEntry.loadedWithTaskMetadata(
            "blip:b+done",
            0L,
            9L,
            "b+done",
            "Pin retry",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "bob@example.com",
            /* taskDueTimestamp= */ 1714560000000L);

    Assert.assertTrue(renderer.renderWindow(Arrays.asList(done)));

    HTMLElement element = blip(host, "b+done");
    Assert.assertNotNull(element);
    Assert.assertTrue(element.hasAttribute("data-task-completed"));
    Assert.assertTrue(
        "data-task-present must be set when isTask=true (task blip)",
        element.hasAttribute("data-task-present"));
    Assert.assertEquals(
        "bob@example.com", element.getAttribute("data-task-assignee"));
    Assert.assertEquals(
        "2024-05-01", element.getAttribute("data-task-due-date"));
  }

  @Test
  public void renderWindowSetsDataTaskPresentForReopenedTaskWithNoMetadata() {
    // Regression: after a full DOM rebuild (renderWindow clears host.innerHTML),
    // a reopened task blip with taskDone=false AND no assignee/due-date must still
    // carry data-task-present so the Lit _taskPresent field can be set to true on
    // the freshly created <wave-blip> element, keeping the affordance mounted.
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadWindowEntry reopened =
        J2clReadWindowEntry.loadedWithTaskMetadata(
            "blip:b+reopened",
            0L,
            12L,
            "b+reopened",
            "Follow up",
            java.util.Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */ org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
            /* bodyItemCount= */ 5,
            /* isTask= */ true);

    Assert.assertTrue(renderer.renderWindow(java.util.Arrays.asList(reopened)));

    HTMLElement element = blip(host, "b+reopened");
    Assert.assertNotNull(element);
    Assert.assertFalse(
        "reopened task must not have data-task-completed",
        element.hasAttribute("data-task-completed"));
    Assert.assertTrue(
        "reopened task must carry data-task-present so Lit can keep affordance mounted",
        element.hasAttribute("data-task-present"));
  }

  @Test
  public void formatDueDateReturnsEmptyForUnknownTimestamp() {
    Assert.assertEquals(
        "",
        J2clReadSurfaceDomRenderer.formatDueDate(
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP));
    Assert.assertEquals("", J2clReadSurfaceDomRenderer.formatDueDate(0L));
    Assert.assertEquals("", J2clReadSurfaceDomRenderer.formatDueDate(-7L));
  }

  @Test
  public void formatDueDatePadsSingleDigitMonthAndDay() {
    assumeBrowserDom();
    // 2024-01-05 00:00:00 UTC
    long jan5 = 1704412800000L;
    Assert.assertEquals("2024-01-05", J2clReadSurfaceDomRenderer.formatDueDate(jan5));
  }

  // J-UI-6 (#1084, R-5.4): optimistic toggle state must survive an unrelated
  // live-update re-render while the toggle delta is still in flight.
  // Otherwise the equality-check fix that lets live updates from other clients
  // re-render correctly would have a side-effect of flickering the user's own
  // optimistic toggle back to the pre-toggle state on a same-wave update.
  @Test
  public void optimisticTaskStateOverridesModelDuringInFlightToggle() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    // Initial render: server says task is open.
    J2clReadBlip open =
        new J2clReadBlip(
            "b+toggle",
            "Body",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
    Assert.assertTrue(
        renderer.render(Arrays.asList(open), Collections.<String>emptyList()));
    Assert.assertFalse(blip(host, "b+toggle").hasAttribute("data-task-completed"));

    // User clicks toggle → optimistic done. View notifies the renderer.
    renderer.noteOptimisticTaskState(/* waveId= */ "", "b+toggle", true);

    // An unrelated live update arrives (e.g. another user's reaction). The
    // model's task/done is still false because the server hasn't echoed
    // our toggle yet. A re-render with a different reaction triggers a
    // full rebuild via sameReadBlip → false (text unchanged but that
    // fires a path through render — for this test, we trigger a rebuild
    // by passing a contentEntries argument with an extra non-empty entry
    // marker, which forces matchesRenderedBlips to fall through).
    //
    // We exercise the rebuild by re-rendering with text that differs by
    // one character so matchesRenderedBlips returns false.
    J2clReadBlip openWithReactionUpdate =
        new J2clReadBlip(
            "b+toggle",
            "Body!",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
    Assert.assertTrue(
        renderer.render(
            Arrays.asList(openWithReactionUpdate), Collections.<String>emptyList()));

    // Optimistic state must have survived the rebuild.
    Assert.assertTrue(
        "optimistic toggle must override stale model value during in-flight",
        blip(host, "b+toggle").hasAttribute("data-task-completed"));
    // Optimistic state is still tracked because the model has not yet
    // caught up.
    Assert.assertEquals(
        Boolean.TRUE,
        renderer.optimisticTaskValueForTest(/* waveId= */ "", "b+toggle"));
  }

  @Test
  public void optimisticTaskStateClearsWhenServerEchoCatchesUp() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip open =
        new J2clReadBlip(
            "b+echoed",
            "Body",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
    Assert.assertTrue(
        renderer.render(Arrays.asList(open), Collections.<String>emptyList()));
    renderer.noteOptimisticTaskState(/* waveId= */ "", "b+echoed", true);

    // Server echo: model now reports task/done=true. Re-render must clear
    // the optimistic entry so future open transitions are not blocked by
    // a stale optimistic-true override.
    J2clReadBlip done = open.withTaskDone(true);
    // Force a rebuild by changing other state too.
    J2clReadBlip doneWithText =
        new J2clReadBlip(
            "b+echoed",
            "Body!",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */
            org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
    Assert.assertTrue(
        renderer.render(Arrays.asList(doneWithText), Collections.<String>emptyList()));

    Assert.assertTrue(blip(host, "b+echoed").hasAttribute("data-task-completed"));
    // Optimistic entry must be cleared once the model agrees so a later
    // open transition is not stuck.
    Assert.assertNull(
        "optimistic state must clear once model catches up",
        renderer.optimisticTaskValueForTest(/* waveId= */ "", "b+echoed"));
    // Avoid unused-warning on the helper-built reference.
    Assert.assertEquals("b+echoed", done.getBlipId());
  }

  // PR #1097 review (codex P2): the optimistic-toggle override must not
  // bleed across waves that share the same blip id (every wave's root
  // is `b+root`). Composite-key lookup keeps stale entries from one
  // wave's toggle from painting the strikethrough on another wave.
  @Test
  public void optimisticTaskStateIsNamespacedByWaveId() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    // User toggles the root blip on wave A while still mounted on wave A.
    host.setAttribute("data-wave-id", "example.com/w+A");
    J2clReadBlip openOnA = new J2clReadBlip("b+root", "A body");
    Assert.assertTrue(renderer.render(Arrays.asList(openOnA), Collections.<String>emptyList()));
    renderer.noteOptimisticTaskState("example.com/w+A", "b+root", true);

    // User switches to wave B (data-wave-id flips on the host) before
    // the server echoes. Wave B's b+root is not the same task — it must
    // render open even though the (wave A) optimistic entry says done.
    host.setAttribute("data-wave-id", "example.com/w+B");
    J2clReadBlip openOnB = new J2clReadBlip("b+root", "B body");
    Assert.assertTrue(renderer.render(Arrays.asList(openOnB), Collections.<String>emptyList()));

    Assert.assertFalse(
        "optimistic toggle on wave A must not bleed into wave B",
        blip(host, "b+root").hasAttribute("data-task-completed"));
    // Wave A's entry is still pending (server has not echoed yet).
    Assert.assertEquals(
        Boolean.TRUE,
        renderer.optimisticTaskValueForTest("example.com/w+A", "b+root"));
    Assert.assertNull(
        renderer.optimisticTaskValueForTest("example.com/w+B", "b+root"));
  }

  // PR #1097 review (codex P1): the toggle submit path has explicit
  // failure outcomes (bootstrap / build / submit) that never update
  // model state. Without a TTL the optimistic override would stick
  // forever and force the wrong state on every subsequent render.
  @Test
  public void optimisticTaskStateExpiresAfterTtlOnFailedSubmit() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip open = new J2clReadBlip("b+stuck", "Body");

    Assert.assertTrue(renderer.render(Arrays.asList(open), Collections.<String>emptyList()));
    renderer.noteOptimisticTaskState("", "b+stuck", true);
    // Inject an entry whose deadline is already in the past — emulates
    // the situation where a failed submit left the override stuck and
    // wall-clock time has now advanced past TTL.
    forceExpireOptimisticTaskEntry(renderer, /* waveId= */ "", "b+stuck");

    // Render with a different text so matchesRenderedBlips fires the
    // full rebuild path and applyTaskState runs.
    J2clReadBlip openLater = new J2clReadBlip("b+stuck", "Body!");
    Assert.assertTrue(
        renderer.render(Arrays.asList(openLater), Collections.<String>emptyList()));

    Assert.assertFalse(
        "expired optimistic override must not pin the strikethrough",
        blip(host, "b+stuck").hasAttribute("data-task-completed"));
    // Entry is purged, so future toggles start clean.
    Assert.assertNull(renderer.optimisticTaskValueForTest("", "b+stuck"));
  }

  /**
   * Reflects into the renderer's optimistic-toggle map and rewrites the
   * deadline for the named entry to a long-past value. Used by the
   * TTL-expiration test above; isolated as a helper to keep the test
   * body focused on the assertion.
   */
  private static void forceExpireOptimisticTaskEntry(
      J2clReadSurfaceDomRenderer renderer, String waveId, String blipId) {
    try {
      Field field = J2clReadSurfaceDomRenderer.class.getDeclaredField("optimisticTaskState");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      java.util.Map<String, Object> map =
          (java.util.Map<String, Object>) field.get(renderer);
      String wave = waveId == null ? "" : waveId;
      String key = wave + "\u0001" + blipId;
      Object existing = map.get(key);
      Assert.assertNotNull("expected entry for key " + key, existing);
      Class<?> entryClass =
          Class.forName(
              "org.waveprotocol.box.j2cl.read.J2clReadSurfaceDomRenderer$OptimisticTaskEntry");
      Field doneField = entryClass.getDeclaredField("done");
      Field expiresField = entryClass.getDeclaredField("expiresAtMs");
      doneField.setAccessible(true);
      expiresField.setAccessible(true);
      boolean prevDone = doneField.getBoolean(existing);
      java.lang.reflect.Constructor<?> ctor =
          entryClass.getDeclaredConstructor(boolean.class, long.class);
      ctor.setAccessible(true);
      Object replacement = ctor.newInstance(prevDone, /* expiresAtMs= */ 0L);
      map.put(key, replacement);
    } catch (Exception e) {
      throw new AssertionError("force-expire failed", e);
    }
  }

  @Test
  public void collapsingWithoutFocusedBlipSanitizesHiddenTabStop() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    root.setAttribute("tabindex", "-1");
    reply.setAttribute("tabindex", "0");

    toggle.click();

    Assert.assertEquals("0", root.getAttribute("tabindex"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
  }

  @Test
  public void reEnhancementPrefersAlreadyMarkedSurfaceOverSiblingWaveContent() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\" data-j2cl-read-surface=\"true\">"
            + "<div class=\"blip\" data-blip-id=\"b+current\">Current</div>"
            + "</div>"
            + "<div class=\"wave-content\">"
            + "<div class=\"blip\" data-blip-id=\"b+sibling\">Sibling</div>"
            + "</div>";

    Assert.assertTrue(new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface());

    Assert.assertEquals(
        "true", blip(host, "b+current").getAttribute("data-j2cl-read-blip"));
    Assert.assertNull(blip(host, "b+sibling").getAttribute("data-j2cl-read-blip"));
  }

  @Test
  public void reEnhancementPreservesFocusedRovingTabStop() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    reply.focus();
    Assert.assertTrue(renderer.enhanceExistingSurface());

    Assert.assertEquals("-1", root.getAttribute("tabindex"));
    Assert.assertEquals("0", reply.getAttribute("tabindex"));
    Assert.assertEquals("true", reply.getAttribute("aria-current"));
    Assert.assertEquals("-1", after.getAttribute("tabindex"));
    Assert.assertEquals(1, host.querySelectorAll("[tabindex='0']").length);
  }

  @Test
  public void keyboardNavigationSkipsCollapsedThreadBlips() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    toggle.click();
    root.focus();
    dispatchKey(root, "ArrowDown");

    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertEquals("true", after.getAttribute("aria-current"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
  }

  @Test
  public void collapsingFocusedThreadMovesToNearestFollowingVisibleBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    reply.focus();
    toggle.click();

    Assert.assertEquals("-1", root.getAttribute("tabindex"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertEquals("true", after.getAttribute("aria-current"));
  }

  @Test
  public void collapsingFocusedLastThreadFallsBackToPreviousVisibleBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHostWithoutFollowingBlip();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");

    reply.focus();
    toggle.click();

    Assert.assertEquals("0", root.getAttribute("tabindex"));
    Assert.assertEquals("true", root.getAttribute("aria-current"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
  }

  @Test
  public void renderLiveBlipsCreatesSemanticReadSurface() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    boolean rendered =
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text"),
                    new J2clReadBlip("b+reply", "Reply text")),
                Collections.<String>emptyList());

    Assert.assertTrue(rendered);
    Assert.assertNotNull(host.querySelector("[data-j2cl-read-surface='true']"));
    Assert.assertEquals(2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);
    Assert.assertEquals("b+root", firstBlip(host).getAttribute("data-blip-id"));
    Assert.assertEquals("0", firstBlip(host).getAttribute("tabindex"));
    Assert.assertEquals(
        "ArrowUp ArrowDown Home End", firstBlip(host).getAttribute("aria-keyshortcuts"));
  }

  @Test
  public void renderWindowEntriesIncludeKeyboardReachableAttachmentControls() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.loaded(
                        "blip:b+root",
                        0L,
                        9L,
                        "b+root",
                        "Root text",
                        Arrays.asList(attachment)))));

    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");
    Assert.assertNotNull(tile);
    Assert.assertEquals("example.com/att+hero", tile.getAttribute("data-attachment-id"));
    Assert.assertEquals("medium", tile.getAttribute("data-display-size"));
    HTMLElement preview = (HTMLElement) tile.querySelector("img");
    Assert.assertNotNull(preview);
    Assert.assertEquals("/attachment/example.com/att+hero", preview.getAttribute("src"));
    Assert.assertEquals("medium", preview.getAttribute("data-display-size"));

    HTMLElement open =
        (HTMLElement) tile.querySelector("[data-j2cl-attachment-open='true']");
    HTMLElement download =
        (HTMLElement) tile.querySelector("[data-j2cl-attachment-download='true']");
    Assert.assertNotNull(open);
    Assert.assertNotNull(download);
    Assert.assertEquals("/attachment/example.com/att+hero", open.getAttribute("href"));
    Assert.assertEquals("0", open.getAttribute("tabindex"));
    Assert.assertEquals("noopener noreferrer", open.getAttribute("rel"));
    Assert.assertEquals("no-referrer", open.getAttribute("referrerpolicy"));
    Assert.assertNull(download.getAttribute("rel"));
    Assert.assertNull(download.getAttribute("referrerpolicy"));
    Assert.assertNull(download.getAttribute("target"));
    Assert.assertEquals("hero.png", download.getAttribute("download"));
    Assert.assertEquals("group", tile.getAttribute("role"));
    Assert.assertEquals(
        "true", tile.querySelector(".j2cl-read-attachment-label").getAttribute("aria-hidden"));
    Assert.assertEquals("lazy", tile.querySelector("img").getAttribute("loading"));
    Assert.assertEquals("Open attachment hero.png (image/png)", open.getAttribute("aria-label"));
    Assert.assertEquals(
        "Download attachment hero.png (image/png)", download.getAttribute("aria-label"));
  }

  @Test
  public void largeInlineImageUsesAttachmentUrlAndDataDisplaySize() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+large",
            "Large diagram",
            "large",
            attachmentMetadata(
                "example.com/att+large",
                "large.png",
                "image/png",
                "/attachment/example.com/att+large",
                "/thumbnail/example.com/att+large",
                new J2clAttachmentMetadata.ImageMetadata(2400, 1600),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment))),
                Collections.<String>emptyList()));

    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-attachment-id='example.com/att+large']");
    HTMLElement preview = (HTMLElement) tile.querySelector(".j2cl-read-attachment-preview");
    Assert.assertEquals("large", tile.getAttribute("data-display-size"));
    Assert.assertEquals("/attachment/example.com/att+large", preview.getAttribute("src"));
    Assert.assertEquals("large", preview.getAttribute("data-display-size"));
  }

  @Test
  public void renderWindowEntriesPropagatePerBlipMetadataIntoTheRenderedBlipElement() {
    // F-2 (#1037, R-3.1) — the viewport-window path is the dominant render
    // path. The window entry now carries author / timestamp / unread /
    // mention metadata sourced from the projector. This guards against a
    // regression where renderWindow rebuilt a 3-arg J2clReadBlip and
    // dropped the per-blip header data on the floor.
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.loadedWithMetadata(
                        "blip:b+root",
                        0L,
                        9L,
                        "b+root",
                        "Root text",
                        Collections.<J2clAttachmentRenderModel>emptyList(),
                        "alice@example.com",
                        "Alice",
                        1700000000000L,
                        /* parentBlipId= */ "",
                        /* threadId= */ "t+root",
                        /* unread= */ true,
                        /* hasMention= */ true))));

    HTMLElement root = blip(host, "b+root");
    Assert.assertNotNull(root);
    Assert.assertEquals("alice@example.com", root.getAttribute("author-id"));
    Assert.assertEquals("Alice", root.getAttribute("author-name"));
    Assert.assertNotNull(root.getAttribute("posted-at"));
    Assert.assertNotNull(root.getAttribute("posted-at-iso"));
    Assert.assertTrue(root.hasAttribute("unread"));
    Assert.assertTrue(root.hasAttribute("has-mention"));
  }

  @Test
  public void renderBlipTextPaintsMentionRangesAsStableReadChips() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setMentionBinder(
        blipId ->
            "b+root".equals(blipId)
                ? Arrays.asList(new J2clMentionRange(6, 9, "alice@example.com", "@Al"))
                : Collections.<J2clMentionRange>emptyList());

    renderer.render(
        Arrays.asList(
            new J2clReadBlip(
                "b+root",
                "Hello @Al",
                Collections.<J2clAttachmentRenderModel>emptyList(),
                "alice@example.com",
                "Alice",
                1700000000000L,
                "",
                "t+root",
                false,
                true)),
        Collections.<String>emptyList());

    HTMLElement blip = blip(host, "b+root");
    HTMLElement content = (HTMLElement) blip.querySelector(".j2cl-read-blip-content");
    HTMLElement mention = (HTMLElement) blip.querySelector("[data-j2cl-read-mention='true']");

    Assert.assertNotNull(content);
    Assert.assertEquals("Hello @Al", content.textContent);
    Assert.assertEquals("true", content.getAttribute("data-has-rendered-mentions"));
    Assert.assertNotNull(mention);
    Assert.assertEquals("@Al", mention.textContent);
    Assert.assertEquals("alice@example.com", mention.getAttribute("data-mention-address"));
    Assert.assertEquals("6", mention.getAttribute("data-mention-start"));
    Assert.assertEquals("9", mention.getAttribute("data-mention-end"));
    Assert.assertNull("Read mention chips are not interactive until navigation is wired", mention.getAttribute("role"));
    Assert.assertNull(
        "Read mention chips are not keyboard-focusable until navigation is wired",
        mention.getAttribute("tabindex"));
  }

  @Test
  public void renderRepaintsWhenMentionBinderChangesWithoutBlipContentChange() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadBlip> blips = Arrays.asList(new J2clReadBlip("b+root", "Hello @Al"));

    renderer.setMentionBinder(null);
    renderer.render(blips, Collections.<String>emptyList());
    Assert.assertEquals(0, host.querySelectorAll("[data-j2cl-read-mention='true']").length);

    renderer.setMentionBinder(
        blipId ->
            "b+root".equals(blipId)
                ? Arrays.asList(new J2clMentionRange(6, 9, "alice@example.com", "@Al"))
                : Collections.<J2clMentionRange>emptyList());
    renderer.render(blips, Collections.<String>emptyList());

    HTMLElement mention = (HTMLElement) host.querySelector("[data-j2cl-read-mention='true']");
    Assert.assertNotNull(
        "Mention binder changes must invalidate the same-blip fast path", mention);
    Assert.assertEquals("alice@example.com", mention.getAttribute("data-mention-address"));
  }

  @Test
  public void renderWindowRepaintsWhenMentionBinderChangesWithoutEntryChange() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadWindowEntry> entries =
        Arrays.asList(J2clReadWindowEntry.loaded("blip:b+root", 0L, 1L, "b+root", "Hello @Al"));

    renderer.setMentionBinder(null);
    renderer.renderWindow(entries);
    Assert.assertEquals(0, host.querySelectorAll("[data-j2cl-read-mention='true']").length);

    renderer.setMentionBinder(
        blipId ->
            "b+root".equals(blipId)
                ? Arrays.asList(new J2clMentionRange(6, 9, "alice@example.com", "@Al"))
                : Collections.<J2clMentionRange>emptyList());
    renderer.renderWindow(entries);

    HTMLElement mention = (HTMLElement) host.querySelector("[data-j2cl-read-mention='true']");
    Assert.assertNotNull(
        "Mention binder changes must invalidate the same-window fast path", mention);
    Assert.assertEquals("alice@example.com", mention.getAttribute("data-mention-address"));
  }

  @Test
  public void renderBlipTextSortsMentionRangesBeforePaintingReadChips() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setMentionBinder(
        blipId ->
            "b+root".equals(blipId)
                ? Arrays.asList(
                    new J2clMentionRange(14, 18, "bob@example.com", "@Bob"),
                    new J2clMentionRange(3, 9, "alice@example.com", "@Alice"))
                : Collections.<J2clMentionRange>emptyList());

    renderer.render(
        Arrays.asList(new J2clReadBlip("b+root", "Hi @Alice and @Bob")),
        Collections.<String>emptyList());

    NodeList<Element> mentions = host.querySelectorAll("[data-j2cl-read-mention='true']");
    Assert.assertEquals(
        "Both mentions must render even when binder ranges are unordered", 2, mentions.length);
    Assert.assertEquals("@Alice", mentions.getAt(0).textContent);
    Assert.assertEquals("@Bob", mentions.getAt(1).textContent);
  }

  @Test
  public void openAndDownloadLinksEmitClickTelemetry() {
    assumeBrowserDom();
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "large",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host, telemetry)
            .render(
                Arrays.asList(new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment))),
                Collections.<String>emptyList()));

    ((HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']")).click();
    J2clClientTelemetry.Event openEvent = telemetry.lastEvent();
    Assert.assertEquals("attachment.open.clicked", openEvent.getName());
    Assert.assertEquals("read-surface", openEvent.getFields().get("source"));
    Assert.assertEquals("large", openEvent.getFields().get("displaySize"));

    ((HTMLElement) host.querySelector("[data-j2cl-attachment-download='true']")).click();
    J2clClientTelemetry.Event downloadEvent = telemetry.lastEvent();
    Assert.assertEquals("attachment.download.clicked", downloadEvent.getName());
    Assert.assertEquals("read-surface", downloadEvent.getFields().get("source"));
    Assert.assertEquals("large", downloadEvent.getFields().get("displaySize"));
    Assert.assertEquals(2, telemetry.events().size());
  }

  @Test
  public void throwingTelemetrySinkDoesNotPreventAttachmentLinkClick() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(
                host,
                event -> {
                  throw new RuntimeException("telemetry boom");
                })
            .render(
                Arrays.asList(new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment))),
                Collections.<String>emptyList()));

    ((HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']")).click();
    ((HTMLElement) host.querySelector("[data-j2cl-attachment-download='true']")).click();
  }

  @Test
  public void crossOriginDownloadLinksOpenSafelyWhenDownloadAttributeIsIgnored() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+cdn",
            "CDN image",
            "medium",
            attachmentMetadata(
                "example.com/att+cdn",
                "cdn.png",
                "image/png",
                "https://cdn.example.test/attachment/cdn.png",
                "https://cdn.example.test/thumbnail/cdn.png",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip(
                        "b+root", "Root text", Arrays.asList(attachment))),
                Collections.<String>emptyList()));

    HTMLElement download =
        (HTMLElement) host.querySelector("[data-j2cl-attachment-download='true']");
    Assert.assertEquals("cdn.png", download.getAttribute("download"));
    Assert.assertEquals("_blank", download.getAttribute("target"));
    Assert.assertEquals("noopener noreferrer", download.getAttribute("rel"));
    Assert.assertEquals("no-referrer", download.getAttribute("referrerpolicy"));
  }

  @Test
  public void rerenderingSameAttachmentBlipsPreservesRenderedNodes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));
    List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment)));

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));
    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));

    Assert.assertSame(tile, host.querySelector("[data-j2cl-read-attachment='true']"));
  }

  @Test
  public void rerenderingChangedAttachmentBlipsReplacesRenderedNodes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clAttachmentRenderModel firstAttachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+first",
            "First",
            "medium",
            attachmentMetadata(
                "example.com/att+first",
                "first.png",
                "image/png",
                "/attachment/example.com/att+first",
                "/thumbnail/example.com/att+first",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));
    J2clAttachmentRenderModel secondAttachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+second",
            "Second",
            "medium",
            attachmentMetadata(
                "example.com/att+second",
                "second.png",
                "image/png",
                "/attachment/example.com/att+second",
                "/thumbnail/example.com/att+second",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root text", Arrays.asList(firstAttachment))),
            Collections.<String>emptyList()));
    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");
    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root text", Arrays.asList(secondAttachment))),
            Collections.<String>emptyList()));

    Assert.assertNotSame(tile, host.querySelector("[data-j2cl-read-attachment='true']"));
    Assert.assertNotNull(host.querySelector("[data-attachment-id='example.com/att+second']"));
  }

  @Test
  public void renderLiveBlipsSurfacesBlockedAttachmentStateWithoutControls() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+blocked",
            "Blocked",
            "small",
            attachmentMetadata(
                "example.com/att+blocked",
                "blocked.exe",
                "application/octet-stream",
                "/attachment/example.com/att+blocked",
                "",
                null,
                true));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip(
                        "b+root", "Root text", Arrays.asList(attachment))),
                Collections.<String>emptyList()));

    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");
    Assert.assertNotNull(tile);
    Assert.assertEquals("blocked", tile.getAttribute("data-attachment-state"));
    Assert.assertNull(tile.querySelector("[data-j2cl-attachment-open='true']"));
    Assert.assertTrue(
        ((HTMLElement) tile.querySelector(".j2cl-read-attachment-status"))
            .textContent
            .contains("blocked"));
    Assert.assertEquals(
        "alert",
        ((HTMLElement) tile.querySelector(".j2cl-read-attachment-status")).getAttribute("role"));
  }

  @Test
  public void renderLiveBlipsSurfacesPendingAndFailureAttachmentStatesWithoutControls() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel pending =
        J2clAttachmentRenderModel.metadataPending(
            "example.com/att+pending", "Pending", "medium");
    J2clAttachmentRenderModel failure =
        J2clAttachmentRenderModel.metadataFailure(
            "example.com/att+failure", "Failure", "medium", "metadata endpoint failed");

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip(
                        "b+root", "Root text", Arrays.asList(pending, failure))),
                Collections.<String>emptyList()));

    HTMLElement pendingTile =
        (HTMLElement) host.querySelector("[data-attachment-id='example.com/att+pending']");
    HTMLElement failureTile =
        (HTMLElement) host.querySelector("[data-attachment-id='example.com/att+failure']");
    Assert.assertEquals("pending", pendingTile.getAttribute("data-attachment-state"));
    Assert.assertEquals("true", pendingTile.getAttribute("aria-busy"));
    Assert.assertEquals(
        "Attachment metadata loading...",
        ((HTMLElement) pendingTile.querySelector(".j2cl-read-attachment-status")).textContent);
    Assert.assertNull(pendingTile.querySelector("[data-j2cl-attachment-open='true']"));
    Assert.assertNull(pendingTile.querySelector(".j2cl-read-attachment-preview"));
    Assert.assertEquals("metadata-failure", failureTile.getAttribute("data-attachment-state"));
    Assert.assertEquals(
        "alert",
        ((HTMLElement) failureTile.querySelector(".j2cl-read-attachment-status"))
            .getAttribute("role"));
    Assert.assertNull(failureTile.querySelector("[data-j2cl-attachment-open='true']"));
  }

  @Test
  public void cardStyleAttachmentWithThumbnailRendersImgPreview() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    // Small display size with an image mime type → card style (not inline), but sourceUrl is set
    // to the thumbnail so the preview img must be rendered with src pointing to it.
    J2clAttachmentRenderModel card =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+pdf",
            "Report",
            "small",
            attachmentMetadata(
                "example.com/att+pdf",
                "report.pdf",
                "application/pdf",
                "/attachment/example.com/att+pdf",
                "/thumbnail/example.com/att+pdf",
                null,
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text", Arrays.asList(card))),
                Collections.<String>emptyList()));

    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-attachment-id='example.com/att+pdf']");
    Assert.assertNotNull(tile);
    Assert.assertEquals("j2cl-read-attachment j2cl-read-attachment-card", tile.className);
    HTMLElement preview = (HTMLElement) tile.querySelector(".j2cl-read-attachment-preview");
    Assert.assertNotNull(preview);
    Assert.assertEquals("IMG", preview.tagName);
    Assert.assertEquals("/thumbnail/example.com/att+pdf", preview.getAttribute("src"));
    Assert.assertEquals("no-referrer", preview.getAttribute("referrerpolicy"));
    Assert.assertEquals("true", preview.getAttribute("aria-hidden"));
    Assert.assertEquals("", preview.getAttribute("alt"));
  }

  @Test
  public void focusedBlipReceivesCurrentMarker() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(new J2clReadBlip("b+root", "Root text")),
                Collections.<String>emptyList()));

    HTMLElement root = blip(host, "b+root");
    root.focus();

    Assert.assertEquals("true", root.getAttribute("aria-current"));
    Assert.assertEquals("0", root.getAttribute("tabindex"));
  }

  @Test
  public void renderLiveBlipsWiresKeyboardTraversal() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text"),
                    new J2clReadBlip("b+reply", "Reply text")),
                Collections.<String>emptyList()));

    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    root.focus();
    dispatchKey(root, "ArrowDown");

    Assert.assertEquals("-1", root.getAttribute("tabindex"));
    Assert.assertEquals("0", reply.getAttribute("tabindex"));
    Assert.assertEquals("true", reply.getAttribute("aria-current"));
  }

  @Test
  public void rerenderingSameLiveBlipsPreservesFocusedNode() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+root", "Root text"),
            new J2clReadBlip("b+reply", "Reply text"));

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));
    HTMLElement reply = blip(host, "b+reply");
    reply.focus();

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));

    Assert.assertSame(reply, blip(host, "b+reply"));
    Assert.assertEquals(reply, DomGlobal.document.activeElement);
    Assert.assertEquals("0", reply.getAttribute("tabindex"));
    Assert.assertEquals("true", reply.getAttribute("aria-current"));
  }

  @Test
  public void rerenderingUpdatedLiveBlipsRestoresFocusByBlipId() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root text"),
                new J2clReadBlip("b+reply", "Reply text")),
            Collections.<String>emptyList()));
    HTMLElement originalReply = blip(host, "b+reply");
    originalReply.focus();

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root text updated"),
                new J2clReadBlip("b+reply", "Reply text updated")),
            Collections.<String>emptyList()));

    HTMLElement updatedReply = blip(host, "b+reply");
    Assert.assertNotSame(originalReply, updatedReply);
    Assert.assertEquals(updatedReply, DomGlobal.document.activeElement);
    Assert.assertEquals("0", updatedReply.getAttribute("tabindex"));
    Assert.assertEquals("true", updatedReply.getAttribute("aria-current"));
  }

  @Test
  public void rerenderingWithExternalFocusDoesNotStealFocusBack() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root text"),
                new J2clReadBlip("b+reply", "Reply text")),
            Collections.<String>emptyList()));
    HTMLElement reply = blip(host, "b+reply");
    reply.focus();

    HTMLButtonElement external =
        (HTMLButtonElement) DomGlobal.document.createElement("button");
    DomGlobal.document.body.appendChild(external);
    try {
      external.focus();
      Assert.assertEquals(external, DomGlobal.document.activeElement);

      Assert.assertTrue(
          renderer.render(
              Arrays.asList(
                  new J2clReadBlip("b+root", "Root text updated"),
                  new J2clReadBlip("b+reply", "Reply text updated")),
              Collections.<String>emptyList()));

      Assert.assertEquals(external, DomGlobal.document.activeElement);
      Assert.assertNull(blip(host, "b+reply").getAttribute("aria-current"));
    } finally {
      if (external.parentElement != null) {
        external.parentElement.removeChild(external);
      }
    }
  }

  @Test
  public void renderLiveBlipsHandlesKeyboardBranchesAndBounds() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text"),
                    new J2clReadBlip("b+middle", "Middle text"),
                    new J2clReadBlip("b+last", "Last text")),
                Collections.<String>emptyList()));

    HTMLElement root = blip(host, "b+root");
    HTMLElement middle = blip(host, "b+middle");
    HTMLElement last = blip(host, "b+last");

    last.focus();
    dispatchKey(last, "ArrowDown");
    Assert.assertEquals("0", last.getAttribute("tabindex"));

    dispatchKey(last, "Home");
    Assert.assertEquals("0", root.getAttribute("tabindex"));

    dispatchKey(root, "ArrowUp");
    Assert.assertEquals("0", root.getAttribute("tabindex"));

    dispatchKey(root, "End");
    Assert.assertEquals("0", last.getAttribute("tabindex"));

    dispatchKey(last, "ArrowUp");
    Assert.assertEquals("0", middle.getAttribute("tabindex"));
  }

  @Test
  public void renderAfterEnhancementRebuildsAndWiresFreshLiveBlips() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    Assert.assertTrue(
        renderer.render(
            Arrays.asList(
                new J2clReadBlip("b+live-root", "Root text"),
                new J2clReadBlip("b+live-reply", "Reply text")),
            Collections.<String>emptyList()));

    Assert.assertNull(blip(host, "b+reply"));
    HTMLElement liveRoot = blip(host, "b+live-root");
    HTMLElement liveReply = blip(host, "b+live-reply");

    liveRoot.focus();
    dispatchKey(liveRoot, "ArrowDown");

    Assert.assertEquals("-1", liveRoot.getAttribute("tabindex"));
    Assert.assertEquals("0", liveReply.getAttribute("tabindex"));
    Assert.assertEquals("true", liveReply.getAttribute("aria-current"));
  }

  @Test
  public void rerenderPreservesFocusedBlipAndTabStop() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    renderer.render(
        Arrays.asList(
            new J2clReadBlip("b+root", "Root text"),
            new J2clReadBlip("b+reply", "Reply text")),
        Collections.<String>emptyList());

    HTMLElement reply = blip(host, "b+reply");
    reply.focus();
    Assert.assertEquals("true", reply.getAttribute("aria-current"));

    // Re-render with the same blip IDs (simulating a read-state-only reprojet).
    renderer.render(
        Arrays.asList(
            new J2clReadBlip("b+root", "Root text updated"),
            new J2clReadBlip("b+reply", "Reply text updated")),
        Collections.<String>emptyList());

    HTMLElement restoredReply = blip(host, "b+reply");
    Assert.assertNotNull(restoredReply);
    Assert.assertEquals("true", restoredReply.getAttribute("aria-current"));
    Assert.assertEquals("0", restoredReply.getAttribute("tabindex"));
    Assert.assertEquals("-1", blip(host, "b+root").getAttribute("tabindex"));
  }

  @Test
  public void renderFallbackEntriesSynthesizesStableEntryIds() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    boolean rendered =
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Collections.<J2clReadBlip>emptyList(),
                Arrays.asList("First fallback", "Second fallback"));

    Assert.assertTrue(rendered);
    Assert.assertEquals(2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);
    Assert.assertEquals("entry-1", firstBlip(host).getAttribute("data-blip-id"));
  }

  @Test
  public void renderWindowEntriesKeepsPlaceholderMetadataInOrder() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    boolean rendered =
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.loaded(
                        "blip:b+root", 30L, 36L, "b+root", "Root text"),
                    J2clReadWindowEntry.placeholder(
                        "blip:b+missing", 36L, 40L, "b+missing")));

    Assert.assertTrue(rendered);
    Assert.assertEquals("b+root", firstBlip(host).getAttribute("data-blip-id"));
    HTMLElement placeholder =
        (HTMLElement) host.querySelector("[data-j2cl-viewport-placeholder='true']");
    Assert.assertNotNull(placeholder);
    Assert.assertEquals("blip:b+missing", placeholder.getAttribute("data-segment"));
    Assert.assertEquals("36", placeholder.getAttribute("data-range-from"));
    Assert.assertEquals("40", placeholder.getAttribute("data-range-to"));
    Assert.assertEquals("b+missing", placeholder.getAttribute("data-placeholder-blip-id"));
    Assert.assertEquals(
        firstBlip(host).nextSibling,
        placeholder);
  }

  @Test
  public void renderWindowEntriesFollowManifestDfsOrderWhenFragmentsArriveOutOfOrder() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setConversationManifest(
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+second", "b+root", "t+first", 1, 0),
                new SidecarConversationManifest.Entry("b+nested", "b+second", "t+nested", 2, 0),
                new SidecarConversationManifest.Entry("b+third", "b+root", "t+second", 1, 1))));

    boolean rendered =
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root"),
                J2clReadWindowEntry.loaded("blip:b+second", 30L, 36L, "b+second", "Second"),
                J2clReadWindowEntry.loaded("blip:b+third", 30L, 36L, "b+third", "Third"),
                J2clReadWindowEntry.loaded("blip:b+nested", 30L, 36L, "b+nested", "Nested")));

    Assert.assertTrue(rendered);
    Assert.assertEquals(
        Arrays.asList("b+root", "b+second", "b+nested", "b+third"),
        blipIds(host));
  }

  @Test
  public void renderWindowReordersExistingFlatWindowWhenManifestArrivesLater() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadWindowEntry> entries =
        Arrays.asList(
            J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root"),
            J2clReadWindowEntry.loaded("blip:b+second", 30L, 36L, "b+second", "Second"),
            J2clReadWindowEntry.loaded("blip:b+third", 30L, 36L, "b+third", "Third"),
            J2clReadWindowEntry.loaded("blip:b+nested", 30L, 36L, "b+nested", "Nested"));

    Assert.assertTrue(renderer.renderWindow(entries));
    Assert.assertEquals(
        Arrays.asList("b+root", "b+second", "b+third", "b+nested"),
        blipIds(host));

    renderer.setConversationManifest(
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+second", "b+root", "t+first", 1, 0),
                new SidecarConversationManifest.Entry("b+nested", "b+second", "t+nested", 2, 0),
                new SidecarConversationManifest.Entry("b+third", "b+root", "t+second", 1, 1))));

    Assert.assertTrue(renderer.renderWindow(entries));
    Assert.assertEquals(
        Arrays.asList("b+root", "b+second", "b+nested", "b+third"),
        blipIds(host));
    Assert.assertEquals("reply", blip(host, "b+nested").getAttribute("data-blip-depth"));
  }

  @Test
  public void rerenderingSameWindowEntriesPreservesFocusedNode() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    List<J2clReadWindowEntry> entries =
        Arrays.asList(
            J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
            J2clReadWindowEntry.placeholder("blip:b+missing", 36L, 40L, "b+missing"));

    Assert.assertTrue(renderer.renderWindow(entries));
    HTMLElement root = blip(host, "b+root");
    root.focus();

    Assert.assertTrue(renderer.renderWindow(entries));

    Assert.assertSame(root, blip(host, "b+root"));
    Assert.assertEquals(root, DomGlobal.document.activeElement);
    Assert.assertEquals("0", root.getAttribute("tabindex"));
    Assert.assertEquals("true", root.getAttribute("aria-current"));
  }

  @Test
  public void renderWindowPlaceholderUpgradeToLoadedAttachmentReplacesRenderedNodes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder("blip:b+reply", 36L, 40L, "b+reply"))));
    HTMLElement root = blip(host, "b+root");
    root.focus();

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+reply",
                    36L,
                    40L,
                    "b+reply",
                    "Reply text",
                    Arrays.asList(attachment)))));

    Assert.assertNotSame(root, blip(host, "b+root"));
    Assert.assertNotNull(host.querySelector("[data-attachment-id='example.com/att+hero']"));
    Assert.assertEquals(blip(host, "b+root"), DomGlobal.document.activeElement);
  }

  @Test
  public void renderWindowMixedPlaceholderAndAttachmentMarksSurfaceLiveAndBusy() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.loaded(
                        "blip:b+root",
                        30L,
                        36L,
                        "b+root",
                        "Root text",
                        Arrays.asList(attachment)),
                    J2clReadWindowEntry.placeholder(
                        "blip:b+missing", 36L, 40L, "b+missing"))));

    Assert.assertEquals(
        "polite", host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));
    Assert.assertEquals(
        "true", host.querySelector("[data-thread-id='root']").getAttribute("aria-busy"));
    Assert.assertNotNull(host.querySelector("[data-attachment-id='example.com/att+hero']"));
    Assert.assertNotNull(host.querySelector("[data-j2cl-viewport-placeholder='true']"));
  }

  @Test
  public void rerenderingSameWindowEntryAttachmentsPreservesRenderedNodes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));
    List<J2clReadWindowEntry> entries =
        Arrays.asList(
            J2clReadWindowEntry.loaded(
                "blip:b+root",
                30L,
                36L,
                "b+root",
                "Root text",
                Arrays.asList(attachment)));

    Assert.assertTrue(renderer.renderWindow(entries));
    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");

    Assert.assertTrue(renderer.renderWindow(entries));

    Assert.assertSame(tile, host.querySelector("[data-j2cl-read-attachment='true']"));
  }

  @Test
  public void rerenderingChangedWindowEntryAttachmentsReplacesRenderedNodes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clAttachmentRenderModel firstAttachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+first",
            "First",
            "medium",
            attachmentMetadata(
                "example.com/att+first",
                "first.png",
                "image/png",
                "/attachment/example.com/att+first",
                "/thumbnail/example.com/att+first",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));
    J2clAttachmentRenderModel secondAttachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+second",
            "Second",
            "medium",
            attachmentMetadata(
                "example.com/att+second",
                "second.png",
                "image/png",
                "/attachment/example.com/att+second",
                "/thumbnail/example.com/att+second",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root",
                    30L,
                    36L,
                    "b+root",
                    "Root text",
                    Arrays.asList(firstAttachment)))));
    HTMLElement tile =
        (HTMLElement) host.querySelector("[data-j2cl-read-attachment='true']");

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root",
                    30L,
                    36L,
                    "b+root",
                    "Root text",
                    Arrays.asList(secondAttachment)))));

    Assert.assertNotSame(tile, host.querySelector("[data-j2cl-read-attachment='true']"));
    Assert.assertNotNull(host.querySelector("[data-attachment-id='example.com/att+second']"));
  }

  @Test
  public void renderWindowPlaceholderOmitsUnknownRangeAttributes() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.placeholder(
                        "blip:b+pending", -1L, -1L, "b+pending"))));

    HTMLElement placeholder =
        (HTMLElement) host.querySelector("[data-j2cl-viewport-placeholder='true']");
    Assert.assertNotNull(placeholder);
    Assert.assertNull(placeholder.getAttribute("data-range-from"));
    Assert.assertNull(placeholder.getAttribute("data-range-to"));
    Assert.assertEquals(
        "polite", host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));
  }

  @Test
  public void renderWindowPlaceholderCanCarryOneSidedRangeMetadata() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.placeholder(
                        "blip:b+pending", 12L, -1L, "b+pending"))));

    HTMLElement placeholder =
        (HTMLElement) host.querySelector("[data-j2cl-viewport-placeholder='true']");
    Assert.assertNotNull(placeholder);
    Assert.assertEquals("12", placeholder.getAttribute("data-range-from"));
    Assert.assertNull(placeholder.getAttribute("data-range-to"));
  }

  @Test
  public void renderWindowRequestsForwardGrowthWhenScrolledToTrailingPlaceholder() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 40px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));

    host.scrollTop = 100;
    host.dispatchEvent(new Event("scroll"));

    Assert.assertEquals("b+missing", requested[0]);
    Assert.assertEquals("forward", requested[1]);
  }

  @Test
  public void renderWindowRequestsBackwardGrowthWhenScrolledToLeadingPlaceholder() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 40px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.placeholder(
                    "blip:b+before", 24L, 30L, "b+before"),
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"))));

    host.scrollTop = 0;
    host.dispatchEvent(new Event("scroll"));

    Assert.assertEquals("b+before", requested[0]);
    Assert.assertEquals("backward", requested[1]);
  }

  @Test
  public void renderWindowAutoRequestsForwardGrowthWhenTrailingPlaceholderAlreadyVisible() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 400px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));

    Assert.assertEquals("b+missing", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.FORWARD, requested[1]);
  }

  @Test
  public void renderWindowAutoRequestsForwardGrowthWhenInteriorPlaceholderAlreadyVisible() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 400px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 40L, 44L, "b+tail", "Tail text"))));

    Assert.assertEquals("b+missing", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.FORWARD, requested[1]);
  }

  @Test
  public void renderWindowFallsThroughFromLoadedPendingEdgeToVisibleInteriorPlaceholder()
      throws Exception {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 400px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });
    setLastScrollDirection(renderer, J2clViewportGrowthDirection.BACKWARD);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 40L, 44L, "b+tail", "Tail text"))));

    Assert.assertEquals("b+missing", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.FORWARD, requested[1]);
  }

  @Test
  public void renderWindowAutoRequestsBackwardGrowthWhenLeadingPlaceholderAlreadyVisible() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 400px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.placeholder(
                    "blip:b+before", 24L, 30L, "b+before"),
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"))));

    Assert.assertEquals("b+before", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.BACKWARD, requested[1]);
  }

  @Test
  public void renderWindowRequestsBackwardGrowthForVisibleLeadingPlaceholderAwayFromEdge() {
    assumeBrowserDom();
    currentStyle = (HTMLElement) DomGlobal.document.createElement("style");
    currentStyle.textContent =
        ".j2cl-read-blip{display:block;height:40px;}"
            + ".j2cl-read-viewport-placeholder{display:block;height:160px;}";
    DomGlobal.document.head.appendChild(currentStyle);
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 200px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.placeholder(
                    "blip:b+before", 24L, 30L, "b+before"),
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 36L, 40L, "b+tail", "Tail text"))));

    requested[0] = null;
    requested[1] = null;
    host.scrollTop = 80;
    host.dispatchEvent(new Event("scroll"));

    Assert.assertEquals("b+before", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.BACKWARD, requested[1]);
  }

  @Test
  public void renderWindowSkipsPendingGrowthReplayWhenListenerIsCleared() throws Exception {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final int[] requestCount = new int[1];
    renderer.setViewportEdgeListener((anchorBlipId, direction) -> requestCount[0]++);
    setLastScrollDirection(renderer, J2clViewportGrowthDirection.FORWARD);
    renderer.setViewportEdgeListener(null);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));

    Assert.assertEquals(0, requestCount[0]);
  }

  @Test
  public void renderWindowEmptyEntriesClearPendingViewportEdgeMemory() throws Exception {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    setLastScrollDirection(renderer, J2clViewportGrowthDirection.FORWARD);

    Assert.assertFalse(renderer.renderWindow(Collections.<J2clReadWindowEntry>emptyList()));

    Assert.assertNull(getLastScrollDirection(renderer));
  }

  @Test
  public void renderWindowDoesNotRequestGrowthWhenScrolledToLoadedEdges() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final int[] requestCount = new int[1];
    renderer.setViewportEdgeListener((anchorBlipId, direction) -> requestCount[0]++);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 36L, 40L, "b+tail", "Tail text"))));

    host.scrollTop = 0;
    host.dispatchEvent(new Event("scroll"));
    host.scrollTop = 100;
    host.dispatchEvent(new Event("scroll"));

    Assert.assertEquals(0, requestCount[0]);
  }

  @Test
  public void flatRenderNoOpPreservesPendingViewportEdgeMemory() throws Exception {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final String[] requested = new String[2];
    renderer.setViewportEdgeListener(
        (anchorBlipId, direction) -> {
          requested[0] = anchorBlipId;
          requested[1] = direction;
        });
    List<J2clReadBlip> blips = Arrays.asList(new J2clReadBlip("b+root", "Root text"));

    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));
    setLastScrollDirection(renderer, J2clViewportGrowthDirection.FORWARD);
    Assert.assertTrue(renderer.render(blips, Collections.<String>emptyList()));
    host.scrollTop = 100;
    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));

    Assert.assertEquals("b+missing", requested[0]);
    Assert.assertEquals(J2clViewportGrowthDirection.FORWARD, requested[1]);
  }

  @Test
  public void renderWindowPostRenderGrowthRequestIsOneShot() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    final int[] requestCount = new int[1];
    renderer.setViewportEdgeListener((anchorBlipId, direction) -> requestCount[0]++);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));
    host.scrollTop = 100;
    host.dispatchEvent(new Event("scroll"));

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text updated once"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));
    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text updated twice"),
                J2clReadWindowEntry.placeholder(
                    "blip:b+missing", 36L, 40L, "b+missing"))));

    Assert.assertEquals(3, requestCount[0]);
  }

  @Test
  public void backwardGrowthPreservesScrollAnchorAcrossPrependRebuild() {
    assumeBrowserDom();
    installFixedBlipLayout();
    HTMLDivElement host = createHost();
    host.setAttribute("style", "height: 80px; overflow-y: auto;");
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 36L, 40L, "b+tail", "Tail text"))));
    host.scrollTop = 0;

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+before", 24L, 30L, "b+before", "Before text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+tail", 36L, 40L, "b+tail", "Tail text"))));

    Assert.assertTrue(host.scrollTop > 0);
  }

  @Test
  public void renderWindowAllPlaceholdersKeepsSurfaceBusyWithoutTabStops() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertTrue(
        new J2clReadSurfaceDomRenderer(host)
            .renderWindow(
                Arrays.asList(
                    J2clReadWindowEntry.placeholder(
                        "blip:b+before", 10L, 12L, "b+before"),
                    J2clReadWindowEntry.placeholder(
                        "blip:b+after", 12L, 14L, "b+after"))));

    Assert.assertNull(firstBlip(host));
    Assert.assertEquals(2, host.querySelectorAll("[data-j2cl-viewport-placeholder='true']").length);
    Assert.assertEquals(
        "true", host.querySelector("[data-thread-id='root']").getAttribute("aria-busy"));
    Assert.assertEquals(
        0, host.querySelectorAll("[data-j2cl-viewport-placeholder='true'][tabindex]").length);
  }

  @Test
  public void renderWindowAndClassicRenderTransitionsClearStaleState() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder("blip:b+missing", 36L, 40L, "b+missing"))));
    Assert.assertNotNull(host.querySelector("[data-j2cl-viewport-placeholder='true']"));

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(new J2clReadBlip("b+root", "Root text updated")),
            Collections.<String>emptyList()));
    Assert.assertNull(host.querySelector("[data-j2cl-viewport-placeholder='true']"));
    Assert.assertEquals("Root text updated", renderedText(blip(host, "b+root")));

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded(
                    "blip:b+root", 30L, 36L, "b+root", "Root text window"))));
    Assert.assertEquals("Root text window", renderedText(blip(host, "b+root")));
  }

  @Test
  public void allPlaceholderWindowThenClassicRenderClearsBusySurfaceState() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.placeholder("blip:b+before", 10L, 12L, "b+before"),
                J2clReadWindowEntry.placeholder("blip:b+after", 12L, 14L, "b+after"))));
    Assert.assertNotNull(host.querySelector("[data-j2cl-viewport-placeholder='true']"));

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(new J2clReadBlip("b+root", "Root text")),
            Collections.<String>emptyList()));

    Assert.assertNull(host.querySelector("[data-j2cl-viewport-placeholder='true']"));
    Assert.assertNull(host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));
    Assert.assertNull(host.querySelector("[data-thread-id='root']").getAttribute("aria-busy"));
    Assert.assertEquals("Root text", renderedText(blip(host, "b+root")));
  }

  @Test
  public void classicRenderAfterWindowNoOpClearsViewportSurfaceState() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"))));
    HTMLElement windowRoot = blip(host, "b+root");
    Assert.assertNull(host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));

    Assert.assertTrue(
        renderer.render(
            Arrays.asList(new J2clReadBlip("b+root", "Root text")),
            Collections.<String>emptyList()));

    HTMLElement classicRoot = blip(host, "b+root");
    Assert.assertNotSame(windowRoot, classicRoot);
    Assert.assertNull(host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));
    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"))));
    Assert.assertNotSame(classicRoot, blip(host, "b+root"));
    Assert.assertNull(host.querySelector("[data-j2cl-read-surface]").getAttribute("aria-live"));
  }

  @Test
  public void renderWindowPlaceholderUpgradePreservesFocusedLoadedBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.placeholder("blip:b+reply", 36L, 40L, "b+reply"))));
    HTMLElement root = blip(host, "b+root");
    root.focus();

    Assert.assertTrue(
        renderer.renderWindow(
            Arrays.asList(
                J2clReadWindowEntry.loaded("blip:b+root", 30L, 36L, "b+root", "Root text"),
                J2clReadWindowEntry.loaded(
                    "blip:b+reply", 36L, 40L, "b+reply", "Reply text"))));

    HTMLElement restoredRoot = blip(host, "b+root");
    Assert.assertEquals(restoredRoot, DomGlobal.document.activeElement);
    Assert.assertEquals("true", restoredRoot.getAttribute("aria-current"));
    Assert.assertEquals("Reply text", renderedText(blip(host, "b+reply")));
  }

  @Test
  public void renderEmptyContentReturnsFalseAndLeavesHostEmpty() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    Assert.assertFalse(
        new J2clReadSurfaceDomRenderer(host)
            .render(Collections.<J2clReadBlip>emptyList(), Collections.<String>emptyList()));
    Assert.assertEquals("", host.innerHTML);
  }

  @Test
  public void duplicateThreadIdsStillGenerateDistinctControlTargets() {
    assumeBrowserDom();
    HTMLDivElement host = createDuplicateThreadIdHost();

    Assert.assertTrue(new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface());

    HTMLButtonElement first =
        (HTMLButtonElement) host.querySelectorAll(".j2cl-read-thread-toggle").item(0);
    HTMLButtonElement second =
        (HTMLButtonElement) host.querySelectorAll(".j2cl-read-thread-toggle").item(1);
    Assert.assertNotNull(first);
    Assert.assertNotNull(second);
    Assert.assertFalse(
        first.getAttribute("aria-controls").equals(second.getAttribute("aria-controls")));
    Assert.assertFalse(first.getAttribute("aria-label").equals(second.getAttribute("aria-label")));
  }

  private HTMLDivElement createHost() {
    currentHost = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(currentHost);
    return currentHost;
  }

  private void installFixedBlipLayout() {
    currentStyle = (HTMLElement) DomGlobal.document.createElement("style");
    currentStyle.textContent =
        ".j2cl-read-blip,.j2cl-read-viewport-placeholder{display:block;height:40px;}";
    DomGlobal.document.head.appendChild(currentStyle);
  }

  private HTMLDivElement createDuplicateThreadIdHost() {
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"inline-thread\" data-thread-id=\"t+duplicate\">"
            + "<div class=\"blip\" data-blip-id=\"b+one\">One</div>"
            + "</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+duplicate\">"
            + "<div class=\"blip\" data-blip-id=\"b+two\">Two</div>"
            + "</div>"
            + "</div></div>";
    return host;
  }

  private HTMLDivElement createThreadedHost() {
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div>"
            + "<div class=\"blip\" data-blip-id=\"b+after\">After</div>"
            + "</div></div>";
    return host;
  }

  private HTMLDivElement createThreadedHostWithoutFollowingBlip() {
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div>"
            + "</div></div>";
    return host;
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  private static HTMLElement firstBlip(HTMLDivElement host) {
    return (HTMLElement) host.querySelector("[data-j2cl-read-blip='true']");
  }

  private static HTMLElement blip(HTMLDivElement host, String blipId) {
    return (HTMLElement) host.querySelector("[data-blip-id='" + blipId + "']");
  }

  private static List<String> blipIds(HTMLDivElement host) {
    NodeList<Element> nodes = host.querySelectorAll("[data-j2cl-read-blip='true']");
    List<String> ids = new ArrayList<String>();
    for (int i = 0; i < nodes.length; i++) {
      Element node = nodes.item(i);
      if (node != null) {
        ids.add(node.getAttribute("data-blip-id"));
      }
    }
    return ids;
  }

  private static String renderedText(HTMLElement blip) {
    HTMLElement content = (HTMLElement) blip.querySelector(".j2cl-read-blip-content");
    return content == null ? "" : content.textContent;
  }

  private static J2clAttachmentMetadata attachmentMetadata(
      String attachmentId,
      String fileName,
      String mimeType,
      String attachmentUrl,
      String thumbnailUrl,
      J2clAttachmentMetadata.ImageMetadata imageMetadata,
      boolean malware) {
    return new J2clAttachmentMetadata(
        attachmentId,
        "example.com/w+1/~/conv+root",
        fileName,
        mimeType,
        1234L,
        "user@example.com",
        attachmentUrl,
        thumbnailUrl,
        imageMetadata,
        null,
        malware);
  }

  private static void setLastScrollDirection(
      J2clReadSurfaceDomRenderer renderer, String direction) throws Exception {
    Field field = J2clReadSurfaceDomRenderer.class.getDeclaredField("lastScrollDirection");
    field.setAccessible(true);
    field.set(renderer, direction);
  }

  private static String getLastScrollDirection(J2clReadSurfaceDomRenderer renderer)
      throws Exception {
    Field field = J2clReadSurfaceDomRenderer.class.getDeclaredField("lastScrollDirection");
    field.setAccessible(true);
    return (String) field.get(renderer);
  }

  private static void dispatchKey(HTMLElement target, String key) {
    KeyboardEventInit init = KeyboardEventInit.create();
    init.setKey(key);
    target.dispatchEvent(new KeyboardEvent("keydown", init));
  }

  // ----------------------------------------------------------------------
  // F-2 slice 2 (#1046) — wave chrome extensions: j/k aliases, focus-changed
  // event dispatch, expand-path symmetry, depth-focus writer, telemetry.
  // ----------------------------------------------------------------------

  @Test
  public void jKeyAliasesArrowDown() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement root = blip(host, "b+root");
    root.focus();
    dispatchKey(root, "j");
    HTMLElement after = blip(host, "b+after");
    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertTrue(after.classList.contains("j2cl-read-blip-focused"));
    Assert.assertEquals(
        "renderer key handling must keep the Lit host focus marker in sync",
        "",
        after.getAttribute("focused"));
    Assert.assertFalse(root.hasAttribute("focused"));
  }

  @Test
  public void repeatedJKeyKeepsFocusedAttributeAdvancing() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"blip\" data-blip-id=\"b+middle\">Middle</div>"
            + "<div class=\"blip\" data-blip-id=\"b+after\">After</div>"
            + "</div></div>";
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement root = blip(host, "b+root");
    HTMLElement middle = blip(host, "b+middle");
    HTMLElement after = blip(host, "b+after");

    root.focus();
    dispatchKey(root, "j");
    Assert.assertEquals("", middle.getAttribute("focused"));
    Assert.assertFalse(root.hasAttribute("focused"));

    dispatchKey(middle, "j");
    Assert.assertEquals(
        "second j press must advance the Lit host focus marker too",
        "",
        after.getAttribute("focused"));
    Assert.assertFalse(middle.hasAttribute("focused"));
  }

  @Test
  public void kKeyAliasesArrowUp() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement after = blip(host, "b+after");
    after.focus();
    // Reply is hidden by default? No — root + after are the visible blips
    // in the threaded fixture. From b+after, k moves to b+root (or the
    // visible reply, whichever is the previous visible blip in DOM order).
    dispatchKey(after, "k");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement root = blip(host, "b+root");
    // The previous visible blip from b+after is b+reply (reply is visible
    // because the inline thread is expanded by default).
    boolean replyOrRootFocused =
        reply.classList.contains("j2cl-read-blip-focused")
            || root.classList.contains("j2cl-read-blip-focused");
    Assert.assertTrue("k must move focus to a previous visible blip", replyOrRootFocused);
    HTMLElement focused = reply.classList.contains("j2cl-read-blip-focused") ? reply : root;
    Assert.assertEquals(
        "k alias must also keep the Lit host focus marker in sync",
        "",
        focused.getAttribute("focused"));
  }

  @Test
  public void focusChangedEventDispatchedOnFocusBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"blip\" data-blip-id=\"b+after\">After</div>"
            + "</div></div>";
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    final String[] receivedBlipId = new String[1];
    surface.addEventListener(
        "wavy-focus-changed",
        evt -> {
          elemental2.dom.CustomEvent<?> custom = (elemental2.dom.CustomEvent<?>) evt;
          jsinterop.base.JsPropertyMap<?> detail =
              jsinterop.base.Js.cast(custom.detail);
          receivedBlipId[0] = String.valueOf(detail.get("blipId"));
        });
    HTMLElement after = blip(host, "b+after");
    after.focus();
    dispatchKey(after, "ArrowUp");
    Assert.assertEquals("b+root", receivedBlipId[0]);
  }

  @Test
  public void focusChangedEventCarriesBoundsObject() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    installFixedBlipLayout();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+a\">A</div>"
            + "<div class=\"blip\" data-blip-id=\"b+b\">B</div>"
            + "</div></div>";
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    final boolean[] hasBounds = new boolean[1];
    final String[] keyHint = new String[1];
    surface.addEventListener(
        "wavy-focus-changed",
        evt -> {
          elemental2.dom.CustomEvent<?> custom = (elemental2.dom.CustomEvent<?>) evt;
          jsinterop.base.JsPropertyMap<?> detail =
              jsinterop.base.Js.cast(custom.detail);
          Object bounds = detail.get("bounds");
          hasBounds[0] = bounds != null;
          keyHint[0] = String.valueOf(detail.get("key"));
        });
    HTMLElement b = blip(host, "b+b");
    b.focus();
    dispatchKey(b, "ArrowUp");
    Assert.assertTrue("focus-changed detail must include bounds", hasBounds[0]);
    Assert.assertNotNull(keyHint[0]);
  }

  @Test
  public void focusFrameLandmarkAppendedToSurface() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement frame =
        (HTMLElement) host.querySelector("[data-j2cl-read-surface='true'] wavy-focus-frame");
    Assert.assertNotNull(
        "renderer must mount a <wavy-focus-frame> child on the surface", frame);
    Assert.assertTrue("focus-frame starts hidden", frame.hasAttribute("hidden"));
  }

  @Test
  public void focusFrameMountIsIdempotent() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.enhanceExistingSurface();
    renderer.enhanceExistingSurface();
    Assert.assertEquals(
        "second enhance pass must not duplicate the focus frame",
        1,
        host.querySelectorAll("wavy-focus-frame").length);
  }

  @Test
  public void setDepthFocusWritesDataAttributesOnHost() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDepthFocus("b+3", "b+1", "Alice");
    Assert.assertEquals("b+3", host.getAttribute("data-current-depth-blip-id"));
    Assert.assertEquals("b+1", host.getAttribute("data-parent-depth-blip-id"));
    Assert.assertEquals("Alice", host.getAttribute("data-parent-author-name"));
  }

  @Test
  public void setDepthFocusClearsDataAttributesAtTopOfWave() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    renderer.setDepthFocus("b+3", "b+1", "Alice");
    renderer.setDepthFocus("", "", "");
    Assert.assertFalse(host.hasAttribute("data-current-depth-blip-id"));
    Assert.assertFalse(host.hasAttribute("data-parent-depth-blip-id"));
    Assert.assertFalse(host.hasAttribute("data-parent-author-name"));
  }

  @Test
  public void collapseToggleEmitsTelemetryEvent() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    RecordingTelemetrySink sink = new RecordingTelemetrySink();
    new J2clReadSurfaceDomRenderer(host, sink).enhanceExistingSurface();
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    Assert.assertNotNull(toggle);
    int before = sink.events().size();
    toggle.click();
    Assert.assertTrue(
        "collapse toggle should emit wave_chrome.thread_collapse.toggle",
        hasEventNamed(sink, "wave_chrome.thread_collapse.toggle", before));
  }

  @Test
  public void focusChangeEmitsTelemetryEvent() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+a\">A</div>"
            + "<div class=\"blip\" data-blip-id=\"b+b\">B</div>"
            + "</div></div>";
    RecordingTelemetrySink sink = new RecordingTelemetrySink();
    new J2clReadSurfaceDomRenderer(host, sink).enhanceExistingSurface();
    int before = sink.events().size();
    HTMLElement b = blip(host, "b+b");
    b.focus();
    dispatchKey(b, "ArrowUp");
    Assert.assertTrue(
        "focus change should emit wave_chrome.focus_frame.transition",
        hasEventNamed(sink, "wave_chrome.focus_frame.transition", before));
  }

  private static boolean hasEventNamed(
      RecordingTelemetrySink sink, String name, int sinceIndex) {
    List<J2clClientTelemetry.Event> events = sink.events();
    for (int i = sinceIndex; i < events.size(); i++) {
      if (name.equals(events.get(i).getName())) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void expandPathReDispatchesFocusChangedForBoundsRefresh() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface();
    HTMLElement root = blip(host, "b+root");
    root.focus();
    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    final int[] focusChangedCount = new int[1];
    surface.addEventListener(
        "wavy-focus-changed", evt -> focusChangedCount[0]++);
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    toggle.click();
    int afterCollapse = focusChangedCount[0];
    toggle.click();
    Assert.assertTrue(
        "expand should re-dispatch focus-changed when focus is visible",
        focusChangedCount[0] > afterCollapse);
  }
}

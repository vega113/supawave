package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.KeyboardEventInit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clReadSurfaceDomRendererTest.class)
public class J2clReadSurfaceDomRendererTest {
  private HTMLDivElement currentHost;

  @After
  public void tearDown() {
    if (currentHost != null && currentHost.parentElement != null) {
      currentHost.parentElement.removeChild(currentHost);
    }
    currentHost = null;
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
  public void enhanceExistingSurfaceReturnsFalseForEmptyHost() {
    assumeBrowserDom();
    Assert.assertFalse(new J2clReadSurfaceDomRenderer(createHost()).enhanceExistingSurface());
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

  private static void dispatchKey(HTMLElement target, String key) {
    KeyboardEventInit init = KeyboardEventInit.create();
    init.setKey(key);
    target.dispatchEvent(new KeyboardEvent("keydown", init));
  }
}

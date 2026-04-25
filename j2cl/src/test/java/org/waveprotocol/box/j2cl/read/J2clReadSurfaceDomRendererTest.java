package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.KeyboardEventInit;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
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
    Assert.assertNotNull(tile.querySelector("img"));

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
}

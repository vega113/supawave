package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.core.JsArray;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jsinterop.base.Js;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;

/**
 * F-2 slice 2 (#1046) — coverage for the chrome elements
 * {@link J2clSelectedWaveView} mounts: {@code <wavy-depth-nav-bar>},
 * {@code <wavy-wave-header-actions>}, {@code <wavy-wave-nav-row>} (and
 * indirectly {@code <wavy-focus-frame>} which the renderer mounts inside its
 * surface).
 *
 * <p>These tests run only when a real browser DOM is present (J2CL test
 * runner against Chromium); JVM test runs skip via {@code Assume}.
 */
@J2clTestInput(J2clSelectedWaveViewChromeTest.class)
public class J2clSelectedWaveViewChromeTest {
  private HTMLElement currentHost;

  @After
  public void tearDown() {
    if (currentHost != null && currentHost.parentElement != null) {
      currentHost.parentElement.removeChild(currentHost);
    }
    currentHost = null;
  }

  @Test
  public void coldMountInsertsAllThreeChromeLandmarks() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    new J2clSelectedWaveView(host);
    Assert.assertNotNull(
        "Cold mount must insert <wavy-depth-nav-bar>",
        host.querySelector("wavy-depth-nav-bar"));
    Assert.assertNotNull(
        "Cold mount must insert <wavy-wave-nav-row>",
        host.querySelector("wavy-wave-nav-row"));
    Assert.assertNotNull(
        "Cold mount must insert <wavy-wave-header-actions>",
        host.querySelector("wavy-wave-header-actions"));
  }

  @Test
  public void coldMountPlacesHeaderActionsBetweenParticipantsAndNavRow() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    new J2clSelectedWaveView(host);

    HTMLElement participants = (HTMLElement) host.querySelector(".sidecar-selected-participants");
    HTMLElement actions = (HTMLElement) host.querySelector("wavy-wave-header-actions");
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");

    Assert.assertNotNull(participants);
    Assert.assertNotNull(actions);
    Assert.assertNotNull(row);
    Assert.assertSame(
        "Header actions must sit after participants",
        actions,
        participants.nextSibling);
    Assert.assertSame(
        "Wave nav row must sit after header actions",
        row,
        actions.nextSibling);
  }

  @Test
  public void coldMountCardCarriesKeyboardBindingHostAttribute() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    new J2clSelectedWaveView(host);
    HTMLElement card = (HTMLElement) host.querySelector(".sidecar-selected-card");
    Assert.assertNotNull(card);
    Assert.assertTrue(
        "Card must carry data-j2cl-selected-wave-host for the H key handler",
        card.hasAttribute("data-j2cl-selected-wave-host"));
  }

  @Test
  public void coldMountDepthNavBarStartsHidden() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    new J2clSelectedWaveView(host);
    HTMLElement bar = (HTMLElement) host.querySelector("wavy-depth-nav-bar");
    Assert.assertTrue(
        "Depth-nav-bar starts hidden at top-of-wave", bar.hasAttribute("hidden"));
  }

  @Test
  public void renderBindsUnreadCountToWaveNavRow() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+1",
            "Selected wave",
            "",
            "5 unread.",
            "Live.",
            "",
            0,
            Collections.<String>emptyList(),
            Arrays.<String>asList(),
            Arrays.<J2clReadBlip>asList(),
            null,
            5,
            false,
            true,
            false);
    view.render(model);
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertEquals(
        "Nav-row receives the model's unread count via the unread-count attribute",
        "5",
        row.getAttribute("unread-count"));
  }

  @Test
  public void renderBindsSourceWaveIdToWaveNavRow() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+abc",
            "Selected wave",
            "",
            "Read.",
            "",
            "",
            0,
            Collections.<String>emptyList(),
            Arrays.<String>asList(),
            Arrays.<J2clReadBlip>asList(),
            null,
            0,
            true,
            true,
            false);
    view.render(model);
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertEquals(
        "Nav-row receives the source wave id",
        "example.com/w+abc",
        row.getAttribute("source-wave-id"));
  }

  @Test
  public void renderBindsSelectedWaveStateToHeaderActions() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        selectedWritableModel(
            "example.com/w+actions",
            Arrays.asList("@example.com", "alice@example.com", "bob@example.com"));

    view.render(model);

    HTMLElement actions = (HTMLElement) host.querySelector("wavy-wave-header-actions");
    Assert.assertEquals(
        "Header actions receive the selected wave id",
        "example.com/w+actions",
        actions.getAttribute("source-wave-id"));
    Assert.assertTrue(
        "Shared-domain participant marks the wave public",
        actions.hasAttribute("public"));
    Assert.assertEquals(
        "Lock state defaults to unlocked until the lock document is projected",
        "unlocked",
        actions.getAttribute("lock-state"));
    Assert.assertFalse(
        "Header actions are enabled when the selected wave has a write session",
        actions.hasAttribute("disabled"));
    Assert.assertFalse(Boolean.TRUE.equals(Js.asPropertyMap(actions).get("disabled")));
    Object participantsObject = Js.asPropertyMap(actions).get("participants");
    Assert.assertTrue(
        "Header actions receive participants as a JS array",
        JsArray.isArray(participantsObject));
    JsArray<?> participants = Js.uncheckedCast(participantsObject);
    Assert.assertEquals(3, participants.length);
    Assert.assertEquals("@example.com", participants.getAt(0));
    Assert.assertEquals("alice@example.com", participants.getAt(1));
    Assert.assertEquals("bob@example.com", participants.getAt(2));
  }

  @Test
  public void renderDisablesHeaderActionsWithoutWriteSession() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);

    view.render(selectedModel("example.com/w+readonly"));

    HTMLElement actions = (HTMLElement) host.querySelector("wavy-wave-header-actions");
    Assert.assertTrue(
        "Read-only selected waves must disable mutating header actions",
        actions.hasAttribute("disabled"));
    Assert.assertTrue(Boolean.TRUE.equals(Js.asPropertyMap(actions).get("disabled")));
  }

  @Test
  public void renderDerivesReadMentionChipFromLiteralParticipantAddressWhenRangesAreAbsent() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+mentions",
            "Selected wave",
            "",
            "Read.",
            "",
            "",
            0,
            Arrays.asList("alice@example.com"),
            Collections.<String>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "Hello @alice@example.com")),
            null,
            0,
            true,
            true,
            false);

    view.render(model);

    HTMLElement mention =
        (HTMLElement) host.querySelector("[data-j2cl-read-mention='true']");
    Assert.assertNotNull(
        "Reloaded read-surface text must recover a stable mention chip from the participant address",
        mention);
    Assert.assertEquals("alice@example.com", mention.getAttribute("data-mention-address"));
    Assert.assertEquals("@alice@example.com", mention.textContent);
  }

  @Test
  public void renderDoesNotDeriveMentionChipFromEmbeddedLiteralParticipantAddress() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+embedded-email",
            "Selected wave",
            "",
            "Read.",
            "",
            "",
            0,
            Arrays.asList("alice@example.com"),
            Collections.<String>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "Forward foo@alice@example.com now")),
            null,
            0,
            true,
            true,
            false);

    view.render(model);

    Assert.assertNull(
        "Embedded address substrings must not become mention chips",
        host.querySelector("[data-j2cl-read-mention='true']"));
  }

  @Test
  public void renderDoesNotDeriveMentionChipFromParticipantAddressPrefix() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+address-prefix",
            "Selected wave",
            "",
            "Read.",
            "",
            "",
            0,
            Arrays.asList("alice@example.com"),
            Collections.<String>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "Ping @alice@example.com.uk")),
            null,
            0,
            true,
            true,
            false);

    view.render(model);

    Assert.assertNull(
        "Longer address prefixes must not be chipped as the shorter participant address",
        host.querySelector("[data-j2cl-read-mention='true']"));
  }

  @Test
  public void renderDoesNotDeriveMentionChipWhenBlipMetadataExplicitlyHasNoMentions() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+plain-email",
            "Selected wave",
            "",
            "Read.",
            "",
            "",
            0,
            Arrays.asList("alice@example.com"),
            Collections.<String>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "Email @alice@example.com for support")),
            J2clSelectedWaveViewportState.empty(),
            Arrays.asList(
                new J2clInteractionBlipModel(
                    "b+root",
                    "Email @alice@example.com for support",
                    Collections.<SidecarAnnotationRange>emptyList(),
                    null)),
            null,
            0,
            true,
            true,
            false);

    view.render(model);

    Assert.assertNull(
        "Plain @participant text must not become a mention chip when metadata explicitly has none",
        host.querySelector("[data-j2cl-read-mention='true']"));
  }

  @Test
  public void setNavRowFolderStateStampsCurrentSourceWaveOwnership() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    view.render(selectedModel("example.com/w+folder"));
    view.setNavRowFolderState(true, true);

    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertTrue(row.hasAttribute("pinned"));
    Assert.assertTrue(row.hasAttribute("archived"));
    Assert.assertEquals(
        "Model-published folder state must be keyed to the current source-wave-id",
        "example.com/w+folder",
        row.getAttribute("data-folder-state-wave-id"));
  }

  @Test
  public void setNavRowFolderStateClearsOwnershipWhenSourceWaveIsAbsent() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    row.setAttribute("pinned", "");
    row.setAttribute("archived", "");
    row.setAttribute("data-folder-state-wave-id", "example.com/w+old");

    view.setNavRowFolderState(true, true);

    Assert.assertFalse(row.hasAttribute("pinned"));
    Assert.assertFalse(row.hasAttribute("archived"));
    Assert.assertFalse(row.hasAttribute("data-folder-state-wave-id"));
  }

  @Test
  public void setNavRowFolderStateClearsOwnershipWhenSourceWaveIsEmpty() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    row.setAttribute("source-wave-id", "");
    row.setAttribute("pinned", "");
    row.setAttribute("archived", "");
    row.setAttribute("data-folder-state-wave-id", "example.com/w+old");

    view.setNavRowFolderState(true, true);

    Assert.assertFalse(row.hasAttribute("pinned"));
    Assert.assertFalse(row.hasAttribute("archived"));
    Assert.assertFalse(row.hasAttribute("data-folder-state-wave-id"));
  }

  @Test
  public void setNavRowFolderStateStampsModelClearedStateForCurrentSourceWave() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);

    view.render(selectedModel("example.com/w+clear"));
    view.setNavRowFolderState(true, true);
    view.setNavRowFolderState(false, false);

    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertFalse(row.hasAttribute("pinned"));
    Assert.assertFalse(row.hasAttribute("archived"));
    Assert.assertEquals(
        "example.com/w+clear",
        row.getAttribute("data-folder-state-wave-id"));
  }

  @Test
  public void setNavRowFolderStateKeepsOwnershipStableForSameWaveFolderSwitch() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);

    view.render(selectedModel("example.com/w+same"));
    view.setNavRowFolderState(true, false);
    view.setNavRowFolderState(false, true);

    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertEquals(
        "example.com/w+same",
        row.getAttribute("data-folder-state-wave-id"));
    Assert.assertFalse(row.hasAttribute("pinned"));
    Assert.assertTrue(row.hasAttribute("archived"));
  }

  @Test
  public void setNavRowFolderStateRewritesOwnershipAcrossRenders() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);

    view.render(selectedModel("example.com/w+a"));
    view.setNavRowFolderState(true, false);
    view.render(selectedModel("example.com/w+b"));
    view.setNavRowFolderState(false, true);

    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertEquals("example.com/w+b", row.getAttribute("source-wave-id"));
    Assert.assertEquals(
        "example.com/w+b",
        row.getAttribute("data-folder-state-wave-id"));
    Assert.assertFalse(row.hasAttribute("pinned"));
    Assert.assertTrue(row.hasAttribute("archived"));
  }

  @Test
  public void renderClearsSourceWaveIdWhenSelectionIsEmpty() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    view.render(selectedModel("example.com/w+old"));
    view.setNavRowFolderState(true, true);

    view.render(J2clSelectedWaveModel.clearedSelection());

    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertFalse(
        "Cleared selection clears the source wave id on the nav row",
        row.hasAttribute("source-wave-id"));
    Assert.assertFalse(
        "Cleared selection clears stale pinned state",
        row.hasAttribute("pinned"));
    Assert.assertFalse(
        "Cleared selection clears stale archived state",
        row.hasAttribute("archived"));
    Assert.assertFalse(
        "Cleared selection clears stale model ownership marker",
        row.hasAttribute("data-folder-state-wave-id"));
    HTMLElement actions = (HTMLElement) host.querySelector("wavy-wave-header-actions");
    Assert.assertFalse(
        "Cleared selection clears the source wave id on header actions",
        actions.hasAttribute("source-wave-id"));
    Assert.assertFalse(
        "Cleared selection clears stale public state",
        actions.hasAttribute("public"));
    Assert.assertFalse(
        "Cleared selection clears stale lock state",
        actions.hasAttribute("lock-state"));
    Assert.assertTrue(
        "Cleared selection disables mutating header actions",
        actions.hasAttribute("disabled"));
    Assert.assertTrue(Boolean.TRUE.equals(Js.asPropertyMap(actions).get("disabled")));
    Object participantsObject = Js.asPropertyMap(actions).get("participants");
    Assert.assertTrue(JsArray.isArray(participantsObject));
    Assert.assertEquals(0, Js.<JsArray<?>>uncheckedCast(participantsObject).length);
  }

  @Test
  public void unknownUnreadCountClampsToZero() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    // J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT == -1; the nav-row must
    // never receive a negative count (would falsely emphasize E.2 cyan).
    view.render(J2clSelectedWaveModel.empty());
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    Assert.assertEquals(
        "Unknown unread count clamps to 0 (no spurious cyan emphasis)",
        "0",
        row.getAttribute("unread-count"));
  }

  @Test
  public void navRowEventsEmitTelemetry() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    RecordingTelemetrySink sink = new RecordingTelemetrySink();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host, sink);
    HTMLElement card = (HTMLElement) host.querySelector(".sidecar-selected-card");
    int before = sink.events().size();
    // Synthesize a nav-row event the same way the Lit element would.
    elemental2.dom.CustomEventInit<Object> init = elemental2.dom.CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    elemental2.dom.CustomEvent<Object> evt =
        new elemental2.dom.CustomEvent<Object>("wave-nav-pin-toggle-requested", init);
    card.dispatchEvent(evt);
    boolean recorded = false;
    for (int i = before; i < sink.events().size(); i++) {
      if ("wave_chrome.nav_row.click".equals(sink.events().get(i).getName())) {
        recorded = true;
        break;
      }
    }
    Assert.assertTrue(
        "Nav-row click must emit wave_chrome.nav_row.click telemetry", recorded);
  }

  @Test
  public void navRowNextAndPreviousEventsMoveFocusedBlip() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    view.render(navigationModel());
    HTMLElement first = (HTMLElement) host.querySelector("wave-blip[data-blip-id='b+1']");
    HTMLElement second = (HTMLElement) host.querySelector("wave-blip[data-blip-id='b+2']");
    Assert.assertNotNull(first);
    Assert.assertNotNull(second);
    first.focus();

    dispatchNavEvent(view.getCardElement(), "wave-nav-next-requested");

    Assert.assertEquals(second, DomGlobal.document.activeElement);

    dispatchNavEvent(view.getCardElement(), "wave-nav-previous-requested");

    Assert.assertEquals(first, DomGlobal.document.activeElement);
  }

  @Test
  public void navRowRecentEndUnreadAndMentionEventsMoveFocusedBlip() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    view.render(navigationModel());

    dispatchNavEvent(view.getCardElement(), "wave-nav-recent-requested");
    Assert.assertEquals(
        host.querySelector("wave-blip[data-blip-id='b+3']"),
        DomGlobal.document.activeElement);

    dispatchNavEvent(view.getCardElement(), "wave-nav-end-requested");
    Assert.assertEquals(
        host.querySelector("wave-blip[data-blip-id='b+3']"),
        DomGlobal.document.activeElement);

    dispatchNavEvent(view.getCardElement(), "wave-nav-next-unread-requested");
    Assert.assertEquals(
        host.querySelector("wave-blip[data-blip-id='b+2']"),
        DomGlobal.document.activeElement);

    dispatchNavEvent(view.getCardElement(), "wave-nav-next-mention-requested");
    Assert.assertEquals(
        host.querySelector("wave-blip[data-blip-id='b+3']"),
        DomGlobal.document.activeElement);

    dispatchNavEvent(view.getCardElement(), "wave-nav-prev-mention-requested");
    Assert.assertEquals(
        host.querySelector("wave-blip[data-blip-id='b+3']"),
        DomGlobal.document.activeElement);
  }

  @Test
  public void selectedWaveRefreshEventInvokesRefreshHandler() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    final String[] refreshedWaveId = {null};
    view.setSelectedWaveRefreshHandler(waveId -> refreshedWaveId[0] = waveId);
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");

    elemental2.dom.CustomEventInit<Object> init = elemental2.dom.CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    jsinterop.base.JsPropertyMap<Object> detail = jsinterop.base.JsPropertyMap.of();
    detail.set("waveId", "example.com/w+restore");
    init.setDetail(detail);
    elemental2.dom.CustomEvent<Object> evt =
        new elemental2.dom.CustomEvent<Object>("wavy-selected-wave-refresh-requested", init);
    row.dispatchEvent(evt);

    Assert.assertEquals("example.com/w+restore", refreshedWaveId[0]);
  }

  @Test
  public void depthNavEventsEmitTelemetry() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    RecordingTelemetrySink sink = new RecordingTelemetrySink();
    new J2clSelectedWaveView(host, sink);
    HTMLElement card = (HTMLElement) host.querySelector(".sidecar-selected-card");
    int before = sink.events().size();
    elemental2.dom.CustomEventInit<Object> init = elemental2.dom.CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    elemental2.dom.CustomEvent<Object> evt =
        new elemental2.dom.CustomEvent<Object>("wavy-depth-up", init);
    card.dispatchEvent(evt);
    boolean recorded = false;
    for (int i = before; i < sink.events().size(); i++) {
      if ("wave_chrome.depth_nav.click".equals(sink.events().get(i).getName())) {
        recorded = true;
        break;
      }
    }
    Assert.assertTrue(
        "Depth-nav click must emit wave_chrome.depth_nav.click telemetry", recorded);
  }

  @Test
  public void serverFirstReBindsExistingChromeLandmarks() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    // Synthesize a server-first markup matching what HtmlRenderer emits.
    host.innerHTML =
        "<div class=\"sidecar-selected-host\" data-j2cl-selected-wave-host=\"true\">"
            + "<section class=\"sidecar-selected-card\""
            + " data-j2cl-server-first-mode=\"snapshot\""
            + " data-j2cl-upgrade-placeholder=\"selected-wave\">"
            + "<p class=\"sidecar-eyebrow\">Opened wave</p>"
            + "<wavy-depth-nav-bar hidden data-j2cl-server-first-chrome=\"true\"></wavy-depth-nav-bar>"
            + "<h2 class=\"sidecar-selected-title\">Title</h2>"
            + "<p class=\"sidecar-selected-unread\" hidden></p>"
            + "<p class=\"sidecar-selected-status\"></p>"
            + "<p class=\"sidecar-selected-detail\"></p>"
            + "<p class=\"sidecar-selected-participants\" hidden></p>"
            + "<wavy-wave-nav-row data-j2cl-server-first-chrome=\"true\"></wavy-wave-nav-row>"
            + "<p class=\"sidecar-selected-snippet\" hidden></p>"
            + "<div class=\"sidecar-selected-compose\"></div>"
            + "<div class=\"sidecar-selected-content\" data-wave-id=\"example.com/w+1\"></div>"
            + "</section>"
            + "</div>";
    HTMLElement bar = (HTMLElement) host.querySelector("wavy-depth-nav-bar");
    HTMLElement row = (HTMLElement) host.querySelector("wavy-wave-nav-row");
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    HTMLElement actions = (HTMLElement) host.querySelector("wavy-wave-header-actions");
    Assert.assertSame(
        "Server-first re-bind must re-use the same depth-nav-bar element (no replaceChild)",
        bar,
        host.querySelector("wavy-depth-nav-bar"));
    Assert.assertSame(
        "Server-first re-bind must re-use the same wave-nav-row element (no replaceChild)",
        row,
        host.querySelector("wavy-wave-nav-row"));
    Assert.assertFalse(
        "Server-first re-bind starts without model-owned folder marker",
        row.hasAttribute("data-folder-state-wave-id"));
    Assert.assertNotNull(
        "Server-first re-bind must create header actions when SSR lacks them",
        actions);
    Assert.assertSame(
        "Server-first header actions must be inserted before the wave nav row",
        row,
        actions.nextSibling);

    view.render(selectedModel("example.com/w+server-first"));
    view.setNavRowFolderState(true, false);

    Assert.assertEquals(
        "example.com/w+server-first",
        row.getAttribute("data-folder-state-wave-id"));
  }

  // J-UI-8 (#1086, R-6.3): aria-busy is set by HtmlRenderer on the
  // server-first card for the lifetime of the snapshot; clearing it is
  // the J2CL view's signal that the live render has replaced the
  // server-first state. The first non-preserved render() call must
  // remove the attribute.
  @Test
  public void liveRenderClearsAriaBusyOnServerFirstCard() {
    assumeBrowserDom();
    HTMLElement host = createHost();
    host.innerHTML =
        "<div class=\"sidecar-selected-host\" data-j2cl-selected-wave-host=\"true\">"
            + "<section class=\"sidecar-selected-card\""
            + " data-j2cl-server-first-mode=\"snapshot\""
            + " data-j2cl-server-first-selected-wave=\"example.com/w+1\""
            + " data-j2cl-upgrade-placeholder=\"selected-wave\""
            + " aria-busy=\"true\">"
            + "<p class=\"sidecar-eyebrow\">Opened wave</p>"
            + "<wavy-depth-nav-bar hidden data-j2cl-server-first-chrome=\"true\"></wavy-depth-nav-bar>"
            + "<h2 class=\"sidecar-selected-title\">Title</h2>"
            + "<p class=\"sidecar-selected-unread\" hidden></p>"
            + "<p class=\"sidecar-selected-status\"></p>"
            + "<p class=\"sidecar-selected-detail\"></p>"
            + "<p class=\"sidecar-selected-participants\" hidden></p>"
            + "<wavy-wave-nav-row data-j2cl-server-first-chrome=\"true\"></wavy-wave-nav-row>"
            + "<p class=\"sidecar-selected-snippet\" hidden></p>"
            + "<div class=\"sidecar-selected-compose\"></div>"
            + "<div class=\"sidecar-selected-content\" data-wave-id=\"example.com/w+2\"></div>"
            + "</section>"
            + "</div>";
    J2clSelectedWaveView view = new J2clSelectedWaveView(host);
    HTMLElement card = (HTMLElement) host.querySelector(".sidecar-selected-card");
    Assert.assertNotNull(card);
    Assert.assertEquals(
        "Server-first markup ships with aria-busy=\"true\"",
        "true",
        card.getAttribute("aria-busy"));

    // Render with a different selected wave id so shouldPreserveServerFirstCard
    // returns false and the live path runs (which then triggers
    // clearServerFirstMarkers).
    J2clSelectedWaveModel model =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+2",
            "Different wave",
            "",
            "0 unread.",
            "Live.",
            "",
            0,
            Collections.<String>emptyList(),
            Arrays.<String>asList(),
            Arrays.<J2clReadBlip>asList(),
            null,
            0,
            false,
            true,
            false);
    view.render(model);

    Assert.assertFalse(
        "clearServerFirstMarkers must remove aria-busy on the live upgrade",
        card.hasAttribute("aria-busy"));
    Assert.assertFalse(
        "clearServerFirstMarkers must remove the upgrade-placeholder marker",
        card.hasAttribute("data-j2cl-upgrade-placeholder"));
  }

  // -- helpers ---------------------------------------------------------

  private HTMLElement createHost() {
    currentHost = (HTMLElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(currentHost);
    return currentHost;
  }

  private static J2clSelectedWaveModel selectedModel(String waveId) {
    return selectedModel(waveId, Collections.<String>emptyList());
  }

  private static J2clSelectedWaveModel selectedModel(String waveId, List<String> participants) {
    return selectedModel(waveId, participants, null);
  }

  private static J2clSelectedWaveModel selectedWritableModel(String waveId, List<String> participants) {
    return selectedModel(
        waveId,
        participants,
        new J2clSidecarWriteSession(waveId, "chan-1", 44L, "ABCD", "b+root"));
  }

  private static J2clSelectedWaveModel selectedModel(
      String waveId, List<String> participants, J2clSidecarWriteSession writeSession) {
    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        waveId,
        "Selected wave",
        "",
        "Read.",
        "",
        "",
        0,
        participants,
        Arrays.<String>asList(),
        Arrays.<J2clReadBlip>asList(),
        writeSession,
        0,
        true,
        true,
        false);
  }

  private static J2clSelectedWaveModel navigationModel() {
    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        "example.com/w+nav",
        "Selected wave",
        "",
        "1 unread.",
        "",
        "",
        0,
        Collections.<String>emptyList(),
        Arrays.<String>asList(),
        Arrays.asList(
            readBlip("b+1", "one", 1000L, false, false),
            readBlip("b+2", "two", 2000L, true, false),
            readBlip("b+3", "three", 3000L, false, true)),
        null,
        1,
        true,
        true,
        false);
  }

  private static J2clReadBlip readBlip(
      String blipId, String text, long modifiedAt, boolean unread, boolean hasMention) {
    return new J2clReadBlip(
        blipId,
        text,
        Collections.emptyList(),
        "vega@example.com",
        "Vega",
        modifiedAt,
        "",
        "",
        unread,
        hasMention);
  }

  private static void dispatchNavEvent(HTMLElement target, String eventName) {
    elemental2.dom.CustomEventInit<Object> init = elemental2.dom.CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    target.dispatchEvent(new elemental2.dom.CustomEvent<Object>(eventName, init));
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }
}

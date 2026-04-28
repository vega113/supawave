package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSearchPanelControllerTest.class)
public class J2clSearchPanelControllerTest {
  @Test
  public void startLoadsDefaultQueryAndRendersInitialResponse() {
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "in:inbox",
                1,
                Collections.singletonList(
                    new SidecarSearchResponse.Digest(
                        "Inbox wave",
                        "Snippet",
                        "example.com/w+abc123",
                        12345L,
                        1,
                        3,
                        Collections.singletonList("teammate@example.com"),
                        "user@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> { }, 1200);

    controller.start("   ", null);

    Assert.assertEquals("in:inbox", gateway.lastQuery);
    Assert.assertEquals(0, gateway.lastIndex);
    Assert.assertEquals(30, gateway.lastNumResults);
    Assert.assertEquals("in:inbox", view.query);
    Assert.assertEquals("user@example.com", view.sessionSummary);
    Assert.assertEquals("1 waves · 1 unread", view.lastModel.getWaveCountText());
    Assert.assertFalse(view.loading);
  }

  @Test
  public void submittingQueryResetsWindowAndShowMoreUsesNextPage() {
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "with:@",
                45,
                Arrays.asList(
                    new SidecarSearchResponse.Digest(
                        "Wave 1",
                        "Snippet",
                        "example.com/w+1",
                        1L,
                        0,
                        2,
                        Collections.singletonList("person@example.com"),
                        "person@example.com",
                        false),
                    new SidecarSearchResponse.Digest(
                        "Wave 2",
                        "Snippet",
                        "example.com/w+2",
                        2L,
                        0,
                        2,
                        Collections.singletonList("person@example.com"),
                        "person@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> { }, 1200);

    controller.start(null, null);
    controller.onQuerySubmitted("with:@");
    Assert.assertEquals("with:@", gateway.lastQuery);
    Assert.assertEquals(30, gateway.lastNumResults);

    controller.onShowMoreRequested();
    Assert.assertEquals("with:@", gateway.lastQuery);
    Assert.assertEquals(60, gateway.lastNumResults);
  }

  @Test
  public void submittingQueryClearsSelectionAndNotifiesHandler() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start(null, null);
    controller.onDigestSelected("example.com/w+1");
    controller.onQuerySubmitted("with:@");

    Assert.assertNull(view.selectedWaveId);
    Assert.assertEquals(
        Arrays.asList("in:inbox|example.com/w+1|true", "with:@|null|true"),
        routeEvents);
  }

  @Test
  public void missingSelectedWaveDoesNotClearRestoredRouteSelection() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start("with:@", "example.com/w+1");
    gateway.response =
        responseWithDigests(
            new SidecarSearchResponse.Digest(
                "Wave 2",
                "Snippet",
                "example.com/w+2",
                2L,
                0,
                2,
                Collections.singletonList("person@example.com"),
                "person@example.com",
                false));

    controller.onShowMoreRequested();

    Assert.assertEquals("example.com/w+1", view.selectedWaveId);
    Assert.assertEquals(
        Arrays.asList("with:@|example.com/w+1|false"),
        routeEvents);
  }

  @Test
  public void searchErrorKeepsCurrentSelectionRouteState() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start(null, null);
    controller.onDigestSelected("example.com/w+1");
    gateway.error = "boom";

    controller.onShowMoreRequested();

    Assert.assertEquals("example.com/w+1", view.selectedWaveId);
    Assert.assertEquals("Search request failed: boom", view.status);
    Assert.assertTrue(view.error);
    Assert.assertEquals(Arrays.asList("in:inbox|example.com/w+1|true"), routeEvents);
  }

  @Test
  public void onReadStateChangedUpdatesMatchingDigestUnreadCount() {
    // F-4 (#1039 / R-4.4): a live read-state change for the selected wave
    // must decrement the matching digest's unread badge in place — without
    // re-running the search.
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "in:inbox",
                1,
                Collections.singletonList(
                    new SidecarSearchResponse.Digest(
                        "Live wave",
                        "Snippet",
                        "example.com/w+1",
                        12345L,
                        /* unread= */ 5,
                        /* msg= */ 5,
                        Collections.singletonList("teammate@example.com"),
                        "user@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start("in:inbox", null);

    // Sanity check: model carries 5 unread for the wave.
    Assert.assertEquals(5, controller.findDigestItem("example.com/w+1").getUnreadCount());

    // Drive the read-state listener with a decremented count.
    controller.onReadStateChanged("example.com/w+1", 3, /* stale= */ false);

    Assert.assertEquals(
        "live decrement must patch the cached model",
        3,
        controller.findDigestItem("example.com/w+1").getUnreadCount());
    Assert.assertEquals(
        "view's updateDigestUnread must have been called for the matching id",
        Arrays.asList("example.com/w+1=3"),
        view.updateDigestUnreadInvocations);
  }

  @Test
  public void onReadStateChangedIgnoresMissingWaveId() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:inbox", 0, null));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start("in:inbox", null);

    controller.onReadStateChanged("", 3, false);
    controller.onReadStateChanged(null, 3, false);
    controller.onReadStateChanged("example.com/w+absent", 0, false);

    Assert.assertTrue(
        "no view update for empty / null / unknown wave ids",
        view.updateDigestUnreadInvocations.isEmpty());
  }

  @Test
  public void onReadStateChangedClampsNegativeCountsToZero() {
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "in:inbox",
                1,
                Collections.singletonList(
                    new SidecarSearchResponse.Digest(
                        "Live wave",
                        "Snippet",
                        "example.com/w+1",
                        12345L,
                        /* unread= */ 4,
                        /* msg= */ 4,
                        Collections.singletonList("teammate@example.com"),
                        "user@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start("in:inbox", null);

    controller.onReadStateChanged("example.com/w+1", -1, false);

    Assert.assertEquals(0, controller.findDigestItem("example.com/w+1").getUnreadCount());
    Assert.assertEquals(
        Arrays.asList("example.com/w+1=0"), view.updateDigestUnreadInvocations);
  }

  // --- J-UI-2 (#1080 / R-4.5): folder + chip switching --------------------

  @Test
  public void savedSearchSelectionIssuesFolderQueryAndAnnounces() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    1,
                    Collections.singletonList("teammate@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + userNavigation),
            1200);
    controller.start(null, null);

    controller.onSavedSearchSelected("mentions", "Mentions", "mentions:me");

    Assert.assertEquals("mentions:me", gateway.lastQuery);
    Assert.assertEquals("Mentions", view.lastAnnouncement);
    Assert.assertEquals(1, view.focusActiveFolderInvocations);
    // Route state must be published for back/forward to work.
    Assert.assertTrue(
        "saved-search must publish a userNavigation route event",
        routeEvents.contains("mentions:me|true"));
  }

  @Test
  public void filterToggleIssuesComposedQueryAndAnnouncesActiveLabel() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:inbox", 0, null));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start("in:inbox", null);

    controller.onFilterToggled(
        "unread", "Unread only", /* active= */ true, "in:inbox is:unread");

    Assert.assertEquals("in:inbox is:unread", gateway.lastQuery);
    Assert.assertEquals("Unread only filter on", view.lastAnnouncement);
  }

  @Test
  public void filterToggleOffAnnouncesInactiveLabel() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:inbox", 0, null));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start("in:inbox is:unread", null);

    controller.onFilterToggled(
        "unread", "Unread only", /* active= */ false, "in:inbox");

    Assert.assertEquals("in:inbox", gateway.lastQuery);
    Assert.assertEquals("Unread only filter off", view.lastAnnouncement);
  }

  @Test
  public void savedSearchSelectionWithEmptyLabelDoesNotAnnounce() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:archive", 0, null));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> {}, 1200);
    controller.start(null, null);

    controller.onSavedSearchSelected("archive", "", "in:archive");

    Assert.assertEquals("in:archive", gateway.lastQuery);
    Assert.assertNull("empty label must not produce an announcement", view.lastAnnouncement);
    // Focus still moves so keyboard users land on the active button.
    Assert.assertEquals(1, view.focusActiveFolderInvocations);
  }

  @Test
  public void digestSelectionPublishesRouteStateAsUserNavigation() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:inbox", 0, null));
    FakeView view = new FakeView();
    J2clSidecarRouteState[] selectedState = new J2clSidecarRouteState[1];
    boolean[] userNavigation = new boolean[1];
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, isUserNavigation) -> {
              selectedState[0] = state;
              userNavigation[0] = isUserNavigation;
            },
            1200);

    controller.onDigestSelected("example.com/w+abc123");

    Assert.assertEquals("example.com/w+abc123", view.selectedWaveId);
    Assert.assertEquals("example.com/w+abc123", selectedState[0].getSelectedWaveId());
    Assert.assertTrue(userNavigation[0]);
  }

  // J-UI-3 (#1081, R-5.1): the optimistic-prepend stub appears at the head
  // of the rendered model immediately, before the gateway response lands.
  @Test
  public void onOptimisticDigestPrependsStubBeforeServerResponse() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Existing wave",
                    "Snippet",
                    "example.com/w+existing",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    // After start the rail shows the existing wave; now create a new one.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Existing wave",
            "Snippet",
            "example.com/w+existing",
            1L,
            0,
            1,
            Collections.singletonList("user@example.com"),
            "user@example.com",
            false));

    controller.onOptimisticDigest("example.com/w+new", "My new wave");

    List<J2clSearchDigestItem> items = view.lastModel.getDigestItems();
    Assert.assertFalse(items.isEmpty());
    Assert.assertEquals("example.com/w+new", items.get(0).getWaveId());
    Assert.assertEquals("My new wave", items.get(0).getTitle());
  }

  // J-UI-3: when the server's refresh response includes the new wave the
  // stub is dropped, leaving exactly one digest for that waveId.
  @Test
  public void serverResponseContainingNewWaveDropsOptimisticStub() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Existing",
                    "Snippet",
                    "example.com/w+existing",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // The next gateway hit returns the new wave (server has indexed it).
    gateway.response =
        responseWithDigests(
            new SidecarSearchResponse.Digest(
                "Server-side new",
                "Snippet",
                "example.com/w+new",
                2L,
                0,
                1,
                Collections.singletonList("user@example.com"),
                "user@example.com",
                false),
            new SidecarSearchResponse.Digest(
                "Existing",
                "Snippet",
                "example.com/w+existing",
                1L,
                0,
                1,
                Collections.singletonList("user@example.com"),
                "user@example.com",
                false));
    controller.onOptimisticDigest("example.com/w+new", "Stub title");

    List<J2clSearchDigestItem> items = view.lastModel.getDigestItems();
    int matches = 0;
    for (J2clSearchDigestItem item : items) {
      if ("example.com/w+new".equals(item.getWaveId())) {
        matches++;
      }
    }
    Assert.assertEquals("exactly one digest for the new wave", 1, matches);
    // The server-side title wins (stub has been replaced).
    Assert.assertEquals("Server-side new", view.lastModel.findDigestItem("example.com/w+new").getTitle());
  }

  // J-UI-3: when the server response does not yet include the new wave the
  // stub is kept at the head of the rail so the user does not see their
  // wave disappear during the search-index lag.
  @Test
  public void serverResponseMissingNewWaveKeepsOptimisticStub() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Other",
                    "Snippet",
                    "example.com/w+other",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // Subsequent search returns same set without the new wave.
    controller.onOptimisticDigest("example.com/w+slow-index", "Slow index");

    List<J2clSearchDigestItem> items = view.lastModel.getDigestItems();
    Assert.assertFalse("rail should still show entries", items.isEmpty());
    Assert.assertEquals(
        "stub should remain at head of rail",
        "example.com/w+slow-index",
        items.get(0).getWaveId());
  }

  // J-UI-3 (#1081, R-5.1) — when the server response confirms the new
  // wave is indexed, the stuck-stub timeout is cancelled so a late timer
  // firing cannot retire a digest the server has already accepted.
  // Mitigates the timeout-fires-before-server-response race (review).
  @Test
  public void serverResponseCancelsScheduledOptimisticTimeout() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Existing",
                    "Snippet",
                    "example.com/w+existing",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    int cancelsBefore = scheduler.cancelCallCount;

    // Server now has the new wave indexed; the next gateway hit returns it.
    gateway.response =
        responseWithDigests(
            new SidecarSearchResponse.Digest(
                "Server-side new",
                "Snippet",
                "example.com/w+confirm",
                2L,
                0,
                1,
                Collections.singletonList("user@example.com"),
                "user@example.com",
                false));
    controller.onOptimisticDigest("example.com/w+confirm", "Confirm me");

    Assert.assertTrue(
        "applyOptimisticStub must cancel the pending timeout",
        scheduler.cancelCallCount > cancelsBefore);
  }

  // J-UI-3: when the search index never returns the new wave, the
  // OPTIMISTIC_TIMEOUT_MS scheduler retires the stub so the rail does
  // not show a stale entry forever.
  @Test
  public void optimisticStubClearedAfterScheduledTimeoutFires() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Other",
                    "Snippet",
                    "example.com/w+other",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    controller.onOptimisticDigest("example.com/w+stuck", "Stuck stub");
    Assert.assertNotNull(view.lastModel.findDigestItem("example.com/w+stuck"));

    scheduler.fire();

    Assert.assertNull(
        "stub should be retired after the scheduled timeout fires",
        view.lastModel.findDigestItem("example.com/w+stuck"));
  }

  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CM-e: rapid back-to-
  // back creates each get their own pending entry; the second submit must
  // not erase the first stub. Both stubs stay visible until the server
  // refresh confirms each one independently.
  @Test
  public void backToBackCreatesPreserveAllPendingOptimisticStubs() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Existing",
                    "Snippet",
                    "example.com/w+existing",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    controller.onOptimisticDigest("example.com/w+first", "First wave");
    controller.onOptimisticDigest("example.com/w+second", "Second wave");

    Assert.assertNotNull(
        "first stub must survive a subsequent create",
        view.lastModel.findDigestItem("example.com/w+first"));
    Assert.assertNotNull(
        "second stub must be present alongside the first",
        view.lastModel.findDigestItem("example.com/w+second"));
    // Most-recent submission renders first.
    Assert.assertEquals(
        "example.com/w+second",
        view.lastModel.getDigestItems().get(0).getWaveId());
  }

  // J-UI-3 — codex P2 PRRT_kwDOBwxLXs5-CU22: a stub created under one query
  // must not be injected into an unrelated rail when the user navigates to
  // a different query before indexing catches up.
  @Test
  public void optimisticStubScopedToActiveQueryDoesNotLeakAcrossRails() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Inbox wave",
                    "Snippet",
                    "example.com/w+inbox",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    controller.onOptimisticDigest("example.com/w+pending", "Pending wave");
    Assert.assertNotNull(view.lastModel.findDigestItem("example.com/w+pending"));

    // User changes rail to a narrower query before indexing catches up.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Mention",
            "Snippet",
            "example.com/w+mention",
            2L,
            0,
            1,
            Collections.singletonList("user@example.com"),
            "user@example.com",
            false));
    controller.onQuerySubmitted("with:@me");

    Assert.assertNull(
        "pending stub from in:inbox must not appear in with:@me rail",
        view.lastModel.findDigestItem("example.com/w+pending"));

    // Navigating back to the original query brings the stub back.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Inbox wave",
            "Snippet",
            "example.com/w+inbox",
            1L,
            0,
            1,
            Collections.singletonList("user@example.com"),
            "user@example.com",
            false));
    controller.onQuerySubmitted("in:inbox");

    Assert.assertNotNull(
        "pending stub reappears once the user navigates back to its query",
        view.lastModel.findDigestItem("example.com/w+pending"));
  }

  // J-UI-3 — codex P2 PRRT_kwDOBwxLXs5-CU24: a transient gateway error
  // right after onOptimisticDigest must not wipe the just-created wave
  // from the rail. The error path re-applies the optimistic stub onto an
  // empty model so the user keeps seeing their wave.
  @Test
  public void refreshErrorPreservesOptimisticStub() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Other",
                    "Snippet",
                    "example.com/w+other",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // Next gateway call will fail.
    gateway.error = "search-cluster timeout";
    controller.onOptimisticDigest("example.com/w+resilient", "Survives outage");

    Assert.assertNotNull(
        "stub must survive a transient refresh failure",
        view.lastModel.findDigestItem("example.com/w+resilient"));
    Assert.assertEquals(
        "example.com/w+resilient",
        view.lastModel.getDigestItems().get(0).getWaveId());
  }

  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: the
  // optimistic stub must be scoped to the query active at SUBMIT time,
  // not the query active when the server-response callback fires. The
  // compose controller calls markCreateSubmitted() synchronously at
  // submit time; by the time onOptimisticDigest fires later, the user
  // may have switched queries — but the stub belongs to the rail they
  // were viewing when they clicked submit.
  @Test
  public void optimisticStubScopedBySubmitTimeQueryNotSuccessTimeQuery() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Inbox wave",
                    "Snippet",
                    "example.com/w+inbox",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // User clicks submit on in:inbox; compose controller stamps the
    // submit-time query before the bootstrap fetch.
    controller.markCreateSubmitted();

    // Before the server responds, the user navigates to a different rail.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Mention",
            "Snippet",
            "example.com/w+mention",
            2L,
            0,
            1,
            Collections.singletonList("user@example.com"),
            "user@example.com",
            false));
    controller.onQuerySubmitted("with:@me");

    // Now the server responds; the optimistic prepend must scope to
    // in:inbox (the submit-time query), NOT with:@me.
    controller.onOptimisticDigest("example.com/w+pending", "Pending wave");
    Assert.assertNull(
        "stub belongs to in:inbox, not the active with:@me rail",
        view.lastModel.findDigestItem("example.com/w+pending"));

    // Switching back to in:inbox brings the stub forward.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Inbox wave",
            "Snippet",
            "example.com/w+inbox",
            1L,
            0,
            1,
            Collections.singletonList("user@example.com"),
            "user@example.com",
            false));
    controller.onQuerySubmitted("in:inbox");
    Assert.assertNotNull(
        "stub reappears once the user is back on the submit-time query",
        view.lastModel.findDigestItem("example.com/w+pending"));
  }

  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7V: when the
  // user submits on query A and navigates to B before the success
  // callback fires, the immediate prepend in onOptimisticDigest must
  // not paint into B's rail. The stub is held in pendingStubs and
  // surfaces only when the user returns to A.
  @Test
  public void onOptimisticDigestSkipsImmediatePrependWhenSubmitQueryDoesNotMatch() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Inbox wave",
                    "Snippet",
                    "example.com/w+inbox",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    controller.markCreateSubmitted();
    gateway.response =
        responseWithDigests(
            new SidecarSearchResponse.Digest(
                "Mention",
                "Snippet",
                "example.com/w+mention",
                2L,
                0,
                1,
                Collections.singletonList("user@example.com"),
                "user@example.com",
                false));
    controller.onQuerySubmitted("with:@me");
    int rendersBefore = view.lastModel.getDigestItems().size();

    controller.onOptimisticDigest("example.com/w+pending", "Pending wave");

    Assert.assertNull(
        "stub must not be painted into the unrelated with:@me rail",
        view.lastModel.findDigestItem("example.com/w+pending"));
    Assert.assertEquals(
        "rail digest count is unchanged because the stub is held back",
        rendersBefore,
        view.lastModel.getDigestItems().size());
  }

  // J-UI-3 — codex P2 PRRT_kwDOBwxLXs5-DA7T: a failed create must drop
  // its pending submit-query stamp so the next successful create
  // consumes its OWN stamp rather than the stale one from the failure.
  @Test
  public void discardOldestSubmitStampPreventsStaleQueryFromLeakingForward() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Mention",
                    "Snippet",
                    "example.com/w+mention",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);
    // Failed create on in:inbox: stamp is queued, then dropped on failure.
    controller.markCreateSubmitted();
    controller.discardOldestSubmitStamp();
    // User navigates to with:@me and submits a successful create there.
    controller.onQuerySubmitted("with:@me");
    controller.markCreateSubmitted();
    controller.onOptimisticDigest("example.com/w+retry", "Retry wave");

    Assert.assertNotNull(
        "successful retry must consume its OWN submit stamp (with:@me), not the discarded in:inbox one",
        view.lastModel.findDigestItem("example.com/w+retry"));
  }

  // J-UI-3 — codex P2 PRRT_kwDOBwxLXs5-DA7T: a failed create must not leave a stale
  // submit-query stamp in the queue. Without cancelPendingCreateSubmit() the next
  // successful create consumes the old stamp and scopes its stub to the wrong rail.
  @Test
  public void cancelPendingCreateSubmitClearsStaleQueueEntry() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Inbox wave", "Snippet", "example.com/w+inbox", 1L, 0, 1,
                    Collections.singletonList("user@example.com"), "user@example.com", false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // First submit on in:inbox stamps the queue, then the create fails.
    controller.markCreateSubmitted();
    controller.cancelPendingCreateSubmit();

    // User switches to with:@me and submits successfully.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Mention", "Snippet", "example.com/w+mention", 2L, 0, 1,
            Collections.singletonList("user@example.com"), "user@example.com", false));
    controller.onQuerySubmitted("with:@me");
    controller.markCreateSubmitted();
    controller.onOptimisticDigest("example.com/w+new", "New wave");

    // Stub must be scoped to with:@me (the submit-time query for the successful create).
    Assert.assertNotNull(
        "stub must appear in with:@me rail (the actual submit query)",
        view.lastModel.findDigestItem("example.com/w+new"));

    // Switching back to in:inbox must NOT show the stub.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Inbox wave", "Snippet", "example.com/w+inbox", 1L, 0, 1,
            Collections.singletonList("user@example.com"), "user@example.com", false));
    controller.onQuerySubmitted("in:inbox");
    Assert.assertNull(
        "stub must not leak into in:inbox (the stale failed-create query)",
        view.lastModel.findDigestItem("example.com/w+new"));
  }

  // J-UI-3 — codex P2 PRRT_kwDOBwxLXs5-DA7V: if the user changes rail before
  // the success callback fires, the immediate prepend in onOptimisticDigest must
  // NOT inject the stub into the wrong rail's render.
  @Test
  public void onOptimisticDigestDoesNotImmediatelyPrependToWrongRail() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Inbox wave", "Snippet", "example.com/w+inbox", 1L, 0, 1,
                    Collections.singletonList("user@example.com"), "user@example.com", false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // User submits on in:inbox, stamps the query.
    controller.markCreateSubmitted();

    // Before the server responds, user navigates to with:@me.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Mention", "Snippet", "example.com/w+mention", 2L, 0, 1,
            Collections.singletonList("user@example.com"), "user@example.com", false));
    controller.onQuerySubmitted("with:@me");

    // Success fires; the immediate render must NOT inject stub into with:@me.
    controller.onOptimisticDigest("example.com/w+newwave", "New wave");
    Assert.assertNull(
        "stub belongs to in:inbox, must not appear in with:@me immediately",
        view.lastModel.findDigestItem("example.com/w+newwave"));

    // Navigating back to in:inbox should bring the stub forward.
    gateway.response = responseWithDigests(
        new SidecarSearchResponse.Digest(
            "Inbox wave", "Snippet", "example.com/w+inbox", 1L, 0, 1,
            Collections.singletonList("user@example.com"), "user@example.com", false));
    controller.onQuerySubmitted("in:inbox");
    Assert.assertNotNull(
        "stub reappears on the correct in:inbox rail",
        view.lastModel.findDigestItem("example.com/w+newwave"));
  }

  // J-UI-3 — when no markCreateSubmitted() stamp is queued (legacy path),
  // onOptimisticDigest falls back to the success-time query so existing
  // wiring continues to work.
  @Test
  public void optimisticStubFallsBackToActiveQueryWhenNoSubmitStampQueued() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Other",
                    "Snippet",
                    "example.com/w+other",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    // No markCreateSubmitted() — direct onOptimisticDigest.
    controller.onOptimisticDigest("example.com/w+pending", "Pending");

    Assert.assertNotNull(
        "fallback path scopes the stub to the active query",
        view.lastModel.findDigestItem("example.com/w+pending"));
  }

  // J-UI-3 (#1081, R-5.1) — CodeRabbit minor PRRT_kwDOBwxLXs5-Cpes: when
  // an optimistic stub is prepended, the rail header's wave-count text
  // must reflect the new entry instead of showing the stale server count.
  // The current implementation appends "(+N pending)" so the user sees
  // "1 waves · 0 unread (+1 pending)" while indexing catches up.
  @Test
  public void optimisticStubAppendsPendingSuffixToWaveCountText() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Existing",
                    "Snippet",
                    "example.com/w+existing",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    controller.onOptimisticDigest("example.com/w+pending", "Pending wave");

    Assert.assertTrue(
        "wave-count text must surface a pending suffix, got: "
            + view.lastModel.getWaveCountText(),
        view.lastModel.getWaveCountText().endsWith("(+1 pending)"));
  }

  // J-UI-3: a second onOptimisticDigest before search returns must replace the
  // "(+1 pending)" suffix with "(+2 pending)" rather than accumulating both.
  @Test
  public void backToBackCreatesUpdatePendingCountText() {
    FakeGateway gateway = new FakeGateway(responseWithDigests());
    FakeView view = new FakeView();
    FakeOptimisticScheduler scheduler = new FakeOptimisticScheduler();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200, scheduler);
    controller.start("in:inbox", null);

    controller.onOptimisticDigest("example.com/w+a", "Wave A");
    String afterFirst = view.lastModel.getWaveCountText();
    Assert.assertTrue(
        "first create must show (+1 pending), got: " + afterFirst,
        afterFirst.endsWith("(+1 pending)"));

    controller.onOptimisticDigest("example.com/w+b", "Wave B");
    String afterSecond = view.lastModel.getWaveCountText();
    Assert.assertTrue(
        "second create must show (+2 pending), got: " + afterSecond,
        afterSecond.endsWith("(+2 pending)"));
    Assert.assertFalse(
        "second create must not retain the (+1 pending) marker, got: " + afterSecond,
        afterSecond.contains("(+1 pending)"));
  }

  // J-UI-3: refreshSearch is idempotent and re-issues the active query
  // without resetting selection or page size.
  @Test
  public void refreshSearchReissuesActiveQuery() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    1,
                    Collections.singletonList("user@example.com"),
                    "user@example.com",
                    false)));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway, view, (state, digestItem, userNavigation) -> { }, 1200);
    controller.start("in:inbox", null);
    int issuedAfterStart = gateway.searchCallCount;

    controller.refreshSearch();

    Assert.assertEquals("in:inbox", gateway.lastQuery);
    Assert.assertEquals(issuedAfterStart + 1, gateway.searchCallCount);
  }

  // J-UI-3 (#1081, R-5.1): captures the stuck-stub timeout runnables so
  // tests can drive them deterministically without sleeping. Supports
  // multiple concurrent schedules (one per pending optimistic stub) so the
  // multi-stub path can be exercised. fire() runs the most-recently
  // scheduled runnable; fireAll() drains the queue.
  private static final class FakeOptimisticScheduler
      implements J2clSearchPanelController.OptimisticScheduler {
    private final java.util.LinkedHashMap<Object, Runnable> pendingByHandle =
        new java.util.LinkedHashMap<Object, Runnable>();
    private Runnable pending;
    private Object pendingHandle;
    private int lastDelayMs = -1;
    private int cancelCallCount;

    @Override
    public Object scheduleTimeout(int delayMs, Runnable action) {
      this.lastDelayMs = delayMs;
      this.pending = action;
      this.pendingHandle = new Object();
      pendingByHandle.put(pendingHandle, action);
      return pendingHandle;
    }

    @Override
    public void cancel(Object handle) {
      cancelCallCount++;
      pendingByHandle.remove(handle);
      if (handle != null && handle == pendingHandle) {
        syncPendingToLatest();
      }
    }

    void fire() {
      Runnable next = pending;
      if (pendingHandle != null) {
        pendingByHandle.remove(pendingHandle);
      }
      syncPendingToLatest();
      if (next != null) {
        next.run();
      }
    }

    private void syncPendingToLatest() {
      pending = null;
      pendingHandle = null;
      for (java.util.Map.Entry<Object, Runnable> entry : pendingByHandle.entrySet()) {
        pendingHandle = entry.getKey();
        pending = entry.getValue();
      }
    }

    void fireAll() {
      java.util.List<Runnable> snapshot = new java.util.ArrayList<>(pendingByHandle.values());
      pendingByHandle.clear();
      pending = null;
      pendingHandle = null;
      for (Runnable r : snapshot) {
        r.run();
      }
    }

    int pendingCount() {
      return pendingByHandle.size();
    }
  }

  private static final class FakeGateway implements J2clSearchPanelController.SearchGateway {
    private SidecarSearchResponse response;
    private String error;
    private String lastQuery;
    private int lastIndex = -1;
    private int lastNumResults = -1;
    // J-UI-3 (#1081): expose a search-call counter so refreshSearch tests can
    // distinguish "called again" from "still has the old lastQuery value".
    private int searchCallCount;

    private FakeGateway(SidecarSearchResponse response) {
      this.response = response;
    }

    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      onSuccess.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
    }

    @Override
    public void search(
        String query,
        int index,
        int numResults,
        J2clSearchPanelController.SuccessCallback<SidecarSearchResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      lastQuery = query;
      lastIndex = index;
      lastNumResults = numResults;
      searchCallCount++;
      if (error != null) {
        onError.accept(error);
        return;
      }
      onSuccess.accept(response);
    }
  }

  private static final class FakeView implements J2clSearchPanelController.View {
    private J2clSearchViewListener listener;
    private String query;
    private boolean loading;
    private String sessionSummary;
    private String status;
    private boolean error;
    private J2clSearchResultModel lastModel = J2clSearchResultModel.empty("");
    private String selectedWaveId;
    private final List<String> updateDigestUnreadInvocations = new ArrayList<String>();
    /** J-UI-2 (#1080): last announce-navigation label, or null if never called. */
    private String lastAnnouncement;
    /** J-UI-2 (#1080): count of focusActiveFolder invocations. */
    private int focusActiveFolderInvocations;

    @Override
    public void bind(J2clSearchViewListener listener) {
      this.listener = listener;
    }

    @Override
    public void setQuery(String query) {
      this.query = query;
    }

    @Override
    public void setLoading(boolean loading) {
      this.loading = loading;
    }

    @Override
    public void setSessionSummary(String summary) {
      this.sessionSummary = summary;
    }

    @Override
    public void setStatus(String status, boolean error) {
      this.status = status;
      this.error = error;
    }

    @Override
    public void render(J2clSearchResultModel model) {
      this.lastModel = model;
    }

    @Override
    public void setSelectedWaveId(String waveId) {
      this.selectedWaveId = waveId;
    }

    @Override
    public void announceNavigation(String label) {
      this.lastAnnouncement = label;
    }

    @Override
    public void focusActiveFolder() {
      this.focusActiveFolderInvocations++;
    }

    @Override
    public boolean updateDigestUnread(String waveId, int unreadCount) {
      // F-4 (#1039 / R-4.4) — emulate the production view's contract:
      // return true only when the wave id is known to the model.
      if (lastModel.findDigestItem(waveId) == null) {
        return false;
      }
      updateDigestUnreadInvocations.add(waveId + "=" + unreadCount);
      return true;
    }
  }

  private static SidecarSearchResponse responseWithDigests(SidecarSearchResponse.Digest... digests) {
    return new SidecarSearchResponse("in:inbox", digests.length, Arrays.asList(digests));
  }
}

package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadataClient;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarFragmentsResponse;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;

@J2clTestInput(J2clSelectedWaveControllerTest.class)
public class J2clSelectedWaveControllerTest {
  @Test
  public void selectingWaveRetriesLongEnoughToRecoverAfterServerRestart() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.fireDisconnect(0);
    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(Arrays.asList(250), harness.scheduledDelays);

    harness.runScheduledRetry(0);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(1, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500), harness.scheduledDelays);

    harness.runScheduledRetry(1);
    Assert.assertEquals(3, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(2, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000), harness.scheduledDelays);

    harness.runScheduledRetry(2);
    Assert.assertEquals(4, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(3, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000, 2000), harness.scheduledDelays);

    harness.runScheduledRetry(3);
    Assert.assertEquals(5, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(4, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000, 2000, 2000), harness.scheduledDelays);

    harness.runScheduledRetry(4);
    Assert.assertEquals(6, harness.bootstrapAttempts.size());
    harness.resolveBootstrap(5);
    Assert.assertEquals(2, harness.openCount);
    harness.deliverUpdate(1, "Recovered after restart");

    Assert.assertFalse((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Live updates reconnected.", harness.modelValue("getStatusText"));
    Assert.assertEquals(Arrays.asList("Recovered after restart"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void selectingWaveStillStopsAfterBoundedReconnectBudget() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.fireDisconnect(0);

    for (int attempt = 1; attempt <= 8; attempt++) {
      Assert.assertFalse((Boolean) harness.modelValue("isError"));
      harness.runScheduledRetry(attempt - 1);
      Assert.assertEquals(attempt + 1, harness.bootstrapAttempts.size());
      Assert.assertEquals(1, harness.openCount);
      harness.rejectBootstrap(attempt, "Network failure for /");
    }

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals(
        Arrays.asList(250, 500, 1000, 2000, 2000, 2000, 2000, 2000), harness.scheduledDelays);
    Assert.assertTrue(
        String.valueOf(harness.modelValue("getStatusText")).contains("Selected wave disconnected"));
    Assert.assertTrue(
        String.valueOf(harness.modelValue("getDetailText")).contains("8 reconnect attempts"));
  }

  @Test
  public void bootstrapErrorRendersSelectedWaveError() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.rejectBootstrap(0, "bootstrap boom");

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Unable to open selected wave.", harness.modelValue("getStatusText"));
    Assert.assertEquals("bootstrap boom", harness.modelValue("getDetailText"));
  }

  @Test
  public void transportOpenErrorRendersSelectedWaveError() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.failOpen(0, "socket boom");

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Selected wave stream failed.", harness.modelValue("getStatusText"));
    Assert.assertEquals("socket boom", harness.modelValue("getDetailText"));
    Assert.assertEquals(1, harness.closedCount);
  }

  @Test
  public void transportStreamErrorRetriesSelectedWave() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");
    harness.failOpen(0, "socket boom");

    Assert.assertFalse((Boolean) harness.modelValue("isError"));
    Assert.assertEquals(Arrays.asList(250), harness.scheduledDelays);
    Assert.assertEquals(1, harness.closedCount);

    harness.runScheduledRetry(0);
    harness.resolveBootstrap(1);
    harness.deliverUpdate(1, "Recovered after socket error");

    Assert.assertEquals("Live updates reconnected.", harness.modelValue("getStatusText"));
    Assert.assertEquals(
        Arrays.asList("Recovered after socket error"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void recoveredStreamResetsReconnectBudgetForNextOutage() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.fireDisconnect(0);
    Assert.assertEquals(Arrays.asList(250), harness.scheduledDelays);

    harness.runScheduledRetry(0);
    harness.resolveBootstrap(1);
    harness.deliverUpdate(1, "Recovered after restart");
    Assert.assertEquals("Live updates reconnected.", harness.modelValue("getStatusText"));

    harness.fireDisconnect(1);
    Assert.assertEquals(Arrays.asList(250, 250), harness.scheduledDelays);
  }

  @Test
  public void staleBootstrapSuccessIsIgnoredAfterRapidReselection() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.selectWave(controller, "example.com/w+b", null);

    harness.resolveBootstrap(0);
    Assert.assertEquals(0, harness.openCount);

    harness.resolveBootstrap(1);
    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals("example.com/w+b", harness.openAttempts.get(0).waveId);
  }

  @Test
  public void selectingWaveDefaultsToExplicitViewportLimitHintWhenViewHasNoAnchor()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    SidecarViewportHints hints = harness.openAttempts.get(0).viewportHints;
    Assert.assertNull(hints.getStartBlipId());
    Assert.assertNull(hints.getDirection());
    Assert.assertEquals(Integer.valueOf(0), hints.getLimit());
  }

  @Test
  public void selectingWaveDefaultsToExplicitViewportLimitHintWhenViewReturnsNoHints()
      throws Exception {
    Harness harness = new Harness();
    harness.initialViewportHints = SidecarViewportHints.none();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    SidecarViewportHints hints = harness.openAttempts.get(0).viewportHints;
    Assert.assertNull(hints.getStartBlipId());
    Assert.assertNull(hints.getDirection());
    Assert.assertEquals(Integer.valueOf(0), hints.getLimit());
  }

  @Test
  public void selectingWaveUsesViewProvidedServerFirstViewportAnchor() throws Exception {
    Harness harness = new Harness();
    harness.initialViewportHints = new SidecarViewportHints("b+server", "forward", null);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    SidecarViewportHints hints = harness.openAttempts.get(0).viewportHints;
    Assert.assertEquals("b+server", hints.getStartBlipId());
    Assert.assertEquals("forward", hints.getDirection());
    Assert.assertNull(hints.getLimit());
  }

  @Test
  public void staleOpenUpdateIsIgnoredAfterSwitchingToDifferentWave() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.resolveBootstrap(0);
    Assert.assertEquals(1, harness.openCount);

    harness.selectWave(controller, "example.com/w+b", null);
    Assert.assertEquals(1, harness.closedCount);
    harness.resolveBootstrap(1);
    Assert.assertEquals(2, harness.openCount);

    harness.deliverUpdate(0, "stale A");
    Assert.assertEquals("Opening selected wave.", harness.modelValue("getStatusText"));

    harness.deliverUpdate(1, "fresh B");
    Assert.assertEquals(Arrays.asList("fresh B"), harness.modelValue("getContentEntries"));
    Assert.assertEquals("example.com/w+b", harness.modelValue("getSelectedWaveId"));
  }

  @Test
  public void sameWaveReselectRefreshesDigestMetadataWithoutReopeningSocket() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "Old snippet", 3));
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.selectWave(controller, "example.com/w+1", digest("Wave A updated", "New snippet", 0));

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals("Wave A updated", harness.modelValue("getTitleText"));
    Assert.assertEquals("Selected digest is read.", harness.modelValue("getUnreadText"));
    Assert.assertEquals("New snippet", harness.modelValue("getSnippetText"));
  }

  @Test
  public void refreshSelectedWaveReopensSameWaveAndClearsWriteSessionWhileLoading()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.refreshSelectedWave(controller);

    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
    Assert.assertNull(harness.modelValue("getWriteSession"));

    harness.resolveBootstrap(1);
    Assert.assertEquals(2, harness.openCount);
    harness.deliverUpdate(1, "Reply now visible");

    Assert.assertEquals(Arrays.asList("Reply now visible"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void channelEstablishmentUpdateIsIgnoredUntilRealWaveletArrives() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    harness.deliverRawUpdate(
        0,
        new SidecarSelectedWaveUpdate(
            1,
            "example.com!w+1/~/dummy+root",
            true,
            "chan-1",
            -1L,
            null,
            Arrays.asList("user@example.com"),
            new ArrayList<SidecarSelectedWaveDocument>(),
            null));

    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));

    harness.deliverUpdate(0, "real content");
    Assert.assertEquals(Arrays.asList("real content"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void selectedWaveUpdateBuildsWriteSessionWithHistoryHash() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "real content");

    J2clSidecarWriteSession writeSession =
        (J2clSidecarWriteSession) harness.modelValue("getWriteSession");
    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
  }

  @Test
  public void snapshotOnlyUpdateRendersDocumentTextWhenFragmentsAreMissing() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "Digest snippet", 3));
    harness.resolveBootstrap(0);
    harness.deliverSnapshotOnlyUpdate(0, "Welcome to SupaWave");

    Assert.assertEquals(Arrays.asList("Welcome to SupaWave"), harness.modelValue("getContentEntries"));
    Assert.assertEquals("Digest snippet", harness.modelValue("getSnippetText"));
    Assert.assertFalse((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void selectedWaveUpdatePromotesWriteSessionMetadata() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    J2clSidecarWriteSession writeSession =
        (J2clSidecarWriteSession) harness.modelValue("getWriteSession");

    Assert.assertNotNull(writeSession);
    Assert.assertEquals("example.com/w+1", writeSession.getSelectedWaveId());
    Assert.assertEquals("chan-1", writeSession.getChannelId());
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("b+root", writeSession.getReplyTargetBlipId());
  }

  @Test
  public void versionZeroSelectedWaveUpdateStillBuildsWriteSession() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(
        0,
        new SidecarSelectedWaveUpdate(
            1,
            "example.com!w+1/~/conv+root",
            true,
            "chan-1",
            0L,
            "ZERO",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 0L, 1L, "Hello from version zero")),
            new SidecarSelectedWaveFragments(
                0L,
                0L,
                0L,
                Arrays.asList(
                    new SidecarSelectedWaveFragmentRange("manifest", 0L, 0L),
                    new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 0L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                    new SidecarSelectedWaveFragment("blip:b+root", "Hello from version zero", 0, 0)))));

    J2clSidecarWriteSession writeSession =
        (J2clSidecarWriteSession) harness.modelValue("getWriteSession");

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(0L, writeSession.getBaseVersion());
    Assert.assertEquals("ZERO", writeSession.getHistoryHash());
  }

  @Test
  public void updateSchedulesReadStateFetchThatReplacesDigestUnreadText() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "snippet", 3));
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "fresh content");

    Assert.assertEquals(1, harness.pendingReadStateDispatches.size());
    Assert.assertEquals(
        "3 unread in the selected digest.", harness.modelValue("getUnreadText"));

    harness.runPendingReadStateDispatch(0);
    Assert.assertEquals(1, harness.readStateAttempts.size());

    harness.resolveReadState(0, 5, false);
    Assert.assertEquals("5 unread.", harness.modelValue("getUnreadText"));
    Assert.assertEquals(5, harness.modelValue("getUnreadCount"));
    Assert.assertTrue((Boolean) harness.modelValue("isReadStateKnown"));
  }

  @Test
  public void readStateFetchFailureKeepsPriorCountAndFlagsStale() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "snippet", 1));
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "content");
    harness.runPendingReadStateDispatch(0);
    harness.resolveReadState(0, 2, false);
    Assert.assertEquals("2 unread.", harness.modelValue("getUnreadText"));

    harness.deliverUpdate(0, "another content");
    harness.runPendingReadStateDispatch(1);
    harness.rejectReadState(1, "network blip");

    Assert.assertEquals("2 unread.", harness.modelValue("getUnreadText"));
    Assert.assertEquals(2, harness.modelValue("getUnreadCount"));
    Assert.assertTrue((Boolean) harness.modelValue("isReadStateStale"));
  }

  @Test
  public void outOfOrderReadStateResponsesOnlyApplyTheLatest() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "content A");
    harness.runPendingReadStateDispatch(0);
    Assert.assertEquals(1, harness.readStateAttempts.size());

    harness.deliverUpdate(0, "content B");
    harness.runPendingReadStateDispatch(1);
    Assert.assertEquals(2, harness.readStateAttempts.size());

    // Second fetch resolves first — becomes the latest applied.
    harness.resolveReadState(1, 7, false);
    Assert.assertEquals("7 unread.", harness.modelValue("getUnreadText"));

    // First fetch resolves later with stale data — must be ignored.
    harness.resolveReadState(0, 1, false);
    Assert.assertEquals("7 unread.", harness.modelValue("getUnreadText"));
    Assert.assertEquals(7, harness.modelValue("getUnreadCount"));
  }

  @Test
  public void generationBumpDiscardsReadStateResponseFromPriorSelection() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "A content");
    harness.runPendingReadStateDispatch(0);
    Assert.assertEquals(1, harness.readStateAttempts.size());

    harness.selectWave(controller, "example.com/w+b", null);
    harness.resolveBootstrap(1);
    harness.deliverUpdate(1, "B content");

    // Stale response from wave+a arrives AFTER the re-selection to wave+b.
    harness.resolveReadState(0, 9, false);

    Assert.assertFalse((Boolean) harness.modelValue("isReadStateKnown"));
    Assert.assertNotEquals(9, harness.modelValue("getUnreadCount"));
  }

  @Test
  public void visibilityChangeTriggersReadStateFetchForActiveSelection() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createControllerWithVisibility(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "content");
    harness.runPendingReadStateDispatch(0);
    harness.resolveReadState(0, 1, false);
    int baseline = harness.readStateAttempts.size();

    harness.fireVisibilityVisible();
    // visibilitychange schedules another debounce tick.
    Assert.assertTrue(harness.pendingReadStateDispatches.size() >= 2);
    harness.runPendingReadStateDispatch(harness.pendingReadStateDispatches.size() - 1);
    Assert.assertEquals(baseline + 1, harness.readStateAttempts.size());
  }

  @Test
  public void visibilityChangeWithoutSelectionIsHarmless() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createControllerWithVisibility(false);

    harness.fireVisibilityVisible();

    Assert.assertEquals(0, harness.pendingReadStateDispatches.size());
    Assert.assertEquals(0, harness.readStateAttempts.size());
  }

  @Test
  public void debounceCoalescesConsecutiveUpdatesIntoOneFetch() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "first");
    harness.deliverUpdate(0, "second");
    // Two dispatches queued because each deliverUpdate schedules one.
    Assert.assertEquals(2, harness.pendingReadStateDispatches.size());

    // Running the first: the debounce token has moved on, so this no-ops.
    harness.runPendingReadStateDispatch(0);
    Assert.assertEquals(0, harness.readStateAttempts.size());

    // Running the latest dispatches exactly one fetch.
    harness.runPendingReadStateDispatch(1);
    Assert.assertEquals(1, harness.readStateAttempts.size());
  }

  @Test
  public void viewportEdgeDispatchesFragmentsFetchWithCurrentVersionWindow() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    FragmentFetchAttempt attempt = harness.fragmentFetchAttempts.get(0);
    Assert.assertEquals("example.com/w+1", attempt.waveId);
    Assert.assertEquals("b+next", attempt.startBlipId);
    Assert.assertEquals("forward", attempt.direction);
    Assert.assertEquals(5, attempt.limit);
    Assert.assertEquals(40L, attempt.startVersion);
    Assert.assertEquals(44L, attempt.endVersion);
  }

  @Test
  public void viewportEdgeFetchMergesGrowthWindowWithoutReopeningSocket() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Next loaded", "Tail loaded"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void duplicateViewportEdgeRequestsAreCoalescedWhileFetchInFlight()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.requestViewportEdge(controller, "b+next", "forward");

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void oppositeViewportEdgesCanFetchConcurrently() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.requestViewportEdge(controller, "b+before", "backward");

    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("forward", harness.fragmentFetchAttempts.get(0).direction);
    Assert.assertEquals("backward", harness.fragmentFetchAttempts.get(1).direction);
  }

  @Test
  public void viewportEdgeBeforeAnyViewportStateDoesNotFetchFragments() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    harness.requestViewportEdge(controller, "b+next", "forward");

    Assert.assertEquals(0, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void viewportEdgeCanUseTrailingAnchorFallback() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, null, "forward");

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+root", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void viewportEdgeWithoutAnchorOrLoadedBlipDoesNotFetchFragments() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithOnlyPlaceholder());

    harness.requestViewportEdge(controller, null, "forward");

    Assert.assertEquals(0, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void backwardViewportGrowthPrependsLoadedWindow() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+before", "backward");
    harness.resolveFragmentFetch(
        0, fragmentsResponseForBlips("b+before", "Before loaded", null, null));

    Assert.assertEquals(
        Arrays.asList("Before loaded", "Root already loaded"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void viewportEdgeCanFetchAgainAfterSuccessfulGrowth() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));
    harness.requestViewportEdge(controller, "b+tail", "forward");

    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void sameViewportEdgeCanFetchAgainAfterSuccessfulGrowth() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void staleViewportGrowthResponseIsDroppedAfterLiveUpdateChangesVersion()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    harness.deliverRawUpdate(
        0,
        update(
            "example.com!w+1/example.com!conv+root",
            "Live update changed base version",
            45L,
            "EFGH"));
    harness.resolveFragmentFetch(0, fragmentsResponse("Stale next", "Stale tail"));

    Assert.assertEquals(
        Arrays.asList("Live update changed base version"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void staleViewportGrowthResponseIsDroppedAfterLiveUpdateChangesHashOnly()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    harness.deliverRawUpdate(
        0,
        update(
            "example.com!w+1/example.com!conv+root",
            "Live update changed history hash",
            44L,
            "EFGH"));
    harness.resolveFragmentFetch(0, fragmentsResponse("Stale next", "Stale tail"));

    Assert.assertEquals(
        Arrays.asList("Live update changed history hash"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void viewportGrowthResponseSurvivesNullToPresentWriteSessionTransition()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholderWithoutWriteSession("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    harness.deliverRawUpdate(0, updateWithPlaceholder("Root refreshed with write session"));
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    Assert.assertEquals(
        Arrays.asList("Root refreshed with write session", "Next loaded", "Tail loaded"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void viewportGrowthFailureKeepsLoadedWindowAndAllowsRetry() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.rejectFragmentFetch(0, "fragment timeout");

    Assert.assertEquals(
        Arrays.asList("Root already loaded"), harness.modelValue("getContentEntries"));
    Assert.assertTrue(
        String.valueOf(harness.modelValue("getDetailText")).contains("fragment timeout"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
    harness.resolveFragmentFetch(1, fragmentsResponse("Recovered next", "Recovered tail"));

    Assert.assertEquals("More selected-wave content loaded.", harness.modelValue("getStatusText"));
    Assert.assertEquals("", harness.modelValue("getDetailText"));
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Recovered next", "Recovered tail"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void viewportGrowthFailureCoalescesSameEdgeReentryDuringRender() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.onNextRender =
        () -> {
          try {
            harness.requestViewportEdge(controller, "b+next", "forward");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    harness.rejectFragmentFetch(0, "fragment timeout");

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    harness.requestViewportEdge(controller, "b+next", "forward");
    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void viewportGrowthSuccessCoalescesSameEdgeReentryDuringRender() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.onNextRender =
        () -> {
          try {
            harness.requestViewportEdge(controller, "b+next", "forward");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    harness.requestViewportEdge(controller, "b+next", "forward");
    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void refreshClearsInFlightViewportEdgeTracking() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    harness.refreshSelectedWave(controller);
    harness.resolveBootstrap(1);
    harness.deliverRawUpdate(1, updateWithPlaceholder("Root reopened"));
    harness.requestViewportEdge(controller, "b+next", "forward");

    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
  }

  @Test
  public void selectedWaveHydratesPendingFragmentAttachments() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(
        0,
        "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
            + "<caption>Hero diagram</caption></image> outro");

    Assert.assertEquals(1, harness.attachmentMetadataAttempts.size());
    Assert.assertEquals(
        Arrays.asList("example.com/att+hero"),
        harness.attachmentMetadataAttempts.get(0).attachmentIds);
    Assert.assertTrue(harness.firstReadAttachment().isMetadataPending());

    harness.resolveAttachmentMetadata(
        0,
        Arrays.asList(
            attachmentMetadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachments/hero.png",
                "/thumbnails/hero.png")),
        Collections.<String>emptyList());

    J2clAttachmentRenderModel attachment = harness.firstReadAttachment();
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.canOpen());
    Assert.assertEquals("/attachments/hero.png", attachment.getOpenUrl());
    Assert.assertEquals("Hero diagram", attachment.getCaption());
    Assert.assertEquals(1, harness.attachmentMetadataAttempts.size());
  }

  @Test
  public void selectedWaveMarksAttachmentMetadataFailure() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(
        0,
        "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
            + "<caption>Hero diagram</caption></image> outro");

    harness.rejectAttachmentMetadata(0, "metadata network failure");

    J2clAttachmentRenderModel attachment = harness.firstReadAttachment();
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.isMetadataFailure());
    Assert.assertFalse(attachment.canOpen());
    Assert.assertTrue(attachment.getStatusText().contains("metadata network failure"));
    Assert.assertEquals(1, harness.attachmentMetadataAttempts.size());
  }

  @Test
  public void selectedWaveMarksAttachmentMetadataDispatchThrowAsFailure() throws Exception {
    Harness harness = new Harness();
    harness.attachmentMetadataDispatchError = "metadata dispatch failed";
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(
        0,
        "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
            + "<caption>Hero diagram</caption></image> outro");

    J2clAttachmentRenderModel attachment = harness.firstReadAttachment();
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.isMetadataFailure());
    Assert.assertTrue(attachment.getStatusText().contains("metadata dispatch failed"));
    Assert.assertEquals(0, harness.attachmentMetadataAttempts.size());
  }

  @Test
  public void selectedWaveTreatsEmptyMetadataSuccessAsMissing() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(
        0,
        "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
            + "<caption>Hero diagram</caption></image> outro");

    harness.resolveAttachmentMetadata(
        0,
        Collections.<J2clAttachmentMetadata>emptyList(),
        Collections.<String>emptyList());

    J2clAttachmentRenderModel attachment = harness.firstReadAttachment();
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.isMetadataFailure());
    Assert.assertFalse(attachment.canOpen());
    Assert.assertEquals(1, harness.attachmentMetadataAttempts.size());
  }

  @Test
  public void staleAttachmentMetadataCallbackDoesNotClearNewerInFlightFetch()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(
        0,
        "Wave A <image attachment=\"example.com/att+old\" display-size=\"medium\">"
            + "<caption>Old</caption></image>");
    Assert.assertEquals(1, harness.attachmentMetadataAttempts.size());

    harness.selectWave(controller, "example.com/w+b", null);
    harness.resolveBootstrap(1);
    harness.deliverUpdate(
        1,
        "Wave B <image attachment=\"example.com/att+new\" display-size=\"medium\">"
            + "<caption>New</caption></image>");
    Assert.assertEquals(2, harness.attachmentMetadataAttempts.size());

    harness.resolveAttachmentMetadata(
        0,
        Arrays.asList(
            attachmentMetadata(
                "example.com/att+old",
                "old.png",
                "image/png",
                "/attachments/old.png",
                "/thumbnails/old.png")),
        Collections.<String>emptyList());
    Assert.assertEquals("example.com/att+new", harness.firstReadAttachment().getAttachmentId());
    Assert.assertTrue(harness.firstReadAttachment().isMetadataPending());

    harness.deliverUpdate(
        1,
        "Wave B <image attachment=\"example.com/att+new\" display-size=\"medium\">"
            + "<caption>New</caption></image>");
    Assert.assertEquals(2, harness.attachmentMetadataAttempts.size());

    harness.resolveAttachmentMetadata(
        1,
        Arrays.asList(
            attachmentMetadata(
                "example.com/att+new",
                "new.png",
                "image/png",
                "/attachments/new.png",
                "/thumbnails/new.png")),
        Collections.<String>emptyList());
    Assert.assertFalse(harness.firstReadAttachment().isMetadataPending());
    Assert.assertEquals("/attachments/new.png", harness.firstReadAttachment().getOpenUrl());
  }

  private static J2clSearchDigestItem digest(String title, String snippet, int unreadCount) {
    return new J2clSearchDigestItem(
        "example.com/w+1", title, snippet, "user@example.com", unreadCount, 2, 1234L, false);
  }

  private static J2clAttachmentMetadata attachmentMetadata(
      String attachmentId,
      String fileName,
      String mimeType,
      String attachmentUrl,
      String thumbnailUrl) {
    return new J2clAttachmentMetadata(
        attachmentId,
        "example.com/w+1/~/conv+root",
        fileName,
        mimeType,
        4096L,
        "user@example.com",
        attachmentUrl,
        thumbnailUrl,
        new J2clAttachmentMetadata.ImageMetadata(1200, 800),
        new J2clAttachmentMetadata.ImageMetadata(320, 200),
        false);
  }

  private static final class Harness {
    private int openCount;
    private int closedCount;
    private final List<Integer> scheduledDelays = new ArrayList<Integer>();
    private final List<Runnable> scheduledRetries = new ArrayList<Runnable>();
    private final List<BootstrapAttempt> bootstrapAttempts = new ArrayList<BootstrapAttempt>();
    private final List<OpenAttempt> openAttempts = new ArrayList<OpenAttempt>();
    private final List<ReadStateFetchAttempt> readStateAttempts = new ArrayList<ReadStateFetchAttempt>();
    private final List<FragmentFetchAttempt> fragmentFetchAttempts =
        new ArrayList<FragmentFetchAttempt>();
    private final List<AttachmentMetadataAttempt> attachmentMetadataAttempts =
        new ArrayList<AttachmentMetadataAttempt>();
    private final List<Runnable> pendingReadStateDispatches = new ArrayList<Runnable>();
    private final List<Runnable> visibilityListeners = new ArrayList<Runnable>();
    private SidecarViewportHints initialViewportHints;
    private Object lastModel;
    private Runnable onNextRender;
    private Method onWaveSelectedMethod;
    private Method onWaveSelectedWithDigestMethod;
    private String attachmentMetadataDispatchError;

    private Object createControllerWithVisibility(boolean withScheduler) throws Exception {
      return createControllerInternal(withScheduler, /* injectVisibility= */ true);
    }

    private Object createController(boolean withScheduler) throws Exception {
      return createControllerInternal(withScheduler, /* injectVisibility= */ false);
    }

    private void fireVisibilityVisible() {
      for (Runnable listener : visibilityListeners) {
        listener.run();
      }
    }

    private Object createControllerInternal(boolean withScheduler, boolean injectVisibility)
        throws Exception {
      Class<?> controllerClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController");
      Class<?> gatewayClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$Gateway");
      Class<?> viewClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$View");
      Class<?> subscriptionClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$Subscription");

      Object gateway =
          Proxy.newProxyInstance(
              gatewayClass.getClassLoader(),
              new Class<?>[] {gatewayClass},
              (proxy, method, args) -> {
                if ("fetchRootSessionBootstrap".equals(method.getName())) {
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap>) args[0];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[1];
                  bootstrapAttempts.add(new BootstrapAttempt(success, error));
                  return null;
                }
                if ("openSelectedWave".equals(method.getName())) {
                  openCount++;
                  SidecarViewportHints viewportHints = (SidecarViewportHints) args[2];
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate>) args[3];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[4];
                  Runnable disconnect = (Runnable) args[5];
                  OpenAttempt attempt =
                      new OpenAttempt((String) args[1], viewportHints, success, error, disconnect);
                  openAttempts.add(attempt);
                  return Proxy.newProxyInstance(
                      subscriptionClass.getClassLoader(),
                      new Class<?>[] {subscriptionClass},
                      (subscriptionProxy, subscriptionMethod, subscriptionArgs) -> {
                        if ("close".equals(subscriptionMethod.getName())) {
                          closedCount++;
                        }
                        return null;
                      });
                }
                if ("fetchSelectedWaveReadState".equals(method.getName())) {
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState>) args[1];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[2];
                  readStateAttempts.add(new ReadStateFetchAttempt((String) args[0], success, error));
                  return null;
                }
                if ("fetchFragments".equals(method.getName())) {
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse>) args[6];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[7];
                  fragmentFetchAttempts.add(
                      new FragmentFetchAttempt(
                          (String) args[0],
                          (String) args[1],
                          (String) args[2],
                          ((Number) args[3]).intValue(),
                          ((Number) args[4]).longValue(),
                          ((Number) args[5]).longValue(),
                          success,
                          error));
                  return null;
                }
                if ("fetchAttachmentMetadata".equals(method.getName())) {
                  if (attachmentMetadataDispatchError != null) {
                    throw new RuntimeException(attachmentMetadataDispatchError);
                  }
                  @SuppressWarnings("unchecked")
                  List<String> attachmentIds = (List<String>) args[0];
                  J2clAttachmentMetadataClient.MetadataCallback callback =
                      (J2clAttachmentMetadataClient.MetadataCallback) args[1];
                  attachmentMetadataAttempts.add(
                      new AttachmentMetadataAttempt(attachmentIds, callback));
                  return null;
                }
                return null;
              });

      Object view =
          Proxy.newProxyInstance(
              viewClass.getClassLoader(),
              new Class<?>[] {viewClass},
              (proxy, method, args) -> {
                if ("render".equals(method.getName())) {
                  lastModel = args[0];
                  Runnable callback = onNextRender;
                  onNextRender = null;
                  if (callback != null) {
                    callback.run();
                  }
                }
                if ("initialViewportHints".equals(method.getName())) {
                  return initialViewportHints;
                }
                return null;
              });

      Class<?> schedulerClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$RetryScheduler");
      Class<?> readStateSchedulerClass =
          Class.forName(
              "org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$ReadStateFetchScheduler");
      Object scheduler =
          Proxy.newProxyInstance(
              schedulerClass.getClassLoader(),
              new Class<?>[] {schedulerClass},
              (proxy, method, args) -> {
                scheduledDelays.add(((Number) args[0]).intValue());
                scheduledRetries.add((Runnable) args[1]);
                return null;
              });
      Object readStateScheduler =
          Proxy.newProxyInstance(
              readStateSchedulerClass.getClassLoader(),
              new Class<?>[] {readStateSchedulerClass},
              (proxy, method, args) -> {
                // Captures debounce dispatches so tests can control when the
                // debounced fetch fires. Not mirrored into scheduledDelays to
                // keep reconnect assertions clean.
                pendingReadStateDispatches.add((Runnable) args[1]);
                return null;
              });
      Object controller;
      if (injectVisibility) {
        Class<?> visibilityClass =
            Class.forName(
                "org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$VisibilitySource");
        Class<?> writeSessionListenerClass =
            Class.forName(
                "org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$WriteSessionListener");
        Object visibility =
            Proxy.newProxyInstance(
                visibilityClass.getClassLoader(),
                new Class<?>[] {visibilityClass},
                (proxy, method, args) -> {
                  if ("addVisibilityListener".equals(method.getName())) {
                    visibilityListeners.add((Runnable) args[0]);
                  }
                  return null;
                });
        Constructor<?> constructor =
            controllerClass.getConstructor(
                gatewayClass,
                viewClass,
                schedulerClass,
                readStateSchedulerClass,
                writeSessionListenerClass,
                visibilityClass);
        controller =
            constructor.newInstance(
                gateway, view, scheduler, readStateScheduler, null, visibility);
      } else {
        Constructor<?> constructor =
            controllerClass.getConstructor(
                gatewayClass, viewClass, schedulerClass, readStateSchedulerClass);
        controller = constructor.newInstance(gateway, view, scheduler, readStateScheduler);
      }
      // The `withScheduler` flag is kept for call-site symmetry with legacy tests;
      // the harness always wires a custom scheduler now so the implicit retry and
      // debounce paths are never driven by the browser timer.
      if (!withScheduler) {
        // No-op: the constructor above supplies a scheduler regardless.
      }
      onWaveSelectedMethod = controllerClass.getMethod("onWaveSelected", String.class);
      onWaveSelectedWithDigestMethod =
          controllerClass.getMethod("onWaveSelected", String.class, J2clSearchDigestItem.class);
      return controller;
    }

    private void selectWave(Object controller, String waveId, J2clSearchDigestItem digestItem) throws Exception {
      if (digestItem == null) {
        onWaveSelectedMethod.invoke(controller, waveId);
      } else {
        onWaveSelectedWithDigestMethod.invoke(controller, waveId, digestItem);
      }
    }

    private void refreshSelectedWave(Object controller) throws Exception {
      Method refreshSelectedWaveMethod =
          controller.getClass().getMethod("refreshSelectedWave");
      refreshSelectedWaveMethod.invoke(controller);
    }

    private void requestViewportEdge(Object controller, String anchorBlipId, String direction)
        throws Exception {
      Method requestViewportEdgeMethod =
          controller.getClass().getDeclaredMethod("onViewportEdge", String.class, String.class);
      requestViewportEdgeMethod.setAccessible(true);
      requestViewportEdgeMethod.invoke(controller, anchorBlipId, direction);
    }

    private void resolveBootstrap(int index) {
      bootstrapAttempts.get(index)
          .success
          .accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
    }

    private void rejectBootstrap(int index, String message) {
      bootstrapAttempts.get(index).error.accept(message);
    }

    private void failOpen(int index, String message) {
      openAttempts.get(index).error.accept(message);
    }

    private void fireDisconnect(int index) {
      openAttempts.get(index).disconnect.run();
    }

    private void runScheduledRetry(int index) {
      scheduledRetries.get(index).run();
    }

    private void runPendingReadStateDispatch(int index) {
      pendingReadStateDispatches.get(index).run();
    }

    private void resolveReadState(int index, int unreadCount, boolean read) {
      readStateAttempts
          .get(index)
          .success
          .accept(
              new SidecarSelectedWaveReadState(
                  readStateAttempts.get(index).waveId, unreadCount, read));
    }

    private void rejectReadState(int index, String message) {
      readStateAttempts.get(index).error.accept(message);
    }

    private void resolveFragmentFetch(int index, SidecarFragmentsResponse response) {
      fragmentFetchAttempts.get(index).success.accept(response);
    }

    private void rejectFragmentFetch(int index, String message) {
      fragmentFetchAttempts.get(index).error.accept(message);
    }

    private void resolveAttachmentMetadata(
        int index,
        List<J2clAttachmentMetadata> attachments,
        List<String> missingAttachmentIds) {
      attachmentMetadataAttempts
          .get(index)
          .callback
          .onComplete(
              J2clAttachmentMetadataClient.MetadataResult.success(
                  attachments, missingAttachmentIds));
    }

    private void rejectAttachmentMetadata(int index, String message) {
      attachmentMetadataAttempts
          .get(index)
          .callback
          .onComplete(
              J2clAttachmentMetadataClient.MetadataResult.failure(
                  J2clAttachmentMetadataClient.ErrorType.NETWORK, message));
    }

    private void deliverUpdate(int index, String rawSnapshot) {
      deliverRawUpdate(index, update("example.com!w+1/example.com!conv+root", rawSnapshot));
    }

    private void deliverSnapshotOnlyUpdate(int index, String textContent) {
      deliverRawUpdate(index, snapshotOnlyUpdate(textContent));
    }

    private void deliverRawUpdate(int index, SidecarSelectedWaveUpdate update) {
      openAttempts.get(index).success.accept(update);
    }

    private Object modelValue(String methodName) throws Exception {
      Method method = lastModel.getClass().getMethod(methodName);
      return method.invoke(lastModel);
    }

    private J2clAttachmentRenderModel firstReadAttachment() throws Exception {
      @SuppressWarnings("unchecked")
      List<J2clReadBlip> readBlips = (List<J2clReadBlip>) modelValue("getReadBlips");
      return readBlips.get(0).getAttachments().get(0);
    }
  }

  private static SidecarSelectedWaveUpdate update(String waveletName, String rawSnapshot) {
    return update(waveletName, rawSnapshot, 44L, "ABCD");
  }

  private static SidecarSelectedWaveUpdate update(
      String waveletName, String rawSnapshot, long resultingVersion, String historyHash) {
    return new SidecarSelectedWaveUpdate(
        1,
        waveletName,
        true,
        "chan-1",
        resultingVersion,
        historyHash,
        Arrays.asList("user@example.com", "teammate@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument(
                "b+root", "user@example.com", 33L, 44L, rawSnapshot)),
        new SidecarSelectedWaveFragments(
            44L,
            40L,
            44L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("manifest", 40L, 44L),
                new SidecarSelectedWaveFragmentRange("blip:b+root", 41L, 44L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                new SidecarSelectedWaveFragment("blip:b+root", rawSnapshot, 0, 0))));
  }

  private static SidecarSelectedWaveUpdate updateWithPlaceholder(String rootSnapshot) {
    return updateWithPlaceholder(rootSnapshot, 44L, "ABCD");
  }

  private static SidecarSelectedWaveUpdate updateWithPlaceholderWithoutWriteSession(
      String rootSnapshot) {
    return updateWithPlaceholder(rootSnapshot, -1L, null);
  }

  private static SidecarSelectedWaveUpdate updateWithPlaceholder(
      String rootSnapshot, long resultingVersion, String historyHash) {
    return new SidecarSelectedWaveUpdate(
        1,
        "example.com!w+1/example.com!conv+root",
        true,
        "chan-1",
        resultingVersion,
        historyHash,
        Arrays.asList("user@example.com", "teammate@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument(
                "b+root", "user@example.com", 33L, 44L, rootSnapshot)),
        new SidecarSelectedWaveFragments(
            44L,
            40L,
            44L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("manifest", 40L, 44L),
                new SidecarSelectedWaveFragmentRange("blip:b+root", 41L, 44L),
                new SidecarSelectedWaveFragmentRange("blip:b+next", 41L, 44L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                new SidecarSelectedWaveFragment("blip:b+root", rootSnapshot, 0, 0))));
  }

  private static SidecarSelectedWaveUpdate updateWithOnlyPlaceholder() {
    return new SidecarSelectedWaveUpdate(
        1,
        "example.com!w+1/example.com!conv+root",
        true,
        "chan-1",
        44L,
        "ABCD",
        Arrays.asList("user@example.com", "teammate@example.com"),
        Collections.<SidecarSelectedWaveDocument>emptyList(),
        new SidecarSelectedWaveFragments(
            44L,
            40L,
            44L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("manifest", 40L, 44L),
                new SidecarSelectedWaveFragmentRange("blip:b+missing", 41L, 44L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0))));
  }

  private static SidecarFragmentsResponse fragmentsResponse(
      String nextSnapshot, String tailSnapshot) {
    return fragmentsResponseForBlips("b+next", nextSnapshot, "b+tail", tailSnapshot);
  }

  private static SidecarFragmentsResponse fragmentsResponseForBlips(
      String firstBlipId, String firstSnapshot, String secondBlipId, String secondSnapshot) {
    StringBuilder json =
        new StringBuilder(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+1/~/conv+root\","
                + "\"version\":{\"snapshot\":48,\"start\":44,\"end\":48},"
                + "\"ranges\":[{\"segment\":\"blip:");
    json.append(firstBlipId)
        .append("\",\"from\":44,\"to\":48}");
    if (secondBlipId != null) {
      json.append(",{\"segment\":\"blip:")
          .append(secondBlipId)
          .append("\",\"from\":44,\"to\":48}");
    }
    json.append("],\"fragments\":[{\"segment\":\"blip:")
        .append(firstBlipId)
        .append("\",\"rawSnapshot\":\"")
        .append(firstSnapshot)
        .append("\",\"adjust\":[],\"diff\":[]}");
    if (secondBlipId != null) {
      json.append(",{\"segment\":\"blip:")
          .append(secondBlipId)
          .append("\",\"rawSnapshot\":\"")
          .append(secondSnapshot)
          .append("\",\"adjust\":[],\"diff\":[]}");
    }
    json.append("]}");
    return SidecarFragmentsResponse.fromJson(
        json.toString());
  }

  private static SidecarSelectedWaveUpdate snapshotOnlyUpdate(String textContent) {
    return new SidecarSelectedWaveUpdate(
        1,
        "local.net!w+s4635670bfbwA/~/conv+root",
        true,
        "ch3",
        -1L,
        null,
        Arrays.asList("user@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument("b+abc123", "user@example.com", 1L, 2L, textContent)),
        null);
  }

  private static final class BootstrapAttempt {
    private final J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success;
    private final J2clSearchPanelController.ErrorCallback error;

    private BootstrapAttempt(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success,
        J2clSearchPanelController.ErrorCallback error) {
      this.success = success;
      this.error = error;
    }
  }

  private static final class ReadStateFetchAttempt {
    private final String waveId;
    private final J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> success;
    private final J2clSearchPanelController.ErrorCallback error;

    private ReadStateFetchAttempt(
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> success,
        J2clSearchPanelController.ErrorCallback error) {
      this.waveId = waveId;
      this.success = success;
      this.error = error;
    }
  }

  private static final class FragmentFetchAttempt {
    private final String waveId;
    private final String startBlipId;
    private final String direction;
    private final int limit;
    private final long startVersion;
    private final long endVersion;
    private final J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse> success;
    private final J2clSearchPanelController.ErrorCallback error;

    private FragmentFetchAttempt(
        String waveId,
        String startBlipId,
        String direction,
        int limit,
        long startVersion,
        long endVersion,
        J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse> success,
        J2clSearchPanelController.ErrorCallback error) {
      this.waveId = waveId;
      this.startBlipId = startBlipId;
      this.direction = direction;
      this.limit = limit;
      this.startVersion = startVersion;
      this.endVersion = endVersion;
      this.success = success;
      this.error = error;
    }
  }

  private static final class AttachmentMetadataAttempt {
    private final List<String> attachmentIds;
    private final J2clAttachmentMetadataClient.MetadataCallback callback;

    private AttachmentMetadataAttempt(
        List<String> attachmentIds,
        J2clAttachmentMetadataClient.MetadataCallback callback) {
      this.attachmentIds = new ArrayList<String>(attachmentIds);
      this.callback = callback;
    }
  }

  private static final class OpenAttempt {
    private final String waveId;
    private final SidecarViewportHints viewportHints;
    private final J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success;
    private final J2clSearchPanelController.ErrorCallback error;
    private final Runnable disconnect;

    private OpenAttempt(
        String waveId,
        SidecarViewportHints viewportHints,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success,
        J2clSearchPanelController.ErrorCallback error,
        Runnable disconnect) {
      this.waveId = waveId;
      this.viewportHints = viewportHints;
      this.success = success;
      this.error = error;
      this.disconnect = disconnect;
    }
  }
}

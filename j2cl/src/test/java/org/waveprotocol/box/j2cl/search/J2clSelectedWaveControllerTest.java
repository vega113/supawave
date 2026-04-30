package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
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
  public void selectingDigestPublishesFolderStateToNavRow() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            "example.com/w+1",
            "Archived pinned wave",
            "snippet",
            "user@example.com",
            0,
            1,
            1234L,
            true,
            true);

    harness.selectWave(controller, "example.com/w+1", digest);

    Assert.assertTrue(harness.lastNavRowPinned);
    Assert.assertTrue(harness.lastNavRowArchived);
  }

  @Test
  public void selectingDigestPublishesFolderStateAfterRenderingSelectedWaveId() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            "example.com/w+1",
            "Archived pinned wave",
            "snippet",
            "user@example.com",
            0,
            1,
            1234L,
            true,
            true);

    harness.selectWave(controller, "example.com/w+1", digest);

    Assert.assertEquals(
        "folder state must be published after the view renders the new source-wave-id",
        Arrays.asList(
            "render:",
            "render:example.com/w+1",
            "folder-state:true:true"),
        harness.viewEvents);
  }

  @Test
  public void clearingSelectionClearsFolderStateOnNavRow() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            "example.com/w+1",
            "Archived pinned wave",
            "snippet",
            "user@example.com",
            0,
            1,
            1234L,
            true,
            true);

    harness.selectWave(controller, "example.com/w+1", digest);
    harness.selectWave(controller, "", null);

    Assert.assertFalse(harness.lastNavRowPinned);
    Assert.assertFalse(harness.lastNavRowArchived);
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
  public void selectedWaveRefreshHandlerRefreshesOnlyCurrentWave() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    Assert.assertNotNull(harness.selectedWaveRefreshHandler);
    harness.selectedWaveRefreshHandler.refresh("example.com/w+other");
    Assert.assertEquals(
        "stale restore events for another wave must not reopen the socket",
        1,
        harness.bootstrapAttempts.size());

    harness.selectedWaveRefreshHandler.refresh("example.com/w+1");
    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void replySubmitHandoffWaitsForLiveUpdateBeforeFallbackRefresh() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L);

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(Arrays.asList(Integer.valueOf(250)), harness.scheduledDelays);

    harness.deliverRawUpdate(
        0, liveReplyFragmentUpdate("Reply now visible from live stream", -1L, null, 45L));
    harness.runScheduledRetry(0);

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply now visible from live stream"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffFetchesForwardViewportWhenLiveUpdateDoesNotAdvance()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L);
    harness.runScheduledRetry(0);

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+next", harness.fragmentFetchAttempts.get(0).startBlipId);

    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+reply", "Reply loaded by post-submit fetch", null, null));

    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply loaded by post-submit fetch"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffSuccessfulForwardFetchDoesNotRefreshSelectedWave()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+created", "Reply loaded by post-submit fetch", null, null));

    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(1, harness.bootstrapAttempts.size());
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply loaded by post-submit fetch"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffAppliesForwardFetchAfterWriteSessionAdvances()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());

    harness.deliverRawUpdate(0, metadataOnlyLiveUpdate(45L, "EFGH"));
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+created", "Reply loaded despite write-session advance", null, null));

    Assert.assertEquals(
        "post-submit fetch must not be discarded just because the same-wave write session advanced",
        0,
        harness.closedCount);
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply loaded despite write-session advance"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffRejectsForwardFetchWhenWriteSessionDisappears()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());

    harness.clearCurrentWriteSession(controller);
    Assert.assertNull(harness.modelValue("getWriteSession"));
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+created", "Stale reply should not merge", null, null));

    Assert.assertFalse(
        String.valueOf(harness.modelValue("getContentEntries"))
            .contains("Stale reply should not merge"));
    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void replySubmitHandoffRejectsOlderForwardFetchOverFreshLiveBlip()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());

    harness.deliverRawUpdate(
        0,
        liveBlipFragmentUpdate(
            "b+created", "Fresh reply from live stream", 50L, "IJKL", 50L));
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+created", "Stale reply should not overwrite live content", null, null));

    Assert.assertFalse(
        String.valueOf(harness.modelValue("getContentEntries"))
            .contains("Stale reply should not overwrite live content"));
    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void replySubmitHandoffRetriesForwardFetchWhenSnapshotLagsSubmittedBlip()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());

    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+stale", "Stale committed window without the submitted blip", null, null));

    Assert.assertEquals(
        "stale post-submit fragment windows must not be applied as terminal success",
        Arrays.asList("Root already loaded"),
        harness.modelValue("getContentEntries"));
    Assert.assertEquals(
        "snapshot lag should schedule one more post-submit fetch before falling back to refresh",
        Arrays.asList(Integer.valueOf(250), Integer.valueOf(250)),
        harness.scheduledDelays);

    harness.runScheduledRetry(1);
    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(1).startBlipId);

    harness.resolveFragmentFetch(
        1,
        fragmentsResponseForBlips(
            "b+created", "Reply loaded after snapshot catches up", null, null));

    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply loaded after snapshot catches up"),
        harness.modelValue("getContentEntries"));
    Assert.assertEquals(0, harness.closedCount);
  }

  @Test
  public void replySubmitHandoffRefreshesAfterForwardFetchExhaustion()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());

    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips("b+stale-1", "Stale committed window one", null, null));
    harness.runScheduledRetry(1);
    Assert.assertEquals(2, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(1).startBlipId);

    harness.resolveFragmentFetch(
        1,
        fragmentsResponseForBlips("b+stale-2", "Stale committed window two", null, null));
    harness.runScheduledRetry(2);
    Assert.assertEquals(3, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(2).startBlipId);

    harness.resolveFragmentFetch(
        2,
        fragmentsResponseForBlips("b+stale-3", "Stale committed window three", null, null));

    Assert.assertEquals(
        "post-submit fetch retries must terminate with a selected-wave refresh",
        1,
        harness.closedCount);
    Assert.assertEquals(
        "post-submit fetch must stop at the configured retry cap",
        3,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertEquals(
        Arrays.asList(Integer.valueOf(250), Integer.valueOf(250), Integer.valueOf(250)),
        harness.scheduledDelays);
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void replySubmitHandoffFetchesForwardViewportWhenLiveUpdateOnlyAdvancesMetadata()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L);
    harness.deliverRawUpdate(0, metadataOnlyLiveUpdate(45L, "EFGH"));
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "metadata-only live updates must not suppress the viewport fetch needed to show the reply",
        1,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+next", harness.fragmentFetchAttempts.get(0).startBlipId);

    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+reply", "Reply loaded after metadata-only live update", null, null));

    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply loaded after metadata-only live update"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffFetchesForwardViewportWhenMetadataAdvanceArrivedFirst()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));
    harness.deliverRawUpdate(0, metadataOnlyLiveUpdate(45L, "EFGH"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "version-only live updates must not make the submitted blip look visible",
        1,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void replySubmitHandoffFetchesSubmittedBlipWhenKnown() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);

    Assert.assertEquals(1, harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void replySubmitHandoffWithUnknownVersionFetchesKnownBlipAfterVersionOnlyAdvance()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", -1L, "b+created");
    harness.deliverRawUpdate(0, metadataOnlyLiveUpdate(45L, "EFGH"));
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "unknown resultingVersion must not suppress fetch for a known submitted blip",
        1,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void replySubmitHandoffWithUnknownVersionFetchesKnownBlipAfterUnrelatedLiveBlip()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", -1L, "b+created");
    harness.deliverRawUpdate(
        0, liveReplyFragmentUpdate("Unrelated blip from live stream", -1L, null, 45L));
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "unknown resultingVersion must not treat an unrelated live blip as the submitted blip",
        1,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void replySubmitHandoffRefreshesSelectedWaveWhenForwardFetchFails()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    harness.runScheduledRetry(0);
    harness.rejectFragmentFetch(0, "fragment timeout");

    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));
  }

  @Test
  public void replySubmitHandoffSkipsFallbackWhenLiveUpdateAlreadyReachedSubmitVersion()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));
    harness.deliverRawUpdate(
        0, liveReplyFragmentUpdate("Reply already visible from live stream", -1L, null, 45L));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+reply");

    Assert.assertTrue(harness.scheduledDelays.isEmpty());
    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(0, harness.closedCount);
    Assert.assertEquals(
        Arrays.asList("Root already loaded", "Reply already visible from live stream"),
        harness.modelValue("getContentEntries"));
  }

  @Test
  public void replySubmitHandoffFetchesForwardViewportWhenUnrelatedBlipArrivesFirst()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));

    harness.replySubmitted(controller, "example.com/w+1", 45L, "b+created");
    // An unrelated live blip (b+reply, not b+created) arrives at the target version.
    // Generic count increase must not suppress the fetch for the actually-submitted blip.
    harness.deliverRawUpdate(
        0, liveReplyFragmentUpdate("Unrelated blip from live stream", -1L, null, 45L));
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "unrelated blip advancing version must not suppress fetch for the submitted blip",
        1,
        harness.fragmentFetchAttempts.size());
    Assert.assertEquals("b+created", harness.fragmentFetchAttempts.get(0).startBlipId);
  }

  @Test
  public void replySubmitHandoffFetchesForwardViewportWhenVersionAlreadyMetButBlipIdUnknown()
      throws Exception {
    // Regression: submittedBlipConfirmedInViewport must return false for empty blipId so that
    // legacy callers (no blip ID) still trigger the forward-fetch even if the viewport version
    // has already advanced to targetVersion via an unrelated live update.
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded", 44L, "ABCD"));
    // An unrelated blip advances the version to 45 before the submit acknowledgement arrives.
    harness.deliverRawUpdate(
        0, liveReplyFragmentUpdate("Unrelated blip from live stream", -1L, null, 45L));

    // Legacy caller: knows the resulting version but not the submitted blip ID.
    harness.replySubmitted(controller, "example.com/w+1", 45L);
    harness.runScheduledRetry(0);

    Assert.assertEquals(
        "unknown blip ID must not suppress the forward-fetch even when version is already met",
        1,
        harness.fragmentFetchAttempts.size());
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
  public void selectedWavePublishesParticipantsBeforeWriteSessionReady() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createControllerWithWriteSessionListener(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(
        0,
        new SidecarSelectedWaveUpdate(
            1,
            "example.com!w+1/example.com!conv+root",
            true,
            "chan-1",
            -1L,
            null,
            Arrays.asList("alice@example.com", "bob@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "alice@example.com", 33L, 44L, "Hello before write session")),
            null));

    Assert.assertEquals("example.com/w+1", harness.lastPublishedSelectedWaveId);
    Assert.assertNull(harness.lastPublishedWriteSession);
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        harness.lastPublishedParticipantIds);
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
  public void viewportGrowthPreservesMetadataForRenderedWindowEntries() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "snippet", 0));
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    @SuppressWarnings("unchecked")
    List<J2clReadBlip> readBlips = (List<J2clReadBlip>) harness.modelValue("getReadBlips");
    Assert.assertEquals(3, readBlips.size());
    Assert.assertEquals("user@example.com", readBlips.get(0).getAuthorId());
    Assert.assertEquals(44L, readBlips.get(0).getLastModifiedTimeMillis());
    Assert.assertEquals("user@example.com", readBlips.get(1).getAuthorId());
    Assert.assertEquals(1234L, readBlips.get(1).getLastModifiedTimeMillis());
    Assert.assertEquals("user@example.com", readBlips.get(2).getAuthorId());
    Assert.assertEquals(1234L, readBlips.get(2).getLastModifiedTimeMillis());
  }

  @Test
  public void viewportGrowthUsesFragmentResponseBlipMetadataWithoutDigestFallback()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseWithBlipMetadata(
            "b+next",
            "Next loaded",
            "next-author@example.com",
            1777463436001L,
            "b+tail",
            "Tail loaded",
            "tail-author@example.com",
            1777463436277L));

    @SuppressWarnings("unchecked")
    List<J2clReadBlip> readBlips = (List<J2clReadBlip>) harness.modelValue("getReadBlips");
    Assert.assertEquals(3, readBlips.size());
    Assert.assertEquals("user@example.com", readBlips.get(0).getAuthorId());
    Assert.assertEquals(44L, readBlips.get(0).getLastModifiedTimeMillis());
    Assert.assertEquals("next-author@example.com", readBlips.get(1).getAuthorId());
    Assert.assertEquals(1777463436001L, readBlips.get(1).getLastModifiedTimeMillis());
    Assert.assertEquals("tail-author@example.com", readBlips.get(2).getAuthorId());
    Assert.assertEquals(1777463436277L, readBlips.get(2).getLastModifiedTimeMillis());
  }

  @Test
  public void viewportGrowthPreservesPreviousBooleanAndTaskStateThroughController()
      throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));
    harness.replaceModelReadBlips(
        controller,
        Arrays.asList(
            new J2clReadBlip(
                "b+root",
                "Root already loaded",
                Collections.<J2clAttachmentRenderModel>emptyList(),
                "root-author@example.com",
                "Root Author",
                1714230000000L,
                "",
                "",
                /* unread= */ true,
                /* hasMention= */ true,
                /* deleted= */ false,
                /* taskDone= */ true,
                /* taskAssignee= */ "assignee@example.com",
                /* taskDueTimestamp= */ 1714560000000L)));

    harness.requestViewportEdge(controller, "b+root", "forward");
    harness.resolveFragmentFetch(
        0,
        fragmentsResponseForBlips(
            "b+root",
            "Root refreshed from fragment growth",
            null,
            null));

    @SuppressWarnings("unchecked")
    List<J2clReadBlip> readBlips = (List<J2clReadBlip>) harness.modelValue("getReadBlips");
    Assert.assertEquals(1, readBlips.size());
    J2clReadBlip root = readBlips.get(0);
    Assert.assertEquals("Root refreshed from fragment growth", root.getText());
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertTrue("controller fragment growth must preserve unread state", root.isUnread());
    Assert.assertTrue(
        "controller fragment growth must preserve mention state",
        root.hasMention());
    Assert.assertFalse(root.isDeleted());
    Assert.assertTrue(
        "controller fragment growth must preserve task completion state",
        root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());
  }

  @Test
  public void viewportGrowthAppliesManifestFromFragmentResponse() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponseWithRawManifest());

    SidecarConversationManifest manifest =
        (SidecarConversationManifest) harness.modelValue("getConversationManifest");
    Assert.assertFalse(manifest.isEmpty());
    Assert.assertEquals("b+root", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals("b+next", manifest.getOrderedEntries().get(1).getBlipId());
    Assert.assertEquals("b+tail", manifest.getOrderedEntries().get(2).getBlipId());
    Assert.assertEquals("b+root", manifest.findByBlipId("b+next").getParentBlipId());
    Assert.assertEquals("b+next", manifest.findByBlipId("b+tail").getParentBlipId());
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
  public void metadataNetworkFailureEmitsTelemetry() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.openWaveWithAttachment(controller);
    harness.rejectAttachmentMetadata(0, "metadata network failure");

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.metadata.failed", event.getName());
    Assert.assertEquals("selected-wave", event.getFields().get("source"));
    Assert.assertEquals("network", event.getFields().get("reason"));
    Assert.assertEquals("other", event.getFields().get("statusBucket"));
  }

  @Test
  public void metadataMissingResultEmitsMetadataReason() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.openWaveWithAttachment(controller);
    harness.resolveAttachmentMetadata(
        0,
        Collections.<J2clAttachmentMetadata>emptyList(),
        Collections.<String>emptyList());

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.metadata.failed", event.getName());
    Assert.assertEquals("metadata", event.getFields().get("reason"));
  }

  @Test
  public void metadataTelemetryMapsEveryMetadataReason() throws Exception {
    assertMetadataReason(
        J2clAttachmentMetadataClient.MetadataResult.failure(
            J2clAttachmentMetadataClient.ErrorType.INVALID_REQUEST, "bad request"),
        "validation",
        "other");
    assertMetadataReason(metadataHttpFailure(403), "forbidden", "4xx");
    assertMetadataReason(metadataHttpFailure(500), "server", "5xx");
    assertMetadataReason(
        J2clAttachmentMetadataClient.MetadataResult.failure(
            J2clAttachmentMetadataClient.ErrorType.UNEXPECTED_CONTENT_TYPE, "html"),
        "metadata",
        "other");
    assertMetadataReason(
        J2clAttachmentMetadataClient.MetadataResult.failure(
            J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, "bad json"),
        "metadata",
        "other");
  }

  @Test
  public void metadataDispatchExceptionEmitsClientErrorReason() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    harness.attachmentMetadataDispatchError = "dispatch failed";
    Object controller = harness.createController(false);

    harness.openWaveWithAttachment(controller);

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.metadata.failed", event.getName());
    Assert.assertEquals("client-error", event.getFields().get("reason"));
    Assert.assertEquals("other", event.getFields().get("statusBucket"));
    Assert.assertTrue(harness.firstReadAttachment().isMetadataFailure());
  }

  @Test
  public void throwingTelemetrySinkDoesNotBreakMetadataFailureRendering() throws Exception {
    Harness harness =
        new Harness(
            event -> {
              throw new RuntimeException("telemetry boom");
            });
    Object controller = harness.createController(false);

    harness.openWaveWithAttachment(controller);
    harness.rejectAttachmentMetadata(0, "metadata network failure");

    Assert.assertTrue(harness.firstReadAttachment().isMetadataFailure());
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

  // ---------------------------------------------------------------------------
  // F-1 viewport telemetry contract (R-4.6, R-7.1, R-7.3, R-7.4)
  // ---------------------------------------------------------------------------

  @Test
  public void selectingWaveEmitsViewportInitialWindowEvent() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    J2clClientTelemetry.Event initial = lastEventNamed(telemetry, "viewport.initial_window");
    Assert.assertEquals("forward", initial.getFields().get("direction"));
    Assert.assertEquals("default", initial.getFields().get("limit"));
  }

  @Test
  public void selectingWaveWithExplicitAnchorEmitsViewportInitialWindowEvent() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    harness.initialViewportHints = new SidecarViewportHints("b+server", "forward", null);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    J2clClientTelemetry.Event initial = lastEventNamed(telemetry, "viewport.initial_window");
    Assert.assertEquals("forward", initial.getFields().get("direction"));
    Assert.assertEquals("default", initial.getFields().get("limit"));
  }

  @Test
  public void viewportEdgeFetchEmitsExtensionAndOutcomeEvents() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    J2clClientTelemetry.Event extension = firstEventNamed(telemetry, "viewport.extension_fetch");
    Assert.assertEquals("forward", extension.getFields().get("direction"));
    Assert.assertEquals("5", extension.getFields().get("limit"));

    J2clClientTelemetry.Event outcome =
        lastEventNamed(telemetry, "viewport.extension_fetch.outcome");
    Assert.assertEquals("forward", outcome.getFields().get("direction"));
    Assert.assertEquals("ok", outcome.getFields().get("outcome"));
  }

  @Test
  public void viewportEdgeFetchEmitsClampAppliedWhenServerReturnsFewerBlips() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    // The growth limit is 5 but the server returns only 2 blips — that's a
    // clamp visible to the client (R-7.3).
    harness.resolveFragmentFetch(0, fragmentsResponse("Next loaded", "Tail loaded"));

    J2clClientTelemetry.Event clamp = lastEventNamed(telemetry, "viewport.clamp_applied");
    Assert.assertEquals("forward", clamp.getFields().get("direction"));
    Assert.assertEquals("5", clamp.getFields().get("requested"));
    Assert.assertEquals("2", clamp.getFields().get("delivered"));
  }

  @Test
  public void viewportEdgeFetchErrorEmitsErrorOutcome() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    harness.requestViewportEdge(controller, "b+next", "forward");
    harness.rejectFragmentFetch(0, "transient");

    J2clClientTelemetry.Event outcome =
        lastEventNamed(telemetry, "viewport.extension_fetch.outcome");
    Assert.assertEquals("forward", outcome.getFields().get("direction"));
    Assert.assertEquals("error", outcome.getFields().get("outcome"));
  }

  @Test
  public void snapshotOnlyUpdateEmitsViewportFallbackToWholeWaveExactlyOnce() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    // First update is a snapshot fallback (no fragments payload).
    harness.deliverRawUpdate(0, snapshotOnlyUpdate("Whole-wave content"));

    long fallbackCount =
        countEventsNamed(telemetry, "viewport.fallback_to_whole_wave");
    Assert.assertEquals("Fallback emitted exactly once on first snapshot", 1, fallbackCount);
    J2clClientTelemetry.Event fallback =
        lastEventNamed(telemetry, "viewport.fallback_to_whole_wave");
    Assert.assertEquals("server-snapshot", fallback.getFields().get("reason"));

    // Subsequent snapshot-only update on the same open must not double-count.
    harness.deliverRawUpdate(0, snapshotOnlyUpdate("Another snapshot"));
    Assert.assertEquals(
        "Fallback de-duplicated for the lifetime of the open",
        1,
        countEventsNamed(telemetry, "viewport.fallback_to_whole_wave"));
  }

  @Test
  public void healthyViewportUpdateDoesNotEmitFallbackToWholeWave() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverRawUpdate(0, updateWithPlaceholder("Root already loaded"));

    Assert.assertEquals(
        "No fallback when the server honoured the viewport hint",
        0,
        countEventsNamed(telemetry, "viewport.fallback_to_whole_wave"));
  }

  private static J2clClientTelemetry.Event firstEventNamed(
      RecordingTelemetrySink telemetry, String name) {
    for (J2clClientTelemetry.Event event : telemetry.events()) {
      if (name.equals(event.getName())) {
        return event;
      }
    }
    throw new AssertionError("No telemetry event named " + name + " recorded");
  }

  private static J2clClientTelemetry.Event lastEventNamed(
      RecordingTelemetrySink telemetry, String name) {
    J2clClientTelemetry.Event found = null;
    for (J2clClientTelemetry.Event event : telemetry.events()) {
      if (name.equals(event.getName())) {
        found = event;
      }
    }
    if (found == null) {
      throw new AssertionError("No telemetry event named " + name + " recorded");
    }
    return found;
  }

  private static long countEventsNamed(RecordingTelemetrySink telemetry, String name) {
    long count = 0;
    for (J2clClientTelemetry.Event event : telemetry.events()) {
      if (name.equals(event.getName())) {
        count++;
      }
    }
    return count;
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

  private static void assertMetadataReason(
      J2clAttachmentMetadataClient.MetadataResult result,
      String reason,
      String statusBucket)
      throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.openWaveWithAttachment(controller);
    harness.completeAttachmentMetadata(0, result);

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.metadata.failed", event.getName());
    Assert.assertEquals("selected-wave", event.getFields().get("source"));
    Assert.assertEquals(reason, event.getFields().get("reason"));
    Assert.assertEquals(statusBucket, event.getFields().get("statusBucket"));
  }

  private static J2clAttachmentMetadataClient.MetadataResult metadataHttpFailure(int statusCode) {
    return J2clAttachmentMetadataClient.MetadataResult.failure(
        J2clAttachmentMetadataClient.ErrorType.HTTP_STATUS,
        "HTTP " + statusCode,
        statusCode);
  }

  // F-4 (#1039 / R-4.4 / subsumes #1056) — mark-blip-read tests.

  @Test
  public void onMarkBlipReadDispatchesGatewayCall() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    int beforeAttempts = harness.markBlipReadAttempts.size();
    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});

    Assert.assertEquals(beforeAttempts + 1, harness.markBlipReadAttempts.size());
    MarkBlipReadAttempt attempt =
        harness.markBlipReadAttempts.get(harness.markBlipReadAttempts.size() - 1);
    Assert.assertEquals("example.com/w+1", attempt.waveId);
    Assert.assertEquals("b+abc", attempt.blipId);
  }

  @Test
  public void onMarkBlipReadIgnoresEmptyBlipId() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    int beforeAttempts = harness.markBlipReadAttempts.size();
    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "", (Runnable) () -> {});
    onMarkBlipRead.invoke(controller, (String) null, (Runnable) () -> {});

    Assert.assertEquals(
        "empty / null blip ids must not dispatch",
        beforeAttempts,
        harness.markBlipReadAttempts.size());
  }

  @Test
  public void onMarkBlipReadDeduplicatesInFlightDispatch() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});

    Assert.assertEquals(
        "second markBlipRead for same blipId must be dropped while first is in flight",
        1,
        harness.markBlipReadAttempts.size());
  }

  @Test
  public void onMarkBlipReadSuccessReleasesInFlightSlotForLaterRetry() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    Assert.assertEquals(1, harness.markBlipReadAttempts.size());
    harness.markBlipReadAttempts.get(0).success.accept(Integer.valueOf(2));

    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    Assert.assertEquals(
        "after success, the same blipId may be dispatched again",
        2,
        harness.markBlipReadAttempts.size());
  }

  @Test
  public void onMarkBlipReadErrorReleasesInFlightSlotForLaterRetry() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    Assert.assertEquals(1, harness.markBlipReadAttempts.size());
    harness.markBlipReadAttempts.get(0).error.accept("transient");

    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    Assert.assertEquals(
        "after error, the same blipId may be dispatched again",
        2,
        harness.markBlipReadAttempts.size());
  }

  @Test
  public void onMarkBlipReadSuccessTriggersDebouncedReadStateFetch() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    int beforePending = harness.pendingReadStateDispatches.size();
    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    harness.markBlipReadAttempts.get(0).success.accept(Integer.valueOf(0));

    Assert.assertTrue(
        "success path must schedule a read-state fetch through the debounce scheduler",
        harness.pendingReadStateDispatches.size() > beforePending);
  }

  @Test
  public void onMarkBlipReadIgnoredWhenNoWaveSelected() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});

    Assert.assertTrue(harness.markBlipReadAttempts.isEmpty());
  }

  /**
   * F-4 (#1039 / R-4.4) review-fix: the in-flight de-dup must be keyed by the
   * {@code (waveId, blipId)} pair, not the blipId alone. Two waves can
   * legitimately share a blipId — without composite keying, a still-pending
   * request from the previous wave would suppress a legitimate dispatch in
   * the next selection.
   */
  @Test
  public void onMarkBlipReadDoesNotCollideAcrossWavesWithSameBlipId() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello A");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+shared", (Runnable) () -> {});
    Assert.assertEquals(1, harness.markBlipReadAttempts.size());
    // Do NOT resolve the first attempt; switch waves while it is still in flight.

    harness.selectWave(controller, "example.com/w+2", null);
    harness.resolveBootstrap(harness.bootstrapAttempts.size() - 1);
    harness.deliverUpdate(harness.openAttempts.size() - 1, "Hello B");

    onMarkBlipRead.invoke(controller, "b+shared", (Runnable) () -> {});

    Assert.assertEquals(
        "second wave's dispatch for the same blipId must not be suppressed by "
            + "the previous wave's in-flight entry",
        2,
        harness.markBlipReadAttempts.size());
    Assert.assertEquals(
        "second dispatch must target the new wave",
        "example.com/w+2",
        harness.markBlipReadAttempts.get(1).waveId);
  }

  /**
   * F-4 (#1039 / R-4.4) review-fix: the {@code skipped-in-flight} telemetry
   * outcome must include {@code latency_ms} so downstream consumers see a
   * uniform schema across success/error/skipped paths.
   */
  @Test
  public void onMarkBlipReadSkippedInFlightEventCarriesLatencyField() throws Exception {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    Harness harness = new Harness(telemetry);
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});

    J2clClientTelemetry.Event skipped = null;
    for (J2clClientTelemetry.Event event : telemetry.events()) {
      if ("j2cl.read.mark_blip_read".equals(event.getName())
          && "skipped-in-flight".equals(event.getFields().get("outcome"))) {
        skipped = event;
        break;
      }
    }
    Assert.assertNotNull("expected a skipped-in-flight outcome event", skipped);
    Assert.assertEquals(
        "skipped-in-flight outcome must include latency_ms=0 for schema parity",
        "0",
        skipped.getFields().get("latency_ms"));
  }

  /**
   * F-4 (#1039 / R-4.4) review-fix: when {@link #onMarkBlipRead} bails before
   * the gateway dispatch (no wave selected, empty blipId, or skipped-as-
   * already-in-flight), the renderer's per-blip in-flight gate must still be
   * released — the renderer added the blip to its own
   * {@code markBlipReadInFlight} set BEFORE invoking the controller, and
   * skipping {@code rendererOnError} would leave that gate stuck so the
   * dwell timer can never re-arm for that blip.
   */
  @Test
  public void onMarkBlipReadReleasesRendererGateWhenNoWaveSelected() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    final boolean[] released = new boolean[] {false};
    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> released[0] = true);

    Assert.assertTrue(
        "rendererOnError must run when no wave is selected so the renderer gate is released",
        released[0]);
    Assert.assertTrue(harness.markBlipReadAttempts.isEmpty());
  }

  @Test
  public void onMarkBlipReadReleasesRendererGateForEmptyBlipId() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    final int[] releaseCount = new int[] {0};
    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    onMarkBlipRead.invoke(controller, "", (Runnable) () -> releaseCount[0]++);
    onMarkBlipRead.invoke(controller, (String) null, (Runnable) () -> releaseCount[0]++);

    Assert.assertEquals(
        "rendererOnError must run on every empty/null-blipId early return",
        2,
        releaseCount[0]);
  }

  @Test
  public void onMarkBlipReadReleasesRendererGateOnSkippedInFlight() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello");

    Method onMarkBlipRead =
        controller.getClass().getDeclaredMethod("onMarkBlipRead", String.class, Runnable.class);
    // First dispatch: succeeds at the gateway level, no skip — gate
    // release happens in the success/error continuations, not in the
    // synchronous early-return.
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> {});

    // Second dispatch for the same (waveId, blipId) is the skipped-in-flight
    // path. The renderer gate MUST still be released here, otherwise the
    // renderer keeps treating the blip as in-flight forever.
    final boolean[] released = new boolean[] {false};
    onMarkBlipRead.invoke(controller, "b+abc", (Runnable) () -> released[0] = true);

    Assert.assertTrue(
        "skipped-in-flight early return must still release the renderer gate",
        released[0]);
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
    // F-4 (#1039 / R-4.4): captures markBlipRead dispatches issued through the
    // controller's onMarkBlipRead path.
    private final List<MarkBlipReadAttempt> markBlipReadAttempts =
        new ArrayList<MarkBlipReadAttempt>();
    private SidecarViewportHints initialViewportHints;
    private Object lastModel;
    private Runnable onNextRender;
    private Method onWaveSelectedMethod;
    private Method onWaveSelectedWithDigestMethod;
    private String attachmentMetadataDispatchError;
    private final List<String> viewEvents = new ArrayList<String>();
    private boolean captureWriteSessionListener;
    private String lastPublishedSelectedWaveId;
    private J2clSidecarWriteSession lastPublishedWriteSession;
    private List<String> lastPublishedParticipantIds = Collections.emptyList();
    private boolean lastNavRowPinned;
    private boolean lastNavRowArchived;
    private J2clSelectedWaveController.SelectedWaveRefreshHandler selectedWaveRefreshHandler;
    private final J2clClientTelemetry.Sink telemetrySink;

    private Harness() {
      this(J2clClientTelemetry.noop());
    }

    private Harness(J2clClientTelemetry.Sink telemetrySink) {
      this.telemetrySink = telemetrySink;
    }

    private Object createControllerWithVisibility(boolean withScheduler) throws Exception {
      return createControllerInternal(withScheduler, /* injectVisibility= */ true);
    }

    private Object createController(boolean withScheduler) throws Exception {
      return createControllerInternal(withScheduler, /* injectVisibility= */ false);
    }

    private Object createControllerWithWriteSessionListener(boolean withScheduler) throws Exception {
      captureWriteSessionListener = true;
      return createControllerInternal(withScheduler, /* injectVisibility= */ true);
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
                if ("markBlipRead".equals(method.getName())) {
                  // F-4 (#1039 / R-4.4): capture the dispatch so the test
                  // controls success/error timing.
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<Integer> success =
                      (J2clSearchPanelController.SuccessCallback<Integer>) args[2];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[3];
                  markBlipReadAttempts.add(
                      new MarkBlipReadAttempt(
                          (String) args[0], (String) args[1], success, error));
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
                  viewEvents.add("render:" + modelSelectedWaveId(lastModel));
                  Runnable callback = onNextRender;
                  onNextRender = null;
                  if (callback != null) {
                    callback.run();
                  }
                }
                if ("initialViewportHints".equals(method.getName())) {
                  return initialViewportHints;
                }
                if ("setNavRowFolderState".equals(method.getName())) {
                  lastNavRowPinned = ((Boolean) args[0]).booleanValue();
                  lastNavRowArchived = ((Boolean) args[1]).booleanValue();
                  viewEvents.add("folder-state:" + lastNavRowPinned + ":" + lastNavRowArchived);
                  return null;
                }
                if ("setSelectedWaveRefreshHandler".equals(method.getName())) {
                  selectedWaveRefreshHandler =
                      (J2clSelectedWaveController.SelectedWaveRefreshHandler) args[0];
                  return null;
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
        Object writeSessionListener =
            captureWriteSessionListener
                ? Proxy.newProxyInstance(
                    writeSessionListenerClass.getClassLoader(),
                    new Class<?>[] {writeSessionListenerClass},
                    (proxy, method, args) -> {
                      if ("onSelectedWaveComposeContextChanged".equals(method.getName())) {
                        lastPublishedSelectedWaveId = (String) args[0];
                        lastPublishedWriteSession = (J2clSidecarWriteSession) args[1];
                        @SuppressWarnings("unchecked")
                        List<String> participantIds = (List<String>) args[2];
                        lastPublishedParticipantIds =
                            participantIds == null
                                ? Collections.<String>emptyList()
                                : new ArrayList<String>(participantIds);
                      }
                      return null;
                    })
                : null;
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
            telemetrySink == null
                ? controllerClass.getConstructor(
                    gatewayClass,
                    viewClass,
                    schedulerClass,
                    readStateSchedulerClass,
                    writeSessionListenerClass,
                    visibilityClass)
                : controllerClass.getConstructor(
                    gatewayClass,
                    viewClass,
                    schedulerClass,
                    readStateSchedulerClass,
                    writeSessionListenerClass,
                    visibilityClass,
                    J2clClientTelemetry.Sink.class);
        controller =
            telemetrySink == null
                ? constructor.newInstance(
                    gateway, view, scheduler, readStateScheduler, writeSessionListener, visibility)
                : constructor.newInstance(
                    gateway,
                    view,
                    scheduler,
                    readStateScheduler,
                    writeSessionListener,
                    visibility,
                    telemetrySink);
      } else {
        Constructor<?> constructor =
            telemetrySink == null
                ? controllerClass.getConstructor(
                    gatewayClass, viewClass, schedulerClass, readStateSchedulerClass)
                : controllerClass.getConstructor(
                    gatewayClass,
                    viewClass,
                    schedulerClass,
                    readStateSchedulerClass,
                    J2clClientTelemetry.Sink.class);
        controller =
            telemetrySink == null
                ? constructor.newInstance(gateway, view, scheduler, readStateScheduler)
                : constructor.newInstance(gateway, view, scheduler, readStateScheduler, telemetrySink);
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

    private void replySubmitted(Object controller, String waveId) throws Exception {
      Method onReplySubmittedMethod =
          controller.getClass().getMethod("onReplySubmitted", String.class);
      onReplySubmittedMethod.invoke(controller, waveId);
    }

    private void replySubmitted(Object controller, String waveId, long resultingVersion)
        throws Exception {
      Method onReplySubmittedMethod =
          controller.getClass().getMethod("onReplySubmitted", String.class, Long.TYPE);
      onReplySubmittedMethod.invoke(controller, waveId, resultingVersion);
    }

    private void replySubmitted(
        Object controller, String waveId, long resultingVersion, String submittedBlipId)
        throws Exception {
      Method onReplySubmittedMethod =
          controller
              .getClass()
              .getMethod("onReplySubmitted", String.class, Long.TYPE, String.class);
      onReplySubmittedMethod.invoke(controller, waveId, resultingVersion, submittedBlipId);
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
      completeAttachmentMetadata(
          index,
          J2clAttachmentMetadataClient.MetadataResult.success(
              attachments, missingAttachmentIds));
    }

    private void rejectAttachmentMetadata(int index, String message) {
      completeAttachmentMetadata(
          index,
          J2clAttachmentMetadataClient.MetadataResult.failure(
              J2clAttachmentMetadataClient.ErrorType.NETWORK, message));
    }

    private void completeAttachmentMetadata(
        int index, J2clAttachmentMetadataClient.MetadataResult result) {
      attachmentMetadataAttempts
          .get(index)
          .callback
          .onComplete(result);
    }

    private void openWaveWithAttachment(Object controller) throws Exception {
      selectWave(controller, "example.com/w+1", null);
      resolveBootstrap(0);
      deliverUpdate(
          0,
          "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
              + "<caption>Hero diagram</caption></image> outro");
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

    private void replaceModelReadBlips(Object controller, List<J2clReadBlip> readBlips)
        throws Exception {
      Field currentModelField = controller.getClass().getDeclaredField("currentModel");
      currentModelField.setAccessible(true);
      J2clSelectedWaveModel currentModel = (J2clSelectedWaveModel) currentModelField.get(controller);
      J2clSelectedWaveModel nextModel = currentModel.withReadBlips(readBlips);
      currentModelField.set(controller, nextModel);
      lastModel = nextModel;
    }

    private void clearCurrentWriteSession(Object controller) throws Exception {
      Field currentModelField = controller.getClass().getDeclaredField("currentModel");
      currentModelField.setAccessible(true);
      J2clSelectedWaveModel currentModel = (J2clSelectedWaveModel) currentModelField.get(controller);
      J2clSelectedWaveModel nextModel =
          new J2clSelectedWaveModel(
              currentModel.hasSelection(),
              currentModel.isLoading(),
              currentModel.isError(),
              currentModel.getSelectedWaveId(),
              currentModel.getTitleText(),
              currentModel.getSnippetText(),
              currentModel.getUnreadText(),
              currentModel.getStatusText(),
              currentModel.getDetailText(),
              currentModel.getReconnectCount(),
              currentModel.getParticipantIds(),
              currentModel.getContentEntries(),
              currentModel.getReadBlips(),
              currentModel.getViewportState(),
              currentModel.getInteractionBlips(),
              null,
              currentModel.getUnreadCount(),
              currentModel.isRead(),
              currentModel.isReadStateKnown(),
              currentModel.isReadStateStale())
              .withConversationManifest(currentModel.getConversationManifest());
      currentModelField.set(controller, nextModel);
      lastModel = nextModel;
    }

    private String modelSelectedWaveId(Object model) throws Exception {
      Method method = model.getClass().getMethod("getSelectedWaveId");
      String waveId = (String) method.invoke(model);
      return waveId == null ? "" : waveId;
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

  private static SidecarSelectedWaveUpdate liveReplyFragmentUpdate(
      String replySnapshot, long resultingVersion, String historyHash) {
    return liveReplyFragmentUpdate(replySnapshot, resultingVersion, historyHash, resultingVersion);
  }

  private static SidecarSelectedWaveUpdate liveReplyFragmentUpdate(
      String replySnapshot, long resultingVersion, String historyHash, long fragmentVersion) {
    return liveBlipFragmentUpdate(
        "b+reply", replySnapshot, resultingVersion, historyHash, fragmentVersion);
  }

  private static SidecarSelectedWaveUpdate liveBlipFragmentUpdate(
      String blipId,
      String rawSnapshot,
      long resultingVersion,
      String historyHash,
      long fragmentVersion) {
    // Live deltas pushed from the server carry snapshotVersion = -1 (the codec default when the
    // server omits the field). Using -1L here ensures the projector treats this as an incremental
    // live delta and merges it into the prior viewport rather than replacing it.
    String segment = "blip:" + blipId;
    return new SidecarSelectedWaveUpdate(
        2,
        "example.com!w+1/example.com!conv+root",
        true,
        "chan-1",
        resultingVersion,
        historyHash,
        Arrays.asList("user@example.com", "teammate@example.com"),
        Collections.<SidecarSelectedWaveDocument>emptyList(),
        new SidecarSelectedWaveFragments(
            -1L,
            Math.max(0L, fragmentVersion - 1L),
            fragmentVersion,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange(
                    segment, Math.max(0L, fragmentVersion - 1L), fragmentVersion)),
            Arrays.asList(
                new SidecarSelectedWaveFragment(segment, rawSnapshot, 0, 0))));
  }

  private static SidecarSelectedWaveUpdate metadataOnlyLiveUpdate(
      long resultingVersion, String historyHash) {
    return new SidecarSelectedWaveUpdate(
        2,
        "example.com!w+1/example.com!conv+root",
        true,
        "chan-1",
        resultingVersion,
        historyHash,
        Arrays.asList("user@example.com", "teammate@example.com"),
        Collections.<SidecarSelectedWaveDocument>emptyList(),
        new SidecarSelectedWaveFragments(
            resultingVersion,
            Math.max(0L, resultingVersion - 1L),
            resultingVersion,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange(
                    "manifest", Math.max(0L, resultingVersion - 1L), resultingVersion),
                new SidecarSelectedWaveFragmentRange(
                    "index", Math.max(0L, resultingVersion - 1L), resultingVersion)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                new SidecarSelectedWaveFragment("index", "metadata", 0, 0))));
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

  private static SidecarFragmentsResponse fragmentsResponseWithBlipMetadata(
      String firstBlipId,
      String firstSnapshot,
      String firstAuthor,
      long firstLastModified,
      String secondBlipId,
      String secondSnapshot,
      String secondAuthor,
      long secondLastModified) {
    StringBuilder json =
        new StringBuilder(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+1/~/conv+root\","
                + "\"version\":{\"snapshot\":48,\"start\":44,\"end\":48},"
                + "\"blips\":[{\"id\":\"");
    json.append(firstBlipId)
        .append("\",\"author\":\"")
        .append(firstAuthor)
        .append("\",\"lastModifiedTime\":")
        .append(firstLastModified)
        .append("}");
    if (secondBlipId != null) {
      json.append(",{\"id\":\"")
          .append(secondBlipId)
          .append("\",\"author\":\"")
          .append(secondAuthor)
          .append("\",\"lastModifiedTime\":")
          .append(secondLastModified)
          .append("}");
    }
    json.append("],\"ranges\":[{\"segment\":\"blip:")
        .append(firstBlipId)
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
    return SidecarFragmentsResponse.fromJson(json.toString());
  }

  private static SidecarFragmentsResponse fragmentsResponseWithRawManifest() {
    return SidecarFragmentsResponse.fromJson(
        "{\"status\":\"ok\",\"waveRef\":\"example.com/w+1/~/conv+root\","
            + "\"version\":{\"snapshot\":48,\"start\":44,\"end\":48},"
            + "\"ranges\":[{\"segment\":\"manifest\",\"from\":44,\"to\":48},"
            + "{\"segment\":\"blip:b+next\",\"from\":44,\"to\":48},"
            + "{\"segment\":\"blip:b+tail\",\"from\":44,\"to\":48}],"
            + "\"fragments\":[{\"segment\":\"manifest\","
            + "\"rawSnapshot\":\"<conversation><blip id=\\\"b+root\\\">"
            + "<thread id=\\\"t+next\\\"><blip id=\\\"b+next\\\">"
            + "<thread id=\\\"t+tail\\\"><blip id=\\\"b+tail\\\"/>"
            + "</thread></blip></thread></blip></conversation>\","
            + "\"adjust\":[],\"diff\":[]},"
            + "{\"segment\":\"blip:b+next\",\"rawSnapshot\":\"Next loaded\","
            + "\"adjust\":[],\"diff\":[]},"
            + "{\"segment\":\"blip:b+tail\",\"rawSnapshot\":\"Tail loaded\","
            + "\"adjust\":[],\"diff\":[]}]}");
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

  /** F-4 (#1039 / R-4.4): captured markBlipRead dispatch in the test harness. */
  private static final class MarkBlipReadAttempt {
    private final String waveId;
    private final String blipId;
    private final J2clSearchPanelController.SuccessCallback<Integer> success;
    private final J2clSearchPanelController.ErrorCallback error;

    private MarkBlipReadAttempt(
        String waveId,
        String blipId,
        J2clSearchPanelController.SuccessCallback<Integer> success,
        J2clSearchPanelController.ErrorCallback error) {
      this.waveId = waveId;
      this.blipId = blipId;
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

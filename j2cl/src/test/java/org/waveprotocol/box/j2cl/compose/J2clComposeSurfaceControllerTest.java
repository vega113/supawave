package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentIdGenerator;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

@J2clTestInput(J2clComposeSurfaceControllerTest.class)
public class J2clComposeSurfaceControllerTest {
  @Test
  public void initialModelEnablesCreateAndKeepsReplyUnavailableUntilWriteSession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(gateway, view, new FakeFactory(), waveId -> { }, waveId -> { });

    controller.start();

    Assert.assertTrue(view.model.isCreateEnabled());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
  }

  @Test
  public void writeSessionEnablesReplyAndPublishesTargetLabel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertTrue(view.model.isReplyAvailable());
    Assert.assertEquals("b+root", view.model.getReplyTargetLabel());
  }

  @Test
  public void sameWaveBasisRefreshPreservesDraftAndSurfacesStaleSubmitState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.resolveBootstrap();

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void differentWaveSelectionClearsReplyDraft() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
  }

  @Test
  public void missingSelectedWaveRejectsReplyWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(new J2clSidecarWriteSession(null, "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("", view.model.getReplyStatusText());
    Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void emptySelectedWaveRejectsReplyWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(new J2clSidecarWriteSession("", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("", view.model.getReplyStatusText());
    Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void differentWaveSelectionDuringStaleSubmitPreservesDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
  }

  @Test
  public void laterDifferentWaveSelectionAfterStaleSubmitClearsDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+3", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void nullWriteSessionAfterStaleSubmitPreservesDraftUntilDifferentWaveReconnect() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(null);

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void nullWriteSessionAfterStaleSubmitPreservesDraftThroughSameWaveReconnect() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void nullWriteSessionPreservesFreshDraftThroughSameWaveReconnect() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Fresh draft");
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Fresh draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void sameWaveRefreshesAfterStaleSubmitKeepDraftAndErrorUntilRetry() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void editingStaleDraftThenNavigatingToDifferentWaveClearsEditedDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Edited draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+3", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void returningToOriginalWaveAfterStaleDifferentWaveKeepsDraftForReview() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void staleDraftRetrySuccessClearsStaleState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, factory, new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.autoResolveBootstrap = true;
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertEquals("Edited draft", factory.lastReplyText);
  }

  @Test
  public void retryInvalidatedByAnotherSessionChangeReparksStaleDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Retry draft");
    controller.onReplySubmitted("Retry draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Retry draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void staleDraftRetryFailureClearsStaleStateAndKeepsDraftEditable() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    gateway.submitResponse = new SidecarSubmitResponse(1, "server rejected", 45L);
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.autoResolveBootstrap = true;
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("server rejected", view.model.getReplyErrorText());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void staleDraftRetryBootstrapFailureClearsStaleStateAndKeepsDraftEditable() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.bootstrapError = "bootstrap unavailable";
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("bootstrap unavailable", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void sameWaveRefreshAfterEditingStaleDraftKeepsDraftWithoutStaleBanner() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Edited draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void signOutWhileReplyStaleClearsStaleMarkersAndError() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onSignedOut();

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertEquals(
        "Sign in to create or reply in the J2CL root shell.", view.model.getReplyStatusText());
  }

  @Test
  public void bootstrapFailurePreservesDraftAndSurfacesRootSubmitError() {
    FakeGateway gateway = new FakeGateway();
    gateway.bootstrapError = "bootstrap unavailable";
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertEquals("bootstrap unavailable", view.model.getReplyErrorText());
  }

  @Test
  public void successfulReplyClearsDraftAndRefreshesThroughHandoff() {
    FakeGateway gateway = new FakeGateway();
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, factory, new ArrayList<String>(), refreshed);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    Assert.assertEquals("Reply", factory.lastReplyText);
  }

  @Test
  public void toolbarBoldCommandEmitsStructuredRichReplyAnnotation() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            refreshed::add);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("bold", view.model.getActiveCommandId());
    Assert.assertEquals("Bold applied to the current draft.", view.model.getCommandStatusText());
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"",
        "{\"1\":{\"2\":[\"fontWeight\"]}}");
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void richEditCommandAppliedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("richEdit.command.applied", event.getName());
    Assert.assertEquals("bold", event.getFields().get("commandId"));
    Assert.assertEquals("applied", event.getFields().get("result"));
  }

  @Test
  public void richEditCommandClearedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("richEdit.command.applied", event.getName());
    Assert.assertEquals("bold", event.getFields().get("commandId"));
    Assert.assertEquals("cleared", event.getFields().get("result"));
  }

  @Test
  public void clearFormattingAcceptedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));

    Assert.assertEquals("richEdit.command.applied", telemetry.lastEvent().getName());
    Assert.assertEquals("clear-formatting", telemetry.lastEvent().getFields().get("commandId"));
    Assert.assertEquals("cleared", telemetry.lastEvent().getFields().get("result"));
  }

  @Test
  public void richEditTelemetryDoesNotEmitForRejectedActions() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT);

    Assert.assertTrue(telemetry.events().isEmpty());
  }

  @Test
  public void throwingTelemetrySinkDoesNotBreakRichEditCommand() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newControllerWithTelemetry(
            view,
            event -> {
              throw new RuntimeException("telemetry boom");
            });

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("bold", view.model.getActiveCommandId());
  }

  @Test
  public void replySubmitUsesFormattingSnapshotFromSubmitClick() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onReplySubmitted("Snapshot reply");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ITALIC));

    gateway.resolveBootstrap();

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Snapshot reply\"");
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontStyle"));
  }

  @Test
  public void replyToolbarFormattingDoesNotAffectCreateWaveSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> created = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            created::add,
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onCreateSubmitted("New wave");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals(Arrays.asList("example.com/w+seedA"), created);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"New wave\"");
  }

  @Test
  public void toolbarRichCommandTogglesOffBeforeSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("", view.model.getActiveCommandId());
    controller.onReplySubmitted("Plain reply");

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
  }

  @Test
  public void toolbarRichCommandToggleOffPreservesActiveUploadStatus() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "uploading.png")));
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

    // Toggle Bold off — the active upload must not be silently overwritten by "Bold cleared."
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("attachment-upload-queue", view.model.getActiveCommandId());
    Assert.assertEquals("Uploading uploading.png (0%).", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void toolbarRichCommandToggleOffPreservesUploadErrorStatus() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "fail.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "error", null));
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

    // Toggle Bold off — the attachment error must not be silently cleared.
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertTrue(view.model.getCommandErrorText().contains("fail.png"));
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void clearFormattingCommandRemovesStructuredInlineAnnotation() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Formatting cleared.", view.model.getCommandStatusText());
    controller.onReplySubmitted("Plain reply");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void unsupportedRichToolbarActionFallsBackToToolbarUnavailable() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void unsupportedToolbarActionFallsBackEvenBeforeWaveIsOpen() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void unsupportedToolbarActionDoesNotClearExistingCommandError() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(new Object());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));
    String existingError = view.model.getCommandErrorText();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));

    Assert.assertEquals(existingError, view.model.getCommandErrorText());
  }

  @Test
  public void inlineRichToolbarActionRequiresSelectedWave() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals(
        "Open a wave before using rich-edit toolbar actions.", view.model.getCommandErrorText());
  }

  @Test
  public void signedOutToolbarActionShowsNeutralToolbarCopy() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onSignedOut();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals("Sign in before using toolbar actions.", view.model.getCommandErrorText());
  }

  @Test
  public void signedOutAttachmentSelectionShowsSignInAndSkipsUpload() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onSignedOut();
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Sign in before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void signedOutPastedImageShowsSignInAndSkipsUpload() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onSignedOut();
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Sign in before pasting an image.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentToolbarCommandDoesNotPretendToInsertWithoutSelection() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    controller.onReplySubmitted("Attach later");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("\"1\":\"image\""));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void pastedImageRequiresSelectedWave() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before pasting an image.", view.model.getCommandErrorText());
  }

  @Test
  public void selectedAttachmentRequiresSelectedWaveWithoutLeavingActiveCommand() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "now.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentInsertRequiresSelectedWaveWithoutLeavingActiveCommand() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(0, view.openAttachmentPickerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void nonSurfacedAttachmentToolbarIdsFallBackToToolbarUnavailable() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CAPTION));
    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_OPEN));
    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_DOWNLOAD));
  }

  @Test
  public void nonSurfacedAttachmentToolbarIdsDoNotClearExistingCommandError() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(null);
    String existingError = view.model.getCommandErrorText();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_OPEN));

    Assert.assertEquals(existingError, view.model.getCommandErrorText());
  }

  @Test
  public void nullPastedImageShowsAttachmentErrorWithoutStartingUpload() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(null);

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertEquals("Pasted image payload is required.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentInsertToolbarOpensPickerAndRestoresReplyFocus() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(1, view.openAttachmentPickerCalls);
    Assert.assertEquals(1, view.focusReplyComposerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void emptyAttachmentSelectionStaysQuietAfterPickerCancel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT);
    controller.onAttachmentFilesSelected(
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>());

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void emptyAttachmentSelectionClearsExistingCommandError() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(null);
    Assert.assertFalse(view.model.getCommandErrorText().isEmpty());

    controller.onAttachmentFilesSelected(
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>());

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentToolbarInsertIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(0, view.openAttachmentPickerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void attachmentSizeCanChangeWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE));

    Assert.assertEquals("attachment-size-large", view.model.getActiveCommandId());
    Assert.assertEquals("Large attachment size selected.", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void selectedAttachmentIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "busy.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void pastedImageIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void pasteImageToolbarActionDocumentsPasteHintAndRestoresFocus() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE));

    Assert.assertEquals("attachment-paste-image", view.model.getActiveCommandId());
    Assert.assertEquals("Paste an image into the reply box.", view.model.getCommandStatusText());
    Assert.assertEquals(1, view.focusReplyComposerCalls);
  }

  @Test
  public void clearFormattingPreservesActiveUploadStatusPriority() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "busy.png")));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));

    Assert.assertEquals("attachment-upload-queue", view.model.getActiveCommandId());
    Assert.assertEquals("Uploading busy.png (0%).", view.model.getCommandStatusText());
  }

  @Test
  public void selectedAttachmentUploadsAndSubmitsStructuredReplyContent() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            refreshed::add);
    Object payload = new Object();

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE);
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(payload, "diagram.png")));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    Assert.assertSame(payload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("Uploading diagram.png (0%).", view.model.getCommandStatusText());

    transport.requests.get(0).getProgressCallback().onProgress(37);

    Assert.assertEquals("Uploading diagram.png (37%).", view.model.getCommandStatusText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "{\"1\":\"display-size\",\"2\":\"large\"}",
        "\"2\":\"diagram.png\"");
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void attachmentIdsContinueAcrossSuccessfulReplyBatches() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "first.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}");

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}");
  }

  @Test
  public void attachmentControllerFactoryKeepsIdsMonotonicAcrossWaveChanges() {
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(transport));
    J2clAttachmentComposerController firstController =
        factory.create(
            "example.com/w+1/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController secondController =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController otherDomainController =
        factory.create(
            "other.example/w+1/~/conv+root",
            "other.example",
            (document, insertion) -> { },
            () -> { });

    firstController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    firstController.cancelAndReset();
    firstController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "after-cancel.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    otherDomainController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "other.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    // Only selections consume ids: cancel clears queue state without rewinding the shared counter.
    secondController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals("/attachment/other.example/seedA", transport.requests.get(2).getUrl());
    Assert.assertEquals("/attachment/example.com/seedC", transport.requests.get(3).getUrl());

    FakeAttachmentTransport secondTransport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory secondFactory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(secondTransport));
    J2clAttachmentComposerController secondFactoryController =
        secondFactory.create(
            "example.com/w+fresh/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    secondFactoryController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fresh.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(
        "/attachment/example.com/seedA", secondTransport.requests.get(0).getUrl());
  }

  @Test
  public void attachmentControllerFactoryUsesGeneratorDomainValidationContract() {
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(transport));
    J2clAttachmentComposerController controller =
        factory.create(
            "example.com/w+1/~/conv+root",
            "  example.com  ",
            (document, insertion) -> { },
            () -> { });

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "trimmed.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    J2clAttachmentComposerController normalizedController =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    normalizedController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "normalized.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());

    assertFactoryRejectsDomain(factory, null);
    assertFactoryRejectsDomain(factory, "");
    assertFactoryRejectsDomain(factory, "   ");
    assertFactoryRejectsDomain(factory, "example.com/bad");

    J2clAttachmentComposerController freshController =
        factory.create(
            "fresh.example/w+1/~/conv+root",
            "fresh.example",
            (document, insertion) -> { },
            () -> { });
    freshController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fresh.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/fresh.example/seedA", transport.requests.get(2).getUrl());
  }

  @Test
  public void attachmentControllerFactoryValidatesDomainBeforeCreatingUploadClient() {
    CountingUploadClientFactory clientFactory = new CountingUploadClientFactory();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed", clientFactory);

    assertFactoryRejectsDomain(factory, null);
    assertFactoryRejectsDomain(factory, "");
    assertFactoryRejectsDomain(factory, "example.com/bad");

    Assert.assertEquals(0, clientFactory.createCalls);
  }

  @Test
  public void attachmentControllerFactoryCreatesUploadClientPerController() {
    CountingUploadClientFactory clientFactory = new CountingUploadClientFactory();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed", clientFactory);

    factory.create(
        "example.com/w+1/~/conv+root",
        "example.com",
        (document, insertion) -> { },
        () -> { });
    factory.create(
        "example.com/w+2/~/conv+root",
        "example.com",
        (document, insertion) -> { },
        () -> { });

    Assert.assertEquals(2, clientFactory.createCalls);
  }

  @Test
  public void publicAttachmentControllerFactoryCreatesFreshUploadClients() throws Exception {
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed");

    J2clAttachmentComposerController first =
        factory.create(
            "example.com/w+1/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController second =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });

    Assert.assertNotSame(uploadClient(first), uploadClient(second));
  }

  @Test
  public void sameWaveReconnectPreservesInFlightAttachmentUpload() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"late.png\"");
  }

  @Test
  public void differentWaveReconnectAfterDisconnectDropsInFlightAttachmentUpload() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void pastedImageUploadsAndSubmitsStructuredReplyContentWhenSignedIn() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            refreshed::add);
    Object payload = new Object();

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(payload);

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertSame(payload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("Uploading pasted-image.png (0%).", view.model.getCommandStatusText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"pasted-image.png\"");
  }

  @Test
  public void richToolbarAnnotationSurvivesAttachmentUploadQueueStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "bold-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("Bold plus file");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold plus file\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"bold-attachment.png\"");
  }

  @Test
  public void richToolbarAnnotationCanToggleOffAfterAttachmentQueueStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "plain-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onReplySubmitted("Plain plus file");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "\"2\":\"Plain plus file\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"plain-attachment.png\"");
  }

  @Test
  public void richToolbarAnnotationSurvivesAttachmentUploadErrorStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "failed-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "failed", null));
    Assert.assertTrue(view.model.getCommandErrorText().contains("failed-attachment.png"));
    controller.onReplySubmitted("Bold without failed file");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold without failed file\"");
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("\"1\":\"attachment\""));
  }

  @Test
  public void richToolbarAnnotationSurvivesReplyFailureForRetry() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals("server rejected", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals("", view.model.getActiveCommandId());

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(2, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"");

    controller.onReplySubmitted("Plain after retry");

    Assert.assertEquals(3, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain after retry\"");
  }

  @Test
  public void richToolbarAnnotationSurvivesMultipleReplyFailuresForRetry() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");
    controller.onReplySubmitted("Bold reply");

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(3, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"");
  }

  @Test
  public void richToolbarCanToggleOffAfterReplyFailureBeforeRetry() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Plain retry");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain retry\"");
  }

  @Test
  public void richToolbarCanSwitchFormattingAfterReplyFailureBeforeRetry() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ITALIC));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Italic retry");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontStyle\",\"3\":\"italic\"}]}}",
        "\"2\":\"Italic retry\"");
  }

  @Test
  public void waveChangeAfterFailedRichReplyClearsPreservedAnnotation() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 51L);
    controller.onReplySubmitted("Plain new wave");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain new wave\"");
  }

  @Test
  public void replySubmitWaitsForInFlightAttachmentUploadBeforeBuildingRequest() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));

    controller.onReplySubmitted("Draft with late file");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        J2clComposeSurfaceController.PENDING_ATTACHMENT_REPLY_MESSAGE,
        view.model.getReplyErrorText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    controller.onReplySubmitted("Draft with late file");

    Assert.assertEquals(1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"late.png\"");
  }

  @Test
  public void submitWaitThenCancelFallsBackToEmptyReplyGuard() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancel.png")));
    controller.onReplySubmitted("");
    Assert.assertEquals(
        J2clComposeSurfaceController.PENDING_ATTACHMENT_REPLY_MESSAGE,
        view.model.getReplyErrorText());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void blankAttachmentFileNameFallsBackToGenericCaption() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "   ")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    Assert.assertEquals("Attached attachment.", view.model.getCommandStatusText());
    controller.onReplySubmitted("");

    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"attachment\"");
  }

  @Test
  public void insertedAttachmentClearsEmptyReplyValidationError() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("");
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "ok.png")));
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void insertedAndCompletedAttachmentDoesNotClearUnrelatedReplyFailure() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("will fail");
    Assert.assertEquals("conflict", view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "new.png")));
    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
  }

  @Test
  public void cancelledAttachmentStateChangeDoesNotClearUnrelatedReplyFailure() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("will fail");
    Assert.assertEquals("conflict", view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "new.png")));
    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
  }

  @Test
  public void waveChangeResetsAttachmentDisplaySizeToMedium() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "medium.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"display-size\",\"2\":\"medium\"}");
  }

  @Test
  public void pastedImageFailureShowsAttachmentErrorAndDoesNotSubmitEmptyReply() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(new Object());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));

    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertTrue(view.model.getCommandErrorText().contains("pasted-image.png"));
    Assert.assertTrue(view.model.getCommandErrorText().contains("unexpected HTTP 202"));

    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void latestAttachmentFailureIsSurfacedWhenFailuresAccumulate() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "first.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "first", null));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(500, "second", null));

    Assert.assertTrue(view.model.getCommandErrorText().contains("second.png"));
  }

  @Test
  public void failedReplyPreservesUploadedAttachmentForRetry() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "retry.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    controller.onReplySubmitted("");

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals(1, gateway.submitCalls);

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 46L);
    controller.onReplySubmitted("");

    Assert.assertEquals(2, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"retry.png\"");
  }

  @Test
  public void cancelAttachmentToolbarActionClearsUploadQueueState() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancel.png")));
    Assert.assertEquals("Uploading cancel.png (0%).", view.model.getCommandStatusText());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));

    Assert.assertEquals("attachment-cancel", view.model.getActiveCommandId());
    Assert.assertEquals("Attachment upload queue cancelled.", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void cancelAttachmentToolbarActionPreservesInsertedAndContinuesIdGenerator() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "kept.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    Assert.assertEquals("Pending uploads cancelled. Attached files kept.", view.model.getCommandStatusText());
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"kept.png\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}",
        "\"2\":\"second.png\"");
  }

  @Test
  public void cancelAttachmentToolbarActionPreservesIdGeneratorAfterPendingUploadCancel() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancelled.png")));
    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("example.com/seedA"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}",
        "\"2\":\"second.png\"");
  }

  @Test
  public void lateAttachmentCompletionAfterCancelIsIgnored() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL);

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void signOutMidAttachmentUploadIgnoresLateCompletion() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "signout.png")));
    controller.onSignedOut();
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertFalse(view.model.isReplyAvailable());
  }

  @Test
  public void waveChangeMidAttachmentUploadIgnoresLateCompletionForNewWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "old.png")));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void signedOutMidFlightCreateAbandonsPendingCallback() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    List<String> created = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), created, new ArrayList<String>());

    controller.start();
    controller.onCreateSubmitted("Hello");
    controller.onSignedOut();
    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(created.isEmpty());
    Assert.assertFalse(view.model.isCreateEnabled());
  }

  @Test
  public void signOutAfterCreateFailureClearsCreateError() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "server rejected", 45L);
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onCreateSubmitted("Hello");

    Assert.assertEquals("server rejected", view.model.getCreateErrorText());
    Assert.assertEquals("", view.model.getCreateStatusText());
    controller.onSignedOut();

    Assert.assertFalse(view.model.isCreateEnabled());
    Assert.assertEquals("", view.model.getCreateErrorText());
    Assert.assertEquals("Sign in to create or reply in the J2CL root shell.", view.model.getCreateStatusText());
  }

  @Test
  public void signedOutMidFlightReplyAbandonsPendingCallbackAndClearsStaleState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), refreshed);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onSignedOut();
    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(refreshed.isEmpty());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void signedOutStateDisablesComposeWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSignedOut();
    controller.onCreateSubmitted("Hello");
    controller.onReplySubmitted("Reply");

    Assert.assertFalse(view.model.isCreateEnabled());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("Sign in to create or reply in the J2CL root shell.", view.model.getCreateStatusText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
    Assert.assertEquals(0, gateway.submitCalls);
  }

  private static J2clComposeSurfaceController newController(
      FakeGateway gateway,
      FakeView view,
      FakeFactory factory,
      List<String> created,
      List<String> refreshed) {
    return new J2clComposeSurfaceController(
        gateway, view, factory, created::add, refreshed::add);
  }

  // J-UI-3 (#1081, R-5.1) — title input value is recorded on the model and
  // cleared when the create succeeds.
  @Test
  public void onCreateTitleChangedPersistsTitleInModel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("My new wave");

    Assert.assertEquals("My new wave", view.model.getCreateTitleDraft());
    Assert.assertEquals("", view.model.getCreateDraft());
  }

  // J-UI-3 (#1081, R-5.1) — live edits are LOSSLESS for whitespace per
  // codex P1 PRRT_kwDOBwxLXs5-ColZ. Only embedded newlines get replaced
  // with a space (paste safety: a literal newline would break the
  // conv/title annotation span). Leading/trailing whitespace is preserved
  // so users can type multi-word titles like "My wave" without each
  // mid-word space being eaten between keystrokes.
  @Test
  public void onCreateTitleChangedKeepsTrailingSpaceForMultiWordEdits() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("My ");

    Assert.assertEquals(
        "trailing space must survive a live keystroke so the next character lands after it",
        "My ",
        view.model.getCreateTitleDraft());
  }

  // J-UI-3 — embedded newlines from a paste are replaced with spaces in
  // the live path so the conv/title annotation never spans more than one
  // line, but leading/trailing whitespace is preserved (only trimmed at
  // submit time).
  @Test
  public void onCreateTitleChangedNeutralisesPastedNewlinesButPreservesEdgeWhitespace() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("  pasted\nfrom-clipboard\r\n  ");

    // Each break run collapses to a single space (CRLF/LF/CR runs no
    // longer leak Windows-style double-spacing): the lone \n becomes one
    // space, the \r\n run becomes one space, and the original 2 trailing
    // spaces are preserved verbatim → 3 trailing spaces total.
    Assert.assertEquals(
        "  pasted from-clipboard   ", view.model.getCreateTitleDraft());
  }

  // J-UI-3 — submit-time normalisation trims edge whitespace so the
  // conv/title annotation in the outgoing delta is a clean single-line
  // span. Live state can still hold whitespace; only the submit path
  // trims.
  @Test
  public void onCreateSubmittedWithTitleTrimsEdgeWhitespaceAtSubmit() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("  Hello world  ", "body");

    // After submit clears the title, the model is empty. We assert via
    // the success path: blank-title-after-success means the trim happened
    // (otherwise the validation gate would have fired). The edge-trim
    // behaviour is also covered by richContentSubmitCreateEmits... below
    // via the encoded delta.
    Assert.assertEquals("", view.model.getCreateTitleDraft());
    Assert.assertEquals("Wave created.", view.model.getCreateStatusText());
  }

  // J-UI-3 — submit with both title and body succeeds and clears both
  // drafts on the success render.
  @Test
  public void onCreateSubmittedWithTitleClearsBothDraftsOnSuccess() {
    List<String> created = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), created, new ArrayList<String>());
    controller.start();
    controller.onCreateTitleChanged("Title");
    controller.onCreateDraftChanged("Body");

    controller.onCreateSubmittedWithTitle("Title", "Body");

    Assert.assertEquals("", view.model.getCreateDraft());
    Assert.assertEquals("", view.model.getCreateTitleDraft());
    Assert.assertEquals(Arrays.asList("example.com/w+new"), created);
  }

  // J-UI-3 — if the user edits the title while the create request is in
  // flight, the success handler must receive the title that was actually
  // submitted, not the live draft value.
  @Test
  public void handleCreateResponseUsesSnapshotTitleNotLiveTitleDraft() {
    List<String> stubTitles = new ArrayList<String>();
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();
    controller.onCreateSubmittedWithTitle("Submitted title", "body text");
    // User edits title while request is in flight
    controller.onCreateTitleChanged("Changed title");
    // Server responds — callback should use the submitted snapshot
    gateway.resolveBootstrap();
    Assert.assertEquals(Arrays.asList("Submitted title"), stubTitles);
  }

  // J-UI-3 — title-only submit (no body) is allowed; the user can ship a
  // wave with just a title. The body draft remains empty.
  @Test
  public void onCreateSubmittedWithTitleAllowsTitleOnlySubmit() {
    List<String> created = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), created, new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("Title only", "");

    Assert.assertEquals(Arrays.asList("example.com/w+new"), created);
    Assert.assertEquals("Wave created.", view.model.getCreateStatusText());
  }

  // J-UI-3 — submitting with both fields blank surfaces the existing
  // "enter some text" validation error rather than calling the gateway.
  @Test
  public void onCreateSubmittedWithBothBlankShowsValidationError() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("   ", "   ");

    Assert.assertEquals("Enter some text before creating a wave.", view.model.getCreateErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  // J-UI-3 — the rich-content factory receives a document with a
  // conv/title annotated component when a title was typed; the body text
  // appears as a separate TEXT component after the title.
  @Test
  public void richContentSubmitCreateEmitsTitleAnnotationFollowedByBody() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    controller.onCreateTitleChanged("Sprint planning");
    controller.onCreateDraftChanged("Standup at 10am");

    controller.onCreateSubmittedWithTitle("Sprint planning", "Standup at 10am");

    Assert.assertNotNull(gateway.lastSubmitRequest);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"conv/title\",\"3\":\"\"}]}}",
        "\"2\":\"Sprint planning\"",
        "{\"1\":{\"2\":[\"conv/title\"]}}",
        "\"2\":\"\\n\"",
        "\"2\":\"Standup at 10am\"");
    Assert.assertTrue(deltaJson.indexOf("Sprint planning") < deltaJson.indexOf("\\n"));
    Assert.assertTrue(deltaJson.indexOf("\\n") < deltaJson.indexOf("Standup at 10am"));
  }

  // J-UI-3 — the legacy single-arg create handler still routes through
  // the success path (back-compat with handlers that have not adopted
  // the title overload).
  @Test
  public void legacySingleArgCreateHandlerStillFiresOnSuccess() {
    final List<String> singleArgCalls = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            (J2clComposeSurfaceController.CreateSuccessHandler) singleArgCalls::add,
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("Title", "Body");

    Assert.assertEquals(Arrays.asList("example.com/w+new"), singleArgCalls);
  }

  // J-UI-3 (#1081, R-5.1) — CodeRabbit major PRRT_kwDOBwxLXs5-Cper:
  // body-only creates must derive a stub title from the body's first
  // non-blank line so the optimistic rail digest does not render as
  // "(untitled wave)" when the user clearly intended their typed text
  // to identify the wave.
  @Test
  public void bodyOnlyCreatePassesFirstBodyLineAsStubTitle() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) {
                onWaveCreated(waveId, "");
              }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("", "  hello world\nsecond line\n");

    Assert.assertEquals(Arrays.asList("hello world"), stubTitles);
  }

  // J-UI-3 — when both title and body are typed, the explicit title wins
  // over the body-derived fallback. Submit-time normalisation trims edge
  // whitespace.
  @Test
  public void submitWithTitleAndBodyPassesTrimmedTitleAsStubTitle() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("  Sprint plan  ", "body line");

    Assert.assertEquals(Arrays.asList("Sprint plan"), stubTitles);
  }

  // J-UI-3 — when neither title nor body has any non-blank text the
  // stub title is empty so onOptimisticDigest's "(untitled wave)"
  // fallback does fire as the last resort.
  @Test
  public void bodyOnlyCreateWithBlankBodyFallsThroughToUntitledFallback() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    // The validation gate normally blocks (both blank), so to exercise
    // the fallback we use a body that is whitespace-only with a
    // non-blank-but-trimmable suffix character to satisfy the gate.
    controller.onCreateSubmittedWithTitle("", "   \n\n.");

    // First non-blank line is ".", so that becomes the stub title.
    Assert.assertEquals(Arrays.asList("."), stubTitles);
  }

  // F-3.S2 (#1038, R-5.3) — telemetry-only assertions for the mention
  // pick / abandon paths. The controller does not change model state
  // (chip lives on the lit composer DOM); it just records telemetry.
  @Test
  public void onMentionPickedRecordsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);
    controller.start();
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    Assert.assertTrue(
        "compose.mention_picked event should be recorded",
        telemetry.events().stream().anyMatch(e -> "compose.mention_picked".equals(e.getName())));
  }

  // F-3.S2 (#1038, R-5.3, PR #1066 review thread PRRT_kwDOBwxLXs592RVM)
  // — picking a mention then submitting a reply must serialise a
  // link/manual annotation referencing the participant address
  // alongside the surrounding text. Without this the outgoing delta
  // is just the literal `@DisplayName` substring with no annotation.
  @Test
  public void mentionPickIsSerialisedAsLinkManualAnnotationOnReplySubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("Hi @Alice Adams welcome");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        delta,
        "\"2\":\"Hi \"",
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"alice@example.com\"}]}}",
        "\"2\":\"@Alice Adams\"",
        "{\"1\":{\"2\":[\"link/manual\"]}}",
        "\"2\":\" welcome\"");
  }

  @Test
  public void multiplePickedMentionsAreSerialisedInOrder() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onMentionPicked("bob@example.com", "Bob Brown");
    controller.onReplySubmitted("@Alice Adams and @Bob Brown");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        delta,
        "\"alice@example.com\"",
        "\"bob@example.com\"",
        "\"2\":\"@Alice Adams\"",
        "\"2\":\"@Bob Brown\"",
        "\"2\":\" and \"");
  }

  @Test
  public void mentionPicksClearedAfterSuccessfulSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("Hi @Alice Adams");
    Assert.assertTrue(
        "first submit must include the link/manual annotation",
        gateway.lastSubmitRequest.getDeltaJson().contains("alice@example.com"));
    // Second submit without picking again should NOT carry the
    // annotation: pendingMentions is cleared on success.
    controller.onReplySubmitted("Plain followup");
    String secondDelta = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertFalse(
        "follow-up reply must not carry a stale link/manual annotation",
        secondDelta.contains("link/manual"));
    assertContains(secondDelta, "\"2\":\"Plain followup\"");
  }

  @Test
  public void mentionPickWithDeletedChipFallsBackToPlainText() {
    // User picks a mention, then deletes the chip on the lit side.
    // The lit composer's atomic-delete handler removes the chip span;
    // the controller's pendingMentions still has the entry, but the
    // chip text no longer occurs in the draft. The submit must fall
    // back to a plain-text component instead of failing.
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("plain text only");
    String delta = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertFalse(
        "no chip text in draft means no annotation should be emitted",
        delta.contains("link/manual"));
    assertContains(delta, "\"2\":\"plain text only\"");
  }

  // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — two picks with the
  // same chipText (e.g. duplicate display names that resolve to
  // distinct addresses) must both serialise as separate mention
  // annotations, each pointing at its own address. The previous
  // first-text-occurrence match collapsed both onto the leading
  // chip and the second mention dropped its annotation.
  @Test
  public void duplicateDisplayNameMentionsBindByChipOffsetNotFirstMatch() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    // Two picks share the chipText "@Alice" but have distinct
    // addresses; chipTextOffset reflects the chip insertion point in
    // the body's plain text at pick time.
    // Chip 1 sits at offset 0 ("@Alice" + " and "); chip 2 sits at
    // offset 11 (right after " and "). Both share the chipText
    // "@Alice" but resolve to distinct addresses.
    controller.onMentionPicked("alice@a.example.com", "Alice", 0);
    controller.onMentionPicked("alice@b.example.com", "Alice", 11);
    controller.onReplySubmitted("@Alice and @Alice");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // Both addresses must round-trip as link/manual annotations; the
    // surrounding plain-text " and " run remains plain text.
    assertContains(
        delta,
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"alice@a.example.com\"}]}}",
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"alice@b.example.com\"}]}}",
        "\"2\":\" and \"");
  }

  // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — when the user
  // types `@Alice` plain text first and then picks a real `@Alice`
  // chip after, the picked chip's offset must steer the binding to
  // the second occurrence. Otherwise the plain literal swallows the
  // annotation and the real chip is submitted as plain text.
  @Test
  public void mentionPickAfterPlainAtNameBindsToChipNotPlainOccurrence() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    // Draft has two `@Alice` substrings; only the second is a picked
    // chip (offset 11 — past the literal occurrence at 0). The first
    // `@Alice` must remain plain text in the outgoing delta.
    controller.onMentionPicked("alice@example.com", "Alice", 11);
    controller.onReplySubmitted("@Alice and @Alice");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // The text run before the chip carries the literal `@Alice` plus
    // the connector word; the annotation wraps only the chip
    // occurrence. We assert the text run starts with `@Alice and `
    // (the literal `@Alice` is part of plain-text payload, not an
    // annotation).
    assertContains(
        delta,
        "\"2\":\"@Alice and \"",
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"alice@example.com\"}]}}",
        "\"2\":\"@Alice\"",
        "{\"1\":{\"2\":[\"link/manual\"]}}");
    // Only ONE link/manual annotation start in the delta — the plain
    // literal must NOT have been bound.
    Assert.assertEquals(
        "exactly one link/manual annotation must be emitted",
        1,
        countOccurrences(delta, "\"1\":\"link/manual\""));
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int idx = haystack.indexOf(needle, from);
      if (idx < 0) return count;
      count++;
      from = idx + needle.length();
    }
  }

  @Test
  public void onMentionAbandonedRecordsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);
    controller.start();
    controller.onMentionAbandoned();
    Assert.assertTrue(
        "compose.mention_abandoned event should be recorded",
        telemetry.events().stream().anyMatch(e -> "compose.mention_abandoned".equals(e.getName())));
  }

  // F-3.S2 (#1038, R-5.4 step 3) — task-toggle goes through the
  // gateway and emits telemetry with state="completed" or "open".
  @Test
  public void onTaskToggledSubmitsDeltaAndRecordsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskToggled("b+root", true);
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    Assert.assertTrue(
        "compose.task_toggled (completed) event should be recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.task_toggled".equals(e.getName())
                        && "completed".equals(e.getFields().get("state"))));
  }

  @Test
  public void onTaskToggledOpenStateRecordsOpenTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onTaskToggled("b+root", false);
    Assert.assertTrue(
        "compose.task_toggled (open) event should be recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.task_toggled".equals(e.getName())
                        && "open".equals(e.getFields().get("state"))));
  }

  @Test
  public void onTaskToggledIgnoresEmptyBlipId() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());
    controller.start();
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onTaskToggled("", true);
    controller.onTaskToggled(null, true);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onTaskToggledIgnoredWhenNoSelectedWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onTaskToggled("b+root", true);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  // F-3.S3 (#1038, R-5.5): reaction toggle path. The controller
  // computes adding-vs-removing from its cached snapshot, fetches the
  // bootstrap, and submits a delta against the react+ document.
  @Test
  public void onReactionToggledAddsWhenSnapshotEmpty() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    Assert.assertNotNull(gateway.lastSubmitRequest);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertTrue(
        "delta must target react+ document, got: " + deltaJson,
        deltaJson.contains("\"1\":\"react+b+root\""));
    Assert.assertTrue(
        "added telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.reaction_toggled".equals(e.getName())
                        && "added".equals(e.getFields().get("state"))));
  }

  @Test
  public void onReactionToggledRemovesWhenUserAlreadyReacted() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    java.util.HashMap<String, java.util.List<SidecarReactionEntry>> snapshots =
        new java.util.HashMap<>();
    snapshots.put(
        "b+root",
        java.util.Arrays.asList(
            new SidecarReactionEntry("👍", java.util.Arrays.asList("user@example.com"))));
    controller.setReactionSnapshots(snapshots);
    int beforeSubmits = gateway.submitCalls;
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    // toggle off should emit a delete-element-start for the user.
    Assert.assertTrue(
        "delta must contain delete-element-start for the user element",
        deltaJson.contains("\"7\":{\"1\":\"user\""));
    Assert.assertTrue(
        "removed telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.reaction_toggled".equals(e.getName())
                        && "removed".equals(e.getFields().get("state"))));
  }

  @Test
  public void onReactionToggledIgnoresWhenSignedOut() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    controller.onSignedOut();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onReactionToggledIgnoresEmptyBlipIdAndEmoji() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onReactionToggled("", "👍");
    controller.onReactionToggled("b+root", "");
    controller.onReactionToggled(null, null);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onReactionToggledNotifiesAddressListenerOnBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    final String[] capturedAddress = new String[] {""};
    controller.setCurrentUserAddressListener(addr -> capturedAddress[0] = addr);
    openWaveForReply(controller);
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals("user@example.com", capturedAddress[0]);
  }

  @Test
  public void onTaskMetadataChangedSubmitsDelta() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskMetadataChanged("b+root", "alice@example.com", "2026-05-15");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
  }

  // F-3.S2 (#1068): regression — a no-op session refresh (same wave id,
  // new J2clSidecarWriteSession instance) used to break the deferred
  // task-toggle write because the post-bootstrap guard compared
  // sessions by reference. The fix compares by selected wave id, so
  // the submit must still go through.
  @Test
  public void onTaskToggledSurvivesNoOpSessionRefresh() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskToggled("b+root", true);
    // Bootstrap is still pending — simulate a no-op session refresh on
    // the same wave between the click and the bootstrap response.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 45L, "EFGH", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-toggle write must survive a no-op session refresh on the same wave",
        beforeSubmits + 1,
        gateway.submitCalls);
    Assert.assertEquals("chan-1", gateway.lastSubmitRequest.getChannelId());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"1\":45", "\"2\":\"EFGH\"");
  }

  @Test
  public void onTaskMetadataChangedSurvivesNoOpSessionRefresh() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskMetadataChanged("b+root", "alice@example.com", "2026-05-15");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 45L, "EFGH", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-metadata write must survive a no-op session refresh on the same wave",
        beforeSubmits + 1,
        gateway.submitCalls);
    Assert.assertEquals("chan-1", gateway.lastSubmitRequest.getChannelId());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"1\":45", "\"2\":\"EFGH\"");
  }

  @Test
  public void onTaskToggledDropsWriteOnWaveSwitch() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskToggled("b+root", true);
    // Genuine wave switch — the captured session no longer matches the
    // current selected wave; the write must be dropped.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 1L, "ZZZZ", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-toggle write must NOT submit after a real wave switch",
        beforeSubmits,
        gateway.submitCalls);
  }

  // F-3.S4 (#1038, R-5.6 step 1): drag-drop telemetry path. The
  // controller routes onDroppedFiles through the same upload plumbing
  // as the H.19 paperclip path, but emits a separate
  // compose.attachment_dropped telemetry event with the file kind.
  @Test
  public void onDroppedFilesEmitsDroppedTelemetryAndDelegatesToAttachmentSelection() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("payload-1", "photo.png"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "compose.attachment_dropped telemetry must record kind=image for .png",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "image".equals(e.getFields().get("kind"))
                        && "queued".equals(e.getFields().get("outcome"))
                        && "1".equals(e.getFields().get("count"))));
  }

  @Test
  public void onDroppedFilesRecordsEmptyOutcomeForEmptyDrop() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onDroppedFiles(Collections.<J2clComposeSurfaceController.AttachmentFileSelection>emptyList());
    Assert.assertTrue(
        "empty-drop telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "empty".equals(e.getFields().get("outcome"))));
  }

  // review-1077 Bug 8: drops blocked by the acceptance gates inside
  // onAttachmentFilesSelected (signed-out / no-wave / reply-submitting)
  // must report a dedicated rejected-* outcome rather than the
  // optimistic `queued` outcome the controller previously emitted.
  @Test
  public void onDroppedFilesRecordsRejectedOutcomeWhenNoSelectedWave() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    // No openWaveForReply() — no selected wave for this drop.
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("p1", "doc.pdf"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "drop without an active wave must record rejected-no-wave (not queued)",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "rejected-no-wave".equals(e.getFields().get("outcome"))));
    Assert.assertFalse(
        "blocked drop must NOT record outcome=queued",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "queued".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDroppedFilesRecordsRejectedOutcomeWhenSignedOut() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onSignedOut();
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("p1", "doc.pdf"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "drop while signed out must record rejected-signed-out outcome",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "rejected-signed-out".equals(e.getFields().get("outcome"))));
  }

  // F-3.S4 (#1038, R-5.6 F.6): blip-delete gateway wiring. The
  // controller fetches the bootstrap, calls
  // DeltaFactory.createBlipDeleteRequest, and submits the result.
  @Test
  public void onDeleteBlipRequestedSubmitsTombstoneDelta() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onDeleteBlipRequested("b+target", "example.com/w+1");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertTrue(
        "delta must target the deleted blip document",
        deltaJson.contains("\"1\":\"b+target\""));
    Assert.assertTrue(
        "delta must carry tombstone/deleted=true annotation",
        deltaJson.contains("tombstone/deleted")
            && deltaJson.contains("\"3\":\"true\""));
    Assert.assertTrue(
        "success telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "success".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresWhenSignedOut() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onSignedOut();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("b+target", "example.com/w+1");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "signed-out telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "signed-out".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresBlankBlipId() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("", "example.com/w+1");
    controller.onDeleteBlipRequested("   ", "example.com/w+1");
    controller.onDeleteBlipRequested(null, "example.com/w+1");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "missing-blip telemetry recorded for empty inputs",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "missing-blip".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresWhenNoSelectedWave() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("b+target", "");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "no-selected-wave telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "no-selected-wave".equals(e.getFields().get("outcome"))));
  }

  // F-3.S4 (#1038): if the user navigates away from the wave between
  // dispatching the confirm dialog and answering it, the controller
  // must reject the delete and emit a `wave-changed` telemetry event.
  @Test
  public void onDeleteBlipRequestedIgnoresWhenWaveChanged() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller); // selected wave = example.com/w+1
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("b+target", "example.com/w+other");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "wave-changed telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "wave-changed".equals(e.getFields().get("outcome"))));
  }

  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DQdB: a sign-out
  // mid-create bumps createGeneration so the deferred submit/bootstrap
  // callbacks are gated out, which means handleCreateFailure (and its
  // failure hook) never fires. onSignedOut must therefore drop the
  // submit-query stamp itself when there was an in-flight create, or
  // the next successful create scopes its optimistic stub to the
  // wrong rail.
  @Test
  public void onSignedOutMidCreateRunsFailureHookSoStampDoesNotLeak() {
    final int[] failureHookFires = {0};
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false; // hold the in-flight create.
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.setCreateFailureHook(() -> failureHookFires[0]++);
    controller.start();
    controller.onCreateSubmittedWithTitle("In-flight title", "body");
    Assert.assertTrue(
        "create must be in flight before sign-out aborts it",
        view.model.isCreateSubmitting());
    int firesBeforeSignOut = failureHookFires[0];

    controller.onSignedOut();

    Assert.assertEquals(
        "sign-out abort must run the failure hook so the stamp is dropped",
        firesBeforeSignOut + 1,
        failureHookFires[0]);
  }

  // J-UI-3 — when no create is in flight, sign-out must NOT call the
  // failure hook (no stamp to drop).
  @Test
  public void onSignedOutWithoutInflightCreateDoesNotRunFailureHook() {
    final int[] failureHookFires = {0};
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.setCreateFailureHook(() -> failureHookFires[0]++);
    controller.start();

    controller.onSignedOut();

    Assert.assertEquals(0, failureHookFires[0]);
  }

  private static J2clComposeSurfaceController newControllerWithTelemetry(
      FakeView view, J2clClientTelemetry.Sink telemetrySink) {
    return new J2clComposeSurfaceController(
        new FakeGateway(),
        view,
        J2clComposeSurfaceController.richContentDeltaFactory("seed"),
        waveId -> { },
        waveId -> { },
        telemetrySink);
  }

  private static void openWaveForReply(J2clComposeSurfaceController controller) {
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
  }

  private static J2clComposeSurfaceController.AttachmentControllerFactory
      testAttachmentControllerFactory(FakeAttachmentTransport transport) {
    return (waveRef, domain, insertionCallback, stateChangeCallback) ->
        new J2clAttachmentComposerController(
            waveRef,
            new J2clAttachmentUploadClient(transport),
            new J2clAttachmentIdGenerator(domain, "seed"),
            insertionCallback,
            stateChangeCallback);
  }

  private static void assertFactoryRejectsDomain(
      J2clComposeSurfaceController.AttachmentControllerFactory factory, String domain) {
    try {
      factory.create(
          "example.com/w+invalid/~/conv+root",
          domain,
          (document, insertion) -> { },
          () -> { });
      Assert.fail("Expected invalid domain to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("domain"));
    }
  }

  private static final class CountingUploadClientFactory
      implements J2clComposeSurfaceController.AttachmentUploadClientFactory {
    private int createCalls;

    @Override
    public J2clAttachmentUploadClient create() {
      createCalls++;
      return new J2clAttachmentUploadClient(new FakeAttachmentTransport());
    }
  }

  private static J2clAttachmentUploadClient uploadClient(
      J2clAttachmentComposerController controller) throws Exception {
    Field field = J2clAttachmentComposerController.class.getDeclaredField("uploadClient");
    field.setAccessible(true);
    return (J2clAttachmentUploadClient) field.get(controller);
  }

  private static void assertContains(String value, String... expectedSubstrings) {
    for (String expectedSubstring : expectedSubstrings) {
      Assert.assertTrue(
          "Expected to find <" + expectedSubstring + "> in <" + value + ">",
          value.contains(expectedSubstring));
    }
  }

  // J-UI-5 (#1083, R-5.1 + R-5.7): the inline rich-text composer
  // forwards a per-fragment component list on reply submit. The
  // controller must build the J2clComposerDocument from those
  // components (preserving per-fragment formatting) instead of
  // collapsing the whole draft to a single annotation.
  @Test
  public void onReplySubmittedWithComponentsBuildsAnnotatedDeltaFromComponents() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("plain "));
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated(
            "strong-bit", "fontWeight", "bold"));
    components.add(J2clComposeSurfaceController.SubmittedComponent.text(" tail"));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(delta, "\"2\":\"plain \"");
    assertContains(delta, "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}");
    assertContains(delta, "\"2\":\"strong-bit\"");
    assertContains(delta, "{\"1\":{\"2\":[\"fontWeight\"]}}");
    assertContains(delta, "\"2\":\" tail\"");
  }

  // J-UI-5 (#1083): a successful component-driven submit clears the
  // pending list so a subsequent plain-text submit (e.g. via the
  // legacy textarea) does not re-emit the bold annotation.
  @Test
  public void componentSubmitSuccessClearsPendingComponentsBeforeNextSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> firstSubmit = new ArrayList<>();
    firstSubmit.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated("hi", "fontWeight", "bold"));
    controller.onReplySubmittedWithComponents(firstSubmit);
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "fontWeight");

    // Mimic a wave reselect to clear the reply draft, then submit
    // again as plain text — should NOT carry fontWeight.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 46L, "WXYZ", "b+root"));
    controller.onReplySubmitted("plain again");

    Assert.assertFalse(
        "Plain-text submit must not re-emit prior fontWeight annotation",
        gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
  }

  // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84a):
  // a SubmittedComponent carrying two annotations (e.g. fontStyle +
  // fontWeight) must serialise as a single chars op bracketed by both
  // annotation start/end pairs in well-nested order, so combined
  // bold+italic round-trips.
  @Test
  public void onReplySubmittedWithComponentsEmitsNestedAnnotationsForCombinedRun() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent.Annotation> ann = new ArrayList<>();
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontStyle", "italic"));
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontWeight", "bold"));
    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotatedMulti("combined", ann));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // Both annotation starts AND both annotation ends, with chars in
    // between. The exact order of starts is fontStyle then fontWeight
    // (declaration order); ends are reversed.
    int italicStart = delta.indexOf("\"1\":\"fontStyle\",\"3\":\"italic\"");
    int boldStart = delta.indexOf("\"1\":\"fontWeight\",\"3\":\"bold\"");
    int chars = delta.indexOf("\"2\":\"combined\"");
    int boldEnd = delta.indexOf("[\"fontWeight\"]");
    int italicEnd = delta.indexOf("[\"fontStyle\"]");
    Assert.assertTrue("italic start present", italicStart >= 0);
    Assert.assertTrue("bold start present", boldStart >= 0);
    Assert.assertTrue("chars present", chars >= 0);
    Assert.assertTrue("bold end present", boldEnd >= 0);
    Assert.assertTrue("italic end present", italicEnd >= 0);
    Assert.assertTrue("italic opens before bold (declaration order)", italicStart < boldStart);
    Assert.assertTrue("bold opens before chars", boldStart < chars);
    Assert.assertTrue("chars before bold close", chars < boldEnd);
    Assert.assertTrue("bold closes before italic (well-nested)", boldEnd < italicEnd);
  }

  // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-F-8o):
  // multi-annotation runs that re-use the same key (e.g. nested
  // `<u><s>x</s></u>` produces two `textDecoration` entries with
  // different values: `underline` then `line-through`) must collapse
  // to one start/end pair so the wave-doc reader's "later wins" rule
  // does not silently drop one of the values.
  @Test
  public void onReplySubmittedWithComponentsCollapsesDuplicateAnnotationKeys() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent.Annotation> ann = new ArrayList<>();
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "textDecoration", "underline"));
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "textDecoration", "line-through"));
    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotatedMulti("decorated", ann));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    int firstStart = delta.indexOf("\"1\":\"textDecoration\",\"3\":\"underline\"");
    int secondStart = delta.indexOf("\"1\":\"textDecoration\",\"3\":\"line-through\"");
    int decorationCloses = delta.split("\\[\"textDecoration\"\\]", -1).length - 1;
    // Last-wins: the `line-through` value survives; the `underline`
    // duplicate is dropped before boundary emission.
    Assert.assertEquals(
        "no duplicate underline start emitted",
        -1,
        firstStart);
    Assert.assertTrue(
        "the surviving textDecoration value reaches the delta",
        secondStart >= 0);
    Assert.assertEquals(
        "exactly one textDecoration close emitted",
        1,
        decorationCloses);
  }

  // J-UI-5 (#1083, coderabbit thread PRRT_kwDOBwxLXs5-Gun6): when
  // `onWriteSessionChanged` preserves a stale reply draft so the user
  // can review/retry, the inline-composer component snapshot MUST
  // also survive — otherwise the retry silently downgrades rich text
  // to plain.
  @Test
  public void componentSnapshotSurvivesStaleDraftPreservation() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    List<J2clComposeSurfaceController.SubmittedComponent> richComponents = new ArrayList<>();
    richComponents.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated(
            "bold-bit", "fontWeight", "bold"));
    controller.onReplySubmittedWithComponents(richComponents);

    // Same wave, different basis ⇒ stale-basis path; replyDraft stays.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft preserved", "bold-bit", view.model.getReplyDraft());
    Assert.assertTrue("Draft is stale", view.model.isReplyStaleBasis());

    // Resolve the in-flight bootstrap — the originally pending submit
    // surfaces the stale-basis error rather than completing. Now the
    // user retries, and the rich annotation must still go through.
    gateway.resolveBootstrap();
    gateway.lastSubmitRequest = null;
    controller.onReplySubmitted("bold-bit");

    Assert.assertNotNull("retry submitted a delta", gateway.lastSubmitRequest);
    Assert.assertTrue(
        "retry preserves the bold annotation across the stale-draft transition",
        gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
  }

  // J-UI-5 (#1083): an annotated component whose text is whitespace-only
  // (a common user flow: bolding a word together with its trailing
  // space) must not throw; the controller downgrades it to a plain
  // text run rather than letting J2clComposerDocument.Builder reject
  // the empty-trim text and tear down the reply path.
  @Test
  public void onReplySubmittedWithComponentsDowngradesWhitespaceAnnotatedToText() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("hi"));
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated(
            " ", "fontWeight", "bold"));
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("there"));

    controller.onReplySubmittedWithComponents(components);

    // The space run is emitted as plain text — no annotation start
    // wrapping a whitespace-only payload (which the builder would
    // reject as "Missing annotated text.").
    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(delta, "\"2\":\"hi\"");
    assertContains(delta, "\"2\":\" \"");
    assertContains(delta, "\"2\":\"there\"");
  }

  private static final class FakeGateway implements J2clComposeSurfaceController.Gateway {
    private int fetchBootstrapCalls;
    private int submitCalls;
    private boolean autoResolveBootstrap = true;
    private String bootstrapError;
    private SidecarSubmitResponse submitResponse = new SidecarSubmitResponse(1, "", 45L);
    private SidecarSubmitRequest lastSubmitRequest;
    private J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> pendingBootstrapSuccess;
    private J2clSearchPanelController.ErrorCallback pendingBootstrapError;

    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      fetchBootstrapCalls++;
      if (bootstrapError != null) {
        onError.accept(bootstrapError);
        return;
      }
      if (autoResolveBootstrap) {
        onSuccess.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
        return;
      }
      pendingBootstrapSuccess = onSuccess;
      pendingBootstrapError = onError;
    }

    @Override
    public void submit(
        SidecarSessionBootstrap bootstrap,
        SidecarSubmitRequest request,
        J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      submitCalls++;
      lastSubmitRequest = request;
      onSuccess.accept(submitResponse);
    }

    private void resolveBootstrap() {
      if (pendingBootstrapSuccess == null) {
        throw new IllegalStateException("No pending bootstrap to resolve");
      }
      J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success =
          pendingBootstrapSuccess;
      pendingBootstrapSuccess = null;
      pendingBootstrapError = null;
      success.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
    }
  }

  private static final class FakeView implements J2clComposeSurfaceController.View {
    private J2clComposeSurfaceModel model;
    private int openAttachmentPickerCalls;
    private int focusReplyComposerCalls;

    @Override
    public void bind(J2clComposeSurfaceController.Listener listener) {
    }

    @Override
    public void render(J2clComposeSurfaceModel model) {
      this.model = model;
    }

    @Override
    public void openAttachmentPicker() {
      openAttachmentPickerCalls++;
    }

    @Override
    public void focusReplyComposer() {
      focusReplyComposerCalls++;
    }
  }

  private static final class FakeAttachmentTransport
      implements J2clAttachmentUploadClient.UploadTransport {
    private final List<J2clAttachmentUploadClient.MultipartUploadRequest> requests =
        new ArrayList<J2clAttachmentUploadClient.MultipartUploadRequest>();
    private final List<J2clAttachmentUploadClient.ResponseHandler> handlers =
        new ArrayList<J2clAttachmentUploadClient.ResponseHandler>();

    @Override
    public void post(
        J2clAttachmentUploadClient.MultipartUploadRequest request,
        J2clAttachmentUploadClient.ResponseHandler handler) {
      requests.add(request);
      handlers.add(handler);
    }

    void complete(int index, J2clAttachmentUploadClient.HttpResponse response) {
      handlers.get(index).onResponse(response);
    }
  }

  private static final class FakeFactory extends J2clPlainTextDeltaFactory {
    private String lastReplyText;

    private FakeFactory() {
      super("seed");
    }

    @Override
    public CreateWaveRequest createWaveRequest(String address, String text) {
      return new CreateWaveRequest(
          "example.com/w+new",
          new SidecarSubmitRequest("example.com/w+new/~/conv+root", "{\"create\":true}", null));
    }

    @Override
    public SidecarSubmitRequest createReplyRequest(
        String address, J2clSidecarWriteSession session, String text) {
      lastReplyText = text;
      return new SidecarSubmitRequest(
          session.getSelectedWaveId() + "/~/conv+root", "{\"reply\":true}", session.getChannelId());
    }
  }
}

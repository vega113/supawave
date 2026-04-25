package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentIdGenerator;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
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

  // Reflection is intentionally used here to avoid adding a test-only getter to the production API.
  // This helper depends on the exact private field name "uploadClient" in
  // J2clAttachmentComposerController — update it if the field is ever renamed.
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

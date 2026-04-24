package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
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
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
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
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
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

  private static final class FakeGateway implements J2clComposeSurfaceController.Gateway {
    private int fetchBootstrapCalls;
    private int submitCalls;
    private boolean autoResolveBootstrap = true;
    private String bootstrapError;
    private SidecarSubmitResponse submitResponse = new SidecarSubmitResponse(1, "", 45L);
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

    @Override
    public void bind(J2clComposeSurfaceController.Listener listener) {
    }

    @Override
    public void render(J2clComposeSurfaceModel model) {
      this.model = model;
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

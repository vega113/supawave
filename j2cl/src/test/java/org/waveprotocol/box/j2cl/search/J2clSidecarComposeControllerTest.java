package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

@J2clTestInput(J2clSidecarComposeControllerTest.class)
public class J2clSidecarComposeControllerTest {
  @Test
  public void blankCreateDraftShowsValidationErrorWithoutTransportCall() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> createdWaveIds = new ArrayList<String>();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            createdWaveIds::add);

    controller.start();
    controller.onCreateSubmitted("   \n  ");

    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
    Assert.assertEquals("Enter some text before creating a wave.", view.model.getCreateErrorText());
    Assert.assertTrue(createdWaveIds.isEmpty());
  }

  @Test
  public void rootShellPresentationUsesShellAwareCreateCopy() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { },
            null,
            J2clSearchPanelView.ShellPresentation.ROOT_SHELL);

    controller.start();

    Assert.assertEquals(
        "Create a self-owned wave inside the root shell.",
        view.model.getCreateStatusText());
  }

  @Test
  public void successfulCreateClearsDraftAndNavigatesToCreatedWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> createdWaveIds = new ArrayList<String>();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            createdWaveIds::add);

    controller.start();
    controller.onCreateDraftChanged("Hello");
    controller.onCreateSubmitted("Hello");

    Assert.assertEquals(1, gateway.fetchBootstrapCalls);
    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals("example.com/w+new", createdWaveIds.get(0));
    Assert.assertEquals("", view.model.getCreateDraft());
    Assert.assertEquals("", view.model.getCreateErrorText());
  }

  @Test
  public void submitFailurePreservesCreateDraftAndShowsServerError() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(0, "server boom", -1L);
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { });

    controller.start();
    controller.onCreateDraftChanged("Hello");
    controller.onCreateSubmitted("Hello");

    Assert.assertEquals("Hello", view.model.getCreateDraft());
    Assert.assertEquals("server boom", view.model.getCreateErrorText());
  }

  @Test
  public void replyRequiresWriteSessionAndPreservesDraftOnTransportFailure() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { });

    controller.start();
    controller.onReplyDraftChanged("Reply");
    controller.onReplySubmitted("Reply");
    Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);

    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    gateway.submitError = "socket boom";
    controller.onReplySubmitted("Reply");

    Assert.assertEquals("Reply", view.model.getReplyDraft());
    Assert.assertEquals("socket boom", view.model.getReplyErrorText());
  }

  @Test
  public void successfulReplyClearsDraftAndRefreshesOpenedWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> refreshedWaveIds = new ArrayList<String>();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { },
            refreshedWaveIds::add);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Reply");
    controller.onReplySubmitted("Reply");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshedWaveIds);
    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void failedReplyDoesNotRefreshOpenedWave() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitError = "socket boom";
    FakeView view = new FakeView();
    List<String> refreshedWaveIds = new ArrayList<String>();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { },
            refreshedWaveIds::add);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");

    Assert.assertTrue(refreshedWaveIds.isEmpty());
    Assert.assertEquals("socket boom", view.model.getReplyErrorText());
  }

  @Test
  public void createSubmissionUsesDraftSnapshotFromSubmitTime() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            factory,
            waveId -> { });

    controller.start();
    controller.onCreateSubmitted("Original");
    controller.onCreateDraftChanged("Edited");
    gateway.resolveBootstrap();

    Assert.assertEquals("Original", factory.lastCreateText);
  }

  @Test
  public void replySubmissionUsesDraftSnapshotFromSubmitTime() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            factory,
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Original");
    controller.onReplyDraftChanged("Edited");
    gateway.resolveBootstrap();

    Assert.assertEquals("Original", factory.lastReplyText);
  }

  @Test
  public void sameWaveBasisChangeInvalidatesPendingReplySubmission() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals("", view.model.getReplyStatusText());
  }

  @Test
  public void writeSessionChangeClearsReplyDraftForDifferentWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Draft for wave one");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
  }

  @Test
  public void writeSessionBasisChangeKeepsReplyDraftForSameWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clSidecarComposeController controller =
        new J2clSidecarComposeController(
            gateway,
            view,
            new FakeFactory(),
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Draft for wave one");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft for wave one", view.model.getReplyDraft());
  }

  private static final class FakeGateway implements J2clSidecarComposeController.Gateway {
    private int fetchBootstrapCalls;
    private int submitCalls;
    private boolean autoResolveBootstrap = true;
    private String submitError;
    private SidecarSubmitResponse submitResponse = new SidecarSubmitResponse(1, "", 45L);
    private J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> pendingBootstrapSuccess;
    private J2clSearchPanelController.ErrorCallback pendingBootstrapError;

    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      fetchBootstrapCalls++;
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
      if (submitError != null) {
        onError.accept(submitError);
        return;
      }
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

  private static final class FakeView implements J2clSidecarComposeController.View {
    private J2clSidecarComposeModel model;

    @Override
    public void bind(J2clSidecarComposeController.Listener listener) {
    }

    @Override
    public void render(J2clSidecarComposeModel model) {
      this.model = model;
    }
  }

  private static final class FakeFactory extends J2clPlainTextDeltaFactory {
    private String lastCreateText;
    private String lastReplyText;
    private J2clSidecarWriteSession lastReplySession;

    private FakeFactory() {
      super("seed");
    }

    @Override
    public CreateWaveRequest createWaveRequest(String address, String text) {
      lastCreateText = text;
      return new CreateWaveRequest(
          "example.com/w+new",
          new SidecarSubmitRequest("example.com/w+new/~/conv+root", "{\"create\":true}", null));
    }

    @Override
    public SidecarSubmitRequest createReplyRequest(
        String address, J2clSidecarWriteSession session, String text) {
      lastReplyText = text;
      lastReplySession = session;
      return new SidecarSubmitRequest(
          session.getSelectedWaveId() + "/~/conv+root", "{\"reply\":true}", session.getChannelId());
    }
  }
}

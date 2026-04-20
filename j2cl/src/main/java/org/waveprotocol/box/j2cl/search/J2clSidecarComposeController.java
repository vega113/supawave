package org.waveprotocol.box.j2cl.search;

import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

public final class J2clSidecarComposeController {
  public interface Gateway {
    void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    void submit(
        SidecarSessionBootstrap bootstrap,
        SidecarSubmitRequest request,
        J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);
  }

  public interface View {
    void bind(Listener listener);

    void render(J2clSidecarComposeModel model);
  }

  public interface Listener {
    void onCreateDraftChanged(String draft);

    void onCreateSubmitted(String draft);

    void onReplyDraftChanged(String draft);

    void onReplySubmitted(String draft);
  }

  @FunctionalInterface
  public interface CreateSuccessHandler {
    void onWaveCreated(String waveId);
  }

  @FunctionalInterface
  public interface ReplySuccessHandler {
    void onReplySubmitted(String waveId);
  }

  private final Gateway gateway;
  private final View view;
  private final J2clPlainTextDeltaFactory deltaFactory;
  private final CreateSuccessHandler createSuccessHandler;
  private final ReplySuccessHandler replySuccessHandler;
  private final J2clSearchPanelView.ShellPresentation shellPresentation;
  private String createDraft = "";
  private boolean createSubmitting;
  private String createStatusText;
  private String createErrorText = "";
  private J2clSidecarWriteSession writeSession;
  private String replyDraft = "";
  private boolean replySubmitting;
  private String replyStatusText = "";
  private String replyErrorText = "";
  private int createGeneration;
  private int replyGeneration;
  private boolean started;

  public J2clSidecarComposeController(
      Gateway gateway,
      View view,
      J2clPlainTextDeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler) {
    this(
        gateway,
        view,
        deltaFactory,
        createSuccessHandler,
        null,
        J2clSearchPanelView.ShellPresentation.SIDE_CAR);
  }

  public J2clSidecarComposeController(
      Gateway gateway,
      View view,
      J2clPlainTextDeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this(gateway, view, deltaFactory, createSuccessHandler, replySuccessHandler,
        J2clSearchPanelView.ShellPresentation.SIDE_CAR);
  }

  public J2clSidecarComposeController(
      Gateway gateway,
      View view,
      J2clPlainTextDeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler,
      J2clSearchPanelView.ShellPresentation shellPresentation) {
    this.gateway = gateway;
    this.view = view;
    this.deltaFactory = deltaFactory;
    this.createSuccessHandler = createSuccessHandler;
    this.replySuccessHandler = replySuccessHandler;
    this.shellPresentation = shellPresentation;
    this.createStatusText =
        shellPresentation == J2clSearchPanelView.ShellPresentation.ROOT_SHELL
            ? "Create a self-owned wave inside the root shell."
            : "Create a self-owned wave from the sidecar.";
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    view.bind(
        new Listener() {
          @Override
          public void onCreateDraftChanged(String draft) {
            createDraft = normalizeDraft(draft);
            createErrorText = "";
            render();
          }

          @Override
          public void onCreateSubmitted(String draft) {
            createDraft = normalizeDraft(draft);
            submitCreate();
          }

          @Override
          public void onReplyDraftChanged(String draft) {
            replyDraft = normalizeDraft(draft);
            replyErrorText = "";
            render();
          }

          @Override
          public void onReplySubmitted(String draft) {
            replyDraft = normalizeDraft(draft);
            submitReply();
          }
        });
    render();
  }

  public void onCreateDraftChanged(String draft) {
    createDraft = normalizeDraft(draft);
    createErrorText = "";
    render();
  }

  public void onCreateSubmitted(String draft) {
    createDraft = normalizeDraft(draft);
    submitCreate();
  }

  public void onReplyDraftChanged(String draft) {
    replyDraft = normalizeDraft(draft);
    replyErrorText = "";
    render();
  }

  public void onReplySubmitted(String draft) {
    replyDraft = normalizeDraft(draft);
    submitReply();
  }

  public void onWriteSessionChanged(J2clSidecarWriteSession nextWriteSession) {
    if (sessionChanged(nextWriteSession)) {
      replyGeneration++;
      replySubmitting = false;
      replyStatusText = "";
      replyErrorText = "";
      if (selectedWaveChanged(nextWriteSession)) {
        replyDraft = "";
      }
    }
    writeSession = nextWriteSession;
    render();
  }

  private void submitCreate() {
    if (createSubmitting) {
      return;
    }
    if (createDraft.trim().isEmpty()) {
      createStatusText = "";
      createErrorText = "Enter some text before creating a wave.";
      render();
      return;
    }
    createSubmitting = true;
    createStatusText =
        shellPresentation == J2clSearchPanelView.ShellPresentation.ROOT_SHELL
            ? "Bootstrapping the root-shell submit session."
            : "Bootstrapping the sidecar submit session.";
    createErrorText = "";
    final String submittedDraft = createDraft;
    final int generation = ++createGeneration;
    render();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (generation != createGeneration) {
            return;
          }
          J2clPlainTextDeltaFactory.CreateWaveRequest request;
          try {
            request = deltaFactory.createWaveRequest(bootstrap.getAddress(), submittedDraft);
          } catch (RuntimeException e) {
            handleCreateFailure(generation, messageOrDefault(e, "Unable to build the create request."));
            return;
          }
          createStatusText = "Submitting the new wave.";
          render();
          gateway.submit(
              bootstrap,
              request.getSubmitRequest(),
              response -> handleCreateResponse(generation, request, response),
              error -> handleCreateFailure(generation, error));
        },
        error -> handleCreateFailure(generation, error));
  }

  private void handleCreateResponse(
      int generation,
      J2clPlainTextDeltaFactory.CreateWaveRequest request,
      SidecarSubmitResponse response) {
    if (generation != createGeneration) {
      return;
    }
    if (!response.getErrorMessage().isEmpty()) {
      handleCreateFailure(generation, response.getErrorMessage());
      return;
    }
    createSubmitting = false;
    createDraft = "";
    createStatusText = "";
    createErrorText = "";
    render();
    if (createSuccessHandler != null) {
      createSuccessHandler.onWaveCreated(request.getCreatedWaveId());
    }
  }

  private void handleCreateFailure(int generation, String error) {
    if (generation != createGeneration) {
      return;
    }
    createSubmitting = false;
    createStatusText = "";
    createErrorText = error == null || error.isEmpty() ? "Create wave failed." : error;
    render();
  }

  private void submitReply() {
    if (replySubmitting) {
      return;
    }
    if (writeSession == null) {
      replyStatusText = "";
      replyErrorText = "Open a wave before sending a reply.";
      render();
      return;
    }
    if (replyDraft.trim().isEmpty()) {
      replyStatusText = "";
      replyErrorText = "Enter some text before replying.";
      render();
      return;
    }
    replySubmitting = true;
    replyStatusText =
        shellPresentation == J2clSearchPanelView.ShellPresentation.ROOT_SHELL
            ? "Bootstrapping the root-shell submit session."
            : "Bootstrapping the sidecar submit session.";
    replyErrorText = "";
    final String submittedDraft = replyDraft;
    final int generation = ++replyGeneration;
    final J2clSidecarWriteSession submitSession = writeSession;
    render();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (generation != replyGeneration) {
            return;
          }
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createReplyRequest(bootstrap.getAddress(), submitSession, submittedDraft);
          } catch (RuntimeException e) {
            handleReplyFailure(generation, messageOrDefault(e, "Unable to build the reply request."));
            return;
          }
          replyStatusText = "Submitting the reply.";
          render();
          gateway.submit(
              bootstrap,
              request,
              response -> handleReplyResponse(generation, submitSession, response),
              error -> handleReplyFailure(generation, error));
        },
        error -> handleReplyFailure(generation, error));
  }

  private void handleReplyResponse(
      int generation, J2clSidecarWriteSession submitSession, SidecarSubmitResponse response) {
    if (generation != replyGeneration) {
      return;
    }
    if (!response.getErrorMessage().isEmpty()) {
      handleReplyFailure(generation, response.getErrorMessage());
      return;
    }
    replySubmitting = false;
    replyDraft = "";
    replyStatusText = "Reply submitted. Waiting for the opened wave to update.";
    replyErrorText = "";
    render();
    if (replySuccessHandler != null
        && submitSession != null
        && submitSession.getSelectedWaveId() != null
        && !submitSession.getSelectedWaveId().isEmpty()) {
      replySuccessHandler.onReplySubmitted(submitSession.getSelectedWaveId());
    }
  }

  private void handleReplyFailure(int generation, String error) {
    if (generation != replyGeneration) {
      return;
    }
    replySubmitting = false;
    replyStatusText = "";
    replyErrorText = error == null || error.isEmpty() ? "Reply failed." : error;
    render();
  }

  private void render() {
    if (!started) {
      return;
    }
    view.render(
        new J2clSidecarComposeModel(
            createDraft,
            createSubmitting,
            createStatusText,
            createErrorText,
            writeSession != null,
            replyDraft,
            replySubmitting,
            replyStatusText,
            replyErrorText,
            writeSession == null ? "" : "Posting a plain-text follow-up from the opened wave context."));
  }

  private boolean sessionChanged(J2clSidecarWriteSession nextWriteSession) {
    if (writeSession == null || nextWriteSession == null) {
      return writeSession != nextWriteSession;
    }
    return !safeEquals(writeSession.getSelectedWaveId(), nextWriteSession.getSelectedWaveId())
        || !safeEquals(writeSession.getChannelId(), nextWriteSession.getChannelId())
        || writeSession.getBaseVersion() != nextWriteSession.getBaseVersion()
        || !safeEquals(writeSession.getHistoryHash(), nextWriteSession.getHistoryHash())
        || !safeEquals(writeSession.getReplyTargetBlipId(), nextWriteSession.getReplyTargetBlipId());
  }

  private boolean selectedWaveChanged(J2clSidecarWriteSession nextWriteSession) {
    String currentWaveId = writeSession == null ? null : writeSession.getSelectedWaveId();
    String nextWaveId = nextWriteSession == null ? null : nextWriteSession.getSelectedWaveId();
    return !safeEquals(currentWaveId, nextWaveId);
  }

  private static String normalizeDraft(String draft) {
    return draft == null ? "" : draft;
  }

  private static String messageOrDefault(RuntimeException error, String fallback) {
    String message = error.getMessage();
    return message == null || message.isEmpty() ? fallback : message;
  }

  private static boolean safeEquals(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }
}

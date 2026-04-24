package org.waveprotocol.box.j2cl.compose;

import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

public final class J2clComposeSurfaceController {
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

    void render(J2clComposeSurfaceModel model);
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

  static final String STALE_REPLY_MESSAGE =
      "The wave changed before your reply was sent. Review it, then retry if it still belongs"
          + " here.";

  private final Gateway gateway;
  private final View view;
  private final J2clPlainTextDeltaFactory deltaFactory;
  private final CreateSuccessHandler createSuccessHandler;
  private final ReplySuccessHandler replySuccessHandler;
  private String createDraft = "";
  private boolean createSubmitting;
  private String createStatusText = "Create a self-owned wave inside the root shell.";
  private String createErrorText = "";
  private J2clSidecarWriteSession writeSession;
  private String replyDraft = "";
  private boolean replySubmitting;
  private boolean replyStaleBasis;
  private String replyStaleWaveId;
  private String replyStatusText = "Open a wave before replying.";
  private String replyErrorText = "";
  private int createGeneration;
  private int replyGeneration;
  private boolean signedOut;
  private boolean started;

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      J2clPlainTextDeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this.gateway = gateway;
    this.view = view;
    this.deltaFactory = deltaFactory;
    this.createSuccessHandler = createSuccessHandler;
    this.replySuccessHandler = replySuccessHandler;
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
            J2clComposeSurfaceController.this.onCreateDraftChanged(draft);
          }

          @Override
          public void onCreateSubmitted(String draft) {
            J2clComposeSurfaceController.this.onCreateSubmitted(draft);
          }

          @Override
          public void onReplyDraftChanged(String draft) {
            J2clComposeSurfaceController.this.onReplyDraftChanged(draft);
          }

          @Override
          public void onReplySubmitted(String draft) {
            J2clComposeSurfaceController.this.onReplySubmitted(draft);
          }
        });
    render();
  }

  public void onSignedOut() {
    signedOut = true;
    createGeneration++;
    replyGeneration++;
    createSubmitting = false;
    replySubmitting = false;
    writeSession = null;
    replyStaleBasis = false;
    replyStaleWaveId = null;
    replyErrorText = "";
    createErrorText = "";
    createStatusText = "Sign in to create or reply in the J2CL root shell.";
    replyStatusText = createStatusText;
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
    replyStaleBasis = false;
    replyStaleWaveId = null;
    render();
  }

  public void onReplySubmitted(String draft) {
    replyDraft = normalizeDraft(draft);
    submitReply();
  }

  public void onWriteSessionChanged(J2clSidecarWriteSession nextWriteSession) {
    if (signedOut) {
      return;
    }
    if (sessionChanged(nextWriteSession)) {
      boolean wasReplySubmitting = replySubmitting;
      replyGeneration++;
      if (wasReplySubmitting) {
        replyErrorText = STALE_REPLY_MESSAGE;
        replyStaleBasis = true;
        // submitReply() guarantees a non-empty selected wave id here, but stay defensive.
        replyStaleWaveId = writeSession == null ? null : writeSession.getSelectedWaveId();
      } else if (!replyStaleBasis) {
        // Stale explanations stay visible across benign reconnect/version refreshes until
        // the user edits/retries or navigates to a different wave.
        replyErrorText = "";
      }
      replySubmitting = false;
      replyStatusText = "";
      if (selectedWaveChanged(nextWriteSession)) {
        String nextWaveId = nextWriteSession == null ? null : nextWriteSession.getSelectedWaveId();
        // Preserve a stale draft through null (disconnected) and same-wave reconnect transitions.
        // Also preserve the first transition that invalidates an in-flight submit so the user
        // can review the unsent text instead of losing it.
        boolean clearDraft = !replyStaleBasis
            || (!wasReplySubmitting
                && nextWaveId != null
                && !safeEquals(replyStaleWaveId, nextWaveId));
        if (clearDraft) {
          replyDraft = "";
          replyErrorText = "";
          replyStaleBasis = false;
          replyStaleWaveId = null;
        }
      }
    }
    writeSession = nextWriteSession;
    if (!hasSelectedWave(writeSession) && replyStatusText.isEmpty()) {
      replyStatusText = "Open a wave before replying.";
    }
    render();
  }

  private void submitCreate() {
    if (signedOut || createSubmitting) {
      render();
      return;
    }
    if (createDraft.trim().isEmpty()) {
      createStatusText = "";
      createErrorText = "Enter some text before creating a wave.";
      render();
      return;
    }
    createSubmitting = true;
    createStatusText = "Bootstrapping the root-shell submit session.";
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
    createStatusText = "Wave created.";
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
    if (signedOut || replySubmitting) {
      render();
      return;
    }
    if (writeSession == null || isEmpty(writeSession.getSelectedWaveId())) {
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
    replyStaleBasis = false;
    replyStaleWaveId = null;
    replyStatusText = "Bootstrapping the root-shell submit session.";
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
    replyStaleBasis = false;
    replyStaleWaveId = null;
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
    replyStaleBasis = false;
    replyStaleWaveId = null;
    render();
  }

  private void render() {
    if (!started) {
      return;
    }
    boolean replyAvailable = !signedOut && hasSelectedWave(writeSession);
    view.render(
        new J2clComposeSurfaceModel(
            !signedOut,
            createDraft,
            createSubmitting,
            createStatusText,
            createErrorText,
            replyAvailable,
            replyAvailable ? writeSession.getReplyTargetBlipId() : "",
            replyDraft,
            replySubmitting,
            replyStaleBasis,
            replyStatusText,
            replyErrorText));
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

  private static boolean hasSelectedWave(J2clSidecarWriteSession session) {
    return session != null && !isEmpty(session.getSelectedWaveId());
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

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }
}

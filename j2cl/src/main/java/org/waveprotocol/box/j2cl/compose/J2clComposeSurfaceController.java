package org.waveprotocol.box.j2cl.compose;

import java.util.ArrayList;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentIdGenerator;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.richtext.J2clComposerDocument;
import org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
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

    void openAttachmentPicker();

    void focusReplyComposer();
  }

  public interface Listener {
    void onCreateDraftChanged(String draft);

    void onCreateSubmitted(String draft);

    void onReplyDraftChanged(String draft);

    void onReplySubmitted(String draft);

    void onAttachmentFilesSelected(List<AttachmentFileSelection> selections);

    void onPastedImage(Object imagePayload);
  }

  @FunctionalInterface
  public interface CreateSuccessHandler {
    void onWaveCreated(String waveId);
  }

  @FunctionalInterface
  public interface ReplySuccessHandler {
    void onReplySubmitted(String waveId);
  }

  public interface DeltaFactory {
    CreateWaveRequest createWaveRequest(
        String address, String draftText, J2clComposerDocument document);

    SidecarSubmitRequest createReplyRequest(
        String address,
        J2clSidecarWriteSession session,
        String draftText,
        J2clComposerDocument document);
  }

  public interface AttachmentControllerFactory {
    J2clAttachmentComposerController create(
        String waveRef,
        String domain,
        J2clAttachmentComposerController.DocumentInsertionCallback insertionCallback,
        J2clAttachmentComposerController.StateChangeCallback stateChangeCallback);
  }

  public static final class AttachmentFileSelection {
    private final Object payload;
    private final String fileName;

    public AttachmentFileSelection(Object payload, String fileName) {
      this.payload = requirePresent(payload, "Attachment payload is required.");
      String normalizedFileName = fileName == null ? "" : fileName.trim();
      this.fileName = normalizedFileName.isEmpty() ? "attachment" : normalizedFileName;
    }

    public Object getPayload() {
      return payload;
    }

    public String getFileName() {
      return fileName;
    }
  }

  public static final class CreateWaveRequest {
    private final String createdWaveId;
    private final SidecarSubmitRequest submitRequest;

    public CreateWaveRequest(String createdWaveId, SidecarSubmitRequest submitRequest) {
      this.createdWaveId = createdWaveId;
      this.submitRequest = submitRequest;
    }

    public String getCreatedWaveId() {
      return createdWaveId;
    }

    public SidecarSubmitRequest getSubmitRequest() {
      return submitRequest;
    }
  }

  static final String STALE_REPLY_MESSAGE =
      "The wave changed before your reply was sent. Review it, then retry if it still belongs"
          + " here.";
  // Synthetic command id used only for command live-region state, not a toolbar action.
  static final String ATTACHMENT_ERROR_STATE_ID = "attachment-error-state";
  private static final String REPLY_SUBMITTING_ATTACHMENT_MESSAGE =
      "Wait for the current reply to finish before attaching files.";
  // Package-visible for assertion reuse in controller tests.
  static final String PENDING_ATTACHMENT_REPLY_MESSAGE =
      "Wait for attachment uploads to finish before replying.";
  static final String EMPTY_REPLY_VALIDATION_MESSAGE =
      "Enter text or attach a file before replying.";
  // Legacy constructors are not used by the root shell; production passes the root session seed.
  private static final String LEGACY_ATTACHMENT_SESSION_SEED = "j2cl";

  private final Gateway gateway;
  private final View view;
  private final DeltaFactory deltaFactory;
  private final AttachmentControllerFactory attachmentControllerFactory;
  private final CreateSuccessHandler createSuccessHandler;
  private final ReplySuccessHandler replySuccessHandler;
  private String createDraft = "";
  private boolean createSubmitting;
  private String createStatusText = "Create a self-owned wave inside the root shell.";
  private String createErrorText = "";
  private J2clSidecarWriteSession writeSession;
  private String lastSelectedWaveId;
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
  private String activeCommandId = "";
  // Tracks rich-text formatting only. Attachment actions reuse activeCommandId for status live
  // regions, so formatting stays separate from upload progress and error state.
  private String annotationCommandId = "";
  private String commandStatusText = "";
  private String commandErrorText = "";
  private J2clAttachmentComposerController attachmentController;
  private final List<J2clAttachmentComposerController.AttachmentInsertion> insertedAttachments =
      new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
  private J2clAttachmentComposerController.DisplaySize attachmentDisplaySize =
      J2clAttachmentComposerController.DisplaySize.MEDIUM;

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      J2clPlainTextDeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this(
        gateway,
        view,
        plainTextDeltaFactory(deltaFactory),
        attachmentControllerFactory(LEGACY_ATTACHMENT_SESSION_SEED),
        createSuccessHandler,
        replySuccessHandler);
  }

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      DeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this(
        gateway,
        view,
        deltaFactory,
        attachmentControllerFactory(LEGACY_ATTACHMENT_SESSION_SEED),
        createSuccessHandler,
        replySuccessHandler);
  }

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      DeltaFactory deltaFactory,
      AttachmentControllerFactory attachmentControllerFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this.gateway = gateway;
    this.view = view;
    this.deltaFactory = deltaFactory;
    this.attachmentControllerFactory =
        requirePresent(attachmentControllerFactory, "Attachment controller factory is required.");
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

          @Override
          public void onAttachmentFilesSelected(List<AttachmentFileSelection> selections) {
            J2clComposeSurfaceController.this.onAttachmentFilesSelected(selections);
          }

          @Override
          public void onPastedImage(Object imagePayload) {
            J2clComposeSurfaceController.this.onPastedImage(imagePayload);
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
    lastSelectedWaveId = null;
    replyStaleBasis = false;
    replyStaleWaveId = null;
    replyErrorText = "";
    createErrorText = "";
    activeCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    resetAttachmentState();
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

  public boolean onToolbarAction(J2clDailyToolbarAction action) {
    if (action == null || !action.isEditAction()) {
      return false;
    }
    if (!isAttachmentAction(action)
        && action != J2clDailyToolbarAction.CLEAR_FORMATTING
        && annotationKey(action) == null) {
      return false;
    }
    if (signedOut) {
      commandStatusText = "";
      commandErrorText = "Sign in before using toolbar actions.";
      render();
      return true;
    }
    if (isAttachmentAction(action)) {
      handleAttachmentToolbarAction(action);
      return true;
    }
    if (!hasSelectedWave(writeSession)) {
      activeCommandId = "";
      annotationCommandId = "";
      commandStatusText = "";
      commandErrorText = "Open a wave before using rich-edit toolbar actions.";
      render();
      view.focusReplyComposer();
      return true;
    }
    if (action == J2clDailyToolbarAction.CLEAR_FORMATTING) {
      activeCommandId = "";
      annotationCommandId = "";
      commandErrorText = "";
      commandStatusText = "";
      refreshAttachmentCommandState();
      if (commandStatusText.isEmpty() && commandErrorText.isEmpty()) {
        activeCommandId = "";
        commandStatusText = "Formatting cleared.";
      }
      render();
      view.focusReplyComposer();
      return true;
    }
    if (safeEquals(annotationCommandId, action.id())) {
      activeCommandId = "";
      annotationCommandId = "";
      commandErrorText = "";
      commandStatusText = action.label() + " cleared.";
      render();
      view.focusReplyComposer();
      return true;
    }
    activeCommandId = action.id();
    annotationCommandId = action.id();
    commandErrorText = "";
    commandStatusText = action.label() + " applied to the current draft.";
    render();
    view.focusReplyComposer();
    return true;
  }

  public void onAttachmentFilesSelected(List<AttachmentFileSelection> selections) {
    if (signedOut) {
      activeCommandId = "";
      commandStatusText = "";
      commandErrorText = "Sign in before attaching files.";
      render();
      view.focusReplyComposer();
      return;
    }
    commandErrorText = "";
    if (replySubmitting) {
      blockAttachmentWhileReplySubmitting();
      return;
    }
    if (!hasSelectedWave(writeSession)) {
      activeCommandId = "";
      commandStatusText = "";
      commandErrorText = "Open a wave before attaching files.";
      render();
      view.focusReplyComposer();
      return;
    }
    if (selections == null || selections.isEmpty()) {
      activeCommandId = "";
      commandStatusText = "";
      render();
      view.focusReplyComposer();
      return;
    }
    try {
      List<J2clAttachmentComposerController.AttachmentSelection> attachmentSelections =
          new ArrayList<J2clAttachmentComposerController.AttachmentSelection>();
      for (AttachmentFileSelection selection : selections) {
        requirePresent(selection, "Attachment selection is required.");
        attachmentSelections.add(
            J2clAttachmentComposerController.AttachmentSelection.file(
                selection.getPayload(), selection.getFileName(), "", attachmentDisplaySize));
      }
      activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
      ensureAttachmentController().selectFiles(attachmentSelections);
      refreshAttachmentCommandState();
    } catch (RuntimeException e) {
      activeCommandId = ATTACHMENT_ERROR_STATE_ID;
      commandStatusText = "";
      commandErrorText = messageOrDefault(e, "Attachment selection failed.");
    }
    render();
    view.focusReplyComposer();
  }

  public void onPastedImage(Object imagePayload) {
    if (signedOut) {
      activeCommandId = "";
      commandStatusText = "";
      commandErrorText = "Sign in before pasting an image.";
      render();
      view.focusReplyComposer();
      return;
    }
    commandErrorText = "";
    if (replySubmitting) {
      blockAttachmentWhileReplySubmitting();
      return;
    }
    if (!hasSelectedWave(writeSession)) {
      activeCommandId = "";
      commandStatusText = "";
      commandErrorText = "Open a wave before pasting an image.";
      render();
      view.focusReplyComposer();
      return;
    }
    try {
      activeCommandId = J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE.id();
      requirePresent(imagePayload, "Pasted image payload is required.");
      ensureAttachmentController().pasteImage(imagePayload, "", attachmentDisplaySize);
      refreshAttachmentCommandState();
    } catch (RuntimeException e) {
      activeCommandId = ATTACHMENT_ERROR_STATE_ID;
      commandStatusText = "";
      commandErrorText = messageOrDefault(e, "Pasted image upload failed.");
    }
    render();
    view.focusReplyComposer();
  }

  public void onWriteSessionChanged(J2clSidecarWriteSession nextWriteSession) {
    if (signedOut) {
      return;
    }
    boolean waveChanged = selectedWaveChanged(nextWriteSession);
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
      if (waveChanged) {
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
    if (waveChanged) {
      // selectedWaveChanged ignores transient null disconnects; this is a real wave transition.
      resetAttachmentState();
    }
    writeSession = nextWriteSession;
    if (hasSelectedWave(nextWriteSession)) {
      lastSelectedWaveId = nextWriteSession.getSelectedWaveId();
    }
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
          CreateWaveRequest request;
          try {
            request =
                deltaFactory.createWaveRequest(
                    bootstrap.getAddress(), submittedDraft, buildDocument(submittedDraft, false, false));
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
      CreateWaveRequest request,
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
    activeCommandId = "";
    annotationCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
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
    if (hasPendingAttachmentUpload()) {
      replyStatusText = "";
      replyErrorText = PENDING_ATTACHMENT_REPLY_MESSAGE;
      render();
      return;
    }
    if (replyDraft.trim().isEmpty() && insertedAttachments.isEmpty()) {
      replyStatusText = "";
      replyErrorText = EMPTY_REPLY_VALIDATION_MESSAGE;
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
                deltaFactory.createReplyRequest(
                    bootstrap.getAddress(),
                    submitSession,
                    submittedDraft,
                    buildDocument(submittedDraft, true, true));
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
    insertedAttachments.clear();
    // A sent reply closes the attachment batch; failures preserve size and attachments for retry.
    attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.MEDIUM;
    activeCommandId = "";
    annotationCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    if (attachmentController != null) {
      // Keep the controller so its id generator counter continues across reply batches.
      attachmentController.cancelAndReset();
    }
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
    activeCommandId = "";
    // Preserve annotationCommandId so the user can retry with the same formatting intent.
    commandStatusText = "";
    commandErrorText = "";
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
            replyErrorText,
            activeCommandId,
            commandStatusText,
            commandErrorText));
  }

  private J2clComposerDocument buildDocument(
      String draftText, boolean includeAttachments, boolean includeAnnotation) {
    J2clComposerDocument.Builder builder = J2clComposerDocument.builder();
    // Only apply the reply-toolbar annotation on the reply path; create-wave submits must not
    // inherit a Bold/Italic toggle the user set in the reply editor.
    J2clDailyToolbarAction action =
        includeAnnotation ? J2clDailyToolbarAction.fromId(annotationCommandId) : null;
    String annotationKey = annotationKey(action);
    String annotationValue = annotationValue(action);
    if (annotationKey != null
        && annotationValue != null
        && draftText != null
        && !draftText.trim().isEmpty()) {
      builder.annotatedText(annotationKey, annotationValue, draftText);
    } else {
      builder.text(draftText);
    }
    if (includeAttachments) {
      for (J2clAttachmentComposerController.AttachmentInsertion insertion : insertedAttachments) {
        builder.imageAttachment(
            insertion.getAttachmentId(),
            insertion.getCaption(),
            insertion.getDisplaySize().getDocumentValue());
      }
    }
    return builder.build();
  }

  public static DeltaFactory richContentDeltaFactory(String sessionSeed) {
    return richContentDeltaFactory(new J2clRichContentDeltaFactory(sessionSeed));
  }

  public static AttachmentControllerFactory attachmentControllerFactory(String sessionSeed) {
    return (waveRef, domain, insertionCallback, stateChangeCallback) ->
        new J2clAttachmentComposerController(
            waveRef,
            new J2clAttachmentUploadClient(),
            new J2clAttachmentIdGenerator(domain, sessionSeed),
            insertionCallback,
            stateChangeCallback);
  }

  public static DeltaFactory richContentDeltaFactory(J2clRichContentDeltaFactory factory) {
    if (factory == null) {
      throw new IllegalArgumentException("Rich content delta factory is required.");
    }
    return new DeltaFactory() {
      @Override
      public CreateWaveRequest createWaveRequest(
          String address, String draftText, J2clComposerDocument document) {
        J2clRichContentDeltaFactory.CreateWaveRequest request =
            factory.createWaveRequest(address, document);
        return new CreateWaveRequest(request.getCreatedWaveId(), request.getSubmitRequest());
      }

      @Override
      public SidecarSubmitRequest createReplyRequest(
          String address,
          J2clSidecarWriteSession session,
          String draftText,
          J2clComposerDocument document) {
        return factory.createReplyRequest(address, session, document);
      }
    };
  }

  private static DeltaFactory plainTextDeltaFactory(J2clPlainTextDeltaFactory factory) {
    if (factory == null) {
      throw new IllegalArgumentException("Plain text delta factory is required.");
    }
    return new DeltaFactory() {
      @Override
      public CreateWaveRequest createWaveRequest(
          String address, String draftText, J2clComposerDocument document) {
        J2clPlainTextDeltaFactory.CreateWaveRequest request =
            factory.createWaveRequest(address, draftText);
        return new CreateWaveRequest(request.getCreatedWaveId(), request.getSubmitRequest());
      }

      @Override
      public SidecarSubmitRequest createReplyRequest(
          String address,
          J2clSidecarWriteSession session,
          String draftText,
          J2clComposerDocument document) {
        return factory.createReplyRequest(address, session, draftText);
      }
    };
  }

  private void handleAttachmentToolbarAction(J2clDailyToolbarAction action) {
    commandErrorText = "";
    if (replySubmitting && isAttachmentEntryAction(action)) {
      blockAttachmentWhileReplySubmitting();
      return;
    }
    activeCommandId = action.id();
    if (action == J2clDailyToolbarAction.ATTACHMENT_SIZE_SMALL) {
      attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.SMALL;
      commandStatusText = "Small attachment size selected.";
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_SIZE_MEDIUM) {
      attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.MEDIUM;
      commandStatusText = "Medium attachment size selected.";
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE) {
      attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.LARGE;
      commandStatusText = "Large attachment size selected.";
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_CANCEL) {
      if (attachmentController != null) {
        attachmentController.cancelAndReset();
        attachmentController = null;
      }
      commandStatusText =
          insertedAttachments.isEmpty()
              ? "Attachment upload queue cancelled."
              : "Pending uploads cancelled. Attached files kept.";
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_INSERT) {
      if (!hasSelectedWave(writeSession)) {
        activeCommandId = "";
        commandStatusText = "";
        commandErrorText = "Open a wave before attaching files.";
      } else {
        // The picker result owns the live-region status so cancelling the dialog stays quiet.
        activeCommandId = "";
        commandStatusText = "";
        view.openAttachmentPicker();
      }
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE) {
      activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
      commandStatusText = "";
      commandErrorText = "";
      refreshAttachmentCommandState();
      if (commandStatusText.isEmpty() && commandErrorText.isEmpty()) {
        commandStatusText = "No attachment uploads are queued.";
      }
      render();
      view.focusReplyComposer();
      return;
    }
    if (action == J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE) {
      commandStatusText = "Paste an image into the reply box.";
      render();
      view.focusReplyComposer();
      return;
    }
    throw new IllegalStateException("Unhandled attachment action: " + action);
  }

  private void blockAttachmentWhileReplySubmitting() {
    activeCommandId = "";
    commandStatusText = "";
    commandErrorText = REPLY_SUBMITTING_ATTACHMENT_MESSAGE;
    render();
    view.focusReplyComposer();
  }

  private J2clAttachmentComposerController ensureAttachmentController() {
    if (attachmentController != null) {
      return attachmentController;
    }
    String waveId = requireNonEmpty(writeSession.getSelectedWaveId(), "Selected wave is required.");
    attachmentController =
        attachmentControllerFactory.create(
            waveId + "/~/conv+root",
            extractDomain(waveId),
            this::onAttachmentInserted,
            this::onAttachmentStateChanged);
    return attachmentController;
  }

  private void onAttachmentInserted(
      J2clComposerDocument document,
      J2clAttachmentComposerController.AttachmentInsertion insertion) {
    insertedAttachments.add(insertion);
    if (shouldClearReplyErrorAfterAttachmentInsert()) {
      replyErrorText = "";
    }
    activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
    commandStatusText = "Attached " + attachmentStatusLabel(insertion) + ".";
    commandErrorText = "";
    render();
  }

  private void onAttachmentStateChanged() {
    refreshAttachmentCommandState();
    // State changes only resolve the pending-upload gate; empty-reply validation clears on insert.
    if (!hasPendingAttachmentUpload() && PENDING_ATTACHMENT_REPLY_MESSAGE.equals(replyErrorText)) {
      replyErrorText = "";
    }
    render();
  }

  private void refreshAttachmentCommandState() {
    if (attachmentController == null) {
      return;
    }
    List<J2clAttachmentComposerController.UploadItem> queue =
        attachmentController.getQueueSnapshot();
    for (int i = queue.size() - 1; i >= 0; i--) {
      J2clAttachmentComposerController.UploadItem item = queue.get(i);
      if (item.getStatus() == J2clAttachmentComposerController.UploadStatus.FAILED
          || item.getStatus() == J2clAttachmentComposerController.UploadStatus.INSERT_FAILED) {
        activeCommandId = ATTACHMENT_ERROR_STATE_ID;
        commandStatusText = "";
        commandErrorText =
            item.getFileName()
                + " failed: "
                + (item.getErrorMessage().isEmpty()
                    ? "Attachment upload failed."
                    : item.getErrorMessage());
        return;
      }
    }
    for (J2clAttachmentComposerController.UploadItem item : queue) {
      if (item.getStatus() == J2clAttachmentComposerController.UploadStatus.UPLOADING) {
        activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
        commandStatusText =
            "Uploading " + item.getFileName() + " (" + item.getProgressPercent() + "%).";
        commandErrorText = "";
        return;
      }
      if (item.getStatus() == J2clAttachmentComposerController.UploadStatus.QUEUED) {
        activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
        commandStatusText = "Queued " + item.getFileName() + " for upload.";
        commandErrorText = "";
        return;
      }
    }
    if (!insertedAttachments.isEmpty()) {
      J2clAttachmentComposerController.AttachmentInsertion insertion =
          insertedAttachments.get(insertedAttachments.size() - 1);
      activeCommandId = J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE.id();
      commandStatusText = "Attached " + attachmentStatusLabel(insertion) + ".";
      commandErrorText = "";
    }
  }

  private boolean shouldClearReplyErrorAfterAttachmentInsert() {
    // Covers pre-insert empty validation and the late-arriving pending-upload gate.
    return EMPTY_REPLY_VALIDATION_MESSAGE.equals(replyErrorText)
        || PENDING_ATTACHMENT_REPLY_MESSAGE.equals(replyErrorText);
  }

  private static String attachmentStatusLabel(
      J2clAttachmentComposerController.AttachmentInsertion insertion) {
    String caption = insertion.getCaption();
    return caption == null || caption.isEmpty() ? "attachment" : caption;
  }

  private boolean hasPendingAttachmentUpload() {
    if (attachmentController == null) {
      return false;
    }
    for (J2clAttachmentComposerController.UploadItem item : attachmentController.getQueueSnapshot()) {
      if (item.getStatus() == J2clAttachmentComposerController.UploadStatus.QUEUED
          || item.getStatus() == J2clAttachmentComposerController.UploadStatus.UPLOADING) {
        return true;
      }
    }
    return false;
  }

  private void resetAttachmentState() {
    J2clAttachmentComposerController previousController = attachmentController;
    attachmentController = null;
    insertedAttachments.clear();
    attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.MEDIUM;
    activeCommandId = "";
    annotationCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    if (previousController != null) {
      previousController.cancelAndReset();
    }
  }

  private static boolean isAttachmentAction(J2clDailyToolbarAction action) {
    return action == J2clDailyToolbarAction.ATTACHMENT_INSERT
        || action == J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE
        || action == J2clDailyToolbarAction.ATTACHMENT_CANCEL
        || action == J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE
        || action == J2clDailyToolbarAction.ATTACHMENT_SIZE_SMALL
        || action == J2clDailyToolbarAction.ATTACHMENT_SIZE_MEDIUM
        || action == J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE;
  }

  private static boolean isAttachmentEntryAction(J2clDailyToolbarAction action) {
    return action == J2clDailyToolbarAction.ATTACHMENT_INSERT
        || action == J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE;
  }

  private static String annotationKey(J2clDailyToolbarAction action) {
    if (action == J2clDailyToolbarAction.BOLD) {
      return "fontWeight";
    }
    if (action == J2clDailyToolbarAction.ITALIC) {
      return "fontStyle";
    }
    if (action == J2clDailyToolbarAction.UNDERLINE
        || action == J2clDailyToolbarAction.STRIKETHROUGH) {
      return "textDecoration";
    }
    return null;
  }

  private static String annotationValue(J2clDailyToolbarAction action) {
    if (action == J2clDailyToolbarAction.BOLD) {
      return "bold";
    }
    if (action == J2clDailyToolbarAction.ITALIC) {
      return "italic";
    }
    if (action == J2clDailyToolbarAction.UNDERLINE) {
      return "underline";
    }
    if (action == J2clDailyToolbarAction.STRIKETHROUGH) {
      return "line-through";
    }
    return null;
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
    String currentWaveId =
        writeSession == null ? lastSelectedWaveId : writeSession.getSelectedWaveId();
    String nextWaveId = nextWriteSession == null ? null : nextWriteSession.getSelectedWaveId();
    if (nextWriteSession == null) {
      return false;
    }
    return !safeEquals(currentWaveId, nextWaveId);
  }

  private static boolean hasSelectedWave(J2clSidecarWriteSession session) {
    return session != null && !isEmpty(session.getSelectedWaveId());
  }

  private static String normalizeDraft(String draft) {
    return draft == null ? "" : draft;
  }

  private static String extractDomain(String waveId) {
    int separator = requireNonEmpty(waveId, "Selected wave is required.").indexOf('/');
    if (separator <= 0) {
      throw new IllegalArgumentException("Selected wave id must include a domain.");
    }
    return waveId.substring(0, separator);
  }

  private static <T> T requirePresent(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
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

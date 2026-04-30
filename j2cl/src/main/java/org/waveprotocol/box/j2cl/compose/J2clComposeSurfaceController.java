package org.waveprotocol.box.j2cl.compose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentIdGenerator;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.richtext.J2clComposerDocument;
import org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
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

    /**
     * J-UI-3 (#1081, R-5.1): focus the title input on the create form so
     * the rail's New Wave button gives the user an immediate place to type.
     * Default no-op so existing test doubles compile unchanged.
     */
    default void focusCreateSurface() {}
  }

  public interface Listener {
    void onCreateDraftChanged(String draft);

    /**
     * J-UI-3 (#1081, R-5.1): the title input value changed.
     * Default no-op so existing test doubles compile unchanged.
     */
    default void onCreateTitleChanged(String title) {}

    void onCreateSubmitted(String draft);

    /**
     * J-UI-3 (#1081, R-5.1): submit triggered from the create form with
     * both title and body provided in a single event so the controller
     * snapshots both at the same instant. Default delegates to
     * {@link #onCreateSubmitted} so plain-text-only test doubles continue
     * to work; production wires this directly.
     */
    default void onCreateSubmittedWithTitle(String title, String draft) {
      onCreateSubmitted(draft);
    }

    void onReplyDraftChanged(String draft);

    void onReplySubmitted(String draft);

    /**
     * J-UI-5 (#1083, R-5.1 + R-5.7): the user submitted a reply from the
     * inline rich-text composer. {@code components} carries the
     * per-fragment text + annotation runs serialized by
     * {@code wavy-composer.js#serializeRichComponents}. The controller
     * builds a {@link J2clComposerDocument} straight from these
     * components (text + annotated text runs) so the submit delta
     * preserves the user's bold / italic / underline / strikethrough /
     * unordered-list / ordered-list / link / blockquote formatting.
     *
     * <p>Default implementation falls back to plain text by joining
     * the supplied components' text. Existing test doubles continue
     * to compile against {@link #onReplySubmitted(String)} without
     * changes; production wiring overrides this method.
     */
    default void onReplySubmittedWithComponents(List<SubmittedComponent> components) {
      StringBuilder builder = new StringBuilder();
      if (components != null) {
        for (SubmittedComponent component : components) {
          if (component != null && component.getText() != null) {
            builder.append(component.getText());
          }
        }
      }
      onReplySubmitted(builder.toString());
    }

    void onAttachmentFilesSelected(List<AttachmentFileSelection> selections);

    void onPastedImage(Object imagePayload);

    /**
     * F-3.S2 (#1038, R-5.3): a participant was picked from the
     * `<mention-suggestion-popover>` mounted by `<wavy-composer>`. The
     * controller snapshots the participant address + display name in
     * `pendingMentions` so the next reply submit splits the plain
     * draft at chip-text occurrences and emits {@code mention/user}
     * annotated components carrying the participant address.
     * {@code chipTextOffset} is the chip's plain-text offset within
     * the composer body at pick time (-1 when unavailable). The
     * controller uses the offset to disambiguate duplicate chip texts
     * (PR #1066 review thread PRRT_kwDOBwxLXs593gTR) so picks that
     * share a display name, or follow a manually typed `@Name`
     * substring, are bound to the correct chip on submit.
     * Default implementation is a no-op so existing test doubles
     * continue to compile without changes.
     */
    default void onMentionPicked(
        String participantAddress, String displayName, int chipTextOffset) {}

    /**
     * F-3.S2 (#1038, R-5.3): the user dismissed the mention popover
     * with a non-empty query (typed `@al` then Esc). Recorded as a
     * telemetry event; no model state change.
     */
    default void onMentionAbandoned() {}

    /**
     * F-3.S2 (#1038, R-5.4): the per-blip task affordance was clicked.
     * The controller emits a stand-alone `task/done` toggle delta.
     */
    default void onTaskToggled(String blipId, boolean completed) {}

    /**
     * F-3.S2 (#1038, R-5.4 step 5): the task-metadata popover emitted
     * a submit. The controller emits a stand-alone delta carrying
     * `task/assignee` + `task/dueTs` annotations.
     */
    default void onTaskMetadataChanged(String blipId, String assigneeAddress, String dueDate) {}

    /**
     * F-3.S3 (#1038, R-5.5): the user picked a reaction emoji in the
     * picker, or clicked an existing reaction chip. The controller
     * computes whether this is an add-or-remove from its cached
     * reaction snapshot and emits the corresponding delta against the
     * `react+<blipId>` data document. {@code emoji} is the literal
     * emoji glyph; {@code blipId} is the blip the reaction targets.
     * Default no-op so existing test doubles compile unchanged.
     */
    default void onReactionToggled(String blipId, String emoji) {}

    /**
     * F-3.S3 (#1038, R-5.5): returns the cached reaction snapshot for
     * the given blip so the view can populate the authors popover.
     * Default returns an empty list so existing test doubles compile.
     */
    default List<SidecarReactionEntry> getReactionSnapshot(String blipId) {
      return Collections.<SidecarReactionEntry>emptyList();
    }

    /**
     * F-3.S4 (#1038, R-5.6 step 1): the user dropped files into the
     * composer body. The controller routes through the same plumbing
     * as {@link #onAttachmentFilesSelected} (default delegates) so
     * existing test doubles compile. Implementations may override to
     * record a separate `compose.attachment_dropped` telemetry event.
     */
    default void onDroppedFiles(List<AttachmentFileSelection> selections) {
      onAttachmentFilesSelected(selections);
    }

    /**
     * F-3.S4 (#1038, R-5.6 F.6): the user confirmed deletion of a
     * blip in the wavy confirm dialog. The controller builds a
     * {@link J2clRichContentDeltaFactory#blipDeleteRequest} delta and
     * submits it via the gateway. Default no-op so existing test
     * doubles compile unchanged.
     */
    default void onDeleteBlipRequested(String blipId, String expectedWaveId) {}
  }

  /**
   * J-UI-5 (#1083, R-5.7): a single text or annotated-text run forwarded
   * by `wavy-composer.js#serializeRichComponents` on reply submit.
   * Mirrors the JS schema: a TEXT component carries plain text; an
   * ANNOTATED component wraps its text in one or more
   * `(annotationKey, annotationValue)` pairs the read codec already
   * understands.
   *
   * <p>Multi-annotation support (codex review #1095 thread
   * PRRT_kwDOBwxLXs5-C84a): combined wraps such as
   * `<strong><em>x</em></strong>` carry both `fontStyle=italic` AND
   * `fontWeight=bold` on the same text run; the controller's submit
   * path opens / closes both annotations bracketing the chars op so
   * combined styles round-trip on reload.
   */
  public static final class SubmittedComponent {
    public enum Kind {
      TEXT,
      ANNOTATED
    }

    public static final class Annotation {
      private final String key;
      private final String value;

      public Annotation(String key, String value) {
        this.key = key == null ? "" : key;
        this.value = value == null ? "" : value;
      }

      public String getKey() {
        return key;
      }

      public String getValue() {
        return value;
      }
    }

    private final Kind kind;
    private final String text;
    private final List<Annotation> annotations;

    public static SubmittedComponent text(String text) {
      return new SubmittedComponent(
          Kind.TEXT,
          text == null ? "" : text,
          Collections.<Annotation>emptyList());
    }

    public static SubmittedComponent annotated(
        String text, String annotationKey, String annotationValue) {
      List<Annotation> list = new ArrayList<Annotation>(1);
      list.add(new Annotation(annotationKey, annotationValue));
      return new SubmittedComponent(
          Kind.ANNOTATED,
          text == null ? "" : text,
          list);
    }

    public static SubmittedComponent annotatedMulti(
        String text, List<Annotation> annotations) {
      List<Annotation> list = annotations == null
          ? Collections.<Annotation>emptyList()
          : new ArrayList<Annotation>(annotations);
      return new SubmittedComponent(
          list.isEmpty() ? Kind.TEXT : Kind.ANNOTATED,
          text == null ? "" : text,
          list);
    }

    private SubmittedComponent(Kind kind, String text, List<Annotation> annotations) {
      this.kind = kind;
      this.text = text;
      this.annotations = Collections.unmodifiableList(annotations);
    }

    public Kind getKind() {
      return kind;
    }

    public String getText() {
      return text;
    }

    /** First annotation key in declaration order; empty when this is a TEXT component. */
    public String getAnnotationKey() {
      return annotations.isEmpty() ? "" : annotations.get(0).getKey();
    }

    /** First annotation value in declaration order; empty when this is a TEXT component. */
    public String getAnnotationValue() {
      return annotations.isEmpty() ? "" : annotations.get(0).getValue();
    }

    /** All annotations attached to this run (≥ 0 entries, in declaration order). */
    public List<Annotation> getAnnotations() {
      return annotations;
    }
  }

  @FunctionalInterface
  public interface CreateSuccessHandler {
    void onWaveCreated(String waveId);

    /**
     * J-UI-3 (#1081, R-5.1): called instead of {@link #onWaveCreated} on
     * production paths that wire the optimistic-digest prepend in the
     * search panel. {@code title} is the trimmed title-input value the
     * user typed; the default delegates to {@link #onWaveCreated} so
     * existing handlers continue to work.
     */
    default void onWaveCreated(String waveId, String title) {
      onWaveCreated(waveId);
    }
  }

  @FunctionalInterface
  public interface ReplySuccessHandler {
    void onReplySubmitted(String waveId);

    default void onReplySubmitted(String waveId, long resultingVersion) {
      onReplySubmitted(waveId);
    }

    default void onReplySubmitted(
        String waveId, long resultingVersion, String submittedBlipId) {
      onReplySubmitted(waveId, resultingVersion);
    }
  }

  public interface DeltaFactory {
    CreateWaveRequest createWaveRequest(
        String address, String draftText, J2clComposerDocument document);

    SidecarSubmitRequest createReplyRequest(
        String address,
        J2clSidecarWriteSession session,
        String draftText,
        J2clComposerDocument document);

    /**
     * F-3.S2 (#1038, R-5.4): build a stand-alone toggle request for a
     * single blip. The plain-text fallback factory throws
     * UnsupportedOperationException because plain-text submissions
     * have no concept of blip-level annotations; the rich-content
     * factory (default in production) handles this via
     * {@link J2clRichContentDeltaFactory#taskToggleRequest}.
     */
    default SidecarSubmitRequest createTaskToggleRequest(
        String address, J2clSidecarWriteSession session, String blipId, boolean completed) {
      throw new UnsupportedOperationException(
          "Task toggle is only available with the rich-content delta factory.");
    }

    /**
     * F-3.S2 (#1038, R-5.4 step 5): build a stand-alone metadata
     * request for a single blip carrying `task/assignee` + `task/dueTs`.
     */
    default SidecarSubmitRequest createTaskMetadataRequest(
        String address,
        J2clSidecarWriteSession session,
        String blipId,
        String assigneeAddress,
        String dueDate) {
      throw new UnsupportedOperationException(
          "Task metadata is only available with the rich-content delta factory.");
    }

    /**
     * F-3.S3 (#1038, R-5.5): build a reaction-toggle request against
     * the `react+<blipId>` data document. {@code currentSnapshot} is
     * the most recent per-blip {@link SidecarReactionEntry} list the
     * controller has; {@code adding} is true when the user is not yet
     * a reactor under {@code emoji}. Plain-text fallback throws
     * because reactions are rich-only.
     */
    default SidecarSubmitRequest createReactionToggleRequest(
        String address,
        J2clSidecarWriteSession session,
        String blipId,
        String emoji,
        List<SidecarReactionEntry> currentSnapshot,
        boolean adding) {
      throw new UnsupportedOperationException(
          "Reaction toggle is only available with the rich-content delta factory.");
    }

    /**
     * F-3.S4 (#1038, R-5.6 F.6): build a stand-alone blip-deletion
     * request. The plain-text fallback throws because deletions are
     * rich-only; the rich-content factory implements via
     * {@link J2clRichContentDeltaFactory#blipDeleteRequest}.
     */
    default SidecarSubmitRequest createBlipDeleteRequest(
        String address, J2clSidecarWriteSession session, String blipId) {
      throw new UnsupportedOperationException(
          "Blip delete is only available with the rich-content delta factory.");
    }
  }

  public interface AttachmentControllerFactory {
    J2clAttachmentComposerController create(
        String waveRef,
        String domain,
        J2clAttachmentComposerController.DocumentInsertionCallback insertionCallback,
        J2clAttachmentComposerController.StateChangeCallback stateChangeCallback);
  }

  interface AttachmentUploadClientFactory {
    J2clAttachmentUploadClient create();
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
  static final String WAITING_FOR_WRITE_SESSION_REPLY_MESSAGE =
      "Wait for the selected wave to finish opening before sending a reply.";
  // Legacy constructors are not used by the root shell; production passes the root session seed.
  private static final String LEGACY_ATTACHMENT_SESSION_SEED = "j2cl";

  private final Gateway gateway;
  private final View view;
  private final DeltaFactory deltaFactory;
  private final AttachmentControllerFactory attachmentControllerFactory;
  private final CreateSuccessHandler createSuccessHandler;
  private final ReplySuccessHandler replySuccessHandler;
  private final J2clClientTelemetry.Sink telemetrySink;
  // F-3.S3 (#1038, R-5.5): listener invoked after each successful
  // bootstrap with the resolved signed-in address. The root shell uses
  // this to set the selected-wave view's current-user address so the
  // chip aria-pressed state reflects "this is your own reaction." Null
  // means no listener installed.
  private CurrentUserAddressListener currentUserAddressListener;
  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: hook fired
  // synchronously at the start of submitCreate so the search panel can
  // stamp the active query for the upcoming optimistic stub before the
  // bootstrap fetch and any user-driven query change. Null means no hook
  // installed; legacy callers that did not register a hook continue to
  // fall back to the success-time query inside onOptimisticDigest.
  private Runnable preCreateSubmitHook;
  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T: hook fired
  // on every create-failure path (bootstrap error, submit error,
  // stale-generation guard) so the search panel can drop the matching
  // submit-query stamp it queued in preCreateSubmitHook. Without this
  // pairing a failed attempt's stamp leaks forward and scopes the next
  // successful create's optimistic stub to the wrong rail.
  private Runnable createFailureHook;
  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DZqM: idempotency
  // set tracking which create generations already had their submit-query
  // stamp retired. Each preCreateSubmitHook fires under one generation;
  // any one of {handleCreateFailure, bootstrap-mismatch, response-
  // mismatch, onSignedOut} may retire it, but only the first such call
  // for that generation should run createFailureHook. Without this, a
  // late stale callback firing AFTER onSignedOut already dropped the
  // stamp would dequeue a NEWER submit's stamp from the search panel.
  private final Set<Integer> retiredCreateStampGenerations = new HashSet<>();
  private String createDraft = "";
  // J-UI-3 (#1081, R-5.1): the title-input value separate from createDraft.
  // Composed into the rich-content document on submit alongside the body so
  // WaveDigester picks the conv/title annotation up server-side.
  private String createTitleDraft = "";

  /**
   * F-3.S3 (#1038, R-5.5): callback fired after each successful
   * gateway bootstrap with the signed-in user's address. Idempotent:
   * the controller invokes the listener every bootstrap so the view's
   * cached address survives reconnects without churn.
   */
  @FunctionalInterface
  public interface CurrentUserAddressListener {
    void onCurrentUserAddress(String address);
  }
  private boolean createSubmitting;
  private String createStatusText = "Create a self-owned wave inside the root shell.";
  private String createErrorText = "";
  private J2clSidecarWriteSession writeSession;
  private String lastSelectedWaveId;
  private String selectedWaveParticipantContextId;
  private List<String> selectedWaveParticipantIds = Collections.emptyList();
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
  // J-UI-5 (#1083, R-5.7): per-fragment annotated runs serialized by
  // the inline rich-text composer. When non-null and non-empty, the
  // next submitReply call builds the J2clComposerDocument from these
  // components instead of the legacy single-annotation-on-whole-draft
  // path. Cleared on submit success / failure / wave change.
  private List<SubmittedComponent> pendingSubmittedComponents;
  private String commandStatusText = "";
  private String commandErrorText = "";
  private J2clAttachmentComposerController attachmentController;
  private final List<J2clAttachmentComposerController.AttachmentInsertion> insertedAttachments =
      new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
  // F-3.S2 (#1038, R-5.3, PR #1066 review thread PRRT_kwDOBwxLXs592RVM):
  // mention picks recorded between draft edits. Each pick becomes an
  // annotated `mention/user` component on the next reply submit so the
  // outgoing delta carries the participant address, not just plain
  // `@DisplayName` text. Cleared on submit success, sign-out, and
  // wave change (mirrors `insertedAttachments`).
  private final List<PendingMention> pendingMentions = new ArrayList<PendingMention>();
  // F-3.S3 (#1038, R-5.5): per-blip reaction snapshot kept in sync via
  // setReactionSnapshot(blipId, entries). The controller uses the
  // snapshot at click time to decide whether the toggle adds or
  // removes the user's <user/> element under the chosen emoji.
  private final Map<String, List<SidecarReactionEntry>> reactionSnapshotsByBlip =
      new HashMap<String, List<SidecarReactionEntry>>();
  // Last successfully resolved user address from a gateway bootstrap.
  // Used to derive the toggle direction on bootstrap-failure telemetry paths
  // where bootstrap.getAddress() is unavailable.
  private String lastKnownAddress = "";
  private J2clAttachmentComposerController.DisplaySize attachmentDisplaySize =
      J2clAttachmentComposerController.DisplaySize.MEDIUM;

  /**
   * F-3.S2 (#1038, R-5.3): a mention chip the user picked in the
   * lit composer. The chip text is `@<displayName>` (or
   * `@<address>` if the display name is blank); on submit the
   * controller splits the plain reply draft at occurrences of this
   * chip text and emits an annotated component keyed by `mention/user`
   * carrying {@link #address} as the annotation value.
   */
  static final class PendingMention {
    final String address;
    final String displayName;
    final String chipText;
    /**
     * Plain-text offset of the chip within the composer body at pick
     * time (using {@code _serializeBodyText}'s flattening rules).
     * {@link #UNKNOWN_CHIP_OFFSET} when the lit picker did not surface
     * an offset (e.g. legacy callers, tests using the two-arg
     * overload). PR #1066 review thread PRRT_kwDOBwxLXs593gTR — the
     * offset is the primary disambiguator for duplicate chip texts on
     * submit serialisation.
     */
    final int chipTextOffset;

    static final int UNKNOWN_CHIP_OFFSET = -1;

    PendingMention(String address, String displayName) {
      this(address, displayName, UNKNOWN_CHIP_OFFSET);
    }

    PendingMention(String address, String displayName, int chipTextOffset) {
      this.address = address == null ? "" : address.trim();
      String label = displayName == null ? "" : displayName.trim();
      this.displayName = label.isEmpty() ? this.address : label;
      this.chipText = "@" + this.displayName;
      this.chipTextOffset = chipTextOffset;
    }
  }

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
        replySuccessHandler,
        J2clClientTelemetry.noop());
  }

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      DeltaFactory deltaFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler,
      J2clClientTelemetry.Sink telemetrySink) {
    this(
        gateway,
        view,
        deltaFactory,
        attachmentControllerFactory(LEGACY_ATTACHMENT_SESSION_SEED, telemetrySink),
        createSuccessHandler,
        replySuccessHandler,
        telemetrySink);
  }

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      DeltaFactory deltaFactory,
      AttachmentControllerFactory attachmentControllerFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler) {
    this(
        gateway,
        view,
        deltaFactory,
        attachmentControllerFactory,
        createSuccessHandler,
        replySuccessHandler,
        J2clClientTelemetry.noop());
  }

  public J2clComposeSurfaceController(
      Gateway gateway,
      View view,
      DeltaFactory deltaFactory,
      AttachmentControllerFactory attachmentControllerFactory,
      CreateSuccessHandler createSuccessHandler,
      ReplySuccessHandler replySuccessHandler,
      J2clClientTelemetry.Sink telemetrySink) {
    this.gateway = gateway;
    this.view = view;
    this.deltaFactory = deltaFactory;
    this.attachmentControllerFactory =
        requirePresent(attachmentControllerFactory, "Attachment controller factory is required.");
    this.createSuccessHandler = createSuccessHandler;
    this.replySuccessHandler = replySuccessHandler;
    this.telemetrySink = requirePresent(telemetrySink, "Compose telemetry sink is required.");
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
          public void onCreateTitleChanged(String title) {
            J2clComposeSurfaceController.this.onCreateTitleChanged(title);
          }

          @Override
          public void onCreateSubmitted(String draft) {
            J2clComposeSurfaceController.this.onCreateSubmitted(draft);
          }

          @Override
          public void onCreateSubmittedWithTitle(String title, String draft) {
            J2clComposeSurfaceController.this.onCreateSubmittedWithTitle(title, draft);
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
          public void onReplySubmittedWithComponents(List<SubmittedComponent> components) {
            J2clComposeSurfaceController.this.onReplySubmittedWithComponents(components);
          }

          @Override
          public void onAttachmentFilesSelected(List<AttachmentFileSelection> selections) {
            J2clComposeSurfaceController.this.onAttachmentFilesSelected(selections);
          }

          @Override
          public void onPastedImage(Object imagePayload) {
            J2clComposeSurfaceController.this.onPastedImage(imagePayload);
          }

          @Override
          public void onMentionPicked(
              String participantAddress, String displayName, int chipTextOffset) {
            J2clComposeSurfaceController.this.onMentionPicked(
                participantAddress, displayName, chipTextOffset);
          }

          @Override
          public void onMentionAbandoned() {
            J2clComposeSurfaceController.this.onMentionAbandoned();
          }

          @Override
          public void onTaskToggled(String blipId, boolean completed) {
            J2clComposeSurfaceController.this.onTaskToggled(blipId, completed);
          }

          @Override
          public void onTaskMetadataChanged(
              String blipId, String assigneeAddress, String dueDate) {
            J2clComposeSurfaceController.this.onTaskMetadataChanged(
                blipId, assigneeAddress, dueDate);
          }

          @Override
          public void onReactionToggled(String blipId, String emoji) {
            J2clComposeSurfaceController.this.onReactionToggled(blipId, emoji);
          }

          @Override
          public List<SidecarReactionEntry> getReactionSnapshot(String blipId) {
            if (blipId == null || blipId.isEmpty()) {
              return Collections.<SidecarReactionEntry>emptyList();
            }
            List<SidecarReactionEntry> s = reactionSnapshotsByBlip.get(blipId.trim());
            return s != null ? new ArrayList<SidecarReactionEntry>(s)
                : Collections.<SidecarReactionEntry>emptyList();
          }

          @Override
          public void onDroppedFiles(List<AttachmentFileSelection> selections) {
            J2clComposeSurfaceController.this.onDroppedFiles(selections);
          }

          @Override
          public void onDeleteBlipRequested(String blipId, String expectedWaveId) {
            J2clComposeSurfaceController.this.onDeleteBlipRequested(blipId, expectedWaveId);
          }
        });
    render();
  }

  public void onSignedOut() {
    signedOut = true;
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DQdB: a sign-out
    // mid-create bumps createGeneration which gates the deferred
    // success/failure callbacks out, so handleCreateFailure (and its
    // failure hook) never run. The pre-submit hook already queued a
    // submit-query stamp on the search panel; without explicit cleanup
    // here that stamp would leak forward and scope the next successful
    // create's stub to the wrong rail. Snapshot the in-flight bit
    // BEFORE we clear createSubmitting, then run the failure hook to
    // drop the orphaned stamp.
    boolean abortedInFlightCreate = createSubmitting;
    int abortedCreateGeneration = createGeneration;
    createGeneration++;
    replyGeneration++;
    createSubmitting = false;
    replySubmitting = false;
    writeSession = null;
    lastSelectedWaveId = null;
    selectedWaveParticipantContextId = null;
    selectedWaveParticipantIds = Collections.emptyList();
    replyStaleBasis = false;
    replyStaleWaveId = null;
    replyErrorText = "";
    createErrorText = "";
    activeCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    resetAttachmentState();
    // F-3.S3 (#1038, R-5.5): drop cached reaction snapshots so a
    // post-sign-out toggle cannot project against the prior session's
    // state. Clear the published user address so the view's aria-pressed
    // state resets immediately on sign-out.
    reactionSnapshotsByBlip.clear();
    notifyCurrentUserAddress("");
    createStatusText = "Sign in to create or reply in the J2CL root shell.";
    replyStatusText = createStatusText;
    render();
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DQdB +
    // PRRT_kwDOBwxLXs5-DZqM: drop the submit-query stamp left behind
    // by an in-flight create that the generation guard will now silently
    // discard. retireCreateStampOnce records the aborted generation in
    // retiredCreateStampGenerations so a stale bootstrap/submit
    // callback firing later for the same generation cannot dequeue a
    // newer submission's stamp. Run AFTER render so the view sees the
    // signed-out chrome before any downstream effect.
    if (abortedInFlightCreate) {
      retireCreateStampOnce(abortedCreateGeneration);
    }
  }

  public void onCreateDraftChanged(String draft) {
    createDraft = normalizeDraft(draft);
    createErrorText = "";
    render();
  }

  /**
   * J-UI-3 (#1081, R-5.1): the title input's value changed. Live updates
   * are kept lossless so users can type multi-word titles normally — we
   * only neutralise embedded newlines (paste safety: a literal newline
   * would break the conv/title annotation span). Leading and trailing
   * whitespace is preserved here and trimmed on submit (codex P1 review
   * thread on PR #1090).
   */
  public void onCreateTitleChanged(String title) {
    createTitleDraft = sanitizeTitleLive(title);
    createErrorText = "";
    render();
  }

  public void onCreateSubmitted(String draft) {
    createDraft = normalizeDraft(draft);
    submitCreate();
  }

  /**
   * J-UI-3 (#1081, R-5.1): submit triggered from the title input (Enter key).
   * Snapshots both title and body before submit so a stray draft-change after
   * Enter does not race with submitCreate. The submit path is the only place
   * that trims the title, so live edits keep every character the user typed.
   */
  public void onCreateSubmittedWithTitle(String title, String draft) {
    createTitleDraft = normalizeTitleForSubmit(title);
    createDraft = normalizeDraft(draft);
    submitCreate();
  }

  /**
   * J-UI-3 (#1081, R-5.1): focuses the title input on the create form.
   * Public entrypoint for the rail's New Wave button via the root shell.
   */
  public void focusCreateSurface() {
    if (started) {
      view.focusCreateSurface();
    }
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
    pendingSubmittedComponents = null;
    submitReply();
  }

  /**
   * J-UI-5 (#1083, R-5.1 + R-5.7): submit a reply whose body is a list
   * of structured text + annotated-text runs serialized by the inline
   * `<wavy-composer>`. Builds a {@link J2clComposerDocument} that
   * preserves the user's per-fragment formatting (bold / italic /
   * underline / strikethrough / list / link) instead of collapsing
   * the whole draft to a single annotation.
   */
  public void onReplySubmittedWithComponents(List<SubmittedComponent> components) {
    if (components == null) {
      pendingSubmittedComponents = null;
      onReplySubmitted("");
      return;
    }
    StringBuilder plainBuilder = new StringBuilder();
    for (SubmittedComponent component : components) {
      if (component != null && component.getText() != null) {
        plainBuilder.append(component.getText());
      }
    }
    replyDraft = normalizeDraft(plainBuilder.toString());
    pendingSubmittedComponents = new ArrayList<SubmittedComponent>(components);
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
      emitRichEditCommand(action, "cleared");
      return true;
    }
    if (safeEquals(annotationCommandId, action.id())) {
      activeCommandId = "";
      annotationCommandId = "";
      commandErrorText = "";
      commandStatusText = "";
      refreshAttachmentCommandState();
      if (commandStatusText.isEmpty() && commandErrorText.isEmpty()) {
        commandStatusText = action.label() + " cleared.";
      }
      render();
      view.focusReplyComposer();
      emitRichEditCommand(action, "cleared");
      return true;
    }
    activeCommandId = action.id();
    annotationCommandId = action.id();
    commandErrorText = "";
    commandStatusText = action.label() + " applied to the current draft.";
    render();
    view.focusReplyComposer();
    emitRichEditCommand(action, "applied");
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

  /**
   * F-3.S4 (#1038, R-5.6 step 1): the user dropped one or more files
   * onto the composer body. Routes through the same upload plumbing as
   * the H.19 paperclip path; emits a separate `compose.attachment_dropped`
   * telemetry event so the analytics dashboards distinguish drag-drop
   * from explicit selection.
   */
  public void onDroppedFiles(List<AttachmentFileSelection> selections) {
    int count = selections == null ? 0 : selections.size();
    String kind = "file";
    if (count > 0) {
      // Best-effort kind detection from the first selection's filename
      // suffix. The actual MIME inspection happens in the upload client.
      AttachmentFileSelection first = selections.get(0);
      if (first != null) {
        String name = first.getFileName();
        if (name != null) {
          String lower = name.toLowerCase(Locale.ROOT);
          if (lower.endsWith(".png")
              || lower.endsWith(".jpg")
              || lower.endsWith(".jpeg")
              || lower.endsWith(".gif")
              || lower.endsWith(".webp")
              || lower.endsWith(".bmp")
              || lower.endsWith(".svg")) {
            kind = "image";
          }
        }
      }
    }
    // Determine the actual drop outcome by mirroring the acceptance
    // gates inside onAttachmentFilesSelected — telemetry should reflect
    // whether the drop was queued, rejected, or empty rather than
    // optimistically labelling every drop "queued".
    String outcome;
    if (count == 0) {
      outcome = "empty";
    } else if (signedOut) {
      outcome = "rejected-signed-out";
    } else if (replySubmitting) {
      outcome = "rejected-reply-submitting";
    } else if (!hasSelectedWave(writeSession)) {
      outcome = "rejected-no-wave";
    } else {
      outcome = "queued";
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("compose.attachment_dropped")
              .field("outcome", outcome)
              .field("kind", kind)
              .field("count", String.valueOf(count))
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
    onAttachmentFilesSelected(selections);
  }

  /**
   * F-3.S4 (#1038, R-5.6 F.6): the user confirmed deletion of {@code
   * blipId} via the wavy confirm dialog. Builds a delete delta via the
   * configured {@link DeltaFactory} and submits it through the gateway.
   * On signed-out, missing wave, or empty {@code blipId} the call is a
   * no-op (with a telemetry event recording the reason).
   */
  public void onDeleteBlipRequested(final String blipId, final String expectedWaveId) {
    if (signedOut) {
      recordBlipDeleteTelemetry("signed-out");
      return;
    }
    if (blipId == null || blipId.trim().isEmpty()) {
      recordBlipDeleteTelemetry("missing-blip");
      return;
    }
    if (expectedWaveId != null && !expectedWaveId.isEmpty()
        && writeSession != null
        && !expectedWaveId.equals(writeSession.getSelectedWaveId())) {
      recordBlipDeleteTelemetry("wave-changed");
      return;
    }
    if (!hasSelectedWave(writeSession)) {
      recordBlipDeleteTelemetry("no-selected-wave");
      return;
    }
    final String trimmed = blipId.trim();
    final J2clSidecarWriteSession submitSession = writeSession;
    gateway.fetchRootSessionBootstrap(
        (SidecarSessionBootstrap bootstrap) -> {
          if (signedOut || !sameLogicalSession(submitSession, writeSession)) {
            recordBlipDeleteTelemetry("session-changed");
            return;
          }
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createBlipDeleteRequest(
                    bootstrap.getAddress(), writeSession, trimmed);
          } catch (RuntimeException e) {
            recordBlipDeleteTelemetry("failure-build");
            return;
          }
          gateway.submit(
              bootstrap,
              request,
              (SidecarSubmitResponse response) -> {
                if (response != null
                    && response.getErrorMessage() != null
                    && !response.getErrorMessage().isEmpty()) {
                  recordBlipDeleteTelemetry("failure-submit");
                  return;
                }
                recordBlipDeleteTelemetry("success");
              },
              (String error) -> recordBlipDeleteTelemetry("failure-submit"));
        },
        (String error) -> recordBlipDeleteTelemetry("failure-bootstrap"));
  }

  private void recordBlipDeleteTelemetry(String outcome) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("compose.blip_deleted")
              .field("outcome", outcome)
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
  }

  /**
   * F-3.S2 (#1038, R-5.3): a mention candidate was picked from the
   * suggestion popover. The lit composer DOM already inserted the
   * chip span; the controller snapshots the participant address +
   * display name so the next reply submission carries a
   * `mention/user` annotated component referencing the participant
   * address (PR #1066 review thread PRRT_kwDOBwxLXs592RVM —
   * mentions must round-trip through the model, not just emit
   * literal `@DisplayName` text).
   *
   * <p>Picks are kept in insertion order. {@link #buildDocument}
   * walks the plain reply draft and matches each pending mention's
   * chip text (`@<displayName>`) as the leftmost occurrence at-or-
   * after the running offset; deletions of the chip on the lit side
   * therefore self-heal because the missing chip text simply leaves
   * the pending entry unmatched and dropped at submit time.
   */
  public void onMentionPicked(String participantAddress, String displayName) {
    onMentionPicked(participantAddress, displayName, PendingMention.UNKNOWN_CHIP_OFFSET);
  }

  public void onMentionPicked(
      String participantAddress, String displayName, int chipTextOffset) {
    if (signedOut) return;
    if (participantAddress != null && !participantAddress.trim().isEmpty()) {
      pendingMentions.add(
          new PendingMention(participantAddress, displayName, chipTextOffset));
    }
    try {
      telemetrySink.record(J2clClientTelemetry.event("compose.mention_picked").build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
  }

  /**
   * F-3.S2 (#1038, R-5.3): the user dismissed the mention popover
   * with a non-empty query. Records telemetry only.
   */
  public void onMentionAbandoned() {
    if (signedOut) return;
    try {
      telemetrySink.record(J2clClientTelemetry.event("compose.mention_abandoned").build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
  }

  /**
   * F-3.S2 (#1038, R-5.4): the per-blip task affordance was clicked.
   * Emits a stand-alone toggle delta against the supplied blip
   * without touching the active reply draft. The bootstrap callback
   * bails out if the user has signed out or switched waves since the
   * click, preventing stale-session writes.
   */
  public void onTaskToggled(final String blipId, final boolean completed) {
    if (signedOut) return;
    if (blipId == null || blipId.trim().isEmpty()) return;
    if (!hasSelectedWave(writeSession)) return;
    final J2clSidecarWriteSession submitSession = writeSession;
    final String trimmedBlipId = blipId.trim();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          // F-3.S2 (#1068): a no-op session refresh on the same wave
          // produces a new J2clSidecarWriteSession instance. We must not
          // drop the write in that case — only bail when the user has
          // signed out or actually switched waves.
          if (signedOut || !sameLogicalSession(submitSession, writeSession)) {
            return;
          }
          notifyCurrentUserAddress(bootstrap.getAddress());
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createTaskToggleRequest(
                    bootstrap.getAddress(), writeSession, trimmedBlipId, completed);
          } catch (RuntimeException e) {
            // Toggling a task is best-effort; log telemetry and return.
            recordTaskToggleTelemetry(completed, "failure-build");
            return;
          }
          gateway.submit(
              bootstrap,
              request,
              response -> {
                if (response != null && !response.getErrorMessage().isEmpty()) {
                  recordTaskToggleTelemetry(completed, "failure-submit");
                  return;
                }
                recordTaskToggleTelemetry(completed, "success");
              },
              error -> recordTaskToggleTelemetry(completed, "failure-submit"));
        },
        error -> recordTaskToggleTelemetry(completed, "failure-bootstrap"));
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 5): the task-metadata popover was
   * submitted with a new owner + due date. Emits a stand-alone delta
   * carrying both annotations.
   */
  public void onTaskMetadataChanged(
      final String blipId, final String assigneeAddress, final String dueDate) {
    if (signedOut) return;
    if (blipId == null || blipId.trim().isEmpty()) return;
    if (!hasSelectedWave(writeSession)) return;
    final J2clSidecarWriteSession submitSession = writeSession;
    final String trimmedBlipId = blipId.trim();
    final String normalizedAssignee = assigneeAddress == null ? "" : assigneeAddress.trim();
    final String normalizedDue = dueDate == null ? "" : dueDate.trim();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          // F-3.S2 (#1068): tolerate no-op session refreshes on the same
          // wave; only drop the write when sign-out or a real wave
          // switch happens between click and bootstrap completion.
          if (signedOut || !sameLogicalSession(submitSession, writeSession)) {
            return;
          }
          notifyCurrentUserAddress(bootstrap.getAddress());
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createTaskMetadataRequest(
                    bootstrap.getAddress(),
                    writeSession,
                    trimmedBlipId,
                    normalizedAssignee,
                    normalizedDue);
          } catch (RuntimeException e) {
            recordTaskMetadataTelemetry("failure-build");
            return;
          }
          gateway.submit(
              bootstrap,
              request,
              response -> {
                if (response != null && !response.getErrorMessage().isEmpty()) {
                  recordTaskMetadataTelemetry("failure-submit");
                  return;
                }
                recordTaskMetadataTelemetry("success");
              },
              error -> recordTaskMetadataTelemetry("failure-submit"));
        },
        error -> recordTaskMetadataTelemetry("failure-bootstrap"));
  }

  private void recordTaskMetadataTelemetry(String outcome) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("compose.task_metadata_changed")
              .field("outcome", outcome)
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
  }

  /**
   * F-3.S3 (#1038, R-5.5): root shell installs this hook to learn the
   * signed-in user's address as soon as the first bootstrap completes
   * and after every reconnect. Used to colour the active-for-current-
   * user chip pressed state. Idempotent — passing {@code null}
   * disables the listener.
   */
  public void setCurrentUserAddressListener(CurrentUserAddressListener listener) {
    this.currentUserAddressListener = listener;
  }

  /**
   * J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: install a
   * hook fired synchronously at the start of {@link #submitCreate}, BEFORE
   * the gateway bootstrap fetch and the server round-trip. The root shell
   * uses it to stamp the search panel's active query as the upcoming
   * optimistic stub's submit-time scope so a query change between submit
   * and success cannot bind the stub to the wrong rail. Idempotent —
   * passing {@code null} disables the hook.
   */
  public void setPreCreateSubmitHook(Runnable hook) {
    this.preCreateSubmitHook = hook;
  }

  /**
   * J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T: install a
   * hook fired on every create-failure path so the search panel can drop
   * the submit-query stamp the pre-submit hook queued. The hook is
   * invoked synchronously when {@link #handleCreateFailure} fires, so
   * the matching pendingSubmitQueries entry is dequeued before any
   * subsequent successful create can consume it. Idempotent — passing
   * {@code null} disables the hook.
   */
  public void setCreateFailureHook(Runnable hook) {
    this.createFailureHook = hook;
  }

  /**
   * J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DZqM: retire the
   * submit-query stamp queued by preCreateSubmitHook for {@code
   * generation}. Idempotent per generation: a second call for the same
   * generation is a silent no-op so a stale bootstrap/submit callback
   * cannot dequeue a newer submission's stamp after onSignedOut already
   * dropped this one. Best-effort: a hook RuntimeException is swallowed.
   */
  private void retireCreateStampOnce(int generation) {
    if (createFailureHook == null) {
      return;
    }
    if (retiredCreateStampGenerations.add(generation)) {
      try {
        createFailureHook.run();
      } catch (RuntimeException ignored) {
        // Best-effort: never re-throw out of an integration callback.
      }
    }
  }

  /**
   * F-3.S3 (#1038, R-5.5): publish the latest per-blip reaction
   * snapshot from the model. The controller uses this on each toggle
   * click to decide whether the user is adding or removing their
   * reaction. The map is keyed by blip id; an empty entry signals "no
   * reactions yet" (the factory then emits the full envelope insert).
   */
  public void setReactionSnapshots(Map<String, List<SidecarReactionEntry>> snapshotsByBlip) {
    reactionSnapshotsByBlip.clear();
    if (snapshotsByBlip == null || snapshotsByBlip.isEmpty()) {
      return;
    }
    for (Map.Entry<String, List<SidecarReactionEntry>> entry : snapshotsByBlip.entrySet()) {
      String key = entry.getKey();
      if (key == null) continue;
      key = key.trim();
      if (key.isEmpty()) continue;
      List<SidecarReactionEntry> snapshot = entry.getValue();
      reactionSnapshotsByBlip.put(
          key,
          snapshot == null
              ? Collections.<SidecarReactionEntry>emptyList()
              : new ArrayList<SidecarReactionEntry>(snapshot));
    }
  }

  /**
   * F-3.S3 (#1038, R-5.5): the user clicked an existing reaction chip
   * or picked an emoji from the picker. The controller computes
   * adding-vs-removing from {@link #reactionSnapshotsByBlip} and emits
   * the corresponding delta against the `react+<blipId>` data
   * document. Mirrors {@link #onTaskToggled}'s independent-bootstrap
   * pattern so a reaction toggle on blip B does not clobber an
   * in-flight reply on blip A.
   */
  public void onReactionToggled(final String blipId, final String emoji) {
    if (signedOut) return;
    if (blipId == null || blipId.trim().isEmpty()) return;
    if (emoji == null || emoji.trim().isEmpty()) return;
    if (!hasSelectedWave(writeSession)) return;
    final J2clSidecarWriteSession submitSession = writeSession;
    final String trimmedBlipId = blipId.trim();
    final String trimmedEmoji = emoji.trim();
    final List<SidecarReactionEntry> snapshot =
        reactionSnapshotsByBlip.containsKey(trimmedBlipId)
            ? new ArrayList<SidecarReactionEntry>(reactionSnapshotsByBlip.get(trimmedBlipId))
            : Collections.<SidecarReactionEntry>emptyList();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (signedOut || writeSession != submitSession) {
            return;
          }
          notifyCurrentUserAddress(bootstrap.getAddress());
          final boolean adding = !userHasReactedWithEmoji(snapshot, bootstrap.getAddress(), trimmedEmoji);
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createReactionToggleRequest(
                    bootstrap.getAddress(),
                    submitSession,
                    trimmedBlipId,
                    trimmedEmoji,
                    snapshot,
                    adding);
          } catch (RuntimeException e) {
            recordReactionToggleTelemetry(adding, "failure-build");
            return;
          }
          gateway.submit(
              bootstrap,
              request,
              response -> {
                if (response != null && !response.getErrorMessage().isEmpty()) {
                  recordReactionToggleTelemetry(adding, "failure-submit");
                  return;
                }
                recordReactionToggleTelemetry(adding, "success");
              },
              error -> recordReactionToggleTelemetry(adding, "failure-submit"));
        },
        // Bootstrap failure: derive direction from the last cached address so the telemetry
        // reflects the user's actual intent rather than always emitting state="removed".
        error -> recordReactionToggleTelemetry(
            !userHasReactedWithEmoji(snapshot, lastKnownAddress, trimmedEmoji),
            "failure-bootstrap"));
  }

  private static boolean userHasReactedWithEmoji(
      List<SidecarReactionEntry> snapshot, String address, String emoji) {
    if (snapshot == null || snapshot.isEmpty()) return false;
    String normalizedAddress = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    for (SidecarReactionEntry entry : snapshot) {
      if (entry == null || !emoji.equals(entry.getEmoji())) continue;
      List<String> users = entry.getAddresses();
      if (users == null) continue;
      for (String user : users) {
        if (user != null
            && user.trim().toLowerCase(Locale.ROOT).equals(normalizedAddress)) {
          return true;
        }
      }
    }
    return false;
  }

  private void recordReactionToggleTelemetry(boolean adding, String outcome) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("compose.reaction_toggled")
              .field("state", adding ? "added" : "removed")
              .field("outcome", outcome)
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
  }

  private void notifyCurrentUserAddress(String address) {
    String normalized = address == null ? "" : address;
    lastKnownAddress = normalized;
    if (currentUserAddressListener == null) return;
    try {
      currentUserAddressListener.onCurrentUserAddress(normalized);
    } catch (Throwable ignored) {
      // Listener errors must not affect compose behavior.
    }
  }

  private void recordTaskToggleTelemetry(boolean completed, String outcome) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("compose.task_toggled")
              .field("state", completed ? "completed" : "open")
              .field("outcome", outcome)
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer behavior.
    }
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

  public void onSelectedWaveComposeContextChanged(
      String selectedWaveId,
      J2clSidecarWriteSession nextWriteSession,
      List<String> participantIds) {
    if (signedOut) {
      return;
    }
    String nextContextId = selectedWaveId == null ? "" : selectedWaveId;
    List<String> nextParticipantIds =
        participantIds == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantIds));
    boolean sameContext = nextContextId.equals(selectedWaveParticipantContextId);
    if (nextContextId.isEmpty()) {
      selectedWaveParticipantContextId = null;
      selectedWaveParticipantIds = Collections.emptyList();
    } else {
      boolean contextChanged =
          selectedWaveParticipantContextId != null
              ? !nextContextId.equals(selectedWaveParticipantContextId)
              : lastSelectedWaveId != null;
      selectedWaveParticipantContextId = nextContextId;
      lastSelectedWaveId = nextContextId;
      if (contextChanged) {
        replyDraft = "";
        replyErrorText = "";
        replyStaleBasis = false;
        replyStaleWaveId = null;
        resetAttachmentState();
      }
      if (!sameContext || !nextParticipantIds.isEmpty() || selectedWaveParticipantIds.isEmpty()) {
        selectedWaveParticipantIds = nextParticipantIds;
      }
    }
    J2clSidecarWriteSession effectiveWriteSession = nextWriteSession;
    if (nextContextId.isEmpty()) {
      effectiveWriteSession = null;
    } else if (effectiveWriteSession != null
        && !nextContextId.equals(effectiveWriteSession.getSelectedWaveId())) {
      effectiveWriteSession = null;
      replySubmitting = false;
      replyErrorText = "";
      replyStaleBasis = false;
      replyStaleWaveId = null;
      resetAttachmentState();
    }
    onWriteSessionChanged(effectiveWriteSession);
  }

  private void submitCreate() {
    if (signedOut || createSubmitting) {
      render();
      return;
    }
    // J-UI-3 (#1081, R-5.1): allow submit when EITHER title or body has text.
    // The user story is "type a title and a message" so a body-only or
    // title-only submission is still a valid create.
    if (createDraft.trim().isEmpty() && createTitleDraft.trim().isEmpty()) {
      createStatusText = "";
      createErrorText = "Enter some text before creating a wave.";
      render();
      return;
    }
    createSubmitting = true;
    createStatusText = "Bootstrapping the root-shell submit session.";
    createErrorText = "";
    final String submittedDraft = createDraft;
    final String submittedTitle = createTitleDraft;
    final int generation = ++createGeneration;
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: stamp the
    // pre-submit search query (via the root shell's hook) BEFORE the
    // bootstrap fetch and server round-trip so the optimistic stub
    // scoping is anchored to the rail visible when the user clicked
    // submit, not the rail visible when the server responds.
    if (preCreateSubmitHook != null) {
      try {
        preCreateSubmitHook.run();
      } catch (RuntimeException ignored) {
        // Hook side-effects are best-effort; never fail the submit because
        // an integration callback misbehaved.
      }
    }
    render();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (generation != createGeneration) {
            // J-UI-3 codex P2 PRRT_kwDOBwxLXs5-DZqM: superseded generation;
            // retire this generation's stamp once. The idempotency set
            // prevents a stale callback from dequeuing a newer
            // submission's stamp after onSignedOut already retired this
            // one.
            retireCreateStampOnce(generation);
            return;
          }
          notifyCurrentUserAddress(bootstrap.getAddress());
          CreateWaveRequest request;
          try {
            request =
                deltaFactory.createWaveRequest(
                    bootstrap.getAddress(),
                    submittedDraft,
                    buildDocument(submittedTitle, submittedDraft, false, ""));
          } catch (RuntimeException e) {
            handleCreateFailure(generation, messageOrDefault(e, "Unable to build the create request."));
            return;
          }
          createStatusText = "Submitting the new wave.";
          render();
          gateway.submit(
              bootstrap,
              request.getSubmitRequest(),
              response -> handleCreateResponse(generation, request, submittedTitle, submittedDraft, response),
              error -> handleCreateFailure(generation, error));
        },
        error -> handleCreateFailure(generation, error));
  }

  private void handleCreateResponse(
      int generation,
      CreateWaveRequest request,
      String submittedTitle,
      String submittedDraft,
      SidecarSubmitResponse response) {
    if (generation != createGeneration) {
      // J-UI-3 codex P2 PRRT_kwDOBwxLXs5-DZqM: superseded; retire this
      // generation's stamp idempotently.
      retireCreateStampOnce(generation);
      return;
    }
    if (!response.getErrorMessage().isEmpty()) {
      handleCreateFailure(generation, response.getErrorMessage());
      return;
    }
    createSubmitting = false;
    // Use the immutable snapshots taken in submitCreate() so that a title
    // edit that arrives during the server round-trip does not corrupt the
    // stub title passed to the optimistic-digest handler.
    String createdTitle = submittedTitle;
    String createdBodyForStub = submittedDraft;
    createDraft = "";
    createTitleDraft = "";
    activeCommandId = "";
    annotationCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    createStatusText = "Wave created.";
    createErrorText = "";
    render();
    if (createSuccessHandler != null) {
      String stubTitle = createdTitle.trim().isEmpty()
          ? firstNonBlankLine(createdBodyForStub)
          : createdTitle.trim();
      createSuccessHandler.onWaveCreated(request.getCreatedWaveId(), stubTitle);
    }
  }

  private void handleCreateFailure(int generation, String error) {
    if (generation != createGeneration) {
      // J-UI-3 codex P2 PRRT_kwDOBwxLXs5-DZqM: superseded; retire this
      // generation's stamp idempotently.
      retireCreateStampOnce(generation);
      return;
    }
    createSubmitting = false;
    createStatusText = "";
    createErrorText = error == null || error.isEmpty() ? "Create wave failed." : error;
    render();
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T + PRRT_kwDOBwxLXs5-DZqM:
    // pair the pre-submit stamp with a failure-time drop, gated by
    // generation so a later stale callback can't double-drop. retire-
    // CreateStampOnce is idempotent per generation.
    {
      retireCreateStampOnce(generation);
    }
  }

  private void submitReply() {
    if (signedOut || replySubmitting) {
      render();
      return;
    }
    if (writeSession == null || isEmpty(writeSession.getSelectedWaveId())) {
      replyStatusText = "";
      replyErrorText =
          hasSelectedWaveContext()
              ? WAITING_FOR_WRITE_SESSION_REPLY_MESSAGE
              : "Open a wave before sending a reply.";
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
    final String submittedAnnotationCommandId = annotationCommandId;
    final int generation = ++replyGeneration;
    final J2clSidecarWriteSession submitSession = writeSession;
    render();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (generation != replyGeneration) {
            return;
          }
          notifyCurrentUserAddress(bootstrap.getAddress());
          SidecarSubmitRequest request;
          try {
            request =
                deltaFactory.createReplyRequest(
                    bootstrap.getAddress(),
                    submitSession,
                    submittedDraft,
                    buildDocument(submittedDraft, true, submittedAnnotationCommandId, true));
          } catch (RuntimeException e) {
            handleReplyFailure(generation, messageOrDefault(e, "Unable to build the reply request."));
            return;
          }
          replyStatusText = "Submitting the reply.";
          render();
          gateway.submit(
              bootstrap,
              request,
              response -> handleReplyResponse(generation, submitSession, request, response),
              error -> handleReplyFailure(generation, error));
        },
        error -> handleReplyFailure(generation, error));
  }

  private void handleReplyResponse(
      int generation,
      J2clSidecarWriteSession submitSession,
      SidecarSubmitRequest request,
      SidecarSubmitResponse response) {
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
    // F-3.S2 (#1038, R-5.3, PR #1066 review thread PRRT_kwDOBwxLXs592RVM):
    // a successful submit consumes pending mention picks; failures
    // preserve the list so a retry submits the same chips.
    pendingMentions.clear();
    // J-UI-5 (#1083): success consumes the structured component list
    // forwarded by the inline composer; failures preserve it so a
    // retry submits the same formatting.
    pendingSubmittedComponents = null;
    // A sent reply closes the attachment batch; failures preserve size and attachments for retry.
    attachmentDisplaySize = J2clAttachmentComposerController.DisplaySize.MEDIUM;
    activeCommandId = "";
    annotationCommandId = "";
    commandStatusText = "";
    commandErrorText = "";
    if (attachmentController != null) {
      // Reuse the controller so reply batches do not rebuild attachment lifecycle state.
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
      replySuccessHandler.onReplySubmitted(
          submitSession.getSelectedWaveId(),
          response.getResultingVersion(),
          request == null ? "" : request.getClientCreatedBlipId());
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
    boolean hasWriteSession = hasSelectedWave(writeSession);
    boolean replyAvailable = !signedOut && (hasWriteSession || hasSelectedWaveContext());
    String renderedReplyStatusText =
        replyAvailable && !hasWriteSession && "Open a wave before replying.".equals(replyStatusText)
            ? ""
            : replyStatusText;
    view.render(
        new J2clComposeSurfaceModel(
            !signedOut,
            createDraft,
            createTitleDraft,
            createSubmitting,
            createStatusText,
            createErrorText,
            replyAvailable,
            hasSelectedWave(writeSession) ? writeSession.getReplyTargetBlipId() : "",
            replyDraft,
            replySubmitting,
            replyStaleBasis,
            renderedReplyStatusText,
            replyErrorText,
            activeCommandId,
            commandStatusText,
            commandErrorText,
            replyAvailable ? participantsForCurrentSelection() : Collections.emptyList()));
  }

  private J2clComposerDocument buildDocument(
      String draftText, boolean includeAttachments, String submittedAnnotationCommandId) {
    return buildDocument("", draftText, includeAttachments, submittedAnnotationCommandId, false);
  }

  /**
   * J-UI-3 (#1081, R-5.1): create-flow overload that prepends the wave title
   * as a {@code conv/title} annotated-text component. {@code titleText} is
   * the trimmed title-input value; empty/null is a no-op so reply-paths that
   * forward an empty title work unchanged.
   */
  private J2clComposerDocument buildDocument(
      String titleText,
      String draftText,
      boolean includeAttachments,
      String submittedAnnotationCommandId) {
    return buildDocument(titleText, draftText, includeAttachments, submittedAnnotationCommandId, false);
  }

  private J2clComposerDocument buildDocument(
      String draftText,
      boolean includeAttachments,
      String submittedAnnotationCommandId,
      boolean includeMentions) {
    return buildDocument("", draftText, includeAttachments, submittedAnnotationCommandId, includeMentions);
  }

  private J2clComposerDocument buildDocument(
      String titleText,
      String draftText,
      boolean includeAttachments,
      String submittedAnnotationCommandId,
      boolean includeMentions) {
    J2clComposerDocument.Builder builder = J2clComposerDocument.builder();
    // J-UI-3: prepend the title annotation when a non-blank title was typed.
    // titleText() is a no-op on null/empty so reply-paths pass through cleanly.
    builder.titleText(titleText);
    // When a title and body text are both present, insert a newline between them so
    // the two runs are not concatenated into a single character sequence in the blip.
    if (titleText != null && !titleText.trim().isEmpty()
        && draftText != null && !draftText.trim().isEmpty()) {
      builder.text("\n");
    }
    // J-UI-5 (#1083, R-5.7): when the inline rich-text composer
    // forwarded a structured component list, build the document
    // straight from it (per-fragment formatting). The components
    // already encode mention chips (mention/user annotations), list
    // and link annotations, and the new inline-format runs (fontWeight
    // / fontStyle / textDecoration), so the legacy single-annotation
    // path is bypassed entirely.
    if (includeMentions
        && pendingSubmittedComponents != null
        && !pendingSubmittedComponents.isEmpty()) {
      for (SubmittedComponent component : pendingSubmittedComponents) {
        if (component == null) continue;
        // J-UI-5 (#1083): the J2clComposerDocument.Builder.annotatedText
        // method rejects whitespace-only text (trim().isEmpty()) with
        // an IllegalArgumentException. A common user flow — bolding a
        // word together with its trailing space — produces an
        // annotated component whose text is " " (a single space),
        // which trims to empty. Downgrade that case to a plain text
        // run so the submit does not throw and tear down the reply.
        if (component.getKind() == SubmittedComponent.Kind.ANNOTATED
            && !component.getText().isEmpty()
            && !component.getText().trim().isEmpty()) {
          List<SubmittedComponent.Annotation> ann = component.getAnnotations();
          List<SubmittedComponent.Annotation> validAnns =
              new ArrayList<SubmittedComponent.Annotation>();
          for (SubmittedComponent.Annotation a : ann) {
            if (a != null && !a.getKey().isEmpty() && !a.getValue().isEmpty()) {
              validAnns.add(a);
            }
          }
          if (!validAnns.isEmpty()) {
            // Codex review #1095 thread PRRT_kwDOBwxLXs5-C84a: use
            // the multi-annotation builder when more than one pair
            // is present so combined bold+italic / bold+link /
            // etc. round-trip through the delta.
            if (validAnns.size() == 1) {
              SubmittedComponent.Annotation only = validAnns.get(0);
              builder.annotatedText(only.getKey(), only.getValue(), component.getText());
            } else {
              List<J2clComposerDocument.KeyValuePair> pairs =
                  new ArrayList<J2clComposerDocument.KeyValuePair>(validAnns.size());
              for (SubmittedComponent.Annotation a : validAnns) {
                pairs.add(new J2clComposerDocument.KeyValuePair(a.getKey(), a.getValue()));
              }
              builder.annotatedTextMulti(pairs, component.getText());
            }
            continue;
          }
        }
        builder.text(component.getText());
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
    // Reply submits pass the snapshotted command id; create submits pass an empty id.
    J2clDailyToolbarAction action = J2clDailyToolbarAction.fromId(submittedAnnotationCommandId);
    String annotationKey = annotationKey(action);
    String annotationValue = annotationValue(action);
    // F-3.S2 (#1038, R-5.3): when mention picks are pending and their chip text
    // occurs in the draft, split into alternating text + `mention/user` components.
    // Only reply submits carry mention annotations; create-wave submits pass
    // includeMentions=false so a mention picked in a reply context can never
    // pollute a concurrent create-wave document that happens to contain the
    // same @DisplayName text.
    boolean appendedMentions = includeMentions && appendMentionedComponents(builder, draftText, annotationKey, annotationValue);
    if (appendedMentions) {
      // No-op: mentions already populated the builder.
    } else if (annotationKey != null
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

  /**
   * F-3.S2 (#1038, R-5.3): emit text + `mention/user` annotated
   * components for each pending mention whose chip text still
   * occurs in {@code draftText}. Returns true when at least one
   * mention was appended (callers then skip the plain-text or
   * toolbar-formatted fallback). Mentions consumed in this pass
   * are not removed from {@link #pendingMentions}; the list is
   * cleared on submit success / wave change / sign-out so a
   * failed submit can retry with the same chip set.
   *
   * <p>Algorithm: sort pending mentions by their first occurrence
   * in {@code draftText} (document order) so that chips inserted
   * out of pick order are serialised correctly. Then walk in
   * document order, emitting alternating text runs and annotated
   * chip spans. Non-mention text runs are emitted via
   * {@link #appendTextRun}: when {@code annotationKey} and
   * {@code annotationValue} are non-null they are wrapped in the
   * active toolbar annotation so surrounding formatted text (e.g.
   * bold, italic) is preserved alongside the mention chips.
   * Mentions whose chip text is not found (e.g. the user deleted
   * the chip via Backspace after picking) are silently skipped.
   */
  private boolean appendMentionedComponents(
      J2clComposerDocument.Builder builder, String draftText,
      String annotationKey, String annotationValue) {
    if (pendingMentions.isEmpty()) return false;
    if (draftText == null || draftText.isEmpty()) return false;
    // Check whether ANY pending mention is still represented in the draft
    // before mutating the builder; if none match (user deleted every chip),
    // fall back to the plain-text path so existing tests keep their shape.
    boolean anyMatch = false;
    for (PendingMention mention : pendingMentions) {
      if (draftText.contains(mention.chipText)) {
        anyMatch = true;
        break;
      }
    }
    if (!anyMatch) return false;
    // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — sort pending
    // mentions by their recorded chip offset (document order) so
    // duplicate display names and `@Name` plain-text duplicates
    // bind to the correct chip on submit. Mentions whose offset is
    // unknown (legacy callers / two-arg overload used in tests)
    // sort to the end and fall back to first-occurrence matching;
    // entries whose chipText no longer appears in the draft are
    // silently skipped (chip deleted on the lit side).
    List<PendingMention> inDocOrder = new ArrayList<>(pendingMentions);
    inDocOrder.sort(
        Comparator.comparingInt(m -> sortKey(m, draftText)));
    int cursor = 0;
    for (PendingMention mention : inDocOrder) {
      int searchStart = cursor;
      if (mention.chipTextOffset >= 0 && mention.chipTextOffset > cursor) {
        // Skip ahead to the chip's recorded position so a plain `@Name`
        // typed before a same-text picked chip is left as plain text
        // rather than swallowing the chip's annotation.
        searchStart = mention.chipTextOffset;
      }
      int idx = draftText.indexOf(mention.chipText, searchStart);
      if (idx < 0) {
        // The recorded offset may be stale (text deleted before the
        // chip on the lit side). Re-search from the running cursor as
        // a last resort to preserve the chip annotation when the
        // chipText still occurs later in the draft.
        if (searchStart > cursor) {
          idx = draftText.indexOf(mention.chipText, cursor);
        }
      }
      if (idx < 0) continue;
      if (idx > cursor) {
        appendTextRun(builder, draftText.substring(cursor, idx), annotationKey, annotationValue);
      }
      builder.annotatedText("mention/user", mention.address, mention.chipText);
      cursor = idx + mention.chipText.length();
    }
    if (cursor < draftText.length()) {
      appendTextRun(builder, draftText.substring(cursor), annotationKey, annotationValue);
    }
    return true;
  }

  private static int sortKey(PendingMention mention, String draftText) {
    if (mention.chipTextOffset >= 0) {
      return mention.chipTextOffset;
    }
    int pos = draftText.indexOf(mention.chipText);
    return pos < 0 ? Integer.MAX_VALUE : pos;
  }

  private static void appendTextRun(
      J2clComposerDocument.Builder builder, String text,
      String annotationKey, String annotationValue) {
    if (annotationKey != null && annotationValue != null) {
      builder.annotatedText(annotationKey, annotationValue, text);
    } else {
      builder.text(text);
    }
  }

  public static DeltaFactory richContentDeltaFactory(String sessionSeed) {
    return richContentDeltaFactory(new J2clRichContentDeltaFactory(sessionSeed));
  }

  public static AttachmentControllerFactory attachmentControllerFactory(String sessionSeed) {
    return attachmentControllerFactory(sessionSeed, () -> new J2clAttachmentUploadClient());
  }

  public static AttachmentControllerFactory attachmentControllerFactory(
      String sessionSeed, J2clClientTelemetry.Sink telemetrySink) {
    return attachmentControllerFactory(
        sessionSeed, () -> new J2clAttachmentUploadClient(), telemetrySink);
  }

  static AttachmentControllerFactory attachmentControllerFactory(
      String sessionSeed, J2clAttachmentUploadClient uploadClient) {
    // Test seam: reuse one injected client so tests can observe all generated upload requests.
    J2clAttachmentUploadClient client =
        requirePresent(uploadClient, "Attachment upload client is required.");
    return attachmentControllerFactory(sessionSeed, () -> client);
  }

  static AttachmentControllerFactory attachmentControllerFactory(
      String sessionSeed, AttachmentUploadClientFactory uploadClientFactory) {
    return attachmentControllerFactory(
        sessionSeed, uploadClientFactory, J2clClientTelemetry.noop());
  }

  static AttachmentControllerFactory attachmentControllerFactory(
      String sessionSeed,
      AttachmentUploadClientFactory uploadClientFactory,
      J2clClientTelemetry.Sink telemetrySink) {
    AttachmentUploadClientFactory clientFactory =
        requirePresent(uploadClientFactory, "Attachment upload client factory is required.");
    J2clClientTelemetry.Sink sink =
        requirePresent(telemetrySink, "Attachment telemetry sink is required.");
    Map<String, J2clAttachmentIdGenerator> idGeneratorsByDomain =
        new HashMap<String, J2clAttachmentIdGenerator>();
    return (waveRef, domain, insertionCallback, stateChangeCallback) -> {
      J2clAttachmentIdGenerator idGenerator =
          attachmentIdGeneratorForDomain(idGeneratorsByDomain, domain, sessionSeed);
      J2clAttachmentUploadClient uploadClient =
          requirePresent(clientFactory.create(), "Attachment upload client is required.");
      return new J2clAttachmentComposerController(
          waveRef, uploadClient, idGenerator, insertionCallback, stateChangeCallback, sink);
    };
  }

  private static J2clAttachmentIdGenerator attachmentIdGeneratorForDomain(
      Map<String, J2clAttachmentIdGenerator> idGeneratorsByDomain,
      String domain,
      String sessionSeed) {
    // Cache by the same trimmed domain that the generator uses for legacy id shape.
    String normalizedDomain = domain == null ? "" : domain.trim();
    J2clAttachmentIdGenerator idGenerator = idGeneratorsByDomain.get(normalizedDomain);
    if (idGenerator == null) {
      idGenerator = new J2clAttachmentIdGenerator(normalizedDomain, sessionSeed);
      idGeneratorsByDomain.put(normalizedDomain, idGenerator);
    }
    return idGenerator;
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

      @Override
      public SidecarSubmitRequest createTaskToggleRequest(
          String address, J2clSidecarWriteSession session, String blipId, boolean completed) {
        return factory.taskToggleRequest(address, session, blipId, completed);
      }

      @Override
      public SidecarSubmitRequest createTaskMetadataRequest(
          String address,
          J2clSidecarWriteSession session,
          String blipId,
          String assigneeAddress,
          String dueDate) {
        return factory.taskMetadataRequest(address, session, blipId, assigneeAddress, dueDate);
      }

      @Override
      public SidecarSubmitRequest createReactionToggleRequest(
          String address,
          J2clSidecarWriteSession session,
          String blipId,
          String emoji,
          List<SidecarReactionEntry> currentSnapshot,
          boolean adding) {
        return factory.reactionToggleRequest(
            address, session, blipId, emoji, currentSnapshot, adding);
      }

      @Override
      public SidecarSubmitRequest createBlipDeleteRequest(
          String address, J2clSidecarWriteSession session, String blipId) {
        return factory.blipDeleteRequest(address, session, blipId);
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

  private void emitRichEditCommand(J2clDailyToolbarAction action, String result) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("richEdit.command.applied")
              .field("commandId", action.id())
              .field("result", result)
              .build());
    } catch (Exception ignored) {
      // Telemetry must never affect composer editing behavior.
    }
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
    // F-3.S2 (#1038, R-5.3): mention picks live alongside attachment
    // state; sign-out and wave-change resets must drop them so a
    // mention picked on wave A cannot leak into a reply on wave B.
    pendingMentions.clear();
    // J-UI-5 (#1083, coderabbit thread PRRT_kwDOBwxLXs5-Gun6): the
    // structured component list mirrors the same lifecycle as the
    // reply draft — when `onWriteSessionChanged` decides to PRESERVE
    // a stale draft so the user can review/retry it, the components
    // snapshot must also survive so the retry does not silently
    // downgrade rich text to plain. Clear only when the draft was
    // also cleared (or never existed).
    if (isEmpty(replyDraft)) {
      pendingSubmittedComponents = null;
    }
    // F-3.S3 (#1038, R-5.5): same rule for reaction snapshots — wave
    // change drops the prior wave's per-blip reaction state.
    reactionSnapshotsByBlip.clear();
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

  private boolean hasSelectedWaveContext() {
    return selectedWaveParticipantContextId != null && !selectedWaveParticipantContextId.isEmpty();
  }

  private List<String> participantsForCurrentSelection() {
    if (!selectedWaveParticipantIds.isEmpty() && hasSelectedWaveContext()) {
      if (writeSession == null
          || selectedWaveParticipantContextId.equals(writeSession.getSelectedWaveId())) {
        return selectedWaveParticipantIds;
      }
    }
    return writeSession == null
        ? Collections.<String>emptyList()
        : writeSession.getParticipantIds();
  }

  /**
   * F-3.S2 (#1068): two write sessions describe the same logical wave
   * when both are non-null and share the same selected wave id. This is
   * looser than reference equality so that no-op session refreshes
   * (same wave, new session object) do not silently drop deferred task
   * writes that captured the prior session reference.
   */
  private static boolean sameLogicalSession(
      J2clSidecarWriteSession captured, J2clSidecarWriteSession current) {
    if (captured == null || current == null) {
      return false;
    }
    return safeEquals(captured.getSelectedWaveId(), current.getSelectedWaveId());
  }

  private static String normalizeDraft(String draft) {
    return draft == null ? "" : draft;
  }

  /**
   * J-UI-3 (#1081, R-5.1): live-edit sanitiser for the title input. Replaces
   * any embedded line break (CR, LF, or any run of them) with a single
   * space so the conv/title annotation never spans more than one line and
   * Windows CRLF pastes do not introduce double spaces. Otherwise
   * preserves the raw input — including trailing whitespace — so users
   * can type multi-word titles normally without each space being eaten
   * between keystrokes (CodeRabbit follow-up on PR #1090).
   */
  private static String sanitizeTitleLive(String title) {
    if (title == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(title.length());
    boolean inBreakRun = false;
    for (int i = 0; i < title.length(); i++) {
      char c = title.charAt(i);
      if (c == '\n' || c == '\r') {
        if (!inBreakRun) {
          out.append(' ');
          inBreakRun = true;
        }
      } else {
        out.append(c);
        inBreakRun = false;
      }
    }
    return out.toString();
  }

  /**
   * J-UI-3 (#1081, R-5.1): submit-time normaliser. Strips leading/trailing
   * whitespace in addition to the live-edit newline sanitisation so the
   * outgoing delta carries a clean single-line title. Called only from the
   * submit path; live edits use {@link #sanitizeTitleLive}.
   */
  private static String normalizeTitleForSubmit(String title) {
    return sanitizeTitleLive(title).trim();
  }

  /**
   * J-UI-3 (#1081, R-5.1): pulls the first non-blank line out of {@code text}
   * for use as an optimistic-stub title fallback when the user submitted a
   * body-only create. Empty/null input yields an empty string so the
   * optimistic-prepend path can fall through to its "(untitled wave)"
   * placeholder.
   */
  private static String firstNonBlankLine(String text) {
    if (text == null) {
      return "";
    }
    int len = text.length();
    int i = 0;
    while (i < len) {
      int lineEnd = i;
      while (lineEnd < len && text.charAt(lineEnd) != '\n' && text.charAt(lineEnd) != '\r') {
        lineEnd++;
      }
      String line = text.substring(i, lineEnd).trim();
      if (!line.isEmpty()) {
        return line;
      }
      i = lineEnd + 1;
    }
    return "";
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

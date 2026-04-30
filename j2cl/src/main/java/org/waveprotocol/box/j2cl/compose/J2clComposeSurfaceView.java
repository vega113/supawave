package org.waveprotocol.box.j2cl.compose;

import elemental2.core.JsArray;
import elemental2.core.JsDate;
import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.File;
import elemental2.dom.FileList;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLTextAreaElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

/**
 * J2CL view that wires the compose-controller events to the lit
 * primitives.
 *
 * F-3.S1 (#1038, R-5.1) extends the legacy view with an inline
 * `<wavy-composer>` mounted at the originating `<wave-blip>` whenever
 * the user clicks Reply / Edit. The `<composer-inline-reply>` element
 * remains the single source of truth for reply state (what the
 * controller renders into); the inline composer is a paint-only mirror
 * that:
 *
 * - mirrors property updates from the legacy element on every render,
 * - re-fires user input events (draft-change, reply-submit,
 *   attachment-paste-image, composer-focus-request) so the controller
 *   sees the same events regardless of which composer surface the user
 *   actually typed into,
 * - listens for `wave-blip-reply-requested` / `wave-blip-edit-requested`
 *   CustomEvents from F-2's `<wave-blip>` toolbar and mounts the inline
 *   composer at the originating blip,
 * - listens for `wavy-composer-cancelled` to remove the inline composer
 *   (the legacy reply target stays unchanged).
 *
 * This keeps S1's blast radius bounded: the controller, model, and
 * existing tests stay unchanged. Subsequent slices migrate the
 * controller to per-composer state (S4 closeout) once mention/task/
 * reaction wiring is in place.
 */
public final class J2clComposeSurfaceView implements J2clComposeSurfaceController.View {
  // J-UI-3 (#1081, R-5.1): the title input precedes the body textarea inside
  // the create form. Single-line input with Enter-to-submit semantics so a
  // user who types just a title can ship the wave without reaching for the
  // mouse.
  private final HTMLInputElement createTitleInput;
  private final HTMLTextAreaElement createInput;
  private final HTMLElement createSubmit;
  private final HTMLElement replyElement;
  private final HTMLInputElement attachmentInput;
  private J2clComposeSurfaceController.Listener listener;
  private final Map<String, HTMLElement> inlineComposers = new HashMap<>();
  private String activeInlineComposerKey = "";
  // J-UI-5 (#1083): captured at construction time from `<shell-root
  // data-j2cl-inline-rich-composer="true">`. When false, the view does
  // not register the body-level wave-blip-reply-requested listeners,
  // so the legacy <composer-inline-reply> textarea remains the only
  // composer surface.
  private final boolean inlineRichComposerEnabled;
  // F-3.S3 (#1038, R-5.5): currently open reaction picker / authors
  // popover instances, mounted body-level so the read renderer's
  // surface rebuild does not tear them down. Keyed by blip id.
  private HTMLElement activeReactionPicker;
  private String activeReactionPickerBlipId = "";
  private HTMLElement activeReactionAuthorsPopover;
  // F-3.S3: GWT-parity reaction emoji set, matching
  // org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionPickerPopup.EMOJI_OPTIONS.
  static final String[] DEFAULT_REACTION_EMOJIS = {
    "👍", "❤️", "😂", "🎉", "😮", "👀"
  };

  // F-3.S4 (#1038, R-5.6 F.6): every wavy-confirm-requested event the
  // view dispatches for a blip-delete is keyed by this prefix so the
  // resolved-listener can extract the original blip id and route only
  // delete-confirmations through onDeleteBlipRequested.
  static final String BLIP_DELETE_REQUEST_PREFIX = "wavy-blip-delete:";

  public J2clComposeSurfaceView(HTMLElement createHost, HTMLElement replyHost) {
    createHost.innerHTML = "";
    replyHost.innerHTML = "";

    HTMLElement shell = (HTMLElement) DomGlobal.document.createElement("composer-shell");
    createHost.appendChild(shell);

    HTMLFormElement createForm = (HTMLFormElement) DomGlobal.document.createElement("form");
    createForm.setAttribute("slot", "create");
    createForm.className = "j2cl-compose-create-form";
    shell.appendChild(createForm);

    // J-UI-3 (#1081, R-5.1): single-line title input above the body textarea.
    // Both inputs are labeled and reachable per the R-5.1 a11y clauses. Note
    // we declare the body textarea first so the title-input's Enter handler
    // can reference it without tripping definite-assignment.
    createInput = (HTMLTextAreaElement) DomGlobal.document.createElement("textarea");
    createInput.setAttribute("aria-label", "New wave content");
    createInput.setAttribute("placeholder", "Start a new wave");
    createInput.rows = 4;
    createInput.oninput =
        event -> {
          if (listener != null) {
            listener.onCreateDraftChanged(createInput.value);
          }
          return null;
        };

    createTitleInput = (HTMLInputElement) DomGlobal.document.createElement("input");
    createTitleInput.type = "text";
    createTitleInput.className = "j2cl-compose-create-title";
    createTitleInput.setAttribute("aria-label", "New wave title");
    createTitleInput.setAttribute("placeholder", "Title");
    createTitleInput.setAttribute("autocomplete", "off");
    createTitleInput.setAttribute("maxlength", "240");
    createTitleInput.oninput =
        event -> {
          if (listener != null) {
            listener.onCreateTitleChanged(createTitleInput.value);
          }
          return null;
        };
    createTitleInput.onkeydown =
        event -> {
          JsPropertyMap<?> eventProps = Js.asPropertyMap(event);
          String key = String.valueOf(eventProps.get("key"));
          boolean shiftKey = Boolean.TRUE.equals(eventProps.get("shiftKey"));
          boolean isComposing = Boolean.TRUE.equals(eventProps.get("isComposing"));
          if (!("Enter".equals(key)) || shiftKey || isComposing) {
            return null;
          }
          event.preventDefault();
          if (listener != null) {
            listener.onCreateSubmittedWithTitle(createTitleInput.value, createInput.value);
          }
          return null;
        };
    // J-UI-3: title appears first in DOM order so the user reaches it before
    // the body textarea on Tab/initial focus.
    createForm.appendChild(createTitleInput);
    createForm.appendChild(createInput);

    createSubmit = (HTMLElement) DomGlobal.document.createElement("composer-submit-affordance");
    setProperty(createSubmit, "label", "Create wave");
    createForm.appendChild(createSubmit);
    createSubmit.addEventListener(
        "submit-affordance",
        event -> {
          if (listener != null) {
            listener.onCreateSubmittedWithTitle(createTitleInput.value, createInput.value);
          }
        });

    // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-NhCf):
    // read the inline-rich-composer flag BEFORE binding any listeners
    // on the legacy `<composer-inline-reply>` element. When the flag
    // is on, the inline `<wavy-composer>` is the SOLE composer
    // surface — we must not also bind the legacy `reply-submit`
    // path, otherwise a draft typed into the textarea would bypass
    // component-based serialization and submit as plain text.
    inlineRichComposerEnabled = readInlineRichComposerFlag();

    replyElement = (HTMLElement) DomGlobal.document.createElement("composer-inline-reply");
    replyHost.appendChild(replyElement);
    if (!inlineRichComposerEnabled) {
      replyElement.addEventListener(
          "draft-change",
          event -> {
            if (listener != null) {
              listener.onReplyDraftChanged(eventDetailValue(event));
            }
          });
      replyElement.addEventListener(
          "reply-submit",
          event -> {
            if (listener != null) {
              listener.onReplySubmitted(propertyString(replyElement, "draft"));
            }
          });
      replyElement.addEventListener(
          "attachment-paste-image",
          event -> {
            if (listener != null) {
              listener.onPastedImage(eventDetailProperty(event, "file"));
            }
          });
    }

    attachmentInput = (HTMLInputElement) DomGlobal.document.createElement("input");
    attachmentInput.type = "file";
    attachmentInput.multiple = true;
    attachmentInput.setAttribute("aria-label", "Choose files to attach");
    attachmentInput.setAttribute("hidden", "");
    // Keep the programmatic picker from joining any ancestor form submission.
    attachmentInput.setAttribute("form", "j2cl-detached-attachment-picker");
    replyHost.appendChild(attachmentInput);
    attachmentInput.onchange =
        event -> {
          if (listener != null) {
            listener.onAttachmentFilesSelected(fileSelections(attachmentInput.files));
          }
          attachmentInput.value = "";
          return null;
        };

    // F-3.S1: listen for inline-composer requests from F-2's <wave-blip>.
    if (inlineRichComposerEnabled) {
      DomGlobal.document.body.addEventListener(
          "wave-blip-reply-requested",
          event -> openInlineComposer(eventDetailString(event, "blipId"), "reply"));
      DomGlobal.document.body.addEventListener(
          "wave-blip-edit-requested",
          event -> openInlineComposer(eventDetailString(event, "blipId"), "edit"));
      DomGlobal.document.body.addEventListener(
          "wave-root-reply-requested",
          event -> openInlineComposer("", "wave-root"));
      DomGlobal.document.body.addEventListener(
          "wavy-composer-cancelled",
          event -> closeInlineComposer(eventDetailString(event, "replyTargetBlipId")));
    }

    // F-3.S2 (#1038): mention popover + per-blip task affordance events.
    DomGlobal.document.body.addEventListener(
        "wave-blip-task-toggled",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          boolean completed = eventDetailBoolean(event, "completed");
          int bodyItemCount = eventDetailInt(event, "bodySize", 0);
          listener.onTaskToggled(blipId, completed, bodyItemCount);
        });
    DomGlobal.document.body.addEventListener(
        "wave-blip-task-metadata-changed",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          String assignee = eventDetailString(event, "assigneeAddress");
          String due = dueDateToEpochMs(eventDetailString(event, "dueDate"));
          int bodyItemCount = eventDetailInt(event, "bodySize", 0);
          listener.onTaskMetadataChanged(blipId, assignee, due, bodyItemCount);
        });
    DomGlobal.document.body.addEventListener(
        "wavy-composer-mention-picked",
        event -> {
          if (listener == null) return;
          String address = eventDetailString(event, "address");
          String displayName = eventDetailString(event, "displayName");
          // PR #1066 review thread PRRT_kwDOBwxLXs593gTR: forward
          // chip-text offset so the controller binds picked mentions
          // by position rather than first-text-occurrence.
          int chipTextOffset = eventDetailInt(event, "chipTextOffset", -1);
          listener.onMentionPicked(address, displayName, chipTextOffset);
        });
    DomGlobal.document.body.addEventListener(
        "wavy-composer-mention-abandoned",
        event -> {
          if (listener == null) return;
          listener.onMentionAbandoned();
        });

    // F-3.S3 (#1038, R-5.5): reaction events from the per-blip
    // <reaction-row> + <reaction-picker-popover>. Both `reaction-pick`
    // (from the picker) and `reaction-toggle` (from a chip) route to
    // the same controller listener — the controller decides
    // adding-vs-removing from its cached snapshot. `reaction-add`
    // mounts a <reaction-picker-popover> next to the row;
    // `reaction-inspect` mounts a <reaction-authors-popover>.
    DomGlobal.document.body.addEventListener(
        "reaction-pick",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          String emoji = eventDetailString(event, "emoji");
          listener.onReactionToggled(blipId, emoji);
          closeReactionPicker(blipId);
        });
    DomGlobal.document.body.addEventListener(
        "reaction-toggle",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          String emoji = eventDetailString(event, "emoji");
          listener.onReactionToggled(blipId, emoji);
        });
    DomGlobal.document.body.addEventListener(
        "reaction-add",
        event -> {
          String blipId = eventDetailString(event, "blipId");
          openReactionPicker(blipId);
        });
    DomGlobal.document.body.addEventListener(
        "reaction-inspect",
        event -> {
          String blipId = eventDetailString(event, "blipId");
          String emoji = eventDetailString(event, "emoji");
          openReactionAuthorsPopover(blipId, emoji);
        });
    DomGlobal.document.body.addEventListener(
        "overlay-close",
        event -> {
          // The picker / authors popover both emit overlay-close on
          // Escape or outside-click; we drop the open instance so the
          // next `reaction-add` can mount fresh.
          closeReactionPicker(null);
          closeReactionAuthorsPopover();
        });

    // F-3.S4 (#1038, R-5.6 step 1): listen for drag-drop file payloads
    // emitted by `<wavy-composer>` when the user drops files on the
    // composer body. Routed through `Listener.onDroppedFiles` so the
    // controller can record drop-specific telemetry distinct from the
    // explicit-picker path.
    DomGlobal.document.body.addEventListener(
        "wavy-composer-attachment-dropped",
        event -> {
          if (listener == null) return;
          List<J2clComposeSurfaceController.AttachmentFileSelection> dropped =
              fileSelectionsFromDroppedEvent(event);
          listener.onDroppedFiles(dropped);
        });

    // F-3.S4 (#1038, R-5.6 F.6): listen for blip-delete requests from
    // the per-blip toolbar. Each request triggers the wavy confirm
    // dialog (no Window.confirm per project memory). When the user
    // confirms, we route through `Listener.onDeleteBlipRequested`.
    ensureWavyConfirmDialogMounted();
    DomGlobal.document.body.addEventListener(
        "wave-blip-delete-requested",
        event -> {
          String blipId = eventDetailString(event, "blipId");
          if (blipId == null || blipId.trim().isEmpty()) return;
          String waveId = eventDetailString(event, "waveId");
          int bodyItemCount = eventDetailInt(event, "bodySize", 0);
          requestBlipDeleteConfirmation(blipId, waveId, bodyItemCount);
        });
    DomGlobal.document.body.addEventListener(
        "wavy-confirm-resolved",
        event -> {
          String requestId = eventDetailString(event, "requestId");
          boolean confirmed = eventDetailBoolean(event, "confirmed");
          if (requestId == null || !requestId.startsWith(BLIP_DELETE_REQUEST_PREFIX)) return;
          String suffix = requestId.substring(BLIP_DELETE_REQUEST_PREFIX.length());
          int sep = suffix.indexOf('|');
          String blipId = sep >= 0 ? suffix.substring(0, sep) : suffix;
          String rest = sep >= 0 ? suffix.substring(sep + 1) : "";
          int secondSep = rest.indexOf('|');
          String expectedWaveId = secondSep >= 0 ? rest.substring(0, secondSep) : rest;
          int bodyItemCount =
              secondSep >= 0 ? parseInt(rest.substring(secondSep + 1), 0) : 0;
          if (confirmed && listener != null && !blipId.isEmpty()) {
            listener.onDeleteBlipRequested(blipId, expectedWaveId, bodyItemCount);
          }
        });
  }

  @Override
  public void bind(J2clComposeSurfaceController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clComposeSurfaceModel model) {
    // J-UI-3 (#1081, R-5.1): only assign input values when they actually differ
    // to avoid clobbering the caret position while the user is typing.
    String modelTitle = model.getCreateTitleDraft();
    if (!String.valueOf(createTitleInput.value).equals(modelTitle)) {
      createTitleInput.value = modelTitle;
    }
    createTitleInput.disabled = !model.isCreateEnabled() || model.isCreateSubmitting();
    String modelDraft = model.getCreateDraft();
    if (!String.valueOf(createInput.value).equals(modelDraft)) {
      createInput.value = modelDraft;
    }
    createInput.disabled = !model.isCreateEnabled() || model.isCreateSubmitting();
    setProperty(createSubmit, "busy", model.isCreateSubmitting());
    setProperty(createSubmit, "disabled", !model.isCreateEnabled() || model.isCreateSubmitting());
    setProperty(createSubmit, "status", model.getCreateStatusText());
    setProperty(createSubmit, "error", model.getCreateErrorText());

    // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-NhCf):
    // when the rich-composer flag is on the legacy textarea must
    // never paint — the inline `<wavy-composer>` is the SOLE composer
    // surface. `<composer-inline-reply>` honours `:host(:not([available]))
    // { display: none }`, so forcing available=false hides the legacy
    // element regardless of the model's reply-available signal.
    setProperty(
        replyElement,
        "available",
        !inlineRichComposerEnabled && model.isReplyAvailable());
    setProperty(replyElement, "targetLabel", model.getReplyTargetLabel());
    setProperty(replyElement, "draft", model.getReplyDraft());
    setProperty(replyElement, "submitting", model.isReplySubmitting());
    setProperty(replyElement, "staleBasis", model.isReplyStaleBasis());
    setProperty(replyElement, "status", model.getReplyStatusText());
    setProperty(replyElement, "error", model.getReplyErrorText());
    setProperty(replyElement, "activeCommand", model.getActiveCommandId());
    setProperty(replyElement, "commandStatus", model.getCommandStatusText());
    setProperty(replyElement, "commandError", model.getCommandErrorText());
    setProperty(replyElement, "participants", buildParticipantsArray(model.getParticipantAddresses()));

    // Mirror reply state only into the currently active inline composer.
    // The model carries a single reply/edit target and draft, so pushing
    // that state into every mounted inline composer can make stale
    // composers display and submit against the wrong target.
    HTMLElement composer = activeInlineComposer();
    if (composer != null) {
      mirrorComposerState(composer, model);
    }
  }

  @Override
  public void openAttachmentPicker() {
    attachmentInput.click();
  }

  @Override
  public void focusReplyComposer() {
    HTMLElement target = activeInlineComposer();
    if (target != null) {
      target.dispatchEvent(new Event("composer-focus-request"));
      return;
    }
    replyElement.dispatchEvent(new Event("composer-focus-request"));
  }

  @Override
  public void focusCreateSurface() {
    // J-UI-3 (#1081, R-5.1): the rail's New Wave button drops the user into
    // the title input; if the input is disabled (signed out / submitting)
    // skip silently rather than throwing.
    if (!createTitleInput.disabled) {
      createTitleInput.focus();
    }
  }

  @Override
  public void focusCreateComposer() {
    // #1076: the keyboard shortcut drops the user straight into the body
    // composer and scrolls it into view without changing the button/title path.
    if (!createInput.disabled) {
      createInput.scrollIntoView();
      createInput.focus();
    }
  }

  /** F-3.S1 entrypoint: mount a `<wavy-composer>` inline at the originating blip. */
  private void openInlineComposer(String blipId, String mode) {
    String key = blipId == null ? "" : blipId;
    if (inlineComposers.containsKey(key)) {
      HTMLElement cached = inlineComposers.get(key);
      if (cached.isConnected) {
        // Already mounted; refresh the requested mode (Reply <-> Edit on
        // the same blip must surface the new verb in the host chip and in
        // emitted event details) and re-focus.
        cached.setAttribute("mode", mode);
        setProperty(cached, "mode", mode);
        cached.dispatchEvent(new Event("composer-focus-request"));
        activeInlineComposerKey = key;
        return;
      }
      // Cached entry is detached (blip DOM was rebuilt); evict and remount.
      inlineComposers.remove(key);
      if (key.equals(activeInlineComposerKey)) {
        activeInlineComposerKey = "";
      }
    }
    // Enforce single active inline composer. Close any previous active one
    // (including the wave-root composer keyed by "") before mounting a new
    // one for a different target. Using `containsKey` instead of an
    // emptiness check keeps the wave-root composer (priorKey == "") from
    // slipping past the guard while still skipping the no-op case where
    // there is no prior composer at all.
    String priorKey = activeInlineComposerKey;
    if (!priorKey.equals(key) && inlineComposers.containsKey(priorKey)) {
      closeInlineComposer(priorKey);
    }
    HTMLElement composer = (HTMLElement) DomGlobal.document.createElement("wavy-composer");
    composer.setAttribute("data-inline-composer", "true");
    composer.setAttribute("reply-target-blip-id", key);
    composer.setAttribute("mode", mode);
    setProperty(composer, "available", true);
    composer.addEventListener(
        "draft-change",
        event -> {
          if (listener != null) {
            listener.onReplyDraftChanged(eventDetailValue(event));
          }
        });
    composer.addEventListener(
        "reply-submit",
        event -> {
          if (listener == null) return;
          // J-UI-5 (#1083, R-5.7): the inline composer carries a
          // per-fragment component list on submit. When present and
          // non-empty, route through onReplySubmittedWithComponents so
          // the controller preserves the user's formatting; otherwise
          // fall through to the plain-text path.
          List<J2clComposeSurfaceController.SubmittedComponent> components =
              decodeSubmittedComponents(event);
          if (components.isEmpty()) {
            listener.onReplySubmitted(propertyString(composer, "draft"));
          } else {
            listener.onReplySubmittedWithComponents(components);
          }
        });
    composer.addEventListener(
        "attachment-paste-image",
        event -> {
          if (listener != null) {
            listener.onPastedImage(eventDetailProperty(event, "file"));
          }
        });

    // Mount a <wavy-format-toolbar> into the composer's toolbar slot and wire
    // selection-change events so the toolbar tracks the active composer.
    HTMLElement formatToolbar = (HTMLElement) DomGlobal.document.createElement("wavy-format-toolbar");
    formatToolbar.setAttribute("slot", "toolbar");
    composer.appendChild(formatToolbar);
    composer.addEventListener(
        "wavy-composer-selection-change",
        event -> setProperty(formatToolbar, "selectionDescriptor", eventDetail(event)));

    HTMLElement mountPoint = locateInlineMountPoint(key);
    if (mountPoint != null) {
      mountPoint.appendChild(composer);
    } else {
      // Fall back to the reply host if the blip is not currently
      // rendered (e.g. a wave-root reply or out-of-viewport blip).
      replyElement.parentNode.appendChild(composer);
    }
    inlineComposers.put(key, composer);
    activeInlineComposerKey = key;
    // Project current model state onto the new composer.
    mirrorComposerStateFromReplyElement(composer);
    composer.dispatchEvent(new Event("composer-focus-request"));
  }

  private void closeInlineComposer(String blipId) {
    String key = blipId == null ? "" : blipId;
    HTMLElement composer = inlineComposers.remove(key);
    if (composer != null && composer.parentNode != null) {
      composer.parentNode.removeChild(composer);
    }
    if (key.equals(activeInlineComposerKey)) {
      activeInlineComposerKey = "";
    }
  }

  private HTMLElement activeInlineComposer() {
    return inlineComposers.get(activeInlineComposerKey);
  }

  private HTMLElement locateInlineMountPoint(String blipId) {
    if (blipId == null || blipId.isEmpty()) {
      return null;
    }
    return (HTMLElement)
        DomGlobal.document.querySelector("wave-blip[data-blip-id=\"" + blipId + "\"]");
  }

  private void mirrorComposerState(HTMLElement composer, J2clComposeSurfaceModel model) {
    setProperty(composer, "available", model.isReplyAvailable());
    setProperty(composer, "targetLabel", model.getReplyTargetLabel());
    setProperty(composer, "draft", model.getReplyDraft());
    setProperty(composer, "submitting", model.isReplySubmitting());
    setProperty(composer, "staleBasis", model.isReplyStaleBasis());
    setProperty(composer, "status", model.getReplyStatusText());
    setProperty(composer, "error", model.getReplyErrorText());
    setProperty(composer, "activeCommand", model.getActiveCommandId());
    setProperty(composer, "commandStatus", model.getCommandStatusText());
    setProperty(composer, "commandError", model.getCommandErrorText());
    setProperty(composer, "participants", buildParticipantsArray(model.getParticipantAddresses()));
  }

  private void mirrorComposerStateFromReplyElement(HTMLElement composer) {
    setProperty(composer, "targetLabel", propertyString(replyElement, "targetLabel"));
    setProperty(composer, "draft", propertyString(replyElement, "draft"));
    setProperty(composer, "submitting", propertyBoolean(replyElement, "submitting"));
    setProperty(composer, "staleBasis", propertyBoolean(replyElement, "staleBasis"));
    setProperty(composer, "status", propertyString(replyElement, "status"));
    setProperty(composer, "error", propertyString(replyElement, "error"));
    setProperty(composer, "activeCommand", propertyString(replyElement, "activeCommand"));
    setProperty(composer, "commandStatus", propertyString(replyElement, "commandStatus"));
    setProperty(composer, "commandError", propertyString(replyElement, "commandError"));
    // G-PORT-5 (#1114): participants must mirror onto a freshly mounted
    // inline composer at construction time, otherwise the user sees an
    // empty popover when typing `@` in the gap between mount and the
    // controller's next full render() — the gap most users notice
    // because the inline composer is opened mid-render. The reply
    // element carries the canonical participants list (set in render()
    // above); forwarding it here makes the inline composer mention-
    // ready from its first paint.
    Object participants = property(replyElement, "participants");
    if (participants != null) {
      setProperty(composer, "participants", participants);
    }
  }

  /**
   * F-3.S3 (#1038, R-5.5): mount a `<reaction-picker-popover>` next to
   * the originating blip's reaction row. Body-level mount keeps the
   * popover alive across the read-renderer's surface rebuilds. The
   * picker emits `reaction-pick` (handled at body level) and
   * `overlay-close` on Escape / outside-click.
   */
  private void openReactionPicker(String blipId) {
    if (blipId == null || blipId.trim().isEmpty()) return;
    String key = blipId;
    closeReactionPicker(null);
    HTMLElement picker =
        (HTMLElement) DomGlobal.document.createElement("reaction-picker-popover");
    picker.setAttribute("data-j2cl-reaction-picker", "true");
    picker.setAttribute("blip-id", key);
    setProperty(picker, "blipId", key);
    setProperty(picker, "emojis", buildEmojiArray(DEFAULT_REACTION_EMOJIS));
    setProperty(picker, "open", true);
    DomGlobal.document.body.appendChild(picker);
    activeReactionPicker = picker;
    activeReactionPickerBlipId = key;
  }

  private void closeReactionPicker(String blipIdOrNull) {
    if (activeReactionPicker == null) {
      return;
    }
    if (blipIdOrNull != null
        && !blipIdOrNull.isEmpty()
        && !blipIdOrNull.equals(activeReactionPickerBlipId)) {
      // Picker was opened for a different blip; leave it.
      return;
    }
    if (activeReactionPicker.parentNode != null) {
      activeReactionPicker.parentNode.removeChild(activeReactionPicker);
    }
    activeReactionPicker = null;
    activeReactionPickerBlipId = "";
  }

  /**
   * F-3.S3 (#1038, R-5.5): mount a `<reaction-authors-popover>` for an
   * inspect click on a chip. The popover's authors property is
   * populated with `{address, displayName}` records derived from the
   * existing participant list — display-name resolution falls back to
   * the address when no friendly name is known.
   */
  private void openReactionAuthorsPopover(String blipId, String emoji) {
    closeReactionAuthorsPopover();
    if (blipId == null || blipId.isEmpty() || emoji == null || emoji.isEmpty()) {
      return;
    }
    List<String> addresses = Collections.<String>emptyList();
    if (listener != null) {
      List<SidecarReactionEntry> snapshot = listener.getReactionSnapshot(blipId);
      if (snapshot == null) {
        snapshot = Collections.<SidecarReactionEntry>emptyList();
      }
      for (SidecarReactionEntry entry : snapshot) {
        if (entry != null && emoji.equals(entry.getEmoji()) && entry.getAddresses() != null) {
          addresses = entry.getAddresses();
          break;
        }
      }
    }
    HTMLElement popover =
        (HTMLElement) DomGlobal.document.createElement("reaction-authors-popover");
    popover.setAttribute("data-j2cl-reaction-authors", "true");
    popover.setAttribute("blip-id", blipId);
    popover.setAttribute("emoji", emoji);
    setProperty(popover, "emoji", emoji);
    setProperty(popover, "reactionLabel", emoji + " reactions");
    setProperty(popover, "authors", buildParticipantsArray(addresses));
    setProperty(popover, "open", true);
    DomGlobal.document.body.appendChild(popover);
    activeReactionAuthorsPopover = popover;
  }

  private void closeReactionAuthorsPopover() {
    if (activeReactionAuthorsPopover == null) {
      return;
    }
    if (activeReactionAuthorsPopover.parentNode != null) {
      activeReactionAuthorsPopover.parentNode.removeChild(activeReactionAuthorsPopover);
    }
    activeReactionAuthorsPopover = null;
  }

  private static JsArray<Object> buildEmojiArray(String[] emojis) {
    JsArray<Object> arr = JsArray.of();
    for (String emoji : emojis) {
      arr.push(emoji);
    }
    return arr;
  }

  private static JsArray<Object> buildParticipantsArray(List<String> addresses) {
    JsArray<Object> arr = JsArray.of();
    for (String address : addresses) {
      JsPropertyMap<Object> entry = JsPropertyMap.of();
      entry.set("address", address);
      entry.set("displayName", address);
      arr.push(entry);
    }
    return arr;
  }

  private static void setProperty(HTMLElement element, String name, Object value) {
    Js.asPropertyMap(element).set(name, value);
  }

  private static String propertyString(HTMLElement element, String name) {
    Object value = Js.asPropertyMap(element).get(name);
    return value == null ? "" : String.valueOf(value);
  }

  /** G-PORT-5 (#1114): raw property accessor for non-string values such
   * as the participants array carried on the reply element. */
  private static Object property(HTMLElement element, String name) {
    return Js.asPropertyMap(element).get(name);
  }

  private static boolean propertyBoolean(HTMLElement element, String name) {
    Object value = Js.asPropertyMap(element).get(name);
    if (value == null) return false;
    if (value instanceof Boolean) return (Boolean) value;
    return "true".equals(String.valueOf(value));
  }

  private static String eventDetailValue(Event event) {
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return "";
    }
    Object value = Js.asPropertyMap(detail).get("value");
    return value == null ? "" : String.valueOf(value);
  }

  private static String eventDetailString(Event event, String key) {
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return "";
    }
    Object value = Js.asPropertyMap(detail).get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static int eventDetailInt(Event event, String key, int fallback) {
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return fallback;
    }
    Object value = Js.asPropertyMap(detail).get(key);
    if (value == null) return fallback;
    if (value instanceof Number) return ((Number) value).intValue();
    String stringValue = String.valueOf(value);
    if (stringValue.isEmpty()) return fallback;
    try {
      return (int) Math.floor(Double.parseDouble(stringValue));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static boolean eventDetailBoolean(Event event, String key) {
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return false;
    }
    Object value = Js.asPropertyMap(detail).get(key);
    if (value == null) return false;
    if (value instanceof Boolean) return (Boolean) value;
    return "true".equals(String.valueOf(value));
  }

  private static Object eventDetailProperty(Event event, String propertyName) {
    Object detail = Js.asPropertyMap(event).get("detail");
    return detail == null ? null : Js.asPropertyMap(detail).get(propertyName);
  }

  private static Object eventDetail(Event event) {
    return Js.asPropertyMap(event).get("detail");
  }

  /**
   * J-UI-5 (#1083): read the `data-j2cl-inline-rich-composer` attribute
   * the SSR emits on `<shell-root>` based on the per-viewer flag value.
   * Returns false when the attribute is absent (default-off), or when
   * the shell element is missing entirely (sidecar / parity-test
   * surfaces that do not paint a `<shell-root>`).
   */
  private static boolean readInlineRichComposerFlag() {
    Object shell =
        DomGlobal.document.querySelector(
            "shell-root[data-j2cl-inline-rich-composer=\"true\"]");
    return shell != null;
  }

  /**
   * J-UI-5 (#1083, R-5.7): decode the {@code detail.components} JS
   * array forwarded by `<wavy-composer>` on `reply-submit`. Each
   * component is `{type, text, annotationKey?, annotationValue?}`.
   * Unknown / malformed entries are skipped so a future schema bump
   * cannot wedge the submit path.
   */
  private static List<J2clComposeSurfaceController.SubmittedComponent> decodeSubmittedComponents(
      Event event) {
    List<J2clComposeSurfaceController.SubmittedComponent> result =
        new ArrayList<J2clComposeSurfaceController.SubmittedComponent>();
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) return result;
    Object componentsObj = Js.asPropertyMap(detail).get("components");
    if (componentsObj == null) return result;
    JsArray<?> jsComponents = Js.cast(componentsObj);
    int len = jsComponents.length;
    for (int i = 0; i < len; i++) {
      Object item = jsComponents.getAt(i);
      if (item == null) continue;
      JsPropertyMap<Object> map = Js.cast(item);
      Object typeObj = map.get("type");
      Object textObj = map.get("text");
      String type = typeObj == null ? "" : String.valueOf(typeObj);
      String text = textObj == null ? "" : String.valueOf(textObj);
      if ("annotated".equals(type)) {
        // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84a):
        // multi-annotation runs forward an `annotations` array of
        // {key, value} pairs. Singular `annotationKey` /
        // `annotationValue` fields stay as a back-compat fallback for
        // single-annotation runs.
        List<J2clComposeSurfaceController.SubmittedComponent.Annotation> parsed =
            new ArrayList<J2clComposeSurfaceController.SubmittedComponent.Annotation>();
        Object annsObj = map.get("annotations");
        if (annsObj != null) {
          JsArray<?> annsArr = Js.cast(annsObj);
          int annsLen = annsArr.length;
          for (int j = 0; j < annsLen; j++) {
            Object annItem = annsArr.getAt(j);
            if (annItem == null) continue;
            JsPropertyMap<Object> annMap = Js.cast(annItem);
            Object kObj = annMap.get("key");
            Object vObj = annMap.get("value");
            String k = kObj == null ? "" : String.valueOf(kObj);
            String v = vObj == null ? "" : String.valueOf(vObj);
            if (!k.isEmpty() && !v.isEmpty()) {
              parsed.add(
                  new J2clComposeSurfaceController.SubmittedComponent.Annotation(k, v));
            }
          }
        }
        if (parsed.isEmpty()) {
          Object keyObj = map.get("annotationKey");
          Object valueObj = map.get("annotationValue");
          String key = keyObj == null ? "" : String.valueOf(keyObj);
          String value = valueObj == null ? "" : String.valueOf(valueObj);
          if (!key.isEmpty() && !value.isEmpty()) {
            parsed.add(
                new J2clComposeSurfaceController.SubmittedComponent.Annotation(key, value));
          }
        }
        if (text.isEmpty() || parsed.isEmpty()) {
          if (!text.isEmpty()) {
            result.add(J2clComposeSurfaceController.SubmittedComponent.text(text));
          }
          continue;
        }
        result.add(
            J2clComposeSurfaceController.SubmittedComponent.annotatedMulti(text, parsed));
      } else if ("text".equals(type)) {
        if (!text.isEmpty()) {
          result.add(J2clComposeSurfaceController.SubmittedComponent.text(text));
        }
      }
    }
    return result;
  }

  /**
   * Converts a YYYY-MM-DD date string (from an HTML date input) to a UTC midnight epoch-millis
   * string so that the written task/dueTs annotation is a numeric timestamp that
   * J2clInteractionBlipModel#parseLong can round-trip.  Returns "" for blank or unparseable input.
   */
  private static String dueDateToEpochMs(String yyyyMmDd) {
    if (yyyyMmDd == null || yyyyMmDd.trim().isEmpty()) {
      return "";
    }
    double ms = new JsDate(yyyyMmDd.trim()).getTime();
    if (Double.isNaN(ms)) {
      return "";
    }
    return String.valueOf((long) ms);
  }

  private static List<J2clComposeSurfaceController.AttachmentFileSelection> fileSelections(
      FileList files) {
    List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>();
    if (files == null) {
      return selections;
    }
    for (int i = 0; i < files.length; i++) {
      File file = files.item(i);
      if (file != null) {
        selections.add(new J2clComposeSurfaceController.AttachmentFileSelection(file, file.name));
      }
    }
    return selections;
  }

  /**
   * F-3.S4 (#1038, R-5.6 step 1): extract the File array from the
   * `wavy-composer-attachment-dropped` CustomEvent's detail.files
   * payload. The lit composer dispatches `Array.from(dataTransfer.files)`
   * so the detail.files property is a JS array of File objects.
   */
  private static List<J2clComposeSurfaceController.AttachmentFileSelection>
      fileSelectionsFromDroppedEvent(Event event) {
    List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>();
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) return selections;
    Object filesObj = Js.asPropertyMap(detail).get("files");
    if (filesObj == null) return selections;
    JsArray<?> jsFiles = Js.cast(filesObj);
    int len = jsFiles.length;
    for (int i = 0; i < len; i++) {
      Object item = jsFiles.getAt(i);
      if (item == null) continue;
      File file = Js.cast(item);
      String fileName = file.name == null ? "attachment" : file.name;
      selections.add(
          new J2clComposeSurfaceController.AttachmentFileSelection(file, fileName));
    }
    return selections;
  }

  /**
   * F-3.S4 (#1038, R-5.6 F.6): mount the body-level
   * `<wavy-confirm-dialog>` element if not already present. The
   * confirm dialog is shared across all consumers (the lit module's
   * ensureWavyConfirmDialogMounted() helper does the JS-side mount;
   * here we rely on the shell bundle having registered the element
   * via index.js so we just create one if missing).
   */
  private void ensureWavyConfirmDialogMounted() {
    if (DomGlobal.document.body.querySelector("wavy-confirm-dialog") != null) return;
    HTMLElement dialog =
        (HTMLElement) DomGlobal.document.createElement("wavy-confirm-dialog");
    DomGlobal.document.body.appendChild(dialog);
  }

  /**
   * F-3.S4 (#1038, R-5.6 F.6): dispatch a `wavy-confirm-requested`
   * CustomEvent that the body-mounted wavy-confirm-dialog catches.
   * The dialog answers with `wavy-confirm-resolved` carrying the same
   * requestId. The view caches no per-request state — the requestId
   * encodes the blip id so the resolved-listener can route directly.
   */
  private void requestBlipDeleteConfirmation(String blipId, String waveId, int bodyItemCount) {
    String requestId =
        BLIP_DELETE_REQUEST_PREFIX
            + blipId
            + "|"
            + (waveId != null ? waveId : "")
            + "|"
            + Math.max(0, bodyItemCount);
    JsPropertyMap<Object> detail = Js.uncheckedCast(JsPropertyMap.of());
    detail.set("requestId", requestId);
    detail.set("message", "Delete this blip?");
    detail.set("confirmLabel", "Delete");
    detail.set("cancelLabel", "Cancel");
    detail.set("tone", "destructive");
    elemental2.dom.CustomEventInit<Object> init = elemental2.dom.CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    init.setDetail(Js.cast(detail));
    try {
      DomGlobal.document.body.dispatchEvent(
          new elemental2.dom.CustomEvent<Object>("wavy-confirm-requested", init));
    } catch (Throwable ignored) {
      // Event dispatch is observational; never let it break the
      // surface state if the wavy-confirm-dialog is missing.
    }
  }
}

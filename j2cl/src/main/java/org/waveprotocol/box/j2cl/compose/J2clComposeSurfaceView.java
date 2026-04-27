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
  private final HTMLTextAreaElement createInput;
  private final HTMLElement createSubmit;
  private final HTMLElement replyElement;
  private final HTMLInputElement attachmentInput;
  private J2clComposeSurfaceController.Listener listener;
  private final Map<String, HTMLElement> inlineComposers = new HashMap<>();
  private String activeInlineComposerKey = "";
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

  public J2clComposeSurfaceView(HTMLElement createHost, HTMLElement replyHost) {
    createHost.innerHTML = "";
    replyHost.innerHTML = "";

    HTMLElement shell = (HTMLElement) DomGlobal.document.createElement("composer-shell");
    createHost.appendChild(shell);

    HTMLFormElement createForm = (HTMLFormElement) DomGlobal.document.createElement("form");
    createForm.setAttribute("slot", "create");
    createForm.className = "j2cl-compose-create-form";
    shell.appendChild(createForm);

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
    createForm.appendChild(createInput);

    createSubmit = (HTMLElement) DomGlobal.document.createElement("composer-submit-affordance");
    setProperty(createSubmit, "label", "Create wave");
    createForm.appendChild(createSubmit);
    createSubmit.addEventListener(
        "submit-affordance",
        event -> {
          if (listener != null) {
            listener.onCreateSubmitted(createInput.value);
          }
        });

    replyElement = (HTMLElement) DomGlobal.document.createElement("composer-inline-reply");
    replyHost.appendChild(replyElement);
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

    // F-3.S2 (#1038): mention popover + per-blip task affordance events.
    DomGlobal.document.body.addEventListener(
        "wave-blip-task-toggled",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          boolean completed = eventDetailBoolean(event, "completed");
          listener.onTaskToggled(blipId, completed);
        });
    DomGlobal.document.body.addEventListener(
        "wave-blip-task-metadata-changed",
        event -> {
          if (listener == null) return;
          String blipId = eventDetailString(event, "blipId");
          String assignee = eventDetailString(event, "assigneeAddress");
          String due = dueDateToEpochMs(eventDetailString(event, "dueDate"));
          listener.onTaskMetadataChanged(blipId, assignee, due);
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
  }

  @Override
  public void bind(J2clComposeSurfaceController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clComposeSurfaceModel model) {
    createInput.value = model.getCreateDraft();
    createInput.disabled = !model.isCreateEnabled() || model.isCreateSubmitting();
    setProperty(createSubmit, "busy", model.isCreateSubmitting());
    setProperty(createSubmit, "disabled", !model.isCreateEnabled() || model.isCreateSubmitting());
    setProperty(createSubmit, "status", model.getCreateStatusText());
    setProperty(createSubmit, "error", model.getCreateErrorText());

    setProperty(replyElement, "available", model.isReplyAvailable());
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
          if (listener != null) {
            listener.onReplySubmitted(propertyString(composer, "draft"));
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
}

package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;

/**
 * Per-blip read-surface model carried through the J2CL read path.
 *
 * <p>F-2 (#1037, R-3.1, R-3.4, R-4.4) extends the original {@link #blipId} +
 * {@link #text} + attachments contract with the per-blip metadata required by
 * the StageOne read surface:
 *
 * <ul>
 *   <li>{@link #authorId} / {@link #authorDisplayName} — F.1 / F.2.
 *   <li>{@link #lastModifiedTimeMillis} — F.3 (full datetime tooltip on hover)
 *       and is also used by the relative-timestamp display.
 *   <li>{@link #parentBlipId} / {@link #threadId} — R-3.7 depth-nav drill-in
 *       and inline-reply chip rendering (F.10).
 *   <li>{@link #unread} — R-4.4 per-blip unread dot + Next-Unread navigation.
 *   <li>{@link #hasMention} — E.6 / E.7 mention navigation (Prev @ / Next @).
 * </ul>
 *
 * <p>All extra fields are optional; the simple {@code (blipId, text)}
 * constructors are preserved for fixture and viewport-placeholder paths that
 * have not yet hydrated metadata.
 */
public final class J2clReadBlip {
  private final String blipId;
  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;
  private final String authorId;
  private final String authorDisplayName;
  private final long lastModifiedTimeMillis;
  private final String parentBlipId;
  private final String threadId;
  private final boolean unread;
  private final boolean hasMention;
  /**
   * F-3.S4 (#1038, R-5.6 F.6 — review-1077 Bug 1): blips carrying the
   * {@code tombstone/deleted=true} annotation written by
   * {@link org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory#blipDeleteRequest}
   * are filtered out of the read surface by
   * {@link J2clReadSurfaceDomRenderer} so the user sees an immediate
   * disappearance after the delete delta lands.  The factory only
   * emits the annotation; this flag is what makes the read renderer
   * actually skip the blip.
   */
  private final boolean deleted;
  /**
   * J-UI-6 (#1084, R-5.4): persisted task done-state read off the blip's
   * {@code task/done=true} annotation written by
   * {@link org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory#taskToggleRequest}.
   * Drives the strikethrough/checkmark rendering on {@code <wave-blip>} via
   * the {@code data-task-completed} attribute the renderer sets when this
   * flag is true. Survives across reload (the annotation is part of the
   * authoritative wavelet DocOp) and across live updates from other clients.
   */
  private final boolean taskDone;
  /**
   * J-UI-6 (#1084, R-5.4 — "associated metadata overlays preserved"):
   * persisted task assignee read off the blip's {@code task/assignee}
   * annotation. Empty string when the annotation is absent or empty.
   */
  private final String taskAssignee;
  /**
   * J-UI-6 (#1084, R-5.4 — "associated metadata overlays preserved"):
   * persisted task due-date timestamp (ms since epoch) read off the blip's
   * {@code task/dueTs} annotation. {@link J2clTaskItemModel#UNKNOWN_DUE_TIMESTAMP}
   * when the annotation is absent or unparseable.
   */
  private final long taskDueTimestamp;
  /**
   * Issue #1129: authoritative wavelet document item count for this blip
   * body, including characters and element starts/ends. Annotation writers
   * need this to retain across the body before closing the annotation.
   */
  private final int bodyItemCount;
  /**
   * True when a {@code task/done} annotation is present on this blip
   * (regardless of its value). Differentiates a reopened task blip
   * (taskDone=false, annotation present) from a non-task blip (annotation
   * absent). Required so the renderer can emit {@code data-task-present}
   * and keep the task affordance mounted across full DOM rebuilds.
   */
  private final boolean isTask;

  public J2clReadBlip(String blipId, String text) {
    this(blipId, text, Collections.<J2clAttachmentRenderModel>emptyList());
  }

  public J2clReadBlip(
      String blipId, String text, List<J2clAttachmentRenderModel> attachments) {
    this(
        blipId,
        text,
        attachments,
        /* authorId= */ "",
        /* authorDisplayName= */ "",
        /* lastModifiedTimeMillis= */ 0L,
        /* parentBlipId= */ "",
        /* threadId= */ "",
        /* unread= */ false,
        /* hasMention= */ false,
        /* deleted= */ false);
  }

  public J2clReadBlip(
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention) {
    this(
        blipId,
        text,
        attachments,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        /* deleted= */ false);
  }

  public J2clReadBlip(
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean deleted) {
    this(
        blipId,
        text,
        attachments,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        deleted,
        /* taskDone= */ false,
        /* taskAssignee= */ "",
        /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
  }

  /**
   * J-UI-6 (#1084, R-5.4) — full constructor that carries the persisted
   * task done state, assignee, and due timestamp through the read path.
   */
  public J2clReadBlip(
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean deleted,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp) {
    this(
        blipId,
        text,
        attachments,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        deleted,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        /* bodyItemCount= */ 0);
  }

  public J2clReadBlip(
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean deleted,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount) {
    this(
        blipId, text, attachments, authorId, authorDisplayName, lastModifiedTimeMillis,
        parentBlipId, threadId, unread, hasMention, deleted, taskDone, taskAssignee,
        taskDueTimestamp, bodyItemCount,
        /* isTask= */ taskDone
            || (taskAssignee != null && !taskAssignee.trim().isEmpty())
            || (taskDueTimestamp != J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP));
  }

  public J2clReadBlip(
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean deleted,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount,
      boolean isTask) {
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
    this.authorId = authorId == null ? "" : authorId;
    this.authorDisplayName = authorDisplayName == null ? "" : authorDisplayName;
    this.lastModifiedTimeMillis = Math.max(0L, lastModifiedTimeMillis);
    this.parentBlipId = parentBlipId == null ? "" : parentBlipId;
    this.threadId = threadId == null ? "" : threadId;
    this.unread = unread;
    this.hasMention = hasMention;
    this.deleted = deleted;
    this.taskDone = taskDone;
    this.taskAssignee = taskAssignee == null ? "" : taskAssignee;
    this.taskDueTimestamp = taskDueTimestamp;
    this.bodyItemCount = Math.max(0, bodyItemCount);
    this.isTask = isTask;
  }

  /** Builder-style copy that flips the unread flag without re-typing the rest. */
  public J2clReadBlip withUnread(boolean nextUnread) {
    if (nextUnread == this.unread) {
      return this;
    }
    return new J2clReadBlip(
        blipId,
        text,
        attachments,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        nextUnread,
        hasMention,
        deleted,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        isTask);
  }

  /**
   * J-UI-6 (#1084) — builder-style copy that flips the persisted task-done
   * flag. Used by the read-state path to carry the optimistic local toggle
   * into the next render before the server-confirmed annotation arrives, in
   * symmetry with {@link #withUnread(boolean)}.
   */
  public J2clReadBlip withTaskDone(boolean nextTaskDone) {
    if (nextTaskDone == this.taskDone) {
      return this;
    }
    return new J2clReadBlip(
        blipId,
        text,
        attachments,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        deleted,
        nextTaskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        true);
  }

  public String getBlipId() {
    return blipId;
  }

  public String getText() {
    return text;
  }

  public List<J2clAttachmentRenderModel> getAttachments() {
    return attachments;
  }

  public String getAuthorId() {
    return authorId;
  }

  public String getAuthorDisplayName() {
    String trimmed = authorDisplayName.trim();
    return trimmed.isEmpty() ? authorId : trimmed;
  }

  public long getLastModifiedTimeMillis() {
    return lastModifiedTimeMillis;
  }

  public String getParentBlipId() {
    return parentBlipId;
  }

  public String getThreadId() {
    return threadId;
  }

  public boolean isUnread() {
    return unread;
  }

  public boolean hasMention() {
    return hasMention;
  }

  /**
   * F-3.S4 (#1038, R-5.6 F.6 — review-1077 Bug 1): blip carries the
   * {@code tombstone/deleted=true} annotation written by the F.6 delete
   * delta. {@link J2clReadSurfaceDomRenderer} filters these out so the
   * deleted blip disappears from the read surface as soon as the delta
   * lands.
   */
  public boolean isDeleted() {
    return deleted;
  }

  /** True when a {@code task/done} annotation is present (regardless of value). */
  public boolean isTask() {
    return isTask;
  }

  /**
   * J-UI-6 (#1084, R-5.4): persisted task-done state read from the
   * {@code task/done=true} annotation. Drives the strikethrough/checkmark
   * rendering on the blip body.
   */
  public boolean isTaskDone() {
    return taskDone;
  }

  /**
   * J-UI-6 (#1084, R-5.4): persisted task assignee read from the
   * {@code task/assignee} annotation. Empty string when unset.
   */
  public String getTaskAssignee() {
    return taskAssignee;
  }

  /**
   * J-UI-6 (#1084, R-5.4): persisted task due-date timestamp (ms since epoch)
   * read from the {@code task/dueTs} annotation, or
   * {@link J2clTaskItemModel#UNKNOWN_DUE_TIMESTAMP} when unset.
   */
  public long getTaskDueTimestamp() {
    return taskDueTimestamp;
  }

  public int getBodyItemCount() {
    return bodyItemCount;
  }
}

package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;

/**
 * Per-segment entry consumed by {@link J2clReadSurfaceDomRenderer#renderWindow}.
 *
 * <p>F-2 (#1037) extends the original (segment, range, blipId, text, attachments,
 * loaded) tuple with the per-blip metadata listed on {@link J2clReadBlip}. The
 * window-render path is the dominant production code path for large waves, so
 * the metadata must flow through here even though the placeholder shape stays
 * unchanged.
 */
public final class J2clReadWindowEntry {
  private final String segment;
  private final long fromVersion;
  private final long toVersion;
  private final String blipId;
  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;
  private final boolean loaded;
  private final String authorId;
  private final String authorDisplayName;
  private final long lastModifiedTimeMillis;
  private final String parentBlipId;
  private final String threadId;
  private final boolean unread;
  private final boolean hasMention;
  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#isTaskDone()}. */
  private final boolean taskDone;
  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#getTaskAssignee()}. */
  private final String taskAssignee;
  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#getTaskDueTimestamp()}. */
  private final long taskDueTimestamp;
  /** Issue #1129: see {@link J2clReadBlip#getBodyItemCount()}. */
  private final int bodyItemCount;
  /** See {@link J2clReadBlip#isTask()}: true when a task/done annotation is present. */
  private final boolean isTask;
  /** Issue #1167: inline reply anchors parsed from raw Wave document markup. */
  private final List<J2clInlineReplyAnchor> inlineReplyAnchors;

  private J2clReadWindowEntry(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      boolean loaded,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount) {
    this(
        segment, fromVersion, toVersion, blipId, text, attachments, loaded,
        authorId, authorDisplayName, lastModifiedTimeMillis, parentBlipId, threadId,
        unread, hasMention, taskDone, taskAssignee, taskDueTimestamp, bodyItemCount,
        /* isTask= */ taskDone
            || (taskAssignee != null && !taskAssignee.trim().isEmpty())
            || (taskDueTimestamp != J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP));
  }

  private J2clReadWindowEntry(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      boolean loaded,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount,
      boolean isTask) {
    this(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        attachments,
        loaded,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        isTask,
        Collections.<J2clInlineReplyAnchor>emptyList());
  }

  private J2clReadWindowEntry(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      boolean loaded,
      String authorId,
      String authorDisplayName,
      long lastModifiedTimeMillis,
      String parentBlipId,
      String threadId,
      boolean unread,
      boolean hasMention,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount,
      boolean isTask,
      List<J2clInlineReplyAnchor> inlineReplyAnchors) {
    this.segment = segment == null ? "" : segment;
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
    this.loaded = loaded;
    this.authorId = authorId == null ? "" : authorId;
    this.authorDisplayName = authorDisplayName == null ? "" : authorDisplayName;
    this.lastModifiedTimeMillis = Math.max(0L, lastModifiedTimeMillis);
    this.parentBlipId = parentBlipId == null ? "" : parentBlipId;
    this.threadId = threadId == null ? "" : threadId;
    this.unread = unread;
    this.hasMention = hasMention;
    this.taskDone = taskDone;
    this.taskAssignee = taskAssignee == null ? "" : taskAssignee;
    this.taskDueTimestamp = taskDueTimestamp;
    this.bodyItemCount = Math.max(0, bodyItemCount);
    this.isTask = isTask;
    this.inlineReplyAnchors =
        inlineReplyAnchors == null
            ? Collections.<J2clInlineReplyAnchor>emptyList()
            : Collections.unmodifiableList(
                new ArrayList<J2clInlineReplyAnchor>(inlineReplyAnchors));
  }

  public static J2clReadWindowEntry loaded(
      String segment, long fromVersion, long toVersion, String blipId, String text) {
    return loaded(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        Collections.<J2clAttachmentRenderModel>emptyList());
  }

  public static J2clReadWindowEntry loaded(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments) {
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        attachments,
        /* loaded= */ true,
        /* authorId= */ "",
        /* authorDisplayName= */ "",
        /* lastModifiedTimeMillis= */ 0L,
        /* parentBlipId= */ "",
        /* threadId= */ "",
        /* unread= */ false,
        /* hasMention= */ false,
        /* taskDone= */ false,
        /* taskAssignee= */ "",
        /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        /* bodyItemCount= */ 0);
  }

  /**
   * F-2 (#1037, R-3.1 / R-3.4 / R-4.4) — full constructor for entries that
   * carry per-blip metadata sourced from the projector.
   */
  public static J2clReadWindowEntry loadedWithMetadata(
      String segment,
      long fromVersion,
      long toVersion,
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
    return loadedWithTaskMetadata(
        segment,
        fromVersion,
        toVersion,
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
        /* taskDone= */ false,
        /* taskAssignee= */ "",
        /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);
  }

  /**
   * J-UI-6 (#1084, R-5.4) — full constructor that also carries persisted
   * task done state, assignee, and due timestamp through the dominant
   * window-render path. The renderer reads these fields when emitting the
   * {@code <wave-blip>} attribute set so reload + live updates from other
   * clients show the strikethrough/checkmark + metadata-overlay state
   * without round-tripping through the conversation model.
   */
  public static J2clReadWindowEntry loadedWithTaskMetadata(
      String segment,
      long fromVersion,
      long toVersion,
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
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp) {
    return loadedWithTaskMetadata(
        segment,
        fromVersion,
        toVersion,
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
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        /* bodyItemCount= */ 0);
  }

  public static J2clReadWindowEntry loadedWithTaskMetadata(
      String segment,
      long fromVersion,
      long toVersion,
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
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount) {
    return loadedWithTaskMetadata(
        segment, fromVersion, toVersion, blipId, text, attachments,
        authorId, authorDisplayName, lastModifiedTimeMillis, parentBlipId, threadId,
        unread, hasMention, taskDone, taskAssignee, taskDueTimestamp, bodyItemCount,
        /* isTask= */ taskDone
            || (taskAssignee != null && !taskAssignee.trim().isEmpty())
            || (taskDueTimestamp != J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP));
  }

  public static J2clReadWindowEntry loadedWithTaskMetadata(
      String segment,
      long fromVersion,
      long toVersion,
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
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount,
      boolean isTask) {
    return loadedWithTaskMetadata(
        segment,
        fromVersion,
        toVersion,
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
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        isTask,
        Collections.<J2clInlineReplyAnchor>emptyList());
  }

  public static J2clReadWindowEntry loadedWithTaskMetadata(
      String segment,
      long fromVersion,
      long toVersion,
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
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      int bodyItemCount,
      boolean isTask,
      List<J2clInlineReplyAnchor> inlineReplyAnchors) {
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        attachments,
        /* loaded= */ true,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        isTask,
        inlineReplyAnchors);
  }

  public static J2clReadWindowEntry placeholder(
      String segment, long fromVersion, long toVersion, String blipId) {
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        "",
        Collections.<J2clAttachmentRenderModel>emptyList(),
        /* loaded= */ false,
        /* authorId= */ "",
        /* authorDisplayName= */ "",
        /* lastModifiedTimeMillis= */ 0L,
        /* parentBlipId= */ "",
        /* threadId= */ "",
        /* unread= */ false,
        /* hasMention= */ false,
        /* taskDone= */ false,
        /* taskAssignee= */ "",
        /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        /* bodyItemCount= */ 0);
  }

  /** Returns a copy of this entry with the unread flag toggled. */
  public J2clReadWindowEntry withUnread(boolean nextUnread) {
    if (nextUnread == this.unread) {
      return this;
    }
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        attachments,
        loaded,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        nextUnread,
        hasMention,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        isTask,
        inlineReplyAnchors);
  }

  /**
   * J-UI-6 (#1084) — builder-style copy that flips the persisted task-done
   * flag. Symmetric with {@link #withUnread(boolean)}.
   */
  public J2clReadWindowEntry withTaskDone(boolean nextTaskDone) {
    if (nextTaskDone == this.taskDone) {
      return this;
    }
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        attachments,
        loaded,
        authorId,
        authorDisplayName,
        lastModifiedTimeMillis,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        nextTaskDone,
        taskAssignee,
        taskDueTimestamp,
        bodyItemCount,
        true,
        inlineReplyAnchors);
  }

  public String getSegment() {
    return segment;
  }

  public long getFromVersion() {
    return fromVersion;
  }

  public long getToVersion() {
    return toVersion;
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

  public boolean isLoaded() {
    return loaded;
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

  /** See {@link J2clReadBlip#isTask()}: true when a task/done annotation is present. */
  public boolean isTask() {
    return isTask;
  }

  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#isTaskDone()}. */
  public boolean isTaskDone() {
    return taskDone;
  }

  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#getTaskAssignee()}. */
  public String getTaskAssignee() {
    return taskAssignee;
  }

  /** J-UI-6 (#1084, R-5.4): see {@link J2clReadBlip#getTaskDueTimestamp()}. */
  public long getTaskDueTimestamp() {
    return taskDueTimestamp;
  }

  public int getBodyItemCount() {
    return bodyItemCount;
  }

  public List<J2clInlineReplyAnchor> getInlineReplyAnchors() {
    return inlineReplyAnchors;
  }
}

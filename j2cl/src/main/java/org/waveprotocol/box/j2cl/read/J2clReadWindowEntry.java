package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;

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
      boolean hasMention) {
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
        /* hasMention= */ false);
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
        hasMention);
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
        /* hasMention= */ false);
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
        hasMention);
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
    return authorDisplayName.isEmpty() ? authorId : authorDisplayName;
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
}

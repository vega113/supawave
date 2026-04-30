package org.waveprotocol.box.j2cl.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;

public final class J2clInteractionBlipModel {
  private final String blipId;
  private final String documentId;
  private final String author;
  private final String text;
  private final List<String> participantContext;
  private final boolean editable;
  private final List<SidecarAnnotationRange> annotationRanges;
  private final List<SidecarReactionEntry> reactionEntries;
  private final List<J2clMentionRange> mentionRanges;
  private final List<J2clTaskItemModel> taskItems;
  private final List<J2clReactionSummary> reactionSummaries;

  public J2clInteractionBlipModel(
      String blipId,
      String text,
      List<SidecarAnnotationRange> annotationRanges,
      List<SidecarReactionEntry> reactionEntries) {
    this(
        blipId,
        blipId,
        "",
        text,
        Collections.<String>emptyList(),
        false,
        annotationRanges,
        reactionEntries);
  }

  public J2clInteractionBlipModel(
      String blipId,
      String documentId,
      String author,
      String text,
      List<String> participantContext,
      boolean editable,
      List<SidecarAnnotationRange> annotationRanges,
      List<SidecarReactionEntry> reactionEntries) {
    this.blipId = blipId == null ? "" : blipId;
    this.documentId = documentId == null ? this.blipId : documentId;
    this.author = author == null ? "" : author;
    this.text = text == null ? "" : text;
    this.participantContext =
        participantContext == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantContext));
    this.editable = editable;
    this.annotationRanges =
        annotationRanges == null
            ? Collections.<SidecarAnnotationRange>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarAnnotationRange>(annotationRanges));
    this.reactionEntries =
        reactionEntries == null
            ? Collections.<SidecarReactionEntry>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarReactionEntry>(reactionEntries));
    this.mentionRanges = refineMentionRanges(this.text, this.annotationRanges);
    this.taskItems = refineTaskItems(this.blipId, this.annotationRanges, this.editable);
    this.reactionSummaries = refineReactionSummaries(this.reactionEntries);
  }

  public String getBlipId() {
    return blipId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public List<String> getParticipantContext() {
    return participantContext;
  }

  public boolean isEditable() {
    return editable;
  }

  public List<SidecarAnnotationRange> getAnnotationRanges() {
    return annotationRanges;
  }

  public List<SidecarReactionEntry> getReactionEntries() {
    return reactionEntries;
  }

  public List<J2clMentionRange> getMentionRanges() {
    return mentionRanges;
  }

  public List<J2clTaskItemModel> getTaskItems() {
    return taskItems;
  }

  public List<J2clReactionSummary> getReactionSummaries() {
    return reactionSummaries;
  }

  /**
   * F-3.S3 (#1038, R-5.5): rebuild this blip's reaction summaries with
   * {@code activeForCurrentUser} flags computed against the signed-in
   * user's address. The model itself stays user-agnostic (constructed
   * inside the projector before the bootstrap address is known); the
   * view layer calls this helper to refresh the chip-pressed state on
   * each render. Empty {@code currentUserAddress} returns the original
   * (always-inactive) summaries unchanged.
   */
  public List<J2clReactionSummary> reactionSummariesForUser(String currentUserAddress) {
    if (currentUserAddress == null || currentUserAddress.trim().isEmpty()) {
      return reactionSummaries;
    }
    String normalized = currentUserAddress.trim();
    List<J2clReactionSummary> rebuilt = new ArrayList<J2clReactionSummary>();
    for (J2clReactionSummary summary : reactionSummaries) {
      boolean active = false;
      for (String address : summary.getParticipantAddresses()) {
        if (address != null) {
          String normalizedAddress = address.trim();
          if (normalizedAddress.equalsIgnoreCase(normalized)) {
            active = true;
            break;
          }
        }
      }
      rebuilt.add(
          new J2clReactionSummary(
              summary.getEmoji(),
              summary.getParticipantAddresses(),
              active,
              summary.getInspectLabel()));
    }
    return Collections.unmodifiableList(rebuilt);
  }

  private static List<J2clMentionRange> refineMentionRanges(
      String text, List<SidecarAnnotationRange> annotationRanges) {
    if (annotationRanges == null || annotationRanges.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clMentionRange> mentions = new ArrayList<J2clMentionRange>();
    for (SidecarAnnotationRange range : annotationRanges) {
      if (range == null || !isMentionAnnotation(range)) {
        continue;
      }
      mentions.add(
          new J2clMentionRange(
              range.getStartOffset(),
              range.getEndOffset(),
              range.getValue(),
              sliceText(text, range.getStartOffset(), range.getEndOffset())));
    }
    return Collections.unmodifiableList(mentions);
  }

  private static boolean isMentionAnnotation(SidecarAnnotationRange range) {
    return range.isMention();
  }

  private static List<J2clTaskItemModel> refineTaskItems(
      String blipId, List<SidecarAnnotationRange> annotationRanges, boolean editable) {
    if (annotationRanges == null || annotationRanges.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clTaskItemModel> tasks = new ArrayList<J2clTaskItemModel>();
    for (SidecarAnnotationRange taskIdRange : annotationRanges) {
      if (taskIdRange == null || !"task/id".equals(taskIdRange.getKey())) {
        continue;
      }
      String taskId = safe(taskIdRange.getValue());
      if (taskId.isEmpty()) {
        continue;
      }
      tasks.add(
          new J2clTaskItemModel(
              taskId,
              taskIdRange.getStartOffset(),
              "task-" + safe(blipId) + "-" + safe(taskId),
              findTaskValue(annotationRanges, "task/assignee", taskIdRange),
              parseLong(findTaskValue(annotationRanges, "task/dueTs", taskIdRange)),
              false,
              editable));
    }
    return Collections.unmodifiableList(tasks);
  }

  private static List<J2clReactionSummary> refineReactionSummaries(
      List<SidecarReactionEntry> reactionEntries) {
    if (reactionEntries == null || reactionEntries.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clReactionSummary> summaries = new ArrayList<J2clReactionSummary>();
    for (SidecarReactionEntry entry : reactionEntries) {
      String emoji = entry == null ? "" : safe(entry.getEmoji());
      if (emoji.isEmpty()) {
        continue;
      }
      // F-3.S3 (#1038, R-5.5): the active-for-current-user flag is
      // computed by the view layer via reactionSummariesForUser(addr)
      // because the bootstrap address is not known when the projector
      // builds this model. Initial summaries are always inactive.
      summaries.add(
          new J2clReactionSummary(emoji, entry.getAddresses(), false, ""));
    }
    return Collections.unmodifiableList(summaries);
  }

  private static String findTaskValue(
      List<SidecarAnnotationRange> annotationRanges, String key, SidecarAnnotationRange taskIdRange) {
    // GWT task metadata annotations share the same range as task/id; keep that contract explicit.
    // Empty string signals that no matching sibling metadata annotation exists.
    for (SidecarAnnotationRange range : annotationRanges) {
      if (range == null || !key.equals(range.getKey())) {
        continue;
      }
      if (range.getStartOffset() == taskIdRange.getStartOffset()
          && range.getEndOffset() == taskIdRange.getEndOffset()) {
        return safe(range.getValue());
      }
    }
    return "";
  }

  private static String sliceText(String text, int startOffset, int endOffset) {
    String value = text == null ? "" : text;
    int start = Math.max(0, Math.min(startOffset, value.length()));
    int end = Math.max(start, Math.min(endOffset, value.length()));
    return value.substring(start, end);
  }

  private static long parseLong(String value) {
    // Missing or non-numeric task/dueTs values intentionally stay unknown.
    if (value == null || value.isEmpty()) {
      return J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}

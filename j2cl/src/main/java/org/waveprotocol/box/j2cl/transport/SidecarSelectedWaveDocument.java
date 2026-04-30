package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SidecarSelectedWaveDocument {
  private static final String REACTION_DATA_DOCUMENT_PREFIX = "react+";

  private final String documentId;
  private final String author;
  private final long lastModifiedVersion;
  private final long lastModifiedTime;
  private final String textContent;
  private final int bodyItemCount;
  private final List<SidecarAnnotationRange> annotationRanges;
  private final List<SidecarReactionEntry> reactionEntries;

  public SidecarSelectedWaveDocument(
      String documentId,
      String author,
      long lastModifiedVersion,
      long lastModifiedTime,
      String textContent) {
    this(
        documentId,
        author,
        lastModifiedVersion,
        lastModifiedTime,
        textContent,
        /* bodyItemCount= */ 0,
        Collections.<SidecarAnnotationRange>emptyList(),
        Collections.<SidecarReactionEntry>emptyList());
  }

  public SidecarSelectedWaveDocument(
      String documentId,
      String author,
      long lastModifiedVersion,
      long lastModifiedTime,
      String textContent,
      List<SidecarAnnotationRange> annotationRanges,
      List<SidecarReactionEntry> reactionEntries) {
    this(
        documentId,
        author,
        lastModifiedVersion,
        lastModifiedTime,
        textContent,
        /* bodyItemCount= */ 0,
        annotationRanges,
        reactionEntries);
  }

  public SidecarSelectedWaveDocument(
      String documentId,
      String author,
      long lastModifiedVersion,
      long lastModifiedTime,
      String textContent,
      int bodyItemCount,
      List<SidecarAnnotationRange> annotationRanges,
      List<SidecarReactionEntry> reactionEntries) {
    this.documentId = documentId;
    this.author = author;
    this.lastModifiedVersion = lastModifiedVersion;
    this.lastModifiedTime = lastModifiedTime;
    this.textContent = textContent;
    this.bodyItemCount = Math.max(0, bodyItemCount);
    this.annotationRanges =
        annotationRanges == null
            ? Collections.<SidecarAnnotationRange>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarAnnotationRange>(annotationRanges));
    this.reactionEntries =
        reactionEntries == null
            ? Collections.<SidecarReactionEntry>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarReactionEntry>(reactionEntries));
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getAuthor() {
    return author;
  }

  public long getLastModifiedVersion() {
    return lastModifiedVersion;
  }

  public long getLastModifiedTime() {
    return lastModifiedTime;
  }

  public String getTextContent() {
    return textContent;
  }

  public int getBodyItemCount() {
    return bodyItemCount;
  }

  public List<SidecarAnnotationRange> getAnnotationRanges() {
    return annotationRanges;
  }

  public List<SidecarReactionEntry> getReactionEntries() {
    return reactionEntries;
  }

  public boolean isReactionDataDocument() {
    return documentId != null
        && documentId.startsWith(REACTION_DATA_DOCUMENT_PREFIX)
        && documentId.length() > REACTION_DATA_DOCUMENT_PREFIX.length();
  }

  public String getReactionTargetBlipId() {
    if (!isReactionDataDocument()) {
      return "";
    }
    return documentId.substring(REACTION_DATA_DOCUMENT_PREFIX.length());
  }
}

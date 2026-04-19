package org.waveprotocol.box.j2cl.transport;

public final class SidecarSelectedWaveDocument {
  private final String documentId;
  private final String author;
  private final long lastModifiedVersion;
  private final long lastModifiedTime;
  private final String textContent;

  public SidecarSelectedWaveDocument(
      String documentId,
      String author,
      long lastModifiedVersion,
      long lastModifiedTime,
      String textContent) {
    this.documentId = documentId;
    this.author = author;
    this.lastModifiedVersion = lastModifiedVersion;
    this.lastModifiedTime = lastModifiedTime;
    this.textContent = textContent;
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
}

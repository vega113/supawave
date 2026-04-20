package org.waveprotocol.box.j2cl.search;

public final class J2clSidecarComposeModel {
  private final String createDraft;
  private final boolean createSubmitting;
  private final String createStatusText;
  private final String createErrorText;
  private final boolean replyAvailable;
  private final String replyDraft;
  private final boolean replySubmitting;
  private final String replyStatusText;
  private final String replyErrorText;
  private final String replyHintText;

  public J2clSidecarComposeModel(
      String createDraft,
      boolean createSubmitting,
      String createStatusText,
      String createErrorText,
      boolean replyAvailable,
      String replyDraft,
      boolean replySubmitting,
      String replyStatusText,
      String replyErrorText,
      String replyHintText) {
    this.createDraft = createDraft;
    this.createSubmitting = createSubmitting;
    this.createStatusText = createStatusText;
    this.createErrorText = createErrorText;
    this.replyAvailable = replyAvailable;
    this.replyDraft = replyDraft;
    this.replySubmitting = replySubmitting;
    this.replyStatusText = replyStatusText;
    this.replyErrorText = replyErrorText;
    this.replyHintText = replyHintText;
  }

  public String getCreateDraft() {
    return createDraft;
  }

  public boolean isCreateSubmitting() {
    return createSubmitting;
  }

  public String getCreateStatusText() {
    return createStatusText;
  }

  public String getCreateErrorText() {
    return createErrorText;
  }

  public boolean isReplyAvailable() {
    return replyAvailable;
  }

  public String getReplyDraft() {
    return replyDraft;
  }

  public boolean isReplySubmitting() {
    return replySubmitting;
  }

  public String getReplyStatusText() {
    return replyStatusText;
  }

  public String getReplyErrorText() {
    return replyErrorText;
  }

  public String getReplyHintText() {
    return replyHintText;
  }
}

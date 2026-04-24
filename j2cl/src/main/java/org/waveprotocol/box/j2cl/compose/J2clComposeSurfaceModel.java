package org.waveprotocol.box.j2cl.compose;

public final class J2clComposeSurfaceModel {
  private final boolean createEnabled;
  private final String createDraft;
  private final boolean createSubmitting;
  private final String createStatusText;
  private final String createErrorText;
  private final boolean replyAvailable;
  private final String replyTargetLabel;
  private final String replyDraft;
  private final boolean replySubmitting;
  private final boolean replyStaleBasis;
  private final String replyStatusText;
  private final String replyErrorText;

  public J2clComposeSurfaceModel(
      boolean createEnabled,
      String createDraft,
      boolean createSubmitting,
      String createStatusText,
      String createErrorText,
      boolean replyAvailable,
      String replyTargetLabel,
      String replyDraft,
      boolean replySubmitting,
      boolean replyStaleBasis,
      String replyStatusText,
      String replyErrorText) {
    this.createEnabled = createEnabled;
    this.createDraft = nullToEmpty(createDraft);
    this.createSubmitting = createSubmitting;
    this.createStatusText = nullToEmpty(createStatusText);
    this.createErrorText = nullToEmpty(createErrorText);
    this.replyAvailable = replyAvailable;
    this.replyTargetLabel = nullToEmpty(replyTargetLabel);
    this.replyDraft = nullToEmpty(replyDraft);
    this.replySubmitting = replySubmitting;
    this.replyStaleBasis = replyStaleBasis;
    this.replyStatusText = nullToEmpty(replyStatusText);
    this.replyErrorText = nullToEmpty(replyErrorText);
  }

  public boolean isCreateEnabled() {
    return createEnabled;
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

  public String getReplyTargetLabel() {
    return replyTargetLabel;
  }

  public String getReplyDraft() {
    return replyDraft;
  }

  public boolean isReplySubmitting() {
    return replySubmitting;
  }

  public boolean isReplyStaleBasis() {
    return replyStaleBasis;
  }

  public String getReplyStatusText() {
    return replyStatusText;
  }

  public String getReplyErrorText() {
    return replyErrorText;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

package org.waveprotocol.box.j2cl.compose;

import java.util.Collections;
import java.util.List;

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
  private final String activeCommandId;
  private final String commandStatusText;
  private final String commandErrorText;
  private final List<String> participantAddresses;

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
      String replyErrorText,
      String activeCommandId,
      String commandStatusText,
      String commandErrorText) {
    this(createEnabled, createDraft, createSubmitting, createStatusText, createErrorText,
        replyAvailable, replyTargetLabel, replyDraft, replySubmitting, replyStaleBasis,
        replyStatusText, replyErrorText, activeCommandId, commandStatusText, commandErrorText,
        Collections.emptyList());
  }

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
      String replyErrorText,
      String activeCommandId,
      String commandStatusText,
      String commandErrorText,
      List<String> participantAddresses) {
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
    this.activeCommandId = nullToEmpty(activeCommandId);
    this.commandStatusText = nullToEmpty(commandStatusText);
    this.commandErrorText = nullToEmpty(commandErrorText);
    this.participantAddresses =
        participantAddresses == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(participantAddresses);
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

  public String getActiveCommandId() {
    return activeCommandId;
  }

  public String getCommandStatusText() {
    return commandStatusText;
  }

  public String getCommandErrorText() {
    return commandErrorText;
  }

  public List<String> getParticipantAddresses() {
    return participantAddresses;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

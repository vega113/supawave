package org.waveprotocol.box.j2cl.toolbar;

public enum J2clDailyToolbarAction {
  RECENT("recent", "Recent", "View", false, false),
  NEXT_UNREAD("next-unread", "Next unread", "View", false, false),
  PREVIOUS("previous", "Previous", "View", false, false),
  NEXT("next", "Next", "View", false, false),
  LAST("last", "Last", "View", false, false),
  PREVIOUS_MENTION("previous-mention", "Previous mention", "Mentions", false, false),
  NEXT_MENTION("next-mention", "Next mention", "Mentions", false, false),
  ARCHIVE("archive", "Archive", "Folders", false, false),
  INBOX("inbox", "Inbox", "Folders", false, false),
  PIN("pin", "Pin", "Folders", false, false),
  UNPIN("unpin", "Unpin", "Folders", false, false),
  HISTORY("history", "History", "Folders", false, false),
  BOLD("bold", "Bold", "Edit", true, true),
  ITALIC("italic", "Italic", "Edit", true, true),
  UNDERLINE("underline", "Underline", "Edit", true, true),
  STRIKETHROUGH("strikethrough", "Strikethrough", "Edit", true, true),
  FONT_FAMILY("font-family", "Font", "Edit", false, true),
  FONT_SIZE("font-size", "Size", "Edit", false, true),
  HEADING("heading", "Heading", "Edit", false, true),
  // Stable IDs reserved for follow-up rich block controls; Task 5 does not surface them.
  HEADING_DEFAULT("heading-default", "Default heading", "Edit", false, true),
  HEADING_H1("heading-h1", "Heading 1", "Edit", false, true),
  HEADING_H2("heading-h2", "Heading 2", "Edit", false, true),
  HEADING_H3("heading-h3", "Heading 3", "Edit", false, true),
  HEADING_H4("heading-h4", "Heading 4", "Edit", false, true),
  UNORDERED_LIST("unordered-list", "Bulleted list", "Edit", true, true),
  ORDERED_LIST("ordered-list", "Numbered list", "Edit", true, true),
  ALIGN_LEFT("align-left", "Align left", "Edit", true, true),
  ALIGN_CENTER("align-center", "Align center", "Edit", true, true),
  ALIGN_RIGHT("align-right", "Align right", "Edit", true, true),
  RTL("rtl", "Right-to-left", "Edit", true, true),
  LINK("link", "Create link", "Edit", false, true),
  UNLINK("unlink", "Remove link", "Edit", false, true),
  CLEAR_FORMATTING("clear-formatting", "Clear formatting", "Edit", false, true),
  INDENT("indent", "Indent", "Edit", false, true),
  OUTDENT("outdent", "Outdent", "Edit", false, true),
  ATTACHMENT_INSERT("attachment-insert", "Attach file", "Attachments", false, true),
  ATTACHMENT_UPLOAD_QUEUE("attachment-upload-queue", "Upload queue", "Attachments", false, true),
  ATTACHMENT_CANCEL("attachment-cancel", "Cancel upload", "Attachments", false, true),
  ATTACHMENT_PASTE_IMAGE("attachment-paste-image", "Paste image", "Attachments", false, true),
  // Stable IDs reserved for follow-up attachment controls; Task 5 does not surface them.
  ATTACHMENT_CAPTION("attachment-caption", "Attachment caption", "Attachments", false, true),
  ATTACHMENT_SIZE_SMALL("attachment-size-small", "Small attachment", "Attachments", false, true),
  ATTACHMENT_SIZE_MEDIUM("attachment-size-medium", "Medium attachment", "Attachments", false, true),
  ATTACHMENT_SIZE_LARGE("attachment-size-large", "Large attachment", "Attachments", false, true),
  ATTACHMENT_OPEN("attachment-open", "Open attachment", "Attachments", false, true),
  ATTACHMENT_DOWNLOAD("attachment-download", "Download attachment", "Attachments", false, true);

  private final String id;
  private final String label;
  private final String groupLabel;
  private final boolean toggle;
  private final boolean editAction;

  J2clDailyToolbarAction(
      String id, String label, String groupLabel, boolean toggle, boolean editAction) {
    this.id = id;
    this.label = label;
    this.groupLabel = groupLabel;
    this.toggle = toggle;
    this.editAction = editAction;
  }

  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  public String groupLabel() {
    return groupLabel;
  }

  public boolean isToggle() {
    return toggle;
  }

  public boolean isEditAction() {
    return editAction;
  }

  public static J2clDailyToolbarAction fromId(String id) {
    for (J2clDailyToolbarAction action : values()) {
      if (action.id.equals(id)) {
        return action;
      }
    }
    return null;
  }
}

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
  HEADING("heading", "Heading", "Edit", false, true),
  UNORDERED_LIST("unordered-list", "Bulleted list", "Edit", true, true),
  ORDERED_LIST("ordered-list", "Numbered list", "Edit", true, true),
  ALIGN_LEFT("align-left", "Align left", "Edit", true, true),
  ALIGN_CENTER("align-center", "Align center", "Edit", true, true),
  ALIGN_RIGHT("align-right", "Align right", "Edit", true, true),
  RTL("rtl", "Right-to-left", "Edit", true, true),
  LINK("link", "Create link", "Edit", false, true),
  UNLINK("unlink", "Remove link", "Edit", false, true),
  CLEAR_FORMATTING("clear-formatting", "Clear formatting", "Edit", false, true);

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

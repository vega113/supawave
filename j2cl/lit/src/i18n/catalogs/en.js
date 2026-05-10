// English catalog for the J2CL Lit shell. The catalog file is plain JS so
// it works under both the esbuild bundler and the web-test-runner native
// ESM loader without import-attribute differences.
//
// Keys are namespaced by source file (e.g. waveActions.*, composer.*,
// formatToolbar.*) so a future migration to per-component catalogs is a
// mechanical rename. See ../README.md for naming convention and how to add
// a new locale.

export const en = {
  // wavy-wave-header-actions.js
  "waveActions.toolbarLabel": "Wave header actions",
  "waveActions.addParticipant": "Add participant",
  "waveActions.newWithParticipants": "New wave with current participants",
  "waveActions.newWithParticipantsShort": "New with participants",
  "waveActions.makePublic": "Make wave public",
  "waveActions.makePrivate": "Make wave private",
  "waveActions.public": "Public",
  "waveActions.private": "Private",
  "waveActions.lockRoot": "Lock root blip",
  "waveActions.lockFull": "Lock full wave",
  "waveActions.unlock": "Unlock wave",
  "waveActions.lockedRoot": "Root locked",
  "waveActions.lockedAll": "Wave locked",
  "waveActions.unlocked": "Unlocked",
  "waveActions.confirmPublic": "Make this wave public?",
  "waveActions.confirmPrivate": "Make this wave private?",
  "waveActions.confirmLockRoot": "Lock the root blip?",
  "waveActions.confirmLockAll": "Lock the full wave?",
  "waveActions.confirmUnlock": "Unlock this wave?",
  "waveActions.confirmLabelMakePublic": "Make public",
  "waveActions.confirmLabelMakePrivate": "Make private",
  "waveActions.confirmLabelLockRoot": "Lock root",
  "waveActions.confirmLabelLockAll": "Lock all",
  "waveActions.confirmLabelUnlock": "Unlock",
  "waveActions.participantAddresses": "Participant addresses",
  "waveActions.participantPlaceholder": "alice@example.com, bob@example.com",
  "waveActions.frequentContacts": "Frequent contacts",
  "waveActions.loadingFrequentContacts": "Loading frequent contacts...",
  "waveActions.cancel": "Cancel",
  "waveActions.add": "Add",

  // wavy-header.js
  "header.brand": "SupaWave home",
  "header.localePicker": "Language",
  "header.notifications": "Notifications",
  "header.inbox": "Inbox",
  "header.userMenu": "Open user menu",
  "header.saveSaving": "Saving changes",
  "header.saveUnsaved": "Unsaved changes",
  "header.saveSaved": "All changes saved",
  "header.netOffline": "Offline",
  "header.netConnecting": "Connecting",
  "header.netOnline": "Online",

  // common
  "common.cancel": "Cancel",
  "common.confirm": "Confirm",
  "common.close": "Close",
  "common.delete": "Delete",
  "common.save": "Save",
  "common.add": "Add",
  "common.remove": "Remove",
  "common.dismiss": "Dismiss",

  // wavy-back-to-inbox.js
  "backToInbox.label": "Back to inbox",

  // wavy-floating-scroll-to-new.js
  "scrollToNew.label": "Scroll to new messages",
  "scrollToNew.newSuffix": "new",

  // wavy-nav-drawer-toggle.js
  "navDrawer.open": "Open navigation drawer",
  "navDrawer.close": "Close navigation drawer",

  // wavy-wave-controls-toggle.js
  "waveControls.open": "Show wave controls",
  "waveControls.close": "Hide wave controls",

  // wavy-search-rail.js / wavy-search-rail-card.js
  "searchRail.title": "Search",
  "searchRail.help": "Search help",
  "searchRail.refresh": "Refresh search results",
  "searchRail.placeholder": "Search waves",
  "searchRail.searchButton": "Search",
  "searchRail.clear": "Clear search",
  "searchRail.openWave": "Open wave",
  "searchRail.markRead": "Mark as read",
  "searchRail.markUnread": "Mark as unread",
  "searchRail.archive": "Archive",
  "searchRail.unarchive": "Restore from archive",

  // wavy-search-help.js
  "searchHelp.title": "Search help",
  "searchHelp.close": "Close search help",
  "searchHelp.gotIt": "Got it",

  // wavy-profile-overlay.js
  "profileOverlay.title": "Profile",
  "profileOverlay.close": "Close profile",
  "profileOverlay.signOut": "Sign out",

  // wavy-version-history.js
  "versionHistory.title": "Version history",
  "versionHistory.close": "Close version history",
  "versionHistory.previous": "Previous version",
  "versionHistory.next": "Next version",

  // wavy-confirm-dialog.js
  "confirmDialog.cancel": "Cancel",
  "confirmDialog.confirm": "Confirm",

  // wavy-link-modal.js
  "linkModal.title": "Insert link",
  "linkModal.urlLabel": "URL",
  "linkModal.textLabel": "Display text",
  "linkModal.cancel": "Cancel",
  "linkModal.insert": "Insert",
  "linkModal.remove": "Remove link",

  // wavy-tags-row.js
  "tagsRow.add": "Add tag",
  "tagsRow.placeholder": "Add a tag",
  "tagsRow.remove": "Remove tag",

  // wavy-task-affordance.js
  "taskAffordance.toggle": "Toggle task",
  "taskAffordance.markComplete": "Mark task complete",
  "taskAffordance.markIncomplete": "Mark task incomplete",

  // task-metadata-popover.js
  "taskMetadata.title": "Task details",
  "taskMetadata.owner": "Owner",
  "taskMetadata.dueDate": "Due date",
  "taskMetadata.clearDueDate": "Clear due date",
  "taskMetadata.close": "Close task details",
  "taskMetadata.save": "Save",

  // wavy-composer.js
  "composer.placeholder": "Write a reply...",
  "composer.send": "Send",
  "composer.cancel": "Cancel",
  "composer.discard": "Discard draft",

  // wavy-format-toolbar.js
  "formatToolbar.label": "Formatting",
  "formatToolbar.bold": "Bold",
  "formatToolbar.italic": "Italic",
  "formatToolbar.underline": "Underline",
  "formatToolbar.strikethrough": "Strikethrough",
  "formatToolbar.headingMenu": "Heading style",
  "formatToolbar.heading1": "Heading 1",
  "formatToolbar.heading2": "Heading 2",
  "formatToolbar.heading3": "Heading 3",
  "formatToolbar.paragraph": "Paragraph",
  "formatToolbar.link": "Insert link",
  "formatToolbar.bulletList": "Bullet list",
  "formatToolbar.numberedList": "Numbered list",
  "formatToolbar.indent": "Increase indent",
  "formatToolbar.outdent": "Decrease indent",
  "formatToolbar.alignLeft": "Align left",
  "formatToolbar.alignCenter": "Align center",
  "formatToolbar.alignRight": "Align right",
  "formatToolbar.color": "Text color",
  "formatToolbar.clearFormatting": "Clear formatting",

  // wavy-colorpicker-popover.js
  "colorPicker.title": "Choose color",
  "colorPicker.close": "Close color picker",
  "colorPicker.reset": "Reset color",

  // composer-submit-affordance.js
  "composerSubmit.label": "Send",
  "composerSubmit.shortcutHint": "Send (Ctrl+Enter)",

  // compose-attachment-picker.js / compose-attachment-card.js
  "attachments.add": "Add attachment",
  "attachments.remove": "Remove attachment",
  "attachments.replace": "Replace attachment",
  "attachments.cardLabel": "Attachment",

  // reaction-picker-popover.js / reaction-row.js
  "reactions.add": "Add reaction",
  "reactions.pickerTitle": "Pick a reaction",
  "reactions.viewAuthors": "View people who reacted",
  "reactions.close": "Close reactions",
  "reactions.removeYours": "Remove your reaction",

  // mention-suggestion-popover.js
  "mentions.title": "Mention",

  // toolbar-button.js / toolbar-overflow-menu.js
  "toolbar.overflowMenu": "More actions",

  // shell-nav-rail.js
  "navRail.label": "Primary navigation",
  "navRail.toggle": "Toggle navigation",

  // shell-status-strip.js
  "statusStrip.label": "Status",

  // wave-blip-toolbar.js / wave-blip.js
  "blipToolbar.label": "Blip actions",
  "blipToolbar.reply": "Reply",
  "blipToolbar.edit": "Edit",
  "blipToolbar.delete": "Delete blip",
  "blipToolbar.more": "More blip actions",
  "blip.openThread": "Open thread",

  // wavy-depth-nav-bar.js
  "depthNav.label": "Thread navigation",
  "depthNav.up": "Go up",
  "depthNav.down": "Go down",

  // wavy-wave-nav-row.js
  "waveNav.label": "Wave navigation",
  "waveNav.archive": "Move wave to archive",
  "waveNav.unarchive": "Restore from archive",
  "waveNav.pin": "Pin wave",
  "waveNav.unpin": "Unpin wave",
  "waveNav.versionHistory": "Show version history",

  // wavy-wave-root-reply-trigger.js
  "rootReply.label": "Reply at top",
  "rootReply.aria": "Reply to the wave",

  // shell-skip-link.js
  "skipLink.label": "Skip to main content",

  // ---- additional keys exercised by the Phase 2 sweep (kept here so
  // the catalog is the source of truth and a future translator only has
  // to scan one file). All values are ASCII / English; non-English
  // catalogs only need to override the user-visible strings they care
  // to translate. ----

  // attachments
  "attachments.attachFile": "Attach file",
  "attachments.dialogLabel": "Attach a file",
  "attachments.fileLabel": "File",
  "attachments.captionLabel": "Caption",
  "attachments.displaySize": "Display size",
  "attachments.uploadProgress": "Attachment upload progress",
  "attachments.uploadFailed": "Attachment upload failed.",
  "attachments.blocked": "Attachment blocked.",
  "attachments.open": "Open",
  "attachments.openPrefix": "Open attachment",
  "attachments.download": "Download",
  "attachments.downloadPrefix": "Download attachment",

  // wave-blip
  "blip.expand": "Expand",
  "blip.collapse": "Collapse",
  "blip.reply": "reply",
  "blip.replies": "replies",
  "blip.underThisBlipSuffix": "under this blip",
  "blip.openAuthorProfile": "Open {name} profile",

  // wave-blip-toolbar
  "blipToolbar.replyAction": "Reply to this blip",
  "blipToolbar.editAction": "Edit this blip",
  "blipToolbar.deleteAction": "Delete this blip",
  "blipToolbar.deleteShort": "Delete",
  "blipToolbar.linkAction": "Copy permalink to this blip",
  "blipToolbar.linkShort": "Link",

  // wavy-colorpicker-popover
  "colorPicker.highlightPalette": "Highlight color palette",
  "colorPicker.textPalette": "Text color palette",

  // wavy-composer
  "composer.editBlip": "Edit blip",
  "composer.replyToWave": "Reply to wave",
  "composer.newWave": "New wave",
  "composer.replyToPrefix": "Reply to",
  "composer.replyLabel": "Reply",

  // depth-nav
  "depthNav.upOneLevel": "Up one level",
  "depthNav.upOneLevelToNameThread": "Up one level to {name}'s thread",
  "depthNav.backToTop": "Back to top of wave",
  "depthNav.upToWave": "Up to wave",

  // interaction-overlay-layer
  "interactionOverlay.defaultLabel": "Interaction overlay",

  // mentions
  "mentions.suggestions": "Mention suggestions",
  "mentions.noMatches": "No mention matches",

  // profile-overlay
  "profileOverlay.previous": "Previous participant",
  "profileOverlay.next": "Next participant",
  "profileOverlay.online": "Online",
  "profileOverlay.lastSeenPrefix": "Last seen",
  "profileOverlay.botProfile": "Bot profile",
  "profileOverlay.profile": "Profile",
  "profileOverlay.sendMessage": "Send Message",
  "profileOverlay.editProfile": "Edit Profile",

  // reactions
  "reactions.rowLabel": "Reactions",
  "reactions.pick": "React with",
  "reactions.toggle": "Toggle",
  "reactions.reactionSuffix": "reaction",
  "reactions.reactionsSuffix": "reactions",
  "reactions.person": "person",
  "reactions.people": "people",
  "reactions.inspectPrefix": "Inspect",
  "reactions.authorsShort": "authors",
  "reactions.authorsForPrefix": "Authors for",
  "reactions.thisReaction": "this reaction",
  "reactions.noneYet": "No reactions yet",

  // search rail
  "searchRail.searchWaves": "Search waves",
  "searchRail.actions": "Search actions",
  "searchRail.sort": "Sort waves",
  "searchRail.filter": "Filter waves",
  "searchRail.filters": "Filters",
  "searchRail.filtersGroup": "Search filters",
  "searchRail.newWave": "New Wave",
  "searchRail.manageSaved": "Manage saved searches",
  "searchRail.savedSearches": "Saved searches",
  "searchRail.unreadMentions": "unread mentions",
  "searchRail.pendingTasks": "pending tasks",
  "searchRail.pinnedSaved": "Pinned saved searches",
  "searchRail.applySavedPrefix": "Apply saved search",
  "searchRail.closeSaved": "Close saved searches",
  "searchRail.applyDiscardsHint": "Apply closes this dialog and discards unsaved edits. Use Save to persist them.",
  "searchRail.savedLoading": "Loading saved searches…",
  "searchRail.savedEmpty": "No saved searches yet. Add the current query to create one.",
  "searchRail.savedName": "Saved search name",
  "searchRail.savedQuery": "Saved search query",
  "searchRail.savedFallback": "saved search",
  "searchRail.applyPrefix": "Apply",
  "searchRail.applyShort": "Apply",
  "searchRail.removePrefix": "Remove",
  "searchRail.pinShort": "Pin",
  "searchRail.addCurrent": "Add current search",
  "searchRail.cardAuthors": "Authors",
  "searchRail.cardAndMorePrefix": "and",
  "searchRail.cardMoreSuffix": "more",
  "searchRail.cardPinned": "Pinned",
  "searchRail.cardMessageCount": "Message count",
  "searchRail.cardUnread": "unread",
  "searchRail.cardNoTitle": "(no title)",

  // status strip
  "statusStrip.online": "Online",
  "statusStrip.offline": "Offline",
  "statusStrip.saving": "Saving changes",
  "statusStrip.saved": "Saved",
  "statusStrip.selectedWave": "Selected wave active",
  "statusStrip.search": "Search results active",
  "statusStrip.loading": "Loading workspace",
  "statusStrip.ready": "Workspace ready",

  // tags row
  "tagsRow.tagsLabel": "Tags:",
  "tagsRow.removePrefix": "Remove tag",
  "tagsRow.cancel": "Cancel tag entry",

  // task affordance
  "taskAffordance.editDetails": "Edit task details",

  // task metadata
  "taskMetadata.assignee": "Assignee",
  "taskMetadata.unassigned": "Unassigned",
  "taskMetadata.dueDatePlaceholder": "YYYY-MM-DD",

  // version history
  "versionHistory.exit": "Exit version history",
  "versionHistory.loading": "Loading…",
  "versionHistory.empty": "No versions loaded yet",
  "versionHistory.currentPreviewPrefix": "Current preview:",
  "versionHistory.slider": "Version history time slider",
  "versionHistory.showChanges": "Show changes",
  "versionHistory.textOnly": "Text only",
  "versionHistory.restore": "Restore version",
  "versionHistory.restorePrefix": "Restore version",
  "versionHistory.restoreShort": "Restore",
  "versionHistory.previewOnly": "Preview only — restore not available",
  "versionHistory.confirmExplanation": "This rewrites the wave to this point.",
  "versionHistory.preview": "Read-only version preview",
  "versionHistory.version": "Version",
  "versionHistory.participants": "participants",
  "versionHistory.documents": "documents",

  // wave-actions popover extras
  "waveActions.addContactPrefix": "Add",

  // wave-nav extras (granular per-button keys for Phase 2 sweep)
  "waveNav.recent": "Jump to recent activity",
  "waveNav.nextUnread": "Jump to next unread blip",
  "waveNav.previous": "Jump to previous blip",
  "waveNav.next": "Jump to next blip",
  "waveNav.end": "Jump to last blip",
  "waveNav.prevMention": "Jump to previous mention",
  "waveNav.nextMention": "Jump to next mention",
  "waveNav.versionHistoryShortcut": "Open version history (h)",
  "waveNav.overflow": "More wave navigation actions"
};

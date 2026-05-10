// German catalog for the J2CL Lit shell.
//
// Where a key is semantically equivalent to a string already shipped by the
// GWT side under `wave/src/main/resources/.../*Messages_de.properties`, this
// file reuses the existing translation verbatim so the J2CL UI cannot drift
// from the legacy UI for shared terms (online/offline, signout, archive,
// pin/unpin, etc.). Net-new keys are translated conservatively; anything
// uncertain stays in English with a `// TODO de:` annotation rather than
// shipping a wrong word — see ../README.md.

export const de = {
  // wavy-wave-header-actions.js
  "waveActions.toolbarLabel": "Wave-Aktionen",
  "waveActions.addParticipant": "Teilnehmer hinzufügen",
  "waveActions.newWithParticipants": "Neue Wave mit aktuellen Teilnehmern",
  "waveActions.newWithParticipantsShort": "Neu mit Teilnehmern",
  // Reused from GWT ParticipantMessages_de.properties (waveIsPrivateClickToMakePublic)
  "waveActions.makePublic": "Wave öffentlich machen",
  "waveActions.makePrivate": "Wave privat machen",
  "waveActions.public": "Öffentlich",
  "waveActions.private": "Privat",
  "waveActions.lockRoot": "Wurzel-Blip sperren",
  "waveActions.lockFull": "Gesamte Wave sperren",
  "waveActions.unlock": "Wave entsperren",
  "waveActions.lockedRoot": "Wurzel gesperrt",
  "waveActions.lockedAll": "Wave gesperrt",
  "waveActions.unlocked": "Entsperrt",
  "waveActions.confirmPublic": "Diese Wave öffentlich machen?",
  "waveActions.confirmPrivate": "Diese Wave privat machen?",
  "waveActions.confirmLockRoot": "Wurzel-Blip sperren?",
  "waveActions.confirmLockAll": "Gesamte Wave sperren?",
  "waveActions.confirmUnlock": "Diese Wave entsperren?",
  "waveActions.confirmLabelMakePublic": "Öffentlich machen",
  "waveActions.confirmLabelMakePrivate": "Privat machen",
  "waveActions.confirmLabelLockRoot": "Wurzel sperren",
  "waveActions.confirmLabelLockAll": "Alles sperren",
  "waveActions.confirmLabelUnlock": "Entsperren",
  "waveActions.participantAddresses": "Teilnehmer-Adressen",
  "waveActions.participantPlaceholder": "alice@example.com, bob@example.com",
  "waveActions.frequentContacts": "Häufige Kontakte",
  "waveActions.loadingFrequentContacts": "Häufige Kontakte werden geladen...",
  "waveActions.cancel": "Abbrechen",
  "waveActions.add": "Hinzufügen",

  // wavy-header.js — reused from SavedStateMessages_de + WebClientMessages_de
  "header.brand": "SupaWave Startseite",
  "header.localePicker": "Sprache",
  "header.notifications": "Benachrichtigungen",
  "header.inbox": "Postfach", // GWT SearchWidgetMessages_de.properties: inbox
  "header.userMenu": "Benutzermenü öffnen",
  "header.saveSaving": "Änderungen werden gespeichert",
  // GWT SavedStateMessages_de.properties: unsaved
  "header.saveUnsaved": "Noch nicht gespeichert",
  // GWT SavedStateMessages_de.properties: saved
  "header.saveSaved": "Gespeichert",
  // GWT WebClientMessages_de.properties: offline / online / connecting
  "header.netOffline": "Offline",
  "header.netConnecting": "Verbinde...",
  "header.netOnline": "Online",

  // common
  "common.cancel": "Abbrechen",
  "common.confirm": "Bestätigen",
  "common.close": "Schließen",
  "common.delete": "Löschen",
  "common.save": "Speichern",
  "common.add": "Hinzufügen",
  // GWT ParticipantMessages_de.properties: remove
  "common.remove": "Entfernen",
  "common.dismiss": "Schließen",

  "backToInbox.label": "Zurück zum Postfach",

  "scrollToNew.label": "Zu neuen Nachrichten springen",

  "navDrawer.open": "Navigation öffnen",
  "navDrawer.close": "Navigation schließen",

  "waveControls.open": "Wave-Steuerung anzeigen",
  "waveControls.close": "Wave-Steuerung ausblenden",

  "searchRail.title": "Suche",
  "searchRail.help": "Suchhilfe",
  "searchRail.refresh": "Suchergebnisse aktualisieren",
  "searchRail.placeholder": "Waves durchsuchen",
  "searchRail.searchButton": "Suchen",
  "searchRail.clear": "Suche zurücksetzen",
  "searchRail.openWave": "Wave öffnen",
  "searchRail.markRead": "Als gelesen markieren",
  "searchRail.markUnread": "Als ungelesen markieren",
  // GWT ToolbarMessages_de.properties: toArchive
  "searchRail.archive": "Zum Archiv",
  "searchRail.unarchive": "Aus dem Archiv wiederherstellen",

  "searchHelp.title": "Suchhilfe",
  "searchHelp.close": "Suchhilfe schließen",

  "profileOverlay.title": "Profil",
  "profileOverlay.close": "Profil schließen",
  // GWT WebClientMessages_de.properties: signout
  "profileOverlay.signOut": "Abmelden",

  "versionHistory.title": "Versionsverlauf",
  "versionHistory.close": "Versionsverlauf schließen",
  // GWT ToolbarMessages_de.properties: previous / next
  "versionHistory.previous": "Vorherig",
  "versionHistory.next": "Nächster",

  "confirmDialog.cancel": "Abbrechen",
  "confirmDialog.confirm": "Bestätigen",

  "linkModal.title": "Link einfügen",
  "linkModal.urlLabel": "URL",
  "linkModal.textLabel": "Anzeigetext",
  "linkModal.cancel": "Abbrechen",
  "linkModal.insert": "Einfügen",
  "linkModal.remove": "Link entfernen",

  "tagsRow.add": "Tag hinzufügen",
  "tagsRow.placeholder": "Tag hinzufügen",
  "tagsRow.remove": "Tag entfernen",

  "taskAffordance.toggle": "Aufgabe umschalten",
  "taskAffordance.markComplete": "Aufgabe als erledigt markieren",
  "taskAffordance.markIncomplete": "Aufgabe als unerledigt markieren",

  "taskMetadata.title": "Aufgaben-Details",
  "taskMetadata.owner": "Verantwortlich",
  "taskMetadata.dueDate": "Fälligkeitsdatum",
  "taskMetadata.clearDueDate": "Fälligkeitsdatum entfernen",
  "taskMetadata.close": "Aufgaben-Details schließen",
  "taskMetadata.save": "Speichern",

  "composer.placeholder": "Antwort schreiben...",
  "composer.send": "Senden",
  "composer.cancel": "Abbrechen",
  "composer.discard": "Entwurf verwerfen",

  "formatToolbar.label": "Formatierung",
  "formatToolbar.bold": "Fett",
  "formatToolbar.italic": "Kursiv",
  "formatToolbar.underline": "Unterstrichen",
  "formatToolbar.strikethrough": "Durchgestrichen",
  "formatToolbar.headingMenu": "Überschrift",
  "formatToolbar.heading1": "Überschrift 1",
  "formatToolbar.heading2": "Überschrift 2",
  "formatToolbar.heading3": "Überschrift 3",
  "formatToolbar.paragraph": "Absatz",
  "formatToolbar.link": "Link einfügen",
  "formatToolbar.bulletList": "Aufzählung",
  "formatToolbar.numberedList": "Nummerierte Liste",
  "formatToolbar.indent": "Einzug vergrößern",
  "formatToolbar.outdent": "Einzug verkleinern",
  "formatToolbar.alignLeft": "Linksbündig",
  "formatToolbar.alignCenter": "Zentriert",
  "formatToolbar.alignRight": "Rechtsbündig",
  "formatToolbar.color": "Textfarbe",
  "formatToolbar.clearFormatting": "Formatierung entfernen",

  "colorPicker.title": "Farbe wählen",
  "colorPicker.close": "Farbauswahl schließen",
  "colorPicker.reset": "Farbe zurücksetzen",

  "composerSubmit.label": "Senden",
  "composerSubmit.shortcutHint": "Senden (Strg+Enter)",

  "attachments.add": "Anhang hinzufügen",
  "attachments.remove": "Anhang entfernen",
  "attachments.replace": "Anhang ersetzen",
  "attachments.cardLabel": "Anhang",

  "reactions.add": "Reaktion hinzufügen",
  "reactions.pickerTitle": "Reaktion wählen",
  "reactions.viewAuthors": "Personen anzeigen, die reagiert haben",
  "reactions.close": "Reaktionen schließen",
  "reactions.removeYours": "Eigene Reaktion entfernen",

  "mentions.title": "Erwähnung",

  "toolbar.overflowMenu": "Weitere Aktionen",

  "navRail.label": "Hauptnavigation",
  "navRail.toggle": "Navigation umschalten",

  "statusStrip.label": "Status",

  "blipToolbar.label": "Blip-Aktionen",
  "blipToolbar.reply": "Antworten",
  "blipToolbar.edit": "Bearbeiten",
  "blipToolbar.delete": "Blip löschen",
  "blipToolbar.more": "Weitere Blip-Aktionen",
  "blip.openThread": "Thread öffnen",

  "depthNav.label": "Thread-Navigation",
  "depthNav.up": "Nach oben",
  "depthNav.down": "Nach unten",

  "waveNav.label": "Wave-Navigation",
  // GWT ToolbarMessages_de.properties: toArchive
  "waveNav.archive": "Zum Archiv",
  "waveNav.unarchive": "Aus dem Archiv wiederherstellen",
  // GWT ToolbarMessages_de.properties: pin / unpin
  "waveNav.pin": "Anheften",
  "waveNav.unpin": "Lösen",
  "waveNav.versionHistory": "Versionsverlauf anzeigen",

  "rootReply.label": "Oben antworten",
  "rootReply.aria": "Auf die Wave antworten",

  "skipLink.label": "Zum Hauptinhalt springen",

  // Phase 2 additions — translated where confident; uncertain entries
  // are intentionally left in English so the t() fallback chain serves
  // a clean string instead of mojibake. See README.md.

  "attachments.attachFile": "Datei anhängen",
  "attachments.dialogLabel": "Datei anhängen",
  "attachments.fileLabel": "Datei",
  "attachments.captionLabel": "Beschriftung",
  "attachments.displaySize": "Anzeigegröße",
  "attachments.uploadProgress": "Anhang-Upload-Fortschritt",
  "attachments.uploadFailed": "Anhang-Upload fehlgeschlagen.",
  "attachments.blocked": "Anhang blockiert.",
  "attachments.open": "Öffnen",
  "attachments.openPrefix": "Anhang öffnen",
  "attachments.download": "Herunterladen",
  "attachments.downloadPrefix": "Anhang herunterladen",

  "blip.expand": "Aufklappen",
  "blip.collapse": "Einklappen",
  "blip.reply": "Antwort",
  "blip.replies": "Antworten",
  "blip.underThisBlipSuffix": "unter diesem Blip",
  "blip.openProfilePrefix": "Profil öffnen:",
  "blip.profileSuffix": "",

  "blipToolbar.replyAction": "Auf diesen Blip antworten",
  "blipToolbar.editAction": "Diesen Blip bearbeiten",
  "blipToolbar.deleteAction": "Diesen Blip löschen",
  "blipToolbar.deleteShort": "Löschen",
  "blipToolbar.linkAction": "Permalink zu diesem Blip kopieren",
  "blipToolbar.linkShort": "Link",

  "colorPicker.highlightPalette": "Hervorhebungs-Farbpalette",
  "colorPicker.textPalette": "Text-Farbpalette",

  "composer.editBlip": "Blip bearbeiten",
  "composer.replyToWave": "Auf Wave antworten",
  "composer.newWave": "Neue Wave",
  "composer.replyToPrefix": "Antwort an",
  "composer.replyLabel": "Antworten",

  "depthNav.upOneLevel": "Eine Ebene nach oben",
  "depthNav.upOneLevelTo": "Eine Ebene nach oben zu",
  "depthNav.upOneLevelThreadSuffix": "s Thread",
  "depthNav.backToTop": "Zurück zum Anfang der Wave",
  "depthNav.upToWave": "Zur Wave",

  "interactionOverlay.defaultLabel": "Interaktions-Overlay",

  "mentions.suggestions": "Erwähnungs-Vorschläge",
  "mentions.noMatches": "Keine Treffer für Erwähnungen",

  "profileOverlay.previous": "Vorheriger Teilnehmer",
  "profileOverlay.next": "Nächster Teilnehmer",
  "profileOverlay.online": "Online",
  "profileOverlay.lastSeenPrefix": "Zuletzt gesehen",
  "profileOverlay.botProfile": "Bot-Profil",
  "profileOverlay.profile": "Profil",
  "profileOverlay.sendMessage": "Nachricht senden",
  "profileOverlay.editProfile": "Profil bearbeiten",

  "reactions.rowLabel": "Reaktionen",
  "reactions.pick": "Reagieren mit",
  "reactions.toggle": "Umschalten",
  "reactions.reactionSuffix": "Reaktion",
  "reactions.reactionsSuffix": "Reaktionen",
  "reactions.person": "Person",
  "reactions.people": "Personen",
  "reactions.inspectPrefix": "Ansehen",
  "reactions.authorsShort": "Autoren",
  "reactions.authorsForPrefix": "Reaktionen von",
  "reactions.thisReaction": "diese Reaktion",
  "reactions.noneYet": "Noch keine Reaktionen",

  "searchRail.searchWaves": "Waves durchsuchen",
  "searchRail.actions": "Suchaktionen",
  "searchRail.sort": "Waves sortieren",
  "searchRail.filter": "Waves filtern",
  "searchRail.filters": "Filter",
  "searchRail.filtersGroup": "Suchfilter",
  // GWT SearchPresenterMessages_de.properties: newWave
  "searchRail.newWave": "Neue Wave",
  "searchRail.manageSaved": "Gespeicherte Suchen verwalten",
  // GWT SearchPresenterMessages_de.properties: savedSearches
  "searchRail.savedSearches": "Gespeicherte Suchen",
  "searchRail.unreadMentions": "ungelesene Erwähnungen",
  "searchRail.pendingTasks": "offene Aufgaben",
  "searchRail.pinnedSaved": "Angeheftete gespeicherte Suchen",
  "searchRail.applySavedPrefix": "Gespeicherte Suche anwenden",
  "searchRail.closeSaved": "Gespeicherte Suchen schließen",
  "searchRail.applyDiscardsHint": "Anwenden schließt diesen Dialog und verwirft nicht gespeicherte Änderungen. Mit Speichern dauerhaft sichern.",
  "searchRail.savedLoading": "Gespeicherte Suchen werden geladen…",
  "searchRail.savedEmpty": "Noch keine gespeicherten Suchen. Aktuelle Suche hinzufügen, um eine zu erstellen.",
  "searchRail.savedName": "Name der gespeicherten Suche",
  "searchRail.savedQuery": "Anfrage der gespeicherten Suche",
  "searchRail.savedFallback": "gespeicherte Suche",
  "searchRail.applyPrefix": "Anwenden",
  "searchRail.applyShort": "Anwenden",
  "searchRail.removePrefix": "Entfernen",
  "searchRail.pinShort": "Anheften",
  "searchRail.addCurrent": "Aktuelle Suche hinzufügen",
  "searchRail.cardAuthors": "Autoren",
  "searchRail.cardAndMorePrefix": "und",
  "searchRail.cardMoreSuffix": "weitere",
  "searchRail.cardPinned": "Angeheftet",
  "searchRail.cardMessageCount": "Nachrichtenzahl",
  "searchRail.cardUnread": "ungelesen",
  "searchRail.cardNoTitle": "(kein Titel)",

  // GWT WebClientMessages_de.properties: online / offline / connecting
  "statusStrip.online": "Online",
  "statusStrip.offline": "Offline",
  // GWT SavedStateMessages_de.properties: unsaved (re-purposed for in-progress save)
  "statusStrip.saving": "Wird gespeichert",
  "statusStrip.saved": "Gespeichert",
  "statusStrip.selectedWave": "Ausgewählte Wave aktiv",
  "statusStrip.search": "Suchergebnisse aktiv",
  "statusStrip.loading": "Arbeitsbereich wird geladen",
  "statusStrip.ready": "Arbeitsbereich bereit",

  "tagsRow.tagsLabel": "Tags:",
  "tagsRow.removePrefix": "Tag entfernen",
  "tagsRow.cancel": "Tag-Eingabe abbrechen",

  "taskAffordance.editDetails": "Aufgaben-Details bearbeiten",

  "taskMetadata.assignee": "Verantwortlich",
  "taskMetadata.unassigned": "Nicht zugewiesen",
  "taskMetadata.dueDatePlaceholder": "JJJJ-MM-TT",

  "versionHistory.exit": "Versionsverlauf beenden",
  "versionHistory.loading": "Wird geladen…",
  "versionHistory.empty": "Noch keine Versionen geladen",
  "versionHistory.currentPreviewPrefix": "Aktuelle Vorschau:",
  "versionHistory.slider": "Versions-Zeitschieberegler",
  "versionHistory.showChanges": "Änderungen anzeigen",
  "versionHistory.textOnly": "Nur Text",
  "versionHistory.restore": "Version wiederherstellen",
  "versionHistory.restorePrefix": "Version wiederherstellen:",
  "versionHistory.restoreShort": "Wiederherstellen",
  "versionHistory.previewOnly": "Nur Vorschau — Wiederherstellen nicht verfügbar",
  "versionHistory.confirmExplanation": "Dies setzt die Wave auf diesen Stand zurück.",
  "versionHistory.preview": "Schreibgeschützte Versionsvorschau",

  "waveActions.addContactPrefix": "Hinzufügen:",

  "waveNav.recent": "Zur letzten Aktivität springen",
  "waveNav.nextUnread": "Zum nächsten ungelesenen Blip springen",
  "waveNav.previous": "Zum vorherigen Blip springen",
  "waveNav.next": "Zum nächsten Blip springen",
  "waveNav.end": "Zum letzten Blip springen",
  "waveNav.prevMention": "Zur vorherigen Erwähnung springen",
  "waveNav.nextMention": "Zur nächsten Erwähnung springen",
  "waveNav.versionHistoryShortcut": "Versionsverlauf öffnen (h)",
  "waveNav.overflow": "Weitere Wave-Navigations-Aktionen"
};

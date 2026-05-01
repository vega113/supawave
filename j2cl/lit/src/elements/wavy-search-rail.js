import { LitElement, css, html } from "lit";
import {
  SEARCH_RAIL_ICON_REFRESH,
  SEARCH_RAIL_ICON_SORT,
  SEARCH_RAIL_ICON_FILTER
} from "../icons/search-rail-icons.js";

/**
 * <wavy-search-rail> — G-PORT-2 (#1111) left-rail search surface.
 *
 * Cloned from the GWT search panel chrome (SearchPanelWidget,
 * SearchPresenter.initToolbarMenu, SearchPanel.css) so a freshly
 * registered user sees the same set of affordances on
 * `?view=j2cl-root` as on `?view=gwt`:
 *
 *   - query textbox + waveform glyph + help-trigger ("?")
 *   - panel-level action row [Refresh] [Sort] [Filter] (G-PORT-2 new)
 *   - New Wave + Manage saved searches buttons
 *   - Saved-search folder list (Inbox/Mentions/Tasks/Public/Archive/Pinned)
 *   - Filter-chip strip (collapsed under the Filter button)
 *   - Result count
 *   - Slot for <wavy-search-rail-card> digest cards
 *
 * The G-PORT-2 action-row is the headline change: the row carries
 * `data-digest-action-row` so the parity test resolves it on both
 * views with one selector, and each button carries
 * `data-digest-action="refresh|sort|filter"`. The buttons emit:
 *
 *   - refresh → wavy-search-refresh-requested (existing event)
 *   - sort    → wavy-search-sort-requested with the selected orderby
 *               token, plus wavy-search-submit when the query changes
 *   - filter  → wavy-search-filter-toggle-requested (G-PORT-2 new;
 *               toggles the existing <details> filter chip strip
 *               open / closed)
 *
 * All previously-existing events still fire (wavy-search-submit,
 * wavy-search-help-toggle, wavy-new-wave-requested,
 * wavy-manage-saved-searches-requested, wavy-saved-search-selected,
 * wavy-search-filter-toggled). Built-in folder selections emit one of
 * the six canonical folderId values; custom saved searches emit
 * `kind: "custom"` and an empty folderId so folder-id consumers do not
 * treat saved-search names as built-in folders.
 */
export class WavySearchRail extends LitElement {
  static properties = {
    query: { type: String, reflect: true },
    activeFolder: { type: String, attribute: "data-active-folder", reflect: true },
    resultCount: { type: String, attribute: "result-count", reflect: true },
    mentionsUnread: { type: Number, attribute: "mentions-unread" },
    tasksPending: { type: Number, attribute: "tasks-pending" },
    savedSearches: { state: true },
    savedSearchDrafts: { state: true },
    savedSearchesOpen: { state: true },
    savedSearchesLoading: { state: true },
    savedSearchesError: { state: true },
    savedSearchesDirty: { state: true },
    sortOpen: { state: true },
    /**
     * G-PORT-2: track whether the filter chip strip is open. The
     * panel-level Filter action button toggles this. The user-driven
     * <details> open state still works because we drive `open` via a
     * reactive binding, not a one-shot init.
     */
    filtersOpen: { type: Boolean, attribute: "filters-open", reflect: true }
  };

  static FOLDERS = [
    { id: "inbox", label: "Inbox", query: "in:inbox" },
    { id: "mentions", label: "Mentions", query: "mentions:me" },
    { id: "tasks", label: "Tasks", query: "tasks:me" },
    { id: "public", label: "Public", query: "with:@" },
    { id: "archive", label: "Archive", query: "in:archive" },
    { id: "pinned", label: "Pinned", query: "in:pinned" }
  ];

  static FILTERS = [
    { id: "unread", label: "Unread only", token: "is:unread" },
    { id: "attachments", label: "With attachments", token: "has:attachment" },
    { id: "from-me", label: "From me", token: "from:me" }
  ];

  static SORTS = [
    { id: "datedesc", label: "Newest activity", token: "orderby:datedesc", default: true },
    { id: "dateasc", label: "Oldest activity", token: "orderby:dateasc" },
    { id: "createddesc", label: "Newest created", token: "orderby:createddesc" },
    { id: "createdasc", label: "Oldest created", token: "orderby:createdasc" }
  ];

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      min-height: var(--wavy-rail-min-height, calc(100vh - 90px));
      padding: 0;
      background: var(--wavy-bg-base, #ffffff);
      color: var(--wavy-text-body, #1a202c);
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
      min-width: 280px;
    }
    .search {
      position: relative;
      display: flex;
      align-items: center;
      gap: 6px;
      height: 41px;
      margin-bottom: 0;
      padding: 0 8px;
      border-bottom: 1px solid var(--wavy-border-hairline, #e2e8f0);
      background: #ffffff;
      box-sizing: border-box;
    }
    .query {
      flex: 1 1 auto;
      box-sizing: border-box;
      width: 100%;
      height: 31px;
      padding: 2px 14px;
      background: var(--wavy-bg-surface, #f7fafc);
      color: var(--wavy-text-body, #1a202c);
      border: 1.5px solid var(--wavy-border-hairline, #e2e8f0);
      border-radius: 20px;
      font: 14px / 1.35 Arial, sans-serif;
    }
    .query:focus-visible {
      outline: none;
      box-shadow: 0 0 0 3px rgba(0, 119, 182, 0.10);
      border-color: var(--wavy-signal-cyan, #0077b6);
      background: var(--wavy-bg-base, #ffffff);
    }
    .waveform {
      display: none;
    }
    .help-trigger {
      flex: 0 0 auto;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #f0f6fc;
      color: var(--wavy-signal-cyan, #0077b6);
      border: 1.5px solid #b0c4d8;
      cursor: pointer;
      font-size: 13px;
      font-weight: 700;
      line-height: 1;
    }
    .help-trigger:hover,
    .help-trigger:focus-visible {
      background: var(--wavy-signal-cyan, #0077b6);
      color: #ffffff;
      border-color: var(--wavy-signal-cyan, #0077b6);
      outline: none;
    }

    /* G-PORT-2: panel-level action row clones GWT SearchPresenter
     * toolbar. Background gradient mirrors SearchPanel.css .toolbar
     * (linear-gradient(180deg, #eef7ff 0%, #dcecff 100%)) but adapted
     * to the dark token palette so it reads against the rail
     * background. */
    .action-row {
      display: flex;
      align-items: center;
      gap: var(--wavy-spacing-1, 4px);
      padding: var(--wavy-spacing-1, 4px);
      min-height: 36px;
      margin-bottom: 0;
      background: linear-gradient(180deg, #eef7ff 0%, #dcecff 100%);
      border: 0;
      border-bottom: 1px solid rgba(120, 170, 220, 0.35);
      border-radius: 0;
      box-sizing: border-box;
    }
    .action-row button {
      flex: 0 0 auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border: 0;
      border-radius: 50%;
      background: transparent;
      color: var(--wavy-text-body, #1a202c);
      cursor: pointer;
      padding: 0;
    }
    .action-row button:hover,
    .action-row button:focus-visible {
      outline: none;
      background: rgba(0, 119, 182, 0.08);
      color: var(--wavy-signal-cyan, #0077b6);
    }
    .action-row button[aria-pressed="true"] {
      background: rgba(0, 119, 182, 0.10);
      color: var(--wavy-signal-cyan, #0077b6);
    }
    .sort-wrap {
      position: relative;
      display: inline-flex;
    }
    .sort-menu {
      position: absolute;
      z-index: 20;
      top: calc(100% + 4px);
      left: 0;
      min-width: 180px;
      padding: 6px;
      border: 1px solid var(--wavy-border-hairline, #cbd5e1);
      border-radius: 10px;
      background: #ffffff;
      box-shadow: 0 12px 24px rgba(15, 23, 42, 0.16);
    }
    .sort-option {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      min-height: 30px;
      padding: 5px 8px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: var(--wavy-text-body, #1a202c);
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
      cursor: pointer;
      text-align: left;
    }
    .sort-option:hover,
    .sort-option:focus-visible,
    .sort-option[aria-checked="true"] {
      background: rgba(0, 119, 182, 0.08);
      color: var(--wavy-signal-cyan, #0077b6);
    }
    .sort-option:focus-visible {
      outline: 2px solid var(--wavy-signal-cyan, #0077b6);
      outline-offset: 1px;
    }

    .actions {
      display: flex;
      gap: 6px;
      padding: 8px;
      margin-bottom: 0;
      border-bottom: 1px solid var(--wavy-border-hairline, #e2e8f0);
    }
    .new-wave {
      flex: 1 1 auto;
      background: var(--wavy-signal-cyan, #0077b6);
      color: #ffffff;
      border: 1px solid var(--wavy-signal-cyan, #0077b6);
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: 6px 12px;
      font: var(--wavy-type-label, 11px / 1.35 Arial, sans-serif);
      font-weight: 600;
      cursor: pointer;
    }
    .manage-saved {
      flex: 0 0 auto;
      background: transparent;
      color: var(--wavy-text-muted, #64748b);
      border: 1px solid var(--wavy-border-hairline, #e2e8f0);
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: 6px 10px;
      font: var(--wavy-type-label, 11px / 1.35 Arial, sans-serif);
      cursor: pointer;
    }
    .folders-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin: 8px 8px 4px;
    }
    .folders-header h2 {
      margin: 0;
      font: var(--wavy-type-label, 11px / 1.35 Arial, sans-serif);
      color: var(--wavy-text-muted, #64748b);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    ul.folders {
      list-style: none;
      margin: 0 0 8px;
      padding: 0;
    }
    .folder {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
      background: transparent;
      color: var(--wavy-text-body, #1a202c);
      border: 0;
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      border-radius: 4px;
      cursor: pointer;
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
      text-align: left;
    }
    .folder[aria-current="page"] {
      background: rgba(0, 119, 182, 0.10);
      color: var(--wavy-signal-cyan, #0077b6);
      font-weight: 600;
    }
    .folder:hover,
    .folder:focus-visible {
      outline: none;
      background: rgba(0, 119, 182, 0.06);
    }
    .custom-searches {
      margin: 0 0 8px;
      padding: 0;
      list-style: none;
    }
    .custom-search {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
      border: 0;
      border-radius: 4px;
      background: transparent;
      color: var(--wavy-text-body, #1a202c);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
      text-align: left;
      cursor: pointer;
    }
    .custom-search:hover,
    .custom-search:focus-visible {
      outline: none;
      background: rgba(0, 119, 182, 0.06);
    }
    .custom-query {
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      color: var(--wavy-text-muted, #64748b);
      font: var(--wavy-type-meta, 11px / 1.4 Arial, sans-serif);
    }
    .custom-name {
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .dot.mentions-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #e53e3e;
    }
    .chip.tasks-chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      height: 16px;
      padding: 0 6px;
      border-radius: 9999px;
      background: var(--wavy-signal-amber, #9a6700);
      color: #ffffff;
      font: var(--wavy-type-meta, 11px / 1.4 Arial, sans-serif);
      font-weight: 600;
    }
    p.result-count {
      margin: 0;
      padding: 0 12px;
      height: 24px;
      line-height: 24px;
      font: 11px / 24px Arial, sans-serif;
      font-weight: 500;
      color: var(--wavy-text-muted, #64748b);
      background: #f8fafc;
      border-bottom: 1px solid var(--wavy-border-hairline, #e2e8f0);
    }
    slot[name="cards"]::slotted(wavy-search-rail-card) {
      display: block;
    }
    slot[name="cards"]::slotted(wavy-search-rail-card:last-of-type) {
      margin-bottom: var(--wavy-spacing-3, 12px);
    }
    details.filters {
      margin: 0 0 var(--wavy-spacing-3, 12px);
    }
    details.filters > summary {
      cursor: pointer;
      list-style: none;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      text-transform: uppercase;
      letter-spacing: 0.06em;
      margin-bottom: var(--wavy-spacing-1, 4px);
    }
    details.filters > summary::-webkit-details-marker {
      display: none;
    }
    .filter-chips {
      display: flex;
      flex-wrap: wrap;
      gap: var(--wavy-spacing-1, 4px);
    }
    .filter-chip {
      background: transparent;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      cursor: pointer;
    }
    .filter-chip:hover,
    .filter-chip:focus-visible {
      outline: none;
      border-color: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    .filter-chip[aria-pressed="true"] {
      background: rgba(34, 211, 238, 0.12);
      color: var(--wavy-signal-cyan, #22d3ee);
      border-color: var(--wavy-signal-cyan, #22d3ee);
      box-shadow: var(--wavy-pulse-ring, 0 0 0 2px rgba(34, 211, 238, 0.22));
    }
    .saved-overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: rgba(15, 23, 42, 0.42);
    }
    .saved-dialog {
      width: min(720px, 100%);
      max-height: min(720px, calc(100vh - 48px));
      overflow: auto;
      border-radius: 18px;
      background: #ffffff;
      color: var(--wavy-text-body, #1a202c);
      box-shadow: 0 28px 80px rgba(15, 23, 42, 0.28);
    }
    .saved-header,
    .saved-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 14px 18px;
      border-bottom: 1px solid var(--wavy-border-hairline, #e2e8f0);
    }
    .saved-footer {
      border-top: 1px solid var(--wavy-border-hairline, #e2e8f0);
      border-bottom: 0;
      justify-content: flex-end;
    }
    .saved-title {
      margin: 0;
      font: 700 16px / 1.3 Arial, sans-serif;
    }
    .saved-body {
      padding: 14px 18px;
    }
    .saved-error {
      margin: 0 0 10px;
      padding: 8px 10px;
      border-radius: 8px;
      background: #fff7ed;
      color: #9a3412;
      font: 12px / 1.4 Arial, sans-serif;
    }
    .saved-hint {
      margin: 0 0 10px;
      color: var(--wavy-text-muted, #64748b);
      font: 12px / 1.4 Arial, sans-serif;
    }
    .saved-row {
      display: grid;
      grid-template-columns: minmax(120px, 1fr) minmax(180px, 1.4fr) auto auto auto;
      gap: 8px;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid var(--wavy-border-hairline, #edf2f7);
    }
    .saved-row input[type="text"] {
      min-width: 0;
      height: 30px;
      border: 1px solid var(--wavy-border-hairline, #cbd5e1);
      border-radius: 8px;
      padding: 4px 8px;
      font: 13px / 1.35 Arial, sans-serif;
    }
    .saved-empty {
      color: var(--wavy-text-muted, #64748b);
      font: 13px / 1.4 Arial, sans-serif;
    }
    .saved-button {
      border: 1px solid var(--wavy-border-hairline, #cbd5e1);
      border-radius: 999px;
      background: #ffffff;
      color: var(--wavy-text-body, #1a202c);
      padding: 6px 10px;
      font: 12px / 1.2 Arial, sans-serif;
      cursor: pointer;
    }
    .saved-button.primary {
      background: var(--wavy-signal-cyan, #0077b6);
      border-color: var(--wavy-signal-cyan, #0077b6);
      color: #ffffff;
    }
    .saved-button:disabled {
      opacity: 0.55;
      cursor: default;
    }
    .saved-button:focus-visible {
      outline: 2px solid var(--wavy-signal-cyan, #0077b6);
      outline-offset: 2px;
    }
  `;

  constructor() {
    super();
    this.query = "in:inbox";
    this.activeFolder = "inbox";
    this.resultCount = "";
    this.mentionsUnread = 0;
    this.tasksPending = 0;
    this.filtersOpen = false;
    this.savedSearches = [];
    this.savedSearchDrafts = [];
    this.savedSearchesOpen = false;
    this.savedSearchesLoading = false;
    this.savedSearchesError = "";
    this.savedSearchesDirty = false;
    this.sortOpen = false;
    this._savedSearchReturnFocus = null;
    this._savedSearchesLoaded = false;
    this._savedSearchesLoadPromise = null;
    this._savedSearchRows = [];
    this._savedSearchesHadInvalidRows = false;
    this._documentListenersAttached = false;
    this._boundDocumentClick = (evt) => this._onDocumentClick(evt);
    this._boundDocumentKeydown = (evt) => this._onDocumentKeydown(evt);
  }

  connectedCallback() {
    super.connectedCallback();
    if (this.hasAttribute("autoload-saved-searches")) {
      this._loadSavedSearches();
    }
  }

  disconnectedCallback() {
    this._releaseDocumentListeners();
    super.disconnectedCallback();
  }

  willUpdate(changed) {
    if (changed.has("query")) {
      this.activeFolder = this._deriveActiveFolder(this.query);
    }
  }

  _deriveActiveFolder(q) {
    const text = (q || "").trim().toLowerCase();
    if (text.length === 0) return "";
    for (const folder of WavySearchRail.FOLDERS) {
      const fq = folder.query.toLowerCase();
      if (text === fq || text.startsWith(fq + " ")) {
        return folder.id;
      }
    }
    return "";
  }

  _emit(name, detail) {
    this.dispatchEvent(
      new CustomEvent(name, {
        bubbles: true,
        composed: true,
        detail: detail || {}
      })
    );
  }

  _onQueryKey(evt) {
    if (evt.key === "Enter") {
      evt.preventDefault();
      const value = evt.target.value;
      this.query = value;
      this._emit("wavy-search-submit", { query: value });
    }
  }

  _onQueryInput(evt) {
    this.query = evt.target.value;
  }

  _onFolderClick(folder) {
    this.query = folder.query;
    this.activeFolder = folder.id;
    this._emit("wavy-saved-search-selected", {
      folderId: folder.id,
      label: folder.label,
      query: folder.query
    });
  }

  focusActiveFolder() {
    if (!this.renderRoot) {
      return;
    }
    const target = this.renderRoot.querySelector('button.folder[aria-current="page"]');
    if (target && typeof target.focus === "function") {
      target.focus();
    }
  }

  _isTokenActive(token) {
    return this._tokenize(this.query).some(
      (t) => t.toLowerCase() === token.toLowerCase()
    );
  }

  _toggleFilter(filter) {
    const current = this._tokenize(this.query);
    const lowered = filter.token.toLowerCase();
    const isActive = current.some((t) => t.toLowerCase() === lowered);
    let next;
    if (isActive) {
      next = current.filter((t) => t.toLowerCase() !== lowered);
    } else {
      next = current.slice();
      next.push(filter.token);
    }
    const composed = next.join(" ").replace(/\s+/g, " ").trim();
    this.query = composed;
    this._emit("wavy-search-filter-toggled", {
      filterId: filter.id,
      label: filter.label,
      token: filter.token,
      active: !isActive,
      query: composed
    });
    this._emit("wavy-search-submit", { query: composed });
  }

  _removeTokens(q, predicate) {
    return this._tokenize(q).filter((token) => !predicate(token));
  }

  _queryWithToken(token, predicate) {
    const next = this._removeTokens(this.query, predicate);
    next.push(token);
    return next.join(" ").replace(/\s+/g, " ").trim();
  }

  _tokenize(q) {
    if (!q) return [];
    return q
      .split(/\s+/)
      .map((t) => t.trim())
      .filter((t) => t.length > 0);
  }

  /** G-PORT-2: panel-level Filter button toggles the chip-strip details. */
  _toggleFilterPanel() {
    this.filtersOpen = !this.filtersOpen;
    this._emit("wavy-search-filter-toggle-requested", {
      open: this.filtersOpen
    });
  }

  _toggleSortMenu() {
    this._setSortOpen(!this.sortOpen);
    if (this.sortOpen) {
      this.updateComplete.then(() => {
        const active =
          this.renderRoot?.querySelector('.sort-option[aria-checked="true"]') ||
          this.renderRoot?.querySelector(".sort-option");
        if (active && typeof active.focus === "function") {
          active.focus();
        }
      });
    }
  }

  _setSortOpen(open) {
    this.sortOpen = open;
    if (open) {
      this._ensureDocumentListeners();
    } else {
      this._releaseDocumentListeners();
    }
  }

  _ensureDocumentListeners() {
    if (this._documentListenersAttached) {
      return;
    }
    document.addEventListener("click", this._boundDocumentClick);
    document.addEventListener("keydown", this._boundDocumentKeydown);
    this._documentListenersAttached = true;
  }

  _releaseDocumentListeners() {
    if (!this._documentListenersAttached) {
      return;
    }
    document.removeEventListener("click", this._boundDocumentClick);
    document.removeEventListener("keydown", this._boundDocumentKeydown);
    this._documentListenersAttached = false;
  }

  _onDocumentClick(evt) {
    if (!this.sortOpen) {
      return;
    }
    const path = typeof evt.composedPath === "function" ? evt.composedPath() : [];
    if (path.includes(this)) {
      return;
    }
    this._setSortOpen(false);
  }

  _onDocumentKeydown(evt) {
    if (evt.key !== "Escape") {
      return;
    }
    if (this.sortOpen) {
      evt.preventDefault();
      this._setSortOpen(false);
      const sort = this.renderRoot?.querySelector('[data-digest-action="sort"]');
      if (sort && typeof sort.focus === "function") {
        sort.focus();
      }
    }
  }

  _onSortMenuKeydown(evt) {
    if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(evt.key)) {
      return;
    }
    const options = Array.from(evt.currentTarget.querySelectorAll(".sort-option"));
    if (options.length === 0) {
      return;
    }
    const active = this.renderRoot?.activeElement || document.activeElement;
    const current = Math.max(0, options.indexOf(active));
    let next = -1;
    if (evt.key === "ArrowDown") {
      next = (current + 1) % options.length;
    } else if (evt.key === "ArrowUp") {
      next = (current - 1 + options.length) % options.length;
    } else if (evt.key === "Home") {
      next = 0;
    } else if (evt.key === "End") {
      next = options.length - 1;
    }
    if (next >= 0) {
      evt.preventDefault();
      options[next].focus();
    }
  }

  _onSortMenuFocusout(evt) {
    const next = evt.relatedTarget;
    if (next && evt.currentTarget.contains(next)) {
      return;
    }
    this._setSortOpen(false);
  }

  _explicitSortToken() {
    const token = this._tokenize(this.query).find((part) =>
      part.toLowerCase().startsWith("orderby:")
    );
    return token || "";
  }

  _applySort(sort) {
    const explicitSort = this._explicitSortToken().toLowerCase();
    const sortToken = sort.token.toLowerCase();
    if ((explicitSort && explicitSort === sortToken) || (!explicitSort && sort.default)) {
      this._setSortOpen(false);
      this._focusSortTrigger();
      return;
    }
    const composed = this._queryWithToken(
      sort.token,
      (token) => token.toLowerCase().startsWith("orderby:")
    );
    this.query = composed;
    this._setSortOpen(false);
    this._focusSortTrigger();
    this._emit("wavy-search-sort-requested", { sortId: sort.id, token: sort.token, query: composed });
    this._emit("wavy-search-submit", { query: composed });
  }

  _focusSortTrigger() {
    this.updateComplete.then(() => {
      const sort = this.renderRoot?.querySelector('[data-digest-action="sort"]');
      if (sort && typeof sort.focus === "function") {
        sort.focus();
      }
    });
  }

  _normalizeSavedSearch(item) {
    return {
      name: String(item?.name || "").trim(),
      query: String(item?.query || "").trim(),
      pinned: item?.pinned === true
    };
  }

  async _loadSavedSearches(force = false) {
    if (!force && this._savedSearchesLoaded) {
      this.savedSearchesError = this._savedSearchesHadInvalidRows
        ? "Some saved searches need both a name and a query."
        : "";
      if (!this.savedSearchesDirty) {
        const rows = this._savedSearchRows.length > 0 ? this._savedSearchRows : this.savedSearches;
        this.savedSearchDrafts = rows.map((item) => ({ ...item }));
      }
      return;
    }
    if (!force && this._savedSearchesLoadPromise) {
      return this._savedSearchesLoadPromise;
    }
    this.savedSearchesLoading = true;
    this.savedSearchesError = "";
    let loadPromise;
    loadPromise = (async () => {
      try {
        const response = await fetch("/searches", { credentials: "same-origin" });
        if (this._savedSearchesLoadPromise !== loadPromise) {
          return;
        }
        if (response.status === 401 || response.status === 403) {
          if (!this._savedSearchesLoaded && !this.savedSearchesDirty) {
            this.savedSearches = [];
            this.savedSearchDrafts = [];
            this._savedSearchRows = [];
            this._savedSearchesHadInvalidRows = false;
            this.savedSearchesDirty = false;
            if (this.savedSearchesOpen) {
              this.savedSearchesError = "Sign in to manage saved searches.";
            }
          } else {
            const authMessage = "Sign in to refresh saved searches.";
            this.savedSearchesError = this._savedSearchesHadInvalidRows
              ? `${authMessage} Some saved searches need both a name and a query.`
              : authMessage;
          }
          return;
        }
        if (!response.ok) {
          throw new Error(`Searches request failed (${response.status})`);
        }
        const payload = await response.json();
        if (this._savedSearchesLoadPromise !== loadPromise) {
          return;
        }
        const rows = Array.isArray(payload)
          ? payload.map((item) => this._normalizeSavedSearch(item))
          : [];
        const invalid = rows.some((item) => !item.name || !item.query);
        const list = rows.filter((item) => item.name && item.query);
        this._savedSearchRows = rows.map((item) => ({ ...item }));
        this._savedSearchesHadInvalidRows = invalid;
        this.savedSearches = list;
        if (!this.savedSearchesDirty) {
          this.savedSearchDrafts = rows.map((item) => ({ ...item }));
        }
        if (invalid) {
          this.savedSearchesError = "Some saved searches need both a name and a query.";
        }
        this._savedSearchesLoaded = true;
      } catch (err) {
        if (this._savedSearchesLoadPromise !== loadPromise) {
          return;
        }
        console.warn("Unable to load saved searches", err);
        this.savedSearchesError = "Unable to load saved searches.";
        this.savedSearchDrafts = this.savedSearches.map((item) => ({ ...item }));
      } finally {
        if (this._savedSearchesLoadPromise === loadPromise) {
          this._savedSearchesLoadPromise = null;
          this.savedSearchesLoading = false;
        }
      }
    })();
    this._savedSearchesLoadPromise = loadPromise;
    return this._savedSearchesLoadPromise;
  }

  async _saveSavedSearches(nextDrafts = this.savedSearchDrafts) {
    const invalid = nextDrafts.some((item) => {
      const normalized = this._normalizeSavedSearch(item);
      return !normalized.name || !normalized.query;
    });
    if (invalid) {
      this.savedSearchesError = "Each saved search needs both a name and a query.";
      return false;
    }
    const next = nextDrafts
      .map((item) => this._normalizeSavedSearch(item));
    this.savedSearchesLoading = true;
    this.savedSearchesError = "";
    try {
      const response = await fetch("/searches", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(next)
      });
      if (!response.ok) {
        throw new Error(`Searches save failed (${response.status})`);
      }
      this.savedSearches = next;
      this._savedSearchRows = next.map((item) => ({ ...item }));
      this._savedSearchesHadInvalidRows = false;
      this.savedSearchDrafts = next.map((item) => ({ ...item }));
      this.savedSearchesDirty = false;
      this._savedSearchesLoaded = true;
      return true;
    } catch (err) {
      console.warn("Unable to save saved searches", err);
      this.savedSearchesError = "Unable to save saved searches.";
      return false;
    } finally {
      this.savedSearchesLoading = false;
    }
  }

  _openSavedSearches() {
    this._emit("wavy-manage-saved-searches-requested");
    this._savedSearchReturnFocus = this.renderRoot?.querySelector(".manage-saved") || null;
    this._setSortOpen(false);
    this.savedSearchesOpen = true;
    this._loadSavedSearches();
    this.updateComplete.then(() => {
      const firstInput = this.renderRoot?.querySelector(
        '.saved-dialog input[type="text"], .saved-dialog button'
      );
      if (firstInput && typeof firstInput.focus === "function") {
        firstInput.focus();
      }
    });
  }

  _closeSavedSearches() {
    this.savedSearchesOpen = false;
    this.savedSearchesError = "";
    this.savedSearchDrafts = this.savedSearches.map((item) => ({ ...item }));
    this.savedSearchesDirty = false;
    const returnFocus = this._savedSearchReturnFocus;
    this._savedSearchReturnFocus = null;
    if (returnFocus && typeof returnFocus.focus === "function") {
      this.updateComplete.then(() => returnFocus.focus());
    }
  }

  _updateSavedSearch(index, field, value) {
    const next = this.savedSearchDrafts.map((item) => ({ ...item }));
    if (!next[index]) {
      return;
    }
    next[index][field] = field === "pinned" ? value === true : String(value || "");
    this.savedSearchDrafts = next;
    this.savedSearchesDirty = true;
  }

  _addCurrentSearch() {
    const query = (this.query || "").trim() || "in:inbox";
    const next = this.savedSearchDrafts.map((item) => ({ ...item }));
    next.push({ name: this._nextSavedSearchName(next), query, pinned: true });
    this.savedSearchDrafts = next;
    this.savedSearchesDirty = true;
    this.updateComplete.then(() => {
      const inputs = this.renderRoot?.querySelectorAll('input[aria-label="Saved search name"]');
      const target = inputs?.[inputs.length - 1];
      if (target && typeof target.focus === "function") {
        target.focus();
        target.select();
      }
    });
  }

  _nextSavedSearchName(existing) {
    const names = new Set(existing.map((item) => this._normalizeSavedSearch(item).name));
    let index = existing.length + 1;
    let candidate = `Saved search ${index}`;
    while (names.has(candidate)) {
      index += 1;
      candidate = `Saved search ${index}`;
    }
    return candidate;
  }

  _removeSavedSearch(index) {
    this.savedSearchDrafts = this.savedSearchDrafts.filter((_, i) => i !== index);
    this.savedSearchesDirty = true;
  }

  async _saveAndClose() {
    if (await this._saveSavedSearches()) {
      this._closeSavedSearches();
    }
  }

  async _applySavedSearch(item) {
    const normalized = this._normalizeSavedSearch(item);
    if (!normalized.query) {
      return;
    }
    // Compute before assigning this.query so same-query pinned shortcuts
    // remain a true no-op while still preserving focus on the clicked button.
    const sameQuery = (this.query || "").trim() === normalized.query;
    this.query = normalized.query;
    if (this.savedSearchesOpen) {
      this._closeSavedSearches();
    } else {
      this._focusCustomSearch(normalized.name);
    }
    if (!sameQuery) {
      this._emit("wavy-saved-search-selected", {
        folderId: "",
        kind: "custom",
        label: normalized.name || normalized.query,
        query: normalized.query,
        savedSearchName: normalized.name
      });
    }
  }

  _focusCustomSearch(name) {
    if (!name) {
      return;
    }
    this.updateComplete.then(() => {
      const target = Array.from(this.renderRoot?.querySelectorAll(".custom-search") || []).find(
        (button) => button.dataset.savedSearchName === name
      );
      if (target && typeof target.focus === "function") {
        target.focus();
      }
    });
  }

  _onSavedDialogKeydown(evt) {
    if (evt.key === "Escape") {
      evt.preventDefault();
      this._closeSavedSearches();
      return;
    }
    if (evt.key !== "Tab") {
      return;
    }
    const controls = Array.from(
      evt.currentTarget.querySelectorAll(
        'button:not([disabled]), input:not([disabled]), [href], select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    ).filter((control) => control.getClientRects().length > 0);
    if (controls.length === 0) {
      return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    const active = this.renderRoot?.activeElement || document.activeElement;
    if (evt.shiftKey && active === first) {
      evt.preventDefault();
      last.focus();
    } else if (!evt.shiftKey && active === last) {
      evt.preventDefault();
      first.focus();
    }
  }

  render() {
    const explicitSort = this._explicitSortToken().toLowerCase();
    const defaultSort = (
      WavySearchRail.SORTS.find((sort) => sort.default) || WavySearchRail.SORTS[0]
    ).token.toLowerCase();
    const effectiveSort = explicitSort || defaultSort;
    const pinnedSearches = this.savedSearches.filter((item) => item.pinned);
    return html`
      <div class="search">
        <span class="waveform" aria-hidden="true">
          <svg viewBox="0 0 14 14" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M1 7h2l1-3 1 6 1-4 1 5 1-6 1 4 1-2h2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <input
          type="search"
          class="query"
          name="q"
          aria-label="Search waves"
          .value=${this.query}
          @keydown=${this._onQueryKey}
          @input=${this._onQueryInput}
        />
        <button
          type="button"
          class="help-trigger"
          aria-label="Search help"
          aria-haspopup="dialog"
          aria-controls="wavy-search-help"
          @click=${() => this._emit("wavy-search-help-toggle")}
        >
          ?
        </button>
      </div>

      <div class="action-row" role="group" aria-label="Search actions" data-digest-action-row>
        <button
          type="button"
          data-digest-action="refresh"
          aria-label="Refresh search results"
          title="Refresh search results"
          @click=${() => this._emit("wavy-search-refresh-requested")}
        >
          ${SEARCH_RAIL_ICON_REFRESH}
        </button>
        <span class="sort-wrap">
          <button
            type="button"
            data-digest-action="sort"
            aria-label="Sort waves"
            title="Sort waves"
            aria-haspopup="menu"
            aria-controls="wavy-search-sort-menu"
            aria-expanded=${this.sortOpen ? "true" : "false"}
            @click=${this._toggleSortMenu}
          >
            ${SEARCH_RAIL_ICON_SORT}
          </button>
          ${this.sortOpen
            ? html`<div
                class="sort-menu"
                id="wavy-search-sort-menu"
                role="menu"
                aria-label="Sort waves"
                @keydown=${this._onSortMenuKeydown}
                @focusout=${this._onSortMenuFocusout}
              >
                ${WavySearchRail.SORTS.map((sort) => html`
                  <button
                    type="button"
                    class="sort-option"
                    role="menuitemradio"
                    aria-checked=${effectiveSort === sort.token.toLowerCase() ? "true" : "false"}
                    data-sort-token=${sort.token}
                    @click=${() => this._applySort(sort)}
                  >
                    <span>${sort.label}</span>
                    ${effectiveSort === sort.token.toLowerCase() ? html`<span aria-hidden="true">✓</span>` : null}
                  </button>
                `)}
              </div>`
            : null}
        </span>
        <button
          type="button"
          data-digest-action="filter"
          aria-label="Filter waves"
          title="Filter waves"
          aria-pressed=${this.filtersOpen ? "true" : "false"}
          aria-expanded=${this.filtersOpen ? "true" : "false"}
          aria-controls="wavy-search-filter-strip"
          @click=${this._toggleFilterPanel}
        >
          ${SEARCH_RAIL_ICON_FILTER}
        </button>
      </div>

      <div class="actions">
        <button
          type="button"
          class="new-wave"
          data-shortcut="Shift+Cmd+O"
          aria-keyshortcuts="Shift+Meta+O Shift+Control+O"
          @click=${() => this._emit("wavy-new-wave-requested", { source: "button" })}
        >
          New Wave
        </button>
        <button
          type="button"
          class="manage-saved"
          aria-haspopup="dialog"
          @click=${this._openSavedSearches}
        >
          Manage saved searches
        </button>
      </div>

      <div class="folders-header">
        <h2 id="folders-title">Saved searches</h2>
      </div>
      <ul class="folders" aria-labelledby="folders-title">
        ${WavySearchRail.FOLDERS.map((folder) => {
          const selected = folder.id === this.activeFolder;
          return html`
            <li>
              <button
                type="button"
                class="folder"
                data-folder-id=${folder.id}
                data-query=${folder.query}
                aria-current=${selected ? "page" : "false"}
                @click=${() => this._onFolderClick(folder)}
              >
                <span class="label">${folder.label}</span>
                ${folder.id === "mentions"
                  ? html`<span
                      class="dot mentions-dot"
                      aria-label="${this.mentionsUnread || 0} unread mentions"
                      ?hidden=${!this.mentionsUnread || this.mentionsUnread <= 0}
                    ></span>`
                  : null}
                ${folder.id === "tasks"
                  ? html`<span
                      class="chip tasks-chip"
                      aria-label="${this.tasksPending || 0} pending tasks"
                      ?hidden=${!this.tasksPending || this.tasksPending <= 0}
                      >${this.tasksPending || 0}</span
                    >`
                  : null}
              </button>
            </li>
          `;
        })}
      </ul>
      ${pinnedSearches.length > 0
        ? html`
            <ul class="custom-searches" aria-label="Pinned saved searches">
              ${pinnedSearches.map((item) => html`
                <li>
                  <button
                    type="button"
                    class="custom-search"
                    data-saved-search-name=${item.name}
                    data-query=${item.query}
                    aria-label=${`Apply saved search ${item.name} (${item.query})`}
                    @click=${() => this._applySavedSearch(item)}
                  >
                    <span class="custom-name">${item.name}</span>
                    <span class="custom-query">${item.query}</span>
                  </button>
                </li>
              `)}
            </ul>
          `
        : null}
      <slot name="cards"></slot>
      <details
        class="filters"
        id="wavy-search-filter-strip"
        data-j2cl-filter-strip
        ?open=${this.filtersOpen}
        @toggle=${(e) => {
          // Keep filtersOpen in sync if the user clicks <summary> directly.
          this.filtersOpen = e.target.open;
        }}
      >
        <summary>Filters</summary>
        <div class="filter-chips" role="group" aria-label="Search filters">
          ${WavySearchRail.FILTERS.map((filter) => {
            const active = this._isTokenActive(filter.token);
            return html`
              <button
                type="button"
                class="filter-chip"
                data-filter-id=${filter.id}
                data-filter-token=${filter.token}
                aria-pressed=${active ? "true" : "false"}
                @click=${() => this._toggleFilter(filter)}
              >
                ${filter.label}
              </button>
            `;
          })}
        </div>
      </details>
      <p class="result-count" aria-live="polite">${this.resultCount || ""}</p>
      ${this.savedSearchesOpen ? this._renderSavedSearchesDialog() : null}
    `;
  }

  _renderSavedSearchesDialog() {
    return html`
      <div class="saved-overlay" @click=${(evt) => {
        if (evt.target === evt.currentTarget) {
          this._closeSavedSearches();
        }
      }}>
        <section
          class="saved-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="saved-searches-title"
          @keydown=${this._onSavedDialogKeydown}
        >
          <div class="saved-header">
            <h3 class="saved-title" id="saved-searches-title">Manage saved searches</h3>
            <button type="button" class="saved-button" aria-label="Close saved searches" @click=${this._closeSavedSearches}>×</button>
          </div>
          <div class="saved-body">
            ${this.savedSearchesError ? html`<p class="saved-error" role="alert">${this.savedSearchesError}</p>` : null}
            ${this.savedSearchesDirty
              ? html`<p class="saved-hint" role="status" aria-live="polite">Apply closes this dialog and discards unsaved edits. Use Save to persist them.</p>`
              : null}
            ${this.savedSearchesLoading ? html`<p class="saved-empty">Loading saved searches…</p>` : null}
            ${!this.savedSearchesLoading && !this.savedSearchesError && this.savedSearchDrafts.length === 0
              ? html`<p class="saved-empty">No saved searches yet. Add the current query to create one.</p>`
              : null}
            ${this.savedSearchDrafts.map((item, index) => html`
              <div class="saved-row" data-saved-search-row>
                <input
                  type="text"
                  aria-label="Saved search name"
                  .value=${item.name}
                  @input=${(evt) => this._updateSavedSearch(index, "name", evt.target.value)}
                />
                <input
                  type="text"
                  aria-label="Saved search query"
                  .value=${item.query}
                  @input=${(evt) => this._updateSavedSearch(index, "query", evt.target.value)}
                />
                <label>
                  <input
                    type="checkbox"
                    .checked=${item.pinned}
                    @change=${(evt) => this._updateSavedSearch(index, "pinned", evt.target.checked)}
                  />
                  Pin
                </label>
                <button
                  type="button"
                  class="saved-button"
                  aria-label=${`Apply ${item.name || item.query || "saved search"}`}
                  @click=${() => this._applySavedSearch(item)}
                >
                  Apply
                </button>
                <button
                  type="button"
                  class="saved-button"
                  aria-label=${`Remove ${item.name || item.query || "saved search"}`}
                  @click=${() => this._removeSavedSearch(index)}
                >
                  Remove
                </button>
              </div>
            `)}
          </div>
          <div class="saved-footer">
            <button
              type="button"
              class="saved-button"
              ?disabled=${this.savedSearchesLoading}
              @click=${this._addCurrentSearch}
            >
              Add current search
            </button>
            <button type="button" class="saved-button" @click=${this._closeSavedSearches}>Cancel</button>
            <button
              type="button"
              class="saved-button primary"
              ?disabled=${this.savedSearchesLoading}
              @click=${this._saveAndClose}
            >
              Save
            </button>
          </div>
        </section>
      </div>
    `;
  }
}

if (!customElements.get("wavy-search-rail")) {
  customElements.define("wavy-search-rail", WavySearchRail);
}

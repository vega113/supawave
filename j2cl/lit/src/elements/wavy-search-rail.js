import { LitElement, css, html } from "lit";
import { t } from "../i18n/t.js";
import {
  SEARCH_RAIL_ICON_REFRESH,
  SEARCH_RAIL_ICON_NEW_WAVE,
  SEARCH_RAIL_ICON_MANAGE_SAVED,
  SEARCH_RAIL_ICON_INBOX,
  SEARCH_RAIL_ICON_MENTIONS,
  SEARCH_RAIL_ICON_TASKS,
  SEARCH_RAIL_ICON_PUBLIC,
  SEARCH_RAIL_ICON_ARCHIVE,
  SEARCH_RAIL_ICON_PINNED
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
 *   - panel-level action row matching the GWT toolbar:
 *     [New Wave] [Manage saved searches] [Inbox] [Mentions]
 *     [Tasks] [Public] [Archive] [Pinned] [Refresh]
 *   - Filter-chip strip above results for explicit advanced filtering
 *   - Result count
 *   - Slot for <wavy-search-rail-card> digest cards
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
      /* GWT-parity (round 2): flat, full-width blue title strip matching
       * the geometry of .sidecar-selected-title (sidecar.css) so the rail
       * and wave-panel headers sit at the same y / height / color
       * side-by-side. The visible heading is aria-hidden so the host's
       * aria-label remains the AT label. */
      .panel-title {
        box-sizing: border-box;
        display: block;
        min-height: 22px;
        margin: 0;
        padding: 3px 8px;
        border-bottom: 1px solid #7aa7d9;
        background: linear-gradient(180deg, #69a9ec 0%, #2f80d1 100%);
        color: #ffffff;
        font: 700 12px / 16px Arial, Helvetica, sans-serif;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
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
      width: 24px;
      height: 28px;
      border: 0;
      border-radius: 3px;
      background: transparent;
      color: var(--wavy-text-body, #1a202c);
      cursor: pointer;
      padding: 0;
      position: relative;
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
    .action-row button[aria-current="page"] {
      background: rgba(0, 119, 182, 0.12);
      color: var(--wavy-signal-cyan, #0077b6);
      box-shadow: inset 0 -2px 0 var(--wavy-signal-cyan, #0077b6);
    }
    .toolbar-spacer {
      flex: 1 1 auto;
      min-width: 8px;
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
      position: absolute;
      top: 2px;
      right: 2px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #e53e3e;
    }
    .chip.tasks-chip {
      position: absolute;
      top: 1px;
      right: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 14px;
      height: 12px;
      padding: 0 4px;
      border-radius: 9999px;
      background: var(--wavy-signal-amber, #9a6700);
      color: #ffffff;
      font: 700 9px / 12px Arial, sans-serif;
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
    this._savedSearchReturnFocus = null;
    this._savedSearchesLoaded = false;
    this._savedSearchesLoadPromise = null;
    this._savedSearchRows = [];
    this._savedSearchesHadInvalidRows = false;
  }

  connectedCallback() {
    super.connectedCallback();
    if (this.hasAttribute("autoload-saved-searches")) {
      this._loadSavedSearches();
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
  }

  willUpdate(changed) {
    if (changed.has("query")) {
      this.activeFolder = this._deriveActiveFolder(this.query);
    }
    if (changed.has("query") || changed.has("resultCount")) {
      this.setAttribute("aria-label", this._panelTitle());
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
    const target = this.renderRoot.querySelector(
      'button[data-folder-id][aria-current="page"]'
    );
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
    this._savedSearchReturnFocus =
      this.renderRoot?.querySelector('[data-digest-action="manage-saved"]') || null;
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
    const pinnedSearches = this.savedSearches.filter((item) => item.pinned);
    return html`
      <h2
        class="panel-title"
        id="wavy-search-rail-title"
        data-j2cl-rail-title
        aria-hidden="true"
      >${this._panelTitle()}</h2>
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
          aria-label=${t("searchRail.searchWaves", "Search waves")}
          placeholder=${t("searchRail.placeholder", "Search waves")}
          .value=${this.query}
          @keydown=${this._onQueryKey}
          @input=${this._onQueryInput}
        />
        <button
          type="button"
          class="help-trigger"
          aria-label=${t("searchRail.help", "Search help")}
          title=${t("searchRail.help", "Search help")}
          aria-haspopup="dialog"
          aria-controls="wavy-search-help"
          @click=${() => this._emit("wavy-search-help-toggle")}
        >
          ?
        </button>
      </div>

      <div class="action-row" role="group" aria-label=${t("searchRail.actions", "Search actions")} data-digest-action-row>
        <button
          type="button"
          data-digest-action="new-wave"
          data-shortcut="Shift+Cmd+O"
          aria-keyshortcuts="Shift+Meta+O Shift+Control+O"
          aria-label=${t("searchRail.newWave", "New Wave")}
          title=${t("searchRail.newWave", "New Wave")}
          @click=${() => this._emit("wavy-new-wave-requested", { source: "button" })}
        >
          ${SEARCH_RAIL_ICON_NEW_WAVE}
        </button>
        <button
          type="button"
          data-digest-action="manage-saved"
          aria-haspopup="dialog"
          aria-label=${t("searchRail.manageSaved", "Manage saved searches")}
          title=${t("searchRail.manageSaved", "Manage saved searches")}
          @click=${this._openSavedSearches}
        >
          ${SEARCH_RAIL_ICON_MANAGE_SAVED}
        </button>
        ${WavySearchRail.FOLDERS.map((folder) => {
          const selected = folder.id === this.activeFolder;
          const badgeLabel =
            folder.id === "mentions" && this.mentionsUnread > 0
              ? `, ${this.mentionsUnread} ${t("searchRail.unreadMentions", "unread mentions")}`
              : folder.id === "tasks" && this.tasksPending > 0
                ? `, ${this.tasksPending} ${t("searchRail.pendingTasks", "pending tasks")}`
                : "";
          const buttonLabel = `${folder.label}${badgeLabel}`;
          const icon = {
            inbox: SEARCH_RAIL_ICON_INBOX,
            mentions: SEARCH_RAIL_ICON_MENTIONS,
            tasks: SEARCH_RAIL_ICON_TASKS,
            public: SEARCH_RAIL_ICON_PUBLIC,
            archive: SEARCH_RAIL_ICON_ARCHIVE,
            pinned: SEARCH_RAIL_ICON_PINNED
          }[folder.id];
          return html`
            <button
              type="button"
              data-digest-action=${folder.id}
              data-folder-id=${folder.id}
              data-query=${folder.query}
              aria-current=${selected ? "page" : "false"}
              aria-label=${buttonLabel}
              title=${buttonLabel}
              @click=${() => this._onFolderClick(folder)}
            >
              ${icon}
              ${folder.id === "mentions"
                ? html`<span
                    class="dot mentions-dot"
                    aria-hidden="true"
                    ?hidden=${!this.mentionsUnread || this.mentionsUnread <= 0}
                  ></span>`
                : null}
              ${folder.id === "tasks"
                ? html`<span
                    class="chip tasks-chip"
                    aria-hidden="true"
                    ?hidden=${!this.tasksPending || this.tasksPending <= 0}
                  >${this.tasksPending || 0}</span>`
                : null}
            </button>
          `;
        })}
        <button
          type="button"
          data-digest-action="refresh"
          aria-label=${t("searchRail.refresh", "Refresh search results")}
          title=${t("searchRail.refresh", "Refresh search results")}
          @click=${() => this._emit("wavy-search-refresh-requested")}
        >
          ${SEARCH_RAIL_ICON_REFRESH}
        </button>
        <span class="toolbar-spacer" aria-hidden="true"></span>
      </div>
      ${pinnedSearches.length > 0
        ? html`
            <ul class="custom-searches" aria-label=${t("searchRail.pinnedSaved", "Pinned saved searches")}>
              ${pinnedSearches.map((item) => {
                const applyLabel = `${t("searchRail.applySavedPrefix", "Apply saved search")} ${item.name} (${item.query})`;
                return html`
                <li>
                  <button
                    type="button"
                    class="custom-search"
                    data-saved-search-name=${item.name}
                    data-query=${item.query}
                    aria-label=${applyLabel}
                    title=${applyLabel}
                    @click=${() => this._applySavedSearch(item)}
                  >
                    <span class="custom-name">${item.name}</span>
                    <span class="custom-query">${item.query}</span>
                  </button>
                </li>
              `;
              })}
            </ul>
          `
        : null}
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
        <summary>${t("searchRail.filters", "Filters")}</summary>
        <div class="filter-chips" role="group" aria-label=${t("searchRail.filtersGroup", "Search filters")}>
          ${WavySearchRail.FILTERS.map((filter) => {
            const active = this._isTokenActive(filter.token);
            return html`
              <button
                type="button"
                class="filter-chip"
                data-filter-id=${filter.id}
                data-filter-token=${filter.token}
                aria-pressed=${active ? "true" : "false"}
                aria-label=${filter.label}
                title=${filter.label}
                @click=${() => this._toggleFilter(filter)}
              >
                ${filter.label}
              </button>
            `;
          })}
        </div>
      </details>
      <p class="result-count" aria-live="polite">${this.resultCount || ""}</p>
      <slot name="cards"></slot>
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
            <h3 class="saved-title" id="saved-searches-title">${t("searchRail.manageSaved", "Manage saved searches")}</h3>
            <button
              type="button"
              class="saved-button"
              aria-label=${t("searchRail.closeSaved", "Close saved searches")}
              title=${t("searchRail.closeSaved", "Close saved searches")}
              @click=${this._closeSavedSearches}
            >×</button>
          </div>
          <div class="saved-body">
            ${this.savedSearchesError ? html`<p class="saved-error" role="alert">${this.savedSearchesError}</p>` : null}
            ${this.savedSearchesDirty
              ? html`<p class="saved-hint" role="status" aria-live="polite">${t("searchRail.applyDiscardsHint", "Apply closes this dialog and discards unsaved edits. Use Save to persist them.")}</p>`
              : null}
            ${this.savedSearchesLoading ? html`<p class="saved-empty">${t("searchRail.savedLoading", "Loading saved searches…")}</p>` : null}
            ${!this.savedSearchesLoading && !this.savedSearchesError && this.savedSearchDrafts.length === 0
              ? html`<p class="saved-empty">${t("searchRail.savedEmpty", "No saved searches yet. Add the current query to create one.")}</p>`
              : null}
            ${this.savedSearchDrafts.map((item, index) => {
              const applyLabel = `${t("searchRail.applyPrefix", "Apply")} ${item.name || item.query || t("searchRail.savedFallback", "saved search")}`;
              const removeLabel = `${t("searchRail.removePrefix", "Remove")} ${item.name || item.query || t("searchRail.savedFallback", "saved search")}`;
              return html`
              <div class="saved-row" data-saved-search-row>
                <input
                  type="text"
                  aria-label=${t("searchRail.savedName", "Saved search name")}
                  .value=${item.name}
                  @input=${(evt) => this._updateSavedSearch(index, "name", evt.target.value)}
                />
                <input
                  type="text"
                  aria-label=${t("searchRail.savedQuery", "Saved search query")}
                  .value=${item.query}
                  @input=${(evt) => this._updateSavedSearch(index, "query", evt.target.value)}
                />
                <label>
                  <input
                    type="checkbox"
                    .checked=${item.pinned}
                    @change=${(evt) => this._updateSavedSearch(index, "pinned", evt.target.checked)}
                  />
                  ${t("searchRail.pinShort", "Pin")}
                </label>
                <button
                  type="button"
                  class="saved-button"
                  aria-label=${applyLabel}
                  title=${applyLabel}
                  @click=${() => this._applySavedSearch(item)}
                >
                  ${t("searchRail.applyShort", "Apply")}
                </button>
                <button
                  type="button"
                  class="saved-button"
                  aria-label=${removeLabel}
                  title=${removeLabel}
                  @click=${() => this._removeSavedSearch(index)}
                >
                  ${t("common.remove", "Remove")}
                </button>
              </div>
              `;
            })}
          </div>
          <div class="saved-footer">
            <button
              type="button"
              class="saved-button"
              aria-label=${t("searchRail.addCurrent", "Add current search")}
              title=${t("searchRail.addCurrent", "Add current search")}
              ?disabled=${this.savedSearchesLoading}
              @click=${this._addCurrentSearch}
            >
              ${t("searchRail.addCurrent", "Add current search")}
            </button>
            <button
              type="button"
              class="saved-button"
              aria-label=${t("common.cancel", "Cancel")}
              title=${t("common.cancel", "Cancel")}
              @click=${this._closeSavedSearches}
            >${t("common.cancel", "Cancel")}</button>
            <button
              type="button"
              class="saved-button primary"
              aria-label=${t("common.save", "Save")}
              title=${t("common.save", "Save")}
              ?disabled=${this.savedSearchesLoading}
              @click=${this._saveAndClose}
            >
              ${t("common.save", "Save")}
            </button>
          </div>
        </section>
      </div>
      `;
    }

    _panelTitle() {
      const query = String(this.query || "").trim() || "in:inbox";
      const count = String(this.resultCount || "").trim();
      return count ? `${query} (${count})` : query;
    }
  }

if (!customElements.get("wavy-search-rail")) {
  customElements.define("wavy-search-rail", WavySearchRail);
}

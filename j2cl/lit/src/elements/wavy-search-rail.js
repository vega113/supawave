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
 *   - sort    → wavy-search-sort-requested    (G-PORT-2 new; future
 *               slice hangs the menu on this)
 *   - filter  → wavy-search-filter-toggle-requested (G-PORT-2 new;
 *               toggles the existing <details> filter chip strip
 *               open / closed)
 *
 * All previously-existing events still fire (wavy-search-submit,
 * wavy-search-help-toggle, wavy-new-wave-requested,
 * wavy-manage-saved-searches-requested, wavy-saved-search-selected,
 * wavy-search-filter-toggled).
 */
export class WavySearchRail extends LitElement {
  static properties = {
    query: { type: String, reflect: true },
    activeFolder: { type: String, attribute: "data-active-folder", reflect: true },
    resultCount: { type: String, attribute: "result-count", reflect: true },
    mentionsUnread: { type: Number, attribute: "mentions-unread" },
    tasksPending: { type: Number, attribute: "tasks-pending" },
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

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      padding: var(--wavy-spacing-3, 12px);
      background: var(--wavy-bg-base, #0b1120);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
      min-width: 240px;
    }
    .search {
      position: relative;
      display: flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      margin-bottom: var(--wavy-spacing-2, 8px);
    }
    .query {
      flex: 1 1 auto;
      box-sizing: border-box;
      width: 100%;
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      padding-left: 32px;
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
    }
    .query:focus-visible {
      outline: none;
      box-shadow: var(--wavy-pulse-ring, 0 0 0 2px rgba(34, 211, 238, 0.22));
      border-color: var(--wavy-signal-cyan, #22d3ee);
    }
    .waveform {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      color: var(--wavy-signal-cyan, #22d3ee);
      pointer-events: none;
      width: 14px;
      height: 14px;
    }
    .help-trigger {
      flex: 0 0 auto;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      cursor: pointer;
      font-weight: 600;
    }
    .help-trigger:hover,
    .help-trigger:focus-visible {
      color: var(--wavy-signal-cyan, #22d3ee);
      border-color: var(--wavy-signal-cyan, #22d3ee);
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
      margin-bottom: var(--wavy-spacing-2, 8px);
      background: var(--wavy-rail-action-bg, rgba(34, 211, 238, 0.08));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
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
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      cursor: pointer;
      padding: 0;
    }
    .action-row button:hover,
    .action-row button:focus-visible {
      outline: none;
      background: rgba(34, 211, 238, 0.16);
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    .action-row button[aria-pressed="true"] {
      background: rgba(34, 211, 238, 0.2);
      color: var(--wavy-signal-cyan, #22d3ee);
    }

    .actions {
      display: flex;
      gap: var(--wavy-spacing-2, 8px);
      margin-bottom: var(--wavy-spacing-3, 12px);
    }
    .new-wave {
      flex: 1 1 auto;
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1120);
      border: 0;
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 600;
      cursor: pointer;
    }
    .manage-saved {
      flex: 0 0 auto;
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-3, 12px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      cursor: pointer;
    }
    .folders-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: var(--wavy-spacing-1, 4px);
    }
    .folders-header h2 {
      margin: 0;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    ul.folders {
      list-style: none;
      margin: 0 0 var(--wavy-spacing-3, 12px);
      padding: 0;
    }
    .folder {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
      background: transparent;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 0;
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      cursor: pointer;
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
      text-align: left;
    }
    .folder[aria-current="page"] {
      background: rgba(34, 211, 238, 0.12);
      color: var(--wavy-signal-cyan, #22d3ee);
      font-weight: 600;
    }
    .folder:hover,
    .folder:focus-visible {
      outline: none;
      background: rgba(34, 211, 238, 0.06);
    }
    .dot.mentions-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--wavy-signal-violet, #7c3aed);
    }
    .chip.tasks-chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      height: 16px;
      padding: 0 6px;
      border-radius: 9999px;
      background: var(--wavy-signal-amber, #fb923c);
      color: var(--wavy-bg-base, #0b1120);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      font-weight: 600;
    }
    p.result-count {
      margin: 0 0 var(--wavy-spacing-3, 12px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
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
  `;

  constructor() {
    super();
    this.query = "in:inbox";
    this.activeFolder = "inbox";
    this.resultCount = "";
    this.mentionsUnread = 0;
    this.tasksPending = 0;
    this.filtersOpen = false;
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

  /** G-PORT-2: panel-level Sort button placeholder for the future menu. */
  _onSortClick() {
    this._emit("wavy-search-sort-requested");
  }

  render() {
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
        <button
          type="button"
          data-digest-action="sort"
          aria-label="Sort waves"
          title="Sort waves"
          @click=${this._onSortClick}
        >
          ${SEARCH_RAIL_ICON_SORT}
        </button>
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
          @click=${() => this._emit("wavy-new-wave-requested")}
        >
          New Wave
        </button>
        <button
          type="button"
          class="manage-saved"
          @click=${() => this._emit("wavy-manage-saved-searches-requested")}
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
    `;
  }
}

if (!customElements.get("wavy-search-rail")) {
  customElements.define("wavy-search-rail", WavySearchRail);
}

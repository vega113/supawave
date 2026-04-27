import { LitElement, css, html } from "lit";

/**
 * <wavy-search-rail> — F-2 (#1037, #1047 slice 3) left-rail search
 * surface that hosts the query textbox, the saved-search folder list,
 * and the inline header controls (help trigger, New Wave, Manage
 * saved searches, Refresh, result count).
 *
 * Inventory affordances covered (B.1–B.12 from the 2026-04-26 GWT
 * functional inventory). Per-digest cards (B.13–B.18) are owned by
 * <wavy-search-rail-card> and slotted into this rail's default slot.
 *
 * - B.1  query textbox (default: in:inbox; waveform glyph; Enter
 *        emits wavy-search-submit)
 * - B.2  search-help trigger (?)  — emits wavy-search-help-toggle;
 *        does NOT own the modal (single document-level instance lives
 *        under <shell-root> and listens for the toggle event)
 * - B.3  New Wave button — emits wavy-new-wave-requested; carries
 *        aria-keyshortcuts metadata. The global Shift+Cmd+O keyboard
 *        listener is intentionally OUT OF SCOPE for this slice (S6
 *        wires the URL state + global keymap)
 * - B.4  Manage saved searches — emits wavy-manage-saved-searches-requested
 * - B.5–B.10 Six saved-search folders (Inbox/Mentions/Tasks/Public/
 *        Archive/Pinned) with the canonical query strings; Inbox is
 *        the default selection. Mentions has a violet unread dot
 *        (revealed when mentions-unread > 0 via --wavy-signal-violet);
 *        Tasks has an amber pending count chip (revealed when
 *        tasks-pending > 0 via --wavy-signal-amber)
 * - B.11 Refresh search results
 * - B.12 Result count "N waves" (low-emphasis, aria-live=polite)
 *
 * Active-folder derivation: when the `query` attribute changes, the
 * rail picks the folder whose canonical query string is a prefix of
 * the current query (case-insensitive) and reflects the match through
 * `data-active-folder` and the matched folder's `aria-current="page"`.
 * If nothing matches, no folder gets aria-current=page (the user is
 * running a custom query).
 */
export class WavySearchRail extends LitElement {
  static properties = {
    query: { type: String, reflect: true },
    activeFolder: { type: String, attribute: "data-active-folder", reflect: true },
    resultCount: { type: String, attribute: "result-count", reflect: true },
    mentionsUnread: { type: Number, attribute: "mentions-unread" },
    tasksPending: { type: Number, attribute: "tasks-pending" }
  };

  static FOLDERS = [
    { id: "inbox", label: "Inbox", query: "in:inbox" },
    { id: "mentions", label: "Mentions", query: "mentions:me" },
    { id: "tasks", label: "Tasks", query: "tasks:me" },
    { id: "public", label: "Public", query: "with:@" },
    { id: "archive", label: "Archive", query: "in:archive" },
    { id: "pinned", label: "Pinned", query: "in:pinned" }
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
      margin-bottom: var(--wavy-spacing-3, 12px);
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
    .refresh {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 0;
      cursor: pointer;
      font-size: 14px;
    }
    .refresh:hover,
    .refresh:focus-visible {
      color: var(--wavy-signal-cyan, #22d3ee);
      outline: none;
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
  `;

  constructor() {
    super();
    this.query = "in:inbox";
    this.activeFolder = "inbox";
    this.resultCount = "";
    this.mentionsUnread = 0;
    this.tasksPending = 0;
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
      query: folder.query
    });
  }

  render() {
    return html`
      <div class="search">
        <span class="waveform" aria-hidden="true">
          <!-- F-2 slice 5 (#1055): explicit width/height on the SVG so
               the glyph never overflows even if shadow DOM has not yet
               attached (e.g. server-rendered light-DOM fallback). -->
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
        <button
          type="button"
          class="refresh"
          aria-label="Refresh search results"
          @click=${() => this._emit("wavy-search-refresh-requested")}
        >
          ⟳
        </button>
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
      <p class="result-count" aria-live="polite">${this.resultCount || ""}</p>
    `;
  }
}

if (!customElements.get("wavy-search-rail")) {
  customElements.define("wavy-search-rail", WavySearchRail);
}

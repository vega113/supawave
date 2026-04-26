import { LitElement, css, html } from "lit";

/**
 * <wavy-search-help> — F-2 (#1037, #1047 slice 3) modal that documents
 * the search query language.
 *
 * Inventory affordances covered (C.1–C.22 from the 2026-04-26 GWT
 * functional inventory):
 *
 * - C.1–C.4 in:inbox/in:archive/in:all/in:pinned filters
 * - C.5–C.6 with:user@domain / with:@ (public)
 * - C.7    creator:user@domain
 * - C.8    tag:name
 * - C.9    unread:true
 * - C.10   title:text
 * - C.11   content:text
 * - C.12   mentions:me
 * - C.13–C.15 tasks:all / tasks:me / tasks:user@domain
 *   (informational only; behavioral filtering owned by F-3 — this slice
 *   only asserts the modal lists the tokens and the parser accepts them)
 * - C.16   free text (implicit content:)
 * - C.17–C.20 orderby:datedesc/dateasc/createddesc/createdasc/creatordesc/creatorasc
 * - C.21   combinations example chips
 * - C.22   "Got it" dismiss
 *
 * Token contract: every advertised token MUST parse through
 * QueryHelper.parseQuery. The companion JUnit fixture
 * J2clSearchHelpTokenParseTest enforces this — any new token added
 * here that is not in TokenQueryType / OrderByValueType will fail the
 * parser fixture (the "do NOT invent new tokens" guard from issue
 * #1047).
 *
 * Mount contract: a SINGLE <wavy-search-help id="wavy-search-help">
 * lives at the document level (mounted by HtmlRenderer in the J2CL
 * root shell). The <wavy-search-rail> help-trigger emits a
 * `wavy-search-help-toggle` CustomEvent (composed + bubbles); a
 * connectedCallback-installed listener on this element flips the
 * `open` attribute. Avoids duplicate-dialog issues seen in the GWT
 * panel before backdrops were re-parented to document.body.
 *
 * Events emitted:
 * - `wavy-search-help-example`  — `{detail: {query}}` when a chip is
 *   clicked. <wavy-search-rail> listens and populates its textbox.
 * - `wavy-search-help-dismissed` — when "Got it" closes the dialog.
 *
 * Accessibility: role="dialog", aria-modal="true". Renders a
 * fixed-position backdrop and sheet-based custom modal; it does not use
 * the native <dialog> element.
 */
export class WavySearchHelp extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      position: fixed;
      inset: 0;
      display: none;
      z-index: 9000;
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    :host([open]) {
      display: block;
    }
    .backdrop {
      position: absolute;
      inset: 0;
      background: rgba(2, 8, 23, 0.55);
    }
    .sheet {
      position: relative;
      max-width: 880px;
      max-height: 86vh;
      margin: 6vh auto 0;
      box-sizing: border-box;
      padding: var(--wavy-spacing-5, 20px);
      background: var(--wavy-bg-surface, #11192a);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      box-shadow: 0 12px 48px rgba(2, 8, 23, 0.45);
      overflow: auto;
    }
    header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--wavy-spacing-3, 12px);
      margin-bottom: var(--wavy-spacing-4, 16px);
    }
    h2 {
      margin: 0;
      font: var(--wavy-type-h2, 1.25rem / 1.3 sans-serif);
      font-weight: 600;
    }
    .dismiss {
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1120);
      border: 0;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 600;
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-4, 16px);
      cursor: pointer;
    }
    .dismiss:focus-visible {
      outline: none;
      box-shadow: var(--wavy-pulse-ring, 0 0 0 4px rgba(34, 211, 238, 0.22));
    }
    .columns {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--wavy-spacing-5, 20px);
    }
    @media (max-width: 720px) {
      .columns {
        grid-template-columns: 1fr;
      }
    }
    .section-title {
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
      font-weight: 600;
      margin: 0 0 var(--wavy-spacing-2, 8px);
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
    }
    th {
      text-align: left;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      border-bottom: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
    }
    td {
      padding: var(--wavy-spacing-2, 8px);
      vertical-align: top;
      border-bottom: 1px solid rgba(34, 211, 238, 0.06);
    }
    code,
    .example {
      font-family: var(--wavy-type-mono-family, ui-monospace, "SF Mono", monospace);
      font-size: 0.85em;
      background: rgba(34, 211, 238, 0.08);
      color: var(--wavy-signal-cyan, #22d3ee);
      padding: 1px 6px;
      border-radius: 4px;
    }
    .example {
      cursor: pointer;
      border: 1px solid transparent;
    }
    .example:hover,
    .example:focus-visible {
      border-color: var(--wavy-signal-cyan, #22d3ee);
      outline: none;
    }
    .combinations {
      margin-top: var(--wavy-spacing-4, 16px);
    }
    .combinations .grid {
      display: flex;
      flex-wrap: wrap;
      gap: var(--wavy-spacing-2, 8px);
    }
    .free-text-row td:first-child {
      font-style: italic;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
    }
  `;

  constructor() {
    super();
    this.open = false;
    this._lastFocused = null;
    this._onToggle = this._onToggle.bind(this);
    this._onKey = this._onKey.bind(this);
    this._onTrapTab = this._onTrapTab.bind(this);
  }

  connectedCallback() {
    super.connectedCallback();
    // The server-side render emits a `hidden` attribute so the SSR
    // light-DOM modal body does not flash before the J2CL bundle
    // upgrades the element. Once Lit takes over, the shadow-DOM
    // `:host { display: none }` rule (gated on the `open` attribute)
    // drives visibility, so we drop the SSR `hidden` here. We also
    // assign the singleton id so client-only mounts (design preview /
    // future test harness) still satisfy the rail's aria-controls.
    if (this.hasAttribute("hidden")) {
      this.removeAttribute("hidden");
    }
    if (!this.id) {
      this.id = "wavy-search-help";
    }
    // Listen at document level so the rail's help-trigger can toggle us
    // even though it lives in a different shadow root.
    document.addEventListener("wavy-search-help-toggle", this._onToggle);
    document.addEventListener("keydown", this._onKey);
  }

  disconnectedCallback() {
    document.removeEventListener("wavy-search-help-toggle", this._onToggle);
    document.removeEventListener("keydown", this._onKey);
    // Defensive: _onTrapTab is only attached while open, but disconnect can
    // race with open=true (singleton detached mid-modal); make removal idempotent.
    document.removeEventListener("keydown", this._onTrapTab);
    super.disconnectedCallback();
  }

  updated(changed) {
    if (changed.has("open")) {
      if (this.open) {
        // Walk through nested shadow roots so we capture the actual focused
        // element (e.g. the help-trigger inside <wavy-search-rail>'s shadow
        // DOM), not just the top-level shadow host. Without this, restore
        // focus would land on the host and the trigger button would never
        // regain focus on close.
        this._lastFocused = this._deepActiveElement();
        // Move focus to the first focusable element inside the modal.
        this.updateComplete.then(() => {
          if (!this.open) return;
          const first = this._focusable()[0];
          if (first) first.focus();
        });
        document.addEventListener("keydown", this._onTrapTab);
      } else {
        document.removeEventListener("keydown", this._onTrapTab);
        if (this._lastFocused && typeof this._lastFocused.focus === "function") {
          this._lastFocused.focus();
        }
        this._lastFocused = null;
      }
    }
  }

  _deepActiveElement() {
    let active = document.activeElement;
    while (active && active.shadowRoot && active.shadowRoot.activeElement) {
      active = active.shadowRoot.activeElement;
    }
    return active;
  }

  _focusable() {
    return Array.from(
      this.shadowRoot.querySelectorAll('button, [tabindex="0"]')
    ).filter((el) => !el.hasAttribute("disabled") && !el.hasAttribute("hidden"));
  }

  _onTrapTab(evt) {
    if (!this.open || evt.key !== "Tab") return;
    const items = this._focusable();
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (evt.shiftKey) {
      if (document.activeElement === first || this.shadowRoot.activeElement === first) {
        evt.preventDefault();
        last.focus();
      }
    } else {
      if (document.activeElement === last || this.shadowRoot.activeElement === last) {
        evt.preventDefault();
        first.focus();
      }
    }
  }

  _onToggle() {
    this.open = !this.open;
  }

  _onKey(evt) {
    if (this.open && evt.key === "Escape") {
      this._dismiss();
    }
  }

  _dismiss() {
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("wavy-search-help-dismissed", {
        bubbles: true,
        composed: true
      })
    );
  }

  _emitExample(query) {
    this.dispatchEvent(
      new CustomEvent("wavy-search-help-example", {
        bubbles: true,
        composed: true,
        detail: { query }
      })
    );
  }

  _example(query) {
    return html`<span
      class="example"
      role="button"
      tabindex="0"
      @click=${() => this._emitExample(query)}
      @keydown=${(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          this._emitExample(query);
        }
      }}
      >${query}</span
    >`;
  }

  render() {
    return html`
      <div class="backdrop" @click=${this._dismiss}></div>
      <div
        class="sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wavy-search-help-title"
      >
        <header>
          <h2 id="wavy-search-help-title">Search Help</h2>
          <button
            type="button"
            class="dismiss"
            @click=${this._dismiss}
            aria-label="Close search help"
          >
            Got it
          </button>
        </header>
        <div class="columns">
          <section>
            <p class="section-title">Filters</p>
            <table>
              <thead>
                <tr><th>Filter</th><th>Description</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>${this._example("in:inbox")}</td>
                  <td>Waves in your inbox</td>
                </tr>
                <tr>
                  <td>${this._example("in:archive")}</td>
                  <td>Archived waves</td>
                </tr>
                <tr>
                  <td>${this._example("in:all")}</td>
                  <td>All waves including public</td>
                </tr>
                <tr>
                  <td>${this._example("in:pinned")}</td>
                  <td>Pinned waves</td>
                </tr>
                <tr>
                  <td><code>with:user@domain</code></td>
                  <td>
                    Waves with a participant — try:
                    ${this._example("with:alice@example.com")}
                  </td>
                </tr>
                <tr>
                  <td>${this._example("with:@")}</td>
                  <td>Public waves (shared domain)</td>
                </tr>
                <tr>
                  <td><code>creator:user@domain</code></td>
                  <td>
                    Waves created by someone — try:
                    ${this._example("creator:bob@example.com")}
                  </td>
                </tr>
                <tr>
                  <td><code>tag:name</code></td>
                  <td>
                    Waves with a specific tag — try:
                    ${this._example("tag:important")}
                  </td>
                </tr>
                <tr>
                  <td>${this._example("unread:true")}</td>
                  <td>Only waves with unread blips</td>
                </tr>
                <tr>
                  <td><code>title:text</code></td>
                  <td>
                    Waves whose title contains text — try:
                    ${this._example("title:meeting")}
                  </td>
                </tr>
                <tr>
                  <td><code>content:text</code></td>
                  <td>
                    Waves containing text in any blip — try:
                    ${this._example("content:agenda")}
                  </td>
                </tr>
                <tr>
                  <td>${this._example("mentions:me")}</td>
                  <td>Waves where you are @mentioned</td>
                </tr>
                <tr>
                  <td>${this._example("tasks:all")}</td>
                  <td>Waves you can access that contain any task</td>
                </tr>
                <tr>
                  <td>${this._example("tasks:me")}</td>
                  <td>Waves with tasks assigned to you</td>
                </tr>
                <tr>
                  <td><code>tasks:user@domain</code></td>
                  <td>
                    Tasks assigned to someone specific — try:
                    ${this._example("tasks:alice@example.com")}
                  </td>
                </tr>
                <tr class="free-text-row">
                  <td>free text</td>
                  <td>
                    Implicit content: search — try:
                    ${this._example("meeting notes")}
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
          <section>
            <p class="section-title">Sort Options</p>
            <table>
              <thead>
                <tr><th>Sort</th><th>Description</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>${this._example("orderby:datedesc")}</td>
                  <td>Last modified, newest first (default)</td>
                </tr>
                <tr>
                  <td>${this._example("orderby:dateasc")}</td>
                  <td>Last modified, oldest first</td>
                </tr>
                <tr>
                  <td>${this._example("orderby:createddesc")}</td>
                  <td>Created time, newest first</td>
                </tr>
                <tr>
                  <td>${this._example("orderby:createdasc")}</td>
                  <td>Created time, oldest first</td>
                </tr>
                <tr>
                  <td>${this._example("orderby:creatordesc")}</td>
                  <td>Creator email Z→A</td>
                </tr>
                <tr>
                  <td>${this._example("orderby:creatorasc")}</td>
                  <td>Creator email A→Z</td>
                </tr>
              </tbody>
            </table>
            <div class="combinations">
              <p class="section-title">Combinations</p>
              <p>
                Filters combine freely. The default sort is
                <code>orderby:datedesc</code> (newest first).
              </p>
              <div class="grid">
                ${this._example("in:inbox tag:important")}
                ${this._example("in:all orderby:createdasc")}
                ${this._example("with:alice@example.com tag:project")}
                ${this._example("in:pinned orderby:creatordesc")}
                ${this._example("creator:bob in:archive")}
                ${this._example("mentions:me unread:true")}
                ${this._example("tasks:all unread:true")}
              </div>
            </div>
          </section>
        </div>
      </div>
    `;
  }
}

if (!customElements.get("wavy-search-help")) {
  customElements.define("wavy-search-help", WavySearchHelp);
}

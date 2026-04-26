# F-2.S3: Search rail + search-help modal + wavy header chrome

Status: Ready for implementation
Owner: codex/issue-1047-search-rail worktree
Issue: [#1047](https://github.com/vega113/supawave/issues/1047)
Parent umbrella: [#1037](https://github.com/vega113/supawave/issues/1037) (slice 3 of 6 — does NOT close the umbrella)
Tracker: [#904](https://github.com/vega113/supawave/issues/904)
Inventory: `docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md`

Foundation in place (all merged to main):
- F-0 (#1035, sha `af7072f9`) — wavy design tokens (`--wavy-*`), `wavy-blip-card` / `wavy-rail-panel` / `wavy-depth-nav` recipes, plugin-slot contracts.
- F-1 (#1036, sha `86ea6b44`) — viewport-scoped data path; depth-axis fragment contract.
- F-2.S1 (#1045, sha `f4a6cd6f`) — `<wave-blip>` + `<wave-blip-toolbar>` Lit elements with the per-blip server-first contract; J2clStageOneReadSurfaceParityTest (8 cases) covering the StageOne read-surface server contract.

Sibling slices (touch different files; safe to land in any order):
- S2 (#1046) — `wavy-wave-nav-row.js`, `wavy-focus-frame.js`, `wavy-depth-nav-bar.js`.
- S4 (#1048) — `wavy-version-history.js`, `wavy-profile-overlay.js`, floating control elements.

## 1. Why this slice exists

S1 lit up the per-blip read surface. S3 lights up the **left rail and the top chrome** that sit around it: the search query box with saved-search folder rail, the search-help modal that documents every filter/sort token, and the wavy header end-region (locale picker, notifications bell, mail icon, user-menu trigger, brand link).

Today on `?view=j2cl-root` the header is the placeholder `<shell-header>` from #964 ("Signed in as <email> · Admin · Sign out"). There is no search rail at all — clicking through the placeholder leads to the legacy GWT search panel, which means none of the inventory rows B.* / C.* are reachable from the J2CL surface.

This slice ships the wavy `<wavy-header>`, `<wavy-search-rail>`, and `<wavy-search-help>` Lit elements, a server-side mounting in `renderJ2clRootShellPage`, and a parity fixture that asserts each of the 45 inventory affordances. The 22 search filter/sort tokens are server-side-tested through the existing `QueryHelper.parseQuery` parser to confirm the help modal advertises only tokens that actually parse.

## 2. Verification ground truth (re-derived in worktree, HEAD `f4a6cd6f`)

### Server side

- `wave/src/main/java/org/waveprotocol/box/server/waveserver/TokenQueryType.java:30-42` — canonical token enum (IN, ORDERBY, WITH, CREATOR, ID, TAG, UNREAD, CONTENT, TITLE, MENTIONS, TASKS).
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java:204-244` — `OrderByValueType` (DATEASC, DATEDESC, CREATEDASC, CREATEDDESC, CREATORASC, CREATORDESC).
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java:315-362` — `parseQuery(String)` is the contract surface. Throws `InvalidQueryException` on unknown tokens / values; tokens without `:` fall back to CONTENT.
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java:118` — `DEFAULT_SEARCH = "in:inbox"`.
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java:596,679,689` — saved-search defaults: Inbox = `in:inbox`, Archive = `in:archive`, Pinned = `in:pinned`.
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml` — every example chip in the GWT search-help panel; this is the authoritative C.1–C.22 token list.
- `wave/src/main/java/org/waveprotocol/box/webclient/search/i18n/SearchWidgetMessages_en.properties` — saved-search labels (Public, Inbox, Archive). The other folders (Mentions, Tasks, Pinned) are constructed in `SearchPresenter` Java.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3320-3460` — `renderJ2clRootShellPage` is the J2CL root template. S3 extends the header + nav slot to mount `<wavy-header>` + `<wavy-search-rail>`; the `<shell-skip-link>` / `<shell-status-strip>` framing remains intact.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneReadSurfaceParityTest.java` — the F-2.S1 fixture. S3 adds a sibling fixture, `J2clSearchRailParityTest`, in the same package; the per-row pattern (mock servlet → assert tokens in HTML) carries over verbatim.

### J2CL/Lit client side

- `j2cl/lit/src/design/wavy-tokens.css:17-173` — F-0 token contract (`--wavy-bg-base`, `--wavy-bg-surface`, `--wavy-text-body`, `--wavy-text-muted`, `--wavy-text-quiet`, `--wavy-signal-cyan`, `--wavy-accent-violet`, `--wavy-accent-amber`, `--wavy-pulse-ring`, `--wavy-radius-card`, `--wavy-spacing-{1..6}`, `--wavy-type-{body,h2,h3,label,meta}`, `--wavy-motion-focus-duration`, `--wavy-easing-focus`).
- `j2cl/lit/src/design/wavy-rail-panel.js:10-138` — F-0 recipe; S3's `<wavy-search-rail>` is composed *next to* this (it is its own element so it can own the search textbox + saved-search folder selection state without inheriting the rail-panel collapse behaviour).
- `j2cl/lit/src/elements/shell-header.js:3-58` — the existing #964 placeholder `<shell-header>` keeps its slot contract; S3's `<wavy-header>` mounts inside the `actions-signed-in` slot of `<shell-header>`. Backwards-compatible: nothing in S2/S4/S5/S6 needs the inner `<shell-header>` markup to change.
- `j2cl/lit/src/elements/wave-blip.js`, `wave-blip-toolbar.js` — F-2.S1 wrapper pattern that S3 mirrors for its own elements (LitElement, attribute reflection, CustomEvent emission, light DOM slots for plugin extensions, `customElements.get(...)` register-once guard).
- `j2cl/lit/src/index.js` — must register the three new elements (`wavy-header`, `wavy-search-rail`, `wavy-search-help`) so the bundled `shell.js` upgrades them on the J2CL root shell page.
- `j2cl/lit/test/wave-blip.test.js`, `wave-blip-toolbar.test.js` — testing pattern: `@open-wc/testing` `fixture(html...)`, register-check, attribute reflection, slot propagation, event emission.

## 3. Token contract — the wavy CSS variables S3 consumes (re-confirm same as F-0)

| Token | Use in S3 |
| --- | --- |
| `--wavy-bg-base` | Page background under header + rail |
| `--wavy-bg-surface` | Header band, rail panel, modal sheet |
| `--wavy-text-body` | Default body text |
| `--wavy-text-muted` | Secondary labels (timestamps, result count) |
| `--wavy-text-quiet` | Slot-extension labels in design preview |
| `--wavy-signal-cyan` | Brand accent dot, focused folder, primary buttons (New Wave), pulse-ring on per-digest unread badge change |
| `--wavy-signal-violet` | Mentions unread dot (header bell + Mentions folder) |
| `--wavy-signal-amber` | Tasks pending count chip |
| `--wavy-pulse-ring` | Signal-cyan box-shadow ring composition (used by `firePulse()` `data-pulse="ring"` styling) |
| `--wavy-motion-pulse-duration` | Duration the `data-pulse="ring"` attribute stays active (600ms) |
| `--wavy-radius-card` | Card / modal corner radius |
| `--wavy-border-hairline` | Card / modal hairline border |
| `--wavy-spacing-{1..6}` | Layout spacing |
| `--wavy-type-{body,h2,h3,label,meta}` | Type scale |
| `--wavy-motion-focus-duration` + `--wavy-easing-focus` | Folder selection + modal open transitions |

S3 DOES NOT introduce new tokens. Anywhere a token is consumed, the element provides a literal fallback in the `var(...)` call so the elements work in isolation tests without `wavy-tokens.css` (mirrors the F-0 + S1 pattern).

## 4. Acceptance contract — every cited inventory affordance

No "practical parity" escape hatch. Each item below has a concrete assertion in either the per-row JS test (`*.test.js`) or the per-row server fixture (`J2clSearchRailParityTest`).

### Header chrome — `<wavy-header>`

| # | Affordance | Assertion |
| --- | --- | --- |
| A.1 | SupaWave brand link → landing | `<wavy-header>` renders `a.brand[href]` with `href="/"`, signal-cyan dot via `<span class="brand-dot" aria-hidden="true"></span>` before logotype text. `wavy-header.test.js` asserts `getComputedStyle(brandDot).backgroundColor` resolves to a non-empty value AND that the dot's inline CSS uses `background: var(--wavy-signal-cyan, #22d3ee)` (a substring assertion on `el.shadowRoot.adoptedStyleSheets[0].cssRules` text suffices when computed color is degraded under headless tests). |
| A.2 | Locale picker (en/de/es/fr/ru/sl/zh_TW) | `<wavy-header>` renders `<select class="locale" aria-label="Language">` with one `<option>` per locale; default value reflects `locale` property. Server-side: `J2clSearchRailParityTest.headerEmitsLocalePicker` asserts the `<wavy-header locale="en">` token + the seven `<option value="...">` tokens. |
| A.5 | Notifications bell (with unread dot) | `<wavy-header>` renders `<button type="button" class="bell" aria-label="Notifications">` containing an inline SVG bell glyph and `<span class="dot violet" hidden>` that becomes visible when `unread-count > 0`. Asserted in `wavy-header.test.js`. |
| A.6 | Inbox/mail icon | `<wavy-header>` renders `<a class="mail" href="/?q=in:inbox" aria-label="Inbox">` containing an inline SVG envelope glyph. Asserted in `wavy-header.test.js`. |
| A.7 | User-menu trigger (avatar chip + email) | `<wavy-header>` renders `<button type="button" class="user-menu" aria-haspopup="menu" aria-expanded="false">` wrapping an avatar `<span class="avatar">` (initials from `user-name` attribute) AND a visible `<span class="user-email">${address}</span>` next to the avatar (matches GWT inventory A.7 "avatar + email"). Click emits `wavy-user-menu-requested` CustomEvent with `{detail: {address}}` so the F-0 menu sheet (already on main) can mount. Asserted in `wavy-header.test.js`. |

A.7 mounts the *trigger* only; the menu items themselves come from F-0 and are out of scope for this slice.

### Search rail — `<wavy-search-rail>`

| # | Affordance | Assertion |
| --- | --- | --- |
| B.1 | Search query textbox (default `in:inbox`) | `<wavy-search-rail>` renders `<input type="search" name="q" class="query" .value=${query}>` (default value `in:inbox`). Adjacent `<span class="waveform" aria-hidden>` with inline SVG waveform glyph. Pressing Enter emits `wavy-search-submit` CustomEvent `{detail: {query}}`. Asserted in `wavy-search-rail.test.js`. |
| B.2 | Search-help trigger (`?` button) → opens modal | Renders `<button type="button" class="help-trigger" aria-label="Search help" aria-haspopup="dialog" aria-controls="wavy-search-help">?</button>`. Click emits `wavy-search-help-toggle` CustomEvent (composed + bubbles) so the document-level `<wavy-search-help id="wavy-search-help">` singleton (mounted by T5 in the root shell, NOT inside the rail) can flip its `open` attribute. Asserted in `wavy-search-rail.test.js` (the rail does NOT own the modal instance — see §8 mount decision). |
| B.3 | New Wave button (Shift+Cmd+O) | Renders `<button type="button" class="new-wave" data-shortcut="Shift+Cmd+O" aria-keyshortcuts="Shift+Meta+O Shift+Control+O">New Wave</button>`. Click emits `wavy-new-wave-requested` CustomEvent (composed + bubbles). The keyboard shortcut handler is intentionally OUT OF SCOPE for S3 — it ships with S6 (URL state + global keymap) so we don't conflict with S2's per-blip arrow / j / k handler. Asserted in `wavy-search-rail.test.js` for click + `aria-keyshortcuts` attribute presence. |
| B.4 | Manage saved searches | Renders `<button type="button" class="manage-saved">Manage saved searches</button>` inside the saved-search rail header; click emits `wavy-manage-saved-searches-requested`. Asserted in `wavy-search-rail.test.js`. |
| B.5–B.10 | Saved-search folders (Inbox selected by default, Mentions with violet unread dot, Tasks with amber pending count, Public, Archive, Pinned) | Renders an `<ul class="folders">` with one `<li><button class="folder" data-query="..." aria-current=${selected ? "page" : "false"}>` per folder. Folder query strings (single source of truth, used by both the rail and the parser fixture): Inbox=`in:inbox`, Mentions=`mentions:me`, Tasks=`tasks:me`, Public=`with:@`, Archive=`in:archive`, Pinned=`in:pinned`. Inbox carries `aria-current="page"` by default. Mentions carries a violet `<span class="dot mentions-dot" hidden>`; when `mentions-unread > 0` the `hidden` attribute is dropped. Tasks carries an amber `<span class="chip tasks-chip">N</span>`; visible when `tasks-pending > 0`. Active-folder derivation: when the rail's `query` attribute changes, an internal `deriveActiveFolderFromQuery(q)` pass picks the first folder whose query string is a prefix of the rail's query (case-insensitive); the matched folder's button gets `aria-current="page"`. Asserted in `wavy-search-rail.test.js`: (a) all six folder buttons render with the canonical `data-query` strings, (b) Inbox is `aria-current="page"` by default, (c) setting `query="in:archive orderby:datedesc"` flips `aria-current="page"` to the Archive folder. |
| B.11 | Refresh search results | Renders `<button class="refresh" aria-label="Refresh search results">⟳</button>`. Click emits `wavy-search-refresh-requested`. Asserted in `wavy-search-rail.test.js`. |
| B.12 | Result count "N waves" | Renders `<p class="result-count" aria-live="polite">${count} waves</p>` with low-emphasis (`color: var(--wavy-text-muted)`). When `count` is `null/undefined` the element renders an empty `<p>` (no count yet). Asserted in `wavy-search-rail.test.js`. |
| B.13 | Per-digest avatar stack (multi-author) | New child element `<wavy-search-rail-card>`: renders `<div class="avatar-stack">` containing one `<span class="avatar" data-initials="..">` per author (max 3 visible + `+N`). Asserted in `wavy-search-rail-card.test.js`. |
| B.14 | Per-digest pinned indicator | `<wavy-search-rail-card>` reflects the `pinned` boolean attribute. When set, renders `<span class="pin" aria-label="Pinned">📌</span>` (literal `<svg class="pin">` cyan pin glyph) styled via `color: var(--wavy-signal-cyan)`. Asserted in `wavy-search-rail-card.test.js`. |
| B.15 | Per-digest title | `<wavy-search-rail-card>` renders `<h3 class="title">${title}</h3>`. Asserted in `wavy-search-rail-card.test.js`. |
| B.16 | Per-digest snippet (multiline truncation) | `<wavy-search-rail-card>` renders `<p class="snippet">${snippet}</p>` styled with `display: -webkit-box; -webkit-line-clamp: 3; overflow: hidden;` so the snippet truncates at three lines. Asserted by computed style in `wavy-search-rail-card.test.js`. |
| B.17 | Per-digest msg count + unread badge with cyan signal-pulse | `<wavy-search-rail-card>` renders `<span class="msg-count">${msgCount}</span>`. When `unread-count > 0` it appends `<span class="badge unread"><span class="pulse"></span>${unread}</span>`. The `firePulse()` method on the card sets `data-pulse="ring"` for `--wavy-pulse-ring-duration` ms then clears it. Mirrors the `firePulse()` contract from `wavy-blip-card`. Asserted in `wavy-search-rail-card.test.js`. |
| B.18 | Per-digest relative timestamp | `<wavy-search-rail-card>` renders `<time class="ts" datetime=${iso} title=${iso}>${rel}</time>`. Asserted in `wavy-search-rail-card.test.js`. |

### Search-help modal — `<wavy-search-help>`

`<wavy-search-help>` is a `role="dialog"` modal with `aria-modal="true"`. It mounts its content into `<dialog>` if available, falling back to a fixed-position `<div role="dialog">` for environments without `<dialog>` support.

**Mount point (single-source-of-truth):** A SINGLE `<wavy-search-help id="wavy-search-help">` instance lives at the document level (sibling of `<wavy-search-rail>`, mounted by T5 inside the `nav` slot of `<shell-root>`). The rail's help-trigger fires a `wavy-search-help-toggle` CustomEvent (composed + bubbles); a small `connectedCallback`-installed listener on the help element flips its own `open` attribute. The rail does NOT own a child `<wavy-search-help>` — this avoids duplicate dialogs and matches the GWT pattern of a single backdrop appended to `document.body`.

**`tasks:*` rows scope clarification (issue C.13–C.15):** The modal LISTS `tasks:all`, `tasks:me`, `tasks:user@domain` as discoverable tokens because the GWT modal already advertises them and the parser already accepts them through `TokenQueryType.TASKS` (no F-3 dependency). The behavioral plumbing (filtering search results by `tasks:me` and showing actual task chips on result cards) is owned by F-3. S3's contract for C.13–C.15 is exclusively "the help modal advertises the token literally, and the parser accepts it." This is consistent with the C.* parser fixture which only asserts that `parseQuery("tasks:me")` does not throw — it does NOT assert any task-result-filtering behaviour.

| # | Token / Affordance | Assertion |
| --- | --- | --- |
| C.1 | Filter `in:inbox` | Modal body table contains `<code>in:inbox</code>` row. Click on the example fires `wavy-search-help-example` CustomEvent `{detail: {query: "in:inbox"}}` so the rail can populate the textbox. |
| C.2 | Filter `in:archive` | Same as C.1 with `in:archive`. |
| C.3 | Filter `in:all` | Same with `in:all`. |
| C.4 | Filter `in:pinned` | Same with `in:pinned`. |
| C.5 | Filter `with:user@domain` | Description column shows `<code>with:user@domain</code>`; example chip is `with:alice@example.com`. |
| C.6 | Filter `with:@` (public) | `<code>with:@</code>` row. |
| C.7 | Filter `creator:user@domain` | `<code>creator:user@domain</code>` row, example chip `creator:bob@example.com`. |
| C.8 | Filter `tag:name` | `<code>tag:name</code>` row, example chip `tag:important`. |
| C.9 | Filter `unread:true` | `<code>unread:true</code>` row. |
| C.10 | Filter `title:text` | `<code>title:text</code>` row, example chip `title:meeting`. |
| C.11 | Filter `content:text` | `<code>content:text</code>` row, example chip `content:agenda`. |
| C.12 | Filter `mentions:me` | `<code>mentions:me</code>` row. |
| C.13 | Filter `tasks:all` | `<code>tasks:all</code>` row. |
| C.14 | Filter `tasks:me` | `<code>tasks:me</code>` row. |
| C.15 | Filter `tasks:user@domain` | `<code>tasks:user@domain</code>` row, example chip `tasks:alice@example.com`. |
| C.16 | Free text (implicit `content:`) | Row labelled `free text`, example chip `meeting notes`. |
| C.17 | Sort `orderby:datedesc` (default) | Sort table row with `<code>orderby:datedesc</code>` and "(default)" callout. |
| C.18 | Sort `orderby:dateasc` | Sort table row. |
| C.19 | Sort `orderby:createddesc` / `createdasc` | Two adjacent rows. |
| C.20 | Sort `orderby:creatordesc` / `creatorasc` | Two adjacent rows. |
| C.21 | Combinations examples | A `<section class="combinations">` containing seven `<code class="example">` chips (mirrors GWT panel: `in:inbox tag:important`, `in:all orderby:createdasc`, `with:alice@example.com tag:project`, `in:pinned orderby:creatordesc`, `creator:bob in:archive`, `mentions:me unread:true`, `tasks:all unread:true`). Each chip click emits `wavy-search-help-example`. |
| C.22 | "Got it" dismiss | `<button class="dismiss">Got it</button>` closes the dialog (`open=false`) and emits `wavy-search-help-dismissed`. |

#### C.* server-side parser test

A second new fixture, `J2clSearchHelpTokenParseTest` (in `wave/src/test/java/org/waveprotocol/box/server/waveserver/`), feeds each of the 22 token strings advertised by `<wavy-search-help>` through `QueryHelper.parseQuery` and asserts:

1. The advertised token strings (`in:inbox`, `in:archive`, …, `orderby:datedesc`, …) all parse without `InvalidQueryException`.
2. Each filter token deposits the expected `TokenQueryType` key in the result map.
3. Each `orderby:` value resolves to a non-null `OrderByValueType.fromToken(value)`.
4. Free text (`meeting notes`, `agenda`) parses into the `CONTENT` bucket.
5. Each combination example string (the seven C.21 chips) parses without exception.
6. NEGATIVE: `in:bogus` is accepted as `in:bogus` by `parseQuery` (in/with/etc. accept arbitrary value strings), but `orderby:bogus` throws `InvalidQueryException`. This guards the contract that the help modal must NOT advertise `orderby:` values that are not in `OrderByValueType`.

The fixture is the contract enforcer: if the modal ever advertises a token the parser rejects, the test fails. This is the "do NOT invent new tokens" guard.

### Per-row server fixture — `J2clSearchRailParityTest`

`wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clSearchRailParityTest.java` — sibling of `J2clStageOneReadSurfaceParityTest` (mirrors the helper plumbing exactly: same `WaveletProvider` mock, same `WaveClientServlet` + `invokeServlet` helpers, same `countOccurrences` helper). Drives a 6-blip wave (so the snapshot path stays the same) through both `?view=j2cl-root` and `?view=gwt`.

**Server-first contract (mirrors S1's `<wavy-blip-card>` design-preview path):** The fixture asserts on the SERVER-RENDERED HTML, not on a post-upgrade DOM. So T5 must SSR the inner light DOM of each new element directly into the page (the Lit elements upgrade in place, replacing their server-rendered light DOM with the rendered template; both representations carry the same data-attributes and inner text the fixture asserts on). This is the same contract S1 uses: `J2clStageOneReadSurfaceParityTest.serverFirstPaintEmitsContractBlipAttributesForJ2clUpgrade` asserts on `data-blip-id=` substrings the server renders, and the client `<wave-blip>` upgrade preserves them.

Concretely T5 emits, server-side:

1. `<wavy-header signed-in locale="en" data-address="${safeAddress}" user-name="${safeAddress}" unread-count="0">` containing the FULL inner markup the client would render (brand link with `<span class="brand-dot">`, `<select class="locale">` with the seven `<option value="..">`, notifications bell button with class `bell`, mail icon `<a class="mail">`, user-menu button with `<span class="avatar">` and `<span class="user-email">`).
2. `<wavy-search-rail query="in:inbox" data-active-folder="inbox" result-count="">` containing the full inner markup (`<input type="search" class="query" value="in:inbox">`, help-trigger button, New Wave button, Manage saved searches button, `<ul class="folders">` with the six `<li><button class="folder" data-query="..." aria-current="${...}">` rows, refresh button, `<p class="result-count" aria-live="polite">`).
3. `<wavy-search-help id="wavy-search-help">` (initially without `open`) containing the FULL modal body — title "Search Help", filter table with all 22 tokens as `<code>` substrings, sort table, combinations section, `<button class="dismiss">Got it</button>`. The element renders the same markup client-side; the upgrade is a no-op for content (only event handlers wire up).

Per-test cases:

1. `j2clRootShellEmitsWavyHeaderHostWithLocaleAndAddressAttributes` — server HTML contains exactly one `<wavy-header` host with `signed-in`, `locale="en"`, `data-address="${address}"`, `user-name="${address}"` attributes.
2. `wavyHeaderInnerLightDomEmitsAllSevenLocales` — header inner light DOM contains `<option value="en"`, `<option value="de"`, `<option value="es"`, `<option value="fr"`, `<option value="ru"`, `<option value="sl"`, `<option value="zh_TW"` substrings.
3. `wavyHeaderInnerLightDomEmitsBrandLocaleBellMailUserMenuChrome` — header inner light DOM contains `class="brand"`, `class="brand-dot"`, `class="locale"`, `class="bell"`, `class="mail"`, `class="user-menu"`, `class="avatar"`, `class="user-email"` substrings (one assertion per affordance).
4. `j2clRootShellEmitsWavySearchRailHostWithDefaultQuery` — server HTML contains exactly one `<wavy-search-rail` host with `query="in:inbox"` and `data-active-folder="inbox"`.
5. `wavySearchRailInnerLightDomEmitsAllSixSavedSearchFolders` — rail inner light DOM contains the six folder buttons with the canonical query strings (`data-query="in:inbox"`, `data-query="mentions:me"`, `data-query="tasks:me"`, `data-query="with:@"`, `data-query="in:archive"`, `data-query="in:pinned"`) AND the six visible labels ("Inbox", "Mentions", "Tasks", "Public", "Archive", "Pinned").
6. `wavySearchRailInnerLightDomEmitsHelpTriggerNewWaveManageRefreshAndCountSlots` — rail inner light DOM contains `class="help-trigger"`, `class="new-wave"`, `class="manage-saved"`, `class="refresh"`, `class="result-count"`, plus `aria-keyshortcuts="Shift+Meta+O Shift+Control+O"` on the New Wave button.
7. `j2clRootShellEmitsWavySearchHelpModalWithAll22TokenLiterals` — server HTML contains exactly one `<wavy-search-help` host (no `open` attribute), and its inner markup contains every one of the 22 token literals: the four `in:` tokens, `with:user@domain`, `with:@`, `creator:user@domain`, `tag:name`, `unread:true`, `title:text`, `content:text`, `mentions:me`, `tasks:all`, `tasks:me`, `tasks:user@domain`, the six `orderby:` values, plus the `free text` row marker, plus the seven combination examples (`in:inbox tag:important`, `in:all orderby:createdasc`, `with:alice@example.com tag:project`, `in:pinned orderby:creatordesc`, `creator:bob in:archive`, `mentions:me unread:true`, `tasks:all unread:true`), plus the `Got it` literal.
8. `legacyGwtRouteDoesNotLeakF2S3Markers` — `?view=gwt` HTML contains none of `<wavy-header`, `<wavy-search-rail`, `<wavy-search-help`, `class="brand-dot"`. The legacy `<g:HTMLPanel>` GWT search panel is unchanged.
9. `signedOutJ2clRootShellDoesNotMountSearchRailOrHeaderChrome` — passing a `null` viewer to the same servlet yields a signed-out `<shell-root-signed-out>` page that contains NEITHER `<wavy-header` NOR `<wavy-search-rail` NOR `<wavy-search-help`. The signed-out branch keeps its existing `<shell-header>` placeholder and the simple Sign-in link. (Defends against accidentally regressing the unauthenticated landing into emitting search/help to anonymous users.)

### S3 saved-search query strings (clarification re: B.5–B.10)

GWT today has only Inbox / Archive / Pinned / Public as named tokens (`in:inbox` / `in:archive` / `in:pinned` / `with:@`). Mentions and Tasks reuse the existing query language: Mentions = `mentions:me`, Tasks = `tasks:me`. The wavy rail therefore advertises:

- Inbox → `in:inbox` (selected by default)
- Mentions → `mentions:me`
- Tasks → `tasks:me`
- Public → `with:@`
- Archive → `in:archive`
- Pinned → `in:pinned`

All six query strings parse through `QueryHelper.parseQuery`; the parity fixture asserts the strings appear in the rail HTML, and `J2clSearchHelpTokenParseTest` (C.* fixture above) asserts the parser accepts each.

## 5. Tasks (T1–T5; each ≤ ~250 LOC of production code with paired tests)

### T1 — `<wavy-search-help>` Lit element + 22-token parser test

Files added:

- `j2cl/lit/src/elements/wavy-search-help.js` — modal element with the 22 token rows + 7 combination examples + Got-it dismiss.
- `j2cl/lit/test/wavy-search-help.test.js` — registration + open/close + every-token-row-present + chip-click-fires-event + dismiss-button + dialog `aria-modal` + reduced-motion.
- `wave/src/test/java/org/waveprotocol/box/server/waveserver/J2clSearchHelpTokenParseTest.java` — parser fixture per §4 C.* server-side test.
- `j2cl/lit/src/index.js` — register the element.

Verification: `cd j2cl/lit && npm test` and `sbt -batch j2clLitTest test` (the JUnit fixture lives under `wave/src/test/...` which `sbt test` runs).

### T2 — `<wavy-search-rail-card>` digest card + tests

Files added:

- `j2cl/lit/src/elements/wavy-search-rail-card.js` — single-card element wrapping author stack, pinned indicator, title, snippet, msg count + unread pulse, timestamp.
- `j2cl/lit/test/wavy-search-rail-card.test.js` — registration + attribute reflection + multi-author stack truncation at 3 + pinned reflection + unread pulse fires + snippet 3-line clamp + timestamp `<time datetime=...>`.
- `j2cl/lit/src/index.js` — register.

Verification: `cd j2cl/lit && npm test`.

### T3 — `<wavy-search-rail>` Lit element + tests

Files added:

- `j2cl/lit/src/elements/wavy-search-rail.js` — query textbox + waveform glyph + help trigger (emits `wavy-search-help-toggle`; does NOT own the modal) + New Wave button (emits `wavy-new-wave-requested`; carries `aria-keyshortcuts` but does NOT install a global keyboard listener) + Manage saved searches + 6 folder rows + Refresh + result-count + a `<slot>` for the digest list (callers populate with `<wavy-search-rail-card>` instances).
- `j2cl/lit/test/wavy-search-rail.test.js` — registration + default `in:inbox` query + Enter submits + help-trigger click emits `wavy-search-help-toggle` (no inline modal) + New Wave click emits `wavy-new-wave-requested` + New Wave button has `aria-keyshortcuts="Shift+Meta+O Shift+Control+O"` attribute + folder click sets `aria-current="page"` and emits `wavy-saved-search-selected` + setting `query="in:archive ..."` flips Archive folder to `aria-current="page"` (active-folder derivation) + Mentions violet dot uses `--wavy-signal-violet` + Tasks amber count uses `--wavy-signal-amber` + Refresh emits + result-count `aria-live="polite"`.
- `j2cl/lit/src/index.js` — register.

Verification: `cd j2cl/lit && npm test`.

### T4 — `<wavy-header>` Lit element + tests

Files added:

- `j2cl/lit/src/elements/wavy-header.js` — brand link with cyan signal dot + locale picker `<select>` (7 options, emits `wavy-locale-changed` on change) + notifications bell with violet unread dot + mail icon + user-menu trigger button (avatar chip with initials + visible email span).
- `j2cl/lit/test/wavy-header.test.js` — registration + brand link with cyan dot (cssRules contains `var(--wavy-signal-cyan`) + locale picker has 7 `<option>` entries with the canonical codes + locale `change` emits `wavy-locale-changed` + notifications bell unread dot toggles on `unread-count > 0` + bell unread dot CSS uses `var(--wavy-signal-violet` (NOT cyan) — assert via cssRules substring + mail icon `href="/?q=in:inbox"` + user-menu trigger renders both `<span class="avatar">` initials AND visible `<span class="user-email">` matching `data-address` + user-menu click emits `wavy-user-menu-requested`.
- `j2cl/lit/src/index.js` — register.

Verification: `cd j2cl/lit && npm test`.

### T5 — Server SSR in `renderJ2clRootShellPage` + `J2clSearchRailParityTest`

Files modified:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — inside the `signedIn` branch of `renderJ2clRootShellPage`:
  1. Inside the `<shell-header slot="header" signed-in>` block, replace the placeholder `<span slot="actions-signed-in">Signed in as ${safeAddress}</span>` with a `<wavy-header slot="actions-signed-in" signed-in locale="en" data-address="${safeAddress}" user-name="${safeAddress}" unread-count="0">` host element WHOSE INNER LIGHT DOM IS SERVER-RENDERED IN FULL. The server emits the brand link, locale `<select>` with the seven `<option value="...">` entries, the bell button, the mail icon, and the user-menu trigger (avatar + email span). Lit upgrade re-renders the same content into the shadow DOM and wires event handlers; the server-rendered light DOM is what `J2clSearchRailParityTest` asserts on (mirrors S1 `<wavy-blip-card>` server-render contract).
  2. Replace the single `<a href="${returnTarget}">Inbox</a>` child of `<shell-nav-rail slot="nav" label="Primary">` with `<wavy-search-rail query="in:inbox" data-active-folder="inbox" result-count="">` containing the FULL inner light DOM (search input, help-trigger, New Wave, Manage saved searches, six folder rows, refresh, result-count `<p>` slot). The `<shell-nav-rail>` wrapper is preserved so future slices can co-mount additional nav-area elements alongside the search rail.
  3. After the `<shell-nav-rail>` replacement, mount EXACTLY ONE document-level `<wavy-search-help id="wavy-search-help">` (no `open` attribute initially) AS A DIRECT CHILD OF `<shell-root>` (NOT inside any slot) so it can `dialog.showModal()` over everything. Inner light DOM contains the full title bar, filters table (16 rows), sort table (6 rows), combinations section (7 chips), Got-it dismiss.
  4. The inline `Admin` link and inline `Sign out` link in `actions-signed-in` are PRESERVED unchanged (alongside the new `<wavy-header>`) — F-0's user-menu sheet element is NOT yet on main, so removing the inline links would orphan affordances A.15 (Admin) and A.17 (Sign out) on the J2CL route. Once F-0's `<wavy-user-menu-sheet>` ships (separate issue), a follow-up slice deletes the inline duplicates. The S3 parity test asserts BOTH `<wavy-header>` and the legacy inline `Admin` / `Sign out` `<a>` tags remain present.

The signed-out branch is NOT touched in this slice (search rail + help modal only mount when signed in; the locale picker for the signed-out flow is a future slice or already covered by the legacy GWT signed-out shell). `J2clSearchRailParityTest.signedOutJ2clRootShellDoesNotMountSearchRailOrHeaderChrome` enforces that.

Files added:

- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clSearchRailParityTest.java` — sibling fixture per §4 list (8 cases).

Verification: `sbt -batch jakartaTest:testOnly *J2clSearchRailParityTest *J2clStageOneReadSurfaceParityTest j2clLitTest j2clProductionBuild`.

## 6. Out of scope (deferred to other slices / issues)

- Wave panel chrome (D.1–D.8): owned by S2 (#1046).
- Per-blip controls (F.1–F.12): already shipped in S1 (#1045).
- Floating + version-history controls (J.1–J.5, K.1–K.6): owned by S4 (#1048).
- URL state + read state (G.1–G.6 depth-nav URL / R-4.4 live unread): owned by S5.
- New filter / sort tokens beyond what GWT supports: explicitly forbidden by the slice brief.
- A.3 "All changes saved" + A.4 "Online" connection state: owned by F-4.
- A.7 menu items themselves (A.8–A.18): owned by F-0; S3 only mounts the trigger.
- Locale picker actually changing the locale: F-2.S1 / F-2.S6 own the routing wiring; S3 only renders the picker. The selection emits a `wavy-locale-changed` CustomEvent that S6 wires to `window.location.search` updates.

## 7. Risk register

- **Risk: GWT search-help and wavy search-help diverge over time.** Mitigation: `J2clSearchHelpTokenParseTest` parses every advertised token through the canonical `QueryHelper.parseQuery`. Anyone adding a new token to either side that isn't in `TokenQueryType` / `OrderByValueType` fails the test.
- **Risk: `<wavy-header>` end-region overflows on narrow viewports.** Mitigation: the element CSS uses `flex-wrap: wrap` on the host and shrinks the locale picker / hides the email span under `(max-width: 600px)`. The host-level wrap behavior is purely declarative CSS — no Lit logic — so the test surface stays at the per-affordance level (locale picker emits 7 options, mail link href is `/?q=in:inbox`, etc.). A future visual-regression slice can add the viewport-resize assertion if it becomes load-bearing.
- **Risk: `<wavy-search-help>` `<dialog>` polyfill lock-up under iOS Safari.** Mitigation: feature-detect `HTMLDialogElement.prototype.showModal` and fall back to a fixed-position div with `aria-modal="true"`. Test asserts both code paths.
- **Risk: Saved-search default selection drifts when the user lands with `?q=in:archive`.** Mitigation: `<wavy-search-rail>` reflects `data-active-folder` based on current `query` — if the query starts with `in:archive` the Archive folder gets `aria-current="page"`. Pure-derivation logic with a unit test.
- **Risk: `J2clSearchRailParityTest` becomes flaky if HTML order changes.** Mitigation: assertions use `String.contains(...)` on stable substrings (element-name + key attribute), not regex on whitespace.

## 8. Verification before PR

1. `cd j2cl/lit && npm test` — all new + existing Lit tests pass.
2. `sbt -batch jakartaTest:testOnly *J2clSearchRailParityTest *J2clStageOneReadSurfaceParityTest` — both parity fixtures pass.
3. `sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild` — all three SBT tasks exit 0.
4. `sbt -batch testOnly *J2clSearchHelpTokenParseTest *QueryHelperTest` — parser fixtures pass; existing QueryHelperTest is not regressed.
5. Worktree-local server boot:
   - `./mvnw -Psearch-sidecar -DskipTests package` then run the staged server (per `feedback_local_server_verification_before_pr.md`).
   - Hit `https://localhost/?view=j2cl-root` after registering a fresh user — confirm header chrome (A.1, A.2, A.5, A.6, A.7) renders, search rail (B.1–B.18) renders with default `in:inbox` query, click `?` opens the modal with all 22 tokens.
   - Side-by-side at `?view=gwt` — confirm the legacy GWT search panel still renders unchanged (no F-2.S3 leakage).

PR title: `F-2 (slice 3): Search rail + search-help modal + wavy header chrome`.

PR body must start with: `Updates #1037 (slice 3 of 6 — does NOT close the umbrella). Updates #904. References #1047.`

Auto-merge: squash.

## 9. Closeout (after merge)

1. `/tmp/parity-chain/f2-s3-merged.txt` written with PR number + sha.
2. Comment on #1047 with the 45 affordances shipped + any deviations.
3. Comment on #904 with the slice 3 evidence.
4. Issue #1047 closes when the auto-merge lands; the umbrella #1037 stays open until S6.

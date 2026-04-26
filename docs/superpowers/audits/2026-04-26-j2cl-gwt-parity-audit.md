# J2CL ↔ GWT Parity Audit (2026-04-26)

Status: Final — follow-up chain filed 2026-04-26  
Audited by: J2CL parity lead session  
Audited against: `docs/j2cl-gwt-parity-matrix.md` (Updated 2026-04-25)  
Live deployment: `https://supawave.ai/`  
Build: `e66ce6013` (post-#1033 merge)  
Account: `vega@supawave.ai` (signed-in, `j2cl-root-bootstrap` feature flag enabled)

Follow-up issues filed against this audit (each cites specific matrix row IDs as hard acceptance):

- [#1035](https://github.com/vega113/supawave/issues/1035) F-0: Refresh Lit design packet for wavy aesthetic + plugin slot contracts
- [#1036](https://github.com/vega113/supawave/issues/1036) F-1: Re-execute viewport-scoped J2CL read-surface data path
- [#1037](https://github.com/vega113/supawave/issues/1037) F-2: Re-execute J2CL StageOne read surface (threaded blip rendering, focus, collapse, read/unread)
- [#1038](https://github.com/vega113/supawave/issues/1038) F-3: Re-execute J2CL StageThree compose surface (inline rich-text editor, mentions, tasks, reactions, attachments)
- [#1039](https://github.com/vega113/supawave/issues/1039) F-4: J2CL live read/unread state + feature activation visibility

## 1. Why this audit exists

The parity issue chain in `docs/j2cl-parity-issue-map.md` §6 is marked **completely closed** in GitHub (#931, #933, #936, #961–#971 all CLOSED). The lived experience of the J2CL root, however, is far from GWT parity. This audit walks the parity matrix row by row against the live deployment to surface the actual gap, so a follow-up issue chain can be filed against concrete deficiencies rather than against a “done” chain or a vague “redo the editor” framing.

## 2. Method

1. Open `https://supawave.ai/?view=j2cl-root` (J2CL surface) and `https://supawave.ai/?view=gwt` (forces the legacy GWT path past the `j2cl-root-bootstrap` flag, per `WaveClientServlet#resolveRequestedView` and `WaveClientServlet#doGet`).
2. Sign-in state and feature flag held constant across both surfaces.
3. For each parity-matrix row in §3–§7, observe current behavior on both surfaces side-by-side, capture the deficit, and assign one of:
   - **PASS** — J2CL meets the row.
   - **PARTIAL** — present but does not match GWT for the daily flow.
   - **FAIL** — not implemented on the J2CL surface.
   - **N/A** — not testable today against this deployment (e.g. requires harness fixture).
4. Collect a sequenced follow-up issue list scoped to "redo the slice with explicit GWT-parity acceptance," not new architecture.

Browser-test evidence stored as Chrome screenshots taken during the audit: J2CL inbox empty ([PR #1034 artifact](https://github.com/vega113/incubator-wave/pull/1034)), J2CL inbox loaded ([PR #1034 artifact](https://github.com/vega113/incubator-wave/pull/1034)), J2CL wave-open `ewlip` ([PR #1034 artifact](https://github.com/vega113/incubator-wave/pull/1034)), GWT inbox + same wave `ewlip` open ([PR #1034 artifact](https://github.com/vega113/incubator-wave/pull/1034)).

## 3. Read Surface (StageOne origin) — Section 3

| Row | Behavior | Status | Evidence |
| --- | --- | --- | --- |
| R-3.1 | Open-wave rendering — blip/thread DOM from conversation view | **FAIL** | J2CL renders all blips as a flat list with **raw blip IDs as section headings** ("Blip 3VPWzePL_DB", "Blip 8b1dxf0BXMA", …) and the blip content as plain text. There is no thread DOM, no nesting, no per-blip author/timestamp/avatar. GWT renders the same wave with author name + avatar + relative timestamp per blip, threaded indenting for replies, per-blip action toolbar (reply/edit/delete/link), and tags row at the bottom. |
| R-3.2 | Focus framing (`FocusFramePresenter`-equivalent) | **FAIL** | J2CL has no visible per-blip selection indicator. Arrow/`j`/`k` keys do not move a focus frame across blips. The "Recent / Next unread / Previous / Next / Last" buttons are present in the right panel but do not move a frame on the rendered content; they appear to drive the open-wave selection at the *list* level, not the *blip* level. |
| R-3.3 | Collapse — thread collapse/expand toggles | **FAIL** | No collapse affordance is rendered on any blip. Threads cannot be collapsed; replies cannot be hidden. |
| R-3.4 | Thread navigation — next/previous unread, deep-reply jumps | **PARTIAL** | The "Next unread / Previous mention / Next mention" buttons exist as labels but operate at the *wave list* level (jumping between waves in the search results) rather than blip-level navigation inside the open wave. The matrix row asks for blip-level unread-aware navigation; that is missing. |
| R-3.5 | Visible-region container model — viewport-scoped fragments | **FAIL** | The whole wave (all 30+ blips for the test wave) renders as a single flat scroll list. There is no visible-window placeholder, no scroll-driven extension, no loading placeholder. The transport is whole-wave. |
| R-3.6 | DOM-as-view provider — semantic queries against the rendered DOM | **FAIL (consequence of R-3.1)** | Without a real conversation DOM, there is no semantic view provider for keyboard, focus, collapse, or menus to query. The flat blip list has no role/label structure that semantic queries could traverse. |

**Read surface verdict: 0/6 PASS, 1/6 PARTIAL, 5/6 FAIL.** The closed slice #966 ("Port StageOne read-surface parity") did not deliver; what shipped is a flat read-out of conversation blip IDs, not a parity-equivalent read surface.

## 4. Live Surface (Stage Two origin) — Section 4

| Row | Behavior | Status | Evidence |
| --- | --- | --- | --- |
| R-4.1 | Socket / session lifecycle | **PASS** | J2CL displays "Live updates connected. supawave.ai/w+3VPWzePL_DA/-/conv+root · channel ch13 · snapshot v125" — socket opens and identifies the session. Auth landed (#933 closed). |
| R-4.2 | Bootstrap contract — no HTML scraping | **PASS** | `/bootstrap.json` contract is in place (#963 + #979 just merged). J2CL no longer scrapes root HTML for session/socket metadata. |
| R-4.3 | Reconnect — transient-loss recovery | **N/A (untested in this session)** | Not exercised in this audit; should be covered by a fixture once the read surface lands. Live banner styling for reconnect is not present on the J2CL shell today. |
| R-4.4 | Read/unread state live updates | **FAIL** | The wave list shows `12 messages` / `86 messages` etc. as raw counts — no per-user read/unread state, no unread badge styling, no count change indicators. GWT shows unread counts that decrement as the user opens waves. #931 ("Add live unread/read state") was closed but the behavior is not present on the J2CL list or open-wave panel. |
| R-4.5 | Route / history integration | **PARTIAL** | Selecting a wave updates the URL (`?wave=supawave.ai%2Fw%2B…`) and the open-wave region updates without page reload. Back/forward navigation appears to work for wave selection. Folder/tag navigation has no equivalent because the J2CL shell has no folders other than Inbox. Signed-in/out transitions not exercised. |
| R-4.6 | Fragment fetch policy — viewport-scoped loading | **FAIL** | Same as R-3.5 — whole-wave payload arrives as a single snapshot. No viewport hint is sent, no fragment extension fires on scroll. |
| R-4.7 | Feature activation and live-update application | **PARTIAL** | Live updates do apply (the snapshot version `v125` indicates server-side incremental application), but features like search, supplement, diff controller, reader are not visibly activated in the J2CL surface — there is no diff control, no search beyond a single text box, no reader mode. |
| R-4.8 | Selected-wave version/hash basis atomicity | **N/A (not testable in this audit)** | This is a write-path correctness property; would need a controlled race fixture or harness test. #936 closed without a J2CL-side write surface that exercises it meaningfully. |

**Live surface verdict: 2/8 PASS, 2/8 PARTIAL, 2/8 FAIL, 2/8 N/A.** The bootstrap and socket lanes are real wins; #931's read/unread work is the most prominent claimed-closed-but-missing item.

## 5. Compose / Edit Surface (StageThree origin) — Section 5

| Row | Behavior | Status | Evidence |
| --- | --- | --- | --- |
| R-5.1 | Compose / reply flow | **PARTIAL** | A reply textarea + "Send reply" button exist in the right-panel `OPENED WAVE` region with `Reply target: b+<blip-id>`. There is also a "New wave" textarea + "Create wave" button on the left panel. The composer is **not** an inline editor; it is a single textarea attached to the bottom of the right rail. There is no caret integration with the rendered conversation, no inline reply at a specific blip, no quote/draft preservation across blip selection, no Enter-to-send semantics distinct from newline. GWT inserts the composer inline at the chosen reply position with formatting + caret survival across live updates. |
| R-5.2 | View and edit toolbars | **PARTIAL** | The toolbar buttons (Bold, Italic, Underline, Strikethrough, Heading, Bulleted list, Numbered list, Align left/center/right, Right-to-left, Create link, Remove link, Clear formatting) are rendered in the right rail, but they are **detached** from the textarea — there is no rich-text input, no selection range, no toggle state. The buttons act as global affordances rather than contextual selection-driven toggles. The reply textarea is plain `<textarea>`, no contenteditable. So the formatting controls cannot apply formatting to the reply being composed in any user-visible way. |
| R-5.3 | Mentions and autocomplete | **FAIL** | No `@`-trigger behavior in the reply textarea. Typing `@` inserts a literal character. No suggestion popover. GWT has working `@<participant>` autocomplete with arrow-key + enter. |
| R-5.4 | Tasks and related metadata overlays | **FAIL** | No task affordance on any blip. No checkbox-style toggle. No task metadata overlays. GWT has visible task UI. |
| R-5.5 | Reactions and comparable interaction overlays | **FAIL** | No reaction affordance on any blip. No reaction chip styling. No add/remove reaction control. GWT has reactions. |
| R-5.6 | Attachment workflow | **PARTIAL** | The right-rail toolbar shows "Attach file / Upload queue / Cancel upload / Paste image / Small / Medium / Large attachment" buttons. They appear functional in isolation. The rendered open-wave does not show attachments inline against the originating blip — attachments existing on a wave (e.g. the `clop_2026-04-08_3163.png pasted-image.png` strings visible in the `@vega` digest snippet) are not visibly resolved as image previews in the J2CL open-wave panel. GWT renders attachments inline with thumbnail/download controls. |
| R-5.7 | Remaining rich-edit daily affordances (lists, block quotes, inline links, etc.) | **FAIL** | None of these are usable today because R-5.2 makes the formatting toolbar non-functional against the plain-textarea composer. |

**Compose verdict: 0/7 PASS, 3/7 PARTIAL, 4/7 FAIL.** This section is the largest user-facing gap. #969 ("Port StageThree compose and toolbar parity") and #970/#971 (overlays + attachment + rich-edit) are all marked closed but the toolbar buttons exist as labels disconnected from a real editor.

## 6. Server-First First Paint and Shell Swap — Section 6

| Row | Behavior | Status | Evidence |
| --- | --- | --- | --- |
| R-6.1 | Server-rendered read-only first paint | **FAIL** | First paint of the J2CL root shell is a "Hosted workflow" placeholder card with "Waiting for the first sidecar search response." The selected wave region says "Select a wave" until the user clicks. There is no server-rendered selected-wave HTML on initial load. The `J2clSelectedWaveSnapshotRenderer` exists in the servlet but the rendered snapshot is not visibly present in the right panel before client boot — i.e. the snapshot is either empty or rendered into a hidden seam that the live region replaces immediately. The R-6.1 "content is readable without JS for the visible region" is not satisfied. |
| R-6.2 | Bootstrap JSON contract | **PASS** | `/bootstrap.json` is live, versioned, and (post-#979) no longer leaks the volatile session ID seed. |
| R-6.3 | Shell-swap upgrade path | **PARTIAL** | The shell does upgrade in place from the static HTML to the live-updates-connected state. There is no unstyled flash. But because R-6.1 fails, the upgrade is from "Select a wave" placeholder to a flat blip list, not from server-rendered wave content to a richer client surface. |
| R-6.4 | Rollback-safe coexistence | **PASS** | `/?view=gwt` (or any non-empty non-`j2cl-root` view value) routes to the legacy GWT root. `/?view=j2cl-root` is the explicit J2CL route. The `j2cl-root-bootstrap` feature flag flips the *default* for empty `view`, which matches the matrix's "operator-level toggle remains reversible" requirement. |

**Server-first verdict: 2/4 PASS, 1/4 PARTIAL, 1/4 FAIL.** The bootstrap contract and route safety are real wins. The actual server-side first paint of wave content (#965's headline goal) is not visible to the user.

## 7. Viewport-Scoped Fragment Windows — Section 7

| Row | Behavior | Status | Evidence |
| --- | --- | --- | --- |
| R-7.1 | Initial visible window | **FAIL** | Test wave `ewlip` opens with the entire conversation rendered as a flat list at once. No initial-window clamp visible to the user. |
| R-7.2 | Extension on scroll | **FAIL** | Scrolling the right rail does not trigger a fragment extension fetch. There is nothing to extend — everything is already in the DOM. |
| R-7.3 | Server clamp behavior | **N/A** | Not exercised against a wave large enough to trip the existing server clamps. |
| R-7.4 | No regression to whole-wave bootstrap | **FAIL** | The current J2CL surface **is** the whole-wave bootstrap path. There is no viewport-scoped path to fall back from. |

**Fragments verdict: 0/4 PASS, 0/4 PARTIAL, 3/4 FAIL, 1/4 N/A.** #967 ("Drive the Lit read surface from viewport-scoped fragment windows") is marked closed but the user-visible behavior is whole-wave-only.

## 8. Aggregate score

| Section | PASS | PARTIAL | FAIL | N/A | Gate-row pass rate |
| --- | ---: | ---: | ---: | ---: | --- |
| §3 Read | 0 | 1 | 5 | 0 | 0/5 gate rows |
| §4 Live | 2 | 2 | 2 | 2 | 2/7 gate rows |
| §5 Compose | 0 | 3 | 4 | 0 | 0/6 gate rows |
| §6 Server-first | 2 | 1 | 1 | 0 | 2/4 gate rows |
| §7 Fragments | 0 | 0 | 3 | 1 | 0/4 gate rows |
| **Total** | **4** | **7** | **15** | **3** | **4/26 gate rows pass** |

The §8 parity gate in the matrix requires *all* gate rows to be closed before §5.1 (opt-in default-root bootstrap) may even be opened. By that contract, **22 of 26 gate rows are not yet met**, despite the source-issue chain being marked closed. The closed status reflects narrow per-issue acceptance ("practical read-surface parity, not full editor parity") rather than the matrix's "must not regress against GWT" contract.

## 9. Why the chain closed without delivering

Pattern observed across slices: every closed Section-4 issue used a phrase like *"practical read-surface parity, not full editor parity"* or *"closes the high-value gap that still blocks root cutover, stays bounded to overlays/interactions"*. These are bound-the-blast-radius escape hatches that let each issue declare success against a narrow per-slice acceptance instead of against the row-level matrix contract. The matrix says *"required to match GWT"*; the issue text says *"practical parity"*; reviewers accepted the latter; users see the gap.

This is not a moral failure of any one slice. It is a process gap between the matrix (which is the actual contract) and the per-issue acceptance (which is what reviewers signed off against).

## 10. Recommended follow-up — re-execute four slices, gate on matrix rows

Rather than open one giant "redo the editor" issue or restart the chain from scratch, propose four tightly-scoped re-execution issues that each cite the **specific failed matrix row IDs** as their hard acceptance criteria. No more "practical parity" language. The acceptance is row-level: every cited row must demonstrate the exact required-to-match-GWT behavior in a fixture that mounts both `/?view=j2cl-root` and `/?view=gwt` and asserts the contract.

Proposed sequencing (data-path-first → UI):

### F-1. Re-execute viewport-scoped read surface (replaces #967 / #965 acceptance)

- Cite rows: R-3.5, R-3.6, R-4.6, R-6.1, R-6.3, R-7.1, R-7.2, R-7.3, R-7.4.
- Acceptance: the J2CL open-wave surface opens with a viewport-scoped initial window (not whole-wave); scroll triggers fragment extension; server clamps are honoured; server-rendered first paint contains visible-region wave HTML before client boot; fixture asserts both paths.
- Why first: the data path is the hard part to retrofit later. Every UI feature inherits the loading model.

### F-2. Re-execute StageOne read surface (replaces #966 acceptance)

- Cite rows: R-3.1, R-3.2, R-3.3, R-3.4 (blip-level), R-4.4 (per-user read/unread live state).
- Acceptance: rendered conversation DOM with author + avatar + timestamp + threading per blip; focus frame moves with arrow/`j`/`k`; thread collapse toggles work; #931 unread state visibly decrements when blips are read; fixture asserts each behavior against both surfaces.
- Why second: requires F-1's data shape to be in place, but is the largest user-visible "looks like an actual conversation" win.

### F-3. Re-execute StageThree compose surface (replaces #969 / #970 / #971 acceptance for daily-path rows)

- Cite rows: R-5.1, R-5.2, R-5.3, R-5.4, R-5.5, R-5.6, R-5.7.
- Acceptance: composer is contenteditable / rich-text, not a plain textarea; toolbar buttons toggle on selection and apply on the active range; `@` trigger opens autocomplete; tasks toggle on a blip; reactions add/remove on a blip; attachments render inline; daily-path rich-edit affordances actually format. Fixture for each row.
- Why third: requires F-2's per-blip rendering to be in place so the composer can attach to a real blip surface.

### F-4. Re-instate live read/unread + per-blip live updates (cleans up #931 / R-4.7)

- Cite rows: R-4.4, R-4.7.
- Acceptance: opening a blip decrements the wave's unread count without page reload; the digest list reflects updates from other clients; features (search, supplement, diff, reader) become visibly active in the live surface.
- Why fourth: smaller in scope, but depends on F-2's blip-level rendering to attach state to.

After F-1..F-4 close against row-level acceptance, the existing parity gate row coverage should jump from 4/26 to ~22/26 and the matrix's §5.1/§5.2/§5.3 future issues become opener-eligible.

## 11. Out-of-scope for this audit

- Browser-harness fixture work — surfaced where relevant but the audit is matrix-row coverage, not fixture coverage. Matrix-row acceptance assumes a fixture lands with each follow-up.
- Lit design-system / Stitch packet (#962) — visual modernization is decoupled from row-level parity per matrix §1 "what may change visually."
- Default-root cutover (issue map §5.1/5.2/5.3) — explicitly gated by the parity gate; cannot be opened until F-1..F-4 close.
- #978 (`fromRootHtml` removal) — tail-end soak-gated cleanup, not on the parity-acquisition critical path. Independent of this audit's recommendations.

## 12. Verification of audit method

- J2CL surface URL `https://supawave.ai/?view=j2cl-root` — confirmed routes to the J2CL root shell (`SupaWave J2CL Root Shell` brand visible).
- GWT surface URL `https://supawave.ai/?view=gwt` — confirmed routes to the legacy GWT path (`SupaWave` brand, full toolbar, threaded wave content visible).
- Both surfaces signed in as the same `vega@supawave.ai` account from the same browser session, so account state is held constant.
- Same wave (`ewlip` at `supawave.ai/w+3VPWzePL_DA`) opened on both surfaces for direct side-by-side comparison.
- Routing verified against `WaveClientServlet#resolveRequestedView` and `WaveClientServlet#doGet` (commit `e66ce6013`) which establishes that `view=j2cl-root` OR (`view` empty AND `j2cl-root-bootstrap` flag enabled) selects the J2CL path; any other non-empty `view` value falls through to GWT.

## 13. Appendix — observed shell chrome contrast

| Surface element | GWT | J2CL |
| --- | --- | --- |
| Top brand | `SupaWave` | `SupaWave J2CL Root Shell` (developer-facing brand) |
| Top toolbar | Search, user menu, notifications, settings, chat icons | Admin button, Sign out — no app affordances |
| Left rail | Search box + filter/sort/refresh buttons + multi-folder navigation + rich digests with avatars + multi-author avatars + relative timestamps + unread badges | One folder (Inbox) + search box + plain-text digest cards (title, author email, snippet, message count) |
| Center | (used by GWT for combined list+wave) | "Hosted workflow" explainer card |
| Right rail | (n/a — GWT uses the center) | Open-wave region with flat blip list (raw blip IDs as headers), reply textarea, large detached toolbar, navigation buttons |
| Open-wave per-blip chrome | Avatar + author name + timestamp + per-blip action toolbar (reply/edit/delete/link), threaded indenting | Just `Blip <id>` as heading + raw text body |
| Bottom of open wave | "Click here to reply" affordance + Tags row | "Reply target: b+<id>" label + Send reply button |

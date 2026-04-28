# J2CL Functional UI Roadmap (2026-04-28)

Status: Active
Owner: J2CL parity sprint
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Audit basis: [`docs/superpowers/audits/2026-04-26-j2cl-gwt-parity-audit.md`](../audits/2026-04-26-j2cl-gwt-parity-audit.md)
Parity matrix: [`docs/j2cl-gwt-parity-matrix.md`](../../j2cl-gwt-parity-matrix.md)

## 1. Why this exists

The 2026-04-26 audit verified that despite F-0..F-4 closing in GitHub, the lived
J2CL surface at `/?view=j2cl-root` is not usable: 4/26 matrix gate rows pass,
the wave list does not render, blips appear as raw IDs, and the composer is
detached from a non-rich textarea. The user-visible state is a `Select a wave`
placeholder with no clickable digest cards.

This roadmap drives the J2CL UI from "shell skeleton" to "actually usable as a
SupaWave client." It is intentionally framed in user-action terms (list waves,
filter, open, edit, mark done) rather than architecture, because the prior
chain over-indexed on architecture and shipped placeholders.

## 2. Deliverables

| Phase | Deliverable | Lane |
| --- | --- | --- |
| 1 | Wavy mockups for the J2CL functional UI (SVG) | docs PR |
| 2 | Umbrella issue + 8 slice issues filed against #904 | issue-creation lane |
| 3 | One PR per slice issue, each with local-server verification | per-slice impl lane |

Mockups must use the SupaWave wordmark/logo, real terminology (Wave, Blip,
Inbox/Mentions/Tasks/Public/Archive/Pinned), and reflect the current J2CL Lit
shell components (`shell-root`, `wavy-search-rail`, `wavy-search-rail-card`,
`shell-main-region`). Surfaces to mock: shell layout, threaded reading view,
inline rich-text composer, mobile, dark mode.

## 3. Issue list

Each issue cites specific matrix rows as hard acceptance — no
"practical parity" escape hatches.

### J-UI-1. Wave list renders in the search rail
- **User story**: I open `/?view=j2cl-root&q=in:inbox`; the rail shows my waves
  as digest cards (avatar, title, snippet, count, timestamp). Clicking opens
  the wave.
- **Acceptance**: J2CL search digest results materialize as
  `<wavy-search-rail-card>` instances inside `<wavy-search-rail>`; selection
  routes URL state and opens the wave region.
- **Matrix rows**: R-3.5 (visible-region container — list level), R-4.5 (route
  integration).
- **Bug evidence**: screenshot on `?view=j2cl-root&q=in:inbox` — `Showing
  search results for in:inbox.` text rendered, zero cards visible.

### J-UI-2. Folder + filter chip switching is functional
- **User story**: I click `Mentions`, `Tasks`, `Public`, `Archive`, `Pinned`,
  or any filter chip (`Unread only`, `With attachments`, `From me`); the rail
  re-queries and updates.
- **Acceptance**: Each saved-search button issues the corresponding query and
  the rail repopulates; filter chips compose with the active folder; URL
  reflects the active query.
- **Matrix rows**: R-4.5.

### J-UI-3. New Wave create flow
- **User story**: I click `New Wave`, type a title and message, the wave is
  created on the server, lands at the top of Inbox, and opens in the wave
  panel.
- **Acceptance**: End-to-end create flow exercising the J2CL write path; new
  wave digest appears in the rail without page reload.
- **Matrix rows**: R-5.1 (compose flow).

### J-UI-4. Open-wave threaded reading view
- **User story**: I open a wave; blips render with author avatar, name,
  relative timestamp, and threaded indenting. I navigate with arrow keys / `j`
  / `k` and a focus frame moves. Threads collapse and expand.
- **Acceptance**: Conversation DOM (not raw blip IDs) for every blip;
  `FocusFramePresenter`-equivalent visible focus; collapse toggle wired.
- **Matrix rows**: R-3.1, R-3.2, R-3.3, R-3.6.

### J-UI-5. Inline rich-text composer + working toolbar
- **User story**: I click reply on a blip; an inline contenteditable composer
  opens at that position. Toolbar buttons (bold, italic, link, list, heading,
  alignment) toggle on selection and apply to the active range.
- **Acceptance**: Composer is contenteditable, not `<textarea>`; toolbar is
  selection-driven; replies submit to the correct parent blip.
- **Matrix rows**: R-5.1, R-5.2, R-5.7.

### J-UI-6. Per-blip task toggle + done state
- **User story**: I click the task affordance on a blip; the blip becomes a
  task. I click "done"; the task is marked complete and the visual state
  reflects it. Reload preserves the state.
- **Acceptance**: Task toggle is wired to the task DocOp; done state renders
  with strikethrough/checkmark and persists across reload.
- **Matrix rows**: R-5.4.

### J-UI-7. Mark-as-read + live unread decrement
- **User story**: I open a wave; its unread count drops to zero in the rail
  digest. Other clients reflect the change.
- **Acceptance**: Per-user read state mutates on open; rail digest re-renders
  count and badge styling; live updates from other clients apply.
- **Matrix rows**: R-4.4.

### J-UI-8. Server-first first-paint of selected wave
- **User story**: I refresh on `?wave=<id>`; the visible region is readable
  before client JS boots.
- **Acceptance**: `J2clSelectedWaveSnapshotRenderer` output present in the
  initial HTML response and visible (no `Select a wave` flash); shell upgrades
  in place.
- **Matrix rows**: R-6.1, R-6.3.

## 4. Sequencing

Phases 1 and 2 run in parallel. Phase 3 sequencing inside the impl lanes:

1. J-UI-1 (unblocks visible feedback for everything else)
2. J-UI-2 (small follow-up to J-UI-1)
3. J-UI-4 (read surface — required by J-UI-5 and J-UI-6)
4. J-UI-5 (composer)
5. J-UI-7 (live state — small)
6. J-UI-6 (tasks — depends on per-blip rendering)
7. J-UI-3 (write path) — can run in parallel with 4–6
8. J-UI-8 (server-first paint) — last, decorator on top of everything else

## 5. Out of scope

- Reactions, mentions autocomplete, attachments inline rendering — these are
  follow-ups already filed under F-3 follow-up issues (#1073–#1076). They are
  not blockers for "actually usable interface."
- Default-root cutover (#923/#924) — gated by the parity matrix §8 gate; this
  roadmap brings J-UI parity to the level needed to revisit that gate, but
  cutover itself remains a separate decision.
- Visual latitude beyond the Wavy mockup set is deferred to future iterations.

## 6. Workflow per slice

Each slice issue follows the standing full-drill workflow:
plan → review → implement → review → QA on local server → flag → changelog →
PR. Each PR receives a `wave-pr-monitor` pane that stays alive until the PR is
merged with all conversations resolved.

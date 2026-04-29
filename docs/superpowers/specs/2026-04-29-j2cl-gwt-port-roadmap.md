# J2CL ← GWT 1-to-1 Port Roadmap (G-PORT) — 2026-04-29

Status: Active
Owner: J2CL GWT-clone sprint
Parent: [#904](https://github.com/vega113/supawave/issues/904)
Prior sprints: [#1078 functional](https://github.com/vega113/supawave/issues/1078) (closed), [#1098 visual polish](https://github.com/vega113/supawave/issues/1098) (closed)

## 1. Why this exists

Functional + visual sprints both shipped passing CI but did not produce a usable
product. Live verification on `https://supawave.ai/?view=j2cl-root` shows:

- Reply textarea + Send button render but **replies do not actually send**
- **Inline replies do not work** — clicking reply on a blip does nothing
  meaningful
- Format toolbar **still renders as 22 stacked text-label buttons** (V-3
  shipped icon code that never reached the rendered surface)
- **Toolbar buttons do not apply formatting** even when the toolbar is
  visible

Root cause is the same one the 2026-04-26 audit named: PRs gated on unit-test
correctness rather than on real-browser end-to-end behavior. Reviewers
accepted "matrix row green" and "code path covered by test" instead of "user
can actually use the feature."

## 2. New direction

Stop iterating on a Wavy redesign. **Replicate the GWT UI in J2CL/Lit
1-to-1.** Goal is identical look, identical behavior, with E2E tests that
exercise real user flows as the only acceptance gate.

Reuse:
- GWT icon assets (PNG/SVG sprites under `wave/src/main/...`)
- GWT CSS classes / tokens where applicable
- GWT layout structure
- GWT Java code where directly translatable to Lit + J2CL-friendly Java

## 3. Hard acceptance contract

Every G-PORT slice PR must include:

1. **E2E test** (Playwright or equivalent) covering the slice's user flow,
   running against `?view=j2cl-root` AND `?view=gwt`, asserting equivalent
   user-visible behavior on both.
2. **Test passes in CI** before merge (no skipping, no flaky retries).
3. **Visual diff check** on the slice's primary view: J2CL screenshot vs GWT
   screenshot, ≤5% pixel delta on the key region.
4. **Manual verification log** in PR body: "logged in as test user → did
   X → observed Y → matches GWT behavior."

A reviewer can reject a PR if the E2E test doesn't actually exercise the
user's flow as described. "Test passes" is necessary but not sufficient.

## 4. Slices

### G-PORT-1. E2E foundation
- Playwright harness under `wave/src/e2e/j2cl-gwt-parity/` that runs against
  a local server at both `?view=j2cl-root` and `?view=gwt`.
- Single shared fixture: registers/signs in a fresh test user for each run.
- Helpers: shared `j2cl()` / `gwt()` page objects focused on bootstrap/load
  assertions in this slice; richer interaction helpers land in later slices.
- Wires CI: a new check `J2CL ↔ GWT Parity E2E` that runs the suite.
- No UI changes in this slice — only the test harness.

### G-PORT-2. Search panel parity
- Clone GWT search rail layout: digest cards with avatars (multi-author
  stack), title, snippet, msg count, unread badge styling, relative
  timestamp, action-icon row (refresh / sort / filter buttons).
- Source: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/.../SearchPanelView*`
- Target: `j2cl/lit/src/elements/wavy-search-rail*.js` (replace), reuse
  GWT icons.
- E2E: open `?view=j2cl-root` → assert digest card layout matches GWT screenshot.

### G-PORT-3. Wave panel + threaded reading parity
- Clone GWT wave conversation rendering: per-blip author chrome (avatar +
  name + timestamp), threaded indenting, focus frame with j/k navigation,
  collapse chevrons, per-blip action toolbar on hover.
- Source: `wave/src/main/.../wavepanel/view/dom/.../*BlipView*`,
  `FocusFramePresenter`, `ConversationDom`.
- Target: `j2cl/lit/src/elements/wavy-blip-card.js` (rewrite) + read
  surface DOM renderer.
- E2E: open a wave → assert each blip has author chrome → press j → focus
  frame moves → click collapse → thread folds.

### G-PORT-4. Inline reply + working compose toolbar
- **The user's #1 complaint.** Clone GWT inline-reply widget — clicking
  Reply on a blip opens an inline contenteditable composer at that
  position. Toolbar uses GWT icons (not text labels), buttons toggle on
  selection and apply on the active range. Send actually delivers.
- Source: `wave/src/main/.../editor/*`,
  `wave/src/main/.../widget/toolbar/*`, the GWT format toolbar.
- Target: `composer-inline-reply.js`, `wavy-format-toolbar.js`,
  `wavy-composer.js` (rewrite as needed). Use the GWT icon SVGs.
- E2E: click reply on a blip → composer opens → type text → click bold →
  bold applies → click send → reply appears in the wave.

### G-PORT-5. Mention autocomplete
- Clone GWT `@`-mention autocomplete: typing `@` opens suggestion list,
  arrow keys navigate, Enter selects, mention chip rendered with link to
  the mentioned user.
- Source: `wave/src/main/.../doodad/mention/*`, `widget/popup/*`.
- Target: `mention-suggestion-popover.js` (rewrite if needed) + composer
  trigger.
- E2E: type `@v` in composer → suggestion popover opens → arrow → Enter →
  mention chip rendered → submit → mention persists.

### G-PORT-6. Tasks + done state
- Clone GWT task widget: per-blip task toggle, done strikethrough, task
  metadata overlay.
- Source: `wave/src/main/.../doodad/task/*`.
- Target: `task-metadata-popover.js` + per-blip task affordance.
- E2E: click task icon on a blip → blip becomes a task → click done →
  visible done state → reload preserves.

### G-PORT-7. Keyboard shortcuts
- Clone GWT keyboard handler: j/k blip navigation, Shift+Cmd+O new wave,
  Esc closes dialogs, Enter in search refreshes, arrow keys in mention
  popover navigate.
- Source: `wave/src/main/.../events/*`, GWT keyboard registry.
- Target: shell-level key handler in `shell-root.js`.
- E2E: each shortcut triggers the documented behavior.

### G-PORT-8. Top-of-wave actions (version history / archive / pin / unpin / refresh)
- Clone GWT action-icon strip at the top of the wave panel.
- Source: `wave/src/main/.../wavepanel/.../FrameView*` + action handlers.
- Target: `shell-status-strip.js` or new `wave-action-bar.js`.
- E2E: each icon triggers correct action (pin → wave moves to pinned;
  archive → wave leaves inbox; etc.).

### G-PORT-9. Visual style parity polish
- Final pass: spacing, fonts, colors, hover/active states, borders match
  GWT pixel-for-pixel on the key views (search panel, open wave,
  composer).
- Source: GWT CSS at `wave/src/main/.../*.css`.
- Target: `j2cl/lit/src/design/wavy-tokens.css` and per-element styles.
- E2E: `playwright/test-screenshot-diff.spec.ts` — assert ≤5% pixel
  delta vs GWT on each key view.

## 5. Sequencing

```text
G-PORT-1 (E2E foundation) →
  G-PORT-2 (search) ┐
  G-PORT-3 (wave+read) ┐
  G-PORT-4 (inline reply + toolbar) ┐
  G-PORT-7 (shortcuts) ┘
                       → G-PORT-5 (mentions) → G-PORT-6 (tasks)
                       → G-PORT-8 (top actions)
                       → G-PORT-9 (visual polish, last)
```

Slice 1 goes first (every other slice depends on the E2E harness for its
acceptance gate), followed by 2/3/4/7 in parallel, then 5/6/8, and finally 9.

## 6. Workflow per slice

- **Subagents, not tmux lanes.** Per user directive 2026-04-29.
- Each slice is one subagent invocation that handles plan → impl → E2E test
  → CI green → PR open → monitor through merge.
- Background mode (`run_in_background: true`) so multiple slices can run
  without blocking the orchestrator.
- One PR per slice. Each PR has its own E2E test as the gate.

## 7. Out of scope

- Visual redesign / Wavy aesthetic (abandoned).
- New features beyond what GWT does today.
- GWT retirement (separate track, gated on G-PORT completion).
- Mobile-specific layout (assume desktop parity first).

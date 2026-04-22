# J2CL Parity Slice Packet Template

Status: Proposed  
Owner: Project Maintainers  
Updated: 2026-04-22  
Review cadence: on-change  

Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Task issue: [#961](https://github.com/vega113/supawave/issues/961)
Related: [`docs/j2cl-gwt-parity-matrix.md`](./j2cl-gwt-parity-matrix.md),
[`docs/j2cl-parity-issue-map.md`](./j2cl-parity-issue-map.md),
[`docs/j2cl-parity-architecture.md`](./j2cl-parity-architecture.md)

## 1. Purpose

This template is the structured packet every downstream J2CL/Lit parity slice
(`#963`–`#971` in the issue map) must fill in before implementation begins.
It is the contract a slice makes with the parity matrix: it names the rows
claimed, the rollout seam, the verification plan, and the traceability links
back to the acceptance source of truth.

How to use it:

1. Copy the template in Section 3 into the slice's GitHub issue body or its
   `docs/superpowers/plans/<date>-issue-<num>-<slug>.md` plan.
2. Fill every field. "n/a" is acceptable only with a short reason.
3. Link back to `#961`, the parity matrix, and the issue map from the packet.
4. Keep the packet and the matrix in lockstep: if the slice discovers behavior
   that the matrix does not cover, update the matrix in the same PR or in a
   predecessor doc-only PR.

A completed example is shown in Section 4 for slice `#966`. The example is a
sample packet only; it does not authorize or schedule `#966` implementation.

## 2. Minimum Contents

Every packet must contain:

- slice identity
- parity matrix rows claimed (anchors into
  [`j2cl-gwt-parity-matrix.md`](./j2cl-gwt-parity-matrix.md))
- GWT seams the slice de-risks (file + approximate line range)
- rollout flag / rollout seam
- server/client surface list
- required-match behaviors (pulled from matrix rows)
- allowed-change visuals (pulled from matrix rows)
- keyboard / focus plan
- accessibility plan
- i18n plan
- browser-harness coverage plan
- telemetry and observability checkpoints
- verification plan (smoke/browser/harness; exact commands expected in the
  linked issue and in `journal/local-verification/` if applicable)
- rollback plan
- traceability (back-links to `#961`, the matrix, the issue map, and the
  parity architecture memo)

## 3. Template

Copy the block below into the slice issue or plan:

```markdown
## Slice Parity Packet — Issue #<NN>

**Title:** <slice title from issue map>
**Stage:** read | live | compose | server-first | fragments
**Dependencies (from issue map §6):** <e.g. #963, #966>

### Parity matrix rows claimed
- R-<section>.<n> — <one-line paraphrase>
- R-<section>.<n> — <one-line paraphrase>

### GWT seams de-risked
- `<repo-relative-path>:<start>-<end>` — <what it owns>
- `<repo-relative-path>:<start>-<end>` — <what it owns>

### Rollout flag / rollout seam
- Flag: `<ClientFlags / config key / route flag>`
- Default: `<off | on>` during implementation
- Reversibility: <how an operator disables without a code rollback>

### Server / client surface list
- Server: `<servlet, renderer, RPC, or config key>`
- Client: `<J2CL entry point, Lit element(s), controller>`

### Required-match behaviors (from matrix)
- <row ID>: <behavior; cite GWT seam if it adds clarity>

### Allowed-change visuals (from matrix)
- <row ID>: <what may change>

### Keyboard / focus plan
- <keyboard/caret/focus expectations this slice preserves>

### Accessibility plan
- <AT-visible roles/labels/announcements this slice preserves or adds>

### i18n plan
- <locale/RTL/translation expectations>

### Browser-harness coverage
- <existing or new harness fixtures the slice adds/updates>

### Telemetry and observability checkpoints
- <signals required at parity gate; metric/counter/log names>

### Verification plan
- Smoke:
  - `bash scripts/worktree-boot.sh --port <port>`
  - `PORT=<port> JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
  - `PORT=<port> bash scripts/wave-smoke.sh check`
  - `PORT=<port> bash scripts/wave-smoke.sh stop`
- Browser:
  - Required by `docs/runbooks/change-type-verification-matrix.md`? <yes/no + row>
  - Route(s) and narrow user-visible path(s)
- Harness:
  - Fixture(s) and expected outcomes

### Rollback plan
- Flag flip / route flip / feature disable
- Operator-visible indicator

### Traceability
- Parity matrix: `docs/j2cl-gwt-parity-matrix.md`
- Packet origin: `#961`
- Issue map: `docs/j2cl-parity-issue-map.md`
- Architecture memo: `docs/j2cl-parity-architecture.md`
- Linked issue(s): `#<NN>`, `#904`
- Linked plan: `docs/superpowers/plans/<date>-issue-<NN>-<slug>.md`
```

## 4. Worked Example — Slice Packet For `#966`

The following is an illustrative packet for slice `#966`
(StageOne read-surface parity). It shows the expected level of detail and
the exact cross-links. It is not a schedule commitment for `#966` and does
not itself authorize implementation.

All concrete names below — flag key, Java package path, Lit element names —
are illustrative placeholders. The real `#966` packet will pick the real
names when that slice is actually planned.

```markdown
## Slice Parity Packet — Issue #966 (ILLUSTRATIVE EXAMPLE)

**Title:** Port StageOne read-surface parity to Lit for open-wave rendering,
focus, collapse, and thread navigation
**Stage:** read
**Dependencies (from issue map §6):** #961, #962, #963, #964

### Parity matrix rows claimed
- R-3.1 — Open-wave rendering
- R-3.2 — Focus framing
- R-3.3 — Collapse
- R-3.4 — Thread navigation (coordinates with #931 for unread state)
- R-3.6 — DOM-as-view provider (infrastructural enabler)

### GWT seams de-risked
- `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java:44-197`
  — read surface lifecycle, focus/collapse/thread-nav installation, view
  provider
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/WavePanelImpl.java`
  — panel assembly invoked by StageOne
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/FullStructure.java`
  — DOM semantic view provider

### Rollout flag / rollout seam
- Flag: `ui.lit_read_surface_enabled` (client flag, gated behind
  `ui.j2cl_root_bootstrap_enabled`)
- Default: `off` during implementation
- Reversibility: operator disables the flag; `/` continues to serve the
  legacy GWT root; `/?view=j2cl-root` continues to work unchanged

### Server / client surface list
- Server: existing `WavePreRenderer` output plus bootstrap JSON from #963
- Client: J2CL controllers under `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/`
  plus Lit custom elements for wave panel, focus frame, collapse toggle,
  thread-nav control

### Required-match behaviors (from matrix)
- R-3.1: blip/thread/inline-reply identity stable across incremental updates
- R-3.2: focus frame survives incremental render and collapse toggles
- R-3.3: collapse preserves scroll anchor, read state, and child visibility
- R-3.4: unread-aware navigation order preserved when unread state exists
- R-3.6: semantic view queries continue to resolve against the new DOM

### Allowed-change visuals (from matrix)
- Shell chrome, spacing, typography, icon set; focus outline visuals;
  toggle animation; loading placeholders (consumed from the #962 design
  packet)

### Keyboard / focus plan
- Preserve existing arrow/tab/shift+tab semantics
- Preserve space/enter toggle semantics on collapse controls
- Focused blip is the active AT region; no focus loss on incremental render

### Accessibility plan
- Conversation tree exposes landmark/list roles
- Collapse toggle announces expanded/collapsed state
- Focus outline meets contrast on light and dark themes

### i18n plan
- Preserve locale fallback and RTL mirroring for blip content
- New control labels translate via the existing message bundle

### Browser-harness coverage
- Add or migrate fixtures descended from the existing StageOne-era tests in
  the GWTTestCase verification matrix, covering at least: open+render,
  keyboard navigation, collapse+expand, next-unread navigation

### Telemetry and observability checkpoints
- First-render timing and blip count (client stats channel)
- Focus-change event counts
- Collapse toggle counts
- Thread-nav event counts with target blip id
- Provider-resolution failure log signal

### Verification plan
- Smoke:
  - `bash scripts/worktree-boot.sh --port 9900`
  - `PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
  - `PORT=9900 bash scripts/wave-smoke.sh check`
  - `PORT=9900 bash scripts/wave-smoke.sh stop`
- Browser:
  - Required by `docs/runbooks/change-type-verification-matrix.md` (`GWT client/UI` row)
  - Open `http://localhost:9900/?view=j2cl-root` with the flag enabled and
    exercise open-wave, focus navigation, collapse, and next-unread
- Harness:
  - Run the read-surface fixture set; confirm parity with existing expected
    outcomes

### Rollback plan
- Disable `ui.lit_read_surface_enabled`; J2CL root falls back to the
  previous read path; `/` continues to serve the legacy GWT root

### Traceability
- Parity matrix: `docs/j2cl-gwt-parity-matrix.md`
- Packet origin: `#961`
- Issue map: `docs/j2cl-parity-issue-map.md`
- Architecture memo: `docs/j2cl-parity-architecture.md`
- Linked issue(s): `#966`, `#904`
- Linked plan: `docs/superpowers/plans/<date>-issue-966-j2cl-read-surface.md`
```

## 5. Change Policy

- Edits to this template are made in reviewed PRs under Claude Opus 4.7 review,
  consistent with `AGENTS.md`.
- When the parity matrix gains new mandatory fields, this template must be
  updated in the same PR.
- Filled-in packets live in their slice's issue or plan, not in this file.

# Issue #961 GWT Parity Matrix And Slice Packet Template Plan

> **For agentic workers:** Treat this as a docs-only slice. No runtime or
> product code changes. Steps use `- [ ]` syntax for tracking.

**Goal:** Produce two committed Markdown artifacts that become the acceptance
source of truth for every downstream J2CL/Lit parity slice under `#904`:

1. `docs/j2cl-gwt-parity-matrix.md` — the frozen GWT behavior inventory that
   defines, per target flow/surface, what must match, what may change, and how
   parity is verified.
2. `docs/j2cl-parity-slice-packet-template.md` — the per-slice packet template
   that downstream parity issues (`#963`–`#971`) must fill in before
   implementation starts.

**Non-goals:**
- no Lit/J2CL parity implementation
- no change to the default root route
- no change to framework/runtime recommendations in the parity architecture memo
- no new GitHub issues (the chain `#961`..`#971` is already open)

**Tech stack / inputs:**
- `docs/j2cl-parity-architecture.md` (merged decision memo)
- `docs/j2cl-parity-issue-map.md` (reviewed issue chain, merged via PR #972)
- `docs/j2cl-lit-implementation-workflow.md` (design workflow)
- `docs/runbooks/browser-verification.md` and
  `docs/runbooks/change-type-verification-matrix.md` (verification baseline)
- Current GWT stage seams:
  - `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- Current J2CL surface:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/**`
- Doc guardrails: `scripts/check-doc-links.sh`, `scripts/check-doc-freshness.sh`

---

## 1. Why This Exists

The issue map (`#960`/PR #972) froze the execution chain but did not define what
"parity" means per surface. Without a committed inventory and a per-slice
packet template, `#963`–`#971` would each re-derive acceptance criteria ad hoc,
silently drifting from the current GWT behavior the repo still depends on.

This task produces the acceptance contract those downstream slices will cite.

## 2. Scope

### 2.1 In Scope

- inventory the GWT behavior that parity slices must match, grouped by the
  existing StageOne/StageTwo/StageThree responsibilities plus the server-first
  and fragment-window seams the issue map already calls out
- record, per row: required behaviors, visual latitude, keyboard/focus
  expectations, accessibility expectations, i18n expectations, browser-harness
  coverage expectations, telemetry/observability checkpoints, and the
  verification shape (smoke, browser, harness)
- define the per-slice packet template every downstream parity issue must fill
  in (title, rollout flag seam, parity matrix rows it claims to close,
  browser-verification plan, telemetry checkpoints, rollback notes,
  traceability back to `#961` and the issue map)
- register both new docs in `docs/README.md` and `docs/DOC_REGISTRY.md`
- add a changelog fragment under `wave/config/changelog.d/`

### 2.2 Explicit Non-Goals

- no implementation of any parity slice
- no modification of `docs/j2cl-parity-architecture.md` or
  `docs/j2cl-parity-issue-map.md` beyond cross-linking if required
- no attempt to re-open or widen the issue-map scope
- no new GitHub issues

## 3. Structure Of The Parity Matrix

`docs/j2cl-gwt-parity-matrix.md` should be organized as:

1. Metadata header block (Status, Owner, Updated, Review cadence) consistent
   with `DOC_REGISTRY.md` rules.
2. Purpose + how-to-use section explaining that this doc is the acceptance
   contract for `#963`–`#971` and must be cited by every slice packet.
3. Stage-aligned sections, mirroring the issue-map stage mapping:
   - **Read surface (StageOne-origin)** — open-wave rendering, focus framing,
     collapse, thread navigation, visible-region read container model
   - **Live surface (StageTwo-origin)** — socket/session lifecycle, reconnect,
     read-state refresh, route/history, fragment fetch policy, feature
     activation
   - **Compose / edit surface (StageThree-origin)** — compose/reply flow,
     view/edit toolbar, mention/task/reaction/interaction overlays, attachment
     and remaining rich-edit daily workflows
   - **Server-first + shell-swap** — prerendered read HTML, bootstrap JSON
     contract, shell upgrade path
   - **Viewport-scoped fragments** — initial visible window, extension under
     scroll, server clamp behavior
4. One table per section with columns:
   `Target behavior` | `Required to match GWT` | `Allowed to change visually` |
   `Keyboard / focus` | `Accessibility` | `i18n` | `Browser harness` |
   `Telemetry / observability` | `Verification shape` |
   `Downstream slice(s)`.
5. Parity gate subsection enumerating the rows that must all be "closed"
   before opt-in cutover (`#5.1`) can even be opened. This section is
   descriptive only: it captures the parity threshold, it does not grant
   authority to open `#5.1`/`#5.2`/`#5.3`. Those issues remain gated by the
   issue map.
6. Addendum section for intentionally out-of-scope legacy edge cases (explicit
   deferral list so they are not silently dropped).

Row sourcing rules:
- every row must cite at least one concrete GWT seam (file + approximate line
  range) or a merged parity architecture reference; no speculative rows
- rows must be scoped to observable behavior, not implementation detail

## 4. Structure Of The Slice Packet Template

`docs/j2cl-parity-slice-packet-template.md` should contain:

1. Metadata header block.
2. Short purpose statement and usage contract (every parity slice in
   `#963`–`#971` fills this in; packet lives in the issue body or an
   issue-scoped plan, not in the matrix itself).
3. Template body with placeholder fields:
   - Slice identity: issue number, title, stage, dependencies
   - Parity matrix rows claimed (direct anchors into the matrix doc)
   - GWT seam(s) this slice de-risks (file + approximate line range, mirroring
     the matrix row-sourcing rule so packet and matrix stay in lockstep)
   - Rollout flag / rollout seam
   - Server/client surface list
   - Required-match behaviors (pulled from matrix)
   - Allowed-change visuals
   - Keyboard / focus plan
   - Accessibility plan
   - i18n plan
   - Browser-harness coverage
   - Telemetry and observability checkpoints
   - Verification plan (smoke/browser/harness, exact commands expected in the
     linked issue)
   - Rollback plan
   - Traceability (back-links to `#961`, the issue-map doc, and the parity
     architecture memo)
4. Worked example filled in against one concrete slice already on the chain
   (pick `#966` StageOne read-surface parity because it is the largest
   user-visible slice and already well-described in the issue map) to prove
   the template is usable.

## 5. Task Breakdown

### Task 1: Baseline review (read-only)
- [ ] Re-read `docs/j2cl-parity-issue-map.md` Section 4 + Section 6 to keep
  downstream issue numbers and ordering accurate.
- [ ] Re-read `docs/j2cl-parity-architecture.md` §2–§7 to anchor the stage
  mapping.
- [ ] Skim StageOne/StageTwo/StageThree seams to pick accurate line ranges for
  matrix row citations; do not expand scope beyond citations.

### Task 2: Write the parity matrix doc
- [ ] Create `docs/j2cl-gwt-parity-matrix.md` with metadata header, purpose,
  stage sections, tables, parity gate section, and deferred-edge-case
  addendum as defined in Section 3.
- [ ] Keep tables narrow enough to render readably; split long cells into
  sub-bullets under the row where useful.

### Task 3: Write the slice packet template
- [ ] Create `docs/j2cl-parity-slice-packet-template.md` with metadata header,
  template body, and the worked example against `#966` as defined in
  Section 4.

### Task 4: Register the docs and add changelog
- [ ] Add both new paths to `docs/DOC_REGISTRY.md` under `## Covered docs`.
- [ ] Add both new paths to the References block of `docs/README.md`,
  adjacent to `docs/j2cl-parity-issue-map.md`.
- [ ] Add a changelog fragment
  `wave/config/changelog.d/2026-04-22-j2cl-parity-matrix.json` following
  the format of `2026-04-22-j2cl-parity-issue-map.json`, describing the
  matrix/template addition.

### Task 5: Verification
- [ ] `git diff --check`
- [ ] `bash scripts/check-doc-freshness.sh`
- [ ] `bash scripts/check-doc-links.sh`
- [ ] `python scripts/validate-changelog.py` (validates fragments under
  `wave/config/changelog.d/`; default args are correct, confirmed from
  `scripts/validate-changelog.py --help`)

### Task 6: Review
- [ ] Self-review for overlap with the parity architecture memo and issue map.
- [ ] Run Claude Opus 4.7 review on the plan before implementation.
- [ ] Run Claude Opus 4.7 review on the implementation diff; resolve valid
  comments.

### Task 7: Traceability + PR
- [ ] Commit the two new docs, `DOC_REGISTRY.md`/`README.md` updates, and the
  changelog fragment together.
- [ ] Push the branch and open a PR against `main` that cites this plan, both
  new doc paths, the verification commands/results, and both review outcomes.
- [ ] Update issue `#961` with worktree path, branch, plan path, commit SHA(s),
  verification output, review outcomes, and PR URL.
- [ ] Monitor the PR to merge (or document concrete blocker).

## 6. Verification Summary

Expected command + outcome record (to be mirrored into the issue):

```bash
git diff --check
bash scripts/check-doc-freshness.sh
bash scripts/check-doc-links.sh
python scripts/validate-changelog.py
```

The changelog validator uses default paths (`wave/config/changelog.d/` for
fragments, `wave/config/changelog.json` for the assembled output).


Expected: all four exit 0; no runtime/product changes so no server smoke
required (Change-Type Verification Matrix row: docs-only → curl/smoke not
required).

## 7. Definition Of Done

- `docs/j2cl-gwt-parity-matrix.md` exists, covers every stage listed in the
  issue map, and is referenced from every Section-4 downstream slice's packet
  template.
- `docs/j2cl-parity-slice-packet-template.md` exists and includes the worked
  example for `#966`.
- Both docs are registered in `docs/DOC_REGISTRY.md` and appear under
  References in `docs/README.md`.
- Changelog fragment is committed and validates.
- Doc guardrail scripts pass.
- Claude Opus 4.7 reviews (plan + implementation) are clean.
- Issue `#961` carries worktree, branch, plan path, commits, verification
  evidence, review outcomes, and PR URL.
- The PR merges (or the blocker is recorded concretely in the issue).

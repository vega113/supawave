# Issue #962 Lit Design System And Stitch-Backed Component Packet Plan

> **For agentic workers:** Treat this as a docs-only slice. No runtime, build,
> or product code changes. No CSS, no Lit sources, no sidecar edits. Steps use
> `- [ ]` syntax for tracking.

**Goal:** Produce the first parity-safe Lit design system and Stitch-backed
component packet that downstream J2CL/Lit parity slices (`#964`, `#965`,
`#966`, `#967`, `#968`, `#969`, `#970`, `#971`) can consume without
re-deriving tokens, component families, or Stitch usage rules. `#965`
consumes the server-first first-paint primitives family (§5.6).

Concretely, this task produces:

1. `docs/j2cl-lit-design-packet.md` — the committed design packet that
   freezes the parity-safe design tokens, component families, and Stitch
   artifact policy every downstream Lit slice will cite.
2. Registration of the new doc in `docs/DOC_REGISTRY.md` and
   `docs/README.md`, plus a changelog fragment under
   `wave/config/changelog.d/`.

**Non-goals:**
- no Lit component source, no CSS/tokens file, no build plumbing
- no real Stitch project creation in this PR (Stitch artifact *policy* only);
  the first Stitch project is authored by downstream consumer slices against
  the rules this packet freezes
- no new GitHub issues
- no changes to transport, bootstrap, auth, socket, or route semantics
- no changes to the default root route or coexistence contract
- no modification of the parity architecture memo, issue map, parity matrix,
  or slice packet template beyond cross-linking

**Tech stack / inputs:**
- `docs/j2cl-parity-architecture.md` (framework direction)
- `docs/j2cl-lit-implementation-workflow.md` (Stitch vs image-gen vs
  code/spec roles)
- `docs/j2cl-parity-issue-map.md` (issue chain ordering and §7 Stitch scope)
- `docs/j2cl-gwt-parity-matrix.md` (row IDs the packet references)
- `docs/j2cl-parity-slice-packet-template.md` (the per-slice packet the
  design packet must dovetail with)
- `docs/runbooks/browser-verification.md` and
  `docs/runbooks/change-type-verification-matrix.md` (docs-only verification
  shape)
- Doc guardrails: `scripts/check-doc-links.sh`, `scripts/check-doc-freshness.sh`
- Changelog guardrail: `scripts/validate-changelog.py`

---

## 1. Why This Exists

The workflow memo (`docs/j2cl-lit-implementation-workflow.md`) already put
Stitch in the right role conceptually: structured design accelerator,
secondary to slice specs. The issue map (`#960`/PR #972) already listed §7
"Where Stitch Fits" at a topic level. The parity matrix (`#961`/PR #973) froze
per-surface acceptance rules.

What is still missing is the **actionable design contract** every downstream
Lit parity slice will consume:

- which tokens exist (typography, color, spacing, shape, elevation, motion,
  density, iconography)
- which component families exist and what variants are in-scope
- where Stitch artifacts are *required* before a slice may start
  implementation, where they are *optional*, and where Stitch has *no role*
- which parity matrix rows each design artifact supports

Without this packet, `#964`–`#971` would each redesign tokens/components
independently, inviting silent drift, inconsistent modernization, and
accidental behavior loss.

The acceptance focus for `#962` in the issue map is explicit:

- approved design packet
- explicit statement of where Stitch artifacts are required vs optional
- no behavior changes
- not on the transport/bootstrap critical path

This plan commits to those bounds literally.

## 2. Scope

### 2.1 In Scope

- one new Markdown artifact, `docs/j2cl-lit-design-packet.md`, covering:
  - metadata header (Status, Owner, Updated, Review cadence, parent tracker,
    task issue, related docs) consistent with `DOC_REGISTRY.md` rules
  - purpose and usage contract (what the packet is, what it is not, who cites
    it, when it may change)
  - visual direction statement (what "Wavy-modern" means at the packet level,
    without prescribing a specific moodboard image)
  - design-token inventory (typography, color, spacing, shape, elevation,
    motion, density, iconography, focus-ring, z-index), each token listed as a
    *semantic* slot plus one line of intent — not pixel values, which are
    authored by Stitch or by the component slice
  - component-family inventory, grouped to match the parity matrix stages:
    - shell / chrome primitives (shared header, nav rail, main region,
      signed-in/signed-out shell, footer/status strip, skip-links)
    - read-surface primitives (wave panel container, blip card, thread
      container, inline reply affordance, focus frame, collapse toggle,
      thread-nav control, visible-region placeholder, read-only skeleton)
    - live-surface indicators (reconnect banner, live-update chip,
      unread/read badges — visual only; state ownership stays in J2CL)
    - compose / toolbar primitives (composer shell, inline reply composer,
      toolbar group, toolbar button, overflow menu — inventory only, no
      behavior)
    - interaction-overlay primitives (menu, popover, toast, tooltip,
      suggestion listbox)
    - server-first first-paint primitives (shell skeleton used by
      server-rendered HTML, upgrade placeholder)
  - per-family variant list with the parity matrix rows the family supports
    (`R-3.*`, `R-4.*`, `R-5.*`, `R-6.*`, `R-7.*`), so downstream slice packets
    can cite both the matrix row and the design-packet family anchor
  - **Stitch Artifact Policy** section, structured as a required/optional/
    prohibited table per family with the downstream slice that consumes it:
    - **Required**: slice may not start implementation without a committed
      Stitch artifact (project id + screen id(s) + variant ids, pinned in the
      slice packet)
    - **Optional**: Stitch is useful but not gating; a written component spec
      in the slice packet is sufficient
    - **Prohibited**: Stitch has no role (bootstrap JSON, socket/auth,
      unread modeling, version/hash, fragment transport/clamp logic, route
      state, feature flags) — restated so slices do not try to design these
      in Stitch
  - image-generation policy (restate the workflow memo: moodboards and
    visual-direction only, never screen spec)
  - accessibility, keyboard/focus, i18n, RTL, and motion/reduced-motion
    rules at the packet level, so downstream component slices inherit them
    rather than re-deriving
  - change policy (how new tokens/components/variants are added, how
    Stitch-required rows become optional, anchor stability for downstream
    citations)
- registration:
  - add the new path to `docs/DOC_REGISTRY.md` under the j2cl parity cluster
  - add the new path to `docs/README.md` under References adjacent to
    `j2cl-parity-slice-packet-template.md`
- changelog fragment
  `wave/config/changelog.d/2026-04-22-j2cl-lit-design-packet.json`
  following the format of `2026-04-22-j2cl-parity-matrix.json`

### 2.2 Explicit Non-Goals

- no Lit component source, no CSS, no token JSON/YAML, no build tooling
- no real Stitch projects or screens created in this PR (the packet defines
  the *rules*; the first Stitch projects are owned, at minimum, by
  downstream shell primitives slice `#964` and read-surface slice `#966`;
  issue map §7 also permits Stitch use in `#969` compose/toolbar, so the
  packet must not read as excluding later Stitch consumers)
- no new GitHub issues; downstream consumers are already in the issue map
- no edits to `docs/j2cl-parity-architecture.md`,
  `docs/j2cl-lit-implementation-workflow.md`,
  `docs/j2cl-parity-issue-map.md`, `docs/j2cl-gwt-parity-matrix.md`, or
  `docs/j2cl-parity-slice-packet-template.md`. Back-link from the new doc
  instead — any edit to those frozen docs bumps their `Updated:` metadata
  and re-opens review surface on already-merged content.
- no auth/socket/bootstrap work; `#933` and `#963` still own those seams
- no changes to the legacy GWT client or the default root route

### 2.3 Out-Of-Band Safeguards

- tokens are expressed as *semantic slot names with intent*, not pixel values,
  so a downstream slice that picks concrete values in Stitch cannot silently
  invalidate the packet
- every variant cites the parity matrix row(s) it supports, so reviewers can
  verify the packet does not quietly introduce behavior not already in the
  matrix
- every "Required Stitch" row cites the consuming slice; a missing consumer
  demotes the row to Optional

## 3. Structure Of The Design Packet

`docs/j2cl-lit-design-packet.md` sections:

1. Metadata header block (Status/Owner/Updated/Review cadence/parent
   tracker/task issue/related).
2. Purpose and usage contract.
3. Visual direction statement (Wavy-modern, progressive-enhancement-friendly,
   read-first).
4. Design tokens:
   4.1 Typography
   4.2 Color (semantic roles + light/dark support, contrast expectations)
   4.3 Spacing
   4.4 Shape / radius
   4.5 Elevation / surfaces / shadow
   4.6 Motion / easing / reduced-motion
   4.7 Density scale
   4.8 Iconography
   4.9 Focus ring and selection indication
   4.10 Z-index layers
5. Component families:
   5.1 Shell / chrome primitives
   5.2 Read-surface primitives
   5.3 Live-surface visual indicators (visual only; state ownership stays
   in J2CL — restate this disclaimer verbatim in the packet so the family
   inventory cannot be read as implying visual-driven state)
   5.4 Compose / toolbar primitives (inventory only)
   5.5 Interaction-overlay primitives
   5.6 Server-first first-paint primitives
6. Stitch Artifact Policy
   - Required / Optional / Prohibited table keyed on family + variant
   - Required rows pin: consuming slice, parity matrix rows, required
     artifact inventory (screens, variants, design-system id)
7. Image-generation policy (restate workflow memo roles)
8. Cross-cutting rules:
   - Accessibility and keyboard/focus
   - i18n and RTL
   - Motion and reduced-motion
   - Progressive enhancement / server-first coexistence
9. Downstream consumption map (which slice consumes which family and which
   Stitch rows)
10. Change policy and anchor stability.

Row-sourcing rules the reviewer will enforce:

- every token section states the semantic slot and its *intent*, not a
  numeric value
- every component family cites parity matrix rows it supports
- every Required Stitch row names a consuming slice (no orphan requirements)
- the packet never claims ownership of transport, auth, bootstrap, socket,
  unread, version/hash, fragment-window, route, or feature-flag behavior

## 4. Task Breakdown

### Task 1: Baseline review (read-only)
- [ ] Re-read `docs/j2cl-lit-implementation-workflow.md` §§3–7 to keep
  Stitch/image-gen/code-spec role boundaries aligned.
- [ ] Re-read `docs/j2cl-parity-issue-map.md` §4 and §7 to keep downstream
  slice numbers accurate and Stitch scope consistent.
- [ ] Re-read `docs/j2cl-gwt-parity-matrix.md` §§3–7 so variant/family
  citations use the correct `R-<section>.<n>` anchors.
- [ ] Skim `docs/j2cl-parity-slice-packet-template.md` §3 so packet fields
  (Required-match behaviors / Allowed-change visuals / Keyboard / i18n /
  Accessibility / Telemetry / Verification) line up with the packet's
  component-family bullets.

### Task 2: Write the design packet
- [ ] Create `docs/j2cl-lit-design-packet.md` with the full §3 structure.
- [ ] Keep token entries as *semantic slot + intent* only.
- [ ] For every component family, list: purpose, variants,
  matrix rows supported, accessibility/keyboard rule inheritance,
  consuming slice(s), and Stitch artifact classification.
- [ ] Complete the Stitch Artifact Policy table. Required rows must name the
  consuming slice; Prohibited rows must name the reason the domain is out of
  scope for visual design (cite issue map §7 or the workflow memo).

### Task 3: Register the doc and add changelog
- [ ] Add `docs/j2cl-lit-design-packet.md` to `docs/DOC_REGISTRY.md`
  adjacent to the other j2cl parity docs.
- [ ] Add the new path to the `## References` section of `docs/README.md`
  adjacent to `j2cl-parity-slice-packet-template.md`.
- [ ] Add
  `wave/config/changelog.d/2026-04-22-j2cl-lit-design-packet.json` modeled
  on `2026-04-22-j2cl-parity-matrix.json`, using `releaseId`
  `2026-04-22-j2cl-lit-design-packet`, `version` `"Issue #962"`, and a
  single `feature` section describing the packet.

### Task 4: Verification
- [ ] `git diff --check`
- [ ] `bash scripts/check-doc-freshness.sh`
- [ ] `bash scripts/check-doc-links.sh`
- [ ] `python scripts/validate-changelog.py` (confirm same-date fragments —
  e.g. `2026-04-22-j2cl-parity-matrix.json` plus the new
  `2026-04-22-j2cl-lit-design-packet.json` — coexist cleanly)
- [ ] Sanity check: every §5.x component family in the new packet cites at
  least one `R-<section>.<n>` anchor from `docs/j2cl-gwt-parity-matrix.md`
  (e.g. `grep -nE '^## 5\.|R-[0-9]+\.[0-9]+' docs/j2cl-lit-design-packet.md`
  and confirm each family block contains at least one `R-` anchor before
  moving to review).

### Task 5: Review
- [ ] Self-review for overlap with parity architecture memo, workflow memo,
  issue map, parity matrix, and slice packet template. Remove duplication;
  rely on cross-links.
- [ ] Run Claude Opus 4.7 review on this plan before implementation; resolve
  feedback or record explicit disposition.
- [ ] Run Claude Opus 4.7 review on the implementation diff; resolve valid
  comments.

### Task 6: Traceability + PR
- [ ] Commit the new doc, `DOC_REGISTRY.md`/`README.md` updates, and the
  changelog fragment together.
- [ ] Push the branch and open a PR against `main` citing this plan, the
  new doc path, verification commands/results, and both review outcomes.
- [ ] Update issue `#962` with worktree path, branch, plan path, commit
  SHA(s), verification output, review outcomes, and PR URL.
- [ ] Monitor the PR until merged or until a concrete blocker is recorded.

## 5. Verification Summary

Expected command + outcome record (to be mirrored into the issue):

```bash
git diff --check
bash scripts/check-doc-freshness.sh
bash scripts/check-doc-links.sh
python scripts/validate-changelog.py
```

The changelog validator uses default paths (`wave/config/changelog.d/` for
fragments, `wave/config/changelog.json` for assembled output).

Expected: all four exit 0. No runtime/product changes, so no server smoke or
browser verification is required (Change-Type Verification Matrix row:
docs-only).

## 6. Definition Of Done

- `docs/j2cl-lit-design-packet.md` exists and:
  - covers every stage in the parity matrix (read, live, compose,
    server-first, fragments) at the design-family level
  - states the Stitch Required / Optional / Prohibited policy explicitly,
    with each Required row naming a consuming slice
  - keeps token entries as semantic slot + intent, not numeric values
  - claims no ownership of transport/auth/bootstrap/unread/version-hash/
    fragment-window/route/feature-flag behavior
- The packet is registered in `docs/DOC_REGISTRY.md` and listed under
  References in `docs/README.md`.
- The changelog fragment validates and is committed.
- Doc guardrail scripts pass.
- Claude Opus 4.7 reviews (plan + implementation) are clean.
- Issue `#962` carries worktree path, branch, plan path, commit SHA(s),
  verification evidence, review outcomes, and PR URL.
- The PR merges, or a concrete blocker is recorded in the issue.

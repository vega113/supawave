# Issue #958 J2CL/Lit Implementation Workflow Plan

> **For agentic workers:** Treat this as a documentation/recommendation slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a follow-on doc that explains how SupaWave should actually implement the J2CL/Lit UI after the merged parity-architecture decision: what role code/specs, Google Stitch, and image-generation tools should each play while preserving full GWT functionality parity and allowing a more modern, more "Wavy" visual direction.

**Architecture:** Start from the merged `docs/j2cl-parity-architecture.md` baseline on `main`. This slice does not revisit the framework choice. Instead, it defines the execution workflow: code-backed parity inventory and behavior specs remain the source of truth; Stitch is the primary structured design/mock/prototype accelerator; GPT/OpenAI image generation is limited to visual direction and moodboard work; Lit implementation follows approved behavior specs plus design artifacts rather than pixel-tracing from images.

**Tech Stack:** Markdown docs under `docs/`, existing merged J2CL parity memo, local Stitch MCP capabilities, official Google Stitch product guidance, official OpenAI image-generation guidance, GitHub issue/PR traceability, and Claude Opus 4.7 review of the final diff.

---

## 1. Why This Exists

Issue `#958` exists because the merged J2CL parity architecture memo answered **what** framework/runtime split to choose, but not **how** to implement it day to day.

The practical workflow question is now:

- should SupaWave use Stitch for mockups and design iteration?
- should it use GPT/OpenAI image generation for mockups?
- what remains code-first/spec-first so parity with the current GWT behavior does not get lost?
- how can the UI become more modern and more "Wavy" without letting visual experiments rewrite behavior accidentally?

The doc must answer those questions in a way that future issues under `#904` can follow directly.

## 2. Scope And Non-Goals

### In Scope

- define the recommended J2CL/Lit implementation workflow
- evaluate Stitch as a design/mock/prototype tool in this repo's workflow
- evaluate GPT/OpenAI image-generation tools as a design aid in this repo's workflow
- define the source of truth for behavior parity
- explain how to separate parity-safe visual modernization from behavior changes
- produce one committed Markdown doc under `docs/`

### Explicit Non-Goals

- no implementation of the Lit UI itself
- no change to the merged framework recommendation
- no attempt to solve transport/auth/read-state issues in this doc
- no replacement of existing design tools across the team in this slice

## 3. Expected Inputs And Files

### Primary Repo Inputs

- `docs/j2cl-parity-architecture.md`
- `docs/j2cl-gwt3-decision-memo.md`
- `docs/runbooks/j2cl-sidecar-testing.md`
- `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- the current J2CL view/controller seams under `j2cl/src/main/java/org/waveprotocol/box/j2cl/**`

### External Inputs

- official Google Stitch product/update docs
- current local Stitch MCP capability surface available in this environment
- official OpenAI image-generation docs

### Likely New / Updated Files

- `docs/j2cl-lit-implementation-workflow.md`
- `docs/README.md`
- `docs/DOC_REGISTRY.md`
- `wave/config/changelog.d/2026-04-22-j2cl-lit-workflow.json`

## 4. Questions The Doc Must Answer

- [ ] What is the recommended source of truth for parity work: code, specs, mockups, or generated images?
- [ ] Where should Stitch be used, and where should it explicitly *not* be trusted?
- [ ] Where should GPT/OpenAI image generation be used, and where should it explicitly *not* be trusted?
- [ ] What implementation packet should exist before a Lit slice starts coding?
- [ ] How should the team preserve current GWT behavior while modernizing the shell/components visually?
- [ ] What is the recommended order from parity inventory -> visual exploration -> approved design system/components -> Lit implementation -> browser verification?

## 5. Concrete Task Breakdown

### Task 1: Freeze The Recommendation Boundary

- [ ] Keep this slice to workflow guidance only.
- [ ] Do not reopen the J2CL-vs-Lit-vs-React decision.
- [ ] Keep the parent tracker as `#904` and position this as a follow-on to merged PR `#956`.

### Task 2: Gather Tool Capability Evidence

- [ ] Use the current local Stitch MCP surface as evidence for what Stitch can do now.
- [ ] Use official Google Stitch docs/update posts for the product-level positioning.
- [ ] Use official OpenAI image-generation docs for the current strengths/limitations of image generation and editing.

### Task 3: Make One Explicit Workflow Recommendation

- [ ] Recommend one of these as the primary design accelerator:
  - Stitch-first
  - image-first
  - code/spec-first with optional design tools
- [ ] Explain the tradeoffs and why the recommended path best preserves parity while still enabling modernized UI.
- [ ] Be explicit about the role of:
  - parity inventory
  - behavioral spec/checklist
  - design system/tokens
  - mockups/prototypes
  - Lit component implementation

### Task 4: Write The Doc

- [ ] Create `docs/j2cl-lit-implementation-workflow.md`.
- [ ] Structure it roughly as:
  - decision summary
  - source of truth for parity
  - where Stitch fits
  - where image generation fits
  - recommended end-to-end workflow
  - how to modernize the look without breaking behavior
  - recommended acceptance gates for future J2CL/Lit slices
- [ ] Keep the recommendation concrete enough that a future issue can follow it without reinterpretation.

### Task 5: Self-review And Claude Review

- [ ] Self-review the new doc for unsupported claims, scope drift, and weak tool guidance.
- [ ] Run `git diff --check`.
- [ ] Run a Claude Opus 4.7 review on the implementation diff.
- [ ] Address valid findings and rerun until clean or until only non-actionable nits remain.

### Task 6: Traceability And PR

- [ ] Comment on the issue with worktree path, plan path, and final PR link.
- [ ] Commit the doc and any index/changelog updates.
- [ ] Open a PR from the issue worktree.

## 6. Verification / Review

Run these from the issue worktree:

```bash
git diff --check
```

Expected result:

- no whitespace or patch-format issues

Then run Claude Opus 4.7 review on the final diff and record the result in the issue/PR trail.

## 7. Definition Of Done

- `docs/j2cl-lit-implementation-workflow.md` exists and is committed
- the doc makes one explicit recommendation for how to use Stitch, one for how to use image generation, and one for what remains the source of truth
- the doc explains how to preserve GWT behavior parity while modernizing look-and-feel
- the doc gives a concrete workflow from parity inventory to Lit implementation and browser verification
- `git diff --check` passes
- Claude Opus 4.7 review has been run on the final diff
- the issue comment trail and PR include plan path, review outcome, and final doc link

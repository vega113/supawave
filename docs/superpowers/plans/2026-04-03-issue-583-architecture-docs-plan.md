# Issue 583 Architecture Docs Extraction Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move durable architecture guidance out of `ORCHESTRATOR.md` into stable docs under `docs/architecture/` while keeping the orchestrator file focused on live operational state.

**Architecture:** Create three stable architecture references that capture the Jakarta dual-source model, the runtime entrypoint map, and the dev persistence topology. Replace long-lived architecture prose in `ORCHESTRATOR.md` with short summaries and links, then update the repo’s documentation navigation surfaces so future refreshes point readers to the stable references instead of rebuilding architecture context inside the orchestrator ledger.

**Tech Stack:** Markdown docs, repo navigation in `README.md`, current-state guide in `docs/current-state.md`

---

## Chunk 1: Source Mapping And Durable Doc Boundaries

### Task 1: Confirm source material and target ownership

**Files:**
- Read: `ORCHESTRATOR.md`
- Read: `README.md`
- Read: `docs/current-state.md`
- Reference: `docs/persistence-topology-audit.md`
- Create later: `docs/architecture/jakarta-dual-source.md`
- Create later: `docs/architecture/runtime-entrypoints.md`
- Create later: `docs/architecture/dev-persistence-topology.md`

- [ ] **Step 1: Capture the durable content currently embedded in `ORCHESTRATOR.md`**

Identify the sections that should move into stable docs:
- module layout and stack summary that explains the Jakarta runtime shape
- Jakarta dual-source explanation and editing rules
- entrypoint and servlet registration map
- persistence architecture and default dev file-store layout

- [ ] **Step 2: Confirm the minimal docs-map surface**

Treat `README.md` section `Documentation map` and `docs/current-state.md`
section `Canonical documentation set` as the minimal docs-map targets. Avoid
wider navigation churn outside those two live entrypoints.

- [ ] **Step 3: Preserve operational-only content in `ORCHESTRATOR.md`**

Keep live items in place:
- production topology and deploy facts
- fragile areas and incident playbooks
- active lanes, open risks, and resumption checklist

Expected result:
- clear boundary between durable architecture docs and living operations ledger

## Chunk 2: Add Stable Architecture References

### Task 2: Create the architecture doc directory and durable references

**Files:**
- Create: `docs/architecture/jakarta-dual-source.md`
- Create: `docs/architecture/runtime-entrypoints.md`
- Create: `docs/architecture/dev-persistence-topology.md`

- [ ] **Step 1: Write `docs/architecture/jakarta-dual-source.md`**

Cover:
- why both `wave/src/main/java/` and `wave/src/jakarta-overrides/java/` exist
- how `build.sbt` source exclusion selects the Jakarta runtime copy
- which code surfaces typically require checking overrides first
- practical editing rules for future agents

- [ ] **Step 2: Write `docs/architecture/runtime-entrypoints.md`**

Cover:
- `ServerMain` as runtime bootstrap
- `ServerRpcProvider` as servlet/router registration seam
- config entrypoints under `wave/config/`
- secondary utilities that are real entrypoints but not the main server path

- [ ] **Step 3: Write `docs/architecture/dev-persistence-topology.md`**

Cover:
- file-backed dev directories (`_accounts`, `_attachments`, `_deltas`, `_certificates`)
- pluggable store interfaces and current safe dev/runtime defaults
- relationship to the existing persistence audit for production and multi-instance concerns
- explicit statement that this file describes development/runtime topology, not the broader Mongo readiness audit

- [ ] **Step 4: Keep cross-links narrow**

Link each new architecture page only where it clarifies neighboring durable docs. Do not perform broad doc cleanup outside the issue scope.

Expected result:
- three stable architecture references exist under `docs/architecture/`

## Chunk 3: Trim The Orchestrator And Update Navigation

### Task 3: Convert `ORCHESTRATOR.md` into a living operational ledger

**Files:**
- Modify: `ORCHESTRATOR.md`

- [ ] **Step 1: Replace the long architecture manual sections with concise summaries**

Trim or compress:
- `Architecture Overview`
- `Module Layout`
- `Technology Stack`
- `Entry Points`
- `Servlet Registration`
- `Jakarta Migration Pattern`
- `Persistence Architecture`
- `Guice Module Hierarchy`
- `Code Generation Pipeline`
- `WebSocket / RPC Architecture`

- [ ] **Step 2: Add explicit links from `ORCHESTRATOR.md` to the new architecture docs**

Point readers to:
- `docs/architecture/jakarta-dual-source.md`
- `docs/architecture/runtime-entrypoints.md`
- `docs/architecture/dev-persistence-topology.md`

- [ ] **Step 3: Keep live-state guidance intact**

Do not change lane policy, PR policy, incident response content, or the core
resume checklist beyond removing duplicated durable architecture prose.

Expected result:
- `ORCHESTRATOR.md` becomes a refreshable operations ledger instead of the primary architecture manual

### Task 4: Update docs navigation links

**Files:**
- Modify: `README.md`
- Modify: `docs/current-state.md`

- [ ] **Step 1: Update `README.md` documentation map**

Add explicit links to the new architecture docs in the `Documentation map` section and reduce any reliance on `ORCHESTRATOR.md` for durable architecture context.

- [ ] **Step 2: Update `docs/current-state.md` canonical documentation set**

Add the new architecture references explicitly and ensure the current-state guide points readers to those stable paths for architecture context.

- [ ] **Step 3: Keep the update narrow**

Do not reorganize the broader canonical documentation set beyond the links needed for these new architecture references.

Expected result:
- `README.md` and `docs/current-state.md` both route readers to the new architecture references

## Chunk 4: Verify The Doc Move

### Task 5: Run targeted verification

**Files:**
- Verify: `ORCHESTRATOR.md`
- Verify: `README.md`
- Verify: `docs/current-state.md`
- Verify: `docs/architecture/jakarta-dual-source.md`
- Verify: `docs/architecture/runtime-entrypoints.md`
- Verify: `docs/architecture/dev-persistence-topology.md`

- [ ] **Step 1: Confirm stable paths exist**

Run:
```bash
fd . docs/architecture
```
Expected:
- the three new architecture docs are present

- [ ] **Step 2: Confirm navigation surfaces reference the new docs**

Run:
```bash
rg -n "docs/architecture/(jakarta-dual-source|runtime-entrypoints|dev-persistence-topology)\\.md" README.md docs/current-state.md ORCHESTRATOR.md
```
Expected:
- matches in `README.md`, `docs/current-state.md`, and `ORCHESTRATOR.md`

- [ ] **Step 3: Confirm `ORCHESTRATOR.md` is no longer the durable architecture manual**

Run:
```bash
rg -n "Architecture Overview|Module Layout|Technology Stack|Servlet Registration|Jakarta Migration Pattern|Persistence Architecture|Guice Module Hierarchy|Code Generation Pipeline|WebSocket / RPC Architecture" ORCHESTRATOR.md
```
Expected:
- no remaining long-form section headers for the extracted architecture manual content

- [ ] **Step 4: Review the final diff before commit**

Run:
```bash
git status --short
git diff -- ORCHESTRATOR.md README.md docs/current-state.md docs/architecture docs/superpowers/plans/2026-04-03-issue-583-architecture-docs-plan.md
```
Expected:
- only the planned docs files changed

## Acceptance Criteria Mapping

- `ORCHESTRATOR.md` no longer serves as the primary durable architecture manual
  - satisfied by Chunk 3 Task 3
- durable architecture references exist at stable paths under `docs/architecture/`
  - satisfied by Chunk 2 Task 2
- the docs map and `docs/current-state.md` link to those references explicitly
  - satisfied by Chunk 3 Task 4
- future orchestrator refreshes can update current state without rewriting long-lived architecture prose
  - satisfied by Chunk 3 Task 3 and Chunk 4 Task 5

## Risks

- Removing too much from `ORCHESTRATOR.md` could weaken the operational resume value; trim only durable architecture prose, not live-state guidance.
- The new persistence topology doc could drift into production-readiness analysis; keep the production and multi-instance material linked out to `docs/persistence-topology-audit.md`.
- The repo has multiple historical docs touching Jakarta and persistence; do not attempt to normalize all of them in this issue.

## Non-Goals

- Changing lane policy, PR policy, or review-gate policy
- Broadly rewriting the documentation set beyond the new architecture links
- Reworking production persistence strategy or Mongo readiness guidance

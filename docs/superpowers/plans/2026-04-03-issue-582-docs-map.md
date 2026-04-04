# Issue #582 Docs Map Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repo docs map and reconcile the canonical entry-point docs so a new agent can start from `README.md` or `docs/current-state.md` and reach the correct guidance in two hops or fewer without broad doc relocation.

**Architecture:** Keep existing docs in place and add lightweight map/index files that route readers by purpose. Reconcile the current entry points around the live SBT/Jakarta reality, then classify existing docs under stable categories instead of moving them.

**Tech Stack:** Markdown documentation, GitHub Issues workflow, existing repo docs under `docs/`

---

## Acceptance Criteria

- `README.md` points to the new docs map and no longer suggests stale Gradle-era guidance is canonical.
- `docs/current-state.md` reflects the current repo state instead of describing `modernization` or Gradle as the active/canonical path, and it matches the GitHub-Issues tracker migration now in `main`.
- `docs/README.md` distinguishes map docs, runbooks, references, ledgers, and archives.
- `docs/agents/README.md`, `docs/architecture/README.md`, and `docs/runbooks/README.md` exist and route to the right existing docs without broad path churn.
- A new agent can start from `README.md` or `docs/current-state.md` and reach the relevant map docs within two hops.

## Scope

- Add:
  - `docs/agents/`
  - `docs/architecture/`
  - `docs/runbooks/`
  - `docs/README.md`
  - `docs/agents/README.md`
  - `docs/architecture/README.md`
  - `docs/runbooks/README.md`
- Update:
  - `README.md`
  - `docs/current-state.md`
  - `docs/BUILDING-sbt.md`
- Classify existing docs in place, including:
  - `docs/BUILDING-sbt.md`
  - `docs/DEV_SETUP.md`
  - `docs/SMOKE_TESTS.md`
  - `docs/CONFIG_FLAGS.md`
  - `docs/fragments-config.md`
  - `docs/persistence-topology-audit.md`
  - `docs/snapshot-gating-decision.md`
  - `docs/j2cl-gwt3-decision-memo.md`
  - `docs/j2cl-gwt3-inventory.md`
  - `docs/modernization-plan.md`
  - `docs/jetty-migration.md`
  - `docs/migrate-conversation-renderer-to-apache-wave.md`
  - `docs/blocks-adoption-plan.md`
  - `docs/deployment/README.md`
  - `docs/deployment/linux-host.md`
  - `docs/deployment/standalone.md`
  - `docs/deployment/caddy.md`
  - deployment overlays such as `docs/deployment/contabo.md`, `docs/deployment/cloudflare-supawave.md`, `docs/deployment/email-deliverability-supawave.md`, and `docs/deployment/mongo-hardening.md`

## Out Of Scope

- Extracting durable architecture guidance from `ORCHESTRATOR.md`
- Slimming or restructuring `AGENTS.md`
- Moving existing docs into new directories unless a canonical path is broken
- Broad content rewrites outside the entry-point and classification inconsistencies required for this issue

## Confirmed Findings

- PR #595 (`[codex] docs: switch live tracker guidance to GitHub issues`) merged to `main` on 2026-04-03, so this branch must now be refreshed onto `origin/main` rather than treated as an unmerged stacked change.
- `README.md` is partly updated for SBT/Jakarta but still carries stale Vagrant and Gradle-era wording and still describes Beads as the live task tracker on this branch.
- `docs/current-state.md` still describes `README.md` as an entry point for Gradle usage, refers to `modernization` as the active branch, and still points to Beads as the live backlog on this branch.
- `docs/BUILDING-sbt.md` says SBT is the sole build system in the header while `build.sbt` still wires `sbt run` through `compileGwt`, and `compileGwt` may delegate to `./gradlew :wave:compileGwt` when `gradlew` is present.
- `docs/BUILDING-sbt.md` also still describes the build as additive, server-only, and not yet working for end users, which conflicts with how the repo now presents SBT/Jakarta elsewhere.
- The required map docs do not exist yet.
- The branch does not contain `docs/github-issues.md`, `docs/agents/tool-usage.md`, or the extracted `docs/architecture/*.md` files that now exist on `main`; the map docs added for #582 must route to those paths once the branch is refreshed.
- Deployment docs already exist in usable canonical paths and should be classified in place, not moved.

## Likely Touched Files

- Modify: `README.md`
- Modify: `docs/current-state.md`
- Modify: `docs/BUILDING-sbt.md`
- Create: `docs/README.md`
- Create: `docs/agents/README.md`
- Create: `docs/architecture/README.md`
- Create: `docs/runbooks/README.md`

### Task 1: Add The Docs Map Layer

**Files:**
- Create: `docs/README.md`
- Create: `docs/agents/README.md`
- Create: `docs/architecture/README.md`
- Create: `docs/runbooks/README.md`

- [ ] Step 1: Create the `docs/agents/`, `docs/architecture/`, and `docs/runbooks/` directories so the new map docs live at stable canonical paths.
- [ ] Step 2: Inventory the existing docs that should be routed through the new map docs, grouped as map docs, runbooks, references, ledgers, and archives. Explicitly route the current build/reference set (`docs/BUILDING-sbt.md`, `docs/DEV_SETUP.md`, `docs/SMOKE_TESTS.md`, `docs/CONFIG_FLAGS.md`, `docs/fragments-config.md`), the current architecture/audit set (`docs/persistence-topology-audit.md`, `docs/snapshot-gating-decision.md`, `docs/j2cl-gwt3-decision-memo.md`, `docs/j2cl-gwt3-inventory.md`, `docs/architecture/*.md`), the live tracker workflow doc (`docs/github-issues.md`), and the historical ledgers (`docs/modernization-plan.md`, `docs/jetty-migration.md`, `docs/migrate-conversation-renderer-to-apache-wave.md`, `docs/blocks-adoption-plan.md`).
- [ ] Step 3: Draft `docs/README.md` as the top-level docs map, with short category definitions and links to the new sub-maps plus existing canonical docs that remain at top level.
- [ ] Step 4: Draft `docs/agents/README.md` for agent-facing entry points such as the current-state guide, `docs/github-issues.md`, `docs/agents/tool-usage.md`, and execution/process docs that help a new agent orient quickly while clearly treating Beads materials as archive-only.
- [ ] Step 5: Draft `docs/architecture/README.md` for durable technical references and ledgers that explain the current system shape without relocating those files.
- [ ] Step 6: Draft `docs/runbooks/README.md` for operational and deployment flows, linking to deployment docs and other hands-on procedures that remain in place.

### Task 2: Reconcile The Canonical Entry Points

**Files:**
- Modify: `README.md`
- Modify: `docs/current-state.md`

- [ ] Step 1: Update `README.md` so its documentation section points readers to `docs/README.md` first, with concise wording that matches the current SBT/Jakarta build and runtime reality.
- [ ] Step 2: Remove or rewrite stale Vagrant and Gradle-era wording in `README.md` where it would mislead a new agent about the active toolchain or setup story.
- [ ] Step 3: Update `docs/current-state.md` so it describes the current repository and branch reality accurately, removes the "Gradle usage" and Gradle-canonical language, aligns with the GitHub-Issues tracker migration already merged on `main`, fixes nearby list drift if needed, and points readers to the new map docs.
- [ ] Step 4: Make sure both entry points satisfy the two-hop navigation requirement by linking directly to the top-level docs map and the most relevant sub-map(s).

### Task 3: Make The Build Doc Internally Consistent

**Files:**
- Modify: `docs/BUILDING-sbt.md`

- [ ] Step 1: Reconcile the header/body mismatch so the document consistently describes the current SBT/Jakarta state.
- [ ] Step 2: Keep the nuance that some build surfaces still have gaps, but express those as current limitations within the supported SBT/Jakarta path rather than as an additive or non-canonical side path.
- [ ] Step 3: Soften or replace wording such as "Gradle was removed" where it would deny the current SBT/Jakarta entry point, while still making clear that readers should start from SBT/Jakarta docs.

### Task 4: Classify Deployment Docs In Place

**Files:**
- Modify: `docs/README.md`
- Modify: `docs/runbooks/README.md`

- [ ] Step 1: Classify `docs/deployment/README.md` as the canonical deployment runbook entry point.
- [ ] Step 2: Classify `linux-host.md`, `standalone.md`, and `caddy.md` as canonical deployment runbooks.
- [ ] Step 3: Classify provider or environment-specific deployment files as overlays or references that remain in place under `docs/deployment/`.

## Verification

- Rebase or otherwise refresh the branch onto `origin/main` so `docs/github-issues.md`, `docs/agents/tool-usage.md`, and the extracted architecture references are present before final verification.
- Review the final link graph manually from both `README.md` and `docs/current-state.md` and confirm the relevant map docs are reachable in two hops or fewer.
- Run a targeted grep to confirm stale Gradle-canonical phrasing is removed from the updated entry-point docs:
  - `rg -n "Gradle usage|Gradle remains canonical|Gradle Tasks" README.md docs/current-state.md`
- Run a targeted grep to confirm live-tracker wording matches the GitHub-Issues migration in the touched docs:
  - `rg -n "repo-local Beads backlog|Live issue/task data|\\.beads/issues.jsonl|GitHub Issues" README.md docs/current-state.md docs/README.md docs/agents/README.md`
- Run a markdown/path sanity check by opening the changed files and confirming each referenced docs path exists in the repo.
- Run `git diff --stat` and `git diff -- README.md docs/current-state.md docs/BUILDING-sbt.md docs/README.md docs/agents/README.md docs/architecture/README.md docs/runbooks/README.md docs/deployment/README.md` to verify the slice stayed narrow and did not introduce broad doc churn.

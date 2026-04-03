# Jakarta Wrong-Edit Guardrail Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an advisory shell-first guardrail that warns when a change likely edits the wrong source tree in the Jakarta dual-source layout.

**Architecture:** Add one reusable shell script that inspects a diff, identifies changed `wave/src/main/java/...` files whose runtime-active Jakarta override exists, suppresses warnings when the override changed in the same diff, and always exits successfully in advisory mode. Run that same script in CI against the PR diff and make it usable locally against staged or working-tree changes so agent lanes can gather signal before any future blocking decision.

**Tech Stack:** Bash, git diff plumbing, GitHub Actions, Python `unittest` for script tests, repo docs in `ORCHESTRATOR.md`.

---

## Chunk 1: Guard Script + CI Wiring

### Task 1: Add the advisory heuristic script

**Files:**
- Create: `scripts/jakarta-wrong-edit-guard.sh`
- Test: `scripts/tests/test_jakarta_wrong_edit_guard.py`

- [ ] **Step 1: Write failing tests for the diff heuristic**
- [ ] **Step 2: Cover runtime-active override detection**
Expected cases:
  - warn when `wave/src/main/java/...` changes and a matching Jakarta override exists
  - do not warn when the matching override changed in the same diff
  - do not warn when the matching override path is one of the known excluded Jakarta override files from `build.sbt`
  - handle rename/status output without crashing
  - return success with no warnings when no suspicious files are present
- [ ] **Step 3: Implement the shell script**
Requirements:
  - support a CI/base-ref mode using `merge-base`
  - support a local mode using staged + working-tree changes
  - emit advisory-only warnings and exit `0`
  - point to `ORCHESTRATOR.md` for the Jakarta override rule
  - tell agents to collect false-positive/false-negative evidence before any blocking mode
- [ ] **Step 4: Wire the script into `.github/workflows/build.yml`**
Requirements:
  - run early enough to surface warnings on PRs
  - keep the build green when warnings fire
  - use the same script entrypoint as local lanes

## Chunk 2: Docs + Verification

### Task 2: Document the guardrail and verify it

**Files:**
- Modify: `ORCHESTRATOR.md`
- Modify: `.github/workflows/build.yml`
- Test: `scripts/tests/test_jakarta_wrong_edit_guard.py`

- [ ] **Step 1: Add a short doc note about the advisory guardrail**
Document:
  - what the script checks
  - that the warning is advisory only for now
  - that agents should use the Jakarta override copy when it exists
- [ ] **Step 2: Run targeted verification**
Run:
  - `python3 -m unittest scripts.tests.test_jakarta_wrong_edit_guard`
  - a direct shell invocation of the script in local mode
  - a direct shell invocation of the script with an explicit base ref
- [ ] **Step 3: Record follow-up evaluation path**
Capture in the issue/PR summary:
  - the script is advisory-only pending lane signal quality
  - future blocking mode requires evidence from false-positive / false-negative collection

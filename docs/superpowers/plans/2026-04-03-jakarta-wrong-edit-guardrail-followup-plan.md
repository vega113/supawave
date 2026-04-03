# Jakarta Wrong-Edit Guardrail Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the advisory Jakarta wrong-edit guardrail docs so every document cited by the warning actually explains the runtime-active override rule.

**Architecture:** Keep the existing shell heuristic and CI wiring unchanged. Add the missing Jakarta dual-source guidance to `AGENTS.md` so it matches `ORCHESTRATOR.md` and remains consistent with the warning emitted by `scripts/jakarta-wrong-edit-guard.sh`.

**Tech Stack:** Markdown docs, Bash script verification, Python `unittest`.

---

## Chunk 1: AGENTS.md Consistency Fix

### Task 1: Add the missing Jakarta override guidance to `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`
- Verify: `scripts/jakarta-wrong-edit-guard.sh`
- Verify: `scripts/tests/test_jakarta_wrong_edit_guard.py`

- [ ] **Step 1: Confirm the current inconsistency**
Run: `rg -n "Jakarta Migration Pattern|jakarta-overrides|wrong-edit-guard" AGENTS.md ORCHESTRATOR.md scripts/jakarta-wrong-edit-guard.sh`
Expected: before the follow-up landed, `scripts/jakarta-wrong-edit-guard.sh` cited `AGENTS.md`, `ORCHESTRATOR.md` documented the override rule, and `AGENTS.md` lacked the matching rule text; after the follow-up, `AGENTS.md` contains the same Jakarta dual-source rule.

- [ ] **Step 2: Add the minimal AGENTS.md rule**
Edit `AGENTS.md` near the workflow and code-guidance sections to state that when a matching file exists under `wave/src/jakarta-overrides/java/`, agents must edit the override copy because the override is runtime-active.

- [ ] **Step 3: Keep the warning/docs surface unchanged unless the edit exposes a contradiction**
Do not change `ORCHESTRATOR.md`, `.github/workflows/build.yml`, or `scripts/jakarta-wrong-edit-guard.sh` unless the AGENTS.md wording reveals a concrete inconsistency that makes the current warning inaccurate.

- [ ] **Step 4: Run targeted verification**
Run: `python3 -m unittest scripts.tests.test_jakarta_wrong_edit_guard`
Expected: all tests pass.

Run: `bash -n scripts/jakarta-wrong-edit-guard.sh`
Expected: exit `0` with no shell syntax errors.

- [ ] **Step 5: Review the final diff and commit**
Run: `git status --short`
Expected: only `AGENTS.md` and this plan file are modified before commit.

Commit: `git add AGENTS.md docs/superpowers/plans/2026-04-03-jakarta-wrong-edit-guardrail-followup-plan.md && git commit -m "docs: align jakarta guardrail guidance"`

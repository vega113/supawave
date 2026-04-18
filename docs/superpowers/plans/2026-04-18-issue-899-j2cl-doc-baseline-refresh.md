# Issue #899 J2CL Doc Baseline Refresh Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the stale J2CL / GWT3 documentation so it reflects the post-Phase-0 cleanup baseline and the current follow-on issue map, without changing any implementation code.

**Architecture:** Treat `docs/j2cl-gwt3-inventory.md` and `docs/j2cl-gwt3-decision-memo.md` as the measured baseline and decision record, then align `docs/j2cl-preparatory-work.md`, `docs/modernization-plan.md`, and `docs/current-state.md` to that same baseline. Use `docs/superpowers/plans/j2cl-full-migration-plan.md` only as the strategic source of truth for wording and sequencing; this issue is a documentation refresh, not a migration step.

**Tech Stack:** Markdown docs, shell-based inventory verification, SBT/Wave local smoke verification, GitHub issue traceability.

---

## Goal / Root Cause
The docs still describe an older pre-Phase-0 J2CL baseline. The repo now has a smaller, cleaned-up measured surface, and the docs need to agree on the same numbers and follow-on issue chain. The refresh is about eliminating stale facts, not about advancing the migration itself.

## Scope and Non-Goals
- Scope: update the five docs listed below to the current measured baseline; replace stale inventory counts and outdated claims with the post-cleanup numbers; align the docs with the current tracker chain (`#904` plus `#900`, `#903`, `#902`, `#898`, and `#901`); keep the strategic migration narrative pointed at `docs/superpowers/plans/j2cl-full-migration-plan.md`.
- Non-goals: no Java, Scala, or build changes; no new migration decisions; no edits to the strategic J2CL full-migration plan; no changes to GitHub issues beyond recording execution evidence and links.

## Exact Files to Update
- `docs/j2cl-gwt3-inventory.md`
- `docs/j2cl-gwt3-decision-memo.md`
- `docs/j2cl-preparatory-work.md`
- `docs/modernization-plan.md`
- `docs/current-state.md`

## Plan
### Task 1: Refresh the measured baseline docs
- [ ] Update `docs/j2cl-gwt3-inventory.md` to use the current counts: `.gwt.xml` files `129`, `GWT.create(...)` callsites `84`, JSNI / `JavaScriptObject` files `114`, JSNI native methods `238`, `GWTTestCase` files `24`, `.ui.xml` templates `23`, and JsInterop / Elemental2 usage `0`.
- [ ] Update `docs/j2cl-gwt3-decision-memo.md` so the evidence basis and recommendation text match the refreshed inventory and the post-Phase-0 cleanup state.
- [ ] Make sure both documents state the current dependency facts explicitly: `guava-gwt` is already gone from `build.sbt`; gadget/htmltemplate client trees are already gone; `WaveContext` already uses `org.waveprotocol.wave.model.document.BlipReadStateMonitor`.

### Task 2: Reconcile the downstream planning docs
- [ ] Update `docs/j2cl-preparatory-work.md` to reflect that the baseline cleanup is already past the earlier inventory stage and that the next work is issue-driven, not discovery-driven.
- [ ] Update `docs/modernization-plan.md` so the Phase 8 / J2CL references point at the refreshed docs and the current follow-on issue map.
- [ ] Update `docs/current-state.md` so the resume-point summary points at the refreshed J2CL docs and the live issue chain instead of stale pre-cleanup assumptions.

### Task 3: Verify the refreshed baseline and record traceability
- [ ] Re-run the inventory and stale-fact checks from the issue worktree.
- [ ] Record the results in the linked issue comment with the worktree path, the plan path, and the exact commands used.
- [ ] Keep the PR body concise and derived from the issue comment and refreshed docs.

## Verification Commands
Run these from `/Users/vega/devroot/worktrees/issue-899-j2cl-doc-refresh`:

```bash
find wave -name '*.gwt.xml' | wc -l
rg -o 'GWT\.create\(' wave/src/main/java wave/src/test/java gen/messages | wc -l
rg -l 'native .*/\*|extends JavaScriptObject|\bJavaScriptObject\b' wave/src/main/java gen/messages | wc -l
rg -l 'GWTTestCase' wave/src/test/java wave/src/test/resources | wc -l
find wave -name '*.ui.xml' | wc -l
rg -l 'JsInterop|Elemental2|elemental2' wave/src/main/java gen/messages | wc -l
rg -n 'guava-gwt' build.sbt
find wave/src/main/java \( -path '*gadget*' -o -path '*htmltemplate*' \) | wc -l
rg -n 'BlipReadStateMonitor' wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java
```

Expected results:
- `.gwt.xml` count: `129`
- `GWT.create(...)` callsites: `84`
- JSNI / `JavaScriptObject` command above returns `114` on this worktree
- `GWTTestCase` files: `24`
- `.ui.xml` templates: `23`
- JsInterop / Elemental2 usage: `0`
- `guava-gwt` check: no matches
- gadget/htmltemplate path check: `0`
- `WaveContext` check: shows the shared `BlipReadStateMonitor` import or usage, not a client/state dependency

## Required Local Verification Before PR
Even though this issue is docs-only, the PR cannot go up until the worktree has been exercised end-to-end:

- `sbt compile`
- `sbt test`
- `bash scripts/worktree-boot.sh --port 9900`  # use the next free port if 9900 is busy, and record the actual port
- `PORT=9900 bash scripts/wave-smoke.sh start`
- Open the staged app in a browser and sanity-check the live flow: register a fresh local user, log in, create a wave, and enter a short line of text in the wave editor.
- `PORT=9900 bash scripts/wave-smoke.sh check`
- `PORT=9900 bash scripts/wave-smoke.sh stop`

Record the exact command outputs and the browser sanity path in the issue comment before opening the PR.

## Acceptance Criteria
- The five docs above all agree on the same refreshed baseline and no longer repeat the stale pre-Phase-0 counts.
- The docs explicitly reflect the current cleanup facts listed in this plan.
- The issue chain is clear: `#904` is the umbrella follow-on tracker, and `#900`, `#903`, `#902`, `#898`, and `#901` are the implementation slices.
- Local compile, tests, and browser smoke verification have been completed and recorded.
- No implementation code has been changed.

## Issue / PR Traceability Notes
- This issue should stay the live execution log for the docs refresh.
- Record the worktree path, branch, plan path, verification commands, and results in the issue comment stream.
- Link the PR back to issue `#899` and keep the PR summary short.
- If any doc still names an old count or a retired dependency, fix the doc before marking the issue ready.

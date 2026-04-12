# Issue 587 Worktree Diagnostics Bundle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one predictable diagnostics bundle command for issue worktrees so lanes can attach compact startup, endpoint, smoke, and log evidence to verification records.

**Architecture:** Reuse the existing `scripts/worktree-boot.sh` plus `scripts/wave-smoke.sh` lifecycle instead of inventing new observability plumbing. Add one shell script that reads the current worktree state, probes the existing endpoints, captures the current smoke result, tails the staged startup/server logs, and emits one Markdown bundle that can be pasted into issue comments or written to a file. Document the flow in one runbook and cross-link it from the existing worktree-lane docs.

**Tech Stack:** Bash, existing worktree/smoke scripts, Markdown runbooks, local staged server verification.

---

## Scope And Decisions

- Keep the scope to one script: `scripts/worktree-diagnostics.sh`.
- Keep the diagnostics bundle grounded in the current staged-worktree runtime:
  - startup output from `target/universal/stage/wave_server.out` (or the legacy staged install equivalent)
  - current endpoint probe statuses
  - current `scripts/wave-smoke.sh check` output and exit status
  - last-N lines from the staged server log (`logs/wave.log`, `logs/wave-json.log`, or `wiab-server.log`, depending on the runtime)
- The diagnostics script must stay useful even when the server failed to start or `wave-smoke.sh check` returns non-zero; failures are evidence, not reasons for the diagnostics command to crash.
- The script may optionally write the Markdown bundle to an explicit output file, but it should always be usable as a single command that prints paste-ready evidence.
- Do not add a second diagnostics script, a metrics daemon, or a larger observability framework.

## Files

- Create: `scripts/worktree-diagnostics.sh`
- Create: `scripts/test-worktree-diagnostics.sh`
- Create: `docs/runbooks/worktree-diagnostics.md`
- Modify: `scripts/worktree-boot.sh`
- Modify: `docs/runbooks/worktree-lane-lifecycle.md`
- Modify: `docs/runbooks/README.md`
- Modify: `docs/SMOKE_TESTS.md`
- Create: `wave/config/changelog.d/2026-04-12-worktree-diagnostics-runbook.json`

## Risks And Non-Goals

- The diagnostics bundle must not quietly report invented paths or green statuses. Missing files and failed smoke checks should stay explicit in the output.
- The worktree helper already owns build prep and evidence-file creation. The diagnostics script should consume that state, not duplicate staging or server startup.
- This task does not change browser-verification policy, add CI wiring, or alter how the server boots.

## Task 1: Lock The Diagnostics Contract With A Failing Script Test

**Files:**
- Create: `scripts/test-worktree-diagnostics.sh`

- [ ] **Step 1: Add a shell test that stubs the worktree environment and asserts the bundle shape**

Create `scripts/test-worktree-diagnostics.sh` using the existing `scripts/test-feature-flag.sh` pattern:
- create a temp fake repo root with `journal/runtime-config/`, `journal/local-verification/`, and a fake staged install dir
- stub `git` so the diagnostics script sees a deterministic repo root and branch
- stub `curl` so `/`, `/healthz`, `/readyz`, and `/webclient/webclient.nocache.js` return deterministic status codes
- stub `scripts/wave-smoke.sh check` so the diagnostics script captures both the smoke output and its exit code
- seed fake `wave_server.out` and `wiab-server.log` files with recognizable tail lines
- assert the Markdown output contains the branch/worktree metadata, endpoint probe statuses, smoke exit/result, startup tail, and server-log tail

- [ ] **Step 2: Run the new shell test and verify it fails for the expected reason**

Run:
```bash
bash scripts/test-worktree-diagnostics.sh
```

Expected:
- the command fails because `scripts/worktree-diagnostics.sh` does not exist yet or does not emit the expected diagnostics sections

## Task 2: Implement The Diagnostics Script

**Files:**
- Create: `scripts/worktree-diagnostics.sh`
- Modify: `scripts/worktree-boot.sh`

- [ ] **Step 1: Add the diagnostics script with a narrow CLI**

Implement `scripts/worktree-diagnostics.sh` with:
- `--port <port>` defaulting to `PORT` or `9898`
- `--lines <n>` defaulting to a small tail such as `40`
- `--output <path>` to optionally persist the Markdown bundle
- automatic discovery of:
  - repo root and current branch via `git`
  - staged install dir using the same `target/universal/stage` vs legacy fallback used by `wave-smoke.sh`
  - current branch-scoped runtime config under `journal/runtime-config/`
  - current local-verification record under `journal/local-verification/` when present

- [ ] **Step 2: Make the diagnostics script resilient when the runtime is unhealthy**

The script should:
- probe `/`, `/healthz`, `/readyz`, and `/webclient/webclient.nocache.js` with curl and record raw HTTP statuses
- run `PORT=<port> bash scripts/wave-smoke.sh check`, capture both stdout/stderr and the exit code, and continue even when that command fails
- render missing log/config/evidence files as explicit `missing` lines instead of exiting
- emit one Markdown bundle that is ready to paste into an issue comment or PR summary

- [ ] **Step 3: Surface the new command from the existing worktree helper**

Update `scripts/worktree-boot.sh` to print the diagnostics command alongside the existing `start`, `check`, and `stop` commands so issue lanes discover it from the normal worktree boot flow:

```bash
Diagnostics:
  PORT=$PORT bash scripts/worktree-diagnostics.sh --port $PORT
```

- [ ] **Step 4: Re-run the shell test and verify it passes**

Run:
```bash
bash scripts/test-worktree-diagnostics.sh
```

Expected:
- the command exits `0`
- output ends with a `PASS: ...` line for the diagnostics script contract

## Task 3: Document The Diagnostics Flow

**Files:**
- Create: `docs/runbooks/worktree-diagnostics.md`
- Modify: `docs/runbooks/worktree-lane-lifecycle.md`
- Modify: `docs/runbooks/README.md`
- Modify: `docs/SMOKE_TESTS.md`

- [ ] **Step 1: Add the dedicated runbook**

Create `docs/runbooks/worktree-diagnostics.md` covering:
- when to run the diagnostics bundle
  - after failed startup/smoke
  - when an issue comment or PR summary needs more detail than raw command lines
- the default command:

```bash
PORT=9900 bash scripts/worktree-diagnostics.sh --port 9900
```

- the optional persisted-output form:

```bash
PORT=9900 bash scripts/worktree-diagnostics.sh --port 9900 \
  --output journal/local-verification/2026-04-12-issue-587-worktree-diagnostics-bundle.md
```

- the expected sections in the bundle and how to reference them from issue comments / PR summaries

- [ ] **Step 2: Cross-link the new runbook from the existing verification docs**

Update the existing runbooks/docs so they point to `docs/runbooks/worktree-diagnostics.md` as the bundled-detail path after `worktree-boot.sh` and `wave-smoke.sh`:
- `docs/runbooks/worktree-lane-lifecycle.md`
- `docs/runbooks/README.md`
- `docs/SMOKE_TESTS.md`

## Task 4: Verification And Evidence

**Files:**
- Create locally only if needed: `journal/local-verification/2026-04-12-issue-587-worktree-diagnostics-bundle.md`
- Create: `wave/config/changelog.d/2026-04-12-worktree-diagnostics-runbook.json`

- [ ] **Step 1: Run focused script verification**

Run:
```bash
bash scripts/test-worktree-diagnostics.sh
bash -n scripts/worktree-diagnostics.sh scripts/worktree-boot.sh
```

Expected:
- the contract test passes
- both scripts pass shell syntax validation

- [ ] **Step 2: Run the real worktree boot + smoke + diagnostics flow**

Run:
```bash
bash scripts/worktree-boot.sh --port 9904
PORT=9904 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-587-worktree-diagnostics-20260412/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-587-worktree-diagnostics-20260412/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9904 bash scripts/wave-smoke.sh check
PORT=9904 bash scripts/worktree-diagnostics.sh --port 9904 \
  --output journal/local-verification/2026-04-12-issue-587-worktree-diagnostics-bundle.md
PORT=9904 bash scripts/wave-smoke.sh stop
```

Expected:
- `worktree-boot.sh` prints the diagnostics command for the selected port
- the smoke check reports the expected root/health/webclient statuses
- the diagnostics bundle file is written and contains endpoint probes, smoke output, startup output tail, and server-log tail
- shutdown completes cleanly

- [ ] **Step 3: Validate the changelog artifact**

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- changelog assembly succeeds
- changelog validation passes

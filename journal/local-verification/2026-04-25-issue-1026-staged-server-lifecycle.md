# Issue #1026 Staged Server Lifecycle Verification

Date: 2026-04-25
Worktree: `/Users/vega/devroot/worktrees/issue-1026-staged-server-lifecycle`
Branch: `codex/issue-1026-staged-server-lifecycle`
Plan: `docs/superpowers/plans/2026-04-25-issue-1026-staged-server-lifecycle.md`

## Pre-Fix Failure

- `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py`: failed before the launch fix because `wave-smoke.sh start` printed `READY` and `Resolved server PID=90788`, then timed out after 20 seconds while stdout/stderr were captured.
- `sbt -batch smokeInstalled` through a 600-second Python subprocess timeout wrapper: reached `READY` and `Resolved server PID=88981`, but the `wave-smoke.sh start` shell remained alive as the Java server parent. After a manual `wave-smoke.sh stop`, SBT advanced to `check` and failed with `ROOT_STATUS=000`.
- First attempted `timeout 600 sbt -batch smokeInstalled` could not run because this macOS environment has no GNU `timeout`; verification used an equivalent Python subprocess timeout wrapper instead.
- First SBT attempt also required `python3 scripts/assemble-changelog.py` because `wave/config/changelog.json` was absent in this worktree.

## Passing Verification

- `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py`: passed, `1 passed in 3.57s`.
- `sbt -batch wave/compile`: passed, Java compile completed successfully.
- `sbt -batch smokeInstalled` through the 600-second Python subprocess timeout wrapper: passed in 39 seconds. Output included `READY`, `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `J2CL_ROOT_STATUS=200`, `SIDECAR_STATUS=200`, `WEBCLIENT_STATUS=200`, and `Stopped server on port 9898`.
- Staged source evidence: `target/universal/stage/lib/org.apache.wave.incubator-wave-0.1.0-SNAPSHOT.jar` contains `org/waveprotocol/box/server/ServerMain.class`.
- `bash scripts/worktree-boot.sh --port 9926`: passed and produced the staged runtime commands for this worktree.
- Worktree start/check sequence on port `9926`: `wave-smoke.sh start` printed `READY`, delayed `wave-smoke.sh check` passed, and `curl http://localhost:9926/?view=j2cl-root` returned `200`.
- Duplicate `wave-smoke.sh start` on port `9926`: stopped stale PID `96030`, started replacement PID `96245`, and `wave-smoke.sh check` passed again.
- Direct SIGTERM to recorded staged PID `96245`: the follow-up `wave-smoke.sh stop` was idempotent and reported `Stopped server on port 9926`.
- Final orphan check: `lsof -nP -iTCP:9926 -sTCP:LISTEN` produced no output; `test -z "$LSOF_OUTPUT"` passed.
- Shutdown hook evidence: `wave/src/main/java/org/waveprotocol/box/server/shutdown/ShutdownManager.java` registers itself with `Runtime.getRuntime().addShutdownHook(this)` on the first shutdown task registration, and the Jakarta `ServerMain` registers `server.stopServer()` before joining.

## Documentation Check

`rg -n "wave-smoke.sh start|wave-smoke.sh check|wave-smoke.sh stop|worktree-boot.sh" docs/SMOKE_TESTS.md docs/runbooks/browser-verification.md` showed existing docs already describe the same command shape. No docs update was needed.

## Review

- Claude Opus implementation review round 1: approved the target fix shape but requested stronger SIGTERM evidence and recommended duplicate-start/SIGTERM automation.
- Resolution: extended `scripts/tests/test_wave_smoke_lifecycle.py` to keep captured-output coverage, verify the first PID remains alive through `check`, exercise duplicate `start`, assert the old PID exits, check the replacement server, send direct SIGTERM to the recorded replacement PID, and assert the port is closed after SIGTERM/stop. Added a shell comment explaining the `disown` fallback.
- Claude Opus implementation review round 2: no blockers; requested closing merge-time comments around `ServerMain.run(...)` caller assumptions, blocking semantics, interrupt ordering, and PID re-resolution.
- Resolution: `rg -n 'ServerMain\.run\(|run\(coreSettings\)' wave scripts docs -S` showed the Jakarta `main(...)` path is the only in-repo caller. Added a `run(...)` Javadoc note that the method blocks until shutdown, moved interrupt-flag restoration after `server.stopServer()`, and changed PID re-resolution to iterate sorted unique port PIDs filtered through `is_wave_process(...)` instead of taking `head -1`.
- Final post-round-2 verification: `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py` passed (`1 passed in 7.41s`), `sbt -batch wave/compile` passed, and `PORT=9926 sbt -batch smokeInstalled` through the 600-second Python subprocess timeout wrapper passed in 35 seconds after the prior `worktree-boot.sh --port 9926` run left the staged runtime config bound to port `9926`. Both `lsof -nP -iTCP:9898 -sTCP:LISTEN` and `lsof -nP -iTCP:9926 -sTCP:LISTEN` produced no output afterward.
- Claude Opus implementation review round 3: verdict approve with no blockers and only optional test/nit suggestions.
- Resolution: tightened the optional items too. The pytest now asserts `wave_server.pid` matches the listener PID, confirms `stop` removes the PID file, and covers the non-Wave-port-owner refusal branch. Minor wording/style nits were addressed in `ServerMain` and `wave-smoke.sh`.
- Final post-round-3 verification: `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py` passed (`2 passed in 7.99s`), `sbt -batch wave/compile` passed, and `PORT=9926 sbt -batch smokeInstalled` through the 600-second Python subprocess timeout wrapper passed in 37 seconds with `ROOT_STATUS=200`, `J2CL_ROOT_STATUS=200`, `SIDECAR_STATUS=200`, `WEBCLIENT_STATUS=200`, and `Stopped server on port 9926`.
- Claude Opus final implementation review: verdict ready to ship with no blockers, no important concerns, no minor nits, and no missing tests or required follow-ups.
- PR review follow-up: addressed Copilot comments by simplifying `disown`, removing the dead `IOException` catch/throws path around `stopServer()`, adding `lsof`/`ss`/`fuser` fallback handling to the Python listener PID helper, and dropping the unused `tmp_path` parameter from `run_smoke(...)`.
- Post-Copilot-fix verification: `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py` passed (`2 passed in 8.05s`), `bash -n scripts/wave-smoke.sh && python3 -m py_compile scripts/tests/test_wave_smoke_lifecycle.py && git diff --check` passed, and `sbt -batch wave/compile` passed.

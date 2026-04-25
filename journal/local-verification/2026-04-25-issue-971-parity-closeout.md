# Local Verification

- Branch: codex/issue-971-parity-closeout
- Worktree: /Users/vega/devroot/worktrees/codex-issue-971-parity-closeout
- Date: 2026-04-25
- Issue: #971
- Note: `scripts/worktree-boot.sh` would have generated `2026-04-25-branch-issue-971-parity-closeout.md` for branch `codex/issue-971-parity-closeout`; this committed evidence file was renamed afterward to `2026-04-25-issue-971-parity-closeout.md`.

## Commands

- `bash scripts/worktree-boot.sh --port 9971`
- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild`
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`
- `git diff --cached --check`
- `git diff --check`
- `PORT=9971 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/codex-issue-971-parity-closeout/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/codex-issue-971-parity-closeout/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
- `PORT=9971 bash scripts/wave-smoke.sh check`
- Immediate staged-server route probe while the process was alive:
  - `GET /`
  - `GET /?view=j2cl-root`
  - `GET /j2cl/index.html`
  - `GET /j2cl-search/sidecar/j2cl-sidecar.js`
  - `GET /webclient/webclient.nocache.js`

## Results

- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild`: passed.
  - J2CL search test completed successfully.
  - J2CL production build completed successfully.
  - Lit tests passed: 23 files, 110 tests, 0 failures.
  - Lit build completed.
  - Note: the SBT J2CL build phase emitted a Vertispan `DiskCacheThread` `RejectedExecutionException` after a success line, then continued through the remaining tasks and exited 0. Follow-up #1027 tracks whether this is benign cache-shutdown noise or a repo-owned build hygiene issue.
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`: passed; assembled 255 entries and validation passed.
- `git diff --cached --check`: passed.
- `git diff --check`: passed.
- `bash scripts/worktree-boot.sh --port 9971`: passed; staged GWT and J2CL assets, wrote the port-specific runtime config, and created this evidence file.
- `wave-smoke.sh start`: reported `PROBE_HTTP=200`, `READY`, and resolved a server PID.
- `wave-smoke.sh check`: failed because the staged server process exited after readiness before the check command could connect.
- `scripts/worktree-diagnostics.sh --port 9971`: confirmed all endpoint probes returned `000` after the process exit, while startup logs showed Jetty had started on `127.0.0.1:9971` and served one root request.
- Immediate route probe while the staged server was alive passed:
  - `ROOT_STATUS=200`
  - `ROOT_GWT=present`
  - `J2CL_ROOT_STATUS=200`
  - `J2CL_ROOT_SHELL=present`
  - `J2CL_INDEX_STATUS=200`
  - `SIDECAR_STATUS=200`
  - `WEBCLIENT_STATUS=200`

## Runtime Limitation

The local staged server did not remain alive long enough for manual browser
verification. The observed runtime behavior is consistent with
`ServerMain.run(...)` returning immediately after
`server.startWebSocketServer(injector)` in the Jakarta entry point, with no
blocking `join()` call in the started server path. This was recorded as a
closeout limitation rather than treated as an attachment/rich-edit regression:
the immediate route probe proved the default GWT root, explicit J2CL root,
production J2CL asset, sidecar asset, and legacy webclient asset were all
served while the staged process was alive.

## Gate Status

This record does not claim final browser-harness closure for R-5.6 or R-5.7.
The daily attachment/rich-edit implementation slices are merged and their
targeted SBT/Lit/CI evidence is recorded in #971, but the persistent local
browser-smoke path remains blocked by #1026 and the shared telemetry channel
work remains tracked in #1025. Keep #971 open until those follow-ups are either
completed or explicitly accepted as non-blocking by the #904 tracker.

## Follow-Up

- Issue: #971.
- Follow-up #1026: repair the local staged-server lifecycle or smoke harness
  so browser verification can run against a persistent process.
- Follow-up #1025: wire #971 attachment/rich-edit telemetry into the shared
  J2CL client stats channel.
- Follow-up #1027: classify and, if repo-owned, fix the Vertispan
  `DiskCacheThread` exception emitted by successful SBT J2CL build runs.

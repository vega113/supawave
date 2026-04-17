Status: Current
Owner: Project Maintainers
Updated: 2026-04-10
Review cadence: quarterly

# Smoke Tests Summary

Date: 2026-04-10
Environment:
- OS: macOS / Linux
- Shell: zsh / bash
- Java: JDK 17
- Build: SBT 1.10+
- Server profile: Jakarta-only (Jetty 12 EE10)

## Automated smoke script

The preferred smoke path uses `scripts/wave-smoke.sh` against the staged
distribution:

1. Build: `sbt Universal/stage`
2. Start: `bash scripts/wave-smoke.sh start` (waits for HTTP readiness)
3. Check: `bash scripts/wave-smoke.sh check`
   - Expected: `ROOT_STATUS=302`, `WEBCLIENT_STATUS=200`
4. Stop: `bash scripts/wave-smoke.sh stop`

For issue worktrees, use
[docs/runbooks/worktree-lane-lifecycle.md](runbooks/worktree-lane-lifecycle.md)
to prepare the staged app and runtime config first. If port `9898` is busy,
rerun `bash scripts/worktree-boot.sh --port 9899` and then carry the same
override into the smoke commands:

1. `PORT=9899 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
2. `PORT=9899 bash scripts/wave-smoke.sh check`
3. `curl -sS http://localhost:9899/healthz`
4. `PORT=9899 bash scripts/wave-smoke.sh stop`

If the start/check flow fails or the issue/PR needs richer runtime detail, run
`PORT=9899 bash scripts/worktree-diagnostics.sh --port 9899` and follow
[docs/runbooks/worktree-diagnostics.md](runbooks/worktree-diagnostics.md) for
the bundled evidence format.

## Browser verification baseline

The standalone browser-smoke baseline remains:

- `bash scripts/wave-smoke-ui.sh`
- `sbt smokeUi`

For issue worktrees, the port-aware equivalent is the worktree lifecycle above:
`worktree-boot.sh` plus `wave-smoke.sh start|check|stop`.

Use [docs/runbooks/browser-verification.md](runbooks/browser-verification.md)
for the default UI-affecting verification flow, and use
[docs/runbooks/change-type-verification-matrix.md](runbooks/change-type-verification-matrix.md)
to decide whether the change also needs a real browser pass after the smoke
checks succeed.

## SBT-level smoke

- `sbt wave/compile` -- passes.
- `sbt smokeInstalled` -- stages and runs smoke tests.

## Build artifacts

- PST JAR under `pst/target/`
- Wave fat JAR via `sbt assembly`; staged distribution at `target/universal/stage/`

## Docker

Build the image (multi-stage, Java 17, Jakarta-only):

    docker build -t wave:dev .

Run (HTTP on 9898):

    docker run --rm -p 9898:9898 wave:dev

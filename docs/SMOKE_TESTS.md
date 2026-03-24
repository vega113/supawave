# Smoke Tests Summary

Date: 2026-03-24
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

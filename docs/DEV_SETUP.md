Status: Current
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Developer Setup (JDK 17, SBT, Protobuf)

This guide helps you build and run the Apache Wave server on JDK 17.

Prerequisites
- Java: JDK 17 installed
  - Suggested: sdkman
    - curl -s "https://get.sdkman.io" | bash
    - sdk install java 17.0.12-zulu
- SBT 1.10+ installed
  - See https://www.scala-sbt.org/download.html
- Git
- macOS or Linux shell

Notes
- SBT will use Java toolchains to compile with JDK 17 automatically for Java modules.
- Protobuf compiler (protoc) is provided via the sbt-protoc plugin; no system install needed.

Quick start (server only)
- From repo root:
  - sbt pst/compile wave/compile
  - sbt Universal/stage
  - ./target/universal/stage/bin/wave
- Visit http://localhost:9898/

Existing-worktree lane bootstrap
- For GitHub issue lanes, create the worktree first and then follow
  [docs/runbooks/worktree-lane-lifecycle.md](runbooks/worktree-lane-lifecycle.md).
- The standard lane prep command is `bash scripts/worktree-boot.sh --port 9899`.
- The helper stages the app, creates a port-specific runtime config, and
  initializes the gitignored local-verification record under
  `journal/local-verification/`.

Doc guardrails
- Two shell scripts enforce doc quality and run in CI on every PR:
  - `bash scripts/check-doc-links.sh` — checks for broken markdown links under `docs/`.
  - `bash scripts/check-doc-freshness.sh` — checks that covered docs listed in
    `docs/DOC_REGISTRY.md` have required owner/freshness metadata.
- Run both locally before pushing doc changes.
- See [`docs/runbooks/doc-guardrails.md`](runbooks/doc-guardrails.md) for details.

Troubleshooting
- If SBT cannot find a suitable JDK, ensure JAVA_HOME is set to a JDK 17 and/or install via sdkman.
- For protobuf errors, ensure you ran `sbt pst/assembly` before `sbt wave/compile` if building tasks individually.

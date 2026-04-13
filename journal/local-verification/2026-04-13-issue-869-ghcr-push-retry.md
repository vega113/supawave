Worktree: /Users/vega/devroot/worktrees/vega113-incubator-wave-pr-870-monitor
Branch: codex/ghcr-push-retry-869
PR: https://github.com/vega113/supawave/pull/870
Plan: N/A (PR remediation batch)

Changes:
- Streamed `docker push` output through `tee` while preserving the retry helper log file for transient-error classification.
- Added a regression test that proves `docker-push-with-retry.sh` emits push output before the mocked push process exits.

Verification:
- `python3 -m unittest scripts.tests.test_docker_push_with_retry`
  - red: failed before the production fix with `_queue.Empty` because no push output was visible before the fake `docker push` exited
  - green: passed after the streaming fix
- `sbt compile`
  - passed
- `sbt test`
  - passed (`2268` tests, `0` failed)

Review remediation:
- Addressed the open Codex review thread on `scripts/docker-push-with-retry.sh` by restoring live `docker push` progress output so GitHub Actions does not sit silent during long layer uploads.
- Guarded the new `tee` pipeline by checking `PIPESTATUS` so a log-write failure still fails the helper explicitly instead of being mistaken for a successful push.

# Issue #1025 J2CL Telemetry Verification

Worktree: `/Users/vega/devroot/worktrees/codex-issue-1025-j2cl-telemetry`
Branch: `codex/issue-1025-j2cl-telemetry`

## Commands

- `sbt -batch j2clSearchTest` - pass after telemetry sink, attachment upload telemetry, attachment metadata telemetry, rich-edit telemetry, read-surface click telemetry, and root-shell wiring slices.
- `sbt -batch j2clProductionBuild j2clLitTest j2clLitBuild` - pass; Lit suite reported 23 test files, 110 passed, 0 failed.
- `python3 scripts/assemble-changelog.py` - pass; assembled 256 entries into `wave/config/changelog.json`.
- `python3 scripts/validate-changelog.py` - pass.
- `git diff --check` - pass.
- `sbt -batch j2clSearchTest` - pass after Claude review follow-up commit `7b4813fa6` aligned read-surface click telemetry with `source=read-surface`.
- `sbt -batch j2clProductionBuild j2clLitTest j2clLitBuild` - pass after Claude review follow-up; Lit suite reported 23 test files, 110 passed, 0 failed.
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py && git diff --check` - pass after Claude review follow-up.

## Notes

- Verification uses SBT entry points only. The SBT task owns any internal J2CL wrapper execution.
- Claude Opus review round 1 returned `pass-with-followups`; required fix was to add and assert `source=read-surface` on `attachment.open.clicked` and `attachment.download.clicked`.
- Claude Opus review round 2 returned `pass-with-followups`; the browser listener concern is already covered by `J2clClientTelemetryTest.browserStatsSinkSwallowsListenerException`, which installs a throwing `window.__stats_listener`, records through `J2clClientTelemetry.browserStatsSink()`, and asserts `window.__stats` still receives the event.
- The reserved-key plan text was updated after round 2 to explicitly include `surface` and `category`, matching the implemented sanitizer and test coverage.
- Failure-reason coverage is implemented in `J2clAttachmentComposerControllerTest` and `J2clSelectedWaveControllerTest`: upload covers `network`, `forbidden`, `server`, `unsupported-file`, `cancelled`, `validation`, and `client-error`; metadata covers `network`, `forbidden`, `server`, `metadata`, `validation`, and `client-error`.
- Claude Opus final review returned `pass` with `0` blockers, `0` important concerns, `0` coverage gaps, `0` open items, and `0` required follow-ups.

# Issue 1209 - SBT-only default build path

Date: 2026-05-08
Worktree: `/Users/vega/devroot/worktrees/codex-sbt-only-build-20260508`
Branch: `codex/sbt-only-build-20260508`
Issue: https://github.com/vega113/supawave/issues/1209
Plan: `docs/superpowers/plans/2026-05-08-sbt-only-default-build.md`

## Scope

Default app entrypoints now stay on the SBT/GWT path:

- `Compile / run`
- `Universal / stage`
- `Universal / packageBin`

The Maven-backed J2CL sidecar remains available only through explicit SBT tasks
such as `j2clRuntimeBuild`. The J2CL parity workflow opts into that task before
running browser parity.

## Review

- Plan review round 1: changes requested; tightened the plan with task-graph
  evidence and smoke semantics.
- Plan review round 2: approved with small changes; applied before
  implementation.
- Implementation review round 1: changes requested; added changelog, explicit
  `packageBin` and `Compile/run` graph checks, CI parity opt-in, and smoke docs.
- Implementation review round 2: pass; addressed follow-up hardening by making
  the Java contract test reject any `j2cl` dependency on default task lines and
  making CI smoke require J2CL assets in the parity lane.

## Verification

- `git diff --check` - passed.
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --changelog wave/config/changelog.json` - passed.
- `sbt --batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"` - passed; 3 tests.
- `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py -q` - passed; 3 tests.
- `sbt --batch "inspect tree Universal/stage"` plus `grep -F "j2clRuntimeBuild"` - passed; no dependency found.
- `sbt --batch "inspect tree Universal/packageBin"` plus `grep -F "j2clRuntimeBuild"` - passed; no dependency found.
- `sbt --batch "inspect tree Compile/run"` plus `grep -F "j2clRuntimeBuild"` - passed; no dependency found.
- `sbt --batch Universal/stage` - passed.
- `sbt --batch Universal/packageBin` - passed.
- `sbt --batch j2clRuntimeBuild` - passed.

## Notes

`sbt --batch j2clRuntimeBuild` still reports the existing npm audit warning from
the Lit install path when dependencies are installed. That warning is outside
this SBT-default-build decoupling scope; the explicit J2CL build exits 0.

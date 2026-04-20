# Issue 923 / PR 939 remediation verification

- Date: 2026-04-20
- Worktree: `/Users/vega/devroot/worktrees/vega113-incubator-wave-pr-939-monitor`
- Branch: `monitor/vega113-incubator-wave/pr-939`
- PR: `#939`

## Commands

```bash
sbt -batch "testOnly org.waveprotocol.box.server.persistence.memory.FeatureFlagSeederJ2clBootstrapTest" "jakartaTest:testOnly org.waveprotocol.box.server.ServerMainConfigOverrideTest"
sbt -batch compile
sbt -batch test
```

## Results

- Follow-up runbook remediation: updated Mode B to copy the `scripts/worktree-boot.sh --port 9914` runtime config from `journal/runtime-config/*-port-9914.application.conf` before enabling `ui.j2cl_root_bootstrap_enabled`, so the documented override keeps the staged server on port 9914.
- Focused regression checks passed after adding:
  - coverage that explicit override config can still reconcile `j2cl-root-bootstrap`
  - coverage that omitting the key from override config preserves the stored flag value
  - coverage that `loadCoreConfig()` rejects a missing `-Dwave.server.config` path
- `sbt -batch compile` passed.
- `sbt -batch test` passed on retry.
  - First run had one unrelated failure in `org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistryConcurrencyTest.concurrentGetsAndPutsDoNotThrow` (`LRU size should be <= maxEntries`).
  - Immediate isolated rerun of that test passed.
  - Full `sbt -batch test` retry passed with `Passed: Total 2538, Failed 0, Errors 0, Passed 2536, Skipped 2`.
- Fresh post-remediation reruns also passed:
  - `sbt -batch compile`
  - `sbt -batch test` with `Passed: Total 2538, Failed 0, Errors 0, Passed 2536, Skipped 2`

# Issue #903 J2CL Pure-Logic Preparation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `wave/model/**` and `wave/concurrencycontrol/**` a clean pure-Java migration target before any UI slice moves to J2CL by removing the last GWT dependencies from the concurrency-control core and moving correctness-critical tests onto plain JVM execution where feasible.

**Architecture:** The live tree already has `wave/model/**` free of direct `com.google.gwt.*` imports, and `WaveContext` already uses the shared `org.waveprotocol.wave.model.document.BlipReadStateMonitor` contract. The remaining pure-logic contamination is concentrated in `ViewChannelImpl` and `ClientStatsRawFragmentsApplier`, where browser-only logging, timing, and HTTP stats reporting leaked into the OT/concurrency-control seam. The fix is to keep OT semantics unchanged while pushing browser-edge behavior behind explicit adapters or client-only wiring, then remove or replace the remaining dead GWT test harness files in the model/concurrencycontrol test tree.

**Tech Stack:** Java, SBT, JUnit 3/4, ripgrep, existing worktree boot/smoke scripts, manual browser sanity verification.

---

## 1. Goal / Root Cause

Issue #903 exists because the shared model / OT core is almost, but not fully, pure Java:

- `wave/src/main/java/org/waveprotocol/wave/model/**` is already clean of direct `com.google.gwt.*` imports in the live tree, so this issue is no longer about reopening `WaveContext` or redoing the `BlipReadStateMonitor` extraction.
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImpl.java` still calls `com.google.gwt.core.client.GWT.log(...)` directly from channel code.
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ClientStatsRawFragmentsApplier.java` still mixes `RequestBuilder`, `RequestCallback`, `Response`, `URL`, `Duration`, debug toasts, and counter posting into a class that currently lives under `wave/concurrencycontrol/**`.
- Legacy GWT test harness files still exist under `wave/src/test/java/org/waveprotocol/wave/model/**`, `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/**`, and matching `.gwt.xml` resources even though most correctness-critical suites in those trees already run as plain JVM tests.

The root cause is architectural leakage: browser-edge debug/reporting behavior and historical GWT harness scaffolding were allowed to live inside packages that now need to be compiler-safe pure logic.

## 2. Scope And Non-Goals

### In Scope

- Remove direct `com.google.gwt.*` usage from `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/**`.
- Keep `wave/src/main/java/org/waveprotocol/wave/model/**` at zero direct `com.google.gwt.*` imports.
- Preserve existing OT / transform / view-channel semantics while extracting browser-edge behavior behind explicit seams.
- Move correctness-critical model and concurrency-control coverage onto plain JVM execution where feasible.
- Delete or replace dead GWT wrapper harness files under model/concurrencycontrol test trees when they are no longer needed.
- Document deferred editor/browser-edge exclusions explicitly in the issue and PR record.

### Explicit Non-Goals

- No editor DOM migration.
- No `wave/src/main/java/org/waveprotocol/wave/client/editor/**` migration.
- No search-panel or other UI-slice migration.
- No `WebClient` / `StageThree` / app-shell cutover.
- No transport-stack rewrite beyond the minimal wiring needed to keep browser-only applier behavior out of `wave/concurrencycontrol/**`.
- No semantic changes to OT, delta submission, fragment application, or wave/model behavior.

### Deferred Editor / Browser-Edge Exclusions

These stay out of issue #903 and should be called out explicitly in the issue/PR notes when implementation lands:

- `wave/src/test/java/org/waveprotocol/wave/client/editor/**`
- `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/**`
- `wave/src/test/java/org/waveprotocol/wave/client/scheduler/**`
- `wave/src/test/java/org/waveprotocol/wave/client/util/**`
- Any remaining `GWTTestCase` or `.gwt.xml` coverage whose purpose is browser DOM, editor integration, widget rendering, or client runtime behavior rather than pure model / concurrency correctness

## 3. Exact Files Likely To Change

### Production Files

- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImpl.java`
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ClientStatsRawFragmentsApplier.java`
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannel.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

### High-Probability Test Files

- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelApplierWiringTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelApplierIntegrationTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ApplierClampTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RawFragmentsApplierTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RawFragmentsApplierHistoryTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RealRawFragmentsApplierTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RealRawFragmentsApplierConcurrencyTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/FragmentRequesterTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/FragmentRequesterMetricsTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/OperationChannelImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/OperationChannelMultiplexerImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/WaveletDeltaChannelImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/client/ClientAndServerTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/client/OperationQueueTest.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/client/OT3Test.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/wave/CcBasedWaveViewTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/conversation/WaveBasedConversationViewTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/supplement/PrimitiveSupplementWithPrimitiveSupplementImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/supplement/PrimitiveSupplementWithWaveletBasedSupplementTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/wave/opbased/ObservableWaveletWithOpBasedWaveletTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/wave/opbased/OpBasedWaveletWaveletTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/wave/opbased/OpBasedWaveletWithWaveletDataImplTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/wave/opbased/WaveletDataWithWaveletDataImplTest.java`

### GWT Harness Cleanup Candidates

- `wave/src/test/java/org/waveprotocol/wave/model/TestBase.java`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/TestBase.java`
- `wave/src/test/java/org/waveprotocol/wave/model/testing/GenericGWTTestBase.java`
- `wave/src/test/resources/org/waveprotocol/wave/model/tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/model/supplement/tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/concurrencycontrol/Tests.gwt.xml`

## 4. Concrete Task Breakdown

### Task 1: Freeze The Live Scope And Do Not Reopen Already-Landed Model Cleanup

**Files:**
- Inspect only: `docs/superpowers/plans/j2cl-full-migration-plan.md`
- Inspect only: `docs/j2cl-gwt3-decision-memo.md`
- Inspect only: `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java`
- Inspect only: `wave/src/main/java/org/waveprotocol/wave/model/document/BlipReadStateMonitor.java`

- [ ] Confirm the live-tree baseline before any code changes:
  - `wave/model/**` has no direct `com.google.gwt.*` imports.
  - `WaveContext` already depends on the shared model-package `BlipReadStateMonitor`.
  - only the two known `wave/concurrencycontrol/**` files still import GWT.
- [ ] Record in the issue comment and PR notes that #903 is now narrower than sections 4.1-4.4 of the older full migration plan:
  - `WaveContext` dependency inversion is already done in the live tree.
  - this issue is focused on concurrency-control contamination plus test-harness cleanup.

### Task 2: Peel Browser Logging And Reporting Out Of `ViewChannelImpl`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImpl.java`
- Modify as needed: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannel.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImplTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelApplierWiringTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelApplierIntegrationTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/ApplierClampTest.java`

- [ ] Replace direct `GWT.log(...)` usage in `ViewChannelImpl` with an explicit pure-Java seam:
  - prefer existing `LoggerBundle` where possible;
  - if a new hook is required, keep it small and package-local to `wave/concurrencycontrol/channel/**`;
  - do not introduce a new dependency from the core back into `wave/client/**`.
- [ ] Keep fragment-fetch and fragment-apply behavior best-effort exactly as it is today:
  - no change to delivery order;
  - no change to failure swallowing behavior;
  - no change to channel state transitions;
  - no change to delta / snapshot handling.
- [ ] Update existing channel tests so they prove behavior is unchanged after the seam extraction.

### Task 3: Move Browser-Only Applier Behavior Out Of `wave/concurrencycontrol/**`

**Files:**
- Modify or move: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ClientStatsRawFragmentsApplier.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RawFragmentsApplierTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RawFragmentsApplierHistoryTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RealRawFragmentsApplierTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RealRawFragmentsApplierConcurrencyTest.java`

- [ ] Split pure fragment-application semantics from browser-only concerns:
  - HTTP POSTing of counters;
  - wall-clock / throttle timing;
  - URL encoding;
  - client debug indicator updates;
  - dev toast reporting.
- [ ] End state requirement:
  - `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/**` contains no `com.google.gwt.*` imports;
  - any browser-only implementation lives outside the pure-logic package or behind a pure interface with browser wiring owned by client code.
- [ ] Rewire `StageTwo` only as much as needed to install the browser-specific adapter from the client side.
- [ ] Do not widen this into the larger transport rewrite tracked elsewhere.

### Task 4: Remove Or Replace Dead GWT Harnesses In Model / Concurrency Tests

**Files:**
- Modify or delete: `wave/src/test/java/org/waveprotocol/wave/model/TestBase.java`
- Modify or delete: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/TestBase.java`
- Modify or delete: `wave/src/test/java/org/waveprotocol/wave/model/testing/GenericGWTTestBase.java`
- Modify or delete: `wave/src/test/resources/org/waveprotocol/wave/model/tests.gwt.xml`
- Modify or delete: `wave/src/test/resources/org/waveprotocol/wave/model/supplement/tests.gwt.xml`
- Modify or delete: `wave/src/test/resources/org/waveprotocol/wave/concurrencycontrol/Tests.gwt.xml`

- [ ] Audit whether these harness files still have any live model/concurrency descendants that matter for pure-logic correctness.
- [ ] If a harness is dead, delete it and its matching `.gwt.xml` resource.
- [ ] If a harness still carries useful correctness tests, replace it with plain JUnit scaffolding inside the same package instead of keeping `GWTTestCase`.
- [ ] Leave editor/browser-edge `GWTTestCase` coverage alone and document the exclusion instead of half-migrating it.

### Task 5: Prove The Pure-Logic Target On The JVM And Record The Deferred Edge Cases

**Files:**
- Verify: files listed above
- Record only: issue comment and PR body

- [ ] Run the import gates until `wave/model/**` and `wave/concurrencycontrol/**` are free of direct `com.google.gwt.*`.
- [ ] Run the targeted JVM model and concurrency suites listed below.
- [ ] Record which browser-edge tests remain intentionally deferred and why.
- [ ] Keep the issue and PR traceability aligned with worktree path, branch, plan path, commands, and results.

## 5. Exact Verification Commands

### GWT Import Gates

Run these first and again before PR:

```bash
rg -n "com\\.google\\.gwt\\." \
  wave/src/main/java/org/waveprotocol/wave/model \
  wave/src/main/java/org/waveprotocol/wave/concurrencycontrol
```

Expected result: no output.

```bash
rg -n "GWTTestCase|com\\.google\\.gwt\\." \
  wave/src/test/java/org/waveprotocol/wave/model \
  wave/src/test/java/org/waveprotocol/wave/concurrencycontrol \
  wave/src/test/resources/org/waveprotocol/wave/model \
  wave/src/test/resources/org/waveprotocol/wave/concurrencycontrol
```

Expected result: no output for the migrated model/concurrency scope.

### Targeted Concurrency-Control JVM Tests

```bash
sbt -batch \
  "testOnly org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelImplTest \
  org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelApplierWiringTest \
  org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelApplierIntegrationTest \
  org.waveprotocol.wave.concurrencycontrol.channel.ApplierClampTest \
  org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplierTest \
  org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplierHistoryTest \
  org.waveprotocol.wave.concurrencycontrol.channel.RealRawFragmentsApplierTest \
  org.waveprotocol.wave.concurrencycontrol.channel.RealRawFragmentsApplierConcurrencyTest"
```

```bash
sbt -batch \
  "testOnly org.waveprotocol.wave.concurrencycontrol.channel.FragmentRequesterTest \
  org.waveprotocol.wave.concurrencycontrol.channel.FragmentRequesterMetricsTest \
  org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelImplTest \
  org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexerImplTest \
  org.waveprotocol.wave.concurrencycontrol.channel.WaveletDeltaChannelImplTest \
  org.waveprotocol.wave.concurrencycontrol.client.ClientAndServerTest \
  org.waveprotocol.wave.concurrencycontrol.client.OperationQueueTest \
  org.waveprotocol.wave.concurrencycontrol.client.OT3Test \
  org.waveprotocol.wave.concurrencycontrol.wave.CcBasedWaveViewTest"
```

### Targeted Model JVM Tests

```bash
sbt -batch \
  "testOnly org.waveprotocol.wave.model.wave.opbased.ObservableWaveletWithOpBasedWaveletTest \
  org.waveprotocol.wave.model.wave.opbased.OpBasedWaveletWaveletTest \
  org.waveprotocol.wave.model.wave.opbased.OpBasedWaveletWithWaveletDataImplTest \
  org.waveprotocol.wave.model.wave.opbased.WaveletDataWithWaveletDataImplTest \
  org.waveprotocol.wave.model.conversation.WaveBasedConversationViewTest \
  org.waveprotocol.wave.model.supplement.PrimitiveSupplementWithPrimitiveSupplementImplTest \
  org.waveprotocol.wave.model.supplement.PrimitiveSupplementWithWaveletBasedSupplementTest"
```

### Repo Compile Gates

```bash
sbt -batch compile test:compile
```

If implementation unexpectedly adds a changelog fragment, also run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

That changelog path is not expected for #903 unless the scope drifts into user-visible behavior.

## 6. Required Local Verification Before PR Creation

Per repo runbooks and the user’s explicit requirement, do not open a PR until compile/tests are green **and** a local staged boot plus browser sanity pass has been recorded from the issue worktree on a non-conflicting port.

For reusable command examples below:

- `$WORKTREE` is the issue worktree root.
- `$REPO_ROOT` is the main repo checkout used as the shared file-store source.

### Local Worktree Boot

If the lane needs existing local data to open a real wave, wire the shared file-store first:

```bash
scripts/worktree-file-store.sh --source "$REPO_ROOT"
```

Then prepare the lane on a non-conflicting port:

```bash
bash scripts/worktree-boot.sh --port 9900
```

If `9900` is occupied, rerun with another free port, for example:

```bash
bash scripts/worktree-boot.sh --port 9901
```

After `worktree-boot.sh` prints the exact commands, run the printed start/check/stop commands exactly as emitted. The expected sequence is:

```bash
PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
PORT=9900 bash scripts/wave-smoke.sh stop
```

### Required Browser Sanity Path

Because the issue touches shared model / concurrency behavior and the user explicitly requires browser sanity before PR:

- open `http://localhost:9900/` (or the alternate chosen port);
- sign in with the normal local test flow if needed;
- load an existing wave or conversation;
- confirm the page shell loads and the wave opens without obvious `ViewChannel` / fragment-application / OT bootstrap regression;
- record the exact route checked and the observed result in `journal/local-verification/<date>-issue-903-j2cl-pure-logic.md` and in the linked GitHub Issue comment.

This browser pass stays intentionally narrow. It is not a request for editor migration or broad UI exploration.

## 7. Acceptance Criteria

- `rg -n "com\\.google\\.gwt\\." wave/src/main/java/org/waveprotocol/wave/model wave/src/main/java/org/waveprotocol/wave/concurrencycontrol` returns no matches.
- `ViewChannelImpl` no longer imports or directly calls GWT APIs.
- Any browser-only fragment-applier stats/reporting behavior is owned by client-side wiring, not by `wave/concurrencycontrol/**`.
- OT semantics are unchanged:
  - no regression in targeted view-channel, operation-channel, OT3, and model suites;
  - no deliberate transform or channel-lifecycle behavior change.
- Dead GWT test harness files in model/concurrencycontrol are either removed or replaced with plain JVM scaffolding.
- Deferred editor/browser-edge exclusions are documented explicitly instead of being silently left behind.
- `sbt -batch compile test:compile` passes.
- Required worktree boot, smoke, and browser sanity evidence is recorded before PR creation.

## 8. Issue / PR Traceability Notes

Keep the issue comment and PR summary aligned with `docs/github-issues.md`:

- Worktree: `<WORKTREE>`
- Branch: `issue-903-j2cl-pure-logic`
- Plan path: `docs/superpowers/plans/2026-04-19-issue-903-j2cl-pure-logic.md`
- Record the exact import-gate commands, targeted `sbt testOnly` commands, compile gate, and worktree boot/browser verification commands with pass/fail results.
- Record commit SHAs and one-line summaries as implementation proceeds.
- Summarize review findings and how each was addressed before resolving review conversations.
- Explicitly note the deferred browser-edge exclusions so reviewers do not assume editor/wavepanel/client GWT tests were supposed to move in this issue.
- Add the PR number/URL back to the issue comment once the branch is ready.

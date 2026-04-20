# Issue #925 Retire Legacy GWT Client Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** retire the legacy GWT browser client bootstrap and packaging path now that `#940` made the J2CL root shell the default runtime, without silently dropping the remaining browser-harness descendants or public landing-page behavior.

**Architecture:** treat this as a removal-and-simplification slice, not another migration prototype. The plan first freezes explicit accounting for every remaining `compileGwt` dependency and browser-harness descendant, then removes the legacy authenticated GWT webclient runtime (`renderWaveClientPage(...)`, `/webclient/*`, `webclient.nocache.js`, and `compileGwt` packaging wiring), and finally updates smoke/docs so the maintained browser runtime is only the J2CL path. The public landing page at `/?view=landing` is a separate product surface and is not the same thing as the retired authenticated GWT client.

**Tech Stack:** SBT 1.10, Jakarta servlet stack, J2CL production build, shell smoke scripts, GitHub issue traceability.

---

## Current State Summary

Post-`#940`, the repo still has two distinct browser-era contracts:

1. The maintained J2CL runtime:
   - `/` defaults to the J2CL root shell.
   - `/?view=j2cl-root` is still the explicit diagnostic alias.
   - `j2clProductionBuild` and `j2clSearchBuild` produce the maintained browser artifacts.

2. The legacy GWT runtime and packaging seam that issue `#925` must retire:
   - `Compile / run` still depends on `compileGwt`.
   - `Universal / stage` and `Universal / packageBin` still depend on `compileGwt` and `verifyGwtAssets`.
   - `HtmlRenderer.renderWaveClientPage(...)` still emits `webclient/webclient.nocache.js`.
   - `ServerRpcProvider` still mounts `/webclient/*`.
   - `scripts/wave-smoke.sh` and `scripts/wave-smoke-ui.sh` still treat `/webclient/webclient.nocache.js` as a required asset.
   - The temporary rollback control plane from `#923/#924` (`j2cl-root-bootstrap`) still exists in `WaveClientServlet`, `FeatureFlagSeeder`, `KnownFeatureFlags`, and `reference.conf`.

The browser-harness debt is also still live and must be explicitly accounted for before `compileGwt` leaves the build:

- `19` direct `GWTTestCase` suites remain.
- `11` inherited browser-era descendants still sit under editor/test-base parents.
- Additional Jakarta/client holdouts still excluded because they reference GWT/client/webclient classes:
  - `WaveWebSocketClientTest`
  - `RemoteWaveViewServiceEmptyUserDataSnapshotTest`
  - `FocusBlipSelectorTest`
  - `BlipMetaDomImplTest`

## Acceptance Criteria Mapped To Concrete Seams

1. The repo no longer depends on the legacy GWT client path for packaging.
   - `build.sbt`
   - `scripts/wave-smoke.sh`
   - `scripts/wave-smoke-ui.sh`
   - `docs/BUILDING-sbt.md`
   - `docs/current-state.md`
   - `docs/runbooks/j2cl-sidecar-testing.md`

2. Obsolete bootstrap/module code for the authenticated GWT client is deleted.
   - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
   - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
   - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
   - any tests that still assert `webclient/webclient.nocache.js`

3. The supported browser runtime is the J2CL client.
   - `/` must still boot the J2CL root shell.
   - `/?view=j2cl-root` may remain as a diagnostic alias if it is still useful, but it must point to the same maintained J2CL runtime.
   - `/?view=landing` remains a public landing-page route and is not treated as the retired authenticated GWT app.

4. `compileGwt` does not leave the build until the remaining browser-harness descendants are explicitly migrated, ported, deleted, or otherwise accounted for.
   - `docs/j2cl-gwttestcase-verification-matrix.md`
   - `docs/j2cl-gwt3-inventory.md`
   - issue `#925` comment log

5. The staged app boots and passes end-to-end verification without the legacy root client.
   - `sbt -batch Universal/stage`
   - `bash scripts/worktree-boot.sh --port <port>`
   - `PORT=<port> bash scripts/wave-smoke.sh check`
   - browser proof against `/`, `/?view=landing`, and any retained diagnostic J2CL alias

## Likely Touched Files And Responsibilities

- `build.sbt`
  - remove `compileGwt`/`verifyGwtAssets`/`compileGwtDev` from the packaging-critical path or delete them outright if no longer needed.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
  - remove the legacy authenticated GWT fallback branch and collapse the root decision around the maintained J2CL runtime plus public landing behavior.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
  - remove `renderWaveClientPage(...)` usage from the active runtime path and delete the legacy `webclient.nocache.js` bootstrap contract if nothing else still needs it.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
  - stop mounting `/webclient/*` once no runtime or smoke path depends on it.
- `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/FeatureFlagSeeder.java`
- `wave/config/reference.conf`
  - remove the temporary `j2cl-root-bootstrap` control plane if the GWT rollback path is retired in this slice.
- `wave/src/test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clBootstrapTest.java`
  - rewrite around the post-GWT contract or replace it with a narrower J2CL-root contract test.
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java`
  - update tests if they still route through `renderWaveClientPage(...)`.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/CachingFiltersJakartaIT.java`
  - remove or update `/webclient/*` caching assertions.
- `scripts/wave-smoke.sh`
- `scripts/wave-smoke-ui.sh`
  - switch route-presence checks away from `/webclient/webclient.nocache.js`.
- `docs/current-state.md`
- `docs/BUILDING-sbt.md`
- `docs/runbooks/j2cl-sidecar-testing.md`
- `docs/j2cl-gwttestcase-verification-matrix.md`
- `docs/j2cl-gwt3-inventory.md`
  - update the documented current state to match the removal.
- `wave/config/changelog.d/2026-04-20-retire-legacy-gwt-client.json`
  - changelog fragment for the removal.

## Explicit Browser-Harness Accounting Checklist

Before removing `compileGwt` from the build, issue `#925` must explicitly account for all of:

### Direct `GWTTestCase` suites (`19`)

- `FastQueueGwtTest`
- `BrowserBackedSchedulerGwtTest`
- `EditorGwtTestCase`
- `ImgDoodadGwtTest`
- `EditorEventHandlerGwtTest`
- `PasteExtractorGwtTest`
- `PasteFormatRendererGwtTest`
- `RepairerGwtTest`
- `TypingExtractorGwtTest`
- `GwtRenderingMutationHandlerGwtTest`
- `NodeManagerGwtTest`
- `CleanupGwtTest`
- `TestBase`
- `KeyBindingRegistryIntegrationGwtTest`
- `AggressiveSelectionHelperGwtTest`
- `ExtendedJSObjectGwtTest`
- `WrappedJSObjectGwtTest`
- `XmlStructureGwtTest`
- `EventDispatcherPanelGwtTest`

### Inherited browser-harness descendants (`11`)

- `ContentTestBase`
- `LazyPersistentContentDocumentGwtTest`
- `NodeEventRouterGwtTest`
- `DomGwtTest`
- `ContentElementGwtTest`
- `ContentTextNodeGwtTest`
- `ElementTestBase`
- `OperationGwtTest`
- `MobileWebkitFocusGwtTest`
- `MobileImeFlushGwtTest`
- `ParagraphGwtTest`

### Extra Jakarta/client holdouts

- `WaveWebSocketClientTest`
- `RemoteWaveViewServiceEmptyUserDataSnapshotTest`
- `FocusBlipSelectorTest`
- `BlipMetaDomImplTest`

### Required disposition format

Every item above must be marked, in docs and issue traceability, as exactly one of:

- migrated in this issue
- explicitly deferred with a named follow-up issue
- intentionally deleted with rationale
- still present but no longer a build blocker because it is no longer part of the supported runtime/test gate

No suite should disappear from the default gate without one of those explicit dispositions.

## Recommended Implementation Order

1. Freeze the descendant accounting and runtime-removal contract.
   - Update the matrix/inventory docs and the issue comment log first so the build-removal work has an explicit checklist.
   - Decide and document that `/?view=landing` is retained as public landing behavior, not as the authenticated GWT client.

2. Remove the temporary rollback control plane.
   - Delete `j2cl-root-bootstrap` seeding/default wiring from `KnownFeatureFlags`, `FeatureFlagSeeder`, and `reference.conf` if the only remaining maintained runtime is J2CL.
   - Update servlet tests so the post-`#925` expectation is unconditional J2CL root behavior plus explicit public landing behavior.

3. Remove the legacy authenticated GWT bootstrap/runtime path.
   - Stop calling `HtmlRenderer.renderWaveClientPage(...)` from the live servlet path.
   - Remove `/webclient/*` mounting from `ServerRpcProvider` if nothing else requires it.
   - Remove HTML/script assertions that still look for `webclient/webclient.nocache.js`.

4. Remove `compileGwt` from the packaging-critical build path.
   - Remove `Compile / run := ... dependsOn(..., compileGwt)`.
   - Remove `Universal / stage` / `Universal / packageBin` dependencies on `compileGwt` and `verifyGwtAssets`.
   - Delete `compileGwtDev` and `verifyGwtAssets` if they are no longer meaningful after the GWT asset contract is gone.

5. Update smoke/docs and prove the new gate.
   - Rewrite smoke scripts and docs to look only for maintained J2CL/runtime assets.
   - Stage and boot the app without `/webclient/webclient.nocache.js`.
   - Record exact results in issue `#925`.

## Exact Verification Commands

### Focused server/runtime tests

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
sbt -batch "testOnly org.waveprotocol.box.server.rpc.WaveClientServletJ2clBootstrapTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"
sbt -batch "jakartaIT:testOnly org.waveprotocol.box.server.jakarta.CachingFiltersJakartaIT"
```

### Packaging gate after `compileGwt` removal

```bash
sbt -batch j2clSearchBuild j2clSearchTest j2clProductionBuild Universal/stage
```

Expected result:

- no `compileGwt` task runs
- `Universal/stage` still succeeds
- maintained J2CL bundles are present

### Local runtime proof

```bash
bash scripts/worktree-boot.sh --port 9916
PORT=9916 bash scripts/wave-smoke.sh start
PORT=9916 bash scripts/wave-smoke.sh check
curl -fsS http://localhost:9916/ | grep -F 'data-j2cl-root-shell'
curl -fsS 'http://localhost:9916/?view=landing' | grep -F 'nav-link-signin'
! curl -fsS http://localhost:9916/webclient/webclient.nocache.js
curl -fsS http://localhost:9916/j2cl-search/sidecar/j2cl-sidecar.js | grep -E 'WaveSandboxEntryPoint|j2cl'
PORT=9916 bash scripts/wave-smoke.sh stop
```

### Browser verification

Open in a local signed-in browser session:

- `http://localhost:9916/`
- `http://localhost:9916/?view=landing`
- `http://localhost:9916/?view=j2cl-root` only if the alias is intentionally retained

Expected result:

- `/` loads the J2CL root shell and the maintained workflow
- `/?view=landing` still shows the public landing page
- the maintained root-shell bundle (`/j2cl-search/sidecar/j2cl-sidecar.js`) is present
- `/webclient/webclient.nocache.js` is gone or no longer required by any supported flow

## Risks

- Removing the rollback seam too early can accidentally break the public landing-page route if the current servlet logic still conflates landing behavior with the retired authenticated GWT client.
- `ServerRpcProvider` and the smoke scripts may still have hidden `/webclient/*` assumptions even after the servlet stops emitting the old bootstrap HTML.
- `HtmlRenderer` has tests and helper CSS around `renderWaveClientPage(...)`; deleting the runtime path without cleaning those callers will leave dead assertions or stale helper code behind.
- The browser-harness debt is large; if its disposition is not explicitly recorded, `compileGwt` removal can silently strand suites that were only surviving because the old client/runtime still existed.
- High-risk migration clusters called out in `docs/j2cl-gwt3-inventory.md` (`communication/gwt`, editor selection HTML, common util/browser helpers, websockets, generated JSO transport) are not appropriate for opportunistic cleanup in this issue.

## Non-Goals

- Do not migrate the remaining `19` direct `GWTTestCase` suites or `11` inherited browser-harness descendants to a new J2CL/browser runner inside this issue unless one is needed for a small unblocker.
- Do not rewrite the editor, wavepanel, websocket, or JS overlay clusters just because the inventory points at them.
- Do not remove the public landing page at `/?view=landing` unless a separate product decision expands scope.
- Do not widen this issue into a full source-tree purge of every `org.waveprotocol.box.webclient.*` class; retire the live runtime/bootstrap/package path first, then follow with deeper cleanup only where the diff proves it is safe.

## Plan Review Checklist

- The plan explicitly separates public landing behavior from the retired authenticated GWT runtime.
- The plan removes the `compileGwt`/`/webclient` packaging contract only after documenting every remaining descendant.
- The verification section proves both packaging and local runtime behavior without `webclient.nocache.js`.
- The likely touched files cover runtime, build, smoke, tests, docs, and changelog surfaces.
- The issue comment log must be updated with the plan path, descendant accounting decision, verification commands, and any follow-up issues created for deferred browser-harness suites.

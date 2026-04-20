# Issue #924 Default Root Cutover Implementation Plan

> **For agentic workers:** implement this plan task-by-task in the issue-924 lane only. Do not widen scope into root-shell redesign, route-state changes, or write-path work.

**Goal:** cut over `/` so the J2CL root shell boots by default, while keeping a temporary rollback path that can restore the legacy GWT root without reverting code. This plan starts from the post-`#923` state on `main`: the reversible bootstrap seam already exists and the explicit `/?view=j2cl-root` J2CL root-shell route is already the diagnostic entry point.

**Architecture:** treat this as a server control-plane change, not a new client feature. The key seam is the root decision in `WaveClientServlet#doGet(...)`. When the root-bootstrap control plane says J2CL, `/` should render the same J2CL root shell that `/?view=j2cl-root` already uses, for both signed-in and signed-out requests. Keep the legacy landing/GWT path reachable through the existing rollback control path and keep the explicit J2CL root-shell URL intact for local proof.

Precedence model for this slice must be explicit:

- operator-provided application config override is authoritative for rollback/cutover
- when no operator override is present, the persisted/store-backed flag remains the source of truth
- `reference.conf` only supplies the fresh-deploy default

That means rollback remains a config change, while stored/admin overrides are still durable when no operator override is set.

## 1. Baseline And Problem

The current repo already has:

- a real J2CL root shell behind `/?view=j2cl-root`
- a server-side root-bootstrap seam introduced by `#923`
- the legacy GWT root path still acting as the default `/` experience

The remaining issue is that the root dispatch still short-circuits through the legacy signed-out/landing branch before the J2CL shell can win by default. The cutover needs to move that decision point, not rebuild the shell.

## 2. Exact Control-Plane And Seam Changes

### Server root dispatch

- Update `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` so the root-bootstrap decision is made before the legacy signed-out landing-page branch.
- Keep `/?view=j2cl-root` as the explicit diagnostic route, but make `/` follow the J2CL default when the bootstrap flag is enabled.
- Preserve `?view=landing` as the explicit landing-page escape hatch so the current public landing page is still reachable without being the default root.
- Ensure the signed-out root path can still render the J2CL shell login entry instead of falling through to the landing page when J2CL is the default.

### Bootstrap control plane

- Use the same server-side bootstrap flag introduced by `#923` as the rollback switch.
- Make the fresh-deploy default `true` in the flag seeding/config path.
- Preserve stored/admin overrides only when the operator has not explicitly set an application override value.
- Make the operator-provided application config override authoritative so an operator can force legacy GWT back on without a code change.
- Keep the source of truth in the server control plane:
  - `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
  - `wave/src/main/java/org/waveprotocol/box/server/persistence/FeatureFlagSeeder.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
  - `wave/config/application.conf`
  - `wave/config/reference.conf`

### J2CL shell preservation

- Keep `HtmlRenderer.renderJ2clRootShellPage(...)` and the J2CL mount path unchanged unless a small copy/return-target adjustment is required to make default-root vs explicit-fallback behavior obvious.
- Do not rework `J2clRootShellController`, `J2clRootShellView`, or the `SandboxEntryPoint` mount contract unless a test proves the root shell needs a tiny adapter for `/` vs `/?view=j2cl-root`.

### Test seam

- Extend the existing servlet coverage in `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java` or the nearest root-bootstrap test seam.
- Add one server-level assertion for each root mode: default-on `/`, rollback-off `/`, and explicit `/?view=j2cl-root`.

## 3. Fallback And Rollback Mechanics

- Primary rollback path: disable the root-bootstrap flag and restart/reload the staged config. That must restore the legacy root behavior without a code rollback.
- Secondary rollback proof: keep `/?view=j2cl-root` working even when `/` is rolled back, so the J2CL shell remains available for targeted diagnosis during the rollout window.
- Keep `/?view=landing` available as the explicit legacy landing-page route. Do not rely on it as the hidden default once the cutover lands.
- If the flag lives in persistent storage, the rollout must verify both fresh-deploy seeding and stored-flag precedence so a rollback does not get masked by a stale DB value.
- Document the rollback command path in the runbook and issue comments, not only in the plan.

## 4. Acceptance Slices

### Slice A: Control-plane flip

- The root-bootstrap flag defaults to J2CL for fresh deploys.
- Existing stored/admin overrides still beat the fresh default when no operator override is set.
- An explicit operator override value in application config beats the stored/admin value and serves as the rollback switch.
- The server decision point chooses J2CL for `/` before the legacy landing-page fallback can win.

### Slice B: Default `/` cutover

- Signed-out `/` renders the J2CL root shell, not the landing page.
- Signed-in `/` renders the same J2CL root shell and preserves the existing auth chrome.
- The J2CL shell still mounts the current search/read/write workflow that `#928` introduced.

### Slice C: Explicit fallback and rollback

- `/?view=j2cl-root` still renders the J2CL shell in both bootstrap modes.
- A rollback to legacy GWT is possible by config/flag only.
- `?view=landing` still reaches the legacy landing page explicitly.

### Slice D: Proof and traceability

- The local verification journal records both bootstrap modes.
- The changelog fragment reflects the user-facing default-root behavior change.
- Issue comments record the worktree, branch, verification commands, and the final rollback decision.

## 5. Likely Files To Change

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/FeatureFlagSeeder.java`
- `wave/config/application.conf`
- `wave/config/reference.conf`
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/WaveClientServletTest.java` or the nearest root-bootstrap server test seam
- `docs/runbooks/j2cl-sidecar-testing.md`
- `wave/config/changelog.d/2026-04-20-j2cl-default-root.json`

## 6. Verification Matrix

| Mode | Route | Expected result | Proof |
| --- | --- | --- | --- |
| Default J2CL | `GET /` signed out | J2CL root shell loads with login entry and the J2CL mount host | `WaveClientServletJ2clRootShellTest` plus browser smoke |
| Default J2CL | `GET /` signed in | Same J2CL root shell loads with authenticated chrome and the hosted workflow | browser smoke in a signed-in session |
| Default J2CL | `GET /?view=j2cl-root` | Same J2CL shell as `/`, including return-target preservation | server test plus `curl -I`/page-content check |
| Rollback off | `GET /` signed out | Legacy root behavior returns, not the J2CL shell | server test plus browser smoke |
| Rollback off | `GET /` signed in | Legacy GWT root remains the default app | `compileGwt Universal/stage` plus browser smoke |
| Rollback off | `GET /?view=j2cl-root` | The explicit J2CL diagnostic route still works | server test and local browser check |
| Explicit legacy landing | `GET /?view=landing` | The landing page remains reachable on demand | server test or direct curl |

Required local matrix:

```bash
sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage
bash scripts/worktree-boot.sh --port 9912
PORT=9912 bash scripts/wave-smoke.sh start
PORT=9912 bash scripts/wave-smoke.sh check
curl -fsS http://localhost:9912/ | grep -F 'data-j2cl-root-shell'
curl -fsS 'http://localhost:9912/?view=landing' | grep -F 'Sign In'
curl -fsS 'http://localhost:9912/?view=j2cl-root' | grep -F 'data-j2cl-root-shell'
PORT=9912 bash scripts/wave-smoke.sh stop

cp journal/runtime-config/issue-924-j2cl-default-root-port-9912.application.conf /tmp/j2cl-default-root.application.conf
perl -0pi -e 's/ui\\.j2cl_root_bootstrap_enabled = false/ui.j2cl_root_bootstrap_enabled = true/' /tmp/j2cl-default-root.application.conf
PORT=9912 JAVA_OPTS="-Djava.util.logging.config.file=$PWD/wave/config/wiab-logging.conf -Djava.security.auth.login.config=$PWD/wave/config/jaas.config -Dwave.server.config=/tmp/j2cl-default-root.application.conf" bash scripts/wave-smoke.sh start
PORT=9912 bash scripts/wave-smoke.sh check
curl -fsS http://localhost:9912/ | grep -F 'data-j2cl-root-shell'
curl -fsS 'http://localhost:9912/?view=landing' | grep -F 'Sign In'
curl -fsS 'http://localhost:9912/?view=j2cl-root' | grep -F 'data-j2cl-root-shell'
```

Use the browser against all of:

- `http://localhost:9912/`
- `http://localhost:9912/?view=landing`
- `http://localhost:9912/?view=j2cl-root`

## 7. Concrete Risks

- Signed-out `/` can still fall through to the landing-page branch if the root dispatch is not reordered before the `id == null` check.
- Fresh-deploy defaults can drift from stored/admin overrides if `KnownFeatureFlags`, `FeatureFlagSeeder`, `ServerMain`, and the config files are not kept in sync or if operator override precedence is not made explicit.
- A rollback that only changes tests or docs is not enough; the control-plane switch must be genuinely config-only at runtime.
- It is easy to accidentally make `/?view=j2cl-root` the only working path and forget to prove `/` itself in the browser.
- The root-shell chrome can look correct in unit tests while the J2CL bundle mount or auth redirect target is wrong in a live browser session, so browser proof remains mandatory.

## 8. Ordering

Recommended order:

1. Confirm the current `#923` bootstrap seam still exists and identify the exact flag/config source of truth.
2. Flip the server decision point so `/` can choose J2CL before the legacy landing-page fallback.
3. Make the fresh-deploy default J2CL while preserving explicit operator-override rollback to legacy GWT.
4. Run the default-on and rollback-off verification matrix in the local staged app, including `?view=landing` as the explicit legacy escape hatch.
5. Record the result in the linked GitHub issue and add the changelog fragment if user-facing behavior changed.

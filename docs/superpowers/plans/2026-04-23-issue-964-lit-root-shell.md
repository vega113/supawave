# Issue #964 Lit Root Shell And Shared Chrome Primitives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-rolled HTML/CSS root-shell chrome emitted by `HtmlRenderer.renderJ2clRootShellPage(...)` with a reusable Lit-based shell + chrome primitive family (`shell-root`, `shell-header`, `shell-nav-rail`, `shell-main-region`, `shell-status-strip`, `shell-skip-link`, plus the signed-out `shell-root-signed-out` variant) that mounts behind the existing J2CL coexistence seam (`?view=j2cl-root` request selector and the `j2cl-root-bootstrap` feature flag) without changing the default `/` behavior.

**Architecture:** Introduce the first Lit package in the repo under `j2cl/lit/` (npm + esbuild) producing a single `shell.js` + `shell.css` bundle staged to `war/j2cl/assets/`. The server-rendered shell HTML is progressively enhanced: it emits semantic markup using the Lit primitive tag names (`<shell-root>`, `<shell-header>`, etc.) with pre-styled fallback content; when the bundle loads the custom elements upgrade in place with no unstyled flash. Session and websocket metadata continue to flow through the existing `window.__session` / `window.__websocket_address` inline-script contract; a narrow adapter (`LitShellInput`) isolates that read so post-#963 the shell can be rewired to the new `J2clBootstrapContract` without touching any primitive.

**Tech Stack:** Lit 3 (JS, no TS), esbuild for bundling, npm (vanilla), Web Test Runner + `@web/test-runner-playwright` for Lit unit tests, Jakarta servlet + `HtmlRenderer` for the server seam, existing `sbt` task graph (`j2clRuntimeBuild`, `compileGwt`, `Universal/stage`), and the existing `scripts/worktree-boot.sh` + `scripts/wave-smoke.sh` for local smoke verification.

---

## 1. Goal / Baseline / Root Cause

### 1.1 Baseline

This plan is written against the post-#931 baseline in this worktree:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:144-170` already contains the `view=j2cl-root` selector branch and the `j2cl-root-bootstrap` feature-flag branch — the **coexistence seam** that #964 must not widen or alter semantically.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` at `renderJ2clRootShellPage(...)` (around the section starting with the method signature that takes `sessionJson, analyticsAccount, buildCommit, serverBuildTime, currentReleaseId, rootShellReturnTarget, websocketAddress`) emits the entire shell as StringBuilder-concatenated inline HTML/CSS/JS, using class names `j2cl-root-shell`, `j2cl-root-shell-banner`, `j2cl-root-shell-nav`, `j2cl-root-shell-status`, `j2cl-root-shell-workflow`, `j2cl-root-shell-pill`, `j2cl-root-shell-link`. This is the **visual surface** #964 replaces.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` and `J2clRootShellView.java` already exist (from #928) and mount the existing J2CL search/selected-wave/compose workflow into the `#j2cl-root-shell-workflow` element. This mount behavior stays untouched.
- `j2cl/src/main/webapp/assets/sidecar.css` (458 lines) serves the sidecar-route CSS and is reused by the root-shell page via `<link rel="stylesheet" href="/j2cl/assets/sidecar.css">`. The new Lit bundle ships alongside it at `/j2cl/assets/shell.js` + `/j2cl/assets/shell.css`.
- No Lit, TypeScript, npm, or Node toolchain exists anywhere in the repo today (`find -name package.json` returns nothing under the tree). #964 introduces it.
- `docs/j2cl-lit-design-packet.md` §5.1 is the design authority for the primitive names and variants; §6 classifies Shell/Chrome as **Required Stitch** for #964 (pinned project id + screen ids + design system id must appear in the slice packet below before implementation starts).
- PR #980 (#963) is still open and conflicting, so **the new `J2clBootstrapContract` + `J2clBootstrapServlet` are not yet on `main`**. The shell must consume the pre-#963 inline-script contract (`window.__session`, `window.__websocket_address`) through a narrow adapter that can be repointed after #963 merges.

### 1.2 Root Cause

The hand-rolled shell from #928 exists as a single ~200-line StringBuilder block of inline HTML+CSS+JS inside `HtmlRenderer`. Every downstream Lit slice (#965 server-first, #966 read-surface, #968 live-surface, #970 overlays) needs a **shared visual container** to render inside. Today that container is:

- not componentized — each downstream slice would have to re-derive the shell chrome
- not a Lit seam — there is no Lit bundle, no custom-element registration, no npm pipeline
- coupled to the StringBuilder block — visual changes require Java edits and server rebuilds

#964 is the narrowest issue that unblocks every Lit slice after it by producing a committed Lit primitive family plus the build/staging plumbing required to ship it.

### 1.3 Narrow Root Cause Summary

- No Lit package exists to author shell primitives in.
- The existing root-shell HTML is not componentized and cannot be consumed by downstream slices.
- There is no progressive-enhancement contract (server HTML ↔ Lit upgrade) yet.
- The shell's bootstrap-data read (session/role/websocket) is hard-coded to the pre-#963 inline globals with no adapter seam.

## 2. Acceptance Criteria

`#964` is complete when all of the following are true:

- The repo hosts a new Lit package at `j2cl/lit/` with a deterministic `npm ci && npm run build` pipeline that emits `war/j2cl/assets/shell.js` and `war/j2cl/assets/shell.css`.
- The bundle defines custom elements registered as `shell-root`, `shell-root-signed-out`, `shell-header`, `shell-nav-rail`, `shell-main-region`, `shell-status-strip`, `shell-skip-link`.
- Each primitive consumes the `LitShellInput` adapter (not `window.__session` directly), so post-#963 rewiring requires only adapter changes.
- `HtmlRenderer.renderJ2clRootShellPage(...)` emits semantic HTML using the new custom-element tags with a pre-styled read-only fallback that renders correctly **with JavaScript disabled** (no unstyled flash, no duplicate chrome on Lit upgrade).
- `sbt j2clLitBuild` is a real task wired into `j2clRuntimeBuild` and therefore into `compileGwt Universal/stage`, so a full stage run includes the Lit bundle without any manual npm step.
- The default `/` route still renders the legacy GWT root when `j2cl-root-bootstrap` is off, unchanged.
- `?view=j2cl-root` and the feature-flag path both render the new Lit-upgraded shell. Signed-in and signed-out variants render correctly.
- The existing J2CL workflow still mounts successfully into the `shell-main-region` slot; the sidecar workflow at `/j2cl-search/index.html` is untouched.
- Unit-level Lit tests run via `sbt j2clLitTest` (thin wrapper around `npm run test`) and pass; the server-side `HtmlRendererJ2clRootShellTest` is extended to assert the new custom-element tags, the fallback markup, and the absence of the old `j2cl-root-shell-banner`/`j2cl-root-shell-nav` classes.
- The cross-path build gate passes:
  - `sbt -batch j2clLitBuild j2clLitTest j2clSearchBuild j2clSearchTest compileGwt Universal/stage`
- Local boot + browser + curl verification shows signed-in and signed-out Lit chrome on `?view=j2cl-root` without regressing the default `/` route.
- A slice parity packet (§5) is filled in with the pinned Stitch project id, screen ids, and design-system id per design-packet §6.
- A changelog fragment exists under `wave/config/changelog.d/`.

## 3. Scope And Non-Goals

### 3.1 In Scope

- Introducing the Lit package, bundler, and sbt integration.
- Authoring the seven Lit primitives listed in §2 with design-packet semantics.
- Rewriting `renderJ2clRootShellPage(...)` to emit progressive-enhancement HTML using the new custom elements.
- Introducing the `LitShellInput` adapter over the current `window.__session` / `window.__websocket_address` contract.
- Extending `HtmlRendererJ2clRootShellTest` and adding the Lit-side unit tests.
- Adding the Stitch project, screens, and design system required by design-packet §6, and pinning their ids in §5 below.

### 3.2 Explicit Non-Goals

- No change to the default `/` route behavior or the `j2cl-root-bootstrap` flag semantics.
- No wiring of the shell primitives to the #963 `J2clBootstrapContract` or `J2clBootstrapServlet` endpoints. That rewire is deferred to a dedicated post-#963 task (step 9 below) that **executes only after PR #980 merges**.
- No work on the §5.2 read-surface primitives, §5.3 live-surface indicators, §5.4 compose/toolbar, §5.5 overlays, or §5.6 server-first skeletons. Those belong to #965/#966/#968/#969/#970.
- No J2CL workflow rewrite; the search/selected-wave/compose controllers stay unchanged.
- No TypeScript. Lit 3 in plain JS is sufficient for this slice and avoids a second toolchain decision.
- No introduction of a broader client router, history integration, or new auth flow.
- No modification of the existing `sidecar.css` file; the shell's styling ships in the new `shell.css`.

## 4. Dependency Readiness (#963 Coupling)

### 4.1 What #963 (PR #980) Owns

PR #980 adds:

- `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java` (updated to consume JSON)

These deliver route/session/socket metadata as an explicit JSON endpoint.

### 4.2 Dependency-Safe Boundary

Tasks 1–8 in §7 below are **safe to land before #963 merges**. They consume only the pre-#963 inline-script globals that already exist today:

- `window.__session` (object with `address`, `role`, `domain`, `idSeed`, `features`)
- `window.__websocket_address` (string)

The `LitShellInput` adapter reads these values through a single module (`j2cl/lit/src/input/inline-shell-input.js`). Post-#963, a second adapter implementation (`json-shell-input.js`) calls the `/j2cl-bootstrap` endpoint. The switch is a one-line swap at shell-bootstrap entry.

### 4.3 Tasks Explicitly Gated On #963

- **Task 9** (Rewire `LitShellInput` to the `J2clBootstrapContract` JSON endpoint) — **must not be executed until PR #980 is merged into `main` and pulled into this worktree**.

Until #980 merges, Task 9's checkboxes stay unchecked and the lane waits.

## 5. Slice Parity Packet — Issue #964

**Title:** Build the Lit root shell and shared chrome primitives behind the existing J2CL coexistence seam
**Stage:** server-first (shell-chrome side of the server-first surface)
**Dependencies (from issue map §6):** #962 (design packet, merged), #963 (bootstrap JSON contract, PR #980 in review)

### Parity matrix rows claimed
- R-6.1 — Server-rendered read-only first paint (shell-chrome portion only; read-surface stays with #965/#966)
- R-6.3 — Shell-swap upgrade path (no unstyled flash between server HTML and Lit-upgraded shell)
- R-6.4 — Rollback-safe coexistence (`/?view=j2cl-root` still reachable; legacy `/` unchanged; feature-flag toggle remains operator-reversible)
- R-4.5 — Route/history integration (shell-chrome portion only; route state semantics stay with #968)

### GWT seams de-risked
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:144-170` — J2CL coexistence seam
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:renderJ2clRootShellPage` — StringBuilder shell HTML/CSS/JS (replaced)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` — workflow mount (untouched)

### Rollout flag / rollout seam
- Flag: `j2cl-root-bootstrap` (existing feature flag; not modified)
- Route selector: `?view=j2cl-root` (existing request selector; not modified)
- Default: `off` (default `/` remains GWT)
- Reversibility: operator toggles the flag off; the route selector remains available for manual verification; no schema or persistence change required to roll back

### Server / client surface list
- Server: `WaveClientServlet`, `HtmlRenderer.renderJ2clRootShellPage`, new `HtmlRenderer.renderLitShellFallback` helper
- Client: `j2cl/lit/` package (new); seven custom elements (`shell-root`, `shell-root-signed-out`, `shell-header`, `shell-nav-rail`, `shell-main-region`, `shell-status-strip`, `shell-skip-link`); `LitShellInput` adapter

### Required-match behaviors (from matrix)
- R-6.1: server HTML shell legible without JS; readable chrome survives network-timeout Lit-bundle failure
- R-6.3: no duplicate roots during upgrade; no unstyled flash; Lit elements upgrade in place over the same DOM the server rendered
- R-6.4: `/?view=j2cl-root` reachable; legacy `/` unchanged; operator rollback reversible via the existing feature flag

### Allowed-change visuals (from matrix)
- Shell chrome, spacing, typography, elevation/surface treatment, nav-rail presentation (per design-packet §4 tokens and §5.1 primitives)
- Signed-in/signed-out banner visuals may change
- The workflow mount region visuals may change **outside** of the mount host; the mount host itself (`#j2cl-root-shell-workflow`) keeps its id/marker contract so `J2clRootShellController` continues to mount unchanged

### Keyboard / focus plan
- Skip link (`shell-skip-link`) is the first tab stop on the page and jumps focus to `shell-main-region`
- Tab order: skip link → brand/header actions → nav rail entries → main region → status strip
- Focus does not jump on Lit upgrade; the active element before upgrade stays active after

### Accessibility plan
- `shell-root` carries `role="application"` only if ARIA audits call for it; default is semantic `<main>` landmarks inside
- `shell-header` uses `<header role="banner">`
- `shell-nav-rail` uses `<nav aria-label="Primary">`
- `shell-main-region` uses `<main>` with `id="j2cl-root-shell-workflow"` preserved so the workflow mount contract stays intact
- `shell-status-strip` uses `<aside role="status" aria-live="polite">` (visual surface only; the live-state behavior belongs to #968/R-4.3)
- Contrast and focus ring rules inherit from design-packet §8.1

### i18n plan
- Primitive attributes accept translated labels via `aria-label` attributes; the server-side `HtmlRenderer` passes locale-resolved strings into the fallback markup
- No new message bundle keys are introduced in this slice beyond `shell.skip_to_main`, `shell.sign_in`, `shell.sign_out`, `shell.admin`

### Browser-harness coverage
- Add `HtmlRendererJ2clRootShellTest` cases for: custom-element tag presence, fallback markup presence, absence of legacy `j2cl-root-shell-*` classes, and signed-out vs signed-in divergence
- Add Lit unit fixtures for each primitive via Web Test Runner (`j2cl/lit/test/`)
- Add one end-to-end curl assertion in the smoke script path that `shell-root` is present and fallback text is present

### Telemetry and observability checkpoints
- Emit a single `client.j2cl.shell.upgrade` event on first `shell-root` upgrade with `{ signedIn: boolean, upgradeMs: number }` payload via the existing client stats channel used by `J2clRootShellController` (piggybacked; no new transport)
- Server-side: existing `renderJ2clRootShellPage` log stays; no new log signals

### Verification plan
- Smoke:
  - `bash scripts/worktree-boot.sh --port 9964`
  - `PORT=9964 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
  - `PORT=9964 bash scripts/wave-smoke.sh check`
  - `PORT=9964 bash scripts/wave-smoke.sh stop`
- Browser:
  - Required by `docs/runbooks/change-type-verification-matrix.md` (GWT client/UI row)
  - Signed-in `http://localhost:9964/?view=j2cl-root`, signed-out `http://localhost:9964/?view=j2cl-root`, default `http://localhost:9964/`
- Harness:
  - `sbt -batch j2clLitBuild j2clLitTest j2clSearchBuild j2clSearchTest compileGwt Universal/stage`

### Rollback plan
- Flip `j2cl-root-bootstrap` off → default `/` continues serving legacy GWT; `?view=j2cl-root` remains reachable as an operator-facing verification route
- Nothing schema-backed to revert; the Lit bundle is a static asset that can be ignored by simply not mounting

### Stitch artifact pinning (design-packet §6, **Required** for §5.1 Shell/Chrome)

The following fields MUST be populated before Task 4 in §7 starts. They are filled during Task 3.

- Stitch project id: `<TO BE PINNED IN TASK 3>`
- Screen ids (one per §5.1 variant):
  - `shell-root`: `<id>`
  - `shell-root-signed-out`: `<id>`
  - `shell-header`: `<id>`
  - `shell-nav-rail`: `<id>`
  - `shell-main-region`: `<id>`
  - `shell-status-strip`: `<id>`
  - `shell-skip-link`: `<id>`
- Design system id: `<id>` (applied to the project, covering design-packet §4.1–§4.10 token slots)

### Traceability
- Parity matrix: `docs/j2cl-gwt-parity-matrix.md`
- Design packet: `docs/j2cl-lit-design-packet.md` (§5.1, §6, §8)
- Issue map: `docs/j2cl-parity-issue-map.md`
- Architecture memo: `docs/j2cl-parity-architecture.md`
- Workflow: `docs/j2cl-lit-implementation-workflow.md`
- Linked issue(s): `#964`, `#962`, `#963`, `#904`, `#960`
- Linked plan: `docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md`

## 6. File Structure

### 6.1 New Files

- `j2cl/lit/package.json` — Lit 3 + esbuild + Web Test Runner pinned versions, build/test scripts
- `j2cl/lit/package-lock.json` — committed lockfile for deterministic builds
- `j2cl/lit/esbuild.config.mjs` — bundle config (entry: `src/index.js`, output: `../../war/j2cl/assets/shell.{js,css}`, target: ES2020, minify, sourcemap)
- `j2cl/lit/web-test-runner.config.mjs` — Web Test Runner config (Playwright chromium)
- `j2cl/lit/.gitignore` — `node_modules/`, `dist/`
- `j2cl/lit/src/index.js` — side-effect entry: registers all seven custom elements
- `j2cl/lit/src/input/lit-shell-input.js` — `LitShellInput` interface definition
- `j2cl/lit/src/input/inline-shell-input.js` — pre-#963 adapter (reads `window.__session` + `window.__websocket_address`)
- `j2cl/lit/src/tokens/shell-tokens.css` — CSS custom properties implementing design-packet §4 token slots for the shell family (values pinned by the Stitch design system)
- `j2cl/lit/src/elements/shell-root.js` — `<shell-root>` signed-in variant
- `j2cl/lit/src/elements/shell-root-signed-out.js` — `<shell-root-signed-out>` variant
- `j2cl/lit/src/elements/shell-header.js` — `<shell-header>`
- `j2cl/lit/src/elements/shell-nav-rail.js` — `<shell-nav-rail>`
- `j2cl/lit/src/elements/shell-main-region.js` — `<shell-main-region>` (slots the existing `#j2cl-root-shell-workflow` mount host)
- `j2cl/lit/src/elements/shell-status-strip.js` — `<shell-status-strip>`
- `j2cl/lit/src/elements/shell-skip-link.js` — `<shell-skip-link>`
- `j2cl/lit/test/shell-root.test.js`
- `j2cl/lit/test/shell-root-signed-out.test.js`
- `j2cl/lit/test/shell-header.test.js`
- `j2cl/lit/test/shell-nav-rail.test.js`
- `j2cl/lit/test/shell-main-region.test.js`
- `j2cl/lit/test/shell-status-strip.test.js`
- `j2cl/lit/test/shell-skip-link.test.js`
- `j2cl/lit/test/inline-shell-input.test.js`
- `wave/config/changelog.d/2026-04-23-lit-root-shell.json`

### 6.2 Modified Files

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — replace the StringBuilder shell block with progressive-enhancement HTML using custom-element tags; inject the `/j2cl/assets/shell.css` stylesheet and the `/j2cl/assets/shell.js` module script
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java` — extend assertions to custom-element tags, fallback markup, absence of legacy classes
- `build.sbt` — add `j2clLitBuild` and `j2clLitTest` tasks; wire `j2clLitBuild` into `j2clRuntimeBuild`; add `j2cl/lit/dist` to `cleanFiles` if the output lands there
- `.gitignore` — add `j2cl/lit/node_modules/` and `j2cl/lit/dist/` if the latter is used by esbuild intermediates
- `AGENTS.md` — add one line under Worktree guardrails: "When running `npm` tasks under `j2cl/lit/`, run from that directory; do not leak Node into other packages."

### 6.3 Inspect-Only References

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` — mount host contract consumer (verify the `#j2cl-root-shell-workflow` id stays stable)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java` — mount DOM consumer
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` — coexistence seam (verify, do not modify)

## 7. Implementation Order

Each task below is bite-sized (2–5 minutes per step). Every step either writes code shown inline, runs the exact command shown, or commits with the exact message shown. TDD order: failing test → minimal code → passing test → commit.

### Task 1: Freeze the baseline and verify the current shell

- [ ] **Step 1.1: Verify default `/` still renders legacy GWT**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-964-lit-root-shell
grep -n "webclient/webclient.nocache.js" wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java | head -3
```

Expected: at least one hit; legacy bootstrap is still referenced.

- [ ] **Step 1.2: Verify the existing coexistence seam is the one #964 uses**

Run:

```bash
grep -n "VIEW_J2CL_ROOT\|j2cl-root-bootstrap" wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java | head -6
```

Expected: `VIEW_J2CL_ROOT` + `j2cl-root-bootstrap` flag reference both present around lines 144–170.

- [ ] **Step 1.3: Confirm no pre-existing Lit or npm tree**

Run:

```bash
find . -name "package.json" -not -path "./node_modules/*" -not -path "*/.git/*" 2>/dev/null
```

Expected: empty output.

- [ ] **Step 1.4: Commit baseline freeze marker**

```bash
git add docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md
git commit -m "docs(#964): freeze plan for Lit root shell slice"
```

### Task 2: Scaffold the Lit package (no Java changes yet)

**Files:**
- Create: `j2cl/lit/package.json`
- Create: `j2cl/lit/.gitignore`
- Create: `j2cl/lit/esbuild.config.mjs`
- Create: `j2cl/lit/web-test-runner.config.mjs`
- Create: `j2cl/lit/src/index.js` (empty placeholder for now)

- [ ] **Step 2.1: Write `j2cl/lit/package.json`**

```json
{
  "name": "supawave-lit-shell",
  "version": "0.0.1",
  "description": "Lit primitives for the SupaWave J2CL root shell (issue #964).",
  "private": true,
  "type": "module",
  "scripts": {
    "build": "node esbuild.config.mjs",
    "test": "web-test-runner \"test/**/*.test.js\" --node-resolve --playwright --browsers chromium"
  },
  "dependencies": {
    "lit": "3.2.1"
  },
  "devDependencies": {
    "@open-wc/testing": "4.0.0",
    "@web/test-runner": "0.19.0",
    "@web/test-runner-playwright": "0.11.0",
    "esbuild": "0.24.0"
  }
}
```

- [ ] **Step 2.2: Write `j2cl/lit/.gitignore`**

```
node_modules/
dist/
```

- [ ] **Step 2.3: Write `j2cl/lit/esbuild.config.mjs`**

Two entry points: one for the JS bundle, one for the CSS tokens. esbuild emits `shell.js` from the JS entry and `shell.css` from the CSS entry, both under `war/j2cl/assets/`. The CSS is a real file served directly by the existing `/j2cl/assets/` static mount so the server-rendered HTML renders styled tokens **without JavaScript** (progressive-enhancement requirement from design packet §5.6).

```javascript
import { build } from "esbuild";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "../../war/j2cl/assets");

await build({
  entryPoints: [
    { in: resolve(here, "src/index.js"), out: "shell" },
    { in: resolve(here, "src/tokens/shell-tokens.css"), out: "shell" },
  ],
  bundle: true,
  format: "esm",
  target: "es2020",
  minify: true,
  sourcemap: true,
  outdir: out,
  logLevel: "info",
});
```

- [ ] **Step 2.4: Write `j2cl/lit/web-test-runner.config.mjs`**

```javascript
import { playwrightLauncher } from "@web/test-runner-playwright";

export default {
  files: "test/**/*.test.js",
  nodeResolve: true,
  browsers: [playwrightLauncher({ product: "chromium" })],
  testFramework: {
    config: { ui: "bdd", timeout: 5000 },
  },
};
```

- [ ] **Step 2.5: Write `j2cl/lit/src/index.js` (placeholder)**

```javascript
// Entry point for the SupaWave Lit shell bundle (issue #964).
// Element registrations land in later tasks.
```

- [ ] **Step 2.6: Run `npm install` and commit the lockfile**

```bash
cd j2cl/lit
npm install
cd ../..
git add j2cl/lit/package.json j2cl/lit/package-lock.json j2cl/lit/.gitignore j2cl/lit/esbuild.config.mjs j2cl/lit/web-test-runner.config.mjs j2cl/lit/src/index.js
git commit -m "feat(#964): scaffold Lit shell package with esbuild + web-test-runner"
```

Expected: clean install; `package-lock.json` committed.

- [ ] **Step 2.7: Verify the empty bundle builds**

```bash
cd j2cl/lit && npm run build && ls -la ../../war/j2cl/assets/shell.js && cd ../..
```

Expected: `shell.js` written (small, near-empty).

### Task 3: Create and pin the Stitch artifact (design-packet §6 gate — BLOCKS Task 5 onward)

**Blocking rule:** Tasks 5, 6, 7, 8 must not start until Steps 3.1–3.5 are complete and every `<TO BE PINNED IN TASK 3>` slot in §5 is filled with a real id. Task 4 (`LitShellInput` adapter) has no visual output and may proceed in parallel with Task 3.

Token values used in Task 7 are not invented — they are copied from the design system created in Step 3.2 via the response payload of `mcp__stitch__create_design_system` / `mcp__stitch__get_project`.

- [ ] **Step 3.1: Create a Stitch project for the shell family**

Use the `mcp__stitch__create_project` MCP tool with:
- name: `SupaWave Lit Shell — issue #964`
- description: "Required Stitch artifact for design-packet §5.1 Shell/Chrome family. Covers shell-root, shell-root-signed-out, shell-header, shell-nav-rail, shell-main-region, shell-status-strip, shell-skip-link."

Record the returned `projectId`.

- [ ] **Step 3.2: Create the design system**

Use `mcp__stitch__create_design_system` covering token slots from design-packet §4.1–§4.10. Apply it to the project via `mcp__stitch__apply_design_system`. Record the `designSystemId`.

- [ ] **Step 3.3: Generate one screen per variant**

Call `mcp__stitch__generate_screen_from_text` seven times, one for each primitive listed in the §5 packet. Record each `screenId`.

- [ ] **Step 3.4: Pin the ids into §5 of this plan**

Edit `docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md` and replace the `<TO BE PINNED IN TASK 3>` placeholders with the actual ids from Steps 3.1–3.3.

- [ ] **Step 3.5: Commit the pinned plan**

```bash
git add docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md
git commit -m "docs(#964): pin Stitch project/screens/design-system ids per design-packet §6"
```

### Task 4: Author the `LitShellInput` adapter (pre-#963 inline variant)

**Files:**
- Create: `j2cl/lit/src/input/lit-shell-input.js`
- Create: `j2cl/lit/src/input/inline-shell-input.js`
- Create: `j2cl/lit/test/inline-shell-input.test.js`

- [ ] **Step 4.1: Write the failing test `j2cl/lit/test/inline-shell-input.test.js`**

```javascript
import { expect } from "@open-wc/testing";
import { createInlineShellInput } from "../src/input/inline-shell-input.js";

describe("inline-shell-input", () => {
  beforeEach(() => {
    window.__session = undefined;
    window.__websocket_address = undefined;
  });

  it("returns a signed-out snapshot when no session is present", () => {
    const input = createInlineShellInput(window);
    const snap = input.read();
    expect(snap.signedIn).to.equal(false);
    expect(snap.address).to.equal("");
    expect(snap.role).to.equal("user");
    expect(snap.websocketAddress).to.equal("");
  });

  it("returns a signed-in snapshot with role normalization", () => {
    window.__session = { address: "a@b.c", role: "admin", domain: "b.c", idSeed: "s", features: [] };
    window.__websocket_address = "ws.example:443";
    const input = createInlineShellInput(window);
    const snap = input.read();
    expect(snap.signedIn).to.equal(true);
    expect(snap.address).to.equal("a@b.c");
    expect(snap.role).to.equal("admin");
    expect(snap.websocketAddress).to.equal("ws.example:443");
  });

  it("returns signed-out when address is empty string", () => {
    window.__session = { address: "", role: "user", domain: "", idSeed: "", features: [] };
    const input = createInlineShellInput(window);
    expect(input.read().signedIn).to.equal(false);
  });
});
```

- [ ] **Step 4.2: Run the test to verify it fails**

```bash
cd j2cl/lit && npm test && cd ../..
```

Expected: FAIL with "Cannot find module ../src/input/inline-shell-input.js" or similar.

- [ ] **Step 4.3: Write `j2cl/lit/src/input/lit-shell-input.js`**

```javascript
/**
 * @typedef {object} LitShellSnapshot
 * @property {boolean} signedIn
 * @property {string} address
 * @property {"admin"|"owner"|"user"} role
 * @property {string} domain
 * @property {string} idSeed
 * @property {string[]} features
 * @property {string} websocketAddress
 *
 * @typedef {object} LitShellInput
 * @property {() => LitShellSnapshot} read
 */

export const EMPTY_SNAPSHOT = Object.freeze({
  signedIn: false,
  address: "",
  role: "user",
  domain: "",
  idSeed: "",
  features: Object.freeze([]),
  websocketAddress: "",
});
```

- [ ] **Step 4.4: Write `j2cl/lit/src/input/inline-shell-input.js`**

```javascript
import { EMPTY_SNAPSHOT } from "./lit-shell-input.js";

const ALLOWED_ROLES = new Set(["admin", "owner", "user"]);

function normalizeRole(raw) {
  return ALLOWED_ROLES.has(raw) ? raw : "user";
}

/**
 * Pre-#963 adapter: reads bootstrap data from inline script globals.
 * Post-#963, swap this for the JSON-based adapter at the import site in
 * src/index.js without touching any shell element.
 */
export function createInlineShellInput(win) {
  return {
    read() {
      const session = win.__session;
      const wsAddress = typeof win.__websocket_address === "string" ? win.__websocket_address : "";
      if (!session || typeof session !== "object") {
        return { ...EMPTY_SNAPSHOT, websocketAddress: wsAddress };
      }
      const address = typeof session.address === "string" ? session.address : "";
      const signedIn = address.length > 0;
      return {
        signedIn,
        address,
        role: normalizeRole(session.role),
        domain: typeof session.domain === "string" ? session.domain : "",
        idSeed: typeof session.idSeed === "string" ? session.idSeed : "",
        features: Array.isArray(session.features) ? [...session.features] : [],
        websocketAddress: wsAddress,
      };
    },
  };
}
```

- [ ] **Step 4.5: Run tests to verify they pass**

```bash
cd j2cl/lit && npm test && cd ../..
```

Expected: 3 passing.

- [ ] **Step 4.6: Commit**

```bash
git add j2cl/lit/src/input/ j2cl/lit/test/inline-shell-input.test.js
git commit -m "feat(#964): add LitShellInput adapter with pre-#963 inline reader"
```

### Task 5: Author `<shell-skip-link>` (simplest primitive, TDD walkthrough)

**Files:**
- Create: `j2cl/lit/src/elements/shell-skip-link.js`
- Create: `j2cl/lit/test/shell-skip-link.test.js`

- [ ] **Step 5.1: Write the failing test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-skip-link.js";

describe("<shell-skip-link>", () => {
  it("renders an anchor linking to the configured target", async () => {
    const el = await fixture(html`<shell-skip-link target="#main" label="Skip to content"></shell-skip-link>`);
    const a = el.renderRoot.querySelector("a");
    expect(a).to.exist;
    expect(a.getAttribute("href")).to.equal("#main");
    expect(a.textContent.trim()).to.equal("Skip to content");
  });

  it("falls back to default target and label", async () => {
    const el = await fixture(html`<shell-skip-link></shell-skip-link>`);
    const a = el.renderRoot.querySelector("a");
    expect(a.getAttribute("href")).to.equal("#j2cl-root-shell-workflow");
    expect(a.textContent.trim()).to.equal("Skip to main content");
  });
});
```

- [ ] **Step 5.2: Run the test to verify it fails**

```bash
cd j2cl/lit && npm test && cd ../..
```

Expected: FAIL (module not found).

- [ ] **Step 5.3: Write the minimal implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellSkipLink extends LitElement {
  static properties = {
    target: { type: String },
    label: { type: String },
  };

  static styles = css`
    :host { position: absolute; top: 0; left: 0; }
    a {
      position: absolute;
      left: -9999px;
      padding: 8px 12px;
      background: var(--shell-color-accent-focus, #1a73e8);
      color: #fff;
      border-radius: 0 0 8px 0;
      font: inherit;
      text-decoration: none;
    }
    a:focus { left: 0; outline: 2px solid #fff; outline-offset: -4px; }
  `;

  constructor() {
    super();
    this.target = "#j2cl-root-shell-workflow";
    this.label = "Skip to main content";
  }

  render() {
    return html`<a href=${this.target}>${this.label}</a>`;
  }
}

customElements.define("shell-skip-link", ShellSkipLink);
```

- [ ] **Step 5.4: Run tests to verify they pass**

```bash
cd j2cl/lit && npm test && cd ../..
```

Expected: 5 passing (3 from Task 4 + 2 here).

- [ ] **Step 5.5: Commit**

```bash
git add j2cl/lit/src/elements/shell-skip-link.js j2cl/lit/test/shell-skip-link.test.js
git commit -m "feat(#964): add <shell-skip-link> primitive"
```

### Task 6: Author the remaining six primitives in the same TDD pattern

Repeat the Task 5 pattern for each of the six elements below. Each element gets its own test file, minimal implementation, and commit. Full code is shown in the sub-steps.

**File layout:**
- Create: `j2cl/lit/src/elements/shell-header.js` + `test/shell-header.test.js`
- Create: `j2cl/lit/src/elements/shell-nav-rail.js` + `test/shell-nav-rail.test.js`
- Create: `j2cl/lit/src/elements/shell-main-region.js` + `test/shell-main-region.test.js`
- Create: `j2cl/lit/src/elements/shell-status-strip.js` + `test/shell-status-strip.test.js`
- Create: `j2cl/lit/src/elements/shell-root.js` + `test/shell-root.test.js`
- Create: `j2cl/lit/src/elements/shell-root-signed-out.js` + `test/shell-root-signed-out.test.js`

#### 6.1 `<shell-header>`

- [ ] **Step 6.1.1: Test — renders banner landmark with brand slot and signed-in/signed-out actions**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-header.js";

describe("<shell-header>", () => {
  it("uses the banner landmark", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("header[role='banner']")).to.exist;
  });

  it("renders the signed-in action slot when signed-in", async () => {
    const el = await fixture(html`<shell-header signed-in></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.not.exist;
  });

  it("renders the signed-out action slot by default", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.not.exist;
  });
});
```

- [ ] **Step 6.1.2: Run to fail**: `cd j2cl/lit && npm test && cd ../..` — FAIL (module missing)

- [ ] **Step 6.1.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellHeader extends LitElement {
  static properties = { signedIn: { type: Boolean, attribute: "signed-in", reflect: true } };
  static styles = css`
    :host { display: block; }
    header {
      display: flex; align-items: center; justify-content: space-between;
      gap: var(--shell-space-inline-default, 12px);
      padding: var(--shell-space-inset-panel, 16px 18px);
      border-bottom: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      background: var(--shell-color-surface-shell, #fff);
    }
    .brand { display: inline-flex; align-items: center; gap: 10px; }
    .actions { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  `;
  constructor() { super(); this.signedIn = false; }
  render() {
    return html`
      <header role="banner">
        <div class="brand"><slot name="brand"></slot></div>
        <div class="actions">
          ${this.signedIn
            ? html`<slot name="actions-signed-in"></slot>`
            : html`<slot name="actions-signed-out"></slot>`}
        </div>
      </header>`;
  }
}
customElements.define("shell-header", ShellHeader);
```

- [ ] **Step 6.1.4: Run to pass** — `cd j2cl/lit && npm test && cd ../..`

- [ ] **Step 6.1.5: Commit** — `git add j2cl/lit/src/elements/shell-header.js j2cl/lit/test/shell-header.test.js && git commit -m "feat(#964): add <shell-header> primitive"`

#### 6.2 `<shell-nav-rail>`

- [ ] **Step 6.2.1: Test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-nav-rail.js";

describe("<shell-nav-rail>", () => {
  it("uses the navigation landmark with a primary label", async () => {
    const el = await fixture(html`<shell-nav-rail></shell-nav-rail>`);
    const nav = el.renderRoot.querySelector("nav");
    expect(nav).to.exist;
    expect(nav.getAttribute("aria-label")).to.equal("Primary");
  });

  it("exposes a default slot for entries", async () => {
    const el = await fixture(html`<shell-nav-rail><a href="/">Home</a></shell-nav-rail>`);
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
  });
});
```

- [ ] **Step 6.2.2: Run to fail**
- [ ] **Step 6.2.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellNavRail extends LitElement {
  static properties = { label: { type: String } };
  static styles = css`
    :host { display: block; }
    nav {
      display: flex; flex-direction: column; gap: 4px;
      padding: var(--shell-space-inset-panel, 12px);
      background: var(--shell-color-surface-shell, #fff);
      border-right: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      min-width: 180px;
    }
    ::slotted(*) { display: block; padding: 8px 10px; border-radius: 8px; }
  `;
  constructor() { super(); this.label = "Primary"; }
  render() {
    return html`<nav aria-label=${this.label}><slot></slot></nav>`;
  }
}
customElements.define("shell-nav-rail", ShellNavRail);
```

- [ ] **Step 6.2.4: Run to pass**
- [ ] **Step 6.2.5: Commit** — `git add j2cl/lit/src/elements/shell-nav-rail.js j2cl/lit/test/shell-nav-rail.test.js && git commit -m "feat(#964): add <shell-nav-rail> primitive"`

#### 6.3 `<shell-main-region>`

- [ ] **Step 6.3.1: Test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-main-region.js";

describe("<shell-main-region>", () => {
  it("uses the main landmark", async () => {
    const el = await fixture(html`<shell-main-region></shell-main-region>`);
    expect(el.renderRoot.querySelector("main")).to.exist;
  });

  it("slots light-DOM children so the workflow mount host stays queryable from document", async () => {
    const el = await fixture(html`
      <shell-main-region>
        <section id="j2cl-root-shell-workflow" data-j2cl-root-shell-workflow="true"></section>
      </shell-main-region>
    `);
    // Shadow contains a slot, NOT the id (the id must live in light DOM so
    // document.getElementById('j2cl-root-shell-workflow') keeps working for
    // J2clRootShellController).
    expect(el.renderRoot.querySelector("slot")).to.exist;
    expect(el.renderRoot.querySelector("#j2cl-root-shell-workflow")).to.not.exist;
    expect(el.querySelector("#j2cl-root-shell-workflow")).to.exist;
    expect(document.getElementById("j2cl-root-shell-workflow")).to.exist;
  });
});
```

- [ ] **Step 6.3.2: Run to fail**
- [ ] **Step 6.3.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellMainRegion extends LitElement {
  static styles = css`
    :host { display: block; flex: 1; min-width: 0; }
    main { padding: var(--shell-space-inset-panel, 20px); min-height: 360px; }
  `;
  render() {
    // The #j2cl-root-shell-workflow element is a light-DOM child of this
    // component; it must stay queryable via document.getElementById so
    // J2clRootShellController.start() continues to work unchanged.
    return html`<main><slot></slot></main>`;
  }
}
customElements.define("shell-main-region", ShellMainRegion);
```

- [ ] **Step 6.3.4: Run to pass**
- [ ] **Step 6.3.5: Commit** — `git add j2cl/lit/src/elements/shell-main-region.js j2cl/lit/test/shell-main-region.test.js && git commit -m "feat(#964): add <shell-main-region> primitive preserving workflow mount host"`

#### 6.4 `<shell-status-strip>`

- [ ] **Step 6.4.1: Test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-status-strip.js";

describe("<shell-status-strip>", () => {
  it("uses a polite status live region", async () => {
    const el = await fixture(html`<shell-status-strip></shell-status-strip>`);
    const aside = el.renderRoot.querySelector("aside");
    expect(aside.getAttribute("role")).to.equal("status");
    expect(aside.getAttribute("aria-live")).to.equal("polite");
  });

  it("exposes a default slot for status content", async () => {
    const el = await fixture(html`<shell-status-strip>Connected</shell-status-strip>`);
    expect(el.renderRoot.querySelector("slot")).to.exist;
  });
});
```

- [ ] **Step 6.4.2: Run to fail**
- [ ] **Step 6.4.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellStatusStrip extends LitElement {
  static styles = css`
    :host { display: block; }
    aside {
      padding: 6px var(--shell-space-inset-panel, 18px);
      background: var(--shell-color-surface-shell, #fff);
      border-top: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.85rem;
    }
  `;
  render() {
    return html`<aside role="status" aria-live="polite"><slot></slot></aside>`;
  }
}
customElements.define("shell-status-strip", ShellStatusStrip);
```

- [ ] **Step 6.4.4: Run to pass**
- [ ] **Step 6.4.5: Commit** — `git add j2cl/lit/src/elements/shell-status-strip.js j2cl/lit/test/shell-status-strip.test.js && git commit -m "feat(#964): add <shell-status-strip> primitive (visual surface only)"`

#### 6.5 `<shell-root>` (signed-in)

- [ ] **Step 6.5.1: Test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-root.js";

describe("<shell-root>", () => {
  it("renders the signed-in layout slots", async () => {
    const el = await fixture(html`<shell-root></shell-root>`);
    expect(el.renderRoot.querySelector("slot[name='skip-link']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='nav']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='main']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
  });
});
```

- [ ] **Step 6.5.2: Run to fail**
- [ ] **Step 6.5.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    :host { display: grid; grid-template-rows: auto 1fr auto; min-height: 100vh;
            background: var(--shell-color-surface-page, #f7fbff);
            color: var(--shell-color-text-primary, #102b3f); }
    .body { display: flex; min-height: 0; }
    slot[name='nav'] { flex: 0 0 auto; }
    slot[name='main'] { flex: 1; min-width: 0; }
  `;
  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <div class="body">
        <slot name="nav"></slot>
        <slot name="main"></slot>
      </div>
      <slot name="status"></slot>`;
  }
}
customElements.define("shell-root", ShellRoot);
```

- [ ] **Step 6.5.4: Run to pass**
- [ ] **Step 6.5.5: Commit** — `git add j2cl/lit/src/elements/shell-root.js j2cl/lit/test/shell-root.test.js && git commit -m "feat(#964): add <shell-root> signed-in shell primitive"`

#### 6.6 `<shell-root-signed-out>`

- [ ] **Step 6.6.1: Test**

```javascript
import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-root-signed-out.js";

describe("<shell-root-signed-out>", () => {
  it("renders skip-link, header, main, and status slots without a nav", async () => {
    const el = await fixture(html`<shell-root-signed-out></shell-root-signed-out>`);
    expect(el.renderRoot.querySelector("slot[name='skip-link']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='main']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='nav']")).to.not.exist;
  });
});
```

- [ ] **Step 6.6.2: Run to fail**
- [ ] **Step 6.6.3: Implementation**

```javascript
import { LitElement, html, css } from "lit";

export class ShellRootSignedOut extends LitElement {
  static styles = css`
    :host { display: grid; grid-template-rows: auto 1fr auto; min-height: 100vh;
            background: var(--shell-color-surface-page, #f7fbff);
            color: var(--shell-color-text-primary, #102b3f); }
  `;
  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <slot name="main"></slot>
      <slot name="status"></slot>`;
  }
}
customElements.define("shell-root-signed-out", ShellRootSignedOut);
```

- [ ] **Step 6.6.4: Run to pass**
- [ ] **Step 6.6.5: Commit** — `git add j2cl/lit/src/elements/shell-root-signed-out.js j2cl/lit/test/shell-root-signed-out.test.js && git commit -m "feat(#964): add <shell-root-signed-out> primitive"`

### Task 7: Wire the primitives into the bundle entry and publish tokens

**Files:**
- Modify: `j2cl/lit/src/index.js`
- Create: `j2cl/lit/src/tokens/shell-tokens.css`

- [ ] **Step 7.1: Write `j2cl/lit/src/tokens/shell-tokens.css`**

Use the concrete token values captured from Step 3.2's design-system response (do not invent values; copy the hex/length strings returned by Stitch for each design-packet §4 slot). The structure below shows the exact slot names the shell consumes; fill in each value from the Stitch design-system payload.

```css
/* CSS custom properties implementing design-packet §4 token slots for the shell family.
 * Values pulled verbatim from the Stitch design system pinned in slice packet §5. */
:root {
  --shell-color-surface-page: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-surface-shell: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-text-primary: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-text-muted: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-divider-subtle: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-accent-brand: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-color-accent-focus: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-space-inline-default: <VALUE FROM STITCH DESIGN SYSTEM>;
  --shell-space-inset-panel: <VALUE FROM STITCH DESIGN SYSTEM>;
}
```

This file is bundled as a real CSS output (`war/j2cl/assets/shell.css`) by esbuild's second entry point (Step 2.3), so the server HTML can load it via `<link>` and render styled chrome **without JavaScript**.

- [ ] **Step 7.2: Rewrite `j2cl/lit/src/index.js`**

Wire the inline-shell adapter onto `window.__litShellInput` so elements read bootstrap data through the adapter from day one. Post-#963, Task 9 swaps this single line to the JSON variant without touching any element.

```javascript
// Entry point for the SupaWave Lit shell bundle (issue #964).
import { createInlineShellInput } from "./input/inline-shell-input.js";
import "./elements/shell-skip-link.js";
import "./elements/shell-header.js";
import "./elements/shell-nav-rail.js";
import "./elements/shell-main-region.js";
import "./elements/shell-status-strip.js";
import "./elements/shell-root.js";
import "./elements/shell-root-signed-out.js";

window.__litShellInput = createInlineShellInput(window);
```

The `shell-tokens.css` file is emitted as a standalone CSS asset by esbuild (Step 2.3); it is **not** imported from JS so it ships regardless of whether the JS bundle loads.

- [ ] **Step 7.3: Build and inspect the bundle**

```bash
cd j2cl/lit && npm run build && cd ../..
head -c 200 war/j2cl/assets/shell.js
```

Expected: minified ESM starting with `import` or bundled definitions.

- [ ] **Step 7.4: Commit**

```bash
git add j2cl/lit/src/index.js j2cl/lit/src/tokens/shell-tokens.css
git commit -m "feat(#964): wire shell primitives and tokens into bundle entry"
```

### Task 8: Replace the server-rendered shell HTML with progressive-enhancement markup

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java`

- [ ] **Step 8.1: Write the failing server-side assertions**

Open `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java` and add these test methods (leave existing tests in place):

```java
  @Test
  public void signedInPageUsesLitCustomElements() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    session.put("role", "user");
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");
    assertThat(html).contains("<shell-root>");
    assertThat(html).contains("<shell-header signed-in");
    assertThat(html).contains("<shell-nav-rail");
    assertThat(html).contains("<shell-main-region");
    assertThat(html).contains("<shell-status-strip");
    assertThat(html).contains("<shell-skip-link");
    assertThat(html).contains("/j2cl/assets/shell.js");
    assertThat(html).contains("/j2cl/assets/shell.css");
  }

  @Test
  public void signedOutPageUsesSignedOutRoot() {
    JSONObject session = new JSONObject();
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");
    assertThat(html).contains("<shell-root-signed-out>");
    assertThat(html).doesNotContain("<shell-root>");
  }

  @Test
  public void legacyShellClassesAreNoLongerEmitted() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");
    assertThat(html).doesNotContain("j2cl-root-shell-banner");
    assertThat(html).doesNotContain("j2cl-root-shell-nav");
    assertThat(html).doesNotContain("j2cl-root-shell-pill");
  }

  @Test
  public void fallbackMarkupIsReadableWithoutJs() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");
    assertThat(html).contains("id=\"j2cl-root-shell-workflow\"");
    assertThat(html).contains("data-j2cl-root-shell-workflow=\"true\"");
    assertThat(html).contains("Skip to main content");
  }
```

- [ ] **Step 8.2: Run the failing tests**

```bash
sbt -batch "testOnly org.waveprotocol.box.server.rpc.HtmlRendererJ2clRootShellTest"
```

Expected: 4 new failures; the existing tests may also fail once the renderer is rewritten (they get updated in Step 8.4).

- [ ] **Step 8.3: Rewrite `renderJ2clRootShellPage(...)` in `HtmlRenderer.java`**

Replace the entire method body with a progressive-enhancement implementation. The new body:

1. Keeps the existing signature, the inline `__session` + `__websocket_address` script block, the analytics fragment, the build/commit meta tags, the same escape helpers, and the same normalized return-target and base-path logic that currently exists.
2. Replaces the `<main class="j2cl-root-shell">` block plus all inline `<style>` CSS and the StringBuilder JS bootstrap with the markup below. Keep the existing `<script src="/j2cl-search/sidecar/j2cl-sidecar.js"></script>` for the signed-in case and the existing `mountWhenReady` bootstrap tail so `J2clRootShellController` continues to mount.
3. Adds two new tags in `<head>`:

```
<link rel="stylesheet" href="/j2cl/assets/shell.css">
<script type="module" src="/j2cl/assets/shell.js"></script>
```

`type="module"` scripts are deferred by default; no `defer` attribute is needed (and `defer` is ignored on module scripts).

4. Replaces `<main class="j2cl-root-shell">...</main>` with:

For signed-in:

```
<shell-root>
  <shell-skip-link slot="skip-link" target="#j2cl-root-shell-workflow" label="Skip to main content"></shell-skip-link>
  <shell-header slot="header" signed-in>
    <a slot="brand" href="{escapedReturnTarget}" aria-label="J2CL root shell">
      <span aria-hidden="true">J2</span><span>SupaWave J2CL Root Shell</span>
    </a>
    <span slot="actions-signed-in">Signed in as {escapedAddress}</span>
    {ADMIN_LINK_OR_EMPTY}
    <a slot="actions-signed-in" data-j2cl-root-signout="true" href="/auth/signout?r={encodedReturnTarget}">Sign out</a>
  </shell-header>
  <shell-nav-rail slot="nav" label="Primary">
    <a href="{escapedReturnTarget}">Inbox</a>
  </shell-nav-rail>
  <shell-main-region slot="main">
    <section id="j2cl-root-shell-workflow" data-j2cl-root-shell-workflow="true">
      <p data-j2cl-fallback="true">The hosted J2CL workflow is loading and will mount here shortly.</p>
    </section>
  </shell-main-region>
  <shell-status-strip slot="status">Return target: {escapedReturnTarget}</shell-status-strip>
</shell-root>
```

For signed-out:

```
<shell-root-signed-out>
  <shell-skip-link slot="skip-link" target="#j2cl-root-shell-workflow" label="Skip to main content"></shell-skip-link>
  <shell-header slot="header">
    <a slot="brand" href="{escapedReturnTarget}" aria-label="J2CL root shell">
      <span aria-hidden="true">J2</span><span>SupaWave J2CL Root Shell</span>
    </a>
    <span slot="actions-signed-out">Signed out</span>
    <a slot="actions-signed-out" data-j2cl-root-signin="true" href="/auth/signin?r={encodedReturnTarget}">Sign in</a>
  </shell-header>
  <shell-main-region slot="main">
    <section id="j2cl-root-shell-workflow" data-j2cl-root-shell-workflow="true">
      <p data-j2cl-fallback="true">After sign-in, the hosted J2CL workflow will mount here without leaving the shell.</p>
    </section>
  </shell-main-region>
  <shell-status-strip slot="status">Return target: {escapedReturnTarget}</shell-status-strip>
</shell-root-signed-out>
```

Placeholder rules:

- `{escapedAddress}` — `StringEscapeUtils.escapeHtml4(address)`.
- `{escapedReturnTarget}` — `StringEscapeUtils.escapeHtml4(resolvedReturnTarget)`.
- `{encodedReturnTarget}` — the existing `encodeLocalReturnTarget(...)` helper result, then `StringEscapeUtils.escapeHtml4(...)`.
- `{ADMIN_LINK_OR_EMPTY}` — when `HumanAccountData.ROLE_ADMIN` or `HumanAccountData.ROLE_OWNER` matches, emit the literal element below on its own line; when neither matches, emit **nothing at all** (not an empty element, not a whitespace-only line — the StringBuilder branch appends exactly zero characters, matching the pre-#964 behavior for non-admin users):

```
<a slot="actions-signed-in" data-j2cl-root-admin-link="true" href="/admin">Admin</a>
```

Delete every StringBuilder line that emitted inline `<style>` rules with `.j2cl-root-shell-*` classes. Re-attach the existing `mountWhenReady`/`syncReturnTargetUi`/`normalizeLegacyHashDeepLink`/`hookHistory` bootstrap JS for the signed-in path into a new private helper `appendJ2clRootShellSignedInBootstrap(StringBuilder sb, String safeResolvedReturnTarget, String safeResolvedBasePath)`. Inside that helper, update the `mountWhenReady` body to clear any `[data-j2cl-fallback]` element inside the workflow region **before** calling `entryPoint.mount(...)`:

```javascript
function clearFallback(){
  var workflow=document.getElementById('j2cl-root-shell-workflow');
  if(!workflow){return;}
  workflow.querySelectorAll('[data-j2cl-fallback]').forEach(function(node){node.remove();});
}
function mountWhenReady(attemptsRemaining){
  var entryPoint=resolveEntryPoint();
  if(entryPoint){clearFallback();entryPoint.mount('j2cl-root-shell-workflow','root-shell');return;}
  if(attemptsRemaining>0){window.setTimeout(function(){mountWhenReady(attemptsRemaining-1);},50);}else{renderLoadError();}
}
```

This removes the fallback `<p>` before the workflow mounts so the controller never races against dead text, and preserves the existing error-path behavior via `renderLoadError()`.

- [ ] **Step 8.4: Update the pre-existing tests in `HtmlRendererJ2clRootShellTest` that asserted the legacy classes**

For every existing assertion that says `assertThat(html).contains("j2cl-root-shell-banner")` (or any other legacy class name listed in Step 8.1's negative assertions), either delete the assertion or replace it with the equivalent custom-element assertion.

- [ ] **Step 8.5: Run all tests, including the existing sign-in/sign-out cases**

```bash
sbt -batch "testOnly org.waveprotocol.box.server.rpc.HtmlRendererJ2clRootShellTest"
```

Expected: all pass.

- [ ] **Step 8.6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java
git commit -m "feat(#964): rewrite J2CL root-shell HTML with Lit custom elements and progressive-enhancement fallback"
```

### Task 9 (GATED ON #963 MERGE — DO NOT EXECUTE EARLY): Rewire `LitShellInput` to the bootstrap JSON contract

**Precondition before ticking Task 9 steps:** PR #980 must be merged into `main` and `git pull` into this worktree.

**Files:**
- Create: `j2cl/lit/src/input/json-shell-input.js`
- Create: `j2cl/lit/test/json-shell-input.test.js`
- Modify: `j2cl/lit/src/index.js`

- [ ] **Step 9.1: Pull the merged `J2clBootstrapContract` contract into the worktree**

```bash
git fetch origin main && git rebase origin/main
cat wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java | head -40
```

Expected: the contract file exists.

- [ ] **Step 9.2: Write the failing `j2cl/lit/test/json-shell-input.test.js`**

```javascript
import { expect } from "@open-wc/testing";
import { createJsonShellInput } from "../src/input/json-shell-input.js";

describe("json-shell-input", () => {
  it("reads the bootstrap contract payload from window.__bootstrap", () => {
    window.__bootstrap = {
      session: { address: "a@b.c", role: "owner", domain: "b.c", idSeed: "s", features: [] },
      websocketAddress: "ws.example:443",
    };
    const snap = createJsonShellInput(window).read();
    expect(snap.signedIn).to.equal(true);
    expect(snap.role).to.equal("owner");
    expect(snap.websocketAddress).to.equal("ws.example:443");
  });
});
```

- [ ] **Step 9.3: Run to fail** — `cd j2cl/lit && npm test && cd ../..` — FAIL (module missing)

- [ ] **Step 9.4: Write `j2cl/lit/src/input/json-shell-input.js`**

```javascript
import { EMPTY_SNAPSHOT } from "./lit-shell-input.js";

const ALLOWED_ROLES = new Set(["admin", "owner", "user"]);
function normalizeRole(raw) { return ALLOWED_ROLES.has(raw) ? raw : "user"; }

export function createJsonShellInput(win) {
  return {
    read() {
      const bootstrap = win.__bootstrap;
      if (!bootstrap || typeof bootstrap !== "object") return EMPTY_SNAPSHOT;
      const session = bootstrap.session || {};
      const address = typeof session.address === "string" ? session.address : "";
      return {
        signedIn: address.length > 0,
        address,
        role: normalizeRole(session.role),
        domain: typeof session.domain === "string" ? session.domain : "",
        idSeed: typeof session.idSeed === "string" ? session.idSeed : "",
        features: Array.isArray(session.features) ? [...session.features] : [],
        websocketAddress: typeof bootstrap.websocketAddress === "string" ? bootstrap.websocketAddress : "",
      };
    },
  };
}
```

- [ ] **Step 9.5: Run to pass**

- [ ] **Step 9.6: Swap the adapter in `j2cl/lit/src/index.js`**

```javascript
// Swap the inline adapter for the JSON-contract adapter now that #963 has merged.
import { createJsonShellInput } from "./input/json-shell-input.js";
window.__litShellInput = createJsonShellInput(window);
```

(If any element already reads `window.__litShellInput`, the swap is complete. If a given element still reads the inline global directly, refactor it to call `window.__litShellInput.read()` in the same commit.)

- [ ] **Step 9.7: Run the full gate**

```bash
sbt -batch j2clLitBuild j2clLitTest j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

- [ ] **Step 9.8: Commit**

```bash
git add j2cl/lit/src/input/json-shell-input.js j2cl/lit/test/json-shell-input.test.js j2cl/lit/src/index.js
git commit -m "feat(#964): swap Lit shell adapter to #963 bootstrap JSON contract"
```

**Scope fence:** Task 9 is **client-adapter-only**. Server-side emission of the `window.__bootstrap` payload (or the `/j2cl-bootstrap` fetch wiring) is #963/#965 scope and MUST NOT be edited here. If the merged #963 ships server-side emission of `window.__bootstrap`, Task 9 is sufficient as-is. If it does not, the adapter swap still lands but the payload remains empty until #965 ships the server-side half; that is acceptable because the pre-#963 inline `window.__session` globals remain in place in `HtmlRenderer` for rollback.

### Task 10: Wire `j2clLitBuild` and `j2clLitTest` into sbt

**Files:**
- Modify: `build.sbt`

- [ ] **Step 10.1: Read the current `j2clRuntimeBuild` body so the wiring append is additive, not a rewrite**

```bash
sed -n '820,950p' build.sbt
```

Record the exact current body of `ThisBuild / j2clRuntimeBuild` (around line 944). The snippet below assumes that body is the two-step `Def.sequential(j2clSearchBuild, j2clProductionBuild)` form shown in the grep output of Task 1. If the current body does anything else (copies staging dirs, asset placement, cleanup), **preserve those side effects** and append `j2clLitBuild` to the sequence rather than replacing the whole body.

- [ ] **Step 10.2: Add the task definitions and wiring**

Open `build.sbt` and insert near the other `j2cl*` task keys (around line 826):

```scala
lazy val j2clLitBuild = taskKey[Unit]("Build the Lit shell bundle into war/j2cl/assets via npm")
lazy val j2clLitTest  = taskKey[Unit]("Run the Lit shell web-test-runner suite via npm")
```

Near the other `ThisBuild /` task bodies (around line 914), add:

```scala
ThisBuild / j2clLitBuild := {
  val log = streams.value.log
  val base = (ThisBuild / baseDirectory).value
  val litDir = base / "j2cl" / "lit"
  if (!(litDir / "node_modules").exists()) {
    val ci = scala.sys.process.Process(Seq("npm", "ci"), litDir) ! log
    if (ci != 0) sys.error(s"[j2cl-lit] npm ci failed with exit code $ci")
  }
  val build = scala.sys.process.Process(Seq("npm", "run", "build"), litDir) ! log
  if (build != 0) sys.error(s"[j2cl-lit] npm run build failed with exit code $build")
}

ThisBuild / j2clLitTest := {
  val log = streams.value.log
  val base = (ThisBuild / baseDirectory).value
  val litDir = base / "j2cl" / "lit"
  if (!(litDir / "node_modules").exists()) {
    val ci = scala.sys.process.Process(Seq("npm", "ci"), litDir) ! log
    if (ci != 0) sys.error(s"[j2cl-lit] npm ci failed with exit code $ci")
  }
  val t = scala.sys.process.Process(Seq("npm", "test"), litDir) ! log
  if (t != 0) sys.error(s"[j2cl-lit] npm test failed with exit code $t")
}
```

Update the existing `j2clRuntimeBuild` around line 944 to include `j2clLitBuild`:

```scala
ThisBuild / j2clRuntimeBuild := Def.sequential(
  ThisBuild / j2clSearchBuild,
  ThisBuild / j2clProductionBuild,
  ThisBuild / j2clLitBuild
)
```

- [ ] **Step 10.3: Run the sbt tasks to verify wiring**

```bash
sbt -batch j2clLitBuild
```

Expected: clean exit; `war/j2cl/assets/shell.js` and `war/j2cl/assets/shell.css` both present.

```bash
sbt -batch j2clLitTest
```

Expected: all Lit unit tests green (7 element suites + 2 adapter suites).

- [ ] **Step 10.4: Commit**

```bash
git add build.sbt
git commit -m "build(#964): wire j2clLitBuild/j2clLitTest into runtime build graph"
```

### Task 11: Add changelog fragment

**Files:**
- Create: `wave/config/changelog.d/2026-04-23-lit-root-shell.json`

- [ ] **Step 11.1: Write the fragment**

```json
{
  "version": "next",
  "date": "2026-04-23",
  "type": "feat",
  "scope": "j2cl",
  "summary": "Lit root shell and shared chrome primitives",
  "description": "Introduce the first Lit package (j2cl/lit/) with reusable shell/chrome primitives (shell-root, shell-root-signed-out, shell-header, shell-nav-rail, shell-main-region, shell-status-strip, shell-skip-link). The J2CL coexistence seam (?view=j2cl-root and the j2cl-root-bootstrap feature flag) now renders these primitives with progressive-enhancement HTML, preserving the default / route on the legacy GWT shell. Issue #964.",
  "issue": 964
}
```

- [ ] **Step 11.2: Regenerate and validate the changelog**

```bash
sbt -batch changelogAssemble
python3 scripts/validate-changelog.py
```

Expected: `wave/config/changelog.json` updated; validator clean.

- [ ] **Step 11.3: Commit**

```bash
git add wave/config/changelog.d/2026-04-23-lit-root-shell.json wave/config/changelog.json
git commit -m "docs(#964): add changelog fragment for Lit root shell"
```

### Task 12: Full gate + local smoke verification

- [ ] **Step 12.1: Run the cross-path gate**

```bash
sbt -batch j2clLitBuild j2clLitTest j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

Expected: green.

- [ ] **Step 12.2: Run the local smoke**

```bash
bash scripts/worktree-boot.sh --port 9964
# wait for printed helper commands, then:
PORT=9964 bash scripts/wave-smoke.sh start
PORT=9964 bash scripts/wave-smoke.sh check
```

Expected: server up.

- [ ] **Step 12.3: Route and asset assertions**

```bash
curl -fsS http://localhost:9964/ | grep -F "webclient/webclient.nocache.js"
curl -fsS "http://localhost:9964/?view=j2cl-root" | grep -F "<shell-root"
curl -fsS "http://localhost:9964/?view=j2cl-root" | grep -F "/j2cl/assets/shell.js"
curl -fsS "http://localhost:9964/?view=j2cl-root" | grep -F "/j2cl/assets/shell.css"
curl -fsS "http://localhost:9964/?view=j2cl-root" | grep -F "Skip to main content"
curl -fsSI http://localhost:9964/j2cl/assets/shell.js | head -1
curl -fsSI http://localhost:9964/j2cl/assets/shell.css | head -1
```

Expected: the five body greps return a non-empty line; both asset HEAD calls return `HTTP/1.1 200 OK`.

- [ ] **Step 12.4: Browser verification**

Register one fresh local user in the worktree-backed file-store, then:

- Open `http://localhost:9964/` signed-out → legacy landing/GWT renders as before.
- Open `http://localhost:9964/?view=j2cl-root` signed-out → new Lit signed-out shell renders; "Sign in" link preserves the `view=j2cl-root` return target.
- Sign in → Lit signed-in shell renders; the existing search/selected-wave/compose workflow mounts inside `<shell-main-region>`; skip link is the first tab stop and jumps focus to the workflow region.
- Open `http://localhost:9964/` signed-in with `j2cl-root-bootstrap` flag ON → Lit signed-in shell renders as default.
- Verify no unstyled flash by throttling JS with DevTools "Slow 3G" and reloading `/?view=j2cl-root`; the server HTML fallback should render fully before Lit upgrades, and upgrade should not cause a visible reflow of the skip link, header, or workflow region.
- In DevTools console, assert the workflow host is unique and the fallback text has been cleared after mount:

  ```javascript
  document.querySelectorAll('#j2cl-root-shell-workflow').length   // expect 1
  document.querySelectorAll('[data-j2cl-fallback]').length         // expect 0 after mount
  ```

- [ ] **Step 12.5: Stop the server**

```bash
PORT=9964 bash scripts/wave-smoke.sh stop
```

- [ ] **Step 12.6: Record evidence in the issue**

Post a comment on issue #964 with: worktree path, plan path, commit SHAs since Task 1, the four curl outputs, and browser-verification summary.

## 8. Risks / Edge Cases

- **Lit bundle fails to load on first paint.** The progressive-enhancement server HTML must remain legible without JS. Tested by Step 12.4 with JS throttling; assertion in `fallbackMarkupIsReadableWithoutJs`.
- **Double-mount.** Both the existing StringBuilder bootstrap JS (`mountWhenReady`) and Lit upgrade could fight over the workflow host. `shell-main-region` renders the `#j2cl-root-shell-workflow` element inside its template with a `<slot>`; the server HTML emits the same element as a child so both the light-DOM child and the light-DOM slot target the same id. Verify no duplicate `#j2cl-root-shell-workflow` exists in the rendered DOM (`document.querySelectorAll('#j2cl-root-shell-workflow').length === 1`) in the browser step.
- **Legacy class collisions with `sidecar.css`.** `sidecar.css` ships its own `.j2cl-root-shell*` rules; once the legacy classes are removed from the renderer, those rules become dead. Leave them in `sidecar.css` for one slice to keep the sidecar route unchanged; plan to delete them in a follow-up once `#965` lands.
- **Stitch artifact drift.** If the Stitch project is updated after pinning, the design packet §6 gate requires a packet amendment PR, not a silent re-pin. Task 3.4 pins the ids explicitly to make drift visible.
- **npm install in CI.** The `j2clLitBuild` sbt task depends on `npm ci` succeeding. CI runners must have Node ≥20 and network access to the npm registry for the initial install. If CI is offline-only, switch to a vendored `node_modules.tar.gz` and cache extraction; that fallback is out of scope for this slice.
- **Cross-origin asset loading.** `/j2cl/assets/shell.js` is served from the same origin as the page; no CORS required. Double-check that no CSP header blocks inline module scripts — the existing `<script type="module">` usage elsewhere in the renderer confirms it is allowed.
- **Rollback under the feature flag.** `j2cl-root-bootstrap` flip-off must leave `/` on the legacy GWT shell with no residual Lit chrome. Assertion: the renderer only emits `<link href="/j2cl/assets/shell.css">` and `<script src="/j2cl/assets/shell.js">` **inside** the `renderJ2clRootShellPage` branch, not in `renderWaveClientPage`.
- **Shell/workflow boundary.** `<shell-main-region>` exposes `#j2cl-root-shell-workflow` as a light-DOM child (not shadow-slotted) so `J2clRootShellController.start()` continues to query for the id at the document level exactly as it does today. Verified by `shell-main-region.test.js`.
- **#963 coupling drift.** If #963 lands and removes `window.__session` in the same PR, Task 9 becomes a precondition for Task 8 rather than a follow-up. Watch PR #980's final diff before merging this plan's Task 8.

## 9. Ordering Relative To The Remaining Queue

1. `#962` (design packet) is already merged.
2. `#963` (PR #980) is in review; Tasks 1–8 land in parallel under #964 without depending on it.
3. `#964` Task 9 runs only after #963 merges.
4. `#965` (server-first first-paint) consumes the shell primitives produced here for its skeleton chrome.
5. `#966` (read-surface) consumes `shell-main-region` as its mount container.
6. `#968`, `#969`, `#970` all inherit the shell family without re-deriving it.

## 10. Handoff / Execution Choice

**"Plan complete and saved to `docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?"**

# Issue #978 Bootstrap JSON Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the temporary J2CL root HTML bootstrap scraping overlap after the `/bootstrap.json` contract has soaked.

**Architecture:** Keep the legacy GWT host page globals because the GWT client still consumes them, but remove the J2CL root-shell overlap globals and all J2CL fallback decoders that read `window.__session` / `window.__websocket_address`. The J2CL Java transport remains JSON-only through `SidecarSessionBootstrap.fromBootstrapJson`, and the Lit shell input remains JSON-only through `createJsonShellInput`.

**Tech Stack:** Java, Jakarta servlet HTML renderer tests, J2CL JVM tests, Lit/Vitest, SBT-only verification, GitHub issue evidence workflow.

---

## File Map

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java`
  - Delete deprecated `fromRootHtml(...)` and its private parser helpers.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
  - Delete root-HTML scraping tests; keep `/bootstrap.json` tests.
- Modify: `j2cl/lit/src/index.js`
  - Remove `createInlineShellInput` import and fallback branch.
- Delete: `j2cl/lit/src/input/inline-shell-input.js`
  - The J2CL Lit bundle no longer reads bootstrap data from inline root globals.
- Delete: `j2cl/lit/test/inline-shell-input.test.js`
  - Covered by `json-shell-input.test.js` after cleanup.
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
  - Remove `var __session` / `var __websocket_address` from `renderJ2clRootShellPage` only.
  - Preserve `renderWaveClientPage` globals for legacy GWT.
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
  - Replace the rollback-overlap assertion with a negative assertion that J2CL root no longer exposes the legacy bootstrap globals.
  - Add or keep a GWT-host assertion elsewhere if needed to prove `renderWaveClientPage` still exposes the GWT globals.
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
  - Replace overlap-window wording with the post-#978 JSON-only contract and rollback notes.
- Add: `wave/config/changelog.d/2026-04-30-j2cl-bootstrap-json-cleanup.json`
  - User-facing behavior changed: J2CL root no longer embeds legacy bootstrap globals.

## Task 1: Red Tests For Removing The Overlap

**Files:**
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
- Modify: `j2cl/lit/src/index.js`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`

- [ ] **Step 1: Replace the J2CL root-shell legacy-global test**

Change `signedInJ2clRootShellStillExposesLegacyBootstrapGlobals` to:

```java
@Test
public void signedInJ2clRootShellDoesNotExposeLegacyBootstrapGlobals() throws Exception {
  WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));
  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletResponse response = mock(HttpServletResponse.class);
  StringWriter body = new StringWriter();
  when(request.getParameter("view")).thenReturn("j2cl-root");
  when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
  when(request.getSession(false)).thenReturn(mock(HttpSession.class));
  when(response.getWriter()).thenReturn(new PrintWriter(body));

  servlet.doGet(request, response);

  String html = body.toString();
  assertFalse(html.contains("var __session = "));
  assertFalse(html.contains("var __websocket_address = "));
}
```

- [ ] **Step 2: Run the server-side red test**

Run:

```bash
sbt --batch "Test/testOnly *WaveClientServletJ2clRootShellTest"
```

Expected before implementation: fail because `renderJ2clRootShellPage` still emits `var __session` and `var __websocket_address`.

- [ ] **Step 3: Delete root-HTML decoder tests**

Remove these tests from `SidecarTransportCodecTest`:

```java
extractSessionBootstrapAddressFromRootHtml
extractSessionBootstrapRejectsMissingAddress
```

Keep all `fromBootstrapJson` coverage intact.

- [ ] **Step 4: Force Lit to JSON-only bootstrap**

Change the end of `j2cl/lit/src/index.js` to:

```javascript
window.__litShellInput = createJsonShellInput(window);
```

This should make `inline-shell-input.test.js` obsolete in Task 3.

## Task 2: Remove Java Root-HTML Scraping

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java`

- [ ] **Step 1: Delete the deprecated method and helpers**

Remove:

```java
@Deprecated
public static SidecarSessionBootstrap fromRootHtml(String html) { ... }
private static String parseWebSocketAddress(String html) { ... }
private static int findMatchingBrace(String html, int objectStart) { ... }
private static int findMatchingStringQuote(String html, int stringStart) { ... }
```

Do not change `fromBootstrapJson(String)`, `extractFeatures(...)`, or host-compatibility helpers.

- [ ] **Step 2: Run transport tests**

Run:

```bash
sbt --batch "Test/testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
```

Expected after implementation: pass; no compile references to `fromRootHtml` remain.

## Task 3: Remove Lit Inline Bootstrap Fallback

**Files:**
- Modify: `j2cl/lit/src/index.js`
- Delete: `j2cl/lit/src/input/inline-shell-input.js`
- Delete: `j2cl/lit/test/inline-shell-input.test.js`

- [ ] **Step 1: Remove the import and fallback**

Delete:

```javascript
import { createInlineShellInput } from "./input/inline-shell-input.js";
```

Keep:

```javascript
import { createJsonShellInput } from "./input/json-shell-input.js";
```

Set:

```javascript
window.__litShellInput = createJsonShellInput(window);
```

- [ ] **Step 2: Delete obsolete inline adapter files**

Delete the source and test:

```bash
git rm j2cl/lit/src/input/inline-shell-input.js j2cl/lit/test/inline-shell-input.test.js
```

- [ ] **Step 3: Run Lit bootstrap tests and build**

Run from `j2cl/lit`:

```bash
npm test -- --files test/json-shell-input.test.js
npm run build
```

Expected: JSON shell input tests and bundle build pass without the inline adapter.

## Task 4: Remove J2CL Root Inline Globals But Preserve GWT

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`

- [ ] **Step 1: Remove only the J2CL root-shell globals**

In `renderJ2clRootShellPage`, delete:

```java
sb.append("<script type=\"text/javascript\">\n");
sb.append("var __session = ")
    .append(escapeInlineJson(resolvedSessionJson.toString()))
    .append(";\n");
sb.append("var __websocket_address = ")
    .append(escapeJsonString(websocketAddress == null ? "" : websocketAddress))
    .append(";\n");
sb.append("</script>\n");
```

Do not touch the same variables in `renderWaveClientPage`; those remain for GWT.

- [ ] **Step 2: Prove the J2CL root no longer exposes globals**

Run:

```bash
sbt --batch "Test/testOnly *WaveClientServletJ2clRootShellTest"
```

Expected: pass after the renderer change.

## Task 5: Runbook And Changelog

**Files:**
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
- Add: `wave/config/changelog.d/2026-04-30-j2cl-bootstrap-json-cleanup.json`

- [ ] **Step 1: Update runbook wording**

Replace the overlap text with:

```markdown
Post-#978, the J2CL sidecar and root shell are JSON-only bootstrap consumers.
The J2CL root shell no longer emits `var __session = ...` or
`var __websocket_address = ...`; use `/bootstrap.json` for all J2CL bootstrap
inspection and automation. The legacy GWT host page still owns its normal
inline bootstrap globals.
```

- [ ] **Step 2: Add changelog fragment**

Create:

```json
{
  "date": "2026-04-30",
  "type": "changed",
  "area": "j2cl",
  "summary": "Removed the temporary J2CL root HTML bootstrap overlap; J2CL now relies on /bootstrap.json only."
}
```

- [ ] **Step 3: Validate changelog**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: both pass.

## Task 6: Final Verification, Review, PR

**Files:**
- All changed files from Tasks 1-5.

- [ ] **Step 1: Run final SBT and Lit gates**

Run:

```bash
sbt --batch "Test/testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
sbt --batch "Test/testOnly *WaveClientServletJ2clRootShellTest *J2clBootstrapServletTest"
sbt --batch compile j2clSearchTest
cd j2cl/lit && npm test -- --files test/json-shell-input.test.js && npm run build
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
git diff --check origin/main...HEAD
```

Expected: all pass.

- [ ] **Step 2: Self-review**

Check:

- No remaining `fromRootHtml` references.
- No J2CL root-shell `var __session` or `var __websocket_address` output.
- GWT `renderWaveClientPage` still emits its legacy globals.
- Runbook no longer says #978 is future overlap cleanup.

- [ ] **Step 3: Claude Opus review attempt**

Run the repo Claude review helper against the branch diff with fallback disabled.
If quota remains blocked, record the exact limit message on #978 and #904.

- [ ] **Step 4: Commit, push, open PR, monitor**

Commit message:

```bash
git commit -m "fix: remove j2cl root html bootstrap overlap"
```

Open a PR linking #978, enable auto-merge, resolve review threads through GraphQL, and monitor until merge.

---

## Self-Review

Spec coverage:
- Removes `SidecarSessionBootstrap.fromRootHtml(...)`: Task 2.
- Removes J2CL root inline `var __session` / `var __websocket_address`: Task 4.
- Updates runbooks/docs to point only at `/bootstrap.json`: Task 5.
- Preserves GWT host bootstrap globals: called out in File Map, Task 4, and Task 6 self-review.
- Leaves `/bootstrap.json` contract proof to `J2clBootstrapServletTest`, not a brittle root-HTML string assertion.

Placeholder scan:
- No TBD/TODO placeholders.
- Every task has concrete file paths and commands.

Type consistency:
- Existing method names are used as currently present: `fromBootstrapJson`, `renderJ2clRootShellPage`, `renderWaveClientPage`, `createJsonShellInput`.
- The plan deliberately avoids inventing new runtime types.

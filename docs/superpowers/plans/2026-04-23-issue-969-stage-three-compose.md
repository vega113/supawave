# Issue #969 StageThree Compose / Toolbar Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the practical daily compose/reply and view/edit toolbar surface into the Lit/J2CL root shell on top of the StageOne read surface and StageTwo root-live surface.

**Architecture:** Treat StageThree parity as a J2CL-owned behavior layer with Lit-owned chrome. Keep write/session state in J2CL, consume the root-live selected-wave/write-basis handoff from `#968`, and render compose/toolbars through focused Lit primitives instead of growing the current manual DOM sidecar views. This issue intentionally covers daily compose/reply and daily toolbar controls only; mention/task/reaction overlays, attachments, and remaining rich-edit edge cases stay in `#970` / `#971`.

**Tech Stack:** J2CL Java under `j2cl/src/main/java`, Lit elements under `j2cl/lit/src/elements`, J2CL transport models under `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport`, selected-wave/live seams from `#936` and `#968`, legacy GWT parity references under `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar`, J2CL JVM tests via `./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar ... test`, Lit web component tests via `npm test -- --runInBand` from `j2cl/lit`, and local browser verification on `/?view=j2cl-root`.

---

## 1. Planning Status And Dependency Gates

This plan is safe to write now, but product implementation is not safe to start from this lane.

- `#936` is closed and available. The atomic selected-wave write-basis rule is already implemented in `J2clSelectedWaveProjector.buildWriteSession(...)`, which only advances `(baseVersion, historyHash)` when both values arrive together.
- `#968` is the hard implementation gate. `#969` must wait until the root-shell live surface lands, because StageThree compose and toolbar state must consume root-owned route, reconnect, selected-wave, fragment, read-state, and status publication seams rather than introducing another controller-local lifecycle.
- `#968` itself is plan-ready but implementation-gated behind `#966` and `#967`. Therefore `#969` is dependency-safe planning only until `#966`, `#967`, and `#968` merge or the team lead explicitly changes the gate.
- Before coding, the implementation lane must rebase this plan onto the post-`#968` branch tip and refresh every file/method reference below, because several names are expected to change when `J2clRootLiveSurfaceController` lands.
- No PR should be opened for this planning lane. The first product PR for `#969` should be opened only after the dependency gate is satisfied and this plan has been revalidated against the merged root-live surface.

## 2. Acceptance Criteria

`#969` is complete when all of the following are true:

- The J2CL root shell exposes a daily-use compose surface for:
  - creating a new plain-text wave,
  - replying to the currently selected/focused daily reply target,
  - seeing draft, submit, success, error, disabled, and stale-basis states.
- Reply submit uses the post-`#936` atomic write basis and the post-`#968` root-live selected-wave handoff; it must not submit against a mismatched version/hash pair or against an old selected wave after route/reconnect changes.
- Daily view toolbar controls are reachable and usable in the Lit/J2CL shell:
  - recent / next unread / previous / next / last navigation,
  - previous mention / next mention when mention ordering exists,
  - archive / inbox, pin / unpin, and history visibility/toggle where the root-live surface provides the backing state.
- Daily edit toolbar controls are reachable and usable for the practical StageThree slice:
  - bold, italic, underline, strikethrough,
  - heading,
  - unordered and ordered lists,
  - text alignment and RTL direction,
  - link create/remove,
  - clear formatting.
- Toolbar controls expose labels, keyboard reachability, pressed/disabled state, and status/error feedback through Lit components and root-shell live regions.
- During root-live reconnect/backoff, controls that require a current write basis are disabled with visible reconnect/stale-basis text; read-only navigation controls remain enabled only when the root-live surface says their target state is current.
- The implementation adds focused unit/component tests and one local browser sanity record proving the root shell can open a wave, expose the compose surface, expose view/edit toolbar controls, and submit or simulate a daily reply path without falling back to legacy GWT.
- No default-root cutover, GWT retirement, attachment upload, mention autocomplete, task metadata overlays, reactions, or full editor-edge-case migration is included.

## 3. Current Baseline Observed In This Worktree

### 3.1 J2CL root-shell composition

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java:21-69`
  - The root controller currently instantiates `J2clSearchPanelView`, `J2clSelectedWaveView`, `J2clSidecarComposeController`, `J2clSelectedWaveController`, `J2clSearchPanelController`, and `J2clSidecarRouteController` directly.
  - The existing compose view is mounted into both `searchView.getComposeHost()` and `selectedWaveView.getComposeHost()`.
  - Reply success currently calls `selectedWaveController.refreshSelectedWave()`, so root-live refresh ownership is not yet separated from selected-wave controller internals.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-40`
  - The root runtime still creates a manual DOM `workflowHost`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:46-61`
  - The only shell status integration in this file today is return-target sync.
  - `#968` is expected to expand this into root-runtime status publication.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3283-3293`
  - Signed-in root-shell HTML mounts the J2CL workflow into `<shell-main-region>` and exposes `<shell-status-strip>`.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3327-3419`
  - Inline bootstrap JS owns legacy hash normalization, history hooks, return-target sync, and bundle mount timing.

### 3.2 Current J2CL compose/write pilot

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:7-18`
  - The controller depends on a gateway that can fetch `/bootstrap.json` and submit `SidecarSubmitRequest` over a short-lived socket.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:52-63`
  - Draft, submit, status, and error state are local fields on the sidecar compose controller.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:166-178`
  - `onWriteSessionChanged(...)` invalidates pending replies on any selected-wave, channel, version/hash, or reply-target change, preserving draft only when the selected wave is unchanged.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:180-241`
  - Create-wave submit is plain-text only and creates a self-owned `conv+root` wave.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:253-333`
  - Reply submit requires a non-null write session, fetches bootstrap again, builds a plain-text reply delta, and refreshes the selected wave on success.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java:21-135`
  - The current view is manual Elemental2 DOM with two textareas and two submit buttons.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java:143-155`
  - Rendering only toggles disabled/hidden/status on those manual DOM nodes.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java:34-71`
  - Create/reply deltas are plain-text `conv+root` operations only.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarWriteSession.java:3-41`
  - The current write basis contains `selectedWaveId`, `channelId`, `baseVersion`, `historyHash`, and `replyTargetBlipId`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:174-211`
  - The write-session basis is derived from selected-wave updates and preserves the atomic version/hash pair after `#936`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:157-220`
  - Submit transport opens a WebSocket, sends an encoded submit frame, handles `ProtocolSubmitResponse`, and treats failed `RpcFinished` as errors.

### 3.3 Current J2CL selected-wave/read surface constraints

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:18-65`
  - The selected-wave view still creates manual DOM card sections and a compose host.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:67-105`
  - It renders title, unread/status/detail/participants/snippet, raw content entries, and empty-state text.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:172-236`
  - Selection changes close subscriptions, reset read-state tracking, fetch bootstrap, and open the selected wave.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:238-370`
  - Reconnect and retry logic is currently selected-wave-controller-local; `#968` should root-scope this before `#969` consumes it.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:400-404`
  - The selected-wave controller publishes write-session changes to the compose controller.

### 3.4 GWT parity targets

- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java:225-350`
  - The GWT view toolbar provides daily navigation and action groups: recent, next unread, previous, next, last, mention navigation, archive, pin, and history.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java:356-483`
  - Archive/inbox and pin/unpin call folder operations and update button state; history visibility is externally controlled.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbarFocusActions.java:46-68`
  - Daily focus navigation delegates recent and next-unread behavior to focus/reader abstractions.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:319-362`
  - The GWT edit toolbar initializes groups for daily formatting, headings, colors, indentation, lists, alignment, direction, links, attachments, and tasks.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:364-450`
  - Bold/italic/underline/strikethrough, superscript/subscript, font size/family, heading, and color controls are editor-context backed.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:501-522`
  - Clear formatting removes style annotations and clears headings.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:639-760`
  - Direction, link create/remove, heading, indentation, lists, and alignment are backed by paragraph/text-selection controllers.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ToolbarSwitcher.java:58-91`
  - GWT swaps view and edit toolbars based on edit-session state.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/ReplyLocationResolver.java:49-86`
  - GWT reply location resolution flushes the active editor before locating the reply insertion point.

### 3.5 Lit design packet targets

- `docs/j2cl-lit-design-packet.md` defines the compose/toolbar primitive family for `#969`: `composer-shell`, `composer-inline-reply`, `toolbar-group`, `toolbar-button`, `toolbar-overflow-menu`, and `composer-submit-affordance`.
- `docs/j2cl-gwt-parity-matrix.md` assigns `R-5.1` compose/reply flow and `R-5.2` view/edit toolbar controls to `#969`.
- `docs/j2cl-parity-issue-map.md:375-400` scopes `#969` to compose/reply flow, view/edit toolbar parity for daily use, and daily compose state visibility.

Phase 0 must refresh every line range in this section before implementation. If any referenced file has moved or changed ownership, the worker must update this plan and the `#969` issue comment before coding.

## 4. Handoff Assumptions From #968

The `#969` implementation lane must verify these assumptions against merged code before editing:

- A root-scoped live-runtime owner exists, expected as `J2clRootLiveSurfaceController` or an equivalent class, and owns bootstrap/session, route/history, selected-wave continuity, reconnect state, read-state/live-state publication, and fragment-policy inputs.
- The root-live controller exposes the currently selected wave, selected digest/read metadata, selected/focused reply target, and write-session basis as one current snapshot or a stable listener contract.
- `J2clSelectedWaveController` no longer acts as the sole owner of reconnect and bootstrap lifecycle. If it still does, `#969` must not add another lifecycle path; pause and update this plan.
- Open-frame viewport hints from `#967` / `#968` are plumbed through selected-wave open, so compose/reply state can coexist with visible-fragment rendering without forcing whole-wave payloads.
- Root-shell status publication uses the existing `shell-status-strip` seam, not a parallel status DOM.
- The route/history handoff covers back/forward and signed-in/out return-target transitions, so compose draft invalidation can key off root route/selection generation rather than parsing URLs itself.
- Signed-out root shell still does not mount the workflow bundle. Compose/toolbars must degrade to signed-out shell chrome and must not assume bootstrap JSON exists before auth.
- The root-live surface either exposes archive/pin/history actions directly or exposes a narrow action adapter where `#969` can add those actions without recreating GWT `FolderOperationService` inside the compose/toolbar layer.
- The root-live/edit surface exposes enough selection and paragraph context for daily edit toolbar actions. If it does not, `#969` may add only a small command interface; if a full editor-state owner is required, stop and defer that work to `#971`.

If any assumption is false after `#968` merges, update this plan before implementation and add a new issue comment explaining the adjusted handoff.

### 4.1 Phase 0 Revalidation Update (2026-04-24)

Post-`#968` code does include `J2clRootLiveSurfaceController` and
`J2clRootLiveSurfaceModel`, but the merged root-live seam is narrower than the
planning assumptions above:

- `J2clRootLiveSurfaceController` owns route URL/state wrapping, selected-wave id
  status publication, return-target sync, and the `shell-status-strip` live
  status seam.
- `J2clSelectedWaveController` still owns bootstrap fetch, selected-wave open,
  reconnect/backoff, viewport-fragment loading, read-state polling, and the
  `WriteSessionListener` publication.
- The current stable write-session handoff for `#969` is therefore
  `J2clSelectedWaveController.WriteSessionListener` plus the projected
  `J2clSidecarWriteSession`, not a root-live snapshot that already includes the
  full write basis.

Adjusted implementation rule for this lane:

- Compose and toolbar code must consume the existing selected-wave write-session
  listener and root-live route/status wrappers; it must not introduce another
  bootstrap/reconnect lifecycle.
- If daily toolbar actions need backing behavior that root-live does not expose,
  add small `#969` command interfaces and disabled/error states instead of
  copying GWT toolbar/editor classes or recreating folder/edit services inside
  the compose/toolbar layer.
- Phase 5 verification must explicitly include the existing selected-wave
  controller/projector tests to guard the post-`#936` atomic write-basis seam.

## 5. File Ownership

### 5.1 New Lit files

- Create: `j2cl/lit/src/elements/composer-shell.js`
  - Top-level compose panel with named slots or properties for create-wave and reply regions.
- Create: `j2cl/lit/src/elements/composer-inline-reply.js`
  - Reply composer state surface: disabled, stale basis, submitting, error, success, reply-target label.
- Create: `j2cl/lit/src/elements/composer-submit-affordance.js`
  - Reusable submit button/status component with accessible busy/error labels.
- Create: `j2cl/lit/src/elements/toolbar-group.js`
  - Responsive grouping container for view/edit toolbar sections.
- Create: `j2cl/lit/src/elements/toolbar-button.js`
  - Button/toggle primitive with `label`, `pressed`, `disabled`, `danger`, and `data-action`.
- Create: `j2cl/lit/src/elements/toolbar-overflow-menu.js`
  - Density fallback for narrow widths; daily controls remain keyboard-reachable.
- Modify: `j2cl/lit/src/index.js`
  - Register the new compose/toolbar elements.

### 5.2 New or modified J2CL behavior files

- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
  - Root-shell compose coordinator. It should replace direct use of `J2clSidecarComposeController` in root-shell mode and consume the root-live snapshot/listener from `#968`.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java`
  - Immutable create/reply state published to the Lit-backed view.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`
  - Thin Elemental2 adapter that renders into the Lit compose custom elements and binds events back to J2CL.
  - Boolean and object state must be set as DOM properties where Lit expects properties, not string attributes; string labels and action ids may be attributes.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceController.java`
  - View/edit toolbar coordinator that maps root-live/read/edit state to daily toolbar actions.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceModel.java`
  - Immutable grouped toolbar model with action ids, labels, state, and visibility.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceView.java`
  - Thin Elemental2 adapter for `toolbar-group`, `toolbar-button`, and `toolbar-overflow-menu`.
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java`
  - Enum or value object for daily actions: recent, next unread, previous, next, last, previous mention, next mention, archive, inbox, pin, unpin, history, bold, italic, underline, strikethrough, heading, unordered list, ordered list, align left/center/right, RTL, link, unlink, clear formatting.
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
  - Stop constructing `J2clSidecarComposeController` directly for root-shell mode once the new compose/toolbar controllers exist.
  - Wire new controllers to the merged `#968` root-live owner.
- Modify only if sidecar compatibility requires it: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java`
  - Preserve sidecar route behavior; do not break `/j2cl-search/index.html`.
  - Default to no extraction. Extract shared create/reply submit logic only if both root-shell and sidecar can use it without coupling root-live state back into sidecar.
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java`
  - Keep existing plain-text create/reply behavior.
  - Add only the minimal operation helpers needed by the daily toolbar action subset that ships in `#969`.

### 5.3 Tests

- Create: `j2cl/lit/test/composer-shell.test.js`
- Create: `j2cl/lit/test/composer-inline-reply.test.js`
- Create: `j2cl/lit/test/composer-submit-affordance.test.js`
- Create: `j2cl/lit/test/toolbar-group.test.js`
- Create: `j2cl/lit/test/toolbar-button.test.js`
- Create: `j2cl/lit/test/toolbar-overflow-menu.test.js`
  - Must include keyboard/focus assertions for open-by-keyboard, focus moving into the opened list, arrow-key or tab-order traversal, and `Escape` close with focus returning to the trigger.
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeControllerTest.java`
  - Keep existing sidecar coverage green if shared logic is extracted.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactoryTest.java`
  - Add daily operation payload coverage only for operations implemented in this issue.

### 5.4 Changelog and verification record for implementation PR

- Create during implementation, not during this planning lane: `wave/config/changelog.d/2026-04-23-issue-969-stage-three-compose.json`
- Create during implementation, not during this planning lane: `journal/local-verification/2026-04-23-issue-969-stage-three-compose.md`

## 6. Phased Implementation Slices

### Phase 0: Revalidate Post-#968 Baseline

**Files:**
- Read: `docs/superpowers/plans/2026-04-23-issue-968-root-live-surface.md`
- Read: merged `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/*`
- Read: merged `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- Read: merged `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- Read: merged `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/*`

- [ ] **Step 1: Confirm dependency state**

Run:

```bash
gh repo view --json nameWithOwner
gh issue view 966 --repo vega113/supawave --json state,title
gh issue view 967 --repo vega113/supawave --json state,title
gh issue view 968 --repo vega113/supawave --json state,title
git fetch origin main
git merge-base --is-ancestor origin/main HEAD || true
```

Expected:

- `gh repo view --json nameWithOwner` reports `vega113/supawave`, or the worker updates the `gh issue view` commands to the actual remote owner/repo before continuing.
- `#966`, `#967`, and `#968` are closed or the team lead has explicitly authorized implementation before closure.
- The implementation branch is based on the post-`#968` integration commit.

- [ ] **Step 2: Refresh the handoff map**

Run:

```bash
rg -n "RootLive|LiveSurface|WriteSession|selectedWave|reconnect|viewport|shell-status|status-strip" j2cl/src/main/java wave/src/jakarta-overrides/java
test -f j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java
test -f j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java
```

Expected:

- One root-live owner is identifiable.
- One selected-wave/write-session publication seam is identifiable.
- The implementation lane updates this plan if names or ownership differ from the assumptions in Section 4.
- The worker refreshes these exact references from Section 3: root-shell composition, root-shell status sync, `HtmlRenderer` root mount/bootstrap, compose controller submit paths, compose view render contract, write-session fields, selected-wave projector write-basis creation, gateway submit transport, selected-wave reconnect/read-state ownership, GWT view-toolbar actions, GWT edit-toolbar actions, `ToolbarSwitcher`, and `ReplyLocationResolver`.
- The selected-wave controller/projector test files still exist at the paths used by Phase 5. If either file moved or was renamed by `#968`, update the Phase 5 test command before continuing.

- [ ] **Step 3: Commit nothing in Phase 0**

Expected:

- If the dependencies are not closed or the handoff does not exist, stop and update issue `#969` with the blocker.

### Phase 1: Add Lit Compose / Toolbar Primitives

**Files:**
- Create: `j2cl/lit/src/elements/composer-shell.js`
- Create: `j2cl/lit/src/elements/composer-inline-reply.js`
- Create: `j2cl/lit/src/elements/composer-submit-affordance.js`
- Create: `j2cl/lit/src/elements/toolbar-group.js`
- Create: `j2cl/lit/src/elements/toolbar-button.js`
- Create: `j2cl/lit/src/elements/toolbar-overflow-menu.js`
- Modify: `j2cl/lit/src/index.js`
- Create tests listed in Section 5.3.

- [ ] **Step 1: Write Lit tests first**

Run from `j2cl/lit`:

```bash
npm test -- --runInBand composer-shell.test.js composer-inline-reply.test.js composer-submit-affordance.test.js toolbar-group.test.js toolbar-button.test.js toolbar-overflow-menu.test.js
```

Expected before implementation:

- FAIL because the new custom elements are not registered.

- [ ] **Step 2: Implement the visual primitives**

Required component behavior:

- `composer-shell` exposes a labeled create section, a labeled reply section, and a root status slot.
- `composer-inline-reply` reflects `available`, `target-label`, `draft`, `submitting`, `stale-basis`, `status`, and `error`.
- `composer-submit-affordance` renders a real `<button>` with busy/disabled state and visible status/error text.
- `toolbar-button` renders a real `<button>` with `aria-label`, `aria-pressed` for toggles, `disabled`, and `data-action`.
- `toolbar-group` exposes a group label and default slot.
- `toolbar-overflow-menu` keeps overflowed actions reachable by keyboard; use a button-triggered menu/list pattern, move focus into the opened list, support arrow-key movement or tab-order traversal consistently, and close on `Escape`.
- Labels should come from a small J2CL message/label source that mirrors the GWT `ToolbarMessages` text where practical. Hard-coded English is acceptable only as a temporary `#969` implementation detail if a follow-up i18n issue/comment is recorded before PR.

- [ ] **Step 3: Re-run Lit tests**

Run:

```bash
cd j2cl/lit
npm test -- --runInBand composer-shell.test.js composer-inline-reply.test.js composer-submit-affordance.test.js toolbar-group.test.js toolbar-button.test.js toolbar-overflow-menu.test.js
```

Expected:

- PASS for the six new component suites.

- [ ] **Step 4: Commit Phase 1**

Commit message:

```bash
git add j2cl/lit/src j2cl/lit/test
git commit -m "feat(j2cl): add Lit compose and toolbar primitives"
```

### Phase 2: Replace Root-Shell Compose Wiring With A Root Compose Surface

**Files:**
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java`
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Modify only if shared logic is extracted: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java`
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`

- [ ] **Step 1: Write compose-controller tests**

Required test cases:

- Initial model shows create enabled, reply unavailable until a root-live write session exists.
- Selecting/opening a wave with a valid write session enables reply and publishes the target label.
- A route/selection generation change invalidates pending reply submit callbacks.
- Same-wave basis refresh preserves the reply draft but clears stale submit state.
- Different-wave selection clears the reply draft.
- Bootstrap failure preserves draft and surfaces root-shell submit error.
- Successful reply clears draft, emits refresh/open action through the root-live handoff, and does not parse route state directly.
- Signed-out/bootstrap-unavailable state disables compose without throwing.
- Root-live signed-out state is a safe no-op: no bootstrap fetch, no submit socket, create/reply disabled, visible signed-out status.
- If selection changes while a submit is in flight, the stale callback is dropped and the user-visible status becomes a stale-basis message instead of a silent success.

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceControllerTest test
```

Expected before implementation:

- FAIL because the new controller/model/view do not exist.

- [ ] **Step 2: Implement root compose controller**

Implementation constraints:

- Consume root-live state from the merged `#968` seam.
- Keep draft state in memory only.
- Use a monotonically increasing generation token for create/reply submit suppression, matching the existing stale-callback pattern in `J2clSidecarComposeController`.
- When a generation token invalidates a pending submit, surface "Selection changed before submit completed. Review the draft and retry." or equivalent stale-basis text while preserving the draft for same-wave changes.
- Build create/reply requests through `J2clPlainTextDeltaFactory` unless a smaller shared submit builder has been extracted.
- Do not introduce URL parameters for compose draft state.
- Do not re-fetch bootstrap independently if `#968` provides a current root session snapshot; if `#968` intentionally exposes only a fetch method, call that method through the root-live owner rather than `J2clSearchGateway` directly.

- [ ] **Step 3: Keep sidecar compatibility green**

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.search.J2clSidecarComposeControllerTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest \
  test
```

Expected:

- Existing sidecar tests pass.
- Any shared extraction preserves `/j2cl-search/index.html` behavior.

- [ ] **Step 4: Commit Phase 2**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/compose j2cl/src/main/java/org/waveprotocol/box/j2cl/root j2cl/src/test/java/org/waveprotocol/box/j2cl/compose j2cl/src/test/java/org/waveprotocol/box/j2cl/search
git commit -m "feat(j2cl): wire root compose surface to live state"
```

### Phase 3: Add Daily View Toolbar Surface

**Files:**
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceController.java`
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceModel.java`
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceView.java`
- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java`

- [ ] **Step 1: Write view-toolbar tests**

Required test cases:

- View toolbar model includes recent, next unread, previous, next, last when a selected wave/read surface exists.
- Mention navigation actions are hidden or disabled until the post-`#966`/`#968` read surface exposes mention order.
- Archive/inbox and pin/unpin map to root-live/folder action seams and show busy/error state.
- History action visibility follows the root-live history permission/state.
- Action dispatch does not call legacy GWT classes.
- All actions have stable action ids and labels.

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest test
```

Expected before implementation:

- FAIL because the toolbar package does not exist.

- [ ] **Step 2: Implement view-toolbar action mapping**

Implementation constraints:

- Map GWT `ViewToolbar` behavior to root-live/read abstractions, not to GWT widget classes.
- If root-live does not expose archive/pin/history actions yet, add a narrow root-live action interface in the `#969` implementation branch and document it in the issue comment.
- Disable unavailable controls visibly instead of hiding the whole toolbar.
- Preserve keyboard reachability; every action must be a native button through `toolbar-button`.

- [ ] **Step 3: Run view-toolbar tests**

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest test
```

Expected:

- PASS for view-toolbar model/action tests.

- [ ] **Step 4: Commit Phase 3**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar j2cl/src/main/java/org/waveprotocol/box/j2cl/root j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar
git commit -m "feat(j2cl): add root view toolbar surface"
```

### Phase 4: Add Daily Edit Toolbar Surface

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceModel.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactoryTest.java`

- [ ] **Step 1: Write edit-toolbar tests**

Required test cases:

- Edit toolbar model includes the daily formatting controls listed in Section 2.
- Toggle controls preserve `pressed`, `disabled`, and current-selection state from the root edit state model.
- Applying bold/italic/underline/strikethrough/list/heading/link/clear-formatting creates the expected operation request or root edit command.
- Submit is blocked with a visible stale-basis error if the selected-wave write session changes during a pending edit action.
- Unsupported `#970`/`#971` actions are absent: mention autocomplete, task metadata overlay, reactions, and attachments.

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest \
  test
```

Expected before implementation:

- FAIL for missing edit actions/operation helpers.

- [ ] **Step 2: Implement daily edit action model**

Implementation constraints:

- Prefer a root edit command interface over copying GWT `EditorContextAdapter`, `ButtonUpdater`, `TextSelectionController`, or `ParagraphApplicationController` into J2CL.
- The root edit command interface must stay small: action id, current selection/write basis generation, optional value, success callback, and error callback. If the implementation needs a persistent editor-state owner, selection model, operation composer, or annotation traversal framework in this issue, stop and move that work to `#971`.
- Keep this issue's formatting subset explicit. If implementing a daily action requires a larger editor model than `#969` owns, leave that action disabled with a documented `#971` follow-up instead of silently shipping partial behavior.
- Do not implement attachment insertion in this phase; it belongs to `#971`.
- Do not implement task metadata overlay in this phase; it belongs to `#970`.
- Do not implement mention autocomplete in this phase; it belongs to `#970`.

- [ ] **Step 3: Run edit-toolbar tests**

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest \
  test
```

Expected:

- PASS for implemented daily edit actions.
- Any intentionally deferred daily-looking action has an explicit disabled state and an issue comment explaining why it is owned by `#970` or `#971`.

- [ ] **Step 4: Commit Phase 4**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactoryTest.java
git commit -m "feat(j2cl): add daily edit toolbar actions"
```

### Phase 5: Root-Shell Integration, Changelog, And Browser Verification

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Modify: `j2cl/src/main/webapp/assets/sidecar.css` only for legacy sidecar/root-shell adapter spacing that affects light-DOM hosts outside the Lit shadow roots. Component styling, toolbar layout, compose state, and overflow styling must live in the Lit component CSS.
- Create: `wave/config/changelog.d/2026-04-23-issue-969-stage-three-compose.json`
- Create: `journal/local-verification/2026-04-23-issue-969-stage-three-compose.md`

- [ ] **Step 1: Run full targeted J2CL/Lit tests**

Run:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceControllerTest,org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest,org.waveprotocol.box.j2cl.search.J2clSidecarComposeControllerTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveProjectorTest \
  test
cd j2cl/lit && npm test -- --runInBand
```

Expected:

- J2CL test run exits 0.
- Lit test run exits 0.

- [ ] **Step 2: Add changelog fragment**

Create a user-facing changelog fragment:

```json
{
  "releaseId": "2026-04-23-issue-969-stage-three-compose",
  "type": "feature",
  "title": "Add J2CL root compose and daily toolbar parity",
  "summary": "The opt-in J2CL root shell now exposes practical compose/reply controls and daily view/edit toolbar actions on top of the live selected-wave surface.",
  "details": [
    "Compose and reply controls show draft, submit, success, error, disabled, and stale-basis state in the J2CL root shell",
    "Daily view and edit toolbar controls are reachable from the Lit/J2CL shell while deeper overlays and attachments remain tracked separately"
  ],
  "issues": ["969"]
}
```

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

Expected:

- Changelog assembly and validation exit 0.
- If validation rejects the fragment schema, inspect current files under `wave/config/changelog.d/`, update the fragment to the current schema, and record that adjustment in the issue comment.

- [ ] **Step 3: Run local root-shell browser sanity**

Use the repo's standard local smoke flow from the implementation worktree. If the local server requires file-store state, run:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Then run the local server smoke command:

```bash
PORT=9900 bash scripts/wave-smoke.sh start
```

If either script is absent after rebasing onto post-`#968`, stop and read `docs/runbooks/worktree-lane-lifecycle.md` plus `docs/runbooks/browser-verification.md`, then update this verification step and the issue comment with the replacement command before running browser verification.

Browser verification target:

```text
http://localhost:9900/?view=j2cl-root
```

Manual/browser assertions:

- The signed-in root shell mounts the J2CL workflow inside `<shell-main-region>`.
- Opening a wave shows the selected-wave read surface and root-live status.
- Create-wave composer is visible and labeled.
- Reply composer becomes available only after a write-session-capable selected wave opens.
- Submitting a reply either succeeds and refreshes the selected wave or surfaces a visible error while preserving draft.
- View toolbar controls are keyboard reachable and visibly disabled/enabled according to state.
- Edit toolbar controls are keyboard reachable, expose labels/pressed state, and do not show attachment/task/reaction/mention-autocomplete controls as completed `#969` scope.
- Overflow menu opens by keyboard, moves focus predictably, and closes with `Escape`.
- Legacy `/` still serves GWT unless the existing explicit opt-in flag says otherwise.

Record exact commands and results in:

```text
journal/local-verification/2026-04-23-issue-969-stage-three-compose.md
```

- [ ] **Step 4: Commit Phase 5**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/root j2cl/src/main/webapp/assets/sidecar.css wave/config/changelog.d/2026-04-23-issue-969-stage-three-compose.json wave/config/changelog.json journal/local-verification/2026-04-23-issue-969-stage-three-compose.md
git commit -m "feat(j2cl): integrate compose toolbar parity in root shell"
```

## 7. Verification Commands For The Implementation Lane

Run these before opening the product PR:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceControllerTest,org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceControllerTest,org.waveprotocol.box.j2cl.search.J2clSidecarComposeControllerTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveProjectorTest \
  test
```

```bash
cd j2cl/lit && npm test -- --runInBand
```

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

```bash
PORT=9900 bash scripts/wave-smoke.sh start
```

Then perform browser verification against:

```text
http://localhost:9900/?view=j2cl-root
```

If tests fail because `wave/config/changelog.json` is missing or stale, run `python3 scripts/assemble-changelog.py` before assuming product code is broken.

## 8. Risks And Mitigations

- **Risk: #968 lands with different root-live seams than assumed.** Mitigation: Phase 0 is a hard revalidation gate; update this plan and issue `#969` before coding.
- **Risk: toolbar parity expands into a full editor rewrite.** Mitigation: daily action list is explicit; mention/task/reaction overlays stay in `#970`; attachments and remaining rich-edit daily gaps stay in `#971`.
- **Risk: copying GWT editor classes creates J2CL-incompatible dependencies.** Mitigation: expose root edit commands and small operation helpers instead of porting `EditorContextAdapter`, GWT widgets, or deferred-binding toolbar controllers.
- **Risk: root compose duplicates bootstrap/reconnect ownership.** Mitigation: compose must consume the root-live session/state owner from `#968`; independent bootstrap fetch is allowed only if `#968` exposes that as the root-owned API.
- **Risk: sidecar route regresses while root-shell compose changes.** Mitigation: keep `J2clSidecarComposeControllerTest` and sidecar search tests in the targeted suite whenever shared logic is extracted.
- **Risk: mobile/narrow toolbar loses actions.** Mitigation: `toolbar-overflow-menu` must be keyboard reachable and tested; do not rely on hidden controls without an overflow route.
- **Risk: local browser verification becomes too broad.** Mitigation: verify only `/?view=j2cl-root` compose/toolbar reachability and one daily reply path; leave overlay/attachment scenarios for later issues.
- **Risk: labels ship as untracked English strings.** Mitigation: use a small J2CL label/message source that mirrors existing GWT copy where practical, or record a follow-up before PR if temporary strings are unavoidable.

## 9. Explicit Non-Goals

- No implementation work in this planning lane.
- No PR from this planning lane.
- No default `/` root cutover.
- No legacy GWT retirement.
- No mention autocomplete implementation.
- No task metadata overlay implementation.
- No reaction overlay implementation.
- No attachment upload/download implementation.
- No full rich-text editor parity beyond the daily toolbar subset listed in Section 2.
- No whole-wave fallback for large waves.
- No new rollout flag unless the `#969` implementation review proves the existing `j2cl-root-bootstrap` coexistence seam is insufficient.

## 10. Self-Review Checklist For This Plan

- [x] Dependency gates are explicit: `#969` implementation waits for `#968`, and `#968` waits for `#966` / `#967`, unless the team lead explicitly changes the gate in the issue trail.
- [x] Acceptance criteria distinguish `#969` from `#970` and `#971`.
- [x] File/method references are grounded in current code line ranges.
- [x] The current #936 atomic write-basis seam is consumed, not replaced.
- [x] The plan does not modify product code in this lane.
- [x] The plan includes architecture seams, file ownership, implementation slices, test commands, local browser verification, risks, non-goals, and #968 handoff assumptions.
- [x] The implementation plan preserves sidecar compatibility and legacy GWT default-root behavior.

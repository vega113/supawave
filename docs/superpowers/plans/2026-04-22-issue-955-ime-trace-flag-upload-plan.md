# Issue #955 Plan: IME Trace Feature-Flag Enablement And Remote Upload

## Scope

- Issue: [#955](https://github.com/vega113/supawave/issues/955)
- Branch: `issue-955-ime-trace-flag-upload`
- Worktree: `/Users/vega/devroot/worktrees/issue-955-ime-trace-flag-upload`

## Root Cause

- `ImeDebugTracer` currently enables itself only from `?ime_debug=on` and `localStorage.ime_debug`, so admins cannot turn it on per user through the repo's feature-flag system.
- The server already has `RemoteLoggingJakartaServlet`, but the current tree no longer mounts the historical `/webclient/remote_logging` route, and there is no client uploader in the tracer.
- The existing per-user flag path already exists end to end for other features:
  `FeatureFlagService` -> `WaveClientServlet` session JSON -> `Session.hasFeature(...)`.

## Intended Fix

- Add a dedicated known feature flag for the IME tracer so admins can allowlist individual users.
- Update `ImeDebugTracer` to enable when either:
  - the feature flag is present in `Session.get().hasFeature(...)`, or
  - the legacy local override (`?ime_debug=on` / `localStorage`) is on.
- Make the tracer re-check the feature-flag source lazily until it becomes available so an early `isEnabled()` call does not permanently miss the session flag.
- Keep the existing console + overlay sinks, and add a best-effort same-origin uploader that POSTs `text/plain;charset=utf-8` batches to `/webclient/remote_logging`.
- Preserve per-line readability on the server side by logging uploaded trace lines individually rather than summarizing a whole multi-line batch into one truncated line.
- Restore the exact remote logging servlet mapping at `/webclient/remote_logging`.

## Files Likely Touched

- `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/debug/ImeDebugTracer.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/RemoteLoggingJakartaServlet.java`
- `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/FeatureFlagServiceTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/RemoteLoggingJakartaServletTest.java`
- `wave/config/changelog.d/*`

## Acceptance Criteria

- [ ] A named IME tracer feature flag exists in the admin feature-flag catalog.
- [ ] A per-user allowlist entry for that flag causes the current user session to enable the tracer without manual URL/localStorage steps.
- [ ] Tracer lines can be forwarded to the server via the existing remote logging servlet route.
- [ ] Uploaded trace batches retain individual trace lines in server logs with authenticated-user context.
- [ ] Existing local override behavior continues to work as a fallback.
- [ ] Automated tests cover the new flag availability and the remote logging endpoint behavior touched by the change.
- [ ] Local verification proves the route exists and the app still boots with the tracer disabled by default.

## Test Plan

- [ ] `sbt "wave/testOnly org.waveprotocol.box.server.persistence.memory.FeatureFlagServiceTest org.waveprotocol.box.server.rpc.RemoteLoggingJakartaServletTest"`
- [ ] Add and run any extra focused test if the route restoration or session-feature visibility needs one.
- [ ] `python3 scripts/assemble-changelog.py`
- [ ] `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`

## Local Verification

- [ ] Start the app from the worktree on a non-conflicting port.
- [ ] Confirm exact-path `/webclient/remote_logging` is mounted, not shadowed by `/webclient/*`, and requires auth.
- [ ] Confirm the main app still loads normally with tracing off by default.
- [ ] Confirm a flagged user session exposes the new flag to the GWT client and that tracer uploads reach server logs.

## Out Of Scope

- Broader editor behavior fixes for Android IME corruption itself.
- General-purpose client logging redesign beyond the IME tracer path.

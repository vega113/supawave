# Issue 1212: restore production J2CL asset packaging

## Problem

Production deploy `c7b3267427fce878e0d681b19ceb5cd4eed88d29` served the
J2CL root shell while omitting the J2CL runtime assets from the deployed image.
The browser then requested `/j2cl/assets/*.css`, `/j2cl/assets/shell.js`, and
`/j2cl-search/sidecar/j2cl-sidecar.js`; those paths returned 404 or HTML, which
triggered strict MIME failures.

The emergency rollback was attempted first. The standalone rollback workflow
failed before host access because the host fingerprint variable is unset, then
`deploy-contabo.yml` with `action=rollback` succeeded and restored the previous
green slot.

## Root Cause

PR #1211 made J2CL asset staging opt-in to stop stale local J2CL output from
leaking into default `Universal/stage` builds. That part is valid for default
developer builds.

The production deploy workflow was not updated to opt in. It still runs:

```bash
sbt --batch "pst/compile; wave/compile; Universal/stage"
```

Because `Universal/stage` now cleans J2CL output unless the build explicitly
requests it, the production image can omit assets that the J2CL root shell still
references.

## Plan

1. Add a failing contract test that production deploy builds must opt in to the
   J2CL runtime asset build and must verify the staged J2CL asset paths before
   building the Docker image.
2. Update `.github/workflows/deploy-contabo.yml` so deploy builds run
   `j2clRuntimeBuild` with `WAVE_STAGE_INCLUDE_J2CL_ASSETS=1` before
   `Universal/stage`.
3. Add a staged asset verification step for the exact production paths that
   failed: `war/j2cl/assets/wavy-thread-collapse.css`,
   `war/j2cl/assets/shell.css`, `war/j2cl/assets/sidecar.css`,
   `war/j2cl/assets/wavy-tokens.css`, `war/j2cl/assets/shell.js`, and
   `war/j2cl-search/sidecar/j2cl-sidecar.js`.
4. Update the source-building `Dockerfile` to use the same explicit opt-in so
   standalone production-ish images do not regress in the same way.
5. Keep default `Universal/stage` behavior opt-in for J2CL assets so stale local
   output is not silently packaged.
6. Add a changelog fragment and update the issue with rollback, plan, commits,
   and verification evidence.

## Self Review

- Scope is narrow: production packaging and its contract tests only.
- It does not revert #1211's useful stale-asset protection for default developer
  builds.
- The fix addresses the actual failing boundary: build command plus staged
  artifact verification, not the browser MIME symptom.
- Local verification must include a clean-ish staged build with the deploy
  command and direct filesystem checks for the failed asset paths before PR.
- Post-merge monitoring must include the production deploy run and public probes
  for the same asset URLs.

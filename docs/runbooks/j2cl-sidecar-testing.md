# J2CL Sidecar Testing

Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-19

This runbook covers the local verification path for the merged J2CL sidecar
work. It is the right procedure when a change touches `j2cl/`, the sidecar
transport/codec seam, or a J2CL-backed UI slice such as
`/j2cl-search/index.html`.

## What Exists Today

As of 2026-04-19, the current J2CL browser surfaces are:

- `/j2cl-search/index.html`
  - the first real J2CL search/results slice
- `/j2cl-debug/index.html`
  - debug-oriented single-project J2CL bundle
- `/j2cl/index.html`
  - production-profile J2CL bundle used by packaging

The root `/` route is still the legacy GWT application. Testing the J2CL path
does not replace the obligation to keep the legacy path green.

## Fast J2CL-Only Checks

Run these first when you want a narrow sidecar signal before the full app gate.

From the repo root:

```bash
sbt -batch j2clSandboxBuild j2clSandboxTest
sbt -batch j2clSearchBuild j2clSearchTest
```

Use these expectations:

- `j2clSandboxBuild` / `j2clSandboxTest`
  - proves the base sidecar sandbox still packages and its smoke tests pass
- `j2clSearchBuild` / `j2clSearchTest`
  - proves the merged J2CL search slice still packages and its unit tests pass

For production-profile output only:

```bash
sbt -batch j2clProductionBuild
```

Use that when you need to verify the `war/j2cl/**` output specifically.

## Full PR Gate For J2CL Work

Use this sequence before claiming a J2CL-affecting branch is ready.

### 1. Regenerate The Changelog If Needed

If `wave/config/changelog.json` is missing or stale:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
```

`wave/config/changelog.json` is generated and gitignored. Do not commit it;
commit only changelog fragments under `wave/config/changelog.d/`.


### 2. Run The Cross-Path Build Gate

```bash
sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

This is the minimum trustworthy gate for the current migration phase because it
proves both of these at once:

- the J2CL sidecar slice still builds/tests
- the legacy GWT root app still compiles and stages

### 3. Prepare Local Runtime Assets

```bash
bash scripts/worktree-boot.sh --port 9900
```

If `9900` is occupied, pick another free port and rerun the command.

Then run the exact printed commands. The normal shape is:

```bash
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=... -Djava.security.auth.login.config=...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

Keep the server running through the route checks and browser verification
below.


### 4. Route Presence Checks

While the server is running:

```bash
curl -sS -I http://localhost:9900/
curl -sS -I http://localhost:9900/webclient/webclient.nocache.js
curl -sS -I http://localhost:9900/j2cl-search/index.html
curl -sS -I http://localhost:9900/j2cl/index.html
# Optional — only present when j2clSandboxBuild was also run
curl -sS -I http://localhost:9900/j2cl-debug/index.html
```

Expected result:

- `/` returns success
- `/webclient/webclient.nocache.js` is present
- `/j2cl-search/index.html` is present
- `/j2cl/index.html` is present (production sidecar artifact from `Universal/stage`)
- `/j2cl-debug/index.html` is present only if `j2clSandboxBuild` was run; not required by the standard gate

## Manual Browser Verification

Open these in the same signed-in browser session:

- `http://localhost:9900/`
- `http://localhost:9900/j2cl-search/index.html`

### Legacy Root Expectations

On `/`:

- the legacy GWT app still boots
- login still works
- the root app remains usable

### J2CL Search Slice Expectations

On `/j2cl-search/index.html`:

- the placeholder card disappears and the J2CL search UI mounts
- the page does not show `WaveSandboxEntryPoint not defined`
- the default query `in:inbox` loads results or an explicit empty state
- entering another query such as `with:@` re-renders without a full-page reload
- `Show more waves` appears only when more server-side results exist
- clicking a digest updates the selected row styling

For stronger manual proof:

- create extra waves from the normal app first
- refresh `/j2cl-search/index.html`
- confirm the wave count and `Show more waves` behavior change coherently

When the route checks and browser verification are finished, stop the local
server:

```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```


## When To Use Direct Maven Instead Of SBT

Prefer the SBT tasks above for normal repo work. Use the Maven wrapper only
when you need to debug the J2CL sidecar in isolation.

From `j2cl/`:

```bash
./mvnw -Psearch-sidecar test
./mvnw -Psearch-sidecar package
./mvnw -Pdebug-single-project package
./mvnw -Pproduction package
```

Use those only for deep sidecar debugging; the repo-standard verification path
should still go through SBT.

## Common Failure Signals

### Placeholder Never Replaced

Symptom:

- `/j2cl-search/index.html` only shows the fallback card text

Meaning:

- the host page loaded, but the generated J2CL bundle did not mount

Check:

- rerun `sbt -batch j2clSearchBuild j2clSearchTest`
- confirm the built assets exist under `war/j2cl-search/**`

### `WaveSandboxEntryPoint not defined`

Symptom:

- the page reports a sidecar load error

Meaning:

- the host page is present, but `sidecar/j2cl-sidecar.js` failed to load or was
  not generated for the current profile

Check:

- rerun the matching J2CL build task
- verify the asset path under the correct `war/j2cl*` directory

### J2CL Build Green, `compileGwt` Red

Meaning:

- the change is not safe for the current migration phase
- the sidecar work has leaked into the active legacy root path

Do not wave that through. Fix the branch until both paths are green.

### Missing `wave/config/changelog.json`

Symptom:

- stage or compile tasks fail before the real verification even begins

Fix:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
```

## Required Evidence To Record

For issue/PR closeout, record:

- the exact build command(s) you ran
- the port used for local boot
- the `wave-smoke.sh check` result
- the route checks for `/` and `/j2cl-search/index.html`
- the browser-observed J2CL behavior

That evidence is what distinguishes a real J2CL verification from “the code
looked plausible.”

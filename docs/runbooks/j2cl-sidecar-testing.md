# J2CL Sidecar Testing

Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-21
Review cadence: quarterly

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

The root `/` route now boots the legacy GWT bootstrap by default, while the
coexistence contract from issue `#949` keeps `/?view=j2cl-root` as the direct
J2CL diagnostic route and lets operators set
`ui.j2cl_root_bootstrap_enabled=true` to switch `/` to the J2CL root shell
without a code rollback.

## Fast J2CL-Only Checks

Run these first when you want a narrow sidecar signal before the full app gate.

From the repo root:

```bash
sbt -batch j2clSandboxBuild j2clSandboxTest
sbt -batch j2clSearchBuild j2clSearchTest
```

If the change touches generated transport/message families, also run:

```bash
sbt -batch generatePstMessages "testOnly org.waveprotocol.pst.PstCodegenContractTest"
```

Use these expectations:

- `j2clSandboxBuild` / `j2clSandboxTest`
  - proves the base sidecar sandbox still packages and its smoke tests pass
- `j2clSearchBuild` / `j2clSearchTest`
  - proves the merged J2CL search slice still packages and its unit tests pass
- `generatePstMessages` + `PstCodegenContractTest`
  - proves the sidecar-safe generated codec families still match the repo's
    authoritative PST contract when transport/search schemas change

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

This is the minimum trustworthy gate for the current coexistence phase because it
proves both of these at once:

- the J2CL sidecar slice still builds/tests
- the staged package still carries the rollback-ready legacy GWT client path

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
curl -fsS -o /dev/null http://localhost:9900/
curl -fsS -o /dev/null http://localhost:9900/?view=landing
curl -fsS http://localhost:9900/ | grep -F 'webclient/webclient.nocache.js'
curl -fsS http://localhost:9900/?view=j2cl-root | grep -F 'data-j2cl-root-shell'
curl -fsS -o /dev/null http://localhost:9900/j2cl-search/index.html
curl -fsS -o /dev/null http://localhost:9900/j2cl/index.html
curl -fsS http://localhost:9900/j2cl-search/sidecar/j2cl-sidecar.js | grep -E "WaveSandboxEntryPoint|j2cl"
test "$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:9900/webclient/webclient.nocache.js)" = 200
# Optional — run this only when j2clSandboxBuild was also run
curl -fsS -o /dev/null http://localhost:9900/j2cl-debug/index.html
```

Expected result:

- `/` renders the legacy GWT bootstrap marker
- `/?view=landing` returns success
- `/?view=j2cl-root` renders the J2CL root-shell marker
- `/j2cl-search/index.html` is present
- `/j2cl/index.html` is present (production sidecar artifact from `Universal/stage`)
- the J2CL search bundle itself is present and non-placeholder
- `/webclient/webclient.nocache.js` returns `200`
- `/j2cl-debug/index.html` is present only if `j2clSandboxBuild` was run; not required by the standard gate

## Manual Browser Verification

Open these in the same signed-in browser session:

- `http://localhost:9900/`
- `http://localhost:9900/j2cl-search/index.html`

### Root Expectations

On `/`:

- the legacy GWT bootstrap page boots
- sign-in and sign-out controls remain available
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

## Dual-Mode Coexistence Matrix

Use this matrix when validating both the default-on legacy GWT root mode and
the opt-in J2CL root mode.

### Mode A: Default-on Legacy GWT Root With Coexistence Assets

```bash
bash scripts/worktree-boot.sh --port 9914
PORT=9914 bash scripts/wave-smoke.sh start
PORT=9914 bash scripts/wave-smoke.sh check
curl -fsS http://localhost:9914/ | grep -F 'webclient/webclient.nocache.js'
curl -fsS http://localhost:9914/?view=landing | grep -F 'nav-link-signin'
curl -fsS http://localhost:9914/?view=j2cl-root | grep -F 'data-j2cl-root-shell'
curl -fsS http://localhost:9914/j2cl-search/sidecar/j2cl-sidecar.js | grep -E 'WaveSandboxEntryPoint|j2cl'
test "$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:9914/webclient/webclient.nocache.js)" = 200
PORT=9914 bash scripts/wave-smoke.sh stop
```

Expected result:

- `/` serves the legacy GWT bootstrap page
- `/?view=landing` still reaches the explicit public landing page
- `/?view=j2cl-root` still serves the same J2CL root shell as the direct diagnostic route
- the sidecar bundle is present and non-placeholder
- `/webclient/webclient.nocache.js` is reachable as part of the active default path

### Mode B: Opt-in J2CL Root With Direct J2CL Diagnostics Still Available

```bash
bash scripts/worktree-boot.sh --port 9915
RUNTIME_CONFIG="$(find journal/runtime-config -maxdepth 1 -name '*-port-9915.application.conf' | head -n1)"
cp "$RUNTIME_CONFIG" /tmp/j2cl-root-bootstrap-on.application.conf
printf '\nui.j2cl_root_bootstrap_enabled=true\n' >> /tmp/j2cl-root-bootstrap-on.application.conf
PORT=9915 JAVA_OPTS="-Djava.util.logging.config.file=$PWD/wave/config/wiab-logging.conf -Djava.security.auth.login.config=$PWD/wave/config/jaas.config -Dwave.server.config=/tmp/j2cl-root-bootstrap-on.application.conf" bash scripts/wave-smoke.sh start
curl -fsS http://localhost:9915/ | grep -F 'data-j2cl-root-shell'
curl -fsS http://localhost:9915/?view=j2cl-root | grep -F 'data-j2cl-root-shell'
test "$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:9915/webclient/webclient.nocache.js)" = 200
test "$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:9915/?view=landing)" = 200
PORT=9915 bash scripts/wave-smoke.sh stop
```

Expected result:

- plain `/` switches to the J2CL root shell when the operator override is on
- `/?view=j2cl-root` still serves the J2CL root shell for diagnosis
- `/webclient/webclient.nocache.js` remains reachable in opt-in J2CL mode
- `/?view=landing` remains the explicit public landing page

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

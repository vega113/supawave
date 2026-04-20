# Local Verification

- Branch: issue-922-j2cl-write-path
- Worktree: /Users/vega/devroot/worktrees/issue-922-j2cl-write-path
- Date: 2026-04-20

## Commands

- `bash scripts/worktree-boot.sh --port 9912`
- `./mvnw -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest,org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactoryTest,org.waveprotocol.box.j2cl.search.J2clSidecarComposeControllerTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest,org.waveprotocol.box.j2cl.search.J2clSidecarRouteControllerTest test` (from `j2cl/`)
- `sbt -batch j2clSearchBuild j2clSearchTest`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py --changelog /Users/vega/devroot/worktrees/issue-922-j2cl-write-path/wave/config/changelog.json`
- `sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage`
- `PORT=9912 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-922-j2cl-write-path/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-922-j2cl-write-path/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
- `PORT=9912 bash scripts/wave-smoke.sh check`
- `curl -sS -I http://localhost:9912/`
- `curl -sS -I http://localhost:9912/j2cl-search/index.html`
- `curl -sS -I 'http://localhost:9912/j2cl-search/index.html?q=in%3Ainbox'`
- Agent Browser: register/sign in a local `issue922bot@local.net` account, open `/j2cl-search/index.html`, attempt visible `New wave` create submit, inspect emitted `ProtocolSubmitRequest` frames, and compare loaded bundle filenames against `war/j2cl-search/sidecar/*`
- `PORT=9912 bash scripts/worktree-diagnostics.sh --port 9912`
- `PORT=9912 bash scripts/wave-smoke.sh stop`

## Results

- The focused J2CL submit/write-path tests passed after the red-green cycle.
- `j2clSearchBuild`, `j2clSearchTest`, `compileGwt`, and `Universal/stage` all passed.
- `wave/config/changelog.json` was assembled successfully; validation passed when invoked with the absolute changelog path above.
- Local runtime checks passed before browser verification:
  - `/` -> `200`
  - `/j2cl-search/index.html` -> `200`
  - `/j2cl-search/index.html?q=in%3Ainbox` -> `200`
  - `wave-smoke.sh check` -> `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`
- Browser verification partially succeeded:
  - the authenticated sidecar session showed the visible `New wave` compose affordance and the `#921` query-plus-wave shell
  - search loaded authenticated inbox results and selection UI
- Browser verification exposed a remaining runtime/build boundary:
  - visible create submit still failed with server error `Mismatched hash at version 0: 0:`
  - instrumented `WebSocket.send` in the authenticated sidecar page showed the live browser was still emitting a `ProtocolSubmitRequest` whose version-zero `historyHash` field was empty
  - the freshly built source-side bundle containing the new create hash path existed under `war/j2cl-search/sidecar/`, but the runtime kept loading the stale active sidecar bundle `org.waveprotocol.box-j2cl-sidecar-1.0-SNAPSHOT-72278e4966bd52c918937e30d1bcd33e.bundle.js`
  - this means the source change and test coverage are present, but end-to-end create or reply verification remains blocked by stale J2CL bundle selection in the local runtime
- A later restart attempt produced `ROOT_STATUS=000`; diagnostics showed the staged server did start successfully, but the browser verification had already been blocked by the stale active sidecar bundle before that restart churn.

## Stabilization

The stale sidecar bundle / empty `historyHash` blocker was resolved in the subsequent commit
`fix(j2cl): stabilize sidecar write-path pilot`. After that fix the bundled version-zero hash is
no longer empty and the server-side "Mismatched hash at version 0" error does not reproduce in a
clean dev build. End-to-end create and reply paths are verified to work on the stabilized build.

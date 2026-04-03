# Robot JWT Java SDK And `gpt-bot` Refactor Plan

Date: 2026-04-03
Status: Reviewed
Owner: `robot-jwt-sdk-refactor`

## Goal

Refactor the Java robot SDK surface and the `gpt-bot` example so SupaWave robot integrations stop depending on legacy OAuth-centric Java client assumptions and instead use the current JWT-based SupaWave management and robot API flows.

## Investigated Current State

### What is already JWT-based

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiServlet.java` uses JWT bearer auth for robot management.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java` issues short-lived JWTs for `DATA_API_ACCESS` and `ROBOT_ACCESS` via `client_credentials`.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java` validates `DATA_API_ACCESS` bearer tokens.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java` validates `ROBOT_ACCESS` bearer tokens.
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java` already obtains JWT access tokens from `/robot/dataapi/token` for outbound calls.

### What is still legacy / misleading on the Java side

- `wave/src/main/java/com/google/wave/api/WaveService.java` only knows how to sign outgoing robot requests with OAuth and throws setup errors that explicitly require `setupOAuth()`.
- `wave/src/main/java/com/google/wave/api/AbstractRobot.java` only exposes `setupOAuth(...)` for outbound API calls and still frames the active API around legacy consumer-key flows.
- `wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java` and `wave/src/jakarta-overrides/java/com/google/wave/api/LocalWaveService.java` preserve the same OAuth-shaped API surface, even though local/Jakarta bots do not use OAuth.
- `gpt-bot` uses hand-built JSON-RPC payloads for `fetchWave`, `search`, and active replies instead of exercising the reusable Java robot SDK surface.

### Passive callback reality in this repo today

- Passive callback transport still uses the classic Wave webhook shape:
  - `/_wave/capabilities.xml`
  - `/_wave/robot/profile`
  - `/_wave/robot/jsonrpc`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java` still POSTs passive event bundles without bearer auth.
- Existing repo plans explicitly call server-signed callback JWTs a future gap, not a completed runtime contract.

## Scope Decision

This lane will migrate the Java outbound SDK/example seam to JWT and make the example align with the current live SupaWave server contract.

This lane will **not** invent a fake callback-JWT protocol that the server does not emit today. Instead it will:

- keep the current passive callback wire format intact,
- keep the current callback-token protection in the example,
- document clearly that passive callback bearer/JWT auth remains a server-side gap outside this focused refactor.

## Acceptance Criteria

- `WaveService` supports endpoint-scoped JWT bearer auth for outgoing robot/Data API requests without removing existing OAuth compatibility paths.
- Per-endpoint auth configuration is explicit and deterministic: for a given RPC URL, JWT and OAuth configuration are mutually exclusive and the most recent setup call replaces the older auth mode for that URL.
- `AbstractRobot` exposes a JWT-based outbound setup path that uses the new `WaveService` support.
- Jakarta/local robot support preserves API parity with no-op JWT setup for in-JVM bots.
- `gpt-bot` stops hand-crafting JSON-RPC requests for SupaWave reads/writes and instead uses the shared Java robot SDK path internally while preserving the existing `SupaWaveClient` string-summary/boolean contract seen by `GptBotRobot`.
- `gpt-bot` still obtains JWTs from `/robot/dataapi/token` with the correct token type per endpoint.
- JWT refresh ownership is explicit: `WaveService` remains a bearer transport helper, while `SupaWaveApiClient` continues to own token acquisition, expiry checks, refresh, and reconfiguration per call.
- Tests cover the new JWT SDK auth path and the example behavior that depends on it.
- Docs explain the current state accurately: JWT for registration/token/data/active operations, callback-token protection for passive webhooks until server callback JWT auth exists.

## Planned Changes

### 1. Add JWT auth support to the shared Java robot client

Files:

- `wave/src/main/java/com/google/wave/api/WaveService.java`
- `wave/src/main/java/com/google/wave/api/AbstractRobot.java`

Changes:

- Add endpoint-scoped JWT setup in `WaveService`, keyed by RPC URL, so callers can configure different bearer tokens for `/robot/dataapi/rpc` and `/robot/rpc`.
- Keep existing OAuth code paths intact for compatibility.
- Make auth mode selection deterministic per RPC URL: `setupJwt(...)` clears any prior OAuth config for that URL, and `setupOAuth(...)` clears any prior JWT config for that URL.
- Update `WaveService.makeRpc(...)` so it uses `Authorization: Bearer ...` when JWT auth is configured for a target RPC URL.
- Keep exception types stable while updating setup/error messages and Javadocs so they no longer claim OAuth is the only supported auth mechanism.
- Add `AbstractRobot.setupJwt(String token, String rpcServerUrl)` as the outbound JWT equivalent of `setupOAuth(...)`.
- Keep parameter ordering aligned with the existing setup convention: token first, RPC URL last.

### 2. Keep Jakarta/local API parity

Files:

- `wave/src/jakarta-overrides/java/com/google/wave/api/LocalWaveService.java`
- `wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java`

Changes:

- Add no-op `setupJwt(...)` support to the local/Jakarta robot path so the public Java API shape stays aligned across runtimes.
- Update local/Jakarta comments to describe outbound auth setup as compatibility no-ops rather than OAuth-only no-ops.

### 3. Refactor `gpt-bot` to use the shared SDK for SupaWave operations

Files:

- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotRobot.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotConfig.java` (only if config naming/docs need adjustment)

Changes:

- Keep JWT token acquisition and caching in `SupaWaveApiClient`.
- Keep `SupaWaveClient` unchanged at the interface level: it continues to return string summaries and boolean success values so `GptBotRobot` stays narrow.
- Replace ad-hoc JSON-RPC request construction with `WaveService` calls:
  - `fetchWaveContext(...)` → `WaveService.fetchWavelet(...)`
  - `search(...)` → `WaveService.search(...)`
  - `appendReply(...)` → fetch wavelet, create child reply via SDK blip operations, then submit via `WaveService.submit(...)`
- Make token refresh ownership explicit in the implementation:
  - `WaveService` stores bearer config exactly as given.
  - `SupaWaveApiClient` checks expiry before each call, refreshes if needed, and applies the fresh token to the `WaveService` instance used for that call.
- Treat the SDK-backed `appendReply(...)` mapping as the highest-risk implementation seam:
  - first prove it with a focused test,
  - if the SDK object model cannot express the current `createChild` semantics correctly, stop and adjust the implementation plan before widening scope.
- Preserve the existing `SupaWaveClient` interface so `GptBotRobot` stays narrow.
- Keep `gpt-bot` passive callback handling intact because that matches the current server contract.

### 4. Update docs to describe the actual current contract

Files:

- `docs/gpt-bot.md`
- `docs/gpt-bot.env.example`
- `docs/superpowers/specs/2026-04-02-gpt-bot-example-robot.md`

Changes:

- Document that outbound robot/Data API access uses JWT bearer tokens via `/robot/dataapi/token`.
- Document that the example now reuses the shared Java SDK path instead of hand-built JSON-RPC.
- Clarify that passive callback protection remains callback-token-based in the current runtime, because the server does not yet send callback bearer JWTs.

## Test Plan

### Focused unit tests

- Add/update tests around `WaveService` to verify JWT-configured RPC requests attach `Authorization: Bearer <token>` and avoid the OAuth request-signing path.
- Add/update tests around `WaveService` to verify mixed auth configuration for the same RPC URL behaves deterministically.
- Add/update tests around `AbstractRobot`/Jakarta parity only if needed for the new setup API.
- Update `gpt-bot` tests so they verify the example still behaves correctly after the SDK refactor.
- Add focused coverage for `SupaWaveApiClient.appendReply(...)` so the SDK-backed fetch/reply/submit path is proven explicitly.

### Commands

- `sbt "wave/testOnly com.google.wave.api.*WaveService* com.google.wave.api.AbstractRobotTest"`
- `sbt "wave/testOnly org.waveprotocol.examples.robots.gptbot.*"`
- `sbt "wave/compile"`

### Local sanity verification

- Start the Wave server locally.
- Start `gpt-bot` locally.
- Passing local sanity means:
  - the Wave server boots successfully,
  - `gpt-bot` boots successfully,
  - the bot health endpoint responds,
  - at least one local JWT-backed SDK round-trip path is exercised successfully by focused tests or a narrow live call,
  - any missing remote credential/callback proof is recorded as a limit rather than silently skipped.
- Verify passive callback registration and token issuance with narrow, real commands if credentials/config allow it.
- Record any limits clearly if full remote callback verification is not possible from local-only credentials.

## Out Of Scope

- Adding a brand-new passive callback JWT protocol on the server side.
- Changing robot registration from shared secrets to robot public keys/JWKS.
- Removing the remaining legacy OAuth client package wholesale.
- Broad cleanup of unrelated robot docs, servlets, or UI surfaces.

## Risks

- `WaveService` has old assumptions baked into error messages and helper structures, so the JWT path should be added surgically to avoid breaking existing OAuth callers.
- The example currently summarizes raw JSON responses; switching to SDK objects changes that seam and needs careful test coverage.
- Active and Data API endpoints require different JWT token types, so endpoint-scoped auth configuration is required.

## Done Definition For This Lane

- Plan reviewed cleanly.
- Shared Java SDK supports JWT outbound auth.
- `gpt-bot` reuses that SDK for SupaWave operations.
- Focused tests pass.
- Local verification is run and summarized.
- Branch is committed, pushed, and opened as a PR against the user-specified base branch `got-bot-robot`.

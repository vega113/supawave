# Issue #811 Robot Streaming Into Waves Design

Status: proposed
Date: 2026-04-10
Issue: #811

## Goal

Let robots progressively write partial output into a wave blip while an LLM or other upstream generator is still producing text, without adding a new server-side streaming transport.

## Current Model

### Passive callback path

The passive robot webhook flow is event-driven and single-response:

1. `RobotsGateway` receives a wavelet update and schedules `org.waveprotocol.box.server.robots.passive.Robot`.
2. `Robot.process(...)` refreshes capabilities if needed, resolves `rpcServerUrl`, and asks `EventGenerator.generateEvents(...)` for an `EventMessageBundle`.
3. `RobotConnector.sendMessageBundle(...)` POSTs that bundle to the robot callback URL and waits for a single JSON array of `OperationRequest` objects.
4. `RobotOperationApplicator.applyOperations(...)` executes the returned operations inside a bound `OperationContextImpl`.
5. `OperationUtil.submitDeltas(...)` submits the generated deltas through `WaveletProvider`.

Important consequence: the passive webhook itself cannot stream incremental robot output, because the server waits for one callback response and only applies operations after that response completes.

### Active/Data JSON-RPC path

The mutation path used by `/robot/rpc` and `/robot/dataapi/rpc` already supports the needed write primitives:

- `BaseApiServlet.processOpsRequest(...)` deserializes an operation batch and executes it in an unbound `OperationContextImpl`.
- `OperationContextImpl.openWavelet(...)` opens the current committed snapshot for each request.
- `RobotWaveletData` captures operations per author and emits deltas from the snapshot version seen at request start.
- `OperationUtil.submitDeltas(...)` submits those deltas through the normal wavelet provider path.

Both the active and data registries expose:

- `blip.createChild`
- `blip.continueThread`
- `wavelet.appendBlip`
- `document.modify`

The runtime `/api-docs` already documents that:

- `blip.createChild` returns a `WaveletBlipCreatedEvent` payload with `newBlipId`
- `document.modify` accepts `REPLACE`

This means a robot can already:

1. create a reply blip once
2. persist the returned `newBlipId`
3. update that same blip in later requests

## OT And Concurrency Constraints

The server-side OT machinery already handles transformed submission against the current committed version, but the robot-side write pattern still matters.

### Safe ownership boundary

Streaming must target a robot-owned reply blip, not the user-authored prompt blip.

Rationale:

- concurrent human edits to the same blip would produce confusing merges
- a dedicated robot reply blip gives the stream a clear ownership boundary
- the existing reply model in `gpt-bot` already follows this shape

### One in-flight update per streamed reply blip

Robot clients must serialize updates to a given streamed reply blip.

Rationale:

- each JSON-RPC request opens a fresh snapshot independently
- two overlapping updates against the same reply blip can race and reorder
- the server will transform them, but it cannot infer the robot’s intended chunk ordering

### Prefer full accumulated replace over append-only deltas

Each streamed update should replace the full visible reply text accumulated so far, not append only the newest chunk.

Rationale:

- retries become self-healing because the next update re-sends the whole accumulated state
- late or duplicate requests converge on the newest full text once ordering is restored
- append-only chunk writes are smaller, but they are much more fragile under retry ambiguity

`DocumentModifyService` already preserves the required opening newline when replacing from position zero, so full-document replace is a safe primitive for a robot-owned reply blip.

### Throttle outbound mutations

The robot should coalesce upstream token deltas into fewer JSON-RPC writes.

Rationale:

- one RPC per token is unnecessary load
- human-visible responsiveness is satisfied by periodic chunked updates
- throttling also reduces the chance of self-induced races

## Options Considered

### Option A: stream through the passive callback itself

Rejected.

- The passive callback contract is single-request/single-response.
- It would require a new long-lived outbound server-to-robot transport.
- It conflicts with the existing repo direction that keeps passive callbacks event-centric.

### Option B: add a brand-new server mutation op such as `blip.streamUpdate`

Rejected.

- Existing `blip.createChild` plus `document.modify` already cover the write semantics.
- A new op would duplicate OT-aware behavior already present in the active/data API.
- It would increase docs, test, and client surface area without solving a real server gap.

### Option C: standardize a streaming write pattern on top of existing JSON-RPC operations

Recommended.

- Reuses stable wave mutation primitives.
- Matches the repo’s existing robot API direction.
- Gives external bots a documented, interoperable flow immediately.
- Lets the example `gpt-bot` prove the pattern end to end.

## Recommended Design

### High-level flow

When a passive callback decides it wants to answer progressively:

1. Read `rpcServerUrl` from the incoming `EventMessageBundle`.
2. Create a dedicated reply blip with `blip.createChild`.
3. Parse and store the returned `newBlipId`.
4. As the upstream model yields more text, coalesce deltas locally.
5. Periodically issue `document.modify` with `modifyHow=REPLACE` against the reply blip, replacing the entire accumulated visible reply.
6. On completion, send one final replace containing the final full text.

### Endpoint and auth selection

The robot should prefer the passive bundle’s `rpcServerUrl` hint rather than hardcoding `/robot/rpc` or `/robot/dataapi/rpc`.

Reasoning:

- recent server work now curates this field by auth mode
- external bots should follow the server-advertised endpoint
- the correct bearer token type depends on the selected endpoint

Token rule:

- if the endpoint is `/robot/rpc`, use `token_type=robot`
- if the endpoint is `/robot/dataapi/rpc`, use the data API token

### Example-bot support

The `gpt-bot` example should gain a streaming-active mode that:

- creates a placeholder reply blip once
- writes periodic full-text replacements into that blip
- uses real chunk callbacks when the LLM backend supports streaming
- degrades to a single final update when the backend only supports one-shot completion

### Backend capability model

`CodexClient` should gain a streaming callback method with a default one-shot fallback.

Expected behavior:

- `OpenAiCodexClient` implements real incremental text streaming
- `ProcessCodexClient` and `EchoCodexClient` can initially use the default one-shot fallback
- if the current OpenAI path needs one-shot tool-calling/web-search behavior, streaming should degrade to that existing one-shot path rather than silently changing semantics

This keeps the robot-side streaming write path reusable without forcing every backend to support token streaming on day one.

## Files Expected To Change

Implementation:

- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/CodexClient.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClient.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotReplyPlanner.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotRobot.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotConfig.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveClient.java`
- `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java`
- new focused helper for throttled streamed reply writes if needed

Docs:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- `docs/gpt-bot.md`
- `docs/robot-data-api-authentication.md`

Tests:

- `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java`
- `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClientTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- possibly targeted client tests if a helper is extracted

Release notes:

- new fragment under `wave/config/changelog.d/`

## Non-Goals

- No new passive robot transport.
- No new WebSocket or SSE robot subscription surface.
- No changes to the core wave OT protocol.
- No attempt to stream into user-authored blips.
- No requirement that every LLM backend provide true token streaming in this change.

## Verification Targets

- unit coverage for the new streaming-active reply path in `gpt-bot`
- unit coverage for any new response parsing or RPC endpoint selection logic
- `/api-docs` test coverage for the new streaming guidance
- local narrow sanity verification with the example robot and the affected robot API/test commands

# Robot API Evolution Plan

## 1. Executive Summary

Wave currently has three robot-facing integration shapes, not one:

- A passive webhook pipeline built around `WaveBus`, `RobotsGateway`, `Robot`, `EventGenerator`, and `RobotConnector`
- An "active" JSON-RPC endpoint at `/robot/rpc`
- A Data API JSON-RPC endpoint at `/robot/dataapi` and `/robot/dataapi/rpc`

The good news is that the "active" and Data APIs are already mostly unified internally. Both run through the same Jakarta `BaseApiServlet`, both use JWT bearer auth, both deserialize the same `OperationRequest[]` payload, and both execute against nearly identical operation registries. The main remaining split is semantic: `ROBOT_NOTIFY` behavior, folder-action scope mapping, and the token type expected at the servlet edge.

The real legacy split is between:

- passive webhook delivery, which is event-driven and initiated by the server, and
- proactive RPC access, which is request/response and initiated by the robot.

The recommended direction is:

1. Merge the active and Data APIs into a single canonical v2 robot RPC surface.
2. Keep legacy `/robot/rpc` and `/robot/dataapi*` paths as compatibility wrappers.
3. Add an explicit delegation model so a robot can act as a human with auditable, revocable grants.
4. Add a JSON streaming protocol for real-time updates, but build it on top of `ClientFrontend` and `WaveViewSubscription`, not on top of the passive robot webhook stack.
5. Treat the passive webhook protocol as a compatibility transport, not as the foundation for interactive alternative UIs.

The overall feasibility is high for:

- inbox and search clients
- wave viewers
- compose/reply/edit flows that can live with high-level robot operations
- personal-helper robots acting for a single human user

The hard part is full editor parity. A complete alternative Wave UI eventually needs JSON access to versioned OT semantics, not only high-level robot operations like `document.modify` and `wavelet.appendBlip`.


## 2. Current State

### 2.1 Surface Inventory

| Surface | Runtime classes | Transport | Auth | Wire format | Registration path |
| --- | --- | --- | --- | --- | --- |
| Passive robot webhook | `RobotsGateway`, `Robot`, `EventGenerator`, `RobotConnector`, `RobotOperationApplicator`, `OperationServiceRegistryImpl` | Server POST to robot callback URL | No bearer auth on outbound callback; server only checks robot account state locally | `EventMessageBundle` JSON to robot, `OperationRequest[]` JSON back | `RobotRegistrarImpl`, `RobotRegistrationServlet`, `/api/robots` |
| Active API | `ActiveApiServlet`, `BaseApiServlet`, `ActiveApiOperationServiceRegistry` | HTTP POST `/robot/rpc` | JWT `ROBOT_ACCESS`, audience `robot`, scope `wave:robot:active` | `OperationRequest[]` JSON-RPC list in, `JsonRpcResponse[]` list out | Token minted by `DataApiTokenServlet` with `token_type=robot` |
| Data API | `DataApiServlet`, `BaseApiServlet`, `DataApiOperationServiceRegistry` | HTTP POST `/robot/dataapi` or `/robot/dataapi/rpc` | JWT `DATA_API_ACCESS`, audience `data-api`, per-op scopes | Same JSON-RPC list format as active API | Session or `client_credentials` via `DataApiTokenServlet` |
| Robot management | `RobotApiServlet`, `RobotDashboardServlet`, `RobotRegistrationServlet` | REST and HTML | User `DATA_API_ACCESS` bearer token or browser session | JSON or HTML | `/api/robots`, `/account/robots`, `/robot/register/create` |
| First-party web client | `ServerRpcProvider`, `WaveClientRpcImpl`, `ClientFrontendImpl`, `WaveViewSubscription`, `WaveWebSocketClient` | WebSocket `/socket` plus protobuf RPC wrappers | Browser session / `ProtocolAuthenticate` with `JSESSIONID` | Protobuf RPC messages serialized as JSON wrappers | First-party only |

### 2.2 Passive API

The passive path is still the classic Wave robot model.

- `RobotsGateway` subscribes to `WaveBus` and receives `waveletUpdate(...)`.
- For each participant added to or already on the wavelet, it parses the address with `RobotName.fromAddress(...)`.
- If the participant resolves to a stored `RobotAccountData` and the account is `isVerified()` and not `isPaused()`, the gateway schedules a per-robot worker.
- `Robot` batches contiguous `DeltaSequence` updates per wavelet, fetches capabilities on first use through `RobotConnector.fetchCapabilities(...)`, then calls `EventGenerator.generateEvents(...)`.
- `EventGenerator` produces an `EventMessageBundle` with `events`, `wavelet`, `blips`, `threads`, `robotAddress`, and optional `proxyingFor`.
- `RobotConnector.sendMessageBundle(...)` serializes the bundle with `RobotSerializer` and POSTs it to `<robot-url>/_wave/robot/jsonrpc`.
- The remote robot returns a list of `OperationRequest` objects.
- `RobotOperationApplicator` applies those operations to a bound `RobotWaveletData` and submits generated deltas through `WaveletProvider.submitRequest(...)`.

Important characteristics:

- Passive delivery is event-centric, not subscription-centric.
- The server performs outbound callbacks; there is no server-held long-lived stream to the robot.
- The callback payload is not a generic wave stream. It is a capability-filtered robot event bundle.
- The server does not authenticate itself to the robot callback URL with a bearer token or signature today.
- Capability discovery still depends on `/_wave/capabilities.xml` through `RobotCapabilitiesParser`.

### 2.3 Active API

The active API is now much thinner than its historical name suggests.

- `ActiveApiServlet` authenticates with `JwtRequestAuthenticator.authenticate(...)`.
- It expects:
  - token type `ROBOT_ACCESS`
  - audience `robot`
  - required scope `wave:robot:active`
- After auth, it delegates to `BaseApiServlet.processOpsRequest(...)`.
- `BaseApiServlet` reads the request body, deserializes `OperationRequest[]`, maps each op to a logical scope with `OpScopeMapper`, validates scopes, executes the operations, and serializes `JsonRpcResponse[]`.
- `ActiveApiOperationServiceRegistry` includes:
  - notify ops
  - wave fetch/search/profile ops
  - wavelet and document mutation ops
  - folder actions
  - import/export ops

Operationally, it is no longer a different transport stack. It is a JWT-authenticated JSON-RPC wrapper over the same operation services used by the Data API.

### 2.4 Data API

The Data API is the same basic servlet shape as the active API.

- `DataApiServlet` authenticates `DATA_API_ACCESS` JWTs with audience `data-api`.
- It uses the same `BaseApiServlet`.
- `DataApiOperationServiceRegistry` is almost identical to `ActiveApiOperationServiceRegistry`.

There are only three meaningful semantic differences today:

1. `ROBOT_NOTIFY` and `ROBOT_NOTIFY_CAPABILITIES_HASH` are real operations in the active registry but `DoNothingService` in the Data registry.
2. `DataApiServlet.mapOperationToOpType(...)` remaps `ROBOT_FOLDER_ACTION` to a write operation so it requires `wave:data:write` rather than `wave:robot:active`.
3. The servlet edge expects a different token type and audience.

That means the active/Data split is already mostly accidental complexity.

### 2.5 Auth and Principal Model

Current principal handling is split by transport.

#### Browser and first-party web client

- Human login state is stored in `HttpSession` through `SessionManager` and `SessionManagerImpl`.
- `AuthenticationServlet` also issues a browser JWT cookie (`wave-session-jwt`) through `BrowserSessionJwtIssuer`.
- `JwtSessionRestorationFilter` can rebuild the `HttpSession` from that browser JWT after restart.
- The first-party WebSocket endpoint still opens with HTTP session state and the browser GWT client still sends `ProtocolAuthenticate` carrying the `JSESSIONID` value from JavaScript.

This matters because the modern JWT system is only partially applied to the web client path.

#### Robot and Data API tokens

- `DataApiTokenServlet` is the sole token issuer for robot-facing APIs.
- Session-based POST issues a `DATA_API_ACCESS` token for the logged-in human user.
- `grant_type=client_credentials` validates the robot account's `consumerSecret` and can issue:
  - `DATA_API_ACCESS` (default)
  - `ROBOT_ACCESS` (`token_type=robot`)
- Robot tokens are denied if the robot is paused, unverified, or missing a callback URL.

#### Stored account state

`RobotAccountData` already contains several useful modern fields:

- `ownerAddress`
- `description`
- `paused`
- `isVerified`
- `tokenExpirySeconds`
- `tokenVersion`

Human accounts do not currently expose a comparable JWT revocation version. `JwtRequestAuthenticator.verifyAccountState(...)` only compares `subjectVersion` against `RobotAccountData.getTokenVersion()` for robot accounts.

That means:

- robot JWTs can be revoked through `tokenVersion`,
- human `DATA_API_ACCESS` JWTs do not have equivalent server-side revocation state today.

This is a major gap for any "act as human" design.

### 2.6 Existing Proxying Primitive

There is already a legacy proxy-authorship seam:

- `JsonRpcConstant.ParamsProperty.PROXYING_FOR`
- `OperationUtil.computeParticipant(...)`
- `OperationUtil.toProxyParticipant(...)`
- `RobotName` addresses in the form `robot+proxy@example.com`

This changes the effective author participant used by the operation services.

It is not a delegation system.

Why it is insufficient:

- there is no persisted consent or grant record
- there is no scope model
- there is no audit model
- reads still authorize against the computed participant id, not against a human subject
- `WaveletDataUtil.checkAccessPermission(...)` only checks actual wave participants or the shared-domain participant

So `proxyingFor` is a formatting trick, not a security boundary.

### 2.7 Wire Format Comparison

#### Passive webhook payload

`EventMessageBundle` JSON contains:

- `events`
- `wavelet`
- `blips`
- `threads`
- `robotAddress`
- optional `proxyingFor`
- optional `rpcServerUrl`

The robot responds with JSON `OperationRequest[]`.

#### Active/Data RPC payload

Both servlets accept the same operation array format serialized by `OperationRequestGsonAdaptor`:

```json
[
  {
    "method": "wave.robot.fetchWave",
    "id": "op1",
    "params": {
      "waveId": "...",
      "waveletId": "..."
    }
  }
]
```

Responses are serialized by `JsonRpcResponseGsonAdaptor` as ordered JSON arrays.

#### First-party protobuf WebSocket path

The first-party client path uses:

- `ProtocolWaveClientRpc.Open`
- `ProtocolWaveClientRpc.Submit`
- `ProtocolWaveClientRpc.Authenticate`

`WaveClientRpcImpl.open(...)` and `ClientFrontendImpl.openRequest(...)` stream:

- snapshots
- deltas
- commit notices
- a marker message
- a server-generated `channelId`

`WaveViewSubscription` also maintains:

- one outstanding submit per wavelet
- own-delta suppression using submitted end versions
- per-wavelet last version

Those are exactly the semantics a real-time alternative UI needs.

### 2.8 What Already Exists That We Can Reuse

The codebase already has several reusable pieces for a v2 robot/client protocol:

- JWT token issuance and validation
- owner-scoped robot management (`RobotApiServlet`, `AccountStore.getRobotAccountsOwnedBy(...)`)
- search and folder operations in robot services
- `FetchWaveService` and snapshot serializers
- `FetchProfilesService` and `FetchProfilesServlet`
- `ClientFrontendImpl` and `WaveViewSubscription` for ordered streaming and submit echo suppression
- public/private wave access checks in `WaveletDataUtil` and `WaveletContainerImpl`

Runtime note for implementers:

- treat `wave/src/jakarta-overrides/java/**` as the live servlet/runtime source of truth
- use `wave/src/main/java/**` for shared logic, models, serializers, and migration seams


## 3. Proposal 1: Unified API

### 3.1 Goal

Create one canonical robot API surface for reads, writes, and robot control:

- one auth model
- one principal model
- one operation registry
- multiple transports

The canonical transport should be a new v2 endpoint family, with legacy endpoints preserved as wrappers.

### 3.2 Canonical v2 Surface

Recommended new canonical paths:

- `POST /robot/api/v2/token`
- `POST /robot/api/v2/rpc`
- `GET /robot/api/v2/openapi.json`
- `GET /api-docs` updated to mark `/robot/api/v2/rpc` as canonical

`/robot/api/v2/openapi.json` should be generated by the existing `ApiDocsServlet` metadata tables in the same way `/api/openapi.json` is generated today. Do not introduce a second documentation source.

Legacy compatibility paths should remain:

- `/robot/rpc`
- `/robot/dataapi`
- `/robot/dataapi/rpc`
- `/robot/dataapi/token`
- `/robot/token`

### 3.3 Core Internal Refactor

Create a shared execution layer and make all servlet variants call it.

Fix the pre-existing `BaseApiServlet` singleton race as part of this refactor.
`ActiveApiServlet` and `DataApiServlet` are singleton-scoped today, while
`BaseApiServlet` stores per-request `operations` in an instance field before
`handleResults(...)` reads it back. `UnifiedRobotOperationExecutor` must not
inherit that pattern. The executor should be request-scoped/stateless: parsed
operations travel through method parameters or immutable request/result objects,
never through servlet instance fields. Under concurrent requests handled by the
same `ActiveApiServlet` or `DataApiServlet` instance, that field can be
overwritten or read mid-request, causing crossed responses or incomplete
operation execution.

#### New classes to add

- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotApiPrincipal.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/UnifiedRobotOperationExecutor.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/UnifiedRobotOperationRequest.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/UnifiedRobotOperationResult.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/UnifiedRobotOperationServiceRegistry.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiFlavor.java`

#### Existing classes to modify

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/JakartaRobotApiBindingsModule.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`

#### Guice binding strategy

Keep the current named legacy bindings and add one new canonical binding:

- `@Named("ActiveApiRegistry")` remains for `/robot/rpc`
- `@Named("DataApiRegistry")` remains for `/robot/dataapi*`
- add `@Named("UnifiedRobotApiRegistry")` for `/robot/api/v2/rpc`

Modify `JakartaRobotApiBindingsModule` to bind all three explicitly so existing named injections do not break.

Implementation rule for the servlet seam:

- `BaseApiServlet` becomes a thin HTTP adapter that reads the body into a local
  `List<OperationRequest>`, calls `UnifiedRobotOperationExecutor.execute(...)`,
  and serializes the returned ordered responses from the executor result.
- Remove the mutable servlet field that caches `operations` between
  `processOpsRequest(...)` and `handleResults(...)`.
- Keep the shared non-Jakarta mirror in sync if that source set is still
  compiled by tests, so the race does not survive in one build flavor.

### 3.4 Registry Unification

`UnifiedRobotOperationServiceRegistry` should become the canonical registry and should use one consistent semantic rule set:

- `ROBOT_NOTIFY` and `ROBOT_NOTIFY_CAPABILITIES_HASH` remain valid operations, but only for principals with `wave:robot:active`
- `ROBOT_FOLDER_ACTION` is always treated as a user-data write, not as an "active transport" special case
- fetch/search/profile/export/import ops stay available to both direct and delegated tokens, subject to scopes

Legacy wrappers should preserve old behavior:

- `DataApiServlet` compatibility mode may still turn `ROBOT_NOTIFY*` into a no-op so existing clients do not break.
- `ActiveApiServlet` compatibility mode may continue to require `ROBOT_ACCESS`.

The canonical v2 path should not preserve that split.

### 3.5 Auth Model for v2

The canonical v2 RPC path should accept a principal context, not only a participant id.

Allowed token types for v2:

- `DATA_API_ACCESS`
- `ROBOT_ACCESS`
- `DELEGATED_API_ACCESS` (proposal 2)

The servlet should authenticate once, derive `RobotApiPrincipal`, and pass it into the executor.

### 3.6 Passive Compatibility Strategy

Do not try to turn the passive webhook path into the canonical core.

Instead:

- keep the passive webhook delivery stack working as-is for legacy robots
- add a compatibility adapter later so webhook deliveries are emitted from the same v2 auth/principal model
- treat passive webhook payloads as a transport adapter, not as the canonical v2 protocol

The passive pipeline uses capability-filtered event bundles. Interactive clients need ordered snapshot/delta streams, which are a different abstraction.

### 3.7 Migration Path

Phase 1:

- land `UnifiedRobotOperationExecutor`
- make `ActiveApiServlet` and `DataApiServlet` thin wrappers over it
- keep all current endpoints live

Phase 2:

- add `/robot/api/v2/rpc`
- point docs and examples at v2

Phase 3:

- add metrics on endpoint usage by path and operation
- mark `/robot/rpc` and `/robot/dataapi*` as legacy aliases

Phase 4:

- only consider retirement after telemetry shows v2 adoption

### 3.8 Effort Estimate

- Complexity: Medium
- Implementation effort: about 2 to 3 engineer-weeks
- Main risks:
  - preserving exact legacy behavior for `ROBOT_NOTIFY` and folder actions
  - keeping API docs and dashboard examples consistent during transition


## 4. Proposal 2: Human Delegation / Act-As Authorization

### 4.1 Goal

Allow a verified robot to act as a specific human user for:

- reading waves the human can read
- posting or editing as that human
- managing inbox and folder state through the user's UDW

This must be explicit, revocable, scoped, auditable, and safe by default.

### 4.2 Why the Current Model Is Not Enough

Current primitives are missing key pieces:

- `ownerAddress` only tells us who created or owns the robot account
- `proxyingFor` only rewrites a participant id format
- `JwtRequestAuthenticator` only returns `ParticipantId`, not actor + subject context
- human accounts have no JWT version / revocation field
- there is no consent UI, no stored grant, and no grant-scoped token issuance

### 4.3 Recommended Security Model

Introduce an explicit delegation grant.

#### New persistence model

Add a dedicated grant store instead of overloading `RobotAccountData`.

#### New classes to add

- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotDelegationGrant.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotDelegationScope.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/RobotDelegationStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileRobotDelegationStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4RobotDelegationStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryRobotDelegationStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotDelegationService.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotDelegationAuditEntry.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/RobotDelegationAuditStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileRobotDelegationAuditStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4RobotDelegationAuditStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryRobotDelegationAuditStore.java`

Recommended grant fields:

- `grantId`
- `robotId`
- `subjectUserId`
- `createdByUserId`
- `scopes`
- `createdAtMillis`
- `updatedAtMillis`
- `expiresAtMillis`
- `revokedAtMillis`
- `status`
- `grantVersion`
- `lastUsedAtMillis`

Recommended audit-entry fields:

- `eventId`
- `grantId`
- `robotId`
- `subjectUserId`
- `action`
- `resourceType`
- `resourceId`
- `requestId`
- `createdAtMillis`
- `result`
- `clientIp`

#### Recommended grant scopes

Keep scopes simple and product-facing:

- `wave:data:read`
- `wave:data:write`
- `wave:inbox:write`
- `wave:profile:read`
- `wave:act_as`

`wave:act_as` should be a marker that the token represents delegated authority, not direct ownership.

### 4.4 Human JWT Revocation State

Add a JWT revocation/version field for human accounts.

#### Classes to modify

- `wave/src/main/java/org/waveprotocol/box/server/account/AccountData.java`
- `wave/src/main/java/org/waveprotocol/box/server/account/HumanAccountData.java`
- `wave/src/main/java/org/waveprotocol/box/server/account/HumanAccountDataImpl.java`
- all account-store serializers and persistence backends:
  - `ProtoAccountDataSerializer`
  - `FileAccountStore`
  - `Mongo4AccountStore`
  - `MemoryStore`
  - legacy `MongoDbStore` if still supported
  - any generated proto/account schemas required by `ProtoAccountDataSerializer`
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtRequestAuthenticator.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`

Recommended shape:

- add `jwtVersion` accessors to `HumanAccountData`
- persist `jwtVersion` in `HumanAccountDataImpl`
- carry the field through every account persistence path listed above, with
  readers defaulting missing stored values to `0`
- stop treating `AccountData.isRobot()` as the only revocation gate;
  `verifyAccountState(...)` must branch on token type and subject account type
- update `DataApiTokenServlet.handleSessionBased(...)` so direct human
  `DATA_API_ACCESS` tokens stop hardcoding `subjectVersion=0` and instead embed
  the current human `jwtVersion`
- generalize `JwtRequestAuthenticator.verifyAccountState(...)` so it validates:
  - robot `tokenVersion` for direct robot tokens
  - human `jwtVersion` for direct human `DATA_API_ACCESS` tokens
  - human `jwtVersion` for `DELEGATED_API_ACCESS` tokens whose `sub` is the
    human `effectiveSubject`

This is required so delegated tokens can be revoked immediately when:

- the human revokes the grant
- the human account is suspended or banned
- the human resets credentials
- an admin invalidates sessions

#### Migration plan for existing human accounts

Do not break existing human `DATA_API_ACCESS` tokens during rollout.

Recommended migration rules:

1. Add `jwtVersion` to human accounts with default value `0`.
2. Persistence readers must treat missing stored `jwtVersion` values as `0`.
3. JWT validation must treat missing `subjectVersion` claims as `0` only for legacy direct `DATA_API_ACCESS` human tokens. `DELEGATED_API_ACCESS` tokens must always carry explicit `subjectVersion`, `actorVersion`, and `delegationVersion` claims; missing any version claim on a delegated token must be rejected, not defaulted.
4. Existing session-based `DATA_API_ACCESS` tokens continue working until expiry because both token and stored version resolve to `0`.
5. The first explicit user revocation event increments `jwtVersion` from `0` to `1`, invalidating all previously minted human tokens.

This allows a rolling deploy without forcing all logged-in users to reauthenticate immediately.

Rule 2 is the persistence layer default for stored account state only. Rule 3 is the validation layer default for JWT claims only. Do not use the persistence fallback to weaken delegated-token claim requirements.

Scope note:

- this is a full account-persistence migration, not a servlet-only change
- the plan should treat the account-store work, serializer updates, token
  minting updates, and auth validation updates as one cross-cutting slice with
  dedicated regression coverage for file, Mongo4, in-memory, and legacy MongoDb
  storage

### 4.5 Token and Claims Changes

#### Classes to modify

- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtTokenType.java`
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtClaims.java`
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtScopes.java`
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtRequestAuthenticator.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`

Decision: use a distinct delegated token type.

Add a new token type:

- `DELEGATED_API_ACCESS`

Extend claims with actor metadata:

- `sub`: the human subject being acted as
- `actorSubject`: the robot id
- `actorVersion`
- `delegationId`
- `delegationVersion`
- `subjectVersion`

Recommended validation rules:

- token type must be `DELEGATED_API_ACCESS`
- `sub` must be a human account
- `actorSubject` must be a robot account
- the human subject account must be in a current, allowed state (not suspended, banned, or otherwise restricted)
- the robot account must still be verified and not paused or disabled
- the delegation grant must exist, be active, and match the subject and actor
- grant validation must confirm the human and robot account states both when the grant is created and again when the token is used, so version matching alone does not establish eligibility; do not rely on version matching by itself to imply the account is still allowed
- the requested scopes must be a subset of the grant scopes
- `subjectVersion`, `actorVersion`, and `delegationVersion` must all still be current
- all version claims must be present and explicit; missing version claims are rejected (no zero-default fallback for delegated tokens)

Minting rules:

- session-based direct user tokens read `HumanAccountData.getJwtVersion()` and
  place it in `subjectVersion`
- direct robot tokens continue to place `RobotAccountData.getTokenVersion()` in
  `subjectVersion`
- delegated tokens read both the human `jwtVersion` and the robot
  `tokenVersion`, placing them in `subjectVersion` and `actorVersion`
  respectively

Keep this decision fixed across the whole implementation. Do not reuse `DATA_API_ACCESS` for delegated tokens.

### 4.6 Principal Model

`JwtRequestAuthenticator` should grow a new method that returns `RobotApiPrincipal` instead of just `ParticipantId`.

Recommended principal shape:

- `authenticatedSubject`
- `effectiveSubject`
- `actorRobot`
- `tokenType`
- `scopes`
- `delegationId`
- `isDelegated`

For direct user tokens:

- `authenticatedSubject == effectiveSubject == human user`
- `actorRobot == null`

For direct robot tokens:

- `authenticatedSubject == effectiveSubject == robot`

For delegated robot tokens:

- `authenticatedSubject == actorRobot == robot`
- `effectiveSubject == human user`

### 4.7 RPC and Stream Behavior

For delegated requests:

- reads/search/profile/folder actions authorize as `effectiveSubject`
- mutations execute as `effectiveSubject`
- the actor robot id is logged and attached to request audit data

Explicit OT submit routing rule:

- for phase-2 `submitDelta`, `RobotStreamSession` must call
  `ClientFrontend.submitRequest(...)` with `loggedInUser = effectiveSubject`
  rather than `authenticatedSubject`
- the embedded `ProtocolWaveletDelta.author` field must also be the
  `effectiveSubject`
- the robot actor stays on `RobotApiPrincipal` and audit metadata; it is not
  written into the protobuf `author` field for delegated submits
- if a delegated stream passes the robot id as `loggedInUser`, the submit will
  fail the existing `author.equals(loggedInUser)` check, so this routing rule is
  mandatory and should be called out in the implementation section for the
  streaming phase

Do not use `proxyingFor` for new delegated flows. Keep it only for legacy compatibility.

### 4.8 UI and UX Flow

Use the existing robot control room as the starting point.

#### New REST endpoints

- `GET /api/robots/{id}/delegations/me`
- `PUT /api/robots/{id}/delegations/me`
- `DELETE /api/robots/{id}/delegations/me`
- optional owner/admin view:
  - `GET /api/robots/{id}/delegations`

#### UI changes

Modify:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiServlet.java`

Recommended first-release UX:

1. User opens `/account/robots`
2. User selects a robot they own
3. User clicks "Allow this robot to act as me"
4. User selects scopes:
   - read my waves
   - post/edit as me
   - manage my inbox
5. User optionally sets an expiry
6. Server stores a grant and shows how the robot can mint delegated bearer tokens

Recommended first-release restriction:

- only allow self-owned robots to receive act-as grants in v1
- robots whose `ownerAddress` is `null` are ineligible for delegation until an
  owner is explicitly assigned or claimed through the existing ownership flow

That keeps the trust model simple and makes the feature a real "personal helper" flow. Multi-user install/share flows can come later.

Enforce this restriction at both layers:

- grant creation time in the dashboard / REST API
- delegated token minting time in `DataApiTokenServlet`

`DataApiTokenServlet` must re-check that the grant's `createdByUserId` and the robot's `ownerAddress` still satisfy the v1 self-owned rule before minting a delegated token.

Validation logic for delegated token minting:

- `grant.robotId` must equal the requested robot
- `grant.subjectUserId` must equal the requesting user
- `grant.createdByUserId` must equal the requesting user, so v1 remains a
  self-grant flow
- `robot.ownerAddress` must equal the requesting user, so the grant creator is
  still the current robot owner
- `grant.status` must be active and not expired or revoked

If a robot's `ownerAddress` is cleared or changed after a delegated token has been minted, increment the robot's `tokenVersion` so previously issued delegated tokens are invalidated immediately. `DataApiTokenServlet` and `PUT /api/robots/{id}/delegations/me` both need to observe `tokenVersion` for this invalidation path.

Failure behavior for legacy ownerless robots:

- `PUT /api/robots/{id}/delegations/me` returns a dedicated validation error such
  as `robot_owner_required`
- delegated token minting returns the same error if a previously valid robot has
  since lost owner metadata or was never backfilled
- do not infer ownership for `null` owners from callback URL, creator history,
  or grant state

Error response format for `robot_owner_required`:

- HTTP status: `400 Bad Request`
- JSON body:

```json
{
  "error": "robot_owner_required",
  "message": "This robot must have an assigned owner before delegation grants can be created or used",
  "robotId": "helper-bot@example.com"
}
```

### 4.9 Abuse Prevention

Mandatory protections:

- short-lived delegated access tokens
- explicit consent per robot and per user
- separate read vs write vs inbox scopes
- immediate revocation on robot pause, secret rotation, or deletion
- immediate revocation on human suspension/banned state
- rate limiting by delegated tuple `(robotId, subjectUserId)` plus a per-robot
  aggregate cap
- audit log for token minting and RPC/stream use

Recommended additional protections:

- default new grants to read-only
- require a second confirmation for write delegation
- show last-used time in the dashboard

#### Rate-limiting design

Add a small dedicated rate-limit service instead of burying limits inside individual servlets.

Recommended new class:

- `wave/src/main/java/org/waveprotocol/box/server/robots/auth/RobotApiRateLimiter.java`

Recommended enforcement points:

- `UnifiedRobotOperationExecutor`
- `RobotStreamWebSocketEndpoint`
- `DataApiTokenServlet` for delegated-token minting

Recommended initial keys:

- direct human requests: `subjectUserId + endpointKind`
- direct robot requests: `robotId + endpointKind`
- delegated requests: `(robotId, subjectUserId) + endpointKind`
- delegated aggregate guardrail: `robotId + endpointKind`

Recommended configurable limits:

- per delegated tuple read RPCs: 300 requests / 5 minutes
- per delegated tuple write RPCs: 60 requests / 5 minutes
- per delegated tuple token mints: 20 / hour
- per delegated tuple stream connection attempts: 30 / 5 minutes
- per robot aggregate delegated read RPCs: 1200 requests / 5 minutes
- per robot aggregate delegated write RPCs: 240 requests / 5 minutes
- per robot aggregate delegated token mints: 100 / hour
- per robot aggregate delegated stream connection attempts: 120 / 5 minutes

Enforcement rule for delegated traffic:

- evaluate both the tuple bucket and the aggregate robot bucket
- reject the request if either bucket is exhausted so one robot cannot multiply
  its total budget by delegating across many humans

For v1, a per-node in-memory token bucket is acceptable. Surface rate-limit failures as:

- HTTP `429` with JSON body for REST/RPC
- `RATE_LIMITED` JSON error event for WebSocket

### 4.10 Effort Estimate

- Complexity: High
- Implementation effort: about 6 to 8 engineer-weeks
- Main risks:
  - cross-backend persistence work across human and robot account stores
  - audit/revocation correctness
  - designing the right token claim shape without painting the system into a corner
  - migrating human `jwtVersion` safely without breaking legacy human tokens


## 5. Proposal 3: Robot API as a Developer-Friendly Client Protocol

### 5.1 Goal

Let alternative clients talk to Wave with ordinary JSON and bearer tokens instead of:

- protobuf codegen
- custom sequence-numbered RPC wrappers
- `ProtocolAuthenticate` with `JSESSIONID`
- first-party-only helper endpoints

### 5.2 Recommendation

Do not expose the current passive webhook format as the new client protocol.

Instead, define a new client-facing JSON protocol family on top of:

- the robot auth model
- the existing operation services
- the existing `ClientFrontend` subscription path

Recommended new path family:

- `GET /robot/client/v1/waves/{waveId}`
- `GET /robot/client/v1/search`
- `GET /robot/client/v1/profiles`
- `GET /robot/client/v1/attachments/{attachmentId}`
- `POST /robot/client/v1/operations`
- `POST /robot/client/v1/folder-actions`
- `WS /robot/client/v1/socket`

### 5.3 Why This Is Better Than Reusing the Webhook Format

The passive webhook format is optimized for capability-filtered business events.

Alternative UIs need:

- ordered snapshots and deltas
- commit notices
- channel identity
- reconnect/resync behavior
- own-submit suppression

Those semantics already exist in `WaveClientRpcImpl`, `ClientFrontendImpl`, and `WaveViewSubscription`. They do not exist in `EventGenerator` webhook bundles.

### 5.4 Minimal Operation Set

For an MVP alternative UI, the minimal client protocol should expose:

#### Read/search

- fetch full wave snapshot
- fetch a single wavelet
- search wave digests
- fetch profiles
- fetch attachments

#### Write/high-level mutate

- append blip
- continue thread / create child
- document modify
- add/remove participant
- set title
- folder actions (read/unread/archive/pin)

#### Real-time

- subscribe to a wave
- receive snapshot/delta/commit events
- submit writes correlated to a channel

### 5.5 Mapping to Existing Wave Concepts

#### Snapshot reads

Back the read endpoints with the same core models already used elsewhere:

- `CommittedWaveletSnapshot`
- `SnapshotSerializer`
- `FetchWaveService`

Recommended JSON response shape:

- `waveId`
- `wavelets[]`
- for each wavelet:
  - `waveletName`
  - `participants[]`
  - `documents[]`
  - `threads`
  - `hashedVersion`
  - `committedVersion`

Use stable field names that line up with current robot DTOs and existing protobuf concepts where practical.

#### High-level mutations

Back `POST /robot/client/v1/operations` with the same operation services already used by the Data API.

This keeps the first version simple and immediately reuses:

- `FetchWaveService`
- `SearchService`
- `ParticipantServices`
- `DocumentModifyService`
- `FolderActionService`
- `WaveletSetTitleService`

#### Raw OT path

A full editor eventually needs a phase-2 path that accepts and emits OT deltas directly.

Recommended phase-2 endpoint:

- `POST /robot/client/v1/wavelets/{waveletName}/deltas`

This should be backed by `ClientFrontend.submitRequest(...)` and a JSON representation of `ProtocolWaveletDelta`.

### 5.6 Recommended Internal Classes

Add:

- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientWaveServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientSearchServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientProfilesServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientAttachmentServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientOperationsServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientFolderServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientOperationGateway.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/client/RobotClientJsonSerializer.java`

Modify:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`

Transport-sharing rule:

- Proposal 3 does not add a separate `RobotClientSocketServlet`; the WebSocket
  transport is Proposal 4's `RobotStreamWebSocketEndpoint`
- `POST /robot/client/v1/operations` and WebSocket `submitOps` must both call
  the same `RobotClientOperationGateway`, which in turn delegates to
  `UnifiedRobotOperationExecutor`
- `RobotClientJsonSerializer` is shared by both the REST servlets and the
  WebSocket codec so the JSON schema for snapshots, operation payloads, and
  errors stays identical across transports
- future raw OT `submitDelta` handling should likewise flow through one shared
  service, not a socket-only implementation

### 5.7 Performance and Consistency Tradeoffs

#### Advantages over protobuf path

- easier for third-party developers
- easier to inspect and debug
- no protobuf toolchain required
- can be consumed directly from JS, Python, Rust, Go, or shell tools

#### Costs

- larger payloads
- slower serialization than protobuf
- more care needed to keep JSON schemas stable
- high-level mutation endpoints are not enough for full editor parity

#### Pagination design

Do not invent a new pagination scheme for search in v1. Reuse the current `SearchService` shape:

- query params: `query`, `index`, `limit`
- response fields: `results`, `index`, `limit`, `nextIndex`

This aligns with the existing `robot.search` semantics and keeps the first client endpoint thin.

#### Attachment design

Do not send clients to the first-party attachment endpoints directly.

Add `RobotClientAttachmentServlet` and require the same bearer-token principal model as the rest of `/robot/client/v1/*`.

Recommended v1 scope:

- read attachment bytes by `attachmentId`

Recommended phase-2 scope:

- upload attachment bytes with a matching write scope

#### Browser-origin policy

Default `/robot/client/v1/*` to same-origin only.

Validate `Origin` only when an `Origin` header is present. Apply the same
decision table in `RobotClient*Servlet` requests and
`RobotStreamWebSocketEndpoint` upgrade/auth handling:

| Case | Behavior |
| --- | --- |
| No `Origin` header | Allow. Treat the request as a non-browser client such as `curl`, mobile, or server-to-server traffic. |
| `Origin: null` | Reject unless the literal `null` origin is explicitly listed in the allowlist. |
| Same-origin request and no allowlist configured | Allow without extra Origin validation. |
| `Origin` present and allowlist configured | Allow only if the exact `Origin` value matches one of the configured entries. |

If cross-origin browser clients are needed, add a strict allowlist config such as:

- `robot.client.allowed_origins = ["https://example-ui.example.com"]`

and validate `Origin` on:

- `RobotClient*Servlet` requests
- WebSocket upgrade/auth for `RobotStreamWebSocketEndpoint`

Use camelCase consistently in all JSON examples below so JS clients and server
implementations share one wire shape.

#### Recommendation

Use a hybrid strategy:

- phase 1: JSON snapshots, JSON stream, high-level robot operations
- phase 2: JSON-encoded OT submit/stream for clients that need full editor fidelity

### 5.8 Effort Estimate

- Complexity: Medium to High
- MVP effort: about 4 to 6 engineer-weeks
- Additional OT parity effort: about 2 to 3 engineer-weeks on top


## 6. Proposal 4: Streaming / Push Events

### 6.1 Goal

Give robot and client implementations a real-time push channel without requiring the protobuf RPC stack.

### 6.2 Existing Streaming Infrastructure

The existing codebase already has the important pieces:

- `ServerRpcProvider` and the Jakarta WebSocket endpoint at `/socket`
- `WaveClientRpcImpl.open(...)`
- `ClientFrontendImpl.openRequest(...)`
- `WaveViewSubscription`
- `ClientFrontendImpl.submitRequest(...)`

This path already knows how to do:

- initial snapshots
- marker messages
- ordered deltas
- commit notices
- channel ids
- own-submit suppression

### 6.3 Recommended Design

Build a new JSON stream transport on top of `ClientFrontend`, not on top of the passive robot webhook flow.

#### New classes to add

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/stream/RobotStreamWebSocketEndpoint.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/stream/RobotStreamSession.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/stream/RobotStreamOpenListener.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/stream/RobotStreamMessageCodec.java`
- optional read-only fallback:
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/stream/RobotSseServlet.java`

#### Existing classes to modify

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/ClientFrontend.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/ClientFrontendImpl.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveletInfo.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/UserManager.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveViewSubscription.java`

### 6.4 Recommended WebSocket Message Model

#### Client to server

```json
{ "type": "auth", "token": "..." }
{ "type": "subscribe", "requestId": "r1", "waveId": "example.com!w+abc", "waveletIdPrefixes": ["conv+"], "knownVersions": [{ "waveletName": "example.com!conv+root", "hashedVersion": { "version": 42, "historyHash": "base64..." } }] }
{ "type": "unsubscribe", "channelId": "ch12" }
{ "type": "submitOps", "requestId": "r2", "channelId": "ch12", "operations": [ ... ] }
{ "type": "submitDelta", "requestId": "r3", "channelId": "ch12", "delta": { "hashedVersion": { "version": 42, "historyHash": "base64..." }, "author": "user@example.com", "operation": [ ... ], "addressPath": [] } }
```

#### Server to client

```json
{ "type": "auth.ok", "subject": "user@example.com", "actor": "helper-bot@example.com" }
{ "type": "subscription.opened", "requestId": "r1", "channelId": "ch12" }
{ "type": "wave.snapshot", "channelId": "ch12", "waveletName": "...", "snapshot": { ... }, "resultingVersion": { ... }, "commitNotice": { ... } }
{ "type": "wave.delta", "channelId": "ch12", "waveletName": "...", "appliedDeltas": [ ... ], "resultingVersion": { ... } }
{ "type": "wave.commit", "channelId": "ch12", "waveletName": "...", "commitNotice": { ... } }
{ "type": "wave.marker", "channelId": "ch12" }
{ "type": "submit.ack", "requestId": "r2", "operationsApplied": 3, "hashedVersionAfterApplication": { ... } }
{ "type": "error", "requestId": "r2", "code": "RESYNC_REQUIRED", "message": "..." }
```

`submitOps` is phase-1 functionality.

`submitOps` intentionally carries no target version. It routes through the
high-level robot operation services, which execute against current server state.

`submitDelta` is phase-2 functionality for OT/editor parity and should not be advertised as part of the first MVP.

`submitDelta` contract details:

- the embedded `delta` object is a JSON form of `ProtocolWaveletDelta` using proto3 JSON
  field-name mapping (camelCase); `hashedVersion` and `author` are required fields
- Per Proposal 2, `delta.author` must equal the delegated session's
  `effectiveSubject`, not the robot actor id
- Per Proposal 2, when routing to `ClientFrontend.submitRequest(...)`, the
  `loggedInUser` parameter must also be set to `effectiveSubject` to satisfy
  the server's `author.equals(loggedInUser)` validation (see §4.7 RPC and
  Stream Behavior)
- if the submit is accepted after operational transformation, the server returns
  `submit.ack` with `hashedVersionAfterApplication`
- if the submitted `hashedVersion` is too stale or cannot be bridged, surface
  `error { code: "RESYNC_REQUIRED" }`
- if the submit fails for another reason, surface
  `error { code: "SUBMIT_FAILED", message: "<wave-server error>" }`

All stream protocol examples use camelCase field names, including
`requestId`, `waveId`, `waveletIdPrefixes`, `knownVersions`, `channelId`,
`hashedVersion`, `historyHash`, `hashedVersionAfterApplication`, and
`addressPath`.

### 6.5 Consistency Model

Use the same underlying semantics as `ProtocolWaveletUpdate`.

Mandatory guarantees:

- ordering is preserved per wavelet
- each update carries a resulting version
- initial subscription ends with a marker
- commit notices are separate from delta delivery
- writes submitted on a channel are not echoed back to that same channel if the existing `WaveViewSubscription` suppression path can identify them

Recommended delivery contract:

- MVP guarantee is at-most-once delivery per socket session
- there is no server-side retry or replay buffer in `WaveViewSubscription` or
  `UserManager` today, so a failed WebSocket send can drop a delta permanently
  for that connection
- clients should apply updates idempotently by `waveletName + resultingVersion`
- recovery is reconnect plus fresh snapshot in phase 1, or `RESYNC_REQUIRED`
  plus resubscribe in later resume-aware phases

Future upgrade path:

- only claim at-least-once delivery if a new per-session buffering and replay
  layer is added to `RobotStreamSession`
- do not advertise at-least-once semantics in the MVP documentation

### 6.6 Resume and Reconnect

Current `ClientFrontendImpl.openRequest(...)` still rejects non-empty `knownWavelets`.

So the recommended sequence is:

- phase 1: reconnect by opening a new channel and replaying a fresh snapshot
- phase 2: extend `ClientFrontendImpl` to accept known versions for resync

Do not promise resumable streams in the first version unless `knownWavelets` support is implemented.

Additional phase-2 blocker that must be designed up front:

- accepting `knownWavelets` in `ClientFrontendImpl.openRequest(...)` is not
  sufficient on its own
- `WaveViewSubscription` tracks `lastVersion` per wavelet and
  `checkUpdateVersion(...)` currently throws if the next delta does not start at
  that expected version
- resumable subscribe therefore requires parsing all supplied `knownVersions`
  into a per-wavelet map before any channel is opened
- if any supplied known version is stale or outside the retained window, reject
  the entire subscription with `RESYNC_REQUIRED`
- do not partially open channels or silently downgrade only some wavelets to
  full snapshots
- the server should retain only a bounded window of recent deltas per wavelet,
  such as the last `N` deltas or the last `T` minutes, so `knownVersions`
  outside that window are considered stale
- stale `knownVersions` are expected reconnect cases, not client bugs; the
  server should return `RESYNC_REQUIRED` and require an explicit resubscribe
  with empty `knownVersions`
- `RESYNC_REQUIRED` does not trigger an automatic full snapshot replay

Concrete phase-2 code changes:

- extend the open path so `knownVersions` are parsed into a per-wavelet map
- validate the full map before any channel is opened
- initialize `WaveViewSubscription.WaveletChannelState.lastVersion` from that
  map only after the request passes the all-or-nothing check
- update `UserManager.subscribe(...)` and related tests to return
  `RESYNC_REQUIRED` when any requested wavelet is stale
- keep the phase-1 reconnect story as full snapshot replay only

Expected MVP reconnect cost:

- one fresh snapshot per subscribed wavelet
- no delta gap bridging

That cost is acceptable for inbox/viewer workloads but is too expensive for editor-grade reconnects, which is why resumable known-version support belongs in phase 2.

### 6.7 Why Not SSE First

SSE is attractive for simple readers, but a full interactive client also needs:

- subscribe/unsubscribe control messages
- write acknowledgements on the same session
- eventual OT submit/echo control

So the primary implementation should be WebSocket/JSON.

Optional phase-1.5:

- add `RobotSseServlet` for read-only dashboards and server-side consumers

### 6.8 Error Taxonomy and Backpressure

Define the WebSocket error vocabulary before implementation.

Recommended error codes:

- `AUTH_FAILED`
- `INVALID_MESSAGE`
- `SUBSCRIPTION_DENIED`
- `RATE_LIMITED`
- `RESYNC_REQUIRED`
- `SUBMIT_FAILED`
- `SERVER_OVERLOADED`

Recommended semantics:

- terminal:
  - `AUTH_FAILED`
  - repeated `INVALID_MESSAGE`
  - `SERVER_OVERLOADED`
- recoverable:
  - `RATE_LIMITED`
  - `SUBMIT_FAILED`
  - `RESYNC_REQUIRED`

Backpressure rule:

- keep a bounded per-session outbound queue
- if the queue exceeds a configured byte or message threshold, emit `SERVER_OVERLOADED` and close the socket
- do not allow unbounded buffering of delta streams

### 6.9 Connection Limits and Cleanup

Recommended limits:

- direct sessions: maximum concurrent WebSocket sessions per authenticated
  subject
- delegated sessions: maximum concurrent WebSocket sessions per
  `(robotId, effectiveSubject)` tuple
- maximum subscribed waves per session

Namespace rule for delegated streams:

- sharing the human participant's existing `UserManager` namespace is not
  acceptable because it lets multiple robots acting as the same human collide
  with each other and with the human's own browser sessions
- delegated streams therefore need a separate subscription namespace keyed by
  something like `(robotId, effectiveSubject)` even though access checks still
  run as `effectiveSubject`
- the exact namespace key is derived once and reused everywhere below
- split the stream routing context into:
  - `accessSubject` for `visibleWaveletsFor(...)`, permission checks, and delta
    authorship
  - `subscriptionNamespace` for `WaveletInfo` / `UserManager` bookkeeping and
    own-submit suppression
- direct browser and direct robot sessions use
  `subscriptionNamespace = authenticatedSubject.toString()`
- delegated robot sessions use
  `subscriptionNamespace = "robot:" + robotId + ":as:" + effectiveSubject.toString()`
- `WaveletInfo`, `UserManager`, own-submit suppression, and logging must all
  consume that exact namespace key so the routing remains deterministic across
  direct and delegated sessions

Recommended cleanup behavior:

- on `onClose` and `onError`, remove every server-side subscription bound to the session
- remove channel/session mappings from the direct or delegated subscription
  namespace manager
- release any held-back delta buffers

If a delegation grant is revoked while a delegated stream is active:

- send `error { "code": "DELEGATION_REVOKED", "message": "Delegation grant was revoked" }`
  before closing the socket
- close the WebSocket with a policy-violation code
- remove the session from the delegated namespace manager as part of the same
  cleanup path used by `onClose` and `onError`

### 6.10 Effort Estimate

- Complexity: Medium
- WebSocket/JSON MVP effort: about 4 to 5 engineer-weeks
- Optional SSE add-on: about 1 additional engineer-week


## 7. Proposal 5: Robot API as the Foundation for an Interactive Wave UI

### 7.1 Feasibility Assessment

This is feasible in phases.

#### Feasible with moderate extension work

- inbox/search views
- wave lists and digests
- read-only wave viewers
- reply/append flows
- participant and title management
- profile panels
- personal-helper actions on behalf of a user

#### Feasible but requires additional protocol work

- real-time collaborative reading with live updates
- optimistic submit/ack flows
- reconnect/resync

#### Hardest part

- full editor parity with the existing GWT client

### 7.2 Minimal Extension Set Required

The minimum set of new capabilities for a credible alternative UI is:

1. Unified RPC endpoint
2. Delegated act-as tokens
3. JSON stream endpoint
4. Snapshot/search/profile/folder JSON endpoints
5. High-level write endpoint

That is enough to build:

- a new inbox
- a wave viewer
- a lightweight compose/reply client
- a personal helper UI

### 7.3 Hard Blockers for Full Parity

#### Blocker 1: OT fidelity

The current robot write model is high-level.

- `document.modify`
- `wavelet.appendBlip`
- participant/title/folder actions

That is not the same as the first-party client's raw `ProtocolWaveletDelta` path. A full rich editor eventually needs:

- raw delta submit
- version-aware delta stream
- clear rebase/resync semantics

#### Blocker 2: Web client-specific coupling

Parts of the live client stack are still tuned for the first-party web client:

- `WaveClientRpcImpl`
- `WaveWebSocketClient`
- `RemoteViewServiceMultiplexer`
- fragment/viewport logic in `WaveClientRpcImpl`

Alternative UIs can ignore fragments at first, but not forever if they want good large-wave performance.

#### Blocker 3: Auth model

The browser/WebSocket path still leans on `HttpSession` and `ProtocolAuthenticate`.

A robot-style UI protocol should use bearer tokens directly.

#### Blocker 4: Auditability for act-as writes

If a robot posts as a human, the system needs a durable story for:

- who acted
- on whose behalf
- when
- through which grant

The current model has no first-class audit trail for this.

### 7.4 Recommended Developer Experience

The desired developer workflow should look like this:

1. Create a robot in `/account/robots`
2. Authorize it to act as the current user
3. Mint a short-lived bearer token
4. Open `WS /robot/client/v1/socket`
5. `auth`
6. `subscribe`
7. receive JSON snapshots and deltas
8. submit operations or deltas over JSON

No protobuf toolchain. No GWT dependencies. No cookie scraping.

### 7.5 Phased Product Roadmap

#### Phase A: Personal helper and read/write utility clients

Scope:

- search
- wave fetch
- folder actions
- append/edit through high-level robot operations
- delegated read/write/inbox grants

Outcome:

- enough for assistants, automation panels, and lightweight alternative clients

#### Phase B: Real-time viewer

Scope:

- JSON stream
- live snapshots/deltas
- channel-based submit/ack

Outcome:

- alternative live viewer and inbox

#### Phase C: Lightweight editor

Scope:

- structured document modify support
- improved reconnect handling
- attachment helpers

Outcome:

- useful but not yet parity-grade editor

#### Phase D: Full editor parity

Scope:

- JSON OT submit/stream
- viewport/fragments support
- richer conflict/rebase handling

Outcome:

- real alternative Wave UI foundation

### 7.6 Effort Estimate

- MVP alternative UI foundation: about 6 to 8 engineer-weeks once proposals 1, 2, and 4 land
- Full editor parity: about 10 to 14 additional engineer-weeks after MVP


## 8. Implementation Roadmap

### 8.1 Recommended Build Order

1. **Unify active and Data RPC internals without changing external behavior**
   - Build `UnifiedRobotOperationExecutor`
   - Build `UnifiedRobotOperationServiceRegistry`
   - Remove the `BaseApiServlet.operations` instance field race as part of the
     executor migration
   - Move `ActiveApiServlet` and `DataApiServlet` onto it

2. **Introduce principal-context auth**
   - Add `RobotApiPrincipal`
   - Extend `JwtRequestAuthenticator`
   - Add human `jwtVersion`

3. **Add delegation persistence and token issuance**
   - Build `RobotDelegationStore`
   - Add `DELEGATED_API_ACCESS`
   - Extend `DataApiTokenServlet`
   - Add dashboard and REST management endpoints

4. **Publish canonical `/robot/api/v2/rpc`**
   - Keep legacy endpoints as wrappers
   - Update docs and dashboard examples

5. **Ship JSON stream backed by `ClientFrontend`**
   - Add `RobotStreamWebSocketEndpoint`
   - Translate `ClientFrontend.OpenListener` into JSON events
   - Route writes through the shared operation gateway
   - For delegated streams, separate `accessSubject` from
     `subscriptionNamespace` before opening subscriptions

6. **Ship client-friendly read/search/profile/folder endpoints**
   - Reuse existing snapshot/profile/search/folder services
   - Stop requiring first-party-only auxiliary endpoints for new clients

7. **Add JSON OT submit/stream if full editor parity is required**
   - Expose a JSON representation of `ProtocolWaveletDelta`
   - Add reconnect/resync support with known versions
   - Seed `WaveViewSubscription` version state from reconnect data before live
     delta delivery
   - Route delegated OT submits with `loggedInUser = effectiveSubject` and
     `delta.author = effectiveSubject`

8. **Decide the future of the passive webhook transport**
   - either keep it as a compatibility adapter forever
   - or add a v2 webhook envelope and deprecate the old `EventMessageBundle` payload

### 8.2 Dependency Notes

- Proposal 2 depends on proposal 1's principal-context refactor.
- Proposal 4 should reuse the `ClientFrontend` stack, so it should come after proposal 1 even if the external endpoint is new.
- Proposal 5 depends on proposals 1, 2, and 4.
- OT JSON work should wait until the non-OT MVP proves the shape of the new client protocol.

### 8.3 Testing Strategy

Add new focused tests as each phase lands.

Recommended new test targets:

- `UnifiedRobotOperationExecutorTest`
- `UnifiedRobotApiServletTest`
- `RobotDelegationServiceTest`
- `DataApiTokenServletDelegationTest`
- `JwtRequestAuthenticatorDelegationTest`
- `DelegatedSubmitDeltaRoutingTest`
- `RobotStreamWebSocketEndpointTest`
- `RobotStreamWebSocketEndpointIT`
- `RobotClientProtocolIT`
- `RobotClientWaveServletTest`
- `RobotClientSearchServletTest`
- `RobotClientOperationsServletTest`
- `RobotClientAttachmentServletTest`
- `PassiveRobotCompatibilityIT`

Compatibility regression tests should continue to cover:

- `ActiveApiServletTest`
- `DataApiServletTest`
- `DataApiTokenServletTest`
- `RobotRegistrarImplTest`
- `RobotConnectorTest`
- `WaveClientRpcImplTest`

Add at least one local server sanity verification for the streaming phase:

- boot the server
- mint a v2 token
- connect to `/robot/client/v1/socket`
- authenticate
- subscribe to a small wave
- verify snapshot plus marker delivery
- submit a write and verify `submit.ack`

`DelegatedSubmitDeltaRoutingTest` should cover the submit-delta routing invariant
directly:

- both `delta.author` and `loggedInUser` equal `effectiveSubject` -> success
- `delta.author` matches but `loggedInUser` differs -> failure
- `loggedInUser` matches but `delta.author` differs -> failure
- both differ -> failure


## 9. Open Questions

1. Is a high-level operation-based client sufficient for the first alternative UI, or is raw OT JSON required from day one?
2. Should the new stream endpoint accept browser session JWTs directly for first-party experimental UIs, or should all clients mint explicit API tokens?
3. Do we want to consolidate existing first-party helper endpoints (`FetchServlet`, `FetchProfilesServlet`, `FolderServlet`) behind the new `/robot/client/v1/*` family, or keep both sets of endpoints indefinitely?
4. What is the deprecation policy for the passive webhook protocol and `capabilities.xml` discovery model?
5. If passive v2 webhook delivery is added later, should the server authenticate outbound deliveries with a server-signed bearer JWT, an HMAC signature, or both?
6. Should the dashboard provide a dedicated "claim ownership" flow for legacy robots with `null ownerAddress`, or should those robots continue to rely on re-registration with a fresh secret and callback URL instead of a backfill path?

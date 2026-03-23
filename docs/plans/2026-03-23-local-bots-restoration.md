# Local Bots Restoration Plan

**Date:** 2026-03-23
**Status:** Draft
**Epic:** Restore built-in robot agents (welcome, passwd, passwdadmin, registration)

## Problem

The Jakarta servlet migration excluded all robot agent classes because they
depend on `com.google.wave.api.AbstractRobot` which extends `javax.servlet.http.HttpServlet`
and uses `net.oauth` for OAuth validation. The agents themselves (WelcomeRobot,
PasswordRobot, PasswordAdminRobot, RegistrationRobot) are excluded by the
blanket `sourcePath.contains('/org/waveprotocol/box/server/robots/agent/')` rule
in `build.gradle`, and their parent classes `AbstractRobot` and `WaveService`
are excluded by exact-file rules because they import `net.oauth.*` and
`javax.servlet.*`.

## Key Insight

Built-in robot agents run **in the same JVM** as the wave server. They never
need HTTP or OAuth -- they can submit operations directly to `OperationUtil`
using the same code path that `BaseApiServlet.processOpsRequest()` uses, just
without the HTTP/OAuth wrapper.

## Architecture

```
Robot Agent --> LocalWaveService --> LocalOperationSubmitter --> OperationUtil --> WaveletProvider
```

- **LocalOperationSubmitter**: A `@Singleton` service that executes a list of
  `OperationRequest` objects directly against `OperationUtil.executeOperation()`
  and returns `List<JsonRpcResponse>`. Pattern copied from
  `BaseApiServlet.processOpsRequest()` but without any HTTP or OAuth.

- **LocalWaveService**: Replaces `WaveService` for in-JVM usage. Keeps the same
  `OperationQueue`-based API (`newWave`, `submit`, `fetchWavelet`,
  `blindWavelet`) but replaces `makeRpc()` with a direct call to
  `LocalOperationSubmitter`. No `net.oauth` imports. `setupOAuth()` is a no-op.

- **AbstractRobot (Jakarta override)**: Strips `javax.servlet.http.HttpServlet`
  inheritance, removes `net.oauth.OAuthException` import, removes HTTP
  `doGet`/`doPost`/`deserializeEvents`, keeps `processEvents()` and event
  handler dispatch. Uses `LocalWaveService` instead of `WaveService`.

- **Agent base classes**: Jakarta overrides of `AbstractBaseRobotAgent`,
  `AbstractCliRobotAgent`, `RobotAgentUtil` -- near-identical copies, only
  changing the import of `AbstractRobot` to the Jakarta override version.

- **PasswordRobot**: Jakarta override that replaces `org.eclipse.jetty.util.MultiMap`
  usage with a direct `MultiMap<String>` constructor + `put()` calls (Jetty 12
  compatible).

- **PasswordAdminRobot, RegistrationRobot**: Near-identical Jakarta overrides
  (no javax/net.oauth dependencies to fix, just need to be in the override tree
  so they pick up the Jakarta `AbstractCliRobotAgent`).

- **WelcomeRobot**: Jakarta override where `setupOAuth()` becomes a no-op via
  `LocalWaveService`, and `greet()` uses `LocalOperationSubmitter` internally.

## Tasks

### Task 1: LocalOperationSubmitter
**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/LocalOperationSubmitter.java`
- `@Singleton`, `@Inject` constructor
- Dependencies: `WaveletProvider`, `EventDataConverterManager`, `ConversationUtil`, `@Named("ActiveApiRegistry") OperationServiceRegistry`
- Method: `submitOperations(List<OperationRequest>, ParticipantId) -> List<JsonRpcResponse>`
- Creates `OperationContextImpl`, iterates operations calling `OperationUtil.executeOperation()`, calls `OperationUtil.submitDeltas()`, collects responses

### Task 2: LocalWaveService
**File:** `wave/src/jakarta-overrides/java/com/google/wave/api/LocalWaveService.java`
- Keeps `OperationQueue`-based API: `newWave()`, `submit()`, `fetchWavelet()`, `blindWavelet()`, `search()`
- `submit()` serializes the `OperationQueue` to `List<OperationRequest>`, passes to `LocalOperationSubmitter`
- `setupOAuth()` is a no-op
- No `net.oauth`, no `HttpFetcher`, no `ConsumerData`
- Constructor takes `LocalOperationSubmitter` and `ParticipantId` (the robot's identity)

### Task 3: Update AbstractRobot (Jakarta override)
**File:** `wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java`
- Remove `extends HttpServlet`
- Remove `net.oauth.OAuthException` import
- Remove `javax.servlet.*` imports
- Remove `doGet()`, `doPost()`, `processRpc()`, `deserializeEvents()`, `serializeOperations()`
- Remove `processCapabilities()`, `processVerifyToken()`, `processProfile()`
- Keep: `processEvents()`, `computeCapabilityMap()`, `computeHash()`, event handler methods
- Use `LocalWaveService` instead of `WaveService`
- `setupOAuth()` becomes a no-op (delegates to `LocalWaveService.setupOAuth()`)
- Add `setLocalWaveService(LocalWaveService)` for injection

### Task 4: Agent base classes
**Files:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/AbstractBaseRobotAgent.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/AbstractCliRobotAgent.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/RobotAgentUtil.java`

Near-identical copies. The only change is they resolve `AbstractRobot` from the
Jakarta override (automatic via classpath ordering).

### Task 5: PasswordRobot (Jakarta override)
**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/passwd/PasswordRobot.java`
- Replace `MultiMap<String> parameters = new MultiMap<String>()` +
  `parameters.putAllValues(ImmutableMap.of(...))` with
  `MultiMap<String> parameters = new MultiMap<String>()` +
  `parameters.put("password", password)` + `parameters.put("address", ...)` (Jetty 12 compat)

### Task 6: Remaining agents
**Files:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/passwd/PasswordAdminRobot.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/registration/RegistrationRobot.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/agent/welcome/WelcomeRobot.java`

Near-identical copies. WelcomeRobot's `greet()` method works unchanged because
`setupOAuth()` and `submit()`/`fetchWavelet()`/`newWave()` are now backed by
`LocalWaveService` -> `LocalOperationSubmitter`.

### Task 7: Wire into ServerMain + Guice
- Add `initializeRobotAgents(injector)` call in `ServerMain.run()`
- Instantiate all 4 agents from the injector
- Add `LocalOperationSubmitter` binding in `JakartaRobotApiBindingsModule`

### Task 8: Update build.gradle exclusions
- Remove `'com/google/wave/api/AbstractRobot.java'` from `jakartaExactExcludes`
  (the Jakarta override replaces it)
- The blanket `robots/agent/` exclusion only affects `main` source, not
  `jakarta-overrides`

### Task 9: Verify RobotApiModule stays excluded
- `RobotApiModule.java` must remain in `jakartaExactExcludes` -- it references
  `net.oauth.OAuthValidator`
- The Jakarta replacement is `JakartaRobotApiBindingsModule`

### Task 10: Tests
- Verify `compileJava` passes
- Unit test for `LocalOperationSubmitter` (mock `WaveletProvider`)
- Integration smoke test: instantiate agents, verify registration

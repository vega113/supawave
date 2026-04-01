# Robot API: Remove Legacy OAuth & Complete JWT Migration

## Problem Statement

The Apache Wave robot API authentication is in a half-migrated state. The Jakarta runtime
servlets (`ActiveApiServlet`, `DataApiServlet`) already authenticate via JWT Bearer tokens,
but significant legacy OAuth 1.0 code still ships in the jakarta-overrides path and is
wired into Guice. This dead code bloats the build, pulls in the unmaintained `net.oauth`
library, and creates confusion about which auth path is canonical.

Additionally, several JWT migration gaps remain: no scope enforcement, no Active API token
endpoint, hardcoded permissive revocation, and the robot client SDK still only speaks OAuth.

## Current State (What Works)

| Component | Status | File (jakarta-overrides) |
|-----------|--------|--------------------------|
| `ActiveApiServlet` | JWT via `JwtRequestAuthenticator` | `robots/active/ActiveApiServlet.java` |
| `DataApiServlet` | JWT via `JwtRequestAuthenticator` | `robots/dataapi/DataApiServlet.java` |
| `DataApiTokenServlet` | Issues `DATA_API_ACCESS` JWTs | `robots/dataapi/DataApiTokenServlet.java` |
| `BaseApiServlet` | Clean — no OAuth dependency | `robots/dataapi/BaseApiServlet.java` |

## What's Broken / Dead

### 1. Legacy OAuth Files Still in jakarta-overrides (REMOVE)

These files are **not registered in Jakarta `ServerMain.java`** but still compile and
are wired by Guice, pulling in `net.oauth`:

| File | Why it's dead |
|------|---------------|
| `robots/dataapi/DataApiOAuthServlet.java` | Full 3-legged OAuth flow. Not registered as a servlet in Jakarta ServerMain. Replaced by `DataApiTokenServlet`. |
| `robots/dataapi/DataApiTokenContainer.java` | In-memory request/access token cache for OAuth. Only used by `DataApiOAuthServlet`. JWT tokens are stateless — no container needed. |
| `robots/util/JakartaHttpRequestMessage.java` | Bridges `net.oauth.OAuthMessage` to `jakarta.servlet.HttpServletRequest`. Only consumer is `DataApiOAuthServlet`. |

### 2. OAuth Guice Bindings in RobotApiModule (REMOVE)

`robots/RobotApiModule.java` (jakarta-overrides) still provides:

```java
@Provides @Singleton OAuthValidator provideOAuthValidator()
@Provides @Singleton OAuthServiceProvider provideOAuthServiceProvider(Config config)
```

Plus named string bindings for OAuth paths:
- `authorize_token_path` → `/OAuthAuthorizeToken`
- `request_token_path` → `/OAuthGetRequestToken`
- `access_token_path` → `/OAuthGetAccessToken`
- `all_tokens_path` → `/OAuthGetAllTokens`

These are consumed **only** by `DataApiOAuthServlet`. Once that servlet is deleted, all
these bindings and the `import net.oauth.*` statements become dead code.

### 3. Client Library Still OAuth-Only (NEEDS MIGRATION)

| File | Issue |
|------|-------|
| `com/google/wave/api/AbstractRobot.java` (both paths) | `setupOAuth()` methods, imports `net.oauth.OAuthException`. No JWT client support. |
| `com/google/wave/api/WaveService.java` | Imports entire `net.oauth.*` stack. HTTP client uses OAuth signing. |
| `com/google/wave/api/oauth/*` | Entire package: `OAuthService`, `OAuthServiceImpl`, `OpenSocialHttp*`, etc. Legacy gadget/robot OAuth client code. |

### 4. Other Legacy OAuth Surfaces

| File | Issue |
|------|-------|
| `org/waveprotocol/box/server/util/OAuthUtil.java` | Helper wrapping `OAuthProblemException`. Only used by `DataApiTokenContainer`. |
| `org/waveprotocol/box/expimp/OAuth.java` | Import/export OAuth helper. Separate tooling — may defer. |
| `build.sbt` lines ~300-303 | `net.oauth.core` dependencies (`oauth`, `oauth-consumer`, `oauth-provider`). |

### 5. Test Files to Remove/Rewrite

| Test File | Status |
|-----------|--------|
| `test/.../dataapi/DataApiOAuthServletTest.java` | Tests legacy OAuth servlet — delete |
| `test/.../dataapi/DataApiTokenContainerTest.java` | Tests legacy token container — delete |
| `test/.../active/ActiveApiServletTest.java` | Tests legacy OAuth active API — rewrite for JWT |
| `test/.../dataapi/DataApiServletTest.java` | Tests legacy OAuth data API — rewrite for JWT |
| `jakarta-test/.../DataApiOAuthServletJakartaIT.java` | Integration test for legacy OAuth — delete |
| `test/.../oauth/impl/OAuthServiceImplRobotTest.java` | Tests legacy client OAuth — delete |

## JWT Migration Gaps to Fix

### Gap 1: Empty Scopes on Token Issuance

`DataApiTokenServlet.issueToken()` creates claims with `Set.of()` (empty scopes):

```java
JwtClaims claims = new JwtClaims(
    JwtTokenType.DATA_API_ACCESS, issuer, subject, ...
    Set.of(),  // <-- EMPTY SCOPES
    issuedAt, issuedAt, expiresAt, 0L);
```

**Fix:** Define standard scopes and populate them:
- `wave:data:read` — fetch waves, search, export
- `wave:data:write` — modify waves, create wavelets
- `wave:robot:active` — Active API operations
- `wave:admin` — admin-only operations

### Gap 2: No Active API Token Endpoint

`DataApiTokenServlet` only issues `DATA_API_ACCESS` tokens. A robot that wants to use
the Active API (`/robot/rpc`) needs a `ROBOT_ACCESS` token but has no endpoint to get one.

**Fix:** Either:
- (a) Extend `DataApiTokenServlet` to accept a `token_type` parameter (`data_api` or `robot`)
- (b) Create a parallel `ActiveApiTokenServlet` at `/robot/rpc/token`

Option (a) is simpler — rename to `RobotTokenServlet` at `/robot/token` and let
the `token_type` parameter control which `JwtTokenType` / `JwtAudience` pair is used.

### Gap 3: Hardcoded Permissive Revocation

`JwtRequestAuthenticator` line ~57:
```java
new JwtRevocationState(0, 0)  // Always permits
```

**Fix:** Wire in a real `JwtRevocationState` backed by the account store's version field.
When a robot secret is rotated or robot is paused, bump the version to invalidate outstanding tokens.

### Gap 4: No Scope Enforcement in Servlets

Neither `ActiveApiServlet` nor `DataApiServlet` check token scopes. All operations are
permitted if the token type and audience match.

**Fix:** Add scope checking in `BaseApiServlet.processOpsRequest()` or in the individual
servlets. Map operation types to required scopes.

### Gap 5: RobotAccountData Still Uses consumerSecret

`RobotAccountData` interface exposes `getConsumerSecret()`. The `DataApiTokenServlet`
validates robots via this shared secret in the `client_credentials` flow. This works
but is not ideal — shared secrets are weaker than asymmetric keys.

**Lower priority:** In a future phase, add `getPublicJwk()` / `getJwksUrl()` fields
to `RobotAccountData` for asymmetric robot authentication.

### Gap 6: ApiDocsServlet Documentation Accuracy

`ApiDocsServlet` serves `/api-docs`, `/api/openapi.json`, `/llms.txt`, and `/llms-full.txt`.
It needs to:
- Document the JWT token endpoint (`/robot/dataapi/token` or new `/robot/token`)
- Remove any references to the legacy OAuth flow (`/robot/dataapi/oauth/*`)
- Document `Authorization: Bearer <jwt>` as the auth mechanism
- Document the `client_credentials` grant type for robots
- Document the session-based token generation for users

## Execution Plan

### Phase A: Remove Dead OAuth Code (jakarta-overrides)

1. Delete `DataApiOAuthServlet.java` from jakarta-overrides
2. Delete `DataApiTokenContainer.java` from jakarta-overrides
3. Delete `JakartaHttpRequestMessage.java` from jakarta-overrides
4. Remove OAuth Guice bindings from `RobotApiModule.java` (keep non-OAuth bindings)
5. Remove `net.oauth` imports from `RobotApiModule.java`
6. Delete corresponding test files
7. Verify build compiles without `net.oauth` in jakarta runtime path

### Phase B: Unify Token Endpoint

1. Rename `DataApiTokenServlet` → `RobotTokenServlet`
2. Move to `/robot/token` (keep `/robot/dataapi/token` as redirect for backwards compat)
3. Add `token_type` parameter: `data_api` (default) or `robot`
4. Issue `ROBOT_ACCESS` / `JwtAudience.ROBOT` tokens when `token_type=robot`
5. Update `ServerMain.java` route registration

### Phase C: Add Scope Enforcement

1. Define scope constants (e.g., in `JwtScopes.java`)
2. Populate scopes in `RobotTokenServlet.issueToken()` based on token type
3. Add `requiredScope` parameter to `JwtRequestAuthenticator.authenticate()`
4. Validate scopes in `ActiveApiServlet` and `DataApiServlet`

### Phase D: Fix Revocation

1. Add `getTokenVersion()` to `RobotAccountData`
2. Wire `JwtRevocationState` from account store in `JwtRequestAuthenticator`
3. Bump token version on `rotateSecret()`, `setPaused()`, `unregister()`

### Phase E: Update API Documentation

1. Update `ApiDocsServlet` to document JWT auth flow
2. Remove OAuth endpoint documentation
3. Add `Authorization: Bearer` examples to all operation docs
4. Update OpenAPI spec (`/api/openapi.json`) with JWT security scheme
5. Update `llms.txt` and `llms-full.txt` for AI consumption
6. Update `README.md` to remove "OAuth surfaces are unsupported" caveat

### Phase F: Client Library (Deferred — larger scope)

1. Add `setupJwt(String token)` to `AbstractRobot`
2. Update `WaveService` to send `Authorization: Bearer` headers
3. Deprecate `setupOAuth()` methods
4. Eventually remove `com.google.wave.api.oauth` package entirely

## Files to Touch (Phases A–E)

### Delete
- `wave/src/jakarta-overrides/.../robots/dataapi/DataApiOAuthServlet.java`
- `wave/src/jakarta-overrides/.../robots/dataapi/DataApiTokenContainer.java`
- `wave/src/jakarta-overrides/.../robots/util/JakartaHttpRequestMessage.java`
- `wave/src/main/java/.../server/util/OAuthUtil.java`
- `wave/src/test/.../dataapi/DataApiOAuthServletTest.java`
- `wave/src/test/.../dataapi/DataApiTokenContainerTest.java`
- `wave/src/jakarta-test/.../DataApiOAuthServletJakartaIT.java`
- `wave/src/test/.../oauth/impl/OAuthServiceImplRobotTest.java`

### Modify
- `wave/src/jakarta-overrides/.../robots/RobotApiModule.java` — remove OAuth bindings
- `wave/src/jakarta-overrides/.../robots/dataapi/DataApiTokenServlet.java` — rename, add scope + token_type
- `wave/src/jakarta-overrides/.../rpc/ApiDocsServlet.java` — update JWT docs
- `wave/src/jakarta-overrides/.../ServerMain.java` — update route registration
- `wave/src/main/java/.../authentication/jwt/JwtRequestAuthenticator.java` — scope validation, real revocation
- `wave/src/main/java/.../account/RobotAccountData.java` — add tokenVersion field
- `wave/src/main/java/.../account/RobotAccountDataImpl.java` — implement tokenVersion
- `build.sbt` — remove `net.oauth` from jakarta compilation scope (keep for javax if needed)

### Create
- `wave/src/main/java/.../authentication/jwt/JwtScopes.java` — scope constants
- New test files for JWT-based robot API authentication

## Copilot GPT-5.4 xhigh Review Findings

Independent review confirmed:

1. **Active API is effectively broken today** — `ActiveApiServlet` requires `ROBOT_ACCESS`/`ROBOT`
   tokens but `DataApiTokenServlet` only mints `DATA_API_ACCESS`/`DATA_API`. There is NO Active
   API token endpoint. This is the highest-priority gap.

2. **JWT hardening is incomplete** — `JwtRequestAuthenticator` and `RobotApiServlet` both use
   `new JwtRevocationState(0, 0)` (always permits). `DataApiTokenServlet` emits empty scopes.
   Nothing enforces scopes at the operation level.

3. **Token lifetime is too permissive** — Default expiry `0` maps to ~100 years. Secret rotation
   via `rotateSecret()` does NOT revoke already-issued JWTs. This is a security gap.

4. **Passive callbacks are unauthenticated** — `RobotConnector` POSTs event bundles to robot
   callback URLs with zero auth headers or signatures. Robots cannot verify the caller is
   the Wave server.

5. **Jakarta OAuth leftovers are already build-excluded** — `build.sbt` already excludes Jakarta
   `RobotApiModule`, `DataApiOAuthServlet`, `DataApiTokenContainer`, and `JakartaHttpRequestMessage`
   from compilation. Removal risk is very low. These are truly dead files.

6. **Main SDK is still OAuth-only** — `AbstractRobot` + `WaveService` require `setupOAuth()` and
   even reference dead Google Wave endpoints.

7. **Docs halfway migrated** — `ApiDocsServlet` documents Data API JWT flow but not `/robot/rpc`.
   The Active API gap is undocumented.

## Build & Verification

```bash
# Compile jakarta path only (no javax legacy)
sbt "wave/compile"

# Run robot API tests
sbt "wave/testOnly *ActiveApiServlet* *DataApiServlet* *DataApiTokenServlet* *RobotRegistrar*"

# Verify no net.oauth imports remain in jakarta-overrides
grep -r "net.oauth" wave/src/jakarta-overrides/
# Expected: zero matches after Phase A

# Verify API docs render correctly
curl http://localhost:9898/api-docs | grep -i oauth
# Expected: zero matches (or only "replaced by JWT" notes)
```

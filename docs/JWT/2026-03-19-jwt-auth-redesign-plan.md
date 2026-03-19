# JWT Auth Redesign Plan Draft

## Goal

Replace the current mix of Jetty session cookies, OAuth 1.0 request signing, robot shared secrets, and ad hoc gadget security tokens with a single JWT-centered authentication design that:

- removes the external `net.oauth` dependency line,
- stops requiring browser JavaScript to read `JSESSIONID`,
- replaces robot shared secrets with asymmetric key material,
- gives the Data API a modern bearer-token model,
- defines an explicit future for gadget `st` tokens instead of relying on legacy OpenSocial behavior.

This document is a research draft only. It proposes architecture, sequencing, and affected code surfaces. It does not authorize product-code changes by itself.

## Current State

Canonical companion inventory:

- [`2026-03-19-jwt-auth-code-surface-inventory.md`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/docs/JWT/2026-03-19-jwt-auth-code-surface-inventory.md)

### Browser and WebSocket auth

- Human login is handled by [`AuthenticationServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java), which writes authenticated state into the Jetty `HttpSession` through [`SessionManagerImpl`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java).
- The browser currently reads `JSESSIONID` from JavaScript and sends it over `ProtocolAuthenticate` on the WebSocket path in [`WaveWebSocketClient`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java).
- Dev config explicitly disables `HttpOnly` for the session cookie in [`application.conf`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/config/application.conf) to keep that fallback working.

Live-runtime note:

- After the `javax` retirement, the live server path is the Jakarta override tree under `wave/src/jakarta-overrides/java/**`. The matching `wave/src/main/java/**` classes remain useful as shared code or migration references, but implementation work must treat the Jakarta variants as the runtime source of truth.

### Active robot API

- `/robot/rpc` is served by [`ActiveApiServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java).
- A robot authenticates by presenting an OAuth 1.0 consumer key equal to its participant id; the server looks up the shared secret from [`RobotAccountData`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java).
- Robot registration emits a consumer secret through [`RobotRegistrationServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java) and persists it through [`RobotRegistrarImpl`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java).
- The runtime bindings and validator construction are owned by [`RobotApiModule`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiModule.java), with Jakarta override counterparts also in scope during migration.

### Data API

- `/robot/dataapi/oauth/*` is served by [`DataApiOAuthServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java).
- `/robot/dataapi` and `/robot/dataapi/rpc` are served by [`DataApiServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java).
- The current flow is a three-legged OAuth 1.0 exchange with request/access tokens held in memory by [`DataApiTokenContainer`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java).
- The shared request-processing seam is [`BaseApiServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java), which owns the OAuth validator dependency that both Active and Data API requests inherit.
- The persisted user-authorization state currently lives in [`OAuthUser`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/oauth/impl/OAuthUser.java), so Data API migration is both a request-path and a persistence cleanup.

### Gadgets

- The local server exposes only `/gadget/gadgetlist` through [`GadgetProviderServlet`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java).
- `/gadgets/*` is a transparent proxy to the configured gadget server in [`ServerMain`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/ServerMain.java).
- The web client still has a gadget security-token concept named `st` in [`GadgetWidget`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetWidget.java), but [`GadgetDataStoreImpl`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetDataStoreImpl.java) currently stores `null` for the token in the cached metadata path.
- The gadget-facing auth helper stack also includes [`PopupLoginFormHandler`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java), [`SimpleLoginFormHandler`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/oauth/impl/SimpleLoginFormHandler.java), [`OpenSocialHttpMessage`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpMessage.java), and [`OpenSocialHttpResponseMessage`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpResponseMessage.java).

### Import / export auth helper

- [`org/waveprotocol/box/expimp/OAuth.java`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/expimp/OAuth.java) is a separate legacy OAuth surface for import/export tooling.
- [`com/google/wave/api/WaveService.java`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/com/google/wave/api/WaveService.java) and [`org/waveprotocol/box/server/util/OAuthUtil.java`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/util/OAuthUtil.java) also still depend on the old auth model and must be either retired or explicitly brought into scope during JWT migration.

## Proposed Target Architecture

### 1. Introduce one issuer and one validator

Add a single auth subsystem that issues and validates signed JWTs across browser, robot, Data API, and gadget contexts.

Design requirements:

- Publish a local JWKS endpoint.
- Use asymmetric signing keys with explicit `kid`.
- Allow only an explicit algorithm allowlist and explicit token `typ` values, following RFC 8725 guidance.
- Validate `iss`, `aud`, `exp`, `nbf`, `iat`, `jti`, `sub`, and a version or revocation marker.
- Keep token parsing and authorization checks in one shared validator path instead of per-servlet ad hoc logic.

Recommended token types:

- `wave-session+jwt`
- `wave-access+jwt`
- `wave-robot+jwt`
- `wave-gadget+jwt`

Recommended ownership:

- Task `incubator-wave-jwt-auth.1` locks the issuer model, key format, token types, audiences, and claim contract.
- Task `incubator-wave-jwt-auth.2` implements the shared validator on the live Jakarta runtime path.
- Task `incubator-wave-jwt-auth.6` resolves whether gadgets get a JWT `st` model or are explicitly de-scoped.

### 2. Browser auth moves from Jetty session identity to cookie-carried JWT identity

Recommended design:

- After successful login, mint a short-lived browser session JWT and store it in a `Secure`, `HttpOnly`, `SameSite=Lax` cookie, preferably with a `__Host-` prefix.
- Stop exposing the auth cookie to browser JavaScript.
- Use the WebSocket HTTP upgrade handshake to authenticate the user instead of requiring `ProtocolAuthenticate` to replay `JSESSIONID` from JS.
- Keep `__session` bootstrap data for non-secret fields only, such as the current address and client id seed.

Recommended browser-session claim shape:

- `iss`: Wave auth issuer
- `sub`: authenticated participant id
- `aud`: `wave-web` and `wave-ws`
- `typ`: `wave-session+jwt`
- `exp`, `iat`, `nbf`, `jti`
- `sid`: stable server-side session id or renewal handle if the design keeps server-managed renewal
- `amr`: authentication method reference when useful for operational debugging

Browser-specific constraints:

- Cookie payload should stay comfortably below the 4 KB browser cookie limit; avoid embedding large scope lists or profile data.
- State-changing HTTP endpoints should keep explicit CSRF protection even with `SameSite=Lax`, using Origin or Referer enforcement plus a double-submit or custom-header strategy where needed.
- WebSocket expiry behavior should be explicit: either close connections on expiry or require a short-lived handshake token that aligns with the cookie renewal window.

Why this is the right direction:

- The current client reads `JSESSIONID` from JS, which is why dev config sets `session_cookie_http_only = false`.
- OWASP’s HTML5 guidance warns against storing session identifiers in browser storage reachable by JavaScript.
- The Jakarta WebSocket path already contains fallback logic that trusts the authenticated upgrade user when explicit token lookup fails, which makes a handshake-led migration realistic.

### 3. Active robots move from shared-secret OAuth 1.0 to asymmetric JWT auth

Recommended design:

- Replace robot `consumerSecret` registration with robot key registration.
- Persist either a robot JWKS URL or repo-owned public JWK metadata plus active `kid`.
- Add a token issuance path where robots authenticate using a signed client assertion JWT and receive short-lived access JWTs scoped for robot actions.
- Replace `/robot/rpc` OAuth 1.0 signature validation with bearer-token validation against the shared JWT validator.
- For server-to-robot callbacks, send a short-lived server-signed JWT in `Authorization: Bearer ...` rather than relying on legacy robot OAuth semantics.

Claim shape for robot access tokens:

- `iss`: Wave auth issuer
- `sub`: robot participant id
- `aud`: `wave-robot-api`
- `scope`: `robot:active` and other fine-grained scopes as needed
- `jti`: unique token id
- `ver`: robot token version for revocation/rotation

This removes two fragile assumptions in the current system:

- robot identity equals OAuth consumer key,
- the only shared secret for that robot lives forever in account persistence.

### 4. Data API moves from 3-legged OAuth 1.0 to scoped JWT bearer tokens

Recommended design:

- Replace the request-token and authorize-token dance with an authorization-code-style flow bound to the browser login session.
- Use PKCE for browser-driven delegated access.
- Issue RFC 9068-style access JWTs for Data API access.
- Replace [`DataApiTokenContainer`](/Users/vega/devroot/worktrees/incubator-wave/jwt-auth-research-lane/wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java) with signed-token validation plus revocation/version checks.
- Keep Data API scopes explicit, for example:
  - `wave:data:read`
  - `wave:data:write`
  - `wave:data:profile`

Recommended Data API access-token claim shape:

- `iss`: Wave auth issuer
- `sub`: user participant id or service principal id
- `aud`: `wave-data-api`
- `typ`: `wave-access+jwt`
- `scope`: one or more `wave:data:*` scopes
- `exp`, `iat`, `nbf`, `jti`
- `act` or equivalent actor metadata when a service token acts on behalf of a user in a future delegated mode

Delegated-user note:

- The first JWT implementation should prefer service-principal access only.
- If delegated-user mode is restored later, the token must distinguish user subject from client or actor subject explicitly instead of reusing the robot/service claim model.

This change is structurally important because the current access-token store is in-memory only, which makes horizontal scale, restart behavior, and operational introspection weak.

### 5. Gadgets get an explicit JWT boundary instead of implicit legacy `st` behavior

This area needs a deliberate product decision before implementation. The current system mixes:

- a local gadget list servlet,
- a transparent proxy to an external gadget server,
- client-side support for `st`,
- a metadata path that does not currently retain a real gadget security token.

Recommended target if gadgets remain supported:

- Define `st` as a short-lived gadget JWT minted by Wave or by a trusted gadget auth broker.
- Scope it to user, wavelet, gadget instance id, and gadget origin or audience.
- Keep it short-lived and one-purpose; do not reuse browser or Data API access tokens as gadget tokens.

If gadgets are strategically being retired, the redesign should say so explicitly and avoid spending JWT work on the proxy path.

## Recommended Migration Sequence

### Phase 0: Decision and boundary lock

- Decide whether Wave is the token issuer or whether it will trust an external issuer.
- Decide whether gadgets stay in scope.
- Decide whether robot registration remains interactive or becomes admin- or config-driven.
- Decide whether import/export tooling must migrate at the same time as robots.

Exit criteria:

- One issuer model.
- One revocation model.
- One answer for gadgets.

Decision deadlines for this phase:

- Issuer model and key configuration: resolve inside `incubator-wave-jwt-auth.1` before any implementation PR opens.
- Gadget support decision: resolve before `incubator-wave-jwt-auth.6` starts implementation.
- Import/export OAuth scope decision: resolve together with the removal lane before `incubator-wave-jwt-auth.2` starts.

### Phase 1: Shared JWT platform

- Add key management, JWKS publication, token minting primitives, token validation, and scope checking.
- Add a single auth abstraction that servlets and WebSocket handlers can use instead of ad hoc session or OAuth logic.
- Put the shared validator at a uniform enforcement seam: an auth filter for HTTP servlet entrypoints plus a matching handshake validator for WebSocket upgrade requests.
- Keep legacy flows running in parallel behind compatibility gates.

Exit criteria:

- JWT issuance and verification are available without any product behavior change yet.

### Phase 2: Browser and WebSocket migration

- Mint browser session JWTs after password or client-certificate login.
- Teach HTTP entrypoints to trust JWT identity.
- Stop reading `JSESSIONID` from JavaScript.
- Remove or shrink `ProtocolAuthenticate` to compatibility-only behavior.

Exit criteria:

- `session_cookie_http_only` can be true in dev and production.
- WebSocket auth works from the handshake path.

### Phase 3: Robot migration

- Replace robot secret registration with robot key registration.
- Add robot token issuance or direct signed-assertion validation.
- Migrate `/robot/rpc` and passive robot callback auth.
- Rotate existing robots through a compatibility bridge until all are re-registered with key material.

Exit criteria:

- No new robot depends on a persisted shared consumer secret.

### Phase 4: Data API migration

- Replace OAuth 1.0 authorization endpoints with JWT-based delegated token issuance.
- Add scopes and audience checks on the Data API servlet path.
- Remove request/access-token caches once compatibility traffic reaches zero.

Exit criteria:

- No new Data API client depends on the OAuth 1.0 dance.

### Phase 5: Gadget decision implementation

- Either implement gadget JWTs and broker validation,
- or explicitly retire the gadget auth surface from the modernization roadmap.

Exit criteria:

- `st` is either a documented JWT or no longer part of the supported product surface.

### Phase 6: Legacy removal

- Remove `net.oauth` dependencies.
- Delete `DataApiTokenContainer`.
- Remove robot consumer-secret persistence and presentation.
- Delete compatibility-only OAuth pages, templates, and tests after rollout.

Exit criteria:

- No runtime auth path depends on OAuth 1.0 or shared robot secrets.

Parallel-lane dependency:

- The current `incubator-wave-modernization.3` removal lane is responsible for stripping the legacy OAuth dependencies and default runtime wiring now.
- This JWT plan inherits that short-term retirement work rather than redoing it.
- Any source files that remain only to preserve compatibility should be treated as dead-code cleanup or explicit follow-up tasks, not as blockers to approving the JWT architecture.

## Claims, Revocation, and Key Management

Recommended baseline:

- Signing algorithm: Ed25519 if library/runtime support is acceptable; ES256 otherwise.
- Access-token lifetime: 5 to 15 minutes.
- Browser session JWT lifetime: short-lived, renewed on activity through a server-controlled mechanism.
- Clock-skew tolerance: default 60 seconds unless production evidence supports a tighter bound.
- Refresh/session continuation:
  - Either rotate short-lived session JWTs directly in cookies,
  - or keep a server-side session handle for renewal and use JWTs only as access assertions.

Recommended revocation model:

- Add `ver` or `token_version` per subject.
- Reject tokens whose embedded version no longer matches the server-side subject version.
- Use `jti` for audit and optional emergency denylisting.

Recommended audience split:

- `wave-web`
- `wave-ws`
- `wave-robot-api`
- `wave-data-api`
- `wave-gadget`

## Risks And Open Questions

### 1. Browser JWTs are not automatically simpler than sessions

JWTs help unify auth format, but browser safety still depends on cookie handling, renewal, revocation, and logout behavior. This design should not move tokens into `localStorage` or other JS-readable storage.

### 2. Gadgets may hide an external dependency problem, not just a token-format problem

Because `/gadgets/*` is currently proxied to a configured gadget server, a JWT redesign may require coordination with that server or a local replacement for part of the OpenSocial token flow.

### 3. Robot compatibility will be the longest tail

Any deployed robot that currently only knows consumer key and secret will need a migration path, likely:

- dual-stack validation during rollout,
- an admin-visible re-registration path,
- a deadline for secret-based auth removal.

### 4. Session-to-WebSocket migration needs explicit validation

The browser currently uses a JS-readable cookie for the WebSocket fallback path. Before removal, the team should verify that all supported browsers and deployment modes work with handshake-authenticated WebSockets or with a dedicated short-lived WebSocket JWT.

### 5. Import/export tooling may need an explicit retirement or migration decision

The import/export helper path still has its own OAuth-specific code. The project should decide whether that tooling is retired with the legacy OAuth removal or migrated onto the same JWT service-token model as robots.

## Decision Closure Required Before Implementation

The following decisions are allowed to remain open during research, but not when implementation starts:

- Issuer model and key source
  - owner: `incubator-wave-jwt-auth.1`
  - target: resolve in the final reviewed plan before any JWT implementation branch opens
- Gadget auth support versus retirement
  - owner: `incubator-wave-jwt-auth.6`
  - target: resolve before any gadget-specific JWT work starts
- Import/export tooling fate
  - owner: `incubator-wave-modernization.3` plus `incubator-wave-jwt-auth.1`
  - target: resolve before the legacy OAuth removal lane is closed
- Java JWT library selection
  - owner: `incubator-wave-jwt-auth.1`
  - target: resolve in the first implementation-ready revision of this plan
- Browser session renewal model
  - owner: `incubator-wave-jwt-auth.1`
  - target: resolve before Phase 2 starts
- Test-classpath policy for temporary `net.oauth` references
  - owner: `incubator-wave-modernization.3`
  - target: resolve before the removal lane claims completion

Implementation should not start with these still implicit.

## Recommended Beads Epic And Task Plan Comments

### Epic comment draft

```text
JWT auth redesign research lane:

Goal: replace legacy session-cookie + OAuth 1.0 + robot shared-secret auth seams with a unified JWT-based model that covers browser sessions, WebSocket auth, robot APIs, Data API delegation, and the remaining gadget token boundary.

Deliverables in this lane:
- architecture draft under docs/JWT/
- exact code-surface inventory
- migration sequencing and rollout risks
- recommended Beads execution slices for implementation lanes

Non-goals for this lane:
- no product-code implementation
- no auth library selection lock until the issuer/validator boundary is approved
- no gadget feature expansion beyond clarifying whether gadget JWT support is required
```

### Task comment draft: `incubator-wave-jwt-auth.2` shared issuer and validator foundation

```text
Task incubator-wave-jwt-auth.2: define the shared JWT platform.

Scope:
- add issuer, JWKS publication, key rotation model, validator abstraction, token type model, and scope/audience rules
- do not migrate endpoints yet

Primary surfaces:
- auth/session abstractions
- servlet/WebSocket auth hooks
- build/runtime dependency ownership

Acceptance:
- one shared validator contract is defined for browser, robot, Data API, and gadget tokens
- token claims and revocation/version model are documented
```

### Task comment draft: `incubator-wave-jwt-auth.3` browser and WebSocket migration

```text
Task incubator-wave-jwt-auth.3: remove JS-readable session-cookie auth.

Scope:
- replace JSESSIONID replay over ProtocolAuthenticate with handshake or dedicated short-lived JWT auth
- set the auth cookie back to HttpOnly
- keep __session bootstrap free of bearer credentials

Acceptance:
- browser login still works
- WebSocket auth no longer depends on Cookies.getCookie("JSESSIONID")
- dev config no longer needs session_cookie_http_only = false
```

### Task comment draft: `incubator-wave-jwt-auth.4` robot active and passive auth migration

```text
Task incubator-wave-jwt-auth.4: replace robot shared secrets with asymmetric robot identity.

Scope:
- robot registration stores public key material instead of consumer secrets
- /robot/rpc accepts JWT bearer access tokens
- passive robot callbacks carry server-signed JWTs

Acceptance:
- new robots no longer receive consumer secrets
- server-side robot auth no longer depends on net.oauth validation
```

### Task comment draft: `incubator-wave-jwt-auth.5` Data API migration

```text
Task incubator-wave-jwt-auth.5: replace Data API OAuth 1.0 flow with scoped JWT access tokens.

Scope:
- remove request/access-token cache design from the Data API path
- define delegated browser flow and PKCE requirements
- add wave:data:* scopes and audience validation

Acceptance:
- Data API auth no longer depends on DataApiTokenContainer
- Data API tokens are JWT bearer tokens with explicit scope and audience claims
```

### Task comment draft: `incubator-wave-jwt-auth.6` gadget boundary decision

```text
Task incubator-wave-jwt-auth.6: resolve gadget auth scope.

Scope:
- determine whether /gadgets and st remain supported
- if yes, define gadget embed JWT claims, issuer, audience, and lifetime
- if no, document retirement and remove JWT gadget work from the critical path

Acceptance:
- gadget auth is either explicitly supported with a JWT design or explicitly retired from scope
```

### Task comment draft: `incubator-wave-jwt-auth.7` legacy OAuth cleanup after rollout

```text
Task incubator-wave-jwt-auth.7: remove the compatibility-only OAuth path after JWT rollout.

Scope:
- delete legacy OAuth pages, token containers, shared-secret fields, and dead helper code once the JWT-backed replacements are live
- keep this cleanup behind the browser, robot, Data API, and gadget rollout tasks

Acceptance:
- no supported runtime path depends on legacy OAuth compatibility code
- cleanup lands only after the replacement tasks are complete
```

## External Guidance Used For This Draft

- RFC 8725, JSON Web Token Best Current Practices: https://www.rfc-editor.org/rfc/rfc8725
- RFC 9068, JWT Profile for OAuth 2.0 Access Tokens: https://www.rfc-editor.org/rfc/rfc9068
- RFC 9700, OAuth 2.0 Security BCP: https://www.rfc-editor.org/rfc/rfc9700
- OWASP HTML5 Security Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html

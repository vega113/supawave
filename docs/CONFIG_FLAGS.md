# Configuration Flags Reference (server + tests)

Status: Living Document
Updated: 2025-09-02

This page documents configuration flags and environment variables recently added or used during modernization. It also tracks temporary flags/classes and their planned removal.

## Server Flags (Typesafe Config)

- core.mongodb_driver: string (default: "v2")
  - Purpose: Select legacy MongoDB driver (v2) or modern driver (v4) for stores that support it.
  - Values: v2 | v4
  - Notes: v4 currently used in scoped adapters/tests; plan to switch default to v4 once all stores migrate.
  - Cleanup: See P6‑T5 below.

- network.enable_forwarded_headers: boolean (default: false)
  - Purpose: Add Forwarded/Proxy header support in Jetty Http/HTTPS configurations.
  - Impact: When true, enables `ForwardedRequestCustomizer` so request attributes (scheme, server host/port, remote address) are derived from proxy headers.
  - Security note: Only enable when the server is deployed behind a trusted reverse proxy or load balancer. Otherwise clients could spoof headers and affect authentication, redirects, and logging.
  - Behavior (Jetty 9.4 vs Jetty 12 EE10)
    - Header support: Both paths honor RFC 7239 `Forwarded` and the de‑facto `X‑Forwarded‑*` family (`X‑Forwarded‑For`, `X‑Forwarded‑Proto`, `X‑Forwarded‑Host`, `X‑Forwarded‑Port`). If both are present, the standardized `Forwarded` header takes precedence.
    - Application point: In both paths we attach `ForwardedRequestCustomizer` to the `HttpConfiguration` used by each connector. In EE10 we construct each `ServerConnector` with `new HttpConnectionFactory(httpConfig)` to ensure the customizer is active.
    - Request fields affected: `HttpServletRequest.getScheme()`, `isSecure()`, `getServerName()`, `getServerPort()`, and `getRemoteAddr()` reflect the forwarded values when present and valid.
    - Invalid/malformed headers: In both paths, invalid values are ignored and the connection’s actual scheme/remote address are used (see jakartaTests `ForwardedHeadersJakartaIT`).
    - Access logs: With access logging enabled, both paths log the effective client IP (derived from forwarding headers) once this flag is enabled.
  - Jakarta (Jetty 12) implementation details in this repo: The EE10 `HttpConfiguration` is created in the Jakarta `ServerRpcProvider` and gets a `ForwardedRequestCustomizer` when this flag is true; all `ServerConnector`s are built with that `HttpConfiguration`.

// Removed in T5: experimental.native_servlet_registration, experimental.enable_programmatic_poc
// These temporary flags have been retired; servlet/filter registration uses the stable path.

- experimental.jetty12_session_lookup: boolean (default: false)
  - Purpose: Enable a best-effort, reflective Jetty 12 session lookup from a token in the Jakarta build.
  - Behavior (Jakarta path only):
    - Attempts to obtain the core `org.eclipse.jetty.session.SessionHandler` and invoke `getSession(String)` reflectively, then unwraps to a `HttpSession` via common accessors.
    - Accepts tokens with or without a node/cluster suffix; if a dot (`.`) is present, the suffix is stripped (e.g., `abc.node1` → `abc`).
    - If reflection fails or the session is not found, returns null gracefully (no exceptions thrown to callers).
  - Security: Intended for transitional use only. Do not rely on this for cross-node production lookups; prefer normal cookie-based session handling.
  - Tests: Covered by `SessionLookupFlagTest` (flag wiring) and `SessionLookupEmbeddedIT` (embedded EE10 server path).
  - Status: Transitional; will be revisited/removed once Jakarta session integration is finalized.

Session adapter behavior
- wave.session.getSessionContext.failFast: boolean (default: false)
  - Purpose: Control behavior of deprecated `HttpSession#getSessionContext()` in migration adapters.
  - Behavior:
    - false (default): log a one-time warning and return null for compatibility.
    - true: throw `UnsupportedOperationException` to surface accidental usages early.
  - Scope: Applies to `JavaxSessionWrapper` (Jakarta migration path only). No effect on legacy javax profile.

## Test/Env Variables (non-Typesafe Config)

- DOCKER_HOST (env)
  - Purpose: Docker socket/daemon location for Testcontainers.
  - Behavior: Tests auto-normalize to Colima’s socket when DOCKER_HOST is missing/invalid: unix://$HOME/.colima/default/docker.sock (best-effort).

- TESTCONTAINERS_RYUK_DISABLED (env)
  - Purpose: Disable Ryuk container in environments that restrict it.
  - Behavior: Our ITs log WARN with env details and skip on container launch failure when set (or when Docker issues occur).

## Removal Plan (temporary flags/classes)

- P5‑T4: Remove temporary Jakarta migration scaffolding (completed)
  - Scope: Removed experimental.enable_programmatic_poc, experimental.native_servlet_registration, and the POC classes under org.waveprotocol.box.server.poc.

- P6‑T5: Finalize Mongo driver modernization flag
  - Scope: Switch default of core.mongodb_driver to v4; deprecate v2 path and remove after stores fully migrate.
  - Trigger: After all Mongo-backed stores have v4 implementations and tests are green in CI.

## How to Override

- application.conf (server):
  # (removed) experimental.native_servlet_registration
  # (removed) experimental.enable_programmatic_poc
  - experimental.jetty12_session_lookup = true
  - network.enable_forwarded_headers = true
  - core.mongodb_driver = v4

- Environment variables (tests):
  - export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  - export TESTCONTAINERS_RYUK_DISABLED=true

## Change Log

- 2025-09-02: Added experimental.native_servlet_registration and experimental.enable_programmatic_poc docs; documented Mongo driver flag and test env behavior.
- 2025-09-03: Retired the two experimental flags and deleted POC classes/tasks.

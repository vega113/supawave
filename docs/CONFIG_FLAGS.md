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
  - Impact: When true, enables ForwardedRequestCustomizer for correct client IP/host handling behind proxies.

- experimental.native_servlet_registration: boolean (default: false)
  - Purpose: Bypass guice-servlet and register servlets/filters programmatically (Jetty) using a Guice child injector for instance creation.
  - Status: Temporary stepping stone to Jakarta (Jetty 12). Off by default.
  - Cleanup: Remove after P5‑T2/P5‑T3 land and Jakarta runtime is stable.

- experimental.enable_programmatic_poc: boolean (default: false)
  - Purpose: When native_servlet_registration is enabled, also register a tiny POC servlet/filter to validate runtime wiring without guice-servlet.
  - Endpoints: GET /poc/hello returns a small text response; a SecurityHeadersFilter adds basic headers.
  - Classes: org.waveprotocol.box.server.poc.ProgrammaticHelloServlet, org.waveprotocol.box.server.poc.SecurityHeadersFilter
  - Status: Strictly temporary dev aid.
  - Cleanup: Remove alongside experimental.native_servlet_registration per P5‑T4.

- experimental.jetty12_session_lookup: boolean (default: false)
  - Purpose: Enable a best-effort, reflective Jetty 12 session lookup from a token in the Jakarta build.
  - Behavior: Attempts to call SessionHandler.getSession(String) and unwrap an HttpSession when available; returns null otherwise.
  - Status: Transitional feature; may be removed or replaced once the full Jakarta session flow is finalized.

## Test/Env Variables (non-Typesafe Config)

- DOCKER_HOST (env)
  - Purpose: Docker socket/daemon location for Testcontainers.
  - Behavior: Tests auto-normalize to Colima’s socket when DOCKER_HOST is missing/invalid: unix://$HOME/.colima/default/docker.sock (best-effort).

- TESTCONTAINERS_RYUK_DISABLED (env)
  - Purpose: Disable Ryuk container in environments that restrict it.
  - Behavior: Our ITs log WARN with env details and skip on container launch failure when set (or when Docker issues occur).

## Removal Plan (temporary flags/classes)

- P5‑T4: Remove temporary Jakarta migration scaffolding
  - Scope: Remove experimental.enable_programmatic_poc, experimental.native_servlet_registration, and the POC classes under org.waveprotocol.box.server.poc.
  - Trigger: After P5‑T2 (Jetty 12 deps) and P5‑T3 (Jakarta imports/registration) are completed and verified in smoke tests.

- P6‑T5: Finalize Mongo driver modernization flag
  - Scope: Switch default of core.mongodb_driver to v4; deprecate v2 path and remove after stores fully migrate.
  - Trigger: After all Mongo-backed stores have v4 implementations and tests are green in CI.

## How to Override

- application.conf (server):
  - experimental.native_servlet_registration = true
  - experimental.enable_programmatic_poc = true
  - experimental.jetty12_session_lookup = true
  - network.enable_forwarded_headers = true
  - core.mongodb_driver = v4

- Environment variables (tests):
  - export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  - export TESTCONTAINERS_RYUK_DISABLED=true

## Change Log

- 2025-09-02: Added experimental.native_servlet_registration and experimental.enable_programmatic_poc docs; documented Mongo driver flag and test env behavior.

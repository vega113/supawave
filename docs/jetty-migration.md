# Jetty Migration Plan

Status: In Progress (staged migration)
Owner: Project Maintainers
Date: 2025-09-07

Status Summary
- Completed: Stage 1 — Jetty 9.4 baseline upgrade and server hardening validated on JDK 17.
- Decision (2025-09-02): Target Jetty 12 (EE10). For Jakarta, use programmatic servlet/filter registration and a programmatic WebSocket endpoint. Retire guice-servlet on the Jakarta path (it is javax-only).
- Stage 2 — Jakarta (Jetty 12): Core HTTP/static/WebSocket parity implemented; servlet/filter import sweep and DI integration are in progress.
  - EE10 server bootstrap: ServletContextHandler, DefaultServlet, GzipHandler.
  - Static resources: ResourceCollection with cache/no-cache splits for /static and /webclient.
  - WebSockets: Programmatic @ServerEndpoint("/socket") with per-connection dispatch; no echo fallback; DI via ServerEndpointConfig.Configurator with validation.
  - Forwarded headers: ForwardedRequestCustomizer behind network.enable_forwarded_headers.
  - Access logs: NCSA request log with append + 7-day retention.
  - Sessions: flag-gated reflective lookup; embedded test coverage.
  - Tests: jakartaTest suite for forwarded headers, access logs, DI guard, and session lookup.
- Compatibility note: For Jakarta builds, javax.servlet-api is still present as compileOnly for transitional stubs. This must be removed before we flip Jakarta as the default.

See also
- Configuration flags and temporary migration toggles: docs/CONFIG_FLAGS.md
- Next up: Complete servlet/filter import sweep to jakarta.*, replace guice-servlet usages with programmatic registration, then flip the default build to Jetty 12.

Objective
- Upgrade Wave’s embedded/used Jetty from 9.2.x to a supported release to improve security, compatibility with modern JDKs, and long-term maintainability.
- Stage the migration to minimize downtime and large refactors.

Context
- Current hosted GWT test harness (gwt-dev) internally launches Jetty 9.2.14.v20151106 and exhibits Java 9+ incompatibilities (e.g., jreLeakPrevention using sun.misc.GC) when run on JDK >= 11.
- Server runtime initially relied on Jetty 9.x APIs and javax.servlet.*; Jakarta work introduces EE10 packages and jakarta.servlet.*.

Target Options
- Option A: Jetty 10 (javax)
  - Pros: Minimal source changes; stays on javax.servlet.*; reduces CVEs vs 9.2.x; supported.
  - Cons: Still javax namespace; does not progress Jakarta migration; shorter runway than Jetty 11/12.
- Option B: Jetty 11/12 (jakarta) — CHOSEN
  - Pros: Aligns with jakarta.servlet.* and long-term ecosystem; modern features, security.
  - Cons: Requires javax -> jakarta import migration; guice-servlet compatibility gap (guice-servlet is javax); web.xml schema update; larger refactor and testing.

Recommendation (staged)
1) Stage 1 (Completed): Adopt Jetty 9.4.x (javax) and modernize: SessionHandler + DefaultSessionCache + FileSessionDataStore, SslConnectionFactory + HttpConfiguration + SecureRequestCustomizer, GzipHandler, security headers (CSP/Referrer-Policy/X-Content-Type-Options) with optional HSTS, forwarded headers support, access logs, static caching, and health endpoints.
2) Stage 2 (In progress): Execute Jakarta migration (Jetty 12) and reach parity.
   - Status: EE10 bootstrap, endpoint dispatch, forwarded headers, access logs, and session lookup compatibility implemented with tests; servlet/filter import sweep and DI replacement pending.

Timeline (as of 2025-09-07)
- T1 (done): EE10 servlet handler, static resources, gzip, basic /socket.
- T2 (done): Programmatic endpoint + per-connection dispatch; removed echo fallback; DI validation.
- T3 (done): Forwarded headers/access logs parity; jakartaTest added.
- T4 (done): Session lookup compatibility layer; flag docs + end-to-end test.
- T5 (done): Retired POC flags and code; simplified provider wiring; constructors collapsed.

Remaining items
- Replace guice-servlet usages with programmatic registration and/or Jakarta-compatible integration; remove GuiceFilter from the Jakarta path.
- Sweep server sources and change javax.servlet.* imports to jakarta.servlet.*; update filters/servlets and any web.xml descriptors.
- Remove compileOnly javax.servlet-api from Jakarta builds; ensure jakarta.servlet-api is the only servlet API on classpath.
- Flip `jettyFamily` default to `jakarta` once CI burn-in is green; keep a fallback profile for javax while deprecating it.
- Update Dockerfile and README to document running under Jetty 12 by default, including any changed ports/flags.
- Continue deprecation cleanup where low risk; track GWT and JUnit legacy warnings separately.

Scope and Impact Areas
- Build dependencies (wave/build.gradle):
  - For Jetty 12/EE10, depend on `org.eclipse.jetty:jetty-server` and `org.eclipse.jetty.ee10:*` modules, plus `jakarta.servlet-api`.
  - Ensure slf4j/logback/log4j bridges are consistent and not duplicated.
- Server code:
  - Replace javax.servlet.* with jakarta.servlet.*.
  - Replace Guice servlet integration (javax) with programmatic registration on Jakarta path.
- Configuration:
  - Programmatic Jetty setup (connectors, thread pools, handlers) validated under Jetty 12.
  - If web.xml present, upgrade descriptors to Jakarta variant.
- Testing:
  - Build & unit tests on JDK 17; run `:wave:testJakarta` and smoke tests.
  - GWT hosted tests are independent (driven by gwt-dev Jetty) and won’t be fixed by server Jetty upgrade; track separately.

Detailed Plan

Stage 1: Jetty 9.4 (javax)
- Completed
  - Dependencies updated to 9.4.54
  - Sessions migrated to SessionHandler + DefaultSessionCache + FileSessionDataStore
  - SSL connector updated (SecureRequestCustomizer, TLS 1.2/1.3)
  - Compression via GzipHandler
  - Security headers filter + HSTS toggle
  - ForwardedRequestCustomizer toggle
  - WebSocket token handling fix
  - Access logs (NCSA) and static caching/no-cache separation
  - Health endpoints added (/healthz, /readyz)
- Acceptance (met)
  - Server starts on JDK 17 and serves endpoints without regressions
  - Health endpoints return 200 OK
  - Access logs created under logs/
  - Caching headers present on static; no-cache on dynamic webclient
  1) Inventory current Jetty dependencies and usages.
  2) Upgrade dependencies in wave/build.gradle to Jetty 9.4.54.v20240208 (or latest 9.4.x).
  3) Replace HashSessionManager and org.eclipse.jetty.server.SessionManager usages with SessionHandler + DefaultSessionCache + FileSessionDataStore (or NullSessionCache if acceptable).
  4) Adjust SSL connector setup as needed (9.4 supports legacy SslContextFactory constructor but prefer updated patterns).
  5) Run server smoke tests on JDK 17; validate filters, servlets, static resources.
  6) Address logging configuration and transitive conflicts (slf4j/logback/log4j) if surfaced.
- Acceptance
  - Server starts and serves key endpoints without regressions.
  - No critical CVEs reported against the new Jetty version.

Stage 2: Jakarta migration (Jetty 12)
Decision & path
- Decision: Target Jetty 12 (Jakarta) for long-term support. Guice-servlet is javax-based; we will replace servlet/filter registration with a programmatic approach while keeping Guice core for DI.
- Pre-requisites
  - Inventory all javax.servlet.* usages and any javax.* dependencies (filters, listeners, web.xml).
- Tasks
  1) Keep `jakarta.servlet-api` (6.x) as the only servlet API on the Jakarta path; remove `javax.servlet-api` from compileOnly.
  2) Update imports javax.* -> jakarta.* across server sources.
  3) Update web.xml schema to Jakarta variant if present; validate descriptors.
  4) Replace guice-servlet integration (ServletModule, GuiceFilter) with programmatic registration; wire filters/servlets.
  5) Re-run full build, `:wave:testJakarta`, and server smoke tests on JDK 17.
- Acceptance
  - Server builds and runs fully under Jakarta.
  - No javax.* dependencies remain on the Jakarta path; CI is green.

Risks and Mitigations
- Risk: guice-servlet Jakarta compatibility
  - Mitigation: Evaluate Jakarta-compatible forks or replace with native servlet registration. Prototype in a branch.
- Risk: Large import churn (javax -> jakarta)
  - Mitigation: Scripted refactor with IDE or sed; compile in small batches; strong test coverage.
- Risk: Transitive dependency conflicts (logging, annotations, etc.)
  - Mitigation: Use dependencyInsight to inspect; pin versions; add exclusions as needed.

Work Breakdown (initial)
- P5-T1 (Decision): Choose Stage 1 -> Jetty 9.4 now; Stage 2 -> plan Jakarta.
- P5-T2 (Jetty 9.4): Update dependencies; refactor session API; smoke test.
- P5-T3 (Jakarta planning): Spike on guice-servlet replacement path; document findings and choose approach.

Validation Checklist
- Server starts on JDK 17 with Jetty 12 (Jakarta) after migration.
- WebSockets, static resources, forwarded headers, access logs verified via tests.
- No javax.* dependencies remain on Jakarta path; follow-up epic created for any residuals.

Notes
- 2025-08-30 Spike outcome: Attempting Jetty 10 directly revealed session API changes (no org.eclipse.jetty.server.SessionManager/HashSessionManager). We temporarily reverted dependencies to 9.2.14 to keep the build green. Plan updated to target 9.4 first with a focused refactor in ServerModule/ServerRpcProvider.
- Upgrading server Jetty will not affect GWT hosted tests (gwt-dev brings its own Jetty). Hosted tests failing under JDK >= 11 must be addressed separately (e.g., run tests with JDK 8 or migrate test strategy).

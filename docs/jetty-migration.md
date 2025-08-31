# Jetty Migration Plan

Status: In Progress (staged migration)
Owner: Project Maintainers
Date: 2025-08-31

Objective
- Upgrade Wave’s embedded/used Jetty from 9.2.x to a supported release to improve security, compatibility with modern JDKs, and long-term maintainability.
- Stage the migration to minimize downtime and large refactors.

Context
- Current hosted GWT test harness (gwt-dev) internally launches Jetty 9.2.14.v20151106 and exhibits Java 9+ incompatibilities (e.g., jreLeakPrevention using sun.misc.GC) when run on JDK >= 11.
- Server runtime Jetty usage in Wave (if any) needs inventory. We expect 9.x APIs and javax.servlet.* imports across the codebase.

Target Options
- Option A: Jetty 10 (javax)
  - Pros: Minimal source changes; stays on javax.servlet.*; reduces CVEs vs 9.2.x; supported.
  - Cons: Still javax namespace; does not progress Jakarta migration; shorter runway than Jetty 11/12.
- Option B: Jetty 11/12 (jakarta)
  - Pros: Aligns with jakarta.servlet.* and long-term ecosystem; modern features, security.
  - Cons: Requires javax -> jakarta import migration; guice-servlet compatibility gap (guice-servlet is javax); web.xml schema update; larger refactor and testing.

Recommendation (staged)
1) Stage 1 (Completed): Adopt Jetty 9.4.x (javax) and modernize: SessionHandler + DefaultSessionCache + FileSessionDataStore, SslConnectionFactory + HttpConfiguration + SecureRequestCustomizer, GzipHandler, security headers (CSP/Referrer-Policy/X-Content-Type-Options) with optional HSTS, forwarded headers support, access logs, static caching, and health endpoints.
2) Stage 2 (Planned): Plan and execute Jakarta migration (Jetty 12 preferred) when guice-servlet or alternative strategy is settled.

Scope and Impact Areas
- Build dependencies (wave/build.gradle):
  - Replace org.eclipse.jetty:jetty-*:9.2.x with 10.0.x equivalents.
  - Ensure slf4j/logback/log4j bridges are consistent and not duplicated.
  - Confirm javax.servlet-api (4.0.1) remains for Jetty 10.
- Server code:
  - javax.servlet.* remains unchanged for Jetty 10.
  - Any Jetty-specific APIs/classes used may need minor adjustments.
- Configuration:
  - Programmatic Jetty setup (connectors, thread pools, handlers) validated under Jetty 10.
  - If web.xml present, validate descriptor versions.
- Testing:
  - Build & unit tests on JDK 17.
  - Server smoke tests (health endpoints, filters, servlets, static content).
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

Stage 2: Jakarta migration (Jetty 11/12)
Decision & path
- Decision: Target Jetty 12 (Jakarta) for long-term support. Guice-servlet is javax-based; we will evaluate options:
  1) Replace guice-servlet with native programmatic registration and keep Guice DI without guice-servlet.
  2) Investigate Jakarta-compatible forks of guice-servlet (if any, maturity unknown).
  3) Consider an alternative DI for servlet integration if needed.
- Preconditions remain as below.
- Pre-requisites
  - Decide strategy for guice-servlet replacement or jakarta-compatible alternative.
    - Options: community Jakarta port of guice-servlet, or refactor to native servlet/filter registration without guice-servlet, or DI alternative.
  - Inventory all javax.servlet.* usages and any javax.* dependencies (filters, listeners, web.xml).
- Tasks
  1) Swap javax.servlet-api -> jakarta.servlet:jakarta.servlet-api (5.x or 6.x, aligned with Jetty target).
  2) Update imports javax.* -> jakarta.* across server sources.
  3) Update web.xml schema to Jakarta variant if present; validate descriptors.
  4) Replace or adapt guice-servlet integration (ServletModule, GuiceFilter); rework bootstrap/init.
  5) Upgrade Jetty to 12.x (preferred) or 11.x; update programmatic Jetty configuration if APIs changed.
  6) Re-run full build, unit tests, and server smoke tests on JDK 17.
- Acceptance
  - Server builds and runs fully under Jakarta.
  - No javax.* dependencies remain; CI is green.

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
- Server starts on JDK 17 with Jetty 12 (Jakarta) after migration
- No critical regressions in endpoints and filters
- Follow-up epic created for Jakarta migration with concrete subtasks and code owners

Notes
- 2025-08-30 Spike outcome: Attempting Jetty 10 directly revealed session API changes (no org.eclipse.jetty.server.SessionManager/HashSessionManager). We temporarily reverted dependencies to 9.2.14 to keep the build green. Plan updated to target 9.4 first with a focused refactor in ServerModule/ServerRpcProvider.
- Upgrading server Jetty will not affect GWT hosted tests (gwt-dev brings its own Jetty). Hosted tests failing under JDK >= 11 must be addressed separately (e.g., run tests with JDK 8 or migrate test strategy).


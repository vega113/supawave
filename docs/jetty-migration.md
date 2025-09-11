# Jetty Migration Plan

Status: In Progress (staged migration)
Owner: Project Maintainers
Date: 2025-09-10

Status Summary
- Completed: Stage 1 — Jetty 9.4 baseline upgrade and server hardening validated on JDK 17.
- Decision (2025-09-02): Target Jetty 12 (EE10). For Jakarta, use programmatic servlet/filter registration and a programmatic WebSocket endpoint. Retire guice-servlet on the Jakarta path (it is javax-only).
- Stage 2 — Jakarta (Jetty 12): Core HTTP/static/WebSocket parity implemented; servlet/filter import sweep and DI integration are in progress. Jakarta unit and integration tests are green locally.
  - EE10 server bootstrap: ServletContextHandler, DefaultServlet, GzipHandler.
  - Static resources: ResourceCollection with cache/no-cache splits for /static and /webclient.
  - WebSockets: Programmatic @ServerEndpoint("/socket") with per-connection dispatch; no echo fallback; DI via ServerEndpointConfig.Configurator with validation.
  - Forwarded headers: ForwardedRequestCustomizer behind network.enable_forwarded_headers.
  - Access logs: NCSA request log with append + 7-day retention.
  - Sessions: flag-gated reflective lookup; embedded test coverage. As of 2025‑09‑11, `HttpSession` adaptation includes:
    - `JavaxSessionWrapper#getServletContext()` now returns a lightweight adapter (non‑null) that forwards safe methods to the underlying Jakarta context; unsupported javax‑specific methods throw `UnsupportedOperationException`.
    - `JavaxSessionWrapper#getSessionContext()` logs once and returns null by default; opt‑in fail‑fast with `-Dwave.session.getSessionContext.failFast=true` throws `UnsupportedOperationException`.
  - Tests: jakartaTest (unit-like) and jakarta ITs for forwarded headers, access logs, caching filters, security headers, DI guard, and session lookup.

Recent changes (2025-09-10)
- Tests hardened for EE10 stability and diagnostics:
  - AccessLogJakartaIT uses a CountDownLatch-based RequestLog (no sleep/polling) for deterministic verification.
  - CachingFiltersJakartaIT factors helpers (assertOk/header/dumpHeaders), uses EE10 FilterHolder, and public nested servlets; failures include headers+body.
  - SecurityHeadersJakartaIT validates CSP/Referrer-Policy and X-Content-Type-Options=nosniff for both default and custom configs; extracted case-insensitive header helper with diagnostics.
  - ForwardedHeadersJakartaIT asserts the safety property (no https upgrade on malformed X-Forwarded-Proto) and documents temporary allowance for literal passthrough pending strict mode; TODO to tighten once strict handling is in place.
  - Refined @Before exception handling: only skip for true EE10 env issues (via TestSupport); fail fast for app misconfigurations.
- New Jakarta ITs:
  - ForwardedHeadersStrictFuzzJakartaIT: fuzz/permutation coverage for duplicate headers, long X-Forwarded-For chains, and oversized header values; validates strict forwarded-header behavior and safety invariants.
  - AttachmentServletJakartaIT: negative tests for path sanitization (extra segments, backslashes, dot-segments, excessive length) and positive domain/id case.
  - SearchServletJakartaIT: invalid parameter 400s, out-of-range clamping, injection-safe serialization, and serializer failure → 500.
- TestSupport added (public, test-only) to centralize EE10 availability checks and consistent skip policy.
- CI: added non-blocking :wave:testJakartaIT step and artifact publishing. Plan to flip to blocking after a burn-in window.
- Compatibility note (updated 2025‑09‑11): The Jakarta build’s runtime classpath contains only Jakarta APIs. For compilation, we temporarily expose `javax.servlet-api` as `compileOnly` to build adapter types used solely by migration wrappers/tests. No javax types are present at runtime on the Jakarta path. This `compileOnly` will be removed when all adapters/tests stop referencing javax.

Security & correctness hardening
- AttachmentServlet (both javax and Jakarta):
  - Exact endpoint matching via `request.getServletPath()` (no URI prefix tricks).
  - Authorization uses metadata-derived waveRef; request waveRef ignored; legacy auto-build metadata path removed.
  - Strict AttachmentId parsing from `pathInfo`: at most one '/', rejects `.`/`..`, backslashes, NUL/newlines, and excessive length; invalid inputs return 404.
  - Thumbnail patterns directory validated at startup (exists/dir/readable). If invalid or files missing, serve a safe in-memory PNG placeholder; no runtime exceptions.
- SearchServlet (both paths):
  - `index` and `numResults` validated: non‑numeric → HTTP 400; numeric out-of-range → clamped with fine log.
  - Serializer failures return HTTP 500 with a short message (instead of rethrowing IOE) and log at SEVERE.

Build changes (Jakarta profile)
- Replaced compileJava task mutation with declarative sourceSets: include `src/jakarta-overrides/java`; exclude javax-era classes only from `src/main/java`. Prevents duplicate-class collisions and keeps overrides (AttachmentServlet/SearchServlet) active.

Jakarta ITs executed by default
- ForwardedHeadersJakartaIT, ForwardedHeadersStrictFuzzJakartaIT, AccessLogJakartaIT, SecurityHeadersJakartaIT, CachingFiltersJakartaIT, AttachmentServletJakartaIT, SearchServletJakartaIT.

See also
- Configuration flags and temporary migration toggles: docs/CONFIG_FLAGS.md
- Next up: Complete servlet/filter import sweep to jakarta.*, replace guice-servlet usages with programmatic registration, then flip the default build to Jetty 12.

Open Work Items (Jakarta)
- Servlet/filter import sweep to `jakarta.*` for remaining RPC servlets (Authentication, SignOut, GadgetProvider, InitialsAvatars) with focused ITs.
- Remove `compileOnly javax.servlet-api` once adapters/tests no longer reference javax interfaces.
- Replace `guice-servlet` on Jakarta path with programmatic registration; keep Guice core for DI.
- Promote `:wave:testJakartaIT` to blocking in CI after a 1–2 week burn‑in.
- Flip default profile to `-PjettyFamily=jakarta` and mark the javax profile as deprecated.

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

Timeline (as of 2025-09-08)
- T1 (done): EE10 servlet handler, static resources, gzip, basic /socket.
- T2 (done): Programmatic endpoint + per-connection dispatch; removed echo fallback; DI validation.
- T3 (done): Forwarded headers/access logs parity; jakartaTest added; jakarta ITs green locally.
- T4 (done): Session lookup compatibility layer; flag docs + end-to-end test.
- T5 (ongoing): Retire POC flags/code as overrides solidify; simplified provider wiring; constructors collapsed.

Remaining items
- Replace guice-servlet usages with programmatic registration; remove GuiceFilter from the Jakarta path.
  - Approach: Use native Jetty EE10 APIs (`ServletContextHandler`, `ServletHolder`, `FilterHolder`) and `jakarta.servlet.*` to register servlets/filters programmatically.
  - WebSockets: Use `JakartaWebSocketServletContainerInitializer` + `ServerEndpointConfig` with DI configurator.
  - No third-party Jakarta servlet glue is required; keep Guice core for DI but not `guice-servlet`.
- Sweep server sources and change javax.servlet.* imports to jakarta.servlet.*; update filters/servlets and any web.xml descriptors.
- Remove compileOnly javax.servlet-api from Jakarta builds; ensure jakarta.servlet-api is the only servlet API on classpath.
- Flip `jettyFamily` default to `jakarta` once CI burn-in is green; keep a fallback profile for javax while deprecating it.
- CI: Jakarta compile + tests run in a dedicated job. As of 2025‑09‑08, PRs are blocking on Jakarta compile/tests, while direct pushes remain non‑blocking during burn‑in. After a 1–2 week green window, flip all events to blocking and prepare the default flip.
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
  - Integration tests on JDK 17; run `:wave:testJakartaIT` (CI non-blocking for now).
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
  5) Re-run full build, `:wave:testJakarta`, `:wave:testJakartaIT`, and server smoke tests on JDK 17.
- Acceptance
  - Build/runtime
    - Server builds and starts under Jakarta (Jetty 12) on JDK 17 with default config.
    - No javax.* dependencies remain on the Jakarta path; CI (PRs) is blocking and green.
  - Predictable request handling
    - Forwarded headers: With `network.enable_forwarded_headers=true` and `network.forwarded_headers.strict=true`, malformed X‑Forwarded‑* values are ignored; effective scheme/remote reflect safe fallback. With strict=false, behavior matches Jetty defaults.
    - Static and webclient resources: `/static/*` returns long‑lived caching headers (immutable, max‑age), `/webclient/*` returns no‑cache headers; ETags enabled for both.
    - Security headers: CSP, Referrer‑Policy, and X‑Content‑Type‑Options=nosniff are set; HSTS is emitted only on secure requests when configured.
    - Access logging: NCSA entries are written for handled requests.
    - Sessions: SessionManager bridge works (login/logout/user lookup); token→session lookup behaves as documented.
    - WebSockets: `/socket` endpoint upgrades, authenticates, and routes messages correctly under Jakarta.
  - Test coverage (Jakarta targets)
    - Unit tests (jakartaTest) cover filters, headers, and helper utilities.
    - Integration tests (jakarta ITs) cover forwarded headers (including strict mode), access logs, caching filters, security headers, and basic session behaviors; tests are deterministic (no sleep‑polling) and non‑flaky.
    - Provider/boot IT validates server bootstrap wiring (Servlet/Filter mappings, WebSocket endpoint, DI).
  - DI & wiring
    - Guice core continues to provide DI for services; servlet/filter registration is programmatic (no guice‑servlet).
    - Endpoints/servlets receive required dependencies (validated by ITs).
  - Operational
    - Docker image and README instructions verified for Jakarta profile; configuration flags documented; logs/metrics unchanged in format.

Risks and Mitigations
- Risk: guice-servlet Jakarta compatibility
  - Mitigation (prioritized, with timeline):
    1) Primary path (default): Replace guice-servlet with native, programmatic servlet/filter registration while keeping Guice core for DI. Target: design and initial wiring in the EE10 ServerModule/ServerRpcProvider by Week 1 of the next sprint; end-to-end provider IT green by Week 2.
    2) Contingency (time-boxed): If a low-risk Jakarta-compatible fork exists, evaluate in a throwaway branch for ≤2 days strictly to compare ergonomics. Acceptance: no javax.* leakage, API stability, and no runtime conflicts. If any criteria fail, abort and stay on primary path.
    3) Evolution/compatibility: Maintain guice-servlet only on the legacy javax profile during the transition. Once the programmatic Jakarta path is green in CI (1–2 weeks), mark guice-servlet as deprecated in docs and remove it from the default build. Remove the javax fallback one milestone later, after user testing feedback.
  - Decision points:
    - D1 (end of Week 1): Proceed with programmatic registration unless the fork clearly outperforms and meets criteria.
    - D2 (end of Week 2): Lock decision; update docs and remove experimental switches on the Jakarta path.
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
- WebSockets, static resources, forwarded headers, access logs, security headers, and caching filters verified via tests.
- No javax.* dependencies remain on Jakarta path; follow-up epic created for any residuals.

Notes
- 2025-08-30 Spike outcome: Attempting Jetty 10 directly revealed session API changes (no org.eclipse.jetty.server.SessionManager/HashSessionManager). We temporarily reverted dependencies to 9.2.14 to keep the build green. Plan updated to target 9.4 first with a focused refactor in ServerModule/ServerRpcProvider.
- Upgrading server Jetty will not affect GWT hosted tests (gwt-dev brings its own Jetty). Hosted tests failing under JDK >= 11 must be addressed separately (e.g., run tests with JDK 8 or migrate test strategy).

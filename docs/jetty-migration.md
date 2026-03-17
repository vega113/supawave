# Jetty Migration Plan

Status: In Progress (staged migration)
Owner: Project Maintainers
Date: 2025-10-11

Status Summary
- Completed: Stage 1 — Jetty 9.4 baseline upgrade and server hardening validated on JDK 17.
- Decision (2025-09-02): Target Jetty 12 (EE10). For Jakarta, use programmatic servlet/filter registration and a programmatic WebSocket endpoint. Retire guice-servlet on the Jakarta path (it is javax-only).
- Stage 2 — Jakarta (Jetty 12) (completed): Core HTTP/static/WebSocket parity implemented; robot APIs and observability endpoints now run on Jakarta by default. Servlet/filter import sweep of shared libraries and expanded tests landed. Gradle defaults to `-PjettyFamily=jakarta` with Jetty 9.4 fallback.
  - EE10 server bootstrap: ServletContextHandler, DefaultServlet, GzipHandler.
  - Static resources: ResourceCollection with cache/no-cache splits for /static and /webclient.
  - WebSockets: Programmatic @ServerEndpoint("/socket") with per-connection dispatch; no echo fallback; DI via ServerEndpointConfig.Configurator with validation.
  - Forwarded headers: ForwardedRequestCustomizer behind network.enable_forwarded_headers.
  - Access logs: NCSA request log with append + 7-day retention.
  - Sessions: flag-gated reflective lookup; embedded test coverage. As of 2025‑09‑18, HttpSession adapters maintain servlet context access and registration flows without javax dependencies.
  - Metrics/observability: Micrometer HTTP timing filter, Prometheus `/metrics`, statusz, and remote logging Jakarta variants.
  - Robot APIs: Active/Data/registration servlets, RobotApiModule wiring, passive connector, and operation/service registries live under `src/jakarta-overrides`.
  - Tests: jakartaTest and jakarta ITs cover forwarded headers, access logs, caching filters, security headers, DI guard, session lookup, and robot servlet flows.

Recent changes (2025-09-27)
- 2025-09-27: Restored profiling parity on the Jakarta server by porting RequestScopeFilter and TimingFilter to jakarta.servlet, re-enabling StatModule, and wiring the filters through ServerRpcProvider alongside targeted tests. Added Jakarta HealthServlet wiring and smoke coverage for /healthz and /readyz endpoints.
- 2025-09-27: Removed the runtime `isJakarta` gating in ServerMain and made the Jakarta test suites part of the default `check`/`testAll` flow.

Recent changes (2025-10-11)
- 2025-10-11: Replaced the placeholder Jakarta WaveWebSocketEndpoint with the full RPC bridge (executor + session manager wiring) and restored blocking sends so RpcTest expectations pass. Updated AuthenticationServlet Jakarta overrides to fall back to `/` on unsafe `r` parameters, aligned unit/IT expectations, and documented the behavior.
- 2025-10-11: Marked `testJakarta` and `testJakartaIT` as part of the default Gradle build; follow-up work on checkstyle conformance for jakarta-overrides is tracked separately.

Recent changes (2025-09-18)
- 2025-09-18: Completed Jakarta robot service registries, RobotApiModule wiring, passive connector overrides, and NotifyOperationService; added a Jakarta override for `com.google.wave.api.AbstractRobot`; introduced Micrometer HTTP metrics filter, Prometheus `/metrics`, and Jakarta variants of remote logging/statusz. Verified `./gradlew -PjettyFamily=jakarta :wave:compileJava :wave:testJakarta :wave:testJakartaIT` with the Jakarta profile as default and documented the Jetty 9.4 fallback path.
- 2025-09-18: Added dedicated Jakarta tests for the Data API OAuth token flow, Prometheus `/metrics` endpoint, and NotifyOperationService hash refresh to keep regression coverage on robot authentication and observability paths.
- 2025-09-15: Ported UserRegistrationServlet, LocaleServlet, WaveRefServlet, and InitialsAvatarsServlet to the Jakarta source set. Added RegistrationSupport helper to decouple account creation from WelcomeRobot, wired new servlet overrides in ServerMain, and excluded the javax variants from Jakarta builds. Locale, waveref, and initial-avatar flows now bridge sessions via WebSessions with Jakarta-focused IT coverage.
- 2025-09-15: Updated Jakarta tests to use WebSessions/RegistrationSupport (no WelcomeRobot or javax HttpSession dependencies). `:wave:testJakarta` and `:wave:testJakartaIT` run green under Jetty 12; default Gradle profile now targets `-PjettyFamily=jakarta`.
- 2025-09-15: Ported robot Data API/Active API/registration servlets to Jakarta with a lightweight OAuth HttpRequestMessage adapter.
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
- Compatibility note (updated 2025‑09‑18): The Jakarta build’s compile and runtime classpaths now contain only Jakarta APIs. The transitional `javax.servlet-api` dependency has been removed; use `-PjettyFamily=javax` when you need the legacy Jetty 9.4 stack during bisects.

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
- Finish servlet/filter import sweep for remaining robot-backed modules (e.g., Gadget provider operations) and add focused ITs as coverage grows.
- Replace `guice-servlet` on Jakarta path with programmatic registration; keep Guice core for DI.
- Promote `:wave:testJakartaIT` to blocking in CI after a 1–2 week burn‑in.
- Ensure javax fallback profile documentation stays current (now opt-in via `-PjettyFamily=javax`).

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
- T5 (done): Retired POC flags/code as overrides solidified; simplified provider wiring; constructors collapsed.

Remaining items
- Coordinate with Infra/CI to flip the Jakarta suites (`:wave:testJakarta`, `:wave:testJakartaIT`) to blocking status and enforce green gates for Jetty 12 builds.
- Plan the community verification window before removing the `-PjettyFamily=javax` fallback profile; document criteria and timeline in docs/CONFIG_FLAGS.md.
- Continue deprecation cleanup where low risk; track remaining GWT hosted-test limitations separately.

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
- Tasks (remaining)
  1) Audit shared libraries (`com/google/wave/api/**`, legacy robot utilities) that still import `javax.servlet` and either provide Jakarta-safe variants or confine them to the javax profile.
  2) Update any web.xml descriptors to the Jakarta schema (if present) and validate via server smoke.
  3) Expand Jakarta integration coverage for robot OAuth/token flows, Prometheus `/metrics`, and production login paths; promote :wave:testJakartaIT to blocking once stable.
  4) Continue running `./gradlew -PjettyFamily=jakarta :wave:compileJava :wave:testJakarta :wave:testJakartaIT` and full server smoke on JDK 17 for every milestone increment.
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

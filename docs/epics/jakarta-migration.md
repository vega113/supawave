# Jakarta migration epic

This document tracks the staged migration from javax to jakarta for Apache Wave, aligned with the Jetty migration path.

Status
- Current baseline: Jetty 9.4.x, Java 17, javax.* APIs
- Target: Jetty 12.x (EE 10/11), Java 17+, jakarta.* APIs
- Strategy: Two-step migration
  1) Stabilize on Jetty 9.4 with modern infra (JDK 17, TLS, HTTP/2, logging, access logs, caching, health endpoints)
  2) Introduce a jakarta layer (Jetty 12) and migrate servlets/filters to jakarta.servlet.*

Scope and subtasks
1) Servlet integration under Jakarta
   - Goal: keep Guice DI while wiring servlets/filters reliably under both javax (legacy) and jakarta (EE10).
   - Approach (final): continue using Guice listener + GuiceFilter for registration; remove temporary flags and POC.
   - Status: completed. Experimental flags removed and codepath simplified.

2) Prepare for jakarta namespace switch
   - Identify packages to migrate: servlets, filters, WebSockets, any javax.* usages
   - Add a compatibility layer or branches for javax vs jakarta builds
   - Plan dependency swaps: javax.servlet-api -> jakarta.servlet-api; Jetty 9.4 -> Jetty 12 modules

3) Jetty 12 migration
   - Introduce Jetty 12 dependencies guarded by a build profile/branch
   - Swap to jakarta.servlet.* imports
   - Adjust any Jetty APIs that changed between 9.4 and 12
   - Update WebSocket modules to Jetty 12 variants

4) Logging and metrics
   - Completed: slf4j 1.7.x + logback 1.2.13 (low-risk)
   - Future: migrate to slf4j 2.x + logback 1.5.x once Jetty 12 baseline is green

5) CI and build
   - Ensure Gradle toolchains target Java 17+; pin wrapper to compatible version
   - Checkstyle configured to exclude generated sources; allow disabling in CI for large logs
   - Add a Jetty 12 build job (allowed to fail) for early signal

6) Docs and rollout
   - Update docs/jetty-migration.md with the path and decisions (done)
   - Provide operator notes for TLS, HTTP/2, access logs, and forwarded headers (done)

Operator flags and configs
- security.enable_ssl: boolean
- security.ssl_keystore_path: string
- security.ssl_keystore_password: string or env var (e.g., ${?WAVE_SSL_KEYSTORE_PASSWORD})

Open questions
- Do we need to retain guice-servlet at all under Jetty 12?
- Should we dual-publish distributions for javax and jakarta for a transition period?

Timeline (proposed)
- Week 1-2: Spike native registration; merge behind flag
- Week 3-4: Jetty 12 branch with jakarta compile; basic smoke checks
- Week 5+: Complete migration and remove javax path, or maintain both via profiles

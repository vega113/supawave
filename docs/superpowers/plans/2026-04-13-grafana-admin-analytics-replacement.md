# Replace Admin Analytics With Grafana Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the server-side `/admin` analytics feature and replace it with Grafana-consumable metrics/log signals so analytics lives in the observability stack instead of Wave.

**Architecture:** Keep only lightweight telemetry emission in Wave. Reuse the existing Micrometer/Prometheus `/metrics` endpoint for aggregate counters and the existing JSON log pipeline for high-cardinality event inspection in Grafana/Loki. Delete the admin analytics UI, admin analytics API routes, persistence-backed hourly counter store, and aggregation service introduced for the admin tab.

**Tech Stack:** Java 17, Guice, Micrometer Prometheus registry, existing JSON/logback logging, Grafana Alloy remote write, SBT/JUnit.

---

## Context

- Requirements source: user request on 2026-04-13 plus GitHub issue `#884`.
- Existing feasibility is confirmed:
  - Wave already exposes Prometheus metrics at `/metrics`.
  - `deploy/supawave-host/configure-grafana-alloy.sh` already remote-writes metrics/logs to Grafana Cloud.
  - The current admin analytics feature is implemented entirely inside Wave via:
    - `AdminServlet` analytics routes
    - `HtmlRenderer` analytics tab/UI/Chart.js hooks
    - `AdminAnalyticsService`
    - `AnalyticsCounterStore` + memory/Mongo implementations
    - `AnalyticsRecorder` + `PublicWaveViewTracker`
- Scope for this slice:
  - Remove admin analytics feature code introduced for issue `#605`.
  - Keep minimal instrumentation hooks only where they emit Grafana-friendly telemetry.
  - Update deploy/docs/changelog for the new Grafana path.

## Acceptance Criteria

- `/admin` no longer renders an `Analytics` tab.
- `/admin/api/analytics/status` and `/admin/api/analytics/history` are no longer supported.
- Wave still emits aggregate analytics counters via `/metrics` for:
  - public wave page views
  - public wave API fetches
  - registrations
  - login/activity events
  - waves created
  - blips created
- Deployment docs and Alloy config show how Wave application metrics are scraped and forwarded to Grafana Cloud.
- User-facing changelog says admin analytics moved out to Grafana.
- Focused tests pass and a narrow local verification proves `/metrics` exposes the new series.

## Out Of Scope

- Building Grafana dashboards or provisioning them through the API.
- Reproducing every former admin-tab query exactly inside Wave.
- Reworking the legacy Google Analytics `analytics_account` page snippet.
- Broad observability cleanup outside the former admin analytics paths.

## File Ownership Map

| Path | Action | Responsibility |
| --- | --- | --- |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` | Modify | Remove analytics tab markup and JavaScript; keep other admin tabs intact. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java` | Modify | Remove analytics routes/injected collaborators; return 404 for unknown `/api/*` admin routes. |
| `wave/src/main/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorder.java` | Modify | Replace hourly-store writes with Micrometer counters and Grafana/Loki-friendly event logging. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java` | Modify | Keep login/activity telemetry hook wired to the new recorder behavior. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java` | Modify | Keep page-view telemetry hook against the new recorder behavior. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java` | Modify | Keep public fetch telemetry hook against the new recorder behavior. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java` | Modify | Keep registration telemetry hook against the new recorder behavior. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java` | Modify | Keep registration telemetry hook against the new recorder behavior. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` | Modify | Keep WaveBus subscription if still used by `AnalyticsRecorder`; remove config-driven analytics-store wiring assumptions. |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java` | Modify | Remove `AnalyticsCounterStore` bindings and config-driven analytics persistence selection. |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/AnalyticsCounterStore.java` | Delete | No longer needed once analytics persistence/history API is removed. |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStore.java` | Delete | Remove former admin analytics storage implementation. |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/NoOpAnalyticsCounterStore.java` | Delete | Remove former admin analytics storage implementation. |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java` | Delete | Remove former admin analytics storage implementation. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsService.java` | Delete | Remove former admin analytics aggregation feature. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTracker.java` | Delete | Remove former in-process top-wave view tracker. |
| `deploy/supawave-host/configure-grafana-alloy.sh` | Modify | Add scrape job for Wave’s `/metrics` endpoint and forward it to remote write. |
| `deploy/supawave-host/README.md` | Modify | Document the Wave scrape path and example validation commands. |
| `wave/config/changelog.d/` | Add | New fragment for moving admin analytics to Grafana. |
| `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererFeatureFlagsTest.java` | Modify | Assert analytics tab is absent. |
| `wave/src/test/java/org/waveprotocol/box/server/rpc/AdminServletTest.java` | Modify | Remove analytics endpoint assertions; add 404 coverage for removed routes. |
| `wave/src/test/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorderTest.java` | Modify | Assert recorder increments Prometheus counters instead of hourly buckets. |
| `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java` | Modify | Assert public page hits increase exported metrics. |
| `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServletTest.java` | Modify | Assert public fetch hits increase exported metrics. |
| `wave/src/test/java/org/waveprotocol/box/server/authentication/SessionManagerTest.java` | Modify | Keep constructor wiring green after recorder changes. |
| `wave/src/test/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsServiceTest.java` | Delete | Remove obsolete admin analytics coverage. |
| `wave/src/test/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTrackerTest.java` | Delete | Remove obsolete tracker coverage. |
| `journal/local-verification/2026-04-13-issue-884-grafana-admin-analytics.md` | Add | Record narrow local verification for issue workflow. |

## Task 1: Remove The Admin Analytics Surface

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererFeatureFlagsTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/AdminServletTest.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java`

- [ ] **Step 1: Write failing tests for the removal contract**

Add assertions that the admin page no longer includes analytics UI hooks, and that removed analytics API routes return `404` instead of HTML.

```java
assertFalse(html.contains("data-tab=\"analytics\">Analytics</button>"));
assertFalse(html.contains("id=\"panel-analytics\""));
assertFalse(html.contains("fetch('/admin/api/analytics/status')"));
assertFalse(html.contains("fetch('/admin/api/analytics/history?window='"));
```

```java
JSONObject json = invokeJsonApi("/api/analytics/status", ownerAccount(ADMIN_ID), featureFlagStore,
    newAnalyticsService());
assertEquals("Not found", json.getString("error"));
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest org.waveprotocol.box.server.rpc.AdminServletTest"
```

Expected:
- `HtmlRendererFeatureFlagsTest` fails because analytics markup is still present.
- `AdminServletTest` fails because analytics routes still exist.

- [ ] **Step 3: Remove analytics tab markup and JavaScript**

Delete the `Analytics` tab button, `panel-analytics` section, chart helpers, analytics loaders, and any analytics-tab lazy-load hook from `HtmlRenderer.renderAdminPage(...)`.

Keep:
- users tab
- contacts tab
- flags tab
- ops tab

- [ ] **Step 4: Remove analytics routes and dependencies from `AdminServlet`**

Implementation details:
- remove `AdminAnalyticsService`, `AnalyticsCounterStore`, and `analyticsCountersEnabled` fields
- remove `/api/analytics/status` and `/api/analytics/history` routing branches
- if `pathInfo` starts with `/api/` and is not otherwise handled, return a JSON `404`

Expected branch shape:

```java
if (pathInfo != null && pathInfo.equals("/api/ops/status")) {
  handleOpsStatus(resp);
} else if (pathInfo != null && pathInfo.equals("/api/ops/reindex/status")) {
  handleReindexStatus(resp);
} else if (pathInfo != null && pathInfo.startsWith("/api/users")) {
  handleGetUsers(req, resp, caller);
} else if (pathInfo != null && pathInfo.startsWith("/api/contacts")) {
  handleGetContacts(req, resp);
} else if (pathInfo != null && pathInfo.startsWith("/api/")) {
  sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Not found");
} else {
  // render admin page
}
```

- [ ] **Step 5: Re-run the removal tests**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest org.waveprotocol.box.server.rpc.AdminServletTest"
```

Expected: PASS.

## Task 2: Replace Analytics Persistence With Grafana-Oriented Telemetry

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorderTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/authentication/SessionManagerTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorder.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/persistence/AnalyticsCounterStore.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStore.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/NoOpAnalyticsCounterStore.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java`
- Delete: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsService.java`
- Delete: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTracker.java`
- Delete: `wave/src/test/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsServiceTest.java`
- Delete: `wave/src/test/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTrackerTest.java`

- [ ] **Step 1: Write failing recorder metric tests**

Rewrite `AnalyticsRecorderTest` so it clears the Micrometer registry, exercises recorder methods, and asserts Prometheus samples exist in the scrape output.

```java
MetricsHolder.prometheus().clear();
AnalyticsRecorder recorder = new AnalyticsRecorder();

recorder.incrementPageViews(BASE_TIME);
String scrape = MetricsHolder.prometheus().scrape();

assertTrue(scrape.contains("wave_analytics_public_wave_page_views_total 1.0"));
```

Also cover:
- `wave_analytics_public_wave_api_views_total`
- `wave_analytics_users_registered_total`
- `wave_analytics_active_user_events_total`
- `wave_analytics_waves_created_total`
- `wave_analytics_blips_created_total`

- [ ] **Step 2: Run the recorder-focused tests to verify they fail**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.AnalyticsRecorderTest org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest org.waveprotocol.box.server.authentication.SessionManagerTest"
```

Expected:
- tests fail because recorder still writes to `AnalyticsCounterStore`.

- [ ] **Step 3: Replace `AnalyticsRecorder` internals**

Keep the public methods and `WaveBus.Subscriber` contract, but remove the store dependency entirely.

Implementation shape:

```java
private final Counter publicWavePageViews;
private final Counter publicWaveApiViews;
private final Counter usersRegistered;
private final Counter activeUserEvents;
private final Counter wavesCreated;
private final Counter blipsCreated;
```

Rules:
- use `MetricsHolder.registry()` to create counters once in the constructor
- `recordBlipsCreated(count, ...)` increments by `count`
- keep the WaveBus subscriber behavior that derives wave/blip creation from deltas
- optionally emit one structured info log per event for Loki using MDC fields, but do not add high-cardinality metric tags

- [ ] **Step 4: Remove analytics persistence and aggregation code**

Delete:
- `AnalyticsCounterStore`
- memory/no-op/Mongo analytics stores
- `AdminAnalyticsService`
- `PublicWaveViewTracker`

Update:
- `PersistenceModule` to stop binding analytics storage
- `ServerMain` to stop consulting `core.analytics_counters_enabled`
- any remaining constructors/tests that still create recorder/store instances

- [ ] **Step 5: Re-run telemetry-focused tests**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.AnalyticsRecorderTest org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest org.waveprotocol.box.server.authentication.SessionManagerTest"
```

Expected: PASS.

## Task 3: Wire Grafana Alloy To Scrape Wave Metrics And Update Docs

**Files:**
- Modify: `deploy/supawave-host/configure-grafana-alloy.sh`
- Modify: `deploy/supawave-host/README.md`
- Add: `wave/config/changelog.d/2026-04-13-grafana-admin-analytics.json`

- [ ] **Step 1: Write a failing documentation/config expectation (manual diff checkpoint)**

Before editing, confirm the current Alloy config has no Wave scrape stanza:

```bash
rg -n "supawave_metrics|/metrics|prometheus.scrape .*supawave" deploy/supawave-host/configure-grafana-alloy.sh
```

Expected: no Wave application scrape block exists yet.

- [ ] **Step 2: Add Wave `/metrics` scrape to Alloy**

Add a scrape job that targets the local Wave process and forwards to `prometheus.remote_write.metrics_service`.

Recommended shape:

```alloy
prometheus.scrape "supawave_app" {
  targets = [{
    __address__ = "127.0.0.1:9898",
    job = "supawave/wave",
    instance = constants.hostname,
  }]
  metrics_path = "/metrics"
  forward_to = [prometheus.relabel.supawave_app.receiver]
}
```

Follow with a relabel block that forwards to remote write and keeps the Wave app job labels stable.

- [ ] **Step 3: Document the Grafana contract**

Update `deploy/supawave-host/README.md` with:
- the Wave scrape requirement
- validation command for Wave metrics
- example metric names to query in Grafana:
  - `wave_analytics_public_wave_page_views_total`
  - `wave_analytics_public_wave_api_views_total`
  - `wave_analytics_users_registered_total`
  - `wave_analytics_active_user_events_total`
  - `wave_analytics_waves_created_total`
  - `wave_analytics_blips_created_total`

- [ ] **Step 4: Add changelog fragment**

Add a fragment describing:
- admin analytics tab removal
- Grafana-backed metrics export
- observability-first analytics path

- [ ] **Step 5: Run doc/config sanity checks**

Run:

```bash
python3 scripts/validate-changelog.py
```

Expected: PASS.

## Task 4: Final Verification, Review, And Issue Evidence

**Files:**
- Add: `journal/local-verification/2026-04-13-issue-884-grafana-admin-analytics.md`

- [ ] **Step 1: Run focused automated verification**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest org.waveprotocol.box.server.rpc.AdminServletTest org.waveprotocol.box.server.waveserver.AnalyticsRecorderTest org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest org.waveprotocol.box.server.authentication.SessionManagerTest"
```

Expected: PASS.

- [ ] **Step 2: Run compile verification**

Run:

```bash
sbt compile
```

Expected: PASS.

- [ ] **Step 3: Run narrow local server sanity verification**

Suggested command:

```bash
sbt run
```

In a second shell:

```bash
curl -fsS http://127.0.0.1:9898/metrics | rg "wave_analytics_(public_wave_page_views|public_wave_api_views|users_registered|active_user_events|waves_created|blips_created)_total"
curl -fsS http://127.0.0.1:9898/admin | rg "Operations|Feature Flags"
```

Expected:
- new analytics metric series are present
- `/admin` still renders without an Analytics tab

- [ ] **Step 4: Record verification in the journal file**

Capture:
- worktree path
- branch
- plan path
- commands run
- results
- residual risk: Grafana dashboard provisioning remains manual/out of scope

- [ ] **Step 5: Self-review the final diff before commit**

Review checklist:
- no dangling analytics imports or constructor params remain
- no references to `/api/analytics/` remain in runtime code
- no references to deleted analytics stores remain in persistence wiring
- Alloy config additions are limited to the Wave metrics scrape path
- changelog and issue evidence reflect the final implementation

## Plan Self-Review

- Spec coverage:
  - remove admin analytics tab: covered by Task 1
  - move analytics to Grafana metrics/log pipeline: covered by Task 2 + Task 3
  - remove server-side analytics persistence/aggregation: covered by Task 2
  - create draft PR after verification: covered by Task 4 follow-through and issue evidence
- Placeholder scan:
  - no `TODO`/`TBD` placeholders remain
  - commands, files, and expected results are concrete
- Type consistency:
  - the plan keeps `AnalyticsRecorder` as the telemetry seam to avoid unnecessary constructor churn
  - `AdminServlet` removal work consistently targets `/api/analytics/status` and `/api/analytics/history`


import json
import os
import urllib.error
import unittest
from pathlib import Path
from unittest import mock

from scripts import upsert_grafana_dashboard


REPO_ROOT = Path(__file__).resolve().parents[2]
PERF_DASHBOARD_PATH = REPO_ROOT / "grafana" / "dashboards" / "perf-observability.json"
ANALYTICS_DASHBOARD_PATH = (
    REPO_ROOT / "deploy" / "supawave-host" / "grafana-dashboards" / "supawave-analytics-overview.json"
)


def walk_panels(dashboard: dict) -> list[dict]:
  found = []
  for panel in dashboard.get("panels", []):
    found.append(panel)
    found.extend(walk_panels(panel))
  return found


def find_panel(dashboard: dict, title: str) -> dict:
  for panel in walk_panels(dashboard):
    if panel.get("title") == title:
      return panel
  raise AssertionError(f"Panel not found: {title}")


class GrafanaDashboardUpsertTest(unittest.TestCase):
  class _FakeResponse:
    def __init__(self, status: int = 200):
      self.status = status

    def __enter__(self):
      return self

    def __exit__(self, exc_type, exc, tb):
      return False

  def test_dashboard_contains_expected_perf_panels(self):
    dashboard = json.loads(PERF_DASHBOARD_PATH.read_text(encoding="utf-8"))
    titles = {panel["title"] for panel in walk_panels(dashboard) if "title" in panel}

    self.assertIn("Latest Runs", titles)
    self.assertIn("P95 by Run", titles)
    self.assertIn("P99 by Run", titles)
    self.assertIn("Request P95 by Run", titles)
    self.assertIn("Request Volume by Run", titles)
    self.assertIn("Latency Distribution", titles)
    self.assertIn("JVM Heap", titles)
    self.assertIn("Runner CPU", titles)

  def test_perf_dashboard_contains_run_and_request_selectors(self):
    dashboard = json.loads(PERF_DASHBOARD_PATH.read_text(encoding="utf-8"))
    variable_names = {entry["name"] for entry in dashboard.get("templating", {}).get("list", [])}

    self.assertIn("branch", variable_names)
    self.assertIn("simulation", variable_names)
    self.assertIn("sha", variable_names)
    self.assertIn("run_id", variable_names)
    self.assertIn("run_attempt", variable_names)
    self.assertIn("request_name", variable_names)

  def test_latest_runs_panel_orders_by_run_timestamp(self):
    dashboard = json.loads(PERF_DASHBOARD_PATH.read_text(encoding="utf-8"))
    latest_runs_panel = find_panel(dashboard, "Latest Runs")

    self.assertIn("wave_perf_last_run_timestamp_seconds", latest_runs_panel["targets"][0]["expr"])
    self.assertIn('run_attempt=~"$run_attempt"', latest_runs_panel["targets"][0]["expr"])

  def test_latency_distribution_panel_uses_instant_query(self):
    dashboard = json.loads(PERF_DASHBOARD_PATH.read_text(encoding="utf-8"))
    latency_distribution_panel = find_panel(dashboard, "Latency Distribution")

    self.assertTrue(latency_distribution_panel["targets"][0]["instant"])

  def test_dashboard_contains_expected_analytics_panels(self):
    dashboard = json.loads(ANALYTICS_DASHBOARD_PATH.read_text(encoding="utf-8"))
    titles = {panel["title"] for panel in walk_panels(dashboard) if "title" in panel}

    self.assertIn("Public Page Views", titles)
    self.assertIn("Public API Views", titles)
    self.assertIn("Registrations", titles)
    self.assertIn("Active User Events", titles)

  def test_default_dashboard_paths_include_perf_and_analytics(self):
    self.assertEqual(
        [PERF_DASHBOARD_PATH, ANALYTICS_DASHBOARD_PATH],
        upsert_grafana_dashboard.default_dashboard_paths(),
    )

  def test_load_dashboard_targets_deduplicates_paths(self):
    targets = upsert_grafana_dashboard.load_dashboard_targets(
        [
            str(PERF_DASHBOARD_PATH),
            str(ANALYTICS_DASHBOARD_PATH),
            str(PERF_DASHBOARD_PATH),
        ]
    )

    self.assertEqual([PERF_DASHBOARD_PATH, ANALYTICS_DASHBOARD_PATH], targets)

  def test_prepare_dashboard_rewrites_perf_dashboard_datasource_uid(self):
    dashboard = upsert_grafana_dashboard.prepare_dashboard(PERF_DASHBOARD_PATH, "datasource")

    rendered = json.dumps(dashboard)
    self.assertIn('"uid": "datasource"', rendered)
    self.assertNotIn("__PROMETHEUS_DS_UID__", rendered)
    self.assertNotIn("${DS_PROMETHEUS}", rendered)

  def test_prepare_dashboard_rewrites_analytics_dashboard_datasource_uid_and_strips_inputs(self):
    dashboard = upsert_grafana_dashboard.prepare_dashboard(ANALYTICS_DASHBOARD_PATH, "datasource")

    rendered = json.dumps(dashboard)
    self.assertIn('"uid": "datasource"', rendered)
    self.assertNotIn("${DS_PROMETHEUS}", rendered)
    self.assertNotIn('"__inputs"', rendered)

  def test_build_payload_sets_overwrite_true(self):
    payload = upsert_grafana_dashboard.build_payload(
        dashboard={"title": "Wave Perf"},
        folder_uid="perf",
    )

    self.assertTrue(payload["overwrite"])
    self.assertEqual("perf", payload["folderUid"])

  def test_main_skips_cleanly_when_credentials_are_missing(self):
    with mock.patch.dict(os.environ, {}, clear=True):
      exit_code = upsert_grafana_dashboard.main([])

    self.assertEqual(0, exit_code)

  def test_main_strict_fails_when_credentials_are_missing(self):
    with mock.patch.dict(os.environ, {}, clear=True):
      exit_code = upsert_grafana_dashboard.main(["--strict"])

    self.assertEqual(1, exit_code)

  def test_main_skips_cleanly_when_grafana_upsert_returns_http_error(self):
    env = {
        "GRAFANA_URL": "https://grafana.example.com",
        "GRAFANA_DASHBOARD_API_TOKEN": "token",
        "GRAFANA_PROMETHEUS_DATASOURCE_UID": "datasource",
    }
    error = urllib.error.HTTPError(
        url="https://grafana.example.com/api/dashboards/db",
        code=502,
        msg="Bad Gateway",
        hdrs=None,
        fp=None,
    )

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch.object(upsert_grafana_dashboard.urllib.request, "urlopen", side_effect=error):
        exit_code = upsert_grafana_dashboard.main([])

    self.assertEqual(0, exit_code)

  def test_main_strict_fails_when_grafana_upsert_returns_http_error(self):
    env = {
        "GRAFANA_URL": "https://grafana.example.com",
        "GRAFANA_DASHBOARD_API_TOKEN": "token",
        "GRAFANA_PROMETHEUS_DATASOURCE_UID": "datasource",
    }
    error = urllib.error.HTTPError(
        url="https://grafana.example.com/api/dashboards/db",
        code=502,
        msg="Bad Gateway",
        hdrs=None,
        fp=None,
    )

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch.object(upsert_grafana_dashboard.urllib.request, "urlopen", side_effect=error):
        exit_code = upsert_grafana_dashboard.main(["--strict"])

    self.assertEqual(1, exit_code)

  def test_main_strict_fails_when_grafana_upsert_returns_url_error(self):
    env = {
        "GRAFANA_URL": "https://grafana.example.com",
        "GRAFANA_DASHBOARD_API_TOKEN": "token",
        "GRAFANA_PROMETHEUS_DATASOURCE_UID": "datasource",
    }

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch.object(
          upsert_grafana_dashboard.urllib.request,
          "urlopen",
          side_effect=urllib.error.URLError("connection refused"),
      ):
        exit_code = upsert_grafana_dashboard.main(["--strict"])

    self.assertEqual(1, exit_code)

  def test_main_posts_both_default_dashboards_on_success(self):
    env = {
        "GRAFANA_URL": "https://grafana.example.com",
        "GRAFANA_DASHBOARD_API_TOKEN": "token",
        "GRAFANA_PROMETHEUS_DATASOURCE_UID": "datasource",
        "GRAFANA_FOLDER_UID": "folder",
    }
    requests = []

    def fake_urlopen(request, timeout=None):
      requests.append(request)
      return self._FakeResponse(status=200)

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch.object(upsert_grafana_dashboard.urllib.request, "urlopen", side_effect=fake_urlopen):
        exit_code = upsert_grafana_dashboard.main([])

    self.assertEqual(0, exit_code)
    self.assertEqual(2, len(requests))

    payloads = [json.loads(request.data.decode("utf-8")) for request in requests]
    titles = [payload["dashboard"]["title"] for payload in payloads]

    self.assertEqual(
        ["Wave Perf Observability", "SupaWave Analytics Overview"],
        titles,
    )
    self.assertTrue(all(payload["overwrite"] for payload in payloads))
    self.assertTrue(all(payload["folderUid"] == "folder" for payload in payloads))

  def test_upsert_dashboard_strict_fails_when_file_is_missing(self):
    exit_code = upsert_grafana_dashboard.upsert_dashboard(
        REPO_ROOT / "missing-dashboard.json",
        grafana_url="https://grafana.example.com",
        api_token="token",
        datasource_uid="datasource",
        folder_uid=None,
        strict=True,
    )

    self.assertEqual(1, exit_code)

  def test_main_skips_cleanly_when_grafana_upsert_returns_url_error(self):
    env = {
        "GRAFANA_URL": "https://grafana.example.com",
        "GRAFANA_DASHBOARD_API_TOKEN": "token",
        "GRAFANA_PROMETHEUS_DATASOURCE_UID": "datasource",
    }

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch.object(
          upsert_grafana_dashboard.urllib.request,
          "urlopen",
          side_effect=urllib.error.URLError("connection refused"),
      ):
        exit_code = upsert_grafana_dashboard.main([])

    self.assertEqual(0, exit_code)


if __name__ == "__main__":
  unittest.main()

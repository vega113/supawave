import json
import os
import urllib.error
import unittest
from pathlib import Path
from unittest import mock

from scripts import upsert_grafana_dashboard


REPO_ROOT = Path(__file__).resolve().parents[2]
DASHBOARD_PATH = REPO_ROOT / "grafana" / "dashboards" / "perf-observability.json"


def walk_panels(dashboard: dict) -> list[dict]:
  found = []
  for panel in dashboard.get("panels", []):
    found.append(panel)
    found.extend(walk_panels(panel))
  return found


class GrafanaDashboardUpsertTest(unittest.TestCase):
  def test_dashboard_contains_expected_perf_panels(self):
    dashboard = json.loads(DASHBOARD_PATH.read_text(encoding="utf-8"))
    titles = {panel["title"] for panel in walk_panels(dashboard) if "title" in panel}

    self.assertIn("Latest Perf Status", titles)
    self.assertIn("P95 by Simulation", titles)
    self.assertIn("JVM Heap", titles)
    self.assertIn("Runner CPU", titles)

  def test_build_payload_sets_overwrite_true(self):
    payload = upsert_grafana_dashboard.build_payload(
        dashboard={"title": "Wave Perf"},
        folder_uid="perf",
    )

    self.assertTrue(payload["overwrite"])
    self.assertEqual("perf", payload["folderUid"])

  def test_main_skips_cleanly_when_credentials_are_missing(self):
    with mock.patch.dict(os.environ, {}, clear=True):
      exit_code = upsert_grafana_dashboard.main()

    self.assertEqual(0, exit_code)

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
        exit_code = upsert_grafana_dashboard.main()

    self.assertEqual(0, exit_code)

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
        exit_code = upsert_grafana_dashboard.main()

    self.assertEqual(0, exit_code)


if __name__ == "__main__":
  unittest.main()

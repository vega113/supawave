import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
BUILD_PATH = REPO_ROOT / "build.sbt"
PLAN_PATH = REPO_ROOT / "docs" / "superpowers" / "plans" / "2026-04-13-grafana-perf-observability.md"


class JakartaMetricsSelectorTest(unittest.TestCase):
  def test_jakarta_test_filter_allows_metrics_servlet_it(self):
    build_text = BUILD_PATH.read_text(encoding="utf-8")

    self.assertIn(
        'name == "org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"',
        build_text,
    )

  def test_plan_uses_jakarta_test_selector_for_metrics_servlet_test(self):
    plan_text = PLAN_PATH.read_text(encoding="utf-8")

    self.assertIn(
        'sbt "JakartaTest/testOnly org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"',
        plan_text,
    )
    self.assertNotIn(
        'sbt "JakartaIT/testOnly org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"',
        plan_text,
    )


if __name__ == "__main__":
  unittest.main()

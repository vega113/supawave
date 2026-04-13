#!/usr/bin/env python3
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DASHBOARD_PATH = REPO_ROOT / "grafana" / "dashboards" / "perf-observability.json"
PLACEHOLDER_UID = "__PROMETHEUS_DS_UID__"


def load_dashboard(path: Path = DASHBOARD_PATH) -> dict:
  return json.loads(path.read_text(encoding="utf-8"))


def inject_datasource(node, datasource_uid: str):
  if isinstance(node, dict):
    updated = {}
    for key, value in node.items():
      if key == "uid" and value == PLACEHOLDER_UID:
        updated[key] = datasource_uid
      else:
        updated[key] = inject_datasource(value, datasource_uid)
    return updated
  if isinstance(node, list):
    return [inject_datasource(item, datasource_uid) for item in node]
  return node


def build_payload(dashboard: dict, folder_uid: str | None = None) -> dict:
  payload = {
      "dashboard": dashboard,
      "overwrite": True,
  }
  if folder_uid:
    payload["folderUid"] = folder_uid
  return payload


def main() -> int:
  grafana_url = os.getenv("GRAFANA_URL", "")
  api_token = os.getenv("GRAFANA_DASHBOARD_API_TOKEN", "")
  datasource_uid = os.getenv("GRAFANA_PROMETHEUS_DATASOURCE_UID", "")
  folder_uid = os.getenv("GRAFANA_FOLDER_UID", "")

  if not grafana_url or not api_token or not datasource_uid:
    print("warning: Grafana dashboard credentials are incomplete; skipping upsert", file=sys.stderr)
    return 0

  dashboard = inject_datasource(load_dashboard(), datasource_uid)
  payload = build_payload(dashboard=dashboard, folder_uid=folder_uid or None)
  request = urllib.request.Request(
      f"{grafana_url.rstrip('/')}/api/dashboards/db",
      data=json.dumps(payload).encode("utf-8"),
      headers={
          "Authorization": f"Bearer {api_token}",
          "Content-Type": "application/json",
      },
      method="POST",
  )

  try:
    with urllib.request.urlopen(request) as response:
      if 200 <= response.status < 300:
        return 0
      print(f"warning: Grafana dashboard upsert returned HTTP {response.status}", file=sys.stderr)
      return 0
  except urllib.error.HTTPError as exc:
    print(f"warning: Grafana dashboard upsert HTTP error: {exc.code} {exc.reason}", file=sys.stderr)
    return 0
  except urllib.error.URLError as exc:
    print(f"warning: Grafana dashboard upsert URL error: {exc.reason}", file=sys.stderr)
    return 0


if __name__ == "__main__":
  raise SystemExit(main())

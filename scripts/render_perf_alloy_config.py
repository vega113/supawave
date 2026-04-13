#!/usr/bin/env python3
import argparse
from urllib.parse import urlparse


def _quoted(value: str) -> str:
  return value.replace("\\", "\\\\").replace('"', '\\"')


def _address_from_url(base_url: str) -> str:
  parsed = urlparse(base_url)
  if not parsed.hostname:
    raise ValueError(f"Invalid base URL: {base_url}")
  port = parsed.port or (443 if parsed.scheme == "https" else 80)
  return f"{parsed.hostname}:{port}"


def render_config(
    metrics_url: str,
    metrics_id: str,
    api_key: str,
    base_url: str,
    exporter_port: int,
    run_labels: dict[str, str],
) -> str:
  wave_address = _address_from_url(base_url)
  label_rules = "\n".join(
      f"""  rule {{
    target_label = "{key}"
    replacement  = "{_quoted(value)}"
  }}"""
      for key, value in run_labels.items()
      if value
  )

  return f"""prometheus.remote_write "perf" {{
  endpoint {{
    url = "{_quoted(metrics_url)}"

    basic_auth {{
      username = "{_quoted(metrics_id)}"
      password = "{_quoted(api_key)}"
    }}
  }}
}}

prometheus.scrape "wave" {{
  targets = [{{
    __address__ = "{wave_address}",
    __metrics_path__ = "/metrics",
  }}]
  forward_to = [prometheus.remote_write.perf.receiver]
  scrape_interval = "5s"
}}

prometheus.scrape "perf_exporter" {{
  targets = [{{
    __address__ = "127.0.0.1:{exporter_port}",
    __metrics_path__ = "/metrics",
  }}]
  forward_to = [prometheus.remote_write.perf.receiver]
  scrape_interval = "5s"
}}

prometheus.exporter.unix "runner" {{
}}

discovery.relabel "runner" {{
  targets = prometheus.exporter.unix.runner.targets
{label_rules}
}}

prometheus.scrape "runner" {{
  targets = discovery.relabel.runner.output
  forward_to = [prometheus.remote_write.perf.receiver]
  scrape_interval = "5s"
}}
"""


def main() -> int:
  parser = argparse.ArgumentParser()
  parser.add_argument("--output", required=True)
  parser.add_argument("--metrics-url", required=True)
  parser.add_argument("--metrics-id", required=True)
  parser.add_argument("--api-key", required=True)
  parser.add_argument("--base-url", required=True)
  parser.add_argument("--exporter-port", type=int, default=9464)
  parser.add_argument("--repo", default="")
  parser.add_argument("--branch", default="")
  parser.add_argument("--sha", default="")
  parser.add_argument("--workflow", default="perf")
  parser.add_argument("--run-id", dest="run_id", default="")
  parser.add_argument("--run-attempt", dest="run_attempt", default="")
  args = parser.parse_args()

  run_labels = {
      "repo": args.repo,
      "branch": args.branch,
      "sha": args.sha,
      "workflow": args.workflow,
      "run_id": args.run_id,
      "run_attempt": args.run_attempt,
  }
  config = render_config(
      metrics_url=args.metrics_url,
      metrics_id=args.metrics_id,
      api_key=args.api_key,
      base_url=args.base_url,
      exporter_port=args.exporter_port,
      run_labels=run_labels,
  )
  with open(args.output, "w", encoding="utf-8") as stream:
    stream.write(config)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())

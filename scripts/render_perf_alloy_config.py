#!/usr/bin/env python3
import argparse
from urllib.parse import urlparse


def _quoted(value: str) -> str:
  return value.replace("\\", "\\\\").replace('"', '\\"')


def _env_expr(var_name: str) -> str:
  return f'sys.env("{_quoted(var_name)}")'


def _address_from_url(base_url: str) -> str:
  parsed = urlparse(base_url)
  if not parsed.hostname:
    raise ValueError(f"Invalid base URL: {base_url}")
  port = parsed.port or (443 if parsed.scheme == "https" else 80)
  return f"{parsed.hostname}:{port}"


def render_config(
    metrics_url: str,
    metrics_id: str,
    auth_env_name: str,
    base_url: str,
    exporter_port: int,
    run_labels: dict[str, str],
) -> str:
  # auth_env_name is the *name* of an environment variable, never a secret
  # value.  Alloy resolves it at runtime via sys.env().  Validate it looks like
  # an identifier so we can be certain no literal secret was passed.
  if not auth_env_name or not auth_env_name.replace("_", "").isalnum():
    raise ValueError(
        f"auth_env_name must be a valid environment-variable name, got: {auth_env_name!r}"
    )
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
      password = {_env_expr(auth_env_name)}
    }}
  }}
}}

discovery.relabel "wave" {{
  targets = [{{
    __address__ = "{wave_address}",
    __metrics_path__ = "/metrics",
  }}]
{label_rules}
}}

prometheus.scrape "wave" {{
  targets = discovery.relabel.wave.output
  forward_to = [prometheus.remote_write.perf.receiver]
  scrape_interval = "5s"
}}

discovery.relabel "perf_exporter" {{
  targets = [{{
    __address__ = "127.0.0.1:{exporter_port}",
    __metrics_path__ = "/metrics",
  }}]
{label_rules}
}}

prometheus.scrape "perf_exporter" {{
  targets = discovery.relabel.perf_exporter.output
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
  parser.add_argument("--api-key-env-var", dest="auth_env_name", default="GCLOUD_RW_API_KEY")
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
      auth_env_name=args.auth_env_name,
      base_url=args.base_url,
      exporter_port=args.exporter_port,
      run_labels=run_labels,
  )
  # The config file contains `password = sys.env("…")`, which is an Alloy
  # runtime expression — the literal secret is never stored.  The CodeQL alert
  # is a false positive; suppress it here.
  with open(args.output, "w", encoding="utf-8") as stream:
    stream.write(config)  # lgtm[py/cleartext-storage-of-sensitive-data]
  return 0


if __name__ == "__main__":
  raise SystemExit(main())

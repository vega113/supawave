#!/usr/bin/env python3
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


LABEL_ORDER = ("repo", "branch", "sha", "workflow", "run_id", "run_attempt", "simulation")

SIMULATION_THRESHOLDS = {
    "SearchLoadSimulation": {
        "p95_lt_2000ms": lambda timings, success_ratio: timings["p95"] < 2000,
        "p99_lt_5000ms": lambda timings, success_ratio: timings["p99"] < 5000,
        "success_gt_95pct": lambda timings, success_ratio: success_ratio > 0.95,
    },
    "WaveOpenSimulation": {
        "p95_lt_3000ms": lambda timings, success_ratio: timings["p95"] < 3000,
        "p99_lt_5000ms": lambda timings, success_ratio: timings["p99"] < 5000,
        "success_gt_95pct": lambda timings, success_ratio: success_ratio > 0.95,
    },
    "FullJourneySimulation": {
        "mean_lt_2000ms": lambda timings, success_ratio: timings["mean"] < 2000,
        "max_lt_10000ms": lambda timings, success_ratio: timings["max"] < 10000,
        "success_gt_95pct": lambda timings, success_ratio: success_ratio > 0.95,
    },
}

TIMING_FIELDS = {
    # Gatling's default charting indicators map percentiles1..4 to 50/75/95/99.
    # If gatling.conf changes those indicators, this exporter mapping must change too.
    "min": "minResponseTime",
    "max": "maxResponseTime",
    "mean": "meanResponseTime",
    "std_dev": "standardDeviation",
    "p50": "percentiles1",
    "p75": "percentiles2",
    "p95": "percentiles3",
    "p99": "percentiles4",
}

DISTRIBUTION_FIELDS = {
    "lt_800ms": "group1",
    "between_800ms_1200ms": "group2",
    "gte_1200ms": "group3",
    "failed": "group4",
}


def _extract_total(stats: dict, field_name: str) -> int | float:
  return stats[field_name]["total"]


def _extract_requests(stats: dict) -> dict:
  request_counts = stats["numberOfRequests"]
  return {
      "ok": request_counts["ok"],
      "ko": request_counts["ko"],
  }


def _extract_timings(stats: dict) -> dict:
  return {
      stat_name: _extract_total(stats, source_name)
      for stat_name, source_name in TIMING_FIELDS.items()
  }


def _extract_distribution(stats: dict) -> dict:
  return {
      bucket_name: {
          "name": stats[source_name]["name"],
          "count": stats[source_name]["count"],
          "percentage": stats[source_name]["percentage"],
      }
      for bucket_name, source_name in DISTRIBUTION_FIELDS.items()
  }


def build_request_metrics(request_nodes: dict) -> list[dict]:
  request_metrics = []

  def walk(nodes: dict):
    for request_node in nodes.values():
      if request_node.get("type") == "GROUP":
        walk(request_node.get("contents", {}))
        continue
      if request_node.get("type") != "REQUEST":
        continue
      stats = request_node["stats"]
      request_metrics.append({
          "request_name": request_node["name"],
          "request_path": request_node.get("path", request_node["name"]),
          "requests": _extract_requests(stats),
          "timings_ms": _extract_timings(stats),
          "distribution": _extract_distribution(stats),
          "mean_requests_per_second": _extract_total(stats, "meanNumberOfRequestsPerSecond"),
      })

  walk(request_nodes)
  return sorted(request_metrics, key=lambda metric: metric["request_name"])


def load_gatling_report(report_dir: Path) -> dict:
  js_dir = report_dir / "js"
  global_stats = json.loads((js_dir / "global_stats.json").read_text(encoding="utf-8"))
  stats_tree = json.loads((js_dir / "stats.json").read_text(encoding="utf-8"))
  return {
      "global_stats": global_stats,
      "request_nodes": stats_tree.get("contents", {}),
  }


def build_summary(simulation: str, report_dir: Path, metadata: dict, exit_code: int) -> dict:
  parsed = load_gatling_report(report_dir)
  global_stats = parsed["global_stats"]
  requests = _extract_requests(global_stats)
  total_requests = requests["ok"] + requests["ko"]
  success_ratio = (requests["ok"] / total_requests) if total_requests else 0.0
  timings_ms = _extract_timings(global_stats)
  thresholds = SIMULATION_THRESHOLDS.get(simulation, {})
  assertions = {
      name: checker(timings_ms, success_ratio)
      for name, checker in thresholds.items()
  }
  summary = {
      "simulation": simulation,
      "repo": metadata.get("repo", ""),
      "branch": metadata.get("branch", ""),
      "sha": metadata.get("sha", ""),
      "workflow": metadata.get("workflow", ""),
      "run_id": metadata.get("run_id", ""),
      "run_attempt": metadata.get("run_attempt", ""),
      "exit_code": exit_code,
      "requests": requests,
      "timings_ms": timings_ms,
      "distribution": _extract_distribution(global_stats),
      "request_metrics": build_request_metrics(parsed["request_nodes"]),
      "mean_requests_per_second": _extract_total(global_stats, "meanNumberOfRequestsPerSecond"),
      "success_ratio": success_ratio,
      "successful_requests_pct": success_ratio * 100.0,
      "assertions": assertions,
      "run_timestamp": int(metadata.get("run_timestamp", time.time())),
  }
  return summary


def load_summaries(results_dir: Path) -> list[dict]:
  summaries = []
  for path in sorted(results_dir.glob("*-summary.json")):
    summaries.append(json.loads(path.read_text(encoding="utf-8")))
  return summaries


def _escape_label(value: object) -> str:
  return str(value).replace("\\", "\\\\").replace('"', '\\"')


def _format_labels(summary: dict, extra: dict | None = None) -> str:
  labels = [f'{key}="{_escape_label(summary.get(key, ""))}"' for key in LABEL_ORDER]
  if extra:
    labels.extend(f'{key}="{_escape_label(value)}"' for key, value in extra.items())
  return ",".join(labels)


def render_metrics(summaries: list[dict]) -> str:
  lines = [
      "# HELP wave_perf_run_info Wave perf run identity.",
      "# TYPE wave_perf_run_info gauge",
      "# HELP wave_perf_response_time_ms Wave perf response-time summary metrics in milliseconds.",
      "# TYPE wave_perf_response_time_ms gauge",
      "# HELP wave_perf_mean_requests_per_second Wave perf throughput summary metrics.",
      "# TYPE wave_perf_mean_requests_per_second gauge",
      "# HELP wave_perf_requests_count Wave perf request counts by outcome.",
      "# TYPE wave_perf_requests_count gauge",
      "# HELP wave_perf_distribution_count Wave perf latency distribution counts.",
      "# TYPE wave_perf_distribution_count gauge",
      "# HELP wave_perf_distribution_ratio Wave perf latency distribution ratios from 0 to 1.",
      "# TYPE wave_perf_distribution_ratio gauge",
      "# HELP wave_perf_request_response_time_ms Wave perf request-level response-time summary metrics in milliseconds.",
      "# TYPE wave_perf_request_response_time_ms gauge",
      "# HELP wave_perf_request_requests_count Wave perf request-level counts by outcome.",
      "# TYPE wave_perf_request_requests_count gauge",
      "# HELP wave_perf_request_mean_requests_per_second Wave perf request-level throughput metrics.",
      "# TYPE wave_perf_request_mean_requests_per_second gauge",
      "# HELP wave_perf_request_distribution_count Wave perf request-level latency distribution counts.",
      "# TYPE wave_perf_request_distribution_count gauge",
      "# HELP wave_perf_request_distribution_ratio Wave perf request-level latency distribution ratios from 0 to 1.",
      "# TYPE wave_perf_request_distribution_ratio gauge",
      "# HELP wave_perf_success_ratio Wave perf success ratio from 0 to 1.",
      "# TYPE wave_perf_success_ratio gauge",
      "# HELP wave_perf_assertion_status Wave perf assertion pass status where 1 is pass and 0 is fail.",
      "# TYPE wave_perf_assertion_status gauge",
      "# HELP wave_perf_last_run_timestamp_seconds Unix timestamp for the last recorded perf run.",
      "# TYPE wave_perf_last_run_timestamp_seconds gauge",
  ]
  for summary in summaries:
    labels = _format_labels(summary)
    lines.append(f"wave_perf_run_info{{{labels}}} 1")
    for stat_name, stat_value in summary["timings_ms"].items():
      lines.append(f'wave_perf_response_time_ms{{{_format_labels(summary, {"stat": stat_name})}}} {stat_value}')
    lines.append(f"wave_perf_mean_requests_per_second{{{labels}}} {summary['mean_requests_per_second']}")
    for status_name, request_count in summary["requests"].items():
      lines.append(f'wave_perf_requests_count{{{_format_labels(summary, {"status": status_name})}}} {request_count}')
    for bucket_name, bucket in summary["distribution"].items():
      lines.append(f'wave_perf_distribution_count{{{_format_labels(summary, {"bucket": bucket_name})}}} {bucket["count"]}')
      lines.append(
          f'wave_perf_distribution_ratio{{{_format_labels(summary, {"bucket": bucket_name})}}} {bucket["percentage"] / 100.0}'
      )
    for request_metric in summary.get("request_metrics", []):
      request_labels = {
          "request_name": request_metric["request_name"],
          "request_path": request_metric["request_path"],
      }
      for stat_name, stat_value in request_metric["timings_ms"].items():
        lines.append(
            f'wave_perf_request_response_time_ms{{{_format_labels(summary, {**request_labels, "stat": stat_name})}}} {stat_value}'
        )
      for status_name, request_count in request_metric["requests"].items():
        lines.append(
            f'wave_perf_request_requests_count{{{_format_labels(summary, {**request_labels, "status": status_name})}}} {request_count}'
        )
      for bucket_name, bucket in request_metric["distribution"].items():
        lines.append(
            f'wave_perf_request_distribution_count{{{_format_labels(summary, {**request_labels, "bucket": bucket_name})}}} {bucket["count"]}'
        )
        lines.append(
            f'wave_perf_request_distribution_ratio{{{_format_labels(summary, {**request_labels, "bucket": bucket_name})}}} {bucket["percentage"] / 100.0}'
        )
      lines.append(
          f'wave_perf_request_mean_requests_per_second{{{_format_labels(summary, request_labels)}}} {request_metric["mean_requests_per_second"]}'
      )
    lines.append(f"wave_perf_success_ratio{{{labels}}} {summary['success_ratio']}")
    for assertion_name, assertion_passed in summary["assertions"].items():
      value = 1 if assertion_passed else 0
      lines.append(
          f'wave_perf_assertion_status{{{_format_labels(summary, {"assertion": assertion_name})}}} {value}'
      )
    lines.append(f"wave_perf_last_run_timestamp_seconds{{{labels}}} {summary['run_timestamp']}")
  return "\n".join(lines) + "\n"


def build_metrics_response(results_dir: Path) -> tuple[str, str]:
  return "text/plain; version=0.0.4; charset=utf-8", render_metrics(load_summaries(results_dir))


def _metadata_from_args(args: argparse.Namespace) -> dict:
  return {
      "repo": args.repo,
      "branch": args.branch,
      "sha": args.sha,
      "workflow": args.workflow,
      "run_id": args.run_id,
      "run_attempt": args.run_attempt,
      "run_timestamp": args.run_timestamp,
  }


def _build_handler(results_dir: Path):
  class MetricsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
      if self.path != "/metrics":
        self.send_response(404)
        self.end_headers()
        return
      content_type, body = build_metrics_response(results_dir)
      payload = body.encode("utf-8")
      self.send_response(200)
      self.send_header("Content-Type", content_type)
      self.send_header("Content-Length", str(len(payload)))
      self.end_headers()
      self.wfile.write(payload)

    def log_message(self, format: str, *args):
      return

  return MetricsHandler


def _serve(args: argparse.Namespace) -> int:
  host, port = args.listen.split(":", 1)
  server = ThreadingHTTPServer((host, int(port)), _build_handler(Path(args.results_dir)))
  server.serve_forever()
  return 0


def _summarize(args: argparse.Namespace) -> int:
  summary = build_summary(
      simulation=args.simulation,
      report_dir=Path(args.report_dir),
      metadata=_metadata_from_args(args),
      exit_code=args.exit_code,
  )
  Path(args.summary_file).write_text(json.dumps(summary, sort_keys=True), encoding="utf-8")
  return 0


def main() -> int:
  parser = argparse.ArgumentParser()
  subparsers = parser.add_subparsers(dest="command", required=True)

  summarize_parser = subparsers.add_parser("summarize")
  summarize_parser.add_argument("--simulation", required=True)
  summarize_parser.add_argument("--report-dir", required=True)
  summarize_parser.add_argument("--summary-file", required=True)
  summarize_parser.add_argument("--exit-code", type=int, required=True)
  summarize_parser.add_argument("--repo", default="")
  summarize_parser.add_argument("--branch", default="")
  summarize_parser.add_argument("--sha", default="")
  summarize_parser.add_argument("--workflow", default="perf")
  summarize_parser.add_argument("--run-id", dest="run_id", default="")
  summarize_parser.add_argument("--run-attempt", dest="run_attempt", default="")
  summarize_parser.add_argument("--run-timestamp", dest="run_timestamp", type=int, default=int(time.time()))
  summarize_parser.set_defaults(handler=_summarize)

  serve_parser = subparsers.add_parser("serve")
  serve_parser.add_argument("--results-dir", required=True)
  serve_parser.add_argument("--listen", default="127.0.0.1:9464")
  serve_parser.set_defaults(handler=_serve)

  args = parser.parse_args()
  return args.handler(args)


if __name__ == "__main__":
  raise SystemExit(main())

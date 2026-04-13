#!/usr/bin/env python3
import argparse
import json
import re
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


def _match_int(pattern: str, text: str) -> int:
  match = re.search(pattern, text, re.MULTILINE)
  if not match:
    raise ValueError(f"Missing Gatling stat for pattern: {pattern}")
  return int(match.group(1))


def _match_float(pattern: str, text: str) -> float:
  match = re.search(pattern, text, re.MULTILINE)
  if not match:
    raise ValueError(f"Missing Gatling stat for pattern: {pattern}")
  return float(match.group(1))


def parse_gatling_output(output_text: str) -> dict:
  return {
      "requests": {
          "ok": _match_int(r"request count\s+\d+\s+\(OK=(\d+)\s+KO=\d+", output_text),
          "ko": _match_int(r"request count\s+\d+\s+\(OK=\d+\s+KO=(\d+)", output_text),
      },
      "timings_ms": {
          "mean": _match_int(r"mean response time\s+(\d+)", output_text),
          "max": _match_int(r"max response time\s+(\d+)", output_text),
          "p95": _match_int(r"response time 95th percentile\s+(\d+)", output_text),
          "p99": _match_int(r"response time 99th percentile\s+(\d+)", output_text),
      },
      "successful_requests_pct": _match_float(r"successful requests\s+([0-9.]+)%", output_text),
  }


def build_summary(simulation: str, output_text: str, metadata: dict, exit_code: int) -> dict:
  parsed = parse_gatling_output(output_text)
  requests = parsed["requests"]
  total_requests = requests["ok"] + requests["ko"]
  success_ratio = (requests["ok"] / total_requests) if total_requests else 0.0
  thresholds = SIMULATION_THRESHOLDS.get(simulation, {})
  assertions = {
      name: checker(parsed["timings_ms"], success_ratio)
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
      "timings_ms": parsed["timings_ms"],
      "success_ratio": success_ratio,
      "successful_requests_pct": parsed["successful_requests_pct"],
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
      "# HELP wave_perf_requests_count Wave perf request counts by outcome.",
      "# TYPE wave_perf_requests_count gauge",
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
    for status_name, request_count in summary["requests"].items():
      lines.append(f'wave_perf_requests_count{{{_format_labels(summary, {"status": status_name})}}} {request_count}')
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
  output_text = Path(args.output_file).read_text(encoding="utf-8")
  summary = build_summary(
      simulation=args.simulation,
      output_text=output_text,
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
  summarize_parser.add_argument("--output-file", required=True)
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

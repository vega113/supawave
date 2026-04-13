#!/usr/bin/env bash
set -euo pipefail

# wave-perf.sh -- orchestrate Gatling performance tests
#
# Usage:
#   ./scripts/wave-perf.sh                                          # build, start, seed, test, stop
#   WAVE_PERF_BASE_URL=http://localhost:9898 ./scripts/wave-perf.sh # skip build+start
#
# The script assumes it is run from the repository root.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT=9898
INSTALL_DIR="$REPO_ROOT/target/universal/stage"
RESULTS_DIR="$REPO_ROOT/wave/target/perf-results"
PID_FILE="$INSTALL_DIR/wave_server.pid"
PERF_REPO="${GITHUB_REPOSITORY:-incubator-wave}"
PERF_BRANCH="${GITHUB_REF_NAME:-$(git -C "$REPO_ROOT" branch --show-current 2>/dev/null || echo local)}"
PERF_SHA="${GITHUB_SHA:-$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo local)}"
PERF_WORKFLOW="${GITHUB_WORKFLOW:-perf}"
PERF_RUN_ID="${GITHUB_RUN_ID:-local}"
PERF_RUN_ATTEMPT="${GITHUB_RUN_ATTEMPT:-1}"

mkdir -p "$RESULTS_DIR"

# ------------------------------------------------------------------
# Helpers (same as wave-e2e.sh)
# ------------------------------------------------------------------

wait_for_healthz() {
  local url="${1:-http://localhost:$PORT}/healthz"
  local timeout="${2:-90}"
  echo "[perf] Waiting for $url (timeout=${timeout}s) ..."
  for i in $(seq 1 "$timeout"); do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then
      echo "[perf] Server healthy after ${i}s"
      return 0
    fi
    sleep 1
  done
  echo "[perf] ERROR: server did not become healthy within ${timeout}s" >&2
  return 1
}

start_server() {
  if curl -sf -o /dev/null "http://localhost:$PORT/healthz" 2>/dev/null; then
    echo "[perf] ERROR: a server is already healthy on port $PORT. Stop it first or set WAVE_PERF_BASE_URL." >&2
    return 1
  fi

  if [[ ! -x "$INSTALL_DIR/bin/wave" ]]; then
    echo "[perf] Distribution not found -- building with SBT ..."
    (cd "$REPO_ROOT" && sbt --batch Universal/stage)
  fi

  echo "[perf] Starting Wave server ..."
  cd "$INSTALL_DIR"
  nohup ./bin/wave > wave_server.out 2>&1 &
  local pid=$!
  echo "$pid" > "$PID_FILE"
  sleep 1
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[perf] ERROR: Wave server exited during startup. See $INSTALL_DIR/wave_server.out." >&2
    return 1
  fi
  cd "$REPO_ROOT"
  echo "[perf] Server PID=$pid"
}

stop_server() {
  echo "[perf] Stopping Wave server ..."
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid=$(cat "$PID_FILE")
    if [[ -n "$pid" ]]; then
      kill "$pid" 2>/dev/null || true
      for _i in $(seq 1 10); do
        if ! kill -0 "$pid" 2>/dev/null; then
          echo "[perf] Server stopped (PID=$pid)"
          return 0
        fi
        sleep 1
      done
      kill -9 "$pid" 2>/dev/null || true
      echo "[perf] Force-killed PID=$pid"
      return 0
    fi
  fi
  local pids
  pids=$(lsof -tiTCP:$PORT -sTCP:LISTEN 2>/dev/null || fuser $PORT/tcp 2>/dev/null || true)
  if [[ -n "${pids:-}" ]]; then
    echo "$pids" | xargs -I{} kill {} 2>/dev/null || true
    echo "[perf] Sent TERM to port $PORT listeners"
  fi
}

# ------------------------------------------------------------------
# Main
# ------------------------------------------------------------------

MANAGED_SERVER=false

if [[ -n "${WAVE_PERF_BASE_URL:-}" ]]; then
  echo "[perf] Using externally managed server at $WAVE_PERF_BASE_URL"
  BASE_URL="$WAVE_PERF_BASE_URL"
else
  MANAGED_SERVER=true
  BASE_URL="http://localhost:$PORT"
  start_server
  trap stop_server EXIT
  wait_for_healthz "$BASE_URL"
fi

# ------------------------------------------------------------------
# Seed test data (20 waves × 10 blips × ~500 chars)
# ------------------------------------------------------------------

echo "[perf] Seeding test data ..."
set +e
WAVE_PERF_BASE_URL="$BASE_URL" \
  sbt --batch 'GatlingTest / runMain org.waveprotocol.wave.perf.WaveDataSeeder 20 10' \
  2>&1 | tee "$RESULTS_DIR/seed-output.txt"
seed_code=${PIPESTATUS[0]}
set -e

if [[ "$seed_code" -ne 0 ]]; then
  echo "[perf] ERROR: data seeding failed (exit $seed_code)" >&2
  exit "$seed_code"
fi

# ------------------------------------------------------------------
# Run Gatling simulations
# ------------------------------------------------------------------

overall_exit=0

for sim in SearchLoadSimulation WaveOpenSimulation FullJourneySimulation; do
  echo ""
  echo "[perf] Running $sim ..."

  set +e
  WAVE_PERF_BASE_URL="$BASE_URL" \
    sbt --batch "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner $sim" \
    2>&1 | tee "$RESULTS_DIR/${sim}-output.txt"
  sim_code=${PIPESTATUS[0]}
  set -e

  summary_file="$RESULTS_DIR/${sim}-summary.json"
  set +e
  python3 scripts/perf_metrics_exporter.py summarize \
    --simulation "$sim" \
    --output-file "$RESULTS_DIR/${sim}-output.txt" \
    --summary-file "$summary_file" \
    --exit-code "$sim_code" \
    --repo "$PERF_REPO" \
    --branch "$PERF_BRANCH" \
    --sha "$PERF_SHA" \
    --workflow "$PERF_WORKFLOW" \
    --run-id "$PERF_RUN_ID" \
    --run-attempt "$PERF_RUN_ATTEMPT"
  summary_code=$?
  set -e
  if [[ "$summary_code" -ne 0 ]]; then
    echo "[perf] WARN: failed to generate summary file for $sim" >&2
  else
    echo "[perf] Summary: $summary_file"
  fi

  if [[ "$sim_code" -ne 0 ]]; then
    echo "[perf] WARN: $sim exited with code $sim_code"
    overall_exit=1
  else
    echo "[perf] $sim passed"
  fi
done

# Copy Gatling HTML reports to results dir
if compgen -G "target/gatling/*/index.html" > /dev/null 2>&1; then
  cp -r target/gatling "$RESULTS_DIR/gatling-reports"
  echo "[perf] Gatling HTML reports copied to $RESULTS_DIR/gatling-reports/"
fi

echo ""
echo "[perf] Tests finished with exit code $overall_exit"
exit "$overall_exit"

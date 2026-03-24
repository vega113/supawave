#!/usr/bin/env bash
set -euo pipefail

# wave-e2e.sh -- orchestrate the E2E test suite
#
# Usage:
#   ./scripts/wave-e2e.sh          # build (if needed), start server, run tests, stop
#   WAVE_E2E_BASE_URL=http://localhost:9898 ./scripts/wave-e2e.sh  # skip build+start
#
# The script assumes it is run from the repository root.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT=9898
INSTALL_DIR="$REPO_ROOT/wave/target/universal/stage"
RESULTS_DIR="$REPO_ROOT/wave/target/e2e-results"
E2E_DIR="$REPO_ROOT/wave/src/e2e-test"
PID_FILE="$INSTALL_DIR/wave_server.pid"

mkdir -p "$RESULTS_DIR"

# ------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------

wait_for_healthz() {
  local url="${1:-http://localhost:$PORT}/healthz"
  local timeout="${2:-90}"
  echo "[e2e] Waiting for $url (timeout=${timeout}s) ..."
  for i in $(seq 1 "$timeout"); do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then
      echo "[e2e] Server healthy after ${i}s"
      return 0
    fi
    sleep 1
  done
  echo "[e2e] ERROR: server did not become healthy within ${timeout}s" >&2
  return 1
}

start_server() {
  if curl -sf -o /dev/null "http://localhost:$PORT/healthz" 2>/dev/null; then
    echo "[e2e] ERROR: a server is already healthy on port $PORT. Stop it first or set WAVE_E2E_BASE_URL." >&2
    return 1
  fi

  if [[ ! -x "$INSTALL_DIR/bin/wave" ]]; then
    echo "[e2e] Distribution not found -- building with SBT ..."
    (cd "$REPO_ROOT" && sbt --batch Universal/stage)
  fi

  echo "[e2e] Starting Wave server ..."
  cd "$INSTALL_DIR"
  nohup ./bin/wave > wave_server.out 2>&1 &
  local pid=$!
  echo "$pid" > "$PID_FILE"
  sleep 1
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[e2e] ERROR: Wave server exited during startup. See $INSTALL_DIR/wave_server.out." >&2
    return 1
  fi
  cd "$REPO_ROOT"
  echo "[e2e] Server PID=$pid"
}

stop_server() {
  echo "[e2e] Stopping Wave server ..."
  # Primary: use PID file
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid=$(cat "$PID_FILE")
    if [[ -n "$pid" ]]; then
      kill "$pid" 2>/dev/null || true
      # Wait for process to exit
      for _i in $(seq 1 10); do
        if ! kill -0 "$pid" 2>/dev/null; then
          echo "[e2e] Server stopped (PID=$pid)"
          return 0
        fi
        sleep 1
      done
      # Force kill if still running
      kill -9 "$pid" 2>/dev/null || true
      echo "[e2e] Force-killed PID=$pid"
      return 0
    fi
  fi
  # Fallback: try lsof (macOS) or fuser (Linux)
  local pids
  pids=$(lsof -tiTCP:$PORT -sTCP:LISTEN 2>/dev/null || fuser $PORT/tcp 2>/dev/null || true)
  if [[ -n "${pids:-}" ]]; then
    echo "$pids" | xargs -I{} kill {} 2>/dev/null || true
    echo "[e2e] Sent TERM to port $PORT listeners"
  fi
}

# ------------------------------------------------------------------
# Main
# ------------------------------------------------------------------

MANAGED_SERVER=false

if [[ -n "${WAVE_E2E_BASE_URL:-}" ]]; then
  echo "[e2e] Using externally managed server at $WAVE_E2E_BASE_URL"
  BASE_URL="$WAVE_E2E_BASE_URL"
else
  MANAGED_SERVER=true
  BASE_URL="http://localhost:$PORT"
  start_server
  # Ensure we stop the server on exit regardless of test outcome
  trap stop_server EXIT
  wait_for_healthz "$BASE_URL"
fi

echo "[e2e] Running pytest against $BASE_URL ..."

PYTHON="${PYTHON:-python3}"

set +e
WAVE_E2E_BASE_URL="$BASE_URL" \
  "$PYTHON" -m pytest "$E2E_DIR" \
    -v \
    --tb=short \
    --junitxml="$RESULTS_DIR/e2e-junit.xml" \
    -o "console_output_style=classic" \
  2>&1 | tee "$RESULTS_DIR/e2e-output.txt"
exit_code=${PIPESTATUS[0]}
set -e

echo "[e2e] Tests finished with exit code $exit_code"
exit "$exit_code"

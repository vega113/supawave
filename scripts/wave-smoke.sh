#!/usr/bin/env bash
set -euo pipefail

# wave-smoke.sh - helper to start/stop/status/check the installed Wave server
# Usage:
#   ./scripts/wave-smoke.sh start   # starts server from install dir and waits for readiness
#   ./scripts/wave-smoke.sh status  # prints HTTP status of root endpoint
#   ./scripts/wave-smoke.sh check   # runs a few endpoint checks
#   ./scripts/wave-smoke.sh stop    # stops server listening on port 9898
#
# Notes:
# - Expects the distribution staged at target/universal/stage (SBT) or wave/build/install/wave (legacy)
# - On Java 17, the application is configured with --add-opens in SBT to support Guice/cglib
# - If a port conflict persists, you can forcefully clear Java processes (dangerous):
#     killall java

if [[ -d "target/universal/stage" ]]; then
  INSTALL_DIR="${INSTALL_DIR:-target/universal/stage}"
else
  INSTALL_DIR="${INSTALL_DIR:-wave/build/install/wave}"
fi
PID_FILE="$INSTALL_DIR/wave_server.pid"
PORT=9898

start() {
  if [[ ! -x "$INSTALL_DIR/bin/wave" ]]; then
    echo "Install dir not found: $INSTALL_DIR. Run: sbt Universal/stage" >&2
    exit 1
  fi
  (cd "$INSTALL_DIR" && nohup ./bin/wave > wave_server.out 2>&1 & echo $! > wave_server.pid)
  echo "Started. PID=$(cat "$PID_FILE")"
  wait_ready
}

wait_ready() {
  for i in {1..60}; do
    http_status=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
    if [[ "${http_status:-000}" != "000" ]]; then
      echo "PROBE_HTTP=$http_status"
    fi
    if [[ "${http_status:-0}" -ge 200 ]]; then
      echo "READY"
      return 0
    fi
    sleep 1
  done
  echo "Server did not become ready within timeout" >&2
  return 1
}

status() {
  http_status=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
  echo "HTTP_STATUS=${http_status:-000}"
}

check() {
  root_status=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
  webclient_status=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/webclient/webclient.nocache.js" || true)
  echo "ROOT_STATUS=${root_status:-000}"
  echo "WEBCLIENT_STATUS=${webclient_status:-000}"

  if [[ "${root_status}" -ne 200 && "${root_status}" -ne 302 ]]; then
    echo "Unexpected root status: ${root_status}" >&2
    return 1
  fi

  if [[ "${webclient_status}" -ne 200 ]]; then
    echo "Missing compiled webclient asset: /webclient/webclient.nocache.js" >&2
    return 1
  fi
}

stop() {
  # Collect all PIDs to kill (port listeners + PID file)
  all_pids=""

  # PIDs from port listener
  port_pids=$(lsof -tiTCP:$PORT -sTCP:LISTEN 2>/dev/null || true)
  if [[ -n "${port_pids:-}" ]]; then
    all_pids="$port_pids"
  fi

  # PID from PID file
  if [[ -f "$PID_FILE" ]]; then
    file_pid=$(cat "$PID_FILE")
    if [[ -n "${file_pid:-}" ]]; then
      all_pids="${all_pids:+$all_pids$'\n'}$file_pid"
    fi
  fi

  if [[ -z "${all_pids:-}" ]]; then
    echo "No running server detected on port $PORT"
    return 0
  fi

  # Deduplicate and send SIGTERM
  all_pids=$(echo "$all_pids" | sort -u)
  echo "$all_pids" | xargs -I{} kill {} 2>/dev/null || true

  # Wait up to 5 seconds for graceful shutdown
  for i in {1..5}; do
    sleep 1
    if ! lsof -tiTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; then
      echo "Stopped server on port $PORT"
      rm -f "$PID_FILE"
      return 0
    fi
  done

  # Force kill if still running
  echo "Graceful shutdown timed out; sending SIGKILL"
  echo "$all_pids" | xargs -I{} kill -9 {} 2>/dev/null || true
  sleep 1
  rm -f "$PID_FILE"
  echo "Force-stopped server on port $PORT"
}

cmd=${1:-}
case "$cmd" in
  start) start ;;
  status) status ;;
  check) check ;;
  stop) stop ;;
  *) echo "Usage: $0 {start|status|check|stop}" >&2; exit 1 ;;
 esac

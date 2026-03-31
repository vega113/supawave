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
# Hard timeout (seconds) for the stop command to prevent CI hangs
STOP_TIMEOUT=${STOP_TIMEOUT:-30}

# Find PIDs listening on $PORT using the best available tool.
# Tries lsof first, then ss+/proc (Linux), then fuser.
find_port_pids() {
  local pids=""

  # Method 1: lsof (macOS + most Linux)
  if command -v lsof >/dev/null 2>&1; then
    pids=$(lsof -tiTCP:$PORT -sTCP:LISTEN 2>/dev/null || true)
  fi

  # Method 2: ss + /proc (Linux, available on minimal Ubuntu where lsof may be absent)
  if [[ -z "${pids:-}" ]] && command -v ss >/dev/null 2>&1; then
    # ss -tlnp output contains "pid=NNN" fragments for listeners on our port
    pids=$(ss -tlnp "sport = :$PORT" 2>/dev/null \
      | grep -oP 'pid=\K[0-9]+' 2>/dev/null || true)
  fi

  # Method 3: fuser (Linux fallback)
  if [[ -z "${pids:-}" ]] && command -v fuser >/dev/null 2>&1; then
    pids=$(fuser $PORT/tcp 2>/dev/null | tr -s ' ' '\n' || true)
  fi

  echo "${pids:-}"
}

# Returns true (0) if something is listening on $PORT.
port_in_use() {
  local pids
  pids=$(find_port_pids)
  [[ -n "${pids:-}" ]]
}

start() {
  if [[ ! -x "$INSTALL_DIR/bin/wave" ]]; then
    echo "Install dir not found: $INSTALL_DIR. Run: sbt Universal/stage" >&2
    exit 1
  fi
  # Ensure port is free before starting (avoids collision with prior runs)
  if port_in_use; then
    echo "Port $PORT already in use — stopping stale server first" >&2
    stop
    sleep 1
  fi
  (cd "$INSTALL_DIR" && nohup ./bin/wave > wave_server.out 2>&1 & echo $! > wave_server.pid)
  echo "Started. Wrapper PID=$(cat "$PID_FILE" 2>/dev/null || echo unknown)"
  wait_ready

  # Re-capture the real Java PID via port detection. The sbt-native-packager
  # wrapper script may fork+exec the JVM, making the original $! stale.
  local real_pid
  real_pid=$(find_port_pids | head -1)
  if [[ -n "${real_pid:-}" ]]; then
    echo "$real_pid" > "$PID_FILE"
    echo "Resolved server PID=$real_pid (via port $PORT)"
  else
    echo "WARNING: could not resolve server PID via port detection" >&2
  fi
}

wait_ready() {
  local curl_log="${INSTALL_DIR}/curl_probe.log"
  for i in {1..60}; do
    # Use -s (silent) without -S to suppress expected connection-refused errors
    # during JVM startup. Redirect stderr to a log file so real networking
    # problems (DNS, loopback misconfiguration) remain diagnosable.
    http_status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" 2>>"$curl_log" || true)
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
  root_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
  echo "ROOT_STATUS=${root_status:-000}"

  if [[ "${root_status}" -ne 200 && "${root_status}" -ne 302 ]]; then
    echo "Unexpected root status: ${root_status}" >&2
    return 1
  fi

  # Health endpoint check
  health_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/healthz" || true)
  echo "HEALTH_STATUS=${health_status:-000}"
  if [[ "${health_status}" -ne 200 ]]; then
    echo "Unexpected health status: ${health_status}" >&2
    return 1
  fi

  webclient_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/webclient/webclient.nocache.js" || true)
  echo "WEBCLIENT_STATUS=${webclient_status:-000}"
  if [[ "${webclient_status}" -ne 200 ]]; then
    echo "Missing compiled webclient asset: /webclient/webclient.nocache.js" >&2
    return 1
  fi
}

stop() {
  local deadline=$(( $(date +%s) + STOP_TIMEOUT ))

  # Collect all PIDs to kill (port listeners + PID file)
  all_pids=""

  # PIDs from port listener (primary detection method)
  port_pids=$(find_port_pids)
  if [[ -n "${port_pids:-}" ]]; then
    all_pids="$port_pids"
  fi

  # PID from PID file (secondary)
  if [[ -f "$PID_FILE" ]]; then
    file_pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if [[ -n "${file_pid:-}" && "${file_pid:-}" =~ ^[0-9]+$ ]]; then
      all_pids="${all_pids:+$all_pids$'\n'}$file_pid"
    fi
  fi

  if [[ -z "${all_pids:-}" ]]; then
    echo "No running server detected on port $PORT"
    rm -f "$PID_FILE"
    return 0
  fi

  # Deduplicate and send SIGTERM
  all_pids=$(echo "$all_pids" | sort -u)
  echo "Sending SIGTERM to PIDs: $(echo $all_pids | tr '\n' ' ')"
  echo "$all_pids" | xargs -I{} kill {} 2>/dev/null || true

  # Wait for graceful shutdown (up to 5 seconds or until deadline)
  for i in {1..5}; do
    if [[ $(date +%s) -ge $deadline ]]; then
      echo "Hard timeout reached during graceful shutdown" >&2
      break
    fi
    sleep 1
    if ! port_in_use; then
      echo "Stopped server on port $PORT"
      rm -f "$PID_FILE"
      return 0
    fi
  done

  # Force kill: re-detect PIDs (they may have changed) and send SIGKILL
  echo "Graceful shutdown timed out; sending SIGKILL"
  fresh_pids=$(find_port_pids)
  # Merge with original PIDs in case port detection misses something
  kill_pids=$(printf '%s\n%s' "${fresh_pids:-}" "${all_pids:-}" | grep -v '^$' | sort -u)
  echo "$kill_pids" | xargs -I{} kill -9 {} 2>/dev/null || true

  # Wait briefly for SIGKILL to take effect
  for i in {1..3}; do
    if [[ $(date +%s) -ge $deadline ]]; then
      break
    fi
    sleep 1
    if ! port_in_use; then
      echo "Force-stopped server on port $PORT"
      rm -f "$PID_FILE"
      return 0
    fi
  done

  # Last resort: fuser -k (sends SIGKILL to anything on the port)
  if port_in_use; then
    echo "Attempting fuser -k as last resort"
    fuser -k $PORT/tcp 2>/dev/null || true
    sleep 1
  fi

  rm -f "$PID_FILE"
  if port_in_use; then
    echo "WARNING: port $PORT still in use after all stop attempts" >&2
    return 1
  fi
  echo "Stopped server on port $PORT"
}

cmd=${1:-}
case "$cmd" in
  start) start ;;
  status) status ;;
  check) check ;;
  stop) stop ;;
  *) echo "Usage: $0 {start|status|check|stop}" >&2; exit 1 ;;
esac

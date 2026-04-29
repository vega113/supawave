#!/usr/bin/env bash
set -euo pipefail

# wave-smoke.sh - helper to start/stop/status/check the installed Wave server
# Usage:
#   ./scripts/wave-smoke.sh start   # starts server from install dir and waits for readiness
#   ./scripts/wave-smoke.sh status  # prints HTTP status of root endpoint
#   ./scripts/wave-smoke.sh check   # runs a few endpoint checks
#   PORT=9899 ./scripts/wave-smoke.sh stop  # stops server listening on the selected port
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
PORT="${PORT:-9898}"
[[ "$PORT" =~ ^[0-9]+$ ]] || { echo "PORT must be numeric, got: $PORT" >&2; exit 1; }
(( PORT >= 1 && PORT <= 65535 )) || { echo "PORT must be between 1 and 65535, got: $PORT" >&2; exit 1; }
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

# Returns 0 if the process with the given PID was launched from our install
# directory (i.e. it is the Wave server we own), 1 otherwise.
# Checks both raw and canonicalized INSTALL_DIR with a trailing / boundary
# to prevent prefix collisions (e.g., /opt/wave vs /opt/wave-prod).
is_wave_process() {
  local pid=$1
  local cmdline canonical_dir
  # Single ps call to avoid TOCTOU between existence check and args fetch.
  # Empty result means the PID has already exited — treat as gone (not a conflict).
  cmdline=$(ps -p "$pid" -o args= 2>/dev/null || true)
  [[ -z "$cmdline" ]] && return 0
  canonical_dir=$(realpath "$INSTALL_DIR" 2>/dev/null || echo "$INSTALL_DIR")
  [[ "$cmdline" == *"${canonical_dir}/"* ]] || [[ "$cmdline" == *"${INSTALL_DIR}/"* ]]
}

# Sends SIGTERM then SIGKILL to a specific space-separated list of PIDs,
# waiting for the port to clear. Does not touch PID_FILE or call fuser.
stop_pids() {
  local pids_to_kill=$1
  local deadline=$(( $(date +%s) + STOP_TIMEOUT ))
  echo "Sending SIGTERM to PIDs: $pids_to_kill" >&2
  printf '%s\n' $pids_to_kill | xargs -I{} kill {} 2>/dev/null || true
  for i in {1..5}; do
    sleep 1
    port_in_use || return 0
    [[ $(date +%s) -lt $deadline ]] || break
  done
  echo "Sending SIGKILL to PIDs: $pids_to_kill" >&2
  printf '%s\n' $pids_to_kill | xargs -I{} kill -9 {} 2>/dev/null || true
  sleep 1
}

# Ensures $PORT is free before launch. Stops any stale Wave server found on the
# port (up to 3 attempts). Returns 0 when the port is clear; returns 1 if a
# non-Wave process owns the port or the port remains occupied after retries.
ensure_port_free() {
  local attempt pids pid non_wave_pids
  for attempt in 1 2 3; do
    if ! port_in_use; then
      return 0
    fi
    pids=$(find_port_pids)
    non_wave_pids=""
    for pid in $pids; do
      if ! is_wave_process "$pid"; then
        non_wave_pids="${non_wave_pids:+$non_wave_pids }$pid"
      fi
    done
    if [[ -n "${non_wave_pids:-}" ]]; then
      echo "Port $PORT in use by non-Wave process (PIDs: $non_wave_pids) — aborting" >&2
      echo "Stop the conflicting process or use a different PORT" >&2
      return 1
    fi
    echo "Port $PORT in use by stale Wave process — stopping (attempt $attempt)" >&2
    stop_pids "$pids" || true
    sleep 1
  done
  if port_in_use; then
    echo "Port $PORT still in use after 3 stop attempts — aborting" >&2
    return 1
  fi
  return 0
}

start() {
  if [[ ! -x "$INSTALL_DIR/bin/wave" ]]; then
    echo "Install dir not found: $INSTALL_DIR. Run: sbt Universal/stage" >&2
    exit 1
  fi
  ensure_port_free || exit 1
  (
    cd "$INSTALL_DIR"
    nohup ./bin/wave > wave_server.out 2>&1 < /dev/null &
    wrapper_pid=$!
    echo "$wrapper_pid" > wave_server.pid
    # Best-effort disown of the current background job; nohup + redirected
    # stdio already prevents the server from receiving SIGHUP.
    disown 2>/dev/null || true
  )
  echo "Started. Wrapper PID=$(cat "$PID_FILE" 2>/dev/null || echo unknown)"
  wait_ready

  # Re-capture the real Java PID via port detection. The sbt-native-packager
  # wrapper script may fork+exec the JVM, making the original $! stale.
  local real_pid="" candidate_pid
  for candidate_pid in $(find_port_pids | sort -u); do
    if is_wave_process "$candidate_pid"; then
      real_pid="$candidate_pid"
      break
    fi
  done
  if [[ -n "${real_pid:-}" ]]; then
    echo "$real_pid" > "$PID_FILE"
    echo "Resolved server PID=$real_pid (via port $PORT)"
  else
    echo "WARNING: could not resolve server PID via port detection" >&2
  fi
}

wait_ready() {
  local curl_log="${INSTALL_DIR}/curl_probe.log"
  # Truncate log from prior runs so it doesn't grow unbounded
  : > "$curl_log"
  for i in {1..60}; do
    # Use -s (silent) without -S to suppress expected connection-refused errors
    # during JVM startup. Redirect stderr to a log file so real networking
    # problems (DNS, loopback misconfiguration) remain diagnosable.
    # --connect-timeout and --max-time prevent curl from stalling indefinitely.
    # Use -sS (silent + show-error) so curl writes errors to stderr for the log.
    http_status=$(curl --connect-timeout 2 --max-time 5 -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" 2>>"$curl_log" || true)
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
  if [[ -s "$curl_log" ]]; then
    echo "--- Curl probe log ---" >&2
    tail -n 20 "$curl_log" >&2
  fi
  return 1
}

status() {
  http_status=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
  echo "HTTP_STATUS=${http_status:-000}"
}

check() {
  local j2cl_root_body_file j2cl_root_body legacy_status
  local root_gwt_presence j2cl_root_shell_presence
  local gwt_view_body_file gwt_view_body gwt_view_status

  # G-PORT-2 (#1111): bare "/" no longer renders the GWT bootstrap by
  # default (V-1/V-5 swapped the unauthenticated root to the J2CL
  # signed-out shell). Fetch the explicit GWT view to assert the
  # legacy bootstrap is still reachable.
  gwt_view_body_file=$(mktemp)
  gwt_view_status=$(curl -sS --max-time 10 -o "$gwt_view_body_file" -w "%{http_code}" "http://localhost:$PORT/?view=gwt" || true)
  gwt_view_body=$(cat "$gwt_view_body_file" 2>/dev/null || true)
  rm -f "$gwt_view_body_file"
  root_gwt_presence=$([[ "$gwt_view_body" == *'webclient/webclient.nocache.js'* ]] && echo present || echo missing)

  root_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || true)
  echo "ROOT_STATUS=${root_status:-000}"
  echo "GWT_VIEW_STATUS=${gwt_view_status:-000}"
  echo "ROOT_GWT=${root_gwt_presence}"

  if [[ "${root_status}" -ne 200 ]]; then
    echo "Unexpected root status: ${root_status}" >&2
    return 1
  fi

  if [[ "${gwt_view_status}" -ne 200 ]]; then
    echo "Unexpected /?view=gwt status: ${gwt_view_status}" >&2
    return 1
  fi

  if ! grep -Fq 'webclient/webclient.nocache.js' <<<"$gwt_view_body"; then
    echo "/?view=gwt did not render the legacy GWT bootstrap asset" >&2
    return 1
  fi

  # Health endpoint check
  health_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/healthz" || true)
  echo "HEALTH_STATUS=${health_status:-000}"
  if [[ "${health_status}" -ne 200 ]]; then
    echo "Unexpected health status: ${health_status}" >&2
    return 1
  fi

  landing_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/?view=landing" || true)
  echo "LANDING_STATUS=${landing_status:-000}"
  if [[ "${landing_status}" -ne 200 ]]; then
    echo "Unexpected landing status: ${landing_status}" >&2
    return 1
  fi

  j2cl_root_body_file=$(mktemp)
  j2cl_root_status=$(curl -sS --max-time 10 -o "$j2cl_root_body_file" -w "%{http_code}" "http://localhost:$PORT/?view=j2cl-root" || true)
  j2cl_root_body=$(cat "$j2cl_root_body_file" 2>/dev/null || true)
  rm -f "$j2cl_root_body_file"
  j2cl_root_shell_presence=$([[ "$j2cl_root_body" == *'data-j2cl-root-shell'* ]] && echo present || echo missing)
  echo "J2CL_ROOT_STATUS=${j2cl_root_status:-000}"
  echo "J2CL_ROOT_SHELL=${j2cl_root_shell_presence}"
  if [[ "${j2cl_root_status}" -ne 200 ]]; then
    echo "Unexpected J2CL root status: ${j2cl_root_status}" >&2
    return 1
  fi

  if ! grep -Fq 'data-j2cl-root-shell' <<<"$j2cl_root_body"; then
    echo "Diagnostic J2CL root route did not render the shell marker" >&2
    return 1
  fi

  if grep -Fq 'webclient/webclient.nocache.js' <<<"$j2cl_root_body"; then
    echo "J2CL root should not include the legacy GWT bootstrap asset" >&2
    return 1
  fi

  j2cl_index_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/j2cl/index.html" || true)
  echo "J2CL_INDEX_STATUS=${j2cl_index_status:-000}"
  if [[ "${j2cl_index_status}" -ne 200 ]]; then
    echo "Missing production J2CL asset: /j2cl/index.html" >&2
    return 1
  fi

  sidecar_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/j2cl-search/sidecar/j2cl-sidecar.js" || true)
  echo "SIDECAR_STATUS=${sidecar_status:-000}"
  if [[ "${sidecar_status}" -ne 200 ]]; then
    echo "Missing compiled J2CL sidecar asset: /j2cl-search/sidecar/j2cl-sidecar.js" >&2
    return 1
  fi

  legacy_status=$(curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "http://localhost:$PORT/webclient/webclient.nocache.js" || true)
  echo "WEBCLIENT_STATUS=${legacy_status:-000}"
  if [[ "${legacy_status}" -ne 200 ]]; then
    echo "Missing coexistence asset: /webclient/webclient.nocache.js (status=${legacy_status})" >&2
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

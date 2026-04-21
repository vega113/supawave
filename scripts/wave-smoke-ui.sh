#!/usr/bin/env bash
set -euo pipefail

# wave-smoke-ui.sh — build (if needed), run briefly, and probe UI endpoints
# - Starts :wave:run in background
# - Waits for HTTP 200 from root, verifies the default legacy GWT bootstrap, and
#   checks both maintained J2CL assets and the rollback-ready /webclient asset
# - Tails logs on failure; always cleans up the background process

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PORT=9898
RUN_OUT="$ROOT_DIR/run.ui.out"
PID_FILE="$ROOT_DIR/sbt.ui.pid"

cleanup() {
  ( pkill -f "org.waveprotocol.box.server.ServerMain" >/dev/null 2>&1 || true )
  ( [[ -f "$PID_FILE" ]] && kill $(cat "$PID_FILE") >/dev/null 2>&1 ) || true
}
trap cleanup EXIT

rm -f "$RUN_OUT" "$PID_FILE" || true

if command -v sbt >/dev/null 2>&1; then
  ( cd "$ROOT_DIR" && sbt run > "$RUN_OUT" 2>&1 & echo $! > "$PID_FILE" )
else
  echo "sbt not found — install SBT 1.10+ to run smoke tests" >&2; exit 1
fi

# Wait up to 90s for server to start
for i in {1..90}; do
  if grep -Eq "Started ServerConnector|jetty-9" "$RUN_OUT"; then break; fi
  sleep 1
done

# Probe endpoints
root_body_file=$(mktemp)
root_status=$(curl -sS --max-time 10 -o "$root_body_file" -w "%{http_code}" http://127.0.0.1:$PORT/ || true)
root_body=$(cat "$root_body_file" 2>/dev/null || true)
rm -f "$root_body_file"
root_gwt_presence=$([[ "$root_body" == *'webclient/webclient.nocache.js'* ]] && echo present || echo missing)
landing_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/?view=landing || true)
j2cl_root_body_file=$(mktemp)
j2cl_root_status=$(curl -sS --max-time 10 -o "$j2cl_root_body_file" -w "%{http_code}" http://127.0.0.1:$PORT/?view=j2cl-root || true)
j2cl_root_body=$(cat "$j2cl_root_body_file" 2>/dev/null || true)
rm -f "$j2cl_root_body_file"
j2cl_root_shell_presence=$([[ "$j2cl_root_body" == *'data-j2cl-root-shell'* ]] && echo present || echo missing)
j2cl_index_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/j2cl/index.html || true)
sidecar_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/j2cl-search/sidecar/j2cl-sidecar.js || true)
legacy_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/webclient/webclient.nocache.js || true)

echo "ROOT=$root_status ROOT_GWT=$root_gwt_presence ROOT_SHELL=$root_gwt_presence LANDING=$landing_status J2CL_ROOT=$j2cl_root_status J2CL_ROOT_SHELL=$j2cl_root_shell_presence J2CL_INDEX=$j2cl_index_status SIDECAR=$sidecar_status WEBCLIENT=$legacy_status"

if [[ "${root_status}" == "000" ]]; then
  echo "Server did not start or port not reachable" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

# Require the legacy GWT bootstrap on the default root and the J2CL root shell on
# the explicit diagnostic route.
if [[ "$root_status" -ne 200 ]]; then
  echo "Unexpected root status: $root_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$root_body" != *'webclient/webclient.nocache.js'* ]]; then
  echo "Root page did not render the legacy GWT bootstrap asset" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$root_body" == *'data-j2cl-root-shell'* ]]; then
  echo "Root page unexpectedly rendered the J2CL shell in default GWT mode" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$landing_status" -ne 200 ]]; then
  echo "Unexpected landing status: $landing_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$j2cl_root_status" -ne 200 ]]; then
  echo "Unexpected J2CL root status: $j2cl_root_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$j2cl_root_body" != *'data-j2cl-root-shell'* ]]; then
  echo "Explicit J2CL root route did not render the shell marker" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$j2cl_index_status" -ne 200 ]]; then
  echo "Missing production /j2cl/index.html asset: $j2cl_index_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$sidecar_status" -ne 200 ]]; then
  echo "Missing compiled /j2cl-search/sidecar/j2cl-sidecar.js asset: $sidecar_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$legacy_status" -ne 200 ]]; then
  echo "Missing coexistence /webclient/webclient.nocache.js asset: $legacy_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

echo "UI smoke OK"

#!/usr/bin/env bash
set -euo pipefail

# wave-smoke-ui.sh — build (if needed), run briefly, and probe UI endpoints
# - Starts :wave:run in background
# - Waits for HTTP 200/302 from root and presence of webclient assets
# - Tails logs on failure; always cleans up the background process

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PORT=9898
RUN_OUT="$ROOT_DIR/run.ui.out"
PID_FILE="$ROOT_DIR/gradle.ui.pid"

cleanup() {
  ( pkill -f "org.waveprotocol.box.server.ServerMain" >/dev/null 2>&1 || true )
  ( [[ -f "$PID_FILE" ]] && kill $(cat "$PID_FILE") >/dev/null 2>&1 ) || true
}
trap cleanup EXIT

rm -f "$RUN_OUT" "$PID_FILE" || true

( cd "$ROOT_DIR" && ./gradlew :wave:run > "$RUN_OUT" 2>&1 & echo $! > "$PID_FILE" )

# Wait up to 90s for server to start
for i in {1..90}; do
  if grep -Eq "Started ServerConnector|jetty-9" "$RUN_OUT"; then break; fi
  sleep 1
done

# Probe endpoints
root_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/ || true)
webclient_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:$PORT/webclient/ || true)

echo "ROOT=$root_status WEBCLIENT=$webclient_status"

if [[ "${root_status}" == "000" ]]; then
  echo "Server did not start or port not reachable" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

# Accept 200 or 302 from root (signin redirect) and 200 from webclient dir listing or index
if [[ "$root_status" -ne 200 && "$root_status" -ne 302 ]]; then
  echo "Unexpected root status: $root_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

if [[ "$webclient_status" -ne 200 && "$webclient_status" -ne 302 && "$webclient_status" -ne 403 ]]; then
  echo "Unexpected /webclient/ status: $webclient_status" >&2
  tail -n 200 "$RUN_OUT" || true
  exit 1
fi

echo "UI smoke OK"

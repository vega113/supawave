#!/usr/bin/env bash
# Start the gpt-bot example robot with a Cloudflare tunnel.
# Usage:  scripts/gptbot-start.sh
# Stop:   scripts/gptbot-stop.sh  (or kill the process group)
#
# Requires: sbt (via sdkman or PATH), cloudflared, curl
# Secrets:  source your .env first, or set GPTBOT_MANAGEMENT_TOKEN
set -euo pipefail
cd "$(dirname "$0")/.."

# ── defaults ──────────────────────────────────────────────────────────
export GPTBOT_ROBOT_NAME="${GPTBOT_ROBOT_NAME:-gpt-bot}"
export GPTBOT_PARTICIPANT_ID="${GPTBOT_PARTICIPANT_ID:-gpt-bot@supawave.ai}"
export GPTBOT_LISTEN_PORT="${GPTBOT_LISTEN_PORT:-8087}"
export GPTBOT_REPLY_MODE="${GPTBOT_REPLY_MODE:-passive}"
export GPTBOT_CONTEXT_MODE="${GPTBOT_CONTEXT_MODE:-none}"
export GPTBOT_CODEX_ENGINE="${GPTBOT_CODEX_ENGINE:-echo}"
export SUPAWAVE_BASE_URL="${SUPAWAVE_BASE_URL:-https://supawave.ai}"

PID_DIR="/tmp/gptbot"
mkdir -p "$PID_DIR"

matches_command() {
  local pid="$1"
  local needle="$2"
  local command
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  [[ -n "$command" && "$command" == *"$needle"* ]]
}

terminate_pid() {
  local pid="$1"
  local needle="$2"
  local label="$3"

  if [[ ! "$pid" =~ ^[0-9]+$ ]]; then
    return 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  if ! matches_command "$pid" "$needle"; then
    echo "Skipping $label pid $pid; command no longer matches $needle" >&2
    return 0
  fi

  kill "$pid" 2>/dev/null || return 0
  for _ in $(seq 1 5); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "Stopped $label"
      return 0
    fi
    sleep 1
  done

  if kill -0 "$pid" 2>/dev/null && matches_command "$pid" "$needle"; then
    kill -9 "$pid" 2>/dev/null && echo "Force-stopped $label"
  fi
}

terminate_pid_file() {
  local pid_file="$1"
  local needle="$2"
  local label="$3"
  local pid

  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]]; then
    terminate_pid "$pid" "$needle" "$label"
  fi
}

report_update_failure() {
  local message="$1"
  local response_file="$2"
  echo "$message" >&2
  if [[ -s "$response_file" ]]; then
    echo "Response body:" >&2
    cat "$response_file" >&2
  fi
  rm -f "$response_file"
  exit 1
}

cleanup() {
  echo ""
  echo "Shutting down..."
  terminate_pid_file "$PID_DIR/cloudflared.pid" "cloudflared" "tunnel"
  terminate_pid_file "$PID_DIR/sbt.pid" "org.waveprotocol.examples.robots.gptbot.GptBotServer" "bot"
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    terminate_pid "$pid" "org.waveprotocol.examples.robots.gptbot.GptBotServer" "port listener"
  done < <(lsof -ti:"$GPTBOT_LISTEN_PORT" 2>/dev/null || true)
  rm -f "$PID_DIR"/*.pid
  echo "Done."
}
trap cleanup EXIT INT TERM

# ── resolve tools ─────────────────────────────────────────────────────
SBT="sbt"
if ! command -v sbt >/dev/null 2>&1; then
  if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    # shellcheck disable=SC1091
    source "$HOME/.sdkman/bin/sdkman-init.sh"
  fi
fi
SBT="$(command -v sbt)"
[[ -n "$SBT" ]] || { echo "sbt not found"; exit 1; }

CLOUDFLARED="cloudflared"
if ! command -v cloudflared >/dev/null 2>&1; then
  if [[ -x /opt/homebrew/bin/cloudflared ]]; then
    CLOUDFLARED=/opt/homebrew/bin/cloudflared
  else
    echo "cloudflared not found"; exit 1
  fi
fi

# ── kill leftovers on the listen port ─────────────────────────────────
while IFS= read -r pid; do
  [[ -n "$pid" ]] || continue
  terminate_pid "$pid" "org.waveprotocol.examples.robots.gptbot.GptBotServer" "port listener"
done < <(lsof -ti:"$GPTBOT_LISTEN_PORT" 2>/dev/null || true)
sleep 1

# ── start bot ─────────────────────────────────────────────────────────
echo "Starting gpt-bot (engine=$GPTBOT_CODEX_ENGINE, port=$GPTBOT_LISTEN_PORT)..."
"$SBT" "runMain org.waveprotocol.examples.robots.gptbot.GptBotServer" > "$PID_DIR/bot.log" 2>&1 &
BOT_PID=$!
echo "$BOT_PID" > "$PID_DIR/sbt.pid"

# wait for health
echo -n "Waiting for bot"
for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:$GPTBOT_LISTEN_PORT/healthz" >/dev/null 2>&1; then
    echo " ready!"
    break
  fi
  echo -n "."
  sleep 2
done
if ! curl -sf "http://127.0.0.1:$GPTBOT_LISTEN_PORT/healthz" >/dev/null 2>&1; then
  echo " FAILED (check $PID_DIR/bot.log)"
  exit 1
fi

# ── start tunnel ──────────────────────────────────────────────────────
echo "Starting Cloudflare tunnel..."
"$CLOUDFLARED" tunnel --url "http://127.0.0.1:$GPTBOT_LISTEN_PORT" > "$PID_DIR/tunnel.log" 2>&1 &
CF_PID=$!
echo "$CF_PID" > "$PID_DIR/cloudflared.pid"

echo -n "Waiting for tunnel URL"
TUNNEL_URL=""
for i in $(seq 1 20); do
  TUNNEL_URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$PID_DIR/tunnel.log" 2>/dev/null | head -1)
  if [[ -n "$TUNNEL_URL" ]]; then
    echo " got it!"
    break
  fi
  echo -n "."
  sleep 1
done
if [[ -z "$TUNNEL_URL" ]]; then
  echo " FAILED (check $PID_DIR/tunnel.log)"
  exit 1
fi

# ── update prod URL if token is available ─────────────────────────────
if [[ -n "${GPTBOT_MANAGEMENT_TOKEN:-}" ]]; then
  echo "Updating callback URL on $SUPAWAVE_BASE_URL..."
  update_response_file="$(mktemp "$PID_DIR/update-response.XXXXXX")"
  set +e
  update_http_code="$(
    curl -sS -o "$update_response_file" -w '%{http_code}' -X PUT \
      "$SUPAWAVE_BASE_URL/api/robots/$GPTBOT_PARTICIPANT_ID/url" \
      -H "Authorization: Bearer $GPTBOT_MANAGEMENT_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"url\": \"$TUNNEL_URL\"}"
  )"
  update_curl_status=$?
  set -e
  if [[ $update_curl_status -ne 0 ]]; then
    report_update_failure "Failed to update callback URL (curl exit $update_curl_status)." \
      "$update_response_file"
  fi
  if [[ ${update_http_code:0:1} != 2 ]]; then
    report_update_failure "Failed to update callback URL (HTTP $update_http_code)." \
      "$update_response_file"
  fi
  python3 -c "import json,sys; d=json.load(sys.stdin); print(f'  callback: {d.get(\"callbackUrl\",\"?\")}')" \
    < "$update_response_file"
  rm -f "$update_response_file"
fi

# ── summary ───────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════"
echo "  gpt-bot is running"
echo "  Local:   http://127.0.0.1:$GPTBOT_LISTEN_PORT"
echo "  Tunnel:  $TUNNEL_URL"
echo "  Health:  $TUNNEL_URL/healthz"
echo "  Caps:    $TUNNEL_URL/_wave/capabilities.xml"
echo "  Logs:    $PID_DIR/bot.log"
echo "════════════════════════════════════════════════════"
echo "Press Ctrl+C to stop."
echo ""

# ── tail logs until killed ────────────────────────────────────────────
tail -f "$PID_DIR/bot.log" 2>/dev/null || wait "$BOT_PID"

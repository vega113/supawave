#!/usr/bin/env bash
# Stop the gpt-bot and its Cloudflare tunnel.
set -euo pipefail

PID_DIR="/tmp/gptbot"
PORT="${GPTBOT_LISTEN_PORT:-8087}"

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

terminate_pid_file "$PID_DIR/cloudflared.pid" "cloudflared" "tunnel"
terminate_pid_file "$PID_DIR/sbt.pid" "org.waveprotocol.examples.robots.gptbot.GptBotServer" "bot"

while IFS= read -r pid; do
  [[ -n "$pid" ]] || continue
  terminate_pid "$pid" "org.waveprotocol.examples.robots.gptbot.GptBotServer" "port listener"
done < <(lsof -ti:"$PORT" 2>/dev/null || true)

rm -f "$PID_DIR"/*.pid
echo "gpt-bot stopped."

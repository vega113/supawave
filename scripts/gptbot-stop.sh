#!/usr/bin/env bash
# Stop the gpt-bot and its Cloudflare tunnel.
set -euo pipefail

PID_DIR="/tmp/gptbot"
PORT="${GPTBOT_LISTEN_PORT:-8087}"

kill "$(cat "$PID_DIR/cloudflared.pid" 2>/dev/null)" 2>/dev/null && echo "Stopped tunnel" || true
kill "$(cat "$PID_DIR/sbt.pid" 2>/dev/null)" 2>/dev/null && echo "Stopped bot" || true
lsof -ti:"$PORT" | xargs kill -9 2>/dev/null || true
rm -f "$PID_DIR"/*.pid
echo "gpt-bot stopped."

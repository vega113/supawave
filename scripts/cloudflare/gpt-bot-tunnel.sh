#!/usr/bin/env bash
set -euo pipefail

listen_port="${GPTBOT_LISTEN_PORT:-8087}"
local_url="http://127.0.0.1:${listen_port}"
hostname="${GPTBOT_TUNNEL_HOSTNAME:-}"

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "cloudflared is required" >&2
  exit 1
fi

echo "Forwarding ${local_url}"
echo "Callback path: /_wave/robot/jsonrpc"

if [[ -n "${hostname}" ]]; then
  echo "Named tunnel hostname: ${hostname}"
  exec cloudflared tunnel --url "${local_url}" --hostname "${hostname}"
fi

exec cloudflared tunnel --url "${local_url}"

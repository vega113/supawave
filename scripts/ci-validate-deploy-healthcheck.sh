#!/usr/bin/env bash
set -euo pipefail

compose_file="deploy/caddy/compose.yml"
runtime_dockerfiles=("Dockerfile.prebuilt" "Dockerfile")

echo "[guard] Validating deploy healthcheck/runtime compatibility"

if ! rg -q 'curl -sf http://localhost:[0-9]+/healthz' "$compose_file"; then
  echo "[guard] INFO: wave healthcheck no longer depends on curl; nothing to validate here."
  exit 0
fi

for dockerfile in "${runtime_dockerfiles[@]}"; do
  if ! rg -q 'apt-get install .*curl' "$dockerfile"; then
    echo "[guard] ERROR: $compose_file uses curl healthcheck, but $dockerfile does not install curl." >&2
    exit 1
  fi
done

echo "[guard] OK: runtime Dockerfiles install curl required by wave healthcheck."

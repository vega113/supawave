#!/usr/bin/env bash
set -euo pipefail

echo "[guard] Checking for experimental flags enabled in config (release mode)"

matches=$(rg -n --pretty --glob '!**/build/**' -e "^\s*experimental\.(native_servlet_registration|enable_programmatic_poc)\s*:\s*true\b" wave/config || true)

if [[ -n "${matches}" ]]; then
  echo "[guard] ERROR: Experimental flags enabled in committed config files:" >&2
  echo "${matches}" >&2
  echo "[guard] Set these to 'false' before tagging a release." >&2
  exit 1
fi

echo "[guard] OK: Experimental flags are not enabled in committed configs."

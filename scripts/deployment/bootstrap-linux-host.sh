#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=1
fi

run() {
  if [[ "$DRY_RUN" -eq 1 ]]; then
    printf '[dry-run] %s\n' "$*"
    return 0
  fi
  "$@"
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

install_pkg() {
  local pkg="$1"
  if dpkg -s "$pkg" >/dev/null 2>&1; then
    printf 'already installed: %s\n' "$pkg"
    return 0
  fi
  run sudo apt-get install -y "$pkg"
}

main() {
  if ! need_cmd apt-get; then
    echo 'This bootstrap script currently targets Ubuntu/Debian-style package management.' >&2
    exit 1
  fi

  run sudo apt-get update
  install_pkg curl
  install_pkg tar
  install_pkg openssl
  install_pkg openjdk-17-jre-headless

  if need_cmd docker; then
    echo 'docker already installed'
    if docker compose version >/dev/null 2>&1; then
      echo 'docker compose already available'
    else
      echo 'docker compose plugin is not available; install it before using the Docker/Caddy deployment path.'
    fi
  else
    echo 'docker is not installed; install Docker Engine before using the Docker/Caddy deployment path.'
  fi
}

main "$@"

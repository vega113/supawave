#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

cmd=${1:-deploy}
script_dir=$(cd "$(dirname "$0")" && pwd)
release_dir="$script_dir"
deploy_root=$(dirname "$(dirname "$release_dir")")
project_name=${PROJECT_NAME:-supawave}
canonical_host=${CANONICAL_HOST:-supawave.ai}
root_host=${ROOT_HOST:-wave.supawave.ai}
www_host=${WWW_HOST:-www.supawave.ai}
internal_port=${WAVE_INTERNAL_PORT:-9898}
smoke_image=${SMOKE_IMAGE:-curlimages/curl:8.10.1}
registry_host=${GHCR_REGISTRY_HOST:-ghcr.io}
deploy_env_file="$deploy_root/shared/deploy.env"
lock_file="$deploy_root/deploy.lock"

require_docker() {
  command -v docker >/dev/null 2>&1 || {
    echo "docker is required on the Linux host" >&2
    exit 1
  }
  docker compose version >/dev/null 2>&1 || {
    echo "docker compose plugin is required on the Linux host" >&2
    exit 1
  }
}

ensure_layout() {
  mkdir -p "$deploy_root"/incoming
  mkdir -p "$deploy_root"/releases
  mkdir -p "$deploy_root"/shared/{accounts,attachments,caddy-config,caddy-data,certificates,deltas,indexes,logs,mongo/db,sessions}
}

load_deploy_env() {
  if [[ -f "$deploy_env_file" ]]; then
    set -a
    source "$deploy_env_file"
    set +a
  fi
}

acquire_lock() {
  exec 9>"$lock_file"
  if ! flock -w 30 9; then
    echo "Could not acquire deployment lock at $lock_file" >&2
    exit 1
  fi
}

release_lock() {
  flock -u 9 || true
}

retry() {
  local n=0
  local max=3
  local delay=15
  until "$@"; do
    n=$((n+1))
    if [ $n -ge $max ]; then
      echo "[deploy] Command failed after $max attempts: $*" >&2
      return 1
    fi
    echo "[deploy] Attempt $n failed, retrying in ${delay}s..." >&2
    sleep $delay
  done
}

activate_release() {
  ln -sfn "$release_dir" "$deploy_root/current"
}

remember_previous_release() {
  if [[ -L "$deploy_root/current" ]]; then
    ln -sfn "$(readlink -f "$deploy_root/current")" "$deploy_root/previous"
    # Remember the image currently running so rollback can restore it
    local current_image
    current_image=$(docker inspect --format='{{.Config.Image}}' "${project_name}-wave-1" 2>/dev/null || true)
    if [[ -n "$current_image" ]]; then
      printf '%s' "$current_image" > "$deploy_root/previous_image"
    fi
  fi
}

login_registry_if_needed() {
  if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    printf '%s' "$GHCR_TOKEN" | docker login "$registry_host" -u "$GHCR_USERNAME" --password-stdin >/dev/null
  fi
}

pull_image() {
  local image_ref="${WAVE_IMAGE:-supawave-wave:$(basename "$release_dir")}"
  # Flush DNS cache to clear any stale entries before pulling
  systemd-resolve --flush-caches 2>/dev/null || true
  # Retry docker pull up to 3 times with 15s backoff to handle transient DNS failures
  retry docker pull "$image_ref" >/dev/null
}

render_application_config() {
  local app_config="$release_dir/application.conf"
  if [[ -f "$app_config" ]]; then
    perl -0pi -e 's/wave\.example\.test/'"$canonical_host"'/g' "$app_config"
  fi
}

_do_compose_up() {
  # --wait blocks until every service with a healthcheck reports healthy.
  # Combined with the wave service's deploy.update_config.order=start-first,
  # Docker Compose will start the new container, wait for it to become healthy,
  # and only then stop the old one — achieving zero-downtime rolling updates.
  DEPLOY_ROOT="$deploy_root" \
  WAVE_IMAGE="${WAVE_IMAGE:-supawave-wave:$(basename "$release_dir")}" \
  WAVE_SERVER_VERSION="${WAVE_SERVER_VERSION:-$(basename "$release_dir")}" \
  CANONICAL_HOST="$canonical_host" \
  ROOT_HOST="$root_host" \
  WWW_HOST="$www_host" \
  WAVE_INTERNAL_PORT="$internal_port" \
  RESEND_API_KEY="${RESEND_API_KEY:-}" \
  WAVE_EMAIL_FROM="${WAVE_EMAIL_FROM:-noreply@${canonical_host}}" \
  WAVE_MAIL_PROVIDER="${WAVE_MAIL_PROVIDER:-logging}" \
    docker compose --project-name "$project_name" -f "$release_dir/compose.yml" up -d --remove-orphans --wait --wait-timeout 420
}

compose_up() {
  # Retry is used to handle transient DNS failures when pulling images
  retry _do_compose_up
}

check_readyz() {
  # Wave no longer binds to a host port; reach it through the compose network.
  docker run --rm --network "${project_name}_default" "$smoke_image" -fsSI --max-time 5 "http://wave:${internal_port}/readyz" >/dev/null
}

check_proxy() {
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${canonical_host}" http://127.0.0.1/ >/dev/null
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${root_host}" http://127.0.0.1/ >/dev/null
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${www_host}" http://127.0.0.1/ >/dev/null
}

wait_for_ready() {
  # Wait for the Wave server to start and pass health checks.
  # Server needs ~20-30s to load all wavelets from MongoDB on startup.
  for _ in $(seq 1 90); do
    if check_readyz; then
      return 0
    fi
    sleep 2
  done
  return 1
}

_do_rollback_compose() {
  local rollback_image="$1"
  local previous_release="$2"
  DEPLOY_ROOT="$deploy_root" \
  WAVE_IMAGE="$rollback_image" \
  WAVE_SERVER_VERSION="${WAVE_SERVER_VERSION:-$(basename "$previous_release")}" \
  CANONICAL_HOST="$canonical_host" \
  ROOT_HOST="$root_host" \
  WWW_HOST="$www_host" \
  WAVE_INTERNAL_PORT="$internal_port" \
  RESEND_API_KEY="${RESEND_API_KEY:-}" \
  WAVE_EMAIL_FROM="${WAVE_EMAIL_FROM:-noreply@${canonical_host}}" \
  WAVE_MAIL_PROVIDER="${WAVE_MAIL_PROVIDER:-logging}" \
    docker compose --project-name "$project_name" -f "$deploy_root/current/compose.yml" up -d --remove-orphans --wait --wait-timeout 420
}

rollback_release() {
  if [[ ! -L "$deploy_root/previous" ]]; then
    echo "No previous release is available for rollback" >&2
    exit 1
  fi

  previous_release=$(readlink -f "$deploy_root/previous")
  if [[ -z "${previous_release:-}" || ! -d "$previous_release" ]]; then
    echo "Previous release link is broken" >&2
    exit 1
  fi

  local rollback_image
  if [[ -f "$deploy_root/previous_image" ]]; then
    rollback_image=$(cat "$deploy_root/previous_image")
  else
    rollback_image="${WAVE_IMAGE:-supawave-wave:$(basename "$previous_release")}"
  fi

  ln -sfn "$previous_release" "$deploy_root/current"
  retry _do_rollback_compose "$rollback_image" "$previous_release"

  wait_for_ready
  check_proxy
  echo "Rolled back to $(basename "$previous_release")"
}

deploy_release() {
  if [[ ! -f "$release_dir/compose.yml" || ! -f "$release_dir/Caddyfile" || ! -f "$release_dir/application.conf" ]]; then
    echo "Release bundle is incomplete" >&2
    exit 1
  fi

  remember_previous_release
  login_registry_if_needed
  pull_image
  render_application_config
  activate_release
  if ! compose_up; then
    echo "Compose startup failed, rolling back" >&2
    rollback_release
    exit 1
  fi

  if wait_for_ready && check_proxy; then
    echo "Deployed $(basename "$release_dir")"
    return 0
  fi

  echo "Smoke checks failed, rolling back" >&2
  rollback_release
  exit 1
}

require_docker
ensure_layout
load_deploy_env
acquire_lock
trap release_lock EXIT INT TERM

case "$cmd" in
  deploy)
    deploy_release
    ;;
  rollback)
    rollback_release
    ;;
  *)
    echo "Usage: $0 {deploy|rollback}" >&2
    exit 2
    ;;
esac

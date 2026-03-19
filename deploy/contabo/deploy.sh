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
release_dir=$(dirname "$script_dir")
deploy_root=$(dirname "$(dirname "$release_dir")")
project_name=${PROJECT_NAME:-supawave}
canonical_host=${CANONICAL_HOST:-wave.supawave.ai}
root_host=${ROOT_HOST:-supawave.ai}
www_host=${WWW_HOST:-www.supawave.ai}
internal_port=${WAVE_INTERNAL_PORT:-9898}
smoke_image=${SMOKE_IMAGE:-curlimages/curl:8.10.1}

require_docker() {
  command -v docker >/dev/null 2>&1 || {
    echo "docker is required on the Contabo host" >&2
    exit 1
  }
  docker compose version >/dev/null 2>&1 || {
    echo "docker compose plugin is required on the Contabo host" >&2
    exit 1
  }
}

ensure_layout() {
  mkdir -p "$deploy_root"/incoming
  mkdir -p "$deploy_root"/releases
  mkdir -p "$deploy_root"/shared/{accounts,attachments,caddy-config,caddy-data,certificates,deltas,indexes,sessions}
}

activate_release() {
  ln -sfn "$release_dir" "$deploy_root/current"
}

remember_previous_release() {
  if [[ -L "$deploy_root/current" ]]; then
    ln -sfn "$(readlink -f "$deploy_root/current")" "$deploy_root/previous"
  fi
}

load_image() {
  gzip -dc "$release_dir/image.tar.gz" | docker load >/dev/null
}

compose_up() {
  DEPLOY_ROOT="$deploy_root" \
  WAVE_IMAGE="${WAVE_IMAGE:-supawave-wave:$(basename "$release_dir")}" \
  CANONICAL_HOST="$canonical_host" \
  ROOT_HOST="$root_host" \
  WWW_HOST="$www_host" \
  WAVE_INTERNAL_PORT="$internal_port" \
    docker compose --project-name "$project_name" -f "$release_dir/compose.yml" up -d --remove-orphans
}

check_readyz() {
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 "http://127.0.0.1:${internal_port}/readyz" >/dev/null
}

check_proxy() {
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${canonical_host}" http://127.0.0.1/ >/dev/null
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${root_host}" http://127.0.0.1/ >/dev/null
  docker run --rm --network host "$smoke_image" -fsSI --max-time 5 -H "Host: ${www_host}" http://127.0.0.1/ >/dev/null
}

wait_for_ready() {
  for _ in $(seq 1 60); do
    if check_readyz; then
      return 0
    fi
    sleep 2
  done
  return 1
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

  ln -sfn "$previous_release" "$deploy_root/current"
  DEPLOY_ROOT="$deploy_root" \
  WAVE_IMAGE="${WAVE_IMAGE:-supawave-wave:$(basename "$previous_release")}" \
  CANONICAL_HOST="$canonical_host" \
  ROOT_HOST="$root_host" \
  WWW_HOST="$www_host" \
  WAVE_INTERNAL_PORT="$internal_port" \
    docker compose --project-name "$project_name" -f "$deploy_root/current/compose.yml" up -d --remove-orphans

  wait_for_ready
  check_proxy
  echo "Rolled back to $(basename "$previous_release")"
}

deploy_release() {
  if [[ ! -f "$release_dir/compose.yml" || ! -f "$release_dir/Caddyfile" || ! -f "$release_dir/application.conf" || ! -f "$release_dir/image.tar.gz" ]]; then
    echo "Release bundle is incomplete" >&2
    exit 1
  fi

  remember_previous_release
  load_image
  activate_release
  compose_up

  if wait_for_ready && check_proxy; then
    echo "Deployed $(basename "$release_dir")"
    return 0
  fi

  echo "Smoke checks failed, rolling back" >&2
  rollback_release
}

require_docker
ensure_layout

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

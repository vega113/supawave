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
canonical_host=${CANONICAL_HOST:-supawave.ai}
root_host=${ROOT_HOST:-wave.supawave.ai}
www_host=${WWW_HOST:-www.supawave.ai}
internal_port=${WAVE_INTERNAL_PORT:-9898}
smoke_image=${SMOKE_IMAGE:-curlimages/curl:8.10.1}
sanity_image=${SANITY_IMAGE:-alpine:3.20}
registry_host=${GHCR_REGISTRY_HOST:-ghcr.io}
deploy_env_file="$deploy_root/shared/deploy.env"

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
  mkdir -p "$deploy_root"/shared/{accounts,attachments,caddy-config,caddy-data,certificates,deltas,indexes,logs,mongo/db,sessions}
}

load_deploy_env() {
  if [[ -f "$deploy_env_file" ]]; then
    set -a
    source "$deploy_env_file"
    set +a
  fi
}

activate_release() {
  ln -sfn "$release_dir" "$deploy_root/current"
}

remember_previous_release() {
  if [[ -L "$deploy_root/current" ]]; then
    ln -sfn "$(readlink -f "$deploy_root/current")" "$deploy_root/previous"
  fi
}

login_registry_if_needed() {
  if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    printf '%s' "$GHCR_TOKEN" | docker login "$registry_host" -u "$GHCR_USERNAME" --password-stdin >/dev/null
  fi
}

pull_image() {
  local image_ref="${WAVE_IMAGE:-supawave-wave:$(basename "$release_dir")}"
  docker pull "$image_ref" >/dev/null
}

render_application_config() {
  local app_config="$release_dir/application.conf"
  if [[ -f "$app_config" ]]; then
    perl -0pi -e 's/wave\.example\.test/'"$canonical_host"'/g' "$app_config"
  fi
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
  local retries=${SMOKE_RETRIES:-240}
  for _ in $(seq 1 "$retries"); do
    if check_readyz; then
      return 0
    fi
    sleep 2
  done
  return 1
}

sanity_check() {
  local addr="${SANITY_ADDRESS:-}"
  local pass="${SANITY_PASSWORD:-}"
  if [[ -z "$addr" || -z "$pass" ]]; then
    echo "[deploy] SANITY_ADDRESS/SANITY_PASSWORD not set, skipping sanity check"
    return 0
  fi

  echo "[deploy] Running sanity check as $addr ..."

  docker run --rm --network host \
    -e INTERNAL_PORT="${internal_port}" \
    -e SANITY_ADDR="${addr}" \
    -e SANITY_PASS="${pass}" \
    "$sanity_image" sh -c '
    set -e
    if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
      apk add --no-cache curl jq >/dev/null 2>&1
    fi

    BASE="http://127.0.0.1:${INTERNAL_PORT}"
    ADDR="$SANITY_ADDR"
    PASS="$SANITY_PASS"
    COOKIE=/tmp/c.txt

    # --- Step 1: Login ---------------------------------------------------
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
      -c "$COOKIE" -L --max-time 10 \
      --data-urlencode "address=${ADDR}" \
      --data-urlencode "password=${PASS}" \
      "$BASE/auth/signin")
    if ! grep -q JSESSIONID "$COOKIE" 2>/dev/null; then
      echo "[sanity] FAIL: login did not set JSESSIONID (HTTP $HTTP)"
      exit 1
    fi
    echo "[sanity] login OK"

    # --- Step 2: Poll search (60 s) --------------------------------------
    DEADLINE=$(( $(date +%s) + 60 ))
    WAVE_ID=""
    while [ "$(date +%s)" -lt "$DEADLINE" ]; do
      RESP=$(curl -s -b "$COOKIE" --max-time 10 \
        "$BASE/search/?query=in:inbox&index=0&numResults=1" 2>/dev/null || true)
      WAVE_ID=$(printf "%s" "$RESP" | jq -r ".[\"3\"][0][\"3\"] // empty" 2>/dev/null || true)
      if [ -n "$WAVE_ID" ]; then break; fi
      sleep 2
    done
    if [ -z "$WAVE_ID" ]; then
      echo "[sanity] FAIL: search returned no waves within 60 s"
      exit 1
    fi
    echo "[sanity] search OK — found wave: $WAVE_ID"

    # --- Step 3: Fetch top wave ------------------------------------------
    FETCH_PATH=$(printf "%s" "$WAVE_ID" | sed "s|!|/|g")
    FETCH_RESP=$(curl -s -b "$COOKIE" -w "\n%{http_code}" --max-time 10 \
      "$BASE/fetch/$FETCH_PATH")
    FETCH_CODE=$(printf "%s" "$FETCH_RESP" | tail -1)
    FETCH_BODY=$(printf "%s" "$FETCH_RESP" | sed "\$d")
    if [ "$FETCH_CODE" != "200" ]; then
      echo "[sanity] FAIL: fetch returned HTTP $FETCH_CODE"
      exit 1
    fi
    HAS_CONTENT=$(printf "%s" "$FETCH_BODY" | jq "has(\"1\")" 2>/dev/null || echo "false")
    if [ "$HAS_CONTENT" != "true" ]; then
      echo "[sanity] FAIL: fetch response missing wavelet content"
      exit 1
    fi
    echo "[sanity] fetch OK — wave loaded with content"
    echo "[sanity] ALL CHECKS PASSED"
  '
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
  if [[ ! -f "$release_dir/compose.yml" || ! -f "$release_dir/Caddyfile" || ! -f "$release_dir/application.conf" ]]; then
    echo "Release bundle is incomplete" >&2
    exit 1
  fi

  remember_previous_release
  login_registry_if_needed
  pull_image
  render_application_config
  activate_release
  compose_up

  if wait_for_ready && check_proxy && sanity_check; then
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

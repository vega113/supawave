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
  local sanity_search_deadline_seconds="${SANITY_SEARCH_DEADLINE_SECONDS:-120}"
  local sanity_search_request_timeout_seconds="${SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS:-15}"
  if [[ -z "$addr" && -z "$pass" ]]; then
    echo "[deploy] SANITY_ADDRESS/SANITY_PASSWORD not set, skipping sanity check"
    return 0
  fi
  if [[ -z "$addr" || -z "$pass" ]]; then
    echo "[deploy] SANITY_ADDRESS and SANITY_PASSWORD must both be set" >&2
    return 1
  fi
  if ! [[ "$sanity_search_deadline_seconds" =~ ^[1-9][0-9]*$ ]]; then
    echo "[deploy] SANITY_SEARCH_DEADLINE_SECONDS must be a positive integer (got: '${sanity_search_deadline_seconds}')" >&2
    return 1
  fi
  if ! [[ "$sanity_search_request_timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    echo "[deploy] SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS must be a positive integer (got: '${sanity_search_request_timeout_seconds}')" >&2
    return 1
  fi
  if (( sanity_search_request_timeout_seconds > sanity_search_deadline_seconds )); then
    echo "[deploy] SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS must be less than or equal to SANITY_SEARCH_DEADLINE_SECONDS (got: '${sanity_search_request_timeout_seconds}' > '${sanity_search_deadline_seconds}')" >&2
    return 1
  fi

  echo "[deploy] Running sanity check ..."

  export INTERNAL_PORT="${internal_port}"
  export SANITY_ADDR="${addr}"
  export SANITY_PASS="${pass}"
  export SANITY_SEARCH_DEADLINE_SECONDS="${sanity_search_deadline_seconds}"
  export SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS="${sanity_search_request_timeout_seconds}"
  docker run --rm --network host \
    -e INTERNAL_PORT \
    -e SANITY_ADDR \
    -e SANITY_PASS \
    -e SANITY_SEARCH_DEADLINE_SECONDS \
    -e SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS \
    "$sanity_image" sh -c '
    set -e
    if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
      if command -v apk >/dev/null 2>&1; then
        if ! apk add --no-cache curl jq >/dev/null; then
          echo "[sanity] FAIL: unable to install curl/jq via apk" >&2
          exit 1
        fi
      else
        echo "[sanity] FAIL: curl/jq not found and apk unavailable in sanity image"
        exit 1
      fi
    fi

    BASE="http://127.0.0.1:${INTERNAL_PORT}"
    ADDR="$SANITY_ADDR"
    PASS="$SANITY_PASS"
    SEARCH_DEADLINE_SECONDS="$SANITY_SEARCH_DEADLINE_SECONDS"
    SEARCH_REQUEST_TIMEOUT_SECONDS="$SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS"
    COOKIE=/tmp/c.txt

    # --- Step 1: Login ---------------------------------------------------
    if ! HTTP=$(curl -sS -o /dev/null -w "%{http_code}" \
      -c "$COOKIE" -L --max-time 10 \
      --data-urlencode "address=${ADDR}" \
      --data-urlencode "password=${PASS}" \
      "$BASE/auth/signin"); then
      echo "[sanity] FAIL: login request failed" >&2
      exit 1
    fi
    if ! grep -q JSESSIONID "$COOKIE" 2>/dev/null; then
      echo "[sanity] FAIL: login did not set JSESSIONID (HTTP $HTTP)"
      exit 1
    fi
    echo "[sanity] login OK"

    # --- Step 2: Poll search until the cold slot finishes inbox warmup ----
    DEADLINE=$(( $(date +%s) + SEARCH_DEADLINE_SECONDS ))
    WAVE_ID=""
    while true; do
      NOW=$(date +%s)
      if [ "$NOW" -ge "$DEADLINE" ]; then break; fi
      REMAINING=$(( DEADLINE - NOW ))
      REQUEST_TIMEOUT="$SEARCH_REQUEST_TIMEOUT_SECONDS"
      if [ "$REQUEST_TIMEOUT" -gt "$REMAINING" ]; then REQUEST_TIMEOUT="$REMAINING"; fi
      if ! RESP=$(curl -sS -b "$COOKIE" --max-time "$REQUEST_TIMEOUT" \
        "$BASE/search/?query=in:inbox&index=0&numResults=1" 2>&1); then
        sleep 2
        continue
      fi
      WAVE_ID=$(printf "%s" "$RESP" | jq -r ".[\"3\"][0][\"3\"] // empty" 2>/dev/null || true)
      if [ -n "$WAVE_ID" ]; then break; fi
      sleep 2
    done
    if [ -z "$WAVE_ID" ]; then
      echo "[sanity] FAIL: search returned no waves within ${SEARCH_DEADLINE_SECONDS} s"
      exit 1
    fi
    echo "[sanity] search OK — found wave: $WAVE_ID"

    # --- Step 3: Fetch top wave ------------------------------------------
    FETCH_PATH=$(printf "%s" "$WAVE_ID" | sed "s|!|/|g")
    if ! FETCH_RESP=$(curl -sS -b "$COOKIE" -w "\n%{http_code}" --max-time 10 \
      "$BASE/fetch/$FETCH_PATH"); then
      echo "[sanity] FAIL: fetch request failed" >&2
      exit 1
    fi
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

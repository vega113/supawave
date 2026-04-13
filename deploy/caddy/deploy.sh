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
deploy_root=${DEPLOY_ROOT:-$(dirname "$(dirname "$script_dir")")}
canonical_host=${CANONICAL_HOST:-supawave.ai}
root_host=${ROOT_HOST:-wave.supawave.ai}
www_host=${WWW_HOST:-www.supawave.ai}
# Export so docker compose can interpolate ${DEPLOY_ROOT:?}, ${CANONICAL_HOST:?}, etc.
export DEPLOY_ROOT="$deploy_root"
export CANONICAL_HOST="$canonical_host"
export ROOT_HOST="$root_host"
export WWW_HOST="$www_host"
smoke_image=${SMOKE_IMAGE:-curlimages/curl:8.10.1}
sanity_image=${SANITY_IMAGE:-alpine:3.20}
registry_host=${GHCR_REGISTRY_HOST:-ghcr.io}
deploy_env_file="$deploy_root/shared/deploy.env"
lock_file="$deploy_root/deploy.lock"
COMPOSE_FILE="$deploy_root/releases/current/compose.yml"
MONGO_MIGRATION_MARKER_SUPPORT_FILE="mongo-migration-marker-supported"

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
  mkdir -p "$deploy_root"/releases/{current,previous,blue,green}
  mkdir -p "$deploy_root"/shared/{accounts,attachments,caddy-config,caddy-data,certificates,deltas,logs,mongo/db}
  mkdir -p "$deploy_root"/shared/indexes/{blue,green}
  mkdir -p "$deploy_root"/shared/sessions/{blue,green}
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

login_registry_if_needed() {
  if [[ -n "${GHCR_USERNAME:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    printf '%s' "$GHCR_TOKEN" | docker login "$registry_host" -u "$GHCR_USERNAME" --password-stdin >/dev/null
  fi
}

pull_image() {
  local image_ref="${WAVE_IMAGE:?WAVE_IMAGE must be set}"
  systemd-resolve --flush-caches 2>/dev/null || true
  retry docker pull "$image_ref" >/dev/null
}

# ---------------------------------------------------------------------------
# Blue-green helpers
# ---------------------------------------------------------------------------

detect_project_name() {
  if [ -n "${PROJECT_NAME:-}" ]; then
    : # already set from deploy.env or env
  elif [ -n "${COMPOSE_PROJECT_NAME:-}" ]; then
    PROJECT_NAME="$COMPOSE_PROJECT_NAME"
  else
    PROJECT_NAME=$(basename "$deploy_root")
    PROJECT_NAME="${PROJECT_NAME:-wave}"
  fi
  export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
}

dc() {
  docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

determine_target_slot() {
  local active
  active=$(cat "$deploy_root/shared/active-slot" 2>/dev/null || echo "blue")
  if [ "$active" = "blue" ]; then
    TARGET_SLOT="green"
    TARGET_PORT=9899
    ACTIVE_SLOT="blue"
  else
    TARGET_SLOT="blue"
    TARGET_PORT=9898
    ACTIVE_SLOT="green"
  fi
}

cancel_cooldown() {
  local systemd_scope=""
  if [ "$(id -u)" -ne 0 ]; then
    systemd_scope="--user"
  fi
  systemctl $systemd_scope stop "wave-cooldown.service" 2>/dev/null || true
  systemctl $systemd_scope stop "wave-cooldown.timer" 2>/dev/null || true
}

activate_release() {
  local release_dir="$deploy_root/releases/current"
  local previous_dir="$deploy_root/releases/previous"
  if [ -d "$release_dir" ] && [ -f "$release_dir/compose.yml" ]; then
    rm -rf "$previous_dir"
    cp -a "$release_dir" "$previous_dir"
  fi
  # Validate all required files are present in the incoming bundle before committing.
  local missing=()
  for f in compose.yml Caddyfile deploy.sh application.conf; do
    [ -f "$script_dir/$f" ] || missing+=("$f")
  done
  if [ "${#missing[@]}" -ne 0 ]; then
    echo "[deploy] ERROR: missing required bundle file(s): ${missing[*]}" >&2
    exit 1
  fi
  # Start from a clean release directory to prevent stale files persisting across releases.
  rm -rf "$release_dir"
  mkdir -p "$release_dir"
  for f in compose.yml Caddyfile deploy.sh application.conf; do
    cp "$script_dir/$f" "$release_dir/"
  done
  chmod +x "$release_dir/deploy.sh"
}

migrate_to_blue_green() {
  if [ -f "$deploy_root/shared/active-slot" ]; then
    echo "[deploy] Already migrated (active-slot exists). Skipping."
    return 0
  fi

  load_deploy_env 2>/dev/null || true
  detect_project_name

  # Check for old compose file in multiple locations:
  # 1. releases/previous/ (created by new activate_release)
  # 2. current/ symlink (legacy layout from pre-blue-green deploys)
  local old_compose=""
  if [ -f "$deploy_root/releases/previous/compose.yml" ]; then
    old_compose="$deploy_root/releases/previous/compose.yml"
  elif [ -f "$deploy_root/current/compose.yml" ]; then
    old_compose="$deploy_root/current/compose.yml"
  else
    echo "[deploy] No previous compose file — fresh install"
    echo "blue" > "$deploy_root/shared/active-slot"
    return 0
  fi

  local current_image
  current_image=$(docker compose -f "$old_compose" -p "$PROJECT_NAME" \
    images wave --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | head -1)
  if [ -z "$current_image" ] || [ "$current_image" = ":" ]; then
    echo "[deploy] No running 'wave' service found — fresh install"
    echo "blue" > "$deploy_root/shared/active-slot"
    return 0
  fi

  echo "[deploy] Migrating to blue-green (brief downtime expected)..."
  echo "[deploy] WARNING: stopping legacy 'wave' container to free port 9898"
  echo "[deploy]   wave-blue will start immediately after on the same port"
  # Export all vars that the legacy compose file may require (${VAR:?} interpolation).
  export WAVE_IMAGE="${current_image}"
  export WAVE_INTERNAL_PORT="${WAVE_INTERNAL_PORT:-9898}"
  export RESEND_API_KEY="${RESEND_API_KEY:-}"
  export WAVE_EMAIL_FROM="${WAVE_EMAIL_FROM:-}"
  # DEPLOY_ROOT/CANONICAL_HOST/ROOT_HOST/WWW_HOST already exported at script top.
  docker compose -f "$old_compose" -p "$PROJECT_NAME" stop wave
  docker compose -f "$old_compose" -p "$PROJECT_NAME" rm -f wave

  # Move existing index and session entries (files and directories) to blue slot.
  # Skip the blue/green subdirectories themselves (created by ensure_layout).
  local f
  for f in "$deploy_root/shared/indexes"/*; do
    local base; base=$(basename "$f")
    if [ "$base" = "blue" ] || [ "$base" = "green" ]; then continue; fi
    [ -e "$f" ] && mv "$f" "$deploy_root/shared/indexes/blue/" 2>/dev/null || true
  done
  for f in "$deploy_root/shared/sessions"/*; do
    local base; base=$(basename "$f")
    if [ "$base" = "blue" ] || [ "$base" = "green" ]; then continue; fi
    [ -e "$f" ] && mv "$f" "$deploy_root/shared/sessions/blue/" 2>/dev/null || true
  done

  echo "blue" > "$deploy_root/shared/active-slot"
  echo "$current_image" > "$deploy_root/releases/blue/image-ref"
  # Copy old application.conf to blue slot (check both new and legacy paths)
  local old_conf=""
  if [ -f "$deploy_root/releases/previous/application.conf" ]; then
    old_conf="$deploy_root/releases/previous/application.conf"
  elif [ -f "$(dirname "$old_compose")/application.conf" ]; then
    old_conf="$(dirname "$old_compose")/application.conf"
  fi
  if [ -n "$old_conf" ]; then
    cp "$old_conf" "$deploy_root/releases/blue/application.conf"
  fi

  cat > "$deploy_root/shared/upstream.caddy" <<'UPSTREAM'
reverse_proxy wave-blue:9898 {
    health_uri /healthz
    health_interval 2s
    health_timeout 3s
    lb_try_duration 5s
    lb_try_interval 250ms
}
UPSTREAM

  export WAVE_IMAGE_BLUE="$current_image"
  dc up -d wave-blue caddy
  echo "[deploy] Migration complete. Active slot: blue"
}

ensure_caddy_bootstrap() {
  if [ ! -f "$deploy_root/shared/upstream.caddy" ]; then
    local active
    active=$(cat "$deploy_root/shared/active-slot" 2>/dev/null || echo "blue")
    generate_upstream "$active"
  fi
  if ! dc ps caddy --format json 2>/dev/null | grep -q '"running"'; then
    dc up -d caddy
  fi
}

generate_upstream() {
  local slot=$1
  [[ "$slot" =~ ^(blue|green)$ ]] || { echo "[deploy] ERROR: invalid slot '$slot'" >&2; return 1; }
  # Write in-place — Docker bind mounts pin to the original inode.
  # Using mv would create a new inode that Docker doesn't follow.
  cat > "$deploy_root/shared/upstream.caddy" <<UPSTREAM
# active: ${slot}
reverse_proxy wave-${slot}:9898 {
    health_uri /healthz
    health_interval 2s
    health_timeout 3s
    lb_try_duration 5s
    lb_try_interval 250ms
}
UPSTREAM
}

reload_caddy() {
  # -T disables pseudo-TTY allocation — required when running over non-interactive SSH
  dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

render_slot_config() {
  local slot=$1
  local slot_dir="$deploy_root/releases/${slot}"
  mkdir -p "$slot_dir"
  cp "$deploy_root/releases/current/application.conf" "$slot_dir/application.conf"
  perl -pi -e "s/wave\\.example\\.test/${canonical_host}/g" "$slot_dir/application.conf"
  echo "${WAVE_IMAGE:?}" > "$slot_dir/image-ref"
  : > "$slot_dir/${MONGO_MIGRATION_MARKER_SUPPORT_FILE}"
}

start_target_slot() {
  export "WAVE_IMAGE_${TARGET_SLOT^^}=${WAVE_IMAGE:?}"
  if [ "$TARGET_SLOT" = "green" ]; then
    dc --profile green up -d "wave-${TARGET_SLOT}"
  else
    dc up -d "wave-${TARGET_SLOT}"
  fi
}

wait_for_slot_health() {
  local port=$1
  local retries=90
  local i=0
  while [ $i -lt $retries ]; do
    if curl -sf "http://localhost:${port}/healthz" > /dev/null 2>&1; then
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  return 1
}

slot_requires_mongo_migration_verification() {
  local slot=$1
  local config_file="$deploy_root/releases/${slot}/application.conf"
  [ -f "$config_file" ] || return 1

  # Strip comment lines before matching so that commented-out examples
  # (e.g. "# mongodb_driver = v4") do not trigger the gate.
  local effective_config
  effective_config=$(grep -v '^\s*#' "$config_file")

  echo "$effective_config" | grep -Eqi 'mongodb_driver[[:space:]]*[:=][[:space:]]*"?v4"?' || return 1
  echo "$effective_config" | grep -Eqi \
    '(signer_info_store_type|attachment_store_type|account_store_type|delta_store_type|snapshot_store_type|contact_message_store_type|feature_flag_store_type|analytics_counter_store_type)[[:space:]]*[:=][[:space:]]*"?mongodb"?'
}

slot_supports_mongo_migration_marker() {
  local slot=$1
  [ -f "$deploy_root/releases/${slot}/${MONGO_MIGRATION_MARKER_SUPPORT_FILE}" ]
}

verify_mongo_migration_completion() {
  local slot=$1
  local container_id started_at
  if ! slot_requires_mongo_migration_verification "$slot"; then
    return 0
  fi
  # Allow rollbacks to N-1 bundles that predate the Mongock startup marker.
  if ! slot_supports_mongo_migration_marker "$slot"; then
    return 0
  fi

  container_id="$(dc ps -q "wave-${slot}" 2>/dev/null | head -n1)"
  if [[ -z "$container_id" ]]; then
    echo "[deploy] ERROR: unable to determine current container id for wave-${slot}" >&2
    return 1
  fi

  started_at="$(docker inspect --format '{{.State.StartedAt}}' "$container_id" 2>/dev/null || true)"
  if [[ -z "$started_at" ]]; then
    echo "[deploy] ERROR: unable to determine current startup time for wave-${slot}" >&2
    return 1
  fi

  if dc logs --no-color --since "$started_at" "wave-${slot}" 2>&1 \
      | grep -Fq "Completed Mongock Mongo schema migrations"; then
    return 0
  fi

  echo "[deploy] ERROR: wave-${slot} did not report Mongo migration completion" >&2
  return 1
}

sanity_check() {
  # Application-level sanity against the slot on $port using $addr/$pass.
  # SANITY_ADDRESS and SANITY_PASSWORD are mandatory for deploy/rollback and
  # this function fails closed with return 1 when either value is missing.
  # In blue-green mode, SANITY_PORT overrides the default port.
  local addr="${SANITY_ADDRESS:-}"
  local pass="${SANITY_PASSWORD:-}"
  local port="${SANITY_PORT:-9898}"
  if [[ -z "$addr" || -z "$pass" ]]; then
    echo "[deploy] ERROR: SANITY_ADDRESS and SANITY_PASSWORD must both be set" >&2
    return 1
  fi

  echo "[deploy] Running sanity check on port ${port}..."

  export INTERNAL_PORT="${port}"
  export SANITY_ADDR="${addr}"
  export SANITY_PASS="${pass}"
  # Use host network so we can reach the specific slot's host-mapped port
  docker run --rm --network host \
    -e INTERNAL_PORT \
    -e SANITY_ADDR \
    -e SANITY_PASS \
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

    BASE="http://localhost:${INTERNAL_PORT}"
    ADDR="$SANITY_ADDR"
    PASS="$SANITY_PASS"
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

    # --- Step 2: Poll search (60 s) --------------------------------------
    DEADLINE=$(( $(date +%s) + 60 ))
    WAVE_ID=""
    while [ "$(date +%s)" -lt "$DEADLINE" ]; do
      if ! RESP=$(curl -sS -b "$COOKIE" --max-time 10 \
        "$BASE/search/?query=in:inbox&index=0&numResults=1" 2>&1); then
        sleep 2
        continue
      fi
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

sanity_check_slot() {
  local port=$1
  SANITY_PORT=$port sanity_check
}

post_swap_smoke() {
  local retries=10
  local i=0
  while [ $i -lt $retries ]; do
    if curl -sf --resolve "${canonical_host}:443:127.0.0.1" \
            "https://${canonical_host}/healthz" > /dev/null 2>&1; then
      break
    fi
    sleep 2
    i=$((i + 1))
  done
  if [ $i -ge $retries ]; then
    echo "[deploy] ERROR: proxy health check failed after $retries retries"
    return 1
  fi

  local redirect_status
  redirect_status=$(curl -so /dev/null -w "%{http_code}" \
    --resolve "${root_host}:443:127.0.0.1" \
    "https://${root_host}/" 2>/dev/null || echo "000")
  if [ "$redirect_status" != "301" ] && [ "$redirect_status" != "308" ]; then
    echo "[deploy] WARNING: root host redirect returned $redirect_status (expected 301/308)"
  fi

  return 0
}

stop_replaced_slot() {
  local old_slot=$1
  local ps_output=""
  if dc stop "wave-${old_slot}" 2>/dev/null; then
    return 0
  fi

  echo "[deploy] WARNING: graceful stop failed for wave-${old_slot}; forcing shutdown" >&2
  dc kill "wave-${old_slot}" 2>/dev/null || true

  if ! ps_output="$(dc ps "wave-${old_slot}" --format json 2>/dev/null)"; then
    echo "[deploy] ERROR: unable to verify whether replaced slot wave-${old_slot} is still running" >&2
    return 1
  fi

  if printf '%s\n' "$ps_output" | grep -q '"running"'; then
    echo "[deploy] ERROR: replaced slot wave-${old_slot} is still running" >&2
    return 1
  fi
  return 0
}

best_effort_stop_slot() {
  local slot=$1
  dc stop "wave-${slot}" 2>/dev/null || dc kill "wave-${slot}" 2>/dev/null || true
}

revert_swap() {
  local restored_slot=$1
  local reverted_slot=$2
  local reason=$3
  local routing_restored=0

  echo "[deploy] ERROR: ${reason}" >&2
  if ! generate_upstream "$restored_slot"; then
    echo "[deploy] ERROR: failed to regenerate upstream for ${restored_slot} while reverting swap" >&2
  else
    if ! reload_caddy; then
      echo "[deploy] ERROR: failed to reload Caddy while reverting swap to ${restored_slot}" >&2
    else
      routing_restored=1
    fi
  fi
  if [ "$routing_restored" -eq 1 ]; then
    best_effort_stop_slot "$reverted_slot"
  else
    echo "[deploy] WARNING: leaving wave-${reverted_slot} running because rollback routing was not restored" >&2
  fi
  exit 1
}

update_state() {
  echo "$TARGET_SLOT" > "$deploy_root/shared/active-slot"
  echo "$ACTIVE_SLOT" > "$deploy_root/shared/previous-slot"
}

# ---------------------------------------------------------------------------
# Command functions
# ---------------------------------------------------------------------------

deploy_release() {
  determine_target_slot
  echo "[deploy] Deploying to ${TARGET_SLOT} slot (active: ${ACTIVE_SLOT})"

  cancel_cooldown
  activate_release
  for req in compose.yml application.conf Caddyfile; do
    [ -f "$deploy_root/releases/current/$req" ] || {
      echo "[deploy] ERROR: missing required file: releases/current/$req" >&2
      exit 1
    }
  done
  detect_project_name
  migrate_to_blue_green
  # Re-determine slot after migration — migration may have initialized active-slot
  determine_target_slot
  ensure_caddy_bootstrap
  login_registry_if_needed
  pull_image
  render_slot_config "$TARGET_SLOT"
  start_target_slot

  if ! wait_for_slot_health "$TARGET_PORT"; then
    echo "[deploy] ERROR: ${TARGET_SLOT} health check failed"
    dc stop "wave-${TARGET_SLOT}" 2>/dev/null || true
    exit 1
  fi

  if ! verify_mongo_migration_completion "$TARGET_SLOT"; then
    dc stop "wave-${TARGET_SLOT}" 2>/dev/null || true
    exit 1
  fi

  if ! sanity_check_slot "$TARGET_PORT"; then
    echo "[deploy] ERROR: ${TARGET_SLOT} sanity check failed"
    dc stop "wave-${TARGET_SLOT}" 2>/dev/null || true
    exit 1
  fi

  generate_upstream "$TARGET_SLOT"
  reload_caddy

  if ! post_swap_smoke; then
    echo "[deploy] ERROR: post-swap smoke failed, reverting"
    generate_upstream "$ACTIVE_SLOT"
    reload_caddy
    dc stop "wave-${TARGET_SLOT}" 2>/dev/null || true
    exit 1
  fi

  if ! stop_replaced_slot "$ACTIVE_SLOT"; then
    revert_swap "$ACTIVE_SLOT" "$TARGET_SLOT" \
      "replaced slot ${ACTIVE_SLOT} failed to stop after deploy swap; reverting"
  fi
  update_state
  echo "[deploy] Deploy complete. Active: ${TARGET_SLOT}"
}

rollback_release() {
  detect_project_name
  local prev
  prev=$(cat "$deploy_root/shared/previous-slot" 2>/dev/null)
  if [ -z "$prev" ]; then
    echo "[deploy] ERROR: No previous slot to rollback to"
    exit 1
  fi

  local prev_port
  prev_port=$([ "$prev" = "blue" ] && echo 9898 || echo 9899)
  local current
  current=$(cat "$deploy_root/shared/active-slot")

  if ! dc ps "wave-${prev}" --format json 2>/dev/null | grep -q '"running"'; then
    local prev_image
    prev_image=$(cat "$deploy_root/releases/${prev}/image-ref" 2>/dev/null)
    if [ -z "$prev_image" ]; then
      echo "[deploy] ERROR: No previous image recorded"
      exit 1
    fi
    export "WAVE_IMAGE_${prev^^}=$prev_image"
    if [ "$prev" = "green" ]; then
      dc --profile green up -d "wave-${prev}"
    else
      dc up -d "wave-${prev}"
    fi
    if ! wait_for_slot_health "$prev_port"; then
      echo "[deploy] ERROR: Previous slot health check failed"
      exit 1
    fi
    if ! verify_mongo_migration_completion "$prev"; then
      exit 1
    fi
  fi

  if ! sanity_check_slot "$prev_port"; then
    echo "[deploy] ERROR: Previous slot sanity check failed — cannot rollback"
    exit 1
  fi

  cancel_cooldown
  ensure_caddy_bootstrap
  generate_upstream "$prev"
  reload_caddy

  if ! post_swap_smoke; then
    echo "[deploy] ERROR: post-rollback proxy smoke failed, reverting"
    generate_upstream "$current"
    reload_caddy
    exit 1
  fi

  if ! stop_replaced_slot "$current"; then
    revert_swap "$current" "$prev" \
      "replaced slot ${current} failed to stop after rollback swap; reverting"
  fi
  echo "$prev" > "$deploy_root/shared/active-slot"
  echo "$current" > "$deploy_root/shared/previous-slot"
  echo "[deploy] Rolled back to ${prev}"
}

show_status() {
  load_deploy_env
  detect_project_name
  local active
  active=$(cat "$deploy_root/shared/active-slot" 2>/dev/null || echo "unknown")
  echo "Active slot: $active"
  echo ""
  echo "Blue:"
  echo "  Image: $(cat "$deploy_root/releases/blue/image-ref" 2>/dev/null || echo 'none')"
  echo "Green:"
  echo "  Image: $(cat "$deploy_root/releases/green/image-ref" 2>/dev/null || echo 'none')"
  echo ""
  echo "Container status:"
  dc ps wave-blue wave-green 2>/dev/null || echo "  (compose not running)"
  echo ""
  echo "Caddy upstream:"
  grep -m1 "reverse_proxy" "$deploy_root/shared/upstream.caddy" 2>/dev/null || echo "  (not configured)"
  echo ""
  echo "Cooldown timer:"
  local systemd_scope=""
  [ "$(id -u)" -ne 0 ] && systemd_scope="--user"
  systemctl $systemd_scope status wave-cooldown.timer 2>/dev/null \
    || echo "  disabled (replaced slot stops immediately)"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

require_docker
ensure_layout
load_deploy_env

case "$cmd" in
  deploy)
    acquire_lock
    trap release_lock EXIT INT TERM
    deploy_release
    ;;
  rollback)
    acquire_lock
    trap release_lock EXIT INT TERM
    rollback_release
    ;;
  status)
    show_status
    ;;
  *)
    echo "Usage: $0 {deploy|rollback|status}" >&2
    exit 2
    ;;
esac

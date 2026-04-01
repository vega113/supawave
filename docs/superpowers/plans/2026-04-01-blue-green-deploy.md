# Blue-Green Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-service rolling deploy with a dual-slot blue-green deployment that gates traffic on sanity checks and supports instant rollback.

**Architecture:** Two Wave services (`wave-blue` on :9898, `wave-green` on :9899) share one MongoDB. Caddy imports a generated `upstream.caddy` file that points to the active slot. The deploy script manages slot selection, health/sanity checks, Caddy reload, and 30-minute cooldown timers via systemd-run.

**Tech Stack:** Docker Compose, Caddy 2.8, Bash, systemd-run, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-04-01-blue-green-deploy-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `deploy/caddy/compose.yml` | Rewrite | Dual services, slot-specific volumes, profiles |
| `deploy/caddy/Caddyfile` | Rewrite | Import upstream.caddy instead of hardcoded reverse_proxy |
| `deploy/caddy/deploy.sh` | Rewrite | Blue-green deploy/rollback/status logic |
| `.github/workflows/deploy-contabo.yml` | Modify | Extraction path, rollback input, PROJECT_NAME |
| `deploy/caddy/deploy.env.example` | Create | Document required env vars including PROJECT_NAME |

---

### Task 1: Rewrite compose.yml with dual Wave services

**Files:**
- Modify: `deploy/caddy/compose.yml`

- [ ] **Step 1: Read current compose.yml**

Already read above. Current file has a single `wave` service with `deploy.update_config.order: start-first` (Swarm-only, no effect).

- [ ] **Step 2: Replace compose.yml with dual-service layout**

```yaml
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

x-wave-env: &wave-env
  RESEND_API_KEY: ${RESEND_API_KEY:-}
  WAVE_EMAIL_FROM: ${WAVE_EMAIL_FROM:-noreply@supawave.ai}
  WAVE_MAIL_PROVIDER: ${WAVE_MAIL_PROVIDER:-logging}
  WAVE_SERVER_VERSION: ${WAVE_SERVER_VERSION:-dev}

services:
  mongo:
    image: mongo:6.0
    restart: unless-stopped
    command:
      - --bind_ip_all
    healthcheck:
      test: ["CMD-SHELL", "mongosh --quiet --eval 'db.runCommand({ ping: 1 }).ok' || exit 1"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    volumes:
      - ${DEPLOY_ROOT:?set DEPLOY_ROOT before running compose}/shared/mongo/db:/data/db

  wave-blue:
    image: ${WAVE_IMAGE_BLUE:-scratch}
    container_name: wave-blue
    restart: unless-stopped
    depends_on:
      mongo:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9898/healthz || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 300s
    ports:
      - "127.0.0.1:9898:9898"
    volumes:
      - ${DEPLOY_ROOT:?}/releases/blue/application.conf:/opt/wave/config/application.conf:ro
      - ${DEPLOY_ROOT:?}/shared/accounts:/opt/wave/_accounts
      - ${DEPLOY_ROOT:?}/shared/attachments:/opt/wave/_attachments
      - ${DEPLOY_ROOT:?}/shared/certificates:/opt/wave/_certificates:ro
      - ${DEPLOY_ROOT:?}/shared/deltas:/opt/wave/_deltas
      - ${DEPLOY_ROOT:?}/shared/indexes/blue:/opt/wave/_indexes
      - ${DEPLOY_ROOT:?}/shared/sessions/blue:/opt/wave/_sessions
      - ${DEPLOY_ROOT:?}/shared/logs:/opt/wave/logs
    environment: *wave-env

  wave-green:
    image: ${WAVE_IMAGE_GREEN:-scratch}
    container_name: wave-green
    restart: unless-stopped
    depends_on:
      mongo:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9898/healthz || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 300s
    ports:
      - "127.0.0.1:9899:9898"
    volumes:
      - ${DEPLOY_ROOT:?}/releases/green/application.conf:/opt/wave/config/application.conf:ro
      - ${DEPLOY_ROOT:?}/shared/accounts:/opt/wave/_accounts
      - ${DEPLOY_ROOT:?}/shared/attachments:/opt/wave/_attachments
      - ${DEPLOY_ROOT:?}/shared/certificates:/opt/wave/_certificates:ro
      - ${DEPLOY_ROOT:?}/shared/deltas:/opt/wave/_deltas
      - ${DEPLOY_ROOT:?}/shared/indexes/green:/opt/wave/_indexes
      - ${DEPLOY_ROOT:?}/shared/sessions/green:/opt/wave/_sessions
      - ${DEPLOY_ROOT:?}/shared/logs:/opt/wave/logs
    environment: *wave-env
    profiles: ["green"]

  caddy:
    image: caddy:2.8.4-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      CANONICAL_HOST: ${CANONICAL_HOST:?set CANONICAL_HOST before running compose}
      ROOT_HOST: ${ROOT_HOST:?set ROOT_HOST before running compose}
      WWW_HOST: ${WWW_HOST:?set WWW_HOST before running compose}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ${DEPLOY_ROOT:?}/shared/upstream.caddy:/etc/caddy/upstream.caddy:ro
      - ${DEPLOY_ROOT:?}/shared/caddy-data:/data
      - ${DEPLOY_ROOT:?}/shared/caddy-config:/config
```

- [ ] **Step 3: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('deploy/caddy/compose.yml'))"`
Expected: No output (success)

- [ ] **Step 4: Commit**

```bash
git add deploy/caddy/compose.yml
git commit -m "feat(deploy): rewrite compose.yml for blue-green dual services

Replace single 'wave' service with wave-blue and wave-green slots.
Slot-specific volumes for Lucene indexes and sessions. Caddy imports
generated upstream.caddy file. Green slot uses compose profile."
```

---

### Task 2: Rewrite Caddyfile with import directive

**Files:**
- Modify: `deploy/caddy/Caddyfile`

- [ ] **Step 1: Replace Caddyfile content**

```caddy
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

{$CANONICAL_HOST} {
  encode zstd gzip
  import /etc/caddy/upstream.caddy
}

{$ROOT_HOST} {
  redir https://{$CANONICAL_HOST}{uri} permanent
}

{$WWW_HOST} {
  redir https://{$CANONICAL_HOST}{uri} permanent
}
```

- [ ] **Step 2: Commit**

```bash
git add deploy/caddy/Caddyfile
git commit -m "feat(deploy): Caddyfile imports upstream.caddy for blue-green swap

Replace hardcoded reverse_proxy block with import directive. The
upstream.caddy file is generated by deploy.sh and points to the
active slot. Caddy reload swaps traffic without container restart."
```

---

### Task 3: Rewrite deploy.sh with blue-green logic

This is the largest task. The entire `deploy/caddy/deploy.sh` is rewritten.

**Files:**
- Modify: `deploy/caddy/deploy.sh`

- [ ] **Step 1: Write the complete deploy.sh**

The new script must include all functions from the spec: `detect_project_name`, `determine_target_slot`, `cancel_cooldown`, `start_target_slot`, `wait_for_slot_health`, `sanity_check_slot`, `ensure_caddy_bootstrap`, `generate_upstream`, `reload_caddy`, `post_swap_smoke`, `schedule_cooldown`, `render_slot_config`, `update_state`, `activate_release`, `migrate_to_blue_green`, plus the `deploy`, `rollback`, and `status` commands.

Write the full file — copy from the spec verbatim for all new functions, preserve `require_docker`, `ensure_layout` (extended), `load_deploy_env`, `acquire_lock`, `release_lock`, `retry`, `login_registry_if_needed`, `pull_image` from the current script. The key structural changes:

1. **Header variables:** Replace `project_name=${PROJECT_NAME:-supawave}` with `detect_project_name()` call. Add `COMPOSE_FILE` variable.
2. **ensure_layout:** Add slot-specific subdirectories for indexes, sessions, releases.
3. **activate_release:** Preserve old release to `releases/previous`, then copy from `incoming/`.
4. **deploy command:** Full blue-green flow from spec (determine slot → cancel cooldown → activate → migrate → bootstrap → pull → render → start → health → sanity → swap → post-smoke → state → cooldown).
5. **rollback command:** Full rollback flow from spec.
6. **status command:** New command showing slot state.
7. **Command dispatch:** Add `status` to the case statement.
8. **Sanity check integration:** `sanity_check_slot` wraps PR #541's `sanity_check()` with port override. If PR #541 is not yet merged, make it a no-op stub that logs a warning.

The complete script is too long to inline here. The engineer should:
- Start from the current `deploy/caddy/deploy.sh`
- Keep: license header, `set -euo pipefail`, `require_docker`, `load_deploy_env`, `acquire_lock`/`release_lock`/`trap`, `retry`, `login_registry_if_needed`
- Replace: everything after `login_registry_if_needed` with the spec's functions and commands
- Extend: `ensure_layout` to add slot-specific dirs
- Replace: `pull_image` to accept `IMAGE_REF` parameter instead of deriving from release dir

Here is the complete replacement for the function/command section (everything from `ensure_layout` onward):

```bash
ensure_layout() {
  mkdir -p "$deploy_root"/incoming
  mkdir -p "$deploy_root"/releases/{current,previous,blue,green}
  mkdir -p "$deploy_root"/shared/{accounts,attachments,caddy-config,caddy-data,certificates,deltas,logs,mongo/db}
  mkdir -p "$deploy_root"/shared/indexes/{blue,green}
  mkdir -p "$deploy_root"/shared/sessions/{blue,green}
}

COMPOSE_FILE="$deploy_root/releases/current/compose.yml"

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
  mkdir -p "$release_dir"
  cp "$deploy_root/incoming/compose.yml" "$release_dir/"
  cp "$deploy_root/incoming/Caddyfile" "$release_dir/"
  cp "$deploy_root/incoming/deploy.sh" "$release_dir/"
  cp "$deploy_root/incoming/application.conf" "$release_dir/"
  chmod +x "$release_dir/deploy.sh"
}

migrate_to_blue_green() {
  if [ -f "$deploy_root/shared/active-slot" ]; then
    echo "[deploy] Already migrated (active-slot exists). Skipping."
    return 0
  fi

  load_deploy_env 2>/dev/null || true
  detect_project_name

  local old_compose="$deploy_root/releases/previous/compose.yml"
  if [ ! -f "$old_compose" ]; then
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

  echo "[deploy] Migrating to blue-green..."
  docker compose -f "$old_compose" -p "$PROJECT_NAME" stop wave
  docker compose -f "$old_compose" -p "$PROJECT_NAME" rm -f wave

  local f
  for f in "$deploy_root/shared/indexes"/*; do
    [ -f "$f" ] && mv "$f" "$deploy_root/shared/indexes/blue/" 2>/dev/null || true
  done
  for f in "$deploy_root/shared/sessions"/*; do
    [ -f "$f" ] && mv "$f" "$deploy_root/shared/sessions/blue/" 2>/dev/null || true
  done

  echo "blue" > "$deploy_root/shared/active-slot"
  echo "$current_image" > "$deploy_root/releases/blue/image-ref"
  if [ -f "$deploy_root/releases/previous/application.conf" ]; then
    cp "$deploy_root/releases/previous/application.conf" \
       "$deploy_root/releases/blue/application.conf"
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
  dc exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

render_slot_config() {
  local slot=$1
  local slot_dir="$deploy_root/releases/${slot}"
  mkdir -p "$slot_dir"
  cp "$deploy_root/releases/current/application.conf" "$slot_dir/application.conf"
  perl -pi -e "s/wave\\.example\\.test/${canonical_host}/g" "$slot_dir/application.conf"
  echo "${WAVE_IMAGE:-unknown}" > "$slot_dir/image-ref"
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

sanity_check_slot() {
  local port=$1
  if type sanity_check >/dev/null 2>&1; then
    SANITY_PORT=$port sanity_check
  else
    echo "[deploy] WARNING: sanity_check not available (PR #541 not merged), skipping"
  fi
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

schedule_cooldown() {
  local old_slot=$1
  local systemd_scope=""
  if [ "$(id -u)" -ne 0 ]; then
    systemd_scope="--user"
  fi
  systemctl $systemd_scope stop "wave-cooldown.service" 2>/dev/null || true
  if ! systemd-run $systemd_scope --on-active=30min --unit="wave-cooldown" \
      -- docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" stop "wave-${old_slot}" 2>/dev/null; then
    echo "[deploy] WARNING: failed to schedule cooldown timer — old slot will stay running"
  fi
}

update_state() {
  echo "$TARGET_SLOT" > "$deploy_root/shared/active-slot"
  echo "$ACTIVE_SLOT" > "$deploy_root/shared/previous-slot"
}

deploy_release() {
  determine_target_slot
  echo "[deploy] Deploying to ${TARGET_SLOT} slot (active: ${ACTIVE_SLOT})"

  cancel_cooldown
  activate_release
  migrate_to_blue_green
  detect_project_name
  ensure_caddy_bootstrap
  login_registry_if_needed
  retry pull_image
  render_slot_config "$TARGET_SLOT"
  start_target_slot

  if ! wait_for_slot_health "$TARGET_PORT"; then
    echo "[deploy] ERROR: ${TARGET_SLOT} health check failed"
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

  update_state
  schedule_cooldown "$ACTIVE_SLOT"
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
  fi

  if ! sanity_check_slot "$prev_port"; then
    echo "[deploy] ERROR: Previous slot sanity check failed — cannot rollback"
    exit 1
  fi

  cancel_cooldown
  generate_upstream "$prev"
  reload_caddy

  if ! post_swap_smoke; then
    echo "[deploy] ERROR: post-rollback proxy smoke failed, reverting"
    local current_active
    current_active=$(cat "$deploy_root/shared/active-slot")
    generate_upstream "$current_active"
    reload_caddy
    exit 1
  fi

  local current
  current=$(cat "$deploy_root/shared/active-slot")
  echo "$prev" > "$deploy_root/shared/active-slot"
  echo "$current" > "$deploy_root/shared/previous-slot"
  schedule_cooldown "$current"
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
  systemctl $systemd_scope status wave-cooldown.timer 2>/dev/null || echo "  (none active)"
}
```

And the command dispatch at the bottom:

```bash
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
  status)
    show_status
    ;;
  *)
    echo "Usage: $0 {deploy|rollback|status}" >&2
    exit 2
    ;;
esac
```

- [ ] **Step 2: Verify script syntax**

Run: `bash -n deploy/caddy/deploy.sh`
Expected: No output (success)

- [ ] **Step 3: Commit**

```bash
git add deploy/caddy/deploy.sh
git commit -m "feat(deploy): rewrite deploy.sh for blue-green deployment

Full blue-green deploy flow: slot selection, health check, sanity check
gate, Caddy reload (not recreate), post-swap proxy smoke, 30-min
cooldown via systemd-run, instant rollback with sanity verification,
status command. Includes one-time migration from single-service layout."
```

---

### Task 4: Create deploy.env.example

**Files:**
- Create: `deploy/caddy/deploy.env.example`

- [ ] **Step 1: Write the example env file**

```bash
# Blue-green deployment configuration
# Copy this file to $DEPLOY_ROOT/shared/deploy.env on the host

# REQUIRED: Must match the existing Docker Compose project name
PROJECT_NAME=supawave

# Optional: sanity check credentials (PR #541)
# SANITY_ADDRESS=user@example.com
# SANITY_PASSWORD=password

# Optional: override defaults
# CANONICAL_HOST=supawave.ai
# ROOT_HOST=wave.supawave.ai
# WWW_HOST=www.supawave.ai
```

- [ ] **Step 2: Commit**

```bash
git add deploy/caddy/deploy.env.example
git commit -m "docs(deploy): add deploy.env.example for blue-green config"
```

---

### Task 5: Update GitHub Actions workflow

**Files:**
- Modify: `.github/workflows/deploy-contabo.yml`

- [ ] **Step 1: Add rollback input to workflow_dispatch**

In the `on:` section, change:

```yaml
on:
  workflow_dispatch:
    inputs:
      action:
        description: 'Deploy action'
        required: false
        default: 'deploy'
        type: choice
        options: ['deploy', 'rollback', 'status']
  push:
    branches:
      - main
```

- [ ] **Step 2: Update the "Deploy on Contabo" step**

Change the SSH command to:
1. Extract to `incoming/` instead of `releases/$RELEASE_ID`
2. Pass `WAVE_IMAGE` (not just `IMAGE_REF`)
3. Use the action input for deploy/rollback
4. Pass `PROJECT_NAME`

Replace the "Deploy on Contabo" step's `run:` block:

```yaml
      - name: Deploy on Contabo
        id: deploy
        env:
          RESEND_API_KEY: ${{ secrets.RESEND_API_KEY }}
          WAVE_EMAIL_FROM: ${{ secrets.WAVE_EMAIL_FROM }}
        run: |
          set -euo pipefail
          remote_root="${{ secrets.CONTABO_DEPLOY_ROOT || format('/home/{0}/supawave', secrets.CONTABO_SSH_USER) }}"
          action="${{ github.event.inputs.action || 'deploy' }}"
          resend_api_key_q=$(printf '%q' "${RESEND_API_KEY:-}")
          wave_email_from_q=$(printf '%q' "${WAVE_EMAIL_FROM:-}")
          ssh -o ServerAliveInterval=30 -o ServerAliveCountMax=20 -p "${{ secrets.CONTABO_SSH_PORT || '22' }}" "${{ secrets.CONTABO_SSH_USER }}@${{ secrets.CONTABO_SSH_HOST }}" "
            set -euo pipefail
            incoming='$remote_root/incoming'
            mkdir -p \"\$incoming\"
            tar -xzf '$remote_root/incoming/$RELEASE_ID.tgz' -C \"\$incoming\"
            chmod +x \"\$incoming/deploy.sh\"
            DEPLOY_ROOT='$remote_root' \
            WAVE_IMAGE='$IMAGE_REF' \
            CANONICAL_HOST='$CANONICAL_HOST' \
            ROOT_HOST='$ROOT_HOST' \
            WWW_HOST='$WWW_HOST' \
            RESEND_API_KEY=$resend_api_key_q \
            WAVE_EMAIL_FROM=$wave_email_from_q \
              bash \"\$incoming/deploy.sh\" $action
          "
```

- [ ] **Step 3: Update the "Assemble deployment bundle" step — add application.conf**

The bundle already includes application.conf. No change needed here, verify only.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/deploy-contabo.yml
git commit -m "feat(ci): update deploy workflow for blue-green deployment

Add rollback/status workflow_dispatch input. Extract bundle to incoming/
instead of releases/\$RELEASE_ID. The deploy.sh script now handles
release activation, slot management, and migration internally."
```

---

### Task 6: Update CI build.yml smoke test

The `build.yml` workflow runs a smoke test that starts the server on port 9898. It uses the current `compose.yml`. Since we changed the compose file to require `WAVE_IMAGE_BLUE` instead of `WAVE_IMAGE`, the CI smoke test needs updating.

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Find and update the smoke test step**

The smoke test likely does `docker compose up` with `WAVE_IMAGE`. Change it to use `WAVE_IMAGE_BLUE` and ensure the slot directories exist. Read the build.yml to find the exact step, then update accordingly.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "fix(ci): update build smoke test for blue-green compose layout"
```

---

### Task 7: End-to-end verification

- [ ] **Step 1: Verify all changed files have valid syntax**

```bash
bash -n deploy/caddy/deploy.sh
python3 -c "import yaml; yaml.safe_load(open('deploy/caddy/compose.yml'))"
```

- [ ] **Step 2: Verify compose file works with both slots**

```bash
cd deploy/caddy
DEPLOY_ROOT=/tmp/wave-test \
WAVE_IMAGE_BLUE=scratch \
CANONICAL_HOST=test.local \
ROOT_HOST=wave.test.local \
WWW_HOST=www.test.local \
  docker compose config
```

Expected: Valid compose output with both wave-blue and wave-green services.

- [ ] **Step 3: Commit all remaining changes**

```bash
git add -A
git commit -m "chore(deploy): finalize blue-green deployment implementation"
```

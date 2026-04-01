# Blue-Green Deployment Design

**Date:** 2026-04-01
**Status:** Draft
**PR Dependency:** #541 (deploy sanity check)

## Problem Statement

The current deployment uses Docker Compose `start-first` rolling updates with Caddy health-aware routing. While this provides near-zero-downtime, it has limitations:

1. **No instant rollback** — rollback requires re-pulling the previous image and restarting, which takes 30-60 seconds.
2. **No approval gate** — Caddy automatically routes to the new container as soon as it's healthy, before functional sanity checks complete.
3. **Brief traffic interruption** — during the rolling swap there's a window where the proxy may route to a container that's healthy but not yet fully warmed (e.g., Lucene indexes loading).

## Solution

True blue-green deployment with dual Wave services, a sanity check gate, and Caddy upstream swapping.

## Architecture

### Service Topology

Two Wave services coexist in a single Docker Compose file sharing one MongoDB instance:

```
┌─────────────────────────────────────────────────┐
│  Docker Compose                                  │
│                                                  │
│  ┌──────────┐    ┌────────────┐                  │
│  │  mongo    │◄───│ wave-blue  │ :9898 (host)    │
│  │  :27017   │◄───│ wave-green │ :9899 (host)    │
│  └──────────┘    └────────────┘                  │
│                                                  │
│  ┌──────────┐                                    │
│  │  caddy    │  reverse_proxy → active slot      │
│  │  :80/:443 │                                   │
│  └──────────┘                                    │
└─────────────────────────────────────────────────┘
```

Both Wave services bind to the container-internal port `9898` but map to different host ports:
- `wave-blue`: `127.0.0.1:9898:9898`
- `wave-green`: `127.0.0.1:9899:9898`

Both share the same MongoDB instance (`mongo:27017`), same volume mounts, and same application.conf. The only difference is the host port binding and the image tag.

### State Files

```
$DEPLOY_ROOT/shared/active-slot      # Contains "blue" or "green"
$DEPLOY_ROOT/shared/previous-slot    # For rollback reference
$DEPLOY_ROOT/shared/blue-image       # Image ref running on blue
$DEPLOY_ROOT/shared/green-image      # Image ref running on green
$DEPLOY_ROOT/shared/cooldown-pid     # PID of background cleanup process
```

### Deployment Flow

```
1. CI builds Docker image, pushes to ghcr.io
2. CI SSHs to host, uploads bundle, runs deploy.sh deploy
3. deploy.sh determines inactive slot:
   - Reads $DEPLOY_ROOT/shared/active-slot
   - If "blue" → target = "green", port = 9899
   - If "green" → target = "blue", port = 9898
   - If file missing → initialize as "blue" active, target "green"
4. Pull new image for target slot
5. Set WAVE_IMAGE_{BLUE|GREEN} env var to new image ref
6. Start target slot: docker compose up -d wave-${target}
7. Wait for target slot health check (/healthz on target port)
   - 90 retries × 2s = 3 minutes max
8. Run sanity check against target slot (login, search, fetch)
   - Uses target port directly, not through Caddy
   - This is the PR #541 sanity_check() function
9. On sanity success:
   a. Update WAVE_UPSTREAM env var to wave-${target}:9898
   b. Recreate Caddy to pick up new upstream
   c. Write "${target}" to active-slot file
   d. Write previous slot to previous-slot file
   e. Store new image ref to ${target}-image file
   f. Kill any existing cooldown process
   g. Schedule old slot stop after 30 minutes:
      (sleep 1800 && docker compose stop wave-${old}) &
      echo $! > cooldown-pid
10. On sanity failure:
    a. Stop target slot
    b. Log failure, exit non-zero (triggers CI failure notification)
```

### Rollback Flow

```
1. deploy.sh rollback
2. Read previous-slot file → old_slot
3. Verify wave-${old_slot} container is still running
   - If not running (cooldown expired): pull old image from ${old_slot}-image, start it, wait for health
   - If running: skip straight to swap
4. Update WAVE_UPSTREAM to wave-${old_slot}:9898
5. Recreate Caddy
6. Update active-slot file
7. Kill cooldown PID if exists
8. Schedule new inactive slot stop after 30 minutes
```

### Status Command

```
deploy.sh status
  - Shows: active slot, inactive slot status, image refs, cooldown remaining
```

## Compose File Changes

### Current (single service)

```yaml
wave:
  image: ${WAVE_IMAGE}
  ports: ["127.0.0.1:9898:9898"]
  deploy:
    update_config:
      order: start-first
      failure_action: rollback
```

### Proposed (dual services)

```yaml
wave-blue:
  image: ${WAVE_IMAGE_BLUE:-scratch}
  container_name: wave-blue
  ports: ["127.0.0.1:9898:9898"]
  depends_on:
    mongo: { condition: service_healthy }
  healthcheck:
    test: ["CMD", "curl", "-sf", "http://localhost:9898/healthz"]
    interval: 10s
    timeout: 5s
    retries: 30
    start_period: 300s
  restart: unless-stopped
  volumes: &wave-volumes
    - ${DEPLOY_ROOT}/releases/current/application.conf:/opt/wave/config/application.conf:ro
    - ${DEPLOY_ROOT}/shared/accounts:/opt/wave/_accounts
    - ${DEPLOY_ROOT}/shared/attachments:/opt/wave/_attachments
    - ${DEPLOY_ROOT}/shared/certificates:/opt/wave/_certificates
    - ${DEPLOY_ROOT}/shared/deltas:/opt/wave/_deltas
    - ${DEPLOY_ROOT}/shared/indexes:/opt/wave/_indexes
    - ${DEPLOY_ROOT}/shared/sessions:/opt/wave/_sessions
    - ${DEPLOY_ROOT}/shared/logs:/opt/wave/logs
  environment: &wave-env
    RESEND_API_KEY: ${RESEND_API_KEY:-}
    WAVE_EMAIL_FROM: ${WAVE_EMAIL_FROM:-}
    WAVE_MAIL_PROVIDER: ${WAVE_MAIL_PROVIDER:-log}
    WAVE_SERVER_VERSION: ${RELEASE_ID:-dev}

wave-green:
  image: ${WAVE_IMAGE_GREEN:-scratch}
  container_name: wave-green
  ports: ["127.0.0.1:9899:9898"]
  depends_on:
    mongo: { condition: service_healthy }
  healthcheck:
    test: ["CMD", "curl", "-sf", "http://localhost:9898/healthz"]
    interval: 10s
    timeout: 5s
    retries: 30
    start_period: 300s
  restart: unless-stopped
  volumes: *wave-volumes
  environment: *wave-env
  profiles: ["green"]

caddy:
  depends_on:
    mongo: { condition: service_healthy }
  environment:
    WAVE_UPSTREAM: ${WAVE_UPSTREAM:-wave-blue:9898}
    CANONICAL_HOST: ${CANONICAL_HOST}
    ROOT_HOST: ${ROOT_HOST}
    WWW_HOST: ${WWW_HOST}
    WAVE_INTERNAL_PORT: "9898"
```

Key changes:
- Two wave services with YAML anchors (`&wave-volumes`, `&wave-env`) to avoid duplication
- `wave-green` uses `profiles: ["green"]` so it doesn't start by default on first deploy
- Both use `scratch` as default image (won't start without explicit image set)
- Caddy depends on mongo (not wave) since it dynamically discovers the active upstream
- `WAVE_UPSTREAM` env var controls which slot Caddy proxies to

### Caddyfile Changes

The Caddyfile already uses `{$WAVE_INTERNAL_PORT}` — only the service name changes:

```caddy
{$CANONICAL_HOST} {
    encode zstd gzip
    reverse_proxy {$WAVE_UPSTREAM} {
        health_uri /healthz
        health_interval 2s
        health_timeout 3s
        lb_try_duration 5s
        lb_try_interval 250ms
    }
}
```

The `WAVE_UPSTREAM` variable replaces the hardcoded `wave:{$WAVE_INTERNAL_PORT}`.

## Deploy Script Changes

### New Functions

```bash
determine_target_slot() {
    local active
    active=$(cat "$DEPLOY_ROOT/shared/active-slot" 2>/dev/null || echo "blue")
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

start_target_slot() {
    export "WAVE_IMAGE_${TARGET_SLOT^^}=$IMAGE_REF"
    if [ "$TARGET_SLOT" = "green" ]; then
        docker compose --profile green up -d "wave-${TARGET_SLOT}"
    else
        docker compose up -d "wave-${TARGET_SLOT}"
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
    # Reuse sanity_check() from PR #541 but target specific port
    SANITY_PORT=$port sanity_check
}

swap_traffic() {
    export WAVE_UPSTREAM="wave-${TARGET_SLOT}:9898"
    docker compose up -d --force-recreate caddy
}

schedule_cooldown() {
    local old_slot=$1
    # Kill any existing cooldown
    if [ -f "$DEPLOY_ROOT/shared/cooldown-pid" ]; then
        kill "$(cat "$DEPLOY_ROOT/shared/cooldown-pid")" 2>/dev/null || true
    fi
    # Schedule old slot stop after 30 minutes
    (sleep 1800 && docker compose stop "wave-${old_slot}" 2>/dev/null) &
    echo $! > "$DEPLOY_ROOT/shared/cooldown-pid"
}

update_state() {
    echo "$TARGET_SLOT" > "$DEPLOY_ROOT/shared/active-slot"
    echo "$ACTIVE_SLOT" > "$DEPLOY_ROOT/shared/previous-slot"
    echo "$IMAGE_REF" > "$DEPLOY_ROOT/shared/${TARGET_SLOT}-image"
}
```

### Modified deploy Command

```bash
deploy() {
    require_docker
    ensure_layout
    load_deploy_env
    acquire_lock
    determine_target_slot
    log "Deploying to ${TARGET_SLOT} slot (active: ${ACTIVE_SLOT})"
    retry pull_image
    start_target_slot
    if ! wait_for_slot_health "$TARGET_PORT"; then
        log "ERROR: ${TARGET_SLOT} health check failed"
        docker compose stop "wave-${TARGET_SLOT}"
        release_lock
        exit 1
    fi
    if ! sanity_check_slot "$TARGET_PORT"; then
        log "ERROR: ${TARGET_SLOT} sanity check failed"
        docker compose stop "wave-${TARGET_SLOT}"
        release_lock
        exit 1
    fi
    swap_traffic
    update_state
    schedule_cooldown "$ACTIVE_SLOT"
    release_lock
    log "Deploy complete. Active: ${TARGET_SLOT}"
}
```

### New rollback Command

```bash
rollback() {
    require_docker
    load_deploy_env
    acquire_lock
    local prev
    prev=$(cat "$DEPLOY_ROOT/shared/previous-slot" 2>/dev/null)
    if [ -z "$prev" ]; then
        log "ERROR: No previous slot to rollback to"
        release_lock
        exit 1
    fi
    # Ensure previous slot is running
    if ! docker compose ps "wave-${prev}" --format json | grep -q '"running"'; then
        local prev_image
        prev_image=$(cat "$DEPLOY_ROOT/shared/${prev}-image" 2>/dev/null)
        if [ -z "$prev_image" ]; then
            log "ERROR: No previous image recorded"
            release_lock
            exit 1
        fi
        export "WAVE_IMAGE_${prev^^}=$prev_image"
        docker compose up -d "wave-${prev}"
        wait_for_slot_health "$([ "$prev" = "blue" ] && echo 9898 || echo 9899)"
    fi
    export WAVE_UPSTREAM="wave-${prev}:9898"
    docker compose up -d --force-recreate caddy
    local current
    current=$(cat "$DEPLOY_ROOT/shared/active-slot")
    echo "$prev" > "$DEPLOY_ROOT/shared/active-slot"
    echo "$current" > "$DEPLOY_ROOT/shared/previous-slot"
    schedule_cooldown "$current"
    release_lock
    log "Rolled back to ${prev}"
}
```

### New status Command

```bash
status() {
    local active
    active=$(cat "$DEPLOY_ROOT/shared/active-slot" 2>/dev/null || echo "unknown")
    echo "Active slot: $active"
    echo "Blue image:  $(cat "$DEPLOY_ROOT/shared/blue-image" 2>/dev/null || echo 'none')"
    echo "Green image: $(cat "$DEPLOY_ROOT/shared/green-image" 2>/dev/null || echo 'none')"
    echo ""
    echo "Container status:"
    docker compose ps wave-blue wave-green 2>/dev/null || echo "  (compose not running)"
    if [ -f "$DEPLOY_ROOT/shared/cooldown-pid" ]; then
        local pid
        pid=$(cat "$DEPLOY_ROOT/shared/cooldown-pid")
        if kill -0 "$pid" 2>/dev/null; then
            echo "Cooldown timer active (PID $pid)"
        else
            echo "Cooldown timer expired"
        fi
    fi
}
```

## GitHub Actions Changes

### deploy-contabo.yml

Changes required:
1. Pass `IMAGE_REF` to deploy script (already done via env var)
2. The deploy script internally handles slot selection
3. Remove `update_config.order` reliance — blue-green replaces rolling updates

The workflow itself needs minimal changes since the deploy script encapsulates the blue-green logic. The main change is that the script now handles the swap internally rather than relying on Docker Compose rolling update behavior.

## Migration Path

### First Deploy After Migration

1. The current `wave` service is running.
2. The new compose file replaces `wave` with `wave-blue` + `wave-green`.
3. `docker compose up -d` will:
   - Stop old `wave` container (name no longer exists)
   - Start `wave-blue` with the current image
4. Initialize `active-slot` to "blue"
5. Subsequent deploys use the blue-green flow

### Migration Script

```bash
migrate_to_blue_green() {
    # Record current image as blue
    local current_image
    current_image=$(docker compose images wave --format json 2>/dev/null | jq -r '.[0].Repository + ":" + .[0].Tag')
    echo "blue" > "$DEPLOY_ROOT/shared/active-slot"
    echo "$current_image" > "$DEPLOY_ROOT/shared/blue-image"
    # Stop old wave service
    docker compose stop wave 2>/dev/null || true
    docker compose rm -f wave 2>/dev/null || true
    # Start wave-blue with current image
    export WAVE_IMAGE_BLUE="$current_image"
    docker compose up -d wave-blue
}
```

## Shared State Considerations

Both slots share:
- **MongoDB** — Safe. Wave operations are idempotent via OT versioning. Two instances can read/write concurrently without corruption.
- **Lucene indexes** — Each Wave instance builds its own in-memory index from MongoDB on startup. The `_indexes` volume is shared but each instance writes to the same directory. **Risk:** concurrent writes during the overlap window.
  - **Mitigation:** Since the old slot is only serving read traffic (no new writes once Caddy swaps) and stops within 30 minutes, the risk is minimal. Lucene index directory should use slot-specific subdirectories if concurrent indexing becomes an issue.
- **File stores** (`_accounts`, `_attachments`, `_deltas`, `_sessions`) — Read-mostly during overlap, safe to share.

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Health check fails | Target slot stopped, deploy exits non-zero, CI sends failure notification |
| Sanity check fails | Target slot stopped, deploy exits non-zero, CI sends failure notification |
| Caddy recreate fails | Target slot still running but not receiving traffic; manual intervention needed |
| Rollback requested but old slot already stopped | Re-pull old image from recorded ref, start, wait for health, then swap |
| Both slots fail | Manual intervention; `deploy.sh status` shows state |
| Deploy during existing deploy | File lock prevents concurrent deploys (30s wait, then fail) |

## Testing Plan

1. **Local Docker Compose test** — verify both services start, different ports work
2. **Staging deploy** — run full cycle on a non-production host
3. **Rollback test** — deploy, verify, rollback, verify old version serves
4. **Cooldown test** — verify old slot stops after 30 minutes
5. **Concurrent write test** — verify MongoDB OT operations work with two instances briefly overlapping
6. **CI integration test** — verify GitHub Actions workflow triggers deploy correctly

# Blue-Green Deployment Design

**Date:** 2026-04-01
**Status:** Draft (v6 — post-review round 5)
**PR Dependency:** #541 (deploy sanity check)

## Problem Statement

The current deployment uses Docker Compose with Caddy health-aware routing. Despite `deploy.update_config.order: start-first` being declared in compose.yml, this directive is **Swarm-only** and has no effect in plain `docker compose` mode. The actual behavior is stop-then-start, meaning:

1. **Downtime on every deploy** — the old container stops before the new one is ready.
2. **No approval gate** — there is no functional sanity check before users hit the new version.
3. **Slow rollback** — rollback requires re-pulling the previous image and restarting (~30-60s).

## Solution

True blue-green deployment with dual Wave services, per-slot release bundles, a sanity check gate (pre- and post-swap), and Caddy config reload (not container recreation).

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
│  │  :80/:443 │  (file-based upstream config)     │
│  └──────────┘                                    │
└─────────────────────────────────────────────────┘
```

Both Wave services bind to the container-internal port `9898` but map to different host ports:
- `wave-blue`: `127.0.0.1:9898:9898`
- `wave-green`: `127.0.0.1:9899:9898`

Both share the same MongoDB instance. They use **slot-specific** directories for Lucene indexes and sessions (see Shared State below).

### Per-Slot Release Directories

Each slot gets its own release bundle to ensure config/image consistency during rollback:

```
$DEPLOY_ROOT/
  releases/
    blue/
      application.conf    # Config rendered for blue's deploy
      image-ref           # ghcr.io/...@sha256:...
    green/
      application.conf    # Config rendered for green's deploy
      image-ref
  shared/
    active-slot           # "blue" or "green"
    previous-slot         # For rollback reference
    accounts/             # MongoDB-backed (shared safely)
    attachments/          # MongoDB-backed (shared safely)
    certificates/         # Shared (read-only at runtime)
    deltas/               # MongoDB-backed (shared safely)
    indexes/
      blue/               # Slot-specific Lucene directory
      green/              # Slot-specific Lucene directory
    sessions/
      blue/               # Slot-specific Jetty sessions
      green/              # Slot-specific Jetty sessions
    logs/                 # Shared (append-only, safe)
    mongo/db/             # Single MongoDB data dir
    caddy-config/         # Caddy runtime config
    caddy-data/           # Caddy ACME certs
    upstream.caddy        # Generated: active upstream definition
```

### Deployment Flow

```
1. CI builds Docker image, pushes to ghcr.io with SHA tag
2. CI SSHs to host, uploads bundle, runs deploy.sh deploy
3. deploy.sh determines inactive slot:
   - Reads $DEPLOY_ROOT/shared/active-slot
   - If "blue" → target = "green", port = 9899
   - If "green" → target = "blue", port = 9898
   - If file missing → initialize as "blue" active, target "green"
4. Cancel any existing cooldown timer BEFORE starting target
5. Pull new image for target slot (with retry)
6. Render application.conf into target slot's release dir
7. Store image ref in target slot's release dir
8. Set WAVE_IMAGE_{BLUE|GREEN} env var to new image ref
9. Start target slot: docker compose up -d wave-${target}
10. Wait for target slot health check (/healthz on target port)
    - 90 retries × 2s = 3 minutes max
11. Run sanity check against target slot (login, search, fetch)
    - Uses target port directly, not through Caddy
    - This is the PR #541 sanity_check() function
12. On sanity success:
    a. Generate upstream.caddy pointing to wave-${target}:9898
    b. Reload Caddy via admin API (no container restart)
    c. Run post-swap smoke through Caddy (proxy-level verification)
    d. If post-swap smoke fails: revert upstream.caddy, reload Caddy, stop target, exit 1
    e. Update active-slot state file
    f. Schedule old slot stop after 30 minutes (via systemd-run)
13. On sanity failure:
    a. Stop target slot
    b. Log failure, exit non-zero (triggers CI failure notification)
```

### Rollback Flow

```
1. deploy.sh rollback
2. Read previous-slot file → old_slot
3. Verify wave-${old_slot} container is still running
   - If not running (cooldown expired):
     a. Read image ref from releases/${old_slot}/image-ref
     b. Set WAVE_IMAGE env, start old slot
     c. Wait for health check
4. Run sanity check against old slot (verify it's actually healthy)
5. Generate upstream.caddy pointing to wave-${old_slot}:9898
6. Reload Caddy via admin API
7. Run post-swap smoke through Caddy
8. Update active-slot / previous-slot state files
9. Cancel active cooldown, schedule new cooldown for current slot
10. Done (instant if old container is still running)
```

### Status Command

```
deploy.sh status
  - Shows: active slot, inactive slot status, image refs per slot
  - Shows: Caddy upstream target
  - Shows: cooldown timer status (systemd timer query)
```

## Compose File Changes

### Proposed (dual services)

Note: YAML anchors cannot merge list items into another list. All volumes
are listed explicitly per service. An `x-wave-common` extension block
documents the shared shape but is NOT referenced via `*` anchors.

```yaml
x-wave-env: &wave-env
  RESEND_API_KEY: ${RESEND_API_KEY:-}
  WAVE_EMAIL_FROM: ${WAVE_EMAIL_FROM:-}
  WAVE_MAIL_PROVIDER: ${WAVE_MAIL_PROVIDER:-log}
  WAVE_SERVER_VERSION: ${RELEASE_ID:-dev}

services:
  mongo:
    image: mongo:6.0
    restart: unless-stopped
    ports: ["127.0.0.1:27017:27017"]
    volumes:
      - ${DEPLOY_ROOT}/shared/mongo/db:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s

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
    volumes:
      - ${DEPLOY_ROOT}/releases/blue/application.conf:/opt/wave/config/application.conf:ro
      - ${DEPLOY_ROOT}/shared/accounts:/opt/wave/_accounts
      - ${DEPLOY_ROOT}/shared/attachments:/opt/wave/_attachments
      - ${DEPLOY_ROOT}/shared/certificates:/opt/wave/_certificates:ro
      - ${DEPLOY_ROOT}/shared/deltas:/opt/wave/_deltas
      - ${DEPLOY_ROOT}/shared/indexes/blue:/opt/wave/_indexes
      - ${DEPLOY_ROOT}/shared/sessions/blue:/opt/wave/_sessions
      - ${DEPLOY_ROOT}/shared/logs:/opt/wave/logs
    environment: *wave-env

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
    volumes:
      - ${DEPLOY_ROOT}/releases/green/application.conf:/opt/wave/config/application.conf:ro
      - ${DEPLOY_ROOT}/shared/accounts:/opt/wave/_accounts
      - ${DEPLOY_ROOT}/shared/attachments:/opt/wave/_attachments
      - ${DEPLOY_ROOT}/shared/certificates:/opt/wave/_certificates:ro
      - ${DEPLOY_ROOT}/shared/deltas:/opt/wave/_deltas
      - ${DEPLOY_ROOT}/shared/indexes/green:/opt/wave/_indexes
      - ${DEPLOY_ROOT}/shared/sessions/green:/opt/wave/_sessions
      - ${DEPLOY_ROOT}/shared/logs:/opt/wave/logs
    environment: *wave-env
    profiles: ["green"]

  caddy:
    image: caddy:2.8.4-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      CANONICAL_HOST: ${CANONICAL_HOST}
      ROOT_HOST: ${ROOT_HOST}
      WWW_HOST: ${WWW_HOST}
    volumes:
      - ${DEPLOY_ROOT}/releases/current/Caddyfile:/etc/caddy/Caddyfile:ro
      - ${DEPLOY_ROOT}/shared/upstream.caddy:/etc/caddy/upstream.caddy:ro
      - ${DEPLOY_ROOT}/shared/caddy-data:/data
      - ${DEPLOY_ROOT}/shared/caddy-config:/config
```

Key changes from current:
- Two wave services with **slot-specific** index and session volumes
- Each slot mounts its own `releases/{slot}/application.conf`
- Caddy imports an `upstream.caddy` file (generated by deploy script)
- `wave-green` uses `profiles: ["green"]` so it doesn't start by default
- Both use `scratch` as default image (won't start without explicit image set)
- Caddy has no `depends_on` — it starts independently and discovers the active upstream via `upstream.caddy`

### `releases/current` Directory

`$DEPLOY_ROOT/releases/current/` contains the **control-plane assets** shared
across both slots: `compose.yml`, `Caddyfile`, and `deploy.sh`. These are
NOT slot-specific — they define the infrastructure topology. Each deploy
overwrites these from the incoming bundle. Slot-specific assets
(`application.conf`, `image-ref`) live under `releases/{blue,green}/`.

The `deploy.sh deploy` command copies incoming control-plane assets to
`releases/current/` before starting the target slot:

```bash
activate_release() {
    local release_dir="$DEPLOY_ROOT/releases/current"
    mkdir -p "$release_dir"
    cp "$DEPLOY_ROOT/incoming/compose.yml" "$release_dir/"
    cp "$DEPLOY_ROOT/incoming/Caddyfile" "$release_dir/"
    cp "$DEPLOY_ROOT/incoming/deploy.sh" "$release_dir/"
}
```

### Caddyfile Changes

```caddy
{$CANONICAL_HOST} {
    encode zstd gzip
    import /etc/caddy/upstream.caddy
}

{$ROOT_HOST} { redir https://{$CANONICAL_HOST}{uri} permanent }
{$WWW_HOST}  { redir https://{$CANONICAL_HOST}{uri} permanent }
```

The `upstream.caddy` file is generated by the deploy script:

```caddy
# Generated by deploy.sh — do not edit
reverse_proxy wave-blue:9898 {
    health_uri /healthz
    health_interval 2s
    health_timeout 3s
    lb_try_duration 5s
    lb_try_interval 250ms
}
```

The swap writes a new `upstream.caddy` pointing to the target slot, then reloads Caddy via its admin API: `curl -X POST http://localhost:2019/load -H "Content-Type: text/caddyfile" -d @/etc/caddy/Caddyfile`. This avoids container restart and preserves in-flight connections.

## Deploy Script Changes

### Compose Helper

All docker compose calls go through a helper that pins project name and file.
The project name MUST match the existing deployment's project name to avoid
creating a second stack (which would conflict on ports). Read it from
`deploy.env` or detect from running containers:

```bash
COMPOSE_FILE="$DEPLOY_ROOT/releases/current/compose.yml"

detect_project_name() {
    # Priority 1: explicit PROJECT_NAME in deploy.env (set by load_deploy_env)
    if [ -n "${PROJECT_NAME:-}" ]; then
        : # already set
    # Priority 2: COMPOSE_PROJECT_NAME env var
    elif [ -n "${COMPOSE_PROJECT_NAME:-}" ]; then
        PROJECT_NAME="$COMPOSE_PROJECT_NAME"
    # Priority 3: derive from DEPLOY_ROOT directory name (matches Compose default)
    else
        PROJECT_NAME=$(basename "$DEPLOY_ROOT")
        PROJECT_NAME="${PROJECT_NAME:-wave}"
    fi
    export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
}

dc() {
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}
```

**Important:** `PROJECT_NAME` MUST be added to `deploy.env` on the host
(e.g. `PROJECT_NAME=supawave`) to match the existing stack. The
`detect_project_name()` function is called once at the top of every
command (deploy, rollback, status) after `load_deploy_env()`. The GitHub
Actions workflow should also pass it as an environment variable.

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

cancel_cooldown() {
    # Cancel any existing systemd timer
    local systemd_scope=""
    if [ "$(id -u)" -ne 0 ]; then
        systemd_scope="--user"
    fi
    systemctl $systemd_scope stop "wave-cooldown.service" 2>/dev/null || true
    systemctl $systemd_scope stop "wave-cooldown.timer" 2>/dev/null || true
}

start_target_slot() {
    export "WAVE_IMAGE_${TARGET_SLOT^^}=$IMAGE_REF"
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
    # Reuse sanity_check() from PR #541 but target specific port
    SANITY_PORT=$port sanity_check
}

ensure_caddy_bootstrap() {
    # Ensure upstream.caddy exists before Caddy starts (first deploy)
    if [ ! -f "$DEPLOY_ROOT/shared/upstream.caddy" ]; then
        local active
        active=$(cat "$DEPLOY_ROOT/shared/active-slot" 2>/dev/null || echo "blue")
        generate_upstream "$active"
    fi
    # Ensure Caddy is running (may not be on first deploy)
    if ! dc ps caddy --format json 2>/dev/null | grep -q '"running"'; then
        dc up -d caddy
    fi
}

generate_upstream() {
    local slot=$1
    # Write to temp file first, then atomically move — prevents Caddy from
    # reading a half-written file if it restarts during write
    cat > "$DEPLOY_ROOT/shared/upstream.caddy.tmp" <<UPSTREAM
# active: ${slot}
reverse_proxy wave-${slot}:9898 {
    health_uri /healthz
    health_interval 2s
    health_timeout 3s
    lb_try_duration 5s
    lb_try_interval 250ms
}
UPSTREAM
    mv "$DEPLOY_ROOT/shared/upstream.caddy.tmp" "$DEPLOY_ROOT/shared/upstream.caddy"
}

reload_caddy() {
    # Reload Caddy config without restarting the container
    dc exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

post_swap_smoke() {
    # Verify through the proxy: TLS, Host header, redirects, content
    local canonical="${CANONICAL_HOST}"
    local retries=10
    local i=0

    # Use --resolve to test against localhost without DNS dependency
    # This validates TLS termination, Host header routing, and upstream health
    while [ $i -lt $retries ]; do
        if curl -sf --resolve "${canonical}:443:127.0.0.1" \
                "https://${canonical}/healthz" > /dev/null 2>&1; then
            break
        fi
        sleep 2
        i=$((i + 1))
    done
    if [ $i -ge $retries ]; then
        log "ERROR: proxy health check failed after $retries retries"
        return 1
    fi

    # Verify redirect from root host works
    local redirect_status
    redirect_status=$(curl -so /dev/null -w "%{http_code}" \
        --resolve "${ROOT_HOST}:443:127.0.0.1" \
        "https://${ROOT_HOST}/" 2>/dev/null || echo "000")
    if [ "$redirect_status" != "301" ] && [ "$redirect_status" != "308" ]; then
        log "WARNING: root host redirect returned $redirect_status (expected 301/308)"
        # Non-fatal: redirect misconfiguration is cosmetic
    fi

    # Verify WebSocket upgrade is possible (connection upgrade header accepted)
    local ws_status
    ws_status=$(curl -so /dev/null -w "%{http_code}" \
        --resolve "${canonical}:443:127.0.0.1" \
        -H "Upgrade: websocket" -H "Connection: Upgrade" \
        -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
        -H "Sec-WebSocket-Version: 13" \
        "https://${canonical}/socket" 2>/dev/null || echo "000")
    if [ "$ws_status" != "101" ] && [ "$ws_status" != "400" ]; then
        log "WARNING: websocket upgrade returned $ws_status (expected 101 or 400)"
        # Non-fatal: WS endpoint may not be at /socket
    fi

    return 0
}

schedule_cooldown() {
    local old_slot=$1
    # Use systemd-run for durable timer (survives SSH disconnect)
    # --user requires loginctl enable-linger for the deploy user;
    # if running as root or via sudo, omit --user.
    local systemd_scope=""
    if [ "$(id -u)" -ne 0 ]; then
        systemd_scope="--user"
    fi
    # Cancel any prior cooldown first
    systemctl $systemd_scope stop "wave-cooldown.service" 2>/dev/null || true
    if ! systemd-run $systemd_scope --on-active=30min --unit="wave-cooldown" \
        -- docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" stop "wave-${old_slot}" 2>/dev/null; then
        log "WARNING: failed to schedule cooldown timer — old slot will stay running until next deploy"
        # Non-fatal: the deploy itself succeeded; old slot stays up harmlessly
    fi
}

render_slot_config() {
    local slot=$1
    local release_dir="$DEPLOY_ROOT/releases/${slot}"
    mkdir -p "$release_dir"
    cp "$DEPLOY_ROOT/incoming/application.conf" "$release_dir/application.conf"
    # Substitute canonical host
    perl -pi -e "s/wave\\.example\\.test/${CANONICAL_HOST}/g" "$release_dir/application.conf"
    echo "$IMAGE_REF" > "$release_dir/image-ref"
}

update_state() {
    echo "$TARGET_SLOT" > "$DEPLOY_ROOT/shared/active-slot"
    echo "$ACTIVE_SLOT" > "$DEPLOY_ROOT/shared/previous-slot"
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

    # Cancel any existing cooldown BEFORE starting new slot
    cancel_cooldown

    activate_release
    migrate_to_blue_green   # no-op if already migrated
    detect_project_name
    ensure_caddy_bootstrap
    retry pull_image
    render_slot_config "$TARGET_SLOT"
    start_target_slot

    if ! wait_for_slot_health "$TARGET_PORT"; then
        log "ERROR: ${TARGET_SLOT} health check failed"
        dc stop "wave-${TARGET_SLOT}"
        release_lock
        exit 1
    fi

    if ! sanity_check_slot "$TARGET_PORT"; then
        log "ERROR: ${TARGET_SLOT} sanity check failed"
        dc stop "wave-${TARGET_SLOT}"
        release_lock
        exit 1
    fi

    # Swap traffic
    generate_upstream "$TARGET_SLOT"
    reload_caddy

    # Post-swap smoke through proxy
    if ! post_swap_smoke; then
        log "ERROR: post-swap smoke failed, reverting"
        generate_upstream "$ACTIVE_SLOT"
        reload_caddy
        dc stop "wave-${TARGET_SLOT}"
        release_lock
        exit 1
    fi

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

    local prev_port
    prev_port=$([ "$prev" = "blue" ] && echo 9898 || echo 9899)

    # Ensure previous slot is running
    if ! dc ps "wave-${prev}" --format json | grep -q '"running"'; then
        local prev_image
        prev_image=$(cat "$DEPLOY_ROOT/releases/${prev}/image-ref" 2>/dev/null)
        if [ -z "$prev_image" ]; then
            log "ERROR: No previous image recorded"
            release_lock
            exit 1
        fi
        export "WAVE_IMAGE_${prev^^}=$prev_image"
        if [ "$prev" = "green" ]; then
            dc --profile green up -d "wave-${prev}"
        else
            dc up -d "wave-${prev}"
        fi
        if ! wait_for_slot_health "$prev_port"; then
            log "ERROR: Previous slot health check failed"
            release_lock
            exit 1
        fi
    fi

    # Verify old slot is actually healthy before swapping
    if ! sanity_check_slot "$prev_port"; then
        log "ERROR: Previous slot sanity check failed — cannot rollback"
        release_lock
        exit 1
    fi

    cancel_cooldown
    generate_upstream "$prev"
    reload_caddy

    if ! post_swap_smoke; then
        log "ERROR: post-rollback proxy smoke failed, reverting Caddy to current slot"
        local current_active
        current_active=$(cat "$DEPLOY_ROOT/shared/active-slot")
        generate_upstream "$current_active"
        reload_caddy
        release_lock
        exit 1
    fi

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
    load_deploy_env
    detect_project_name
    local active
    active=$(cat "$DEPLOY_ROOT/shared/active-slot" 2>/dev/null || echo "unknown")
    echo "Active slot: $active"
    echo ""
    echo "Blue:"
    echo "  Image: $(cat "$DEPLOY_ROOT/releases/blue/image-ref" 2>/dev/null || echo 'none')"
    echo "Green:"
    echo "  Image: $(cat "$DEPLOY_ROOT/releases/green/image-ref" 2>/dev/null || echo 'none')"
    echo ""
    echo "Container status:"
    dc ps wave-blue wave-green 2>/dev/null || echo "  (compose not running)"
    echo ""
    echo "Caddy upstream:"
    grep -m1 "reverse_proxy" "$DEPLOY_ROOT/shared/upstream.caddy" 2>/dev/null || echo "  (not configured)"
    echo ""
    echo "Cooldown timer:"
    local systemd_scope=""
    [ "$(id -u)" -ne 0 ] && systemd_scope="--user"
    systemctl $systemd_scope status wave-cooldown.timer 2>/dev/null || echo "  (none active)"
}
```

## Shared State Considerations

### Safe to Share

- **MongoDB** — Production uses MongoDB for accounts, deltas, attachments, contacts. Wave operations are idempotent via OT versioning. Two instances can read/write concurrently without corruption.
- **Certificates** (`_certificates`) — Read-only at runtime, safe to share.
- **Logs** — Append-only, safe to share.

### Must Be Slot-Specific

- **Lucene indexes** (`_indexes/{blue,green}`) — `LuceneIndexWriterFactory` acquires an exclusive write lock on the index directory. Two instances competing for the same lock will cause the second to fail after ~60s of retries. Each slot gets its own index directory. Both rebuild from MongoDB on startup independently.

- **Sessions** (`_sessions/{blue,green}`) — Jetty `FileSessionDataStore` is not designed for concurrent JVM access. Additionally, session format/state may differ between versions. Each slot gets its own session directory. Users on the old slot will need to re-authenticate after swap (existing behavior — the JWT restoration filter already handles this gracefully).

### Layout Initialization

The `ensure_layout()` function must create slot-specific subdirectories:

```bash
ensure_layout() {
    # ... existing shared dirs ...
    mkdir -p "$DEPLOY_ROOT/shared/indexes/blue"
    mkdir -p "$DEPLOY_ROOT/shared/indexes/green"
    mkdir -p "$DEPLOY_ROOT/shared/sessions/blue"
    mkdir -p "$DEPLOY_ROOT/shared/sessions/green"
    mkdir -p "$DEPLOY_ROOT/releases/blue"
    mkdir -p "$DEPLOY_ROOT/releases/green"
}
```

## Caddy Reload Strategy

The spec uses Caddy's built-in reload capability instead of container recreation:

1. **Why not `docker compose up -d --force-recreate caddy`?** — Recreating the Caddy container tears down the listener on `:80/:443`, dropping in-flight HTTP requests and WebSocket connections. This defeats the zero-downtime goal.

2. **How reload works:** `caddy reload --config /path/to/Caddyfile --adapter caddyfile` atomically swaps the running config. Existing connections are drained gracefully. New connections immediately use the new upstream.

3. **Caddy admin API must be enabled.** The default Caddy config enables the admin API on `localhost:2019`. This is already the case in the stock Caddy Docker image.

4. **Import directive:** The Caddyfile uses `import /etc/caddy/upstream.caddy` to pull in the generated upstream block. This file is volume-mounted from `$DEPLOY_ROOT/shared/upstream.caddy`.

## GitHub Actions Changes

### deploy-contabo.yml

Changes needed:

1. **Bundle extraction path** — the current workflow extracts to `releases/$RELEASE_ID`
   then symlinks `releases/current`. The deploy script's `activate_release()` copies
   from `$DEPLOY_ROOT/incoming/*` to `releases/current/`. The workflow must extract
   to `$DEPLOY_ROOT/incoming/` instead of `releases/$RELEASE_ID`, OR the deploy script
   must be updated to read from the workflow's extraction path. **Recommended:** update
   the workflow to extract to `incoming/` since that's simpler and matches the spec.
2. **PROJECT_NAME env var** — add to the SSH command environment (read from deploy secrets).
3. **Rollback** — can be triggered manually via `workflow_dispatch` with a `rollback` input.
4. **Bundle assembly** — already packs compose.yml, Caddyfile, application.conf, deploy.sh.
5. **New secrets** — `PROJECT_NAME` should be added to deployment environment.

Optional addition: a `rollback` workflow_dispatch input:
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
```

## Migration Path

### Prerequisites

1. PR #541 (sanity check) must be merged first.
2. `systemd-run` must be available on the deploy host (standard on systemd-based Linux).
3. If deploying as a non-root user, `loginctl enable-linger <user>` must be run once
   so that `systemd-run --user` timers survive SSH disconnect.

### First Deploy After Migration

1. The migration runs as part of the **first deploy that includes the new
   blue-green compose file**. The CI deploy uploads the new compose.yml
   (with `wave-blue`/`wave-green` services) to `$DEPLOY_ROOT/incoming/`,
   then `deploy.sh deploy` detects the missing `active-slot` file and
   runs migration automatically.

```bash
migrate_to_blue_green() {
    local deploy_root="${DEPLOY_ROOT:?}"

    # Guard: skip if already migrated
    if [ -f "$deploy_root/shared/active-slot" ]; then
        log "Already migrated (active-slot exists). Skipping."
        return 0
    fi

    # Create slot-specific directories
    mkdir -p "$deploy_root/releases/blue" "$deploy_root/releases/green"
    mkdir -p "$deploy_root/shared/indexes/blue" "$deploy_root/shared/indexes/green"
    mkdir -p "$deploy_root/shared/sessions/blue" "$deploy_root/shared/sessions/green"

    # Detect project name from deploy.env or directory
    load_deploy_env 2>/dev/null || true
    detect_project_name

    # Find the OLD compose file to detect running state
    local old_compose="$deploy_root/releases/current/compose.yml"
    if [ ! -f "$old_compose" ]; then
        log "No existing compose file — fresh install, skipping migration"
        echo "blue" > "$deploy_root/shared/active-slot"
        return 0
    fi

    local current_image
    current_image=$(docker compose -f "$old_compose" -p "$PROJECT_NAME" \
        images wave --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | head -1)
    if [ -z "$current_image" ] || [ "$current_image" = ":" ]; then
        log "No running 'wave' service found — fresh install, skipping container migration"
        echo "blue" > "$deploy_root/shared/active-slot"
        return 0
    fi

    # Step 1: Stop old container using OLD compose (releases port 9898 + file locks)
    log "Stopping old 'wave' container..."
    docker compose -f "$old_compose" -p "$PROJECT_NAME" stop wave
    docker compose -f "$old_compose" -p "$PROJECT_NAME" rm -f wave

    # Step 2: Move index/session files safely (old container is stopped)
    local f
    for f in "$deploy_root/shared/indexes"/*; do
        [ -f "$f" ] && mv "$f" "$deploy_root/shared/indexes/blue/" 2>/dev/null || true
    done
    for f in "$deploy_root/shared/sessions"/*; do
        [ -f "$f" ] && mv "$f" "$deploy_root/shared/sessions/blue/" 2>/dev/null || true
    done

    # Step 3: Record state
    echo "blue" > "$deploy_root/shared/active-slot"
    echo "$current_image" > "$deploy_root/releases/blue/image-ref"
    if [ -f "$deploy_root/releases/current/application.conf" ]; then
        cp "$deploy_root/releases/current/application.conf" \
           "$deploy_root/releases/blue/application.conf"
    fi

    # Step 4: Generate upstream.caddy
    cat > "$deploy_root/shared/upstream.caddy" <<'UPSTREAM'
reverse_proxy wave-blue:9898 {
    health_uri /healthz
    health_interval 2s
    health_timeout 3s
    lb_try_duration 5s
    lb_try_interval 250ms
}
UPSTREAM

    # Step 5: Install NEW compose/Caddyfile to releases/current
    #         (activate_release was already called by deploy() before migration)
    # The NEW compose file defines wave-blue/wave-green, so we use it from here

    # Step 6: Start wave-blue using NEW compose file (now in releases/current)
    export WAVE_IMAGE_BLUE="$current_image"
    dc up -d wave-blue caddy

    log "Migration complete. Active slot: blue (brief downtime during migration is expected)"
}
```

2. The migration is guarded by the `active-slot` file — running it twice is a no-op.
3. Fresh installs (no running `wave` container) skip container migration and just initialize state.
4. After migration, all subsequent deploys use the blue-green flow automatically.

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Health check fails | Target slot stopped, deploy exits non-zero, CI sends failure notification |
| Sanity check fails | Target slot stopped, deploy exits non-zero, CI sends failure notification |
| Post-swap proxy smoke fails | Upstream reverted to old slot, Caddy reloaded, target stopped, exit non-zero |
| Caddy reload fails | Target slot running but not receiving traffic; manual intervention needed |
| Rollback requested but old slot stopped | Re-pull from recorded image-ref, start, health+sanity check, then swap |
| Rollback requested but old slot unhealthy | Sanity check catches this — rollback aborted with error |
| Both slots fail | Manual intervention; `deploy.sh status` shows state |
| Deploy during existing deploy | File lock prevents concurrent deploys (30s wait, then fail) |
| SSH disconnects mid-deploy | Lock auto-releases on process exit; cooldown timer persists via systemd |
| Cooldown timer after host reboot | systemd transient timer is lost; old slot stays up until next deploy cleans it up (harmless) |

## Health Check Limitations

The current `/healthz` and `/readyz` endpoints are identical — they return `200 ok` as soon as the servlet container starts. They do **not** verify:
- Lucene index is built and queryable
- MongoDB connection pool is warmed
- WebSocket endpoint is ready

The sanity check (PR #541) compensates by testing login, search, and wave fetch — these exercise the full stack including Lucene and MongoDB. The health check serves only as a "process is alive" signal; the sanity check is the true readiness gate.

**Future improvement:** Add a `/readyz` endpoint that checks Lucene index state and MongoDB connectivity. This is out of scope for this design.

## Testing Plan

1. **Local Docker Compose test** — verify both services start on different ports, slot-specific volumes are isolated
2. **Caddy reload test** — verify `caddy reload` swaps upstream without dropping connections
3. **Migration test** — run migration script on a copy of production layout, verify blue slot starts correctly
4. **Full deploy cycle** — deploy to green, sanity check, swap, verify through proxy
5. **Rollback test** — deploy, swap, rollback, verify old version serves correctly
6. **Cooldown test** — verify systemd timer stops old slot after 30 minutes
7. **Post-swap smoke test** — verify proxy-level health, TLS termination, WebSocket upgrade
8. **Concurrent Lucene test** — verify two slots with separate index dirs don't conflict
9. **Session isolation test** — verify login on blue doesn't create session files in green's directory
10. **CI integration test** — verify GitHub Actions workflow triggers deploy and rollback correctly

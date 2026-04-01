# MongoDB Backup Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automated MongoDB backups every 6 hours with 10-backup retention, Docker-aware manual backup/restore scripts, and cron installer for the supawave production host.

**Architecture:** Scripts use `docker exec` to run `mongodump`/`mongorestore` inside the existing `supawave-mongo-1` container, streaming archives to the host filesystem. A cron installer script handles one-time setup including script deployment and crontab configuration.

**Tech Stack:** Bash, Docker, mongodump/mongorestore, crontab

**Spec:** `docs/superpowers/specs/2026-04-01-mongodb-backup-automation-design.md`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `deploy/mongo/backup.sh` | Rewrite | Docker-aware mongodump with rotation, disk check, atomic write |
| `deploy/mongo/restore.sh` | Rewrite | Docker-aware mongorestore with listing and confirmation |
| `deploy/mongo/install-cron.sh` | Create | One-time host setup: copy scripts, install crontab |
| `deploy/mongo/README.md` | Rewrite | Operator documentation: backup, restore, cron, troubleshooting |
| `docs/deployment/mongo-hardening.md` | Modify (section 2) | Sync backup section with new scripts/schedule |

---

### Task 1: Write backup.sh

**Files:**
- Rewrite: `deploy/mongo/backup.sh`

- [ ] **Step 1: Write backup.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

# MongoDB Backup Script (Docker-aware)
# Runs mongodump inside the Mongo container, streams archive to host filesystem.
# Rotates old backups to keep only the newest KEEP_COUNT archives.

DEPLOY_ROOT=${DEPLOY_ROOT:-/home/ubuntu/supawave}
BACKUP_DIR=${BACKUP_DIR:-$DEPLOY_ROOT/shared/mongo/backups}
MONGO_CONTAINER=${MONGO_CONTAINER:-supawave-mongo-1}
MONGO_DATABASE=${MONGO_DATABASE:-wiab}
KEEP_COUNT=${KEEP_COUNT:-10}
MIN_DISK_MB=${MIN_DISK_MB:-1024}

umask 077

log() {
  printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2
}

check_disk_space() {
  local avail_kb
  avail_kb=$(df -k "$BACKUP_DIR" | awk 'NR==2 {print $4}')
  local avail_mb=$((avail_kb / 1024))
  if [[ $avail_mb -lt $MIN_DISK_MB ]]; then
    log "ERROR: only ${avail_mb} MB free on backup partition (minimum: ${MIN_DISK_MB} MB)"
    exit 2
  fi
}

rotate_backups() {
  local backups
  backups=$(ls -1 "$BACKUP_DIR"/wiab-*.archive.gz 2>/dev/null | sort)
  local count
  count=$(echo "$backups" | grep -c . || true)
  if [[ $count -gt $KEEP_COUNT ]]; then
    local to_delete=$((count - KEEP_COUNT))
    echo "$backups" | head -n "$to_delete" | while read -r old; do
      log "rotating out: $(basename "$old")"
      rm -f "$old"
    done
  fi
}

main() {
  mkdir -p "$BACKUP_DIR"
  check_disk_space

  local timestamp
  timestamp=$(date -u +%Y%m%d-%H%M%S)
  local archive_name="wiab-${timestamp}.archive.gz"
  local archive_path="${BACKUP_DIR}/${archive_name}"
  local tmp_path="${archive_path}.tmp"

  # Cleanup partial file on failure
  trap 'rm -f "$tmp_path"' EXIT

  log "starting backup of database '${MONGO_DATABASE}' from container '${MONGO_CONTAINER}'"

  docker exec "$MONGO_CONTAINER" \
    mongodump --db="$MONGO_DATABASE" --archive --gzip \
    > "$tmp_path"

  # Validate
  if [[ ! -s "$tmp_path" ]]; then
    log "ERROR: backup produced empty file"
    exit 1
  fi

  # Atomic rename
  mv "$tmp_path" "$archive_path"
  trap - EXIT

  log "backup complete: ${archive_path} ($(du -h "$archive_path" | cut -f1))"

  rotate_backups

  # Print archive path to stdout for cron log / scripting
  printf '%s\n' "$archive_path"
}

main "$@"
```

- [ ] **Step 2: Verify backup.sh is syntactically valid**

Run: `bash -n deploy/mongo/backup.sh`
Expected: no output (clean parse)

- [ ] **Step 3: Commit**

```bash
git add deploy/mongo/backup.sh
git commit -m "feat: rewrite backup.sh with Docker-aware mongodump and rotation"
```

---

### Task 2: Write restore.sh

**Files:**
- Rewrite: `deploy/mongo/restore.sh`

- [ ] **Step 1: Write restore.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

# MongoDB Restore Script (Docker-aware)
# Pipes a gzipped archive into mongorestore inside the Mongo container.
# With no arguments, lists available backups.

DEPLOY_ROOT=${DEPLOY_ROOT:-/home/ubuntu/supawave}
BACKUP_DIR=${BACKUP_DIR:-$DEPLOY_ROOT/shared/mongo/backups}
MONGO_CONTAINER=${MONGO_CONTAINER:-supawave-mongo-1}
MONGO_DATABASE=${MONGO_DATABASE:-wiab}

log() {
  printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2
}

list_backups() {
  log "available backups in ${BACKUP_DIR}:"
  if ls -1 "$BACKUP_DIR"/wiab-*.archive.gz >/dev/null 2>&1; then
    ls -1t "$BACKUP_DIR"/wiab-*.archive.gz | while read -r f; do
      printf '  %s  (%s)\n' "$(basename "$f")" "$(du -h "$f" | cut -f1)" >&2
    done
  else
    log "  (none)"
  fi
}

main() {
  local yes_flag=false
  local archive_path=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --yes|-y) yes_flag=true; shift ;;
      -*) log "unknown flag: $1"; exit 64 ;;
      *) archive_path="$1"; shift ;;
    esac
  done

  if [[ -z "$archive_path" ]]; then
    list_backups
    exit 0
  fi

  if [[ ! -f "$archive_path" ]]; then
    log "ERROR: archive not found: ${archive_path}"
    exit 66
  fi

  if [[ ! -s "$archive_path" ]]; then
    log "ERROR: archive is empty: ${archive_path}"
    exit 66
  fi

  if [[ "$yes_flag" != "true" ]]; then
    printf 'This will DROP all data in database "%s" and restore from:\n  %s\nContinue? [y/N] ' \
      "$MONGO_DATABASE" "$archive_path" >&2
    local answer
    read -r answer
    case "$answer" in
      [yY]|[yY][eE][sS]) ;;
      *) log "aborted by user"; exit 1 ;;
    esac
  fi

  log "restoring database '${MONGO_DATABASE}' from $(basename "$archive_path")"

  docker exec -i "$MONGO_CONTAINER" \
    mongorestore --db="$MONGO_DATABASE" --archive --gzip --drop \
    < "$archive_path"

  log "restore complete"
}

main "$@"
```

- [ ] **Step 2: Verify restore.sh is syntactically valid**

Run: `bash -n deploy/mongo/restore.sh`
Expected: no output (clean parse)

- [ ] **Step 3: Commit**

```bash
git add deploy/mongo/restore.sh
git commit -m "feat: rewrite restore.sh with Docker-aware mongorestore and listing"
```

---

### Task 3: Write install-cron.sh

**Files:**
- Create: `deploy/mongo/install-cron.sh`

- [ ] **Step 1: Write install-cron.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

# MongoDB Backup Cron Installer
# One-time setup: copies scripts to shared/mongo/, installs crontab entry.
# Safe to re-run — updates scripts and repairs stale cron entries.

DEPLOY_ROOT=${DEPLOY_ROOT:-/home/ubuntu/supawave}
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

SHARED_MONGO="${DEPLOY_ROOT}/shared/mongo"
SHARED_LOGS="${DEPLOY_ROOT}/shared/logs"
BACKUP_SCRIPT="${SHARED_MONGO}/backup.sh"
CRON_MARKER="# wave-mongo-backup"
CRON_JOB="0 */6 * * * ${BACKUP_SCRIPT} >> ${SHARED_LOGS}/backup.log 2>&1 ${CRON_MARKER}"

log() {
  printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2
}

copy_scripts() {
  mkdir -p "$SHARED_MONGO" "$SHARED_LOGS" "${SHARED_MONGO}/backups"

  cp "$SCRIPT_DIR/backup.sh" "$SHARED_MONGO/backup.sh"
  cp "$SCRIPT_DIR/restore.sh" "$SHARED_MONGO/restore.sh"
  chmod +x "$SHARED_MONGO/backup.sh" "$SHARED_MONGO/restore.sh"

  log "scripts copied to ${SHARED_MONGO}/"
}

install_cron() {
  local current_cron
  current_cron=$(crontab -l 2>/dev/null || true)

  # Check if our marker exists in current crontab
  if echo "$current_cron" | grep -qF "$CRON_MARKER"; then
    # Check if CRON_TZ=UTC line precedes the job
    local tz_and_job
    tz_and_job=$(echo "$current_cron" | grep -B1 "$CRON_MARKER" | head -1)
    if [[ "$tz_and_job" == "CRON_TZ=UTC" ]]; then
      log "cron job already installed and correct:"
      echo "$current_cron" | grep -B1 "$CRON_MARKER" >&2
      return 0
    fi

    # Stale entry — remove it (and any CRON_TZ line before it)
    log "removing stale cron entry"
    current_cron=$(echo "$current_cron" | grep -v "$CRON_MARKER")
    # Also remove orphaned CRON_TZ=UTC lines that we may have inserted before
    # (only remove if not followed by another job)
  fi

  # Append new two-line entry
  local new_cron
  new_cron=$(printf '%s\nCRON_TZ=UTC\n%s\n' "$current_cron" "$CRON_JOB")
  echo "$new_cron" | crontab -

  log "cron job installed:"
  log "  CRON_TZ=UTC"
  log "  ${CRON_JOB}"
}

main() {
  copy_scripts
  install_cron
  log "setup complete. backups will run every 6 hours (UTC)."
  log "verify with: crontab -l | grep wave-mongo-backup"
}

main "$@"
```

- [ ] **Step 2: Verify install-cron.sh is syntactically valid**

Run: `bash -n deploy/mongo/install-cron.sh`
Expected: no output (clean parse)

- [ ] **Step 3: Commit**

```bash
git add deploy/mongo/install-cron.sh
git commit -m "feat: add install-cron.sh for one-time backup cron setup"
```

---

### Task 4: Rewrite deploy/mongo/README.md

**Files:**
- Rewrite: `deploy/mongo/README.md`

- [ ] **Step 1: Write README.md**

```markdown
# MongoDB Backup and Restore

Operational scripts for backing up and restoring the Wave MongoDB database
(`wiab`) on the production host. Scripts run `mongodump`/`mongorestore` inside
the existing Docker container — no host-side Mongo tools needed.

## Quick Start

### Manual backup

```bash
# From anywhere on the host:
/home/ubuntu/supawave/shared/mongo/backup.sh
```

The archive is saved to `/home/ubuntu/supawave/shared/mongo/backups/` and the
path is printed to stdout. Old backups beyond the retention count are
automatically deleted.

### Manual restore

```bash
# List available backups:
/home/ubuntu/supawave/shared/mongo/restore.sh

# Restore a specific backup (will prompt for confirmation):
cd /home/ubuntu/supawave
docker compose -p supawave -f current/compose.yml stop wave
./shared/mongo/restore.sh shared/mongo/backups/wiab-20260401-060000.archive.gz
docker compose -p supawave -f current/compose.yml start wave
```

Stop Wave before restoring to prevent writes during the drop-and-replace. The
`-p supawave -f current/compose.yml` flags match the project name used by
`deploy.sh`.

To skip the confirmation prompt (for scripted use), pass `--yes`:

```bash
./shared/mongo/restore.sh --yes shared/mongo/backups/wiab-20260401-060000.archive.gz
```

## Automated Backups (Cron)

### Setup

Run the installer (one-time, or re-run to update scripts):

```bash
cd /path/to/repo
./deploy/mongo/install-cron.sh
```

This:
1. Copies `backup.sh` and `restore.sh` to `/home/ubuntu/supawave/shared/mongo/`
2. Installs a crontab entry to run backups every 6 hours (UTC)

### Schedule

Backups run at 00:00, 06:00, 12:00, and 18:00 UTC. Output is logged to
`/home/ubuntu/supawave/shared/logs/backup.log`.

Verify the cron job:

```bash
crontab -l | grep wave-mongo-backup
```

### Retention

The 10 most recent backups are kept. Older archives are deleted after each
backup run. At 6-hour intervals, this gives ~60 hours of backup history.

Override with `KEEP_COUNT`:

```bash
KEEP_COUNT=20 /home/ubuntu/supawave/shared/mongo/backup.sh
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DEPLOY_ROOT` | `/home/ubuntu/supawave` | Base deploy directory |
| `BACKUP_DIR` | `$DEPLOY_ROOT/shared/mongo/backups` | Where archives are stored |
| `MONGO_CONTAINER` | `supawave-mongo-1` | Docker container name |
| `MONGO_DATABASE` | `wiab` | Database to dump/restore |
| `KEEP_COUNT` | `10` | Number of backups to retain |
| `MIN_DISK_MB` | `1024` | Minimum free disk (MB) before aborting |

## Troubleshooting

**"Cannot connect to the Docker daemon"**
- The user running the script must have Docker access. On production, the
  `ubuntu` user is in the `docker` group.

**"Error: container not found"**
- Verify the container is running: `docker ps | grep mongo`
- Check the container name matches `MONGO_CONTAINER`.

**"disk space insufficient" (exit code 2)**
- Free up space or lower `MIN_DISK_MB`. Check backup directory for unexpectedly
  large archives.

**Backup produced empty file**
- Check that MongoDB is healthy: `docker exec supawave-mongo-1 mongosh --quiet --eval 'db.runCommand({ping:1})'`
- Check Docker logs: `docker logs supawave-mongo-1 --tail 20`

**Restore fails with "not authorized"**
- When MongoDB authentication is enabled, add credentials via `MONGODB_URI`.
  This is not yet needed (auth is currently disabled).

## Restore Drill

Periodically verify backups by restoring into a scratch container:

```bash
# Start a throwaway Mongo:
docker run -d --name mongo-drill -p 27777:27017 mongo:6.0

# Restore:
MONGO_CONTAINER=mongo-drill \
  /home/ubuntu/supawave/shared/mongo/restore.sh --yes \
  /home/ubuntu/supawave/shared/mongo/backups/wiab-LATEST.archive.gz

# Verify:
docker exec mongo-drill mongosh --quiet --eval 'db.getCollectionNames()' wiab

# Cleanup:
docker rm -f mongo-drill
```

## Future Enhancements

- **Authentication:** When MongoDB auth is enabled, scripts will need
  credentials via `MONGODB_URI` or separate env vars.
- **Off-host replication:** rsync or S3-compatible storage for disaster recovery.
- **Monitoring:** Alert on backup failures (email, Slack, PagerDuty).
```

- [ ] **Step 2: Commit**

```bash
git add deploy/mongo/README.md
git commit -m "docs: rewrite deploy/mongo/README.md with full operator guide"
```

---

### Task 5: Update docs/deployment/mongo-hardening.md

**Files:**
- Modify: `docs/deployment/mongo-hardening.md` (section 2, lines ~192-274)

- [ ] **Step 1: Replace section 2 backup content**

Replace section `2. Backup strategy` (from `### 2a. Daily mongodump via cron` through
`### 2e. Restore drill`) with content that matches the new scripts. Keep the section
structure but update commands, schedule, and retention to match the spec.

The key changes:
- Schedule: `0 */6 * * *` (every 6 hours) replaces `0 3 * * *` (daily at 3 AM)
- Scripts: `docker exec` approach replaces host-side `mongodump` + `MONGODB_URI`
- Retention: "keep last 10" replaces "7 daily / 4 weekly / 3 monthly"
- Location: `$DEPLOY_ROOT/shared/mongo/backups/` replaces `$DEPLOY_ROOT/shared/backups/`
- Setup: reference `install-cron.sh` instead of manual crontab editing
- Restore: reference `restore.sh` with Docker compose `-p supawave -f` flags

Update the checklist items in section 4 to match:
- Replace `[ ] Ops: set up daily backup cron (step 2a)` with `[ ] Ops: run install-cron.sh on host (step 2a)`
- Replace `[ ] Ops: set up retention pruning (step 2b)` with `[ ] Ops: verify retention (automatic, KEEP_COUNT=10)`
- Replace `[ ] Ops: run first restore drill (step 2e)` with `[ ] Ops: run restore drill against scratch container (step 2c)`

Also update the "Current state" table at the top:
- Change `Backup schedule` from `**NONE**` to `Every 6 hours via cron (see section 2)`
- Change `Database` row to show the current 12 collections instead of 2

- [ ] **Step 2: Verify the file has no broken markdown**

Run: `head -5 docs/deployment/mongo-hardening.md`
Expected: valid markdown header

- [ ] **Step 3: Commit**

```bash
git add docs/deployment/mongo-hardening.md
git commit -m "docs: sync mongo-hardening.md backup section with new automation scripts"
```

---

### Task 6: Test on production host

**Files:** None (operational verification)

- [ ] **Step 1: Copy scripts to host and run backup**

```bash
ssh supawave 'mkdir -p /home/ubuntu/supawave/shared/mongo/backups /home/ubuntu/supawave/shared/logs'
scp deploy/mongo/backup.sh deploy/mongo/restore.sh deploy/mongo/install-cron.sh supawave:/home/ubuntu/supawave/shared/mongo/
ssh supawave 'chmod +x /home/ubuntu/supawave/shared/mongo/{backup,restore,install-cron}.sh'
ssh supawave '/home/ubuntu/supawave/shared/mongo/backup.sh'
```

Expected: prints archive path, e.g. `/home/ubuntu/supawave/shared/mongo/backups/wiab-20260401-HHMMSS.archive.gz`

- [ ] **Step 2: Verify archive permissions and size**

```bash
ssh supawave 'ls -la /home/ubuntu/supawave/shared/mongo/backups/'
```

Expected: archive exists, permissions are `-rw-------` (600), size > 0

- [ ] **Step 3: Test restore listing**

```bash
ssh supawave '/home/ubuntu/supawave/shared/mongo/restore.sh'
```

Expected: lists the backup we just created

- [ ] **Step 4: Test restore into scratch container**

```bash
ssh supawave 'docker run -d --name mongo-drill -p 27777:27017 mongo:6.0 && sleep 3'
ssh supawave 'MONGO_CONTAINER=mongo-drill /home/ubuntu/supawave/shared/mongo/restore.sh --yes /home/ubuntu/supawave/shared/mongo/backups/wiab-*.archive.gz'
ssh supawave 'docker exec mongo-drill mongosh --quiet --eval "db.getCollectionNames()" wiab'
ssh supawave 'docker rm -f mongo-drill'
```

Expected: collection names match production (deltas, account, contacts, snapshots, etc.)

- [ ] **Step 5: Test disk space check**

```bash
ssh supawave 'MIN_DISK_MB=999999999 /home/ubuntu/supawave/shared/mongo/backup.sh'
```

Expected: exit code 2, error message about insufficient disk space

- [ ] **Step 6: Test rotation**

```bash
ssh supawave 'for i in $(seq 1 12); do KEEP_COUNT=10 /home/ubuntu/supawave/shared/mongo/backup.sh; sleep 1; done'
ssh supawave 'ls -1 /home/ubuntu/supawave/shared/mongo/backups/wiab-*.archive.gz | wc -l'
```

Expected: exactly 10 archives remain

- [ ] **Step 7: Install cron**

```bash
ssh supawave '/home/ubuntu/supawave/shared/mongo/install-cron.sh'
ssh supawave 'crontab -l | grep -A1 CRON_TZ'
```

Expected: shows `CRON_TZ=UTC` followed by the backup cron job line

- [ ] **Step 8: Commit verification results**

```bash
git commit --allow-empty -m "chore: verified backup scripts on production host"
```

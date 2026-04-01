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
  backups=$(ls -1 "$BACKUP_DIR"/${MONGO_DATABASE}-*.archive.gz 2>/dev/null | sort)
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

# Global so the EXIT trap can access it after main() returns
_tmp_path=""

main() {
  mkdir -p "$BACKUP_DIR"
  check_disk_space

  local timestamp
  timestamp=$(date -u +%Y%m%d-%H%M%S)
  local archive_name="${MONGO_DATABASE}-${timestamp}.archive.gz"
  local archive_path="${BACKUP_DIR}/${archive_name}"
  _tmp_path="${archive_path}.tmp"

  # Cleanup partial file on failure (uses global _tmp_path for trap safety)
  trap 'rm -f "$_tmp_path"' EXIT

  log "starting backup of database '${MONGO_DATABASE}' from container '${MONGO_CONTAINER}'"

  if ! docker exec "$MONGO_CONTAINER" \
    mongodump --db="$MONGO_DATABASE" --archive --gzip \
    > "$_tmp_path"; then
    log "ERROR: mongodump failed"
    exit 1
  fi

  # Validate
  if [[ ! -s "$_tmp_path" ]]; then
    log "ERROR: backup produced empty file"
    exit 1
  fi

  # Atomic rename
  mv "$_tmp_path" "$archive_path"
  trap - EXIT

  log "backup complete: ${archive_path} ($(du -h "$archive_path" | cut -f1))"

  rotate_backups

  # Print archive path to stdout for cron log / scripting
  printf '%s\n' "$archive_path"
}

main "$@"

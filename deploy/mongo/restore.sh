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
  if ls -1 "$BACKUP_DIR"/${MONGO_DATABASE}-*.archive.gz >/dev/null 2>&1; then
    ls -1t "$BACKUP_DIR"/${MONGO_DATABASE}-*.archive.gz | while read -r f; do
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
      *)
        if [[ -n "$archive_path" ]]; then
          log "ERROR: only one archive path allowed"; exit 64
        fi
        archive_path="$1"; shift ;;
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

  if ! docker exec -i "$MONGO_CONTAINER" \
    mongorestore --db="$MONGO_DATABASE" --archive --gzip --drop \
    < "$archive_path"; then
    log "ERROR: mongorestore failed"
    exit 1
  fi

  log "restore complete"
}

main "$@"

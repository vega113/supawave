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

  local src_backup="$SCRIPT_DIR/backup.sh"
  local src_restore="$SCRIPT_DIR/restore.sh"
  local dst_backup="$SHARED_MONGO/backup.sh"
  local dst_restore="$SHARED_MONGO/restore.sh"

  # Skip copy if source and destination are the same file (running from shared/mongo/)
  if [[ "$(realpath "$src_backup")" != "$(realpath "$dst_backup" 2>/dev/null || echo _)" ]]; then
    cp "$src_backup" "$dst_backup"
    cp "$src_restore" "$dst_restore"
    log "scripts copied to ${SHARED_MONGO}/"
  else
    log "scripts already in ${SHARED_MONGO}/ (skipping copy)"
  fi
  chmod +x "$dst_backup" "$dst_restore"
}

install_cron() {
  local current_cron
  current_cron=$(crontab -l 2>/dev/null || true)

  # Remove all existing marker lines and any CRON_TZ=UTC line immediately before each one.
  # This handles duplicates and stale entries cleanly on every run.
  local cleaned_cron
  cleaned_cron=$(echo "$current_cron" | awk -v marker="$CRON_MARKER" '
    { lines[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        if (index(lines[i], marker)) {
          if (i > 1 && lines[i-1] == "CRON_TZ=UTC") skip[i-1] = 1
          skip[i] = 1
        }
      }
      for (i = 1; i <= NR; i++) {
        if (!skip[i]) print lines[i]
      }
    }
  ')

  # Append canonical two-line entry
  local new_cron
  new_cron=$(printf '%s\nCRON_TZ=UTC\n%s\n' "$cleaned_cron" "$CRON_JOB")
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

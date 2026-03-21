#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'usage: %s [output-archive]\n' "${0##*/}" >&2
}

main() {
  if [[ $# -gt 1 ]]; then
    usage
    exit 64
  fi

  : "${MONGODB_URI:?set MONGODB_URI to a MongoDB URI}"

  local backup_dir
  local archive_path
  local timestamp

  backup_dir=${BACKUP_DIR:-./backups}
  mkdir -p "$backup_dir"

  timestamp=$(date -u +%Y%m%dT%H%M%SZ)
  archive_path=${1:-"$backup_dir/wiab-$timestamp.archive.gz"}

  mongodump --uri="$MONGODB_URI" --archive="$archive_path" --gzip
  printf '%s\n' "$archive_path"
}

main "$@"

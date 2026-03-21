#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'usage: %s <input-archive>\n' "${0##*/}" >&2
}

main() {
  if [[ $# -ne 1 ]]; then
    usage
    exit 64
  fi

  : "${MONGODB_URI:?set MONGODB_URI to a MongoDB URI}"

  local archive_path
  local restore_args

  archive_path=$1
  if [[ ! -f "$archive_path" ]]; then
    printf 'restore archive not found: %s\n' "$archive_path" >&2
    exit 66
  fi

  restore_args=(--uri="$MONGODB_URI" --archive="$archive_path" --gzip)
  if [[ "${MONGORESTORE_DROP:-true}" == "true" ]]; then
    restore_args+=(--drop)
  fi

  mongorestore "${restore_args[@]}"
}

main "$@"

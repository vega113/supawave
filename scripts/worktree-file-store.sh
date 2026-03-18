#!/usr/bin/env bash
set -euo pipefail

MODE="symlink"
FORCE=false
SOURCE_ROOT=""

usage() {
  cat <<'EOF'
Usage:
  scripts/worktree-file-store.sh --source /path/to/source/checkout [--mode symlink|copy] [--force]

Purpose:
  Prepare the current incubator-wave worktree for realistic local testing by
  linking or copying the file-based persistence directories from another
  checkout.

Defaults:
  --mode symlink

Managed paths:
  wave/_accounts
  wave/_attachments
  wave/_deltas
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source)
      SOURCE_ROOT="${2:-}"
      shift 2
      ;;
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$SOURCE_ROOT" ]]; then
  echo "--source is required" >&2
  usage >&2
  exit 2
fi

if [[ "$MODE" != "symlink" && "$MODE" != "copy" ]]; then
  echo "--mode must be 'symlink' or 'copy'" >&2
  exit 2
fi

TARGET_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [[ -z "$TARGET_ROOT" ]]; then
  echo "Current directory is not inside a git worktree" >&2
  exit 2
fi

SOURCE_ROOT=$(cd "$SOURCE_ROOT" && pwd)
if [[ "$SOURCE_ROOT" == "$TARGET_ROOT" ]]; then
  echo "Source and target roots are the same: $SOURCE_ROOT" >&2
  exit 2
fi

PATHS=(
  "wave/_accounts"
  "wave/_attachments"
  "wave/_deltas"
)

prepare_target_path() {
  local target_path="$1"
  if [[ -L "$target_path" ]]; then
    if $FORCE; then
      rm -f "$target_path"
      return
    fi
    echo "Target already exists as symlink: $target_path" >&2
    exit 1
  fi
  if [[ -e "$target_path" ]]; then
    if $FORCE; then
      rm -rf "$target_path"
      return
    fi
    echo "Target already exists: $target_path (use --force to replace)" >&2
    exit 1
  fi
}

for rel_path in "${PATHS[@]}"; do
  source_path="$SOURCE_ROOT/$rel_path"
  target_path="$TARGET_ROOT/$rel_path"

  if [[ ! -e "$source_path" ]]; then
    echo "Skipping missing source path: $source_path"
    continue
  fi

  mkdir -p "$(dirname "$target_path")"
  prepare_target_path "$target_path"

  if [[ "$MODE" == "symlink" ]]; then
    ln -s "$source_path" "$target_path"
    echo "Linked $target_path -> $source_path"
  else
    cp -R "$source_path" "$target_path"
    echo "Copied $source_path -> $target_path"
  fi
done

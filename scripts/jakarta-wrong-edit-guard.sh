#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_REF=""

usage() {
  cat <<'EOF'
Usage: scripts/jakarta-wrong-edit-guard.sh [--repo-root PATH] [--base-ref REF]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-root)
      REPO_ROOT="${2:?missing value for --repo-root}"
      shift 2
      ;;
    --base-ref)
      BASE_REF="${2:?missing value for --base-ref}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

BUILD_FILE="$REPO_ROOT/build.sbt"
MAIN_PREFIX="wave/src/main/java/"
OVERRIDE_PREFIX="wave/src/jakarta-overrides/java/"

MAIN_EXCLUDES=""
JAKARTA_EXCLUDES=""
CHANGED_MAIN=""
CHANGED_OVERRIDE=""

extract_set_entries() {
  local set_name="$1"
  awk -v set_name="$set_name" '
    $0 ~ ("val " set_name ": Set\\[String\\] = Set\\(") {
      inside = 1
      next
    }
    inside {
      if ($0 ~ /^[[:space:]]*\)/) {
        exit
      }
      line = $0
      while (match(line, /"[^"]+"/)) {
        print substr(line, RSTART + 1, RLENGTH - 2)
        line = substr(line, RSTART + RLENGTH)
      }
    }
  ' "$BUILD_FILE"
}

load_excludes() {
  local entry=""

  while IFS= read -r entry; do
    if [[ -n "$entry" ]]; then
      append_unique_line MAIN_EXCLUDES "$entry"
    fi
  done < <(extract_set_entries "mainExactExcludes")

  while IFS= read -r entry; do
    if [[ -n "$entry" ]]; then
      append_unique_line JAKARTA_EXCLUDES "$entry"
    fi
  done < <(extract_set_entries "jakartaExactExcludes")
}

append_unique_line() {
  local variable_name="$1"
  local entry="$2"
  local current_value="${!variable_name:-}"

  if ! contains_line "$current_value" "$entry"; then
    printf -v "$variable_name" '%s%s\n' "$current_value" "$entry"
  fi
}

contains_line() {
  local haystack="${1:-}"
  local needle="$2"

  if [[ -z "$haystack" ]]; then
    return 1
  fi

  printf '%s' "$haystack" | grep -Fqx "$needle"
}

collect_changed_paths() {
  local merge_base=""
  local status=""
  local code=""
  local path=""
  local -a diff_command=(git -C "$REPO_ROOT" diff --name-status -z --find-renames --find-copies)

  if [[ -n "$BASE_REF" ]]; then
    merge_base="$(git -C "$REPO_ROOT" merge-base "$BASE_REF" HEAD)"
    diff_command+=("$merge_base" HEAD)
  else
    diff_command+=(HEAD)
  fi

  while IFS= read -r -d '' status; do
    code="${status%%[0-9]*}"
    path=""

    case "$code" in
      R|C)
        IFS= read -r -d '' _
        IFS= read -r -d '' path
        ;;
      *)
        IFS= read -r -d '' path
        ;;
    esac

    case "$path" in
      "$MAIN_PREFIX"*)
        append_unique_line CHANGED_MAIN "${path#$MAIN_PREFIX}"
        ;;
      "$OVERRIDE_PREFIX"*)
        append_unique_line CHANGED_OVERRIDE "${path#$OVERRIDE_PREFIX}"
        ;;
    esac
  done < <("${diff_command[@]}")
}

is_runtime_active_override() {
  local relative_path="$1"
  local override_path="$REPO_ROOT/$OVERRIDE_PREFIX$relative_path"

  if ! contains_line "$MAIN_EXCLUDES" "$relative_path"; then
    return 1
  fi

  if contains_line "$JAKARTA_EXCLUDES" "$relative_path"; then
    return 1
  fi

  if [[ ! -f "$override_path" ]]; then
    return 1
  fi

  return 0
}

print_warning_block() {
  local relative_path="$1"
  local main_path="$MAIN_PREFIX$relative_path"
  local override_path="$OVERRIDE_PREFIX$relative_path"

  cat <<EOF
[guard] WARNING: likely Jakarta wrong-tree edit detected (advisory only)
[guard]   main copy: $main_path
[guard]   runtime override: $override_path
[guard]   rule: when a runtime-active Jakarta override exists, edit the override copy
[guard]   docs: ORCHESTRATOR.md (Jakarta Migration Pattern), AGENTS.md
[guard]   follow-up: collect false-positive/false-negative evidence for #589 before any blocking mode decision
EOF
}

main() {
  if [[ ! -f "$BUILD_FILE" ]]; then
    echo "[guard] ERROR: build.sbt not found at $BUILD_FILE" >&2
    exit 2
  fi

  load_excludes
  collect_changed_paths

  local relative_path=""
  local warned=0

  while IFS= read -r relative_path; do
    if [[ -z "$relative_path" ]]; then
      continue
    fi
    if contains_line "$CHANGED_OVERRIDE" "$relative_path"; then
      continue
    fi
    if is_runtime_active_override "$relative_path"; then
      print_warning_block "$relative_path"
      warned=1
    fi
  done < <(printf '%s' "$CHANGED_MAIN" | sed '/^$/d' | sort)

  if [[ "$warned" -eq 0 ]]; then
    echo "[guard] OK: no likely Jakarta wrong-tree edits found."
  fi
}

main

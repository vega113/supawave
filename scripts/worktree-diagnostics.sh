#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-9898}"
LINES=40
OUTPUT_PATH=""

usage() {
  cat <<'EOF'
Usage:
  bash scripts/worktree-diagnostics.sh [--port <port>] [--lines <n>] [--output <path>]

Purpose:
  Collect a compact diagnostics bundle for the current incubator-wave worktree.

What it records:
  - worktree, branch, runtime-config, and evidence-file metadata
  - current endpoint probe statuses
  - current `scripts/wave-smoke.sh check` output and exit status
  - last-N lines from the staged startup output and server log
EOF
}

fail() {
  echo "$*" >&2
  exit 1
}

require_option_value() {
  local option="$1"
  if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
    fail "$option requires a value"
  fi
}

sanitize_slug() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-'
}

trim_edge_dashes() {
  local value="$1"
  value="${value##-}"
  value="${value%%-}"
  printf '%s' "$value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      require_option_value "$1" "${2-}"
      PORT="${2:-}"
      shift 2
      ;;
    --lines)
      require_option_value "$1" "${2-}"
      LINES="${2:-}"
      shift 2
      ;;
    --output)
      require_option_value "$1" "${2-}"
      OUTPUT_PATH="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

[[ "$PORT" =~ ^[0-9]+$ ]] || fail "--port must be numeric"
(( PORT >= 1 && PORT <= 65535 )) || fail "--port must be between 1 and 65535"
[[ "$LINES" =~ ^[0-9]+$ ]] || fail "--lines must be numeric"
(( LINES >= 1 )) || fail "--lines must be at least 1"

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || true)
[[ -n "$ROOT" ]] || fail "Run this helper from inside an incubator-wave git worktree"
ROOT=$(cd "$ROOT" && pwd)

BRANCH=$(git branch --show-current)
[[ -n "$BRANCH" ]] || fail "Could not determine the current branch"

if [[ -d "$ROOT/target/universal/stage" ]]; then
  INSTALL_DIR="$ROOT/target/universal/stage"
else
  INSTALL_DIR="$ROOT/wave/build/install/wave"
fi

BRANCH_SLUG=$(trim_edge_dashes "$(sanitize_slug "$BRANCH")")
RUNTIME_CONFIG="$ROOT/journal/runtime-config/${BRANCH_SLUG}-port-${PORT}.application.conf"

ISSUE_NUMBER=""
SLUG_PART="$BRANCH"
if [[ "$BRANCH" =~ ^issue-([0-9]+)-(.+)$ ]]; then
  ISSUE_NUMBER="${BASH_REMATCH[1]}"
  SLUG_PART="${BASH_REMATCH[2]}"
fi

EVIDENCE_SLUG=$(trim_edge_dashes "$(sanitize_slug "$SLUG_PART")")
DATE_STAMP=$(date +%F)

resolve_evidence_file() {
  local fallback="$1"
  local candidate newest
  local -a matches=()

  shopt -s nullglob
  if [[ -n "$ISSUE_NUMBER" ]]; then
    matches=("$ROOT"/journal/local-verification/*-issue-"$ISSUE_NUMBER"-"$EVIDENCE_SLUG".md)
  else
    matches=("$ROOT"/journal/local-verification/*-branch-"$EVIDENCE_SLUG".md)
  fi
  shopt -u nullglob

  if (( ${#matches[@]} == 0 )); then
    printf '%s' "$fallback"
    return
  fi

  newest="${matches[0]}"
  for candidate in "${matches[@]:1}"; do
    if [[ "$candidate" -nt "$newest" ]]; then
      newest="$candidate"
    fi
  done

  printf '%s' "$newest"
}

if [[ -n "$ISSUE_NUMBER" ]]; then
  EVIDENCE_FILE="$(resolve_evidence_file "$ROOT/journal/local-verification/$DATE_STAMP-issue-$ISSUE_NUMBER-$EVIDENCE_SLUG.md")"
else
  EVIDENCE_FILE="$(resolve_evidence_file "$ROOT/journal/local-verification/$DATE_STAMP-branch-$EVIDENCE_SLUG.md")"
fi

STARTUP_LOG="$INSTALL_DIR/wave_server.out"

resolve_server_log() {
  local candidate
  for candidate in \
    "$INSTALL_DIR/wiab-server.log" \
    "$INSTALL_DIR/logs/wave.log" \
    "$INSTALL_DIR/logs/wave-json.log"
  do
    if [[ -f "$candidate" ]]; then
      printf '%s' "$candidate"
      return
    fi
  done

  printf '%s' "$INSTALL_DIR/logs/wave.log"
}

SERVER_LOG="$(resolve_server_log)"

probe_status() {
  local path="$1"
  local status
  status="$(curl -sS --max-time 5 -o /dev/null -w "%{http_code}" "http://localhost:$PORT$path" 2>/dev/null || true)"
  if [[ -z "$status" ]]; then
    status="000"
  fi
  printf '%s' "$status"
}

tail_section() {
  local path="$1"
  if [[ -f "$path" ]]; then
    printf '```text\n'
    tail -n "$LINES" "$path"
    printf '\n```\n'
  else
    printf '(missing: %s)\n' "$path"
  fi
}

smoke_output="$(
  cd "$ROOT"
  PORT="$PORT" bash "$ROOT/scripts/wave-smoke.sh" check 2>&1
)" && smoke_status=0 || smoke_status=$?

format_path_status() {
  local path="$1"
  if [[ -f "$path" ]]; then
    printf '%s' "$path"
  else
    printf '(missing: %s)' "$path"
  fi
}

render_bundle() {
  cat <<EOF
# Worktree Diagnostics Bundle

- Branch: $BRANCH
- Worktree: $ROOT
- Port: $PORT
- Install dir: $INSTALL_DIR
- Runtime config: $(format_path_status "$RUNTIME_CONFIG")
- Evidence file: $(format_path_status "$EVIDENCE_FILE")
- Generated at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

## Endpoint Probes

- \`GET /\` -> \`$(probe_status "/")\`
- \`GET /healthz\` -> \`$(probe_status "/healthz")\`
- \`GET /readyz\` -> \`$(probe_status "/readyz")\`
- \`GET /webclient/webclient.nocache.js\` -> \`$(probe_status "/webclient/webclient.nocache.js")\`

## Smoke Check

- Command: \`PORT=$PORT bash scripts/wave-smoke.sh check\`
- Smoke exit: \`$smoke_status\`

\`\`\`text
$smoke_output
\`\`\`

## Startup Output Tail ($STARTUP_LOG)

EOF
  tail_section "$STARTUP_LOG"
  cat <<EOF

## Server Log Tail ($SERVER_LOG)

EOF
  tail_section "$SERVER_LOG"
}

if [[ -n "$OUTPUT_PATH" ]]; then
  mkdir -p "$(dirname "$OUTPUT_PATH")"
  render_bundle | tee "$OUTPUT_PATH"
else
  render_bundle
fi

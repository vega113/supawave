#!/usr/bin/env bash
set -euo pipefail

PORT=9898
SHARED_FILE_STORE=false
FILE_STORE_SOURCE="${HOME}/devroot/incubator-wave"

usage() {
  cat <<'EOF'
Usage:
  bash scripts/worktree-boot.sh [--port <port>] [--shared-file-store] [--file-store-source <path>]

Purpose:
  Prepare an existing incubator-wave worktree for local verification.

What it does:
  - refuses to run from the primary checkout
  - checks the requested port for existing listeners
  - optionally links shared file-store state from another checkout
  - stages the app with sbt Universal/stage
  - writes a port-specific runtime config under journal/runtime-config/
  - creates a local-verification journal entry under journal/local-verification/
  - prints the exact start/check/diagnostics/stop commands for the selected port
EOF
}

fail() {
  echo "$*" >&2
  exit 1
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

port_listener_output() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || true
    return
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -tlnp "sport = :$PORT" 2>/dev/null || true
    return
  fi

  if command -v fuser >/dev/null 2>&1; then
    fuser "$PORT"/tcp 2>/dev/null || true
    return
  fi

  fail "Cannot check port $PORT: none of lsof, ss, or fuser found"
}

port_in_use() {
  local output
  output=$(port_listener_output)
  [[ -n "$output" ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      PORT="${2:-}"
      shift 2
      ;;
    --shared-file-store)
      SHARED_FILE_STORE=true
      shift
      ;;
    --file-store-source)
      FILE_STORE_SOURCE="${2:-}"
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

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || true)
[[ -n "$ROOT" ]] || fail "Run this helper from inside an incubator-wave git worktree"
ROOT=$(cd "$ROOT" && pwd)

GIT_DIR=$(git rev-parse --absolute-git-dir)
COMMON_GIT_DIR=$(git rev-parse --git-common-dir)
COMMON_GIT_DIR=$(cd "$COMMON_GIT_DIR" && pwd)
if [[ "$GIT_DIR" == "$COMMON_GIT_DIR" ]]; then
  fail "Run this helper from a linked worktree, not from a primary checkout clone: $ROOT"
fi

BRANCH=$(git -C "$ROOT" branch --show-current)
[[ -n "$BRANCH" ]] || fail "Could not determine the current branch"

if port_in_use; then
  {
    echo "Port $PORT is already in use. Choose another --port value."
    echo
    echo "Active listener(s):"
    port_listener_output
  } >&2
  exit 1
fi

if $SHARED_FILE_STORE; then
  bash "$ROOT/scripts/worktree-file-store.sh" --source "$FILE_STORE_SOURCE"
fi

pushd "$ROOT" >/dev/null
sbt -batch Universal/stage
popd >/dev/null

RUNTIME_DIR="$ROOT/journal/runtime-config"
EVIDENCE_DIR="$ROOT/journal/local-verification"
mkdir -p "$RUNTIME_DIR" "$EVIDENCE_DIR"

RUNTIME_CONFIG="$RUNTIME_DIR/$(sanitize_slug "$BRANCH")-port-$PORT.application.conf"
perl -0pe "s/http_frontend_addresses\\s*=\\s*\\[[^\\]]*\\]/http_frontend_addresses = [\"127.0.0.1:$PORT\"]/g" \
  "$ROOT/wave/config/application.conf" > "$RUNTIME_CONFIG"

# Copy the port-specific config into the staged install directory so the
# server picks it up directly (ServerMain loads config/application.conf
# relative to its CWD, which wave-smoke.sh sets to INSTALL_DIR).
if [[ -d "$ROOT/target/universal/stage" ]]; then
  STAGED_CONFIG="$ROOT/target/universal/stage/config/application.conf"
else
  STAGED_CONFIG="$ROOT/wave/build/install/wave/config/application.conf"
fi
if [[ ! -f "$STAGED_CONFIG" ]]; then
  echo "Error: staged config not found at '$STAGED_CONFIG' after staging; port-specific config was generated at '$RUNTIME_CONFIG' but the staged distribution was not updated for port $PORT." >&2
  exit 1
fi
cp "$RUNTIME_CONFIG" "$STAGED_CONFIG"

DATE_STAMP=$(date +%F)
ISSUE_NUMBER=""
SLUG_PART="$BRANCH"
if [[ "$BRANCH" =~ ^issue-([0-9]+)-(.+)$ ]]; then
  ISSUE_NUMBER="${BASH_REMATCH[1]}"
  SLUG_PART="${BASH_REMATCH[2]}"
fi

SLUG=$(trim_edge_dashes "$(sanitize_slug "$SLUG_PART")")
if [[ -n "$ISSUE_NUMBER" ]]; then
  EVIDENCE_FILE="$EVIDENCE_DIR/$DATE_STAMP-issue-$ISSUE_NUMBER-$SLUG.md"
else
  EVIDENCE_FILE="$EVIDENCE_DIR/$DATE_STAMP-branch-$SLUG.md"
fi

if [[ ! -f "$EVIDENCE_FILE" ]]; then
  cat > "$EVIDENCE_FILE" <<EOF
# Local Verification

- Branch: $BRANCH
- Worktree: $ROOT
- Date: $DATE_STAMP

## Commands

- \`bash scripts/worktree-boot.sh --port $PORT\`

## Results

- pending

## Follow-up

- PR: pending
- Issue: pending
EOF
fi

JAVA_OPTS_VALUE="-Djava.util.logging.config.file=$ROOT/wave/config/wiab-logging.conf -Djava.security.auth.login.config=$ROOT/wave/config/jaas.config"

cat <<EOF
Worktree boot assets ready.

Branch: $BRANCH
Worktree: $ROOT
Runtime config: $RUNTIME_CONFIG
Evidence file: $EVIDENCE_FILE

Start:
  PORT=$PORT JAVA_OPTS='$JAVA_OPTS_VALUE' bash scripts/wave-smoke.sh start

Check:
  PORT=$PORT bash scripts/wave-smoke.sh check

Diagnostics:
  PORT=$PORT bash scripts/worktree-diagnostics.sh --port $PORT

Stop:
  PORT=$PORT bash scripts/wave-smoke.sh stop
EOF

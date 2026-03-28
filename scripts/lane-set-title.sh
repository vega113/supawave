#!/usr/bin/env bash
set -euo pipefail

TITLE="${1:?usage: lane-set-title.sh <title> [pane-target]}"
PANE_TARGET="${2:-${TMUX_PANE:-}}"

if [ -z "$PANE_TARGET" ]; then
  echo "No pane target provided and TMUX_PANE is empty" >&2
  exit 1
fi

tmux select-pane -t "$PANE_TARGET" -T "$TITLE"

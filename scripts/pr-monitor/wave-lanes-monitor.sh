#!/usr/bin/env bash
# wave-lanes-monitor.sh — Monitors open PRs in wave-lanes tmux panes.
#
# Runs in a continuous loop (every 5 minutes):
#   1. Closes panes whose PRs are merged/closed
#   2. Creates lanes for uncovered open PRs
#   3. Sends targeted instructions to idle lane agents based on PR status
#
# Usage:
#   scripts/pr-monitor/wave-lanes-monitor.sh
#
# Or via a Claude agent:
#   claude --model claude-haiku-4-5-20251001 --dangerously-skip-permissions \
#     < scripts/pr-monitor/wave-lanes-monitor-prompt.md

# set -e intentionally omitted: this is a long-running daemon where transient errors
# (network blips, missing panes) must not abort the monitoring loop.
set -uo pipefail

REPO="vega113/incubator-wave"
WAVE_SESSION="vibe-code:wave-lanes"
WORKTREE_BASE="/Users/vega/devroot/worktrees"
REPO_PATH="/Users/vega/devroot/incubator-wave"
CYCLE_INTERVAL=300

extract_pr_number() {
  local title="$1"
  [[ $title =~ PR#([0-9]+) ]] && echo "${BASH_REMATCH[1]}" || echo ""
}

is_pane_idle() {
  local pane="$1"
  local cmd
  cmd=$(tmux list-panes -t "$WAVE_SESSION" -F "#{pane_index}: #{pane_current_command}" 2>/dev/null \
    | grep "^${pane}:" | cut -d: -f2- | sed 's/^ *//' || echo "")
  [[ ! "$cmd" =~ claude ]]
}

get_unresolved_threads() {
  local pr=$1
  gh api graphql -f query="{ repository(owner:\"vega113\", name:\"incubator-wave\") { pullRequest(number:${pr}) { reviewThreads(first:100) { nodes { isResolved } } } } }" \
    -q '.data.repository.pullRequest.reviewThreads.nodes | map(select(.isResolved == false)) | length' 2>/dev/null || echo "0"
}

check_pr_state() {
  local pr=$1
  gh pr view "$pr" --repo "$REPO" --json state -q '.state' 2>/dev/null || echo "UNKNOWN"
}

close_pane() {
  local pane_idx=$1
  local pr=$2
  local state=$3
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] CLOSED pane $pane_idx (PR#$pr $state)"
  tmux send-keys -t "$WAVE_SESSION.$pane_idx" C-c 2>/dev/null || true
  sleep 2
  tmux send-keys -t "$WAVE_SESSION.$pane_idx" "/exit" Enter 2>/dev/null || true
  sleep 2
  tmux kill-pane -t "$WAVE_SESSION.$pane_idx" 2>/dev/null || true
  tmux select-layout -t "$WAVE_SESSION" tiled 2>/dev/null || true
}

send_instructions() {
  local pane_idx=$1
  local pr=$2
  local branch=$3
  local mergeable=$4

  # Skip if agent is busy
  if ! is_pane_idle "$pane_idx"; then
    return
  fi

  # Check unresolved threads
  local threads
  threads=$(get_unresolved_threads "$pr")

  # Check CI
  local ci_failing=false
  if gh pr checks "$pr" --repo "$REPO" 2>/dev/null | grep -qE "fail|error"; then
    ci_failing=true
  fi

  if [[ "$threads" -gt 0 ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr has $threads unresolved threads — sending instructions"
    tmux send-keys -t "$WAVE_SESSION.$pane_idx" \
      "PRIORITY: PR #$pr has $threads unresolved review threads. Read each with: gh api repos/$REPO/pulls/$pr/comments. Fix the issue or reply, then RESOLVE each thread via: gh api graphql -f query='mutation { resolveReviewThread(input:{threadId:\"THREAD_ID\"}) { thread { isResolved } } }'. All threads must be resolved for CI to pass." Enter
  elif [[ "$mergeable" != "MERGEABLE" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr has conflicts — sending rebase instructions"
    tmux send-keys -t "$WAVE_SESSION.$pane_idx" \
      "PRIORITY: PR #$pr has merge conflicts. git fetch origin main && git rebase origin/main. For EACH conflict read BOTH sides carefully — do NOT blindly --ours/--theirs. Preserve features from both. After resolving: git push --force-with-lease origin $branch" Enter
  elif [[ "$ci_failing" == "true" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr has CI failures — sending fix instructions"
    tmux send-keys -t "$WAVE_SESSION.$pane_idx" \
      "PRIORITY: PR #$pr has failing CI. Run: cd wave && sbt compile 2>&1 | tail -30. Fix build errors. Then: cd wave && sbt test 2>&1 | tail -50. Fix test failures at root cause. Do NOT skip tests. Push when green." Enter
  else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr is clean — ready to merge"
  fi
}

# ── main loop ────────────────────────────────────────────────────
echo "[$(date '+%Y-%m-%d %H:%M:%S')] wave-lanes-monitor started (cycle=${CYCLE_INTERVAL}s)"

while true; do
  # Preflight: ensure the tmux session and window exist before doing any work
  if ! tmux has-session -t "$WAVE_SESSION" 2>/dev/null; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: tmux session/window '$WAVE_SESSION' not found — waiting for next cycle"
    sleep "$CYCLE_INTERVAL"
    continue
  fi

  # ── PART 1: Close merged/closed PR panes ──
  pane_list=$(tmux list-panes -t "$WAVE_SESSION" -F "#{pane_index}: #{pane_title}" 2>/dev/null || echo "")

  if [ -n "$pane_list" ]; then
    # Collect panes to close first, then close (avoids index shifting mid-loop)
    declare -a panes_to_close=()
    while IFS=':' read -r pane_idx rest; do
      title=$(echo "$rest" | sed 's/^ *//')
      pr=$(extract_pr_number "$title")
      if [ -n "$pr" ]; then
        state=$(check_pr_state "$pr")
        if [[ "$state" == "MERGED" || "$state" == "CLOSED" ]]; then
          panes_to_close+=("$pane_idx|$pr|$state")
        fi
      fi
    done <<< "$pane_list"

    # Close in reverse order to avoid index shifting
    for ((i=${#panes_to_close[@]}-1; i>=0; i--)); do
      IFS='|' read -r idx pr state <<< "${panes_to_close[$i]}"
      close_pane "$idx" "$pr" "$state"
    done
    unset panes_to_close
  fi

  # ── PART 2: Ensure open PRs have lanes + send instructions ──
  pr_json=$(gh pr list --repo "$REPO" --state open --json number,title,headRefName,mergeable --limit 50 2>/dev/null || echo "[]")

  if [ "$pr_json" != "[]" ]; then
    # Re-read pane list after closures
    pane_info=$(tmux list-panes -t "$WAVE_SESSION" -F "#{pane_index}: #{pane_title} | #{pane_current_path}" 2>/dev/null || echo "")

    echo "$pr_json" | jq -r '.[] | "\(.number)|\(.title)|\(.headRefName)|\(.mergeable)"' | while IFS='|' read -r pr title branch mergeable; do
      # Check if any pane covers this PR
      pane_idx=""
      if [ -n "$pane_info" ]; then
        pane_idx=$(echo "$pane_info" | grep -E "PR#${pr}\b" | head -1 | cut -d: -f1 | tr -d ' ')
        if [ -z "$pane_idx" ]; then
          pane_idx=$(echo "$pane_info" | grep -F -- "$branch" | head -1 | cut -d: -f1 | tr -d ' ')
        fi
      fi

      if [ -z "$pane_idx" ]; then
        # Create new lane
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Creating lane for PR#$pr ($title)"
        worktree_path="$WORKTREE_BASE/pr-$pr-lane"
        if [ -d "$worktree_path" ]; then
          # Worktree already exists (orphaned from a previous run) — fetch to avoid stale code
          git -C "$worktree_path" fetch origin 2>/dev/null || true
        else
          git -C "$REPO_PATH" worktree add "$worktree_path" --track -b "pr-$pr" "origin/$branch" 2>/dev/null || true
        fi

        if [ ! -d "$worktree_path" ]; then
          echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: worktree for PR#$pr could not be created at $worktree_path — skipping lane creation"
          continue
        fi

        new_pane=$(tmux split-window -P -F "#{pane_index}" -t "$WAVE_SESSION" -h -c "$worktree_path" 2>/dev/null || echo "")
        title_short=$(echo "$title" | cut -c1-35)
        if [ -n "$new_pane" ]; then
          tmux select-pane -t "$WAVE_SESSION.$new_pane" -T "PR#$pr $title_short" 2>/dev/null || true
          tmux select-layout -t "$WAVE_SESSION" tiled 2>/dev/null || true
          tmux send-keys -t "$WAVE_SESSION.$new_pane" \
            "cd '$worktree_path' && claude --model claude-sonnet-4-6 --dangerously-skip-permissions" Enter
          sleep 5
          pane_idx="$new_pane"
        fi
      else
        # Update title if needed
        title_short=$(echo "$title" | cut -c1-35)
        tmux select-pane -t "$WAVE_SESSION.$pane_idx" -T "PR#$pr $title_short" 2>/dev/null || true
      fi

      # Send instructions only when a pane is available
      if [ -n "$pane_idx" ]; then
        send_instructions "$pane_idx" "$pr" "$branch" "$mergeable"
      else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARN: no pane available for PR#$pr — skipping instructions"
      fi
    done
  fi

  sleep "$CYCLE_INTERVAL"
done

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

DEFAULT_REPO="vega113/supawave"
REPO="${GITHUB_REPO:-$DEFAULT_REPO}"
WAVE_SESSION="vibe-code:wave-lanes"
WORKTREE_BASE="/Users/vega/devroot/worktrees"
REPO_PATH="/Users/vega/devroot/incubator-wave"
CYCLE_INTERVAL=300
REPO_OWNER="${REPO%%/*}"
REPO_NAME="${REPO#*/}"

extract_pr_number() {
  local text="$1"
  [[ $text =~ PR#([0-9]+) ]] && echo "${BASH_REMATCH[1]}" || echo ""
}

# Extract PR number from worktree path like /Users/.../pr-700-lane
extract_pr_from_path() {
  local path="$1"
  [[ $path =~ pr-([0-9]+)-lane ]] && echo "${BASH_REMATCH[1]}" || echo ""
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
  gh api graphql -f query="{ repository(owner:\"${REPO_OWNER}\", name:\"${REPO_NAME}\") { pullRequest(number:${pr}) { reviewThreads(first:100) { nodes { isResolved } } } } }" \
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

send_prompt_via_file() {
  # Write prompt to a temp file and use tmux load-buffer + paste-buffer
  # to avoid tmux send-keys truncation on long prompts.
  local pane_idx=$1
  local prompt_text=$2
  local prompt_file
  prompt_file=$(mktemp /tmp/wave-lane-prompt-XXXXXX.txt)
  printf '%s' "$prompt_text" > "$prompt_file"
  tmux load-buffer "$prompt_file" 2>/dev/null || true
  tmux paste-buffer -t "$WAVE_SESSION.$pane_idx" 2>/dev/null || true
  tmux send-keys -t "$WAVE_SESSION.$pane_idx" Enter 2>/dev/null || true
  rm -f "$prompt_file"
}

launch_interactive_agent() {
  # Launch claude in INTERACTIVE mode so it stays running and user can see/steer it.
  # Prompt is delivered via tmux paste-buffer to avoid send-keys truncation.
  local pane_idx=$1
  local worktree_path=$2
  local initial_prompt=$3

  tmux send-keys -t "$WAVE_SESSION.$pane_idx" \
    "cd '$worktree_path' && claude --model claude-sonnet-4-6 --dangerously-skip-permissions" Enter
  # Wait for claude to fully initialize before sending the first message
  sleep 10
  send_prompt_via_file "$pane_idx" "$initial_prompt"
}

send_instructions() {
  local pane_idx=$1
  local pr=$2
  local branch=$3
  local mergeable=$4
  local title=$5

  # Check unresolved threads
  local threads
  threads=$(get_unresolved_threads "$pr")

  # Check CI
  local ci_failing=false
  if gh pr checks "$pr" --repo "$REPO" 2>/dev/null | grep -qE "fail|error"; then
    ci_failing=true
  fi

  # Determine what's wrong
  local msg=""
  if [[ "$threads" -gt 0 ]]; then
    msg="PR #$pr has $threads unresolved review threads. Read comments: gh api repos/$REPO/pulls/$pr/comments. Fix each issue, then resolve threads via GraphQL resolveReviewThread. All threads must be resolved for CI."
  elif [[ "$mergeable" != "MERGEABLE" ]]; then
    msg="PR #$pr has merge conflicts. Rebase: git fetch origin main && git rebase origin/main. Examine BOTH sides of each conflict carefully. Push with --force-with-lease."
  elif [[ "$ci_failing" == "true" ]]; then
    msg="PR #$pr has failing CI. Build: cd wave && sbt compile. Test: cd wave && sbt test. Fix root causes. Push when green."
  else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr is clean — ready to merge"
    return
  fi

  # Check if pane has a running claude agent or is idle zsh
  if is_pane_idle "$pane_idx"; then
    # No agent running — launch interactive claude first, then send prompt
    local worktree_path="$WORKTREE_BASE/pr-$pr-lane"
    if [ -d "$worktree_path" ]; then
      echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr — launching interactive agent with instructions"
      launch_interactive_agent "$pane_idx" "$worktree_path" \
        "You are fixing PR #$pr ($title). Branch: $branch. Repo: $REPO. $msg Steps: 1) Fix issues. 2) Resolve all review threads via GraphQL. 3) Rebase if needed. 4) Build and test. 5) Push when clean."
    fi
  else
    # Agent is already running — send via paste-buffer to avoid truncation
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PR#$pr — sending message to running agent"
    send_prompt_via_file "$pane_idx" "$msg"
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
  # Uses BOTH pane title (PR#NNN) and pane path (pr-NNN-lane) to detect PR association
  # Use tab as delimiter — safe since pane titles and paths cannot contain tabs
  pane_list=$(tmux list-panes -t "$WAVE_SESSION" -F "#{pane_index}"$'\t'"#{pane_title}"$'\t'"#{pane_current_path}" 2>/dev/null || echo "")

  if [ -n "$pane_list" ]; then
    declare -a panes_to_close=()
    while IFS=$'\t' read -r pane_idx title pane_path; do
      pane_idx=$(echo "$pane_idx" | tr -d ' ')
      # Try to get PR number from title first, then from path
      pr=$(extract_pr_number "$title")
      if [ -z "$pr" ]; then
        pr=$(extract_pr_from_path "$pane_path")
      fi
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
    echo "$pr_json" | jq -r '.[] | [.number, .title, .headRefName, .mergeable] | @tsv' | while IFS=$'\t' read -r pr title branch mergeable; do
      # Re-read pane list EACH iteration to see panes created in previous iterations
      # Use tab as delimiter — safe since pane titles and paths cannot contain tabs
      pane_info=$(tmux list-panes -t "$WAVE_SESSION" -F "#{pane_index}"$'\t'"#{pane_title}"$'\t'"#{pane_current_path}" 2>/dev/null || echo "")

      # Check if any pane covers this PR (by title, path with branch, or worktree name pr-NNN-lane)
      pane_idx=""
      if [ -n "$pane_info" ]; then
        pane_idx=$(echo "$pane_info" | grep -E "PR#${pr}\b" | head -1 | cut -f1)
        if [ -z "$pane_idx" ]; then
          pane_idx=$(echo "$pane_info" | grep -F -- "pr-${pr}-lane" | head -1 | cut -f1)
        fi
        if [ -z "$pane_idx" ]; then
          pane_idx=$(echo "$pane_info" | grep -F -- "$branch" | head -1 | cut -f1)
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
          launch_interactive_agent "$new_pane" "$worktree_path" \
            "You are fixing PR #$pr ($title_short). Branch: $branch. Repo: $REPO. Address all PR issues: unresolved review threads, conflicts, CI failures. Steps: 1) Read review comments. 2) Fix issues and resolve threads via GraphQL. 3) Rebase if conflicts. 4) Build: cd wave && sbt compile. 5) Test: cd wave && sbt test. 6) Push when clean."
          pane_idx="$new_pane"
        fi
      else
        # Update title if needed
        title_short=$(echo "$title" | cut -c1-35)
        tmux select-pane -t "$WAVE_SESSION.$pane_idx" -T "PR#$pr $title_short" 2>/dev/null || true
      fi

      # Send instructions only when a pane is available
      if [ -n "$pane_idx" ]; then
        send_instructions "$pane_idx" "$pr" "$branch" "$mergeable" "$title"
      else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARN: no pane available for PR#$pr — skipping instructions"
      fi
    done
  fi

  sleep "$CYCLE_INTERVAL"
done

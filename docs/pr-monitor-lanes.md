# PR Monitor Lanes

Use `scripts/start-pr-monitor.sh` to create or restart a monitor lane for a single incubator-wave PR.

Example:

```bash
scripts/start-pr-monitor.sh --pr-number 412 --worktree /Users/vega/devroot/worktrees/incubator-wave/pr412-live-monitor
```

Behavior:

- routes monitors into tmux window `wave-pr-monitor`
- titles the pane as `PR #<number> <title>`
- writes durable prompt, log, and runner files under `<shared-root>` (default: `/Users/vega/devroot/worktrees/pr-monitors/`, configurable via `--shared-root`)
- launches Codex with explicit `--dangerously-bypass-approvals-and-sandbox --sandbox danger-full-access`
- never uses `--full-auto`
- polls GitHub from the shell without model tokens while the PR is only waiting on pending checks, review-gate timing, or armed auto-merge
- starts or restarts the inner Codex monitor only when GitHub state looks actionable, such as unresolved review threads, conflicts, a behind branch, failed non-gate checks, or a merge-ready PR without auto-merge
- stops only when GitHub reports the PR merged or closed without merge
- leaves the pane open after completion for inspection

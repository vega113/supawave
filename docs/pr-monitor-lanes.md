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
- restarts the inner monitor if Codex exits while the PR is still open
- stops only when GitHub reports the PR merged or closed without merge
- leaves the pane open after completion for inspection

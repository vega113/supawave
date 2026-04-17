Status: Current
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Worktree Lane Lifecycle

Use this runbook when a GitHub issue lane already has its own incubator-wave
git worktree and needs a repeatable local boot flow without hidden setup steps.

## 1. Create the worktree

Create the branch and worktree from the primary checkout, then switch into the
new worktree before launching any agent or server process:

```bash
git -C /Users/vega/devroot/incubator-wave fetch origin
git -C /Users/vega/devroot/incubator-wave worktree add \
  /Users/vega/devroot/worktrees/issue-585-worktree-boot-lifecycle \
  -b issue-585-worktree-boot-lifecycle \
  origin/main
cd /Users/vega/devroot/worktrees/issue-585-worktree-boot-lifecycle
```

Do not launch tmux lanes or run `git switch` from the primary checkout. Lane
commands must run from the assigned worktree directory.

## 2. Launch the lane from the worktree

The lane entry command must `cd` into the worktree first:

```bash
tmux send-keys -t "wave-lanes:0.0" \
  "cd /Users/vega/devroot/worktrees/issue-585-worktree-boot-lifecycle && claude --model gpt-5.4-mini --dangerously-skip-permissions" \
  Enter
```

## 3. Reuse shared local file-store state when needed

If the task needs realistic local accounts, attachments, or deltas, wire the
shared file-store before booting the app:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

The default symlink mode is preferred. Use `--mode copy` only when isolated
persistence state is required.

## 4. Prepare the worktree boot assets

Run the helper from inside the worktree:

```bash
bash scripts/worktree-boot.sh --port 9899
```

Optional shared-store setup can be folded into the same step:

```bash
bash scripts/worktree-boot.sh --shared-file-store --port 9899
```

The helper does four things:

- verifies that it is running in a linked worktree instead of a primary
  checkout clone
- stages the app with `sbt -batch Universal/stage`
- creates a port-specific runtime config under `journal/runtime-config/`
- creates the canonical local-verification record under
  `journal/local-verification/`

The helper prints the exact `start`, `check`, `diagnostics`, and `stop`
commands for the lane. Use those commands as printed.

## 5. Handle port conflicts explicitly

If the requested port is already occupied, the helper exits with a clear error
and does not kill the listener. Choose a different port and rerun the helper:

```bash
bash scripts/worktree-boot.sh --port 9900
```

This is the standard response for GitHub-Issues lanes. Port pressure is not a
reason to skip local verification.

## 6. Record verification evidence

For GitHub-Issues workflow lanes that are not using Beads, record local
verification evidence in:

```text
journal/local-verification/<date>-issue-<number>-<slug>.md
```

The helper creates the file when the branch follows the
`issue-<number>-<slug>` naming pattern. Keep the record in the worktree and
mirror the important commands and outcomes into the PR body and issue comment.

Minimum record shape:

```markdown
# Local Verification

- Branch: issue-585-worktree-boot-lifecycle
- Worktree: /Users/vega/devroot/worktrees/issue-585-worktree-boot-lifecycle
- Date: 2026-04-03

## Commands

- `bash scripts/worktree-boot.sh --port 9899`
- `PORT=9899 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
- `PORT=9899 bash scripts/wave-smoke.sh check`
- `curl -sS http://localhost:9899/healthz`
- `PORT=9899 bash scripts/wave-smoke.sh stop`

## Results

- staged app built successfully
- health endpoint returned `200`
- shutdown completed cleanly

## Follow-up

- PR: <link>
- Issue: <link>
```

## 7. Start, verify, and stop the lane

After `scripts/worktree-boot.sh` finishes, run the printed commands in order:

1. Start the server with the printed `JAVA_OPTS` and `PORT`.
2. Run `bash scripts/wave-smoke.sh check`.
3. If startup or smoke fails, or if the issue/PR needs richer runtime detail,
   run `PORT=<port> bash scripts/worktree-diagnostics.sh --port <port>` and use
   [`worktree-diagnostics.md`](worktree-diagnostics.md) for the bundled
   evidence format.
4. If the change can affect browser-visible behavior, use
   [`browser-verification.md`](browser-verification.md) and
   [`change-type-verification-matrix.md`](change-type-verification-matrix.md)
   to decide whether a browser pass is required and what narrow path to check.
5. Stop the server with `bash scripts/wave-smoke.sh stop`.

This runbook standardizes only the base lifecycle. Browser-verification
expectations are standardized separately in
[`browser-verification.md`](browser-verification.md), and they still do not
require new browser automation or observability tooling.

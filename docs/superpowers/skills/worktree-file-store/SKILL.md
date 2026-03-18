---
name: worktree-file-store
description: Use when creating a new incubator-wave worktree that must reuse the existing file-based accounts, deltas, and attachments for realistic local testing.
---

# Worktree File Store

Use this repo-local helper when a new git worktree needs access to the current
file-based Wave persistence state.

Default behavior:
- Use symlinks, not copies.
- Reuse these directories from another checkout:
  - `wave/_accounts`
  - `wave/_attachments`
  - `wave/_deltas`

Run from the target worktree:

```bash
scripts/worktree-file-store.sh --source /path/to/source/checkout
```

Use copy mode only when you need isolated persistence state:

```bash
scripts/worktree-file-store.sh --source /path/to/source/checkout --mode copy
```

Notes:
- The target must be a git worktree for this repository.
- The source should be the checkout that already contains the live file-store
  data you want to test against.
- Symlink mode is preferred because it is fast and keeps test data aligned
  across worktrees.
- Copy mode is slower and creates isolated state, which is useful only for
  destructive experiments.

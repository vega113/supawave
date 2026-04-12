# Worktree Diagnostics

Use this runbook when an incubator-wave issue worktree needs a compact
diagnostics bundle for a failed verification run or when an issue comment / PR
summary needs more detail than raw command lines.

## 1. What The Bundle Captures

`scripts/worktree-diagnostics.sh` stays on top of the existing worktree flow. It
does not stage or start the app. Instead, it captures the current state of the
already-prepared worktree:

- branch, worktree, port, runtime-config, and evidence-file metadata
- current endpoint probe statuses for `/`, `/healthz`, `/readyz`, and the
  compiled webclient asset
- current `scripts/wave-smoke.sh check` output and exit status
- last-N lines from the staged startup output (`wave_server.out`)
- last-N lines from the staged server log (`logs/wave.log`,
  `logs/wave-json.log`, or `wiab-server.log`, depending on the runtime)

The command stays useful when the runtime is unhealthy. Missing files and
non-zero smoke checks are rendered as evidence, not treated as fatal errors.

## 2. Default Usage

After `worktree-boot.sh` has prepared the staged app, use:

```bash
PORT=9900 bash scripts/worktree-diagnostics.sh --port 9900
```

Typical timing:

1. after `PORT=9900 bash scripts/wave-smoke.sh check` fails
2. after the smoke passes but the issue comment or PR needs richer detail
3. after a startup timeout so the bundle can capture the startup and server-log
   tails

## 3. Persisted Output

To keep a durable copy in the worktree-local verification area, use `--output`:

```bash
PORT=9900 bash scripts/worktree-diagnostics.sh --port 9900 \
  --output journal/local-verification/2026-04-12-issue-587-worktree-diagnostics-bundle.md
```

This prints the bundle to stdout and writes the same Markdown to the requested
file.

## 4. Expected Output Shape

The bundle contains these sections:

- `# Worktree Diagnostics Bundle`
- worktree metadata bullets
- `## Endpoint Probes`
- `## Smoke Check`
- `## Startup Output Tail (...)`
- `## Server Log Tail (...)`

Example:

````markdown
# Worktree Diagnostics Bundle

- Branch: issue-587-worktree-diagnostics-20260412
- Worktree: /Users/vega/devroot/worktrees/issue-587-worktree-diagnostics-20260412
- Port: 9900

## Endpoint Probes

- `GET /` -> `302`
- `GET /healthz` -> `200`

## Smoke Check

- Command: `PORT=9900 bash scripts/wave-smoke.sh check`
- Smoke exit: `1`

```text
ROOT_STATUS=302
Unexpected health status: 500
```
````

When files are missing, the bundle prints explicit `(missing: /path/to/file)`
markers so the issue comment or PR summary can show exactly what was absent.

## 5. How To Reference The Bundle

Summarize the important lines in the issue comment or PR body:

- endpoint statuses
- smoke exit/result
- the most relevant startup or server-log tail lines
- the path to the saved diagnostics file when `--output` was used

This keeps the issue/PR traceable without pasting an entire raw server log.

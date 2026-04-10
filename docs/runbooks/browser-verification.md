# Browser Verification

Use this runbook to decide when incubator-wave changes need real browser
verification and how to run that verification without introducing a new
browser framework.

## 1. Baseline

The existing shell baseline stays in place:

- Standalone/source-tree UI smoke: `bash scripts/wave-smoke-ui.sh`
- Worktree-lane equivalent: `bash scripts/worktree-boot.sh --port <port>`,
  followed by the printed `wave-smoke.sh start|check|stop` commands

The worktree-lane flow is the port-aware equivalent of `wave-smoke-ui.sh`.
It uses the existing staged distribution and the existing smoke checks instead
of inventing another wrapper around the same commands.

## 2. Default Path For UI-Affecting Changes

When a change can affect browser-visible behavior, use this default path:

1. Run `bash scripts/worktree-boot.sh --port 9900` from the issue worktree.
2. Start the staged server with the exact `PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
   command printed by `worktree-boot.sh`.
3. Run `PORT=9900 bash scripts/wave-smoke.sh check`.
4. If the change-type matrix says browser verification is required, open
   `http://localhost:9900/` and exercise only the narrow path affected by the
   change.
5. Stop the server with `PORT=9900 bash scripts/wave-smoke.sh stop`.

The smoke checks verify the server boots, health responds, and the compiled
web client asset is present; the browser pass verifies the specific auth,
routing, or UI behavior that curl cannot prove.

## 3. When Curl Or Smoke Is Enough

Smoke-only verification is usually enough when the change cannot alter rendered
browser behavior. Typical examples:

- backend-only logic with no auth, servlet, routing, or client-asset changes
- search/indexing/storage changes that surface through existing server APIs but
  do not alter the rendered page shell
- deployment changes whose validation is already covered by deployment runbooks
  and do not change client assets, headers, or auth/session behavior

In those cases, record the exact smoke or curl commands you ran and note that
the matrix did not require a browser pass.

## 4. When Browser Verification Is Required

Add a browser pass when the change can alter browser-visible behavior, even if
the smoke checks are green. This includes:

- auth and session transitions
- servlet-rendered routes or redirects
- GWT client/UI changes, including CSS, layout, editor behavior, and widgets
- packaging/build changes that can change the served client assets
- deployment changes that can change page delivery, auth headers, or static
  asset serving

Use the smallest possible manual or scripted browser flow that proves the
affected behavior. This runbook standardizes the expectation, not the exact UI
steps for every feature. Feature-specific plans should still describe the
narrow browser flow they need.

For change-type defaults, use
[`change-type-verification-matrix.md`](change-type-verification-matrix.md).

## 5. Evidence To Record

Record the verification evidence in the issue comment and, for issue worktrees,
in `journal/local-verification/<date>-issue-<number>-<slug>.md`.

Minimum evidence:

- exact startup, smoke, and shutdown commands
- whether the matrix required browser verification
- the URL or route checked in the browser
- the narrow user-visible behavior that was confirmed
- the observed result

Example:

```markdown
- `bash scripts/worktree-boot.sh --port 9900`
- `PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start`
- `PORT=9900 bash scripts/wave-smoke.sh check`
- Browser verification required by `docs/runbooks/change-type-verification-matrix.md` (`GWT client/UI` row)
- Opened `http://localhost:9900/` and confirmed the affected toolbar/action/auth flow behaved as expected
- `PORT=9900 bash scripts/wave-smoke.sh stop`
```

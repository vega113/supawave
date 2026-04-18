# Local Verification

- Branch: issue-903-j2cl-pure-logic
- Worktree: /Users/vega/devroot/worktrees/issue-903-j2cl-pure-logic
- Date: 2026-04-19

## Commands

- `bash scripts/worktree-boot.sh --port 9900`
- `PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-903-j2cl-pure-logic/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-903-j2cl-pure-logic/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
- `PORT=9900 bash scripts/wave-smoke.sh check`
- `scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave`
- `PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-903-j2cl-pure-logic/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-903-j2cl-pure-logic/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
- `PORT=9900 bash scripts/wave-smoke.sh check`
- `curl -s -o /tmp/issue903-register.out -w '%{http_code}\n' -X POST 'http://localhost:9900/auth/register' -d 'address=issue903test&password=issue903test'`
- Browser sanity via `agent-browser --session issue903`

## Results

- `worktree-boot.sh` succeeded on port `9900` and rebuilt the staged distribution plus GWT webclient assets.
- First smoke check passed: `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`.
- The worktree had no local `_accounts`, `_attachments`, or `_deltas` content, so I linked the shared file-store from `/Users/vega/devroot/incubator-wave`. That store was effectively empty as well, so there was no preexisting conversation to open.
- Second smoke start/check after linking the shared file-store also passed: `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`.
- Local auth endpoints worked on the branch server. Registering `issue903test` returned HTTP `302`, and browser login succeeded on `http://localhost:9900/auth/signin`.
- Browser sanity on the branch server:
  - logged into `http://localhost:9900/` as `issue903test@local.net`
  - created a wave from the local UI toolbar
  - confirmed navigation to `http://localhost:9900/#local.net/w+6iM5HKdUjpA`
  - reopened the same wave URL and confirmed the conversation shell loaded again without an obvious `ViewChannel`, fragment-application, or OT bootstrap regression
  - observed fragment/debug state after reload: `Fragments: 0 | Applier: on | Blips: 1/1 | Applied: 0/0 | T=5ms | Mode: stream | Fetch: on | Dyn: on`
- Final shutdown succeeded with `PORT=9900 bash scripts/wave-smoke.sh stop`.

## Follow-up

- PR: pending
- Issue: `#903`

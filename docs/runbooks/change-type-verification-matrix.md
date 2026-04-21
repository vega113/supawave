Status: Current
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Change-Type Verification Matrix

Use this matrix after the base worktree boot/smoke flow succeeds. It decides
whether the change needs a real browser pass or whether curl/smoke evidence is
enough.

| Change type | Typical examples | Default scripted baseline | Browser verification | Browser focus | Evidence |
| --- | --- | --- | --- | --- | --- |
| Server-only | backend services, storage, indexing, logging, internal RPC logic with no auth/page-shell/client-asset change | `bash scripts/worktree-boot.sh --port <port>` then `PORT=<port> ... bash scripts/wave-smoke.sh start`, `PORT=<port> bash scripts/wave-smoke.sh check`, `PORT=<port> bash scripts/wave-smoke.sh stop` | Usually unnecessary | None unless the server change leaks into rendered behavior | record the exact smoke/curl commands and note that the matrix did not require a browser pass |
| Servlet/auth | signin/signout handlers, session filters, redirects, servlet-rendered pages, route guards | same worktree baseline as above | Required | sign-in/sign-out, redirect target, page shell, session restoration, route access | record the commands, route checked, credentials/test account if relevant, and observed auth result |
| GWT client/UI | widgets, CSS, layout, editor behavior, topbar, client routing, browser-only regressions | same worktree baseline as above | Required | the narrow user-visible path touched by the change | record the commands, affected route, action performed, and browser-visible result |
| Packaging/build/distribution | staged dist changes, asset packaging, cache-busting, static resource wiring | worktree baseline above and, when useful, standalone `bash scripts/wave-smoke-ui.sh` | Required when browser-visible assets/pages can change; otherwise optional | home page load, J2CL root-shell marker, served `/webclient/webclient.nocache.js`, signin page, affected asset path | record whether the change altered browser-visible packaging and which asset/page check was performed |
| Deployment-only | compose/systemd/Caddy/deploy scripts with no app-code change | deployment/runbook verification plus smoke on the target environment or a local equivalent | Usually unnecessary; required if delivery/auth/static-asset behavior can change | root page, signin flow, or client asset delivery only when the deployment change can affect them | record deployment or smoke commands and explicitly note whether browser verification was or was not required |

Rules of thumb:

- If the change cannot alter rendered browser behavior, curl/smoke is enough.
- If the change can affect auth, routing, GWT rendering, or served client
  assets, add a browser pass.
- Keep the browser pass narrow. The goal is not a full exploratory session; it
  is proof that the affected path still behaves correctly.

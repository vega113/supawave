# Caddy Deployment Flavor Implementation Plan

> **For agentic workers:** REQUIRED: Follow `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` as the canonical execution model when it applies, and use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the reviewed deployment-flavor design by shipping provider-neutral Linux host docs, standalone and Caddy deployment guides, official Caddy assets, systemd examples, and host bootstrap/prerequisite guidance without regressing the current automated deployment path.

**Architecture:** Keep Wave standalone-capable while elevating Caddy to an official optional deployment flavor. Make the canonical docs and deploy assets provider-neutral (`linux-host`, `deploy/caddy`) and keep current automation working either by updating it to the new generic asset path or by retaining compatibility shims during the transition. The implementation is documentation- and asset-heavy, so verification should emphasize config correctness, script sanity, and cross-reference consistency rather than application logic changes.

**Tech Stack:** Markdown docs, Bash scripts, Docker Compose, Caddy config, systemd unit examples, GitHub Actions YAML.

---

## Naming Task

Task `incubator-wave-deployment.7` is a follow-on provider-neutral naming sweep that is blocked by this implementation task.
This plan (`incubator-wave-deployment.6`) establishes the new canonical provider-neutral assets under `deploy/caddy/` and the new deployment documentation structure.
Any new or rewritten assets in this task must use generic Linux-host/Caddy naming from the start, while task `.7` handles the broader codebase-wide cleanup of canonical `contabo` references that remain outside the new canonical surfaces.

## Chunk 1: Establish Provider-Neutral Deployment Assets

### Task 1: Create the new provider-neutral asset layout

**Files:**
- Create: `deploy/caddy/README.md`
- Create: `deploy/caddy/Caddyfile`
- Create: `deploy/caddy/compose.yml`
- Create: `deploy/caddy/application.conf`
- Create: `deploy/caddy/deploy.sh`
- Create: `deploy/caddy/deploy.env.example`
- Create: `deploy/systemd/wave-standalone.service`
- Create: `deploy/systemd/wave.service`
- Create: `deploy/systemd/caddy.service`
- Create: `scripts/deployment/bootstrap-linux-host.sh`
- Modify: `deploy/contabo/Caddyfile`
- Modify: `deploy/contabo/compose.yml`
- Modify: `deploy/contabo/application.conf`
- Modify: `deploy/contabo/deploy.sh`
- Modify: `deploy/contabo/deploy.env.example`
- Modify: `.github/workflows/deploy-contabo.yml`

- [ ] **Step 1: Inspect the current deploy assets and workflow expectations**

Run:
```bash
sed -n '1,240p' deploy/contabo/Caddyfile
sed -n '1,260p' deploy/contabo/compose.yml
sed -n '1,260p' .github/workflows/deploy-contabo.yml
```
Expected: confirm the current bundle still reads from `deploy/contabo/*` and note exactly which file paths the workflow packages.

- [ ] **Step 2: Copy the current deployment assets into the new canonical Caddy path**

Create these canonical assets from the current working `deploy/contabo/*` versions:
- `deploy/caddy/Caddyfile`
- `deploy/caddy/compose.yml`
- `deploy/caddy/application.conf`
- `deploy/caddy/deploy.sh`
- `deploy/caddy/deploy.env.example`

Expected content rules:
- same runtime topology as today (`wave + caddy [+ mongo only if still needed by the chosen example]`)
- no provider-specific naming in comments or variable names
- hostnames remain examples, not vendor assumptions
- `application.conf` no longer hardcodes `wave.supawave.ai` as if it were the only supported deployment target
- `deploy.sh` error messages and comments no longer say `Contabo host`; use generic Linux host wording instead
- `deploy.env.example` is copied into the canonical path and generalized so it documents registry/deploy variables without vendor-specific wording
- the copied env example matches the expectations of `deploy/caddy/deploy.sh` and becomes the canonical env file referenced by the docs

- [ ] **Step 3: Decide and implement the compatibility strategy for current automation**

Implementation decision:
- update `.github/workflows/deploy-contabo.yml` to package from `deploy/caddy/*`
- update the workflow and docs to read from `deploy/caddy/*` directly, and add a short `deploy/contabo/README.md` note explaining that the provider-specific path has been superseded by the canonical provider-neutral asset path

The GitHub workflow update must account for all four currently bundled files:
- `deploy/contabo/compose.yml`
- `deploy/contabo/Caddyfile`
- `deploy/contabo/application.conf`
- `deploy/contabo/deploy.sh`

And it must also account for the deploy environment example path:
- `deploy/contabo/deploy.env.example`

For `deploy/contabo/deploy.env.example`, either remove it with the legacy path cleanup or replace it with a short note that points operators to `deploy/caddy/deploy.env.example`.

Rollback note:
- if the workflow path update breaks deployment automation, revert the asset-path change commit to restore the previous `deploy/contabo/*` packaging immediately.

Expected result:
- current deployment automation still works
- canonical assets live under `deploy/caddy/`

- [ ] **Step 4: Add systemd examples for both deployment flavors**

Create:
- `deploy/systemd/wave-standalone.service`
- `deploy/systemd/wave.service`
- `deploy/systemd/caddy.service`

Requirements:
- standalone unit documents the optional `:80` companion behavior when applicable
- caddy-fronted units clearly separate Wave and Caddy responsibilities
- units are examples, not hardcoded to one username or one VPS vendor

- [ ] **Step 5: Add the Linux host bootstrap script**

Create `scripts/deployment/bootstrap-linux-host.sh`.

Also create `deploy/caddy/README.md` summarizing what lives in the canonical asset directory and which files operators should use first.

Scope:
- install or verify baseline host prerequisites for the reference Linux environment
- support a dry-run mode
- cover common packages/tools only, not full application deployment

At minimum handle:
- `curl`, `tar`, `openssl`
- Java 17 for standalone guidance
- Docker Engine and `docker compose` for Docker/Caddy path

- [ ] **Step 6: Verify config and script syntax**

Run:
```bash
bash -n scripts/deployment/bootstrap-linux-host.sh
set -a
source deploy/caddy/deploy.env.example
set +a
WAVE_IMAGE=ghcr.io/example/wave:test \
DEPLOY_ROOT=/tmp/supawave \
WAVE_INTERNAL_PORT=9898 \
CANONICAL_HOST=wave.example.test \
ROOT_HOST=example.test \
WWW_HOST=www.example.test \
docker compose -f deploy/caddy/compose.yml config | tee /tmp/caddy-compose.rendered
grep -E 'YOUR_|CHANGEME|\$\{[A-Z_][A-Z0-9_]*\}' /tmp/caddy-compose.rendered && exit 1 || true
```
Expected: shell syntax passes, the canonical env example loads successfully, Compose config renders without missing variable errors, and the rendered config does not contain unresolved placeholder values.

If Docker is unavailable locally, document the Docker-dependent checks as required follow-up verification on a Docker-capable machine or CI runner rather than silently skipping them.

- [ ] **Step 7: Verify systemd examples structurally**

Primary verification path:
```bash
docker run --rm -v "$PWD/deploy/systemd:/units:ro" debian:bookworm \
  bash -lc 'apt-get update >/dev/null && apt-get install -y systemd >/dev/null && systemd-analyze verify /units/*.service'
```
Optional direct Linux-host verification when available:
```bash
systemd-analyze verify deploy/systemd/*.service
```
Last-resort fallback if neither container nor Linux-host verification is available:
```bash
rg -n '^[[]Unit[]]|^[[]Service[]]|^[[]Install[]]' deploy/systemd/*.service
```
Expected: real unit verification in a Linux container or on a Linux host; only fall back to section-structure grep when stronger verification is not available, and document that limitation in the task notes.

- [ ] **Step 8: Commit the asset/layout slice**

```bash
git add deploy/caddy deploy/systemd scripts/deployment/bootstrap-linux-host.sh .github/workflows/deploy-contabo.yml deploy/contabo

git commit -m "Add provider-neutral deployment assets"
```

## Chunk 2: Write the Deployment Documentation Set

### Task 2: Add the new provider-neutral docs

**Files:**
- Create: `docs/deployment/README.md`
- Create: `docs/deployment/linux-host.md`
- Create: `docs/deployment/standalone.md`
- Create: `docs/deployment/caddy.md`
- Modify: `docs/deployment/contabo.md`
- Modify: `docs/deployment/cloudflare-supawave.md`
- Modify: `README.md`
- Modify: `docs/current-state.md`

- [ ] **Step 0: Confirm Chunk 1 assets exist before writing cross-referencing docs**

Run:
```bash
find deploy/caddy deploy/systemd scripts/deployment -type f | sort
```
Expected: the canonical assets referenced by the docs already exist in the worktree.

- [ ] **Step 1: Write `docs/deployment/README.md` and `docs/deployment/linux-host.md`**

Required sections in `docs/deployment/README.md`:
- deployment doc entry points and reading order
- short explanation of standalone vs caddy-fronted modes
- links to `linux-host.md`, `standalone.md`, `caddy.md`, and any optional overlays

Required sections in `docs/deployment/linux-host.md`:
- supported host baseline
- prerequisites checklist
- reference bootstrap script usage
- links to standalone and caddy-fronted guides
- provider-neutral posture and optional provider overlays

- [ ] **Step 2: Write `docs/deployment/standalone.md`**

Required sections:
- what standalone means
- when to choose it
- direct TLS/keystore expectations
- optional `:80` listener/redirect or ACME companion note
- validation checklist
- migration notes from/to caddy-fronted mode
- troubleshooting subset relevant to standalone

- [ ] **Step 3: Write `docs/deployment/caddy.md`**

Required sections:
- what Caddy is
- what role it plays relative to Wave
- why it is recommended for many operators
- how the `wave + caddy` topology works
- Docker and non-Docker/systemd examples
- validation checklist
- migration notes from/to standalone mode
- troubleshooting subset relevant to caddy-fronted mode

- [ ] **Step 4: Convert `docs/deployment/contabo.md` into an optional provider overlay**

Required outcome:
- stop treating Contabo as the canonical deployment story
- keep only provider-specific notes that sit on top of `linux-host.md` and the chosen flavor guide
- link back to the generic docs as the source of truth

- [ ] **Step 5: Reposition `docs/deployment/cloudflare-supawave.md` in the new doc structure**

Required outcome:
- make it clear that Cloudflare is an optional DNS/proxy overlay, not part of the canonical host baseline
- link it from the generic docs only where relevant
- avoid leaving it as an orphaned provider-specific note with no relationship to `linux-host.md`, `standalone.md`, or `caddy.md`
- add a short prerequisites/header note that points readers back to `linux-host.md`
- add links from the relevant generic docs where DNS or proxy overlays are discussed

- [ ] **Step 6: Update `README.md` and `docs/current-state.md`**

Required outcome:
- README points to the two supported deployment flavors and the generic Linux host guide
- current-state explicitly says:
  - standalone direct-TLS Wave is supported
  - Caddy-fronted Wave is supported and recommended for many operators
  - Cloudflare is optional, not required

- [ ] **Step 7: Add the rendered decision matrix and acceptance-driven content checks**

Run:
```bash
rg -n 'Decision Matrix|Troubleshooting|Out of Scope|linux-host|standalone|caddy-fronted' \
  README.md docs/current-state.md docs/deployment/*.md
```
Expected: all required conceptual sections exist in the new docs.

- [ ] **Step 8: Commit the documentation slice**

```bash
git add README.md docs/current-state.md docs/deployment

git commit -m "Document standalone and Caddy deployment modes"
```

## Chunk 3: Validate Cross-References and Operational Consistency

### Task 3: Reconcile docs, assets, and workflow behavior

**Files:**
- Modify: `docs/deployment/linux-host.md`
- Modify: `docs/deployment/standalone.md`
- Modify: `docs/deployment/caddy.md`
- Modify: `docs/deployment/contabo.md`
- Modify: `README.md`
- Modify: `docs/current-state.md`
- Modify: `.beads/issues.jsonl`

- [ ] **Step 1: Verify all referenced paths exist**

Run:
```bash
find deploy docs/deployment scripts/deployment -type f | sort
```
Expected: every documented file path exists in the repo.

- [ ] **Step 2: Verify docs do not leave provider-specific language as canonical**

Run:
```bash
rg -n 'Contabo|canonical deployment|provider-neutral|Ubuntu LTS|Cloudflare' \
  README.md docs/current-state.md docs/deployment/*.md
```
Expected:
- `Contabo` appears only as an optional provider-specific example
- generic Linux host guidance is the canonical path

- [ ] **Step 3: Verify the workflow still references the intended asset path**

Run:
```bash
rg -n 'deploy/(contabo|caddy)' .github/workflows/deploy-contabo.yml docs/deployment/*.md README.md
```
Expected: workflow/docs point consistently at the chosen canonical asset path and any compatibility path is explicitly documented.

- [ ] **Step 3a: Add CI validation for the canonical deployment assets**

Modify the appropriate build workflow so it runs at least:
```bash
bash -n scripts/deployment/bootstrap-linux-host.sh
WAVE_IMAGE=ghcr.io/example/wave:test DEPLOY_ROOT=/tmp/supawave WAVE_INTERNAL_PORT=9898 CANONICAL_HOST=wave.example.test ROOT_HOST=example.test WWW_HOST=www.example.test docker compose -f deploy/caddy/compose.yml config
```
And, if practical on the runner, a structural validation of the systemd examples.

Expected: the new canonical deployment assets are checked automatically on push instead of relying only on manual execution.

Live end-to-end execution of `deploy-contabo.yml` against a real host is explicitly deferred out of this documentation/assets task and should be handled as a separate operational verification step after merge.

- [ ] **Step 4: Update Beads with plan and verification summary**

Add comments to `incubator-wave-deployment.6` covering:
- plan path
- commit SHAs
- verification commands
- any compatibility choice kept for the current deploy workflow

- [ ] **Step 5: Final review and commit if cross-reference fixes were needed**

Run:
```bash
git diff --check
```
Expected: no whitespace/path reference issues remain.

Commit if needed:
```bash
git add README.md docs/current-state.md docs/deployment .github/workflows/deploy-contabo.yml .beads/issues.jsonl deploy scripts

git commit -m "Tighten deployment docs and references"
```

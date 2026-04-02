# Deploy Recovery Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore successful `Deploy Contabo` runs by fixing the verified migration and provisioning defects that currently break production deploys.

**Architecture:** Treat this as three narrow reliability bugs in one deploy path, not a redesign. The bundle/path and optional-`java` defects from `a23a78856` were already fixed by `122afdd76`; this recovery plan picks up the remaining live failures. Fix the legacy-to-blue-green migration image probe in `deploy/caddy/deploy.sh`, make every swap-size comparison tolerant to real kernel-reported sizes in the host-tuning scripts, and add deterministic regression tests plus CI wiring so these failures cannot recur silently.

**Tech Stack:** Bash, Docker Compose v2, GitHub Actions, Python `unittest`, SSH-based host verification

**Evidence:**
- Run `23865935933` failed after `a23a78856` because the deploy bundle did not include `deploy/production/sysctl-tuning.conf` / `limits.conf.prod`, and `validate.sh` still treated `java` as a hard preflight requirement. Those defects were already fixed by `122afdd76`, so they are context for this incident, not new implementation scope for this recovery plan.
- Run `23877202417` and `23874408522` failed because `/swapfile` reported `34359734272` bytes after recreation, which is `4096` bytes below the nominal `32 GiB` byte count required by the scripts.
- Run `23877956797` still fails on the live host because `/home/ubuntu/supawave` is on the legacy single-slot layout (`supawave-wave-1`, `supawave-caddy-1`, no `shared/active-slot`), and `migrate_to_blue_green()` exits on `docker compose ... images wave --format '{{.Repository}}:{{.Tag}}'` before any migration logging. On the host, that command returns `format value "{{.Repository}}:{{.Tag}}" could not be parsed: parsing failed`.

**Non-Goals:**
- Do not redesign the blue-green topology.
- Do not change unrelated mail deliverability settings, Caddy routing behavior, or Mongo topology.
- Do not add new operational dependencies on the host beyond what the current deploy path already assumes.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `scripts/tests/test_deploy_recovery.py` | Create | Deterministic regression tests for migration probe and swap validation behavior |
| `.github/workflows/build.yml` | Modify | Run the new Python deploy regression tests in CI |
| `deploy/caddy/deploy.sh` | Modify | Replace the brittle legacy image probe and add explicit migration-step logging |
| `deploy/supawave-host/setup-swap.sh` | Modify | Make swap validation accept the real active size with a small tolerance |
| `deploy/supawave-host/validate.sh` | Modify | Match the swap tolerance logic used by setup-swap |

## Chunk 1: Regression Coverage

### Task 1: Add regression tests before changing deploy logic

**Files:**
- Create: `scripts/tests/test_deploy_recovery.py`
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Read the existing Python script-test style**

Run:
```bash
sed -n '1,220p' scripts/tests/test_validate_changelog.py
sed -n '1,240p' scripts/tests/test_pr_monitor.py
```
Expected: existing `unittest`-based pattern with temp directories and subprocess/mock helpers.

- [ ] **Step 2: Write the failing migration-probe regression test**

Implement a test that exercises the legacy migration seam in `deploy/caddy/deploy.sh` through a temp `DEPLOY_ROOT` and a mocked `docker` executable on `PATH`.

Required fixture behavior:
- legacy layout exists at `<tmp>/current/compose.yml`
- no `shared/active-slot` exists
- mocked `docker compose ... images wave --format ...` returns exit `1` with stderr `format value ... could not be parsed`
- mocked `docker inspect --format '{{.Config.Image}}' supawave-wave-1` returns `ghcr.io/example/wave:legacy`

Required assertion for the initial failing test:
- current script exits non-zero during `deploy`
- stderr/stdout shows it dies before logging a migration decision beyond the top-level `Deploying to ...` line

- [ ] **Step 3: Write the failing swap-tolerance regression test**

Implement a test that runs the swap validation path with mocked `swapon`, `fallocate`, `mkswap`, and `sysctl` commands.

Required fixture behavior:
- `swapon --show=NAME,SIZE --bytes` returns `/swapfile 34359734272`
- requested swap target remains `32`
- no other blocking validation failures are present

Required assertion for the initial failing test:
- current `setup-swap.sh` or `validate.sh post` rejects the swapfile even though it is only `4096` bytes below nominal size
- current `setup-swap.sh` also takes the recreation branch for that same near-nominal swapfile, proving the `main()` guard needs the same tolerance fix

- [ ] **Step 4: Wire the new tests into CI**

Extend the existing `Validate canonical deployment assets` section in `.github/workflows/build.yml` with a new named step immediately after the current Bash/Compose validation commands and before the Java/SBT work. This repo already includes `scripts/__init__.py` and `scripts/tests/__init__.py`, so standardize on dotted-module `unittest` invocation:
```bash
python3 -m unittest \
  scripts.tests.test_validate_changelog \
  scripts.tests.test_pr_monitor \
  scripts.tests.test_deploy_recovery
```

- [ ] **Step 5: Run the new tests to verify they fail for the expected reasons**

Run:
```bash
python3 -m unittest scripts.tests.test_deploy_recovery -v
```
Expected:
- migration probe test fails because `deploy/caddy/deploy.sh` still uses the unsupported `docker compose images --format` call
- swap test fails because the scripts still require the exact nominal byte count

- [ ] **Step 6: Commit the failing tests scaffold**

Run:
```bash
git add scripts/tests/test_deploy_recovery.py .github/workflows/build.yml
git commit -m "test: cover deploy recovery regressions"
```

---

## Chunk 2: Fix The Legacy Migration Probe

### Task 2: Make blue-green migration work on the live legacy host layout

**Files:**
- Modify: `deploy/caddy/deploy.sh`
- Test: `scripts/tests/test_deploy_recovery.py`

- [ ] **Step 1: Replace the brittle legacy image lookup**

In `migrate_to_blue_green()`, remove this unsupported probe:
```bash
current_image=$(docker compose -f "$old_compose" -p "$PROJECT_NAME" \
  images wave --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | head -1)
```

Replace it with a Compose-v2-safe probe that cannot fail silently under `set -euo pipefail`, for example:
- first resolve the live container id with `docker compose -f "$old_compose" -p "$PROJECT_NAME" ps -q wave`
- if present, resolve the image with a guarded inspect call such as `docker inspect --format '{{.Config.Image}}' "$container_id" 2>/dev/null || true`
- if the service is absent but the legacy container still exists, fall back to a guarded `docker inspect --format '{{.Config.Image}}' supawave-wave-1 2>/dev/null || true`
- if neither path yields an image, log a fresh-install message explicitly and return cleanly

- [ ] **Step 2: Add explicit step logging around the migration seam**

Add clear logs before each branch that can currently fail silently:
- old compose discovery
- legacy container image discovery
- legacy container stop/remove
- first blue-slot start

Do not add noisy tracing. Add one log line per meaningful decision so the next deploy failure is diagnosable from the GitHub Actions log alone.

- [ ] **Step 3: Preserve existing migration scope**

Do not expand the migration beyond what is already intended:
- keep the current legacy `current/` symlink fallback
- keep the current index/session move logic
- keep the current caddy upstream bootstrap flow
- keep `PROJECT_NAME` compatibility with the existing `supawave` host project

- [ ] **Step 4: Run the regression test and static checks**

Run:
```bash
bash -n deploy/caddy/deploy.sh
python3 -m unittest scripts.tests.test_deploy_recovery -v
```
Expected:
- syntax check passes
- migration regression now passes
- swap regression still fails until Chunk 3 is implemented

- [ ] **Step 5: Perform a local compose validation smoke**

This only validates Compose rendering and required env interpolation. It does not exercise the migration path; the regression test in Step 4 is the migration proof.

Run:
```bash
WAVE_IMAGE_BLUE=ghcr.io/example/wave:test \
DEPLOY_ROOT=/tmp/supawave \
CANONICAL_HOST=wave.example.test \
ROOT_HOST=example.test \
WWW_HOST=www.example.test \
RESEND_API_KEY=test-dummy \
WAVE_EMAIL_FROM=test-dummy \
docker compose -f deploy/caddy/compose.yml config >/tmp/caddy-compose.out
```
Expected: compose renders successfully.

- [ ] **Step 6: Commit the migration fix**

Run:
```bash
git add deploy/caddy/deploy.sh scripts/tests/test_deploy_recovery.py
git commit -m "fix: make blue-green migration work on legacy hosts"
```

---

## Chunk 3: Fix Swap Validation False Negatives

### Task 3: Accept valid swapfiles that report slightly below the nominal byte count in every swap-size branch

**Files:**
- Modify: `deploy/supawave-host/setup-swap.sh`
- Modify: `deploy/supawave-host/validate.sh`
- Test: `scripts/tests/test_deploy_recovery.py`

- [ ] **Step 1: Define one shared tolerance rule**

Adopt a small tolerance that matches the observed production failure and page-size realities.

Required behavior:
- target remains `SWAP_SIZE_GB * 1024 * 1024 * 1024`
- allow at least `4096` bytes of shortfall
- keep failure behavior for materially undersized swapfiles

Implement the rule once per script with the same formula, for example:
```bash
minimum_bytes=$((expected_bytes - 4096))
```
Use a named variable so the threshold is readable and consistent.

- [ ] **Step 2: Update every `setup-swap.sh` size comparison**

Change both of these branches to use the same `minimum_bytes` threshold:
- `validate_swap()` should compare the active size against `minimum_bytes`, not the exact nominal count
- the `main()` branch that currently checks whether the existing active swapfile is `-ge $swap_bytes` should also use the same threshold so a valid `32 GiB - 4096 bytes` swapfile does not trigger unnecessary recreation on every run

- [ ] **Step 3: Update `validate.sh` post-flight validation**

Change `check_swap()` to compute the same threshold explicitly, for example `minimum_bytes=$((expected_bytes - 4096))`, and continue honoring `SWAP_SIZE_GB` if overridden.

- [ ] **Step 4: Run the focused regression tests**

Run:
```bash
python3 -m unittest scripts.tests.test_deploy_recovery -v
```
Expected:
- migration probe test passes
- swap tolerance test passes

- [ ] **Step 5: Re-run build-path checks**

Run:
```bash
bash -n deploy/supawave-host/setup-swap.sh
bash -n deploy/supawave-host/validate.sh
python3 -m unittest \
  scripts.tests.test_validate_changelog \
  scripts.tests.test_pr_monitor \
  scripts.tests.test_deploy_recovery -v
```
Expected: all checks pass.

- [ ] **Step 6: Commit the swap fix**

Run:
```bash
git add deploy/supawave-host/setup-swap.sh deploy/supawave-host/validate.sh scripts/tests/test_deploy_recovery.py
git commit -m "fix: tolerate real swapfile sizes in deploy validation"
```

---

## Chunk 4: Live Host Verification And Deploy Recovery

### Task 4: Verify the fix on the Contabo host before opening the PR

**Files:**
- No new repo files; record commands/results in Beads comments and PR description

- [ ] **Step 1: Verify the host still matches the investigated legacy state**

Run:
```bash
ssh supawave 'set -e; root=/home/ubuntu/supawave; for p in "$root/current" "$root/previous" "$root/releases/current" "$root/releases/blue" "$root/releases/green"; do [ -e "$p" ] && ls -ld "$p" || echo missing:$p; done; docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"'
```
Expected: legacy `supawave-wave-1` / `supawave-caddy-1` still present and `shared/active-slot` still absent until the fixed deploy runs.

- [ ] **Step 2: Trigger a controlled deploy via `workflow_dispatch` from the fix branch before merge**

Use the repository’s normal deploy workflow so production validation happens before merge. Do not hand-run ad hoc production mutations outside the scripted deploy.

Minimum success criteria:
- migration logs show an explicit legacy image discovery step
- blue slot starts and health checks pass
- the active slot file is created
- the deploy ends with `Deploy complete. Active: ...`

- [ ] **Step 3: Run post-deploy sanity on the host and public endpoint**

Run:
```bash
ssh supawave 'curl -s http://localhost:9898/healthz || curl -s http://localhost:9899/healthz'
curl -fsS https://supawave.ai/healthz
```
Expected: HTTP 200 on the active slot and public endpoint.

- [ ] **Step 4: Record the exact verification commands and outcomes in Beads**

Include:
- the local test command set
- the host verification command(s)
- the deploy workflow run URL
- the final active slot and public health result

---

## Execution Notes

- The current production blocker is not speculative. The host is still on the legacy single-slot layout, and the migration probe is currently incompatible with the installed Docker Compose version. Primary evidence: https://github.com/vega113/incubator-wave/actions/runs/23877956797, https://github.com/vega113/incubator-wave/actions/runs/23877202417, https://github.com/vega113/incubator-wave/actions/runs/23874408522, https://github.com/vega113/incubator-wave/actions/runs/23865935933
- Keep the implementation narrow. Fix the verified seams first, then verify the live migration path.
- If the first fixed deploy exposes a second migration issue, capture the new seam explicitly before broadening the patch.

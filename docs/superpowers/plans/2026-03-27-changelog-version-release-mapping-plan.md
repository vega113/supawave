# Changelog Version Release Mapping Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SupaWave’s deploy-update popup and `/changelog` history release-aware so each deployed build maps to the correct release notes, while preventing future PRs from silently regressing changelog ordering or wiping prior entries.

**Architecture:** Separate build identity from release-note identity. The deployed build will continue to expose its real build commit through `/version`, while the changelog will move from an implicit “latest entry wins” list to an explicit release-indexed history where each entry carries a stable `releaseId` used only for changelog lookup. The server version endpoint will stay the update trigger, but it will also report the current changelog release state and the exact release-note range between the client’s loaded release and the server’s current release. CI validation will treat stale-branch rewrites and ordering regressions as build failures.

**Tech Stack:** Java 17 servlets, `org.json`, GWT/inline browser JS in `HtmlRenderer`, GitHub Actions, bash/Python validation tooling, JSON changelog data in `wave/config/changelog.json` and `wave/src/main/resources/config/changelog.json`

---

## Investigation Summary

- Current bug:
  - [`VersionServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionServlet.java) injects `changelogProvider.getLatestEntry()` into `/version`.
  - [`HtmlRenderer.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java) shows that payload directly in `showUpgradeBanner(...)`.
  - Result: the popup is tied to array index `0`, not to the actual deployed app version.
- Regression mode:
  - A merged backfill branch already existed (`PR #396` / merge `93b4a5de`), but later branches reintroduced an older two-entry or one-entry changelog snapshot, proving that stale-branch merges can silently delete previously released entries.
- Deployment constraint:
  - Contabo deploys already have a real build identity (GitHub Actions `github.sha`) in [`deploy-contabo.yml`](.github/workflows/deploy-contabo.yml), but that identity is not passed into `WAVE_SERVER_VERSION`.
  - The plan must preserve that exact build identity instead of replacing it with a changelog-only key.
- External pattern references:
  - Keep a Changelog recommends explicit versioned releases and backfilling missed release notes instead of leaving gaps.
  - GitHub Releases uses immutable tag-backed releases instead of “latest note in a file.”
  - Sentry releases are keyed by explicit version identifiers and can be mapped to commit ranges.

## Chosen Design

- Use one changelog entry per shipped user-facing release note set.
- Add immutable `releaseId` metadata to every entry.
- Make `releaseId` a stable changelog key, not the build commit:
  - format: short slug-like IDs such as `2026-03-27-unread-only-search-filter`
  - unique across the file
  - authored in the PR branch and preserved after merge
- Keep the real deployed build identity separate:
  - `/version.buildCommit` reports the actual deployed commit
  - `/version.buildTime` keeps restart detection
  - `/version.releaseId` reports the current changelog release key from the deployed changelog file
- Define the current release by changelog position, not by `/changelog/latest` semantics:
  - top entry in the validated changelog file is the current shipped release-note entry for that build
  - `/changelog/latest` keeps meaning “top of changelog history”
  - add a separate current/by-id API shape where needed
- Teach the server to answer:
  - “What build is deployed?”
  - “What changelog release key is current for that build?”
  - “Which changelog entries are newer than the client’s loaded release and at or before the current release?”
  - “Is this an exact mapped release change, a partial/unmapped history case, or only a restart/same-release rebuild?”
- Update the popup to render the returned release range instead of a single latest entry.
- Add CI validation so a PR fails if it:
  - removes existing `releaseId`s from the base changelog,
  - reorders old releases,
  - introduces duplicate `releaseId`s,
  - omits required display metadata,
  - or appends a new release note in the wrong place.

## File Map

**Modify:**
- [`wave/config/changelog.json`](wave/config/changelog.json)
- [`wave/src/main/resources/config/changelog.json`](wave/src/main/resources/config/changelog.json)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogProvider.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogProvider.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java)
- [`wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogProviderTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogProviderTest.java)
- [`wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogServletTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogServletTest.java)
- [`wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java)
- [`wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/VersionServletJakartaIT.java`](wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/VersionServletJakartaIT.java)
- [`wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletFragmentDefaultsTest.java`](wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletFragmentDefaultsTest.java)
- [`deploy/caddy/compose.yml`](deploy/caddy/compose.yml)
- [`deploy/caddy/deploy.sh`](deploy/caddy/deploy.sh)
- [`.github/workflows/build.yml`](.github/workflows/build.yml)
- [`.github/workflows/deploy-contabo.yml`](.github/workflows/deploy-contabo.yml)
- [`AGENTS.md`](AGENTS.md)

**Create:**
- [`scripts/validate-changelog.py`](scripts/validate-changelog.py)

## Chunk 1: Release-Aware Changelog Schema And Provider

### Task 1: Convert the changelog data model from “latest card” to immutable release entries

**Files:**
- Modify: [`wave/config/changelog.json`](wave/config/changelog.json)
- Modify: [`wave/src/main/resources/config/changelog.json`](wave/src/main/resources/config/changelog.json)
- Modify: [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogProvider.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogProvider.java)
- Test: [`wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogProviderTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogProviderTest.java)

- [ ] **Step 1: Write failing provider tests for release-aware lookup**

Add tests for:
- unique `releaseId` requirement,
- required `date`, `title`, `summary`, and `sections`,
- newest-first preservation,
- lookup by exact `releaseId`,
- range lookup from prior release to current release,
- fallback when `since` release is unknown,
- detection of duplicate or missing `releaseId`.

- [ ] **Step 2: Run provider tests to verify the new cases fail**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogProviderTest"
```

Expected:
- FAIL because the provider only knows `getLatestEntry()` and raw `getEntries()`.

- [ ] **Step 3: Implement release-aware changelog parsing**

Add provider behavior for:
- `getReleaseEntry(String releaseId)`
- `getReleasesForRange(String previousReleaseId, String currentReleaseId)`
- `getCurrentReleaseEntry()`
- range-status helpers for exact/partial/unmapped semantics
- schema validation for `releaseId`, `date`, `title`, `summary`, and `sections`
- immutable cloning of returned JSON

- [ ] **Step 4: Backfill both changelog files with real release entries**

Backfill one entry per shipped user-facing release using stable release-note IDs, including the currently missing post-`/changelog` releases such as:
- `title:` / `content:` search
- feature-flag tooling if kept user-facing
- changelog system rollout
- OT search rollout and fallback fixes
- wave latest-activity sorting
- API docs
- Lucene 9 rollout scaffolding
- search query reset and implicit content search
- restore last opened wave
- unread digest preservation
- search bootstrap restoration
- inline-anchor crash fix
- unread-only search filter
- Keep `wave/src/main/resources/config/changelog.json` aligned with `wave/config/changelog.json` for every user-facing release entry.

- [ ] **Step 5: Re-run provider tests**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogProviderTest"
```

Expected:
- PASS

## Chunk 2: Popup Range Mapping And Server APIs

### Task 2: Make `/version` and `/changelog` return release-correct notes

**Files:**
- Modify: [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionServlet.java)
- Modify: [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ChangelogServlet.java)
- Modify: [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java)
- Test: [`wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/VersionServletJakartaIT.java`](wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/VersionServletJakartaIT.java)
- Test: [`wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogServletTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/ChangelogServletTest.java)

- [ ] **Step 1: Write failing servlet tests for range lookups**

Add coverage for:
- `/version?since=<oldReleaseId>` returning only releases newer than `since` and up to the current deployed release,
- `/version` without `since` returning build identity plus current release identity without misleading latest-entry payload,
- `/version` returning a closed `releaseNotesStatus` enum with these meanings:
  - `current_only`: no `since` parameter was supplied, so the server can report only the current release identity
  - `exact`: `since` was found and is older than the current release, so `releaseNotes` is an exact forward range
  - `partial`: `since` was not found, but the current release is mapped, so `releaseNotes` contains only the current release entry
  - `same_release`: `since` matches the current release, so there is no new mapped release-note range
  - `non_forward`: `since` is newer than the current release, so the browser is ahead of the server or the server rolled back
  - `unmapped`: the current deployed build has no mapped release entry, so no release-note range can be claimed
- `/version?since=<releaseId>` where `since` is newer than the current deployed release returning no mapped notes and a generic status,
- current deployed builds whose top changelog release is missing or unmapped returning no release-note range,
- `/changelog/latest` staying aligned with history-top semantics,
- a dedicated current/by-id or range endpoint returning contiguous release entries.

- [ ] **Step 2: Run targeted servlet tests to verify failure**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogServletTest"
sbt "wave/testOnly org.waveprotocol.box.server.jakarta.VersionServletJakartaIT"
```

Expected:
- FAIL because the endpoints only expose latest-entry behavior.

- [ ] **Step 3: Implement range-aware servlet responses**

Update the API contract so:
- `/version` always returns:
  - real build identity (`buildCommit`)
  - current release key (`releaseId`)
  - `buildTime`
  - explicit `releaseNotesStatus`
- `/version?since=<releaseId>` also returns `releaseNotes` for the exact release range when available
- the server does not claim release notes when it only knows about a same-release restart or unmapped build
- changelog endpoints can return exact current release data without guessing from array index

- [ ] **Step 4: Re-run targeted servlet tests**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogServletTest"
sbt "wave/testOnly org.waveprotocol.box.server.jakarta.VersionServletJakartaIT"
```

Expected:
- PASS

### Task 3: Update the client popup to show the actual deployed changes

**Files:**
- Modify: [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java)
- Test: [`wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java)
- Test: [`wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletFragmentDefaultsTest.java`](wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletFragmentDefaultsTest.java)

- [ ] **Step 1: Write failing tests for popup script generation**

Add assertions that the page script:
- calls `/version?since=<currentReleaseId>`,
- renders `releaseNotes` rather than `data.changelog`,
- gracefully falls back to a generic reload message when no mapped notes exist,
- links to `/changelog` without implying wrong content.

- [ ] **Step 2: Run the popup-related tests to verify failure**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"
sbt "wave/testOnly org.waveprotocol.box.server.rpc.WaveClientServletFragmentDefaultsTest"
```

Expected:
- FAIL because the inline script still consumes `data.changelog || null`.

- [ ] **Step 3: Implement popup range rendering**

Client behavior:
- keep both `currentBuildCommit` and `currentReleaseId` from the initial page payload,
- poll `/version?since=<currentReleaseId>`,
- if the deployed build changed:
  - render the returned release-note range,
  - show one concise summary line plus a compact bullet list or release titles,
  - fall back to a generic reload call-to-action when `releaseNotesStatus` is `unmapped`, `same_release`, `non_forward`, or `current_only`,
  - fall back to the same generic reload path when the server reports a non-forward or unknown-history range,
  - avoid claiming “latest changes” when the release mapping is not exact.

- [ ] **Step 4: Re-run popup-related tests**

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"
sbt "wave/testOnly org.waveprotocol.box.server.rpc.WaveClientServletFragmentDefaultsTest"
```

Expected:
- PASS

## Chunk 3: Deploy Identity Wiring And Enforcement

### Task 4: Make deployed server builds expose the real build commit

**Files:**
- Modify: [`deploy/caddy/compose.yml`](deploy/caddy/compose.yml)
- Modify: [`deploy/caddy/deploy.sh`](deploy/caddy/deploy.sh)
- Modify: [`.github/workflows/deploy-contabo.yml`](.github/workflows/deploy-contabo.yml)

- [ ] **Step 1: Add a failing validation path for server-version wiring**

Use a narrow static validation target:
- prove the deploy workflow currently never passes `WAVE_SERVER_VERSION`,
- define the expected shell/env wiring in code comments or tests where feasible,
- verify the compose file accepts and forwards `WAVE_SERVER_VERSION`.

- [ ] **Step 2: Implement build-commit wiring**

Deploy behavior:
- set `WAVE_SERVER_VERSION` to the actual deployed build commit (GitHub Actions `github.sha`)
- pass it through the deploy workflow and compose runtime
- make checkout/fetch behavior explicit enough that both the pushed commit and prior main history are available for validation
- make rollback restore the rolled-back release's `WAVE_SERVER_VERSION`, not the forward deploy's value
- preserve existing `buildTime` support for same-release restarts

- [ ] **Step 3: Verify the deploy assets render with the new env var**

Run:
```bash
WAVE_IMAGE=ghcr.io/example/wave:test \
DEPLOY_ROOT=/tmp/supawave \
WAVE_INTERNAL_PORT=9898 \
CANONICAL_HOST=wave.example.test \
ROOT_HOST=example.test \
WWW_HOST=www.example.test \
RESEND_API_KEY=test-dummy \
WAVE_EMAIL_FROM=test-dummy \
WAVE_SERVER_VERSION=test-build-commit \
docker compose -f deploy/caddy/compose.yml config >/tmp/caddy-compose.out
```

Expected:
- PASS
- rollback path should derive `WAVE_SERVER_VERSION` from the previous release directory name

### Task 5: Add CI guardrails against stale changelog rewrites

**Files:**
- Create: [`scripts/validate-changelog.py`](scripts/validate-changelog.py)
- Modify: [`.github/workflows/build.yml`](.github/workflows/build.yml)
- Modify: [`AGENTS.md`](AGENTS.md)

- [ ] **Step 1: Write the validation script**

Script responsibilities:
- validate JSON syntax and required schema fields,
- enforce unique `releaseId`s,
- enforce newest-first ordering,
- on PR builds, compare against the base branch changelog and fail if older release IDs were deleted or reordered,
- require that any newly added release entries are prepended above the existing history instead of inserted in the middle,
- on deploy/push-to-main runs, compare the pushed changelog against the previous main commit before building the image so the merge result cannot deploy with a regressed changelog,
- fail with actionable output that explains whether the problem is missing backfill, stale-branch overwrite, or wrong insertion position.

- [ ] **Step 2: Run the validator locally against the current changelog**

Run:
```bash
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
```

Expected:
- PASS after data migration

- [ ] **Step 3: Wire the validator into CI**

Add a build step before compile that runs:
```bash
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json --base-ref "${GITHUB_BASE_REF:-}"
```

Workflow details:
- `build.yml` must either use `actions/checkout` with `fetch-depth: 0` or explicitly fetch `origin/$GITHUB_BASE_REF` before validation so the base changelog exists locally.
- `deploy-contabo.yml` must run the same validator against the pushed merge result before image build/deploy, using the previous main commit or pushed-before SHA as the comparison base.

- [ ] **Step 4: Document the authoring rule**

Update [`AGENTS.md`](AGENTS.md) so user-facing PRs must:
- prepend a new release entry,
- update both changelog files in lockstep,
- keep prior releases intact,
- use a stable `releaseId` slug that will not change during review-fix pushes,
- avoid reformatting or regrouping older releases in feature PRs.

## Acceptance Criteria

- `/version` exposes the actual deployed build commit and a separate changelog release key.
- The update popup shows the correct release note entry or release range for the deployed version.
- A deploy of a stale-branch PR cannot silently replace the changelog with an older snapshot without CI failing.
- The changelog is backfilled with the currently missing SupaWave releases needed to explain the already-deployed product changes.
- Same-release restarts still prompt reload generically when needed without lying about release notes.

## Exact Verification Targets

- `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`
- `python3 scripts/validate-changelog.py --changelog wave/src/main/resources/config/changelog.json`
- `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json --base-ref <base-ref>`
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogProviderTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.ChangelogServletTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.WaveClientServletFragmentDefaultsTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.jakarta.VersionServletJakartaIT"`
- `sbt wave/compile`
- `sbt compileGwt`
- deploy-compose config render with `WAVE_SERVER_VERSION=test-build-commit`
- local server sanity check after implementation:
  - `sbt prepareServerConfig run`
  - verify `/version` returns the expected `buildCommit` / `releaseId` / `releaseNotesStatus` shape and `/changelog` renders the backfilled history

## Out Of Scope

- Replacing the popup with a richer modal UX
- Auto-generating changelog prose from commit messages
- Backfilling every non-user-facing CI or docs-only PR
- Rewriting the broader release-management process outside SupaWave’s current deploy-on-main model

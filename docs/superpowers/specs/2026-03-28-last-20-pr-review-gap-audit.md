# Last 20 PR Review Gap Audit

Date: 2026-03-28
Repository: `vega113/incubator-wave`
Audit lane: `audit/last-20-pr-review-gaps`

## Scope

This audit covered the last 20 merged PRs visible from GitHub state at the start of the run:

- `#440` Improve robot AI onboarding control room
- `#441` fix: support tag queries in OT search
- `#431` Add robot management dashboard
- `#438` fix: preserve next unread after deleted blip
- `#437` fix: prevent reply delete sink-null crash
- `#436` Improve AI robot onboarding docs and control room
- `#435` fix: give blip links a stronger SupaWave affordance
- `#434` Fix robot registration activation flow
- `#432` fix: clear stale save warning state
- `#433` Fix default link affordance in wave content
- `#423` fix: restore profile last-seen updates
- `#430` fix: polish robot registration success page
- `#429` fix: recover cleanly from deploy-time reconnects
- `#425` fix: keep tagged wave bottoms reachable above the tags bar
- `#421` fix: surface lucene9 in admin feature flags
- `#428` fix: harden PR review monitoring
- `#424` fix: correct landing page capability copy
- `#426` fix: clarify feature-flag auth recovery
- `#422` Fix DM classification for ordinary two-person waves
- `#420` fix: bootstrap public-wave search read state

Method:

1. Listed the last 20 merged PRs from GitHub state with `gh pr list --state merged --limit 20`.
2. Pulled thread-aware review data for each PR via GitHub GraphQL (`reviewThreads`, `isResolved`, comment bodies, and links).
3. Classified comments as major only when they materially affected behavior, safety, correctness, availability, or maintainability.
4. Verified candidate misses against current `main`, not just the stale local lane snapshot.
5. Opened dedicated follow-up PRs only when the missed issue still existed on current `main`.

## Result

4 of the last 20 merged PRs were merged with at least one major review finding not actually addressed before merge:

- `#431`
- `#436`
- `#440`
- `#441`

Three of those missed findings were still live on current `main` and now have dedicated follow-up PRs:

- `#445` fix: expose robot secret rotation in dashboard
- `#446` fix: trust IPv6 loopback in robot dashboard URLs
- `#448` fix: harden OT search subscription lifecycle

No monitor panes were started for those PRs in this audit run.

## Findings

### PR #431: Add robot management dashboard

Status: merged with a major missed review finding, still live on `main`, fixed in follow-up PR `#445`.

Evidence:

- Review thread: `https://github.com/vega113/incubator-wave/pull/431#discussion_r3004799253`
- Reviewer finding: the servlet implemented `action=rotate-secret` server-side, but the dashboard rendered no UI control that could actually trigger the action.
- Current `main` evidence: [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L382) through [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L394) render only the callback-update form. There is no rotate-secret form or button in the page markup even though `doPost` handles `rotate-secret`.
- Follow-up PR: `#445` <https://github.com/vega113/incubator-wave/pull/445>

### PR #436: Improve AI robot onboarding docs and control room

Status: merged with a major missed review finding, still live on `main`, fixed in follow-up PR `#446`.

Evidence:

- Review thread: `https://github.com/vega113/incubator-wave/pull/436#discussion_r3004742508`
- Reviewer finding: bracketed IPv6 loopback hosts such as `[::1]:9898` were treated as untrusted because trusted-host parsing split at the first colon.
- Current `main` evidence: [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L473) through [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L484) still normalize the host with `host.indexOf(':')`, which collapses `[::1]:9898` to `[` and breaks the trust check.
- Follow-up PR: `#446` <https://github.com/vega113/incubator-wave/pull/446>

### PR #440: Improve robot AI onboarding control room

Status: merged with major unresolved review findings, but the flagged behavior is not currently live on `main`, so no follow-up PR was opened from this audit.

Evidence:

- Unresolved review thread: `https://github.com/vega113/incubator-wave/pull/440#discussion_r3004830856`
  - Finding: sign-in redirect should preserve the actual request path instead of hard-coding `/account/robots`.
  - Current `main` status: not reproduced. [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L130) through [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L137) redirect using `req.getRequestURI()`, not a hard-coded path.
- Unresolved review thread: `https://github.com/vega113/incubator-wave/pull/440#discussion_r3004830857`
  - Finding: canonical dashboard base URL fallback should stay on `https://` instead of downgrading to request `scheme`.
  - Current `main` status: not reproduced. [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L449) through [RobotDashboardServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java#L461) already fall back to `https://` plus the configured domain.

Notes:

- `#440` merged into `feat/robot-dashboard-management`, not directly into `main`.
- The PR still counts as a review-gap merge because those major threads were left unresolved at merge time.

### PR #441: fix: support tag queries in OT search

Status: merged with major missed review findings, still live on `main`, fixed in follow-up PR `#448`.

Evidence:

- Unresolved review thread: `https://github.com/vega113/incubator-wave/pull/441#discussion_r3004818313`
  - Finding: inactive OT-search updates retained per-query publisher/cache state and could leak memory over time.
  - Current `main` evidence: [SearchWaveletSnapshotPublisher.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisher.java#L82) through [SearchWaveletSnapshotPublisher.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisher.java#L114) still create/update per-query state on `publishUpdate` without an inactive-subscription prune path.
- Unresolved review thread: `https://github.com/vega113/incubator-wave/pull/441#discussion_r3004818317`
  - Finding: the auxiliary canonical bootstrap requery could throw after the primary search succeeded, turning a successful request into a 500.
  - Current `main` evidence: [SearchServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java#L101) through [SearchServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java#L109) call the bootstrap requery in-line, and [SearchServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java#L170) through [SearchServlet.java](https://github.com/vega113/incubator-wave/blob/main/wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java#L179) still invoke `performSearch(canonicalRequest, user)` without a catch.
- Follow-up PR: `#448` <https://github.com/vega113/incubator-wave/pull/448>

## Remaining 16 PRs

No major missed review finding was confirmed in:

- `#438`
- `#437`
- `#435`
- `#434`
- `#432`
- `#433`
- `#423`
- `#430`
- `#429`
- `#425`
- `#421`
- `#428`
- `#424`
- `#426`
- `#422`
- `#420`

These PRs either had:

- no review threads,
- only minor/nit-level comments,
- or major comments that were updated away or otherwise not reproducible as merged defects.

## Root-Cause Pattern

The pattern is not just “reviewers missed bugs.” The stronger pattern is that the merge process allowed unresolved major review threads to stop mattering operationally.

Observed contributors:

1. Review-thread resolution was not treated as a hard merge precondition.
   - The confirmed misses were all visible as unresolved major review threads at merge time.
   - That means the process gap was not reviewer detection; it was merge governance.

2. Review redundancy was degraded on the same batch of PRs.
   - Several PRs in this slice show CodeRabbit rate limits/failures and repeated Codex review quota exhaustion in top-level conversation logs.
   - When one reviewer fails open and the other is rate-limited, unresolved inline findings can survive unless the merge gate explicitly blocks on review-thread cleanliness.

3. Stacked PRs into non-`main` base branches obscured risk ownership.
   - `#440` and `#441` were merged into feature/fix branches rather than directly into `main`.
   - That made it easier for unresolved findings to be accepted as “later branch cleanup” instead of “must fix before merge”.

4. The repo was already reacting to the same class of problem.
   - PR `#428` (“fix: harden PR review monitoring”) in the same 20-PR window is itself evidence that review-monitoring and merge-gate behavior were unstable during this batch.

## Follow-Up PRs Opened

- `#445` fix: expose robot secret rotation in dashboard
  - Branch: `fix/robot-dashboard-rotate-secret-followup`
  - Head SHA: `d3a3a37659a68173b0603def78736481a2e8316e`
- `#446` fix: trust IPv6 loopback in robot dashboard URLs
  - Branch: `fix/robot-dashboard-base-url-followup`
  - Head SHA: `767300ba09e83722ccad4305a35a868e14313214`
- `#448` fix: harden OT search subscription lifecycle
  - Branch: `fix/ot-search-review-gap-followup`
  - Head SHA: `0312cfb9ae65d4930e3f9afc229780d52ff90533`

## Verification Used For Follow-Ups

- `#445`
  - `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
  - local server sanity: `GET /healthz -> 200`, `HEAD /account/robots -> 302`, `GET /auth/signin -> 200`
- `#446`
  - `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
  - local server sanity: `GET /healthz -> 200`, `HEAD /account/robots -> 302`, `GET /auth/signin -> 200`
- `#448`
  - `sbt "testOnly org.waveprotocol.box.server.waveserver.search.SearchWaveletSnapshotPublisherTest org.waveprotocol.box.server.rpc.SearchServletTest"`
  - local server sanity: `GET /healthz -> 200`, `GET /search/?query=in:inbox&index=5&numResults=3 -> 403` while unauthenticated

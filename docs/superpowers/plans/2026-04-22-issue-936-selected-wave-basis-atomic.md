Status: Proposed
Updated: 2026-04-22
Owner: Lane (issue-936)
Issue: https://github.com/vega113/supawave/issues/936
Worktree: /Users/vega/devroot/worktrees/issue-936-selected-wave-basis-atomic
Branch: issue-936-selected-wave-basis-atomic

# Issue #936 — Keep J2CL selected-wave version/hash basis atomic

## Summary

`J2clSelectedWaveProjector.buildWriteSession(...)` currently advances
`baseVersion` and `historyHash` independently when it folds a new
`SidecarSelectedWaveUpdate` into the cached write session. If an update
carries a newer `resultingVersion` while `resultingVersionHistoryHash` is
null or empty, the projector combines the new version with the previous
hash and publishes an invalid pair. The sidecar reply path now depends on
that pair, so a mismatched pair makes the client look ready while it
builds a delta basis the server will reject.

Fix: treat `resultingVersion` + `resultingVersionHistoryHash` as a coupled
atomic pair. Advance the write session's `(baseVersion, historyHash)` only
when both values arrive together; otherwise preserve the previous pair
untouched.

## Out of scope

- Server-side changes in `SidecarTransportCodec` or its producers. The
  transport continues to send both fields; this change only hardens the
  client projector against partial updates.
- Any changes to `J2clSidecarWriteSession` shape or to how
  `J2clSidecarComposeController` consumes the session — only the
  construction rules change.
- Channel id / reply-target blip id resolution. Those already preserve the
  previous value independently and are not part of the reported bug.

## Current behavior (pre-fix)

File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`

`buildWriteSession` resolves four fields independently:

1. `channelId`: from update, else from previous write session.
2. `replyTargetBlipId`: from update documents/fragments, else previous.
3. `baseVersion`: `resolveBaseVersion(update)` — returns
   `update.getResultingVersion()` when ≥ 0, else the fragments snapshot
   version, else the max document last-modified version. If the result is
   < 0, fall back to `previous.getWriteSession().getBaseVersion()`.
4. `historyHash`: `update.getResultingVersionHistoryHash()` when non-empty,
   else `previous.getWriteSession().getHistoryHash()`.

Failure mode the issue calls out:

- Update arrives with `resultingVersion = N+1` and empty
  `resultingVersionHistoryHash`.
- Previous session has `(baseVersion = N, historyHash = H_N)`.
- Projector emits `(baseVersion = N+1, historyHash = H_N)` — a mismatched
  pair.

There is a second latent case with the same shape that must also be
fixed to make the invariant durable:

- Update arrives with `resultingVersion < 0` but `fragments.snapshotVersion
  = M` (or max doc version = M). `resolveBaseVersion` promotes M as the
  new base. If previous session has `(K, H_K)` with K != M, we again
  emit a mismatched pair `(M, H_K)`.

## Target behavior (post-fix)

The `(baseVersion, historyHash)` pair must move together. Only two
transitions are legal:

- **Advance**: the incoming update supplies both a new
  `resultingVersion >= 0` AND a non-empty
  `resultingVersionHistoryHash`. Use those two values as the new pair.
- **Preserve**: otherwise, keep the previous write session's
  `(baseVersion, historyHash)` exactly as-is.

If neither an advance nor a preserve is possible (no coupled pair in the
update AND no previous write session), the projector returns `null` for
the write session, just as today when inputs are incomplete.

Channel id and reply-target blip id resolution keep their current
"prefer update, fall back to previous" semantics — they are not part of
the coupled pair.

## Implementation

### Edit — `J2clSelectedWaveProjector.java`

Replace the two independent resolutions (`resolveBaseVersion` result +
`historyHash` fallback) with a single coupled resolution inside
`buildWriteSession`:

```java
long baseVersion;
String historyHash;

long updateVersion = update.getResultingVersion();
String updateHash = update.getResultingVersionHistoryHash();
boolean updateHasCoupledPair =
    updateVersion >= 0 && updateHash != null && !updateHash.isEmpty();

if (updateHasCoupledPair) {
  baseVersion = updateVersion;
  historyHash = updateHash;
} else if (previous != null && previous.getWriteSession() != null) {
  baseVersion = previous.getWriteSession().getBaseVersion();
  historyHash = previous.getWriteSession().getHistoryHash();
} else {
  return null;
}
```

Delete the now-unused `resolveBaseVersion` helper (it encoded the
snapshot/document fallback that silently created mismatched pairs).

Keep the existing null/empty guard at the bottom of `buildWriteSession`
— after the coupled resolution, the only way the guard can still reject
is when channelId or replyTargetBlipId is missing.

### Regression test

Add a new JVM test class
`j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
that exercises `J2clSelectedWaveProjector.project` directly. Rationale:
the projector is static and pure, and the existing reflective controller
harness would force a full sidecar boot just to exercise this unit.

Test cases:

1. `advancesWriteSessionWhenUpdateCarriesCoupledVersionAndHash`: previous
   session is `(44, "ABCD")`; new update carries `(50, "EFGH")`; expect
   `(50, "EFGH")`.
2. `preservesPreviousPairWhenUpdateOmitsHistoryHash` (the regression
   test the issue requires): previous session is `(44, "ABCD")`; new
   update carries `resultingVersion = 50` and
   `resultingVersionHistoryHash = null` (and separately, `""`); expect
   `(44, "ABCD")`. This is the exact failure mode PR #935 left open.
3. `preservesPreviousPairWhenUpdateHasNoResultingVersion`: previous
   session is `(44, "ABCD")`; update carries `resultingVersion = -1` and
   populated fragments/document versions; expect `(44, "ABCD")` — the
   snapshot/document fallback must not silently advance version against
   the stale hash.
4. `returnsNullWriteSessionWhenNoPreviousAndUpdateLacksCoupledPair`:
   previous is null; update has `resultingVersion = -1` or empty hash;
   expect `null` write session.
5. `buildsWriteSessionOnFirstCoupledUpdate`: previous is null; update
   carries `(0, "ZERO")` (mirrors the existing `versionZeroSelected...`
   test); expect `(0, "ZERO")` — confirms version zero is a valid
   advance value.

All five tests construct `SidecarSelectedWaveUpdate` directly and call
`J2clSelectedWaveProjector.project`. No reflection, no controller harness.

### No changes required

- `J2clSidecarWriteSession.java` — shape unchanged.
- `J2clSelectedWaveController.java` — calls projector once per update;
  no behavior change.
- `SidecarSelectedWaveUpdate.java` — transport shape unchanged.
- `SidecarTransportCodec.java` — encoder/decoder unchanged.

## Verification

Targeted Maven run (mirrors PR #935's verification pattern, restricted to
the changed projector and its nearest neighbors):

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar \
  -Dtest=org.waveprotocol.box.j2cl.search.J2clSelectedWaveProjectorTest,org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest \
  test
```

Record the exact command and outcome in the linked issue comment.

No server-boot sanity check is warranted: this change is pure client-side
projector logic with no runtime initialization, no schema, no UI surface,
and no feature flag. The write-session construction rule is covered by
unit tests; no local-verification journal entry is required for this
slice.

No changelog fragment: the change has no user-visible behavior on its own
(it only prevents an invalid delta basis from being handed to the reply
submit path, which is a correctness guarantee, not a product change).

## Risk assessment

- **Behavior narrowing**: the snapshot/document version fallback in
  `resolveBaseVersion` is removed. Verified no caller depends on that
  fallback in isolation: the only write session that ever materialized
  from that path would have required a matching `historyHash` to pass
  the null/empty guard, and a hash never originated from fragments/docs.
  So the fallback either produced the bug described in #936 or produced
  `null` — both outcomes are preserved or improved by the new logic.
- **Previous tests**: the existing
  `J2clSelectedWaveControllerTest.selectedWaveUpdateBuildsWriteSessionWithHistoryHash`,
  `selectedWaveUpdatePromotesWriteSessionMetadata`, and
  `versionZeroSelectedWaveUpdateStillBuildsWriteSession` all pass an
  update with coupled `(resultingVersion, resultingVersionHistoryHash)`
  pairs. They stay green under the new rule.

## Acceptance checklist

- [ ] `buildWriteSession` resolves `(baseVersion, historyHash)` as a
      coupled pair (advance-both or preserve-both).
- [ ] `resolveBaseVersion` removed.
- [ ] New `J2clSelectedWaveProjectorTest` covers the five cases above,
      including the partial-update regression case from #936.
- [ ] Targeted Maven run passes.
- [ ] Plan Claude review clean.
- [ ] Implementation Claude review clean.
- [ ] Issue comment records worktree, plan path, commits, verification,
      review outcomes.
- [ ] PR opened against main and merged (or explicitly blocked).

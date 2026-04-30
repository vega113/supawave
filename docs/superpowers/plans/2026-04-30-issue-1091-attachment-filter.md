# Issue 1091 Has Attachment Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the canonical `has:attachment` J2CL filter chip execute server-side for both in-memory and Solr-backed search without changing the behavior of unknown `has:<value>` tokens.

**Architecture:** Keep attachment detection doc-based and provider-neutral. `QueryHelper` owns case-insensitive token-value lookup, a new package-local `AttachmentSearchFilter` owns `WaveViewData` attachment detection and mutable result filtering, and both search providers call it only when `HAS=attachment` is present.

**Tech Stack:** Java, SBT, JUnit 3 style tests, existing Wave `WaveViewData` / `ObservableWaveletData` model, `IdUtil.isAttachmentDataDocument`.

---

## File Map

- Modify `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java`: add generic `hasTokenValue(...)` and keep `hasIsValue(...)` delegating to it.
- Create `wave/src/main/java/org/waveprotocol/box/server/waveserver/AttachmentSearchFilter.java`: implement shared `has:attachment` query detection, wave attachment detection, and in-place result filtering.
- Modify `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`: activate the shared filter after folder/tag preparation and before text-style filters; include attachment counts in the search summary; update stale `has:attachment` deferred comment.
- Modify `wave/src/main/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImpl.java`: mirror the shared post-filter after Solr result materialization and before unread pagination filtering.
- Modify `wave/src/test/java/org/waveprotocol/box/server/waveserver/QueryHelperTest.java`: cover generic `HAS=attachment` token lookup and unknown `HAS` no-op predicate behavior.
- Modify `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`: add integration coverage for `in:inbox has:attachment`, bare `has:attachment`, legacy `m/attachment/...` docs, and unknown `has:<value>`.
- Modify `wave/src/test/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImplTest.java`: cover the shared Solr post-filter seam without requiring a real Solr server.
- Add `wave/config/changelog.d/2026-04-30-j2cl-has-attachment-filter.json`: user-facing parity change.

## Task 1: Query Token Predicate

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/QueryHelperTest.java`

- [ ] **Step 1: Add failing tests for generic token lookup**

Add these tests after the existing `hasIsValue` tests:

```java
public void testHasTokenValueReturnsTrueForMatchingHasToken() throws Exception {
  Map<TokenQueryType, Set<String>> queryParams =
      QueryHelper.parseQuery("in:inbox has:attachment");
  assertTrue(QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "attachment"));
}

public void testHasTokenValueIsCaseInsensitive() throws Exception {
  Map<TokenQueryType, Set<String>> queryParams =
      QueryHelper.parseQuery("has:ATTACHMENT");
  assertTrue(QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "attachment"));
  assertTrue(QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "ATTACHMENT"));
}

public void testHasTokenValueReturnsFalseForUnknownHasValue() throws Exception {
  Map<TokenQueryType, Set<String>> queryParams =
      QueryHelper.parseQuery("has:starred");
  assertFalse(QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "attachment"));
}
```

- [ ] **Step 2: Run the focused parser tests and confirm red**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.QueryHelperTest'
```

Expected: compile failure because `QueryHelper.hasTokenValue(...)` does not exist yet.

- [ ] **Step 3: Implement the generic helper**

Add this method near `hasIsValue(...)`, then replace the `hasIsValue` body with delegation:

```java
public static boolean hasTokenValue(
    Map<TokenQueryType, Set<String>> queryParams, TokenQueryType type, String value) {
  Set<String> values = queryParams.get(type);
  if (values == null || values.isEmpty()) {
    return false;
  }
  for (String candidate : values) {
    if (candidate != null && candidate.equalsIgnoreCase(value)) {
      return true;
    }
  }
  return false;
}

public static boolean hasIsValue(Map<TokenQueryType, Set<String>> queryParams, String value) {
  return hasTokenValue(queryParams, TokenQueryType.IS, value);
}
```

- [ ] **Step 4: Re-run parser tests**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.QueryHelperTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/QueryHelperTest.java
git commit -m "feat: add generic search token predicate"
```

## Task 2: Shared Attachment Filter

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/waveserver/AttachmentSearchFilter.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImplTest.java`

- [ ] **Step 1: Add failing shared-filter tests**

Add a package-local helper test in `SolrSearchProviderImplTest` because it already lives in `org.waveprotocol.box.server.waveserver` and avoids a second test class. The test should construct minimal `WaveViewData` instances with one conversational wavelet containing document ids `attach+modern` and `m/attachment/legacy`, then call `AttachmentSearchFilter.filterByHasAttachment(...)`.

Expected assertions:

```java
assertEquals(1, filteredModern.size());
assertSame(modernAttachmentWave, filteredModern.get(0));
assertEquals(1, filteredLegacy.size());
assertSame(legacyAttachmentWave, filteredLegacy.get(0));
assertFalse(AttachmentSearchFilter.isHasAttachmentQuery(
    QueryHelper.parseQuery("has:unknown")));
```

- [ ] **Step 2: Run Solr test and confirm red**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.SolrSearchProviderImplTest'
```

Expected: compile failure because `AttachmentSearchFilter` does not exist yet.

- [ ] **Step 3: Implement `AttachmentSearchFilter`**

Create:

```java
package org.waveprotocol.box.server.waveserver;

import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AttachmentSearchFilter {
  static boolean isHasAttachmentQuery(Map<TokenQueryType, Set<String>> queryParams) {
    return QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "attachment");
  }

  static void filterByHasAttachment(List<WaveViewData> results) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      if (!hasAttachmentDocument(it.next())) {
        it.remove();
      }
    }
  }

  static boolean hasAttachmentDocument(WaveViewData wave) {
    for (ObservableWaveletData wavelet : wave.getWavelets()) {
      if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
        continue;
      }
      for (String documentId : wavelet.getDocumentIds()) {
        if (IdUtil.isAttachmentDataDocument(documentId)) {
          return true;
        }
      }
    }
    return false;
  }

  private AttachmentSearchFilter() {
  }
}
```

- [ ] **Step 4: Re-run Solr test**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.SolrSearchProviderImplTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/AttachmentSearchFilter.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImplTest.java
git commit -m "feat: add shared attachment search filter"
```

## Task 3: Simple Search Execution

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [ ] **Step 1: Add failing Simple integration tests**

Add tests near `testSearchUnknownIsTokenIsNoOp`:

```java
public void testSearchHasAttachmentCombinesWithInbox() throws Exception {
  WaveletName withAttachment = WaveletName.of(WaveId.of(DOMAIN, "with-attachment"), WAVELET_ID);
  WaveletName withoutAttachment = WaveletName.of(WaveId.of(DOMAIN, "without-attachment"), WAVELET_ID);

  submitDeltaToNewWavelet(withAttachment, USER1, addParticipantToWavelet(USER1, withAttachment));
  addAttachmentDataDocumentToWavelet(withAttachment, USER1, "modern");
  submitDeltaToNewWavelet(withoutAttachment, USER1, addParticipantToWavelet(USER1, withoutAttachment));

  SearchResult results = searchProvider.search(USER1, "in:inbox has:attachment", 0, 10);

  assertEquals(1, results.getNumResults());
  assertEquals("with-attachment",
      WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
}
```

Add equivalent bare `has:attachment`, legacy `m/attachment/legacy`, and unknown no-op tests. The unknown no-op test should query `in:inbox has:unknown` and assert both waves remain.

Add this helper near `addTagToWavelet(...)`:

```java
private void addAttachmentDataDocumentToWavelet(
    WaveletName name, ParticipantId user, String attachmentId) throws Exception {
  addDocumentToWavelet(name, user,
      IdUtil.join(IdConstants.ATTACHMENT_METADATA_PREFIX, attachmentId));
}

private void addLegacyAttachmentDataDocumentToWavelet(
    WaveletName name, ParticipantId user, String attachmentId) throws Exception {
  addDocumentToWavelet(name, user, "m/attachment/" + attachmentId);
}

private void addDocumentToWavelet(WaveletName name, ParticipantId user, String documentId)
    throws Exception {
  WaveletOperationContext context = new WaveletOperationContext(user, 0, 1);
  WaveletOperation op =
      new WaveletBlipOperation(documentId, new BlipContentOperation(context,
          new DocOpBuilder().characters("attachment metadata").build()));
  submitDeltaToExistingWavelet(name, user, op);
}
```

- [ ] **Step 2: Run Simple test and confirm red**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'
```

Expected: new `has:attachment` tests fail because no provider calls the filter yet.

- [ ] **Step 3: Implement Simple provider filtering**

In `search(...)`, compute:

```java
final boolean hasAttachmentQuery = AttachmentSearchFilter.isHasAttachmentQuery(queryParams);
```

Add `attachmentsAfter` to the filter counters. After tag filtering and before title/content filtering, apply:

```java
if (hasAttachmentQuery) {
  LOG.fine("Attachment filter: candidates=" + results.size());
  AttachmentSearchFilter.filterByHasAttachment(results);
  attachmentsAfter = results.size();
  LOG.fine("Attachment filter result: " + attachmentsAfter + " remain");
}
```

Include `attachmentsAfter` in `hasFilters` and the summary list. Update the stale comment so only `from:me` remains deferred.

- [ ] **Step 4: Re-run Simple test**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java
git commit -m "feat: execute has attachment filter in simple search"
```

## Task 4: Solr Mirror, Changelog, Verification

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImpl.java`
- Add: `wave/config/changelog.d/2026-04-30-j2cl-has-attachment-filter.json`

- [ ] **Step 1: Mirror the post-filter in Solr**

After `resultsList` is created and before tag/unread filtering, add:

```java
if (AttachmentSearchFilter.isHasAttachmentQuery(queryParams)) {
  AttachmentSearchFilter.filterByHasAttachment(resultsList);
}
```

Update the chip-token comment: `has:attachment` is post-filtered now; `from:me` remains deferred.

- [ ] **Step 2: Add changelog fragment**

Create:

```json
{
  "date": "2026-04-30",
  "type": "fixed",
  "area": "j2cl-search",
  "summary": "The J2CL With attachments search chip now filters waves server-side for both simple and Solr search providers."
}
```

- [ ] **Step 3: Run changelog validation**

Run:

```bash
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: PASS.

- [ ] **Step 4: Run focused search verification**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.QueryHelperTest org.waveprotocol.box.server.waveserver.SolrSearchProviderImplTest org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'
```

Expected: PASS.

- [ ] **Step 5: Run parity build smoke**

Run:

```bash
sbt --batch compile j2clSearchTest
```

Expected: PASS.

- [ ] **Step 6: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/SolrSearchProviderImpl.java \
  wave/config/changelog.d/2026-04-30-j2cl-has-attachment-filter.json
git commit -m "feat: mirror has attachment filter in solr search"
```

## Review and PR Gate

- [ ] Self-review the diff against issue #1091 acceptance criteria.
- [ ] Attempt Claude Opus 4.7 plan review and implementation review. If quota is still exhausted, record the exact quota message in the issue and continue with self-review plus GitHub PR checks.
- [ ] Post an issue update with worktree, plan path, commits, verification commands, and review status.
- [ ] Push `codex/issue-1091-attachment-filter-20260430`.
- [ ] Open a PR linked to #1091 and #904.
- [ ] Monitor GitHub checks, CodeRabbit/Codex review comments, unresolved review threads, mergeability, and branch freshness until merged.

## Self-Review

- Spec coverage: Simple search, Solr search, doc-based attachment seam, unknown `has:<value>` no-op, folder composition, no-folder query, and tests are all mapped to tasks.
- Placeholder scan: No task uses TBD/TODO/later placeholders; commands and expected outcomes are explicit.
- Type consistency: `AttachmentSearchFilter.isHasAttachmentQuery(...)`, `filterByHasAttachment(...)`, and `QueryHelper.hasTokenValue(...)` are used consistently across plan tasks.
- Scope check: No attachment indexing or UI changes are included; this remains the server-side execution follow-up requested by #1091.

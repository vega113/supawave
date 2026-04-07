# Pinned Waves UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pinned waves always appear at the top of the inbox list, and a pin icon is shown next to each pinned wave in the search panel.

**Architecture:** Add `pinned: bool` to the `SearchResponse.Digest` proto wire format. The server populates it from `supplement.isPinned()` inside `WaveDigester`. The client reads it into `DigestSnapshot.isPinned()` and calls `DigestDomImpl.setPinned()` to show a small SVG pin icon. A one-line guard in `SimpleSearchProviderImpl` skips pinned-first promotion when `orderby:` is present.

**Tech Stack:** Java 11, Protocol Buffers (proto2), GWT (client-side Java-to-JS), SBT build, JUnit 3 (TestCase), Mockito.

---

## File Map

| File | Change |
|------|--------|
| `wave/src/proto/proto/org/waveprotocol/box/search/search.proto` | Add `optional bool pinned = 9` |
| `gen/messages/org/waveprotocol/box/search/SearchResponse.java` | Add `getPinned()`/`setPinned()` to Digest interface |
| `gen/messages/org/waveprotocol/box/search/SearchResponseBuilder.java` | Add `pinned` to DigestBuilder |
| `gen/messages/org/waveprotocol/box/search/SearchResponseUtil.java` | Add pinned to equality/hash |
| `gen/messages/org/waveprotocol/box/search/jso/SearchResponseJsoImpl.java` | Add pinned to JSO overlay (GWT client reads this) |
| `gen/messages/org/waveprotocol/box/search/proto/SearchResponseProtoImpl.java` | Add pinned delegation to proto |
| `gen/messages/org/waveprotocol/box/search/impl/SearchResponseImpl.java` | Add pinned field to plain impl |
| `gen/messages/org/waveprotocol/box/search/gson/SearchResponseGsonImpl.java` | Add pinned to Gson serialization |
| `wave/src/main/java/com/google/wave/api/SearchResult.java` | Add `pinned` to `Digest` |
| `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java` | Read `supplement.isPinned()` |
| `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java` | Serialize `pinned` into proto |
| `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java` | Guard promotion on no-orderby |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java` | Add `pinned` to `DigestSnapshot` |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java` | Pass `pinned` from proto JSO |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java` | Preserve `pinned` in `deactivate()` |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/testing/FakeSearchService.java` | Add `false` to `DigestSnapshot` |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java` | Add `false` in `parseDigest()` |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestView.java` | Add `setPinned(boolean)` |
| `wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml` | Add pin icon element |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java` | Implement `setPinned()`, add `pinIcon` field and Css method |
| `wave/src/main/resources/org/waveprotocol/box/webclient/search/mock/digest.css` | Add `.pinIcon` rule |
| `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java` | Call `digestUi.setPinned()` |

---

## Task 1: Proto + generated files — add `pinned` field

**Files:**
- Modify: `wave/src/proto/proto/org/waveprotocol/box/search/search.proto`
- Modify: `gen/messages/org/waveprotocol/box/search/SearchResponse.java`
- Modify: `gen/messages/org/waveprotocol/box/search/SearchResponseBuilder.java`
- Modify: `gen/messages/org/waveprotocol/box/search/SearchResponseUtil.java`
- Modify: `gen/messages/org/waveprotocol/box/search/jso/SearchResponseJsoImpl.java`
- Modify: `gen/messages/org/waveprotocol/box/search/proto/SearchResponseProtoImpl.java`
- Modify: `gen/messages/org/waveprotocol/box/search/impl/SearchResponseImpl.java`
- Modify: `gen/messages/org/waveprotocol/box/search/gson/SearchResponseGsonImpl.java`

- [ ] **Step 1.1: Add field to search.proto**

In `wave/src/proto/proto/org/waveprotocol/box/search/search.proto`, after `required string author = 8;`:

```protobuf
    // The wave author.
    required string author = 8;
    // Whether the wave is pinned by this user.
    optional bool pinned = 9;
```

- [ ] **Step 1.2: Add to SearchResponse.java interface**

In `gen/messages/org/waveprotocol/box/search/SearchResponse.java`, after `void setAuthor(String author);` (line 303) and before `}` that closes the `Digest` interface:

```java
    /** Returns pinned, or false if hasn't been set. */
    boolean getPinned();

    /** Sets pinned. */
    void setPinned(boolean pinned);
```

- [ ] **Step 1.3: Add to SearchResponseBuilder.java**

In `gen/messages/org/waveprotocol/box/search/SearchResponseBuilder.java`, find `DigestBuilder`:

After the line `private String author;` (~line 301), add:
```java
    private Boolean pinned;
```

After the `setAuthor` method (~line 499), add:
```java
    public DigestBuilder setPinned(boolean value) {
      this.pinned = value;
      return this;
    }
```

In the `build()` method of `DigestBuilder`, after `message.setAuthor(author);` (~line 674), add:
```java
      if (pinned != null) {
        message.setPinned(pinned);
      }
```

- [ ] **Step 1.4: Add to SearchResponseUtil.java**

Find the `isEqual` method for Digest. After `if (m1.getBlipCount() != m2.getBlipCount()) return false;` (~line 217), add:
```java
      if (m1.getPinned() != m2.getPinned()) return false;
```

In the `getHashCode` method for Digest, after `result = (31 * result) + Integer.valueOf(message.getBlipCount()).hashCode();` (~line 400), add:
```java
      result = (31 * result) + Boolean.valueOf(message.getPinned()).hashCode();
```

- [ ] **Step 1.5: Add to SearchResponseJsoImpl.java (GWT JSO overlay)**

In the `DigestJsoImpl` class, after `private static final String keyAuthor = "8";` (~line 158), add:
```java
    private static final String keyPinned = "9";
```

After the `setAuthor` method, add:
```java
    @Override
    public boolean getPinned() {
      return hasProperty(this, keyPinned) ? getPropertyAsBoolean(this, keyPinned) : false;
    }

    @Override
    public void setPinned(boolean value) {
      setPropertyAsBoolean(this, keyPinned, value);
    }
```

- [ ] **Step 1.6: Add to SearchResponseProtoImpl.java**

Find the `DigestProtoImpl` inner class. After the `setAuthor` method (~line 631), add:
```java
    @Override
    public boolean getPinned() {
      return proto.hasPinned() ? proto.getPinned() : false;
    }

    @Override
    public void setPinned(boolean value) {
      protoBuilder.setPinned(value);
    }
```

In the JSON serialization method (where `json.add("8", ...)` appears for author), after it add:
```java
      json.add("9", new JsonPrimitive(getPinned()));
```

In the JSON deserialization method (where `setAuthor(elem.getAsString())` appears), after it add:
```java
        case "9":
          setPinned(elem.getAsBoolean());
          break;
```

In the `copyFrom` method of `DigestProtoImpl`, after `setAuthor(message.getAuthor());`, add:
```java
      setPinned(message.getPinned());
```

- [ ] **Step 1.7: Add to SearchResponseImpl.java**

In the `DigestImpl` inner class, after `private String author;` (~line 293), add:
```java
    private Boolean pinned;
```

After the `setAuthor` method (~line 722), add:
```java
    @Override
    public boolean getPinned() {
      return pinned != null ? pinned : false;
    }

    @Override
    public void setPinned(boolean value) {
      this.pinned = value;
    }
```

In the `reset()` method (where `this.author = null;` appears ~line 893), after it add:
```java
      this.pinned = null;
```

In the `copyFrom` method, after `setAuthor(message.getAuthor());` (~line 465), add:
```java
      setPinned(message.getPinned());
```

- [ ] **Step 1.8: Add to SearchResponseGsonImpl.java**

In the Gson serialization for Digest (where `json.add("8", new JsonPrimitive(message.getAuthor()));` appears ~line 490), after it add:
```java
      json.add("9", new JsonPrimitive(message.getPinned()));
```

In the Gson deserialization (where `jsonObject.get("8")` is handled ~line 809-829), add:
```java
      if (jsonObject.has("9")) {
        setPinned(jsonObject.get("9").getAsBoolean());
      }
```

- [ ] **Step 1.9: Verify compilation**

```bash
cd /Users/vega/devroot/worktrees/feat-pinned-waves-ux
sbt compile
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 1.10: Commit**

```bash
git add wave/src/proto/proto/org/waveprotocol/box/search/search.proto \
  gen/messages/org/waveprotocol/box/search/SearchResponse.java \
  gen/messages/org/waveprotocol/box/search/SearchResponseBuilder.java \
  gen/messages/org/waveprotocol/box/search/SearchResponseUtil.java \
  gen/messages/org/waveprotocol/box/search/jso/SearchResponseJsoImpl.java \
  gen/messages/org/waveprotocol/box/search/proto/SearchResponseProtoImpl.java \
  gen/messages/org/waveprotocol/box/search/impl/SearchResponseImpl.java \
  gen/messages/org/waveprotocol/box/search/gson/SearchResponseGsonImpl.java
git commit -m "feat(proto): add pinned field to SearchResponse.Digest"
```

---

## Task 2: Server Java API — SearchResult.Digest + WaveDigester + SearchServlet

**Files:**
- Modify: `wave/src/main/java/com/google/wave/api/SearchResult.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java`

- [ ] **Step 2.1: Write failing test in WaveDigesterTest**

In `WaveDigesterTest.java`, add after the existing `testUnreadCount` test:

```java
  public void testPinnedWaveDigestReflectsSupplementIsPinned() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("pinned wave title");
    ObservableWaveletData observableWaveletData = data.copyWaveletData().get(0);
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(observableWaveletData);
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);

    SupplementedWave supplement = mock(SupplementedWave.class);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(false);
    when(supplement.isPinned()).thenReturn(true);

    Digest digest =
        digester.generateDigest(
            conversation,
            supplement,
            observableWaveletData,
            Collections.singletonList(observableWaveletData));

    assertTrue("digest.isPinned() should be true when supplement.isPinned() is true",
        digest.isPinned());
  }

  public void testUnpinnedWaveDigestReturnsFalseForIsPinned() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("regular wave");
    ObservableWaveletData observableWaveletData = data.copyWaveletData().get(0);
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(observableWaveletData);
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);

    SupplementedWave supplement = mock(SupplementedWave.class);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(false);
    when(supplement.isPinned()).thenReturn(false);

    Digest digest =
        digester.generateDigest(
            conversation,
            supplement,
            observableWaveletData,
            Collections.singletonList(observableWaveletData));

    assertFalse("digest.isPinned() should be false when supplement.isPinned() is false",
        digest.isPinned());
  }
```

- [ ] **Step 2.2: Run test — verify it fails**

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.WaveDigesterTest"
```

Expected: compile error — `Digest.isPinned()` doesn't exist yet, or the test fails with assertion error.

- [ ] **Step 2.3: Add `pinned` to `com.google.wave.api.SearchResult.Digest`**

In `wave/src/main/java/com/google/wave/api/SearchResult.java`, in the `Digest` inner class:

After `private final int blipCount;` (~line 42), add:
```java
    private final boolean pinned;
```

Replace the existing constructor:
```java
    public Digest(String title, String snippet, String waveId, List<String> participants,
                  long lastModified, long created, int unreadCount, int blipCount) {
      this.title = title;
      this.snippet = snippet;
      this.waveId = waveId;
      this.participants = new ArrayList<String>(participants);
      this.lastModified = lastModified;
      this.created = created;
      this.unreadCount = unreadCount;
      this.blipCount = blipCount;
    }
```

With:
```java
    public Digest(String title, String snippet, String waveId, List<String> participants,
                  long lastModified, long created, int unreadCount, int blipCount,
                  boolean pinned) {
      this.title = title;
      this.snippet = snippet;
      this.waveId = waveId;
      this.participants = new ArrayList<String>(participants);
      this.lastModified = lastModified;
      this.created = created;
      this.unreadCount = unreadCount;
      this.blipCount = blipCount;
      this.pinned = pinned;
    }
```

After the last existing getter (`getBlipCount()` — read the file to find it), add:
```java
    public boolean isPinned() {
      return pinned;
    }
```

- [ ] **Step 2.4: Update WaveDigester to pass `supplement.isPinned()`**

In `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`, at line ~334, replace:

```java
    return new Digest(title, snippet, waveId, participants, lastModified,
        rawWaveletData.getCreationTime(), unreadCount, blipCount);
```

With:

```java
    return new Digest(title, snippet, waveId, participants, lastModified,
        rawWaveletData.getCreationTime(), unreadCount, blipCount, supplement.isPinned());
```

- [ ] **Step 2.5: Fix all existing callers of `new Digest(...)` to pass `false` for pinned**

Search for all other callers of `new Digest(` in the codebase:

```bash
grep -rn "new Digest(" wave/src/main/java/com/google/wave/api/ wave/src/test/
```

For each call with the 8-argument form (excluding WaveDigester, which you just updated), append `, false`:

The likely callers are in test files. Update each one to add `, false` as the last argument.

- [ ] **Step 2.6: Update SearchServlet.serializeDigest() to set pinned**

In `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`, in the `serializeDigest` method after `digestBuilder.setAuthor(...)` block (before `return digestBuilder.build()`), add:

```java
    digestBuilder.setPinned(searchResultDigest.isPinned());
```

Full context for location — the method ends with:
```java
    SearchResponse.Digest digest = digestBuilder.build();
    return digest;
```

Insert `digestBuilder.setPinned(searchResultDigest.isPinned());` immediately before `SearchResponse.Digest digest = digestBuilder.build();`.

- [ ] **Step 2.7: Run WaveDigester tests — verify they pass**

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.WaveDigesterTest"
```

Expected: All tests PASS.

- [ ] **Step 2.8: Commit**

```bash
git add wave/src/main/java/com/google/wave/api/SearchResult.java \
  wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java
git commit -m "feat(server): populate isPinned in search digest from supplement"
```

---

## Task 3: SimpleSearchProviderImpl — skip promotion when orderby present

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [ ] **Step 3.1: Write failing tests in SimpleSearchProviderImplTest**

Add these two test methods and one helper to `SimpleSearchProviderImplTest.java`:

```java
  /**
   * Verifies pinned waves appear first in a plain in:inbox query.
   */
  public void testPinnedWavesAppearFirstInPlainInbox() throws Exception {
    // Wave A — older, will be pinned
    WaveletName olderPinned = WaveletName.of(WaveId.of(DOMAIN, "old-pinned"), WAVELET_ID);
    submitDeltaToNewWavelet(olderPinned, USER1, addParticipantToWavelet(USER1, olderPinned));
    pinWaveForUser(olderPinned, USER1);

    // Wave B — newer, not pinned
    WaveletName newerUnpinned = WaveletName.of(WaveId.of(DOMAIN, "new-unpinned"), WAVELET_ID);
    submitDeltaToNewWavelet(newerUnpinned, USER1,
        addParticipantToWavelet(USER1, newerUnpinned));

    SearchResult results = searchProvider.search(USER1, "in:inbox", 0, 10);

    assertEquals(2, results.getNumResults());
    // Pinned wave must be first regardless of date.
    assertEquals(olderPinned.waveId.serialise(), results.getDigests().get(0).getWaveId());
    assertTrue("First result must be pinned", results.getDigests().get(0).isPinned());
    assertFalse("Second result must not be pinned", results.getDigests().get(1).isPinned());
  }

  /**
   * Verifies pinned waves are NOT forced to the top when orderby: is present.
   */
  public void testPinnedWavesNotPromotedWhenOrderByPresent() throws Exception {
    // Wave A — older, pinned
    WaveletName olderPinned = WaveletName.of(WaveId.of(DOMAIN, "old-pinned-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(olderPinned, USER1, addParticipantToWavelet(USER1, olderPinned));
    pinWaveForUser(olderPinned, USER1);

    // Wave B — newer, not pinned
    WaveletName newerUnpinned = WaveletName.of(WaveId.of(DOMAIN, "new-unpinned-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(newerUnpinned, USER1,
        addParticipantToWavelet(USER1, newerUnpinned));

    // With orderby:datedesc, newest should be first regardless of pin state.
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);

    assertEquals(2, results.getNumResults());
    // Newer wave must come first because orderby:datedesc is specified.
    assertEquals(newerUnpinned.waveId.serialise(), results.getDigests().get(0).getWaveId());
  }
```

Add this private helper method alongside `archiveWaveForUser` and `muteWaveForUser`:

```java
  private void pinWaveForUser(WaveletName name, ParticipantId user) throws Exception {
    WaveletOperation pinOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.FOLDERS_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                new DocOpBuilder()
                    .elementStart(
                        WaveletBasedSupplement.FOLDER_TAG,
                        new AttributesImpl(
                            WaveletBasedSupplement.ID_ATTR,
                            String.valueOf(SupplementedWaveImpl.PINNED_FOLDER)))
                    .elementEnd()
                    .build()));
    submitDeltaToNewWaveletWithoutView(
        userDataWaveletName(name.waveId, user), user, pinOperation);
  }
```

Add the following import to `SimpleSearchProviderImplTest.java` (with existing imports):
```java
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
```

- [ ] **Step 3.2: Run tests — verify they fail**

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"
```

Expected: `testPinnedWavesAppearFirstInPlainInbox` and `testPinnedWavesNotPromotedWhenOrderByPresent` FAIL (or compile errors).

- [ ] **Step 3.3: Add orderby guard in SimpleSearchProviderImpl**

In `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`, find the block starting at ~line 348:

```java
    // Promote pinned waves to the top of results (unless the query is specifically
    // for pinned waves, in which case all results are already pinned).
    if (!isPinnedQuery) {
```

Replace it with:

```java
    // Promote pinned waves to the top of results (unless the query is specifically
    // for pinned waves, in which case all results are already pinned).
    // Also skip promotion if an explicit orderby: modifier is present — the user
    // chose a sort order so we must respect it rather than forcing pinned-first.
    final boolean hasOrderBy = queryParams.containsKey(TokenQueryType.ORDERBY);
    if (!isPinnedQuery && !hasOrderBy) {
```

- [ ] **Step 3.4: Run tests — verify they pass**

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"
```

Expected: All tests including the two new ones PASS.

- [ ] **Step 3.5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java
git commit -m "feat(search): skip pinned-first promotion when orderby modifier present"
```

---

## Task 4: Client transport — wire `pinned` from proto to DigestSnapshot

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/testing/FakeSearchService.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`

- [ ] **Step 4.1: Add `pinned` to DigestSnapshot**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java`, in the `DigestSnapshot` class:

After `private final List<ParticipantId> participants;` (~line 64), add:
```java
    private final boolean pinned;
```

Replace the existing constructor:
```java
    public DigestSnapshot(String title, String snippet, WaveId waveId, ParticipantId author,
        List<ParticipantId> participants, double lastModified, int unreadCount, int blipCount) {
      this.title = title;
      this.snippet = snippet;
      this.waveId = waveId;
      this.author = author;
      this.participants = participants;
      this.lastModified = lastModified;
      this.unreadCount = unreadCount;
      this.blipCount = blipCount;
    }
```

With:
```java
    public DigestSnapshot(String title, String snippet, WaveId waveId, ParticipantId author,
        List<ParticipantId> participants, double lastModified, int unreadCount, int blipCount,
        boolean pinned) {
      this.title = title;
      this.snippet = snippet;
      this.waveId = waveId;
      this.author = author;
      this.participants = participants;
      this.lastModified = lastModified;
      this.unreadCount = unreadCount;
      this.blipCount = blipCount;
      this.pinned = pinned;
    }
```

Replace the existing `isPinned()` override:
```java
    @Override
    public boolean isPinned() {
      return false; // Pin state determined server-side for ordering
    }
```

With:
```java
    @Override
    public boolean isPinned() {
      return pinned;
    }
```

- [ ] **Step 4.2: Update JsoSearchBuilderImpl to pass `pinned` from proto**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`, in the `deserializeDigest` method (~line 164), replace:

```java
      DigestSnapshot digestSnapshot =
          new DigestSnapshot(digest.getTitle(), digest.getSnippet(), WaveId.deserialise(digest
              .getWaveId()), ParticipantId.ofUnsafe(digest.getAuthor()), participantIds,
              digest.getLastModified(), digest.getUnreadCount(), digest.getBlipCount());
```

With:

```java
      DigestSnapshot digestSnapshot =
          new DigestSnapshot(digest.getTitle(), digest.getSnippet(), WaveId.deserialise(digest
              .getWaveId()), ParticipantId.ofUnsafe(digest.getAuthor()), participantIds,
              digest.getLastModified(), digest.getUnreadCount(), digest.getBlipCount(),
              digest.getPinned());
```

- [ ] **Step 4.3: Preserve pinned in SimpleSearch.DigestProxy.deactivate()**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java`, in the `deactivate()` method (~line 103), replace:

```java
      staticDigest =
          new DigestSnapshot(getTitle(), getSnippet(), getWaveId(), getAuthor(),
              getParticipantsSnippet(), getLastModifiedTime(), getUnreadCount(), getBlipCount());
```

With:

```java
      staticDigest =
          new DigestSnapshot(getTitle(), getSnippet(), getWaveId(), getAuthor(),
              getParticipantsSnippet(), getLastModifiedTime(), getUnreadCount(), getBlipCount(),
              staticDigest.isPinned());
```

Note: We use `staticDigest.isPinned()` (not `isPinned()`) to preserve the original search-response pin state, since `WaveBasedDigest.isPinned()` always returns false.

- [ ] **Step 4.4: Fix FakeSearchService — add `false` for pinned**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/testing/FakeSearchService.java`, at ~line 72, replace:

```java
        digests.add(new DigestSnapshot(title, snippet, wid, author, participants, lmt, unread, msgs));
```

With:

```java
        digests.add(new DigestSnapshot(title, snippet, wid, author, participants, lmt, unread, msgs, false));
```

- [ ] **Step 4.5: Fix SearchPresenter.parseDigest() — add `false` for pinned**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`, in the `parseDigest` method (~line 1453), replace:

```java
      return new SearchService.DigestSnapshot(
          stringValue(attrs.get("title")),
          stringValue(attrs.get("snippet")),
          waveId,
          author,
          participants,
          parseLong(attrs.get("modified")),
          parseInt(attrs.get("unread"), 0),
          parseInt(attrs.get("blips"), 0));
```

With:

```java
      return new SearchService.DigestSnapshot(
          stringValue(attrs.get("title")),
          stringValue(attrs.get("snippet")),
          waveId,
          author,
          participants,
          parseLong(attrs.get("modified")),
          parseInt(attrs.get("unread"), 0),
          parseInt(attrs.get("blips"), 0),
          false);
```

- [ ] **Step 4.6: Check for any remaining callers and fix them**

Search for remaining 8-arg `DigestSnapshot` calls that need updating:

```bash
grep -rn "new DigestSnapshot(" wave/src/
```

For any remaining instances with 8 arguments (not already updated), append `, false` before the closing `)`.

- [ ] **Step 4.7: Verify compilation**

```bash
sbt compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4.8: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java \
  wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java \
  wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java \
  wave/src/main/java/org/waveprotocol/box/webclient/search/testing/FakeSearchService.java \
  wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java
git commit -m "feat(client): wire pinned state from proto through DigestSnapshot"
```

---

## Task 5: Client UI — pin icon in DigestDomImpl

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestView.java`
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java`
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/mock/digest.css`

- [ ] **Step 5.1: Add `setPinned` to DigestView interface**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestView.java`, after `void setMessageCounts(int unread, int total);`, add:

```java
  void setPinned(boolean pinned);
```

- [ ] **Step 5.2: Add pin icon element to DigestDomImpl.ui.xml**

In `wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml`, replace the `<div class='{css.info}'>` block:

```xml
      <div class='{css.info}'>
        <div ui:field='time'/>
        <div ui:field='msgs'/>
      </div>
```

With:

```xml
      <div class='{css.info}'>
        <span ui:field='pinIcon' class='{css.pinIcon}'>&#x1F4CC;</span>
        <div ui:field='time'/>
        <div ui:field='msgs'/>
      </div>
```

The `&#x1F4CC;` is the pushpin emoji (📌). It renders at the system emoji font size and is controlled by the CSS.

- [ ] **Step 5.3: Add `pinIcon` CSS class to DigestDomImpl.Css interface and @UiField**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java`:

1. In the `Css` interface, after `String selected();`, add:
```java
    String pinIcon();
```

2. After the `@UiField Element msgs;` declaration (~line 96), add:
```java
  @UiField
  Element pinIcon;
```

3. In the `reset()` method (~line 117), after `self.removeClassName(css.selected());`, add:
```java
    pinIcon.getStyle().setDisplay(com.google.gwt.dom.client.Style.Display.NONE);
```

4. Add the `setPinned` method after `setMessageCounts`:
```java
  @Override
  public void setPinned(boolean pinned) {
    if (pinned) {
      pinIcon.getStyle().setDisplay(com.google.gwt.dom.client.Style.Display.INLINE);
    } else {
      pinIcon.getStyle().setDisplay(com.google.gwt.dom.client.Style.Display.NONE);
    }
  }
```

- [ ] **Step 5.4: Add `.pinIcon` CSS rule to digest.css**

In `wave/src/main/resources/org/waveprotocol/box/webclient/search/mock/digest.css`, append after the last existing rule:

```css
.pinIcon {
  display: none;
  font-size: 11px;
  color: #888;
  margin-right: 4px;
  vertical-align: middle;
  cursor: default;
}

.selected .pinIcon {
  color: rgba(255,255,255,0.8);
}
```

- [ ] **Step 5.5: Verify compilation**

```bash
sbt compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 5.6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/search/DigestView.java \
  wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml \
  wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java \
  wave/src/main/resources/org/waveprotocol/box/webclient/search/mock/digest.css
git commit -m "feat(ui): add pin icon to DigestDomImpl for pinned waves"
```

---

## Task 6: Wire SearchPanelRenderer to call setPinned

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java`

- [ ] **Step 6.1: Call `setPinned` in `SearchPanelRenderer.render()`**

In `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java`, in the `render` method, after the `digestUi.setTimestamp(...)` line (~line 64), add:

```java
    digestUi.setPinned(digest.isPinned());
```

The full render method will look like:
```java
  public void render(Digest digest, DigestView digestUi) {
    Collection<Profile> avatars = CollectionUtils.createQueue();
    if (digest.getAuthor() != null) {
      avatars.add(profiles.getProfile(digest.getAuthor()));
    }
    for (ParticipantId other : digest.getParticipantsSnippet()) {
      if (avatars.size() < MAX_AVATARS) {
        avatars.add(profiles.getProfile(other));
      } else {
        break;
      }
    }

    digestUi.setAvatars(avatars);
    digestUi.setTitleText(digest.getTitle());
    digestUi.setSnippet(digest.getSnippet());
    digestUi.setMessageCounts(digest.getUnreadCount(), digest.getBlipCount());
    digestUi.setTimestamp(
        DateUtils.getInstance().formatPastDate((long) digest.getLastModifiedTime()));
    digestUi.setPinned(digest.isPinned());
  }
```

- [ ] **Step 6.2: Verify compilation**

```bash
sbt compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 6.3: Run all tests**

```bash
sbt test
```

Expected: All tests PASS.

- [ ] **Step 6.4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java
git commit -m "feat(ui): call setPinned in SearchPanelRenderer to show pin icon"
```

---

## Task 7: Local verification, push, and PR

- [ ] **Step 7.1: Run full test suite one more time**

```bash
sbt test
```

Expected: All tests PASS with no failures.

- [ ] **Step 7.2: Push branch**

```bash
git push -u origin feat/pinned-waves-inbox-top
```

- [ ] **Step 7.3: Create PR**

```bash
gh pr create \
  --title "feat: pinned waves always on top of inbox + pin icon" \
  --body "$(cat <<'EOF'
## Summary

- Pinned waves always sort to the top of the `in:inbox` view (skipped when `orderby:` is present)
- Each pinned wave shows a 📌 pin icon in the search results list
- `pinned` boolean added to `SearchResponse.Digest` proto — populated from `SupplementedWave.isPinned()` server-side, read by GWT client through `DigestSnapshot`

## Changes

- `search.proto` + 7 generated files: add `optional bool pinned = 9` to Digest
- `WaveDigester`: reads `supplement.isPinned()` into `SearchResult.Digest`
- `SearchServlet`: serializes `pinned` into proto response
- `SimpleSearchProviderImpl`: skip `promotePinnedWaves()` when `orderby:` param present
- `DigestSnapshot` + `JsoSearchBuilderImpl`: wire `pinned` from proto to client
- `DigestDomImpl` + CSS: show 📌 icon when wave is pinned
- `SearchPanelRenderer`: call `setPinned()` on each rendered digest

## Test plan

- [ ] `sbt test` passes (including 2 new tests in `SimpleSearchProviderImplTest` and 2 in `WaveDigesterTest`)
- [ ] Manual: open inbox, verify pinned waves appear at top
- [ ] Manual: open inbox with `orderby:dateasc`, verify no forced pinned-first
- [ ] Manual: pinned waves show 📌 icon; unpinned do not

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

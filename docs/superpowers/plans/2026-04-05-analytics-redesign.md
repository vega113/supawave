# Analytics Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken on-demand full-scan analytics with incremental MongoDB counters, historical time windows, and Chart.js charts in a wavy-themed admin UI.

**Architecture:** Events (wave/blip creation, user registration, views) are recorded incrementally into hourly MongoDB buckets via `AnalyticsRecorder` (a WaveBus subscriber + servlet hooks). A query service aggregates buckets for time windows (1h–30d). The admin UI uses Chart.js for trend charts with a wave-themed design.

**Tech Stack:** Java 17, MongoDB 4.x driver, Chart.js 4.x (CDN), Guice DI, WaveBus pub/sub

**Spec:** `docs/superpowers/specs/2026-04-05-analytics-redesign.md`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `wave/src/main/java/org/waveprotocol/box/server/persistence/AnalyticsCounterStore.java` | Create | Interface for hourly counter storage |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java` | Create | MongoDB implementation with upsert/aggregation |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStore.java` | Create | In-memory implementation for non-MongoDB |
| `wave/src/main/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorder.java` | Create | WaveBus subscriber + event recording singleton |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java` | Modify | Add `provideMongoDbAnalyticsCounterStore()` |
| `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java` | Modify | Add `bindAnalyticsCounterStore()` |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` | Modify | Subscribe AnalyticsRecorder to WaveBus |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java` | Modify | Add `/admin/api/analytics/history` endpoint |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` | Modify | Rewrite analytics panel with charts + wavy UI |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java` | Modify | Add `AnalyticsRecorder.incrementPageViews()` |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java` | Modify | Add `AnalyticsRecorder.incrementApiViews()` |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java` | Modify | Add `AnalyticsRecorder.recordActiveUser()` |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java` | Modify | Add `AnalyticsRecorder.incrementUsersRegistered()` |
| `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStoreTest.java` | Create | Tests for in-memory store |
| `wave/src/test/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorderTest.java` | Create | Tests for recorder |

---

### Task 1: AnalyticsCounterStore Interface & HourlyBucket

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/AnalyticsCounterStore.java`

- [ ] **Step 1: Create the interface and data class**

```java
package org.waveprotocol.box.server.persistence;

import java.util.List;
import java.util.Set;

/**
 * Stores and retrieves hourly analytics counters.
 * Each bucket represents one hour of activity.
 */
public interface AnalyticsCounterStore {

  /** Increment waves created in the hour containing timestampMs. */
  void incrementWavesCreated(long timestampMs);

  /** Increment blips created in the hour containing timestampMs. */
  void incrementBlipsCreated(long timestampMs, int count);

  /** Increment users registered in the hour containing timestampMs. */
  void incrementUsersRegistered(long timestampMs);

  /** Record a user as active in the hour containing timestampMs. */
  void recordActiveUser(String userId, long timestampMs);

  /** Increment page views in the hour containing timestampMs. */
  void incrementPageViews(long timestampMs);

  /** Increment API views in the hour containing timestampMs. */
  void incrementApiViews(long timestampMs);

  /** Returns hourly buckets in [fromMs, toMs) range, ordered by hour ascending. */
  List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs);

  /** A single hour of analytics data. */
  final class HourlyBucket {
    private final long hourMs;
    private final int wavesCreated;
    private final int blipsCreated;
    private final int usersRegistered;
    private final Set<String> activeUserIds;
    private final long pageViews;
    private final long apiViews;

    public HourlyBucket(long hourMs, int wavesCreated, int blipsCreated,
        int usersRegistered, Set<String> activeUserIds, long pageViews, long apiViews) {
      this.hourMs = hourMs;
      this.wavesCreated = wavesCreated;
      this.blipsCreated = blipsCreated;
      this.usersRegistered = usersRegistered;
      this.activeUserIds = Set.copyOf(activeUserIds);
      this.pageViews = pageViews;
      this.apiViews = apiViews;
    }

    public long getHourMs() { return hourMs; }
    public int getWavesCreated() { return wavesCreated; }
    public int getBlipsCreated() { return blipsCreated; }
    public int getUsersRegistered() { return usersRegistered; }
    public Set<String> getActiveUserIds() { return activeUserIds; }
    public long getPageViews() { return pageViews; }
    public long getApiViews() { return apiViews; }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/AnalyticsCounterStore.java
git commit -m "feat(analytics): add AnalyticsCounterStore interface and HourlyBucket"
```

---

### Task 2: MemoryAnalyticsCounterStore

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStore.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStoreTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package org.waveprotocol.box.server.persistence.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;

public class MemoryAnalyticsCounterStoreTest {

  private static final long HOUR_MS = 3600_000L;
  // 2026-04-05T10:30:00Z
  private static final long BASE_TIME = 1775386200000L;
  // Truncated to 2026-04-05T10:00:00Z
  private static final long BASE_HOUR = BASE_TIME - (BASE_TIME % HOUR_MS);

  private MemoryAnalyticsCounterStore store;

  @Before
  public void setUp() {
    store = new MemoryAnalyticsCounterStore();
  }

  @Test
  public void testIncrementWavesCreated() {
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + 1000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getWavesCreated());
  }

  @Test
  public void testIncrementBlipsCreated() {
    store.incrementBlipsCreated(BASE_TIME, 5);
    store.incrementBlipsCreated(BASE_TIME + 1000, 3);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(8, buckets.get(0).getBlipsCreated());
  }

  @Test
  public void testRecordActiveUser_deduplicatesWithinHour() {
    store.recordActiveUser("alice@example.com", BASE_TIME);
    store.recordActiveUser("alice@example.com", BASE_TIME + 1000);
    store.recordActiveUser("bob@example.com", BASE_TIME + 2000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getActiveUserIds().size());
    assertTrue(buckets.get(0).getActiveUserIds().contains("alice@example.com"));
    assertTrue(buckets.get(0).getActiveUserIds().contains("bob@example.com"));
  }

  @Test
  public void testSeparateHours() {
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + HOUR_MS);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + 2 * HOUR_MS);
    assertEquals(2, buckets.size());
    assertEquals(1, buckets.get(0).getWavesCreated());
    assertEquals(1, buckets.get(1).getWavesCreated());
  }

  @Test
  public void testGetHourlyBuckets_emptyRangeReturnsEmpty() {
    store.incrementWavesCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR + 2 * HOUR_MS, BASE_HOUR + 3 * HOUR_MS);
    assertEquals(0, buckets.size());
  }

  @Test
  public void testGetHourlyBuckets_orderedAscending() {
    store.incrementWavesCreated(BASE_TIME + 2 * HOUR_MS);
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + HOUR_MS);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + 3 * HOUR_MS);
    assertEquals(3, buckets.size());
    assertTrue(buckets.get(0).getHourMs() < buckets.get(1).getHourMs());
    assertTrue(buckets.get(1).getHourMs() < buckets.get(2).getHourMs());
  }

  @Test
  public void testIncrementPageAndApiViews() {
    store.incrementPageViews(BASE_TIME);
    store.incrementPageViews(BASE_TIME);
    store.incrementApiViews(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getPageViews());
    assertEquals(1, buckets.get(0).getApiViews());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/vega/devroot/incubator-wave && sbt "Test/compile" 2>&1 | tail -5`
Expected: Compilation failure — `MemoryAnalyticsCounterStore` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.waveprotocol.box.server.persistence.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;

/**
 * In-memory implementation of {@link AnalyticsCounterStore}.
 * Data does not survive process restarts.
 */
public final class MemoryAnalyticsCounterStore implements AnalyticsCounterStore {

  private static final long HOUR_MS = 3_600_000L;

  private final ConcurrentMap<Long, MutableBucket> buckets = new ConcurrentHashMap<>();

  @Override
  public void incrementWavesCreated(long timestampMs) {
    getBucket(timestampMs).wavesCreated.increment();
  }

  @Override
  public void incrementBlipsCreated(long timestampMs, int count) {
    getBucket(timestampMs).blipsCreated.add(count);
  }

  @Override
  public void incrementUsersRegistered(long timestampMs) {
    getBucket(timestampMs).usersRegistered.increment();
  }

  @Override
  public void recordActiveUser(String userId, long timestampMs) {
    getBucket(timestampMs).activeUserIds.add(userId);
  }

  @Override
  public void incrementPageViews(long timestampMs) {
    getBucket(timestampMs).pageViews.increment();
  }

  @Override
  public void incrementApiViews(long timestampMs) {
    getBucket(timestampMs).apiViews.increment();
  }

  @Override
  public List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs) {
    long fromHour = truncateToHour(fromMs);
    long toHour = truncateToHour(toMs);
    List<HourlyBucket> result = new ArrayList<>();
    for (var entry : buckets.entrySet()) {
      long hour = entry.getKey();
      if (hour >= fromHour && hour < toHour) {
        result.add(entry.getValue().freeze(hour));
      }
    }
    result.sort((a, b) -> Long.compare(a.getHourMs(), b.getHourMs()));
    return Collections.unmodifiableList(result);
  }

  private MutableBucket getBucket(long timestampMs) {
    return buckets.computeIfAbsent(truncateToHour(timestampMs), k -> new MutableBucket());
  }

  static long truncateToHour(long timestampMs) {
    return timestampMs - (timestampMs % HOUR_MS);
  }

  private static final class MutableBucket {
    final LongAdder wavesCreated = new LongAdder();
    final LongAdder blipsCreated = new LongAdder();
    final LongAdder usersRegistered = new LongAdder();
    final Set<String> activeUserIds = ConcurrentHashMap.newKeySet();
    final LongAdder pageViews = new LongAdder();
    final LongAdder apiViews = new LongAdder();

    HourlyBucket freeze(long hourMs) {
      return new HourlyBucket(
          hourMs,
          (int) wavesCreated.sum(),
          (int) blipsCreated.sum(),
          (int) usersRegistered.sum(),
          new HashSet<>(activeUserIds),
          pageViews.sum(),
          apiViews.sum());
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/vega/devroot/incubator-wave && sbt "testOnly org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStoreTest" 2>&1 | tail -10`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStore.java \
       wave/src/test/java/org/waveprotocol/box/server/persistence/memory/MemoryAnalyticsCounterStoreTest.java
git commit -m "feat(analytics): add MemoryAnalyticsCounterStore with tests"
```

---

### Task 3: Mongo4AnalyticsCounterStore

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java`

- [ ] **Step 1: Create the MongoDB implementation**

```java
package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;

/**
 * MongoDB implementation of {@link AnalyticsCounterStore}.
 * Uses the {@code analytics_hourly} collection with one document per hour.
 * All writes use upsert with $inc/$addToSet for lock-free incremental updates.
 */
final class Mongo4AnalyticsCounterStore implements AnalyticsCounterStore {

  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(Mongo4AnalyticsCounterStore.class.getName());

  private static final String COLLECTION = "analytics_hourly";
  private static final String HOUR_FIELD = "hour";
  private static final String WAVES_CREATED = "wavesCreated";
  private static final String BLIPS_CREATED = "blipsCreated";
  private static final String USERS_REGISTERED = "usersRegistered";
  private static final String ACTIVE_USER_IDS = "activeUserIds";
  private static final String PAGE_VIEWS = "pageViews";
  private static final String API_VIEWS = "apiViews";
  private static final long HOUR_MS = 3_600_000L;
  private static final UpdateOptions UPSERT = new UpdateOptions().upsert(true);

  private final MongoCollection<Document> col;

  Mongo4AnalyticsCounterStore(MongoDatabase db) {
    this.col = db.getCollection(COLLECTION);
    col.createIndex(Indexes.ascending(HOUR_FIELD), new IndexOptions().unique(true));
  }

  @Override
  public void incrementWavesCreated(long timestampMs) {
    upsertInc(timestampMs, WAVES_CREATED, 1);
  }

  @Override
  public void incrementBlipsCreated(long timestampMs, int count) {
    upsertInc(timestampMs, BLIPS_CREATED, count);
  }

  @Override
  public void incrementUsersRegistered(long timestampMs) {
    upsertInc(timestampMs, USERS_REGISTERED, 1);
  }

  @Override
  public void recordActiveUser(String userId, long timestampMs) {
    try {
      Date hour = new Date(truncateToHour(timestampMs));
      col.updateOne(
          Filters.eq(HOUR_FIELD, hour),
          Updates.addToSet(ACTIVE_USER_IDS, userId),
          UPSERT);
    } catch (Exception e) {
      LOG.warning("Failed to record active user: " + e.getMessage());
    }
  }

  @Override
  public void incrementPageViews(long timestampMs) {
    upsertInc(timestampMs, PAGE_VIEWS, 1);
  }

  @Override
  public void incrementApiViews(long timestampMs) {
    upsertInc(timestampMs, API_VIEWS, 1);
  }

  @Override
  public List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs) {
    Date fromHour = new Date(truncateToHour(fromMs));
    Date toHour = new Date(truncateToHour(toMs));
    Bson filter = Filters.and(
        Filters.gte(HOUR_FIELD, fromHour),
        Filters.lt(HOUR_FIELD, toHour));

    List<HourlyBucket> result = new ArrayList<>();
    for (Document doc : col.find(filter).sort(new Document(HOUR_FIELD, 1))) {
      result.add(docToBucket(doc));
    }
    return Collections.unmodifiableList(result);
  }

  private void upsertInc(long timestampMs, String field, int amount) {
    try {
      Date hour = new Date(truncateToHour(timestampMs));
      col.updateOne(
          Filters.eq(HOUR_FIELD, hour),
          Updates.inc(field, amount),
          UPSERT);
    } catch (Exception e) {
      LOG.warning("Failed to increment " + field + ": " + e.getMessage());
    }
  }

  private static HourlyBucket docToBucket(Document doc) {
    Date hour = doc.getDate(HOUR_FIELD);
    @SuppressWarnings("unchecked")
    List<String> activeList = doc.getList(ACTIVE_USER_IDS, String.class, Collections.emptyList());
    return new HourlyBucket(
        hour != null ? hour.getTime() : 0L,
        doc.getInteger(WAVES_CREATED, 0),
        doc.getInteger(BLIPS_CREATED, 0),
        doc.getInteger(USERS_REGISTERED, 0),
        new HashSet<>(activeList),
        doc.getLong(PAGE_VIEWS) != null ? doc.getLong(PAGE_VIEWS) : doc.getInteger(PAGE_VIEWS, 0),
        doc.getLong(API_VIEWS) != null ? doc.getLong(API_VIEWS) : doc.getInteger(API_VIEWS, 0));
  }

  static long truncateToHour(long timestampMs) {
    return timestampMs - (timestampMs % HOUR_MS);
  }
}
```

- [ ] **Step 2: Add provider method to Mongo4DbProvider**

In `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java`, add after line 97:

```java
  public AnalyticsCounterStore provideMongoDbAnalyticsCounterStore() { ensure(); return new Mongo4AnalyticsCounterStore(db); }
```

Also add the import at the top:
```java
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
```

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java \
       wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java
git commit -m "feat(analytics): add Mongo4AnalyticsCounterStore with upsert writes"
```

---

### Task 4: Bind AnalyticsCounterStore in PersistenceModule

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java`

- [ ] **Step 1: Add binding method and call it from configure()**

Add import at top of `PersistenceModule.java`:
```java
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore;
```

Add call in `configure()` method after `bindFeatureFlagStore();`:
```java
    bindAnalyticsCounterStore();
```

Add new method after `bindFeatureFlagStore()`:
```java
  /**
   * Binds the AnalyticsCounterStore for incremental analytics counters.
   * Uses MongoDB when available, otherwise falls back to in-memory.
   */
  private void bindAnalyticsCounterStore() {
    if (accountStoreType.equalsIgnoreCase("mongodb") && "v4".equalsIgnoreCase(mongoDriver)) {
      bind(AnalyticsCounterStore.class)
          .toInstance(getMongo4Provider().provideMongoDbAnalyticsCounterStore());
    } else {
      bind(AnalyticsCounterStore.class)
          .to(MemoryAnalyticsCounterStore.class).in(Singleton.class);
    }
  }
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/vega/devroot/incubator-wave && sbt compile 2>&1 | tail -5`
Expected: Compilation succeeds.

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java
git commit -m "feat(analytics): bind AnalyticsCounterStore in PersistenceModule"
```

---

### Task 5: AnalyticsRecorder (WaveBus Subscriber)

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorder.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorderTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package org.waveprotocol.box.server.waveserver;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;
import org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore;

public class AnalyticsRecorderTest {

  private static final long HOUR_MS = 3_600_000L;
  private static final long BASE_TIME = 1775386200000L;
  private static final long BASE_HOUR = BASE_TIME - (BASE_TIME % HOUR_MS);

  private MemoryAnalyticsCounterStore store;
  private AnalyticsRecorder recorder;

  @Before
  public void setUp() {
    store = new MemoryAnalyticsCounterStore();
    recorder = new AnalyticsRecorder(store);
  }

  @Test
  public void testIncrementPageViews() {
    recorder.incrementPageViews(BASE_TIME);
    recorder.incrementPageViews(BASE_TIME + 1000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getPageViews());
  }

  @Test
  public void testIncrementApiViews() {
    recorder.incrementApiViews(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getApiViews());
  }

  @Test
  public void testRecordActiveUser() {
    recorder.recordActiveUser("alice@example.com", BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getActiveUserIds().size());
  }

  @Test
  public void testIncrementUsersRegistered() {
    recorder.incrementUsersRegistered(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getUsersRegistered());
  }

  @Test
  public void testRecordWaveCreated() {
    recorder.recordWaveCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getWavesCreated());
  }

  @Test
  public void testRecordBlipsCreated() {
    recorder.recordBlipsCreated(3, BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(3, buckets.get(0).getBlipsCreated());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Expected: Compilation fails — `AnalyticsRecorder` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.waveprotocol.box.server.waveserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Records analytics events into hourly counters.
 * Subscribes to WaveBus for wave/blip creation tracking.
 * Also called directly by servlets for views, registration, and activity.
 */
@Singleton
public final class AnalyticsRecorder implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(AnalyticsRecorder.class);

  private final AnalyticsCounterStore store;

  @Inject
  public AnalyticsRecorder(AnalyticsCounterStore store) {
    this.store = store;
  }

  // ---- Direct recording methods (called by servlets) ----

  public void incrementPageViews(long timestampMs) {
    store.incrementPageViews(timestampMs);
  }

  public void incrementApiViews(long timestampMs) {
    store.incrementApiViews(timestampMs);
  }

  public void recordActiveUser(String userId, long timestampMs) {
    store.recordActiveUser(userId, timestampMs);
  }

  public void incrementUsersRegistered(long timestampMs) {
    store.incrementUsersRegistered(timestampMs);
  }

  public void recordWaveCreated(long timestampMs) {
    store.incrementWavesCreated(timestampMs);
  }

  public void recordBlipsCreated(int count, long timestampMs) {
    if (count > 0) {
      store.incrementBlipsCreated(timestampMs, count);
    }
  }

  // ---- WaveBus.Subscriber ----

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
      return;
    }
    try {
      for (TransformedWaveletDelta delta : deltas) {
        long timestamp = delta.getApplicationTimestamp();
        // Detect new wave: first delta at version 0 on conv+root
        if (delta.getAppliedAtVersion() == 0L
            && "conv+root".equals(wavelet.getWaveletId().getId())) {
          store.incrementWavesCreated(timestamp);
        }
        // Count new blip operations
        Set<String> newBlipIds = new HashSet<>();
        for (WaveletOperation op : delta) {
          if (op instanceof WaveletBlipOperation blipOp) {
            String blipId = blipOp.getBlipId();
            if (blipId != null && IdUtil.isBlipId(blipId)) {
              newBlipIds.add(blipId);
            }
          }
        }
        if (!newBlipIds.isEmpty()) {
          store.incrementBlipsCreated(timestamp, newBlipIds.size());
        }
      }
    } catch (Exception e) {
      LOG.warning("AnalyticsRecorder.waveletUpdate failed: " + e.getMessage());
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // No action needed on commit.
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/vega/devroot/incubator-wave && sbt "testOnly org.waveprotocol.box.server.waveserver.AnalyticsRecorderTest" 2>&1 | tail -10`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorder.java \
       wave/src/test/java/org/waveprotocol/box/server/waveserver/AnalyticsRecorderTest.java
git commit -m "feat(analytics): add AnalyticsRecorder WaveBus subscriber with tests"
```

---

### Task 6: Subscribe AnalyticsRecorder in ServerMain

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] **Step 1: Find the WaveBus subscription block in ServerMain**

Look for the section that subscribes `ContactsRecorder` to waveBus (around line 440). Add AnalyticsRecorder subscription right after.

Add to imports:
```java
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
```

After the line `waveBus.subscribe(contactsRecorder);` and `LOG.info("ContactsRecorder subscribed to WaveBus");`, add:
```java
    AnalyticsRecorder analyticsRecorder = injector.getInstance(AnalyticsRecorder.class);
    waveBus.subscribe(analyticsRecorder);
    LOG.info("AnalyticsRecorder subscribed to WaveBus");
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/vega/devroot/incubator-wave && sbt compile 2>&1 | tail -5`
Expected: Compilation succeeds.

- [ ] **Step 3: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java
git commit -m "feat(analytics): subscribe AnalyticsRecorder to WaveBus in ServerMain"
```

---

### Task 7: Hook View/Activity/Registration Events

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`

- [ ] **Step 1: Add AnalyticsRecorder to PublicWaveServlet**

In `PublicWaveServlet.java`:
- Add field: `private final AnalyticsRecorder analyticsRecorder;`
- Add to constructor injection: `AnalyticsRecorder analyticsRecorder` parameter + `this.analyticsRecorder = analyticsRecorder;`
- After line `publicWaveViewTracker.recordPageView(waveId);` (line ~166), add:
```java
    analyticsRecorder.incrementPageViews(System.currentTimeMillis());
```
- Add import: `import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;`

- [ ] **Step 2: Add AnalyticsRecorder to PublicWaveFetchServlet**

In `PublicWaveFetchServlet.java`:
- Add field: `private final AnalyticsRecorder analyticsRecorder;`
- Add to constructor injection: `AnalyticsRecorder analyticsRecorder` parameter + `this.analyticsRecorder = analyticsRecorder;`
- After each `publicWaveViewTracker.recordApiView(...)` call (lines ~175, ~181, ~191), add:
```java
        analyticsRecorder.incrementApiViews(System.currentTimeMillis());
```
- Add import: `import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;`

- [ ] **Step 3: Add AnalyticsRecorder to SessionManagerImpl**

In `SessionManagerImpl.java`:
- Add field: `private final AnalyticsRecorder analyticsRecorder;`
- Add to constructor `@Inject`: `AnalyticsRecorder analyticsRecorder` parameter + `this.analyticsRecorder = analyticsRecorder;`
- In `refreshLastActivity()`, after `session.setAttribute(LAST_ACTIVITY_UPDATE_FIELD, now);` (line ~151), add:
```java
        analyticsRecorder.recordActiveUser(user.getAddress(), now);
```
- Add import: `import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;`

- [ ] **Step 4: Add AnalyticsRecorder to AuthenticationServlet for registration**

In `AuthenticationServlet.java`, find the registration success path (where a new account is created and persisted). After the account is successfully saved, add:
```java
    analyticsRecorder.incrementUsersRegistered(System.currentTimeMillis());
```
- Add field and constructor injection as with the other servlets.
- Add import: `import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;`

- [ ] **Step 5: Verify compilation**

Run: `cd /Users/vega/devroot/incubator-wave && sbt compile 2>&1 | tail -10`
Expected: Compilation succeeds.

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java \
       wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java \
       wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java \
       wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java
git commit -m "feat(analytics): hook event sources to AnalyticsRecorder"
```

---

### Task 8: Analytics History API Endpoint

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java`

- [ ] **Step 1: Add AnalyticsCounterStore injection and history handler**

Add import:
```java
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;
```

Add field to `AdminServlet`:
```java
  private final AnalyticsCounterStore analyticsCounterStore;
```

Add to constructor injection and assignment.

In the `doGet` routing section (around line 145), add a new route after the analytics/status check:
```java
    } else if (pathInfo != null && pathInfo.equals("/api/analytics/history")) {
      handleAnalyticsHistory(req, resp);
```

Add the handler method:
```java
  private void handleAnalyticsHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String window = req.getParameter("window");
    if (window == null || window.isEmpty()) {
      window = "24h";
    }
    long now = System.currentTimeMillis();
    long fromMs = resolveWindowStart(now, window);
    if (fromMs < 0) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid window. Use: 1h, 6h, 12h, 24h, 48h, 7d, 30d");
      return;
    }

    List<HourlyBucket> hourlyBuckets = analyticsCounterStore.getHourlyBuckets(fromMs, now);

    // Decide granularity: hourly for <=48h, daily for >48h
    long windowMs = now - fromMs;
    boolean daily = windowMs > 48L * 3600_000L;

    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    w.append('{');
    w.append("\"window\":").append(jsonStr(window));
    w.append(",\"granularity\":").append(jsonStr(daily ? "daily" : "hourly"));

    // Compute totals
    int totalWavesCreated = 0, totalBlipsCreated = 0, totalUsersRegistered = 0;
    long totalPageViews = 0, totalApiViews = 0;
    java.util.Set<String> allActiveUsers = new java.util.HashSet<>();
    for (HourlyBucket b : hourlyBuckets) {
      totalWavesCreated += b.getWavesCreated();
      totalBlipsCreated += b.getBlipsCreated();
      totalUsersRegistered += b.getUsersRegistered();
      totalPageViews += b.getPageViews();
      totalApiViews += b.getApiViews();
      allActiveUsers.addAll(b.getActiveUserIds());
    }
    w.append(",\"totals\":{");
    w.append("\"wavesCreated\":").append(String.valueOf(totalWavesCreated));
    w.append(",\"blipsCreated\":").append(String.valueOf(totalBlipsCreated));
    w.append(",\"usersRegistered\":").append(String.valueOf(totalUsersRegistered));
    w.append(",\"activeUsers\":").append(String.valueOf(allActiveUsers.size()));
    w.append(",\"pageViews\":").append(String.valueOf(totalPageViews));
    w.append(",\"apiViews\":").append(String.valueOf(totalApiViews));
    w.append('}');

    // Build series
    w.append(",\"series\":[");
    if (daily) {
      writeDailySeries(w, hourlyBuckets);
    } else {
      writeHourlySeries(w, hourlyBuckets);
    }
    w.append(']');
    w.append('}');
    w.flush();
  }

  private void writeHourlySeries(PrintWriter w, List<HourlyBucket> buckets) {
    for (int i = 0; i < buckets.size(); i++) {
      if (i > 0) w.append(',');
      HourlyBucket b = buckets.get(i);
      writeSeriesPoint(w, b.getHourMs(), b.getWavesCreated(), b.getBlipsCreated(),
          b.getUsersRegistered(), b.getActiveUserIds().size(), b.getPageViews(), b.getApiViews());
    }
  }

  private void writeDailySeries(PrintWriter w, List<HourlyBucket> buckets) {
    // Aggregate hourly into daily buckets
    java.util.Map<Long, int[]> daily = new java.util.LinkedHashMap<>();
    java.util.Map<Long, java.util.Set<String>> dailyUsers = new java.util.HashMap<>();
    long dayMs = 86_400_000L;
    for (HourlyBucket b : buckets) {
      long dayKey = b.getHourMs() - (b.getHourMs() % dayMs);
      int[] agg = daily.computeIfAbsent(dayKey, k -> new int[6]);
      agg[0] += b.getWavesCreated();
      agg[1] += b.getBlipsCreated();
      agg[2] += b.getUsersRegistered();
      agg[4] += (int) b.getPageViews();
      agg[5] += (int) b.getApiViews();
      dailyUsers.computeIfAbsent(dayKey, k -> new java.util.HashSet<>()).addAll(b.getActiveUserIds());
    }
    boolean first = true;
    for (var entry : daily.entrySet()) {
      if (!first) w.append(',');
      first = false;
      int[] agg = entry.getValue();
      int activeCount = dailyUsers.getOrDefault(entry.getKey(), java.util.Collections.emptySet()).size();
      writeSeriesPoint(w, entry.getKey(), agg[0], agg[1], agg[2], activeCount, agg[4], agg[5]);
    }
  }

  private void writeSeriesPoint(PrintWriter w, long timeMs, int wavesCreated, int blipsCreated,
      int usersRegistered, int activeUsers, long pageViews, long apiViews) {
    w.append("{\"time\":").append(String.valueOf(timeMs));
    w.append(",\"wavesCreated\":").append(String.valueOf(wavesCreated));
    w.append(",\"blipsCreated\":").append(String.valueOf(blipsCreated));
    w.append(",\"usersRegistered\":").append(String.valueOf(usersRegistered));
    w.append(",\"activeUsers\":").append(String.valueOf(activeUsers));
    w.append(",\"pageViews\":").append(String.valueOf(pageViews));
    w.append(",\"apiViews\":").append(String.valueOf(apiViews));
    w.append('}');
  }

  private static long resolveWindowStart(long now, String window) {
    return switch (window) {
      case "1h" -> now - 3_600_000L;
      case "6h" -> now - 6L * 3_600_000L;
      case "12h" -> now - 12L * 3_600_000L;
      case "24h" -> now - 24L * 3_600_000L;
      case "48h" -> now - 48L * 3_600_000L;
      case "7d" -> now - 7L * 86_400_000L;
      case "30d" -> now - 30L * 86_400_000L;
      default -> -1L;
    };
  }
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/vega/devroot/incubator-wave && sbt compile 2>&1 | tail -5`
Expected: Compilation succeeds.

- [ ] **Step 3: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java
git commit -m "feat(analytics): add /admin/api/analytics/history endpoint with time windows"
```

---

### Task 9: Rewrite Analytics Panel UI with Charts

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

This is the largest task. Replace the entire `panel-analytics` section (lines ~4369-4410) with the new wavy-themed charts UI.

- [ ] **Step 1: Add Chart.js CDN script tag**

Find the `<head>` section in `renderAdminPage()`. Add Chart.js CDN before the closing `</head>`:
```java
sb.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js\"></script>\n");
```

- [ ] **Step 2: Replace the analytics panel HTML**

Replace the entire analytics panel (from `// Analytics tab panel` comment to `// end panel-analytics`) with new content. The new panel includes:

1. **Wave SVG divider** at the top
2. **Time window pills** (1h, 6h, 12h, 24h, 48h, 7d, 30d)
3. **Summary stat cards** (4-column grid)
4. **Chart row** (2x2 grid of Chart.js canvas elements)
5. **Current state card** (partition counts from existing endpoint)
6. **Top waves/users tables** (kept from current design)

Key HTML structure:
```java
sb.append("  <div class=\"tab-panel\" id=\"panel-analytics\">\n");
// Wave SVG divider
sb.append("    <svg viewBox=\"0 0 1200 60\" style=\"width:100%;height:40px;display:block;margin-bottom:8px;\">\n");
sb.append("      <path d=\"M0,30 C200,60 400,0 600,30 C800,60 1000,0 1200,30 L1200,60 L0,60 Z\" fill=\"url(#waveGrad)\"/>\n");
sb.append("      <defs><linearGradient id=\"waveGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n");
sb.append("        <stop offset=\"0%\" stop-color=\"").append(WAVE_PRIMARY).append("\"/>\n");
sb.append("        <stop offset=\"50%\" stop-color=\"").append(WAVE_ACCENT).append("\"/>\n");
sb.append("        <stop offset=\"100%\" stop-color=\"").append(WAVE_LIGHT).append("\"/>\n");
sb.append("      </linearGradient></defs>\n");
sb.append("    </svg>\n");
// Time window pills
sb.append("    <div style=\"display:flex;gap:6px;padding:0 24px 16px;flex-wrap:wrap;\">\n");
for (String w : new String[]{"1h","6h","12h","24h","48h","7d","30d"}) {
  String active = w.equals("24h") ? "background:" + WAVE_PRIMARY + ";color:#fff;" : "background:" + WAVE_BG + ";color:" + WAVE_TEXT + ";";
  sb.append("      <button class=\"analytics-pill\" data-window=\"").append(w).append("\" style=\"border:none;padding:6px 16px;border-radius:20px;cursor:pointer;font-size:13px;font-weight:600;transition:all .2s;").append(active).append("\">").append(w).append("</button>\n");
}
sb.append("    </div>\n");
// Summary cards
sb.append("    <div style=\"padding:0 24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:16px;\">\n");
String[] cardLabels = {"Waves Created", "Blips Created", "Users Registered", "Active Users", "Page Views", "API Views"};
String[] cardIds = {"histWavesCreated", "histBlipsCreated", "histUsersRegistered", "histActiveUsers", "histPageViews", "histApiViews"};
for (int i = 0; i < cardLabels.length; i++) {
  sb.append("      <div style=\"padding:14px;border:1px solid ").append(WAVE_BORDER).append(";border-radius:12px;background:linear-gradient(180deg,#f8fbff 0%,#fff 100%);\">");
  sb.append("<div style=\"font-size:11px;color:").append(WAVE_TEXT_MUTED).append(";text-transform:uppercase;letter-spacing:0.5px;\">").append(cardLabels[i]).append("</div>");
  sb.append("<div id=\"").append(cardIds[i]).append("\" style=\"font-size:28px;font-weight:700;color:").append(WAVE_PRIMARY).append(";\">—</div></div>\n");
}
sb.append("    </div>\n");
// Chart row (2x2)
sb.append("    <div style=\"padding:0 24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(400px,1fr));gap:16px;margin-bottom:16px;\">\n");
String[] chartTitles = {"Waves Created", "Blips Created", "Users Registered", "Active Users"};
String[] chartIds = {"chartWaves", "chartBlips", "chartUsers", "chartActive"};
for (int i = 0; i < chartTitles.length; i++) {
  sb.append("      <div class=\"admin-card\" style=\"padding:16px;\">\n");
  sb.append("        <h3 style=\"margin:0 0 8px;font-size:14px;color:").append(WAVE_TEXT).append(";\">").append(chartTitles[i]).append("</h3>\n");
  sb.append("        <canvas id=\"").append(chartIds[i]).append("\" height=\"200\"></canvas>\n");
  sb.append("      </div>\n");
}
sb.append("    </div>\n");
// Current state card (partition counts — uses existing /analytics/status endpoint)
sb.append("    <div class=\"admin-card\" style=\"margin:0 24px 16px;\">\n");
sb.append("      <div class=\"admin-card-header\"><h2>Current State</h2></div>\n");
sb.append("      <div class=\"admin-table-wrap\"><table class=\"admin-table\" style=\"max-width:500px;\"><tbody>\n");
sb.append("        <tr><td>Total Waves</td><td id=\"analyticsTotalWaves\">—</td></tr>\n");
sb.append("        <tr><td>Public Waves</td><td id=\"analyticsPublicWaves\">—</td></tr>\n");
sb.append("        <tr><td>Private Waves</td><td id=\"analyticsPrivateWaves\">—</td></tr>\n");
sb.append("        <tr><td>Public Blips</td><td id=\"analyticsPublicBlips\">—</td></tr>\n");
sb.append("        <tr><td>Private Blips</td><td id=\"analyticsPrivateBlips\">—</td></tr>\n");
sb.append("      </tbody></table></div>\n");
sb.append("      <div class=\"stats-bar\" id=\"analyticsWarnings\">Loading...</div>\n");
sb.append("    </div>\n");
// Top waves/users tables (same as before)
// ... keep existing top waves and top users tables ...
sb.append("  </div>\n"); // end panel-analytics
```

- [ ] **Step 3: Replace the analytics JavaScript**

Replace the `loadAnalyticsStatus()` function and add Chart.js logic. The new JS:

1. Fetches `/admin/api/analytics/history?window=24h` on tab open
2. Populates summary cards from `totals`
3. Creates 4 Chart.js line charts from `series` data
4. Pill buttons switch the time window and reload
5. Also fetches `/admin/api/analytics/status` for current state card (partition counts)
6. Charts use wave gradient fills (`WAVE_PRIMARY` → `WAVE_ACCENT` → `WAVE_LIGHT`)

Key chart configuration pattern:
```javascript
function createChart(canvasId, label, dataKey, color) {
  var ctx = document.getElementById(canvasId).getContext('2d');
  var gradient = ctx.createLinearGradient(0, 0, 0, 200);
  gradient.addColorStop(0, color + '40');
  gradient.addColorStop(1, color + '05');
  return new Chart(ctx, {
    type: 'line',
    data: {
      labels: [],
      datasets: [{
        label: label,
        data: [],
        borderColor: color,
        backgroundColor: gradient,
        fill: true,
        tension: 0.4,
        pointRadius: 2,
        pointHoverRadius: 5,
        borderWidth: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 8, font: { size: 11 } } },
        y: { beginAtZero: true, grid: { color: '#e2e8f020' }, ticks: { font: { size: 11 } } }
      }
    }
  });
}
```

Charts are created with these colors:
- Waves: `#0077b6` (WAVE_PRIMARY)
- Blips: `#00b4d8` (WAVE_ACCENT)
- Users: `#90e0ef` (WAVE_LIGHT)
- Active: `#0096c7` (between primary and accent)

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/vega/devroot/incubator-wave && sbt compile 2>&1 | tail -5`
Expected: Compilation succeeds.

- [ ] **Step 5: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
git commit -m "feat(analytics): rewrite admin analytics panel with Chart.js and wavy theme"
```

---

### Task 10: Integration Test & Manual Verification

- [ ] **Step 1: Run all existing tests to check for regressions**

Run: `cd /Users/vega/devroot/incubator-wave && sbt test 2>&1 | tail -20`
Expected: All existing tests pass. Fix any failures.

- [ ] **Step 2: Run the server locally**

Run: `cd /Users/vega/devroot/incubator-wave && sbt run 2>&1 | head -50`
Expected: Server starts without errors. Look for `AnalyticsRecorder subscribed to WaveBus` in startup logs.

- [ ] **Step 3: Verify the admin panel loads**

1. Open `http://localhost:9898/admin` in a browser
2. Click the "Analytics" tab
3. Verify:
   - Time window pills appear (1h through 30d)
   - Summary cards show numbers (may be 0 if no activity)
   - Four charts render (may be empty)
   - Current state card loads partition counts
   - Top waves/users tables load
   - No JS errors in browser console

- [ ] **Step 4: Create test activity and verify counters**

1. Create a new wave via the app
2. Add a blip
3. Wait 30 seconds, reload analytics tab
4. Verify "Waves Created" and "Blips Created" increment

- [ ] **Step 5: Final commit with any fixes**

```bash
git add -A
git commit -m "fix(analytics): integration fixes from manual testing"
```

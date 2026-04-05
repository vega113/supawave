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

  @Override public void incrementWavesCreated(long timestampMs) { upsertInc(timestampMs, WAVES_CREATED, 1L); }
  @Override public void incrementBlipsCreated(long timestampMs, int count) { upsertInc(timestampMs, BLIPS_CREATED, count); }
  @Override public void incrementUsersRegistered(long timestampMs) { upsertInc(timestampMs, USERS_REGISTERED, 1L); }

  @Override
  public void recordActiveUser(String userId, long timestampMs) {
    try {
      Date hour = new Date(truncateToHour(timestampMs));
      col.updateOne(Filters.eq(HOUR_FIELD, hour), Updates.addToSet(ACTIVE_USER_IDS, userId), UPSERT);
    } catch (Exception e) {
      LOG.warning("Failed to record active user: " + e.getMessage());
    }
  }

  @Override public void incrementPageViews(long timestampMs) { upsertInc(timestampMs, PAGE_VIEWS, 1L); }
  @Override public void incrementApiViews(long timestampMs) { upsertInc(timestampMs, API_VIEWS, 1L); }

  @Override
  public List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs) {
    Date fromHour = new Date(truncateToHour(fromMs));
    Date toHour = new Date(exclusiveUpperHour(toMs));
    Bson filter = Filters.and(Filters.gte(HOUR_FIELD, fromHour), Filters.lt(HOUR_FIELD, toHour));
    List<HourlyBucket> result = new ArrayList<>();
    for (Document doc : col.find(filter).sort(new Document(HOUR_FIELD, 1))) {
      result.add(docToBucket(doc));
    }
    return Collections.unmodifiableList(result);
  }

  private void upsertInc(long timestampMs, String field, long amount) {
    try {
      Date hour = new Date(truncateToHour(timestampMs));
      col.updateOne(Filters.eq(HOUR_FIELD, hour), Updates.inc(field, amount), UPSERT);
    } catch (Exception e) {
      LOG.warning("Failed to increment " + field + ": " + e.getMessage());
    }
  }

  private static HourlyBucket docToBucket(Document doc) {
    Date hour = doc.getDate(HOUR_FIELD);
    @SuppressWarnings("unchecked")
    List<String> activeList = doc.getList(ACTIVE_USER_IDS, String.class, Collections.emptyList());
    long wavesCreatedVal = numberValue(doc.get(WAVES_CREATED));
    long blipsCreatedVal = numberValue(doc.get(BLIPS_CREATED));
    long usersRegisteredVal = numberValue(doc.get(USERS_REGISTERED));
    long pageViewsVal = numberValue(doc.get(PAGE_VIEWS));
    long apiViewsVal = numberValue(doc.get(API_VIEWS));
    return new HourlyBucket(
        hour != null ? hour.getTime() : 0L,
        wavesCreatedVal,
        blipsCreatedVal,
        usersRegisteredVal,
        new HashSet<>(activeList),
        pageViewsVal,
        apiViewsVal);
  }

  private static long numberValue(Object value) {
    return value instanceof Number ? ((Number) value).longValue() : 0L;
  }

  static long truncateToHour(long timestampMs) {
    return timestampMs - (timestampMs % HOUR_MS);
  }

  private static long exclusiveUpperHour(long timestampMs) {
    long toHour = truncateToHour(timestampMs);
    if (timestampMs == toHour) {
      return timestampMs;
    }
    long nextHour = toHour + HOUR_MS;
    return nextHour < toHour ? Long.MAX_VALUE : nextHour;
  }
}

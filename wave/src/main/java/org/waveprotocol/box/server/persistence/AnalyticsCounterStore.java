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

package org.waveprotocol.box.server.waveserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/**
 * Aggregates admin analytics from accounts, snapshots, delta history, and live public views.
 */
@Singleton
public final class AdminAnalyticsService {
  private static final long DAY_MS = 24L * 60L * 60L * 1000L;
  static final long CACHE_TTL_MS = 30L * 1000L;
  static final long REFRESH_BUDGET_MS = 30L * 1000L;

  private final AccountStore accountStore;
  private final WaveletProvider waveletProvider;
  private final DeltaStore deltaStore;
  private final PublicWaveViewTracker publicWaveViewTracker;
  private final ParticipantId sharedDomainParticipant;
  private final LongSupplier nowSupplier;
  private final LongSupplier refreshTimeSupplier;
  private final long refreshBudgetMs;

  private volatile AnalyticsSnapshot cachedSnapshot;

  @Inject
  public AdminAnalyticsService(
      AccountStore accountStore,
      WaveletProvider waveletProvider,
      DeltaStore deltaStore,
      PublicWaveViewTracker publicWaveViewTracker,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      Config config) {
    this(
        accountStore,
        waveletProvider,
        deltaStore,
        publicWaveViewTracker,
        waveDomain,
        System::currentTimeMillis,
        System::currentTimeMillis,
        config.hasPath("analytics.refresh_budget_ms")
            ? config.getLong("analytics.refresh_budget_ms")
            : REFRESH_BUDGET_MS);
  }

  AdminAnalyticsService(
      AccountStore accountStore,
      WaveletProvider waveletProvider,
      DeltaStore deltaStore,
      PublicWaveViewTracker publicWaveViewTracker,
      String waveDomain,
      long now) {
    this(
        accountStore,
        waveletProvider,
        deltaStore,
        publicWaveViewTracker,
        waveDomain,
        () -> now);
  }

  AdminAnalyticsService(
      AccountStore accountStore,
      WaveletProvider waveletProvider,
      DeltaStore deltaStore,
      PublicWaveViewTracker publicWaveViewTracker,
      String waveDomain,
      LongSupplier nowSupplier) {
    this(
        accountStore,
        waveletProvider,
        deltaStore,
        publicWaveViewTracker,
        waveDomain,
        nowSupplier,
        nowSupplier,
        REFRESH_BUDGET_MS);
  }

  AdminAnalyticsService(
      AccountStore accountStore,
      WaveletProvider waveletProvider,
      DeltaStore deltaStore,
      PublicWaveViewTracker publicWaveViewTracker,
      String waveDomain,
      LongSupplier nowSupplier,
      LongSupplier refreshTimeSupplier,
      long refreshBudgetMs) {
    this.accountStore = accountStore;
    this.waveletProvider = waveletProvider;
    this.deltaStore = deltaStore;
    this.publicWaveViewTracker = publicWaveViewTracker;
    this.sharedDomainParticipant = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
    this.nowSupplier = nowSupplier;
    this.refreshTimeSupplier = refreshTimeSupplier;
    this.refreshBudgetMs = refreshBudgetMs;
  }

  public AnalyticsSnapshot getAnalyticsSnapshot()
      throws PersistenceException, WaveServerException, IOException {
    AnalyticsSnapshot snapshot = cachedSnapshot;
    long now = nowSupplier.getAsLong();
    if (snapshot != null && now - snapshot.getGeneratedAtMs() < CACHE_TTL_MS) {
      return snapshot;
    }

    synchronized (this) {
      snapshot = cachedSnapshot;
      if (snapshot != null && now - snapshot.getGeneratedAtMs() < CACHE_TTL_MS) {
        return snapshot;
      }
      long refreshStartedAt = refreshTimeSupplier.getAsLong();
      try {
        AnalyticsSnapshot fresh = collectSnapshot(now, refreshStartedAt);
        cachedSnapshot = fresh;
        return fresh;
      } catch (RefreshBudgetExceededException e) {
        return fallbackSnapshot(
            snapshot,
            now,
            refreshStartedAt,
            "Analytics refresh exceeded the "
                + refreshBudgetMs
                + "ms budget; serving the last good summary.");
      } catch (PersistenceException | WaveServerException | IOException | RuntimeException e) {
        return fallbackSnapshot(
            snapshot,
            now,
            refreshStartedAt,
            "Analytics refresh failed; serving the last good summary.");
      }
    }
  }

  private AnalyticsSnapshot collectSnapshot(long now, long refreshStartedAt)
      throws PersistenceException, WaveServerException, IOException, RefreshBudgetExceededException {
    MutableSummary summary = new MutableSummary();
    Map<String, MutableTopUser> userStats = new HashMap<>();
    Set<String> writers24h = new HashSet<>();
    Set<String> writers7d = new HashSet<>();
    Set<String> writers30d = new HashSet<>();
    Map<String, MutableTopWave> waves = collectWaveSnapshotMetrics(now, summary, refreshStartedAt);

    checkRefreshBudget(refreshStartedAt);
    collectAccountMetrics(summary, now);
    checkRefreshBudget(refreshStartedAt);
    collectDeltaMetrics(now, summary, userStats, writers24h, writers7d, writers30d, refreshStartedAt);
    summary.writers24h = writers24h.size();
    summary.writers7d = writers7d.size();
    summary.writers30d = writers30d.size();

    List<TopWave> topViewed = topViewedPublicWaves(waves);
    List<TopWave> topParticipated = topParticipatedPublicWaves(waves);
    List<TopUser> topUsers = topUsers(userStats, now);
    LiveViews liveViews =
        new LiveViews(
            publicWaveViewTracker.getTotalPageViews(), publicWaveViewTracker.getTotalApiViews());

    return new AnalyticsSnapshot(
        summary.freeze(),
        topViewed,
        topParticipated,
        topUsers,
        liveViews,
        now,
        Math.max(0L, refreshTimeSupplier.getAsLong() - refreshStartedAt),
        false,
        Collections.emptyList());
  }

  private AnalyticsSnapshot fallbackSnapshot(
      AnalyticsSnapshot snapshot, long now, long refreshStartedAt, String warning) {
    long scanDurationMs = Math.max(0L, refreshTimeSupplier.getAsLong() - refreshStartedAt);
    if (snapshot == null) {
      return warningSnapshot(now, scanDurationMs, warning);
    }
    List<String> warnings = new ArrayList<>(snapshot.getWarnings());
    warnings.add(warning);
    return new AnalyticsSnapshot(
        snapshot.getSummary(),
        snapshot.getTopViewedPublicWaves(),
        snapshot.getTopParticipatedPublicWaves(),
        snapshot.getTopUsers(),
        liveViewsSnapshot(),
        snapshot.getGeneratedAtMs(),
        scanDurationMs,
        true,
        warnings);
  }

  private AnalyticsSnapshot warningSnapshot(long now, long scanDurationMs, String warning) {
    return new AnalyticsSnapshot(
        new MutableSummary().freeze(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        liveViewsSnapshot(),
        now,
        scanDurationMs,
        true,
        List.of(warning));
  }

  private LiveViews liveViewsSnapshot() {
    return new LiveViews(
        publicWaveViewTracker.getTotalPageViews(), publicWaveViewTracker.getTotalApiViews());
  }

  private void collectAccountMetrics(MutableSummary summary, long now) throws PersistenceException {
    for (AccountData account : accountStore.getAllAccounts()) {
      if (account == null || !account.isHuman()) {
        continue;
      }
      HumanAccountData human = account.asHuman();
      if (isWithin(now, human.getLastLoginTime(), DAY_MS)) {
        summary.loggedIn24h++;
      }
      if (isWithin(now, human.getLastLoginTime(), 7L * DAY_MS)) {
        summary.loggedIn7d++;
      }
      if (isWithin(now, human.getLastLoginTime(), 30L * DAY_MS)) {
        summary.loggedIn30d++;
      }
      if (isWithin(now, human.getLastActivityTime(), DAY_MS)) {
        summary.active24h++;
      }
      if (isWithin(now, human.getLastActivityTime(), 7L * DAY_MS)) {
        summary.active7d++;
      }
      if (isWithin(now, human.getLastActivityTime(), 30L * DAY_MS)) {
        summary.active30d++;
      }
    }
  }

  private Map<String, MutableTopWave> collectWaveSnapshotMetrics(
      long now, MutableSummary summary, long refreshStartedAt)
      throws WaveServerException, RefreshBudgetExceededException {
    Map<String, MutableTopWave> waves = new HashMap<>();
    ExceptionalIterator<WaveId, WaveServerException> waveIds = waveletProvider.getWaveIds();
    while (waveIds.hasNext()) {
      checkRefreshBudget(refreshStartedAt);
      WaveId waveId = waveIds.next();
      MutableTopWave wave = waves.computeIfAbsent(waveId.serialise(), key -> new MutableTopWave(waveId));
      summary.totalWaves++;
      WaveletId rootWaveletId = WaveletId.of(waveId.getDomain(), "conv+root");
      accumulateWaveletSnapshot(wave, waveId, rootWaveletId);
      for (WaveletId waveletId : waveletProvider.getWaveletIds(waveId)) {
        if (waveletId.equals(rootWaveletId) || !IdUtil.isConversationalId(waveletId)) {
          continue;
        }
        accumulateWaveletSnapshot(wave, waveId, waveletId);
      }
      wave.views = publicWaveViewTracker.getCombinedViews(waveId);
      if (wave.publicWave) {
        summary.publicWaves++;
        summary.publicBlipsCurrent += wave.blipCount;
      } else {
        summary.privateWaves++;
        summary.privateBlipsCurrent += wave.blipCount;
      }
      if (isWithin(now, wave.createdTime, 7L * DAY_MS)) {
        summary.wavesCreated7d++;
      }
      if (isWithin(now, wave.lastModifiedTime, 7L * DAY_MS)) {
        summary.wavesUpdated7d++;
      }
    }
    return waves;
  }

  private void accumulateWaveletSnapshot(MutableTopWave wave, WaveId waveId, WaveletId waveletId)
      throws WaveServerException {
    CommittedWaveletSnapshot committed = waveletProvider.getSnapshot(WaveletName.of(waveId, waveletId));
    if (committed == null || committed.snapshot == null) {
      return;
    }
    ReadableWaveletData snapshot = committed.snapshot;
    boolean isPublic = WaveletDataUtil.isPublicWavelet(snapshot, sharedDomainParticipant);
    wave.publicWave = wave.publicWave || isPublic;
    wave.lastModifiedTime = Math.max(wave.lastModifiedTime, snapshot.getLastModifiedTime());
    if (wave.createdTime == 0L || snapshot.getCreationTime() < wave.createdTime) {
      wave.createdTime = snapshot.getCreationTime();
    }
    wave.title = chooseTitle(wave.title, waveId, snapshot);
    collectWaveParticipants(wave, snapshot);
    collectWaveBlips(wave, snapshot);
  }

  private void collectWaveParticipants(MutableTopWave wave, ReadableWaveletData snapshot) {
    for (ParticipantId participant : snapshot.getParticipants()) {
      if (participant == null || participant.equals(sharedDomainParticipant)) {
        continue;
      }
      wave.participants.add(participant.getAddress());
    }
  }

  private void collectWaveBlips(MutableTopWave wave, ReadableWaveletData snapshot) {
    for (String docId : snapshot.getDocumentIds()) {
      if (!isCountedBlipId(docId)) {
        continue;
      }
      wave.blipCount++;
      ReadableBlipData blip = snapshot.getDocument(docId);
      if (blip == null) {
        continue;
      }
      if (blip.getAuthor() != null && !blip.getAuthor().equals(sharedDomainParticipant)) {
        wave.contributors.add(blip.getAuthor().getAddress());
      }
      for (ParticipantId contributor : blip.getContributors()) {
        if (contributor == null || contributor.equals(sharedDomainParticipant)) {
          continue;
        }
        wave.contributors.add(contributor.getAddress());
      }
    }
  }

  private void collectDeltaMetrics(
      long now,
      MutableSummary summary,
      Map<String, MutableTopUser> userStats,
      Set<String> writers24h,
      Set<String> writers7d,
      Set<String> writers30d,
      long refreshStartedAt)
      throws PersistenceException, IOException, RefreshBudgetExceededException {
    ExceptionalIterator<WaveId, PersistenceException> waveIds = deltaStore.getWaveIdIterator();
    while (waveIds.hasNext()) {
      checkRefreshBudget(refreshStartedAt);
      WaveId waveId = waveIds.next();
      for (WaveletId waveletId : deltaStore.lookup(waveId)) {
        if (!IdUtil.isConversationalId(waveletId)) {
          continue;
        }
        scanWaveletDeltas(
            waveId,
            waveletId,
            now,
            summary,
            userStats,
            writers24h,
            writers7d,
            writers30d,
            refreshStartedAt);
      }
    }
  }

  private void scanWaveletDeltas(
      WaveId waveId,
      WaveletId waveletId,
      long now,
      MutableSummary summary,
      Map<String, MutableTopUser> userStats,
      Set<String> writers24h,
      Set<String> writers7d,
      Set<String> writers30d,
      long refreshStartedAt)
      throws PersistenceException, IOException, RefreshBudgetExceededException {
    DeltaStore.DeltasAccess deltas = deltaStore.open(WaveletName.of(waveId, waveletId));
    try {
      if (deltas.isEmpty() || deltas.getEndVersion() == null) {
        return;
      }
      Set<String> seenBlips = new HashSet<>();
      long version = 0L;
      long endVersion = deltas.getEndVersion().getVersion();
      while (version < endVersion) {
        checkRefreshBudget(refreshStartedAt);
        WaveletDeltaRecord delta = deltas.getDelta(version);
        if (delta == null) {
          version++;
          continue;
        }
        processDelta(
            waveId,
            delta,
            now,
            seenBlips,
            summary,
            userStats,
            writers24h,
            writers7d,
            writers30d);
        long nextVersion = delta.getResultingVersion().getVersion();
        version = nextVersion > version ? nextVersion : version + 1L;
      }
    } finally {
      deltas.close();
    }
  }

  private void processDelta(
      WaveId waveId,
      WaveletDeltaRecord delta,
      long now,
      Set<String> seenBlips,
      MutableSummary summary,
      Map<String, MutableTopUser> userStats,
      Set<String> writers24h,
      Set<String> writers7d,
      Set<String> writers30d) {
    boolean countedWrite = false;
    ParticipantId author = delta.getAuthor();
    String authorId = author == null ? null : author.getAddress();
    MutableTopUser topUser =
        authorId == null ? null : userStats.computeIfAbsent(authorId, MutableTopUser::new);

    for (WaveletOperation operation : delta.getTransformedDelta()) {
      if (!(operation instanceof WaveletBlipOperation)) {
        continue;
      }
      String blipId = ((WaveletBlipOperation) operation).getBlipId();
      if (!isCountedBlipId(blipId)) {
        continue;
      }
      countedWrite = true;
      if (seenBlips.add(blipId)) {
        summary.totalBlipsCreated++;
        if (topUser != null) {
          topUser.blipsCreated++;
        }
      }
    }

    if (!countedWrite || topUser == null) {
      return;
    }

    topUser.writeCount++;
    topUser.waveIds.add(waveId.serialise());
    topUser.lastWriteTime = Math.max(topUser.lastWriteTime, delta.getApplicationTimestamp());

    if (isWithin(now, delta.getApplicationTimestamp(), DAY_MS)) {
      writers24h.add(authorId);
    }
    if (isWithin(now, delta.getApplicationTimestamp(), 7L * DAY_MS)) {
      writers7d.add(authorId);
    }
    if (isWithin(now, delta.getApplicationTimestamp(), 30L * DAY_MS)) {
      writers30d.add(authorId);
    }
  }

  private List<TopWave> topViewedPublicWaves(Map<String, MutableTopWave> waves) {
    List<TopWave> ranked = new ArrayList<>();
    for (MutableTopWave wave : waves.values()) {
      if (!wave.publicWave || wave.views <= 0L) {
        continue;
      }
      ranked.add(wave.freeze());
    }
    ranked.sort(
        Comparator.comparingLong(TopWave::getViews)
            .reversed()
            .thenComparing(Comparator.comparingInt(TopWave::getContributorCount).reversed())
            .thenComparing(Comparator.comparingLong(TopWave::getLastModifiedTime).reversed()));
    return truncate(ranked, 10);
  }

  private List<TopWave> topParticipatedPublicWaves(Map<String, MutableTopWave> waves) {
    List<TopWave> ranked = new ArrayList<>();
    for (MutableTopWave wave : waves.values()) {
      if (!wave.publicWave) {
        continue;
      }
      ranked.add(wave.freeze());
    }
    ranked.sort(
        Comparator.comparingInt(TopWave::getContributorCount)
            .reversed()
            .thenComparing(Comparator.comparingInt(TopWave::getParticipantCount).reversed())
            .thenComparing(Comparator.comparingLong(TopWave::getViews).reversed())
            .thenComparing(Comparator.comparingLong(TopWave::getLastModifiedTime).reversed()));
    return truncate(ranked, 10);
  }

  private List<TopUser> topUsers(Map<String, MutableTopUser> userStats, long now) {
    List<TopUser> ranked = new ArrayList<>();
    for (MutableTopUser user : userStats.values()) {
      if (!isWithin(now, user.lastWriteTime, 7L * DAY_MS)) {
        continue;
      }
      ranked.add(user.freeze());
    }
    ranked.sort(
        Comparator.comparingLong(TopUser::getWriteCount)
            .reversed()
            .thenComparing(Comparator.comparingLong(TopUser::getBlipsCreated).reversed())
            .thenComparing(Comparator.comparingLong(TopUser::getLastWriteTime).reversed()));
    return truncate(ranked, 10);
  }

  private static <T> List<T> truncate(List<T> items, int limit) {
    if (items.size() <= limit) {
      return Collections.unmodifiableList(items);
    }
    return Collections.unmodifiableList(new ArrayList<>(items.subList(0, limit)));
  }

  private static boolean isCountedBlipId(String docId) {
    return docId != null && IdUtil.isBlipId(docId);
  }

  private void checkRefreshBudget(long refreshStartedAt) throws RefreshBudgetExceededException {
    if (refreshBudgetMs > 0L && refreshTimeSupplier.getAsLong() - refreshStartedAt > refreshBudgetMs) {
      throw new RefreshBudgetExceededException();
    }
  }

  private static boolean isWithin(long now, long timestamp, long windowMs) {
    return timestamp > 0L && now - timestamp <= windowMs;
  }

  private static String chooseTitle(String currentTitle, WaveId waveId, ReadableWaveletData snapshot) {
    if (currentTitle != null && !currentTitle.isEmpty()) {
      return currentTitle;
    }
    for (String docId : snapshot.getDocumentIds()) {
      if (!isCountedBlipId(docId)) {
        continue;
      }
      return docId;
    }
    return waveId.serialise();
  }

  public static final class AnalyticsSnapshot {
    private final Summary summary;
    private final List<TopWave> topViewedPublicWaves;
    private final List<TopWave> topParticipatedPublicWaves;
    private final List<TopUser> topUsers;
    private final LiveViews liveViews;
    private final long generatedAtMs;
    private final long scanDurationMs;
    private final boolean stale;
    private final List<String> warnings;

    AnalyticsSnapshot(
        Summary summary,
        List<TopWave> topViewedPublicWaves,
        List<TopWave> topParticipatedPublicWaves,
        List<TopUser> topUsers,
        LiveViews liveViews,
        long generatedAtMs,
        long scanDurationMs,
        boolean stale,
        List<String> warnings) {
      this.summary = summary;
      this.topViewedPublicWaves = Collections.unmodifiableList(new ArrayList<>(topViewedPublicWaves));
      this.topParticipatedPublicWaves =
          Collections.unmodifiableList(new ArrayList<>(topParticipatedPublicWaves));
      this.topUsers = Collections.unmodifiableList(new ArrayList<>(topUsers));
      this.liveViews = liveViews;
      this.generatedAtMs = generatedAtMs;
      this.scanDurationMs = scanDurationMs;
      this.stale = stale;
      this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public Summary getSummary() {
      return summary;
    }

    public List<TopWave> getTopViewedPublicWaves() {
      return topViewedPublicWaves;
    }

    public List<TopWave> getTopParticipatedPublicWaves() {
      return topParticipatedPublicWaves;
    }

    public List<TopUser> getTopUsers() {
      return topUsers;
    }

    public LiveViews getLiveViews() {
      return liveViews;
    }

    public long getGeneratedAtMs() {
      return generatedAtMs;
    }

    public long getScanDurationMs() {
      return scanDurationMs;
    }

    public boolean isStale() {
      return stale;
    }

    public List<String> getWarnings() {
      return warnings;
    }
  }

  public static final class Summary {
    private final int totalWaves;
    private final int publicWaves;
    private final int privateWaves;
    private final int totalBlipsCreated;
    private final int publicBlipsCurrent;
    private final int privateBlipsCurrent;
    private final int loggedIn24h;
    private final int loggedIn7d;
    private final int loggedIn30d;
    private final int active24h;
    private final int active7d;
    private final int active30d;
    private final int writers24h;
    private final int writers7d;
    private final int writers30d;
    private final int wavesCreated7d;
    private final int wavesUpdated7d;

    Summary(MutableSummary summary) {
      this.totalWaves = summary.totalWaves;
      this.publicWaves = summary.publicWaves;
      this.privateWaves = summary.privateWaves;
      this.totalBlipsCreated = summary.totalBlipsCreated;
      this.publicBlipsCurrent = summary.publicBlipsCurrent;
      this.privateBlipsCurrent = summary.privateBlipsCurrent;
      this.loggedIn24h = summary.loggedIn24h;
      this.loggedIn7d = summary.loggedIn7d;
      this.loggedIn30d = summary.loggedIn30d;
      this.active24h = summary.active24h;
      this.active7d = summary.active7d;
      this.active30d = summary.active30d;
      this.writers24h = summary.writers24h;
      this.writers7d = summary.writers7d;
      this.writers30d = summary.writers30d;
      this.wavesCreated7d = summary.wavesCreated7d;
      this.wavesUpdated7d = summary.wavesUpdated7d;
    }

    public int getTotalWaves() { return totalWaves; }
    public int getPublicWaves() { return publicWaves; }
    public int getPrivateWaves() { return privateWaves; }
    public int getTotalBlipsCreated() { return totalBlipsCreated; }
    public int getPublicBlipsCurrent() { return publicBlipsCurrent; }
    public int getPrivateBlipsCurrent() { return privateBlipsCurrent; }
    public int getLoggedIn24h() { return loggedIn24h; }
    public int getLoggedIn7d() { return loggedIn7d; }
    public int getLoggedIn30d() { return loggedIn30d; }
    public int getActive24h() { return active24h; }
    public int getActive7d() { return active7d; }
    public int getActive30d() { return active30d; }
    public int getWriters24h() { return writers24h; }
    public int getWriters7d() { return writers7d; }
    public int getWriters30d() { return writers30d; }
    public int getWavesCreated7d() { return wavesCreated7d; }
    public int getWavesUpdated7d() { return wavesUpdated7d; }
  }

  public static final class TopWave {
    private final String waveId;
    private final String title;
    private final long views;
    private final int participantCount;
    private final int contributorCount;
    private final int blipCount;
    private final long lastModifiedTime;

    TopWave(
        String waveId,
        String title,
        long views,
        int participantCount,
        int contributorCount,
        int blipCount,
        long lastModifiedTime) {
      this.waveId = waveId;
      this.title = title;
      this.views = views;
      this.participantCount = participantCount;
      this.contributorCount = contributorCount;
      this.blipCount = blipCount;
      this.lastModifiedTime = lastModifiedTime;
    }

    public String getWaveId() { return waveId; }
    public String getTitle() { return title; }
    public long getViews() { return views; }
    public int getParticipantCount() { return participantCount; }
    public int getContributorCount() { return contributorCount; }
    public int getBlipCount() { return blipCount; }
    public long getLastModifiedTime() { return lastModifiedTime; }
  }

  public static final class TopUser {
    private final String userId;
    private final long writeCount;
    private final long blipsCreated;
    private final int wavesContributed;
    private final long lastWriteTime;

    TopUser(String userId, long writeCount, long blipsCreated, int wavesContributed, long lastWriteTime) {
      this.userId = userId;
      this.writeCount = writeCount;
      this.blipsCreated = blipsCreated;
      this.wavesContributed = wavesContributed;
      this.lastWriteTime = lastWriteTime;
    }

    public String getUserId() { return userId; }
    public long getWriteCount() { return writeCount; }
    public long getBlipsCreated() { return blipsCreated; }
    public int getWavesContributed() { return wavesContributed; }
    public long getLastWriteTime() { return lastWriteTime; }
  }

  public static final class LiveViews {
    private final long pageViewsSinceStart;
    private final long apiViewsSinceStart;

    LiveViews(long pageViewsSinceStart, long apiViewsSinceStart) {
      this.pageViewsSinceStart = pageViewsSinceStart;
      this.apiViewsSinceStart = apiViewsSinceStart;
    }

    public long getPageViewsSinceStart() { return pageViewsSinceStart; }
    public long getApiViewsSinceStart() { return apiViewsSinceStart; }
  }

  private static final class MutableSummary {
    private int totalWaves;
    private int publicWaves;
    private int privateWaves;
    private int totalBlipsCreated;
    private int publicBlipsCurrent;
    private int privateBlipsCurrent;
    private int loggedIn24h;
    private int loggedIn7d;
    private int loggedIn30d;
    private int active24h;
    private int active7d;
    private int active30d;
    private int writers24h;
    private int writers7d;
    private int writers30d;
    private int wavesCreated7d;
    private int wavesUpdated7d;

    Summary freeze() {
      return new Summary(this);
    }
  }

  private static final class MutableTopWave {
    private final WaveId waveId;
    private boolean publicWave;
    private long views;
    private long createdTime;
    private long lastModifiedTime;
    private int blipCount;
    private String title = "";
    private final Set<String> participants = new LinkedHashSet<>();
    private final Set<String> contributors = new LinkedHashSet<>();

    MutableTopWave(WaveId waveId) {
      this.waveId = waveId;
    }

    TopWave freeze() {
      return new TopWave(
          waveId.serialise(),
          title == null || title.isEmpty() ? waveId.serialise() : title,
          views,
          participants.size(),
          contributors.size(),
          blipCount,
          lastModifiedTime);
    }
  }

  private static final class MutableTopUser {
    private final String userId;
    private long writeCount;
    private long blipsCreated;
    private long lastWriteTime;
    private final Set<String> waveIds = new HashSet<>();

    MutableTopUser(String userId) {
      this.userId = userId;
    }

    TopUser freeze() {
      return new TopUser(userId, writeCount, blipsCreated, waveIds.size(), lastWriteTime);
    }
  }

  private static final class RefreshBudgetExceededException extends Exception {
    private static final long serialVersionUID = 1L;
  }
}

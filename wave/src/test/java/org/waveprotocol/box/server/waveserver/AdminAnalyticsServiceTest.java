package org.waveprotocol.box.server.waveserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.Test;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;

public final class AdminAnalyticsServiceTest {
  private static final String DOMAIN = "example.com";
  private static final ParticipantId SHARED_DOMAIN = ParticipantId.ofUnsafe("@" + DOMAIN);
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@" + DOMAIN);
  private static final ParticipantId ALICE = ParticipantId.ofUnsafe("alice@" + DOMAIN);
  private static final ParticipantId BOB = ParticipantId.ofUnsafe("bob@" + DOMAIN);

  @Test
  public void collectAnalyticsSummarizesAccountsWavesAndDeltaHistory() throws Exception {
    long now = 1_710_000_000_000L;

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);

    HumanAccountData owner = humanAccount(OWNER, "owner@example.com", now - days(60), now - hours(6), now - hours(2));
    HumanAccountData alice = humanAccount(ALICE, "alice@example.com", now - days(20), now - hours(8), now - hours(1));
    HumanAccountData bob = humanAccount(BOB, "bob@example.com", now - days(40), now - days(8), now - days(2));
    when(accountStore.getAllAccounts()).thenReturn(List.of(owner, alice, bob));

    WaveId publicWaveId = WaveId.of(DOMAIN, "w+public");
    WaveId privateWaveId = WaveId.of(DOMAIN, "w+private");
    WaveletId convRoot = WaveletId.of(DOMAIN, "conv+root");
    WaveletName publicWaveletName = WaveletName.of(publicWaveId, convRoot);
    WaveletName privateWaveletName = WaveletName.of(privateWaveId, convRoot);

    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(Arrays.asList(publicWaveId, privateWaveId).iterator()));
    when(waveletProvider.getWaveletIds(publicWaveId)).thenReturn(ImmutableSet.of(convRoot));
    when(waveletProvider.getWaveletIds(privateWaveId)).thenReturn(ImmutableSet.of(convRoot));
    when(waveletProvider.getSnapshot(publicWaveletName))
        .thenReturn(new CommittedWaveletSnapshot(publicWavelet(publicWaveletName, now), HashedVersion.unsigned(2)));
    when(waveletProvider.getSnapshot(privateWaveletName))
        .thenReturn(new CommittedWaveletSnapshot(privateWavelet(privateWaveletName, now), HashedVersion.unsigned(1)));

    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(Arrays.asList(publicWaveId, privateWaveId).iterator()));
    when(deltaStore.lookup(publicWaveId)).thenReturn(ImmutableSet.of(convRoot));
    when(deltaStore.lookup(privateWaveId)).thenReturn(ImmutableSet.of(convRoot));

    DeltaStore.DeltasAccess publicDeltas = mock(DeltaStore.DeltasAccess.class);
    when(publicDeltas.getEndVersion()).thenReturn(HashedVersion.unsigned(3));
    when(publicDeltas.getDelta(0)).thenReturn(blipDelta(OWNER, 0, "b+public-root", now - hours(5)));
    when(publicDeltas.getDelta(1)).thenReturn(blipDelta(ALICE, 1, "b+public-reply", now - hours(1)));
    when(publicDeltas.getDelta(2)).thenReturn(blipDelta(ALICE, 2, "b+public-root", now - minutes(30)));
    when(deltaStore.open(publicWaveletName)).thenReturn(publicDeltas);

    DeltaStore.DeltasAccess privateDeltas = mock(DeltaStore.DeltasAccess.class);
    when(privateDeltas.getEndVersion()).thenReturn(HashedVersion.unsigned(1));
    when(privateDeltas.getDelta(0)).thenReturn(blipDelta(BOB, 0, "b+private-root", now - days(9)));
    when(deltaStore.open(privateWaveletName)).thenReturn(privateDeltas);

    PublicWaveViewTracker viewTracker = new PublicWaveViewTracker();
    viewTracker.recordPageView(publicWaveId);
    viewTracker.recordPageView(publicWaveId);
    viewTracker.recordApiView(publicWaveId);

    AdminAnalyticsService service =
        new AdminAnalyticsService(accountStore, waveletProvider, deltaStore, viewTracker, DOMAIN, now);

    AdminAnalyticsService.AnalyticsSnapshot snapshot = service.getAnalyticsSnapshot();

    assertEquals(2, snapshot.getSummary().getTotalWaves());
    assertEquals(1, snapshot.getSummary().getPublicWaves());
    assertEquals(1, snapshot.getSummary().getPrivateWaves());
    assertEquals(3, snapshot.getSummary().getTotalBlipsCreated());
    assertEquals(2, snapshot.getSummary().getPublicBlipsCurrent());
    assertEquals(1, snapshot.getSummary().getPrivateBlipsCurrent());
    assertEquals(2, snapshot.getSummary().getLoggedIn7d());
    assertEquals(3, snapshot.getSummary().getActive7d());
    assertEquals(2, snapshot.getSummary().getWriters24h());
    assertEquals(2, snapshot.getSummary().getActive24h());
    assertEquals(2, snapshot.getSummary().getWriters7d());
    assertEquals(1, snapshot.getTopViewedPublicWaves().size());
    assertEquals(publicWaveId.serialise(), snapshot.getTopViewedPublicWaves().get(0).getWaveId());
    assertEquals(3L, snapshot.getTopViewedPublicWaves().get(0).getViews());
    assertEquals(2, snapshot.getTopUsers().size());
    assertEquals(ALICE.getAddress(), snapshot.getTopUsers().get(0).getUserId());
    assertEquals(2L, snapshot.getTopUsers().get(0).getWriteCount());
    assertFalse(snapshot.getTopParticipatedPublicWaves().isEmpty());
    assertTrue(snapshot.getLiveViews().getPageViewsSinceStart() >= 2L);
    assertTrue(snapshot.getLiveViews().getApiViewsSinceStart() >= 1L);
  }

  @Test
  public void collectAnalyticsReturnsStableDefaultsForEmptyInstall() throws Exception {
    long now = 1_710_000_000_000L;

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);
    when(accountStore.getAllAccounts()).thenReturn(List.<AccountData>of());
    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));
    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));

    AdminAnalyticsService service =
        new AdminAnalyticsService(
            accountStore, waveletProvider, deltaStore, new PublicWaveViewTracker(), DOMAIN, now);

    AdminAnalyticsService.AnalyticsSnapshot snapshot = service.getAnalyticsSnapshot();

    assertEquals(0, snapshot.getSummary().getTotalWaves());
    assertEquals(0, snapshot.getSummary().getPublicWaves());
    assertEquals(0, snapshot.getSummary().getPrivateWaves());
    assertEquals(0, snapshot.getSummary().getTotalBlipsCreated());
    assertEquals(0, snapshot.getSummary().getPublicBlipsCurrent());
    assertEquals(0, snapshot.getSummary().getPrivateBlipsCurrent());
    assertEquals(0, snapshot.getSummary().getLoggedIn24h());
    assertEquals(0, snapshot.getSummary().getActive30d());
    assertEquals(0, snapshot.getSummary().getWriters7d());
    assertTrue(snapshot.getTopViewedPublicWaves().isEmpty());
    assertTrue(snapshot.getTopParticipatedPublicWaves().isEmpty());
    assertTrue(snapshot.getTopUsers().isEmpty());
    assertEquals(0L, snapshot.getLiveViews().getPageViewsSinceStart());
    assertEquals(0L, snapshot.getLiveViews().getApiViewsSinceStart());
    assertFalse(snapshot.isStale());
    assertTrue(snapshot.getWarnings().isEmpty());
  }

  @Test
  public void getAnalyticsSnapshotReusesCachedSnapshotInsideTtlWindow() throws Exception {
    long now = 1_710_000_000_000L;
    AtomicLong nowRef = new AtomicLong(now);

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);
    when(accountStore.getAllAccounts()).thenReturn(List.<AccountData>of());
    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));
    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));

    AdminAnalyticsService service =
        new AdminAnalyticsService(
            accountStore,
            waveletProvider,
            deltaStore,
            new PublicWaveViewTracker(),
            DOMAIN,
            nowRef::get);

    AdminAnalyticsService.AnalyticsSnapshot first = service.getAnalyticsSnapshot();
    nowRef.addAndGet(AdminAnalyticsService.CACHE_TTL_MS - 1L);
    AdminAnalyticsService.AnalyticsSnapshot second = service.getAnalyticsSnapshot();

    assertSame(first, second);
    verify(accountStore, times(1)).getAllAccounts();
  }

  @Test
  public void getAnalyticsSnapshotReturnsStaleLastGoodSnapshotWhenRefreshFails() throws Exception {
    long now = 1_710_000_000_000L;
    AtomicLong nowRef = new AtomicLong(now);

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);
    when(accountStore.getAllAccounts())
        .thenReturn(List.<AccountData>of())
        .thenThrow(new PersistenceException("boom"));
    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));
    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));

    AdminAnalyticsService service =
        new AdminAnalyticsService(
            accountStore,
            waveletProvider,
            deltaStore,
            new PublicWaveViewTracker(),
            DOMAIN,
            nowRef::get,
            nowRef::get,
            5_000L);

    AdminAnalyticsService.AnalyticsSnapshot first = service.getAnalyticsSnapshot();

    nowRef.addAndGet(AdminAnalyticsService.CACHE_TTL_MS + 1L);
    AdminAnalyticsService.AnalyticsSnapshot second = service.getAnalyticsSnapshot();

    assertTrue(second.isStale());
    assertFalse(second.getWarnings().isEmpty());
    assertEquals(first.getSummary().getTotalWaves(), second.getSummary().getTotalWaves());
    assertEquals(first.getGeneratedAtMs(), second.getGeneratedAtMs());
  }

  @Test
  public void getAnalyticsSnapshotReturnsWarningSnapshotWhenRefreshBudgetExceeded() throws Exception {
    long now = 1_710_000_000_000L;

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);
    when(accountStore.getAllAccounts()).thenReturn(List.<AccountData>of());
    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));
    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.<WaveId>of().iterator()));

    AdminAnalyticsService service =
        new AdminAnalyticsService(
            accountStore,
            waveletProvider,
            deltaStore,
            new PublicWaveViewTracker(),
            DOMAIN,
            () -> now,
            sequence(0L, 6_001L, 6_001L),
            5_000L);

    AdminAnalyticsService.AnalyticsSnapshot snapshot = service.getAnalyticsSnapshot();

    assertTrue(snapshot.isStale());
    assertFalse(snapshot.getWarnings().isEmpty());
    assertEquals(0, snapshot.getSummary().getTotalWaves());
    assertEquals(0, snapshot.getSummary().getTotalBlipsCreated());
  }

  @Test
  public void collectAnalyticsIgnoresNonBlipDocuments() throws Exception {
    long now = 1_710_000_000_000L;

    AccountStore accountStore = mock(AccountStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    DeltaStore deltaStore = mock(DeltaStore.class);
    when(accountStore.getAllAccounts()).thenReturn(List.<AccountData>of());

    WaveId publicWaveId = WaveId.of(DOMAIN, "w+public");
    WaveletId convRoot = WaveletId.of(DOMAIN, "conv+root");
    WaveletName publicWaveletName = WaveletName.of(publicWaveId, convRoot);

    WaveletData publicWavelet = publicWavelet(publicWaveletName, now);
    publicWavelet.createDocument(
        "m/attachment/123",
        OWNER,
        List.of(OWNER),
        new DocInitializationBuilder()
            .elementStart("body", Attributes.EMPTY_MAP)
            .characters("metadata")
            .elementEnd()
            .build(),
        now - minutes(20),
        3L);

    when(waveletProvider.getWaveIds())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.of(publicWaveId).iterator()));
    when(waveletProvider.getWaveletIds(publicWaveId)).thenReturn(ImmutableSet.of(convRoot));
    when(waveletProvider.getSnapshot(publicWaveletName))
        .thenReturn(new CommittedWaveletSnapshot(publicWavelet, HashedVersion.unsigned(2)));

    when(deltaStore.getWaveIdIterator())
        .thenReturn(ExceptionalIterator.FromIterator.create(List.of(publicWaveId).iterator()));
    when(deltaStore.lookup(publicWaveId)).thenReturn(ImmutableSet.of(convRoot));
    DeltaStore.DeltasAccess publicDeltas = mock(DeltaStore.DeltasAccess.class);
    when(publicDeltas.getEndVersion()).thenReturn(HashedVersion.unsigned(2));
    when(publicDeltas.getDelta(0)).thenReturn(blipDelta(OWNER, 0, "b+public-root", now - hours(1)));
    when(publicDeltas.getDelta(1)).thenReturn(blipDelta(OWNER, 1, "m/attachment/123", now - minutes(20)));
    when(deltaStore.open(publicWaveletName)).thenReturn(publicDeltas);

    AdminAnalyticsService service =
        new AdminAnalyticsService(
            accountStore, waveletProvider, deltaStore, new PublicWaveViewTracker(), DOMAIN, now);

    AdminAnalyticsService.AnalyticsSnapshot snapshot = service.getAnalyticsSnapshot();

    assertEquals(1, snapshot.getSummary().getTotalWaves());
    assertEquals(1, snapshot.getSummary().getTotalBlipsCreated());
    assertEquals(1, snapshot.getSummary().getPublicWaves());
    assertEquals(2, snapshot.getSummary().getPublicBlipsCurrent());
  }

  private static HumanAccountData humanAccount(
      ParticipantId id, String email, long registrationTime, long lastLoginTime, long lastActivityTime) {
    HumanAccountData account = new HumanAccountDataImpl(id);
    account.setEmail(email);
    account.setRegistrationTime(registrationTime);
    account.setLastLoginTime(lastLoginTime);
    account.setLastActivityTime(lastActivityTime);
    return account;
  }

  private static WaveletData publicWavelet(WaveletName name, long now) {
    WaveletData wavelet = baseWavelet(name, OWNER, now - days(5));
    wavelet.addParticipant(SHARED_DOMAIN);
    wavelet.addParticipant(ALICE);
    wavelet.createDocument(
        "b+public-root",
        OWNER,
        List.of(OWNER, ALICE),
        new DocInitializationBuilder()
            .elementStart("body", Attributes.EMPTY_MAP)
            .characters("Public root")
            .elementEnd()
            .build(),
        now - hours(5),
        1L);
    wavelet.createDocument(
        "b+public-reply",
        ALICE,
        List.of(ALICE),
        new DocInitializationBuilder()
            .elementStart("body", Attributes.EMPTY_MAP)
            .characters("Public reply")
            .elementEnd()
            .build(),
        now - hours(1),
        2L);
    return wavelet;
  }

  private static WaveletData privateWavelet(WaveletName name, long now) {
    WaveletData wavelet = baseWavelet(name, OWNER, now - days(10));
    wavelet.addParticipant(BOB);
    wavelet.createDocument(
        "b+private-root",
        BOB,
        List.of(BOB),
        new DocInitializationBuilder()
            .elementStart("body", Attributes.EMPTY_MAP)
            .characters("Private root")
            .elementEnd()
            .build(),
        now - days(2),
        1L);
    return wavelet;
  }

  private static WaveletData baseWavelet(WaveletName name, ParticipantId creator, long creationTime) {
    WaveletData wavelet =
        WaveletDataUtil.createEmptyWavelet(name, creator, HashedVersion.unsigned(0), creationTime);
    wavelet.addParticipant(creator);
    return wavelet;
  }

  private static WaveletDeltaRecord blipDelta(
      ParticipantId author, long appliedAtVersion, String blipId, long applicationTimestamp) {
    WaveletOperation op =
        new WaveletBlipOperation(
            blipId,
            new BlipContentOperation(
                new WaveletOperationContext(author, applicationTimestamp, 1),
                new DocOpBuilder().characters("x").build()));
    HashedVersion startVersion = HashedVersion.unsigned(appliedAtVersion);
    HashedVersion endVersion = HashedVersion.unsigned(appliedAtVersion + 1);
    return new WaveletDeltaRecord(
        startVersion,
        null,
        TransformedWaveletDelta.cloneOperations(author, endVersion, applicationTimestamp, List.of(op)));
  }

  private static long minutes(long value) {
    return value * 60L * 1000L;
  }

  private static long hours(long value) {
    return value * 60L * 60L * 1000L;
  }

  private static long days(long value) {
    return value * 24L * 60L * 60L * 1000L;
  }

  private static LongSupplier sequence(long... values) {
    AtomicInteger index = new AtomicInteger(0);
    return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
  }
}

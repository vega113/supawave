package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.Test;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.waveserver.ReindexService;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdater;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class AdminServletTest {
  private static final ParticipantId ADMIN_ID = ParticipantId.ofUnsafe("owner@example.com");

  @Test
  public void opsStatusUsesFeatureFlagStateAndExposesUpdaterMetrics() throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    featureFlagStore.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            false,
            Collections.singletonMap(ADMIN_ID.getAddress(), true)));

    SearchWaveletUpdater searchWaveletUpdater = mock(SearchWaveletUpdater.class);
    when(searchWaveletUpdater.isPublicBatchingEnabled()).thenReturn(false);
    when(searchWaveletUpdater.getPublicBatchMs()).thenReturn(0L);
    when(searchWaveletUpdater.getPublicFanoutThreshold()).thenReturn(3);
    when(searchWaveletUpdater.getHighParticipantThreshold()).thenReturn(9);
    when(searchWaveletUpdater.getActiveSubscriptionCount()).thenReturn(7);
    when(searchWaveletUpdater.getIndexedWaveCount()).thenReturn(11);
    when(searchWaveletUpdater.getWaveUpdateCount()).thenReturn(13L);
    when(searchWaveletUpdater.getLowLatencyWaveUpdateCount()).thenReturn(8L);
    when(searchWaveletUpdater.getSlowPathWaveUpdateCount()).thenReturn(5L);
    when(searchWaveletUpdater.getSlowPathFlushCount()).thenReturn(3L);
    when(searchWaveletUpdater.getSlowPathQueuedSubscriptionCount()).thenReturn(42L);
    when(searchWaveletUpdater.getSearchRecomputeCount()).thenReturn(19L);

    JSONObject json = invokeOpsStatus(featureFlagStore, mockProvider(searchWaveletUpdater));
    JSONObject otSearch = json.getJSONObject("otSearch");

    assertFalse(otSearch.getBoolean("configEnabled"));
    assertTrue(otSearch.getBoolean("enabled"));
    assertFalse(otSearch.getBoolean("publicBatchingEnabled"));
    assertEquals(0L, otSearch.getLong("publicBatchMs"));
    assertEquals(3, otSearch.getInt("publicFanoutThreshold"));
    assertEquals(9, otSearch.getInt("highParticipantThreshold"));
    assertEquals(7, otSearch.getInt("activeSubscriptions"));
    assertEquals(11, otSearch.getInt("indexedWaves"));
    assertEquals(13L, otSearch.getLong("waveUpdateCount"));
    assertEquals(19L, otSearch.getLong("searchRecomputeCount"));
  }

  @Test
  public void opsStatusDoesNotInstantiateUpdaterWhenFeatureFlagIsOff() throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider = mock(Provider.class);

    JSONObject json = invokeOpsStatus(featureFlagStore, searchWaveletUpdaterProvider);
    JSONObject otSearch = json.getJSONObject("otSearch");

    assertFalse(otSearch.getBoolean("configEnabled"));
    assertFalse(otSearch.getBoolean("enabled"));
    assertTrue(otSearch.getBoolean("publicBatchingEnabled"));
    assertEquals(0L, otSearch.getLong("publicBatchMs"));
    assertEquals(1, otSearch.getInt("publicFanoutThreshold"));
    assertEquals(1, otSearch.getInt("highParticipantThreshold"));
    assertEquals(0L, otSearch.getLong("activeSubscriptions"));
    verify(searchWaveletUpdaterProvider, never()).get();
  }

  @Test
  public void opsStatusIncludesLuceneQueryCountWithoutAverageWhenNoQueriesRecorded()
      throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    JSONObject json =
        invokeOpsStatus(featureFlagStore, mockProvider(mock(SearchWaveletUpdater.class)), 0L, 0.0);

    JSONObject searchIndex = json.getJSONObject("searchIndex");
    assertEquals(0L, searchIndex.getLong("queryCount"));
    assertFalse(searchIndex.has("queryAvgMs"));
  }

  @Test
  public void opsStatusSerializesReindexStatsWithLocaleIndependentDecimalSeparator()
      throws Exception {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
      ReindexService reindexService = new ReindexService(mock(Lucene9WaveIndexerImpl.class));
      reindexService.recordStartupReindex(
          new Lucene9WaveIndexerImpl.ReindexStats(
              5, 0, 61L, 61_500_000L, 9_000_000L, 15_000_000L));

      JSONObject json =
          invokeOpsStatus(featureFlagStore, mockProvider(mock(SearchWaveletUpdater.class)), reindexService);

      JSONObject lastReindex = json.getJSONObject("lastReindex");
      assertEquals(5, lastReindex.getInt("waveCount"));
      assertEquals(12.3, lastReindex.getDouble("avgMsPerWave"), 0.0001);
    } finally {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  public void opsStatusSerializesLuceneQueryStatsWithLocaleIndependentDecimalSeparator()
      throws Exception {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
      JSONObject json =
          invokeOpsStatus(
              featureFlagStore, mockProvider(mock(SearchWaveletUpdater.class)), 5L, 12.3);

      JSONObject searchIndex = json.getJSONObject("searchIndex");
      assertEquals(5L, searchIndex.getLong("queryCount"));
      assertEquals(12.3, searchIndex.getDouble("queryAvgMs"), 0.0001);
    } finally {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  public void removedAnalyticsStatusRouteReturnsNotFoundForAdmin() throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    JSONObject json =
        invokeJsonApi(
            "/api/analytics/status", ownerAccount(ADMIN_ID), featureFlagStore);

    assertEquals("Not found", json.getString("error"));
  }

  @Test
  public void removedAnalyticsHistoryRouteReturnsNotFoundForAdmin() throws Exception {
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    JSONObject json =
        invokeJsonApi(
            "/api/analytics/history", ownerAccount(ADMIN_ID), featureFlagStore);

    assertEquals("Not found", json.getString("error"));
  }

  private static JSONObject invokeOpsStatus(
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider) throws Exception {
    return invokeOpsStatus(featureFlagStore, searchWaveletUpdaterProvider, 0L, 0.0);
  }

  private static JSONObject invokeOpsStatus(
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider,
      ReindexService reindexService) throws Exception {
    return invokeJsonApi(
        "/api/ops/status",
        ownerAccount(ADMIN_ID),
        featureFlagStore,
        searchWaveletUpdaterProvider,
        reindexService,
        0L,
        0.0);
  }

  private static JSONObject invokeOpsStatus(
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider,
      long queryCount,
      double queryAvgMs) throws Exception {
    return invokeJsonApi(
        "/api/ops/status",
        ownerAccount(ADMIN_ID),
        featureFlagStore,
        searchWaveletUpdaterProvider,
        queryCount,
        queryAvgMs);
  }

  private static JSONObject invokeJsonApi(
      String pathInfo,
      HumanAccountData account,
      FeatureFlagStore featureFlagStore) throws Exception {
    return invokeJsonApi(
        pathInfo,
        account,
        featureFlagStore,
        mockProvider(mock(SearchWaveletUpdater.class)),
        0L,
        0.0);
  }

  private static JSONObject invokeJsonApi(
      String pathInfo,
      HumanAccountData account,
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider,
      long queryCount,
      double queryAvgMs) throws Exception {
    return invokeJsonApi(
        pathInfo,
        account,
        featureFlagStore,
        searchWaveletUpdaterProvider,
        new ReindexService(null),
        queryCount,
        queryAvgMs);
  }

  private static JSONObject invokeJsonApi(
      String pathInfo,
      HumanAccountData account,
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider,
      ReindexService reindexService,
      long queryCount,
      double queryAvgMs) throws Exception {
    AccountStore accountStore = mock(AccountStore.class);
    SessionManager sessionManager = mock(SessionManager.class);
    ContactMessageStore contactMessageStore = mock(ContactMessageStore.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    Lucene9WaveIndexerImpl lucene9Indexer = mock(Lucene9WaveIndexerImpl.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    StringWriter body = new StringWriter();
    PrintWriter writer = new PrintWriter(body);
    FeatureFlagService featureFlagService = new FeatureFlagService(featureFlagStore);
    Config config =
        ConfigFactory.parseString(
            "core.search_type = \"lucene9\"\n"
                + "core.public_url = \"https://wave.example.test\"\n"
                + "search.ot_search_enabled = false\n"
                + "search.ot_search_public_batching_enabled = true\n"
                + "search.ot_search_public_batch_ms = -15000\n"
                + "search.ot_search_public_fanout_threshold = 0\n"
                + "search.ot_search_high_participant_threshold = 0");

    when(lucene9Indexer.getLastRebuildWaveCount()).thenReturn(0);
    when(lucene9Indexer.getIndexedDocCount()).thenReturn(0);
    when(lucene9Indexer.getIncrementalIndexCount()).thenReturn(0L);
    when(lucene9Indexer.getQueryCount()).thenReturn(queryCount);
    if (queryCount > 0) {
      when(lucene9Indexer.getQueryAvgMs()).thenReturn(queryAvgMs);
    }
    when(sessionManager.getLoggedInUser(any())).thenReturn(account.getId());
    when(accountStore.getAccount(account.getId())).thenReturn(account);
    when(request.getPathInfo()).thenReturn(pathInfo);
    when(request.getSession(false)).thenReturn(session);
    when(response.getWriter()).thenReturn(writer);

    AdminServlet servlet =
        new AdminServlet(
            accountStore,
            sessionManager,
            contactMessageStore,
            mock(org.waveprotocol.box.server.mail.MailProvider.class),
            "wave.example.test",
            reindexService,
            config,
            waveletProvider,
            featureFlagService,
            featureFlagStore,
            lucene9Indexer,
            searchWaveletUpdaterProvider);
    try {
      servlet.doGet(request, response);
      writer.flush();
    } finally {
      featureFlagService.shutdown();
    }
    return new JSONObject(body.toString());
  }

  @SuppressWarnings("unchecked")
  private static Provider<SearchWaveletUpdater> mockProvider(SearchWaveletUpdater updater) {
    Provider<SearchWaveletUpdater> provider = mock(Provider.class);
    when(provider.get()).thenReturn(updater);
    return provider;
  }

  private static HumanAccountData ownerAccount(ParticipantId id) {
    HumanAccountData account = new HumanAccountDataImpl(id);
    account.setRole(HumanAccountData.ROLE_OWNER);
    return account;
  }

}

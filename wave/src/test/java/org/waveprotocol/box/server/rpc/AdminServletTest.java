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
    when(searchWaveletUpdater.isPublicBatchingEnabled()).thenReturn(true);
    when(searchWaveletUpdater.getPublicBatchMs()).thenReturn(15_000L);
    when(searchWaveletUpdater.getPublicFanoutThreshold()).thenReturn(25);
    when(searchWaveletUpdater.getHighParticipantThreshold()).thenReturn(25);
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
    assertTrue(otSearch.getBoolean("publicBatchingEnabled"));
    assertEquals(15_000L, otSearch.getLong("publicBatchMs"));
    assertEquals(25, otSearch.getInt("publicFanoutThreshold"));
    assertEquals(25, otSearch.getInt("highParticipantThreshold"));
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

  private static JSONObject invokeOpsStatus(
      FeatureFlagStore featureFlagStore,
      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider) throws Exception {
    AccountStore accountStore = mock(AccountStore.class);
    SessionManager sessionManager = mock(SessionManager.class);
    ContactMessageStore contactMessageStore = mock(ContactMessageStore.class);
    ReindexService reindexService = new ReindexService(null);
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
    HumanAccountData admin = new HumanAccountDataImpl(ADMIN_ID);
    admin.setRole(HumanAccountData.ROLE_OWNER);

    when(lucene9Indexer.getLastRebuildWaveCount()).thenReturn(0);
    when(lucene9Indexer.getIndexedDocCount()).thenReturn(0);
    when(lucene9Indexer.getIncrementalIndexCount()).thenReturn(0L);
    when(sessionManager.getLoggedInUser(any())).thenReturn(ADMIN_ID);
    when(accountStore.getAccount(ADMIN_ID)).thenReturn(admin);
    when(request.getPathInfo()).thenReturn("/api/ops/status");
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
}

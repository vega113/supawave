/**
 * Jakarta override of ServerMain removing compile-time references to javax-bound
 * robot servlets/modules. Runtime behavior mirrors the main variant with Jakarta
 * gating already in place.
 */
package org.waveprotocol.box.server;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.box.server.frontend.ClientFrontend;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.dev.ClientApplierStatsJakartaServlet;
import org.waveprotocol.box.server.mail.MailModule;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.rpc.*;
import org.waveprotocol.box.server.robots.ProfileFetcherModule;
import org.waveprotocol.box.server.robots.JakartaRobotApiBindingsModule;
import org.waveprotocol.box.server.robots.RobotApiServlet;
import org.waveprotocol.box.server.robots.RobotDashboardServlet;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.robots.agent.registration.RegistrationRobot;
import org.waveprotocol.box.server.robots.dataapi.DataApiServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiTokenServlet;
import org.waveprotocol.box.server.contact.ContactsRecorder;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.waveserver.*;
import org.waveprotocol.box.server.waveserver.ReindexService;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletSnapshotPublisher;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletManager;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerMain {
  private static final Log LOG = Log.get(ServerMain.class);
  private static final long SEARCH_WAVELET_UPDATER_RETRY_SECONDS = 30L;

  private static void printStackTraceLite(Throwable t) {
    org.waveprotocol.box.server.util.StackTraces.printStackTraceLite(t, System.err);
  }

  public static void main(String... args) {
    try {
      java.util.logging.LogManager.getLogManager().reset();
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
    } catch (Throwable ignore) {}
    try {
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
        try { printStackTraceLite(throwable); } catch (Throwable ignore) {}
      });
      LOG.info(structuredLoggingStatusMessage());

      Module coreSettings = new AbstractModule() {
        @Override protected void configure() {
          Config config = ConfigFactory.defaultOverrides()
              .withFallback(ConfigFactory.parseFile(new File("config/application.conf")))
              .withFallback(ConfigFactory.parseFile(new File("config/reference.conf")))
              .resolve();
          bind(Config.class).toInstance(config);
          bind(Key.get(String.class, Names.named(CoreSettingsNames.WAVE_SERVER_DOMAIN)))
              .toInstance(config.getString("core.wave_server_domain"));
        }
      };
      run(coreSettings);
    } catch (PersistenceException | WaveServerException e) {
      LOG.severe("Fatal when running server:", e);
    } catch (Throwable t) {
      LOG.severe("Unexpected fatal error (" + t.getClass().getName() + ")");
      try { printStackTraceLite(t); } catch (Throwable ignore) {}
    }
  }

  public static void run(Module coreSettings) throws PersistenceException, WaveServerException {
    Injector injector = Guice.createInjector(coreSettings);
    Config config = injector.getInstance(Config.class);

    Module profilingModule = injector.getInstance(StatModule.class);
    ExecutorsModule executorsModule = injector.getInstance(ExecutorsModule.class);
    injector = injector.createChildInjector(profilingModule, executorsModule);

    Module serverModule = injector.getInstance(ServerModule.class);
    Module federationModule = buildFederationModule(injector);
    PersistenceModule persistenceModule = injector.getInstance(PersistenceModule.class);
    Module searchModule = injector.getInstance(SearchModule.class);
    Module profileFetcherModule = injector.getInstance(ProfileFetcherModule.class);
    Module robotApiModule = new JakartaRobotApiBindingsModule();
    Module mailModule = injector.getInstance(MailModule.class);

    persistenceModule.runMongoMigrationsIfNeeded();

    injector = injector.createChildInjector(serverModule, persistenceModule,
        robotApiModule, searchModule, federationModule, profileFetcherModule, mailModule);

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain = config.getString("core.wave_server_domain");
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    bootstrapOwner(injector.getInstance(AccountStore.class), config, domain);
    backfillRegistrationTimes(injector.getInstance(AccountStore.class));
    initializeServlets(server, config);
    initializeRobots(injector, waveBus);
    initializeRobotAgents(injector);
    initializeContacts(injector, waveBus);
    initializeFrontend(injector, server, waveBus);
    initializeSearch(injector, waveBus, config);
    initializeShutdownHandler(server);

    LOG.info("Starting server");
    server.startWebSocketServer(injector);
  }

  private static Module buildFederationModule(Injector settingsInjector) {
    return settingsInjector.getInstance(NoOpFederationModule.class);
  }

  private static void initializeServer(Injector injector, String waveDomain)
      throws PersistenceException, WaveServerException {
    AccountStore accountStore = injector.getInstance(AccountStore.class);
    accountStore.initializeAccountStore();
    AccountStoreHolder.init(accountStore, waveDomain);
    initializeFeatureFlags(injector);

    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore)certPathStore).initializeSignerInfoStore();
    }

    // Initialize ContactMessageStore asynchronously to avoid blocking if MongoDB is unavailable
    initializeContactMessageStoreAsync(injector);

    WaveletProvider waveServer = injector.getInstance(WaveletProvider.class);
    waveServer.initialize();
  }

  /** Seeds config-driven feature flags into the persistent store before services read them. */
  private static void initializeFeatureFlags(Injector injector) {
    try {
      Config config = injector.getInstance(Config.class);
      FeatureFlagStore featureFlagStore = injector.getInstance(FeatureFlagStore.class);
      FeatureFlagSeeder.seedSearchFeatureFlags(featureFlagStore, config);
      injector.getInstance(FeatureFlagService.class).refreshCache();
    } catch (PersistenceException e) {
      LOG.log(java.util.logging.Level.WARNING,
          "Failed to seed ot-search feature flag; search updates stay off", e);
    }
  }

  private static void initializeSearchWaveletUpdater(
      Injector injector, WaveBus waveBus, FeatureFlagStore featureFlagStore) {
    try {
      if (FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(featureFlagStore)) {
        subscribeSearchWaveletUpdater(injector, waveBus);
      }
    } catch (PersistenceException e) {
      LOG.log(java.util.logging.Level.WARNING,
          "Failed to read ot-search feature flag; retrying search updates later", e);
      retrySearchWaveletUpdaterInitialization(injector, waveBus, featureFlagStore);
    }
  }

  private static void retrySearchWaveletUpdaterInitialization(
      Injector injector, WaveBus waveBus, FeatureFlagStore featureFlagStore) {
    ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "SearchWaveletUpdater-Initializer");
      thread.setDaemon(true);
      return thread;
    });
    retryExecutor.scheduleWithFixedDelay(() -> {
      try {
        if (FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(featureFlagStore)) {
          subscribeSearchWaveletUpdater(injector, waveBus);
          retryExecutor.shutdown();
        }
      } catch (PersistenceException e) {
        LOG.log(java.util.logging.Level.WARNING,
            "Failed to read ot-search feature flag during retry; search updates stay off", e);
      }
    }, SEARCH_WAVELET_UPDATER_RETRY_SECONDS, SEARCH_WAVELET_UPDATER_RETRY_SECONDS, TimeUnit.SECONDS);
  }

  private static void subscribeSearchWaveletUpdater(Injector injector, WaveBus waveBus) {
    org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdater searchUpdater =
        injector.getInstance(org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdater.class);
    waveBus.subscribe(searchUpdater);
    LOG.info("SearchWaveletUpdater subscribed to WaveBus (ot-search enabled)");
  }

  /** Initializes ContactMessageStore asynchronously to avoid blocking if MongoDB is unavailable. */
  private static void initializeContactMessageStoreAsync(Injector injector) {
    Thread initThread = new Thread(() -> {
      try {
        org.waveprotocol.box.server.persistence.ContactMessageStore contactMessageStore =
            injector.getInstance(org.waveprotocol.box.server.persistence.ContactMessageStore.class);
        contactMessageStore.initializeContactMessageStore();
      } catch (Exception e) {
        LOG.log(java.util.logging.Level.WARNING, "Failed to initialize ContactMessageStore (contact form submissions may not work)", e);
      }
    }, "ContactMessageStore-Initializer");
    initThread.setDaemon(true);
    initThread.start();
  }

  /**
   * Ensures the configured owner address has the "owner" role. This covers the
   * case where the account was created before auto-owner detection existed.
   * If {@code core.owner_address} is unset or the account doesn't exist yet,
   * this is a no-op.
   */
  private static void bootstrapOwner(AccountStore accountStore, Config config, String domain) {
    String ownerAddress = "";
    if (config.hasPath("core.owner_address")) {
      ownerAddress = config.getString("core.owner_address").trim();
    }
    if (ownerAddress.isEmpty()) {
      return;
    }
    // Append domain if the address has no '@'
    if (!ownerAddress.contains("@")) {
      ownerAddress = ownerAddress + "@" + domain;
    }
    try {
      ParticipantId pid = ParticipantId.ofUnsafe(ownerAddress);
      AccountData acct = accountStore.getAccount(pid);
      if (acct == null) {
        LOG.info("Configured owner " + ownerAddress + " does not exist yet — skipping bootstrap");
        return;
      }
      if (!acct.isHuman()) {
        LOG.warning("Configured owner " + ownerAddress + " is not a human account — skipping");
        return;
      }
      HumanAccountData human = acct.asHuman();
      if (!HumanAccountData.ROLE_OWNER.equals(human.getRole())) {
        LOG.info("Bootstrapping owner role for " + ownerAddress
            + " (was '" + human.getRole() + "')");
        human.setRole(HumanAccountData.ROLE_OWNER);
        accountStore.putAccount(acct);
      }
    } catch (PersistenceException e) {
      LOG.severe("Failed to bootstrap owner for " + ownerAddress, e);
    }
  }

  /**
   * Backfills {@code registrationTime} for accounts created before the field
   * was introduced (PR #183). Any human account with {@code registrationTime == 0}
   * gets stamped with the current time so the admin dashboard no longer shows "--".
   * This is a one-time migration: once the value is set and persisted it will be
   * read back on subsequent startups and this method becomes a no-op.
   */
  private static void backfillRegistrationTimes(AccountStore accountStore) {
    try {
      List<AccountData> allAccounts = accountStore.getAllAccounts();
      long now = System.currentTimeMillis();
      int backfilled = 0;
      for (AccountData acct : allAccounts) {
        if (!acct.isHuman()) continue;
        HumanAccountData human = acct.asHuman();
        if (human.getRegistrationTime() == 0) {
          human.setRegistrationTime(now);
          accountStore.putAccount(acct);
          backfilled++;
        }
      }
      if (backfilled > 0) {
        LOG.info("Backfilled registrationTime for " + backfilled
            + " legacy account(s) (set to current time)");
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to backfill registration times — legacy accounts will still show '--'", e);
    }
  }

  private static void initializeServlets(ServerRpcProvider server, Config config) {
    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);
    server.addServlet(UrlPreviewServlet.URL_PREVIEW_URL, UrlPreviewServlet.class);
    server.addServlet("/auth/signin", AuthenticationServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);
    server.addServlet("/auth/confirm-email", EmailConfirmServlet.class);
    server.addServlet("/auth/password-reset", PasswordResetServlet.class);
    server.addServlet("/auth/magic-link", MagicLinkServlet.class);
    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/fetch/*", FetchServlet.class);
    server.addServlet("/fetch/version/*", VersionedFetchServlet.class);
    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/dev/client-applier-stats", ClientApplierStatsJakartaServlet.class);
    server.addServlet("/healthz", HealthServlet.class);
    server.addServlet("/readyz", HealthServlet.class);
    server.addServlet("/version", VersionServlet.class);
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    server.addServlet("/userprofile/*", ProfileServlet.class);
    server.addServlet("/account/settings", AccountSettingsServlet.class);
    server.addServlet("/account/settings/*", AccountSettingsServlet.class);
    server.addServlet("/contacts", FetchContactsServlet.class);
    server.addServlet("/contacts/search/*", ContactSearchServlet.class);
    server.addServlet("/iniavatars/*", org.apache.wave.box.server.rpc.InitialsAvatarsServlet.class);
    server.addServlet("/wave/public/*", PublicWaveFetchServlet.class);
    server.addServlet("/public", PublicDirectoryServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    server.addServlet("/history/*", VersionHistoryServlet.class);
    server.addServlet("/admin/flags", FeatureFlagServlet.class);
    server.addServlet("/admin/flags/*", FeatureFlagServlet.class);
    server.addServlet("/admin", AdminServlet.class);
    server.addServlet("/admin/*", AdminServlet.class);
    server.addServlet("/account/robots", RobotDashboardServlet.class);
    server.addServlet("/api/robots", RobotApiServlet.class);
    server.addServlet("/api/robots/*", RobotApiServlet.class);
    server.addServlet("/contact", ContactServlet.class);
    server.addServlet("/folder/*", FolderServlet.class);
    server.addServlet("/searches", SearchesServlet.class);
    server.addServlet("/tags", TagsServlet.class);
    server.addServlet("/robot/rpc", ActiveApiServlet.class);
    server.addServlet("/robot/dataapi", DataApiServlet.class);
    server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
    server.addServlet("/robot/dataapi/token", DataApiTokenServlet.class);
    server.addServlet("/robot/token", DataApiTokenServlet.class);

    // Register FragmentsServlet for HTTP fragment transport (mirrors main ServerMain logic).
    try {
      String transport = readFragmentsTransport(config);
      if (transport == null || transport.isEmpty()) {
        transport = "off";
      }
      if (isFragmentsHttpEnabled(transport)) {
        server.addServlet("/fragments", FragmentsServlet.class);
        server.addServlet("/fragments/*", FragmentsServlet.class);
      } else {
        LOG.info("Fragments HTTP endpoint is disabled (effective transport='" + transport + "')");
      }
    } catch (Exception e) {
      LOG.warning("Failed to configure fragments transport/endpoints; leaving /fragments disabled", e);
    }

    server.addServlet("/render/*", RenderApiServlet.class);
    server.addServlet("/wave/*", PublicWaveServlet.class);

    server.addServlet("/terms", LegalServlet.class);
    server.addServlet("/privacy", LegalServlet.class);
    server.addServlet("/changelog", ChangelogServlet.class);
    server.addServlet("/changelog/*", ChangelogServlet.class);

    // API documentation
    server.addServlet("/api-docs", ApiDocsServlet.class);
    server.addServlet("/api/openapi.json", ApiDocsServlet.class);
    server.addServlet("/api/llm.txt", ApiDocsServlet.class);
    server.addServlet("/llms.txt", ApiDocsServlet.class);
    server.addServlet("/llms-full.txt", ApiDocsServlet.class);

    // SEO endpoints
    server.addServlet("/robots.txt", RobotsServlet.class);
    server.addServlet("/sitemap.xml", SitemapServlet.class);

    server.addServlet("/", WaveClientServlet.class);
  }

  /**
   * Reads the unified fragments transport from Typesafe Config.
   * Expected values: off | http | stream | both
   */
  private static String readFragmentsTransport(Config config) {
    try {
      if (config.hasPath("server.fragments.transport")) {
        String v = config.getString("server.fragments.transport");
        if (v != null) {
          v = v.trim().toLowerCase();
          if (!v.isEmpty()) {
            return v;
          }
        }
      }
    } catch (Exception e) {
      LOG.warning("Error reading server.fragments.transport", e);
    }
    return null;
  }

  private static boolean isFragmentsHttpEnabled(String transport) {
    if (transport == null) {
      return false;
    }
    // Enable HTTP endpoint for http, both, and stream modes (stream mode needs
    // the HTTP endpoint as a fallback before the view channel is ready).
    return "http".equals(transport) || "both".equals(transport) || "stream".equals(transport);
  }

  private static void initializeRobots(Injector injector, WaveBus waveBus) {
    RobotsGateway robotsGateway = injector.getInstance(RobotsGateway.class);
    waveBus.subscribe(robotsGateway);
  }

  /** Subscribes the contacts recorder to the wave bus so contact relationships are tracked. */
  private static void initializeContacts(Injector injector, WaveBus waveBus) {
    try {
      ContactStore contactStore = injector.getInstance(ContactStore.class);
      contactStore.initializeContactStore();
    } catch (PersistenceException e) {
      LOG.warning("Failed to initialize ContactStore; contact recording may not persist", e);
    }
    ContactsRecorder contactsRecorder = injector.getInstance(ContactsRecorder.class);
    waveBus.subscribe(contactsRecorder);
    LOG.info("ContactsRecorder subscribed to WaveBus");
    AnalyticsRecorder analyticsRecorder = injector.getInstance(AnalyticsRecorder.class);
    waveBus.subscribe(analyticsRecorder);
    LOG.info("AnalyticsRecorder subscribed to WaveBus");
  }

  private static void initializeFrontend(Injector injector, ServerRpcProvider server,
      WaveBus waveBus) throws WaveServerException {
    HashedVersionFactory hashFactory = injector.getInstance(HashedVersionFactory.class);
    WaveletProvider provider = injector.getInstance(WaveletProvider.class);
    WaveletInfo waveletInfo = WaveletInfo.create(hashFactory, provider);
    injector.getInstance(SearchWaveletDispatcher.class).initialize(waveletInfo);
    ClientFrontend frontend =
        ClientFrontendImpl.create(
            provider,
            waveBus,
            waveletInfo,
            injector.getInstance(SearchProvider.class),
            injector.getInstance(SearchWaveletSnapshotPublisher.class));
    org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc.Interface rpcImpl =
        WaveClientRpcImpl.create(frontend, false, injector.getInstance(SearchWaveletManager.class));
    server.registerService(org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc.newReflectiveService(rpcImpl));
    new StaleAnnotationSweeper(provider).start();
  }

  /**
   * Initializes the built-in robot agents (registration). These agents run
   * in-JVM and use LocalOperationSubmitter to bypass HTTP/OAuth.
   *
   * <p>Password bots were removed because password reset is now handled via
   * email. The welcome wave is injected directly by {@link
   * org.waveprotocol.box.server.rpc.WelcomeWaveCreator}.
   */
  private static void initializeRobotAgents(Injector injector) {
    try {
      injector.getInstance(RegistrationRobot.class);
      LOG.info("Initialized RegistrationRobot agent");
    } catch (Exception e) {
      LOG.warning("Failed to initialize RegistrationRobot", e);
    }
  }

  private static void initializeFederation(Injector injector) {
    FederationTransport federationManager = injector.getInstance(FederationTransport.class);
    federationManager.startFederation();
  }

  private static void initializeSearch(Injector injector, WaveBus waveBus, Config config)
      throws WaveServerException {
    long startMs = System.currentTimeMillis();
    PerUserWaveViewDistpatcher waveViewDistpatcher = injector.getInstance(PerUserWaveViewDistpatcher.class);
    PerUserWaveViewBus.Listener listener = injector.getInstance(PerUserWaveViewBus.Listener.class);
    PerUserWaveViewHandler waveViewHandler = injector.getInstance(PerUserWaveViewHandler.class);
    waveViewDistpatcher.addListener(listener);
    waveBus.subscribe(waveViewDistpatcher);
    if (!(waveViewHandler instanceof WaveBus.Subscriber)) {
      String message = "PerUserWaveViewHandler " + waveViewHandler.getClass().getName()
          + " must implement WaveBus.Subscriber for search freshness invalidation";
      LOG.severe(message);
      throw new IllegalStateException(message);
    }
    waveBus.subscribe((WaveBus.Subscriber) waveViewHandler);
    WaveIndexer waveIndexer = injector.getInstance(WaveIndexer.class);
    waveIndexer.remakeIndex();
    if ("lucene".equals(config.getString("core.search_type"))) {
      Lucene9WaveIndexerImpl lucene9Indexer = injector.getInstance(Lucene9WaveIndexerImpl.class);
      waveBus.subscribe(lucene9Indexer);
      ReindexService reindexService = injector.getInstance(ReindexService.class);
      if (lucene9Indexer.getLastReindexStats() != null) {
        reindexService.recordStartupReindex(lucene9Indexer.getLastReindexStats());
      } else if (lucene9Indexer.getLastRebuildWaveCount() >= 0) {
        reindexService.recordStartupReindex(lucene9Indexer.getLastRebuildWaveCount());
      }
    }

    // Register OT search wavelet updater AFTER PerUserWaveViewDistpatcher
    // so that the per-user wave view index is current before search updates.
    // ORDERING DEPENDENCY: SearchWaveletUpdater must be subscribed AFTER lucene9Indexer.
    // SearchWaveletUpdater.waveletCommitted() triggers mention-subscription recomputes only
    // after Lucene9 has committed its MENTIONED field update in its own waveletCommitted().
    // WaveletNotificationDispatcher iterates subscribers via CopyOnWriteArraySet, which
    // preserves insertion order; SearchWaveletUpdater is therefore notified after lucene9Indexer.
    FeatureFlagStore featureFlagStore = injector.getInstance(FeatureFlagStore.class);
    initializeSearchWaveletUpdater(injector, waveBus, featureFlagStore);

    warmUpWaveView(injector, config);

    long elapsedMs = System.currentTimeMillis() - startMs;
    LOG.info("initializeSearch completed in " + elapsedMs + " ms");
  }

  /**
   * Pre-warms the wave map and, if an owner is configured, the owner's per-user
   * view when {@code search.startup_wave_view_warmup} is enabled.
   *
   * <p>When the flag is disabled, the server preserves the lazy first-search
   * behavior instead of blocking startup on this warmup step.
   *
   * <p>If {@code core.owner_address} is set, only {@code retrievePerUserWaveView}
   * is called — that call already triggers {@code loadAllWavelets()} internally
   * on cache miss, so a separate explicit load would double the startup I/O.
   * Without an owner, a direct {@code loadAllWavelets()} warms the WaveMap cache.
   */
  static void warmUpWaveView(Injector injector, Config config) {
    if (!config.hasPath("search.startup_wave_view_warmup")
        || !config.getBoolean("search.startup_wave_view_warmup")) {
      return;
    }
    try {
      long warmupStart = System.currentTimeMillis();
      final String rawOwner = config.hasPath("core.owner_address")
          ? config.getString("core.owner_address").trim()
          : "";
      if (!rawOwner.isEmpty()) {
        final String ownerAddress = rawOwner.contains("@")
            ? rawOwner
            : rawOwner + "@" + config.getString("core.wave_server_domain");
        PerUserWaveViewProvider viewProvider =
            injector.getInstance(PerUserWaveViewProvider.class);
        ParticipantId owner = ParticipantId.ofUnsafe(ownerAddress);
        viewProvider.retrievePerUserWaveView(owner);
        long viewMs = System.currentTimeMillis() - warmupStart;
        LOG.info("Pre-warmed wave view for owner " + ownerAddress + " in " + viewMs + " ms");
      } else {
        WaveMap waveMap = injector.getInstance(WaveMap.class);
        waveMap.loadAllWavelets();
        long loadMs = System.currentTimeMillis() - warmupStart;
        LOG.info("Wave map pre-warmed: loaded all wavelets in " + loadMs + " ms");
      }
    } catch (Exception e) {
      LOG.log(java.util.logging.Level.WARNING,
          "Wave view warmup failed (non-fatal, will load on first request)", e);
    }
  }

  private static void initializeShutdownHandler(final ServerRpcProvider server) {
    ShutdownManager.getInstance().register(new Shutdownable() {
      @Override public void shutdown() throws Exception { server.stopServer(); }
    }, ServerMain.class.getSimpleName(), ShutdownPriority.Server);
  }

  static String structuredLoggingStatusMessage() {
    return "Structured JSON logging enabled; writing Grafana/Loki source logs to "
        + new File("logs/wave-json.log").getAbsolutePath();
  }
}

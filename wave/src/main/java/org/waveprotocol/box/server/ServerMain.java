/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server;

import com.google.gwt.logging.server.RemoteLoggingServiceImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.wave.box.server.rpc.InitialsAvatarsServlet;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.box.server.frontend.ClientFrontend;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
import org.waveprotocol.box.server.frontend.FragmentsViewChannelHandler;
import org.waveprotocol.box.server.frontend.FragmentsFetchBridgeImpl;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.robots.ProfileFetcherModule;
import org.waveprotocol.box.server.robots.RobotApiModule;
import org.waveprotocol.box.server.robots.RobotRegistrationServlet;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordAdminRobot;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordRobot;
import org.waveprotocol.box.server.robots.agent.registration.RegistrationRobot;
import org.waveprotocol.box.server.robots.agent.welcome.WelcomeRobot;
import org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiServlet;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.contact.ContactsRecorder;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.rpc.*;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.rpc.MdcLoggingFilter;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.server.stat.StatuszServlet;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.server.waveserver.*;
import org.waveprotocol.box.server.waveserver.StaleAnnotationSweeper;
import org.waveprotocol.box.stat.StatService;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.box.server.dev.ClientApplierStatsServlet;
import org.waveprotocol.box.server.security.SecurityHeadersFilter;
import org.waveprotocol.box.server.security.StaticCacheFilter;
import org.waveprotocol.box.server.security.NoCacheFilter;
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.waveprotocol.box.server.config.ConfigurationInitializationException;
import org.waveprotocol.box.server.frontend.ManifestOrderCache;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelImpl;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.NoOpRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.SkeletonRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier;
 

    

/**
 * Wave Server entrypoint.
 */
public class ServerMain {

  private static void printStackTraceLite(Throwable t) {
    org.waveprotocol.box.server.util.StackTraces.printStackTraceLite(t, System.err);
  }

  private static final Log LOG = Log.get(ServerMain.class);
  private static final long SEARCH_WAVELET_UPDATER_RETRY_SECONDS = 30L;

  public static void main(String... args) {
    configureLoggingBridge();
    installGlobalExceptionLogger();
    try {
      Module coreSettings = new AbstractModule() {

        @Override
        protected void configure() {
          // Load configuration with precedence (single canonical location under module's config/):
          // 1) System properties / -D overrides
          // 2) config/application.conf (project-local overrides)
          // 3) config/reference.conf (project-local defaults)
          Config config = ConfigFactory.defaultOverrides()
              .withFallback(ConfigFactory.parseFile(new File("config/application.conf")))
              .withFallback(ConfigFactory.parseFile(new File("config/reference.conf")))
              .resolve();
          bind(Config.class).toInstance(config);
          bind(Key.get(String.class, Names.named(CoreSettingsNames.WAVE_SERVER_DOMAIN)))
              .toInstance(config.getString("core.wave_server_domain"));
          applyWebSocketSystemProperties(config);
          configureFragmentsApplier(config);
          enableFragmentsMetrics(config);
          configureViewportLimits(config);
        }
      };
      run(coreSettings);
    } catch (PersistenceException e) {
      LOG.severe("PersistenceException when running server:", e);
    } catch (WaveServerException e) {
      LOG.severe("WaveServerException when running server:", e);
    } catch (Throwable t) {
      LOG.severe("Unexpected fatal error when running server (" + t.getClass().getName() + ")");
      try {
        printStackTraceLite(t);
      } catch (Throwable ignore) { }
    }
  }

  public static void run(Module coreSettings) throws PersistenceException,
      WaveServerException {
    Injector injector = Guice.createInjector(coreSettings);
    // Read config early to decide Jakarta vs javax behavior
    Config config = injector.getInstance(Config.class);
    boolean isJakarta = isJakarta(config);

    Module profilingModule = isJakarta ? new AbstractModule() { @Override protected void configure() { /* no-op on Jakarta */ } }
                                       : injector.getInstance(StatModule.class);
    ExecutorsModule executorsModule = injector.getInstance(ExecutorsModule.class);
    injector = injector.createChildInjector(profilingModule, executorsModule);

    Module serverModule = injector.getInstance(ServerModule.class);
    Module federationModule = buildFederationModule(injector);
    PersistenceModule persistenceModule = injector.getInstance(PersistenceModule.class);
    Module searchModule = injector.getInstance(SearchModule.class);
    // Build child injector conditionally including robots/profile fetchers on javax only
    if (!isJakarta) {
      Module robotApiModule = new RobotApiModule();
      Module profileFetcherModule = injector.getInstance(ProfileFetcherModule.class);
      injector = injector.createChildInjector(serverModule, persistenceModule, robotApiModule,
          federationModule, searchModule, profileFetcherModule);
    } else {
      injector = injector.createChildInjector(serverModule, persistenceModule, federationModule,
          searchModule);
    }

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain = config.getString("core.wave_server_domain");
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    initializeServlets(server, config);
    if (!isJakarta) {
      initializeRobotAgents(server);
      initializeRobots(injector, waveBus);
    }
    initializeContacts(injector, waveBus);
    initializeFrontend(injector, server, waveBus);
    if (!isJakarta) {
      initializeFederation(injector);
    }
    initializeSearch(injector, waveBus, config);
    initializeShutdownHandler(server);

    LOG.info("Starting server");
    server.startWebSocketServer(injector);
  }

  private static boolean isJakarta(Config config) {
    return true;
  }

  private static Module buildFederationModule(Injector settingsInjector) {
    return settingsInjector.getInstance(NoOpFederationModule.class);
  }

  /** Configures JUL→SLF4J bridge if available. */
  private static void configureLoggingBridge() {
    try {
      LogManager.getLogManager().reset();
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
    } catch (Throwable ignore) { }
  }

  /** Installs a minimal global uncaught exception handler that logs stack traces. */
  private static void installGlobalExceptionLogger() {
    try {
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
        try {
          LOG.severe("Uncaught exception on thread " + thread.getName() +
              " (" + throwable.getClass().getName() + ")");
        } finally {
          try { printStackTraceLite(throwable); } catch (Throwable ignore) { }
        }
      });
    } catch (Throwable ignore) { }
  }

  /** Applies WebSocket tunables to system properties for internal client channels. */
  private static void applyWebSocketSystemProperties(Config config) {
    try {
      if (config.hasPath("wave.websocket.connectTimeoutMs")) {
        System.setProperty("wave.websocket.connectTimeoutMs",
            Integer.toString(config.getInt("wave.websocket.connectTimeoutMs")));
      }
      if (config.hasPath("wave.websocket.connectWaitMs")) {
        System.setProperty("wave.websocket.connectWaitMs",
            Integer.toString(config.getInt("wave.websocket.connectWaitMs")));
      }
      if (config.hasPath("wave.websocket.maxBackoffMs")) {
        System.setProperty("wave.websocket.maxBackoffMs",
            Integer.toString(config.getInt("wave.websocket.maxBackoffMs")));
      }
      if (config.hasPath("wave.websocket.jitterFraction")) {
        System.setProperty("wave.websocket.jitterFraction",
            Double.toString(config.getDouble("wave.websocket.jitterFraction")));
      }
    } catch (Throwable t) {
      LOG.warning("Failed to apply wave.websocket config to system properties", t);
    }
  }

  /** Configures the fragments applier instance and related warn threshold/flags. */
  private static void configureFragmentsApplier(Config config) {
    try {
      boolean applierEnabled = false;
      boolean forceClientFragments = false;
      try {
        if (config.hasPath("client.flags.defaults.enableFragmentsApplier")) {
          applierEnabled = config.getBoolean("client.flags.defaults.enableFragmentsApplier");
        } else if (config.hasPath("client.flags.defaults")) {
          // Fallback: CSV-style defaults string (e.g., "a=true,b=false").
          String s = config.getString("client.flags.defaults");
          if (s != null) {
            for (String p : s.split(",")) {
              String t = p.trim();
              if (t.isEmpty()) continue;
              int eq = t.indexOf('=');
              String name = (eq > 0) ? t.substring(0, eq).trim() : t;
              String val = (eq > 0) ? t.substring(eq + 1).trim() : "true";
              if ("enableFragmentsApplier".equals(name)) {
                applierEnabled = Boolean.parseBoolean(val);
              }
              if ("forceClientFragments".equals(name)) {
                forceClientFragments = Boolean.parseBoolean(val);
              }
            }
          }
        }
        if (config.hasPath("wave.fragments.forceClientApplier")) {
          forceClientFragments = config.getBoolean("wave.fragments.forceClientApplier");
        }
      } catch (ConfigException e) {
        LOG.info("Failed reading fragments applier defaults; falling back to false", e);
      }
      try {
        if (applierEnabled) {
          String impl = "skeleton";
          try {
            if (config.hasPath("wave.fragments.applier.impl")) {
              impl = config.getString("wave.fragments.applier.impl");
            }
          } catch (ConfigException ignore) { }
          if ("real".equalsIgnoreCase(impl)) {
            ViewChannelImpl.setFragmentsApplier(new RealRawFragmentsApplier());
          } else {
            ViewChannelImpl.setFragmentsApplier(new SkeletonRawFragmentsApplier());
          }
        } else {
          ViewChannelImpl.setFragmentsApplier(new NoOpRawFragmentsApplier());
        }
        ViewChannelImpl.setFragmentsApplierEnabled(applierEnabled);
        WaveClientRpcImpl.setForceClientFragments(forceClientFragments);
        String applierCls = applierEnabled ? "SkeletonRawFragmentsApplier" : "NoOpRawFragmentsApplier";
        int warnMs = 50;
        try {
          if (config.hasPath("wave.fragments.applier.warnMs")) {
            warnMs = config.getInt("wave.fragments.applier.warnMs");
          }
        } catch (ConfigException e) {
          LOG.info("Failed reading wave.fragments.applier.warnMs; using default " + warnMs, e);
        }
        try { ViewChannelImpl.setApplierWarnMs(warnMs); }
        catch (Throwable ignore) { }
        LOG.info("Fragments applier: enabled=" + applierEnabled + ", impl=" + applierCls + ", warnMs=" + warnMs
            + ", forceClientFragments=" + forceClientFragments);
      } catch (Throwable t) {
        LOG.warning("Failed to wire fragments applier instance; proceeding without applier", t);
      }
    } catch (Throwable t) {
      LOG.warning("Failed to apply fragments applier config", t);
    }
  }

  /** Enables fragments metrics counters when explicitly requested or when profiling is on. */
  private static void enableFragmentsMetrics(Config config) {
    boolean metrics = false;
    try {
      if (config.hasPath("wave.fragments.metrics.enabled")) {
        metrics = config.getBoolean("wave.fragments.metrics.enabled");
      } else if (config.hasPath("core.enable_profiling")) {
        metrics = config.getBoolean("core.enable_profiling");
      }
    } catch (ConfigException ignore) { }
    FragmentsMetrics.setEnabled(metrics);
  }

  /** Applies viewport defaults and bounds for emitting fragment windows. */
  private static void configureViewportLimits(Config config) {
    try {
      int defLimit = WaveClientRpcImpl.getDefaultViewportLimit();
      int maxLimit = WaveClientRpcImpl.getMaxViewportLimit();
      if (config.hasPath("wave.fragments.defaultViewportLimit")) {
        defLimit = config.getInt("wave.fragments.defaultViewportLimit");
      }
      if (config.hasPath("wave.fragments.maxViewportLimit")) {
        maxLimit = config.getInt("wave.fragments.maxViewportLimit");
      }
      WaveClientRpcImpl.setViewportLimits(defLimit, maxLimit);
    } catch (Exception e) {
      LOG.warning("Failed to configure fragments viewport limits; using defaults", e);
    }
  }

  private static void initializeServer(Injector injector, String waveDomain)
      throws PersistenceException, WaveServerException {
    AccountStore accountStore = injector.getInstance(AccountStore.class);
    accountStore.initializeAccountStore();
    AccountStoreHolder.init(accountStore, waveDomain);
    initializeFeatureFlags(injector);

    // Initialize ContactStore asynchronously to avoid blocking if MongoDB is unavailable
    initializeContactStoreAsync(injector);

    initializeSignerInfoStore(injector);
    initializeWaveServer(injector);
  }

  /** Initializes the signer info store when the configured CertPathStore supports it. */
  private static void initializeSignerInfoStore(Injector injector) throws PersistenceException {
    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore) certPathStore).initializeSignerInfoStore();
    }
  }

  /** Performs WaveletProvider initialization. */
  private static void initializeWaveServer(Injector injector) throws PersistenceException, WaveServerException {
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
      LOG.log(Level.WARNING, "Failed to seed ot-search feature flag; search updates stay off", e);
    }
  }

  private static void initializeSearchWaveletUpdater(
      Injector injector, WaveBus waveBus, FeatureFlagStore featureFlagStore) {
    try {
      if (FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(featureFlagStore)) {
        subscribeSearchWaveletUpdater(injector, waveBus);
      }
    } catch (PersistenceException e) {
      LOG.log(Level.WARNING,
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
        LOG.log(Level.WARNING,
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

  /** Initializes ContactStore asynchronously to avoid blocking if MongoDB is unavailable. */
  private static void initializeContactStoreAsync(Injector injector) {
    Thread initThread = new Thread(() -> {
      try {
        ContactStore contactStore = injector.getInstance(ContactStore.class);
        contactStore.initializeContactStore();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Failed to initialize ContactStore (contacts may not work)", e);
      }
    }, "ContactStore-Initializer");
    initThread.setDaemon(true);
    initThread.start();
  }

  private static void initializeServlets(ServerRpcProvider server, Config config) {
    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);

    server.addServlet(SessionManager.SIGN_IN_URL, AuthenticationServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);

    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/fetch/*", FetchServlet.class);
    server.addServlet("/fetch/version/*", VersionedFetchServlet.class);
    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/notification/*", NotificationServlet.class);
    server.addServlet("/healthz", HealthServlet.class);
    server.addServlet("/readyz", HealthServlet.class);

    // Skip robots and GWT remote logging on Jakarta; these are javax/GWT-bound.
    if (!isJakarta(config)) {
      server.addServlet("/robot/dataapi", DataApiServlet.class);
      server.addServlet(DataApiOAuthServlet.DATA_API_OAUTH_PATH + "/*", DataApiOAuthServlet.class);
      server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
      server.addServlet("/robot/register/*", RobotRegistrationServlet.class);
      server.addServlet("/robot/rpc", ActiveApiServlet.class);
      server.addServlet("/webclient/remote_logging", RemoteLoggingServiceImpl.class);
    }
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    server.addServlet("/contacts", FetchContactsServlet.class);
    server.addServlet("/contacts/search/*", ContactSearchServlet.class);
    // Dev endpoint: client-side fragments applier stats (session-based)
    server.addServlet("/dev/client-applier-stats", ClientApplierStatsServlet.class);
    server.addServlet("/iniavatars/*", InitialsAvatarsServlet.class);
    server.addServlet("/wave/public/*", PublicWaveFetchServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    // Note: this javax-era ServerMain is excluded from compilation (see mainExactExcludes in build.sbt).
    // The active registration lives in the jakarta-overrides variant of ServerMain.
    server.addServlet("/folder/*", FolderServlet.class);
    server.addServlet("/searches", SearchesServlet.class);
    try {
      // Unified transport: single source of truth.
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

    server.addServlet("/", WaveClientServlet.class);

    addSecurityAndCachingFilters(server);
    addProfiling(server, config);
  }

  /** Installs security headers and caching/no-cache filters. */
  private static void addSecurityAndCachingFilters(ServerRpcProvider server) {
    server.addFilter("/*", SecurityHeadersFilter.class);
    server.addFilter("/static/*", StaticCacheFilter.class);
    server.addFilter("/webclient/*", NoCacheFilter.class);
  }

  /** Adds request-scoped metrics and status endpoints when profiling is enabled. */
  private static void addProfiling(ServerRpcProvider server, Config config) {
    server.addFilter("/*", MdcLoggingFilter.class);
    server.addFilter("/*", RequestScopeFilter.class);
    boolean enableProfiling = config.getBoolean("core.enable_profiling");
    if (enableProfiling) {
      server.addFilter("/*", TimingFilter.class);
      server.addServlet(StatService.STAT_URL, StatuszServlet.class);
    }
  }

  /**
   * Reads the unified fragments transport from Typesafe Config.
   * Expected values: off | http | stream | both
   */
  @javax.annotation.Nullable
  private static String readFragmentsTransport(com.typesafe.config.Config config) {
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
    } catch (Throwable ignore) {}

    boolean legacyHttp = false;
    boolean legacyStream = false;
    try {
      if (config.hasPath("server.enableFragmentsHttp")) {
        legacyHttp = config.getBoolean("server.enableFragmentsHttp");
      }
    } catch (Throwable ignore) {}
    try {
      if (config.hasPath("server.enableFetchFragmentsRpc")) {
        legacyStream = config.getBoolean("server.enableFetchFragmentsRpc");
      }
    } catch (Throwable ignore) {}

    if (legacyHttp && legacyStream) {
      return "both";
    }
    if (legacyStream) {
      return "stream";
    }
    if (legacyHttp) {
      return "http";
    }
    return null;
  }

  private static boolean isFragmentsHttpEnabled(String transport) {
    if (transport == null) {
      return false;
    }
    if ("http".equals(transport) || "both".equals(transport) || "stream".equals(transport)) {
      return true;
    }
    return false;
  }

  private static boolean isFragmentsStreamEnabled(String transport) {
    if (transport == null) {
      return false;
    }
    if ("stream".equals(transport) || "both".equals(transport)) {
      return true;
    }
    return false;
  }

  private static void initializeRobots(Injector injector, WaveBus waveBus) {
    RobotsGateway robotsGateway = injector.getInstance(RobotsGateway.class);
    waveBus.subscribe(robotsGateway);
  }

  private static void initializeRobotAgents(ServerRpcProvider server) {
    server.addServlet(PasswordRobot.ROBOT_URI + "/*", PasswordRobot.class);
    server.addServlet(PasswordAdminRobot.ROBOT_URI + "/*", PasswordAdminRobot.class);
    server.addServlet(WelcomeRobot.ROBOT_URI + "/*", WelcomeRobot.class);
    server.addServlet(RegistrationRobot.ROBOT_URI + "/*", RegistrationRobot.class);
  }

  /** Subscribes the contacts recorder to the wave bus so contact relationships are tracked. */
  private static void initializeContacts(Injector injector, WaveBus waveBus) {
    ContactsRecorder contactsRecorder = injector.getInstance(ContactsRecorder.class);
    waveBus.subscribe(contactsRecorder);
  }

  private static void initializeFrontend(Injector injector, ServerRpcProvider server,
      WaveBus waveBus) throws WaveServerException {
    HashedVersionFactory hashFactory = injector.getInstance(HashedVersionFactory.class);

    WaveletProvider provider = injector.getInstance(WaveletProvider.class);
    WaveletInfo waveletInfo = WaveletInfo.create(hashFactory, provider);
    injector.getInstance(SearchWaveletDispatcher.class).initialize(waveletInfo);
    ClientFrontend frontend =
        ClientFrontendImpl.create(provider, waveBus, waveletInfo);

    ProtocolWaveClientRpc.Interface rpcImpl = WaveClientRpcImpl.create(frontend, false);
    server.registerService(ProtocolWaveClientRpc.newReflectiveService(rpcImpl));
    applyFragmentsConfig(injector.getInstance(com.typesafe.config.Config.class));
    wireFragmentsHandler(injector, provider);
    wireFragmentsFetchBridge(injector, provider);
    new StaleAnnotationSweeper(provider).start();
  }

  /** Sets the FragmentsViewChannelHandler used to emit ProtocolFragments in updates. */
  private static void wireFragmentsHandler(Injector injector, WaveletProvider provider) {
    try {
      WaveClientRpcImpl.setFragmentsHandler(
          new FragmentsViewChannelHandler(provider, injector.getInstance(Config.class)));
    } catch (Throwable t) {
      LOG.warning("Failed to wire FragmentsViewChannelHandler; fragments RPC disabled", t);
    }
  }

  /** Hooks ViewChannelImpl.fetchFragments via a lightweight server bridge. */
  private static void wireFragmentsFetchBridge(Injector injector, WaveletProvider provider) {
    try {
      ViewChannelImpl.setFragmentsFetchBridge(
          new FragmentsFetchBridgeImpl(provider, injector.getInstance(Config.class)));
    } catch (Throwable t) {
      LOG.warning("Failed to wire FragmentsFetchBridge; ViewChannel.fetchFragments disabled", t);
    }
  }

  /**
   * Applies and validates fragments-related cache configuration.
   * Throws {@link org.waveprotocol.box.server.config.ConfigurationInitializationException}
   * when values are invalid so startup can fail fast with actionable logs.
   */
  public static void applyFragmentsConfig(Config cfg) {
 
    applySegmentRegistryMaxEntries(cfg);
    applySegmentRegistryTtlMs(cfg);
    applyManifestOrderCacheMaxEntries(cfg);
    applyManifestOrderCacheTtlMs(cfg);
    validateApplierWarnMs(cfg);
    validateApplierImpl(cfg);
  }

  /** Validates and applies server.segmentStateRegistry.maxEntries. */
  private static void applySegmentRegistryMaxEntries(Config cfg) {
    if (!cfg.hasPath("server.segmentStateRegistry.maxEntries")) return;
    final String key = "server.segmentStateRegistry.maxEntries";
    try {
      int max = cfg.getInt(key);
      if (max <= 0) {
        LOG.severe("Invalid config: " + key + "=" + max + " (must be > 0)");
        throw new ConfigurationInitializationException(key + " must be > 0 (got " + max + ")");
      }
      SegmentWaveletStateRegistry.setMaxEntries(max);
    } catch (com.typesafe.config.ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  /** Validates and applies server.segmentStateRegistry.ttlMs. */
  private static void applySegmentRegistryTtlMs(Config cfg) {
    if (!cfg.hasPath("server.segmentStateRegistry.ttlMs")) return;
    final String key = "server.segmentStateRegistry.ttlMs";
    try {
      long ttl = cfg.getLong(key);
      if (ttl < 0L) {
        LOG.severe("Invalid config: " + key + "=" + ttl + " (must be >= 0; 0 disables TTL)");
        throw new ConfigurationInitializationException(key + " must be >= 0 (got " + ttl + ")");
      }
      SegmentWaveletStateRegistry.setTtlMs(ttl);
    } catch (com.typesafe.config.ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  /** Validates and applies wave.fragments.manifestOrderCache.maxEntries. */
  private static void applyManifestOrderCacheMaxEntries(Config cfg) {
    if (!cfg.hasPath("wave.fragments.manifestOrderCache.maxEntries")) return;
    final String key = "wave.fragments.manifestOrderCache.maxEntries";
    try {
      int max = cfg.getInt(key);
      if (max <= 0) {
        LOG.severe("Invalid config: " + key + "=" + max + " (must be > 0)");
        throw new ConfigurationInitializationException(key + " must be > 0 (got " + max + ")");
      }
      ManifestOrderCache.setMaxEntries(max);
    } catch (com.typesafe.config.ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  /** Validates and applies wave.fragments.manifestOrderCache.ttlMs. */
  private static void applyManifestOrderCacheTtlMs(Config cfg) {
    if (!cfg.hasPath("wave.fragments.manifestOrderCache.ttlMs")) return;
    final String key = "wave.fragments.manifestOrderCache.ttlMs";
    try {
      long ttl = cfg.getLong(key);
      if (ttl < 0L) {
        LOG.severe("Invalid config: " + key + "=" + ttl + " (must be >= 0; 0 disables TTL)");
        throw new ConfigurationInitializationException(key + " must be >= 0 (got " + ttl + ")");
      }
      ManifestOrderCache.setTtlMs(ttl);
    } catch (com.typesafe.config.ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  /** Validates wave.fragments.applier.warnMs. */
  private static void validateApplierWarnMs(Config cfg) {
    if (!cfg.hasPath("wave.fragments.applier.warnMs")) return;
    final String key = "wave.fragments.applier.warnMs";
    try {
      int warn = cfg.getInt(key);
      if (warn < 0) {
        LOG.severe("Invalid config: " + key + "=" + warn + " (must be >= 0)");
        throw new ConfigurationInitializationException(key + " must be >= 0 (got " + warn + ")");
      }
    } catch (ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  /** Validates wave.fragments.applier.impl against supported values. */
  private static void validateApplierImpl(Config cfg) {
    if (!cfg.hasPath("wave.fragments.applier.impl")) return;
    final String key = "wave.fragments.applier.impl";
    try {
      String impl = cfg.getString(key);
      String v = impl == null ? "" : impl.trim().toLowerCase();
      if (!("noop".equals(v) || "skeleton".equals(v) || "real".equals(v))) {
        LOG.severe("Invalid config: " + key + "='" + impl + "' (must be one of noop|skeleton|real)");
        throw new ConfigurationInitializationException(
            key + " must be one of noop|skeleton|real (got '" + impl + "')");
      }
    } catch (ConfigException e) {
      LOG.severe("Invalid type for config: " + key + ": " + e.getMessage());
      throw new ConfigurationInitializationException("Invalid type for " + key, e);
    }
  }

  private static void initializeFederation(Injector injector) {
    FederationTransport federationManager = injector.getInstance(FederationTransport.class);
    federationManager.startFederation();
  }

  private static void initializeSearch(Injector injector, WaveBus waveBus, Config config)
      throws WaveServerException {
    long startMs = System.currentTimeMillis();
    PerUserWaveViewDistpatcher waveViewDistpatcher =
        injector.getInstance(PerUserWaveViewDistpatcher.class);
    PerUserWaveViewBus.Listener listener = injector.getInstance(PerUserWaveViewBus.Listener.class);
    waveViewDistpatcher.addListener(listener);
    waveBus.subscribe(waveViewDistpatcher);

    WaveIndexer waveIndexer = injector.getInstance(WaveIndexer.class);
    waveIndexer.remakeIndex();

    // Register OT search wavelet updater AFTER PerUserWaveViewDistpatcher
    // so that the per-user wave view index is current before search updates.
    FeatureFlagStore featureFlagStore = injector.getInstance(FeatureFlagStore.class);
    initializeSearchWaveletUpdater(injector, waveBus, featureFlagStore);

    long elapsedMs = System.currentTimeMillis() - startMs;
    LOG.info("initializeSearch completed in " + elapsedMs + " ms");
  }

  private static void initializeShutdownHandler(final ServerRpcProvider server) {
    ShutdownManager.getInstance().register(new Shutdownable() {

      @Override
      public void shutdown() throws Exception {
        server.stopServer();
      }
    }, ServerMain.class.getSimpleName(), ShutdownPriority.Server);
  }
}

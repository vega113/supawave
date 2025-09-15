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
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.persistence.AccountStore;
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
import org.waveprotocol.box.server.rpc.*;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.server.stat.StatuszServlet;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.server.waveserver.*;
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
    PrintStream err = System.err;
    err.println(t.getClass().getName());
    for (StackTraceElement ste : t.getStackTrace()) {
      err.println("\tat " + ste.toString());
    }
    Throwable cause = t.getCause();
    Set<Throwable> seen = new HashSet<>();
    while (cause != null && !seen.contains(cause)) {
      seen.add(cause);
      err.println("Caused by: " + cause.getClass().getName());
      for (StackTraceElement ste : cause.getStackTrace()) {
        err.println("\tat " + ste.toString());
      }
      cause = cause.getCause();
    }
  }

  private static final Log LOG = Log.get(ServerMain.class);

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
    } catch (ConfigurationException e) {
      LOG.severe("ConfigurationException when running server:", e);
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
      ConfigurationException, WaveServerException {
    Injector injector = Guice.createInjector(coreSettings);
    Module profilingModule = injector.getInstance(StatModule.class);
    ExecutorsModule executorsModule = injector.getInstance(ExecutorsModule.class);
    injector = injector.createChildInjector(profilingModule, executorsModule);

    Config config = injector.getInstance(Config.class);

    Module serverModule = injector.getInstance(ServerModule.class);
    Module federationModule = buildFederationModule(injector);
    Module robotApiModule = new RobotApiModule();
    PersistenceModule persistenceModule = injector.getInstance(PersistenceModule.class);
    Module searchModule = injector.getInstance(SearchModule.class);
    Module profileFetcherModule = injector.getInstance(ProfileFetcherModule.class);
    injector = injector.createChildInjector(serverModule, persistenceModule, robotApiModule,
        federationModule, searchModule, profileFetcherModule);

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain = config.getString("core.wave_server_domain");
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    initializeServlets(server, config);
    initializeRobotAgents(server);
    initializeRobots(injector, waveBus);
    initializeFrontend(injector, server, waveBus);
    initializeFederation(injector);
    initializeSearch(injector, waveBus);
    initializeShutdownHandler(server);

    LOG.info("Starting server");
    server.startWebSocketServer(injector);
  }

  private static Module buildFederationModule(Injector settingsInjector)
      throws ConfigurationException {
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
                break;
              }
            }
          }
        }
      } catch (ConfigException e) {
        LOG.info("Failed reading client.flags.defaults.enableFragmentsApplier; defaulting to false", e);
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
        LOG.info("Fragments applier: enabled=" + applierEnabled + ", impl=" + applierCls + ", warnMs=" + warnMs);
        // If enabled on the server, mirror the flag to the client's wave.clientFlags so the
        // GWT client also wires its client-side applier (ClientStatsRawFragmentsApplier) and we
        // can observe activity via /dev/client-applier-stats.
        if (applierEnabled) {
          try {
            String cf = System.getProperty("wave.clientFlags");
            if (cf == null || !cf.contains("enableFragmentsApplier")) {
              System.setProperty("wave.clientFlags",
                  (cf == null || cf.isEmpty()) ? "enableFragmentsApplier=true"
                      : (cf + ",enableFragmentsApplier=true"));
            }
          } catch (Throwable ignore) { }
        }
      } catch (Throwable t) {
        LOG.warning("Failed to wire fragments applier instance; proceeding without applier", t);
      }
      if (config.hasPath("wave.fragments.applier.warnMs")) {
        System.setProperty("wave.fragments.applier.warnMs",
            Integer.toString(config.getInt("wave.fragments.applier.warnMs")));
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

  private static void initializeServlets(ServerRpcProvider server, Config config) {
    server.addServlet("/gadget/gadgetlist", GadgetProviderServlet.class);

    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);

    server.addServlet(SessionManager.SIGN_IN_URL, AuthenticationServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);

    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/fetch/*", FetchServlet.class);
    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/notification/*", NotificationServlet.class);

    server.addServlet("/robot/dataapi", DataApiServlet.class);
    server.addServlet(DataApiOAuthServlet.DATA_API_OAUTH_PATH + "/*", DataApiOAuthServlet.class);
    server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
    server.addServlet("/robot/register/*", RobotRegistrationServlet.class);
    server.addServlet("/robot/rpc", ActiveApiServlet.class);
    server.addServlet("/webclient/remote_logging", RemoteLoggingServiceImpl.class);
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    // Dev endpoint: client-side fragments applier stats (session-based)
    server.addServlet("/dev/client-applier-stats", ClientApplierStatsServlet.class);
    server.addServlet("/iniavatars/*", InitialsAvatarsServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    try {
      // Unified transport: single source of truth.
      String transport = readFragmentsTransport(config);
      if (transport == null || transport.isEmpty()) {
        transport = "off";
      }

      // Mirror effective transport + booleans into system properties so ConfigFactory.load()
      // (used by StatuszServlet) sees consistent values regardless of source.
      setEffectiveTransportSystemProperties(transport);

      if ("http".equals(transport) || "both".equals(transport)) {
        server.addServlet("/fragments/*", FragmentsServlet.class);
      } else {
        LOG.info("Fragments HTTP endpoint is disabled (effective transport='" + transport + "')");
      }
    } catch (Exception e) {
      LOG.warning("Failed to configure fragments transport/endpoints; leaving /fragments disabled", e);
    }

    String gadgetServerHostname = config.getString("core.gadget_server_hostname");
    int gadgetServerPort = config.getInt("core.gadget_server_port");
    LOG.info("Starting GadgetProxyServlet for " + gadgetServerHostname + ":" + gadgetServerPort);
    server.addTransparentProxy("/gadgets/*",
        "http://" + gadgetServerHostname + ":" + gadgetServerPort + "/gadgets", "/gadgets");

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
        return (v == null) ? null : v.trim().toLowerCase();
      }
    } catch (Throwable ignore) {}
    return null;
  }

  /**
   * Mirrors the configured transport into system properties so consumers using ConfigFactory.load()
   * can observe the effective values. Also injects a default client fragmentFetchMode when absent.
   */
  private static void setEffectiveTransportSystemProperties(String transport) {
    try { System.setProperty("server.fragments.transport", transport); } catch (Throwable ignore) {}
    boolean tHttp = "http".equals(transport) || "both".equals(transport);
    boolean tStream = "stream".equals(transport) || "both".equals(transport);
    System.setProperty("server.enableFragmentsHttp", Boolean.toString(tHttp));
    System.setProperty("server.enableFetchFragmentsRpc", Boolean.toString(tStream));
    String cf = System.getProperty("wave.clientFlags");
    if (cf == null || !cf.contains("fragmentFetchMode")) {
      String mode = tStream ? "stream" : (tHttp ? "http" : "off");
      System.setProperty("wave.clientFlags",
          (cf == null || cf.isEmpty()) ? ("fragmentFetchMode=" + mode)
              : (cf + ",fragmentFetchMode=" + mode));
    }
    // Also propagate selected client flag defaults when present in config
    try {
      String existing = System.getProperty("wave.clientFlags");
      StringBuilder sb = new StringBuilder(existing == null ? "" : existing);
      com.typesafe.config.Config cfg = com.typesafe.config.ConfigFactory.load();
      if (cfg.hasPath("client.flags.defaults.quasiDeletionDwellMs")) {
        int dwell = cfg.getInt("client.flags.defaults.quasiDeletionDwellMs");
        if (sb.length() > 0) sb.append(',');
        sb.append("quasiDeletionDwellMs=").append(dwell);
      }
      if (sb.length() > 0) {
        System.setProperty("wave.clientFlags", sb.toString());
      }
    } catch (Throwable ignore) {}
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

  private static void initializeFrontend(Injector injector, ServerRpcProvider server,
      WaveBus waveBus) throws WaveServerException {
    HashedVersionFactory hashFactory = injector.getInstance(HashedVersionFactory.class);

    WaveletProvider provider = injector.getInstance(WaveletProvider.class);
    WaveletInfo waveletInfo = WaveletInfo.create(hashFactory, provider);
    ClientFrontend frontend =
        ClientFrontendImpl.create(provider, waveBus, waveletInfo);

    ProtocolWaveClientRpc.Interface rpcImpl = WaveClientRpcImpl.create(frontend, false);
    server.registerService(ProtocolWaveClientRpc.newReflectiveService(rpcImpl));
    applyFragmentsConfig(injector.getInstance(com.typesafe.config.Config.class));
    wireFragmentsHandler(injector, provider);
    wireFragmentsFetchBridge(injector, provider);
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

  private static void initializeSearch(Injector injector, WaveBus waveBus)
      throws WaveServerException {
    PerUserWaveViewDistpatcher waveViewDistpatcher =
        injector.getInstance(PerUserWaveViewDistpatcher.class);
    PerUserWaveViewBus.Listener listener = injector.getInstance(PerUserWaveViewBus.Listener.class);
    waveViewDistpatcher.addListener(listener);
    waveBus.subscribe(waveViewDistpatcher);

    WaveIndexer waveIndexer = injector.getInstance(WaveIndexer.class);
    waveIndexer.remakeIndex();
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

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
import org.apache.commons.configuration.ConfigurationException;
import org.apache.wave.box.server.rpc.InitialsAvatarsServlet;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.box.server.frontend.ClientFrontend;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
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
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;

/**
 * Wave Server entrypoint.
 */
public class ServerMain {

  private static void printStackTraceLite(Throwable t) {
    java.io.PrintStream err = System.err;
    err.println(t.getClass().getName());
    for (StackTraceElement ste : t.getStackTrace()) {
      err.println("\tat " + ste.toString());
    }
    Throwable cause = t.getCause();
    java.util.Set<Throwable> seen = new java.util.HashSet<>();
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
    // Route java.util.logging through SLF4J (logback) early
    try {
      java.util.logging.LogManager.getLogManager().reset();
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
    } catch (Throwable ignore) {
      // If bridge not on classpath, continue without failing startup
    }
    // Ensure any uncaught exceptions are logged with a full stack trace
    try {
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
        try {
          // Avoid passing the Throwable to logging to prevent Guice/ASM formatting issues
          LOG.severe("Uncaught exception on thread " + thread.getName() + " (" + throwable.getClass().getName() + ")");
        } catch (Throwable ignored) {
          // Avoid throwing from the handler itself
        } finally {
          try {
            // Print minimal stack trace without invoking Throwable.toString()/getMessage()
            printStackTraceLite(throwable);
          } catch (Throwable ignore2) {
            // ignore
          }
        }
      });
    } catch (Throwable ignore) {
      // Best-effort; do not fail startup if handler cannot be set
    }
    try {
      Module coreSettings = new AbstractModule() {

        @Override
        protected void configure() {
          Config config = ConfigFactory.defaultOverrides()
              .withFallback(ConfigFactory.parseFile(new File("config/application.conf")))
              .withFallback(ConfigFactory.parseFile(new File("config/reference.conf")))
              .resolve();
          bind(Config.class).toInstance(config);
          bind(Key.get(String.class, Names.named(CoreSettingsNames.WAVE_SERVER_DOMAIN)))
              .toInstance(config.getString("core.wave_server_domain"));
          // Apply WebSocket tunables to system props for internal client usage
          try {
            if (config.hasPath("wave.websocket.connectTimeoutMs"))
              System.setProperty("wave.websocket.connectTimeoutMs", Integer.toString(config.getInt("wave.websocket.connectTimeoutMs")));
            if (config.hasPath("wave.websocket.connectWaitMs"))
              System.setProperty("wave.websocket.connectWaitMs", Integer.toString(config.getInt("wave.websocket.connectWaitMs")));
            if (config.hasPath("wave.websocket.maxBackoffMs"))
              System.setProperty("wave.websocket.maxBackoffMs", Integer.toString(config.getInt("wave.websocket.maxBackoffMs")));
            if (config.hasPath("wave.websocket.jitterFraction"))
              System.setProperty("wave.websocket.jitterFraction", Double.toString(config.getDouble("wave.websocket.jitterFraction")));
          } catch (Throwable t) {
            LOG.warning("Failed to apply wave.websocket config to system properties", t);
          }
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
      // Avoid passing Throwable into logging to bypass Guice/ASM message formatting
      LOG.severe("Unexpected fatal error when running server (" + t.getClass().getName() + ")");
      try {
        printStackTraceLite(t);
      } catch (Throwable ignore) {
        // ignore
      }
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

  private static void initializeServer(Injector injector, String waveDomain)
      throws PersistenceException, WaveServerException {
    AccountStore accountStore = injector.getInstance(AccountStore.class);
    accountStore.initializeAccountStore();
    AccountStoreHolder.init(accountStore, waveDomain);

    // Initialize the SignerInfoStore.
    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore)certPathStore).initializeSignerInfoStore();
    }

    // Initialize the server.
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
    server.addServlet("/iniavatars/*", InitialsAvatarsServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    // Optional fragment fetch endpoint for dynamic rendering Phase 6
    server.addServlet("/fragments/*", FragmentsServlet.class);

    String gadgetServerHostname = config.getString("core.gadget_server_hostname");
    int gadgetServerPort = config.getInt("core.gadget_server_port");
    LOG.info("Starting GadgetProxyServlet for " + gadgetServerHostname + ":" + gadgetServerPort);
    server.addTransparentProxy("/gadgets/*",
        "http://" + gadgetServerHostname + ":" + gadgetServerPort + "/gadgets", "/gadgets");

    server.addServlet("/", WaveClientServlet.class);

    // Security headers
    server.addFilter("/*", org.waveprotocol.box.server.security.SecurityHeadersFilter.class);

    // Static asset caching for /static/* and no-cache for GWT webclient
    server.addFilter("/static/*", org.waveprotocol.box.server.security.StaticCacheFilter.class);
    server.addFilter("/webclient/*", org.waveprotocol.box.server.security.NoCacheFilter.class);

    // Profiling
    server.addFilter("/*", RequestScopeFilter.class);
    boolean enableProfiling = config.getBoolean("core.enable_profiling");
    if (enableProfiling) {
      server.addFilter("/*", TimingFilter.class);
      server.addServlet(StatService.STAT_URL, StatuszServlet.class);
    }
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
    try {
      org.waveprotocol.box.server.frontend.WaveClientRpcImpl.setFragmentsHandler(
          new org.waveprotocol.box.server.frontend.FragmentsViewChannelHandler(
              injector.getInstance(org.waveprotocol.box.server.waveserver.WaveletProvider.class),
              injector.getInstance(com.typesafe.config.Config.class))
      );
    } catch (Throwable t) {
      LOG.warning("Failed to wire FragmentsViewChannelHandler; fragments RPC disabled", t);
    }

    // Optional: wire fragments fetch bridge for ViewChannelImpl
    try {
      org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelImpl.setFragmentsFetchBridge(
          new org.waveprotocol.box.server.frontend.FragmentsFetchBridgeImpl(provider,
              injector.getInstance(com.typesafe.config.Config.class))
      );
    } catch (Throwable t) {
      LOG.warning("Failed to wire FragmentsFetchBridge; ViewChannel.fetchFragments disabled", t);
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

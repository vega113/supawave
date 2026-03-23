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
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.dev.ClientApplierStatsJakartaServlet;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.rpc.*;
import org.waveprotocol.box.server.robots.ProfileFetcherModule;
import org.waveprotocol.box.server.robots.JakartaRobotApiBindingsModule;
import org.waveprotocol.box.server.robots.RobotRegistrationServlet;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiTokenServlet;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.waveserver.*;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;

public class ServerMain {
  private static final Log LOG = Log.get(ServerMain.class);

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

    injector = injector.createChildInjector(serverModule, persistenceModule,
        robotApiModule, searchModule, federationModule, profileFetcherModule);

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain = config.getString("core.wave_server_domain");
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    initializeServlets(server, config);
    initializeRobots(injector, waveBus);
    initializeFrontend(injector, server, waveBus);
    initializeSearch(injector, waveBus);
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

    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore)certPathStore).initializeSignerInfoStore();
    }

    WaveletProvider waveServer = injector.getInstance(WaveletProvider.class);
    waveServer.initialize();
  }

  private static void initializeServlets(ServerRpcProvider server, Config config) {
    server.addServlet("/gadget/gadgetlist", GadgetProviderServlet.class);
    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);
    server.addServlet("/auth/signin", AuthenticationServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);
    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/fetch/*", FetchServlet.class);
    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/dev/client-applier-stats", ClientApplierStatsJakartaServlet.class);
    server.addServlet("/healthz", HealthServlet.class);
    server.addServlet("/readyz", HealthServlet.class);
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    server.addServlet("/iniavatars/*", org.apache.wave.box.server.rpc.InitialsAvatarsServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    server.addServlet("/robot/register/*", RobotRegistrationServlet.class);
    server.addServlet("/robot/rpc", ActiveApiServlet.class);
    server.addServlet("/robot/dataapi", DataApiServlet.class);
    server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
    server.addServlet("/robot/dataapi/token", DataApiTokenServlet.class);
    server.addServlet("/", WaveClientServlet.class);
  }

  private static void initializeRobots(Injector injector, WaveBus waveBus) {
    RobotsGateway robotsGateway = injector.getInstance(RobotsGateway.class);
    waveBus.subscribe(robotsGateway);
  }

  private static void initializeFrontend(Injector injector, ServerRpcProvider server,
      WaveBus waveBus) throws WaveServerException {
    HashedVersionFactory hashFactory = injector.getInstance(HashedVersionFactory.class);
    WaveletProvider provider = injector.getInstance(WaveletProvider.class);
    WaveletInfo waveletInfo = WaveletInfo.create(hashFactory, provider);
    ClientFrontend frontend = ClientFrontendImpl.create(provider, waveBus, waveletInfo);
    org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc.Interface rpcImpl =
        WaveClientRpcImpl.create(frontend, false);
    server.registerService(org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc.newReflectiveService(rpcImpl));
  }

  private static void initializeFederation(Injector injector) {
    FederationTransport federationManager = injector.getInstance(FederationTransport.class);
    federationManager.startFederation();
  }

  private static void initializeSearch(Injector injector, WaveBus waveBus)
      throws WaveServerException {
    PerUserWaveViewDistpatcher waveViewDistpatcher = injector.getInstance(PerUserWaveViewDistpatcher.class);
    PerUserWaveViewBus.Listener listener = injector.getInstance(PerUserWaveViewBus.Listener.class);
    waveViewDistpatcher.addListener(listener);
    waveBus.subscribe(waveViewDistpatcher);
    WaveIndexer waveIndexer = injector.getInstance(WaveIndexer.class);
    waveIndexer.remakeIndex();
  }

  private static void initializeShutdownHandler(final ServerRpcProvider server) {
    ShutdownManager.getInstance().register(new Shutdownable() {
      @Override public void shutdown() throws Exception { server.stopServer(); }
    }, ServerMain.class.getSimpleName(), ShutdownPriority.Server);
  }
}

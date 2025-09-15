package org.waveprotocol.box.server.jakarta;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import java.util.concurrent.Executor;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;

@Singleton
public class ServerRpcProviderJakartaProvider implements Provider<ServerRpcProvider> {
  private final Config config;
  private final SessionManager sessionManager;
  private final SessionHandler sessionHandler;
  private final Executor clientServerExecutor;

  @Inject
  public ServerRpcProviderJakartaProvider(
      Config config,
      SessionManager sessionManager,
      SessionHandler sessionHandler,
      @ExecutorAnnotations.ClientServerExecutor Executor clientServerExecutor) {
    this.config = config;
    this.sessionManager = sessionManager;
    this.sessionHandler = sessionHandler;
    this.clientServerExecutor = clientServerExecutor;
  }

  @Override
  public ServerRpcProvider get() {
    // Use the @Inject constructor on the Jakarta ServerRpcProvider; constructing here
    // avoids Guice scanning the class' methods during injector creation.
    return new ServerRpcProvider(config, sessionManager, sessionHandler, clientServerExecutor);
  }
}


package org.waveprotocol.box.server.robots;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterModule;
import com.google.wave.api.robot.HttpRobotConnection;
import com.google.wave.api.robot.RobotConnection;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.waveprotocol.box.server.robots.active.ActiveApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.agent.LocalOperationSubmitter;
import org.waveprotocol.box.server.robots.agent.registration.RegistrationRobot;
import org.waveprotocol.box.server.robots.dataapi.DataApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.passive.RobotConnector;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.register.RobotRegistrarImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class JakartaRobotApiBindingsModule extends AbstractModule {
  private static final int NUMBER_OF_THREADS = 10;

  @Override
  protected void configure() {
    install(new EventDataConverterModule());
    install(new RobotSerializerModule());
    bind(RobotRegistrar.class).to(RobotRegistrarImpl.class).in(Singleton.class);
    bind(RobotCapabilityFetcher.class).to(RobotConnector.class);
    bind(LocalOperationSubmitter.class).in(Singleton.class);

    // Explicitly bind robot agents so Guice does not promote JIT bindings to a
    // parent injector.  Without these, ChildBindingAlreadySet errors occur
    // because the agents depend on AccountStore / RobotRegistrar which are
    // bound in sibling child modules.
    bind(RegistrationRobot.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  @Inject
  protected RobotConnector provideRobotConnector(RobotConnection connection,
                                                 RobotSerializer serializer) {
    return new RobotConnector(connection, serializer);
  }

  @Provides
  @Singleton
  protected RobotConnection provideRobotConnection() {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(50);
    cm.setDefaultMaxPerRoute(10);
    CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("RobotConnection").build();
    return new HttpRobotConnection(httpClient,
        Executors.newFixedThreadPool(NUMBER_OF_THREADS, threadFactory));
  }

  @Provides
  @Singleton
  @Named("GatewayExecutor")
  protected Executor provideGatewayExecutor() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("PassiveRobotRunner").build();
    return Executors.newFixedThreadPool(NUMBER_OF_THREADS, threadFactory);
  }

  @Provides
  @Singleton
  @Inject
  @Named("ActiveApiRegistry")
  protected OperationServiceRegistry provideActiveApiRegistry(Injector injector) {
    return new ActiveApiOperationServiceRegistry(injector);
  }

  @Provides
  @Singleton
  @Inject
  @Named("DataApiRegistry")
  protected OperationServiceRegistry provideDataApiRegistry(Injector injector) {
    return new DataApiOperationServiceRegistry(injector);
  }
}

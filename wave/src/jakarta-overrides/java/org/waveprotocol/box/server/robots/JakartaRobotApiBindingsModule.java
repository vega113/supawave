package org.waveprotocol.box.server.robots;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.data.converter.EventDataConverterModule;
import org.waveprotocol.box.server.robots.active.ActiveApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.dataapi.DataApiOperationServiceRegistry;

public final class JakartaRobotApiBindingsModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new EventDataConverterModule());
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

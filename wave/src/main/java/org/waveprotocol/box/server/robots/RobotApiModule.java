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

package org.waveprotocol.box.server.robots;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterModule;
import com.google.wave.api.robot.HttpRobotConnection;
import com.google.wave.api.robot.RobotConnection;

import com.typesafe.config.Config;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.waveprotocol.box.server.robots.active.ActiveApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.dataapi.DataApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.passive.RobotConnector;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Robot API Module.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class RobotApiModule extends AbstractModule {

  private static final int NUMBER_OF_THREADS = 10;

  @Override
  protected void configure() {
    install(new EventDataConverterModule());
    install(new RobotSerializerModule());

    bind(RobotCapabilityFetcher.class).to(RobotConnector.class);
  }

  @Provides
  @Inject
  @Singleton
  protected RobotConnector provideRobotConnector(
      RobotConnection connection, RobotSerializer serializer) {
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
    return new HttpRobotConnection(
        httpClient, Executors.newFixedThreadPool(NUMBER_OF_THREADS, threadFactory));
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

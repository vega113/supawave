/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.jakarta;

import com.google.common.collect.ImmutableMap;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationType;
import com.google.wave.api.ProtocolVersion;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import com.google.gson.Gson;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.impl.GsonFactory;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.robots.operations.NotifyOperationService;
import org.waveprotocol.box.server.robots.passive.RobotConnector;
import org.waveprotocol.wave.model.wave.ParticipantId;
import com.typesafe.config.ConfigFactory;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Verifies Jakarta NotifyOperationService behaviour for robot capability refresh.
 */
public final class NotifyOperationServiceJakartaIT {
  private static final String ROBOT_ID = "notifybot@example.com";
  private static final String ROBOT_URL = "https://robots.example.com/notify";
  private static final String CONSUMER_SECRET = "secret";

  private ParticipantId robotParticipant;
  private InMemoryAccountStore accountStore;
  private ConfigurableRobotConnection connection;
  private NotifyOperationService service;

  @Before
  public void setUp() throws Exception {
    robotParticipant = ParticipantId.ofUnsafe(ROBOT_ID);
    accountStore = new InMemoryAccountStore();
    connection = new ConfigurableRobotConnection("hash-new");
    RobotSerializer serializer = createSerializer();
    RobotConnector connector = new RobotConnector(connection, serializer);
    service = new NotifyOperationService(accountStore, connector, ConfigFactory.empty());
  }

  @Test
  public void matchingCapabilitiesHashShortCircuitsFetch() throws Exception {
    accountStore.putAccount(newRobotAccount("hash-current"));
    connection.setCapabilitiesHash("hash-current");

    OperationRequest request = new OperationRequest(
        OperationType.ROBOT_NOTIFY.name(),
        "op-matching",
        OperationRequest.Parameter.of(ParamsProperty.CAPABILITIES_HASH, "hash-current"));
    OperationContextImpl context = new OperationContextImpl(null, null, null);

    service.execute(request, context, robotParticipant);

    JsonRpcResponse response = context.getResponse(request.getId());
    assertNotNull(response);
    assertFalse(response.isError());
    assertTrue(response.getData().isEmpty());
    assertNull("capabilities fetch should not run when hashes match", connection.getLastRequestedHash());
  }

  @Test
  public void mismatchedHashFetchesAndUpdatesAccount() throws Exception {
    accountStore.putAccount(newRobotAccount("hash-old"));
    connection.setCapabilitiesHash("hash-new");

    OperationRequest request = new OperationRequest(
        OperationType.ROBOT_NOTIFY.name(),
        "op-refresh",
        OperationRequest.Parameter.of(ParamsProperty.CAPABILITIES_HASH, "hash-new"));
    OperationContextImpl context = new OperationContextImpl(null, null, null);

    service.execute(request, context, robotParticipant);

    RobotAccountData updated = accountStore.getAccount(robotParticipant).asRobot();
    assertEquals("hash-new", updated.getCapabilities().getCapabilitiesHash());

    JsonRpcResponse response = context.getResponse(request.getId());
    assertNotNull(response);
    assertFalse(response.isError());
    assertTrue(response.getData().isEmpty());
    assertEquals("hash-new", connection.getLastRequestedHash());
  }

  private RobotAccountData newRobotAccount(String capabilitiesHash) {
    RobotCapabilities capabilities = new RobotCapabilities(ImmutableMap.of(), capabilitiesHash,
        ProtocolVersion.DEFAULT);
    return new RobotAccountDataImpl(robotParticipant, ROBOT_URL, CONSUMER_SECRET, capabilities, true);
  }

  private static RobotSerializer createSerializer() {
    NavigableMap<ProtocolVersion, Gson> gsons = new java.util.TreeMap<>();
    Gson gson = new GsonFactory().create();
    gsons.put(ProtocolVersion.V2_2, gson);
    gsons.put(ProtocolVersion.V2_1, gson);

    GsonFactory v2Factory = new GsonFactory();
    gsons.put(ProtocolVersion.V2, v2Factory.create());
    return new RobotSerializer(gsons, ProtocolVersion.DEFAULT);
  }

  private static final class InMemoryAccountStore implements AccountStore {
    private final Map<ParticipantId, AccountData> store = new ConcurrentHashMap<>();

    @Override
    public void initializeAccountStore() {}

    @Override
    public AccountData getAccount(ParticipantId id) {
      return store.get(id);
    }

    @Override
    public void putAccount(AccountData account) {
      store.put(account.getId(), account);
    }

    @Override
    public void removeAccount(ParticipantId id) {
      store.remove(id);
    }
  }

  /** RobotConnection stub returning deterministic capabilities XML. */
  private static final class ConfigurableRobotConnection implements com.google.wave.api.robot.RobotConnection {
    private volatile String capabilitiesHash;
    private volatile String lastRequestedHash;

    ConfigurableRobotConnection(String initialHash) {
      this.capabilitiesHash = initialHash;
    }

    void setCapabilitiesHash(String hash) {
      this.capabilitiesHash = hash;
    }

    String getLastRequestedHash() {
      return lastRequestedHash;
    }

    @Override
    public String get(String url) {
      lastRequestedHash = capabilitiesHash;
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">"
          + "<w:capabilities><w:capability name=\"wavelet_self_added\"/></w:capabilities>"
          + "<w:version>" + capabilitiesHash + "</w:version>"
          + "<w:protocolversion>0.22</w:protocolversion>"
          + "</w:robot>";
    }

    @Override
    public com.google.common.util.concurrent.ListenableFuture<String> asyncGet(String url) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String postJson(String url, String jsonBody) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.google.common.util.concurrent.ListenableFuture<String> asyncPostJson(String url, String jsonBody) {
      throw new UnsupportedOperationException();
    }
  }
}

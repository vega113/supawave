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

package org.waveprotocol.box.server.robots.passive;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.event.EventType;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.robot.Capability;
import com.google.wave.api.robot.RobotConnection;
import com.google.wave.api.robot.RobotConnectionException;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unit test for the {@link RobotConnector}.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class RobotConnectorTest extends TestCase {

  private static final String CAPABILITIES_HASH = "0x42ea590";
  private static final String CAPABILITIES_XML =
      "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\"> " + "<w:version>"
          + CAPABILITIES_HASH + "</w:version> " + "<w:protocolversion>0.22</w:protocolversion> "
          + "<w:capabilities> " + "<w:capability name=\"OPERATION_ERROR\"/> "
          + "<w:capability name=\"WAVELET_SELF_ADDED\"/> " + "</w:capabilities>" + "</w:robot> ";
  private static final String TWO_LEGGED_CAPABILITIES_XML =
      "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">"
          + "<w:version>" + CAPABILITIES_HASH + "</w:version>"
          + "<w:protocolversion>0.22</w:protocolversion>"
          + "<w:capabilities><w:capability name=\"WAVELET_SELF_ADDED\"/></w:capabilities>"
          + "<w:consumer_keys>"
          + "<w:consumer_key for=\"https://wave.example.com/robot/rpc\">test@example.com</w:consumer_key>"
          + "</w:consumer_keys>"
          + "</w:robot>";
  private static final String THREE_LEGGED_CAPABILITIES_XML =
      "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">"
          + "<w:version>" + CAPABILITIES_HASH + "</w:version>"
          + "<w:protocolversion>0.22</w:protocolversion>"
          + "<w:capabilities><w:capability name=\"WAVELET_SELF_ADDED\"/></w:capabilities>"
          + "<w:consumer_keys>"
          + "<w:consumer_key for=\"https://wave.example.com/robot/dataapi/rpc\">test@example.com</w:consumer_key>"
          + "</w:consumer_keys>"
          + "</w:robot>";
  private static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.DEFAULT;
  private static final String ROBOT_ACCOUNT_NAME = "test@example.com";
  private static final String TEST_URL = "www.example.com/robot";
  private static final String TEST_RPC_ENDPOINT = TEST_URL + Robot.RPC_URL;
  private static final String TEST_CAPABILITIES_ENDPOINT = TEST_URL + Robot.CAPABILITIES_URL;
  private static final String ABSOLUTE_BASE_URL = "http://www.example.com/myrobot";
  private static final String ABSOLUTE_RPC_ENDPOINT = ABSOLUTE_BASE_URL + Robot.RPC_URL;
  private static final String ABSOLUTE_CAPABILITIES_ENDPOINT =
      ABSOLUTE_BASE_URL + Robot.CAPABILITIES_URL;
  private static final RobotAccountData ROBOT_ACCOUNT =
      new RobotAccountDataImpl(ParticipantId.ofUnsafe(ROBOT_ACCOUNT_NAME), TEST_URL, "secret",
          new RobotCapabilities(
              Maps.<EventType, Capability> newHashMap(), "FakeHash", ProtocolVersion.DEFAULT),
          true, 0L, null, "", 0L, 1234L, false, 7L, 5678L);
  private static final RobotAccountData ABSOLUTE_ROBOT_ACCOUNT =
      new RobotAccountDataImpl(ParticipantId.ofUnsafe(ROBOT_ACCOUNT_NAME), ABSOLUTE_RPC_ENDPOINT,
          "secret", new RobotCapabilities(
              Maps.<EventType, Capability> newHashMap(), "FakeHash", ProtocolVersion.DEFAULT),
          true);

  private static final EventMessageBundle BUNDLE =
      new EventMessageBundle(ROBOT_ACCOUNT_NAME, "www.example.com/rpc");
  private static final String SERIALIZED_BUNDLE = "BUNDLE";
  private static final String RETURNED_OPERATION = "OPERATION";

  private RobotConnection connection;
  private RobotSerializer serializer;
  private RobotConnector connector;
  private Robot robot;

  @Override
  protected void setUp() throws Exception {
    connection = mock(RobotConnection.class);
    serializer = mock(RobotSerializer.class);
    connector = new RobotConnector(connection, serializer);

    robot = mock(Robot.class);
    when(robot.getAccount()).thenReturn(ROBOT_ACCOUNT);
  }

  public void testSuccessfulSendMessageBundle() throws Exception {
    final List<OperationRequest> expectedOperations = Collections.unmodifiableList(
        Lists.newArrayList(new OperationRequest("wavelet.setTitle", "op1")));

    when(serializer.serialize(BUNDLE, PROTOCOL_VERSION)).thenReturn(SERIALIZED_BUNDLE);
    when(connection.postJson(TEST_RPC_ENDPOINT, SERIALIZED_BUNDLE)).thenReturn(RETURNED_OPERATION);
    when(serializer.deserializeOperations(RETURNED_OPERATION)).thenReturn(expectedOperations);

    List<OperationRequest> operations =
        connector.sendMessageBundle(BUNDLE, robot, PROTOCOL_VERSION);
    assertEquals(expectedOperations, operations);
  }

  public void testSendMessageBundleAppendsRpcUrlWhenStoredUrlOnlyContainsRpcSubstring()
      throws Exception {
    final List<OperationRequest> expectedOperations = Collections.unmodifiableList(
        Lists.newArrayList(new OperationRequest("wavelet.setTitle", "op1")));
    final String storedUrl = "http://www.example.com/myrobot/_wave/robot/jsonrpc-extra";
    final String expectedUrl = storedUrl + Robot.RPC_URL;
    RobotAccountData account =
        new RobotAccountDataImpl(ParticipantId.ofUnsafe(ROBOT_ACCOUNT_NAME), storedUrl, "secret",
            new RobotCapabilities(
                Maps.<EventType, Capability> newHashMap(), "FakeHash",
                ProtocolVersion.DEFAULT), true);

    when(robot.getAccount()).thenReturn(account);
    when(serializer.serialize(BUNDLE, PROTOCOL_VERSION)).thenReturn(SERIALIZED_BUNDLE);
    when(connection.postJson(storedUrl, SERIALIZED_BUNDLE)).thenReturn(RETURNED_OPERATION);
    when(connection.postJson(expectedUrl, SERIALIZED_BUNDLE)).thenReturn(RETURNED_OPERATION);
    when(serializer.deserializeOperations(RETURNED_OPERATION)).thenReturn(expectedOperations);

    List<OperationRequest> operations =
        connector.sendMessageBundle(BUNDLE, robot, PROTOCOL_VERSION);
    assertEquals(expectedOperations, operations);
    verify(connection).postJson(expectedUrl, SERIALIZED_BUNDLE);
  }

  public void testSendMessageBundleKeepsAbsoluteRpcEndpointWhenStoredUrlAlreadyIncludesIt()
      throws Exception {
    final List<OperationRequest> expectedOperations = Collections.unmodifiableList(
        Lists.newArrayList(new OperationRequest("wavelet.setTitle", "op1")));
    RobotAccountData account =
        new RobotAccountDataImpl(ParticipantId.ofUnsafe(ROBOT_ACCOUNT_NAME),
            ABSOLUTE_RPC_ENDPOINT, "secret",
            new RobotCapabilities(
                Maps.<EventType, Capability> newHashMap(), "FakeHash",
                ProtocolVersion.DEFAULT), true);

    when(robot.getAccount()).thenReturn(account);
    when(serializer.serialize(BUNDLE, PROTOCOL_VERSION)).thenReturn(SERIALIZED_BUNDLE);
    when(connection.postJson(ABSOLUTE_RPC_ENDPOINT, SERIALIZED_BUNDLE)).thenReturn(
        RETURNED_OPERATION);
    when(serializer.deserializeOperations(RETURNED_OPERATION)).thenReturn(expectedOperations);

    List<OperationRequest> operations =
        connector.sendMessageBundle(BUNDLE, robot, PROTOCOL_VERSION);
    assertEquals(expectedOperations, operations);
    verify(connection).postJson(ABSOLUTE_RPC_ENDPOINT, SERIALIZED_BUNDLE);
  }

  public void testConnectionFailsSafely() throws Exception {
    when(serializer.serialize(BUNDLE, PROTOCOL_VERSION)).thenReturn(SERIALIZED_BUNDLE);
    when(connection.postJson(TEST_RPC_ENDPOINT, SERIALIZED_BUNDLE)).thenThrow(
        new RobotConnectionException("Connection Failed"));

    List<OperationRequest> operations =
        connector.sendMessageBundle(BUNDLE, robot, PROTOCOL_VERSION);
    assertNull("Expected null on connection failure to distinguish from successful empty response",
        operations);
  }

  public void testDeserializationFailsSafely() throws Exception {
    when(serializer.serialize(BUNDLE, PROTOCOL_VERSION)).thenReturn(SERIALIZED_BUNDLE);
    when(connection.postJson(TEST_RPC_ENDPOINT, SERIALIZED_BUNDLE)).thenReturn(RETURNED_OPERATION);
    when(serializer.deserializeOperations(RETURNED_OPERATION)).thenThrow(
        new InvalidRequestException("Invalid Request"));

    List<OperationRequest> operations =
        connector.sendMessageBundle(BUNDLE, robot, PROTOCOL_VERSION);
    assertNull("Expected null on deserialization failure to distinguish from successful empty response",
        operations);
  }

  public void testFetchCapabilities() throws Exception {
    when(connection.get(TEST_CAPABILITIES_ENDPOINT)).thenReturn(CAPABILITIES_XML);

    RobotAccountData accountData = connector.fetchCapabilities(ROBOT_ACCOUNT, "");

    RobotCapabilities capabilities = accountData.getCapabilities();
    assertEquals("Expected capabilities hash as specified in the xml", CAPABILITIES_HASH,
        capabilities.getCapabilitiesHash());
    assertEquals("Expected protocol version as specified in the xml", ProtocolVersion.V2_2,
        capabilities.getProtocolVersion());
    Map<EventType, Capability> capabilitiesMap = capabilities.getCapabilitiesMap();
    assertTrue("Expected capabilities as specified in the xml", capabilitiesMap.size() == 2);
    assertTrue("Expected capabilities as specified in the xml",
        capabilitiesMap.containsKey(EventType.WAVELET_SELF_ADDED));
    assertTrue("Expected capabilities as specified in the xml",
        capabilitiesMap.containsKey(EventType.OPERATION_ERROR));
    assertTrue("Expected a fresh update timestamp",
        accountData.getUpdatedAtMillis() > ROBOT_ACCOUNT.getUpdatedAtMillis());
    assertEquals("Expected token version to be preserved", ROBOT_ACCOUNT.getTokenVersion(),
        accountData.getTokenVersion());
    assertEquals("Expected last-active timestamp to be preserved",
        ROBOT_ACCOUNT.getLastActiveAtMillis(), accountData.getLastActiveAtMillis());
    // Only one connection should be made
    verify(connection).get(TEST_CAPABILITIES_ENDPOINT);
  }

  public void testFetchCapabilitiesPreservesCustomBasePathWhenRobotUrlIncludesRpcEndpoint()
      throws Exception {
    when(connection.get(ABSOLUTE_CAPABILITIES_ENDPOINT)).thenReturn(CAPABILITIES_XML);
    when(connection.get("http://www.example.com/_wave/capabilities.xml")).thenReturn(
        CAPABILITIES_XML);

    RobotAccountData accountData = connector.fetchCapabilities(ABSOLUTE_ROBOT_ACCOUNT, "");

    RobotCapabilities capabilities = accountData.getCapabilities();
    assertEquals("Expected capabilities hash as specified in the xml", CAPABILITIES_HASH,
        capabilities.getCapabilitiesHash());
    assertEquals("Expected protocol version as specified in the xml", ProtocolVersion.V2_2,
        capabilities.getProtocolVersion());
    verify(connection).get(ABSOLUTE_CAPABILITIES_ENDPOINT);
  }

  public void testFetchCapabilitiesPrefersRobotRpcWhenCapabilitiesAdvertiseTwoLeggedOauth()
      throws Exception {
    when(connection.get(TEST_CAPABILITIES_ENDPOINT)).thenReturn(TWO_LEGGED_CAPABILITIES_XML);

    RobotAccountData accountData =
        connector.fetchCapabilities(ROBOT_ACCOUNT, "https://wave.example.com/robot/rpc");

    assertEquals("Expected two-legged passive robots to advertise the active RPC endpoint",
        "https://wave.example.com/robot/rpc", accountData.getCapabilities().getRpcServerUrl());
  }

  public void testFetchCapabilitiesFallsBackToDataApiRpcWhenOnlyThreeLeggedOauthIsAdvertised()
      throws Exception {
    when(connection.get(TEST_CAPABILITIES_ENDPOINT)).thenReturn(THREE_LEGGED_CAPABILITIES_XML);

    RobotAccountData accountData =
        connector.fetchCapabilities(ROBOT_ACCOUNT, "https://wave.example.com/robot/rpc");

    assertEquals("Expected three-legged passive robots to advertise the Data API RPC endpoint",
        "https://wave.example.com/robot/dataapi/rpc",
        accountData.getCapabilities().getRpcServerUrl());
  }

  public void testFetchCapabilitiesPreservesAbsoluteBasePathWithoutRpcEndpoint()
      throws Exception {
    RobotAccountData account =
        new RobotAccountDataImpl(ParticipantId.ofUnsafe(ROBOT_ACCOUNT_NAME), ABSOLUTE_BASE_URL,
            "secret", new RobotCapabilities(
                Maps.<EventType, Capability> newHashMap(), "FakeHash",
                ProtocolVersion.DEFAULT), true);

    when(connection.get(ABSOLUTE_BASE_URL)).thenReturn(CAPABILITIES_XML);
    when(connection.get(ABSOLUTE_CAPABILITIES_ENDPOINT)).thenReturn(CAPABILITIES_XML);

    RobotAccountData accountData = connector.fetchCapabilities(account, "");

    RobotCapabilities capabilities = accountData.getCapabilities();
    assertEquals("Expected capabilities hash as specified in the xml", CAPABILITIES_HASH,
        capabilities.getCapabilitiesHash());
    assertEquals("Expected protocol version as specified in the xml", ProtocolVersion.V2_2,
        capabilities.getProtocolVersion());
    verify(connection).get(ABSOLUTE_CAPABILITIES_ENDPOINT);
  }
}

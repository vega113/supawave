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

import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.box.server.frontend.FragmentsViewChannelHandler;
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
import org.waveprotocol.box.server.waveserver.WaveletProvider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public final class ServerMainStructuredLoggingTest {

  @After
  public void clearFragmentsHandler() {
    WaveClientRpcImpl.setFragmentsHandler(null);
  }

  @Test
  public void structuredLoggingStatusMentionsWaveJsonLogPath() {
    assertTrue(ServerMain.structuredLoggingStatusMessage().contains("logs/wave-json.log"));
  }

  @Test
  public void installFragmentsHandlerWiresWaveClientRpcOpenPath() {
    WaveClientRpcImpl.setFragmentsHandler(null);

    FragmentsViewChannelHandler handler =
        ServerMain.installFragmentsHandlerForFrontend(
            mock(WaveletProvider.class),
            ConfigFactory.parseString("server.fragments.transport = stream"));

    assertTrue(handler.isEnabled());
    assertSame(handler, WaveClientRpcImpl.getFragmentsHandlerForTesting());
  }

  @Test
  public void installFragmentsHandlerStillWiresDisabledHandler() {
    WaveClientRpcImpl.setFragmentsHandler(null);

    FragmentsViewChannelHandler handler =
        ServerMain.installFragmentsHandlerForFrontend(
            mock(WaveletProvider.class),
            ConfigFactory.parseString("server.fragments.transport = off"));

    assertFalse(handler.isEnabled());
    assertSame(handler, WaveClientRpcImpl.getFragmentsHandlerForTesting());
  }
}

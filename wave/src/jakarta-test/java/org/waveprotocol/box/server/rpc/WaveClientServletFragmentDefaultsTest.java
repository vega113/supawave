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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Properties;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.wave.common.bootstrap.FlagConstants;

public final class WaveClientServletFragmentDefaultsTest {
  private Properties originalProperties;

  @Before
  public void captureSystemProperties() {
    originalProperties = (Properties) System.getProperties().clone();
    System.clearProperty("wave.clientFlags");
  }

  @After
  public void restoreSystemProperties() {
    System.setProperties((Properties) originalProperties.clone());
  }

  @Test
  public void fragmentDefaultsComeFromConfigWhenClientFlagsPropertyIsAbsent() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n" +
        "server.fragments.transport=\"stream\"\n" +
        "wave.fragments.forceClientApplier=true");

    JSONObject flags = getClientFlags(config);

    assertEquals("stream", flags.getString(FlagConstants.FRAGMENT_FETCH_MODE));
    assertTrue(flags.getBoolean(FlagConstants.FORCE_CLIENT_FRAGMENTS));
  }

  @Test
  public void explicitClientFlagsOverrideDerivedFragmentDefaults() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n" +
        "server.fragments.transport=\"stream\"\n" +
        "wave.fragments.forceClientApplier=true");
    System.setProperty(
        "wave.clientFlags",
        "fragmentFetchMode=off,forceClientFragments=false,enableDynamicRendering=true");

    JSONObject flags = getClientFlags(config);

    assertEquals("off", flags.getString(FlagConstants.FRAGMENT_FETCH_MODE));
    assertFalse(flags.getBoolean(FlagConstants.FORCE_CLIENT_FRAGMENTS));
    assertTrue(flags.getBoolean(FlagConstants.ENABLE_DYNAMIC_RENDERING));
  }

  @Test
  public void objectDefaultsBeatDerivedFragmentDefaults() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n" +
        "server.fragments.transport=\"stream\"\n" +
        "client.flags.defaults.fragmentFetchMode=\"off\"");

    JSONObject flags = getClientFlags(config);

    assertEquals("off", flags.getString(FlagConstants.FRAGMENT_FETCH_MODE));
  }

  @Test
  public void csvDefaultsStillApplyWhenObjectDefaultsAreAbsent() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n" +
        "client.flags.defaults=\"enableDynamicRendering=true\"");

    JSONObject flags = getClientFlags(config);

    assertTrue(flags.getBoolean(FlagConstants.ENABLE_DYNAMIC_RENDERING));
  }

  @Test
  public void requestFlagsAreTypedWithoutClientFlagsBaseReflection() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n");

    WaveClientServlet servlet = new WaveClientServlet(
        "example.com", config, mock(SessionManager.class),
        mock(AccountStore.class), mockVersionServlet(),
        mock(WavePreRenderer.class),
        mock(J2clSelectedWaveSnapshotRenderer.class),
        mock(FeatureFlagService.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    Enumeration<String> names = new Vector<>(java.util.List.of(
        "forceClientFragments",
        "fragmentsApplierMaxRanges",
        "fragmentFetchMode")).elements();
    when(request.getParameterNames()).thenReturn(names);
    when(request.getParameter("forceClientFragments")).thenReturn("true");
    when(request.getParameter("fragmentsApplierMaxRanges")).thenReturn("7");
    when(request.getParameter("fragmentFetchMode")).thenReturn("stream");

    JSONObject flags = servlet.getClientFlags(request);

    assertTrue(flags.getBoolean(FlagConstants.FORCE_CLIENT_FRAGMENTS));
    assertEquals(7, flags.getInt(FlagConstants.FRAGMENTS_APPLIER_MAX_RANGES));
    assertEquals("stream", flags.getString(FlagConstants.FRAGMENT_FETCH_MODE));
  }

  private static JSONObject getClientFlags(Config config) throws Exception {
    WaveClientServlet servlet = new WaveClientServlet(
        "example.com", config, mock(SessionManager.class),
        mock(AccountStore.class), mockVersionServlet(),
        mock(WavePreRenderer.class),
        mock(J2clSelectedWaveSnapshotRenderer.class),
        mock(FeatureFlagService.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());

    return servlet.getClientFlags(request);
  }

  private static VersionServlet mockVersionServlet() {
    return new VersionServlet("test", 0L);
  }
}

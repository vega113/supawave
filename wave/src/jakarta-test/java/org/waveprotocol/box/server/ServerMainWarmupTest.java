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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewProvider;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.box.server.waveserver.WaveletStateException;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class ServerMainWarmupTest {

  @Test
  public void skipsWarmupWhenFlagIsFalse() {
    Injector injector = mock(Injector.class);
    Config config = config("search.startup_wave_view_warmup = false");

    ServerMain.warmUpWaveView(injector, config);

    verifyZeroInteractions(injector);
  }

  @Test
  public void skipsWarmupWhenFlagIsAbsent() {
    Injector injector = mock(Injector.class);
    Config config = config("");

    ServerMain.warmUpWaveView(injector, config);

    verifyZeroInteractions(injector);
  }

  @Test
  public void warmupUsesConfiguredOwnerAddress() {
    Injector injector = mock(Injector.class);
    PerUserWaveViewProvider viewProvider = mock(PerUserWaveViewProvider.class);
    when(injector.getInstance(PerUserWaveViewProvider.class)).thenReturn(viewProvider);
    Config config = config(
        "search.startup_wave_view_warmup = true\n"
            + "core.owner_address = \"vega@local.net\"");

    ServerMain.warmUpWaveView(injector, config);

    verify(viewProvider).retrievePerUserWaveView(ParticipantId.ofUnsafe("vega@local.net"));
  }

  @Test
  public void warmupAppendsWaveDomainForBareOwnerName() {
    Injector injector = mock(Injector.class);
    PerUserWaveViewProvider viewProvider = mock(PerUserWaveViewProvider.class);
    when(injector.getInstance(PerUserWaveViewProvider.class)).thenReturn(viewProvider);
    Config config = config(
        "search.startup_wave_view_warmup = true\n"
            + "core.owner_address = \"vega\"");

    ServerMain.warmUpWaveView(injector, config);

    verify(viewProvider).retrievePerUserWaveView(ParticipantId.ofUnsafe("vega@local.net"));
  }

  @Test
  public void warmupLoadsWaveMapWhenOwnerAddressIsBlank() throws Exception {
    Injector injector = mock(Injector.class);
    WaveMap waveMap = mock(WaveMap.class);
    when(injector.getInstance(WaveMap.class)).thenReturn(waveMap);
    Config config = config(
        "search.startup_wave_view_warmup = true\n"
            + "core.owner_address = \"\"");

    ServerMain.warmUpWaveView(injector, config);

    verify(waveMap).loadAllWavelets();
  }

  @Test
  public void warmupLoadsWaveMapWhenOwnerAddressIsMissing() throws Exception {
    Injector injector = mock(Injector.class);
    WaveMap waveMap = mock(WaveMap.class);
    when(injector.getInstance(WaveMap.class)).thenReturn(waveMap);
    Config config = config("search.startup_wave_view_warmup = true");

    ServerMain.warmUpWaveView(injector, config);

    verify(waveMap).loadAllWavelets();
  }

  @Test
  public void warmupSwallowsWaveMapLoadFailures() throws Exception {
    Injector injector = mock(Injector.class);
    WaveMap waveMap = mock(WaveMap.class);
    when(injector.getInstance(WaveMap.class)).thenReturn(waveMap);
    doThrow(new WaveletStateException("boom")).when(waveMap).loadAllWavelets();
    Config config = config(
        "search.startup_wave_view_warmup = true\n"
            + "core.owner_address = \"\"");

    ServerMain.warmUpWaveView(injector, config);

    verify(waveMap).loadAllWavelets();
  }

  private static Config config(String body) {
    return ConfigFactory.parseString(body)
        .withFallback(ConfigFactory.parseString("core.wave_server_domain = \"local.net\""))
        .resolve();
  }
}

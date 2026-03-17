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

import static org.junit.Assert.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.box.server.frontend.ManifestOrderCache;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;

public final class ServerMainConfigValidationTest {

  @After
  public void resetCaches() {
    // best effort: restore sane defaults
    ManifestOrderCache.resetToDefaults();
    SegmentWaveletStateRegistry.setMaxEntries(1024);
    SegmentWaveletStateRegistry.setTtlMs(300_000L);
    SegmentWaveletStateRegistry.clearForTests();
  }

  @Test(expected = RuntimeException.class)
  public void invalidSegmentRegistryMaxEntriesFailsStartup() {
    Config cfg = ConfigFactory.parseString("server.segmentStateRegistry.maxEntries=0");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidSegmentRegistryTtlFailsStartup() {
    Config cfg = ConfigFactory.parseString("server.segmentStateRegistry.ttlMs=-1");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidManifestCacheMaxEntriesFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.manifestOrderCache.maxEntries=0");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidManifestCacheTtlFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.manifestOrderCache.ttlMs=-1");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test
  public void validValuesApply() {
    Config cfg = ConfigFactory.parseString(
        "server.segmentStateRegistry.maxEntries=64, server.segmentStateRegistry.ttlMs=1000, " +
        "wave.fragments.manifestOrderCache.maxEntries=32, wave.fragments.manifestOrderCache.ttlMs=2000");
    ServerMain.applyFragmentsConfig(cfg); // should not throw
  }

  @Test
  public void defaultsApplyWhenAllKeysOmitted() {
    // No fragments/caches keys provided — should use defaults and not throw
    Config cfg = ConfigFactory.parseString("core.enable_profiling=false");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test
  public void defaultsApplyWhenSomeKeysOmitted() {
    // Provide only one key; others are omitted and should keep defaults
    Config cfg = ConfigFactory.parseString("server.segmentStateRegistry.maxEntries=128");
    ServerMain.applyFragmentsConfig(cfg);
  }
}

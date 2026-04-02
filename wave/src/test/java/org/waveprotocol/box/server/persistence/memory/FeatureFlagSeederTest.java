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
package org.waveprotocol.box.server.persistence.memory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagService;

public final class FeatureFlagSeederTest {
  @Test
  public void seedsOtSearchEnabledWhenConfigEnablesIt() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = true"));

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void seedsOtSearchDisabledWhenConfigDisablesIt() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = false"));

    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.getEnabledFlagNames(null).contains("ot-search"));
  }
}

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class FeatureFlagSeederTest {
  @Test
  public void seedsOtSearchEnabledWhenConfigEnablesIt() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = true"));

    assertTrue(store.get("ot-search") != null);

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void seedsOtSearchDisabledWhenConfigDisablesIt() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = false"));

    assertTrue(store.get("ot-search") != null);

    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void preservesExistingEnabledStateWhenSeedingOtSearchFlag() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            true,
            Collections.emptyMap()));

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = false"));

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(store.get("ot-search").isEnabled());
    assertTrue(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void preservesExistingAllowedUsersWhenSeedingOtSearchFlag() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            false,
            allowedUsers(true)));

    FeatureFlagSeeder.seedSearchFeatureFlags(
        store, ConfigFactory.parseString("search.ot_search_enabled = false"));

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.isEnabled("ot-search", "vega@supawave.ai"));
    assertFalse(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void seedsSocialAuthEnabledWhenConfigEnablesIt() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedSocialAuthFeatureFlag(
        store, ConfigFactory.parseString("core.social_auth.enabled = true"));

    assertTrue(store.get("social-auth") != null);

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.isGloballyEnabled("social-auth"));
  }

  @Test
  public void preservesExistingSocialAuthFlagWhenConfigChanges() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(new FeatureFlag("social-auth", "Social sign-in", false, allowedUsers(true)));

    FeatureFlagSeeder.seedSocialAuthFeatureFlag(
        store, ConfigFactory.parseString("core.social_auth.enabled = true"));

    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.isGloballyEnabled("social-auth"));
    assertTrue(service.isEnabled("social-auth", "vega@supawave.ai"));
  }

  @Test
  public void searchWaveletUpdaterIsEnabledWhenFlagHasAllowlist() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            false,
            allowedUsers(true)));

    assertTrue(FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(store));
  }

  @Test
  public void searchWaveletUpdaterIsEnabledWhenFlagGloballyEnabled() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(new FeatureFlag("ot-search",
        "Real-time search wavelets (replaces 15s polling)",
        true, Collections.emptyMap()));

    assertTrue(FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(store));
  }

  private static Map<String, Boolean> allowedUsers(boolean enabled) {
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("vega@supawave.ai", enabled);
    return allowedUsers;
  }
}

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

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class FeatureFlagServiceTest {
  @Test
  public void knownFlagsStayDisabledForFreshStores() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void storedOtSearchFlagEnablesKnownFlag() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            true,
            new LinkedHashMap<>()));

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void storedFalseOtSearchFlagKeepsKnownFlagDisabled() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "ot-search",
            "Real-time search wavelets (replaces 15s polling)",
            false,
            new LinkedHashMap<>()));

    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.getEnabledFlagNames(null).contains("ot-search"));
  }

  @Test
  public void disabledAllowedUserDoesNotEnableFlag() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(new FeatureFlag("new-ui", "New UI", false, allowedUsers(false)));

    FeatureFlagService service = new FeatureFlagService(store);

    assertFalse(service.isEnabled("new-ui", "vega@supawave.ai"));
  }

  @Test
  public void enabledAllowedUserStillSeesFlagWhenGlobalDisabled() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(new FeatureFlag("new-ui", "New UI", false, allowedUsers(true)));

    FeatureFlagService service = new FeatureFlagService(store);

    assertTrue(service.isEnabled("new-ui", "vega@supawave.ai"));
  }

  private static Map<String, Boolean> allowedUsers(boolean enabled) {
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("vega@supawave.ai", enabled);
    return allowedUsers;
  }
}

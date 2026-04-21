/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.persistence.memory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.ConfigFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.KnownFeatureFlags;

public final class FeatureFlagSeederJ2clBootstrapTest {
  @Test
  public void knownBootstrapFlagIsRegisteredAndDefaultsOn() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    FeatureFlagService service = new FeatureFlagService(store);
    try {
      assertTrue(KnownFeatureFlags.isKnownFlag("j2cl-root-bootstrap"));
      assertTrue(service.getEnabledFlagNames(null).contains("j2cl-root-bootstrap"));
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void seedsJ2clRootBootstrapFlagFromConfigWhenAbsent() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();

    FeatureFlagSeeder.seedJ2clRootBootstrapFeatureFlags(
        store, ConfigFactory.parseString("ui.j2cl_root_bootstrap_enabled = false"));

    FeatureFlag flag = store.get("j2cl-root-bootstrap");

    assertTrue(flag != null);
    assertFalse(flag.isEnabled());
    assertTrue(flag.getAllowedUsers().isEmpty());
  }

  @Test
  public void preservesExistingJ2clRootBootstrapFlagWhenSeeding() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("admin@example.com", true);
    store.save(
        new FeatureFlag(
            "j2cl-root-bootstrap",
            "Bootstrap the J2CL root shell on / while keeping /webclient rollback ready",
            true,
            allowedUsers));

    FeatureFlagSeeder.seedJ2clRootBootstrapFeatureFlags(
        store, ConfigFactory.parseString("ui.j2cl_root_bootstrap_enabled = false"));

    FeatureFlag flag = store.get("j2cl-root-bootstrap");

    assertTrue(flag.isEnabled());
    assertTrue(flag.getAllowedUsers().containsKey("admin@example.com"));
    assertTrue(flag.getAllowedUsers().get("admin@example.com"));
  }

  @Test
  public void reconcilePreservesExistingJ2clRootBootstrapFlagWhenOverrideConfigOmitsValue()
      throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("admin@example.com", true);
    store.save(
        new FeatureFlag(
            "j2cl-root-bootstrap",
            "Bootstrap the J2CL root shell on / while keeping /webclient rollback ready",
            true,
            allowedUsers));

    FeatureFlagSeeder.reconcileJ2clRootBootstrapFeatureFlag(
        store, ConfigFactory.parseString("core.http_frontend_addresses=[\"127.0.0.1:9898\"]"));

    FeatureFlag flag = store.get("j2cl-root-bootstrap");

    assertTrue(flag.isEnabled());
    assertTrue(flag.getAllowedUsers().containsKey("admin@example.com"));
    assertTrue(flag.getAllowedUsers().get("admin@example.com"));
  }

  @Test
  public void reconcileAppliesExplicitJ2clRootBootstrapOverride() throws Exception {
    MemoryFeatureFlagStore store = new MemoryFeatureFlagStore();
    store.save(
        new FeatureFlag(
            "j2cl-root-bootstrap",
            "Bootstrap the J2CL root shell on / while keeping /webclient rollback ready",
            true,
            new LinkedHashMap<>()));

    FeatureFlagSeeder.reconcileJ2clRootBootstrapFeatureFlag(
        store, ConfigFactory.parseString("ui.j2cl_root_bootstrap_enabled = false"));

    FeatureFlag flag = store.get("j2cl-root-bootstrap");

    assertFalse(flag.isEnabled());
  }
}

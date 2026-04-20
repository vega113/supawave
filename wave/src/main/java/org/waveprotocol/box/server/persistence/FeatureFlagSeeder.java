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
package org.waveprotocol.box.server.persistence;

import com.typesafe.config.Config;
import java.util.Collections;
import java.util.logging.Logger;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class FeatureFlagSeeder {
  private static final Logger LOG = Logger.getLogger(FeatureFlagSeeder.class.getName());

  private static final String OT_SEARCH_FLAG_NAME = "ot-search";
  private static final String OT_SEARCH_DESCRIPTION =
      "OT/Lucene search (real-time wavelets + full-text indexing)";
  private static final String OT_SEARCH_CONFIG_KEY = "search.ot_search_enabled";
  private static final String J2CL_ROOT_BOOTSTRAP_FLAG_NAME = "j2cl-root-bootstrap";
  private static final String J2CL_ROOT_BOOTSTRAP_DESCRIPTION =
      "Bootstrap the J2CL root shell on /";
  private static final String J2CL_ROOT_BOOTSTRAP_CONFIG_KEY = "ui.j2cl_root_bootstrap_enabled";

  private FeatureFlagSeeder() {
  }

  public static void seedSearchFeatureFlags(FeatureFlagStore store, Config config)
      throws PersistenceException {
    if (store == null || config == null || !config.hasPath(OT_SEARCH_CONFIG_KEY)) {
      return;
    }
    if (store.get(OT_SEARCH_FLAG_NAME) == null) {
      // Flag not yet in DB — initialize from config so fresh deploys start with the right default.
      // If a flag already exists (admin set it via the API), leave it untouched.
      boolean enabled = config.getBoolean(OT_SEARCH_CONFIG_KEY);
      store.save(
          new FeatureFlag(
              OT_SEARCH_FLAG_NAME,
              OT_SEARCH_DESCRIPTION,
              enabled,
              Collections.emptyMap()));
      LOG.info("Seeded ot-search feature flag: enabled=" + enabled);
    } else {
      LOG.info("ot-search feature flag already present in store — preserving existing value");
    }
  }

  public static void seedJ2clRootBootstrapFeatureFlags(FeatureFlagStore store, Config config)
      throws PersistenceException {
    if (store == null || config == null || !config.hasPath(J2CL_ROOT_BOOTSTRAP_CONFIG_KEY)) {
      return;
    }
    if (store.get(J2CL_ROOT_BOOTSTRAP_FLAG_NAME) == null) {
      // Flag not yet in DB — initialize from config so fresh deploys start with the right default.
      // If a flag already exists (admin set it via the API), leave it untouched.
      boolean enabled = config.getBoolean(J2CL_ROOT_BOOTSTRAP_CONFIG_KEY);
      store.save(
          new FeatureFlag(
              J2CL_ROOT_BOOTSTRAP_FLAG_NAME,
              J2CL_ROOT_BOOTSTRAP_DESCRIPTION,
              enabled,
              Collections.emptyMap()));
      LOG.info("Seeded j2cl-root-bootstrap feature flag: enabled=" + enabled);
    } else {
      LOG.info("j2cl-root-bootstrap feature flag already present in store — preserving existing value");
    }
  }

  public static void reconcileJ2clRootBootstrapFeatureFlag(
      FeatureFlagStore store, Config config) throws PersistenceException {
    // Reconcile only from the operator-provided application/override config, not from
    // the fully-resolved effective config (which always contains the reference default).
    if (store == null || config == null || !config.hasPath(J2CL_ROOT_BOOTSTRAP_CONFIG_KEY)) {
      return;
    }

    boolean enabled = config.getBoolean(J2CL_ROOT_BOOTSTRAP_CONFIG_KEY);
    FeatureFlag existing = store.get(J2CL_ROOT_BOOTSTRAP_FLAG_NAME);
    if (existing == null) {
      store.save(
          new FeatureFlag(
              J2CL_ROOT_BOOTSTRAP_FLAG_NAME,
              J2CL_ROOT_BOOTSTRAP_DESCRIPTION,
              enabled,
              Collections.emptyMap()));
      LOG.info("Reconciled j2cl-root-bootstrap feature flag from startup config: enabled=" + enabled);
      return;
    }

    if (existing.isEnabled() != enabled) {
      store.save(
          new FeatureFlag(
              J2CL_ROOT_BOOTSTRAP_FLAG_NAME,
              existing.getDescription().isEmpty()
                  ? J2CL_ROOT_BOOTSTRAP_DESCRIPTION
                  : existing.getDescription(),
              enabled,
              existing.getAllowedUsers()));
      LOG.info("Reconciled j2cl-root-bootstrap feature flag from startup config: enabled=" + enabled);
    }
  }

  public static boolean isSearchWaveletUpdaterEnabled(FeatureFlagStore store)
      throws PersistenceException {
    FeatureFlag otSearchFlag = store != null ? store.get(OT_SEARCH_FLAG_NAME) : null;
    boolean enabled = otSearchFlag != null
        && (otSearchFlag.isEnabled()
            || otSearchFlag.getAllowedUsers().containsValue(Boolean.TRUE));
    return enabled;
  }
}

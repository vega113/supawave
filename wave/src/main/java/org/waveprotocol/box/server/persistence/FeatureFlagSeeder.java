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
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class FeatureFlagSeeder {
  private static final String OT_SEARCH_FLAG_NAME = "ot-search";
  private static final String OT_SEARCH_DESCRIPTION =
      "Real-time search wavelets (replaces 15s polling)";
  private static final String OT_SEARCH_CONFIG_KEY = "search.ot_search_enabled";

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
      store.save(
          new FeatureFlag(
              OT_SEARCH_FLAG_NAME,
              OT_SEARCH_DESCRIPTION,
              config.getBoolean(OT_SEARCH_CONFIG_KEY),
              Collections.emptyMap()));
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

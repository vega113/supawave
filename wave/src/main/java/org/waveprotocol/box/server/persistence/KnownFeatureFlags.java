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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class KnownFeatureFlags {
  private static final List<FeatureFlag> DEFAULTS;

  static {
    List<FeatureFlag> defaults = new ArrayList<>();
    defaults.add(new FeatureFlag("lucene9", "Lucene 9.x full-text search", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("ot-search", "Real-time search wavelets (replaces 15s polling)", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("grafana-log-export", "Allow forwarding remote logs to Grafana/Loki pipeline", true, Collections.emptyMap()));
    DEFAULTS = Collections.unmodifiableList(defaults);
  }

  private KnownFeatureFlags() {
  }

  public static List<FeatureFlag> mergeWithStored(List<FeatureFlag> storedFlags) {
    LinkedHashMap<String, FeatureFlag> mergedFlags = new LinkedHashMap<>();
    addFlags(mergedFlags, DEFAULTS);
    addFlags(mergedFlags, storedFlags);
    return new ArrayList<>(mergedFlags.values());
  }

  public static boolean isKnownFlag(String name) {
    if (name == null) {
      return false;
    }
    for (FeatureFlag flag : DEFAULTS) {
      if (flag != null && name.equals(flag.getName())) {
        return true;
      }
    }
    return false;
  }

  private static void addFlags(LinkedHashMap<String, FeatureFlag> mergedFlags,
      List<FeatureFlag> flags) {
    if (flags == null) {
      return;
    }
    for (FeatureFlag flag : flags) {
      if (flag != null && flag.getName() != null) {
        mergedFlags.put(flag.getName(), flag);
      }
    }
  }
}

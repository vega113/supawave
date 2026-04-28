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
    defaults.add(new FeatureFlag("ot-search", "OT/Lucene search (real-time wavelets + full-text indexing)", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("ot-search-fallback", "Allow OT search to bootstrap or fall back to legacy HTTP polling", false, Collections.emptyMap()));
    // "grafana-log-export": log forwarding is currently handled at the infrastructure level by the
    // Grafana Alloy agent (see deploy/supawave-host/configure-grafana-alloy.sh). This flag is
    // registered here so admins can see and toggle it via the feature-flags UI; a future PR will
    // wire isEnabled("grafana-log-export") into the Java-side log-export path when added.
    defaults.add(new FeatureFlag("grafana-log-export", "Allow forwarding remote logs to Grafana/Loki pipeline", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("new-blip-indicator", "Show floating pill when new messages arrive below viewport", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("mention-unread-badge", "Show unread @mention count badge and next-mention navigation", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("task-unread-badge", "Show unread task count badge and task navigation", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("task-search", "Enable tasks:me search filter and Tasks toolbar button", true, Collections.emptyMap()));
    defaults.add(new FeatureFlag("mentions-search", "Enable @mention search filter and Mentions toolbar button", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("compact-inline-blips", "Compact inline blip layout at nesting depth", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("ime-debug-tracer", "Enable IME diagnostic trace overlay and remote log upload", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("j2cl-root-bootstrap", "Bootstrap the J2CL root shell on / while keeping /webclient rollback ready", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("j2cl-search-rail-cards", "Render J2CL search digests as <wavy-search-rail-card> elements inside <wavy-search-rail> instead of the legacy plain-DOM digest list", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("j-ui-3-new-wave", "J-UI-3 new-wave create flow: title input + optimistic rail prepend (#1081)", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("j2cl-inline-rich-composer", "Open a contenteditable <wavy-composer> with a selection-driven format toolbar at the chosen blip on /?view=j2cl-root, replacing the legacy textarea-shaped composer-inline-reply.", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("j2cl-server-first-paint", "Emit a <noscript> info banner on the J2CL root shell so visitors with JavaScript disabled see why interactive features (compose, reactions, live updates) are unavailable on the static snapshot. R-6.1 / R-6.3 first-paint guarantee.", false, Collections.emptyMap()));
    defaults.add(new FeatureFlag("social-auth", "Enable Google and GitHub social sign-in and sign-up", false, Collections.emptyMap()));
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

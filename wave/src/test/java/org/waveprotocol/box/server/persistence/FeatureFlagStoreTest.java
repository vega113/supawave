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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;

public final class FeatureFlagStoreTest {
  @Test
  public void parsesLegacyAndStatusAwareAllowedUsers() {
    Map<String, Boolean> allowedUsers =
        FeatureFlag.fromStoredAllowedUsers(
            Arrays.asList(
                "vega@supawave.ai",
                "ops@supawave.ai:disabled",
                "qa@supawave.ai:enabled"));

    assertEquals(Boolean.TRUE, allowedUsers.get("vega@supawave.ai"));
    assertEquals(Boolean.FALSE, allowedUsers.get("ops@supawave.ai"));
    assertEquals(Boolean.TRUE, allowedUsers.get("qa@supawave.ai"));
  }

  @Test
  public void formatsAllowedUsersWithExplicitStatuses() {
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("vega@supawave.ai", true);
    allowedUsers.put("ops@supawave.ai", false);

    List<String> storedUsers = FeatureFlag.toStoredAllowedUsers(allowedUsers);

    assertEquals(
        Arrays.asList("vega@supawave.ai:enabled", "ops@supawave.ai:disabled"),
        storedUsers);
    assertTrue(storedUsers.get(0).endsWith(":enabled"));
    assertTrue(storedUsers.get(1).endsWith(":disabled"));
  }
}

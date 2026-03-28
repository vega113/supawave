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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage interface for feature flags.
 *
 * <p>Feature flags control gradual rollout of new functionality. Each flag
 * can be enabled globally or for specific users (by participant address).
 */
public interface FeatureFlagStore {

  /**
   * Initializes the store (e.g. ensure indexes).
   *
   * @throws PersistenceException if initialization fails
   */
  void initializeFeatureFlagStore() throws PersistenceException;

  /**
   * Returns all feature flags.
   */
  List<FeatureFlag> getAll() throws PersistenceException;

  /**
   * Returns the feature flag with the given name, or null if not found.
   */
  FeatureFlag get(String name) throws PersistenceException;

  /**
   * Creates or updates a feature flag. The flag name is the unique key.
   */
  void save(FeatureFlag flag) throws PersistenceException;

  /**
   * Deletes the feature flag with the given name.
   */
  void delete(String name) throws PersistenceException;

  /**
   * Immutable data object representing a feature flag.
   */
  final class FeatureFlag {
    private static final String ENABLED_SUFFIX = ":enabled";
    private static final String DISABLED_SUFFIX = ":disabled";

    private final String name;
    private final String description;
    private final boolean enabled;
    private final Map<String, Boolean> allowedUsers;

    public FeatureFlag(String name, String description, boolean enabled,
                       Map<String, Boolean> allowedUsers) {
      this.name = name;
      this.description = description != null ? description : "";
      this.enabled = enabled;
      this.allowedUsers = Collections.unmodifiableMap(copyAllowedUsers(allowedUsers));
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Boolean> getAllowedUsers() { return allowedUsers; }

    public static Map<String, Boolean> fromStoredAllowedUsers(Iterable<String> storedUsers) {
      Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
      if (storedUsers == null) {
        return allowedUsers;
      }
      for (String storedUser : storedUsers) {
        if (storedUser == null) {
          continue;
        }
        String trimmed = storedUser.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        if (trimmed.endsWith(ENABLED_SUFFIX)) {
          allowedUsers.put(
              trimmed.substring(0, trimmed.length() - ENABLED_SUFFIX.length()),
              true);
        } else if (trimmed.endsWith(DISABLED_SUFFIX)) {
          allowedUsers.put(
              trimmed.substring(0, trimmed.length() - DISABLED_SUFFIX.length()),
              false);
        } else {
          allowedUsers.put(trimmed, true);
        }
      }
      return allowedUsers;
    }

    public static List<String> toStoredAllowedUsers(Map<String, Boolean> allowedUsers) {
      List<String> storedUsers = new java.util.ArrayList<>();
      for (Map.Entry<String, Boolean> entry : copyAllowedUsers(allowedUsers).entrySet()) {
        storedUsers.add(entry.getKey() + (entry.getValue() ? ENABLED_SUFFIX : DISABLED_SUFFIX));
      }
      return storedUsers;
    }

    private static Map<String, Boolean> copyAllowedUsers(Map<String, Boolean> allowedUsers) {
      if (allowedUsers == null || allowedUsers.isEmpty()) {
        return Collections.emptyMap();
      }
      Map<String, Boolean> copy = new LinkedHashMap<>();
      for (Map.Entry<String, Boolean> entry : allowedUsers.entrySet()) {
        String email = entry.getKey();
        if (email == null) {
          continue;
        }
        String trimmedEmail = email.trim();
        if (trimmedEmail.isEmpty()) {
          continue;
        }
        copy.put(trimmedEmail, !Boolean.FALSE.equals(entry.getValue()));
      }
      return copy;
    }
  }
}

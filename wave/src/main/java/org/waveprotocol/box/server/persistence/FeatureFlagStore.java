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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final String name;
    private final String description;
    private final boolean enabled;
    private final Set<String> allowedUsers;

    public FeatureFlag(String name, String description, boolean enabled,
                       Set<String> allowedUsers) {
      this.name = name;
      this.description = description != null ? description : "";
      this.enabled = enabled;
      this.allowedUsers = allowedUsers != null
          ? Collections.unmodifiableSet(new LinkedHashSet<>(allowedUsers))
          : Collections.emptySet();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public Set<String> getAllowedUsers() { return allowedUsers; }
  }
}

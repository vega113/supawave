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

import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link FeatureFlagStore} for development and testing.
 */
public final class MemoryFeatureFlagStore implements FeatureFlagStore {

  private final ConcurrentHashMap<String, FeatureFlag> flags = new ConcurrentHashMap<>();

  @Override
  public void initializeFeatureFlagStore() throws PersistenceException {
    // no-op for in-memory store
  }

  @Override
  public List<FeatureFlag> getAll() throws PersistenceException {
    return new ArrayList<>(flags.values());
  }

  @Override
  public FeatureFlag get(String name) throws PersistenceException {
    return flags.get(name);
  }

  @Override
  public void save(FeatureFlag flag) throws PersistenceException {
    flags.put(flag.getName(), flag);
  }

  @Override
  public void delete(String name) throws PersistenceException {
    flags.remove(name);
  }
}

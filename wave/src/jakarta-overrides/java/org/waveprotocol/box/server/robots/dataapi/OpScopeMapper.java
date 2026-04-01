/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
package org.waveprotocol.box.server.robots.dataapi;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OpScopeMapper {
  public static final String SCOPE_WAVE_DATA_READ = "wave:data:read";
  public static final String SCOPE_WAVE_DATA_WRITE = "wave:data:write";
  public static final String SCOPE_WAVE_ROBOT_ACTIVE = "wave:robot:active";
  public static final String SCOPE_WAVE_ADMIN = "wave:admin";

  private static final Map<OpType, Set<String>> OPERATION_SCOPES = new EnumMap<>(OpType.class);

  static {
    OPERATION_SCOPES.put(OpType.FETCH_WAVELET, setOf(SCOPE_WAVE_DATA_READ));
    OPERATION_SCOPES.put(OpType.FETCH_WAVE, setOf(SCOPE_WAVE_DATA_READ));
    OPERATION_SCOPES.put(OpType.LIST_WAVES, setOf(SCOPE_WAVE_DATA_READ));
    OPERATION_SCOPES.put(OpType.MODIFY_WAVELET, setOf(SCOPE_WAVE_DATA_WRITE));
    OPERATION_SCOPES.put(OpType.CREATE_WAVELET, setOf(SCOPE_WAVE_DATA_WRITE));
    OPERATION_SCOPES.put(OpType.SUBMIT_DELTA, setOf(SCOPE_WAVE_DATA_WRITE));
    OPERATION_SCOPES.put(OpType.ROBOT_RPC, setOf(SCOPE_WAVE_ROBOT_ACTIVE));
    OPERATION_SCOPES.put(OpType.ADMIN_OPERATION, setOf(SCOPE_WAVE_ADMIN));
  }

  public static Set<String> getRequiredScopes(OpType opType) {
    return OPERATION_SCOPES.getOrDefault(opType, new HashSet<>());
  }

  public static boolean checkScopes(OpType opType, Set<String> tokenScopes) {
    Set<String> required = getRequiredScopes(opType);
    if (required.isEmpty()) {
      return false;
    }
    return tokenScopes.containsAll(required);
  }

  private static Set<String> setOf(String... scopes) {
    Set<String> set = new HashSet<>();
    for (String scope : scopes) {
      set.add(scope);
    }
    return set;
  }

  public enum OpType {
    FETCH_WAVELET,
    FETCH_WAVE,
    LIST_WAVES,
    MODIFY_WAVELET,
    CREATE_WAVELET,
    SUBMIT_DELTA,
    ROBOT_RPC,
    ADMIN_OPERATION
  }
}

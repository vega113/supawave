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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.server.authentication.jwt.JwtScopes;

public class OpScopeMapper {
  // Deprecated: use JwtScopes constants instead
  public static final String SCOPE_WAVE_DATA_READ = JwtScopes.DATA_READ;
  public static final String SCOPE_WAVE_DATA_WRITE = JwtScopes.DATA_WRITE;
  public static final String SCOPE_WAVE_ROBOT_ACTIVE = JwtScopes.ROBOT_ACTIVE;
  public static final String SCOPE_WAVE_ADMIN = "wave:admin";

  private static final Map<OpType, Set<String>> OPERATION_SCOPES = new EnumMap<>(OpType.class);

  static {
    OPERATION_SCOPES.put(OpType.FETCH_WAVE, Set.of(JwtScopes.DATA_READ));
    OPERATION_SCOPES.put(OpType.LIST_WAVES, Set.of(JwtScopes.DATA_READ));
    OPERATION_SCOPES.put(OpType.MODIFY_WAVELET, Set.of(JwtScopes.DATA_WRITE));
    OPERATION_SCOPES.put(OpType.CREATE_WAVELET, Set.of(JwtScopes.DATA_WRITE));
    OPERATION_SCOPES.put(OpType.SUBMIT_DELTA, Set.of(JwtScopes.DATA_WRITE));
    OPERATION_SCOPES.put(OpType.ROBOT_RPC, Set.of(JwtScopes.ROBOT_ACTIVE));
    OPERATION_SCOPES.put(OpType.ADMIN_OPERATION, Set.of(SCOPE_WAVE_ADMIN));
  }

  public static Set<String> getRequiredScopes(OpType opType) {
    return OPERATION_SCOPES.getOrDefault(opType, Collections.emptySet());
  }

  public static boolean checkScopes(OpType opType, Set<String> tokenScopes) {
    if (tokenScopes == null || tokenScopes.isEmpty()) {
      return false;
    }
    Set<String> required = getRequiredScopes(opType);
    if (required.isEmpty()) {
      return false;
    }
    return tokenScopes.containsAll(required);
  }

  public enum OpType {
    FETCH_WAVE,
    LIST_WAVES,
    MODIFY_WAVELET,
    CREATE_WAVELET,
    SUBMIT_DELTA,
    ROBOT_RPC,
    ADMIN_OPERATION
  }
}

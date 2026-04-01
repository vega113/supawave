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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for {@link OpScopeMapper}.
 */
public class OpScopeMapperTest {

  @Test
  public void testModifyWaveletRequiresWriteScope() {
    Set<String> required = OpScopeMapper.getRequiredScopes(OpScopeMapper.OpType.MODIFY_WAVELET);
    assertFalse("MODIFY_WAVELET should require at least one scope", required.isEmpty());
    assertTrue("MODIFY_WAVELET should require wave:data:write",
        required.contains(OpScopeMapper.SCOPE_WAVE_DATA_WRITE));
    assertFalse("MODIFY_WAVELET should not require read scope",
        required.contains(OpScopeMapper.SCOPE_WAVE_DATA_READ));
  }

  @Test
  public void testCheckScopesPassesWithRequiredScope() {
    Set<String> tokenScopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);
    assertTrue("checkScopes should pass when token has required scope",
        OpScopeMapper.checkScopes(OpScopeMapper.OpType.FETCH_WAVE, tokenScopes));
  }

  @Test
  public void testCheckScopesFailsWithoutRequiredScope() {
    Set<String> tokenScopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);
    assertFalse("checkScopes should fail when token lacks required scope",
        OpScopeMapper.checkScopes(OpScopeMapper.OpType.MODIFY_WAVELET, tokenScopes));
  }

  @Test
  public void allOpTypesAreMapped() {
    // Verify all OpType enum values have scope mappings (fail-closed by default)
    // and checkScopes behaves consistently
    Set<String> tokenScopes = Set.of(
        OpScopeMapper.SCOPE_WAVE_DATA_READ,
        OpScopeMapper.SCOPE_WAVE_DATA_WRITE,
        OpScopeMapper.SCOPE_WAVE_ROBOT_ACTIVE,
        OpScopeMapper.SCOPE_WAVE_ADMIN);

    for (OpScopeMapper.OpType opType : OpScopeMapper.OpType.values()) {
      Set<String> required = OpScopeMapper.getRequiredScopes(opType);
      assertFalse("Every OpType should have required scopes mapped: " + opType,
          required.isEmpty());
      assertEquals("getRequiredScopes should be consistent with checkScopes for " + opType,
          tokenScopes.containsAll(required),
          OpScopeMapper.checkScopes(opType, tokenScopes));
    }

    // Explicit check: all defined op types have non-empty scopes
    assertEquals("All OpType enum values should have required scopes",
        OpScopeMapper.OpType.values().length,
        countNonEmptyMappings());
  }

  @Test
  public void testNotifyOperationsRequireDataReadScopeOnly() {
    // ROBOT_NOTIFY and related operations should only require data read scope
    // They are not write operations and should not require wave:robot:active
    Set<String> dataReadScope = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);

    // Test that FETCH_WAVE (which maps ROBOT_NOTIFY in Data API) passes with read scope
    assertTrue("FETCH_WAVE should pass with data read scope only",
        OpScopeMapper.checkScopes(OpScopeMapper.OpType.FETCH_WAVE, dataReadScope));

    // Verify that the read scope alone does not pass write operations
    assertFalse("MODIFY_WAVELET should fail with read scope only",
        OpScopeMapper.checkScopes(OpScopeMapper.OpType.MODIFY_WAVELET, dataReadScope));
  }

  private int countNonEmptyMappings() {
    int count = 0;
    for (OpScopeMapper.OpType opType : OpScopeMapper.OpType.values()) {
      if (!OpScopeMapper.getRequiredScopes(opType).isEmpty()) {
        count++;
      }
    }
    return count;
  }
}

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
  public void testCheckScopesFailsWithUnknownOperation() {
    // UNKNOWN operation type is not in OpScopeMapper.OpType, but we can test
    // a type with an empty scope set by checking behavior: getRequiredScopes returns empty
    // and checkScopes returns false (fail-closed for unmapped types)
    Set<String> tokenScopes = Set.of(
        OpScopeMapper.SCOPE_WAVE_DATA_READ,
        OpScopeMapper.SCOPE_WAVE_DATA_WRITE,
        OpScopeMapper.SCOPE_WAVE_ROBOT_ACTIVE,
        OpScopeMapper.SCOPE_WAVE_ADMIN);
    // Create an OpType that is not mapped - all OpType enum values are mapped,
    // so we test that an unmapped operation returns empty scopes and fails closed
    // by verifying checkScopes returns false when required set is empty
    for (OpScopeMapper.OpType opType : OpScopeMapper.OpType.values()) {
      Set<String> required = OpScopeMapper.getRequiredScopes(opType);
      if (required.isEmpty()) {
        assertFalse("checkScopes should return false for op with empty required scopes",
            OpScopeMapper.checkScopes(opType, tokenScopes));
      } else {
        assertEquals("getRequiredScopes should be consistent with checkScopes for " + opType,
            tokenScopes.containsAll(required),
            OpScopeMapper.checkScopes(opType, tokenScopes));
      }
    }
    // Verify all defined op types have non-empty scopes (fail-closed by default)
    assertEquals("All defined OpTypes should have required scopes mapped",
        OpScopeMapper.OpType.values().length,
        countNonEmptyMappings());
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

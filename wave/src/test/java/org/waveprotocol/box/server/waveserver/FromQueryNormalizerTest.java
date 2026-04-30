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
 * specific language governing permissions and limitations under the
 * License.
 */

package org.waveprotocol.box.server.waveserver;

import junit.framework.TestCase;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class FromQueryNormalizerTest extends TestCase {

  public void testNormalizeResolvesMeToCurrentUser() {
    assertEquals(
        "user@example.com",
        FromQueryNormalizer.normalize("me", ParticipantId.ofUnsafe("User@Example.com")));
  }

  public void testNormalizeAppendsLocalDomainForBareNames() {
    assertEquals(
        "alice@example.com",
        FromQueryNormalizer.normalize("Alice", ParticipantId.ofUnsafe("user@example.com")));
  }

  public void testNormalizeLowercasesExplicitAddresses() {
    assertEquals(
        "alice@example.com",
        FromQueryNormalizer.normalize("Alice@Example.com",
            ParticipantId.ofUnsafe("user@example.com")));
  }

  public void testNormalizeRejectsEmptyInput() {
    try {
      FromQueryNormalizer.normalize("", ParticipantId.ofUnsafe("user@example.com"));
      fail("Expected empty from value to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("raw from value"));
    }
  }

  public void testNormalizeRejectsNullInput() {
    try {
      FromQueryNormalizer.normalize(null, ParticipantId.ofUnsafe("user@example.com"));
      fail("Expected null from value to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("raw from value"));
    }
  }

  public void testNormalizeRejectsNullUser() {
    try {
      FromQueryNormalizer.normalize("me", null);
      fail("Expected null user to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("user cannot be null"));
    }
  }
}

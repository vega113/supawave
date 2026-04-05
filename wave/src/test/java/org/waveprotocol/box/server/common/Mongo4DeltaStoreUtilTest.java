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

package org.waveprotocol.box.server.common;

import junit.framework.TestCase;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreUtil;
import org.waveprotocol.wave.model.version.HashedVersion;

/**
 * Unit tests for {@link Mongo4DeltaStoreUtil} serialization helpers.
 * These tests exercise the BSON/Java logic only; no MongoDB connection required.
 */
public class Mongo4DeltaStoreUtilTest extends TestCase {

  /**
   * When the MongoDB document has no historyhash field (e.g. older records),
   * deserializeHashedVersion must return an empty-hash HashedVersion rather
   * than crashing with IllegalArgumentException("null history hash").
   */
  public void testDeserializeHashedVersionMissingHashFieldReturnsEmptyHash() {
    Document doc = new Document();
    doc.append(Mongo4DeltaStoreUtil.FIELD_VERSION, 42L);
    // FIELD_HISTORYHASH intentionally absent

    HashedVersion result = Mongo4DeltaStoreUtil.deserializeHashedVersion(doc);

    assertEquals(42L, result.getVersion());
    assertNotNull(result.getHistoryHash());
    assertEquals(0, result.getHistoryHash().length);
  }

  /**
   * When the MongoDB document has historyhash as a Binary value, it must be
   * decoded correctly (existing happy-path must be unaffected by the fix).
   */
  public void testDeserializeHashedVersionWithBinaryHashRoundTrips() {
    byte[] hash = new byte[]{1, 2, 3, 4};
    Document doc = new Document();
    doc.append(Mongo4DeltaStoreUtil.FIELD_VERSION, 7L);
    doc.append(Mongo4DeltaStoreUtil.FIELD_HISTORYHASH, new Binary(hash));

    HashedVersion result = Mongo4DeltaStoreUtil.deserializeHashedVersion(doc);

    assertEquals(7L, result.getVersion());
    assertEquals(4, result.getHistoryHash().length);
    assertEquals(1, result.getHistoryHash()[0]);
    assertEquals(4, result.getHistoryHash()[3]);
  }
}

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

package org.waveprotocol.box.server.frontend;

import static org.junit.Assert.*;

import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;

public final class FragmentsRequestTest {

  @Test
  public void buildWithRangesOnlyIsValid() {
    FragmentsRequest req = new FragmentsRequest.Builder()
        .addRange(SegmentId.INDEX_ID, VersionRange.of(1, 10))
        .addRange(SegmentId.MANIFEST_ID, VersionRange.of(1, 10))
        .build();
    assertTrue(req.ranges.containsKey(SegmentId.INDEX_ID));
    assertEquals(1, req.ranges.get(SegmentId.INDEX_ID).from());
    assertEquals(10, req.ranges.get(SegmentId.INDEX_ID).to());
    assertEquals(FragmentsRequest.NO_VERSION, req.startVersion);
    assertEquals(FragmentsRequest.NO_VERSION, req.endVersion);
  }

  @Test
  public void buildWithCommonStartEndIsValid() {
    FragmentsRequest req = new FragmentsRequest.Builder()
        .setStartVersion(5).setEndVersion(15).build();
    assertTrue(req.ranges.isEmpty());
    assertEquals(5, req.startVersion);
    assertEquals(15, req.endVersion);
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildWithRangesAndCommonVersionsIsInvalid() {
    new FragmentsRequest.Builder()
        .addRange(SegmentId.MANIFEST_ID, VersionRange.of(1, 2))
        .setStartVersion(1).setEndVersion(2)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildWithOnlyStartOrOnlyEndIsInvalid() {
    new FragmentsRequest.Builder().setStartVersion(1).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildWithStartGreaterThanEndIsInvalid() {
    new FragmentsRequest.Builder().setStartVersion(10).setEndVersion(2).build();
  }
}


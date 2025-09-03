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
package org.waveprotocol.wave.concurrencycontrol.channel;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.SkeletonRawFragmentsApplier;
import org.waveprotocol.wave.model.id.WaveletId;

public final class RawFragmentsApplierTest {
  @Test
  public void appliesValidRangesAndRejectsInvalid() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    applier.apply(wid, Arrays.asList(
        new RawFragment("index", 0, 0),
        new RawFragment("blip:b+1", 1, 3),
        new RawFragment("manifest", 0, 0),
        new RawFragment("blip:b+bad", 5, 2) // invalid: from>to
    ));

    assertEquals(3, applier.getAppliedCount());
    assertEquals(1, applier.getRejectedCount());
    Map<String, VersionRange> m = applier.getStateFor(wid);
    assertNotNull(m);
    assertEquals(VersionRange.of(0, 0).toString(), m.get("index").toString());
    assertEquals(VersionRange.of(0, 0).toString(), m.get("manifest").toString());
    assertEquals(VersionRange.of(1, 3).toString(), m.get("blip:b+1").toString());
    assertFalse(m.containsKey("blip:b+bad"));
  }
}


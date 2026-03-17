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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier.Interval;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

public final class RealRawFragmentsApplierTest {

  @Test
  public void mergesOverlappingAndAdjacent() {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<RawFragment> fs = Arrays.asList(
        new RawFragment("blip:b+1", 1, 3),
        new RawFragment("blip:b+1", 4, 5),
        new RawFragment("blip:b+1", 8, 10),
        new RawFragment("blip:b+1", 2, 9));
    a.apply(wid, fs);

    List<Interval> cov = a.getCoverage(wid, "blip:b+1");
    assertEquals(1, cov.size());
    assertEquals(1, cov.get(0).from);
    assertEquals(10, cov.get(0).to);
    assertEquals(4, a.getAppliedCount());
    assertEquals(0, a.getRejectedCount());
  }

  @Test
  public void ignoresInvalidAndKeepsDisjoint() {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    a.apply(wid, Arrays.asList(
        new RawFragment("manifest", 0, 0),
        new RawFragment("manifest", 2, 3),
        new RawFragment("manifest", 5, 5),
        new RawFragment("manifest", 7, 6) // invalid
    ));
    List<Interval> cov = a.getCoverage(wid, "manifest");
    assertEquals(3, cov.size());
    assertEquals(1, a.getRejectedCount());
  }

  @Test
  public void multipleSegmentsIndependent() {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    a.apply(wid, Arrays.asList(
        new RawFragment("blip:b+1", 1, 1),
        new RawFragment("blip:b+2", 2, 2))
    );
    assertEquals(1, a.getCoverage(wid, "blip:b+1").size());
    assertEquals(1, a.getCoverage(wid, "blip:b+2").size());
  }
}


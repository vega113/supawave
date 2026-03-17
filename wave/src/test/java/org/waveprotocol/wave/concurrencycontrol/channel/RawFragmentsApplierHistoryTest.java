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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.SkeletonRawFragmentsApplier;
import org.waveprotocol.wave.model.id.WaveletId;

/**
 * Tests for SkeletonRawFragmentsApplier history behavior.
 *
 * Methodology:
 * - Applies a sequence of fragments and verifies that the time-ordered history
 *   is bounded and contains only the most recent entries.
 * - Covers edge cases: max=0 behavior (clamped to 1), empty batch, duplicates,
 *   and negative ranges rejection.
 */
public final class RawFragmentsApplierHistoryTest {
  @Test
  public void historyIsBoundedAndInOrder() {
    int max = 5;
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier(max);
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<RawFragment> batch = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      batch.clear();
      batch.add(new RawFragment("blip:b+" + i, i, i));
      applier.apply(wid, batch);
    }
    List<SkeletonRawFragmentsApplier.HistoryEntry> hist = applier.getHistoryFor(wid);
    assertEquals("history should be bounded to max", max, hist.size());
    // Should contain last 5 applied entries
    for (int j = 0; j < max; j++) {
      assertTrue(hist.get(j).segment.endsWith(Integer.toString(10 - max + j)));
    }
  }

  @Test
  public void maxZeroIsClampedAndKeepsOne() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier(0); // clamped to 1
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    for (int i = 0; i < 3; i++) {
      applier.apply(wid, java.util.Arrays.asList(new RawFragment("blip:b+" + i, i, i)));
    }
    java.util.List<SkeletonRawFragmentsApplier.HistoryEntry> hist = applier.getHistoryFor(wid);
    assertEquals(1, hist.size());
    assertTrue(hist.get(0).segment.endsWith("2"));
  }

  @Test
  public void emptyBatchDoesNotChangeHistory() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier(5);
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    applier.apply(wid, new java.util.ArrayList<>());
    assertTrue(applier.getHistoryFor(wid).isEmpty());
  }

  @Test
  public void duplicateFragmentsAreRecordedAndLatestStateWins() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier(5);
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    RawFragment f1 = new RawFragment("blip:b+1", 1, 2);
    RawFragment f2 = new RawFragment("blip:b+1", 3, 4);
    applier.apply(wid, java.util.Arrays.asList(f1));
    applier.apply(wid, java.util.Arrays.asList(f2));
    java.util.Map<String, org.waveprotocol.box.server.persistence.blocks.VersionRange> state = applier.getStateFor(wid);
    assertEquals(org.waveprotocol.box.server.persistence.blocks.VersionRange.of(3,4).toString(), state.get("blip:b+1").toString());
    // history should contain both entries, most recent last
    java.util.List<SkeletonRawFragmentsApplier.HistoryEntry> hist = applier.getHistoryFor(wid);
    assertEquals(2, hist.size());
    assertEquals(3, hist.get(1).from);
    assertEquals(4, hist.get(1).to);
  }

  @Test
  public void negativeRangesAreRejected() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier(5);
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    applier.apply(wid, java.util.Arrays.asList(new RawFragment("index", -1, 0)));
    assertEquals(0, applier.getAppliedCount());
    assertEquals(1, applier.getRejectedCount());
    assertTrue(applier.getHistoryFor(wid).isEmpty());
  }
}

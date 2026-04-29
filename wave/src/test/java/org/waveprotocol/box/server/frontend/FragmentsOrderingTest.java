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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.frontend.FragmentsFetcherCompat.BlipMeta;
import org.waveprotocol.wave.model.wave.ParticipantId;

/** Lightweight tests for manifest-ordered slicing. */
public final class FragmentsOrderingTest {

  private static Map<String, BlipMeta> metas(long t1, long t2, long t3) {
    Map<String, BlipMeta> m = new LinkedHashMap<>();
    ParticipantId a = ParticipantId.ofUnsafe("a@example.com");
    m.put("b+1", new BlipMeta(a, t1));
    m.put("b+2", new BlipMeta(a, t2));
    m.put("b+3", new BlipMeta(a, t3));
    return m;
  }

  @Test
  public void sliceUsesProvidedOrder() {
    Map<String, BlipMeta> m = metas(5, 1, 3);
    List<String> order = new ArrayList<>();
    order.add("b+2"); order.add("b+3"); order.add("b+1");
    List<String> out = FragmentsFetcherCompat.sliceUsingOrder(m, order, "b+3", "forward", 2);
    assertEquals(java.util.Arrays.asList("b+3", "b+1"), out);
  }

  @Test
  public void sliceFallsBackToMtimeWhenNoOrder() {
    Map<String, BlipMeta> m = metas(5, 1, 3);
    // mtime asc: [b+2, b+3, b+1]; start at b+3, limit 2
    List<String> out = FragmentsFetcherCompat.sliceUsingOrder(m, java.util.Collections.emptyList(),
        "b+3", "forward", 2);
    assertEquals(java.util.Arrays.asList("b+3", "b+1"), out);
  }

  @Test
  public void sliceFallsBackToMtimeWhenStartIsMissingFromOrder() {
    Map<String, BlipMeta> m = metas(5, 1, 3);
    List<String> order = new ArrayList<>();
    order.add("b+2"); order.add("b+1");
    List<String> out = FragmentsFetcherCompat.sliceUsingOrder(m, order, "b+3", "forward", 2);
    assertEquals(java.util.Arrays.asList("b+3", "b+1"), out);
  }

  @Test
  public void sliceUsesProvidedOrderWhenUnknownStartIsMissingFromMetas() {
    Map<String, BlipMeta> m = metas(5, 1, 3);
    List<String> order = new ArrayList<>();
    order.add("b+2"); order.add("b+3"); order.add("b+1");
    List<String> out =
        FragmentsFetcherCompat.sliceUsingOrder(m, order, "b+missing", "forward", 2);
    assertEquals(java.util.Arrays.asList("b+2", "b+3"), out);
  }
}

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

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.util.RouteWaveToken;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;

public final class GwtRouteWaveTokenTest extends TestCase {

  public void testRouteWaveParameterWinsOverStaleHistoryToken() {
    assertEquals(
        "example.com/w+from-query",
        RouteWaveToken.selectInitialToken(
            "example.com/w+from-hash",
            "example.com/w+from-query",
            null,
            JavaWaverefEncoder.INSTANCE));
  }

  public void testSameWavePreservesHistoryFocusMetadata() {
    assertEquals(
        "example.com/w+same/~/conv+root/b+child&focus=b+child&slide-nav=1",
        RouteWaveToken.selectInitialToken(
            "example.com/w+same/~/conv+root/b+child&focus=b+child&slide-nav=1",
            "example.com/w+same",
            null,
            JavaWaverefEncoder.INSTANCE));
  }

  public void testRouteDepthWinsOverSameWaveHashWithoutFocus() {
    assertEquals(
        "example.com/w+same/~/conv+root/b+leaf",
        RouteWaveToken.selectInitialToken(
            "example.com/w+same",
            "example.com/w+same",
            "b+leaf",
            JavaWaverefEncoder.INSTANCE));
  }

  public void testRouteDepthBuildsBlipTokenWhenHashIsMissing() {
    assertEquals(
        "example.com/w+from-query/~/conv+root/b+leaf",
        RouteWaveToken.selectInitialToken(
            "",
            "example.com/w+from-query",
            "b+leaf",
            JavaWaverefEncoder.INSTANCE));
  }

  public void testRouteDepthPreservesRouteWavelet() {
    assertEquals(
        "example.com/w+from-query/~/conv+private/b+leaf",
        RouteWaveToken.selectInitialToken(
            "",
            "example.com/w+from-query/~/conv+private",
            "b+leaf",
            JavaWaverefEncoder.INSTANCE));
  }

  public void testRouteWaveletWinsOverSameWaveHashWithDifferentWavelet() {
    assertEquals(
        "example.com/w+same/~/conv+private/b+leaf",
        RouteWaveToken.selectInitialToken(
            "example.com/w+same/~/conv+root/b+leaf",
            "example.com/w+same/~/conv+private",
            "b+leaf",
            JavaWaverefEncoder.INSTANCE));
  }

  public void testRouteDepthRejectsHistoryMetadataSeparators() {
    assertEquals(
        "example.com/w+from-query",
        RouteWaveToken.selectInitialToken(
            "",
            "example.com/w+from-query",
            "b+leaf&focus=b+other",
            JavaWaverefEncoder.INSTANCE));
  }

  public void testInvalidRouteWaveKeepsHistoryToken() {
    assertEquals(
        "example.com/w+from-hash",
        RouteWaveToken.selectInitialToken(
            "example.com/w+from-hash",
            "not-a-wave",
            null,
            JavaWaverefEncoder.INSTANCE));
  }
}

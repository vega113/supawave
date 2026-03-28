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

package org.waveprotocol.box.common;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SearchResultUpdateTest extends TestCase {
  private static final int UNKNOWN_SIZE = -1;

  public void testEmptyRefreshIsNotVacuousWhenCurrentResultsStillContainDigests() {
    List<String> currentResults = Collections.singletonList("stale-digest");

    assertFalse(SearchResultUpdate.isVacuousRefresh(
        0, currentResults, 0, Collections.emptyList(), false));
  }

  public void testUnknownTotalEmptyRefreshIsNotVacuousWhenCurrentResultsExist() {
    List<String> currentResults = Collections.singletonList("stale-digest");

    assertFalse(SearchResultUpdate.isVacuousRefresh(
        UNKNOWN_SIZE,
        currentResults,
        UNKNOWN_SIZE,
        Collections.emptyList(),
        true));
  }

  public void testMatchingResultsRemainVacuous() {
    List<String> currentResults = Arrays.asList("a", "b");
    List<String> refreshedResults = Arrays.asList("a", "b");

    assertTrue(SearchResultUpdate.isVacuousRefresh(
        2, currentResults, 2, refreshedResults, false));
  }
}

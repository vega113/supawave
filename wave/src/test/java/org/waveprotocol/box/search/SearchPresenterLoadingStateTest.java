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

package org.waveprotocol.box.search;

import junit.framework.TestCase;

public final class SearchPresenterLoadingStateTest extends TestCase {

  public void testShouldShowLoadingSkeletonDuringInitialSearchOnly() {
    assertTrue(SearchBootstrapUiState.allowLoadingSkeletonForSearchStart(0));
    assertFalse(SearchBootstrapUiState.allowLoadingSkeletonForSearchStart(2));
    assertTrue(SearchBootstrapUiState.shouldShowLoadingSkeleton(true, true, 0));
    assertFalse(SearchBootstrapUiState.shouldShowLoadingSkeleton(false, true, 0));
    assertFalse(SearchBootstrapUiState.shouldShowLoadingSkeleton(true, true, 2));
    assertFalse(SearchBootstrapUiState.shouldShowLoadingSkeleton(true, false, 0));
  }

  public void testReconnectRetryStopsAfterOtTimeout() {
    assertTrue(SearchBootstrapUiState.shouldRetryOtSubscriptionOnReconnect(true, false, false));
    assertFalse(SearchBootstrapUiState.shouldRetryOtSubscriptionOnReconnect(true, false, true));
    assertFalse(SearchBootstrapUiState.shouldRetryOtSubscriptionOnReconnect(true, true, false));
    assertFalse(SearchBootstrapUiState.shouldRetryOtSubscriptionOnReconnect(false, false, false));
  }

  public void testOtBootstrapUsesOtChannelWhenEnabled() {
    assertFalse(SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(true, false));
    assertFalse(SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(true, true));
    assertTrue(SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(false, false));
  }

  public void testShowMoreHttpFallbackRequiresExplicitFlagWhenOtNotReady() {
    assertFalse(SearchBootstrapUiState.shouldUseHttpSearchForWindowRequest(true, false, false));
    assertTrue(SearchBootstrapUiState.shouldUseHttpSearchForWindowRequest(true, false, true));
    assertFalse(SearchBootstrapUiState.shouldUseHttpSearchForWindowRequest(true, true, true));
    assertTrue(SearchBootstrapUiState.shouldUseHttpSearchForWindowRequest(false, false, false));
  }
}

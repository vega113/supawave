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

public final class SearchBootstrapUiState {

  private SearchBootstrapUiState() {
  }

  public static boolean allowLoadingSkeletonForSearchStart(int minimumTotal) {
    return minimumTotal == 0;
  }

  public static boolean shouldShowLoadingSkeleton(
      boolean allowLoadingSkeleton, boolean searching, int minimumTotal) {
    return allowLoadingSkeleton && searching && minimumTotal == 0;
  }

  public static boolean shouldRetryOtSubscriptionOnReconnect(
      boolean otSearchEnabled, boolean useOtSearch, boolean otSearchTimedOut) {
    return otSearchEnabled && !useOtSearch && !otSearchTimedOut;
  }

  public static boolean shouldBootstrapViaHttpWhenOtStarts(
      boolean otSearchEnabled, boolean otSearchFallbackEnabled) {
    return !otSearchEnabled;
  }

  public static boolean shouldUseHttpSearchForWindowRequest(
      boolean otSearchEnabled, boolean useOtSearch, boolean otSearchFallbackEnabled) {
    if (!otSearchEnabled) {
      return true;
    }
    if (useOtSearch) {
      return false;
    }
    return otSearchFallbackEnabled;
  }
}

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

package org.waveprotocol.wave.model.util;

/**
 * Width-aware policy for deciding when a reply branch should be promoted into
 * focused-thread mode instead of staying inline with its ancestors.
 */
public final class ThreadFocusPolicy {
  static final int DESKTOP_INLINE_DEPTH_LIMIT = 2;
  static final int DESKTOP_MIN_CONTENT_WIDTH_PX = 420;
  static final int MOBILE_MIN_CONTENT_WIDTH_PX = 300;

  private ThreadFocusPolicy() {
  }

  public static boolean shouldUseFocusedThread(
      boolean mobile, int depth, int availableWidthPx, boolean editing) {
    int minWidth = mobile ? MOBILE_MIN_CONTENT_WIDTH_PX : DESKTOP_MIN_CONTENT_WIDTH_PX;

    if (mobile) {
      return depth > 0 || availableWidthPx < minWidth;
    }

    if (editing) {
      return availableWidthPx < minWidth;
    }

    return depth > DESKTOP_INLINE_DEPTH_LIMIT && availableWidthPx < minWidth;
  }
}

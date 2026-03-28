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

public final class DigestStateMergingTest extends TestCase {

  public void testSnapshotCountsWinUntilLiveDigestChanges() {
    DigestStateMerging merging = new DigestStateMerging();

    merging.reset();
    merging.onSnapshotUpdated(5, 7, 2, 4);

    assertEquals(5, merging.resolveUnreadCount(5, 2));
    assertEquals(7, merging.resolveBlipCount(7, 4));

    merging.reset();

    assertEquals(2, merging.resolveUnreadCount(5, 2));
    assertEquals(4, merging.resolveBlipCount(7, 4));
  }

  public void testHigherLiveCountsRemainVisible() {
    DigestStateMerging merging = new DigestStateMerging();

    merging.reset();
    merging.onSnapshotUpdated(2, 4, 5, 6);

    assertEquals(5, merging.resolveUnreadCount(2, 5));
    assertEquals(6, merging.resolveBlipCount(4, 6));
  }
}

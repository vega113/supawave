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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import junit.framework.TestCase;

/**
 * Regression coverage for reaction inspect/toggle gesture state.
 */
public final class ReactionInteractionTrackerTest extends TestCase {

  public void testTouchInspectConsumesOnlyTheArmedTap() {
    ReactionInteractionTracker tracker = new ReactionInteractionTracker(900d);

    tracker.armTouchInspect("b+1", "thumbs_up", 100d);

    assertTrue(tracker.consumeTouchInspect("b+1", "thumbs_up", 150d));
    assertFalse(tracker.consumeTouchInspect("b+1", "thumbs_up", 160d));
  }

  public void testTouchInspectIgnoresDesktopClickWithoutTouchArm() {
    ReactionInteractionTracker tracker = new ReactionInteractionTracker(900d);

    assertFalse(tracker.consumeTouchInspect("b+1", "thumbs_up", 150d));
  }

  public void testClickSuppressionClearsAfterOneConsumedClick() {
    ReactionInteractionTracker tracker = new ReactionInteractionTracker(900d);

    tracker.suppressClick("b+1", "thumbs_up", 100d);

    assertTrue(tracker.shouldSuppressClick("b+1", "thumbs_up", 150d));
    assertFalse(tracker.shouldSuppressClick("b+1", "thumbs_up", 200d));
  }
}

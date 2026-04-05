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

package org.waveprotocol.wave.model.supplement;

import junit.framework.TestCase;
import org.waveprotocol.wave.model.testing.BasicFactories;
import org.waveprotocol.wave.model.testing.FakeWaveView;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.id.WaveletId;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies folder add/remove listener callbacks fire correctly.
 */
public final class WaveletBasedSupplementFolderListenerTest extends TestCase {

  public void testFolderAddRemoveCallbacks() {
    FakeWaveView view = BasicFactories.fakeWaveViewBuilder().build();
    Wavelet userData = view.createUserData();
    WaveletBasedSupplement supp = WaveletBasedSupplement.create(userData);

    AtomicInteger added = new AtomicInteger(0);
    AtomicInteger removed = new AtomicInteger(0);

    supp.addListener(new ObservablePrimitiveSupplement.Listener() {
      @Override
      public void onLastReadBlipVersionChanged(WaveletId wid, String bid, int oldVersion, int newVersion) {}
      @Override
      public void onLastReadWaveletVersionChanged(WaveletId wid, int oldVersion, int newVersion) {}
      @Override
      public void onLastReadParticipantsVersionChanged(WaveletId wid, int oldVersion, int newVersion) {}
      @Override
      public void onLastReadTagsVersionChanged(WaveletId wid, int oldVersion, int newVersion) {}
      @Override
      public void onFollowed() {}
      @Override
      public void onUnfollowed() {}
      @Override
      public void onFollowCleared() {}
      @Override
      public void onArchiveVersionChanged(WaveletId wid, int oldVersion, int newVersion) {}
      @Override
      public void onArchiveClearChanged(boolean oldValue, boolean newValue) {}
      @Override
      public void onFolderAdded(int newFolder) { added.incrementAndGet(); }
      @Override
      public void onFolderRemoved(int oldFolder) { removed.incrementAndGet(); }
      @Override
      public void onWantedEvaluationsChanged(WaveletId wid) {}
      @Override
      public void onThreadStateChanged(WaveletId wid, String tid, ThreadState oldState, ThreadState newState) {}
    });

    // Add and remove folder id 5
    supp.addFolder(5);
    supp.removeFolder(5);

    assertEquals("onFolderAdded should be called once", 1, added.get());
    assertEquals("onFolderRemoved should be called once", 1, removed.get());
  }
}

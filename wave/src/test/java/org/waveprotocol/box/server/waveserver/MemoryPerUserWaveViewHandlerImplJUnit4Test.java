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

package org.waveprotocol.box.server.waveserver;

import org.junit.Before;
import org.junit.Test;

/**
 * JUnit4 wrapper so sbt/junit-interface discovers the
 * {@link MemoryPerUserWaveViewHandlerImplTest} cases reliably.
 */
public class MemoryPerUserWaveViewHandlerImplJUnit4Test
    extends MemoryPerUserWaveViewHandlerImplTest {

  @Before
  public void init() throws Exception {
    super.setUp();
  }

  @Test
  public void retrievePerUserWaveViewReloadsColdWaveMap() {
    super.testRetrievePerUserWaveViewReloadsColdWaveMap();
  }

  @Test
  public void rapidCacheMissesShareSingleLoad() {
    super.testRapidCacheMissesShareSingleLoad();
  }

  @Test
  public void retrievePerUserWaveViewCacheHitSkipsReload() {
    super.testRetrievePerUserWaveViewCacheHitSkipsReload();
  }

  @Test
  public void waveletCommittedSkipsInvalidateWhenCommittedVersionCheckRequiresWriteLock()
      throws Exception {
    super.testWaveletCommittedSkipsInvalidateWhenCommittedVersionCheckRequiresWriteLock();
  }

  @Test
  public void waveletCommittedMarksWaveMapDirtyWhenInvalidateThrowsRuntimeException()
      throws Exception {
    super.testWaveletCommittedMarksWaveMapDirtyWhenInvalidateThrowsRuntimeException();
  }

  @Test
  public void waveletCommittedSkipsInvalidateWhenCachedWaveletStillLoading()
      throws Exception {
    super.testWaveletCommittedSkipsInvalidateWhenCachedWaveletStillLoading();
  }
}

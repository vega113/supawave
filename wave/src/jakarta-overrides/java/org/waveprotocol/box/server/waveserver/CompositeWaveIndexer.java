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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class CompositeWaveIndexer implements WaveIndexer {

  private final WaveIndexer legacyWaveIndexer;
  private final WaveIndexer lucene9WaveIndexer;

  @Inject
  public CompositeWaveIndexer(@Named("legacyWaveIndexer") WaveIndexer legacyWaveIndexer,
      @Named("lucene9WaveIndexer") WaveIndexer lucene9WaveIndexer) {
    this.legacyWaveIndexer = legacyWaveIndexer;
    this.lucene9WaveIndexer = lucene9WaveIndexer;
  }

  @Override
  public void remakeIndex() throws WaveletStateException, WaveServerException {
    Exception legacyFailure = null;
    try {
      legacyWaveIndexer.remakeIndex();
    } catch (WaveletStateException | WaveServerException | RuntimeException e) {
      legacyFailure = e;
    }
    Exception lucene9Failure = null;
    try {
      lucene9WaveIndexer.remakeIndex();
    } catch (WaveletStateException | WaveServerException | RuntimeException e) {
      lucene9Failure = e;
    }
    if (legacyFailure != null && lucene9Failure != null) {
      WaveServerException aggregated = new WaveServerException(
          "Both legacy and lucene9 index rebuilds failed", legacyFailure);
      aggregated.addSuppressed(lucene9Failure);
      throw aggregated;
    }
    if (legacyFailure != null) {
      throwAppropriate(legacyFailure);
    }
    if (lucene9Failure != null) {
      throwAppropriate(lucene9Failure);
    }
  }

  private static void throwAppropriate(Exception e)
      throws WaveletStateException, WaveServerException {
    if (e instanceof WaveletStateException) {
      throw (WaveletStateException) e;
    }
    if (e instanceof WaveServerException) {
      throw (WaveServerException) e;
    }
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    throw new WaveServerException(e);
  }
}

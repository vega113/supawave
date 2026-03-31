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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;

/**
 * Manages async Lucene reindex operations triggered by admin dashboard.
 * Only one reindex job can run at a time.
 */
@Singleton
public class ReindexService {

  private static final Logger LOG = Logger.getLogger(ReindexService.class.getName());

  public enum State { IDLE, RUNNING, COMPLETED, FAILED }

  private final @Nullable Lucene9WaveIndexerImpl indexer;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "reindex-worker");
    t.setDaemon(true);
    return t;
  });

  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private volatile long startTimeMs;
  private volatile long endTimeMs;
  private volatile int waveCount;
  private volatile String errorMessage;
  private volatile String triggeredBy;

  @Inject
  public ReindexService(@Nullable Lucene9WaveIndexerImpl indexer) {
    this.indexer = indexer;
  }

  /**
   * Triggers an async reindex. Returns true if the job was started, false if
   * already running or no indexer is available.
   */
  public boolean triggerReindex(String adminUser) {
    if (indexer == null) {
      LOG.warning("Reindex requested but no Lucene9 indexer available (search_type != lucene)");
      return false;
    }
    if (!state.compareAndSet(State.IDLE, State.RUNNING)
        && !state.compareAndSet(State.COMPLETED, State.RUNNING)
        && !state.compareAndSet(State.FAILED, State.RUNNING)) {
      return false;
    }

    this.triggeredBy = adminUser;
    this.startTimeMs = System.currentTimeMillis();
    this.endTimeMs = 0;
    this.waveCount = 0;
    this.errorMessage = null;

    LOG.info("Admin reindex triggered by " + adminUser);
    executor.submit(() -> {
      try {
        int count = indexer.forceRemakeIndex();
        this.waveCount = count;
        this.endTimeMs = System.currentTimeMillis();
        this.state.set(State.COMPLETED);
        LOG.info("Admin reindex completed: " + count + " waves in "
            + (endTimeMs - startTimeMs) + " ms");
      } catch (Exception e) {
        this.errorMessage = e.getMessage();
        this.endTimeMs = System.currentTimeMillis();
        this.state.set(State.FAILED);
        LOG.log(Level.SEVERE, "Admin reindex failed", e);
      }
    });
    return true;
  }

  /**
   * Records a startup reindex result so the dashboard can display it.
   */
  public void recordStartupReindex(int waveCount) {
    this.triggeredBy = "startup";
    this.startTimeMs = System.currentTimeMillis();
    this.endTimeMs = System.currentTimeMillis();
    this.waveCount = waveCount;
    this.state.set(State.COMPLETED);
  }

  public State getState() { return state.get(); }
  public long getStartTimeMs() { return startTimeMs; }
  public long getEndTimeMs() { return endTimeMs; }
  public int getWaveCount() { return waveCount; }
  public String getErrorMessage() { return errorMessage; }
  public String getTriggeredBy() { return triggeredBy; }
}

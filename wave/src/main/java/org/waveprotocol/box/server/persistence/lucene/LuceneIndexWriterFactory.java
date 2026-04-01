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
package org.waveprotocol.box.server.persistence.lucene;

import java.io.IOException;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.waveprotocol.box.server.waveserver.IndexException;

/**
 * Shared utility for opening a Lucene {@link IndexWriter} with write-lock
 * retry logic, intended for use during start-first rolling deploys where the
 * previous container may still hold the lock.
 */
public final class LuceneIndexWriterFactory {

  /** Maximum attempts to acquire the Lucene write lock before giving up. */
  public static final int LOCK_RETRY_ATTEMPTS = 12;

  /** Delay between write-lock acquisition attempts. */
  public static final long LOCK_RETRY_DELAY_MS = 5_000;

  private LuceneIndexWriterFactory() {}

  /**
   * Opens an {@link IndexWriter} on {@code directory}, retrying up to
   * {@link #LOCK_RETRY_ATTEMPTS} times when the write lock is held by a
   * previous process (e.g. during a rolling deploy).
   *
   * @throws IndexException if the lock cannot be acquired or an I/O error occurs
   */
  public static IndexWriter openWithRetry(Directory directory, Analyzer analyzer, Logger log) {
    for (int attempt = 1; attempt <= LOCK_RETRY_ATTEMPTS; attempt++) {
      try {
        return new IndexWriter(directory, new IndexWriterConfig(analyzer));
      } catch (org.apache.lucene.store.LockObtainFailedException e) {
        if (attempt == LOCK_RETRY_ATTEMPTS) {
          throw new IndexException("Failed to acquire Lucene write lock after "
              + LOCK_RETRY_ATTEMPTS + " attempts", e);
        }
        log.info("Lucene write lock held by previous instance, retrying in "
            + (LOCK_RETRY_DELAY_MS / 1000) + "s (attempt " + attempt + "/"
            + LOCK_RETRY_ATTEMPTS + ")");
        try {
          Thread.sleep(LOCK_RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IndexException("Interrupted waiting for Lucene write lock", ie);
        }
      } catch (IOException e) {
        throw new IndexException(e);
      }
    }
    throw new IndexException("Unreachable");
  }
}

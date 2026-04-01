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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl.ReindexStats;

public class ReindexServiceTest {

  private Lucene9WaveIndexerImpl mockIndexer;
  private ReindexService service;

  private static ReindexStats makeStats(int waveCount) {
    // sumNs=waveCount*10ms, minNs=5ms, maxNs=20ms
    return new ReindexStats(waveCount, 0, waveCount * 10L,
        waveCount * 10_000_000L, 5_000_000L, 20_000_000L);
  }

  @Before
  public void setUp() {
    mockIndexer = mock(Lucene9WaveIndexerImpl.class);
    when(mockIndexer.getLastRebuildWaveCount()).thenReturn(-1);
    when(mockIndexer.getIndexedDocCount()).thenReturn(0);
    service = new ReindexService(mockIndexer);
  }

  @Test
  public void testInitialStateIsIdle() {
    assertEquals(ReindexService.State.IDLE, service.getState());
  }

  @Test
  public void testTriggerReindexStartsJob() throws Exception {
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class))).thenReturn(makeStats(42));

    boolean started = service.triggerReindex("admin@test.com");
    assertTrue(started);

    // Wait for async completion
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(ReindexService.State.COMPLETED, service.getState());
    assertEquals(42, service.getWaveCount());
    assertEquals("admin@test.com", service.getTriggeredBy());
    assertTrue(service.getEndTimeMs() >= service.getStartTimeMs());
    assertTrue(service.getLastAvgMsPerWave() > 0);
  }

  @Test
  public void testConcurrentReindexReturnsConflict() throws Exception {
    // Block the indexer so the first job stays RUNNING
    CountDownLatch latch = new CountDownLatch(1);
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class))).thenAnswer(inv -> {
      latch.await(10, TimeUnit.SECONDS);
      return makeStats(10);
    });

    assertTrue(service.triggerReindex("admin1@test.com"));
    // Wait a moment for the executor to pick up the job
    awaitState(ReindexService.State.RUNNING, 2000);

    // Second trigger should fail
    assertFalse(service.triggerReindex("admin2@test.com"));
    assertEquals("admin1@test.com", service.getTriggeredBy());

    latch.countDown();
    awaitState(ReindexService.State.COMPLETED, 5000);
  }

  @Test
  public void testFailedReindexSetsErrorState() throws Exception {
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class)))
        .thenThrow(new RuntimeException("disk full"));

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.FAILED, 5000);

    assertEquals(ReindexService.State.FAILED, service.getState());
    assertEquals("disk full", service.getErrorMessage());
  }

  @Test
  public void testCanRetriggerAfterCompletion() throws Exception {
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class))).thenReturn(makeStats(10));

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);

    // Should be able to trigger again
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class))).thenReturn(makeStats(20));
    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(20, service.getWaveCount());
  }

  @Test
  public void testCanRetriggerAfterFailure() throws Exception {
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class)))
        .thenThrow(new RuntimeException("fail"));

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.FAILED, 5000);

    // Should be able to trigger again
    doReturn(makeStats(15)).when(mockIndexer).forceRemakeIndex(any(IntConsumer.class));
    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(15, service.getWaveCount());
  }

  @Test
  public void testNullIndexerReturnsNotStarted() {
    ReindexService noIndexer = new ReindexService(null);
    assertFalse(noIndexer.triggerReindex("admin@test.com"));
    assertEquals(ReindexService.State.IDLE, noIndexer.getState());
  }

  @Test
  public void testRecordStartupReindex() {
    service.recordStartupReindex(134);
    assertEquals(ReindexService.State.COMPLETED, service.getState());
    assertEquals(134, service.getWaveCount());
    assertEquals("startup", service.getTriggeredBy());
  }

  @Test
  public void testRecordStartupReindexWithStats() {
    ReindexStats stats = makeStats(200);
    service.recordStartupReindex(stats);
    assertEquals(ReindexService.State.COMPLETED, service.getState());
    assertEquals(200, service.getWaveCount());
    assertEquals("startup", service.getTriggeredBy());
    assertTrue(service.getLastAvgMsPerWave() > 0);
  }

  @Test
  public void testProgressTrackingDuringReindex() throws Exception {
    when(mockIndexer.getLastRebuildWaveCount()).thenReturn(100);
    when(mockIndexer.forceRemakeIndex(any(IntConsumer.class))).thenAnswer(inv -> {
      IntConsumer callback = inv.getArgument(0);
      for (int i = 1; i <= 50; i++) callback.accept(i);
      return makeStats(50);
    });

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(100, service.getEstimatedTotalWaves());
    assertEquals(50, service.getWavesIndexedSoFar());
  }

  private void awaitState(ReindexService.State expected, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (service.getState() != expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertEquals("Timed out waiting for state " + expected, expected, service.getState());
  }
}

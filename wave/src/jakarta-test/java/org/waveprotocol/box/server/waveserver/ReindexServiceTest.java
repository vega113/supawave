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
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;

public class ReindexServiceTest {

  private Lucene9WaveIndexerImpl mockIndexer;
  private ReindexService service;

  @Before
  public void setUp() {
    mockIndexer = mock(Lucene9WaveIndexerImpl.class);
    service = new ReindexService(mockIndexer);
  }

  @Test
  public void testInitialStateIsIdle() {
    assertEquals(ReindexService.State.IDLE, service.getState());
  }

  @Test
  public void testTriggerReindexStartsJob() throws Exception {
    when(mockIndexer.forceRemakeIndex()).thenReturn(42);

    boolean started = service.triggerReindex("admin@test.com");
    assertTrue(started);

    // Wait for async completion
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(ReindexService.State.COMPLETED, service.getState());
    assertEquals(42, service.getWaveCount());
    assertEquals("admin@test.com", service.getTriggeredBy());
    assertTrue(service.getEndTimeMs() >= service.getStartTimeMs());
  }

  @Test
  public void testConcurrentReindexReturnsConflict() throws Exception {
    // Block the indexer so the first job stays RUNNING
    CountDownLatch latch = new CountDownLatch(1);
    when(mockIndexer.forceRemakeIndex()).thenAnswer(inv -> {
      latch.await(10, TimeUnit.SECONDS);
      return 10;
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
    when(mockIndexer.forceRemakeIndex()).thenThrow(new RuntimeException("disk full"));

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.FAILED, 5000);

    assertEquals(ReindexService.State.FAILED, service.getState());
    assertEquals("disk full", service.getErrorMessage());
  }

  @Test
  public void testCanRetriggerAfterCompletion() throws Exception {
    when(mockIndexer.forceRemakeIndex()).thenReturn(10);

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);

    // Should be able to trigger again
    when(mockIndexer.forceRemakeIndex()).thenReturn(20);
    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.COMPLETED, 5000);
    assertEquals(20, service.getWaveCount());
  }

  @Test
  public void testCanRetriggerAfterFailure() throws Exception {
    when(mockIndexer.forceRemakeIndex()).thenThrow(new RuntimeException("fail"));

    assertTrue(service.triggerReindex("admin@test.com"));
    awaitState(ReindexService.State.FAILED, 5000);

    // Should be able to trigger again — use doReturn to avoid re-triggering the throw
    doReturn(15).when(mockIndexer).forceRemakeIndex();
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

  private void awaitState(ReindexService.State expected, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (service.getState() != expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertEquals("Timed out waiting for state " + expected, expected, service.getState());
  }
}

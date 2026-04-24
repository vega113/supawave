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

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.util.Pair;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests {@link BlockingSuccessFailCallback}.
 */
public class BlockingSuccessFailCallbackTest extends TestCase {

  public void testAwaitReturnsSuccessResult() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    callback.onSuccess("ok");
    Pair<String, Exception> result = callback.await(0, TimeUnit.MILLISECONDS);

    assertNotNull(result);
    assertEquals("ok", result.first);
    assertNull(result.second);
  }

  public void testAwaitReturnsFailureResult() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();
    Exception failure = new Exception("failed");

    callback.onFailure(failure);
    Pair<String, Exception> result = callback.await(0, TimeUnit.MILLISECONDS);

    assertNotNull(result);
    assertNull(result.first);
    assertSame(failure, result.second);
  }

  public void testAwaitReturnsNullOnTimeout() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    assertNull(callback.await(0, TimeUnit.MILLISECONDS));
  }

  public void testOnSuccessRejectsNullValue() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    assertThrowsIllegalArgument(new Runnable() {
      @Override
      public void run() {
        callback.onSuccess(null);
      }
    });
  }

  public void testOnFailureRejectsNullValue() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    assertThrowsIllegalArgument(new Runnable() {
      @Override
      public void run() {
        callback.onFailure(null);
      }
    });
  }

  public void testDuplicateSuccessCallbackIsRejected() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    callback.onSuccess("first");

    assertThrowsIllegalState(new Runnable() {
      @Override
      public void run() {
        callback.onSuccess("second");
      }
    });
  }

  public void testDuplicateFailureCallbackIsRejected() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    callback.onFailure(new Exception("first"));

    assertThrowsIllegalState(new Runnable() {
      @Override
      public void run() {
        callback.onFailure(new Exception("second"));
      }
    });
  }

  public void testFailureAfterSuccessIsRejected() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    callback.onSuccess("ok");

    assertThrowsIllegalState(new Runnable() {
      @Override
      public void run() {
        callback.onFailure(new Exception("failed"));
      }
    });
  }

  public void testSuccessAfterFailureIsRejected() {
    BlockingSuccessFailCallback<String, Exception> callback = BlockingSuccessFailCallback.create();

    callback.onFailure(new Exception("failed"));

    assertThrowsIllegalState(new Runnable() {
      @Override
      public void run() {
        callback.onSuccess("ok");
      }
    });
  }

  public void testConcurrentMixedCallbacksOnlyOneCompletes() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertConcurrentMixedCallbackOnlyOneCompletes();
    }
  }

  private static void assertConcurrentMixedCallbackOnlyOneCompletes() throws Exception {
    final BlockingSuccessFailCallback<String, Exception> callback =
        BlockingSuccessFailCallback.create();
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(2);
    final AtomicInteger completed = new AtomicInteger();
    final AtomicInteger rejected = new AtomicInteger();

    Thread successThread = new Thread(new Runnable() {
      @Override
      public void run() {
        awaitUninterruptibly(start);
        try {
          callback.onSuccess("ok");
          completed.incrementAndGet();
        } catch (IllegalStateException expected) {
          rejected.incrementAndGet();
        } finally {
          done.countDown();
        }
      }
    });
    Thread failureThread = new Thread(new Runnable() {
      @Override
      public void run() {
        awaitUninterruptibly(start);
        try {
          callback.onFailure(new Exception("failed"));
          completed.incrementAndGet();
        } catch (IllegalStateException expected) {
          rejected.incrementAndGet();
        } finally {
          done.countDown();
        }
      }
    });

    successThread.start();
    failureThread.start();
    start.countDown();
    assertTrue("Timed out waiting for concurrent callbacks", done.await(5, TimeUnit.SECONDS));

    assertEquals(1, completed.get());
    assertEquals(1, rejected.get());
    Pair<String, Exception> result = callback.await(0, TimeUnit.MILLISECONDS);
    assertNotNull(result);
    assertTrue((result.first == null) != (result.second == null));
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
        return;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
  }

  private static void assertThrowsIllegalArgument(Runnable action) {
    try {
      action.run();
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private static void assertThrowsIllegalState(Runnable action) {
    try {
      action.run();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // Expected.
    }
  }
}

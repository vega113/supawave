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

import com.google.common.base.Preconditions;

import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.util.logging.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@code SuccessFailCallback} which supports blocking for a response.  Note that neither
 * {@link #onSuccess} nor {@link #onFailure} will accept a null argument.
 * This helper is intended for tests and blocking bridge code that must fail fast when a callback is
 * used incorrectly: duplicate callbacks, mixed success/failure callbacks, and null callback values
 * are programmer errors and are rejected immediately.
 */
public class BlockingSuccessFailCallback<S, F> implements SuccessFailCallback<S, F> {

  private final static Log LOG = Log.get(BlockingSuccessFailCallback.class);

  private final AtomicReference<S> successResult = new AtomicReference<S>(null);
  private final AtomicReference<F> failureResult = new AtomicReference<F>(null);
  private final AtomicBoolean completed = new AtomicBoolean(false);
  private final CountDownLatch awaitLatch = new CountDownLatch(1);
  private final String description;

  private BlockingSuccessFailCallback(String description) {
    this.description = description;
  }

  public static <S, F> BlockingSuccessFailCallback<S, F> create() {
    StackTraceElement caller = new Throwable().getStackTrace()[1];
    return new BlockingSuccessFailCallback<S, F>("Created at " + caller.getClassName() + "."
        + caller.getMethodName() + ":" + caller.getLineNumber());
  }

  /**
   * Wait for either {@link #onSuccess} or {@link #onFailure} to be called and return both results.
   * Either the first of the pair (success value) or second of the pair (failure value) will hold
   * a not-null result indicating which method was run, while the other is guaranteed to contain
   * null.
   *
   * The return result may be null if the await did not return within the timeout.
   *
   * @param timeout
   * @param unit
   * @return pair of (success value, failure value) where exactly one will be not-null, or null
   *         if the await timed out
   */
  public Pair<S, F> await(long timeout, TimeUnit unit) {
    try {
      long startMs = System.currentTimeMillis();
      if (!awaitLatch.await(timeout, unit)) {
        LOG.warning(description + ": timed out while waiting for " + timeout + " " + unit);
        return null;
      }
      LOG.fine(description + ": await took " + (System.currentTimeMillis() - startMs) + " ms");
    } catch (InterruptedException e) {
      LOG.severe(description + ": interrupted while waiting", e);
      throw new IllegalStateException(e);
    }
    return Pair.of(successResult.get(), failureResult.get());
  }

  @Override
  public void onFailure(F failureValue) {
    Preconditions.checkArgument(failureValue != null,
        description + ": onFailure requires a non-null failure value");
    Preconditions.checkState(completed.compareAndSet(false, true),
        description + ": callback already completed before onFailure");
    failureResult.set(failureValue);
    LOG.warning(description + ": onFailure(" + failureValue + ")");
    awaitLatch.countDown();
  }

  @Override
  public void onSuccess(S successValue) {
    Preconditions.checkArgument(successValue != null,
        description + ": onSuccess requires a non-null success value");
    Preconditions.checkState(completed.compareAndSet(false, true),
        description + ": callback already completed before onSuccess");
    LOG.fine(description + ": onSuccess(" + successValue + ")");
    successResult.set(successValue);
    awaitLatch.countDown();
  }
}

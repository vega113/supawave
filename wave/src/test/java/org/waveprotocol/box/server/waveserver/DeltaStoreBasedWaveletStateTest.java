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

import com.google.common.util.concurrent.MoreExecutors;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.testing.DeltaTestUtil;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;

/**
 * Runs wavelet state tests with the {@link DeltaStoreBasedWaveletState}.
 *
 * @author soren@google.com (Soren Lassen)
 */
public class DeltaStoreBasedWaveletStateTest extends WaveletStateTestBase {

  private static final WaveletName NAME = WaveletName.of(
      WaveId.of("example.com", "stale-wave"),
      WaveletId.of("example.com", "conv+root"));
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("author@example.com");
  private static final DeltaTestUtil UTIL = new DeltaTestUtil(AUTHOR);
  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);
  private static final HashedVersion V0 = HASH_FACTORY.createVersionZero(NAME);
  private final Executor PERSIST_EXECUTOR = MoreExecutors.directExecutor();
  private DeltaStore store;

  @Override
  public void setUp() throws Exception {
    store = new MemoryDeltaStore();
    super.setUp();
  }

  @Override
  protected WaveletState createEmptyState(WaveletName name) throws Exception {
    return DeltaStoreBasedWaveletState.create(store.open(name), PERSIST_EXECUTOR);
  }

  @Override
  protected void awaitPersistence() throws Exception {
    // Same-thread executor already completed.
    return;
  }

  public void testPersistRejectsStaleWriterWhenStorageAlreadyAdvanced() throws Exception {
    WaveletState stale = createEmptyState(NAME);
    WaveletState fresh = createEmptyState(NAME);
    WaveletDeltaRecord delta = makeDelta(V0, 1234567890L, 1);

    fresh.appendDelta(delta);
    fresh.persist(delta.getResultingVersion()).get();

    stale.appendDelta(delta);
    try {
      stale.persist(delta.getResultingVersion()).get();
      fail("Expected stale persist to be rejected");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof PersistenceException);
      assertTrue(e.getCause().getMessage().contains("stale"));
    }

    WaveletDeltaRecord persisted = store.open(NAME).getDelta(0);
    assertNotNull(persisted);
    assertEquals(delta.getResultingVersion(), persisted.getResultingVersion());
  }

  public void testPersistRejectsStaleWriterWithoutLeavingQueueStuck() throws Exception {
    WaveletState stale = createEmptyState(NAME);
    WaveletState fresh = createEmptyState(NAME);
    WaveletDeltaRecord delta = makeDelta(V0, 1234567890L, 1);

    fresh.appendDelta(delta);
    fresh.persist(delta.getResultingVersion()).get();

    stale.appendDelta(delta);
    try {
      stale.persist(delta.getResultingVersion()).get();
      fail("Expected stale persist to be rejected");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof PersistenceException);
    }

    var retry = stale.persist(delta.getResultingVersion());
    assertTrue("stale retry should fail fast instead of hanging", retry.isDone());
    try {
      retry.get();
      fail("Expected stale retry to be rejected");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof PersistenceException);
      assertTrue(e.getCause().getMessage().contains("stale"));
    }
  }

  public void testPersistFailsClearlyWhenCachedDeltaIsMissing() throws Exception {
    WaveletState state = createEmptyState(NAME);
    WaveletDeltaRecord first = makeDelta(V0, 1234567890L, 1);
    WaveletDeltaRecord second = makeDelta(first.getResultingVersion(), 1234567891L, 1);

    state.appendDelta(first);
    state.appendDelta(second);
    state.flush(second.getResultingVersion());

    try {
      state.persist(second.getResultingVersion()).get();
      fail("Expected persist to fail when an in-memory delta is missing");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof PersistenceException);
      assertTrue(e.getCause().getMessage().contains("Missing cached delta"));
      assertTrue(e.getCause().getMessage().contains(NAME.toString()));
    }
  }

  public void testPersistRetriesAfterRecoverablePartialAppendFailure() throws Exception {
    DeltaStore.DeltasAccess durable = store.open(NAME);
    WaveletState state = DeltaStoreBasedWaveletState.create(
        new PartiallyFailingDeltasAccess(durable), PERSIST_EXECUTOR);
    WaveletDeltaRecord first = makeDelta(V0, 1234567890L, 1);
    WaveletDeltaRecord second = makeDelta(first.getResultingVersion(), 1234567891L, 1);

    state.appendDelta(first);
    state.appendDelta(second);

    try {
      state.persist(second.getResultingVersion()).get();
      fail("Expected initial partial append failure");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof PersistenceException);
      assertTrue(e.getCause().getMessage().contains("simulated partial append failure"));
    }

    state.persist(second.getResultingVersion()).get();

    assertEquals(second.getResultingVersion(), durable.getEndVersion());
    assertEquals(second.getResultingVersion(), state.getLastPersistedVersion());
    assertNotNull(durable.getDelta(0));
    assertNotNull(durable.getDelta(first.getResultingVersion().getVersion()));
  }

  // TODO(soren): We need to add tests here that verify interactions with storage.
  // The base tests only test the public interface, not any interactions with the storage system.

  private static WaveletDeltaRecord makeDelta(HashedVersion appliedAtVersion, long timestamp,
      int numOps) throws Exception {
    var delta = UTIL.makeNoOpDelta(appliedAtVersion, timestamp, numOps);
    var applied = WaveServerTestUtil.buildAppliedDelta(delta, timestamp);
    return new WaveletDeltaRecord(
        appliedAtVersion,
        applied,
        AppliedDeltaUtil.buildTransformedDelta(applied, delta));
  }

  private static final class PartiallyFailingDeltasAccess implements DeltaStore.DeltasAccess {
    private final DeltaStore.DeltasAccess delegate;
    private boolean failOnce = true;

    private PartiallyFailingDeltasAccess(DeltaStore.DeltasAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public WaveletName getWaveletName() {
      return delegate.getWaveletName();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public HashedVersion getEndVersion() {
      return delegate.getEndVersion();
    }

    @Override
    public WaveletDeltaRecord getDelta(long version) throws java.io.IOException {
      return delegate.getDelta(version);
    }

    @Override
    public WaveletDeltaRecord getDeltaByEndVersion(long version) throws java.io.IOException {
      return delegate.getDeltaByEndVersion(version);
    }

    @Override
    public HashedVersion getAppliedAtVersion(long version) throws java.io.IOException {
      return delegate.getAppliedAtVersion(version);
    }

    @Override
    public HashedVersion getResultingVersion(long version) throws java.io.IOException {
      return delegate.getResultingVersion(version);
    }

    @Override
    public ByteStringMessage<org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta>
        getAppliedDelta(long version) throws java.io.IOException {
      return delegate.getAppliedDelta(version);
    }

    @Override
    public TransformedWaveletDelta getTransformedDelta(long version) throws java.io.IOException {
      return delegate.getTransformedDelta(version);
    }

    @Override
    public void append(java.util.Collection<WaveletDeltaRecord> deltas) throws PersistenceException {
      if (failOnce) {
        failOnce = false;
        for (WaveletDeltaRecord delta : deltas) {
          delegate.append(java.util.Collections.singletonList(delta));
          throw new PersistenceException("simulated partial append failure");
        }
      }
      delegate.append(deltas);
    }

    @Override
    public void close() throws java.io.IOException {
      delegate.close();
    }
  }
}

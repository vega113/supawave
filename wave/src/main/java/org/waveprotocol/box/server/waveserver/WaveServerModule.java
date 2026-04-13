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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.executor.ExecutorAnnotations.StorageContinuationExecutor;
import org.waveprotocol.box.server.executor.ExecutorAnnotations.WaveletLoadExecutor;
import org.waveprotocol.box.server.executor.RequestScopeExecutor;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.wave.crypto.*;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.logging.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Guice Module for the prototype Server.
 *
 */
public class WaveServerModule extends AbstractModule {
  private static final Log LOG = Log.get(WaveServerModule.class);
  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);

  private final Executor waveletLoadExecutor;
  private final Executor storageContinuationExecutor;
  private final boolean enableFederation;
  private final int snapshotInterval;


  @Inject
  WaveServerModule(Config config,
      @WaveletLoadExecutor Executor waveletLoadExecutor,
      @StorageContinuationExecutor Executor storageContinuationExecutor) {
    this.enableFederation = config.getBoolean("federation.enable_federation");
    this.waveletLoadExecutor = waveletLoadExecutor;
    this.storageContinuationExecutor = storageContinuationExecutor;
    this.snapshotInterval = config.hasPath("core.snapshot_interval")
        ? config.getInt("core.snapshot_interval") : 0;
  }

  @Override
  protected void configure() {
    bind(TimeSource.class).to(DefaultTimeSource.class).in(Singleton.class);

    if (enableFederation) {
      bind(SignatureHandler.class)
      .toProvider(SigningSignatureHandler.SigningSignatureHandlerProvider.class);
    } else {
      bind(SignatureHandler.class)
      .toProvider(NonSigningSignatureHandler.NonSigningSignatureHandlerProvider.class);
    }

    try {
      bind(WaveSignatureVerifier.class).toConstructor(WaveSignatureVerifier.class.getConstructor(
          WaveCertPathValidator.class, CertPathStore.class));
      bind(VerifiedCertChainCache.class).to(DefaultCacheImpl.class).in(Singleton.class);
      bind(DefaultCacheImpl.class).toConstructor(
          DefaultCacheImpl.class.getConstructor(TimeSource.class));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }

    bind(WaveletNotificationDispatcher.class).in(Singleton.class);
    bind(WaveBus.class).to(WaveletNotificationDispatcher.class);
    bind(WaveletNotificationSubscriber.class).to(WaveletNotificationDispatcher.class);
    bind(TrustRootsProvider.class).to(DefaultTrustRootsProvider.class).in(Singleton.class);
    bind(CertificateManager.class).to(CertificateManagerImpl.class).in(Singleton.class);
    bind(DeltaAndSnapshotStore.class).to(DeltaStoreBasedSnapshotStore.class).in(Singleton.class);
    bind(WaveMap.class).in(Singleton.class);
    bind(WaveletProvider.class).to(WaveServerImpl.class).asEagerSingleton();
    bind(ReadableWaveletDataProvider.class).to(WaveServerImpl.class).in(Singleton.class);
    bind(HashedVersionFactory.class).toInstance(HASH_FACTORY);

    // OT search wavelet bindings (Phases 1-3)
    bind(org.waveprotocol.box.server.waveserver.search.SearchWaveletManager.class).in(Singleton.class);
    bind(org.waveprotocol.box.server.waveserver.search.SearchIndexer.class).in(Singleton.class);
    bind(org.waveprotocol.box.server.waveserver.search.SearchWaveletDataProvider.class).in(Singleton.class);
    bind(org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdater.class).in(Singleton.class);
  }

  @Provides
  @SuppressWarnings("unused")
  private LocalWaveletContainer.Factory provideLocalWaveletContainerFactory(
      final DeltaStore deltaStore, final SnapshotStore snapshotStore) {
    return new LocalWaveletContainer.Factory() {
      @Override
      public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String waveDomain) {
        return new LocalWaveletContainerImpl(waveletName, notifiee, loadWaveletState(
            waveletLoadExecutor, deltaStore, snapshotStore, snapshotInterval,
            waveletName, waveletLoadExecutor), waveDomain,
            storageContinuationExecutor);
      }
    };
  }

  @Provides
  @SuppressWarnings("unused")
  private RemoteWaveletContainer.Factory provideRemoteWaveletContainerFactory(
      final DeltaStore deltaStore, final SnapshotStore snapshotStore) {
    return new RemoteWaveletContainer.Factory() {
      @Override
      public RemoteWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String waveDomain) {
        return new RemoteWaveletContainerImpl(waveletName, notifiee, loadWaveletState(
            waveletLoadExecutor, deltaStore, snapshotStore, snapshotInterval,
            waveletName, waveletLoadExecutor),
            storageContinuationExecutor);
      }
    };
  }

    @Provides
    @SuppressWarnings("unused")
    private WaveCertPathValidator provideWaveCertPathValidator(Config config,
       TimeSource timeSource,
       VerifiedCertChainCache certCache,
       TrustRootsProvider trustRootsProvider) {
        if (config.getBoolean("federation.waveserver_disable_signer_verification")) {
            return new DisabledCertPathValidator();
        } else {
            return new CachedCertPathValidator(certCache, timeSource, trustRootsProvider);
        }
    }

  /**
   * Returns a future whose result is the state of the wavelet after it has been
   * loaded from storage. Any failure is reported as a
   * {@link PersistenceException}.
   */
  @VisibleForTesting
  static ListenableFuture<DeltaStoreBasedWaveletState> loadWaveletState(Executor executor,
      final DeltaStore deltaStore, final SnapshotStore snapshotStore, final int snapshotInterval,
      final WaveletName waveletName, final Executor persistExecutor) {
    final long scheduledAtMs = System.currentTimeMillis();
    ListenableFutureTask<DeltaStoreBasedWaveletState> task =
        ListenableFutureTask.create(
           new Callable<DeltaStoreBasedWaveletState>() {
             @Override
             public DeltaStoreBasedWaveletState call() throws PersistenceException {
               long startAtMs = System.currentTimeMillis();
               if (LOG.isFineLoggable()) {
                 LOG.fine("Starting initial wavelet load for " + waveletName
                     + " after queuedMs=" + (startAtMs - scheduledAtMs)
                     + " on " + RequestScopeExecutor.describeExecutor(executor));
               }
               try {
                 DeltaStoreBasedWaveletState state = DeltaStoreBasedWaveletState.create(
                     deltaStore.open(waveletName), snapshotStore, snapshotInterval, persistExecutor);
                 if (LOG.isFineLoggable()) {
                   LOG.fine("Finished initial wavelet load for " + waveletName
                       + " in " + (System.currentTimeMillis() - startAtMs) + "ms");
                 }
                 return state;
               } catch (PersistenceException e) {
                 LOG.warning("Initial wavelet load failed for " + waveletName
                     + " after " + (System.currentTimeMillis() - startAtMs) + "ms"
                     + " on " + RequestScopeExecutor.describeExecutor(executor), e);
                 throw e;
               }
             }
           });
    if (LOG.isFineLoggable()) {
      LOG.fine("Scheduling initial wavelet load for " + waveletName
          + " on " + RequestScopeExecutor.describeExecutor(executor));
    }
    executor.execute(task);
    return task;
  }
}

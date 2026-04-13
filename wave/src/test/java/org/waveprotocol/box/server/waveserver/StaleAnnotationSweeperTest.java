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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.CapturingOperationSink;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipationHelper;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.conversation.ConversationBlip;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Unit tests for {@link StaleAnnotationSweeper}.
 */
public class StaleAnnotationSweeperTest extends TestCase {

  private static final ParticipantId ALICE = ParticipantId.ofUnsafe("alice@example.com");
  private static final WaveId WAVE_ID = WaveId.of("example.com", "wave1");
  private static final WaveletId WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);
  private static final DocumentFactory<?> DOCUMENT_FACTORY =
      ObservablePluggableMutableDocument.createFactory(SchemaCollection.empty());
  private static final WaveletOperationContext.Factory CONTEXT_FACTORY =
      new WaveletOperationContext.Factory() {
        @Override
        public WaveletOperationContext createContext() {
          return new WaveletOperationContext(ALICE, 0L, 1);
        }

        @Override
        public WaveletOperationContext createContext(ParticipantId creator) {
          throw new UnsupportedOperationException();
        }
      };

  private WaveletProvider mockProvider;
  private StaleAnnotationSweeper sweeper;
  private ObservableWaveletData waveletData;
  private ConversationUtil conversationUtil;

  @Override
  protected void setUp() throws Exception {
    mockProvider = mock(WaveletProvider.class);
    sweeper = new StaleAnnotationSweeper(mockProvider);
    conversationUtil = new ConversationUtil(FakeIdGenerator.create());

    waveletData = WaveletDataImpl.Factory.create(DOCUMENT_FACTORY).create(
        new EmptyWaveletSnapshot(WAVE_ID, WAVELET_ID, ALICE,
            HASH_FACTORY.createVersionZero(WAVELET_NAME), 0L));
    waveletData.addParticipant(ALICE);
    waveletData.setVersion(1);
  }

  /** Builds an OpBasedWavelet backed by waveletData with op capturing. */
  private OpBasedWavelet buildWavelet(CapturingOperationSink<WaveletOperation> sink) {
    SilentOperationSink<WaveletOperation> executor =
        SilentOperationSink.Executor.<WaveletOperation, WaveletData>build(waveletData);
    return new OpBasedWavelet(waveletData.getWaveId(), waveletData, CONTEXT_FACTORY,
        ParticipationHelper.DEFAULT, executor, sink);
  }

  /** Sets up the mock provider to expose a single wavelet with the given snapshot. */
  @SuppressWarnings("unchecked")
  private void setupProvider(ObservableWaveletData snapshot) throws Exception {
    HashedVersion version = snapshot.getHashedVersion();
    CommittedWaveletSnapshot committed = new CommittedWaveletSnapshot(snapshot, version);

    ExceptionalIterator<WaveId, WaveServerException> waveIter =
        ExceptionalIterator.FromIterator.create(Collections.singletonList(WAVE_ID).iterator());
    when(mockProvider.getWaveIds()).thenReturn(waveIter);
    when(mockProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(WAVELET_ID));
    when(mockProvider.getSnapshot(WAVELET_NAME)).thenReturn(committed);
  }

  /**
   * Tests that a stale annotation (empty endTimeMs, startTimeMs well beyond the threshold) causes
   * the sweeper to submit a cleanup delta.
   */
  public void testSweepSubmitsDeltaForStaleAnnotation() throws Exception {
    CapturingOperationSink<WaveletOperation> output = new CapturingOperationSink<>();
    OpBasedWavelet wavelet = buildWavelet(output);

    WaveletBasedConversation.makeWaveletConversational(wavelet);
    ConversationBlip blip = conversationUtil.buildConversation(wavelet)
        .getRoot().getRootThread().appendBlip();

    // Stale session: startTimeMs is 1000ms (epoch+1s), well beyond the 30-min threshold.
    long staleStartMs = 1000L;
    blip.getContent().setAnnotation(0, 1, "user/d/session-stale",
        ALICE.getAddress() + "," + staleStartMs + ",");

    waveletData.setVersion(waveletData.getVersion() + 1);
    setupProvider(waveletData);

    sweeper.sweep();

    // Verify that submitRequest was called exactly once (to close the stale annotation).
    ArgumentCaptor<ProtocolWaveletDelta> deltaCaptor =
        ArgumentCaptor.forClass(ProtocolWaveletDelta.class);
    verify(mockProvider).submitRequest(
        eq(WAVELET_NAME),
        deltaCaptor.capture(),
        any(WaveletProvider.SubmitRequestListener.class));

    ProtocolWaveletDelta delta = deltaCaptor.getValue();
    assertEquals("Author should be the annotating user", ALICE.getAddress(), delta.getAuthor());
    assertEquals("Delta should have one operation", 1, delta.getOperationCount());
    String mutateDoc = delta.getOperation(0).getMutateDocument().getDocumentId();
    assertNotNull("Operation should mutate a document", mutateDoc);
  }

  /**
   * Tests that a non-stale annotation (empty endTimeMs, recent startTimeMs) does NOT trigger
   * a cleanup delta.
   */
  public void testSweepDoesNotSubmitDeltaForActiveAnnotation() throws Exception {
    CapturingOperationSink<WaveletOperation> output = new CapturingOperationSink<>();
    OpBasedWavelet wavelet = buildWavelet(output);

    WaveletBasedConversation.makeWaveletConversational(wavelet);
    ConversationBlip blip = conversationUtil.buildConversation(wavelet)
        .getRoot().getRootThread().appendBlip();

    // Active (non-stale) session: started 1 second ago.
    long recentStartMs = System.currentTimeMillis() - 1000L;
    blip.getContent().setAnnotation(0, 1, "user/d/session-active",
        ALICE.getAddress() + "," + recentStartMs + ",");

    waveletData.setVersion(waveletData.getVersion() + 1);
    setupProvider(waveletData);

    sweeper.sweep();

    // submitRequest must NOT be called — the session is not stale.
    verify(mockProvider, never()).submitRequest(
        any(WaveletName.class),
        any(ProtocolWaveletDelta.class),
        any(WaveletProvider.SubmitRequestListener.class));
  }

  /**
   * Tests that a properly closed annotation (non-empty endTimeMs) does NOT trigger a cleanup delta.
   */
  public void testSweepIgnoresProperlyClosedAnnotation() throws Exception {
    CapturingOperationSink<WaveletOperation> output = new CapturingOperationSink<>();
    OpBasedWavelet wavelet = buildWavelet(output);

    WaveletBasedConversation.makeWaveletConversational(wavelet);
    ConversationBlip blip = conversationUtil.buildConversation(wavelet)
        .getRoot().getRootThread().appendBlip();

    // Properly closed annotation (endTimeMs is set).
    blip.getContent().setAnnotation(0, 1, "user/d/session-closed",
        ALICE.getAddress() + ",1000,2000");

    waveletData.setVersion(waveletData.getVersion() + 1);
    setupProvider(waveletData);

    sweeper.sweep();

    verify(mockProvider, never()).submitRequest(
        any(WaveletName.class),
        any(ProtocolWaveletDelta.class),
        any(WaveletProvider.SubmitRequestListener.class));
  }

  /**
   * Tests that a companion user/e/ annotation is NOT nulled out when the corresponding
   * user/d/ session has an active IME composition (non-numeric, non-empty parts[2]).
   * The session is recent and still open — the sweep must not touch either annotation.
   */
  public void testSweepPreservesCompanionAnnotationDuringImeComposition() throws Exception {
    CapturingOperationSink<WaveletOperation> output = new CapturingOperationSink<>();
    OpBasedWavelet wavelet = buildWavelet(output);

    WaveletBasedConversation.makeWaveletConversational(wavelet);
    ConversationBlip blip = conversationUtil.buildConversation(wavelet)
        .getRoot().getRootThread().appendBlip();

    // Active session with IME composition: parts[2] = "composingText" (non-numeric, session open).
    long recentStartMs = System.currentTimeMillis() - 1000L;
    blip.getContent().setAnnotation(0, 1, "user/d/session-ime",
        ALICE.getAddress() + "," + recentStartMs + ",composingText");

    // Companion user/e/ annotation — must NOT be nulled out while session is active.
    blip.getContent().setAnnotation(0, 1, "user/e/session-ime", ALICE.getAddress());

    waveletData.setVersion(waveletData.getVersion() + 1);
    setupProvider(waveletData);

    sweeper.sweep();

    // No delta should be submitted: the user/d/ session is open (IME active) and recent.
    verify(mockProvider, never()).submitRequest(
        any(WaveletName.class),
        any(ProtocolWaveletDelta.class),
        any(WaveletProvider.SubmitRequestListener.class));
  }

  public void testSweepDoesNotWarnForRootLockDenial() throws Exception {
    assertLockDenialIsNotWarned("The root blip is locked. Editing is not allowed here.");
  }

  public void testSweepDoesNotWarnForAllLockDenial() throws Exception {
    assertLockDenialIsNotWarned("This wave is locked. Editing and replies are not allowed.");
  }

  public void testSweepDoesNotWarnForLockDenialWithAdditionalContext() throws Exception {
    assertLockDenialIsNotWarned(
        "Blocked by policy: This wave is locked. Editing and replies are not allowed. [lockId=7]");
  }

  private void assertLockDenialIsNotWarned(String errorMessage) throws Exception {
    CapturingOperationSink<WaveletOperation> output = new CapturingOperationSink<>();
    OpBasedWavelet wavelet = buildWavelet(output);

    WaveletBasedConversation.makeWaveletConversational(wavelet);
    ConversationBlip blip = conversationUtil.buildConversation(wavelet)
        .getRoot().getRootThread().appendBlip();

    long staleStartMs = 1000L;
    blip.getContent().setAnnotation(0, 1, "user/d/session-stale",
        ALICE.getAddress() + "," + staleStartMs + ",");

    waveletData.setVersion(waveletData.getVersion() + 1);
    setupProvider(waveletData);

    doAnswer(invocation -> {
      WaveletProvider.SubmitRequestListener listener =
          (WaveletProvider.SubmitRequestListener) invocation.getArguments()[2];
      listener.onFailure(errorMessage);
      return null;
    }).when(mockProvider).submitRequest(
        eq(WAVELET_NAME),
        any(ProtocolWaveletDelta.class),
        any(WaveletProvider.SubmitRequestListener.class));

    Logger logger = Logger.getLogger(StaleAnnotationSweeper.class.getName());
    List<LogRecord> records = new ArrayList<>();
    Handler captureHandler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {}

      @Override
      public void close() throws SecurityException {}
    };

    Level savedLevel = logger.getLevel();
    boolean savedUseParent = logger.getUseParentHandlers();
    logger.addHandler(captureHandler);
    logger.setLevel(Level.ALL);
    logger.setUseParentHandlers(false);
    try {
      sweeper.sweep();
    } finally {
      logger.removeHandler(captureHandler);
      logger.setLevel(savedLevel);
      logger.setUseParentHandlers(savedUseParent);
    }

    boolean warned = false;
    boolean loggedFine = false;
    for (LogRecord record : records) {
      if (record.getMessage() != null
          && record.getMessage().contains(WAVELET_NAME + "/" + blip.getId())
          && record.getMessage().contains(errorMessage)) {
        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
          warned = true;
        }
        if (record.getLevel().intValue() <= Level.INFO.intValue()) {
          loggedFine = true;
        }
      }
    }

    assertFalse("Legitimate lock denial should not be logged as warning", warned);
    assertTrue("Legitimate lock denial should still be logged at a low level", loggedFine);
  }
}

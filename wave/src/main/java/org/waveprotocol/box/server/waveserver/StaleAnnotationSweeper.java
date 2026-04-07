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

import com.google.common.collect.ImmutableSet;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletOperation;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically sweeps all wavelets for stale {@code user/d/} editor annotations and submits
 * deltas to close them.
 *
 * <p>A stale annotation is one with an empty {@code endTimeMs} field (indicating an active edit
 * session) whose {@code startTimeMs} is older than {@link #STALE_EDITING_THRESHOLD_MS}. This
 * occurs when a browser crashes or a tab is force-closed mid-edit: the client never sends the
 * annotation-close delta, so the annotation lingers permanently. Bots that use these annotations
 * to detect active editing will be permanently blocked from responding to affected blips.
 *
 * <p>The sweeper runs every {@link #SWEEP_INTERVAL_MINUTES} minutes as a daemon background task.
 * Cleanup deltas are submitted directly via {@link WaveletProvider}, bypassing the RPC layer.
 */
public class StaleAnnotationSweeper {

  private static final Log LOG = Log.get(StaleAnnotationSweeper.class);

  /**
   * Editing sessions open for longer than this are considered stale (client crashed/disconnected
   * without closing the session).
   */
  static final long STALE_EDITING_THRESHOLD_MS = 30 * 60 * 1000L; // 30 minutes

  /** How often to run the sweep. */
  private static final long SWEEP_INTERVAL_MINUTES = 10L;

  private final WaveletProvider waveletProvider;

  /** Simple holder for a discovered stale annotation and its document range. */
  private static class StaleAnnotation {
    final String key;       // e.g. "user/d/sessionId"
    final String userId;    // first component of the annotation value
    final String oldValue;  // full annotation value, e.g. "userId,startMs,"
    final int start;        // inclusive start position in the document
    final int end;          // exclusive end position in the document
    final int docSize;      // total document item count

    StaleAnnotation(String key, String userId, String oldValue, int start, int end, int docSize) {
      this.key = key;
      this.userId = userId;
      this.oldValue = oldValue;
      this.start = start;
      this.end = end;
      this.docSize = docSize;
    }
  }

  public StaleAnnotationSweeper(WaveletProvider waveletProvider) {
    this.waveletProvider = waveletProvider;
  }

  /**
   * Starts the background sweep task. Subsequent sweeps run every
   * {@link #SWEEP_INTERVAL_MINUTES} minutes. The task runs on a daemon thread so it does not
   * prevent JVM shutdown.
   */
  public void start() {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "StaleAnnotationSweeper");
      t.setDaemon(true);
      return t;
    });
    executor.scheduleWithFixedDelay(
        this::sweep,
        SWEEP_INTERVAL_MINUTES,
        SWEEP_INTERVAL_MINUTES,
        TimeUnit.MINUTES);
    LOG.info("StaleAnnotationSweeper started (interval=" + SWEEP_INTERVAL_MINUTES
        + "m, staleThreshold=" + STALE_EDITING_THRESHOLD_MS + "ms)");
  }

  /** Runs one full sweep across all waves known to the server. */
  void sweep() {
    long now = System.currentTimeMillis();
    int wavesScanned = 0;
    int annotationsClosed = 0;
    try {
      ExceptionalIterator<WaveId, WaveServerException> waveIds = waveletProvider.getWaveIds();
      while (waveIds.hasNext()) {
        WaveId waveId = waveIds.next();
        wavesScanned++;
        try {
          annotationsClosed += sweepWave(waveId, now);
        } catch (Exception e) {
          LOG.warning("StaleAnnotationSweeper: error sweeping wave " + waveId, e);
        }
      }
      if (LOG.isFineLoggable()) {
        LOG.fine("StaleAnnotationSweeper: swept " + wavesScanned
            + " waves, closed " + annotationsClosed + " stale annotations");
      }
    } catch (WaveServerException e) {
      LOG.warning("StaleAnnotationSweeper: error iterating waves", e);
    }
  }

  private int sweepWave(WaveId waveId, long now) throws WaveServerException {
    ImmutableSet<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
    int closed = 0;
    for (WaveletId waveletId : waveletIds) {
      WaveletName waveletName = WaveletName.of(waveId, waveletId);
      try {
        closed += sweepWavelet(waveletName, now);
      } catch (Exception e) {
        LOG.warning("StaleAnnotationSweeper: error sweeping wavelet " + waveletName, e);
      }
    }
    return closed;
  }

  private int sweepWavelet(WaveletName waveletName, long now) throws WaveServerException {
    CommittedWaveletSnapshot committedSnapshot = waveletProvider.getSnapshot(waveletName);
    if (committedSnapshot == null) {
      return 0;
    }
    ReadableWaveletData snapshot = committedSnapshot.snapshot;
    // Create a mutable copy so we can use the ObservableDocument annotation API for reading.
    ObservableWaveletData wavelet = WaveletDataUtil.copyWavelet(snapshot);
    int closed = 0;
    for (String docId : wavelet.getDocumentIds()) {
      try {
        closed += sweepDocument(waveletName, docId, wavelet, snapshot, now);
      } catch (Exception e) {
        LOG.warning("StaleAnnotationSweeper: error sweeping " + waveletName + "/" + docId, e);
      }
    }
    return closed;
  }

  private int sweepDocument(WaveletName waveletName, String docId,
      ObservableWaveletData wavelet, ReadableWaveletData snapshot, long now) {
    BlipData blip = wavelet.getDocument(docId);
    if (blip == null) {
      return 0;
    }
    DocumentOperationSink content = blip.getContent();
    if (!(content instanceof ObservableDocument)) {
      return 0;
    }
    ObservableDocument doc = (ObservableDocument) content;
    int docSize = doc.size();
    if (docSize <= 0) {
      return 0;
    }

    // Collect stale annotations before submitting deltas (avoid iterator/state issues).
    // Pre-capture the participant set for security validation inside the lambda.
    Set<ParticipantId> participants = snapshot.getParticipants();
    List<StaleAnnotation> staleAnnotations = new ArrayList<>();
    doc.knownKeys().each(key -> {
      if (!key.startsWith("user/d/")) return;
      // Iterate all disjoint non-null intervals for this key. A single session key can
      // appear in multiple ranges if the same session annotated non-contiguous spans.
      int searchFrom = 0;
      while (searchFrom < docSize) {
        int pos = doc.firstAnnotationChange(searchFrom, docSize, key, null);
        if (pos < 0) break; // no more non-null intervals
        String value = doc.getAnnotation(pos, key);
        if (value == null) {
          // Annotation is still null; advance to avoid an infinite loop.
          searchFrom = pos + 1;
          continue;
        }
        // Find end of this interval.
        int annotEnd = doc.firstAnnotationChange(pos, docSize, key, value);
        if (annotEnd < 0) annotEnd = docSize;

        String[] parts = value.split(",", 3);
        // endTimeMs must be empty for the session to be "open"
        if (parts.length >= 3 && parts[2].isEmpty()
            && parts.length >= 2 && !parts[1].isEmpty()) {
          try {
            long startTimeMs = Long.parseLong(parts[1]);
            if (now - startTimeMs > STALE_EDITING_THRESHOLD_MS) {
              String userId = parts[0];
              // Validate userId is a known wavelet participant. Annotation values come from
              // document content and could be forged by a malicious participant. Submitting
              // a cleanup delta as an arbitrary userId would let the server impersonate any
              // user — only proceed if the extracted userId is actually in this wavelet.
              ParticipantId authorId;
              try {
                authorId = ParticipantId.of(userId);
              } catch (InvalidParticipantAddress e) {
                LOG.warning("StaleAnnotationSweeper: skipping stale annotation " + key
                    + " — malformed userId '" + userId + "'");
                searchFrom = annotEnd;
                continue;
              }
              if (!participants.contains(authorId)) {
                LOG.warning("StaleAnnotationSweeper: skipping stale annotation " + key
                    + " — userId '" + userId + "' is not a wavelet participant"
                    + " (possible forged annotation)");
                searchFrom = annotEnd;
                continue;
              }
              staleAnnotations.add(new StaleAnnotation(key, userId, value, pos, annotEnd, docSize));
            }
          } catch (NumberFormatException e) {
            // Malformed startTimeMs; skip this interval.
          }
        }
        searchFrom = annotEnd;
      }
    });

    // Submit cleanup deltas for all stale annotations found. All deltas reference the same
    // snapshot hash; if more than one succeeds, the second will fail with a version conflict
    // (benign — the next sweep retries with the updated version). Submitting all ensures that
    // a permanently un-submittable annotation (e.g. author no longer a participant) does not
    // block later stale annotations in the same document.
    int closed = 0;
    for (StaleAnnotation stale : staleAnnotations) {
      boolean submitted = submitCleanupDelta(waveletName, docId, stale, snapshot, now);
      if (submitted) {
        closed++;
      }
    }
    return closed;
  }

  /**
   * Builds and submits a delta that sets the {@code endTimeMs} of the stale annotation to
   * {@code now}, marking the editing session as closed.
   *
   * @return true if the delta was submitted (may still fail asynchronously)
   */
  private boolean submitCleanupDelta(WaveletName waveletName, String docId,
      StaleAnnotation stale, ReadableWaveletData snapshot, long now) {
    try {
      String closedValue = stale.oldValue + now; // "userId,startMs," + nowMs = "userId,startMs,nowMs"
      ProtocolDocumentOperation.Component.AnnotationBoundary.Builder openBoundary =
          ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder()
              .addChange(ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder()
                  .setKey(stale.key)
                  .setOldValue(stale.oldValue)
                  .setNewValue(closedValue));

      ProtocolDocumentOperation.Component.AnnotationBoundary.Builder closeBoundary =
          ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder()
              .addEnd(stale.key);

      ProtocolDocumentOperation.Builder docOp = ProtocolDocumentOperation.newBuilder();
      if (stale.start > 0) {
        docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
            .setRetainItemCount(stale.start));
      }
      docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
          .setAnnotationBoundary(openBoundary));
      if (stale.end > stale.start) {
        docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
            .setRetainItemCount(stale.end - stale.start));
      }
      docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
          .setAnnotationBoundary(closeBoundary));
      if (stale.end < stale.docSize) {
        docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
            .setRetainItemCount(stale.docSize - stale.end));
      }

      ProtocolWaveletDelta delta = ProtocolWaveletDelta.newBuilder()
          .setAuthor(stale.userId)
          .setHashedVersion(CoreWaveletOperationSerializer.serialize(snapshot.getHashedVersion()))
          .addOperation(ProtocolWaveletOperation.newBuilder()
              .setMutateDocument(ProtocolWaveletOperation.MutateDocument.newBuilder()
                  .setDocumentId(docId)
                  .setDocumentOperation(docOp)))
          .build();

      waveletProvider.submitRequest(waveletName, delta, new WaveletProvider.SubmitRequestListener() {
        @Override
        public void onSuccess(int operationsApplied, org.waveprotocol.wave.model.version.HashedVersion version,
            long applicationTimestamp) {
          LOG.info("StaleAnnotationSweeper: closed stale annotation " + stale.key
              + " in " + waveletName + "/" + docId + " (author=" + stale.userId + ")");
        }

        @Override
        public void onFailure(String errorMessage) {
          // Common causes: version conflict (another delta was applied concurrently), or the
          // author is no longer a participant. Both are benign — the next sweep will retry.
          LOG.warning("StaleAnnotationSweeper: failed to close stale annotation " + stale.key
              + " in " + waveletName + "/" + docId + ": " + errorMessage);
        }
      });
      return true;
    } catch (Exception e) {
      LOG.warning("StaleAnnotationSweeper: error building cleanup delta for "
          + waveletName + "/" + docId + " key=" + stale.key, e);
      return false;
    }
  }
}

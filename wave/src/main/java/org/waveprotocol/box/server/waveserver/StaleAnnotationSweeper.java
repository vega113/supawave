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
  private static final String ROOT_BLIP_LOCKED_ERROR =
      "The root blip is locked. Editing is not allowed here.";
  private static final String WAVE_LOCKED_ERROR =
      "This wave is locked. Editing and replies are not allowed.";

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

  /** A single annotation mutation event to be applied at a given document position. */
  private static final class AnnotationOp {
    enum Kind { CHANGE, END }
    final int position;
    final Kind kind;
    final String key;
    final String oldValue; // only for CHANGE
    final String newValue; // only for CHANGE; null means remove the annotation

    AnnotationOp(int position, Kind kind, String key, String oldValue, String newValue) {
      this.position = position;
      this.kind = kind;
      this.key = key;
      this.oldValue = oldValue;
      this.newValue = newValue;
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
        () -> {
          try {
            sweep();
          } catch (Exception e) {
            LOG.severe("StaleAnnotationSweeper: unexpected exception in sweep", e);
          }
        },
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
        if (parts.length >= 3 && parts[2].isEmpty() && !parts[1].isEmpty()) {
          try {
            double parsed = Double.parseDouble(parts[1]);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("Non-finite timestamp");
            long startTimeMs = (long) parsed;
            if (now - startTimeMs > STALE_EDITING_THRESHOLD_MS) {
              String userId = parts[0];
              // Note: userId is from annotation content but is only used for logging.
              // The cleanup delta author is set from server-validated participants in
              // submitCleanupDelta, so ex-participants' stale annotations are swept correctly.
              staleAnnotations.add(new StaleAnnotation(key, userId, value, pos, annotEnd, docSize));
            }
          } catch (NumberFormatException e) {
            // Malformed startTimeMs; skip this interval.
          }
        }
        searchFrom = annotEnd;
      }
    });

    // Collect companion user/e/sessionId annotations that should be nulled out.
    // user/e/ stores the cursor end-position marker (value = user address string, no timestamp),
    // so it cannot be detected as stale independently. Null out any user/e/sessionId where the
    // corresponding user/d/sessionId has no currently open, non-stale interval — this covers:
    // (a) sessions that are stale in this sweep, and (b) orphaned user/e/ annotations left
    // behind when a previous user/d/ cleanup succeeded but the companion null-out failed on a
    // version conflict (the closed user/d/ session would not appear as stale on the next sweep).
    List<StaleAnnotation> companionAnnotations = new ArrayList<>();
    doc.knownKeys().each(key -> {
      if (!key.startsWith("user/e/")) return;
      String sessionId = key.substring("user/e/".length());
      String dKey = "user/d/" + sessionId;

      // Check whether user/d/sessionId has any open, non-stale interval.
      boolean hasActiveSession = false;
      int dSearchFrom = 0;
      while (dSearchFrom < docSize) {
        int dPos = doc.firstAnnotationChange(dSearchFrom, docSize, dKey, null);
        if (dPos < 0) break;
        String dValue = doc.getAnnotation(dPos, dKey);
        if (dValue == null) { dSearchFrom = dPos + 1; continue; }
        String[] dParts = dValue.split(",", 3);
        // parts[2] is the IME composition state when written by the client, which can be:
        //   ""          → no active IME composition, session is open
        //   non-numeric → IME text is being composed, session is still open
        //   numeric     → close timestamp written by the sweeper, session is closed
        // Treat the session as closed only when parts[2] is a finite numeric value.
        boolean dSessionClosed = false;
        if (dParts.length >= 3 && !dParts[2].isEmpty()) {
          try {
            double closeTs = Double.parseDouble(dParts[2]);
            dSessionClosed = Double.isFinite(closeTs);
          } catch (NumberFormatException ignored) {
            // non-numeric → IME composition text → session still open
          }
        }
        if (!dSessionClosed && dParts.length >= 2 && !dParts[1].isEmpty()) {
          try {
            double parsed = Double.parseDouble(dParts[1]);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("Non-finite timestamp");
            long startTimeMs = (long) parsed;
            if (now - startTimeMs <= STALE_EDITING_THRESHOLD_MS) {
              hasActiveSession = true;
              break;
            }
          } catch (NumberFormatException ignored) {}
        }
        int dEnd = doc.firstAnnotationChange(dPos, docSize, dKey, dValue);
        dSearchFrom = (dEnd < 0) ? docSize : dEnd;
      }
      if (hasActiveSession) return;

      // Collect all non-null intervals for this user/e/ key.
      int searchFrom = 0;
      while (searchFrom < docSize) {
        int pos = doc.firstAnnotationChange(searchFrom, docSize, key, null);
        if (pos < 0) break;
        String value = doc.getAnnotation(pos, key);
        if (value == null) { searchFrom = pos + 1; continue; }
        int annotEnd = doc.firstAnnotationChange(pos, docSize, key, value);
        if (annotEnd < 0) annotEnd = docSize;
        companionAnnotations.add(new StaleAnnotation(key, null, value, pos, annotEnd, docSize));
        searchFrom = annotEnd;
      }
    });

    // Batch all mutations (user/d close + user/e null-out) into one delta per document so that
    // all annotation changes share a single hashed version and succeed together.
    if (staleAnnotations.isEmpty() && companionAnnotations.isEmpty()) {
      return 0;
    }
    boolean submitted = submitBatchedDelta(waveletName, docId, staleAnnotations,
        companionAnnotations, snapshot, now);
    return submitted ? staleAnnotations.size() : 0;
  }

  /**
   * Builds and submits a single delta that closes all stale {@code user/d/*} annotations (sets
   * {@code endTimeMs = now}) and nulls out all orphaned {@code user/e/*} companion annotations in
   * one document operation. Batching into one delta avoids version-conflict cascades that occur
   * when multiple per-interval deltas all share the same {@code snapshot.getHashedVersion()}.
   *
   * <p>Events (annotation-boundary changes) are sorted by document position so the resulting
   * DocOp covers the full document in a single linear pass.
   *
   * @return true if the delta was submitted (may still fail asynchronously)
   */
  private boolean submitBatchedDelta(WaveletName waveletName, String docId,
      List<StaleAnnotation> staleAnnotations, List<StaleAnnotation> companionAnnotations,
      ReadableWaveletData snapshot, long now) {
    try {
      int docSize = staleAnnotations.isEmpty()
          ? companionAnnotations.get(0).docSize
          : staleAnnotations.get(0).docSize;

      // Build sorted list of annotation events: CHANGE (start of a mutation) and END (end of it).
      List<AnnotationOp> ops = new ArrayList<>();
      for (StaleAnnotation stale : staleAnnotations) {
        String closedValue = stale.oldValue + now; // "userId,startMs," + nowMs → "userId,startMs,nowMs"
        ops.add(new AnnotationOp(stale.start, AnnotationOp.Kind.CHANGE,
            stale.key, stale.oldValue, closedValue));
        ops.add(new AnnotationOp(stale.end, AnnotationOp.Kind.END, stale.key, null, null));
      }
      for (StaleAnnotation companion : companionAnnotations) {
        // No newValue → sets annotation to null (removes it).
        ops.add(new AnnotationOp(companion.start, AnnotationOp.Kind.CHANGE,
            companion.key, companion.oldValue, null));
        ops.add(new AnnotationOp(companion.end, AnnotationOp.Kind.END, companion.key, null, null));
      }
      // Sort by position; at equal positions emit END before CHANGE; within same position+kind
      // sort by key so DocOpAutomaton.checkAnnotationBoundary sees strictly monotonic key order.
      ops.sort((a, b) -> {
        int cmp = Integer.compare(a.position, b.position);
        if (cmp != 0) return cmp;
        int kindCmp = (a.kind == AnnotationOp.Kind.END ? 0 : 1)
            - (b.kind == AnnotationOp.Kind.END ? 0 : 1);
        if (kindCmp != 0) return kindCmp;
        return a.key.compareTo(b.key);
      });

      // Deduplicate: if adjacent intervals share a boundary, the same key can have both an END
      // and a CHANGE at the same position. DocOpAutomaton rejects duplicate keys in a single
      // AnnotationBoundary, so drop the END — the CHANGE already transitions the annotation.
      List<AnnotationOp> deduped = new ArrayList<>(ops.size());
      {
        int j = 0;
        while (j < ops.size()) {
          int pos = ops.get(j).position;
          Set<String> changeKeysAtPos = new java.util.HashSet<>();
          for (int k = j; k < ops.size() && ops.get(k).position == pos; k++) {
            if (ops.get(k).kind == AnnotationOp.Kind.CHANGE) {
              changeKeysAtPos.add(ops.get(k).key);
            }
          }
          while (j < ops.size() && ops.get(j).position == pos) {
            AnnotationOp op = ops.get(j++);
            if (op.kind != AnnotationOp.Kind.END || !changeKeysAtPos.contains(op.key)) {
              deduped.add(op);
            }
          }
        }
        ops = deduped;
      }

      // Walk sorted events and build one DocOp covering the entire document.
      ProtocolDocumentOperation.Builder docOp = ProtocolDocumentOperation.newBuilder();
      int currentPos = 0;
      int i = 0;
      while (i < ops.size()) {
        int pos = ops.get(i).position;
        if (pos > currentPos) {
          docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
              .setRetainItemCount(pos - currentPos));
          currentPos = pos;
        }
        // Collect all events at this position into one annotationBoundary.
        ProtocolDocumentOperation.Component.AnnotationBoundary.Builder boundary =
            ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder();
        while (i < ops.size() && ops.get(i).position == pos) {
          AnnotationOp op = ops.get(i++);
          if (op.kind == AnnotationOp.Kind.END) {
            boundary.addEnd(op.key);
          } else {
            ProtocolDocumentOperation.Component.KeyValueUpdate.Builder kv =
                ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder()
                    .setKey(op.key)
                    .setOldValue(op.oldValue);
            if (op.newValue != null) {
              kv.setNewValue(op.newValue);
            }
            boundary.addChange(kv);
          }
        }
        docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
            .setAnnotationBoundary(boundary));
      }
      if (currentPos < docSize) {
        docOp.addComponent(ProtocolDocumentOperation.Component.newBuilder()
            .setRetainItemCount(docSize - currentPos));
      }

      // Use the wavelet creator as author if still a participant; otherwise first participant.
      // Never use annotation-embedded userId as author (could be forged by a participant).
      ParticipantId creatorId = snapshot.getCreator();
      Set<ParticipantId> participants = snapshot.getParticipants();
      if (participants.isEmpty()) {
        LOG.warning("StaleAnnotationSweeper: no participants on " + waveletName
            + "; skipping batch cleanup for " + docId);
        return false;
      }
      String deltaAuthor = participants.contains(creatorId)
          ? creatorId.getAddress()
          : participants.iterator().next().getAddress();

      ProtocolWaveletDelta delta = ProtocolWaveletDelta.newBuilder()
          .setAuthor(deltaAuthor)
          .setHashedVersion(CoreWaveletOperationSerializer.serialize(snapshot.getHashedVersion()))
          .addOperation(ProtocolWaveletOperation.newBuilder()
              .setMutateDocument(ProtocolWaveletOperation.MutateDocument.newBuilder()
                  .setDocumentId(docId)
                  .setDocumentOperation(docOp)))
          .build();

      int staleCount = staleAnnotations.size();
      int companionCount = companionAnnotations.size();
      waveletProvider.submitRequest(waveletName, delta, new WaveletProvider.SubmitRequestListener() {
        @Override
        public void onSuccess(int operationsApplied,
            org.waveprotocol.wave.model.version.HashedVersion version,
            long applicationTimestamp) {
          LOG.info("StaleAnnotationSweeper: swept " + staleCount + " stale annotation(s)"
              + (companionCount > 0 ? " + " + companionCount + " companion(s)" : "")
              + " in " + waveletName + "/" + docId);
        }

        @Override
        public void onFailure(String errorMessage) {
          // Common causes: version conflict (concurrent delta), author no longer a participant,
          // or cleanup on a legitimately locked wave/root blip. All are benign — the next sweep
          // will retry with a fresh snapshot version, except lock denials which are expected.
          if (isLegitimateLockDenial(errorMessage)) {
            LOG.fine("StaleAnnotationSweeper: skipping cleanup for locked document "
                + waveletName + "/" + docId + ": " + errorMessage);
            return;
          }
          LOG.warning("StaleAnnotationSweeper: batch cleanup failed for "
              + waveletName + "/" + docId + ": " + errorMessage);
        }
      });
      return true;
    } catch (Exception e) {
      LOG.warning("StaleAnnotationSweeper: error building batch cleanup delta for "
          + waveletName + "/" + docId, e);
      return false;
    }
  }

  private static boolean isLegitimateLockDenial(String errorMessage) {
    return ROOT_BLIP_LOCKED_ERROR.equals(errorMessage) || WAVE_LOCKED_ERROR.equals(errorMessage);
  }
}

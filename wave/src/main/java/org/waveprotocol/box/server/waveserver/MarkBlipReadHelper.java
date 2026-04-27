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
import com.google.inject.Inject;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;

import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestListener;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Server seam for the J2CL {@code markBlipRead} write path. Mutates the
 * authenticated user's user-data wavelet via the same supplement op pipeline
 * the GWT client and the robot {@code FolderActionService} use today; the J2CL
 * surface gets behavioural parity with no new write code path.
 *
 * <p>This helper is structurally adjacent to
 * {@link SelectedWaveReadStateHelper} (which serves the read counterpart), but
 * takes a different dependency set ({@link WaveletProvider},
 * {@link EventDataConverterManager}, {@link ConversationUtil}) so it can drive
 * an {@link OperationContextImpl} for the write. The two helpers share the
 * "collapse missing-wave + access-denied to {@code NOT_FOUND}" semantic so
 * non-participants cannot probe wave existence.
 *
 * <p>Outcome contract:
 * <ul>
 *   <li>{@link Outcome#OK} — supplement op submitted; {@code unreadCountAfter}
 *       reflects the post-op count.</li>
 *   <li>{@link Outcome#ALREADY_READ} — blip was already read for this user;
 *       no UDW delta was submitted (the op is idempotent at the
 *       {@link SupplementedWave#markAsRead(ConversationBlip)} layer); the
 *       count is reported back to the caller for live UI consistency.</li>
 *   <li>{@link Outcome#NOT_FOUND} — wave/wavelet/blip absent OR access denied;
 *       collapsed into a single response so existence cannot be probed.</li>
 *   <li>{@link Outcome#BAD_REQUEST} — null arguments. Servlet maps to 400.</li>
 *   <li>{@link Outcome#INTERNAL_ERROR} — backend wavelet load failure.</li>
 * </ul>
 *
 * <p>Introduced for issue #1039 (F-4) / closes #1056.
 */
public class MarkBlipReadHelper {

  private static final Log LOG = Log.get(MarkBlipReadHelper.class);

  /** Outcome of a {@link #markBlipRead} call. */
  public enum Outcome {
    /** Supplement op submitted. */
    OK,
    /** Blip was already read; no delta submitted. */
    ALREADY_READ,
    /** Wave/wavelet/blip not found OR access denied (collapsed). */
    NOT_FOUND,
    /** Required arguments missing or malformed. */
    BAD_REQUEST,
    /** Wavelet load failure. */
    INTERNAL_ERROR;
  }

  /** Result of a {@link #markBlipRead} call. */
  public static final class Result {
    private final Outcome outcome;
    private final int unreadCountAfter;

    private Result(Outcome outcome, int unreadCountAfter) {
      this.outcome = outcome;
      this.unreadCountAfter = unreadCountAfter;
    }

    public Outcome getOutcome() {
      return outcome;
    }

    /** -1 when unknown (not-found / bad-request / internal-error). */
    public int getUnreadCountAfter() {
      return unreadCountAfter;
    }

    public static Result ok(int unreadCountAfter) {
      return new Result(Outcome.OK, Math.max(0, unreadCountAfter));
    }

    public static Result alreadyRead(int unreadCountAfter) {
      return new Result(Outcome.ALREADY_READ, Math.max(0, unreadCountAfter));
    }

    public static Result notFound() {
      return new Result(Outcome.NOT_FOUND, -1);
    }

    public static Result badRequest() {
      return new Result(Outcome.BAD_REQUEST, -1);
    }

    public static Result internalError() {
      return new Result(Outcome.INTERNAL_ERROR, -1);
    }
  }

  private final WaveletProvider waveletProvider;
  private final EventDataConverterManager converterManager;
  private final ConversationUtil conversationUtil;
  private final SelectedWaveReadStateHelper readStateHelper;

  @Inject
  public MarkBlipReadHelper(
      WaveletProvider waveletProvider,
      EventDataConverterManager converterManager,
      ConversationUtil conversationUtil,
      SelectedWaveReadStateHelper readStateHelper) {
    this.waveletProvider = waveletProvider;
    this.converterManager = converterManager;
    this.conversationUtil = conversationUtil;
    this.readStateHelper = readStateHelper;
  }

  /**
   * Marks the given blip as read for the given user. Returns a {@link Result}
   * carrying the outcome plus the post-op unread count for the wave (read back
   * via {@link SelectedWaveReadStateHelper#computeReadState} so the read and
   * write paths agree on the value the client sees).
   */
  public Result markBlipRead(
      ParticipantId user, WaveId waveId, WaveletId waveletId, String blipId) {
    if (user == null || waveId == null || waveletId == null || blipId == null
        || blipId.isEmpty()) {
      return Result.badRequest();
    }

    if (!IdUtil.isConversationalId(waveletId)) {
      // The mark-read pipeline only mutates the UDW; the blip lives on a
      // conversational wavelet. Refuse to even try other shapes.
      return Result.badRequest();
    }

    // Existence + access guard mirroring SelectedWaveReadStateHelper:
    // collapse missing wave / wavelet load failure / access-denied into 404
    // so non-participants cannot probe existence.
    SelectedWaveReadStateHelper.Result accessProbe;
    try {
      accessProbe = readStateHelper.computeReadState(user, waveId);
    } catch (RuntimeException e) {
      LOG.warning("mark-blip-read: access probe failed for " + waveId, e);
      return Result.internalError();
    }
    if (!accessProbe.exists() || !accessProbe.accessAllowed()) {
      return Result.notFound();
    }

    // Drive the supplement op via OperationContextImpl, the same pipeline
    // FolderActionService uses for the robot 'markAsRead' op. Implementation
    // note: OperationContextImpl implements OperationResults
    // (see OperationContextImpl.java:74), so it plugs straight into
    // OperationUtil.submitDeltas without a cast.
    OperationContextImpl context =
        new OperationContextImpl(
            waveletProvider,
            converterManager.getEventDataConverter(ProtocolVersion.DEFAULT),
            conversationUtil);

    OpBasedWavelet conv;
    try {
      conv = openWaveletViaContext(context, waveId, waveletId, user);
    } catch (RuntimeException e) {
      // openWaveletViaContext already collapses the not-found / access-denied
      // path into a null return (via its catch of InvalidRequestException).
      // Anything that escapes here is a genuine backend fault — mapping it
      // to NOT_FOUND would hide a server-side error behind a false 404, so
      // we surface it as INTERNAL_ERROR to match the helper's documented
      // outcome contract (and to align with the udwId open path below).
      LOG.warning("mark-blip-read: failed to open conversational wavelet "
          + WaveletName.of(waveId, waveletId), e);
      return Result.internalError();
    }
    if (conv == null) {
      // openWaveletViaContext returns null specifically when openWavelet
      // throws InvalidRequestException — i.e. the wavelet is missing or the
      // user lacks access. Both collapse to NOT_FOUND per the helper
      // contract so non-participants cannot probe wavelet existence (the
      // access probe at the top of this method already approved the wave;
      // this guards against a racy disappearance or a stale cache).
      return Result.notFound();
    }

    ObservableConversationView view = conversationUtil.buildConversation(conv);
    Conversation root = view.getRoot();
    if (root == null) {
      return Result.notFound();
    }

    ConversationBlip blip = root.getBlip(blipId);
    if (blip == null) {
      // Unknown blip id: 404 (do NOT 400, the id may be valid syntactically
      // but absent — same shape as wave-not-found).
      return Result.notFound();
    }

    // Open the user-data wavelet on the same context so the supplement
    // mutations land in a single delta. UDW is auto-created by openWavelet
    // when missing (see OperationContextImpl#openWavelet).
    WaveletId udwId = IdUtil.buildUserDataWaveletId(user);
    OpBasedWavelet udw;
    try {
      udw = openWaveletViaContext(context, waveId, udwId, user);
    } catch (RuntimeException e) {
      LOG.warning("mark-blip-read: failed to open user-data wavelet "
          + WaveletName.of(waveId, udwId), e);
      return Result.internalError();
    }
    if (udw == null) {
      LOG.warning("mark-blip-read: user-data wavelet unavailable "
          + WaveletName.of(waveId, udwId));
      return Result.internalError();
    }

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    SupplementedWave supplement =
        SupplementedWaveImpl.create(udwState, view, user, DefaultFollow.ALWAYS);

    boolean wasUnread = supplement.isUnread(blip);
    if (!wasUnread) {
      // Supplement is already read; markAsRead is a no-op at the
      // SupplementedWaveImpl layer (see SupplementedWaveImpl.java:306). We
      // skip the submit entirely and report the current count for live UI.
      return Result.alreadyRead(currentUnreadCount(user, waveId));
    }

    supplement.markAsRead(blip);

    // Defence-in-depth: only the UDW delta should be present. The conv
    // wavelet was opened only to materialize the ConversationView; if a
    // delta accumulated there we have a bug and would silently mutate the
    // wave. Refuse to submit and signal an error instead.
    WaveletName convWaveletName = WaveletName.of(waveId, waveletId);
    RobotWaveletData convData = context.getOpenWavelets().get(convWaveletName);
    if (convData != null && !convData.getDeltas().isEmpty()) {
      LOG.warning("mark-blip-read: unexpected conv-wavelet deltas, refusing submit "
          + convWaveletName);
      return Result.internalError();
    }

    // We deliberately do NOT use the fire-and-forget LoggingRequestListener
    // here. The servlet immediately reports the resulting outcome to the
    // J2CL client (which decrements badges live based on it), so an OK
    // response after a rejected submit would make the UI show a phantom
    // decrement. The current WaveletProvider implementations
    // (WaveServerImpl in particular) invoke SubmitRequestListener
    // synchronously from within submitRequest(...), so capturing the
    // outcome on the calling thread is sufficient — no waiting required.
    CapturingRequestListener captured = new CapturingRequestListener();
    OperationUtil.submitDeltas(context, waveletProvider, captured);

    if (!captured.completed) {
      // Defence-in-depth: every existing WaveletProvider in this codebase
      // resolves submitRequest synchronously, but if a future provider
      // implementation buffers the call we must not silently report OK
      // for an unconfirmed write.
      LOG.warning("mark-blip-read: submitDeltas returned without invoking the listener for "
          + WaveletName.of(waveId, IdUtil.buildUserDataWaveletId(user)));
      return Result.internalError();
    }
    if (captured.failureMessage != null) {
      LOG.warning("mark-blip-read: UDW delta rejected for "
          + WaveletName.of(waveId, IdUtil.buildUserDataWaveletId(user))
          + " — " + captured.failureMessage);
      return Result.internalError();
    }

    return Result.ok(currentUnreadCount(user, waveId));
  }

  /**
   * Synchronous {@link SubmitRequestListener} that captures the submit
   * outcome on the calling thread so {@link #markBlipRead} can reject
   * {@link Result#ok} when the provider returned a failure rather than
   * throwing. The current {@link WaveletProvider} implementations in this
   * server (e.g. {@code WaveServerImpl}) invoke the listener synchronously
   * from {@code submitRequest(...)}; the {@link #completed} flag guards
   * against a future provider that buffers the call.
   */
  private static final class CapturingRequestListener implements SubmitRequestListener {
    boolean completed;
    String failureMessage;

    @Override
    public void onSuccess(int operationsApplied, HashedVersion hashedVersionAfterApplication,
        long applicationTimestamp) {
      completed = true;
      if (LOG.isFineLoggable()) {
        LOG.fine("mark-blip-read: " + operationsApplied + " op(s) applied @"
            + hashedVersionAfterApplication);
      }
    }

    @Override
    public void onFailure(String errorMessage) {
      completed = true;
      failureMessage = errorMessage == null ? "unknown" : errorMessage;
    }
  }

  /**
   * Reads back the current unread count via the existing read helper so the
   * write and read paths agree on the value the client sees.
   */
  private int currentUnreadCount(ParticipantId user, WaveId waveId) {
    try {
      SelectedWaveReadStateHelper.Result post = readStateHelper.computeReadState(user, waveId);
      if (!post.exists() || !post.accessAllowed()) {
        return 0;
      }
      return post.getUnreadCount();
    } catch (RuntimeException e) {
      LOG.warning("mark-blip-read: failed to recompute unread count for " + waveId, e);
      return 0;
    }
  }

  @VisibleForTesting
  OpBasedWavelet openWaveletViaContext(
      OperationContextImpl context, WaveId waveId, WaveletId waveletId, ParticipantId user) {
    try {
      return context.openWavelet(waveId, waveletId, user);
    } catch (InvalidRequestException e) {
      // openWavelet wraps load failures and missing wavelets in
      // InvalidRequestException; collapse to null so the caller can map to 404.
      return null;
    }
  }
}

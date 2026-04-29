/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.box.server.rpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.util.RobotsUtil;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Jakarta variant of FolderServlet for moving waves between folders.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@Singleton
public final class FolderServlet extends HttpServlet {

  private static final Log LOG = Log.get(FolderServlet.class);
  private static final WaveletProvider.SubmitRequestListener LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);
  private static final Pattern VERSION_SUFFIX = Pattern.compile(":\\d+$");

  private final SessionManager sessionManager;
  private final WaveletProvider waveletProvider;
  private final ConversationUtil conversationUtil;
  private final EventDataConverterManager converterManager;

  @Inject
  public FolderServlet(SessionManager sessionManager, WaveletProvider waveletProvider,
      ConversationUtil conversationUtil, EventDataConverterManager converterManager) {
    this.sessionManager = sessionManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.converterManager = converterManager;
  }

  @Override
  @VisibleForTesting
  @SuppressWarnings("deprecation") // StringEscapeUtils.unescapeHtml4 is deprecated in commons-lang3; commons-text is not on the classpath
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(request, false));
    if (user == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    String operation = request.getParameter("operation");
    String folder = request.getParameter("folder");
    String[] waves = request.getParameterValues("waveId");
    if ("move".equals(operation)) {
      if (!"archive".equals(folder) && !"inbox".equals(folder)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown folder");
        return;
      }
      if (waves != null) {
        boolean anyFailure = false;
        for (String wave : waves) {
          try {
            WaveId waveId = WaveId.deserialise(
                stripVersionSuffix(StringEscapeUtils.unescapeHtml4(wave)));
            moveToFolder(waveId, folder, user);
          } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Move to " + folder + " error ", ex);
            anyFailure = true;
          }
        }
        if (anyFailure) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to move wave(s)");
          return;
        }
      }
      response.setStatus(HttpServletResponse.SC_OK);
    } else if ("pin".equals(operation) || "unpin".equals(operation)) {
      boolean pinning = "pin".equals(operation);
      if (waves != null) {
        boolean anyFailure = false;
        for (String wave : waves) {
          try {
            WaveId waveId = WaveId.deserialise(
                stripVersionSuffix(StringEscapeUtils.unescapeHtml4(wave)));
            setPinState(waveId, pinning, user);
          } catch (Exception ex) {
            LOG.log(Level.SEVERE, (pinning ? "Pin" : "Unpin") + " error ", ex);
            anyFailure = true;
          }
        }
        if (anyFailure) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "Failed to " + (pinning ? "pin" : "unpin") + " wave(s)");
          return;
        }
      }
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown operation");
    }
  }

  /**
   * Moves a wave to the specified folder for a given participant.
   */
  public void moveToFolder(WaveId waveId, String folder, ParticipantId participant)
      throws InvalidRequestException, OperationException, InterruptedException, ExecutionException {

    OperationContextImpl context = new OperationContextImpl(waveletProvider,
        converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);

    OpBasedWavelet wavelet = context.openWavelet(waveId,
        WaveletId.of(waveId.getDomain(), IdConstants.CONVERSATION_ROOT_WAVELET), participant);
    ConversationView conversationView = context.getConversationUtil().buildConversation(wavelet);

    OpBasedWavelet udw = openParticipantUserDataWavelet(context, waveId, participant);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);

    SupplementedWave supplement =
        SupplementedWaveImpl.create(udwState, conversationView, participant, DefaultFollow.ALWAYS);
    if ("archive".equals(folder)) {
      supplement.archive();
    } else if ("inbox".equals(folder)) {
      supplement.inbox();
    }
    OperationUtil.submitDeltas(context, waveletProvider, LOGGING_REQUEST_LISTENER);
  }

  /**
   * Pins or unpins a wave for a given participant by writing directly to the
   * UDW folder document. This avoids building the conversation model, which
   * can fail for waves with malformed manifest documents.
   */
  public void setPinState(WaveId waveId, boolean pin, ParticipantId participant)
      throws InvalidRequestException, OperationException, InterruptedException, ExecutionException {
    verifyWaveVisibleToParticipant(waveId, participant);

    OperationContextImpl context = new OperationContextImpl(waveletProvider,
        converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);

    OpBasedWavelet udw = openParticipantUserDataWavelet(context, waveId, participant);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    if (pin) {
      if (!udwState.isInFolder(SupplementedWaveImpl.PINNED_FOLDER)) {
        udwState.addFolder(SupplementedWaveImpl.PINNED_FOLDER);
      }
    } else {
      if (udwState.isInFolder(SupplementedWaveImpl.PINNED_FOLDER)) {
        udwState.removeFolder(SupplementedWaveImpl.PINNED_FOLDER);
      }
    }
    OperationUtil.submitDeltas(context, waveletProvider, LOGGING_REQUEST_LISTENER);
  }

  private OpBasedWavelet openParticipantUserDataWavelet(
      OperationContextImpl context, WaveId waveId, ParticipantId participant)
      throws InvalidRequestException {
    WaveletId userDataWaveletId = IdUtil.buildUserDataWaveletId(participant);
    RobotWaveletData userDataWavelet = loadParticipantUserDataWavelet(waveId, participant);
    context.putWavelet(waveId, userDataWaveletId, userDataWavelet);
    return context.openWavelet(waveId, userDataWaveletId, participant);
  }

  private RobotWaveletData loadParticipantUserDataWavelet(WaveId waveId, ParticipantId participant)
      throws InvalidRequestException {
    WaveletName userDataWaveletName = WaveletName.of(waveId, IdUtil.buildUserDataWaveletId(participant));
    try {
      CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(userDataWaveletName);
      if (snapshot == null) {
        return RobotsUtil.createEmptyRobotWavelet(participant, userDataWaveletName);
      }
      return new RobotWaveletData(snapshot.snapshot, snapshot.committedVersion);
    } catch (WaveServerException e) {
      throw new InvalidRequestException("Wavelet " + userDataWaveletName + " couldn't be retrieved");
    }
  }

  /**
   * Strips a trailing {@code :N} version suffix from a serialized wave ID.
   * Some client code paths append a version number (e.g. {@code :1}) to the
   * wave ID for cache or OT versioning purposes.  The suffix is not part of
   * the canonical wave-ID format and must be removed before deserialization.
   */
  @VisibleForTesting
  static String stripVersionSuffix(String waveIdStr) {
    if (waveIdStr == null) {
      return null;
    }
    // Only strip from modern slash-form IDs (e.g. "domain/id:N").
    // Legacy IDs use '!' as separator; do not modify them.
    if (!waveIdStr.contains("/")) {
      return waveIdStr;
    }
    return VERSION_SUFFIX.matcher(waveIdStr).replaceFirst("");
  }

  private void verifyWaveVisibleToParticipant(WaveId waveId, ParticipantId participant)
      throws InvalidRequestException {
    try {
      boolean hasAccessibleWavelet = false;
      // G-PORT-8 (#1117): waveletProvider.getWaveletIds(waveId) reads the
      // PERSISTED wavelet store and returns an empty set for a wave that
      // is alive in memory but has not yet been flushed (e.g. the
      // freshly-created Welcome wave). This matched
      // SimpleSearchProviderImpl which also falls back to the in-memory
      // wave-map (see expandConversationalWavelets). Without that
      // fallback, every pin/archive on a brand-new wave returned 500
      // "Access rejected" because the wavelet-id set was empty before
      // the access check could even run. We honor the same fallback
      // here: try persisted ids first, then read in-memory wavelet ids
      // as a safety net so the access check runs on the actual wavelet
      // that IS loaded.
      java.util.Set<WaveletId> waveletIds =
          new java.util.HashSet<>(waveletProvider.getWaveletIds(waveId));
      if (waveletIds.isEmpty()) {
        // Fallback: ask the conversation-root wavelet directly. If the
        // user IS a participant of conv+root, the snapshot fetch will
        // succeed; if not, it returns null. This avoids exposing a
        // distinct 404-vs-403 surface (we still throw "Access rejected"
        // either way).
        WaveletId convRoot = WaveletId.of(
            waveId.getDomain(), IdConstants.CONVERSATION_ROOT_WAVELET);
        waveletIds.add(convRoot);
      }
      for (WaveletId waveletId : waveletIds) {
        WaveletName waveletName = WaveletName.of(waveId, waveletId);
        if (waveletProvider.checkAccessPermission(waveletName, participant)) {
          hasAccessibleWavelet = true;
          break;
        }
      }
      if (!hasAccessibleWavelet) {
        throw new InvalidRequestException("Access rejected");
      }
    } catch (WaveServerException e) {
      throw new InvalidRequestException("Wave " + waveId + " couldn't be retrieved");
    }
  }
}

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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringEscapeUtils;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
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
 * A servlet to move waves between folders (inbox/archive).
 * Hosted on /folder.
 *
 * Valid request format: POST /folder/?operation=move&amp;folder=archive&amp;waveId=xxxx
 *
 * Ported from Wiab.pro.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@Singleton
public final class FolderServlet extends HttpServlet {

  private static final Log LOG = Log.get(FolderServlet.class);
  private static final WaveletProvider.SubmitRequestListener LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);

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
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(request.getSession(false));
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
            WaveId waveId = WaveId.deserialise(StringEscapeUtils.unescapeHtml4(wave));
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

    WaveletId udwId =
        WaveletId.of(waveId.getDomain(),
            IdUtil.join(IdConstants.USER_DATA_WAVELET_PREFIX, participant.getAddress()));
    OpBasedWavelet udw = context.openWavelet(waveId, udwId, participant);

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
}

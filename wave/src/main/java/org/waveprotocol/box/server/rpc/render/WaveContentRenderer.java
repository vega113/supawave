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

package org.waveprotocol.box.server.rpc.render;

import org.waveprotocol.wave.client.render.ReductionBasedRenderer;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ReadOnlyWaveView;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Higher-level wave content renderer that converts a {@link WaveViewData}
 * snapshot into a complete HTML fragment for all blips in the wave.
 *
 * <p>This is SSR Phase 2, building on the Phase 1 {@link ServerHtmlRenderer}
 * which implements the low-level {@code RenderingRules<String>} interface.
 * This class adds:
 * <ul>
 *   <li>Title extraction from the first blip</li>
 *   <li>Tags rendering</li>
 *   <li>Wave-level metadata (wave ID, creation time)</li>
 *   <li>Blip and thread counting</li>
 *   <li>Robust edge-case handling (empty waves, deleted blips, etc.)</li>
 * </ul>
 *
 * <p>The output is an HTML fragment (not a full page) suitable for embedding
 * in a page template. Phase 4 will provide the servlet and template layer.
 *
 * @see ServerHtmlRenderer
 * @see ReductionBasedRenderer
 */
public final class WaveContentRenderer {

  private static final Logger LOG = Logger.getLogger(WaveContentRenderer.class.getName());

  // CSS class constants
  private static final String CSS_WAVE_CONTENT = "wave-content";
  private static final String CSS_WAVE_HEADER = "wave-header";
  private static final String CSS_WAVE_TITLE = "wave-title";
  private static final String CSS_WAVE_META = "wave-meta";
  private static final String CSS_WAVE_TAGS = "wave-tags";
  private static final String CSS_WAVE_TAG = "wave-tag";
  private static final String CSS_WAVE_BODY = "wave-body";
  private static final String CSS_WAVE_EMPTY = "wave-empty";

  /** No instantiation -- static utility class. */
  private WaveContentRenderer() {}

  interface RenderBudget {
    boolean isExceeded();
  }

  static final class RenderBudgetExceededException extends RuntimeException {
    private RenderBudgetExceededException() {
      super("Wave content render budget exceeded");
    }
  }

  // =========================================================================
  // Primary entry point
  // =========================================================================

  /**
   * Renders a wave snapshot into a complete HTML fragment.
   *
   * <p>The fragment includes a header section (title, metadata, tags) and
   * the conversation body (participants, blips, threads). It is designed
   * to be embedded in a page template.
   *
   * @param waveView the wave snapshot to render
   * @param viewer   the participant viewing the wave (for future read-state)
   * @return an HTML fragment string, never null
   */
  public static String renderWaveContent(WaveViewData waveView, ParticipantId viewer) {
    return renderWaveContent(waveView, viewer, () -> false);
  }

  static String renderWaveContent(WaveViewData waveView, ParticipantId viewer, RenderBudget budget) {
    checkBudget(budget);
    if (waveView == null) {
      return emptyWaveHtml("No wave data available.");
    }

    // Locate the conversational wavelet.
    ObservableWaveletData convWaveletData = findConversationWavelet(waveView);
    checkBudget(budget);
    if (convWaveletData == null) {
      return emptyWaveHtml("This wave has no conversation data.");
    }

    // Build the read-only conversation model.
    OpBasedWavelet wavelet;
    ObservableConversationView conversations;
    try {
      wavelet = OpBasedWavelet.createReadOnly(convWaveletData);
      if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
        return emptyWaveHtml("This wave has no conversation structure.");
      }
      checkBudget(budget);
      ReadOnlyWaveView wv = new ReadOnlyWaveView(waveView.getWaveId());
      wv.addWavelet(wavelet);
      IdGenerator readOnlyIdGen = new ServerHtmlRenderer.NoOpIdGenerator();
      conversations = WaveBasedConversationView.create(wv, readOnlyIdGen);
      checkBudget(budget);
    } catch (RenderBudgetExceededException e) {
      throw e;
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to build conversation model for wave "
          + waveView.getWaveId(), e);
      return emptyWaveHtml("Unable to render this wave.");
    }

    Conversation rootConversation = conversations.getRoot();
    if (rootConversation == null) {
      return emptyWaveHtml("This wave has no root conversation.");
    }

    // Extract metadata.
    checkBudget(budget);
    String title = extractTitle(rootConversation);
    Set<String> tags = safeGetTags(rootConversation);
    int[] counts = countBlipsAndThreads(rootConversation);
    int blipCount = counts[0];
    int threadCount = counts[1];
    long creationTime = convWaveletData.getCreationTime();

    // Render the conversation tree using Phase 1 renderer.
    String conversationHtml;
    try {
      checkBudget(budget);
      ServerHtmlRenderer rules = new ServerHtmlRenderer(viewer, budget);
      String rendered = ReductionBasedRenderer.renderWith(rules, conversations);
      checkBudget(budget);
      conversationHtml = rendered != null ? rendered : "";
    } catch (RenderBudgetExceededException e) {
      throw e;
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to render conversation tree for wave "
          + waveView.getWaveId(), e);
      conversationHtml = "<div class=\"" + CSS_WAVE_EMPTY
          + "\"><p>Error rendering wave content.</p></div>";
    }

    // Assemble the complete HTML fragment.
    StringBuilder sb = new StringBuilder();
    sb.append("<div class=\"").append(CSS_WAVE_CONTENT).append("\"");
    sb.append(" data-wave-id=\"")
        .append(ServerHtmlRenderer.escapeAttr(waveView.getWaveId().serialise()))
        .append("\">");

    // -- Header: title + metadata + tags --
    sb.append("<div class=\"").append(CSS_WAVE_HEADER).append("\">");

    // Title
    if (!title.isEmpty()) {
      sb.append("<h1 class=\"").append(CSS_WAVE_TITLE).append("\">")
          .append(ServerHtmlRenderer.escapeHtml(title)).append("</h1>");
    }

    // Metadata line
    sb.append("<div class=\"").append(CSS_WAVE_META).append("\">");
    if (creationTime > 0) {
      sb.append("<span>Created: ")
          .append(ServerHtmlRenderer.escapeHtml(formatTimestamp(creationTime)))
          .append("</span>");
    }
    sb.append("<span>").append(blipCount)
        .append(blipCount == 1 ? " message" : " messages").append("</span>");
    if (threadCount > 1) {
      sb.append("<span>").append(threadCount)
          .append(threadCount == 1 ? " thread" : " threads").append("</span>");
    }
    sb.append("</div>"); // wave-meta

    // Tags
    if (tags != null && !tags.isEmpty()) {
      sb.append("<div class=\"").append(CSS_WAVE_TAGS).append("\">");
      for (String tag : tags) {
        sb.append("<span class=\"").append(CSS_WAVE_TAG).append("\">")
            .append(ServerHtmlRenderer.escapeHtml(tag)).append("</span>");
      }
      sb.append("</div>");
    }

    sb.append("</div>"); // wave-header

    // -- Body: the rendered conversation tree --
    sb.append("<div class=\"").append(CSS_WAVE_BODY).append("\">");
    sb.append(conversationHtml);
    sb.append("</div>"); // wave-body

    sb.append("</div>"); // wave-content
    return sb.toString();
  }

  static void checkBudget(RenderBudget budget) {
    if (budget != null && budget.isExceeded()) {
      throw new RenderBudgetExceededException();
    }
  }

  // =========================================================================
  // Wavelet discovery
  // =========================================================================

  /**
   * Finds the conversational root wavelet in the wave view. Falls back to the
   * first conversational wavelet if no root is found.
   */
  static ObservableWaveletData findConversationWavelet(WaveViewData waveView) {
    ObservableWaveletData rootWaveletData = null;
    ObservableWaveletData otherConvData = null;
    for (ObservableWaveletData waveletData : waveView.getWavelets()) {
      WaveletId wid = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(wid)) {
        rootWaveletData = waveletData;
      } else if (IdUtil.isConversationalId(wid)) {
        if (otherConvData == null) {
          otherConvData = waveletData;
        }
      }
    }
    return rootWaveletData != null ? rootWaveletData : otherConvData;
  }

  // =========================================================================
  // Title extraction
  // =========================================================================

  /**
   * Extracts the wave title from the first blip of the root thread.
   *
   * @return the title string, or empty string if none found
   */
  static String extractTitle(Conversation conversation) {
    if (conversation == null) {
      return "";
    }
    ConversationThread rootThread = conversation.getRootThread();
    if (rootThread == null) {
      return "";
    }
    ConversationBlip firstBlip = rootThread.getFirstBlip();
    if (firstBlip == null) {
      return "";
    }
    Document doc = firstBlip.getContent();
    if (doc == null) {
      return "";
    }
    try {
      String title = TitleHelper.extractTitle(doc);
      return title != null ? title : "";
    } catch (Exception e) {
      LOG.log(Level.FINE, "Could not extract title", e);
      return "";
    }
  }

  // =========================================================================
  // Tags
  // =========================================================================

  /**
   * Safely retrieves tags from a conversation, returning null if unavailable.
   */
  static Set<String> safeGetTags(Conversation conversation) {
    try {
      Set<String> tags = conversation.getTags();
      return (tags != null && !tags.isEmpty()) ? tags : null;
    } catch (Exception e) {
      LOG.log(Level.FINE, "Could not retrieve tags", e);
      return null;
    }
  }

  // =========================================================================
  // Counting
  // =========================================================================

  /**
   * Counts the total number of blips and threads in a conversation.
   *
   * @return int array of [blipCount, threadCount]
   */
  static int[] countBlipsAndThreads(Conversation conversation) {
    int blipCount = 0;
    int threadCount = 0;

    ConversationThread rootThread = conversation.getRootThread();
    if (rootThread == null) {
      return new int[]{0, 0};
    }

    // Use a simple iterative BFS to avoid deep recursion.
    List<ConversationThread> queue = new ArrayList<>();
    queue.add(rootThread);

    while (!queue.isEmpty()) {
      ConversationThread thread = queue.remove(queue.size() - 1); // DFS via stack
      threadCount++;
      for (ConversationBlip blip : thread.getBlips()) {
        blipCount++;
        for (ConversationThread reply : blip.getReplyThreads()) {
          queue.add(reply);
        }
      }
    }

    return new int[]{blipCount, threadCount};
  }

  // =========================================================================
  // Utility
  // =========================================================================

  /** Produces an empty-state HTML fragment with a message. */
  private static String emptyWaveHtml(String message) {
    return "<div class=\"" + CSS_WAVE_CONTENT + "\">"
        + "<div class=\"" + CSS_WAVE_EMPTY + "\"><p>"
        + ServerHtmlRenderer.escapeHtml(message)
        + "</p></div></div>";
  }

  /** Formats a millisecond timestamp into a human-readable date string. */
  private static String formatTimestamp(long millis) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    return sdf.format(new Date(millis));
  }
}

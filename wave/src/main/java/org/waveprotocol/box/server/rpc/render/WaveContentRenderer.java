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
import java.util.LinkedHashSet;
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

  /**
   * Terminal placeholder appended after the windowed root-thread blips. The
   * J2CL client treats this as an AT-announcement marker only; the actual
   * extension-on-scroll trigger is driven by the live update's
   * {@code J2clSelectedWaveViewportState.getReadWindowEntries()} (placeholder
   * entries originate there, not from this server-side marker).
   */
  static final String VISIBLE_REGION_PLACEHOLDER_HTML =
      "<div class=\"visible-region-placeholder\""
          + " data-j2cl-server-placeholder=\"true\""
          + " data-segment=\"placeholder-tail\""
          + " role=\"listitem\" aria-busy=\"true\">"
          + "Additional blips will load on scroll."
          + "</div>";

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
    return renderWaveContent(waveView, viewer, () -> false, 0);
  }

  /**
   * Renders a wave snapshot into a complete HTML fragment, optionally
   * clamped to the first {@code initialWindowSize} root-thread blips. When
   * {@code initialWindowSize <= 0} the renderer falls back to the
   * whole-wave shape (the legacy GWT pre-render contract).
   *
   * <p>This is the entry point used by the J2CL root-shell first-paint
   * (R-6.1, R-7.1) so the inline server HTML matches the same window the
   * J2CL client requests on the live socket open. Inline reply threads
   * under the included root blips are always rendered fully — the window
   * applies only to the root-thread sequence.
   */
  public static String renderWaveContent(
      WaveViewData waveView, ParticipantId viewer, int initialWindowSize) {
    return renderWaveContent(waveView, viewer, () -> false, initialWindowSize);
  }

  static String renderWaveContent(WaveViewData waveView, ParticipantId viewer, RenderBudget budget) {
    return renderWaveContent(waveView, viewer, budget, 0);
  }

  static String renderWaveContent(
      WaveViewData waveView,
      ParticipantId viewer,
      RenderBudget budget,
      int initialWindowSize) {
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
    int[] counts = countBlipsAndThreads(rootConversation, budget);
    int blipCount = counts[0];
    int threadCount = counts[1];
    long creationTime = convWaveletData.getCreationTime();

    // Build the per-render windowing options before invoking the rules engine
    // so the renderer can mark the first focusable blip and short-list the
    // allowed root-thread blip ids in a single forward pass.
    ServerHtmlRenderer.WindowOptions windowOptions =
        buildWindowOptions(rootConversation, initialWindowSize, budget);

    // Render the conversation tree using Phase 1 renderer.
    String conversationHtml;
    try {
      checkBudget(budget);
      ServerHtmlRenderer rules = new ServerHtmlRenderer(viewer, budget, windowOptions);
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
        .append("\"");
    if (windowOptions.isWindowed()) {
      // F-1: the J2CL renderer reads this attribute to confirm the server
      // window size matches the limit it will request on the live socket open.
      sb.append(" data-j2cl-initial-window-size=\"")
          .append(initialWindowSize)
          .append("\"");
      sb.append(" data-j2cl-server-first-surface=\"true\"");
    }
    sb.append(">");

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

  /**
   * Build the per-render windowing options. When {@code initialWindowSize} is
   * positive and the root thread carries more blips than the requested
   * window, the returned options short-list the leading {@code N} blip ids
   * and supply the terminal placeholder. When {@code initialWindowSize} is
   * non-positive, or when the root thread fits entirely within the window,
   * the legacy whole-conversation shape is preserved.
   *
   * <p>Always sets {@code firstRootBlipId} to the first root-thread blip
   * (when one exists) so the keyboard contract (R-6.1) is consistent across
   * windowed and whole-wave renders — the static HTML must always have
   * exactly one focusable blip.
   */
  static ServerHtmlRenderer.WindowOptions buildWindowOptions(
      Conversation conversation, int initialWindowSize, RenderBudget budget) {
    if (conversation == null || conversation.getRootThread() == null) {
      return ServerHtmlRenderer.WindowOptions.none();
    }
    String firstRootBlipId = null;
    Set<String> allowed = new LinkedHashSet<String>();
    int taken = 0;
    boolean clamps = false;
    for (ConversationBlip blip : conversation.getRootThread().getBlips()) {
      checkBudget(budget);
      if (blip == null) {
        continue;
      }
      if (firstRootBlipId == null) {
        firstRootBlipId = blip.getId();
      }
      if (initialWindowSize > 0) {
        if (taken < initialWindowSize) {
          allowed.add(blip.getId());
          taken++;
        } else {
          clamps = true;
          break;
        }
      }
    }
    ConversationThread rootThread = conversation.getRootThread();
    if (initialWindowSize <= 0) {
      // Whole-wave shape — only the focus marker is meaningful.
      return new ServerHtmlRenderer.WindowOptions(
          firstRootBlipId,
          java.util.Collections.<String>emptySet(),
          null,
          rootThread);
    }
    // clamps is set during the single pass above; no second scan needed.
    return new ServerHtmlRenderer.WindowOptions(
        firstRootBlipId,
        allowed,
        clamps ? VISIBLE_REGION_PLACEHOLDER_HTML : null,
        rootThread);
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
  static int[] countBlipsAndThreads(Conversation conversation, RenderBudget budget) {
    int blipCount = 0;
    int threadCount = 0;

    ConversationThread rootThread = conversation.getRootThread();
    if (rootThread == null) {
      return new int[]{0, 0};
    }

    // Use a simple iterative DFS to avoid deep recursion.
    List<ConversationThread> queue = new ArrayList<>();
    queue.add(rootThread);

    while (!queue.isEmpty()) {
      checkBudget(budget);
      ConversationThread thread = queue.remove(queue.size() - 1);
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

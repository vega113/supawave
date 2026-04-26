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
import org.waveprotocol.wave.client.render.RenderingRules;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.ReadableDocument;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ReadOnlyWaveView;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

import org.waveprotocol.wave.model.id.WaveId;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Server-side HTML renderer for wave documents.
 *
 * <p>Implements {@link RenderingRules RenderingRules&lt;String&gt;} so it can
 * be plugged into {@link ReductionBasedRenderer} to produce a complete HTML
 * representation of a wave's conversation tree without any GWT or browser
 * dependencies.
 *
 * <p>This is the foundation of SSR (server-side rendering) for Apache Wave.
 * Later phases will wire this into a servlet for public wave viewing and
 * pre-rendering for the GWT client.
 *
 * @see RenderingRules
 * @see ReductionBasedRenderer
 */
public final class ServerHtmlRenderer implements RenderingRules<String> {

  // =========================================================================
  // CSS class constants (matching the wave panel style conventions)
  // =========================================================================

  private static final String CSS_WAVE = "wave";
  private static final String CSS_CONVERSATION = "conversation";
  private static final String CSS_PARTICIPANTS = "participants";
  private static final String CSS_PARTICIPANT = "participant";
  private static final String CSS_AVATAR = "participant-avatar";
  private static final String CSS_THREAD = "thread";
  private static final String CSS_INLINE_THREAD = "inline-thread";
  private static final String CSS_BLIP = "blip";
  private static final String CSS_BLIP_META = "blip-meta";
  private static final String CSS_BLIP_AUTHOR = "blip-author";
  private static final String CSS_BLIP_TIME = "blip-time";
  private static final String CSS_BLIP_CONTENT = "blip-content";
  private static final String CSS_BLIP_REPLIES = "blip-replies";
  private static final String CSS_ANCHOR = "anchor";

  // =========================================================================
  // Instance state
  // =========================================================================

  /** The viewer for whom we are rendering (used for future read-state, not yet wired). */
  private final ParticipantId viewer;
  private final WaveContentRenderer.RenderBudget budget;
  private final WindowOptions windowOptions;
  /**
   * Tracks whether the non-windowed render has already assigned {@code tabindex="0"} to a root
   * blip.  In windowed mode {@link WindowOptions#firstRootBlipId()} controls focus; in non-windowed
   * mode we assign focus to the very first root-thread blip we encounter so that the rendered HTML
   * always has exactly one keyboard-focusable entry point.
   */
  private boolean nonWindowedFocusAssigned = false;

  public ServerHtmlRenderer(ParticipantId viewer) {
    this(viewer, () -> false);
  }

  ServerHtmlRenderer(ParticipantId viewer, WaveContentRenderer.RenderBudget budget) {
    this(viewer, budget, WindowOptions.none());
  }

  ServerHtmlRenderer(
      ParticipantId viewer,
      WaveContentRenderer.RenderBudget budget,
      WindowOptions windowOptions) {
    this.viewer = viewer;
    this.budget = budget;
    this.windowOptions = windowOptions == null ? WindowOptions.none() : windowOptions;
  }

  /**
   * Per-render windowing + keyboard-contract options applied during HTML
   * emission. Carrying these through the render pipeline lets the renderer
   * honour the F-1 visible-window contract (R-3.5/R-6.1/R-7.1) without
   * mutating per-blip emission semantics for the legacy GWT pre-render path.
   */
  static final class WindowOptions {
    private static final WindowOptions NONE = new WindowOptions(null, Collections.<String>emptySet(), null);

    private final String firstRootBlipId;
    private final Set<String> allowedRootBlipIds;
    private final String terminalPlaceholderHtml;

    WindowOptions(
        String firstRootBlipId,
        Set<String> allowedRootBlipIds,
        String terminalPlaceholderHtml) {
      this.firstRootBlipId = firstRootBlipId;
      this.allowedRootBlipIds =
          allowedRootBlipIds == null
              ? Collections.<String>emptySet()
              : Collections.unmodifiableSet(allowedRootBlipIds);
      this.terminalPlaceholderHtml = terminalPlaceholderHtml;
    }

    static WindowOptions none() {
      return NONE;
    }

    String firstRootBlipId() {
      return firstRootBlipId;
    }

    boolean isWindowed() {
      return !allowedRootBlipIds.isEmpty();
    }

    boolean isAllowed(String rootBlipId) {
      return allowedRootBlipIds.contains(rootBlipId);
    }

    String terminalPlaceholderHtml() {
      return terminalPlaceholderHtml == null ? "" : terminalPlaceholderHtml;
    }
  }

  // =========================================================================
  // RenderingRules<String> implementation
  // =========================================================================

  /**
   * Renders the document content of a blip. This is the "leaf" render that
   * converts the blip's XML document tree into HTML.
   *
   * @param blip   the blip whose document to render
   * @param replies thread renderings that may be consumed for inline placement
   * @return HTML string of the document content
   */
  @Override
  public String render(ConversationBlip blip, IdentityMap<ConversationThread, String> replies) {
    checkBudget();
    Document doc = blip.getContent();
    if (doc == null) {
      return "";
    }
    return renderDocument(doc, budget);
  }

  /**
   * Renders a complete blip: metadata (author, time) + document + reply threads.
   */
  @Override
  public String render(ConversationBlip blip, String document,
      IdentityMap<ConversationThread, String> defaultAnchors,
      IdentityMap<Conversation, String> nestedConversations) {
    checkBudget();

    StringBuilder sb = new StringBuilder();
    sb.append("<div class=\"").append(CSS_BLIP).append("\"");
    sb.append(" data-blip-id=\"").append(escapeAttr(blip.getId())).append("\"");
    boolean isRootThreadBlip =
        blip.getThread() != null
            && blip.getThread().getConversation() != null
            && blip.getThread() == blip.getThread().getConversation().getRootThread();
    sb.append(" role=\"").append(isRootThreadBlip ? "listitem" : "article").append("\"");
    boolean isTabbable;
    if (windowOptions.isWindowed()) {
      // Windowed mode: only the designated first root blip is tabbable.
      isTabbable =
          isRootThreadBlip
              && windowOptions.firstRootBlipId() != null
              && windowOptions.firstRootBlipId().equals(blip.getId());
    } else {
      // Non-windowed mode: make the first root-thread blip encountered tabbable
      // so the rendered surface always has at least one keyboard entry point.
      if (isRootThreadBlip && !nonWindowedFocusAssigned) {
        isTabbable = true;
        nonWindowedFocusAssigned = true;
      } else {
        isTabbable = false;
      }
    }
    sb.append(" tabindex=\"").append(isTabbable ? "0" : "-1").append("\">");

    // -- Meta bar: author + timestamp --
    sb.append("<div class=\"").append(CSS_BLIP_META).append("\">");

    ParticipantId author = blip.getAuthorId();
    String authorAddr = author != null ? author.getAddress() : "unknown";
    sb.append("<span class=\"").append(CSS_BLIP_AUTHOR).append("\">")
        .append(escapeHtml(authorAddr)).append("</span>");

    long lastModified = blip.getLastModifiedTime();
    if (lastModified > 0) {
      sb.append(" <span class=\"").append(CSS_BLIP_TIME).append("\">")
          .append(escapeHtml(formatTimestamp(lastModified))).append("</span>");
    }

    sb.append("</div>"); // blip-meta

    // -- Document content --
    sb.append("<div class=\"").append(CSS_BLIP_CONTENT).append("\">")
        .append(document)
        .append("</div>");

    // -- Default-anchored reply threads --
    StringBuilder repliesHtml = new StringBuilder();
    for (ConversationThread thread : blip.getReplyThreads()) {
      checkBudget();
      String anchor = defaultAnchors.get(thread);
      if (anchor != null) {
        repliesHtml.append(anchor);
      }
    }
    // -- Nested (private) conversations --
    nestedConversations.each((conv, html) -> repliesHtml.append(html));

    if (repliesHtml.length() > 0) {
      sb.append("<div class=\"").append(CSS_BLIP_REPLIES).append("\">")
          .append(repliesHtml)
          .append("</div>");
    }

    sb.append("</div>"); // blip
    return sb.toString();
  }

  /**
   * Renders a thread: a sequence of blips.
   */
  @Override
  public String render(ConversationThread thread,
      IdentityMap<ConversationBlip, String> blipUis) {
    checkBudget();
    StringBuilder sb = new StringBuilder();

    boolean isRoot = thread.getConversation().getRootThread() == thread;
    String cssClass = isRoot ? CSS_THREAD : CSS_INLINE_THREAD;

    sb.append("<div class=\"").append(cssClass).append("\"");
    sb.append(" data-thread-id=\"").append(escapeAttr(thread.getId())).append("\"");
    sb.append(" role=\"").append(isRoot ? "list" : "group").append("\"");
    if (!isRoot) {
      // Inline-thread aria-label mirrors what J2clReadSurfaceDomRenderer adds
      // post-mount; supplying it server-side keeps the static HTML AT-usable
      // before client boot (R-6.1).
      sb.append(" aria-label=\"inline reply thread\"");
    }
    sb.append(">");

    boolean filterRootBlips = isRoot && windowOptions.isWindowed();
    for (ConversationBlip blip : thread.getBlips()) {
      checkBudget();
      if (filterRootBlips && !windowOptions.isAllowed(blip.getId())) {
        continue;
      }
      String blipHtml = blipUis.get(blip);
      if (blipHtml != null) {
        sb.append(blipHtml);
      }
    }

    if (filterRootBlips) {
      sb.append(windowOptions.terminalPlaceholderHtml());
    }

    sb.append("</div>");
    return sb.toString();
  }

  /**
   * Renders a conversation: participants + root thread.
   */
  @Override
  public String render(Conversation conversation, String participants, String thread) {
    checkBudget();
    StringBuilder sb = new StringBuilder();
    sb.append("<div class=\"").append(CSS_CONVERSATION).append("\"");
    sb.append(" data-conv-id=\"").append(escapeAttr(conversation.getId())).append("\">");
    sb.append(participants);
    sb.append(thread);
    sb.append("</div>");
    return sb.toString();
  }

  /**
   * Renders a single participant.
   */
  @Override
  public String render(Conversation conversation, ParticipantId participant) {
    checkBudget();
    String address = participant.getAddress();
    String name = extractNameFromAddress(address);
    String avatarLetter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();

    StringBuilder sb = new StringBuilder();
    sb.append("<span class=\"").append(CSS_PARTICIPANT).append("\"");
    sb.append(" data-address=\"").append(escapeAttr(address)).append("\"");
    sb.append(" title=\"").append(escapeAttr(address)).append("\">");
    sb.append("<span class=\"").append(CSS_AVATAR).append("\">")
        .append(escapeHtml(avatarLetter)).append("</span>");
    sb.append("<span>").append(escapeHtml(name)).append("</span>");
    sb.append("</span>");
    return sb.toString();
  }

  /**
   * Renders the participants bar for a conversation.
   */
  @Override
  public String render(Conversation conversation, StringMap<String> participantUis) {
    checkBudget();
    StringBuilder sb = new StringBuilder();
    sb.append("<div class=\"").append(CSS_PARTICIPANTS).append("\">");
    for (ParticipantId participant : conversation.getParticipantIds()) {
      checkBudget();
      String html = participantUis.get(participant.getAddress());
      if (html != null) {
        sb.append(html);
      }
    }
    sb.append("</div>");
    return sb.toString();
  }

  /**
   * Renders the wave view: picks the first (main) conversation.
   */
  @Override
  public String render(ConversationView wave,
      IdentityMap<Conversation, String> conversations) {
    checkBudget();
    if (conversations.isEmpty()) {
      return "<div class=\"" + CSS_WAVE + "\"></div>";
    }
    // Return the first available conversation rendering.
    return conversations.reduce(null,
        (soFar, key, item) -> soFar == null ? item : soFar);
  }

  /**
   * Renders a default anchor wrapping a thread.
   */
  @Override
  public String render(ConversationThread thread, String threadR) {
    checkBudget();
    if (threadR == null) {
      return "";
    }
    return "<div class=\"" + CSS_ANCHOR + "\">" + threadR + "</div>";
  }

  // =========================================================================
  // Document content extraction
  // =========================================================================

  /**
   * Converts a wave {@link Document} (XML DOM) into HTML by walking its tree.
   *
   * <p>The wave document structure is roughly:
   * <pre>
   *   &lt;body&gt;
   *     &lt;line/&gt; text content ...
   *     &lt;line/&gt; more text ...
   *   &lt;/body&gt;
   * </pre>
   *
   * We translate {@code <line>} elements into {@code <p>} paragraphs and
   * preserve known formatting attributes.
   */
  public static String renderDocument(Document doc) {
    return renderDocument(doc, () -> false);
  }

  private static String renderDocument(
      Document doc, WaveContentRenderer.RenderBudget budget) {
    WaveContentRenderer.checkBudget(budget);
    Doc.E docElement = doc.getDocumentElement();
    if (docElement == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    renderNode(doc, docElement, sb, false, budget);
    return sb.toString();
  }

  /**
   * Recursively renders a document node and its children into HTML.
   *
   * @param doc      the readable document
   * @param node     current node to render
   * @param sb       output buffer
   * @param inBody   whether we are inside a {@code <body>} element
   */
  private static void renderNode(ReadableDocument<Doc.N, Doc.E, Doc.T> doc,
      Doc.N node, StringBuilder sb, boolean inBody, WaveContentRenderer.RenderBudget budget) {
    WaveContentRenderer.checkBudget(budget);
    Doc.E element = doc.asElement(node);
    if (element != null) {
      renderElement(doc, element, sb, inBody, budget);
    } else {
      Doc.T text = doc.asText(node);
      if (text != null) {
        sb.append(escapeHtml(doc.getData(text)));
      }
    }
  }

  /**
   * Renders an element node. Maps wave document tags to HTML equivalents.
   */
  private static void renderElement(ReadableDocument<Doc.N, Doc.E, Doc.T> doc,
      Doc.E element, StringBuilder sb, boolean inBody, WaveContentRenderer.RenderBudget budget) {
    WaveContentRenderer.checkBudget(budget);
    String tag = doc.getTagName(element);
    Map<String, String> attrs = doc.getAttributes(element);

    if ("body".equals(tag)) {
      // Enter the body: render children in body-mode.
      renderChildren(doc, element, sb, true, budget);
      return;
    }

    if ("line".equals(tag) || "l:p".equals(tag)) {
      // Line element marks a paragraph boundary.
      // Check for heading type (t attribute).
      String lineType = attrs != null ? attrs.get("t") : null;
      String htmlTag = mapLineType(lineType);
      sb.append("<").append(htmlTag).append(">");
      // The text content follows the <line/> as siblings, not children.
      // We close the tag after rendering subsequent text siblings (handled by
      // the caller in renderChildren).
      return;
    }

    if ("reply".equals(tag)) {
      // Inline reply anchor -- skip, threads are rendered via the anchor mechanism.
      return;
    }

    if ("head".equals(tag)) {
      // Blip head metadata -- skip for content rendering.
      return;
    }

    // Formatting elements
    if ("b".equals(tag) || "bold".equals(tag)) {
      sb.append("<strong>");
      renderChildren(doc, element, sb, inBody, budget);
      sb.append("</strong>");
      return;
    }
    if ("i".equals(tag) || "italic".equals(tag)) {
      sb.append("<em>");
      renderChildren(doc, element, sb, inBody, budget);
      sb.append("</em>");
      return;
    }
    if ("u".equals(tag) || "underline".equals(tag)) {
      sb.append("<u>");
      renderChildren(doc, element, sb, inBody, budget);
      sb.append("</u>");
      return;
    }
    if ("s".equals(tag) || "strikethrough".equals(tag)) {
      sb.append("<s>");
      renderChildren(doc, element, sb, inBody, budget);
      sb.append("</s>");
      return;
    }

    // Links
    if ("a".equals(tag) || "link".equals(tag)) {
      String href = attrs != null ? attrs.get("href") : null;
      if (href == null && attrs != null) {
        href = attrs.get("url");
      }
      String safeHref = sanitizeUrl(href, true);
      if (safeHref != null) {
        sb.append("<a href=\"").append(escapeAttr(safeHref)).append("\" rel=\"nofollow\">");
      } else if (href != null) {
        sb.append("<a rel=\"nofollow\">");
      } else {
        sb.append("<a>");
      }
      renderChildren(doc, element, sb, inBody, budget);
      sb.append("</a>");
      return;
    }

    // Checkbox (task check element)
    if ("check".equals(tag)) {
      String value = attrs != null ? attrs.get("value") : null;
      boolean checked = "true".equalsIgnoreCase(value);
      sb.append("<input type=\"checkbox\" disabled");
      if (checked) {
        sb.append(" checked");
      }
      sb.append(" />");
      return;
    }

    // Image
    if ("image".equals(tag) || "img".equals(tag)) {
      String src = attrs != null ? attrs.get("src") : null;
      if (src == null && attrs != null) {
        src = attrs.get("attachment");
      }
      String safeSrc = sanitizeUrl(src, false);
      if (safeSrc != null) {
        sb.append("<img src=\"").append(escapeAttr(safeSrc)).append("\" />");
      }
      return;
    }

    // Default: render children without wrapping unknown elements.
    renderChildren(doc, element, sb, inBody, budget);
  }

  /**
   * Renders the children of an element. When inside the body, groups text
   * nodes between {@code <line>} markers into paragraphs.
   */
  private static void renderChildren(ReadableDocument<Doc.N, Doc.E, Doc.T> doc,
      Doc.E parent, StringBuilder sb, boolean inBody, WaveContentRenderer.RenderBudget budget) {
    WaveContentRenderer.checkBudget(budget);
    if (!inBody) {
      // Outside body, just render children sequentially.
      for (Doc.N child = doc.getFirstChild(parent); child != null;
          child = doc.getNextSibling(child)) {
        WaveContentRenderer.checkBudget(budget);
        renderNode(doc, child, sb, false, budget);
      }
      return;
    }

    // Inside body: group text between line elements into <p> blocks.
    boolean inParagraph = false;
    String currentTag = "p";

    for (Doc.N child = doc.getFirstChild(parent); child != null;
        child = doc.getNextSibling(child)) {
      WaveContentRenderer.checkBudget(budget);
      Doc.E childEl = doc.asElement(child);
      if (childEl != null) {
        String childTag = doc.getTagName(childEl);
        if ("line".equals(childTag) || "l:p".equals(childTag)) {
          // Close previous paragraph if open.
          if (inParagraph) {
            sb.append("</").append(currentTag).append(">");
          }
          // Determine the HTML tag for this line.
          Map<String, String> lineAttrs = doc.getAttributes(childEl);
          String lineType = lineAttrs != null ? lineAttrs.get("t") : null;
          currentTag = mapLineType(lineType);
          sb.append("<").append(currentTag).append(">");
          inParagraph = true;
        } else if ("reply".equals(childTag) || "head".equals(childTag)) {
          // Skip inline anchors and head elements.
        } else {
          // Nested element inside body (formatting etc.)
          if (!inParagraph) {
            currentTag = "p";
            sb.append("<").append(currentTag).append(">");
            inParagraph = true;
          }
          renderNode(doc, child, sb, true, budget);
        }
      } else {
        Doc.T text = doc.asText(child);
        if (text != null) {
          String data = doc.getData(text);
          if (data != null && !data.isEmpty()) {
            if (!inParagraph) {
              currentTag = "p";
              sb.append("<").append(currentTag).append(">");
              inParagraph = true;
            }
            sb.append(escapeHtml(data));
          }
        }
      }
    }
    // Close trailing paragraph.
    if (inParagraph) {
      sb.append("</").append(currentTag).append(">");
    }
  }

  private void checkBudget() {
    WaveContentRenderer.checkBudget(budget);
  }

  /**
   * Maps a wave line {@code t} attribute to an HTML tag name.
   */
  private static String mapLineType(String lineType) {
    if (lineType == null) {
      return "p";
    }
    switch (lineType) {
      case "h1": return "h1";
      case "h2": return "h2";
      case "h3": return "h3";
      case "h4": return "h4";
      case "li":
        return "li";
      default:
        return "p";
    }
  }

  // =========================================================================
  // Static entry point
  // =========================================================================

  /**
   * Renders an entire wave as a complete HTML fragment.
   *
   * <p>This is the primary entry point for server-side rendering. It
   * constructs a {@link ConversationView} from the raw wave data, then
   * feeds it through the {@link ReductionBasedRenderer} with this
   * renderer's production rules.
   *
   * @param waveView the wave snapshot to render
   * @param viewer   the participant viewing the wave (for future read-state)
   * @return a complete HTML string for all blips in the wave, or an empty
   *         {@code <div>} if the wave has no conversations
   */
  public static String renderWave(WaveViewData waveView, ParticipantId viewer) {
    // Find the conversational root wavelet.
    ObservableWaveletData rootWaveletData = null;
    ObservableWaveletData otherConvData = null;
    for (ObservableWaveletData waveletData : waveView.getWavelets()) {
      WaveletId wid = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(wid)) {
        rootWaveletData = waveletData;
      } else if (IdUtil.isConversationalId(wid)) {
        otherConvData = waveletData;
      }
    }

    ObservableWaveletData convWaveletData =
        rootWaveletData != null ? rootWaveletData : otherConvData;
    if (convWaveletData == null) {
      return "<div class=\"" + CSS_WAVE + "\"></div>";
    }

    OpBasedWavelet wavelet = OpBasedWavelet.createReadOnly(convWaveletData);
    if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
      return "<div class=\"" + CSS_WAVE + "\"></div>";
    }

    // Build the conversation model.
    ReadOnlyWaveView wv = new ReadOnlyWaveView(waveView.getWaveId());
    wv.addWavelet(wavelet);

    // Use a no-op IdGenerator since we are only reading.
    IdGenerator readOnlyIdGen = new NoOpIdGenerator();
    ObservableConversationView conversations = WaveBasedConversationView.create(wv, readOnlyIdGen);

    // Render via the reduction pipeline.
    ServerHtmlRenderer rules = new ServerHtmlRenderer(viewer);
    String rendered = ReductionBasedRenderer.renderWith(rules, conversations);

    if (rendered == null) {
      return "<div class=\"" + CSS_WAVE + "\"></div>";
    }

    return "<div class=\"" + CSS_WAVE + "\">" + rendered + "</div>";
  }

  /**
   * Returns a self-contained HTML page wrapping the rendered wave content,
   * including basic CSS styles.
   *
   * @param waveView the wave snapshot to render
   * @param viewer   the participant viewing the wave
   * @return a complete HTML page string
   */
  public static String renderWavePage(WaveViewData waveView, ParticipantId viewer) {
    String content = renderWave(waveView, viewer);
    StringBuilder page = new StringBuilder();
    page.append("<!DOCTYPE html>\n<html>\n<head>\n");
    page.append("<meta charset=\"UTF-8\">\n");
    page.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    page.append("<title>Wave</title>\n");
    page.append("<style>\n");
    page.append(DEFAULT_CSS);
    page.append("\n</style>\n");
    page.append("</head>\n<body>\n");
    page.append(content);
    page.append("\n</body>\n</html>");
    return page.toString();
  }

  // =========================================================================
  // Utility methods
  // =========================================================================

  /** Escapes a string for safe inclusion in HTML text content. */
  static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&':  sb.append("&amp;");  break;
        case '<':  sb.append("&lt;");   break;
        case '>':  sb.append("&gt;");   break;
        case '"':  sb.append("&quot;"); break;
        case '\'': sb.append("&#39;");  break;
        default:   sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Escapes a string for safe inclusion in an HTML attribute value. */
  static String escapeAttr(String text) {
    // Same as escapeHtml -- covers all necessary attribute escaping.
    return escapeHtml(text);
  }

  static String sanitizeUrl(String candidateUrl, boolean allowMailto) {
    if (candidateUrl == null) {
      return null;
    }
    String trimmedUrl = candidateUrl.trim();
    if (trimmedUrl.isEmpty()) {
      return null;
    }
    // Reject protocol-relative URLs like //evil.example/path; browsers resolve
    // these using the current page scheme and they are a common open-redirect vector.
    if (trimmedUrl.startsWith("//")) {
      return null;
    }
    // Extract scheme by scanning for the first ':'.  Using indexOf instead of
    // java.net.URI keeps the check permissive enough to accept URLs that contain
    // spaces or other characters that URI strictly rejects
    // (e.g. "https://example.com/search?q=hello world").
    // A colon is only a scheme separator when it appears before any '/', '?', or '#'
    // — otherwise it is part of a path, query, or fragment (e.g. "?q=foo:bar").
    int colonIdx = trimmedUrl.indexOf(':');
    int firstPathChar = Integer.MAX_VALUE;
    for (char c : new char[]{'/', '?', '#'}) {
      int idx = trimmedUrl.indexOf(c);
      if (idx >= 0 && idx < firstPathChar) {
        firstPathChar = idx;
      }
    }
    boolean colonIsScheme = colonIdx > 0 && colonIdx < firstPathChar;
    String normalizedScheme = colonIsScheme
        ? trimmedUrl.substring(0, colonIdx).toLowerCase(Locale.ROOT)
        : null;
    // No scheme → relative URL (path, query, or fragment) — always safe.
    if (normalizedScheme == null) {
      return trimmedUrl;
    }
    boolean allowed = "http".equals(normalizedScheme)
        || "https".equals(normalizedScheme)
        || "ftp".equals(normalizedScheme)
        || (allowMailto && "mailto".equals(normalizedScheme))
        // wave:// and waveid:// are internal Wave reference schemes used for
        // in-app navigation; clients convert them to safe fragment URLs.
        || (allowMailto && ("wave".equals(normalizedScheme) || "waveid".equals(normalizedScheme)));
    return allowed ? trimmedUrl : null;
  }

  /** Extracts a human-readable name from a wave address (e.g., "user@example.com" -> "user"). */
  private static String extractNameFromAddress(String address) {
    if (address == null || address.isEmpty()) {
      return "unknown";
    }
    int at = address.indexOf('@');
    return at > 0 ? address.substring(0, at) : address;
  }

  /** Formats a millisecond timestamp into a human-readable date string. */
  private static String formatTimestamp(long millis) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    return sdf.format(new Date(millis));
  }

  // =========================================================================
  // No-op IdGenerator for read-only conversation construction
  // =========================================================================

  /**
   * A minimal {@link IdGenerator} that throws on any attempt to generate IDs.
   * Used when constructing a read-only conversation view for rendering.
   */
  static final class NoOpIdGenerator implements IdGenerator {
    @Override public WaveId newWaveId() {
      throw new UnsupportedOperationException("Read-only: cannot generate wave IDs");
    }
    @Override public WaveletId newConversationWaveletId() {
      throw new UnsupportedOperationException("Read-only: cannot generate wavelet IDs");
    }
    @Override public WaveletId newConversationRootWaveletId() {
      throw new UnsupportedOperationException("Read-only: cannot generate wavelet IDs");
    }
    @Override public WaveletId buildConversationRootWaveletId(WaveId waveId) {
      throw new UnsupportedOperationException("Read-only: cannot generate wavelet IDs");
    }
    @Override public WaveletId newUserDataWaveletId(String address) {
      throw new UnsupportedOperationException("Read-only: cannot generate wavelet IDs");
    }
    @Override public String newUniqueToken() {
      throw new UnsupportedOperationException("Read-only: cannot generate tokens");
    }
    @Override public String newDataDocumentId() {
      throw new UnsupportedOperationException("Read-only: cannot generate doc IDs");
    }
    @Override public String newBlipId() {
      throw new UnsupportedOperationException("Read-only: cannot generate blip IDs");
    }
    @Override public @Deprecated String peekBlipId() {
      throw new UnsupportedOperationException("Read-only: cannot generate blip IDs");
    }
    @Override public String newId(String namespace) {
      throw new UnsupportedOperationException("Read-only: cannot generate IDs");
    }
    @Override public String getDefaultDomain() {
      return "local.net";
    }
  }

  // =========================================================================
  // Default CSS styles
  // =========================================================================

  private static final String DEFAULT_CSS =
      "body {\n"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,\n"
      + "    Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;\n"
      + "  background: #f0f4f8;\n"
      + "  color: #1a202c;\n"
      + "  margin: 0;\n"
      + "  padding: 16px;\n"
      + "}\n"
      + ".wave {\n"
      + "  max-width: 900px;\n"
      + "  margin: 0 auto;\n"
      + "}\n"
      + ".conversation {\n"
      + "  background: #fff;\n"
      + "  border-radius: 8px;\n"
      + "  box-shadow: 0 1px 3px rgba(0,0,0,0.12);\n"
      + "  padding: 16px;\n"
      + "}\n"
      + ".participants {\n"
      + "  display: flex;\n"
      + "  flex-wrap: wrap;\n"
      + "  gap: 8px;\n"
      + "  padding: 8px 0;\n"
      + "  border-bottom: 1px solid #e2e8f0;\n"
      + "  margin-bottom: 16px;\n"
      + "}\n"
      + ".participant {\n"
      + "  display: inline-flex;\n"
      + "  align-items: center;\n"
      + "  gap: 4px;\n"
      + "  font-size: 0.85em;\n"
      + "  color: #4a5568;\n"
      + "}\n"
      + ".participant-avatar {\n"
      + "  display: inline-flex;\n"
      + "  align-items: center;\n"
      + "  justify-content: center;\n"
      + "  width: 24px;\n"
      + "  height: 24px;\n"
      + "  border-radius: 50%;\n"
      + "  background: #0077b6;\n"
      + "  color: #fff;\n"
      + "  font-size: 0.75em;\n"
      + "  font-weight: bold;\n"
      + "}\n"
      + ".blip {\n"
      + "  margin-bottom: 12px;\n"
      + "  padding: 8px 0;\n"
      + "  border-bottom: 1px solid #edf2f7;\n"
      + "}\n"
      + ".blip:last-child {\n"
      + "  border-bottom: none;\n"
      + "}\n"
      + ".blip-meta {\n"
      + "  display: flex;\n"
      + "  align-items: center;\n"
      + "  gap: 8px;\n"
      + "  margin-bottom: 4px;\n"
      + "}\n"
      + ".blip-author {\n"
      + "  font-weight: 600;\n"
      + "  color: #0077b6;\n"
      + "  font-size: 0.9em;\n"
      + "}\n"
      + ".blip-time {\n"
      + "  font-size: 0.8em;\n"
      + "  color: #a0aec0;\n"
      + "}\n"
      + ".blip-content {\n"
      + "  line-height: 1.6;\n"
      + "}\n"
      + ".blip-content p {\n"
      + "  margin: 0.25em 0;\n"
      + "}\n"
      + ".blip-content h1, .blip-content h2,\n"
      + ".blip-content h3, .blip-content h4 {\n"
      + "  margin: 0.5em 0 0.25em;\n"
      + "}\n"
      + ".blip-content a {\n"
      + "  color: #0077b6;\n"
      + "  text-decoration: underline;\n"
      + "}\n"
      + ".blip-replies {\n"
      + "  margin-left: 24px;\n"
      + "  padding-left: 12px;\n"
      + "  border-left: 2px solid #e2e8f0;\n"
      + "}\n"
      + ".inline-thread {\n"
      + "  margin-left: 24px;\n"
      + "  padding-left: 12px;\n"
      + "  border-left: 2px solid #90e0ef;\n"
      + "}\n"
      + ".anchor {\n"
      + "  margin: 4px 0;\n"
      + "}\n";
}

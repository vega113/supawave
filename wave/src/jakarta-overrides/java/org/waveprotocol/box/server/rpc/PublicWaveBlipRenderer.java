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
package org.waveprotocol.box.server.rpc;

import org.waveprotocol.wave.model.document.DocumentConstants;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.InitializationCursorAdapter;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders blip content from a wavelet snapshot into structured HTML for the
 * public wave page. Walks the conversation manifest to extract blips in
 * document order with thread depth, then converts each blip's DocOp into
 * HTML with paragraph structure and inline formatting (bold, italic, links).
 *
 * <p>IMPORTANT: {@code characters()} calls can be split by
 * {@code annotationBoundary()} at any point, so we always use StringBuilder
 * to accumulate text and flush it when the formatting context changes.
 */
public final class PublicWaveBlipRenderer {

  private PublicWaveBlipRenderer() {} // utility class

  // =========================================================================
  // BlipData — lightweight holder for rendered blip information
  // =========================================================================

  /** Holds the rendered information for a single blip. */
  public static final class BlipInfo {
    public final String author;
    public final long lastModifiedTime;
    public final String htmlContent;
    public final int threadDepth;

    BlipInfo(String author, long lastModifiedTime, String htmlContent, int threadDepth) {
      this.author = author;
      this.lastModifiedTime = lastModifiedTime;
      this.htmlContent = htmlContent;
      this.threadDepth = threadDepth;
    }
  }

  // =========================================================================
  // Public API
  // =========================================================================

  /**
   * Extracts all blips from the wavelet in conversation-manifest order,
   * rendering each blip's document content to HTML.
   *
   * @param snapshot the wavelet snapshot
   * @return ordered list of rendered blip info, empty if no blips found
   */
  public static List<BlipInfo> renderBlips(ReadableWaveletData snapshot) {
    List<BlipInfo> result = new ArrayList<>();

    // Find the conversation manifest document.
    if (!snapshot.getDocumentIds().contains(DocumentConstants.MANIFEST_DOCUMENT_ID)) {
      return result;
    }

    ReadableBlipData manifestDoc =
        snapshot.getDocument(DocumentConstants.MANIFEST_DOCUMENT_ID);
    if (manifestDoc == null) {
      return result;
    }

    // Walk the manifest to collect blip IDs in order with their thread depth.
    List<ManifestEntry> entries = walkManifest(manifestDoc);

    // Render each blip.
    for (ManifestEntry entry : entries) {
      ReadableBlipData blipDoc = snapshot.getDocument(entry.blipId);
      if (blipDoc == null) {
        continue; // deleted blip
      }

      String author = blipDoc.getAuthor() != null
          ? blipDoc.getAuthor().getAddress() : "";
      long lastModified = blipDoc.getLastModifiedTime();
      String html = renderBlipDocToHtml(blipDoc);

      if (!html.isEmpty()) {
        result.add(new BlipInfo(author, lastModified, html, entry.depth));
      }
    }

    return result;
  }

  // =========================================================================
  // Manifest walking — extracts blip IDs in order with thread depth
  // =========================================================================

  /** A blip reference found in the manifest, with its nesting depth. */
  private static final class ManifestEntry {
    final String blipId;
    final int depth;

    ManifestEntry(String blipId, int depth) {
      this.blipId = blipId;
      this.depth = depth;
    }
  }

  /**
   * Walks the conversation manifest document to extract blip IDs in their
   * natural (tree) order. Thread depth starts at 0 for the root thread.
   */
  private static List<ManifestEntry> walkManifest(ReadableBlipData manifestDoc) {
    final List<ManifestEntry> entries = new ArrayList<>();
    // Track depth by counting open thread elements.
    // Start at -1 because the root <thread> inside <conversation> is depth 0.
    final int[] depth = {-1};
    // Element stack so we know which element is closing in elementEnd().
    final List<String> elementStack = new ArrayList<>();

    DocOp docOp = manifestDoc.getContent().asOperation();
    docOp.apply(InitializationCursorAdapter.adapt(new DocInitializationCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void elementEnd() {
        if (!elementStack.isEmpty()) {
          String closed = elementStack.remove(elementStack.size() - 1);
          if (DocumentConstants.THREAD.equals(closed)) {
            depth[0]--;
          }
        }
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
        elementStack.add(type);
        if (DocumentConstants.BLIP.equals(type)) {
          String blipId = attrs.get(DocumentConstants.BLIP_ID);
          if (blipId != null) {
            entries.add(new ManifestEntry(blipId, Math.max(0, depth[0])));
          }
        } else if (DocumentConstants.THREAD.equals(type)) {
          depth[0]++;
        }
      }
    }));

    return entries;
  }

  // =========================================================================
  // Blip document → HTML rendering
  // =========================================================================

  /**
   * Renders a single blip document's content to HTML. Handles:
   * <ul>
   *   <li>{@code <line/>} elements with {@code t} attribute for headings/lists</li>
   *   <li>{@code <line t="li">} for unordered lists, {@code <line t="li" listyle="decimal">}
   *       for ordered lists — consecutive list items are wrapped in {@code <ul>}/{@code <ol>}</li>
   *   <li>Annotation boundaries for bold, italic, and link formatting</li>
   *   <li>Proper text accumulation across split {@code characters()} calls</li>
   * </ul>
   */
  private static String renderBlipDocToHtml(ReadableBlipData blipDoc) {
    DocOp docOp = blipDoc.getContent().asOperation();
    final StringBuilder html = new StringBuilder();
    // Track current formatting annotations.
    final Map<String, String> activeAnnotations = new HashMap<>();
    // Track whether we are inside a body element.
    final boolean[] inBody = {false};
    // Track whether we have an open paragraph/heading/li tag.
    final String[] currentTag = {null};
    // Track open list wrapper: "ul", "ol", or null if not in a list.
    final String[] currentListTag = {null};
    // Track open formatting spans so we can close/reopen them at annotation boundaries.
    final boolean[] inBold = {false};
    final boolean[] inItalic = {false};
    final String[] linkUrl = {null};
    // Element stack to track nesting.
    final List<String> elementStack = new ArrayList<>();

    docOp.apply(InitializationCursorAdapter.adapt(new DocInitializationCursor() {

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        // Process annotation ends.
        for (int i = 0; i < map.endSize(); i++) {
          String key = map.getEndKey(i);
          activeAnnotations.remove(key);
        }

        // Process annotation changes.
        for (int i = 0; i < map.changeSize(); i++) {
          String key = map.getChangeKey(i);
          String newValue = map.getNewValue(i);
          if (newValue != null) {
            activeAnnotations.put(key, newValue);
          } else {
            activeAnnotations.remove(key);
          }
        }

        // Compute the new formatting state.
        boolean newBold = "bold".equals(activeAnnotations.get("style/fontWeight"));
        boolean newItalic = "italic".equals(activeAnnotations.get("style/fontStyle"));
        String newLink = null;
        if (activeAnnotations.containsKey("link/manual")) {
          newLink = activeAnnotations.get("link/manual");
        } else if (activeAnnotations.containsKey("link/auto")) {
          newLink = activeAnnotations.get("link/auto");
        } else if (activeAnnotations.containsKey("link/wave")) {
          newLink = activeAnnotations.get("link/wave");
        }

        // Only emit close/open tags if formatting actually changed and we're in body.
        boolean boldChanged = inBold[0] != newBold;
        boolean italicChanged = inItalic[0] != newItalic;
        boolean linkChanged = !strEquals(linkUrl[0], newLink);

        if (inBody[0] && (boldChanged || italicChanged || linkChanged)) {
          closeFormattingSpans(html, inBold, inItalic, linkUrl);
          inBold[0] = newBold;
          inItalic[0] = newItalic;
          linkUrl[0] = newLink;
          openFormattingSpans(html, inBold, inItalic, linkUrl);
        } else {
          inBold[0] = newBold;
          inItalic[0] = newItalic;
          linkUrl[0] = newLink;
        }
      }

      @Override
      public void characters(String chars) {
        if (!inBody[0]) {
          return;
        }
        // Ensure we are inside a paragraph-level tag.
        if (currentTag[0] == null) {
          currentTag[0] = "p";
          html.append("<p>");
        }
        // Escape and append the text. characters() can be split by
        // annotationBoundary() at any point, so we just append each chunk.
        html.append(HtmlRenderer.escapeHtml(chars));
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
        elementStack.add(type);

        if (DocumentConstants.BODY.equals(type)) {
          inBody[0] = true;
          return;
        }

        if (!inBody[0]) {
          return;
        }

        if (DocumentConstants.LINE.equals(type) || "l:p".equals(type)) {
          // Close any open formatting spans, then close the previous block.
          closeFormattingSpans(html, inBold, inItalic, linkUrl);
          if (currentTag[0] != null) {
            html.append("</").append(currentTag[0]).append(">");
          }

          // Determine line type and list style from attributes.
          String lineType = attrs != null ? attrs.get("t") : null;
          String listStyle = attrs != null ? attrs.get("listyle") : null;
          boolean isList = "li".equals(lineType);

          if (isList) {
            // Determine the list wrapper tag based on listyle attribute.
            String neededListTag = "decimal".equals(listStyle) ? "ol" : "ul";

            if (currentListTag[0] == null) {
              // Not in a list yet — open one.
              html.append("<").append(neededListTag).append(">");
              currentListTag[0] = neededListTag;
            } else if (!currentListTag[0].equals(neededListTag)) {
              // Switching list type (e.g., ul -> ol) — close old, open new.
              html.append("</").append(currentListTag[0]).append(">");
              html.append("<").append(neededListTag).append(">");
              currentListTag[0] = neededListTag;
            }
            // Emit <li> as the block tag.
            currentTag[0] = "li";
            html.append("<li>");
          } else {
            // Non-list line — close any open list wrapper first.
            closeListIfOpen(html, currentListTag);
            currentTag[0] = mapLineType(lineType);
            html.append("<").append(currentTag[0]).append(">");
          }

          // Reopen formatting spans in the new block.
          openFormattingSpans(html, inBold, inItalic, linkUrl);
          return;
        }

        // Skip reply anchors and head elements.
        if ("reply".equals(type) || "head".equals(type)) {
          return;
        }
      }

      @Override
      public void elementEnd() {
        if (elementStack.isEmpty()) {
          return;
        }
        String type = elementStack.remove(elementStack.size() - 1);

        if (DocumentConstants.BODY.equals(type)) {
          // Close any open formatting and block element.
          closeFormattingSpans(html, inBold, inItalic, linkUrl);
          if (currentTag[0] != null) {
            html.append("</").append(currentTag[0]).append(">");
            currentTag[0] = null;
          }
          // Close any open list wrapper.
          closeListIfOpen(html, currentListTag);
          inBody[0] = false;
        }
      }
    }));

    return html.toString();
  }

  /**
   * Closes an open list wrapper ({@code <ul>} or {@code <ol>}) if one is active.
   */
  private static void closeListIfOpen(StringBuilder html, String[] currentListTag) {
    if (currentListTag[0] != null) {
      html.append("</").append(currentListTag[0]).append(">");
      currentListTag[0] = null;
    }
  }

  /**
   * Maps a wave line {@code t} attribute value to an HTML tag name.
   * List items ({@code t="li"}) are handled separately in the caller.
   */
  private static String mapLineType(String lineType) {
    if (lineType == null) {
      return "p";
    }
    switch (lineType) {
      case "h1": return "h2"; // Downgrade: page h1 is the wave title
      case "h2": return "h3";
      case "h3": return "h4";
      case "h4": return "h5";
      default: return "p";
    }
  }

  /**
   * Closes any open inline formatting spans (link, italic, bold) in
   * reverse order of opening.
   */
  private static void closeFormattingSpans(StringBuilder html,
      boolean[] inBold, boolean[] inItalic, String[] linkUrl) {
    // Close in reverse order: bold, italic, link.
    if (inBold[0]) {
      html.append("</strong>");
    }
    if (inItalic[0]) {
      html.append("</em>");
    }
    if (linkUrl[0] != null) {
      html.append("</a>");
    }
  }

  /** Null-safe string equality check. */
  private static boolean strEquals(String a, String b) {
    if (a == null) return b == null;
    return a.equals(b);
  }

  /**
   * Opens inline formatting spans (link, italic, bold) in standard order.
   */
  private static void openFormattingSpans(StringBuilder html,
      boolean[] inBold, boolean[] inItalic, String[] linkUrl) {
    // Open in order: link, italic, bold.
    if (linkUrl[0] != null) {
      html.append("<a href=\"")
          .append(HtmlRenderer.escapeHtml(linkUrl[0]))
          .append("\" rel=\"nofollow noopener\" target=\"_blank\">");
    }
    if (inItalic[0]) {
      html.append("<em>");
    }
    if (inBold[0]) {
      html.append("<strong>");
    }
  }
}

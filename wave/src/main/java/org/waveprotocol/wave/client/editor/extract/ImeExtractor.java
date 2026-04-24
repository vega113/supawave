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

package org.waveprotocol.wave.client.editor.extract;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Text;

import org.waveprotocol.wave.client.common.util.DomHelper;
import org.waveprotocol.wave.client.common.util.QuirksConstants;
import org.waveprotocol.wave.client.common.util.UserAgent;
import org.waveprotocol.wave.client.editor.ElementHandlerRegistry;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.ContentTextNode;
import org.waveprotocol.wave.client.editor.content.Renderer;
import org.waveprotocol.wave.client.editor.content.paragraph.ParagraphHelper;
import org.waveprotocol.wave.client.editor.debug.ImeDebugTracer;
import org.waveprotocol.wave.client.editor.impl.NodeManager;
import org.waveprotocol.wave.client.editor.selection.html.NativeSelectionUtil;
import org.waveprotocol.wave.model.document.util.DocHelper;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.document.util.FilteredView.Skip;
import org.waveprotocol.wave.model.document.util.LocalDocument;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.util.GhostTextReconciler;
import org.waveprotocol.wave.model.util.ImeCompositionTextTracker;

import java.util.Collections;

/**
 * Controller for a little DOM object to contain the cursor during IME
 * composition, to protect it from concurrent mutations to the surrounding DOM.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public class ImeExtractor {

  private final SpanElement imeContainer = Document.get().createSpanElement();

  private final SpanElement imeInput;

  private Point.El<Node> inContainer;

  private ContentElement wrapper = null;

  /**
   * On real Android Chrome/Brave, the browser can freeze its composition
   * insertion anchor at {@code compositionstart} time, before
   * {@link #activate(DocumentContext, Point)} moves the DOM selection into
   * the scratch span. When that happens, the first character of the
   * composed word lands in the text node adjacent to the scratch instead of
   * inside it, and {@code imeContainer.getInnerText()} misses it. The
   * {@code ghost*} fields capture a snapshot of those adjacent text nodes
   * at {@code activate()} time so we can recover the lost characters in
   * {@link #getEffectiveContent()} before they are discarded at
   * {@link #deactivate(LocalDocument)}.
   */
  private Node ghostPreviousSibling;
  private String ghostPreviousSiblingModelBaseline;
  private String ghostPreviousSiblingCapturedText;
  private Node ghostNextSibling;
  private String ghostNextSiblingModelBaseline;
  private String ghostNextSiblingCapturedText;
  private final ImeCompositionTextTracker compositionTextTracker =
      new ImeCompositionTextTracker();
  private boolean suppressPreviousGhostForCurrentComposition;

  private static final String WRAPPER_TAGNAME = "l:ime";

  public static void register(ElementHandlerRegistry registry) {
    registry.registerRenderer(WRAPPER_TAGNAME, new Renderer() {
      @Override
      public Element createDomImpl(Renderable element) {
        return element.setAutoAppendContainer(Document.get().createSpanElement());
      }
    });
  }

  /***/
  public ImeExtractor() {
    NodeManager.setTransparency(imeContainer, Skip.DEEP);
    NodeManager.setMayContainSelectionEvenWhenDeep(imeContainer, true);
    if (QuirksConstants.SUPPORTS_CARET_IN_EMPTY_SPAN) {
      // For browsers that support putting the caret in an empty span,
      // we do just that (it's simpler).
      imeInput = imeContainer;
    } else {
      // For other browsers, we use inline block so we can reuse the
      // paragraph logic to keep the ime extractor span open (i.e. to
      // allow the cursor to live inside it when it contains no text).
      // see #clearContainer()
      imeContainer.getStyle().setDisplay(Display.INLINE_BLOCK);
      DomHelper.setContentEditable(imeContainer, false, false);

      imeInput = Document.get().createSpanElement();
      imeInput.getStyle().setDisplay(Display.INLINE_BLOCK);
      imeInput.getStyle().setProperty("outline", "0");
      DomHelper.setContentEditable(imeInput, true, false);
      NodeManager.setTransparency(imeInput, Skip.DEEP);
      NodeManager.setMayContainSelectionEvenWhenDeep(imeInput, true);

      imeContainer.appendChild(imeInput);
    }
    clearContainer();
  }

  /**
   * @return the current composition text if isActive(), null otherwise.
   */
  public String getContent() {
    return isActive() ? imeContainer.getInnerText() : null;
  }

  /**
   * Returns the effective composition text, combining the IME scratch span
   * contents with any ghost characters that the browser inserted into the
   * adjacent DOM text nodes instead of into the scratch.
   *
   * <p>This is the value that must be flushed into the document model at
   * {@code compositionEnd} — using {@link #getContent()} alone drops the
   * first character of every composed word on real Android Chrome/Brave,
   * because those browsers can freeze the composition insertion anchor at
   * {@code compositionstart} time, before the editor moves the DOM
   * selection into the scratch span.
   *
   * @return the merged composition text if active, {@code null} otherwise.
   */
  public String getEffectiveContent() {
    if (!isActive()) {
      if (ImeDebugTracer.isEnabled()) {
        ImeDebugTracer.start("ImeExtractor.getEffectiveContent")
            .add("active", false).emit();
      }
      return null;
    }
    String scratchContent = imeContainer.getInnerText();
    if (scratchContent == null) {
      scratchContent = "";
    }
    String currentPrev = readCurrentPreviousText();
    String currentNext = readCurrentNextText();
    String effectiveScratchContent = UserAgent.isAndroid()
        ? compositionTextTracker.effectiveText(scratchContent)
        : scratchContent;
    suppressPreviousGhostForCurrentComposition = UserAgent.isAndroid()
        && GhostTextReconciler.shouldSuppressPreviousGhostForRecoveredScratch(
            scratchContent, effectiveScratchContent, ghostPreviousSiblingModelBaseline,
            ghostPreviousSiblingCapturedText, currentPrev);
    String result = GhostTextReconciler.combineWithCapturedGhosts(effectiveScratchContent,
        suppressPreviousGhostForCurrentComposition ? null : ghostPreviousSiblingModelBaseline,
        suppressPreviousGhostForCurrentComposition ? null : ghostPreviousSiblingCapturedText,
        suppressPreviousGhostForCurrentComposition ? null : currentPrev,
        ghostNextSiblingModelBaseline, ghostNextSiblingCapturedText, currentNext);
    if (ImeDebugTracer.isEnabled()) {
      ImeDebugTracer.start("ImeExtractor.getEffectiveContent")
          .add("scratch", scratchContent)
          .add("effectiveScratch", effectiveScratchContent)
          .add("suppressPrevGhost", suppressPreviousGhostForCurrentComposition)
          .add("prevModelBaseline", ghostPreviousSiblingModelBaseline)
          .add("prevCaptured", ghostPreviousSiblingCapturedText)
          .add("currentPrev", currentPrev)
          .add("nextModelBaseline", ghostNextSiblingModelBaseline)
          .add("nextCaptured", ghostNextSiblingCapturedText)
          .add("currentNext", currentNext)
          .add("result", result)
          .emit();
    }
    return result;
  }

  /**
   * Activates the IME extractor at the given location.
   *
   * The extraction node will be put in place, and selection moved to it.
   *
   * @param cxt
   * @param location
   */
  public void activate(
      DocumentContext<ContentNode, ContentElement, ContentTextNode> cxt,
      Point<ContentNode> location) {

    LocalDocument<ContentNode, ContentElement, ContentTextNode> doc = cxt.annotatableContent();
    clearWrapper(doc);

    Point<ContentNode> point = DocHelper.ensureNodeBoundary(
        DocHelper.transparentSlice(location, cxt),
        doc, cxt.textNodeOrganiser());

    // NOTE(danilatos): Needed as a workaround to bug 2152316
    ContentElement container = point.getContainer().asElement();
    ContentNode nodeAfter = point.getNodeAfter();
    if (nodeAfter != null) {
      container = nodeAfter.getParentElement();
    }
    ////

    String previousModelBaseline = readModelText(previousContentSibling(doc, container, nodeAfter));
    String nextModelBaseline = readModelText(nodeAfter);

    wrapper = doc.transparentCreate(
        WRAPPER_TAGNAME, Collections.<String, String>emptyMap(),
        container, nodeAfter);

    wrapper.getContainerNodelet().appendChild(imeContainer);
    NativeSelectionUtil.setCaret(inContainer);
    captureGhostBaseline(previousModelBaseline, nextModelBaseline);
    compositionTextTracker.reset();
    suppressPreviousGhostForCurrentComposition = false;
    if (ImeDebugTracer.isEnabled()) {
      Element anchor = imeContainer.getParentElement();
      ImeDebugTracer.start("ImeExtractor.activate")
          .add("prevSibling", ImeDebugTracer.describe(ghostPreviousSibling))
          .add("prevModelBaseline", ghostPreviousSiblingModelBaseline)
          .add("prevCaptured", ghostPreviousSiblingCapturedText)
          .add("nextSibling", ImeDebugTracer.describe(ghostNextSibling))
          .add("nextModelBaseline", ghostNextSiblingModelBaseline)
          .add("nextCaptured", ghostNextSiblingCapturedText)
          .add("anchorParent", ImeDebugTracer.describe(anchor == null ? null : anchor.getParentElement()))
          .add("anchorInnerText", ImeDebugTracer.innerText(anchor == null ? null : anchor.getParentElement()))
          .emit();
    }
  }

  /** Records composition event text for browsers that rewrite the scratch span. */
  public void compositionUpdate(String compositionText) {
    if (!isActive()) {
      return;
    }
    compositionTextTracker.observe(compositionText);
    if (ImeDebugTracer.isEnabled()) {
      ImeDebugTracer.start("ImeExtractor.compositionUpdate")
          .add("data", compositionText)
          .add("scratch", imeContainer.getInnerText())
          .emit();
    }
  }

  /**
   * Records the contents of the DOM text nodes adjacent to the IME scratch
   * at the moment the scratch is created, so that {@link
   * #getEffectiveContent()} can later recover any composition characters the
   * browser steered into those siblings instead of the scratch.
   */
  private void captureGhostBaseline(String previousModelBaseline,
      String nextModelBaseline) {
    Element scratchDomAnchor = imeContainer.getParentElement();
    if (scratchDomAnchor == null) {
      ghostPreviousSibling = null;
      ghostPreviousSiblingModelBaseline = null;
      ghostPreviousSiblingCapturedText = null;
      ghostNextSibling = null;
      ghostNextSiblingModelBaseline = null;
      ghostNextSiblingCapturedText = null;
      return;
    }
    ghostPreviousSibling = scratchDomAnchor.getPreviousSibling();
    ghostPreviousSiblingModelBaseline = previousModelBaseline;
    ghostPreviousSiblingCapturedText = readText(ghostPreviousSibling);
    ghostNextSibling = scratchDomAnchor.getNextSibling();
    ghostNextSiblingModelBaseline = nextModelBaseline;
    ghostNextSiblingCapturedText = readText(ghostNextSibling);
  }

  /**
   * Removes the IME extractor node.
   * @param doc
   * @return the location where the node resided
   */
  public Point<ContentNode> deactivate(
      LocalDocument<ContentNode, ContentElement, ContentTextNode> doc) {
    if (ImeDebugTracer.isEnabled()) {
      ImeDebugTracer.start("ImeExtractor.deactivate")
          .add("scratch", imeContainer.getInnerText())
          .add("prevSibling", ImeDebugTracer.describe(ghostPreviousSibling))
          .add("prevCurrent", readCurrentPreviousText())
          .add("prevCapturedNodeCurrent", ImeDebugTracer.readText(ghostPreviousSibling))
          .add("prevModelBaseline", ghostPreviousSiblingModelBaseline)
          .add("prevCaptured", ghostPreviousSiblingCapturedText)
          .add("nextSibling", ImeDebugTracer.describe(ghostNextSibling))
          .add("nextCurrent", readCurrentNextText())
          .add("nextCapturedNodeCurrent", ImeDebugTracer.readText(ghostNextSibling))
          .add("nextModelBaseline", ghostNextSiblingModelBaseline)
          .add("nextCaptured", ghostNextSiblingCapturedText)
          .emit();
    }
    // Restore any ghost text back to its baseline BEFORE we tear down the
    // wrapper. The captured ghost characters are already accounted for in
    // the composition string that the caller obtained from
    // getEffectiveContent(); leaving them in the DOM would cause the
    // renderer to see them again after we insert the composition into the
    // content model, leading to double-insertion or stray leftovers.
    restoreGhostBaseline();
    Point.El<ContentNode> ret = Point.<ContentNode>inElement(
        doc.getParentElement(wrapper), doc.getNextSibling(wrapper));
    clearWrapper(doc);
    return ret;
  }

  private void restoreGhostBaseline() {
    Node currentPreviousSibling = currentAdjacentSibling(true);
    Node currentNextSibling = currentAdjacentSibling(false);
    if (!suppressPreviousGhostForCurrentComposition) {
      restorePreviousTextNode(currentPreviousSibling, ghostPreviousSiblingModelBaseline);
    }
    restoreNextTextNode(currentNextSibling, ghostNextSiblingModelBaseline);
    if (!suppressPreviousGhostForCurrentComposition
        && currentPreviousSibling != ghostPreviousSibling) {
      restorePreviousTextNode(ghostPreviousSibling, ghostPreviousSiblingModelBaseline);
    }
    if (currentNextSibling != ghostNextSibling) {
      restoreNextTextNode(ghostNextSibling, ghostNextSiblingModelBaseline);
    }
    ghostPreviousSibling = null;
    ghostPreviousSiblingModelBaseline = null;
    ghostPreviousSiblingCapturedText = null;
    ghostNextSibling = null;
    ghostNextSiblingModelBaseline = null;
    ghostNextSiblingCapturedText = null;
    suppressPreviousGhostForCurrentComposition = false;
    compositionTextTracker.reset();
  }

  private String readCurrentPreviousText() {
    return readText(currentAdjacentSibling(true));
  }

  private String readCurrentNextText() {
    return readText(currentAdjacentSibling(false));
  }

  private Node currentAdjacentSibling(boolean previous) {
    Element scratchDomAnchor = imeContainer.getParentElement();
    if (scratchDomAnchor == null) {
      return null;
    }
    return previous ? scratchDomAnchor.getPreviousSibling() : scratchDomAnchor.getNextSibling();
  }

  private static void restorePreviousTextNode(Node node, String baseline) {
    restoreTextNode(node, GhostTextReconciler.restorePreviousSiblingText(
        baseline, readText(node)));
  }

  private static void restoreNextTextNode(Node node, String baseline) {
    restoreTextNode(node, GhostTextReconciler.restoreNextSiblingText(
        baseline, readText(node)));
  }

  private static void restoreTextNode(Node node, String restoredValue) {
    if (node == null || restoredValue == null) {
      return;
    }
    if (node.getNodeType() != Node.TEXT_NODE) {
      return;
    }
    Text text = Text.as(node);
    if (!restoredValue.equals(text.getData())) {
      text.setData(restoredValue);
    }
  }

  private static String readText(Node node) {
    if (node == null) {
      return null;
    }
    if (node.getNodeType() != Node.TEXT_NODE) {
      return null;
    }
    return Text.as(node).getData();
  }

  private static ContentNode previousContentSibling(
      LocalDocument<ContentNode, ContentElement, ContentTextNode> doc,
      ContentElement container, ContentNode nodeAfter) {
    return nodeAfter == null
        ? doc.getLastChild(container)
        : doc.getPreviousSibling(nodeAfter);
  }

  private static String readModelText(ContentNode node) {
    if (node == null) {
      return "";
    }
    ContentTextNode text = node.asText();
    return text == null ? "" : text.getData();
  }

  /**
   * @return whether the extractor is actively containing composition events
   */
  public boolean isActive() {
    return wrapper != null;
  }

  /** Sets the composition text for testing only. */
  public void setContentForTest(String text) {
    imeInput.setInnerText(text);
  }

  private void clearWrapper(LocalDocument<ContentNode, ContentElement, ContentTextNode> doc) {
    if (wrapper != null && wrapper.getParentElement() != null) {
      doc.transparentDeepRemove(wrapper);
    }
    wrapper = null;
    clearContainer();
  }

  private void clearContainer() {
    imeInput.setInnerHTML("");
    if (!QuirksConstants.SUPPORTS_CARET_IN_EMPTY_SPAN) {
      ParagraphHelper.INSTANCE.onEmpty(imeInput);
    }
    inContainer = Point.<Node>inElement(imeInput, imeInput.getFirstChild());
  }

}

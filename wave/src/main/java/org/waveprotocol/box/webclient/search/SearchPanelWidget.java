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

package org.waveprotocol.box.webclient.search;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;

import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.wave.client.common.util.LinkedSequence;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;
import org.waveprotocol.wave.client.wavepanel.impl.collapse.MobileDetector;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.StringMap;

/**
 * View interface for the search panel.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public class SearchPanelWidget extends Composite implements SearchPanelView {

  /** Resources used by this widget. */
  interface Resources extends ClientBundle {
    @Source("images/toolbar_empty.png")
    @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
    ImageResource emptyToolbar();

    /** CSS */
    @Source("SearchPanel.css")
    Css css();
  }

  interface Css extends CssResource {
    String self();

    String search();

    String toolbar();

    String waveCount();

    String list();

    String showMore();

    String mentionBadge();
  }

  /**
   * Positioning constants for components of this panel.
   */
  static class CssConstants {
    private static int SEARCH_HEIGHT_PX = 51; // To match wave panel.
    // Keep the search panel reserve in sync with the 36px shared toolbar row.
    // The legacy emptyToolbar sprite is still 24px tall and is no longer the
    // correct layout authority for the rendered toolbar height.
    private static int TOOLBAR_HEIGHT_PX = 36;
    private static int TOOLBAR_TOP_PX = 0 + SEARCH_HEIGHT_PX;
    /** Height of the wave count info bar (24px content + 1px border). */
    private static int WAVE_COUNT_HEIGHT_PX = 25;
    private static int WAVE_COUNT_TOP_PX = TOOLBAR_TOP_PX + TOOLBAR_HEIGHT_PX;
    private static int LIST_TOP_PX = WAVE_COUNT_TOP_PX + WAVE_COUNT_HEIGHT_PX;

    // CSS constants exported to .css files
    static String SEARCH_HEIGHT = SEARCH_HEIGHT_PX + "px";
    static String TOOLBAR_TOP = TOOLBAR_TOP_PX + "px";
    static String WAVE_COUNT_TOP = WAVE_COUNT_TOP_PX + "px";
    static String LIST_TOP = LIST_TOP_PX + "px";
  }

  @UiField(provided = true)
  static Css css = SearchPanelResourceLoader.getPanel().css();

  interface Binder extends UiBinder<FramedPanel, SearchPanelWidget> {
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  private final FramedPanel frame;
  @UiField
  SearchWidget search;
  @UiField
  ToplevelToolbarWidget toolbar;
  @UiField
  Element waveCount;
  @UiField
  Element list;
  @UiField
  Element showMore;
  @UiField
  ImplPanel self;
  private final LinkedSequence<DigestDomImpl> digests = LinkedSequence.create();
  private final StringMap<DigestDomImpl> byId = CollectionUtils.createStringMap();
  private final SearchPanelRenderer renderer;
  private final Pool<DigestDomImpl> digestPool =
      ToppingUpPool.create(new ToppingUpPool.Factory<DigestDomImpl>() {
        @Override
        public DigestDomImpl create() {
          return new DigestDomImpl(SearchPanelWidget.this);
        }
      }, 20);
  private Listener listener;

  /** Whether an infinite-scroll load-more request is in progress. */
  private boolean isLoadingMore = false;

  /** The spinner element shown at the bottom while loading more results. */
  private Element loadingSpinner;

  /** Distance from the bottom of the scroll area to trigger loading more (px).
   *  Desktop uses 200px; mobile uses 300px (trigger earlier since momentum
   *  scrolling covers distance faster on touch devices). */
  private static final int DESKTOP_SCROLL_THRESHOLD_PX = 200;
  private static final int MOBILE_SCROLL_THRESHOLD_PX = 300;
  private static final int LOADING_SKELETON_ROWS = 6;

  public SearchPanelWidget(SearchPanelRenderer renderer) {
    initWidget(frame = BINDER.createAndBindUi(this));
    toolbar.setOverflowEnabled(false);
    this.renderer = renderer;
    createLoadingSpinner();
  }

  /**
   * Creates a small CSS spinner element to show at the bottom of the list
   * while loading more results via infinite scroll.
   */
  private void createLoadingSpinner() {
    loadingSpinner = Document.get().createDivElement();
    loadingSpinner.setAttribute("style",
        "display:none;text-align:center;padding:12px 0;"
        + "margin-bottom:20%");
    loadingSpinner.setInnerHTML(
        "<div style=\"display:inline-block;width:20px;height:20px;"
        + "border:2px solid #e2e8f0;border-top-color:#0077b6;"
        + "border-radius:50%;animation:wave-inf-spin 0.6s linear infinite\"></div>");
  }

  /**
   * Attaches the scroll listener to the list element. Must be called after the
   * widget is attached and the list element is live in the DOM.
   */
  private native void attachScrollListener(Element listEl) /*-{
    var self = this;
    listEl.addEventListener('scroll', function() {
      self.@org.waveprotocol.box.webclient.search.SearchPanelWidget::onListScroll()();
    });
  }-*/;

  /**
   * Called when the list element is scrolled. Checks if the user is near the
   * bottom and triggers loading more results if needed.
   */
  private void onListScroll() {
    if (isLoadingMore || listener == null) {
      return;
    }
    int scrollTop = list.getScrollTop();
    int scrollHeight = list.getScrollHeight();
    int clientHeight = list.getClientHeight();
    int threshold = MobileDetector.isMobile()
        ? MOBILE_SCROLL_THRESHOLD_PX : DESKTOP_SCROLL_THRESHOLD_PX;
    if (scrollTop + clientHeight >= scrollHeight - threshold) {
      // Only trigger if more results are available. The showMore element's
      // visibility is set to "hidden" when there are no more results, and
      // cleared (visible) when more results exist.
      String vis = showMore.getStyle().getVisibility();
      boolean moreAvailable = vis == null || vis.isEmpty() || !vis.equals("hidden");
      if (moreAvailable) {
        isLoadingMore = true;
        showLoadingSpinner(true);
        listener.onShowMoreClicked();
      }
    }
  }

  /**
   * Shows or hides the loading spinner at the bottom of the list.
   */
  private void showLoadingSpinner(boolean show) {
    if (show) {
      loadingSpinner.getStyle().setProperty("display", "block");
    } else {
      loadingSpinner.getStyle().setProperty("display", "none");
    }
  }

  /**
   * Called by the presenter after more results have been loaded (or when there
   * are no more results). Resets the loading state so infinite scroll can
   * trigger again.
   */
  public void onLoadMoreComplete() {
    isLoadingMore = false;
    showLoadingSpinner(false);
  }

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null, "this.listener == null");
    Preconditions.checkArgument(listener != null, "listener != null");
    this.listener = listener;
    // Attach the scroll listener for infinite scroll
    attachScrollListener(list);
    // Insert the loading spinner before the showMore element
    list.insertBefore(loadingSpinner, showMore);
  }

  @Override
  public void reset() {
    Preconditions.checkState(listener != null, "listener != null");
    listener = null;
  }

  void onDigestRemoved(DigestDomImpl digestUi) {
    digests.remove(digestUi);
    byId.remove(digestUi.getId());

    // Restore blank state and recycle.
    digestUi.reset();
    digestPool.recycle(digestUi);
  }

  @Override
  public void setTitleText(String text) {
    frame.setTitleText(text);
  }

  @Override
  public void setWaveCountText(String text) {
    if (text == null || text.isEmpty()) {
      waveCount.setInnerText("");
      waveCount.getStyle().setProperty("display", "none");
    } else {
      waveCount.setInnerText(text);
      waveCount.getStyle().clearProperty("display");
    }
  }

  @Override
  public SearchWidget getSearch() {
    return search;
  }

  public ToplevelToolbarWidget getToolbar() {
    return toolbar;
  }

  @Override
  public com.google.gwt.dom.client.Element getPanelRoot() {
    return self.getElement();
  }

  @Override
  public DigestDomImpl getFirst() {
    return digests.getFirst();
  }

  @Override
  public DigestDomImpl getLast() {
    return digests.getLast();
  }

  @Override
  public DigestDomImpl getNext(DigestView ref) {
    return digests.getNext(narrow(ref));
  }

  @Override
  public DigestDomImpl getPrevious(DigestView ref) {
    return digests.getPrevious(narrow(ref));
  }

  @Override
  public DigestDomImpl insertBefore(DigestView ref, Digest digest) {
    DigestDomImpl digestUi = digestPool.get();
    renderer.render(digest, digestUi);

    DigestDomImpl refDomImpl = narrow(ref);
    // Insert before the loading spinner (which sits before showMore) when
    // appending at the end, so digests always appear above the spinner.
    // Fall back to showMore if the spinner hasn't been inserted yet.
    Element sentinel = loadingSpinner.getParentElement() != null ? loadingSpinner : showMore;
    Element refElement = refDomImpl != null ? refDomImpl.getElement() : sentinel;
    byId.put(digestUi.getId(), digestUi);
    digests.insertBefore(refDomImpl, digestUi);
    list.insertBefore(digestUi.getElement(), refElement);

    return digestUi;
  }

  @Override
  public DigestDomImpl insertAfter(DigestView ref, Digest digest) {
    DigestDomImpl digestUi = digestPool.get();
    renderer.render(digest, digestUi);

    DigestDomImpl refDomImpl = narrow(ref);
    Element sentinel = loadingSpinner.getParentElement() != null ? loadingSpinner : showMore;
    Element refElement = refDomImpl != null ? refDomImpl.getElement() : sentinel;
    byId.put(digestUi.getId(), digestUi);
    if (refDomImpl != null) {
      digests.insertAfter(refDomImpl, digestUi);
      list.insertAfter(digestUi.getElement(), refElement);
    } else {
      digests.insertBefore(null, digestUi);
      list.insertBefore(digestUi.getElement(), sentinel);
    }
    return digestUi;
  }

  @Override
  public void renderDigest(DigestView digestUi, Digest digest) {
    renderer.render(digest, digestUi);
  }

  @Override
  public void clearDigests() {
    removeSkeletonLoader();
    resetLoadingState();
    clearDigestViews();
  }

  @Override
  public void showLoadingSkeleton() {
    resetLoadingState();
    clearDigestViews();
    removeExistingLoadingSkeleton();
    Element skeleton = Document.get().createDivElement();
    skeleton.setId("wave-list-skeleton");
    skeleton.setInnerHTML(buildLoadingSkeletonHtml());
    Element sentinel = loadingSpinner.getParentElement() != null ? loadingSpinner : showMore;
    list.insertBefore(skeleton, sentinel);
  }

  private void removeExistingLoadingSkeleton() {
    Element skeleton = Document.get().getElementById("wave-list-skeleton");
    if (skeleton != null) {
      skeleton.removeFromParent();
    }
  }

  private void resetLoadingState() {
    isLoadingMore = false;
    showLoadingSpinner(false);
  }

  private void clearDigestViews() {
    while (!digests.isEmpty()) {
      digests.getFirst().remove();
    }
    assert digests.isEmpty();
  }

  private static String buildLoadingSkeletonHtml() {
    StringBuilder html = new StringBuilder();
    for (int i = 0; i < LOADING_SKELETON_ROWS; i++) {
      html.append("<div class=\"skel-digest\">")
          .append("<div class=\"skel-avatar\"></div>")
          .append("<div class=\"skel-lines\">")
          .append("<div class=\"skel-line title\"></div>")
          .append("<div class=\"skel-line snippet\"></div>")
          .append("</div>")
          .append("<div class=\"skel-time\"></div>")
          .append("</div>");
    }
    return html.toString();
  }

  /**
   * Removes the server-rendered skeleton loading placeholder from the DOM
   * with a smooth fade-out transition. The skeleton is shown instantly in
   * the HTML page before GWT initializes to give users immediate visual
   * feedback. Once the real search results are ready, this method fades it
   * out and then removes it from the DOM.
   */
  private static native void removeSkeletonLoader() /*-{
    var skel = $doc.getElementById('wave-list-skeleton');
    if (skel && skel.parentNode) {
      skel.classList.add('fade-out');
      var parent = skel.parentNode;
      $wnd.setTimeout(function() {
        if (skel.parentNode) {
          parent.removeChild(skel);
        }
      }, 250);
    }
  }-*/;

  @Override
  public void setShowMoreVisible(boolean visible) {
    // The showMore element is kept in the DOM for layout padding purposes,
    // but the text is hidden. Infinite scroll handles loading more results.
    // We track the visibility state so the scroll listener knows whether
    // more results are available.
    if (visible) {
      showMore.getStyle().clearVisibility();
    } else {
      showMore.getStyle().setVisibility(Visibility.HIDDEN);
    }
    // Hide the text content -- infinite scroll replaces the click interaction
    showMore.setInnerText("");
  }

  @UiHandler("self")
  void handleClick(ClickEvent e) {
    Element target = e.getNativeEvent().getEventTarget().cast();
    Element top = self.getElement();
    while (!top.equals(target)) {
      if ("digest".equals(target.getAttribute(BuilderHelper.KIND_ATTRIBUTE))) {
        handleClick(byId.get(target.getAttribute(DigestDomImpl.DIGEST_ID_ATTRIBUTE)));
        e.stopPropagation();
        return;
      }
      target = target.getParentElement();
    }
  }

  private void handleClick(DigestDomImpl digestUi) {
    if (digestUi == null) {
      // Error - there's an element in the DOM that looks like a digest, but
      // it's not in the digest map.
      // TODO(hearnden): log.
    } else {
      if (listener != null) {
        listener.onClicked(digestUi);
      }
    }
  }

  private static DigestDomImpl narrow(DigestView digestUi) {
    return (DigestDomImpl) digestUi;
  }
}

package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class J2clSearchPanelView implements J2clSearchPanelController.View {
  private final HTMLElement host;
  private final HTMLElement sessionSummary;
  private final HTMLElement status;
  private final HTMLElement waveCount;
  private final HTMLInputElement queryInput;
  private final HTMLButtonElement submitButton;
  private final HTMLDivElement digestList;
  private final HTMLElement emptyState;
  private final HTMLButtonElement showMoreButton;
  private final Map<String, J2clDigestView> digestViews = new LinkedHashMap<String, J2clDigestView>();
  private J2clSearchViewListener listener;

  public J2clSearchPanelView(HTMLElement host) {
    this.host = host;
    host.innerHTML = "";

    HTMLElement shell = (HTMLElement) DomGlobal.document.createElement("section");
    shell.className = "sidecar-search-shell";
    host.appendChild(shell);

    HTMLElement card = (HTMLElement) DomGlobal.document.createElement("div");
    card.className = "sidecar-search-card";
    shell.appendChild(card);

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Isolated J2CL search slice";
    card.appendChild(eyebrow);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("h1");
    title.className = "sidecar-title";
    title.textContent = "First real search/results vertical slice";
    card.appendChild(title);

    HTMLElement detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-detail";
    detail.textContent =
        "The root / route still runs the legacy GWT app. This sidecar route only mounts the first J2CL search panel slice.";
    card.appendChild(detail);

    sessionSummary = (HTMLElement) DomGlobal.document.createElement("p");
    sessionSummary.className = "sidecar-search-session";
    sessionSummary.textContent = "Inspecting the active root session.";
    card.appendChild(sessionSummary);

    HTMLFormElement form = (HTMLFormElement) DomGlobal.document.createElement("form");
    form.className = "sidecar-search-toolbar";
    card.appendChild(form);

    queryInput = (HTMLInputElement) DomGlobal.document.createElement("input");
    queryInput.className = "sidecar-search-input";
    queryInput.type = "search";
    queryInput.placeholder = "Search waves";
    queryInput.autocomplete = "off";
    form.appendChild(queryInput);

    submitButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    submitButton.className = "sidecar-search-submit";
    submitButton.type = "submit";
    submitButton.textContent = "Search";
    form.appendChild(submitButton);

    form.onsubmit =
        event -> {
          event.preventDefault();
          if (listener != null) {
            listener.onQuerySubmitted(queryInput.value);
          }
          return null;
        };

    status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-search-status";
    status.textContent = "Waiting for the first sidecar search response.";
    card.appendChild(status);

    waveCount = (HTMLElement) DomGlobal.document.createElement("p");
    waveCount.className = "sidecar-wave-count";
    card.appendChild(waveCount);

    digestList = (HTMLDivElement) DomGlobal.document.createElement("div");
    digestList.className = "sidecar-digests";
    card.appendChild(digestList);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    emptyState.textContent = "Search results will appear here.";
    card.appendChild(emptyState);

    showMoreButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    showMoreButton.className = "sidecar-show-more";
    showMoreButton.type = "button";
    showMoreButton.textContent = "Show more waves";
    showMoreButton.hidden = true;
    showMoreButton.onclick =
        event -> {
          if (listener != null) {
            listener.onShowMoreRequested();
          }
          return null;
        };
    card.appendChild(showMoreButton);
  }

  @Override
  public void bind(J2clSearchViewListener listener) {
    this.listener = listener;
  }

  @Override
  public void setQuery(String query) {
    if (query == null) {
      queryInput.value = "";
      return;
    }
    queryInput.value = query;
  }

  @Override
  public void setLoading(boolean loading) {
    queryInput.disabled = loading;
    submitButton.disabled = loading;
    showMoreButton.disabled = loading;
    host.className = loading ? "sidecar-root sidecar-root-loading" : "sidecar-root";
  }

  @Override
  public void setSessionSummary(String summary) {
    sessionSummary.textContent = summary;
  }

  @Override
  public void setStatus(String statusText, boolean error) {
    status.className = error ? "sidecar-search-status sidecar-search-status-error" : "sidecar-search-status";
    status.textContent = statusText;
  }

  @Override
  public void render(J2clSearchResultModel model) {
    waveCount.textContent = model.getWaveCountText();
    digestList.innerHTML = "";
    digestViews.clear();

    if (model.isEmpty()) {
      emptyState.hidden = false;
      emptyState.textContent = model.getEmptyMessage().isEmpty()
          ? "No waves matched this query."
          : model.getEmptyMessage();
    } else {
      emptyState.hidden = true;
      for (J2clSearchDigestItem item : model.getDigestItems()) {
        J2clDigestView digestView =
            new J2clDigestView(
                item,
                waveId -> {
                  if (listener != null) {
                    listener.onDigestSelected(waveId);
                  }
                });
        digestViews.put(item.getWaveId(), digestView);
        digestList.appendChild(digestView.element());
      }
    }

    showMoreButton.hidden = !model.isShowMoreVisible();
  }

  @Override
  public void setSelectedWaveId(String waveId) {
    for (Map.Entry<String, J2clDigestView> entry : digestViews.entrySet()) {
      entry.getValue().setSelected(entry.getKey().equals(waveId));
    }
  }
}

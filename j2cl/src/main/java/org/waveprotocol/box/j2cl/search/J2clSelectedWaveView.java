package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;

public final class J2clSelectedWaveView implements J2clSelectedWaveController.View {
  private final HTMLElement title;
  private final HTMLElement unread;
  private final HTMLElement status;
  private final HTMLElement detail;
  private final HTMLElement participantSummary;
  private final HTMLElement snippet;
  private final HTMLDivElement contentList;
  private final HTMLElement emptyState;

  public J2clSelectedWaveView(HTMLElement host) {
    host.innerHTML = "";

    HTMLElement card = (HTMLElement) DomGlobal.document.createElement("section");
    card.className = "sidecar-selected-card";
    host.appendChild(card);

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Read-only selected wave";
    card.appendChild(eyebrow);

    title = (HTMLElement) DomGlobal.document.createElement("h2");
    title.className = "sidecar-selected-title";
    card.appendChild(title);

    unread = (HTMLElement) DomGlobal.document.createElement("p");
    unread.className = "sidecar-selected-unread";
    card.appendChild(unread);

    status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-selected-status";
    card.appendChild(status);

    detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-selected-detail";
    card.appendChild(detail);

    participantSummary = (HTMLElement) DomGlobal.document.createElement("p");
    participantSummary.className = "sidecar-selected-participants";
    card.appendChild(participantSummary);

    snippet = (HTMLElement) DomGlobal.document.createElement("p");
    snippet.className = "sidecar-selected-snippet";
    card.appendChild(snippet);

    contentList = (HTMLDivElement) DomGlobal.document.createElement("div");
    contentList.className = "sidecar-selected-content";
    card.appendChild(contentList);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    card.appendChild(emptyState);
  }

  @Override
  public void render(J2clSelectedWaveModel model) {
    title.textContent = model.getTitleText();
    unread.textContent = model.getUnreadText();
    unread.hidden = model.getUnreadText().isEmpty();
    status.className =
        model.isError()
            ? "sidecar-selected-status sidecar-selected-status-error"
            : "sidecar-selected-status";
    status.textContent = model.getStatusText();
    detail.textContent = model.getDetailText();
    participantSummary.textContent =
        model.getParticipantIds().isEmpty()
            ? ""
            : "Participants: " + String.join(", ", model.getParticipantIds());
    participantSummary.hidden = model.getParticipantIds().isEmpty();
    snippet.textContent = model.getSnippetText();
    snippet.hidden = model.getSnippetText().isEmpty();

    contentList.innerHTML = "";
    for (String entry : model.getContentEntries()) {
      HTMLElement block = (HTMLElement) DomGlobal.document.createElement("pre");
      block.className = "sidecar-selected-entry";
      block.textContent = entry;
      contentList.appendChild(block);
    }

    emptyState.hidden = model.hasSelection() && !model.getContentEntries().isEmpty();
    emptyState.textContent =
        model.hasSelection()
            ? (model.isLoading()
                ? "Waiting for selected-wave content."
                : "No selected-wave content is available yet.")
            : model.getStatusText();
  }
}

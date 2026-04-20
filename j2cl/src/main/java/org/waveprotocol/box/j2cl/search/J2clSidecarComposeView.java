package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLTextAreaElement;

public final class J2clSidecarComposeView implements J2clSidecarComposeController.View {
  private final HTMLTextAreaElement createInput;
  private final HTMLButtonElement createButton;
  private final HTMLElement createStatus;
  private final HTMLDivElement replySection;
  private final HTMLElement replyHint;
  private final HTMLTextAreaElement replyInput;
  private final HTMLButtonElement replyButton;
  private final HTMLElement replyStatus;
  private J2clSidecarComposeController.Listener listener;

  public J2clSidecarComposeView(HTMLElement createHost, HTMLElement replyHost) {
    this(createHost, replyHost, J2clSearchPanelView.ShellPresentation.SIDE_CAR);
  }

  public J2clSidecarComposeView(
      HTMLElement createHost,
      HTMLElement replyHost,
      J2clSearchPanelView.ShellPresentation shellPresentation) {
    createHost.innerHTML = "";
    replyHost.innerHTML = "";

    HTMLElement createSection = (HTMLElement) DomGlobal.document.createElement("section");
    createSection.className = "sidecar-compose-block";
    createHost.appendChild(createSection);

    HTMLElement createTitle = (HTMLElement) DomGlobal.document.createElement("h2");
    createTitle.className = "sidecar-compose-title";
    createTitle.textContent = "New wave";
    createSection.appendChild(createTitle);

    HTMLElement createDetail = (HTMLElement) DomGlobal.document.createElement("p");
    createDetail.className = "sidecar-compose-detail";
    createDetail.textContent =
        shellPresentation == J2clSearchPanelView.ShellPresentation.ROOT_SHELL
            ? "Create a self-owned plain-text wave inside the root shell."
            : "Create a self-owned plain-text wave without leaving this sidecar.";
    createSection.appendChild(createDetail);

    HTMLFormElement createForm = (HTMLFormElement) DomGlobal.document.createElement("form");
    createForm.className = "sidecar-compose-form";
    createSection.appendChild(createForm);

    createInput = (HTMLTextAreaElement) DomGlobal.document.createElement("textarea");
    createInput.className = "sidecar-compose-textarea";
    createInput.placeholder = "Start a new wave";
    createInput.setAttribute("aria-label", "New wave content");
    createInput.rows = 4;
    createInput.oninput =
        event -> {
          if (listener != null) {
            listener.onCreateDraftChanged(createInput.value);
          }
          return null;
        };
    createForm.appendChild(createInput);

    createButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    createButton.className = "sidecar-compose-submit";
    createButton.type = "submit";
    createButton.textContent = "Create wave";
    createForm.appendChild(createButton);

    createStatus = (HTMLElement) DomGlobal.document.createElement("p");
    createStatus.className = "sidecar-compose-status";
    createSection.appendChild(createStatus);

    createForm.onsubmit =
        event -> {
          event.preventDefault();
          if (listener != null) {
            listener.onCreateSubmitted(createInput.value);
          }
          return null;
        };

    replySection = (HTMLDivElement) DomGlobal.document.createElement("div");
    replySection.className = "sidecar-compose-block";
    replyHost.appendChild(replySection);

    HTMLElement replyTitle = (HTMLElement) DomGlobal.document.createElement("h3");
    replyTitle.className = "sidecar-compose-title sidecar-compose-title-secondary";
    replyTitle.textContent = "Reply";
    replySection.appendChild(replyTitle);

    replyHint = (HTMLElement) DomGlobal.document.createElement("p");
    replyHint.className = "sidecar-compose-detail";
    replySection.appendChild(replyHint);

    HTMLFormElement replyForm = (HTMLFormElement) DomGlobal.document.createElement("form");
    replyForm.className = "sidecar-compose-form";
    replySection.appendChild(replyForm);

    replyInput = (HTMLTextAreaElement) DomGlobal.document.createElement("textarea");
    replyInput.className = "sidecar-compose-textarea";
    replyInput.placeholder = "Reply in the opened wave";
    replyInput.setAttribute("aria-label", "Reply");
    replyInput.rows = 3;
    replyInput.oninput =
        event -> {
          if (listener != null) {
            listener.onReplyDraftChanged(replyInput.value);
          }
          return null;
        };
    replyForm.appendChild(replyInput);

    replyButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    replyButton.className = "sidecar-compose-submit";
    replyButton.type = "submit";
    replyButton.textContent = "Send reply";
    replyForm.appendChild(replyButton);

    replyStatus = (HTMLElement) DomGlobal.document.createElement("p");
    replyStatus.className = "sidecar-compose-status";
    replySection.appendChild(replyStatus);

    replyForm.onsubmit =
        event -> {
          event.preventDefault();
          if (listener != null) {
            listener.onReplySubmitted(replyInput.value);
          }
          return null;
        };
  }

  @Override
  public void bind(J2clSidecarComposeController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clSidecarComposeModel model) {
    createInput.value = model.getCreateDraft();
    createInput.disabled = model.isCreateSubmitting();
    createButton.disabled = model.isCreateSubmitting();
    setStatus(createStatus, model.getCreateStatusText(), model.getCreateErrorText());

    replySection.hidden = !model.isReplyAvailable();
    replyHint.textContent = model.getReplyHintText();
    replyInput.value = model.getReplyDraft();
    replyInput.disabled = model.isReplySubmitting();
    replyButton.disabled = model.isReplySubmitting();
    setStatus(replyStatus, model.getReplyStatusText(), model.getReplyErrorText());
  }

  private static void setStatus(HTMLElement element, String statusText, String errorText) {
    boolean error = errorText != null && !errorText.isEmpty();
    element.className =
        error ? "sidecar-compose-status sidecar-compose-status-error" : "sidecar-compose-status";
    element.textContent = error ? errorText : statusText;
    element.hidden = element.textContent == null || element.textContent.isEmpty();
  }
}

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/index.js";

describe("<compose-attachment-picker>", () => {
  it("is registered by the Lit shell bundle", () => {
    expect(customElements.get("compose-attachment-picker")).to.be.a("function");
  });

  it("opens and closes as a modal picker while restoring focus to the trigger", async () => {
    const el = await fixture(html`<compose-attachment-picker></compose-attachment-picker>`);
    const trigger = el.renderRoot.querySelector("[data-action='attachment-insert']");

    const openEvent = oneEvent(el, "attachment-picker-open");
    trigger.click();
    expect((await openEvent).detail.action).to.equal("attachment-insert");
    await el.updateComplete;

    const overlay = el.renderRoot.querySelector("interaction-overlay-layer");
    expect(trigger.getAttribute("aria-expanded")).to.equal("true");
    expect(overlay.open).to.equal(true);
    expect(overlay.modal).to.equal(true);

    const closeEvent = oneEvent(el, "attachment-picker-close");
    overlay.dispatchEvent(new CustomEvent("overlay-close", {
      detail: { reason: "escape", focusTargetId: "attachment-picker-trigger" },
      bubbles: true,
      composed: true
    }));
    expect((await closeEvent).detail.reason).to.equal("escape");
    await el.updateComplete;

    expect(el.open).to.equal(false);
    expect(trigger).to.equal(el.shadowRoot.activeElement);
  });

  it("does not re-emit open when the picker is already open", async () => {
    const el = await fixture(html`<compose-attachment-picker></compose-attachment-picker>`);
    const trigger = el.renderRoot.querySelector("[data-action='attachment-insert']");
    let openEvents = 0;
    el.addEventListener("attachment-picker-open", () => {
      openEvents += 1;
    });

    trigger.click();
    trigger.click();

    expect(openEvents).to.equal(1);
  });

  it("closes from Cancel with a stable cancel reason", async () => {
    const el = await fixture(html`<compose-attachment-picker open></compose-attachment-picker>`);
    const cancel = el.renderRoot.querySelector("[data-action='attachment-cancel']");

    const closeEvent = oneEvent(el, "attachment-picker-close");
    cancel.click();
    expect((await closeEvent).detail.reason).to.equal("cancel");
    await el.updateComplete;

    expect(el.open).to.equal(false);
    expect(el.renderRoot.querySelector("[data-action='attachment-insert']")).to.equal(
      el.shadowRoot.activeElement
    );
  });

  it("emits selected files with caption and display size", async () => {
    const el = await fixture(html`<compose-attachment-picker open></compose-attachment-picker>`);
    const caption = el.renderRoot.querySelector("[name='attachment-caption']");
    const large = el.renderRoot.querySelector("[value='large']");
    const fileInput = el.renderRoot.querySelector("input[type='file']");
    const image = new File(["png"], "diagram.png", { type: "image/png" });

    caption.value = "Architecture diagram";
    caption.dispatchEvent(new Event("input", { bubbles: true, composed: true }));

    const captionEvent = oneEvent(el, "attachment-caption");
    caption.value = "Updated caption";
    caption.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
    expect((await captionEvent).detail).to.deep.equal({
      action: "attachment-caption",
      caption: "Updated caption"
    });

    const sizeEvent = oneEvent(el, "attachment-size-selected");
    large.click();
    expect((await sizeEvent).detail).to.deep.equal({
      action: "attachment-size-large",
      displaySize: "large"
    });

    const selectEvent = oneEvent(el, "attachment-files-selected");
    Object.defineProperty(fileInput, "files", {
      value: [image],
      configurable: true
    });
    fileInput.dispatchEvent(new Event("change", { bubbles: true, composed: true }));

    expect((await selectEvent).detail).to.deep.equal({
      action: "attachment-upload-queue",
      files: [image],
      caption: "Updated caption",
      displaySize: "large"
    });
  });

  it("does not re-emit display size when clicking the selected size", async () => {
    const el = await fixture(html`<compose-attachment-picker open></compose-attachment-picker>`);
    const medium = el.renderRoot.querySelector("[value='medium']");
    let sizeEvents = 0;
    el.addEventListener("attachment-size-selected", () => {
      sizeEvents += 1;
    });

    medium.click();

    expect(sizeEvents).to.equal(0);
  });

  it("renders upload progress, success, and error as accessible live regions", async () => {
    const el = await fixture(html`
      <compose-attachment-picker
        open
        upload-state="uploading"
        upload-progress="45"
        upload-message="Uploading diagram.png"
      ></compose-attachment-picker>
    `);

    const status = el.renderRoot.querySelector("[role='status']");
    const progress = el.renderRoot.querySelector("[role='progressbar']");
    expect(status.getAttribute("aria-live")).to.equal("polite");
    expect(status.textContent).to.include("Uploading diagram.png");
    expect(progress.getAttribute("aria-valuenow")).to.equal("45");

    el.uploadState = "success";
    el.uploadMessage = "diagram.png attached";
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[role='status']").textContent).to.include("attached");

    el.uploadState = "error";
    el.uploadError = "Upload failed. Try again.";
    await el.updateComplete;
    const alert = el.renderRoot.querySelector("[role='alert']");
    expect(alert.getAttribute("aria-live")).to.equal("assertive");
    expect(alert.textContent).to.include("Upload failed");
  });

  it("keeps keyboard traversal and reduced-motion/focus-visible hooks testable", async () => {
    const el = await fixture(html`<compose-attachment-picker open></compose-attachment-picker>`);
    const medium = el.renderRoot.querySelector("[value='medium']");
    const large = el.renderRoot.querySelector("[value='large']");

    medium.focus();
    medium.dispatchEvent(new KeyboardEvent("keydown", {
      key: "ArrowRight",
      bubbles: true,
      composed: true,
      cancelable: true
    }));
    await el.updateComplete;

    expect(el.displaySize).to.equal("large");
    expect(large).to.equal(el.shadowRoot.activeElement);
    expect(String(el.constructor.styles.cssText)).to.include("prefers-reduced-motion");
    expect(String(el.constructor.styles.cssText)).to.include(":focus-visible");
  });
});

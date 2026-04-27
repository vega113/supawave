import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-link-modal.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

before(() => ensureWavyTokensLoaded());

describe("<wavy-link-modal>", () => {
  it("collapses to display:none when not open", async () => {
    const el = await fixture(html`<wavy-link-modal></wavy-link-modal>`);
    expect(getComputedStyle(el).display).to.equal("none");
  });

  it("renders URL + display fields when open", async () => {
    const el = await fixture(html`
      <wavy-link-modal open url-value="https://example.com" display-value="Example"></wavy-link-modal>
    `);
    const url = el.renderRoot.querySelector("[data-link-modal-url]");
    const display = el.renderRoot.querySelector("[data-link-modal-display]");
    expect(url.value).to.equal("https://example.com");
    expect(display.value).to.equal("Example");
  });

  it("emits wavy-link-modal-submit on valid URL", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const url = el.renderRoot.querySelector("[data-link-modal-url]");
    const display = el.renderRoot.querySelector("[data-link-modal-display]");
    url.value = "https://wavyapp.com";
    display.value = "Wavy";
    const submit = oneEvent(el, "wavy-link-modal-submit");
    el.renderRoot.querySelector("form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true })
    );
    const evt = await submit;
    expect(evt.detail.url).to.equal("https://wavyapp.com");
    expect(evt.detail.display).to.equal("Wavy");
  });

  it("rejects an invalid URL and surfaces an error", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const url = el.renderRoot.querySelector("[data-link-modal-url]");
    url.value = "not-a-url";
    let submits = 0;
    el.addEventListener("wavy-link-modal-submit", () => submits++);
    el.renderRoot.querySelector("form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true })
    );
    await el.updateComplete;
    expect(submits).to.equal(0);
    const err = el.renderRoot.querySelector("[data-link-modal-error]");
    expect(err).to.exist;
    expect(err.textContent).to.match(/valid URL/);
  });

  it("emits wavy-link-modal-cancel on cancel button click", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const cancel = el.renderRoot.querySelector("[data-link-modal-cancel]");
    const cancelEvent = oneEvent(el, "wavy-link-modal-cancel");
    cancel.click();
    await cancelEvent;
    expect(el.open).to.equal(false);
  });

  it("emits wavy-link-modal-cancel on Escape", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const cancelEvent = oneEvent(el, "wavy-link-modal-cancel");
    document.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true })
    );
    await cancelEvent;
    expect(el.open).to.equal(false);
  });

  it("falls back display text to URL when display is empty", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const url = el.renderRoot.querySelector("[data-link-modal-url]");
    url.value = "https://wavyapp.com";
    const submit = oneEvent(el, "wavy-link-modal-submit");
    el.renderRoot.querySelector("form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true })
    );
    const evt = await submit;
    expect(evt.detail.display).to.equal("https://wavyapp.com");
  });

  it("accepts mailto: URLs", async () => {
    const el = await fixture(html`<wavy-link-modal open></wavy-link-modal>`);
    const url = el.renderRoot.querySelector("[data-link-modal-url]");
    url.value = "mailto:test@example.com";
    const submit = oneEvent(el, "wavy-link-modal-submit");
    el.renderRoot.querySelector("form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true })
    );
    const evt = await submit;
    expect(evt.detail.url).to.equal("mailto:test@example.com");
  });
});

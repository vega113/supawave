import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-tags-row.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

before(() => ensureWavyTokensLoaded());

describe("<wavy-tags-row>", () => {
  it("renders 'Tags:' label and chips for each tag (I.1, I.2)", async () => {
    const el = await fixture(html`
      <wavy-tags-row .tags=${["urgent", "tasks"]}></wavy-tags-row>
    `);
    expect(el.renderRoot.textContent).to.match(/Tags:/);
    expect(el.renderRoot.querySelectorAll('[data-tag-chip]').length).to.equal(2);
    expect(el.renderRoot.querySelector('[data-tag-chip="urgent"]')).to.exist;
    expect(el.renderRoot.querySelector('[data-tag-chip="tasks"]')).to.exist;
  });

  it("emits wave-tag-remove-requested when × on a chip is clicked (I.2)", async () => {
    const el = await fixture(html`
      <wavy-tags-row wave-id="w1" .tags=${["urgent"]}></wavy-tags-row>
    `);
    const remove = el.renderRoot.querySelector('[data-tag-remove="urgent"]');
    const evt = oneEvent(el, "wave-tag-remove-requested");
    remove.click();
    const event = await evt;
    expect(event.detail).to.deep.equal({ waveId: "w1", tag: "urgent" });
  });

  it("renders the + Add tag button when not editing (I.3)", async () => {
    const el = await fixture(html`<wavy-tags-row></wavy-tags-row>`);
    const add = el.renderRoot.querySelector("[data-tags-add]");
    expect(add).to.exist;
    expect(add.textContent.trim()).to.match(/Add tag/);
  });

  it("opens the inline textbox on Add click and shows a Cancel × (I.4, I.5)", async () => {
    const el = await fixture(html`<wavy-tags-row></wavy-tags-row>`);
    el.renderRoot.querySelector("[data-tags-add]").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector("[data-tags-input]");
    const cancel = el.renderRoot.querySelector("[data-tags-cancel]");
    expect(input).to.exist;
    expect(cancel).to.exist;
  });

  it("emits wave-tag-add-requested on Enter in the textbox (I.4)", async () => {
    const el = await fixture(html`<wavy-tags-row wave-id="w42"></wavy-tags-row>`);
    el.renderRoot.querySelector("[data-tags-add]").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector("[data-tags-input]");
    input.value = "todo";
    const evt = oneEvent(el, "wave-tag-add-requested");
    input.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true, cancelable: true })
    );
    const event = await evt;
    expect(event.detail).to.deep.equal({ waveId: "w42", tag: "todo" });
  });

  it("dismisses editing mode on Escape (I.5)", async () => {
    const el = await fixture(html`<wavy-tags-row></wavy-tags-row>`);
    el.renderRoot.querySelector("[data-tags-add]").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector("[data-tags-input]");
    input.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true, composed: true, cancelable: true })
    );
    await el.updateComplete;
    expect(el.editing).to.equal(false);
  });

  it("dismisses editing mode on Cancel × click (I.5)", async () => {
    const el = await fixture(html`<wavy-tags-row></wavy-tags-row>`);
    el.renderRoot.querySelector("[data-tags-add]").click();
    await el.updateComplete;
    el.renderRoot.querySelector("[data-tags-cancel]").click();
    await el.updateComplete;
    expect(el.editing).to.equal(false);
  });

  it("does not emit wave-tag-add-requested for empty input", async () => {
    const el = await fixture(html`<wavy-tags-row></wavy-tags-row>`);
    el.renderRoot.querySelector("[data-tags-add]").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector("[data-tags-input]");
    input.value = "   ";
    let adds = 0;
    el.addEventListener("wave-tag-add-requested", () => adds++);
    input.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true, cancelable: true })
    );
    expect(adds).to.equal(0);
  });
});

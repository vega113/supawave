import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wave-blip-toolbar.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

before(async () => {
  ensureWavyTokensLoaded();
});

describe("<wave-blip-toolbar>", () => {
  it("registers the F-2 toolbar custom element", () => {
    expect(customElements.get("wave-blip-toolbar")).to.exist;
  });

  it("renders Reply / Edit / Link / Delete / overflow buttons with stable data-toolbar-action keys", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b1" data-wave-id="w1"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-toolbar-action='reply']")).to.exist;
    expect(el.renderRoot.querySelector("[data-toolbar-action='edit']")).to.exist;
    expect(el.renderRoot.querySelector("[data-toolbar-action='link']")).to.exist;
    // F-3.S4 (#1038, R-5.6 F.6)
    expect(el.renderRoot.querySelector("[data-toolbar-action='delete']")).to.exist;
    expect(el.renderRoot.querySelector("[data-toolbar-action='overflow']")).to.exist;
  });

  it("each button has an aria-label that names the action", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b2" data-wave-id="w2"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-toolbar-action='reply']").getAttribute("aria-label"))
      .to.equal("Reply to this blip");
    expect(el.renderRoot.querySelector("[data-toolbar-action='edit']").getAttribute("aria-label"))
      .to.equal("Edit this blip");
    expect(el.renderRoot.querySelector("[data-toolbar-action='link']").getAttribute("aria-label"))
      .to.equal("Copy permalink to this blip");
    expect(el.renderRoot.querySelector("[data-toolbar-action='delete']").getAttribute("aria-label"))
      .to.equal("Delete this blip");
    expect(el.renderRoot.querySelector("[data-toolbar-action='overflow']").getAttribute("aria-label"))
      .to.equal("More blip actions");
  });

  it("overflow button is announced as a menu trigger (aria-haspopup='menu')", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b3" data-wave-id="w3"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-toolbar-action='overflow']")
      .getAttribute("aria-haspopup")).to.equal("menu");
  });

  it("Reply button click emits wave-blip-toolbar-reply with blip context", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b4" data-wave-id="w4"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("[data-toolbar-action='reply']").click(), 0);
    const ev = await oneEvent(el, "wave-blip-toolbar-reply");
    expect(ev.detail.blipId).to.equal("b4");
    expect(ev.detail.waveId).to.equal("w4");
    expect(ev.bubbles).to.be.true;
    expect(ev.composed).to.be.true;
  });

  it("Edit button click emits wave-blip-toolbar-edit", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b5" data-wave-id="w5"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("[data-toolbar-action='edit']").click(), 0);
    const ev = await oneEvent(el, "wave-blip-toolbar-edit");
    expect(ev.detail.blipId).to.equal("b5");
  });

  it("Link button click emits wave-blip-toolbar-link", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b6" data-wave-id="w6"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("[data-toolbar-action='link']").click(), 0);
    const ev = await oneEvent(el, "wave-blip-toolbar-link");
    expect(ev.detail.blipId).to.equal("b6");
  });

  it("overflow button click emits wave-blip-toolbar-overflow", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b7" data-wave-id="w7"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("[data-toolbar-action='overflow']").click(), 0);
    const ev = await oneEvent(el, "wave-blip-toolbar-overflow");
    expect(ev.detail.blipId).to.equal("b7");
  });

  // F-3.S4 (#1038, R-5.6 F.6): the Delete button click emits a
  // dedicated wave-blip-toolbar-delete event so the parent wave-blip
  // can re-emit a public wave-blip-delete-requested CustomEvent the
  // J2CL compose view listens for.
  it("Delete button click emits wave-blip-toolbar-delete", async () => {
    const el = await fixture(html`
      <wave-blip-toolbar data-blip-id="b8" data-wave-id="w8"></wave-blip-toolbar>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("[data-toolbar-action='delete']").click(), 0);
    const ev = await oneEvent(el, "wave-blip-toolbar-delete");
    expect(ev.detail.blipId).to.equal("b8");
    expect(ev.detail.waveId).to.equal("w8");
    expect(ev.bubbles).to.be.true;
    expect(ev.composed).to.be.true;
  });
});

import { fixture, expect, html, oneEvent, aTimeout } from "@open-wc/testing";
import "../src/elements/wave-blip.js";
import "../src/elements/wave-blip-toolbar.js";
import "../src/design/wavy-blip-card.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

async function waitForStylesheet() {
  for (let i = 0; i < 50; i++) {
    const cs = getComputedStyle(document.documentElement);
    if (cs.getPropertyValue("--wavy-bg-base").trim() !== "") return;
    await new Promise((r) => setTimeout(r, 20));
  }
  throw new Error("wavy-tokens.css did not apply within 1000ms");
}

before(async () => {
  ensureWavyTokensLoaded();
  await waitForStylesheet();
});

describe("<wave-blip>", () => {
  it("registers the F-2 wrapper custom element", () => {
    expect(customElements.get("wave-blip")).to.exist;
  });

  it("reflects blip-id, wave-id, has-mention, focused, unread, reply-count", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b1"
        data-wave-id="w1"
        author-id="alice@example.com"
        author-name="Alice"
        posted-at="2m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
        is-author
        focused
        unread
        has-mention
        reply-count="3"
      >
        Hello world.
      </wave-blip>
    `);
    expect(el.getAttribute("data-blip-id")).to.equal("b1");
    expect(el.getAttribute("data-wave-id")).to.equal("w1");
    expect(el.hasAttribute("focused")).to.be.true;
    expect(el.hasAttribute("unread")).to.be.true;
    expect(el.hasAttribute("has-mention")).to.be.true;
    expect(el.getAttribute("reply-count")).to.equal("3");
  });

  it("propagates blip + wave id + author + focus state to the inner wavy-blip-card", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b2"
        data-wave-id="w2"
        author-name="Bob"
        posted-at="just now"
        is-author
        focused
        unread
      >
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const card = el.renderRoot.querySelector("wavy-blip-card");
    expect(card).to.exist;
    expect(card.getAttribute("data-blip-id")).to.equal("b2");
    expect(card.getAttribute("data-wave-id")).to.equal("w2");
    expect(card.getAttribute("author-name")).to.equal("Bob");
    expect(card.hasAttribute("focused")).to.be.true;
    expect(card.hasAttribute("unread")).to.be.true;
  });

  it("renders the per-blip toolbar in the metadata slot", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b3" data-wave-id="w3" author-name="Carol">
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    expect(toolbar).to.exist;
    expect(toolbar.getAttribute("data-blip-id")).to.equal("b3");
  });

  it("re-emits wave-blip-reply-requested with blip context when toolbar Reply fires", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b4" data-wave-id="w4" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    await toolbar.updateComplete;
    const replyBtn = toolbar.renderRoot.querySelector("[data-toolbar-action='reply']");
    expect(replyBtn).to.exist;
    setTimeout(() => replyBtn.click(), 0);
    const ev = await oneEvent(el, "wave-blip-reply-requested");
    expect(ev.detail.blipId).to.equal("b4");
    expect(ev.detail.waveId).to.equal("w4");
  });

  it("re-emits wave-blip-edit-requested when toolbar Edit fires", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b5" data-wave-id="w5" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    await toolbar.updateComplete;
    const editBtn = toolbar.renderRoot.querySelector("[data-toolbar-action='edit']");
    setTimeout(() => editBtn.click(), 0);
    const ev = await oneEvent(el, "wave-blip-edit-requested");
    expect(ev.detail.blipId).to.equal("b5");
  });

  it("renders the inline-reply chip only when reply-count > 0", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b6" data-wave-id="w6" author-name="A">x</wave-blip>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-inline-reply-chip='true']")).to.not.exist;

    el.replyCount = 4;
    await el.updateComplete;
    const chip = el.renderRoot.querySelector("[data-inline-reply-chip='true']");
    expect(chip).to.exist;
    expect(chip.textContent.trim()).to.contain("4");
  });

  it("inline-reply chip click emits wave-blip-drill-in-requested with blip context", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b7"
        data-wave-id="w7"
        author-name="A"
        reply-count="2"
      >
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const chip = el.renderRoot.querySelector("[data-inline-reply-chip='true']");
    setTimeout(() => chip.click(), 0);
    const ev = await oneEvent(el, "wave-blip-drill-in-requested");
    expect(ev.detail.blipId).to.equal("b7");
    expect(ev.detail.waveId).to.equal("w7");
  });

  it("avatar click emits wave-blip-profile-requested with author id", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b8"
        data-wave-id="w8"
        author-id="dora@example.com"
        author-name="Dora"
      ></wave-blip>
    `);
    await el.updateComplete;
    const avatar = el.renderRoot.querySelector("[data-blip-avatar='true']");
    setTimeout(() => avatar.click(), 0);
    const ev = await oneEvent(el, "wave-blip-profile-requested");
    expect(ev.detail.blipId).to.equal("b8");
    expect(ev.detail.authorId).to.equal("dora@example.com");
  });

  it("renders the timestamp with an ISO-8601 datetime tooltip", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b9"
        data-wave-id="w9"
        author-name="E"
        posted-at="5m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
      ></wave-blip>
    `);
    await el.updateComplete;
    const time = el.renderRoot.querySelector("time.posted");
    expect(time).to.exist;
    expect(time.getAttribute("title")).to.equal("2026-04-26T12:00:00Z");
    expect(time.getAttribute("datetime")).to.equal("2026-04-26T12:00:00Z");
  });

  it("blipView getter returns a frozen snapshot mirroring the F-0 plugin contract", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b10"
        data-wave-id="w10"
        author-id="a@x"
        author-name="Anne"
        posted-at="1m"
        posted-at-iso="2026-04-26T12:00:00Z"
        is-author
        unread
        has-mention
        reply-count="5"
      ></wave-blip>
    `);
    await el.updateComplete;
    const view = el.blipView;
    expect(Object.isFrozen(view)).to.be.true;
    expect(view.id).to.equal("b10");
    expect(view.waveId).to.equal("w10");
    expect(view.authorName).to.equal("Anne");
    expect(view.authorId).to.equal("a@x");
    expect(view.postedAtIso).to.equal("2026-04-26T12:00:00Z");
    expect(view.isAuthor).to.be.true;
    expect(view.unread).to.be.true;
    expect(view.hasMention).to.be.true;
    expect(view.replyCount).to.equal(5);
  });

  it("blip-extension slot still resolves the F-0 plugin context (forwarding contract)", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b11" data-wave-id="w11" author-name="A">
        body
        <span slot="blip-extension" data-test-plugin="true">hello plugin</span>
      </wave-blip>
    `);
    await el.updateComplete;
    const card = el.renderRoot.querySelector("wavy-blip-card");
    // The wrapper forwards the slot through; the inner card sees the plugin
    // node via its own blip-extension slot.
    const cardSlot = card.renderRoot.querySelector("slot[name='blip-extension']");
    expect(cardSlot).to.exist;
    // The inner card's plugin contract surfaces via host.dataset on the card.
    expect(card.dataset.blipId).to.equal("b11");
    expect(card.dataset.waveId).to.equal("w11");
  });

  it("firePulse triggers the F-0 live-pulse on the inner card", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b12" data-wave-id="w12" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    el.firePulse();
    await aTimeout(0);
    const card = el.renderRoot.querySelector("wavy-blip-card");
    expect(card.hasAttribute("live-pulse")).to.be.true;
  });

  it("violet mention rail attribute toggles via has-mention reflection", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b13" data-wave-id="w13" author-name="A"></wave-blip>
    `);
    el.hasMention = true;
    await el.updateComplete;
    expect(el.hasAttribute("has-mention")).to.be.true;
    el.hasMention = false;
    await el.updateComplete;
    expect(el.hasAttribute("has-mention")).to.be.false;
  });
});

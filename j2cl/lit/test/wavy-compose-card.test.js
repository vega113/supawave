import { fixture, expect, html } from "@open-wc/testing";
import "../src/design/wavy-compose-card.js";

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
}

before(async () => {
  ensureWavyTokensLoaded();
  await waitForStylesheet();
});

describe("<wavy-compose-card>", () => {
  it("exposes the four named slots", async () => {
    const el = await fixture(html`<wavy-compose-card></wavy-compose-card>`);
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='toolbar']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='compose-extension']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='affordance']")).to.exist;
  });

  it("reflects reply-target-blip-id as a data attribute (data-* form per slot contract)", async () => {
    const el = await fixture(html`
      <wavy-compose-card data-reply-target-blip-id="b42"></wavy-compose-card>
    `);
    // Plugins read this via host.dataset.replyTargetBlipId per
    // docs/j2cl-plugin-slots.md.
    expect(el.dataset.replyTargetBlipId).to.equal("b42");
    expect(el.getAttribute("data-reply-target-blip-id")).to.equal("b42");
  });

  it("empty compose-extension slot in design preview shows the dashed outline label", async () => {
    document.documentElement.dataset.wavyDesignPreview = "true";
    try {
      const el = await fixture(html`<wavy-compose-card></wavy-compose-card>`);
      const wrapper = el.renderRoot.querySelector(".ext-slot-wrapper");
      const cs = getComputedStyle(wrapper);
      expect(cs.borderStyle).to.equal("dashed");
    } finally {
      delete document.documentElement.dataset.wavyDesignPreview;
    }
  });

  it("focused state applies the cyan focus ring border-color", async () => {
    const wrap = await fixture(
      html`<div data-wavy-theme="dark">
        <wavy-compose-card focused></wavy-compose-card>
      </div>`
    );
    const card = wrap.querySelector("wavy-compose-card");
    const cs = getComputedStyle(card);
    expect(cs.borderColor.replace(/\s+/g, "")).to.match(/rgb\(34,211,238\)/);
  });

  it("submitting state suppresses pointer-events on the affordance row", async () => {
    const el = await fixture(html`
      <wavy-compose-card submitting>
        <button slot="affordance">Submit</button>
      </wavy-compose-card>
    `);
    const row = el.renderRoot.querySelector(".affordance-row");
    expect(getComputedStyle(row).pointerEvents).to.equal("none");
  });

  it("composerState and activeSelection setters return frozen objects", async () => {
    const el = await fixture(html`<wavy-compose-card></wavy-compose-card>`);
    el.composerState = { drafts: 3 };
    el.activeSelection = { start: 0, end: 4 };
    expect(Object.isFrozen(el.composerState)).to.equal(true);
    expect(Object.isFrozen(el.activeSelection)).to.equal(true);
    expect(el.composerState.drafts).to.equal(3);
    expect(el.activeSelection.end).to.equal(4);
    expect(() => {
      el.composerState.drafts = 99;
    }).to.throw();
  });

  it("dark / light / contrast variants flip the surface background", async () => {
    const variants = ["dark", "light", "contrast"];
    const surfaces = new Set();
    for (const v of variants) {
      const wrap = await fixture(
        html`<div data-wavy-theme=${v}>
          <wavy-compose-card></wavy-compose-card>
        </div>`
      );
      const card = wrap.querySelector("wavy-compose-card");
      surfaces.add(getComputedStyle(card).backgroundColor);
    }
    expect(surfaces.size).to.be.greaterThanOrEqual(2);
  });
});

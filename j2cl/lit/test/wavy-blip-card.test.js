import { fixture, expect, html } from "@open-wc/testing";
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

describe("<wavy-blip-card>", () => {
  it("reflects the four contract data attributes (data-* form per slot contract)", async () => {
    const el = await fixture(html`
      <wavy-blip-card
        data-blip-id="b1"
        data-wave-id="w1"
        author-name="Alice"
        is-author
        posted-at="2026-04-26T12:00Z"
      ></wavy-blip-card>
    `);
    // Plugins read these via host.dataset.* per docs/j2cl-plugin-slots.md.
    expect(el.dataset.blipId).to.equal("b1");
    expect(el.dataset.waveId).to.equal("w1");
    expect(el.dataset.blipAuthor).to.equal("Alice");
    expect(el.dataset.blipIsAuthor).to.equal("true");
    // Defensive: also assert the canonical attribute names exist.
    expect(el.getAttribute("data-blip-id")).to.equal("b1");
    expect(el.getAttribute("data-wave-id")).to.equal("w1");
  });

  it("exposes the four named slots", async () => {
    const el = await fixture(html`<wavy-blip-card></wavy-blip-card>`);
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='blip-extension']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='reactions']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='metadata']")).to.exist;
  });

  it("empty blip-extension slot in design preview shows the dashed outline label", async () => {
    document.documentElement.dataset.wavyDesignPreview = "true";
    try {
      const el = await fixture(html`<wavy-blip-card></wavy-blip-card>`);
      const wrapper = el.renderRoot.querySelector(".ext-slot-wrapper");
      const cs = getComputedStyle(wrapper);
      // The :host-context([data-wavy-design-preview]) rule should make
      // the wrapper render as a block with a dashed border.
      expect(cs.borderStyle).to.equal("dashed");
      expect(cs.display).to.equal("block");
    } finally {
      delete document.documentElement.dataset.wavyDesignPreview;
    }
  });

  it("empty blip-extension slot without design preview collapses to zero height (no dashed outline)", async () => {
    delete document.documentElement.dataset.wavyDesignPreview;
    const el = await fixture(html`<wavy-blip-card></wavy-blip-card>`);
    const wrapper = el.renderRoot.querySelector(".ext-slot-wrapper");
    const cs = getComputedStyle(wrapper);
    expect(cs.borderStyle).to.not.equal("dashed");
    expect(parseInt(cs.height, 10)).to.equal(0);
  });

  it("focused state applies the GWT-light cyan focus ring border-color", async () => {
    const card = await fixture(html`<wavy-blip-card focused></wavy-blip-card>`);
    const cs = getComputedStyle(card);
    // box-shadow resolves to the GWT toolbar blue (#0077b6).
    expect(cs.boxShadow.replace(/\s+/g, "")).to.match(/rgb\(0,119,182\)/);
  });

  it("blipView returns a frozen read-only snapshot of the card state", async () => {
    const el = await fixture(html`
      <wavy-blip-card
        data-blip-id="b1"
        data-wave-id="w1"
        author-name="Alice"
        is-author
      ></wavy-blip-card>
    `);
    const view = el.blipView;
    expect(view.id).to.equal("b1");
    expect(view.waveId).to.equal("w1");
    expect(view.authorName).to.equal("Alice");
    expect(view.isAuthor).to.equal(true);
    expect(Object.isFrozen(view)).to.equal(true);
  });

  it("blipView mutation throws under strict mode (custom-element default)", async () => {
    const el = await fixture(html`
      <wavy-blip-card data-blip-id="b1"></wavy-blip-card>
    `);
    const view = el.blipView;
    expect(() => {
      view.id = "mutated";
    }).to.throw();
  });

  it("dark / light / contrast variants flip the surface background", async () => {
    const variants = ["dark", "light", "contrast"];
    const surfaces = new Set();
    for (const v of variants) {
      const wrap = await fixture(
        html`<div data-wavy-theme=${v}>
          <wavy-blip-card></wavy-blip-card>
        </div>`
      );
      const card = wrap.querySelector("wavy-blip-card");
      surfaces.add(getComputedStyle(card).backgroundColor);
    }
    // At least dark vs light must differ; contrast may share with dark
    // when the wrapper is itself dark (#0b1320). Require ≥2 distinct.
    expect(surfaces.size).to.be.greaterThanOrEqual(2);
  });

  it("firePulse() sets the live-pulse attribute", async () => {
    const el = await fixture(html`<wavy-blip-card></wavy-blip-card>`);
    el.firePulse();
    expect(el.hasAttribute("live-pulse")).to.equal(true);
  });

  it("back-to-back firePulse() restarts the animation (remove → force layout → add)", async () => {
    const el = await fixture(html`<wavy-blip-card></wavy-blip-card>`);
    el.firePulse();
    expect(el.hasAttribute("live-pulse")).to.equal(true);
    // Spy on removeAttribute to confirm the restart pattern fires.
    let removed = 0;
    const original = el.removeAttribute.bind(el);
    el.removeAttribute = (name, ...rest) => {
      if (name === "live-pulse") removed += 1;
      return original(name, ...rest);
    };
    el.firePulse();
    expect(removed, "restart should call removeAttribute('live-pulse') once").to.equal(1);
    expect(el.hasAttribute("live-pulse")).to.equal(true);
  });
});

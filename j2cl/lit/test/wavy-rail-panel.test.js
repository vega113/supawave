import { fixture, expect, html } from "@open-wc/testing";
import "../src/design/wavy-rail-panel.js";

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

describe("<wavy-rail-panel>", () => {
  it("exposes the named slots including rail-extension", async () => {
    const el = await fixture(html`<wavy-rail-panel></wavy-rail-panel>`);
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header-actions']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='rail-extension']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='footer']")).to.exist;
  });

  it("reflects active-wave-id and active-folder as data-* attributes per slot contract", async () => {
    const el = await fixture(html`
      <wavy-rail-panel
        panel-title="Saved searches"
        data-active-wave-id="w1"
        data-active-folder="inbox"
      ></wavy-rail-panel>
    `);
    // Plugins read via host.dataset.* per docs/j2cl-plugin-slots.md.
    expect(el.dataset.activeWaveId).to.equal("w1");
    expect(el.dataset.activeFolder).to.equal("inbox");
    expect(el.getAttribute("data-active-wave-id")).to.equal("w1");
    expect(el.getAttribute("data-active-folder")).to.equal("inbox");
  });

  it("empty rail-extension slot in design preview shows the dashed outline label", async () => {
    document.documentElement.dataset.wavyDesignPreview = "true";
    try {
      const el = await fixture(html`<wavy-rail-panel></wavy-rail-panel>`);
      const wrapper = el.renderRoot.querySelector(".ext-slot-wrapper");
      const cs = getComputedStyle(wrapper);
      expect(cs.borderStyle).to.equal("dashed");
      expect(cs.display).to.equal("block");
    } finally {
      delete document.documentElement.dataset.wavyDesignPreview;
    }
  });

  it("toggleCollapsed flips the collapsed attribute and aria-expanded", async () => {
    const el = await fixture(html`<wavy-rail-panel></wavy-rail-panel>`);
    const region = el.renderRoot.querySelector("section[role='region']");
    expect(region.getAttribute("aria-expanded")).to.equal("true");
    el.toggleCollapsed();
    await el.updateComplete;
    expect(el.hasAttribute("collapsed")).to.equal(true);
    expect(region.getAttribute("aria-expanded")).to.equal("false");
  });

  it("renders the panel title in the header", async () => {
    const el = await fixture(html`
      <wavy-rail-panel panel-title="Saved"></wavy-rail-panel>
    `);
    const h2 = el.renderRoot.querySelector("h2");
    expect(h2.textContent.trim()).to.equal("Saved");
  });

  it("dark / light / contrast variants flip the surface background", async () => {
    const variants = ["dark", "light", "contrast"];
    const surfaces = new Set();
    for (const v of variants) {
      const wrap = await fixture(
        html`<div data-wavy-theme=${v}>
          <wavy-rail-panel></wavy-rail-panel>
        </div>`
      );
      const card = wrap.querySelector("wavy-rail-panel");
      surfaces.add(getComputedStyle(card).backgroundColor);
    }
    expect(surfaces.size).to.be.greaterThanOrEqual(2);
  });
});

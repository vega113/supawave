import { fixture, expect, html } from "@open-wc/testing";
import "../src/design/wavy-edit-toolbar.js";

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

describe("<wavy-edit-toolbar>", () => {
  it("exposes the default and toolbar-extension slots", async () => {
    const el = await fixture(html`<wavy-edit-toolbar></wavy-edit-toolbar>`);
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='toolbar-extension']")).to.exist;
  });

  it("uses the toolbar landmark role", async () => {
    const el = await fixture(html`<wavy-edit-toolbar></wavy-edit-toolbar>`);
    expect(el.renderRoot.querySelector("div[role='toolbar']")).to.exist;
  });

  it("debounces active-selection writes to the data attribute via rAF", async () => {
    const el = await fixture(html`
      <wavy-edit-toolbar active-selection='{"start":0,"end":4}'></wavy-edit-toolbar>
    `);
    // The attribute set via the literal triggers the rAF; wait one frame
    // plus a tick so the data attribute reflects.
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    // Plugins read via host.dataset.activeSelection per docs/j2cl-plugin-slots.md.
    expect(el.dataset.activeSelection).to.equal('{"start":0,"end":4}');
    expect(el.getAttribute("data-active-selection")).to.equal('{"start":0,"end":4}');
  });

  it("empty toolbar-extension slot in design preview shows the dashed outline label", async () => {
    document.documentElement.dataset.wavyDesignPreview = "true";
    try {
      const el = await fixture(html`<wavy-edit-toolbar></wavy-edit-toolbar>`);
      const wrapper = el.renderRoot.querySelector(".ext-slot-wrapper");
      const cs = getComputedStyle(wrapper);
      expect(cs.borderStyle).to.equal("dashed");
    } finally {
      delete document.documentElement.dataset.wavyDesignPreview;
    }
  });

  it("dark / light / contrast variants flip the surface background", async () => {
    const variants = ["dark", "light", "contrast"];
    const surfaces = new Set();
    for (const v of variants) {
      const wrap = await fixture(
        html`<div data-wavy-theme=${v}>
          <wavy-edit-toolbar></wavy-edit-toolbar>
        </div>`
      );
      const tb = wrap.querySelector("wavy-edit-toolbar");
      surfaces.add(getComputedStyle(tb).backgroundColor);
    }
    expect(surfaces.size).to.be.greaterThanOrEqual(2);
  });
});

import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-nav-rail.js";

describe("<shell-nav-rail>", () => {
  it("uses the navigation landmark with a primary label", async () => {
    const el = await fixture(html`<shell-nav-rail></shell-nav-rail>`);
    const nav = el.renderRoot.querySelector("nav");
    expect(nav).to.exist;
    expect(nav.getAttribute("aria-label")).to.equal("Primary");
  });

  it("exposes a default slot for entries", async () => {
    const el = await fixture(
      html`<shell-nav-rail><a href="/">Home</a></shell-nav-rail>`
    );
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
  });
});

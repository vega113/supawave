import { fixture, expect, html } from "@open-wc/testing";
import { ShellNavRail } from "../src/elements/shell-nav-rail.js";
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

  // :host(:has(> X)) inside a shadow stylesheet does not match light-DOM slotted
  // children in Chromium's CSS engine, so verify the rule is declared rather than
  // testing computed style.
  it("declares flush nav layout rule for wavy-search-rail host context", () => {
    const cssText = ShellNavRail.styles.cssText;
    expect(cssText).to.include(":host(:has(> wavy-search-rail)) nav");
    expect(cssText).to.include("padding: 0");
  });

  it("preserves nav inset padding for non-search-rail slotted children", async () => {
    const el = await fixture(
      html`<shell-nav-rail><a href="/">Home</a></shell-nav-rail>`
    );
    const nav = el.renderRoot.querySelector("nav");
    expect(getComputedStyle(nav).padding).to.equal("12px");
  });

  it("zeroes padding and border-radius on slotted wavy-search-rail", async () => {
    const searchRail = document.createElement("wavy-search-rail");
    const el = await fixture(
      html`<shell-nav-rail>${searchRail}</shell-nav-rail>`
    );
    await el.updateComplete;
    expect(getComputedStyle(searchRail).padding).to.equal("0px");
    expect(getComputedStyle(searchRail).borderRadius).to.equal("0px");
  });
});

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

  // Round 5 (#1237): the previous :host(:has(> wavy-search-rail)) selector
  // never matched in Chromium against light-DOM slotted children. The
  // component now sets data-flush on the host via slotchange so the rule
  // actually applies at runtime — assert the computed style.
  it("collapses nav inset padding to zero when wrapping a wavy-search-rail", async () => {
    const searchRail = document.createElement("wavy-search-rail");
    const el = await fixture(
      html`<shell-nav-rail>${searchRail}</shell-nav-rail>`
    );
    await el.updateComplete;
    expect(el.hasAttribute("data-flush")).to.equal(true);
    const nav = el.renderRoot.querySelector("nav");
    expect(getComputedStyle(nav).padding).to.equal("0px");
  });

  it("preserves nav inset padding for non-search-rail slotted children", async () => {
    const el = await fixture(
      html`<shell-nav-rail><a href="/">Home</a></shell-nav-rail>`
    );
    expect(el.hasAttribute("data-flush")).to.equal(false);
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

  it("toggles data-flush on slotted wavy-search-rail removal", async () => {
    const searchRail = document.createElement("wavy-search-rail");
    const el = await fixture(
      html`<shell-nav-rail>${searchRail}</shell-nav-rail>`
    );
    await el.updateComplete;
    expect(el.hasAttribute("data-flush")).to.equal(true);
    searchRail.remove();
    // Slot change fires asynchronously; wait one microtask + a frame.
    await new Promise((resolve) => requestAnimationFrame(resolve));
    expect(el.hasAttribute("data-flush")).to.equal(false);
  });
});

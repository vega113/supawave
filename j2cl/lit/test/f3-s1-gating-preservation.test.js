import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/wavy-composer.js";
import "../src/elements/wavy-format-toolbar.js";

/**
 * F-3.S1 (#1038) static regression-lock: the F-2.S6 fix (PR #1061) gated
 * the always-visible H.* toolbar wall and the always-visible reply
 * textarea. F-3.S1 must NOT bring either back.
 *
 * The two gating rules:
 *   1. <wavy-composer> collapses to display:none when not [available].
 *   2. <wavy-format-toolbar> hides itself (no transform offset, hidden
 *      attribute set) when no selection is active.
 *
 * If either of these regresses, the wave panel paints a permanent editor
 * surface — exactly the bug F-2.S6 fixed. The acceptance row R-5.2
 * step 8 explicitly requires that no <wavy-format-toolbar> is rendered
 * in the document body when the user has not opened a Reply or Edit.
 */
describe("F-3.S1 preserves F-2.S6 gating", () => {
  it("Rule 1: <wavy-composer> collapses to display:none when not available", async () => {
    const el = await fixture(html`<wavy-composer></wavy-composer>`);
    await el.updateComplete;
    expect(el.hasAttribute("available")).to.equal(false);
    expect(getComputedStyle(el).display).to.equal("none");
  });

  it("Rule 1: <wavy-composer> renders normally once available is set", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    await el.updateComplete;
    expect(getComputedStyle(el).display).to.equal("block");
  });

  it("Rule 2: <wavy-format-toolbar> hides itself with collapsed selection", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = { collapsed: true };
    await el.updateComplete;
    await new Promise((r) => requestAnimationFrame(r));
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("Rule 2: <wavy-format-toolbar> hides itself with no descriptor", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("Acceptance R-5.2 step 8: no wavy-format-toolbar in the body when no composer is mounted", async () => {
    // No <wavy-composer> means no compose session is active. Even if a
    // <wavy-format-toolbar> were registered as a custom element, no
    // instance should be in the document until a composer creates one.
    const toolbars = document.querySelectorAll("wavy-format-toolbar");
    expect(toolbars.length).to.equal(0);
  });

  it("Acceptance: wavy-composer's body is rendered when available", async () => {
    // The contenteditable body is created lazily and persists across
    // renders. When the host is not [available], CSS collapses the host
    // to display:none — the shadow DOM may still contain the body, but
    // it is not visible.
    const on = await fixture(html`<wavy-composer available></wavy-composer>`);
    await on.updateComplete;
    expect(on.shadowRoot.querySelector("[data-composer-body]")).to.exist;
  });
});

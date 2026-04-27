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

  it("Acceptance R-5.2 step 8: no auto-mounted wavy-format-toolbar in document.body", async () => {
    // The acceptance row asserts that no <wavy-format-toolbar> element
    // is rendered in production until a composer creates one. The
    // contract is: importing the element registers the custom element
    // (via customElements.define) but does NOT mount any instance. The
    // composer is responsible for instantiating the toolbar lazily.
    //
    // We verify the contract at the source level — wavy-composer.js
    // does NOT auto-create a wavy-format-toolbar in its render(). The
    // floating toolbar is mounted by the compose VIEW (Java-side) only
    // when an active composer exists.
    const composerHtml = await fetch("/src/elements/wavy-composer.js").then((r) => r.text());
    expect(
      composerHtml.includes("createElement(\"wavy-format-toolbar\")")
        || composerHtml.includes("<wavy-format-toolbar"),
      "wavy-composer.js must NOT eagerly mount <wavy-format-toolbar> — toolbar mount is the view's responsibility"
    ).to.equal(false);
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

import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/wavy-search-rail.js";
import "../src/elements/composer-inline-reply.js";

/**
 * F-2 follow-up (#1060) — visible-regression locks for the gaps covered
 * in this file that survived the F-2 closeout (PR #1059, sha dc8ee6a3):
 *
 *   1. <wavy-search-rail> shadow DOM exposed a default <slot></slot>,
 *      which projected the SSR'd light-DOM rail (search box + folder
 *      list + "Saved searches" header) under the rendered shadow chrome.
 *      Live readers saw the rail twice — dark wavy on top + light
 *      legacy below.
 *   2. <composer-inline-reply> rendered "Reply target: ..." + an empty
 *      Send-reply textarea on every selected wave, even when no compose
 *      was active, painting as a permanent editor-toolbar wall.
 *
 * These assertions FAIL on origin/main HEAD before the fix and PASS
 * after, so these covered regressions cannot ship a third time.
 * (The J2CL read-renderer fallback-to-blip-id fix is regression-locked
 * in HtmlRendererJ2clRootShellIntegrationTest.)
 */

describe("F-2 follow-up #1060 — no duplicate rail / no flat blip-id / no toolbar wall", () => {
  it("Gap 1: <wavy-search-rail> shadow DOM does not expose a default <slot>", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    // Regression guard: any unnamed <slot> in the shadow DOM would
    // project SSR light children under the rendered chrome and
    // duplicate the rail surface.
    const defaultSlots = Array.from(
      el.renderRoot.querySelectorAll("slot")
    ).filter((s) => !s.hasAttribute("name"));
    expect(
      defaultSlots,
      "wavy-search-rail must not project SSR'd light DOM via a default slot"
    ).to.have.length(0);
  });

  it("Gap 1: light-DOM SSR fallback children are not rendered after upgrade", async () => {
    // Mount the element with the same SSR'd light-DOM shape the Jakarta
    // HtmlRenderer emits, then assert the projected fallback chrome is
    // absent from the visible render.
    const el = await fixture(html`
      <wavy-search-rail query="in:inbox" data-active-folder="inbox" result-count="">
        <div class="search">
          <input type="search" class="query" name="q" value="in:inbox" />
          <button type="button" class="help-trigger">?</button>
        </div>
        <div class="actions">
          <button type="button" class="new-wave">New Wave</button>
          <button type="button" class="manage-saved">Manage saved searches</button>
        </div>
        <div class="folders-header">
          <h2 id="folders-title">Saved searches</h2>
        </div>
        <ul class="folders" aria-labelledby="folders-title">
          <li><button class="folder" data-folder-id="inbox">Inbox</button></li>
        </ul>
      </wavy-search-rail>
    `);
    await el.updateComplete;
    // Light-DOM children are still in the DOM (custom element preserved
    // them) but they should NOT render twice on screen — only the
    // shadow-DOM render is visible. Since there is no default <slot>,
    // light children with no slot= attribute have no place to project
    // and are visually hidden by the shadow root.
    const lightChildren = Array.from(el.children).filter(
      (c) => !c.hasAttribute("slot")
    );
    expect(lightChildren.length, "test fixture supplies SSR light children")
      .to.be.greaterThan(0);
    for (const child of lightChildren) {
      expect(
        child.assignedSlot,
        `light child <${child.tagName.toLowerCase()}> must not assign to any slot`
      ).to.equal(null);
    }
  });

  it("Gap 3: <composer-inline-reply> collapses to display:none when not available", async () => {
    const el = await fixture(html`
      <composer-inline-reply target-label="Root blip"></composer-inline-reply>
    `);
    await el.updateComplete;
    expect(el.hasAttribute("available")).to.equal(false);
    const computed = window.getComputedStyle(el).display;
    expect(
      computed,
      "composer-inline-reply must collapse pre-compose so the reply textarea is not always visible"
    ).to.equal("none");
  });

  it("Gap 3: <composer-inline-reply> renders normally once available is set", async () => {
    // V-2 (#1100): the "Reply target: <id>" line is dev-only; pass
    // debug-overlay so this gap test can still assert the label
    // round-trips through the label property when overlay is on.
    const el = await fixture(html`
      <composer-inline-reply available target-label="Root blip" debug-overlay></composer-inline-reply>
    `);
    await el.updateComplete;
    expect(window.getComputedStyle(el).display).to.equal("block");
    expect(el.renderRoot.textContent).to.include("Root blip");
  });
});

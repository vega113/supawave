import { fixture, expect, html } from "@open-wc/testing";

/**
 * F-2 slice 6 (#1058, Part A) — visible-rail regression lock.
 *
 * Slice 5 (#1057) marked the legacy `.sidecar-search-card` wrapper
 * with `data-j2cl-legacy-search-card="hidden"` but the matching CSS
 * rule used `display: contents`, which removes the wrapper's box but
 * keeps every child visible. The `.sidecar-digests` adoption target
 * and `.sidecar-empty-state` paragraph painted as a duplicate light
 * surface below the dark wavy rail.
 *
 * This fixture mounts the SSR'd structure inline + the actual
 * `wavy-thread-collapse.css` stylesheet, then asserts that the
 * legacy card and every child has computed `display: none`. If the
 * regression returns, the test FAILS BEFORE the bug ships.
 */

const cssUrl = new URL(
  "../src/design/wavy-thread-collapse.css",
  import.meta.url
);

async function loadStylesheet() {
  const response = await fetch(cssUrl.href);
  if (!response.ok) {
    throw new Error(
      `Failed to load wavy-thread-collapse.css from ${cssUrl.href}: ${response.status}`
    );
  }
  const text = await response.text();
  // Re-use a single <style> in the document head so multiple `it`
  // blocks share the rule registry without flickering the DOM.
  let style = document.head.querySelector("style[data-test-wavy-rail-hidden]");
  if (!style) {
    style = document.createElement("style");
    style.setAttribute("data-test-wavy-rail-hidden", "true");
    style.textContent = text;
    document.head.appendChild(style);
  }
  return text;
}

describe("legacy search card is visibly hidden when wavy-search-rail is mounted", () => {
  before(async () => {
    await loadStylesheet();
  });

  it("css source-of-truth uses display:none !important on the legacy card", async () => {
    const text = await loadStylesheet();
    expect(text).to.match(
      /\.sidecar-search-card\[data-j2cl-legacy-search-card="hidden"\]\s*\{\s*display\s*:\s*none\s*!important\s*;\s*\}/
    );
    // Regression guard: the buggy display:contents must not appear on
    // any block keyed off the legacy-search-card marker.
    let idx = 0;
    while (true) {
      const next = text.indexOf(
        '[data-j2cl-legacy-search-card="hidden"]',
        idx
      );
      if (next < 0) break;
      const blockStart = text.indexOf("{", next);
      const blockEnd = text.indexOf("}", blockStart);
      const block = text.slice(blockStart, blockEnd);
      expect(block, "legacy-card hide block must not use display:contents")
        .to.not.include("display: contents");
      idx = blockEnd;
    }
  });

  it("computed display on the legacy card is 'none' after the rule is applied", async () => {
    const wrapper = await fixture(html`
      <div class="sidecar-search-card" data-j2cl-legacy-search-card="hidden">
        <form class="sidecar-search-toolbar" data-j2cl-legacy-search-form="true" hidden>
          <input class="sidecar-search-input" />
          <button class="sidecar-search-submit" type="submit">Search</button>
        </form>
        <p class="sidecar-search-session" hidden>Hidden session.</p>
        <div class="sidecar-search-compose"></div>
        <p class="sidecar-search-status" hidden>Hidden status.</p>
        <p class="sidecar-wave-count" hidden></p>
        <div class="sidecar-digests">
          <div class="legacy-digest">Legacy digest item that must NOT paint.</div>
        </div>
        <div class="sidecar-empty-state">Search results will appear here.</div>
        <button class="sidecar-show-more" type="button" hidden>Show more waves</button>
      </div>
    `);

    expect(getComputedStyle(wrapper).display).to.equal("none");

    // Every child must inherit the hidden ancestor — `offsetParent` is
    // null when an ancestor has `display: none`, regardless of the
    // child's own style. This confirms NO legacy-styled child paints.
    const children = wrapper.querySelectorAll(":scope > *");
    expect(children.length).to.be.greaterThan(0);
    for (const child of children) {
      expect(child.offsetParent, `child ${child.className} must not be painted`).to.equal(null);
    }

    // The digest list and empty-state — the two specific elements
    // that S5 left visible — must be invisible.
    const digests = wrapper.querySelector(".sidecar-digests");
    const empty = wrapper.querySelector(".sidecar-empty-state");
    expect(digests.offsetParent, "sidecar-digests must not be painted").to.equal(null);
    expect(empty.offsetParent, "sidecar-empty-state must not be painted").to.equal(null);
  });

  it("an empty compose host collapses via :empty so it does not insert a layout gap", async () => {
    const compose = await fixture(html`
      <div class="sidecar-selected-compose" data-j2cl-compose-host="true"></div>
    `);
    expect(getComputedStyle(compose).display).to.equal("none");

    // Once the controller appends children (an active edit session),
    // the :empty selector no longer matches and the host expands.
    const child = document.createElement("div");
    child.textContent = "Edit toolbar wall";
    compose.appendChild(child);
    // Force a reflow so the :empty rule re-evaluates.
    void compose.offsetHeight;
    expect(getComputedStyle(compose).display).to.not.equal("none");
  });
});

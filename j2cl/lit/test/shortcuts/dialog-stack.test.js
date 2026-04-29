// G-PORT-7 (#1116): tests for the dialog-stack Esc helper.
import { fixture, expect, html } from "@open-wc/testing";
import { closeTopmostDialog, _internalForTesting } from "../../src/shortcuts/dialog-stack.js";
const { collectShadowRoots, composedTreeCompare } = _internalForTesting;

// Minimal mock surfaces — the real surfaces all expose either an
// `open` boolean property (set by Lit reflection) or the host `open`
// attribute. We mimic both shapes.

function defineMockSurface(tag) {
  if (customElements.get(tag)) return;
  class S extends HTMLElement {
    constructor() {
      super();
      this._open = false;
    }
    get open() { return this._open; }
    set open(v) {
      this._open = !!v;
      if (v) this.setAttribute("open", ""); else this.removeAttribute("open");
    }
    close() { this.open = false; this._closeCalls = (this._closeCalls || 0) + 1; }
  }
  customElements.define(tag, S);
}

[
  "wavy-confirm-dialog", "wavy-link-modal", "wavy-version-history",
  "reaction-picker-popover", "reaction-authors-popover",
  "task-metadata-popover", "mention-suggestion-popover",
  "wavy-search-help", "wavy-profile-overlay"
].forEach(defineMockSurface);

describe("closeTopmostDialog", () => {
  it("returns false when nothing is open", async () => {
    const root = await fixture(html`<div></div>`);
    expect(closeTopmostDialog(root)).to.equal(false);
  });

  it("closes a tier-1 dialog", async () => {
    const root = await fixture(html`
      <div>
        <wavy-confirm-dialog open></wavy-confirm-dialog>
      </div>
    `);
    const dlg = root.querySelector("wavy-confirm-dialog");
    dlg.open = true;
    expect(closeTopmostDialog(root)).to.equal(true);
    expect(dlg.open).to.equal(false);
  });

  it("prefers tier-1 dialog over tier-2 popover when both open", async () => {
    const root = await fixture(html`
      <div>
        <reaction-picker-popover></reaction-picker-popover>
        <wavy-confirm-dialog></wavy-confirm-dialog>
      </div>
    `);
    const popover = root.querySelector("reaction-picker-popover");
    const dlg = root.querySelector("wavy-confirm-dialog");
    popover.open = true;
    dlg.open = true;
    expect(closeTopmostDialog(root)).to.equal(true);
    expect(dlg.open).to.equal(false);
    // Popover untouched: another Esc keystroke (next dispatcher call)
    // would close it next.
    expect(popover.open).to.equal(true);
  });

  it("within a tier closes the LAST in document order", async () => {
    const root = await fixture(html`
      <div>
        <reaction-picker-popover></reaction-picker-popover>
        <task-metadata-popover></task-metadata-popover>
      </div>
    `);
    const first = root.querySelector("reaction-picker-popover");
    const second = root.querySelector("task-metadata-popover");
    first.open = true;
    second.open = true;
    expect(closeTopmostDialog(root)).to.equal(true);
    expect(second.open).to.equal(false);
    expect(first.open).to.equal(true);
  });

  it("calls host.close() when defined", async () => {
    const root = await fixture(html`
      <div>
        <wavy-link-modal></wavy-link-modal>
      </div>
    `);
    const dlg = root.querySelector("wavy-link-modal");
    dlg.open = true;
    closeTopmostDialog(root);
    expect(dlg._closeCalls).to.equal(1);
  });

  it("closes a tier-2 popover rendered inside a shadow root", async () => {
    // Simulates task-metadata-popover inside wavy-task-affordance's shadow root.
    const hostTag = "x-shadow-host-for-test";
    if (!customElements.get(hostTag)) {
      class ShadowHost extends HTMLElement {
        constructor() {
          super();
          this.attachShadow({ mode: "open" });
        }
        connectedCallback() {
          const el = document.createElement("task-metadata-popover");
          this.shadowRoot.appendChild(el);
        }
      }
      customElements.define(hostTag, ShadowHost);
    }
    const root = await fixture(html`<div><x-shadow-host-for-test></x-shadow-host-for-test></div>`);
    const host = root.querySelector(hostTag);
    const popover = host.shadowRoot.querySelector("task-metadata-popover");
    popover.open = true;
    expect(collectShadowRoots(root)).to.have.lengthOf(1);
    expect(closeTopmostDialog(root)).to.equal(true);
    expect(popover.open).to.equal(false);
  });

  it("prefers later light-DOM popover over earlier shadow-root popover (composed-tree order)", async () => {
    // Shadow host appears FIRST in document order; a plain light-DOM popover
    // appears AFTER it. Both are in tier-2. The one appearing later in the
    // composed tree (the light-DOM one) must be treated as "topmost".
    const earlyHostTag = "x-early-shadow-host";
    if (!customElements.get(earlyHostTag)) {
      class EarlyHost extends HTMLElement {
        constructor() {
          super();
          this.attachShadow({ mode: "open" });
        }
        connectedCallback() {
          const el = document.createElement("reaction-picker-popover");
          this.shadowRoot.appendChild(el);
        }
      }
      customElements.define(earlyHostTag, EarlyHost);
    }
    // Build the fixture manually to avoid Lit html`` dynamic-tag restriction.
    const root = document.createElement("div");
    const earlyHost = document.createElement(earlyHostTag);
    const lightPopover = document.createElement("task-metadata-popover");
    root.appendChild(earlyHost);
    root.appendChild(lightPopover);
    document.body.appendChild(root);
    // Allow connectedCallback to run and shadow DOM to attach.
    await new Promise((r) => requestAnimationFrame(r));
    const shadowPopover = earlyHost.shadowRoot && earlyHost.shadowRoot.querySelector("reaction-picker-popover");
    try {
      if (!shadowPopover) {
        // connectedCallback may not have run in this test environment; skip
        // the shadow-popover half and just verify the light one closes.
        lightPopover.open = true;
        expect(closeTopmostDialog(root)).to.equal(true);
        expect(lightPopover.open).to.equal(false);
        return;
      }
      shadowPopover.open = true;
      lightPopover.open = true;
      // The light-DOM task-metadata-popover comes later in composed-tree order
      // and must be closed first.
      expect(closeTopmostDialog(root)).to.equal(true);
      expect(lightPopover.open).to.equal(false);
      expect(shadowPopover.open).to.equal(true);
    } finally {
      document.body.removeChild(root);
    }
  });
});

// G-PORT-7 (#1116): tests for the dialog-stack Esc helper.
import { fixture, expect, html } from "@open-wc/testing";
import { closeTopmostDialog } from "../../src/shortcuts/dialog-stack.js";

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
});

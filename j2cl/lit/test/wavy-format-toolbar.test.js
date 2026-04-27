import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-format-toolbar.js";
import {
  DAILY_RICH_EDIT_ACTION_IDS
} from "../src/elements/wavy-format-toolbar.js";

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
  throw new Error("wavy-tokens.css did not apply within 1000ms");
}

before(async () => {
  ensureWavyTokensLoaded();
  await waitForStylesheet();
});

describe("<wavy-format-toolbar>", () => {
  it("ships the daily-rich-edit subset of H.* actions", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const buttons = el.renderRoot.querySelectorAll("[data-toolbar-action]");
    const actionIds = Array.from(buttons).map((b) => b.getAttribute("data-toolbar-action"));
    expect(actionIds).to.include.members([
      "bold",
      "italic",
      "underline",
      "strikethrough",
      "heading",
      "unordered-list",
      "ordered-list",
      "indent",
      "outdent",
      "align-left",
      "align-center",
      "align-right",
      "rtl",
      "link",
      "unlink",
      "clear-formatting"
    ]);
    expect(actionIds.length).to.equal(DAILY_RICH_EDIT_ACTION_IDS.length);
  });

  it("hides itself when selection descriptor is collapsed", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = { collapsed: true };
    await el.updateComplete;
    await new Promise((r) => requestAnimationFrame(r));
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("shows and positions itself when selection has bounding rect", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 200, left: 200, width: 80, height: 20 },
      activeAnnotations: []
    };
    await el.updateComplete;
    await new Promise((r) => requestAnimationFrame(r));
    expect(el.hasAttribute("hidden")).to.equal(false);
    expect(el.style.transform).to.match(/translate\(/);
    // Sanity: not the off-screen sentinel.
    expect(el.style.transform).to.not.equal("translate(-9999px, -9999px)");
  });

  it("reflects active annotations as pressed state on toggle buttons", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 100, left: 100, width: 60, height: 18 },
      activeAnnotations: ["strong", "em"]
    };
    await el.updateComplete;
    await new Promise((r) => requestAnimationFrame(r));
    const bold = el.renderRoot.querySelector('[data-toolbar-action="bold"]');
    const italic = el.renderRoot.querySelector('[data-toolbar-action="italic"]');
    const underline = el.renderRoot.querySelector('[data-toolbar-action="underline"]');
    expect(bold.hasAttribute("pressed")).to.equal(true);
    expect(italic.hasAttribute("pressed")).to.equal(true);
    expect(underline.hasAttribute("pressed")).to.equal(false);
  });

  it("re-dispatches toolbar-action as wavy-format-toolbar-action with the descriptor", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 100, left: 100, width: 60, height: 18 },
      activeAnnotations: []
    };
    await el.updateComplete;
    const bold = el.renderRoot.querySelector('[data-toolbar-action="bold"]');
    const trigger = oneEvent(el, "wavy-format-toolbar-action");
    bold.dispatchEvent(
      new CustomEvent("toolbar-action", {
        detail: { action: "bold" },
        bubbles: true,
        composed: true
      })
    );
    const evt = await trigger;
    expect(evt.detail.actionId).to.equal("bold");
    expect(evt.detail.selectionDescriptor.activeAnnotations).to.deep.equal([]);
  });

  it("forwards the toolbar-extension slot to wavy-edit-toolbar", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const editToolbar = el.renderRoot.querySelector("wavy-edit-toolbar");
    const slot = editToolbar.renderRoot.querySelector("slot[name='toolbar-extension']");
    expect(slot).to.exist;
  });
});

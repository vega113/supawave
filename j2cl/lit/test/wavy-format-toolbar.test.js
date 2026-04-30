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
    expect(actionIds.slice(0, 9)).to.deep.equal([
      "bold",
      "italic",
      "underline",
      "strikethrough",
      "superscript",
      "subscript",
      "font-family",
      "font-size",
      "heading"
    ]);
    expect(actionIds).to.include.members([
      "bold",
      "italic",
      "underline",
      "strikethrough",
      "superscript",
      "subscript",
      "font-family",
      "font-size",
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
      "clear-formatting",
      // F-3.S2 (#1038, R-5.4 step 6 + H.20)
      "insert-task",
      // F-3.S4 (#1038, R-5.6 step 3 + H.19)
      "attachment-insert"
    ]);
    expect(actionIds.length).to.equal(DAILY_RICH_EDIT_ACTION_IDS.length);
  });

  it("renders Font and Size dropdowns and emits action value", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 100, left: 100, width: 60, height: 18 },
      activeAnnotations: []
    };
    await el.updateComplete;

    const font = el.renderRoot.querySelector('select[data-toolbar-action="font-family"]');
    const size = el.renderRoot.querySelector('select[data-toolbar-action="font-size"]');
    expect(font, "font dropdown must render").to.exist;
    expect(size, "size dropdown must render").to.exist;
    expect(font.getAttribute("aria-label")).to.equal("Font");
    expect(size.getAttribute("aria-label")).to.equal("Size");

    const fontEvent = oneEvent(el, "wavy-format-toolbar-action");
    font.value = "Georgia";
    font.dispatchEvent(new Event("change", { bubbles: true, composed: true }));
    const fontAction = await fontEvent;
    expect(fontAction.detail.actionId).to.equal("font-family");
    expect(fontAction.detail.value).to.equal("Georgia");
    expect(fontAction.detail.selectionDescriptor).to.equal(el.selectionDescriptor);

    const sizeEvent = oneEvent(el, "wavy-format-toolbar-action");
    size.value = "18px";
    size.dispatchEvent(new Event("change", { bubbles: true, composed: true }));
    const sizeAction = await sizeEvent;
    expect(sizeAction.detail.actionId).to.equal("font-size");
    expect(sizeAction.detail.value).to.equal("18px");
  });

  // F-3.S4 (#1038, R-5.6 step 3 + H.19): the paperclip button is
  // present and emits wavy-format-toolbar-action with actionId
  // "attachment-insert" so the controller's
  // handleAttachmentToolbarAction path opens the picker.
  it("includes the H.19 attachment paperclip button (R-5.6 step 3)", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const button = el.renderRoot.querySelector(
      'toolbar-button[data-toolbar-action="attachment-insert"]'
    );
    expect(button, "attachment-insert button must render").to.exist;
    expect(button.getAttribute("label")).to.equal("Attach file");

    const eventPromise = oneEvent(el, "wavy-format-toolbar-action");
    button.dispatchEvent(
      new CustomEvent("toolbar-action", {
        bubbles: true,
        composed: true,
        detail: { action: "attachment-insert" }
      })
    );
    const event = await eventPromise;
    expect(event.detail.actionId).to.equal("attachment-insert");
  });

  // F-3.S2 (#1038, R-5.4 step 6): the H.20 Insert-task button is
  // present and emits wavy-format-toolbar-action with the matching id.
  it("includes the H.20 Insert-task button (R-5.4 step 6)", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const button = el.renderRoot.querySelector('[data-toolbar-action="insert-task"]');
    expect(button).to.exist;
    expect(button.getAttribute("label")).to.equal("Insert task");
  });

  it("re-dispatches the insert-task action via wavy-format-toolbar-action", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 100, left: 100, width: 60, height: 18 },
      activeAnnotations: []
    };
    await el.updateComplete;
    const button = el.renderRoot.querySelector('[data-toolbar-action="insert-task"]');
    const trigger = oneEvent(el, "wavy-format-toolbar-action");
    button.dispatchEvent(
      new CustomEvent("toolbar-action", {
        detail: { action: "insert-task" },
        bubbles: true,
        composed: true
      })
    );
    const evt = await trigger;
    expect(evt.detail.actionId).to.equal("insert-task");
  });

  it("re-dispatches H.5/H.6 superscript and subscript actions", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    el.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 100, left: 100, width: 60, height: 18 },
      activeAnnotations: []
    };
    await el.updateComplete;

    for (const actionId of ["superscript", "subscript"]) {
      const button = el.renderRoot.querySelector(`[data-toolbar-action="${actionId}"]`);
      expect(button, `${actionId} button must render`).to.exist;
      const trigger = oneEvent(el, "wavy-format-toolbar-action");
      button.dispatchEvent(
        new CustomEvent("toolbar-action", {
          detail: { action: actionId },
          bubbles: true,
          composed: true
        })
      );
      const evt = await trigger;
      expect(evt.detail.actionId).to.equal(actionId);
    }
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

  // V-3 (#1101): each daily-rich-edit toolbar button renders an icon
  // glyph instead of a text label.
  it("renders an SVG icon inside every toolbar-button (V-3 visual swap)", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const buttons = el.renderRoot.querySelectorAll("toolbar-button");
    expect(buttons.length).to.be.greaterThan(0);
    buttons.forEach((b) => {
      const svg = b.renderRoot.querySelector("svg");
      expect(svg, `button ${b.getAttribute("data-toolbar-action")} must render an <svg>`).to.exist;
    });
  });

  // V-3 (#1101): explicit toolbar-divider siblings between groups so
  // the visual separator does not depend on a brittle ::slotted
  // sibling selector.
  it("emits at least one .toolbar-divider between groups", async () => {
    const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
    const dividers = el.renderRoot.querySelectorAll(".toolbar-divider");
    expect(dividers.length).to.be.greaterThan(0);
  });
});

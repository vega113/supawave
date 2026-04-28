import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/toolbar-button.js";

describe("<toolbar-button>", () => {
  it("renders a native button with stable action id and pressed state", async () => {
    const el = await fixture(html`
      <toolbar-button
        action="bold"
        label="Bold"
        toggle
        pressed
      ></toolbar-button>
    `);

    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-label")).to.equal("Bold");
    expect(button.getAttribute("aria-pressed")).to.equal("true");
    expect(button.dataset.action).to.equal("bold");
  });

  it("dispatches toolbar-action with the action id", async () => {
    const el = await fixture(html`
      <toolbar-button action="archive" label="Archive"></toolbar-button>
    `);
    const eventPromise = oneEvent(el, "toolbar-action");

    el.renderRoot.querySelector("button").click();

    expect((await eventPromise).detail.action).to.equal("archive");
  });

  it("does not dispatch toolbar-action without an action id", async () => {
    const el = await fixture(html`
      <toolbar-button label="No action"></toolbar-button>
    `);
    let dispatched = false;
    el.addEventListener("toolbar-action", () => {
      dispatched = true;
    });

    el.renderRoot.querySelector("button").click();

    expect(dispatched).to.equal(false);
  });

  // V-3 (#1101): icon mode renders an SVG glyph instead of the text
  // label, while keeping the label bound to aria-label and title so
  // assistive tech and tooltips still work.
  it("renders an SVG glyph and keeps label as aria-label + title when icon is set", async () => {
    const el = await fixture(html`
      <toolbar-button action="bold" icon="bold" label="Bold"></toolbar-button>
    `);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-label")).to.equal("Bold");
    expect(button.getAttribute("title")).to.equal("Bold");
    const svg = button.querySelector("svg");
    expect(svg, "icon mode must render an <svg>").to.exist;
    expect(button.textContent.trim()).to.equal("");
  });

  it("falls back to text label when icon is unset", async () => {
    const el = await fixture(html`
      <toolbar-button action="archive" label="Archive"></toolbar-button>
    `);
    const button = el.renderRoot.querySelector("button");
    expect(button.querySelector("svg")).to.equal(null);
    expect(button.textContent.trim()).to.equal("Archive");
  });

  it("does not reflect an empty icon attribute for text-mode consumers", async () => {
    const el = await fixture(html`
      <toolbar-button action="archive" label="Archive"></toolbar-button>
    `);
    expect(el.hasAttribute("icon")).to.equal(false);
  });

  // V-3 (#1101): the title attribute is gated on icon presence so that
  // text-mode consumers (wave-blip-toolbar etc.) do not gain unexpected
  // hover tooltips from this slice. Per copilot review feedback.
  it("does not set the title attribute in text mode", async () => {
    const el = await fixture(html`
      <toolbar-button action="archive" label="Archive"></toolbar-button>
    `);
    const button = el.renderRoot.querySelector("button");
    expect(button.hasAttribute("title")).to.equal(false);
  });
});

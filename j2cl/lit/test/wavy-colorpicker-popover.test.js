import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-colorpicker-popover.js";
import { WAVY_COLOR_PALETTE } from "../src/format/color-options.js";

describe("<wavy-colorpicker-popover>", () => {
  it("renders the GWT-compatible palette as an ARIA grid", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const grid = el.renderRoot.querySelector("[role='grid']");
    const cells = el.renderRoot.querySelectorAll("[role='gridcell']");

    expect(grid).to.exist;
    expect(grid.getAttribute("aria-label")).to.equal("Text color palette");
    expect(cells.length).to.equal(WAVY_COLOR_PALETTE.length);
    expect(cells[0].getAttribute("aria-selected")).to.equal("true");
    expect(cells[0].getAttribute("data-color")).to.equal("rgb(0, 0, 0)");
  });

  it("emits the selected color and closes on click", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const selected = oneEvent(el, "wavy-colorpicker-color-selected");
    el.renderRoot.querySelector("[data-color='rgb(204, 0, 0)']").click();

    const event = await selected;
    expect(event.detail.color).to.equal("rgb(204, 0, 0)");
    expect(event.detail.mode).to.equal("text");
    expect(el.open).to.equal(false);
  });

  it("moves active cell with arrow keys and selects with Enter", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const grid = el.renderRoot.querySelector("[role='grid']");
    grid.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[aria-selected='true']").getAttribute("data-color"))
      .to.equal("rgb(67, 67, 67)");

    const selected = oneEvent(el, "wavy-colorpicker-color-selected");
    grid.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    const event = await selected;
    expect(event.detail.color).to.equal("rgb(67, 67, 67)");
  });

  it("emits a close event when dismissed with Escape", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const grid = el.renderRoot.querySelector("[role='grid']");
    const closed = oneEvent(el, "wavy-colorpicker-popover-closed");

    grid.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));

    const event = await closed;
    expect(event.detail.reason).to.equal("escape");
    expect(el.open).to.equal(false);
  });

  it("focuses the palette grid when opened for keyboard users", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover></wavy-colorpicker-popover>`);
    el.open = true;
    await el.updateComplete;
    await el.updateComplete;

    expect(el.renderRoot.activeElement).to.equal(el.renderRoot.querySelector("[role='grid']"));
  });

  it("keeps the palette grid inside the viewport when the toolbar slot is offscreen", async () => {
    const host = await fixture(html`<div
      style="position: fixed; top: 48px; left: calc(100vw + 96px);"
    ><wavy-colorpicker-popover open></wavy-colorpicker-popover></div>`);
    const el = host.querySelector("wavy-colorpicker-popover");
    await el.updateComplete;
    await new Promise((resolve) => requestAnimationFrame(resolve));

    const rect = el.renderRoot.querySelector("[role='grid']").getBoundingClientRect();
    expect(rect.left).to.be.at.least(8);
    expect(rect.right).to.be.at.most(window.innerWidth - 7);
    expect(document.elementFromPoint(rect.left + 4, rect.top + 4)).to.equal(el);

    el.open = false;
    await el.updateComplete;
    expect(el.style.transform).to.equal("");
    expect(el.style.width).to.equal("");
    expect(el.style.height).to.equal("");
  });

  it("uses highlight labeling when mode is highlight", async () => {
    const el = await fixture(
      html`<wavy-colorpicker-popover open mode="highlight"></wavy-colorpicker-popover>`
    );
    expect(el.renderRoot.querySelector("[role='grid']").getAttribute("aria-label"))
      .to.equal("Highlight color palette");
  });
});

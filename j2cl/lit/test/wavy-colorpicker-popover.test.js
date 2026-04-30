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

  it("uses highlight labeling when mode is highlight", async () => {
    const el = await fixture(
      html`<wavy-colorpicker-popover open mode="highlight"></wavy-colorpicker-popover>`
    );
    expect(el.renderRoot.querySelector("[role='grid']").getAttribute("aria-label"))
      .to.equal("Highlight color palette");
  });
});

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/toolbar-overflow-menu.js";

describe("<toolbar-overflow-menu>", () => {
  it("opens from keyboard, focuses the first item, and closes on Escape", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <button data-action="history">History</button>
        <button data-action="clear-formatting">Clear formatting</button>
      </toolbar-overflow-menu>
    `);
    const trigger = el.renderRoot.querySelector("button[aria-haspopup='menu']");

    trigger.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    expect(el.open).to.equal(true);
    expect(el.querySelector("[data-action='history']")).to.equal(document.activeElement);

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await el.updateComplete;

    expect(el.open).to.equal(false);
    expect(trigger).to.equal(el.shadowRoot.activeElement);
  });

  it("moves focus with arrow keys and redispatches toolbar-action", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <button data-action="history">History</button>
        <button data-action="clear-formatting">Clear formatting</button>
      </toolbar-overflow-menu>
    `);

    el.open = true;
    await el.updateComplete;
    el.focusFirstItem();
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
    expect(el.querySelector("[data-action='clear-formatting']")).to.equal(document.activeElement);

    const eventPromise = oneEvent(el, "toolbar-action");
    el.querySelector("[data-action='clear-formatting']").click();
    expect((await eventPromise).detail.action).to.equal("clear-formatting");
  });
});

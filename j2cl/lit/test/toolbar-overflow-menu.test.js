import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/toolbar-button.js";
import "../src/elements/toolbar-overflow-menu.js";

describe("<toolbar-overflow-menu>", () => {
  it("opens from keyboard, focuses the first item, and closes on Escape", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <button data-action="history">History</button>
        <button data-action="clear-formatting">Clear formatting</button>
      </toolbar-overflow-menu>
    `);
    const trigger = el.renderRoot.querySelector("button[aria-haspopup='true']");

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

  it("includes slotted toolbar-button elements in keyboard focus order", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <toolbar-button action="history" label="History"></toolbar-button>
        <toolbar-button action="clear-formatting" label="Clear formatting"></toolbar-button>
      </toolbar-overflow-menu>
    `);
    const trigger = el.renderRoot.querySelector("button[aria-haspopup='true']");
    const historyButton = el.querySelector("toolbar-button[action='history']");
    const clearButton = el.querySelector("toolbar-button[action='clear-formatting']");

    trigger.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    expect(el.open).to.equal(true);
    expect(historyButton.renderRoot.querySelector("button")).to.equal(
      historyButton.shadowRoot.activeElement
    );

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));

    expect(clearButton.renderRoot.querySelector("button")).to.equal(
      clearButton.shadowRoot.activeElement
    );
  });

  it("skips disabled slotted toolbar-button elements during keyboard navigation", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <button data-action="history">History</button>
        <toolbar-button action="archive" label="Archive" disabled></toolbar-button>
        <toolbar-button action="clear-formatting" label="Clear formatting"></toolbar-button>
      </toolbar-overflow-menu>
    `);
    const trigger = el.renderRoot.querySelector("button[aria-haspopup='true']");
    const historyButton = el.querySelector("[data-action='history']");
    const clearButton = el.querySelector("toolbar-button[action='clear-formatting']");

    trigger.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    expect(historyButton).to.equal(document.activeElement);

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));

    expect(clearButton.renderRoot.querySelector("button")).to.equal(
      clearButton.shadowRoot.activeElement
    );

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowUp", bubbles: true }));

    expect(historyButton).to.equal(document.activeElement);
  });

  it("closes and forwards actions from slotted toolbar-button elements", async () => {
    const el = await fixture(html`
      <toolbar-overflow-menu label="More toolbar actions">
        <toolbar-button action="history" label="History"></toolbar-button>
      </toolbar-overflow-menu>
    `);

    el.open = true;
    await el.updateComplete;
    const trigger = el.renderRoot.querySelector("button[aria-haspopup='true']");
    let actionCount = 0;
    // Count alongside oneEvent so this catches accidental duplicate redispatches.
    el.addEventListener("toolbar-action", () => {
      actionCount += 1;
    });
    const eventPromise = oneEvent(el, "toolbar-action");

    el.querySelector("toolbar-button").renderRoot.querySelector("button").click();

    expect((await eventPromise).detail.action).to.equal("history");
    await el.updateComplete;
    expect(actionCount).to.equal(1);
    expect(el.open).to.equal(false);
    expect(trigger).to.equal(el.shadowRoot.activeElement);
  });
});

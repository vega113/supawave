import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/reaction-picker-popover.js";

describe("<reaction-picker-popover>", () => {
  it("focuses the first emoji when opened and emits selected emoji", async () => {
    const el = await fixture(html`
      <reaction-picker-popover
        blip-id="b+1"
        .emojis=${["tada", "thumbs_up"]}
        open
      ></reaction-picker-popover>
    `);
    const first = el.renderRoot.querySelector("[data-emoji='tada']");
    expect(el.renderRoot.querySelector("[role='menu']").getAttribute("aria-orientation")).to.equal(
      "horizontal"
    );
    expect(first).to.equal(el.shadowRoot.activeElement);

    const eventPromise = oneEvent(el, "reaction-pick");
    first.click();
    expect((await eventPromise).detail).to.deep.equal({ blipId: "b+1", emoji: "tada" });
  });

  it("keeps a single active picker and closes on Escape", async () => {
    const first = await fixture(html`
      <reaction-picker-popover blip-id="b+1" .emojis=${["tada"]} open></reaction-picker-popover>
    `);
    const supersededPromise = oneEvent(first, "overlay-close");
    const second = await fixture(html`
      <reaction-picker-popover blip-id="b+2" .emojis=${["wave"]} open></reaction-picker-popover>
    `);

    expect((await supersededPromise).detail).to.deep.equal({
      reason: "superseded",
      focusTargetId: ""
    });
    expect(first.open).to.equal(false);
    expect(second.open).to.equal(true);

    const closePromise = oneEvent(second, "overlay-close");
    second.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect((await closePromise).detail).to.deep.equal({ reason: "escape", focusTargetId: "" });
  });

  it("moves focus through the reaction menu with arrow, Home, and End keys", async () => {
    const el = await fixture(html`
      <reaction-picker-popover
        blip-id="b+1"
        .emojis=${["tada", "wave", "heart"]}
        open
      ></reaction-picker-popover>
    `);

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
    expect(el.shadowRoot.activeElement.dataset.emoji).to.equal("wave");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
    expect(el.shadowRoot.activeElement.dataset.emoji).to.equal("wave");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }));
    expect(el.shadowRoot.activeElement.dataset.emoji).to.equal("tada");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "End", bubbles: true }));
    expect(el.shadowRoot.activeElement.dataset.emoji).to.equal("heart");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Home", bubbles: true }));
    expect(el.shadowRoot.activeElement.dataset.emoji).to.equal("tada");
  });

  it("ignores navigation keys when the picker has no buttons", async () => {
    const el = await fixture(html`
      <reaction-picker-popover blip-id="b+empty" open></reaction-picker-popover>
    `);

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Home", bubbles: true }));
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "End", bubbles: true }));

    expect(el.renderRoot.querySelectorAll("button").length).to.equal(0);
  });

  it("clears the active picker reference when disconnected", async () => {
    const first = await fixture(html`
      <reaction-picker-popover blip-id="b+1" .emojis=${["tada"]} open></reaction-picker-popover>
    `);
    first.remove();

    const second = await fixture(html`
      <reaction-picker-popover blip-id="b+2" .emojis=${["wave"]} open></reaction-picker-popover>
    `);

    expect(second.open).to.equal(true);
  });
});

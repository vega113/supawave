import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/mention-suggestion-popover.js";

describe("<mention-suggestion-popover>", () => {
  const candidates = [
    { address: "alice@example.com", displayName: "Alice Adams" },
    { address: "bob@example.com", displayName: "Bob Brown" }
  ];

  it("renders listbox options and announces candidate count", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);

    const listbox = el.renderRoot.querySelector("[role='listbox']");
    const options = el.renderRoot.querySelectorAll("[role='option']");
    expect(listbox).to.equal(el.shadowRoot.activeElement);
    expect(listbox.getAttribute("tabindex")).to.equal("-1");
    expect(listbox.getAttribute("aria-activedescendant")).to.equal(options[0].id);
    expect(options[0].getAttribute("aria-selected")).to.equal("true");
    expect(options[1].dataset.address).to.equal("bob@example.com");
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.include(
      "2 mention suggestions"
    );
  });

  it("moves active option with arrows and selects with Enter", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "mention-select");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      el.renderRoot.querySelectorAll("[role='option']")[1].id
    );
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));

    expect((await eventPromise).detail.address).to.equal("bob@example.com");
  });

  it("selects candidates from pointer activation", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "mention-select");

    el.renderRoot.querySelectorAll("[role='option']")[1].click();

    expect((await eventPromise).detail).to.deep.equal({
      address: "bob@example.com",
      displayName: "Bob Brown"
    });
  });

  it("wraps with ArrowUp and selects with Tab", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "mention-select");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowUp", bubbles: true }));
    await el.updateComplete;
    expect(
      el.renderRoot.querySelectorAll("[role='option']")[1].getAttribute("aria-selected")
    ).to.equal("true");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Tab", bubbles: true }));

    expect((await eventPromise).detail.address).to.equal("bob@example.com");
  });

  it("emits overlay-close on Escape and handles empty candidates", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${[]}
        focus-target-id="composer-caret"
        open
      ></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "overlay-close");

    expect(el.renderRoot.textContent).to.include("No mention matches");
    expect(el.renderRoot.querySelector("[role='listbox']").hasAttribute("aria-activedescendant")).to.equal(
      false
    );
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.include(
      "No mention suggestions"
    );

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));

    expect((await eventPromise).detail).to.deep.equal({
      reason: "escape",
      focusTargetId: "composer-caret"
    });
  });

  it("does not trap Tab when no candidates are selectable", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${[]} open></mention-suggestion-popover>
    `);
    const event = new KeyboardEvent("keydown", {
      key: "Tab",
      bubbles: true,
      cancelable: true
    });

    el.dispatchEvent(event);

    expect(event.defaultPrevented).to.equal(false);
  });

  it("does not trap Enter when no candidates are selectable", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${[]} open></mention-suggestion-popover>
    `);
    const event = new KeyboardEvent("keydown", {
      key: "Enter",
      bubbles: true,
      cancelable: true
    });

    el.dispatchEvent(event);

    expect(event.defaultPrevented).to.equal(false);
  });

  it("traps Enter when a candidate is selectable", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const event = new KeyboardEvent("keydown", {
      key: "Enter",
      bubbles: true,
      cancelable: true
    });

    el.dispatchEvent(event);

    expect(event.defaultPrevented).to.equal(true);
  });

  it("keeps option ids unique across popover instances", async () => {
    const first = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const second = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);

    expect(first.renderRoot.querySelector("[role='option']").id).to.not.equal(
      second.renderRoot.querySelector("[role='option']").id
    );
  });

  it("clamps active selection when candidates shrink", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        active-index="5"
        open
      ></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "mention-select");

    el.candidates = [{ address: "solo@example.com", displayName: "Solo" }];
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      el.renderRoot.querySelector("[role='option']").id
    );
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));

    expect((await eventPromise).detail.address).to.equal("solo@example.com");
  });

  it("filters malformed candidate entries before rendering", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${[null, "bad", { address: "valid@example.com" }]}
        open
      ></mention-suggestion-popover>
    `);

    const options = el.renderRoot.querySelectorAll("[role='option']");
    expect(options.length).to.equal(1);
    expect(options[0].dataset.address).to.equal("valid@example.com");
  });

  it("does not consume Enter when there are no candidates", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${[]} open></mention-suggestion-popover>
    `);

    const enterEvent = new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true });
    el.dispatchEvent(enterEvent);

    expect(enterEvent.defaultPrevented).to.equal(false);
  });
});

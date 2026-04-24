import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/reaction-authors-popover.js";

describe("<reaction-authors-popover>", () => {
  it("renders author list semantics and empty state", async () => {
    const el = await fixture(html`
      <reaction-authors-popover
        emoji="tada"
        reaction-label="party popper"
        .authors=${["alice@example.com", "bob@example.com"]}
        open
      ></reaction-authors-popover>
    `);

    expect(el.renderRoot.querySelector("[role='region']").getAttribute("aria-label")).to.equal(
      "Authors for party popper"
    );
    expect(el.renderRoot.querySelector(".popover")).to.equal(el.shadowRoot.activeElement);
    expect(el.renderRoot.querySelectorAll("li").length).to.equal(2);

    el.authors = [];
    await el.updateComplete;
    expect(el.renderRoot.textContent).to.include("No reactions yet");
  });

  it("closes from Escape and emits focus restoration target", async () => {
    const el = await fixture(html`
      <reaction-authors-popover
        emoji="tada"
        focus-target-id="reaction-tada"
        .authors=${["alice@example.com"]}
        open
      ></reaction-authors-popover>
    `);
    const eventPromise = oneEvent(el, "overlay-close");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));

    expect((await eventPromise).detail).to.deep.equal({
      reason: "escape",
      focusTargetId: "reaction-tada"
    });
  });

  it("focuses the popover surface again after reopening", async () => {
    const el = await fixture(html`
      <reaction-authors-popover emoji="tada" .authors=${[]} open></reaction-authors-popover>
    `);

    el.close("test");
    await el.updateComplete;
    el.open = true;
    await el.updateComplete;

    expect(el.renderRoot.querySelector(".popover")).to.equal(el.shadowRoot.activeElement);
  });
});

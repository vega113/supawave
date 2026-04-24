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
});

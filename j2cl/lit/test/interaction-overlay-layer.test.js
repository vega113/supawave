import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/interaction-overlay-layer.js";

describe("<interaction-overlay-layer>", () => {
  it("wraps non-modal overlay content and dispatches close with focus target", async () => {
    const el = await fixture(html`
      <interaction-overlay-layer open focus-target-id="trigger-1">
        <button>Overlay action</button>
      </interaction-overlay-layer>
    `);

    expect(el.renderRoot.querySelector("[part='surface']").getAttribute("role")).to.equal(
      "group"
    );
    expect(el.renderRoot.querySelector("[part='surface']").hasAttribute("aria-modal")).to.equal(
      false
    );
    expect(getComputedStyle(el.renderRoot.querySelector("[part='surface']")).position).to.equal(
      "relative"
    );

    const eventPromise = oneEvent(el, "overlay-close");
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect((await eventPromise).detail).to.deep.equal({
      reason: "escape",
      focusTargetId: "trigger-1"
    });
  });

  it("supports modal role and hidden closed state", async () => {
    const el = await fixture(html`
      <interaction-overlay-layer modal label="Task details">
        <button id="first">First</button>
        <button id="last">Last</button>
      </interaction-overlay-layer>
    `);

    expect(el.renderRoot.querySelector("[part='surface']")).to.equal(null);

    el.open = true;
    await el.updateComplete;

    const surface = el.renderRoot.querySelector("[part='surface']");
    expect(surface.getAttribute("role")).to.equal("dialog");
    expect(surface.getAttribute("aria-modal")).to.equal("true");
    expect(surface.getAttribute("aria-label")).to.equal("Task details");
    expect(surface).to.equal(el.shadowRoot.activeElement);

    el.querySelector("#last").focus();
    el.querySelector("#last").dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );
    expect(document.activeElement).to.equal(el.querySelector("#first"));

    el.querySelector("#first").dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        shiftKey: true,
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );
    expect(document.activeElement).to.equal(el.querySelector("#last"));
  });
});

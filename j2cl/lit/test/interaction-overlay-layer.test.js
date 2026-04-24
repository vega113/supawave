import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/interaction-overlay-layer.js";

class ShadowOverlayControls extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" }).innerHTML = `
      <button id="first">First</button>
      <button id="last">Last</button>
    `;
  }
}

if (!customElements.get("shadow-overlay-controls")) {
  customElements.define("shadow-overlay-controls", ShadowOverlayControls);
}

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

  it("does not close when slotted content already handled Escape", async () => {
    const el = await fixture(html`
      <interaction-overlay-layer open modal label="Nested overlay">
        <button id="nested">Nested close</button>
      </interaction-overlay-layer>
    `);
    let closeCount = 0;
    const nested = el.querySelector("#nested");
    el.addEventListener("overlay-close", () => {
      closeCount += 1;
    });
    nested.addEventListener("keydown", event => {
      event.preventDefault();
    });

    nested.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );

    expect(closeCount).to.equal(0);
    expect(el.open).to.equal(true);
  });

  it("traps modal focus through slotted custom element shadow controls", async () => {
    const el = await fixture(html`
      <interaction-overlay-layer open modal label="Shadow task details">
        <shadow-overlay-controls></shadow-overlay-controls>
      </interaction-overlay-layer>
    `);
    const controls = el.querySelector("shadow-overlay-controls");
    const first = controls.shadowRoot.querySelector("#first");
    const last = controls.shadowRoot.querySelector("#last");

    first.focus();
    first.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        shiftKey: true,
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );
    expect(controls.shadowRoot.activeElement).to.equal(last);

    last.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );
    expect(controls.shadowRoot.activeElement).to.equal(first);
  });
});

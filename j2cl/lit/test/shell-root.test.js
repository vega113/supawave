import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-root.js";

describe("<shell-root>", () => {
  it("renders the signed-in layout slots", async () => {
    const el = await fixture(html`<shell-root></shell-root>`);
    expect(el.renderRoot.querySelector("slot[name='skip-link']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='nav']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='splitter']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='main']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
  });

  it("exposes the rail-extension slot for plugin payloads (F-4 / R-4.7)", async () => {
    const el = await fixture(html`<shell-root></shell-root>`);
    expect(el.renderRoot.querySelector("slot[name='rail-extension']")).to.exist;
  });

  it("projects light-DOM children with slot=rail-extension into the new slot", async () => {
    const el = await fixture(html`
      <shell-root>
        <div slot="rail-extension" id="plugin-mount">plugin payload</div>
      </shell-root>
    `);
    const slot = el.renderRoot.querySelector("slot[name='rail-extension']");
    const assigned = slot.assignedNodes({ flatten: true });
    const ids = assigned
      .filter((n) => n.nodeType === Node.ELEMENT_NODE)
      .map((n) => n.id);
    expect(ids).to.include("plugin-mount");
  });

  it("projects a resize splitter between nav and main", async () => {
    const el = await fixture(html`
      <shell-root>
        <button slot="splitter" id="search-splitter">resize</button>
      </shell-root>
    `);
    const slot = el.renderRoot.querySelector("slot[name='splitter']");
    const assigned = slot.assignedElements({ flatten: true });
    expect(assigned.map((node) => node.id)).to.include("search-splitter");
  });

  it("keyboard resizing updates and persists the search rail width", async () => {
    window.localStorage.removeItem("j2cl.searchRailWidth");
    const el = await fixture(html`
      <shell-root style="--j2cl-search-rail-width: 376px">
        <button slot="splitter" id="search-splitter">resize</button>
      </shell-root>
    `);
    const splitter = el.querySelector("#search-splitter");

    splitter.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );

    expect(el.style.getPropertyValue("--j2cl-search-rail-width")).to.equal("392px");
    expect(window.localStorage.getItem("j2cl.searchRailWidth")).to.equal("392");
  });

  it("keeps resizing usable when localStorage is blocked", async () => {
    const originalGetItem = Storage.prototype.getItem;
    const originalSetItem = Storage.prototype.setItem;
    Storage.prototype.getItem = () => {
      throw new DOMException("blocked", "SecurityError");
    };
    Storage.prototype.setItem = () => {
      throw new DOMException("blocked", "SecurityError");
    };
    try {
      const el = await fixture(html`
        <shell-root style="--j2cl-search-rail-width: 376px">
          <button slot="splitter" id="search-splitter">resize</button>
        </shell-root>
      `);
      const splitter = el.querySelector("#search-splitter");

      splitter.dispatchEvent(
        new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
      );

      expect(el.style.getPropertyValue("--j2cl-search-rail-width")).to.equal("392px");
    } finally {
      Storage.prototype.getItem = originalGetItem;
      Storage.prototype.setItem = originalSetItem;
    }
  });

  it("cleans resize tracking when pointer capture is canceled", async () => {
    window.localStorage.removeItem("j2cl.searchRailWidth");
    const el = await fixture(html`
      <shell-root style="--j2cl-search-rail-width: 376px">
        <button slot="splitter" id="search-splitter">resize</button>
      </shell-root>
    `);
    const splitter = el.querySelector("#search-splitter");
    let capturedPointer = null;
    let releasedPointer = null;
    splitter.setPointerCapture = (pointerId) => {
      capturedPointer = pointerId;
    };
    splitter.releasePointerCapture = (pointerId) => {
      releasedPointer = pointerId;
    };

    splitter.dispatchEvent(
      new PointerEvent("pointerdown", {
        bubbles: true,
        button: 0,
        clientX: 100,
        pointerId: 7
      })
    );
    document.dispatchEvent(
      new PointerEvent("pointercancel", {
        bubbles: true,
        pointerId: 7
      })
    );
    document.dispatchEvent(
      new PointerEvent("pointermove", {
        bubbles: true,
        clientX: 180,
        pointerId: 7
      })
    );

    expect(capturedPointer).to.equal(7);
    expect(releasedPointer).to.equal(7);
    expect(el.style.getPropertyValue("--j2cl-search-rail-width")).to.equal("376px");
  });

  it("document wave-controls toggle switches compact mode on the shell", async () => {
    const el = await fixture(html`<shell-root></shell-root>`);

    document.dispatchEvent(
      new CustomEvent("wavy-wave-controls-toggled", {
        bubbles: true,
        composed: true,
        detail: { pressed: true }
      })
    );
    expect(el.getAttribute("data-wave-controls-compact")).to.equal("true");

    document.dispatchEvent(
      new CustomEvent("wavy-wave-controls-toggled", {
        bubbles: true,
        composed: true,
        detail: { pressed: false }
      })
    );
    expect(el.hasAttribute("data-wave-controls-compact")).to.be.false;
  });
});

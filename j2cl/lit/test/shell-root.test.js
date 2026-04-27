import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-root.js";

describe("<shell-root>", () => {
  it("renders the signed-in layout slots", async () => {
    const el = await fixture(html`<shell-root></shell-root>`);
    expect(el.renderRoot.querySelector("slot[name='skip-link']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='nav']")).to.exist;
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
});

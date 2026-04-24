import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/toolbar-group.js";

describe("<toolbar-group>", () => {
  it("uses an accessible toolbar group label and exposes the default slot", async () => {
    const el = await fixture(html`
      <toolbar-group label="View controls">
        <button>Recent</button>
      </toolbar-group>
    `);

    const group = el.renderRoot.querySelector("[role='group']");
    expect(group.getAttribute("aria-label")).to.equal("View controls");
    expect(el.renderRoot.querySelector("slot:not([name])")).to.exist;
  });
});

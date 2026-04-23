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
});

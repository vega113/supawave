import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-root-signed-out.js";

describe("<shell-root-signed-out>", () => {
  it("renders skip-link, header, main, and status slots without a nav", async () => {
    const el = await fixture(html`<shell-root-signed-out></shell-root-signed-out>`);
    expect(el.renderRoot.querySelector("slot[name='skip-link']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='header']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='main']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='nav']")).to.not.exist;
  });
});

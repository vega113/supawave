import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-header.js";

describe("<shell-header>", () => {
  it("uses the banner landmark", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("header[role='banner']")).to.exist;
  });

  it("renders the signed-in action slot when signed-in", async () => {
    const el = await fixture(html`<shell-header signed-in></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.not.exist;
  });

  it("renders the signed-out action slot by default", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.not.exist;
  });
});

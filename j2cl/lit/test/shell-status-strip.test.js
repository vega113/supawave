import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-status-strip.js";

describe("<shell-status-strip>", () => {
  it("uses a polite status live region", async () => {
    const el = await fixture(html`<shell-status-strip></shell-status-strip>`);
    const aside = el.renderRoot.querySelector("aside");
    expect(aside.getAttribute("role")).to.equal("status");
    expect(aside.getAttribute("aria-live")).to.equal("polite");
  });

  it("exposes a default slot for status content", async () => {
    const el = await fixture(html`<shell-status-strip>Connected</shell-status-strip>`);
    expect(el.renderRoot.querySelector("slot")).to.exist;
  });
});

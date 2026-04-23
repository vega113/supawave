import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-skip-link.js";

describe("<shell-skip-link>", () => {
  it("renders an anchor linking to the configured target", async () => {
    const el = await fixture(
      html`<shell-skip-link
        target="#main"
        label="Skip to content"
      ></shell-skip-link>`
    );
    const anchor = el.querySelector("a");
    expect(anchor).to.exist;
    expect(anchor.getAttribute("href")).to.equal("#main");
    expect(anchor.textContent.trim()).to.equal("Skip to content");
    expect(el.renderRoot.querySelector("slot")).to.exist;
  });

  it("falls back to default target and label", async () => {
    const el = await fixture(html`<shell-skip-link></shell-skip-link>`);
    const anchor = el.querySelector("a");
    expect(anchor.getAttribute("href")).to.equal("#j2cl-root-shell-workflow");
    expect(anchor.textContent.trim()).to.equal("Skip to main content");
  });
});

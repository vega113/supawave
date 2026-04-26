import { fixture, expect, html } from "@open-wc/testing";
import "../src/design/wavy-depth-nav.js";

describe("<wavy-depth-nav>", () => {
  it("renders one nav element with the breadcrumb landmark", async () => {
    const el = await fixture(html`<wavy-depth-nav></wavy-depth-nav>`);
    const nav = el.renderRoot.querySelector("nav[aria-label='Breadcrumb']");
    expect(nav).to.exist;
  });

  it("renders one entry per crumb", async () => {
    const el = await fixture(html`<wavy-depth-nav></wavy-depth-nav>`);
    el.crumbs = [
      { label: "Inbox", href: "/inbox" },
      { label: "Sample wave", href: "/wave/1" },
      { label: "Top thread", current: true }
    ];
    await el.updateComplete;
    const nav = el.renderRoot.querySelector("nav");
    const anchors = nav.querySelectorAll("a");
    const currentSpans = nav.querySelectorAll("[aria-current='page']");
    expect(anchors.length).to.equal(2);
    expect(currentSpans.length).to.equal(1);
    expect(currentSpans[0].textContent.trim()).to.equal("Top thread");
  });

  it("crumb without href and not current renders a plain span", async () => {
    const el = await fixture(html`<wavy-depth-nav></wavy-depth-nav>`);
    el.crumbs = [{ label: "Plain" }];
    await el.updateComplete;
    expect(el.renderRoot.querySelector("a")).to.equal(null);
    expect(el.renderRoot.querySelector("[aria-current]")).to.equal(null);
  });
});

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-depth-nav-bar.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

async function waitForStylesheet() {
  for (let i = 0; i < 50; i++) {
    const cs = getComputedStyle(document.documentElement);
    if (cs.getPropertyValue("--wavy-bg-base").trim() !== "") return;
    await new Promise((r) => setTimeout(r, 20));
  }
  throw new Error("wavy-tokens.css did not apply within 1000ms");
}

before(async () => {
  ensureWavyTokensLoaded();
  await waitForStylesheet();
});

describe("<wavy-depth-nav-bar>", () => {
  it("registers the F-2 slice 2 custom element", () => {
    expect(customElements.get("wavy-depth-nav-bar")).to.exist;
  });

  it("hides itself at top-of-wave (no current depth + no crumbs)", async () => {
    const el = await fixture(html`<wavy-depth-nav-bar></wavy-depth-nav-bar>`);
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.be.true;
  });

  it("shows itself when a current depth is set", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+3"
        parent-depth-blip-id="b+1"
        parent-author-name="Alice"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.be.false;
  });

  it("G.2 — Up one level button has parent-author-aware aria-label", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+3"
        parent-depth-blip-id="b+1"
        parent-author-name="Alice"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('button[data-action="up-one-level"]');
    expect(button).to.exist;
    expect(button.getAttribute("aria-label")).to.equal(
      "Up one level to Alice's thread"
    );
  });

  it("G.2 — Up one level falls back to generic label when parentAuthorName is empty", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+3"
        parent-depth-blip-id="b+1"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('button[data-action="up-one-level"]');
    expect(button.getAttribute("aria-label")).to.equal("Up one level");
  });

  it("G.2 — clicking Up one level emits wavy-depth-up with from/to blip ids", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+3"
        parent-depth-blip-id="b+1"
        parent-author-name="Alice"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('button[data-action="up-one-level"]');
    const eventPromise = oneEvent(el, "wavy-depth-up");
    button.click();
    const event = await eventPromise;
    expect(event.detail.fromBlipId).to.equal("b+3");
    expect(event.detail.toBlipId).to.equal("b+1");
    expect(event.bubbles).to.be.true;
    expect(event.composed).to.be.true;
  });

  it("G.3 — Up to wave button aria-label is constant", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+3"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('button[data-action="up-to-wave"]');
    expect(button).to.exist;
    expect(button.getAttribute("aria-label")).to.equal("Back to top of wave");
  });

  it("G.3 — clicking Up to wave emits wavy-depth-root with fromBlipId", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+5"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('button[data-action="up-to-wave"]');
    const eventPromise = oneEvent(el, "wavy-depth-root");
    button.click();
    const event = await eventPromise;
    expect(event.detail.fromBlipId).to.equal("b+5");
    expect(event.bubbles).to.be.true;
    expect(event.composed).to.be.true;
  });

  it("composes inner <wavy-depth-nav> with crumbs", async () => {
    const crumbs = [
      { label: "Inbox", href: "/?folder=inbox" },
      { label: "Sample wave", blipId: "b+root" },
      { label: "Top thread", current: true, blipId: "b+1" }
    ];
    const el = await fixture(html`<wavy-depth-nav-bar
      current-depth-blip-id="b+1"
      .crumbs=${crumbs}
    ></wavy-depth-nav-bar>`);
    await el.updateComplete;
    const inner = el.renderRoot.querySelector("wavy-depth-nav");
    expect(inner).to.exist;
    await inner.updateComplete;
    expect(inner.crumbs).to.equal(crumbs);
  });

  it("emits wavy-depth-jump-to-crumb when an inner crumb with blipId is clicked", async () => {
    const crumbs = [
      { label: "Sample wave", blipId: "b+root" },
      { label: "Reply thread", blipId: "b+2" },
      { label: "This blip", current: true }
    ];
    const el = await fixture(html`<wavy-depth-nav-bar
      current-depth-blip-id="b+2"
      .crumbs=${crumbs}
    ></wavy-depth-nav-bar>`);
    await el.updateComplete;
    const inner = el.renderRoot.querySelector("wavy-depth-nav");
    await inner.updateComplete;
    // The inner recipe renders crumbs as <a> / <span> with the label as
    // text content. Click the second crumb (Reply thread).
    const replyAnchor = Array.from(
      inner.renderRoot.querySelectorAll("a, span")
    ).find((n) => n.textContent.trim() === "Reply thread");
    expect(replyAnchor, "Reply thread crumb missing").to.exist;
    const eventPromise = oneEvent(el, "wavy-depth-jump-to-crumb");
    replyAnchor.click();
    const event = await eventPromise;
    expect(event.detail.blipId).to.equal("b+2");
  });

  it("reserves an awareness-pill slot for S5 to fill", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar current-depth-blip-id="b+1">
        <span slot="awareness-pill" data-test="pill">↑ 2 new</span>
      </wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    const slot = el.renderRoot.querySelector('slot[name="awareness-pill"]');
    expect(slot).to.exist;
    const assigned = slot.assignedNodes({ flatten: true });
    expect(assigned.some((n) => n.dataset && n.dataset.test === "pill")).to.be.true;
  });

  it("reflects current-depth-blip-id on the host", async () => {
    const el = await fixture(
      html`<wavy-depth-nav-bar
        current-depth-blip-id="b+9"
      ></wavy-depth-nav-bar>`
    );
    await el.updateComplete;
    expect(el.getAttribute("current-depth-blip-id")).to.equal("b+9");
  });
});

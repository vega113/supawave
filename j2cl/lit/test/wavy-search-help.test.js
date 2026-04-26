import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-search-help.js";

describe("<wavy-search-help>", () => {
  it("registers the F-2.S3 modal element", () => {
    expect(customElements.get("wavy-search-help")).to.exist;
  });

  it("strips SSR `hidden` attribute on connectedCallback (no-flicker upgrade)", async () => {
    // Construct a host with the SSR-shape attribute to confirm Lit
    // drops `hidden` so the modal can later be opened via the toggle.
    const el = await fixture(html`<wavy-search-help hidden></wavy-search-help>`);
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.be.false;
  });

  it("auto-assigns id=wavy-search-help when SSR mounted without id", async () => {
    const el = await fixture(html`<wavy-search-help></wavy-search-help>`);
    await el.updateComplete;
    expect(el.id).to.equal("wavy-search-help");
  });

  it("renders closed by default and opens via the document-level toggle event", async () => {
    const el = await fixture(html`<wavy-search-help></wavy-search-help>`);
    expect(el.hasAttribute("open")).to.be.false;
    document.dispatchEvent(new CustomEvent("wavy-search-help-toggle"));
    await el.updateComplete;
    expect(el.hasAttribute("open")).to.be.true;
    document.dispatchEvent(new CustomEvent("wavy-search-help-toggle"));
    await el.updateComplete;
    expect(el.hasAttribute("open")).to.be.false;
  });

  it("renders dialog semantics (role=dialog, aria-modal)", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const sheet = el.renderRoot.querySelector(".sheet");
    expect(sheet.getAttribute("role")).to.equal("dialog");
    expect(sheet.getAttribute("aria-modal")).to.equal("true");
  });

  // C.1–C.16 filter rows, C.17–C.20 sort rows, C.21 combination chips, C.22 dismiss
  const ADVERTISED_TOKENS = [
    // Filters (C.1–C.15)
    "in:inbox",
    "in:archive",
    "in:all",
    "in:pinned",
    "with:user@domain",
    "with:alice@example.com",
    "with:@",
    "creator:user@domain",
    "creator:bob@example.com",
    "tag:name",
    "tag:important",
    "unread:true",
    "title:text",
    "title:meeting",
    "content:text",
    "content:agenda",
    "mentions:me",
    "tasks:all",
    "tasks:me",
    "tasks:user@domain",
    "tasks:alice@example.com",
    // Free text marker (C.16)
    "meeting notes",
    // Sort (C.17–C.20)
    "orderby:datedesc",
    "orderby:dateasc",
    "orderby:createddesc",
    "orderby:createdasc",
    "orderby:creatordesc",
    "orderby:creatorasc",
    // Combination examples (C.21)
    "in:inbox tag:important",
    "in:all orderby:createdasc",
    "with:alice@example.com tag:project",
    "in:pinned orderby:creatordesc",
    "creator:bob in:archive",
    "mentions:me unread:true",
    "tasks:all unread:true"
  ];

  ADVERTISED_TOKENS.forEach((token) => {
    it(`advertises the ${token} token literal in the modal body`, async () => {
      const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
      await el.updateComplete;
      const text = el.renderRoot.textContent || "";
      expect(text).to.include(token);
    });
  });

  it("each filter example chip emits wavy-search-help-example with the token", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const examples = Array.from(el.renderRoot.querySelectorAll(".example"));
    expect(examples.length).to.be.greaterThan(20);
    const inboxChip = examples.find((e) => e.textContent.trim() === "in:inbox");
    expect(inboxChip, "in:inbox chip exists").to.exist;
    setTimeout(() => inboxChip.click(), 0);
    const evt = await oneEvent(el, "wavy-search-help-example");
    expect(evt.detail.query).to.equal("in:inbox");
  });

  it("Got it dismiss closes the dialog and emits wavy-search-help-dismissed", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const dismiss = el.renderRoot.querySelector(".dismiss");
    expect(dismiss).to.exist;
    expect(dismiss.textContent.trim()).to.equal("Got it");
    setTimeout(() => dismiss.click(), 0);
    const evt = await oneEvent(el, "wavy-search-help-dismissed");
    expect(evt).to.exist;
    expect(el.hasAttribute("open")).to.be.false;
  });

  it("Escape key dismisses when open", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await el.updateComplete;
    expect(el.hasAttribute("open")).to.be.false;
  });

  it("backdrop click dismisses", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const backdrop = el.renderRoot.querySelector(".backdrop");
    expect(backdrop).to.exist;
    backdrop.click();
    await el.updateComplete;
    expect(el.hasAttribute("open")).to.be.false;
  });

  it("renders both Filters and Sort Options sections", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const titles = Array.from(el.renderRoot.querySelectorAll(".section-title")).map(
      (n) => n.textContent.trim()
    );
    expect(titles).to.include("Filters");
    expect(titles).to.include("Sort Options");
    expect(titles).to.include("Combinations");
  });

  it("renders the free-text row marker", async () => {
    const el = await fixture(html`<wavy-search-help open></wavy-search-help>`);
    await el.updateComplete;
    const row = el.renderRoot.querySelector(".free-text-row");
    expect(row).to.exist;
    expect(row.textContent).to.include("free text");
  });
});

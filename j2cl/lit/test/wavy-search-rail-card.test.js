import { fixture, expect, html, oneEvent, aTimeout } from "@open-wc/testing";
import "../src/elements/wavy-search-rail-card.js";

describe("<wavy-search-rail-card>", () => {
  it("registers the F-2.S3 digest card element", () => {
    expect(customElements.get("wavy-search-rail-card")).to.exist;
  });

  it("reflects wave-id, pinned, msg-count, unread-count attributes", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card
        data-wave-id="w1"
        title="Hello"
        snippet="Lorem ipsum"
        posted-at="2m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
        msg-count="5"
        unread-count="2"
        pinned
        authors="Alice, Bob, Carol, Dave"
      ></wavy-search-rail-card>
    `);
    await el.updateComplete;
    expect(el.getAttribute("data-wave-id")).to.equal("w1");
    expect(el.hasAttribute("pinned")).to.be.true;
    expect(el.msgCount).to.equal(5);
    expect(el.unreadCount).to.equal(2);
  });

  it("renders title and snippet (B.15, B.16)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card title="Project meeting" snippet="Agenda for the week">
      </wavy-search-rail-card>
    `);
    await el.updateComplete;
    const title = el.renderRoot.querySelector("h3.title");
    const snippet = el.renderRoot.querySelector("p.snippet");
    expect(title.textContent.trim()).to.equal("Project meeting");
    expect(snippet.textContent.trim()).to.equal("Agenda for the week");
  });

  it("multi-author avatar stack truncates to 3 visible + overflow chip (B.13)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card authors="Alice Smith, Bob Jones, Carol White, Dave Black, Eve Green">
      </wavy-search-rail-card>
    `);
    await el.updateComplete;
    const avatars = el.renderRoot.querySelectorAll(".avatar");
    expect(avatars.length).to.equal(4); // 3 visible + 1 overflow chip
    const overflow = el.renderRoot.querySelector(".avatar.more");
    expect(overflow).to.exist;
    expect(overflow.textContent.trim()).to.equal("+2");
  });

  it("avatar stack uses initials from each author display name (B.13)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card authors="Alice Smith, Bob"></wavy-search-rail-card>
    `);
    await el.updateComplete;
    const avatars = Array.from(el.renderRoot.querySelectorAll(".avatar:not(.more)"));
    expect(avatars[0].textContent.trim()).to.equal("AS");
    expect(avatars[1].textContent.trim()).to.equal("BO");
  });

  it("renders cyan pin glyph only when pinned (B.14)", async () => {
    const elUnpinned = await fixture(html`<wavy-search-rail-card></wavy-search-rail-card>`);
    await elUnpinned.updateComplete;
    expect(elUnpinned.renderRoot.querySelector(".pin")).to.be.null;

    const elPinned = await fixture(html`<wavy-search-rail-card pinned></wavy-search-rail-card>`);
    await elPinned.updateComplete;
    const pin = elPinned.renderRoot.querySelector(".pin");
    expect(pin).to.exist;
    expect(pin.getAttribute("aria-label")).to.equal("Pinned");
  });

  it("snippet uses 3-line clamp via -webkit-line-clamp (B.16)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card snippet="A long snippet that would overflow."></wavy-search-rail-card>
    `);
    await el.updateComplete;
    const snippet = el.renderRoot.querySelector("p.snippet");
    const cs = getComputedStyle(snippet);
    // Cross-browser: -webkit-line-clamp on most engines, line-clamp on
    // newer ones. Either having "3" satisfies the contract.
    const wkClamp = cs.getPropertyValue("-webkit-line-clamp").trim();
    const stdClamp = cs.getPropertyValue("line-clamp").trim();
    expect(
      wkClamp === "3" || stdClamp === "3",
      `expected line-clamp:3 (got webkit=${wkClamp}, std=${stdClamp})`
    ).to.be.true;
    expect(cs.overflow).to.equal("hidden");
  });

  it("unread badge appears only when unread-count > 0 (B.17)", async () => {
    const elZero = await fixture(html`
      <wavy-search-rail-card msg-count="3" unread-count="0"></wavy-search-rail-card>
    `);
    await elZero.updateComplete;
    expect(elZero.renderRoot.querySelector(".badge.unread")).to.be.null;

    const elTwo = await fixture(html`
      <wavy-search-rail-card msg-count="3" unread-count="2"></wavy-search-rail-card>
    `);
    await elTwo.updateComplete;
    const badge = elTwo.renderRoot.querySelector(".badge.unread");
    expect(badge).to.exist;
    expect(badge.textContent.trim()).to.equal("2");
  });

  it("firePulse() sets data-pulse=ring then clears it (B.17 cyan signal-pulse)", async () => {
    // Use a short pulse duration override to keep the test fast.
    const el = await fixture(html`
      <wavy-search-rail-card
        unread-count="1"
        style="--wavy-motion-pulse-duration: 80;"
      ></wavy-search-rail-card>
    `);
    await el.updateComplete;
    el.firePulse();
    expect(el.dataset.pulse).to.equal("ring");
    await aTimeout(150);
    expect(el.dataset.pulse).to.be.undefined;
  });

  it("relative timestamp uses <time datetime=...> with title tooltip (B.18)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card
        posted-at="2m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
      ></wavy-search-rail-card>
    `);
    await el.updateComplete;
    const time = el.renderRoot.querySelector("time.ts");
    expect(time).to.exist;
    expect(time.getAttribute("datetime")).to.equal("2026-04-26T12:00:00Z");
    expect(time.getAttribute("title")).to.equal("2026-04-26T12:00:00Z");
    expect(time.textContent.trim()).to.equal("2m ago");
  });

  it("click emits wavy-search-rail-card-selected with waveId", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card data-wave-id="w-7" title="Hello"></wavy-search-rail-card>
    `);
    await el.updateComplete;
    setTimeout(() => el.renderRoot.querySelector("article").click(), 0);
    const evt = await oneEvent(el, "wavy-search-rail-card-selected");
    expect(evt.detail.waveId).to.equal("w-7");
  });

  it("Enter/Space on the card emits selection (keyboard a11y)", async () => {
    const el = await fixture(html`
      <wavy-search-rail-card data-wave-id="w-9"></wavy-search-rail-card>
    `);
    await el.updateComplete;
    const article = el.renderRoot.querySelector("article");
    setTimeout(() => {
      article.dispatchEvent(
        new KeyboardEvent("keydown", { key: "Enter", bubbles: true })
      );
    }, 0);
    const evt = await oneEvent(el, "wavy-search-rail-card-selected");
    expect(evt.detail.waveId).to.equal("w-9");
  });

  it("falls back to (no title) when title is empty", async () => {
    const el = await fixture(html`<wavy-search-rail-card></wavy-search-rail-card>`);
    await el.updateComplete;
    const title = el.renderRoot.querySelector("h3.title");
    expect(title.textContent.trim()).to.equal("(no title)");
  });
});

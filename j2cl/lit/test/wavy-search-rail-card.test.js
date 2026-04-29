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

  // G-PORT-2 (#1111): the card host carries `data-digest-card` and the
  // sub-elements carry stable `data-digest-*` attributes (avatars,
  // title, snippet, msg-count, time) so a single Playwright selector
  // resolves the same DOM shape on `?view=j2cl-root` and `?view=gwt`.
  describe("G-PORT-2 parity selectors (#1111)", () => {
    it("host carries data-digest-card", async () => {
      const el = await fixture(html`<wavy-search-rail-card></wavy-search-rail-card>`);
      await el.updateComplete;
      expect(el.hasAttribute("data-digest-card")).to.equal(true);
    });

    it("inner sub-elements expose the six required data-digest-* hooks", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          title="Sprint review"
          snippet="Agenda"
          msg-count="3"
          posted-at="2m ago"
          posted-at-iso="2026-04-26T12:00:00Z"
          authors="Alice, Bob"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      expect(el.renderRoot.querySelector("[data-digest-avatars]")).to.exist;
      expect(el.renderRoot.querySelector("[data-digest-title]")).to.exist;
      expect(el.renderRoot.querySelector("[data-digest-snippet]")).to.exist;
      expect(el.renderRoot.querySelector("[data-digest-msg-count]")).to.exist;
      expect(el.renderRoot.querySelector("[data-digest-time]")).to.exist;
    });

    it("DOM order mirrors the GWT digest layout: avatars + info + title + snippet", async () => {
      // GWT DigestDomImpl.ui.xml: .inner > .avatars + .info + .title + .snippet.
      // The J2CL clone preserves the same order so a side-by-side diff
      // shows the same shape.
      const el = await fixture(html`
        <wavy-search-rail-card
          title="X"
          snippet="Y"
          msg-count="1"
          posted-at="now"
          authors="Alice"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      const inner = el.renderRoot.querySelector(".inner");
      expect(inner, "inner wrapper must mount").to.exist;
      const kids = Array.from(inner.children);
      expect(kids.map((k) => k.className.split(" ")[0]).slice(0, 4)).to.deep.equal([
        "avatars",
        "info",
        "title",
        "snippet"
      ]);
    });
  });

  // J-UI-1 (#1079): selected reflective property toggles aria-current
  // on the inner <article> so the route controller can drive selection
  // from the URL state.
  describe("J-UI-1 selection (#1079)", () => {
    it("aria-current is omitted by default (no false attribute that AT might announce)", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card data-wave-id="w-1" title="A"></wavy-search-rail-card>
      `);
      await el.updateComplete;
      const article = el.renderRoot.querySelector("article");
      expect(article.hasAttribute("aria-current")).to.equal(false);
    });

    it("setting selected=true reflects aria-current='true' on the article", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card data-wave-id="w-1" title="A"></wavy-search-rail-card>
      `);
      await el.updateComplete;
      el.selected = true;
      await el.updateComplete;
      const article = el.renderRoot.querySelector("article");
      expect(article.getAttribute("aria-current")).to.equal("true");
      expect(el.hasAttribute("selected")).to.equal(true);
    });

    it("clearing selected removes aria-current entirely", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card data-wave-id="w-1" title="A" selected></wavy-search-rail-card>
      `);
      await el.updateComplete;
      el.selected = false;
      await el.updateComplete;
      const article = el.renderRoot.querySelector("article");
      expect(article.hasAttribute("aria-current")).to.equal(false);
      expect(el.hasAttribute("selected")).to.equal(false);
    });
  });

  // J-UI-7 (#1085, R-4.4): live mark-as-read decrement + the matching
  // increment when a peer posts a new reply. The Java view mutates the
  // `unread-count` attribute on the host; the element re-renders the
  // badge, fires a host-level pulse, and exposes a CSS read-state hook
  // so the cue is visible regardless of badge visibility.
  describe("J-UI-7 live unread mutation (#1085)", () => {
    it("does NOT pulse on the initial render (avoids paint-time noise)", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="2"
          style="--wavy-motion-pulse-duration: 60;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      expect(el.dataset.pulse).to.be.undefined;
    });

    it("pulses on a 2 -> 1 decrement and keeps the badge", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="2"
          style="--wavy-motion-pulse-duration: 60;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      el.setAttribute("unread-count", "1");
      await el.updateComplete;
      expect(el.dataset.pulse).to.equal("ring");
      const badge = el.renderRoot.querySelector(".badge.unread");
      expect(badge).to.exist;
      expect(badge.textContent.trim()).to.equal("1");
      await aTimeout(120);
      expect(el.dataset.pulse).to.be.undefined;
    });

    it("pulses on a 1 -> 0 zero-out and removes the badge (host owns the cue)", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="1"
          style="--wavy-motion-pulse-duration: 60;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      el.setAttribute("unread-count", "0");
      await el.updateComplete;
      expect(el.dataset.pulse).to.equal("ring");
      // Badge is gone — but the host still pulses, which is the whole
      // point of moving the box-shadow from .badge.unread to :host.
      expect(el.renderRoot.querySelector(".badge.unread")).to.be.null;
      // Reflected attribute survives.
      expect(el.getAttribute("unread-count")).to.equal("0");
    });

    it("pulses on a 0 -> 2 increment and re-renders the badge", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="0"
          style="--wavy-motion-pulse-duration: 60;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".badge.unread")).to.be.null;
      el.setAttribute("unread-count", "2");
      await el.updateComplete;
      expect(el.dataset.pulse).to.equal("ring");
      const badge = el.renderRoot.querySelector(".badge.unread");
      expect(badge).to.exist;
      expect(badge.textContent.trim()).to.equal("2");
    });

    it("setting the same unread-count value does not fire a pulse (no churn)", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="2"
          style="--wavy-motion-pulse-duration: 60;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      // No-op set: same numeric value.
      el.setAttribute("unread-count", "2");
      await el.updateComplete;
      expect(el.dataset.pulse).to.be.undefined;
    });

    it("exposes :host([unread-count='0']) as a CSS read-state hook", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card unread-count="0"></wavy-search-rail-card>
      `);
      await el.updateComplete;
      const cs = getComputedStyle(el);
      expect(cs.getPropertyValue("--wavy-rail-card-read").trim()).to.equal("1");
      el.setAttribute("unread-count", "3");
      await el.updateComplete;
      const cs2 = getComputedStyle(el);
      // CSS variable falls back to undefined ("") when the
      // :host([unread-count="0"]) rule no longer matches.
      expect(cs2.getPropertyValue("--wavy-rail-card-read").trim()).to.equal("");
    });

    it("article aria-label tracks the live unread count for AT", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card title="Sprint review" unread-count="2"></wavy-search-rail-card>
      `);
      await el.updateComplete;
      let article = el.renderRoot.querySelector("article");
      expect(article.getAttribute("aria-label")).to.equal("Sprint review. 2 unread.");
      el.setAttribute("unread-count", "0");
      await el.updateComplete;
      article = el.renderRoot.querySelector("article");
      expect(article.getAttribute("aria-label")).to.equal("Sprint review. Read.");
    });

    it("rapid back-to-back unread changes keep the pulse marker until the last clear", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card
          msg-count="3"
          unread-count="2"
          style="--wavy-motion-pulse-duration: 80;"
        ></wavy-search-rail-card>
      `);
      await el.updateComplete;
      el.setAttribute("unread-count", "1");
      await el.updateComplete;
      // First pulse is in flight.
      expect(el.dataset.pulse).to.equal("ring");
      // Second pulse arrives well before the first 80ms timer would fire.
      await aTimeout(20);
      el.setAttribute("unread-count", "0");
      await el.updateComplete;
      expect(el.dataset.pulse).to.equal("ring");
      // First pulse's original timer would have fired by now if it had not
      // been cancelled. The marker must still be present because the second
      // pulse rearmed the timer.
      await aTimeout(70);
      expect(el.dataset.pulse).to.equal("ring");
      // Second pulse's full duration has now elapsed; marker clears.
      await aTimeout(40);
      expect(el.dataset.pulse).to.be.undefined;
    });

    it("zero-titled card still produces a sensible aria-label", async () => {
      const el = await fixture(html`
        <wavy-search-rail-card unread-count="0"></wavy-search-rail-card>
      `);
      await el.updateComplete;
      const article = el.renderRoot.querySelector("article");
      expect(article.getAttribute("aria-label")).to.equal("(no title). Read.");
    });
  });
});

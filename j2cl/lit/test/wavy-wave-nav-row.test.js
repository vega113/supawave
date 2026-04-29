import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-wave-nav-row.js";

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

describe("<wavy-wave-nav-row>", () => {
  it("registers the F-2 slice 2 custom element", () => {
    expect(customElements.get("wavy-wave-nav-row")).to.exist;
  });

  it("renders all 10 buttons E.1 through E.10 in fixed order", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row
        selected-blip-id="b+1"
        source-wave-id="w+f2"
      ></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const actions = Array.from(
      el.renderRoot.querySelectorAll("nav > button[data-action]")
    ).map((b) => b.dataset.action);
    expect(actions).to.deep.equal([
      "recent",
      "next-unread",
      "previous",
      "next",
      "end",
      "prev-mention",
      "next-mention",
      "archive",
      "pin",
      "version-history",
      "overflow"
    ]);
  });

  // --- per-row coverage (E.1 through E.10) -------------------------------
  // Each row asserts (a) ARIA label, (b) event name on click,
  // (c) detail shape including selectedBlipId + sourceWaveId.

  const rows = [
    {
      label: "E.1 Recent",
      action: "recent",
      ariaLabel: "Jump to recent activity",
      eventName: "wave-nav-recent-requested"
    },
    {
      label: "E.2 Next Unread",
      action: "next-unread",
      ariaLabel: "Jump to next unread blip",
      eventName: "wave-nav-next-unread-requested"
    },
    {
      label: "E.3 Previous",
      action: "previous",
      ariaLabel: "Jump to previous blip",
      eventName: "wave-nav-previous-requested"
    },
    {
      label: "E.4 Next",
      action: "next",
      ariaLabel: "Jump to next blip",
      eventName: "wave-nav-next-requested"
    },
    {
      label: "E.5 End",
      action: "end",
      ariaLabel: "Jump to last blip",
      eventName: "wave-nav-end-requested"
    },
    {
      label: "E.6 Prev mention",
      action: "prev-mention",
      ariaLabel: "Jump to previous mention",
      eventName: "wave-nav-prev-mention-requested"
    },
    {
      label: "E.7 Next mention",
      action: "next-mention",
      ariaLabel: "Jump to next mention",
      eventName: "wave-nav-next-mention-requested"
    },
    {
      label: "E.8 Archive (default state)",
      action: "archive",
      ariaLabel: "Move wave to archive",
      eventName: "wave-nav-archive-toggle-requested"
    },
    {
      label: "E.9 Pin (default state)",
      action: "pin",
      ariaLabel: "Pin wave",
      eventName: "wave-nav-pin-toggle-requested"
    },
    {
      label: "E.10 Version History",
      action: "version-history",
      ariaLabel: "Open version history (h)",
      eventName: "wave-nav-version-history-requested"
    }
  ];

  for (const row of rows) {
    it(`${row.label} — has correct ARIA label and dispatches ${row.eventName}`, async () => {
      const el = await fixture(
        html`<wavy-wave-nav-row
          selected-blip-id="b+anchor"
          source-wave-id="w+f2"
        ></wavy-wave-nav-row>`
      );
      await el.updateComplete;
      const button = el.renderRoot.querySelector(
        `nav > button[data-action="${row.action}"]`
      );
      expect(button, `button[data-action=${row.action}] missing`).to.exist;
      expect(button.getAttribute("aria-label")).to.equal(row.ariaLabel);

      const eventPromise = oneEvent(el, row.eventName);
      button.click();
      const event = await eventPromise;
      expect(event.detail.selectedBlipId).to.equal("b+anchor");
      expect(event.detail.sourceWaveId).to.equal("w+f2");
      expect(event.bubbles).to.be.true;
      expect(event.composed).to.be.true;
    });
  }

  it("E.8 Archive button toggles aria-label when archived=true", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row archived></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('nav > button[data-action="archive"]');
    expect(button.getAttribute("aria-label")).to.equal("Restore from archive");
  });

  it("E.9 Pin button toggles aria-label and emphasis when pinned=true", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row pinned></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector('nav > button[data-action="pin"]');
    expect(button.getAttribute("aria-label")).to.equal("Unpin wave");
    expect(button.dataset.emphasis).to.equal("cyan");
    expect(button.getAttribute("aria-pressed")).to.equal("true");
  });

  // G-PORT-8 (#1117): every button renders a SVG glyph cloned from
  // GWT ViewToolbar.java. No text labels.
  it("G-PORT-8: every nav button renders an SVG icon", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const actions = [
      "recent",
      "next-unread",
      "previous",
      "next",
      "end",
      "prev-mention",
      "next-mention",
      "archive",
      "pin",
      "version-history"
    ];
    for (const action of actions) {
      const btn = el.renderRoot.querySelector(
        `nav > button[data-action="${action}"]`
      );
      expect(btn, `button[data-action=${action}]`).to.exist;
      expect(
        btn.querySelector("svg"),
        `button[data-action=${action}] must contain an <svg> glyph`
      ).to.exist;
      // No raw text label on the icon button (legacy mode rendered
      // "Recent", "Archive", etc.). Only whitespace remains.
      expect(btn.textContent.trim()).to.equal("");
      expect(
        btn.getAttribute("title"),
        `button[data-action=${action}] must carry a hover title`
      ).to.match(/.+/);
    }
  });

  it("G-PORT-8: archive + pin buttons reflect aria-pressed for the toggle state", async () => {
    const off = await fixture(
      html`<wavy-wave-nav-row></wavy-wave-nav-row>`
    );
    await off.updateComplete;
    expect(
      off.renderRoot
        .querySelector('button[data-action="archive"]')
        .getAttribute("aria-pressed")
    ).to.equal("false");
    expect(
      off.renderRoot
        .querySelector('button[data-action="pin"]')
        .getAttribute("aria-pressed")
    ).to.equal("false");
    const on = await fixture(
      html`<wavy-wave-nav-row pinned archived></wavy-wave-nav-row>`
    );
    await on.updateComplete;
    expect(
      on.renderRoot
        .querySelector('button[data-action="archive"]')
        .getAttribute("aria-pressed")
    ).to.equal("true");
    expect(
      on.renderRoot
        .querySelector('button[data-action="pin"]')
        .getAttribute("aria-pressed")
    ).to.equal("true");
  });

  it("E.2 Next-unread emphasis flips to cyan when unreadCount > 0", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row unread-count="3"></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector(
      'nav > button[data-action="next-unread"]'
    );
    expect(button.dataset.emphasis).to.equal("cyan");
    // Computed style: rgb(34, 211, 238) is the cyan token.
    const computed = getComputedStyle(button);
    expect(computed.color).to.contain("rgb(34, 211, 238)");
  });

  it("E.6/E.7 mention emphasis flips to violet when mentionCount > 0", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row mention-count="2"></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const prev = el.renderRoot.querySelector('nav > button[data-action="prev-mention"]');
    const next = el.renderRoot.querySelector('nav > button[data-action="next-mention"]');
    expect(prev.dataset.emphasis).to.equal("violet");
    expect(next.dataset.emphasis).to.equal("violet");
    expect(getComputedStyle(prev).color).to.contain("rgb(124, 58, 237)");
  });

  it("H keyboard shortcut on the card host emits wave-nav-version-history-requested", async () => {
    const card = await fixture(
      html`<section data-j2cl-selected-wave-host>
        <wavy-wave-nav-row selected-blip-id="b+1"></wavy-wave-nav-row>
      </section>`
    );
    const el = card.querySelector("wavy-wave-nav-row");
    await el.updateComplete;
    const eventPromise = oneEvent(el, "wave-nav-version-history-requested");
    card.dispatchEvent(
      new KeyboardEvent("keydown", { key: "H", bubbles: true })
    );
    const event = await eventPromise;
    expect(event.detail.keyboard).to.be.true;
    expect(event.detail.selectedBlipId).to.equal("b+1");
  });

  it("H keyboard ignored when target is INPUT", async () => {
    const card = await fixture(
      html`<section data-j2cl-selected-wave-host>
        <input id="search" />
        <wavy-wave-nav-row></wavy-wave-nav-row>
      </section>`
    );
    const el = card.querySelector("wavy-wave-nav-row");
    await el.updateComplete;
    let fired = false;
    el.addEventListener("wave-nav-version-history-requested", () => {
      fired = true;
    });
    const input = card.querySelector("#search");
    input.focus();
    input.dispatchEvent(
      new KeyboardEvent("keydown", { key: "H", bubbles: true })
    );
    // Allow the synchronous handler to run.
    await new Promise((r) => setTimeout(r, 10));
    expect(fired).to.be.false;
  });

  it("H keyboard ignored when modifier (cmd/ctrl/alt) is held", async () => {
    const card = await fixture(
      html`<section data-j2cl-selected-wave-host>
        <wavy-wave-nav-row></wavy-wave-nav-row>
      </section>`
    );
    const el = card.querySelector("wavy-wave-nav-row");
    await el.updateComplete;
    let fired = false;
    el.addEventListener("wave-nav-version-history-requested", () => {
      fired = true;
    });
    card.dispatchEvent(
      new KeyboardEvent("keydown", { key: "H", metaKey: true, bubbles: true })
    );
    card.dispatchEvent(
      new KeyboardEvent("keydown", { key: "H", ctrlKey: true, bubbles: true })
    );
    card.dispatchEvent(
      new KeyboardEvent("keydown", { key: "H", altKey: true, bubbles: true })
    );
    await new Promise((r) => setTimeout(r, 10));
    expect(fired).to.be.false;
  });

  it("H keyboard binds to closest selected-wave-host ancestor (not document)", async () => {
    // Two cards mounted simultaneously — verify each row only receives its
    // own card's keydown, not the other's.
    const wrapper = await fixture(
      html`<div>
        <section id="a" data-j2cl-selected-wave-host>
          <wavy-wave-nav-row id="rowA" selected-blip-id="bA"></wavy-wave-nav-row>
        </section>
        <section id="b" data-j2cl-selected-wave-host>
          <wavy-wave-nav-row id="rowB" selected-blip-id="bB"></wavy-wave-nav-row>
        </section>
      </div>`
    );
    const rowA = wrapper.querySelector("#rowA");
    const rowB = wrapper.querySelector("#rowB");
    await rowA.updateComplete;
    await rowB.updateComplete;
    let firedA = 0;
    let firedB = 0;
    rowA.addEventListener("wave-nav-version-history-requested", () => firedA++);
    rowB.addEventListener("wave-nav-version-history-requested", () => firedB++);
    wrapper
      .querySelector("#a")
      .dispatchEvent(new KeyboardEvent("keydown", { key: "H", bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
    expect(firedA).to.equal(1);
    expect(firedB).to.equal(0);
  });

  it("declares container-type:inline-size on host for @container query", async () => {
    const el = await fixture(html`<wavy-wave-nav-row></wavy-wave-nav-row>`);
    await el.updateComplete;
    const computed = getComputedStyle(el);
    expect(computed.containerType).to.equal("inline-size");
    expect(computed.containerName).to.equal("wave-nav-row");
  });

  it("overflow trigger toggles overflow menu open/closed", async () => {
    const el = await fixture(html`<wavy-wave-nav-row></wavy-wave-nav-row>`);
    await el.updateComplete;
    const overflow = el.renderRoot.querySelector(".overflow-trigger");
    const menu = el.renderRoot.querySelector(".overflow-menu");
    expect(menu.dataset.open).to.equal("false");
    overflow.click();
    await el.updateComplete;
    expect(menu.dataset.open).to.equal("true");
    overflow.click();
    await el.updateComplete;
    expect(menu.dataset.open).to.equal("false");
  });

  it("overflow menu items dispatch the same events as their full-width counterparts", async () => {
    const el = await fixture(
      html`<wavy-wave-nav-row selected-blip-id="b+ov"></wavy-wave-nav-row>`
    );
    await el.updateComplete;
    const overflowItems = el.renderRoot.querySelectorAll(
      ".overflow-menu button[data-overflow]"
    );
    expect(overflowItems.length).to.equal(3);
    const promise = oneEvent(el, "wave-nav-version-history-requested");
    const ovHistory = el.renderRoot.querySelector(
      '.overflow-menu button[data-action="version-history"]'
    );
    ovHistory.click();
    const event = await promise;
    expect(event.detail.selectedBlipId).to.equal("b+ov");
  });
});

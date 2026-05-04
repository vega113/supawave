import { fixture, expect, html, oneEvent, aTimeout } from "@open-wc/testing";
import "../src/elements/wave-blip.js";
import "../src/elements/wave-blip-toolbar.js";
import "../src/design/wavy-blip-card.js";

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

describe("<wave-blip>", () => {
  it("registers the F-2 wrapper custom element", () => {
    expect(customElements.get("wave-blip")).to.exist;
  });

  it("reflects blip-id, wave-id, has-mention, focused, unread, reply-count", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b1"
        data-wave-id="w1"
        author-id="alice@example.com"
        author-name="Alice"
        posted-at="2m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
        is-author
        focused
        unread
        has-mention
        reply-count="3"
      >
        Hello world.
      </wave-blip>
    `);
    expect(el.getAttribute("data-blip-id")).to.equal("b1");
    expect(el.getAttribute("data-wave-id")).to.equal("w1");
    expect(el.hasAttribute("focused")).to.be.true;
    expect(el.hasAttribute("unread")).to.be.true;
    expect(el.hasAttribute("has-mention")).to.be.true;
    expect(el.getAttribute("reply-count")).to.equal("3");
  });

  it("propagates blip + wave id + author + focus state to the inner wavy-blip-card", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b2"
        data-wave-id="w2"
        author-name="Bob"
        posted-at="just now"
        is-author
        focused
        unread
      >
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const card = el.renderRoot.querySelector("wavy-blip-card");
    expect(card).to.exist;
    expect(card.getAttribute("data-blip-id")).to.equal("b2");
    expect(card.getAttribute("data-wave-id")).to.equal("w2");
    expect(card.getAttribute("author-name")).to.equal("Bob");
    expect(card.hasAttribute("focused")).to.be.true;
    expect(card.hasAttribute("unread")).to.be.true;
  });

  it("renders the per-blip toolbar in the metadata slot", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b3" data-wave-id="w3" author-name="Carol">
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    expect(toolbar).to.exist;
    expect(toolbar.getAttribute("data-blip-id")).to.equal("b3");
  });

  it("re-emits wave-blip-reply-requested with blip context when toolbar Reply fires", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b4" data-wave-id="w4" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    await toolbar.updateComplete;
    const replyBtn = toolbar.renderRoot.querySelector("[data-toolbar-action='reply']");
    expect(replyBtn).to.exist;
    setTimeout(() => replyBtn.click(), 0);
    const ev = await oneEvent(el, "wave-blip-reply-requested");
    expect(ev.detail.blipId).to.equal("b4");
    expect(ev.detail.waveId).to.equal("w4");
  });

  it("re-emits wave-blip-edit-requested when toolbar Edit fires", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b5" data-wave-id="w5" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    await toolbar.updateComplete;
    const editBtn = toolbar.renderRoot.querySelector("[data-toolbar-action='edit']");
    setTimeout(() => editBtn.click(), 0);
    const ev = await oneEvent(el, "wave-blip-edit-requested");
    expect(ev.detail.blipId).to.equal("b5");
  });

  // F-3.S4 (#1038, R-5.6 F.6): the Delete button on the per-blip
  // toolbar bubbles wave-blip-toolbar-delete; wave-blip re-emits
  // wave-blip-delete-requested with {blipId, waveId, bodySize} so the J2CL
  // compose view can route through the wavy confirm dialog and the
  // controller's onDeleteBlipRequested listener.
  it("re-emits wave-blip-delete-requested when toolbar Delete fires", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b9"
        data-wave-id="w9"
        data-blip-doc-size="17"
        author-name="A"
      ></wave-blip>
    `);
    await el.updateComplete;
    const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
    await toolbar.updateComplete;
    const deleteBtn = toolbar.renderRoot.querySelector("[data-toolbar-action='delete']");
    expect(deleteBtn).to.exist;
    setTimeout(() => deleteBtn.click(), 0);
    const ev = await oneEvent(el, "wave-blip-delete-requested");
    expect(ev.detail.blipId).to.equal("b9");
    expect(ev.detail.waveId).to.equal("w9");
    expect(ev.detail.bodySize).to.equal(17);
    expect(ev.bubbles).to.be.true;
    expect(ev.composed).to.be.true;
  });

  it("does not render a body-level inline-reply chip when reply-count > 0", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b6" data-wave-id="w6" author-name="A">x</wave-blip>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-inline-reply-chip='true']")).to.not.exist;

    el.replyCount = 4;
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[data-inline-reply-chip='true']")).to.not.exist;
  });

  it("compact thread chevron click requests inline thread toggle with blip context", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b7"
        data-wave-id="w7"
        author-name="A"
        reply-count="2"
      >
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const chevron = el.renderRoot.querySelector("[data-thread-chevron='true']");
    expect(chevron).to.exist;
    expect(chevron.getAttribute("aria-label")).to.equal("Collapse 2 replies under this blip");
    setTimeout(() => chevron.click(), 0);
    const ev = await oneEvent(el, "wave-blip-thread-toggle-requested");
    expect(ev.detail.blipId).to.equal("b7");
    expect(ev.detail.waveId).to.equal("w7");
  });

  it("compact thread chevron does not emit the shell depth-navigation event", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b7nav"
        data-wave-id="w7"
        author-name="A"
        reply-count="2"
      >
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const chevron = el.renderRoot.querySelector("[data-thread-chevron='true']");
    expect(chevron.getAttribute("title")).to.equal("Collapse 2 replies under this blip");
    let depthEventCount = 0;
    el.addEventListener("wavy-depth-drill-in", () => depthEventCount++);
    chevron.click();
    expect(depthEventCount).to.equal(0);
  });

  it("uses singular grammar for a one-reply chevron aria-label", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b7a" data-wave-id="w7" author-name="A" reply-count="1">
        body
      </wave-blip>
    `);
    await el.updateComplete;
    const chevron = el.renderRoot.querySelector("[data-thread-chevron='true']");
    expect(chevron.getAttribute("aria-label")).to.equal("Collapse 1 reply under this blip");
  });

  it("avatar click emits wave-blip-profile-requested with author id", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b8"
        data-wave-id="w8"
        author-id="dora@example.com"
        author-name="Dora"
      ></wave-blip>
    `);
    await el.updateComplete;
    const avatar = el.renderRoot.querySelector("[data-blip-avatar='true']");
    setTimeout(() => avatar.click(), 0);
    const ev = await oneEvent(el, "wave-blip-profile-requested");
    expect(ev.detail.blipId).to.equal("b8");
    expect(ev.detail.authorId).to.equal("dora@example.com");
  });

  it("renders the timestamp with an ISO-8601 datetime tooltip", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b9"
        data-wave-id="w9"
        author-name="E"
        posted-at="5m ago"
        posted-at-iso="2026-04-26T12:00:00Z"
      ></wave-blip>
    `);
    await el.updateComplete;
    const time = el.renderRoot.querySelector("time.posted");
    expect(time).to.exist;
    expect(time.getAttribute("title")).to.equal("2026-04-26T12:00:00Z");
    expect(time.getAttribute("datetime")).to.equal("2026-04-26T12:00:00Z");
  });

  it("does not append root-depth debug text to the visible timestamp", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b9r"
        data-wave-id="w9"
        author-name="E"
        posted-at="5m ago"
        data-blip-depth="root"
      ></wave-blip>
    `);
    await el.updateComplete;
    const time = el.renderRoot.querySelector("time.posted");
    expect(time.textContent.trim()).to.equal("5m ago");
    expect(time.textContent).to.not.include("root");
  });

  it("omits the datetime attribute entirely when postedAtIso is empty", async () => {
    // Empty datetime="" is invalid HTML and confuses ATs / validators. The
    // wrapper must skip the attribute (via lit's ifDefined directive)
    // rather than emit an empty string.
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b9b"
        data-wave-id="w9b"
        author-name="E"
        posted-at="Blip b+9b"
      ></wave-blip>
    `);
    await el.updateComplete;
    const time = el.renderRoot.querySelector("time.posted");
    expect(time).to.exist;
    expect(time.hasAttribute("datetime")).to.be.false;
  });

  it("Link button builds a permalink that preserves view=j2cl-root and uses a fragment for the blip", async () => {
    // The server only recognises the `wave` query param. The previous
    // implementation built `?wave=...&blip=...`, which dropped the
    // `view=j2cl-root` shell route and used an unsupported `blip` query
    // param. The permalink must round-trip through the J2CL root shell and
    // anchor to this blip via the URL fragment.
    const originalHref = window.location.href;
    const baseUrl = new URL(window.location.href);
    baseUrl.search = "?view=j2cl-root";
    baseUrl.hash = "";
    history.replaceState(null, "", baseUrl.toString());
    try {
      const el = await fixture(html`
        <wave-blip
          data-blip-id="b+link"
          data-wave-id="example.com/w+w1"
          author-name="A"
        ></wave-blip>
      `);
      await el.updateComplete;
      setTimeout(() => {
        const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
        toolbar.dispatchEvent(
          new CustomEvent("wave-blip-toolbar-link", {
            bubbles: true,
            composed: true
          })
        );
      }, 0);
      const ev = await oneEvent(el, "wave-blip-link-copied");
      const url = new URL(ev.detail.url);
      expect(url.searchParams.get("view")).to.equal("j2cl-root");
      expect(url.searchParams.get("wave")).to.equal("example.com/w+w1");
      expect(url.searchParams.has("blip")).to.be.false;
      expect(url.hash).to.equal("#blip-b+link");
    } finally {
      history.replaceState(null, "", originalHref);
    }
  });

  it("blipView getter returns a frozen snapshot mirroring the F-0 plugin contract", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b10"
        data-wave-id="w10"
        author-id="a@x"
        author-name="Anne"
        posted-at="1m"
        posted-at-iso="2026-04-26T12:00:00Z"
        is-author
        unread
        has-mention
        reply-count="5"
      ></wave-blip>
    `);
    await el.updateComplete;
    const view = el.blipView;
    expect(Object.isFrozen(view)).to.be.true;
    expect(view.id).to.equal("b10");
    expect(view.waveId).to.equal("w10");
    expect(view.authorName).to.equal("Anne");
    expect(view.authorId).to.equal("a@x");
    expect(view.postedAtIso).to.equal("2026-04-26T12:00:00Z");
    expect(view.isAuthor).to.be.true;
    expect(view.unread).to.be.true;
    expect(view.hasMention).to.be.true;
    expect(view.replyCount).to.equal(5);
  });

  it("blip-extension slot still resolves the F-0 plugin context (forwarding contract)", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b11" data-wave-id="w11" author-name="A">
        body
        <span slot="blip-extension" data-test-plugin="true">hello plugin</span>
      </wave-blip>
    `);
    await el.updateComplete;
    const card = el.renderRoot.querySelector("wavy-blip-card");
    // The wrapper forwards the slot through; the inner card sees the plugin
    // node via its own blip-extension slot.
    const cardSlot = card.renderRoot.querySelector("slot[name='blip-extension']");
    expect(cardSlot).to.exist;
    // The inner card's plugin contract surfaces via host.dataset on the card.
    expect(card.dataset.blipId).to.equal("b11");
    expect(card.dataset.waveId).to.equal("w11");
  });

  it("firePulse triggers the F-0 live-pulse on the inner card", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b12" data-wave-id="w12" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    el.firePulse();
    await aTimeout(0);
    const card = el.renderRoot.querySelector("wavy-blip-card");
    expect(card.hasAttribute("live-pulse")).to.be.true;
  });

  it("cyan mention rail attribute toggles via has-mention reflection", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b13" data-wave-id="w13" author-name="A"></wave-blip>
    `);
    el.hasMention = true;
    await el.updateComplete;
    expect(el.hasAttribute("has-mention")).to.be.true;
    el.hasMention = false;
    await el.updateComplete;
    expect(el.hasAttribute("has-mention")).to.be.false;
  });

  // F-3.S2 (#1038, R-5.4) per-blip task integration.
  it("does not mount a generic task affordance on normal blips", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b20" data-wave-id="w20" author-name="A"></wave-blip>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector('[data-task-affordance-slot]')).to.equal(null);
    expect(el.renderRoot.textContent).to.not.include("Task");
  });

  it("mounts <wavy-task-affordance> only when task state is present", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b20"
        data-wave-id="w20"
        author-name="A"
        data-task-assignee="alice@example.com"
      ></wave-blip>
    `);
    await el.updateComplete;
    const slot = el.renderRoot.querySelector('[data-task-affordance-slot]');
    expect(slot).to.exist;
    const affordance = slot.querySelector("wavy-task-affordance");
    expect(affordance).to.exist;
    expect(affordance.getAttribute("data-blip-id")).to.equal("b20");
    expect(affordance.getAttribute("data-wave-id")).to.equal("w20");
  });

  it("keeps task affordances out of visible focus flow until the blip is active", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b20a"
        data-wave-id="w20a"
        author-name="A"
        data-task-completed
      ></wave-blip>
    `);
    await el.updateComplete;
    const slot = el.renderRoot.querySelector('[data-task-affordance-slot]');
    expect(getComputedStyle(slot).visibility).to.equal("hidden");

    el.setAttribute("focused", "");
    await el.updateComplete;
    expect(getComputedStyle(slot).visibility).to.equal("visible");

    el.removeAttribute("focused");
    el.setAttribute("tabindex", "0");
    await el.updateComplete;
    expect(getComputedStyle(slot).visibility).to.equal("visible");
  });

  it("reflects taskCompleted as data-task-completed on the host", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b21" data-wave-id="w21" data-task-completed></wave-blip>
    `);
    await el.updateComplete;
    expect(el.taskCompleted).to.equal(true);
    expect(el.hasAttribute("data-task-completed")).to.equal(true);
    el.taskCompleted = false;
    await el.updateComplete;
    expect(el.hasAttribute("data-task-completed")).to.equal(false);
  });

  it("keeps task affordance mounted after an optimistic reopen toggle", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b21b" data-wave-id="w21b" data-task-completed></wave-blip>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector('[data-task-affordance-slot]')).to.exist;
    el.taskCompleted = false;
    await el.updateComplete;
    expect(el.renderRoot.querySelector('[data-task-affordance-slot]')).to.exist;
  });

  it("keeps task affordance mounted after DOM rebuild via data-task-present (reopened task)", async () => {
    // Simulate the renderWindow DOM-rebuild path: the renderer emits data-task-present
    // so a reopened task with no assignee/due-date keeps its affordance visible even
    // though _taskPresent is lost when the old <wave-blip> node was destroyed.
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b21c"
        data-wave-id="w21c"
        data-task-present
      ></wave-blip>
    `);
    await el.updateComplete;
    expect(el.renderRoot.querySelector('[data-task-affordance-slot]')).to.exist;
  });

  it("re-emits wave-blip-task-toggled from the inner affordance with full detail", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b22"
        data-wave-id="w22"
        data-blip-doc-size="17"
        data-task-assignee="alice@example.com"
      ></wave-blip>
    `);
    await el.updateComplete;
    const affordance = el.renderRoot.querySelector("wavy-task-affordance");
    await affordance.updateComplete;
    const toggle = affordance.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    let captured = null;
    el.addEventListener("wave-blip-task-toggled", (e) => {
      captured = e.detail;
    });
    toggle.click();
    await el.updateComplete;
    expect(captured).to.deep.equal({
      blipId: "b22",
      waveId: "w22",
      completed: true,
      bodySize: 17
    });
  });

  it("does not strike through the whole body when data-task-completed is set", async () => {
    const el = await fixture(html`
      <wave-blip data-blip-id="b30" data-wave-id="w30" data-task-completed>
        Body text
      </wave-blip>
    `);
    await el.updateComplete;
    const body = el.renderRoot.querySelector(".body");
    expect(body).to.exist;
    const style = getComputedStyle(body);
    expect(style.textDecorationLine || style.textDecoration).to.not.contain("line-through");
  });

  it("propagates taskAssignee + taskDueDate to the inner affordance", async () => {
    const el = await fixture(html`
      <wave-blip
        data-blip-id="b31"
        data-wave-id="w31"
        data-task-assignee="bob@example.com"
        data-task-due-date="2026-05-01"
      ></wave-blip>
    `);
    await el.updateComplete;
    const affordance = el.renderRoot.querySelector("wavy-task-affordance");
    expect(affordance).to.exist;
    expect(affordance.getAttribute("data-task-assignee")).to.equal(
      "bob@example.com"
    );
    expect(affordance.getAttribute("data-task-due-date")).to.equal(
      "2026-05-01"
    );
  });

  // V-4 (#1102): per-blip chrome on the open wave.
  describe("V-4 per-blip chrome", () => {
    it("avatar palette is deterministic per author-id (same id → same palette index)", async () => {
      const a = await fixture(html`
        <wave-blip data-blip-id="b40" author-id="alice@example.com" author-name="Alice"></wave-blip>
      `);
      const b = await fixture(html`
        <wave-blip data-blip-id="b41" author-id="alice@example.com" author-name="Alice2"></wave-blip>
      `);
      await a.updateComplete;
      await b.updateComplete;
      const aPalette = a.renderRoot.querySelector(".avatar").getAttribute("data-palette");
      const bPalette = b.renderRoot.querySelector(".avatar").getAttribute("data-palette");
      expect(aPalette).to.equal(bPalette);
      expect(["0", "1", "2", "3"]).to.include(aPalette);
    });

    it("avatar palette differs across distinct author ids (at least one of three differs)", async () => {
      const ids = ["alice@x", "bob@y", "carol@z"];
      const palettes = new Set();
      for (const id of ids) {
        const el = await fixture(html`
          <wave-blip data-blip-id="b" author-id=${id}></wave-blip>
        `);
        await el.updateComplete;
        palettes.add(el.renderRoot.querySelector(".avatar").getAttribute("data-palette"));
      }
      expect(palettes.size).to.be.greaterThan(1);
    });

    it("renders the thread chevron only when reply-count > 0", async () => {
      const el = await fixture(html`
        <wave-blip data-blip-id="b42" author-name="A">x</wave-blip>
      `);
      await el.updateComplete;
      let chevron = el.renderRoot.querySelector(".thread-chevron[data-thread-chevron='true']");
      expect(chevron, "no chevron when no replies").to.not.exist;

      el.replyCount = 2;
      await el.updateComplete;
      chevron = el.renderRoot.querySelector(".thread-chevron[data-thread-chevron='true']");
      expect(chevron, "chevron when reply-count > 0").to.exist;
      expect(chevron.textContent.trim()).to.equal("▾"); // ▾
    });

    it("chevron flips to ▸ when data-thread-collapsed is set", async () => {
      const el = await fixture(html`
        <wave-blip
          data-blip-id="b43"
          author-name="A"
          reply-count="2"
          data-thread-collapsed
        >x</wave-blip>
      `);
      await el.updateComplete;
      const chevron = el.renderRoot.querySelector(".thread-chevron[data-thread-chevron='true']");
      expect(chevron).to.exist;
      expect(chevron.textContent.trim()).to.equal("▸"); // ▸
    });

    it("root-depth blip keeps the timestamp free of debug depth text", async () => {
      const el = await fixture(html`
        <wave-blip
          data-blip-id="b44"
          data-wave-id="w44"
          author-name="A"
          posted-at="2 hours ago"
          data-blip-depth="root"
        ></wave-blip>
      `);
      await el.updateComplete;
      const time = el.renderRoot.querySelector("time.posted");
      expect(time.textContent).to.contain("2 hours ago");
      expect(time.textContent).to.not.contain("· root");
      expect(time.textContent).to.not.contain("root");
    });

    it("reply-depth blip omits the root suffix", async () => {
      const el = await fixture(html`
        <wave-blip
          data-blip-id="b45"
          author-name="A"
          posted-at="1m ago"
          data-blip-depth="reply"
        ></wave-blip>
      `);
      await el.updateComplete;
      const time = el.renderRoot.querySelector("time.posted");
      expect(time.textContent).to.not.contain("· root");
    });

    it("focused blip stamps data-variant='focused' on the inner toolbar", async () => {
      const el = await fixture(html`
        <wave-blip data-blip-id="b46" data-wave-id="w46" focused></wave-blip>
      `);
      await el.updateComplete;
      const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
      expect(toolbar.getAttribute("data-variant")).to.equal("focused");
    });

    it("tabindex=0 blip paints as visually focused for GWT parity", async () => {
      const el = await fixture(html`
        <wave-blip data-blip-id="b48" data-wave-id="w48" tabindex="0"></wave-blip>
      `);
      await el.updateComplete;
      const card = el.renderRoot.querySelector("wavy-blip-card");
      const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
      expect(card.hasAttribute("focused")).to.equal(true);
      expect(toolbar.getAttribute("data-variant")).to.equal("focused");
    });

    it("unfocused blip stamps data-variant='default' on the inner toolbar", async () => {
      const el = await fixture(html`
        <wave-blip data-blip-id="b47" data-wave-id="w47"></wave-blip>
      `);
      await el.updateComplete;
      const toolbar = el.renderRoot.querySelector("wave-blip-toolbar");
      expect(toolbar.getAttribute("data-variant")).to.equal("default");
    });
  });
});

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-search-rail.js";

describe("<wavy-search-rail>", () => {
  it("registers the F-2.S3 search-rail element", () => {
    expect(customElements.get("wavy-search-rail")).to.exist;
  });

  it("defaults to query=in:inbox and active folder=inbox", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    expect(el.query).to.equal("in:inbox");
    expect(el.activeFolder).to.equal("inbox");
    const inbox = el.renderRoot.querySelector('[data-folder-id="inbox"]');
    expect(inbox.getAttribute("aria-current")).to.equal("page");
  });

  it("renders all six saved-search folders with canonical query strings (B.5–B.10)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const folders = Array.from(el.renderRoot.querySelectorAll("button.folder"));
    const map = new Map(folders.map((b) => [b.dataset.folderId, b.dataset.query]));
    expect(map.get("inbox")).to.equal("in:inbox");
    expect(map.get("mentions")).to.equal("mentions:me");
    expect(map.get("tasks")).to.equal("tasks:me");
    expect(map.get("public")).to.equal("with:@");
    expect(map.get("archive")).to.equal("in:archive");
    expect(map.get("pinned")).to.equal("in:pinned");
    const labels = folders.map((b) => b.querySelector(".label").textContent.trim());
    expect(labels).to.deep.equal(["Inbox", "Mentions", "Tasks", "Public", "Archive", "Pinned"]);
  });

  it("Enter in the query box emits wavy-search-submit (B.1)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const input = el.renderRoot.querySelector("input.query");
    input.value = "in:archive tag:work";
    setTimeout(() => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    }, 0);
    const evt = await oneEvent(el, "wavy-search-submit");
    expect(evt.detail.query).to.equal("in:archive tag:work");
  });

  it("renders waveform glyph next to the query input (B.1)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    expect(el.renderRoot.querySelector(".waveform svg")).to.exist;
  });

  it("help-trigger click emits wavy-search-help-toggle (B.2; modal not owned by rail)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const help = el.renderRoot.querySelector(".help-trigger");
    expect(help).to.exist;
    expect(help.getAttribute("aria-haspopup")).to.equal("dialog");
    expect(help.getAttribute("aria-controls")).to.equal("wavy-search-help");
    setTimeout(() => help.click(), 0);
    const evt = await oneEvent(el, "wavy-search-help-toggle");
    expect(evt).to.exist;
    // The rail does NOT own a child <wavy-search-help> instance — the
    // singleton lives at document level.
    expect(el.renderRoot.querySelector("wavy-search-help")).to.be.null;
  });

  it("New Wave button click emits wavy-new-wave-requested and carries aria-keyshortcuts (B.3)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const newWave = el.renderRoot.querySelector(".new-wave");
    expect(newWave).to.exist;
    expect(newWave.getAttribute("aria-keyshortcuts")).to.equal(
      "Shift+Meta+O Shift+Control+O"
    );
    expect(newWave.dataset.shortcut).to.equal("Shift+Cmd+O");
    setTimeout(() => newWave.click(), 0);
    const evt = await oneEvent(el, "wavy-new-wave-requested");
    expect(evt).to.exist;
  });

  it("Manage saved searches click emits wavy-manage-saved-searches-requested (B.4)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const manage = el.renderRoot.querySelector(".manage-saved");
    setTimeout(() => manage.click(), 0);
    const evt = await oneEvent(el, "wavy-manage-saved-searches-requested");
    expect(evt).to.exist;
  });

  it("Refresh click emits wavy-search-refresh-requested (B.11)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const refresh = el.renderRoot.querySelector(".refresh");
    setTimeout(() => refresh.click(), 0);
    const evt = await oneEvent(el, "wavy-search-refresh-requested");
    expect(evt).to.exist;
  });

  it("clicking a folder flips aria-current and emits wavy-saved-search-selected", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const archive = el.renderRoot.querySelector('[data-folder-id="archive"]');
    setTimeout(() => archive.click(), 0);
    const evt = await oneEvent(el, "wavy-saved-search-selected");
    expect(evt.detail.folderId).to.equal("archive");
    expect(evt.detail.query).to.equal("in:archive");
    await el.updateComplete;
    expect(archive.getAttribute("aria-current")).to.equal("page");
    expect(
      el.renderRoot
        .querySelector('[data-folder-id="inbox"]')
        .getAttribute("aria-current")
    ).to.equal("false");
  });

  it("setting query=in:archive ... derives Archive as active folder (programmatic)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    el.query = "in:archive orderby:datedesc";
    await el.updateComplete;
    expect(el.activeFolder).to.equal("archive");
    expect(
      el.renderRoot
        .querySelector('[data-folder-id="archive"]')
        .getAttribute("aria-current")
    ).to.equal("page");
  });

  it("custom query that doesn't match a folder leaves no aria-current", async () => {
    const el = await fixture(
      html`<wavy-search-rail query="title:meeting"></wavy-search-rail>`
    );
    await el.updateComplete;
    expect(el.activeFolder).to.equal("");
    const folders = el.renderRoot.querySelectorAll("button.folder");
    folders.forEach((b) => expect(b.getAttribute("aria-current")).to.equal("false"));
  });

  it("Mentions violet dot is hidden by default and revealed when mentions-unread > 0", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const dot = el.renderRoot
      .querySelector('[data-folder-id="mentions"]')
      .querySelector(".mentions-dot");
    expect(dot).to.exist;
    expect(dot.hasAttribute("hidden")).to.be.true;
    el.mentionsUnread = 3;
    await el.updateComplete;
    expect(dot.hasAttribute("hidden")).to.be.false;
  });

  it("Mentions dot uses --wavy-signal-violet (NOT cyan)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const cssText = WavySearchRail_styleText();
    expect(cssText).to.include("var(--wavy-signal-violet");
  });

  it("Tasks amber chip is hidden by default and revealed when tasks-pending > 0", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const chip = el.renderRoot
      .querySelector('[data-folder-id="tasks"]')
      .querySelector(".tasks-chip");
    expect(chip).to.exist;
    expect(chip.hasAttribute("hidden")).to.be.true;
    el.tasksPending = 7;
    await el.updateComplete;
    expect(chip.hasAttribute("hidden")).to.be.false;
    expect(chip.textContent.trim()).to.equal("7");
  });

  it("Tasks chip uses --wavy-signal-amber", async () => {
    const cssText = WavySearchRail_styleText();
    expect(cssText).to.include("var(--wavy-signal-amber");
  });

  it("result-count <p> is aria-live polite (B.12)", async () => {
    const el = await fixture(
      html`<wavy-search-rail result-count="133 waves"></wavy-search-rail>`
    );
    await el.updateComplete;
    const p = el.renderRoot.querySelector("p.result-count");
    expect(p).to.exist;
    expect(p.getAttribute("aria-live")).to.equal("polite");
    expect(p.textContent.trim()).to.equal("133 waves");
  });

  it("does NOT expose a default slot for SSR fallback children (#1060)", async () => {
    // F-2 follow-up (#1060): the previous default slot projected the
    // SSR'd light DOM under the rendered shadow chrome and painted the
    // rail twice. The shadow-DOM render is now self-contained; light
    // DOM children supplied for SSR fallback have no slot to project
    // into and are visually hidden after upgrade.
    const el = await fixture(html`
      <wavy-search-rail>
        <div data-stub-card="1">card</div>
      </wavy-search-rail>
    `);
    await el.updateComplete;
    const defaultSlot = Array.from(
      el.renderRoot.querySelectorAll("slot")
    ).find((s) => !s.hasAttribute("name"));
    expect(defaultSlot, "no default <slot> in shadow DOM").to.not.exist;
    const stub = el.querySelector('[data-stub-card="1"]');
    expect(stub, "light child stays in light DOM").to.exist;
    expect(stub.assignedSlot, "light child must not project anywhere")
      .to.equal(null);
  });

  // F-4 (#1039 / R-4.7) — filter chip strip.
  describe("filter chip strip (F-4 / R-4.7)", () => {
    it("renders three filter chips inside <details data-j2cl-filter-strip>", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(strip, "filter strip must mount").to.exist;
      const chips = Array.from(strip.querySelectorAll("button.filter-chip"));
      expect(chips.map((c) => c.dataset.filterId)).to.deep.equal([
        "unread",
        "attachments",
        "from-me"
      ]);
    });

    it("clicking a chip composes the token into the query and emits submit", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      expect(submit.detail.query).to.equal("in:inbox is:unread");
      await el.updateComplete;
      expect(chip.getAttribute("aria-pressed")).to.equal("true");
    });

    it("toggling the chip off removes the token (case-insensitive)", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="IS:UNREAD foo"></wavy-search-rail>`
      );
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      expect(chip.getAttribute("aria-pressed")).to.equal("true");
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      // Removal must drop ALL case-insensitive matches and keep user tokens
      expect(submit.detail.query).to.equal("foo");
    });

    it("emits wavy-search-filter-toggled with active flag and filterId", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="attachments"]');
      setTimeout(() => chip.click(), 0);
      const evt = await oneEvent(el, "wavy-search-filter-toggled");
      expect(evt.detail.filterId).to.equal("attachments");
      expect(evt.detail.token).to.equal("has:attachment");
      expect(evt.detail.active).to.equal(true);
    });

    it("preserves user-typed tokens when adding a filter", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="from:bob in:inbox"></wavy-search-rail>`
      );
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      expect(submit.detail.query).to.equal("from:bob in:inbox is:unread");
    });

    it("does not match substring tokens (is:unread does not collide with is:unread-foo)", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="is:unread-foo bar"></wavy-search-rail>`
      );
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      // Token equality is exact, so the chip should NOT be active here.
      expect(chip.getAttribute("aria-pressed")).to.equal("false");
    });
  });

  // J-UI-1 (#1079): the rail must expose a `cards` slot so the J2CL
  // search panel can project <wavy-search-rail-card> children inside the
  // shadow DOM. Without the slot the children would be hidden post-upgrade.
  describe("J-UI-1 cards slot (#1079)", () => {
    it("declares a <slot name=\"cards\"> in the shadow DOM", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      expect(slot, "rail must expose a cards slot for digest projection").to.exist;
    });

    it("accepts <wavy-search-rail-card> light-DOM children projected into the cards slot", async () => {
      const el = await fixture(html`
        <wavy-search-rail>
          <wavy-search-rail-card slot="cards" data-wave-id="w+a"></wavy-search-rail-card>
          <wavy-search-rail-card slot="cards" data-wave-id="w+b"></wavy-search-rail-card>
        </wavy-search-rail>
      `);
      await el.updateComplete;
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      const assigned = slot.assignedElements();
      expect(assigned.map((n) => n.dataset.waveId)).to.deep.equal(["w+a", "w+b"]);
    });

    it("preserves the saved-search list above the cards slot and filter strip below it", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const folders = el.renderRoot.querySelector("ul.folders");
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      const filters = el.renderRoot.querySelector("details.filters");
      expect(folders, "saved-search list mounts").to.exist;
      expect(slot, "cards slot mounts").to.exist;
      expect(filters, "filter strip mounts").to.exist;
      // Document order: folders, then cards slot, then filter strip.
      expect(folders.compareDocumentPosition(slot) & Node.DOCUMENT_POSITION_FOLLOWING).to.be.greaterThan(0);
      expect(slot.compareDocumentPosition(filters) & Node.DOCUMENT_POSITION_FOLLOWING).to.be.greaterThan(0);
    });
  });
});

// Helper: read the element's static stylesheet text so we can assert
// the wavy token names actually appear (defends against silent renames).
function WavySearchRail_styleText() {
  const cls = customElements.get("wavy-search-rail");
  const styles = cls.styles;
  const arr = Array.isArray(styles) ? styles : [styles];
  return arr.map((s) => (s && s.cssText) || "").join("\n");
}

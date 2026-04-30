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
    // G-PORT-2 (#1111): Refresh moved into the panel-level action row
    // alongside Sort and Filter, accessed via `[data-digest-action]`.
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const refresh = el.renderRoot.querySelector('[data-digest-action="refresh"]');
    expect(refresh, "refresh button must mount in the action row").to.exist;
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

  it("Mentions red dot is hidden by default and revealed when mentions-unread > 0", async () => {
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

  it("Mentions dot uses the GWT unread red, not the task/toolbar palettes", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const dot = el.renderRoot
      .querySelector('[data-folder-id="mentions"]')
      .querySelector(".mentions-dot");
    expect(getComputedStyle(dot).backgroundColor).to.equal("rgb(229, 62, 62)");
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

  it("G-PORT-9: stretches the rail to the GWT viewport-height panel", async () => {
    const cssText = WavySearchRail_styleText();
    expect(cssText).to.include("min-height: var(--wavy-rail-min-height");
    expect(cssText).to.include("calc(100vh - 90px)");
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

  // J-UI-2 (#1080 / R-4.5): folder click must carry the user-visible
  // label so the J2CL controller can announce navigation via aria-live
  // without re-deriving the label from the folderId.
  describe("J-UI-2 navigation announce + focus (#1080)", () => {
    it("wavy-saved-search-selected detail carries folder label", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const archive = el.renderRoot.querySelector(
        '[data-folder-id="archive"]'
      );
      setTimeout(() => archive.click(), 0);
      const evt = await oneEvent(el, "wavy-saved-search-selected");
      expect(evt.detail.folderId).to.equal("archive");
      expect(evt.detail.label).to.equal("Archive");
      expect(evt.detail.query).to.equal("in:archive");
    });

    it("wavy-search-filter-toggled detail carries filter label", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="from-me"]');
      setTimeout(() => chip.click(), 0);
      const evt = await oneEvent(el, "wavy-search-filter-toggled");
      expect(evt.detail.filterId).to.equal("from-me");
      expect(evt.detail.label).to.equal("From me");
      expect(evt.detail.active).to.equal(true);
    });

    it("focusActiveFolder() moves focus to the aria-current=page button", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      el.query = "in:archive";
      await el.updateComplete;
      el.focusActiveFolder();
      const archive = el.renderRoot.querySelector(
        '[data-folder-id="archive"]'
      );
      expect(el.renderRoot.activeElement || el.shadowRoot.activeElement).to.equal(
        archive
      );
    });

    it("focusActiveFolder() is a no-op when no folder is active", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="title:meeting"></wavy-search-rail>`
      );
      await el.updateComplete;
      // No throw, no focus change.
      expect(() => el.focusActiveFolder()).to.not.throw();
    });

    it("each of the six folders emits its canonical query+label pair", async () => {
      const expected = [
        { id: "inbox", label: "Inbox", query: "in:inbox" },
        { id: "mentions", label: "Mentions", query: "mentions:me" },
        { id: "tasks", label: "Tasks", query: "tasks:me" },
        { id: "public", label: "Public", query: "with:@" },
        { id: "archive", label: "Archive", query: "in:archive" },
        { id: "pinned", label: "Pinned", query: "in:pinned" }
      ];
      for (const folder of expected) {
        const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
        await el.updateComplete;
        const button = el.renderRoot.querySelector(
          `[data-folder-id="${folder.id}"]`
        );
        setTimeout(() => button.click(), 0);
        const evt = await oneEvent(el, "wavy-saved-search-selected");
        expect(evt.detail).to.deep.equal({
          folderId: folder.id,
          label: folder.label,
          query: folder.query
        });
      }
    });

    it("each of the three chips composes with in:inbox and emits label", async () => {
      const expected = [
        { id: "unread", label: "Unread only", token: "is:unread" },
        { id: "attachments", label: "With attachments", token: "has:attachment" },
        { id: "from-me", label: "From me", token: "from:me" }
      ];
      for (const chip of expected) {
        const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
        await el.updateComplete;
        const button = el.renderRoot.querySelector(
          `[data-filter-id="${chip.id}"]`
        );
        setTimeout(() => button.click(), 0);
        const evt = await oneEvent(el, "wavy-search-filter-toggled");
        expect(evt.detail.filterId).to.equal(chip.id);
        expect(evt.detail.label).to.equal(chip.label);
        expect(evt.detail.token).to.equal(chip.token);
        expect(evt.detail.query).to.equal("in:inbox " + chip.token);
        expect(evt.detail.active).to.equal(true);
      }
    });

    it("chip toggle still emits BOTH wavy-search-filter-toggled and wavy-search-submit", async () => {
      // Defends against a refactor that splits the two events apart.
      // The Java view dedupes via chipDrivenSubmitPending; if either
      // event stops firing, the dedupe contract breaks silently.
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      const seen = [];
      el.addEventListener("wavy-search-filter-toggled", (e) =>
        seen.push("toggled:" + e.detail.query)
      );
      el.addEventListener("wavy-search-submit", (e) =>
        seen.push("submit:" + e.detail.query)
      );
      chip.click();
      expect(seen).to.deep.equal([
        "toggled:in:inbox is:unread",
        "submit:in:inbox is:unread"
      ]);
    });

    it("filter-toggled is dispatched BEFORE wavy-search-submit (dedup order contract)", async () => {
      // J-UI-2 (#1080): the J2CL view's chipDrivenSubmitPending dedup
      // assumes filter-toggled lands first, sets the flag, and the
      // following submit listener sees and consumes the flag. If a
      // future refactor reverses this order the J2CL submit handler
      // would issue a duplicate backend search.
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      const order = [];
      el.addEventListener("wavy-search-submit", () => order.push("submit"));
      el.addEventListener("wavy-search-filter-toggled", () =>
        order.push("toggled")
      );
      chip.click();
      // Re-assert order independent of registration order — the rail's
      // _toggleFilter must dispatch toggled first so the J2CL flag can
      // pre-arm before submit checks it.
      expect(order).to.deep.equal(["toggled", "submit"]);
    });
  });

  // G-PORT-2 (#1111): panel-level action row clones the GWT
  // SearchPresenter toolbar — refresh + sort + filter buttons in a
  // single visible row, each tagged with `data-digest-action="..."`
  // so the parity test resolves them on both views via one selector.
  describe("G-PORT-2 panel-level action row (#1111)", () => {
    it("renders an action row with refresh + sort + filter buttons", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const row = el.renderRoot.querySelector("[data-digest-action-row]");
      expect(row, "action row must mount").to.exist;
      const buttons = Array.from(row.querySelectorAll("button[data-digest-action]"));
      expect(buttons.map((b) => b.dataset.digestAction)).to.deep.equal([
        "refresh",
        "sort",
        "filter"
      ]);
    });

    it("each action button carries an aria-label for screen readers", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const refresh = el.renderRoot.querySelector('[data-digest-action="refresh"]');
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      const filter = el.renderRoot.querySelector('[data-digest-action="filter"]');
      expect(refresh.getAttribute("aria-label")).to.equal("Refresh search results");
      expect(sort.getAttribute("aria-label")).to.equal("Sort waves");
      expect(filter.getAttribute("aria-label")).to.equal("Filter waves");
    });

    it("Sort button click emits wavy-search-sort-requested", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      setTimeout(() => sort.click(), 0);
      const evt = await oneEvent(el, "wavy-search-sort-requested");
      expect(evt).to.exist;
    });

    it("Filter button click toggles the chip strip open and emits an event", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const filterBtn = el.renderRoot.querySelector('[data-digest-action="filter"]');
      const details = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(details.hasAttribute("open"), "starts collapsed").to.equal(false);

      // Use a click and capture the synchronous event without race risk:
      // wavy-search-filter-toggle-requested is fired synchronously inside
      // the handler, so attach the listener first and then click.
      let toggleEvt = null;
      el.addEventListener("wavy-search-filter-toggle-requested", (e) => {
        toggleEvt = e;
      });
      filterBtn.click();
      await el.updateComplete;
      expect(toggleEvt, "filter-toggle-requested fires").to.exist;
      expect(toggleEvt.detail.open).to.equal(true);
      expect(details.hasAttribute("open"), "details opens").to.equal(true);
      expect(filterBtn.getAttribute("aria-pressed")).to.equal("true");
      expect(filterBtn.getAttribute("aria-expanded")).to.equal("true");

      // Second click closes.
      filterBtn.click();
      await el.updateComplete;
      expect(details.hasAttribute("open")).to.equal(false);
      expect(filterBtn.getAttribute("aria-pressed")).to.equal("false");
    });

    it("filter button aria-controls points at the filter strip id", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const filterBtn = el.renderRoot.querySelector('[data-digest-action="filter"]');
      const details = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(filterBtn.getAttribute("aria-controls")).to.equal(details.id);
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

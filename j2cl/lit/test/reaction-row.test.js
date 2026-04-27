import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/reaction-row.js";

describe("<reaction-row>", () => {
  const reactions = [
    { emoji: "tada", count: 2, active: true, inspectLabel: "2 reactions for tada." },
    { emoji: "thumbs_up", count: 1, active: false, inspectLabel: "1 reaction for thumbs up." }
  ];

  it("renders stable reaction chips and live reaction count text", async () => {
    const el = await fixture(html`<reaction-row blip-id="b+1" .reactions=${reactions}></reaction-row>`);

    const chips = el.renderRoot.querySelectorAll("[data-reaction-chip]");
    expect(chips.length).to.equal(2);
    expect(chips[0].dataset.emoji).to.equal("tada");
    expect(chips[0].getAttribute("aria-pressed")).to.equal("true");
    expect(chips[0].getAttribute("aria-label")).to.include("2 people");
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.include(
      "3 reactions"
    );
  });

  it("emits add, toggle, and inspect events with stable ids", async () => {
    const el = await fixture(html`<reaction-row blip-id="b+1" .reactions=${reactions}></reaction-row>`);

    const addPromise = oneEvent(el, "reaction-add");
    el.renderRoot.querySelector("[data-reaction-add]").click();
    const addEvent = await addPromise;
    expect(addEvent.bubbles).to.equal(true);
    expect(addEvent.composed).to.equal(true);
    expect(addEvent.detail.blipId).to.equal("b+1");

    const togglePromise = oneEvent(el, "reaction-toggle");
    el.renderRoot.querySelector("[data-reaction-chip]").click();
    const toggleEvent = await togglePromise;
    expect(toggleEvent.bubbles).to.equal(true);
    expect(toggleEvent.composed).to.equal(true);
    expect(toggleEvent.detail).to.deep.equal({ blipId: "b+1", emoji: "tada" });

    const inspectPromise = oneEvent(el, "reaction-inspect");
    el.renderRoot.querySelector("[data-reaction-inspect]").click();
    const inspectEvent = await inspectPromise;
    expect(inspectEvent.bubbles).to.equal(true);
    expect(inspectEvent.composed).to.equal(true);
    expect(inspectEvent.detail).to.deep.equal({ blipId: "b+1", emoji: "tada" });
  });

  it("renders an empty add-only state without mutation decisions", async () => {
    const el = await fixture(html`<reaction-row blip-id="b+empty" .reactions=${[]}></reaction-row>`);

    expect(el.renderRoot.querySelectorAll("[data-reaction-chip]").length).to.equal(0);
    expect(el.renderRoot.querySelector("[data-reaction-add]").getAttribute("aria-label")).to.equal(
      "Add reaction"
    );
  });

  it("normalizes malformed reaction counts for visible and live text", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+bad-count"
        .reactions=${[{ emoji: "wave", count: "abc" }]}
      ></reaction-row>
    `);

    expect(el.renderRoot.querySelector("[data-reaction-chip]").textContent).to.include("wave 0");
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.equal(
      "0 reactions."
    );
  });

  it("accepts pre-resolved reaction glyphs without changing event ids", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+glyph"
        .reactions=${[
          { emoji: "thumbs_up", glyph: "👍", accessibleName: "thumbs up", count: 1 }
        ]}
      ></reaction-row>
    `);
    const togglePromise = oneEvent(el, "reaction-toggle");

    expect(el.renderRoot.querySelector("[data-reaction-chip]").textContent).to.include("👍 1");
    el.renderRoot.querySelector("[data-reaction-chip]").click();
    expect((await togglePromise).detail).to.deep.equal({
      blipId: "b+glyph",
      emoji: "thumbs_up"
    });
  });

  it("clamps negative and infinite reaction counts", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+safe-count"
        .reactions=${[
          { emoji: "sad", count: -3 },
          { emoji: "fire", count: Infinity }
        ]}
      ></reaction-row>
    `);

    const chips = el.renderRoot.querySelectorAll("[data-reaction-chip]");
    expect(chips[0].textContent).to.include("sad 0");
    expect(chips[1].textContent).to.include("fire 0");
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.equal(
      "0 reactions."
    );
  });

  it("filters malformed reaction entries before rendering", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+bad-reactions"
        .reactions=${[null, "bad", { emoji: "wave", count: 1 }]}
      ></reaction-row>
    `);

    const chips = el.renderRoot.querySelectorAll("[data-reaction-chip]");
    expect(chips.length).to.equal(1);
    expect(chips[0].dataset.emoji).to.equal("wave");
  });

  it("falls back to empty when reactions is not an array", async () => {
    const el = await fixture(html`<reaction-row blip-id="b+non-array" .reactions=${"not-an-array"}></reaction-row>`);
    expect(el.renderRoot.querySelectorAll("[data-reaction-chip]").length).to.equal(0);
  });

  // F-3.S3 (#1038, R-5.5 step 13): active chips adopt the wavy violet
  // tokens. The CSS uses `--wavy-signal-violet` for the border and
  // `--wavy-signal-violet-soft` for the fill — the tokens may not be
  // resolved in the test harness, so we assert the source carries the
  // tokens instead of the resolved colour. (Pinned at the source-level
  // by the parity fixture.)
  it("includes wavy signal-violet tokens for the active-pressed state", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+violet"
        .reactions=${[{ emoji: "👍", count: 1, active: true }]}
      ></reaction-row>
    `);
    const styles = String(
      (el.constructor.styles && el.constructor.styles.cssText) || ""
    );
    expect(styles.indexOf("--wavy-signal-violet")).to.be.greaterThanOrEqual(0);
    expect(styles.indexOf("--wavy-signal-violet-soft")).to.be.greaterThanOrEqual(0);
    expect(styles.indexOf("--wavy-radius-pill")).to.be.greaterThanOrEqual(0);
    expect(styles.indexOf("--wavy-pulse-ring")).to.be.greaterThanOrEqual(0);
  });

  // F-3.S3 (#1038, R-5.5 step 14): a chip whose count increases via
  // live-update fires a one-shot `data-live-pulse` attribute that the
  // CSS pulse animation hooks into.
  it("pulses chips whose count increases between renders", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+pulse"
        .reactions=${[{ emoji: "🎉", count: 1 }]}
      ></reaction-row>
    `);

    // First render establishes the baseline.
    el.reactions = [{ emoji: "🎉", count: 2 }];
    await el.updateComplete;
    const chip = el.renderRoot.querySelector(
      "[data-reaction-chip][data-emoji='🎉']"
    );
    expect(chip).to.exist;
    expect(chip.getAttribute("data-live-pulse")).to.equal("true");
  });

  // F-3.S3: counts that go DOWN do NOT trigger the pulse (avoids
  // flashing when other users remove their reactions).
  it("does not pulse when a chip's count decreases", async () => {
    const el = await fixture(html`
      <reaction-row
        blip-id="b+nopulse"
        .reactions=${[{ emoji: "❤", count: 3 }]}
      ></reaction-row>
    `);
    el.reactions = [{ emoji: "❤", count: 2 }];
    await el.updateComplete;
    const chip = el.renderRoot.querySelector(
      "[data-reaction-chip][data-emoji='❤']"
    );
    expect(chip.hasAttribute("data-live-pulse")).to.equal(false);
  });
});

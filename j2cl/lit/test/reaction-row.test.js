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
    expect((await addPromise).detail.blipId).to.equal("b+1");

    const togglePromise = oneEvent(el, "reaction-toggle");
    el.renderRoot.querySelector("[data-reaction-chip]").click();
    expect((await togglePromise).detail).to.deep.equal({ blipId: "b+1", emoji: "tada" });

    const inspectPromise = oneEvent(el, "reaction-inspect");
    el.renderRoot.querySelector("[data-reaction-inspect]").click();
    expect((await inspectPromise).detail).to.deep.equal({ blipId: "b+1", emoji: "tada" });
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
});

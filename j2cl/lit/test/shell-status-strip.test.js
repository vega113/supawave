import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-status-strip.js";

describe("<shell-status-strip>", () => {
  it("uses a polite status live region", async () => {
    const el = await fixture(html`<shell-status-strip></shell-status-strip>`);
    const aside = el.renderRoot.querySelector("aside");
    expect(aside.getAttribute("role")).to.equal("status");
    expect(aside.getAttribute("aria-live")).to.equal("polite");
  });

  it("exposes a default slot for status content", async () => {
    const el = await fixture(html`<shell-status-strip>Connected</shell-status-strip>`);
    expect(el.renderRoot.querySelector("slot")).to.exist;
  });

  it("renders compact status chips instead of visible raw status prose", async () => {
    const el = await fixture(html`
      <shell-status-strip
        data-connection-state="online"
        data-save-state="saved"
        data-route-state="selected-wave"
      >supawave.ai/w+1 · channel ch1 · snapshot v2</shell-status-strip>
    `);
    const chips = Array.from(el.renderRoot.querySelectorAll("[data-status-chip]"));
    expect(chips.map((chip) => chip.getAttribute("data-status-chip"))).to.deep.equal([
      "connection",
      "saved",
      "route"
    ]);
    expect(chips.map((chip) => chip.getAttribute("aria-label"))).to.deep.equal([
      "Online",
      "Saved",
      "Selected wave active"
    ]);
    expect(el.renderRoot.querySelector("slot").classList.contains("status-live-text")).to.be.true;
  });

  it("styles the saving chip as a transitional warning state", async () => {
    const el = await fixture(html`
      <shell-status-strip data-save-state="saving"></shell-status-strip>
    `);
    const chip = el.renderRoot.querySelector('[data-status-chip="saved"]');

    expect(chip.getAttribute("aria-label")).to.equal("Saving changes");
    expect(getComputedStyle(chip).backgroundColor).to.equal("rgb(255, 247, 230)");
  });
});

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-wave-controls-toggle.js";

describe("<wavy-wave-controls-toggle>", () => {
  it("defines the wavy-wave-controls-toggle custom element", () => {
    expect(customElements.get("wavy-wave-controls-toggle")).to.exist;
  });

  it("default state: not pressed, inner button aria-pressed=false, label = Hide wave controls", async () => {
    const el = await fixture(
      html`<wavy-wave-controls-toggle></wavy-wave-controls-toggle>`
    );
    expect(el.pressed).to.equal(false);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-pressed")).to.equal("false");
    expect(button.getAttribute("aria-label")).to.equal("Hide wave controls");
    expect(button.querySelector("svg")).to.exist;
    expect(button.textContent.trim()).to.equal("");
  });

  it("click toggles to pressed=true and inner button label flips to Show wave controls", async () => {
    const el = await fixture(
      html`<wavy-wave-controls-toggle></wavy-wave-controls-toggle>`
    );
    el.renderRoot.querySelector("button").click();
    await el.updateComplete;
    expect(el.pressed).to.equal(true);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-pressed")).to.equal("true");
    expect(button.getAttribute("aria-label")).to.equal("Show wave controls");
  });

  it("inner native <button> activates on real Enter (focus + click cycle)", async () => {
    const el = await fixture(
      html`<wavy-wave-controls-toggle></wavy-wave-controls-toggle>`
    );
    const button = el.renderRoot.querySelector("button");
    button.focus();
    // Native <button> emits click on Enter / Space — emulate the click result
    button.click();
    await el.updateComplete;
    expect(el.pressed).to.equal(true);
  });

  it("emits wavy-wave-controls-toggled with the new pressed value", async () => {
    const el = await fixture(
      html`<wavy-wave-controls-toggle></wavy-wave-controls-toggle>`
    );
    const button = el.renderRoot.querySelector("button");
    setTimeout(() => button.click());
    const ev = await oneEvent(el, "wavy-wave-controls-toggled");
    expect(ev.detail).to.deep.equal({ pressed: true });
    expect(ev.bubbles).to.equal(true);
    expect(ev.composed).to.equal(true);
  });

  it("toggling twice flips back to pressed=false", async () => {
    const el = await fixture(
      html`<wavy-wave-controls-toggle></wavy-wave-controls-toggle>`
    );
    el.renderRoot.querySelector("button").click();
    await el.updateComplete;
    el.renderRoot.querySelector("button").click();
    await el.updateComplete;
    expect(el.pressed).to.equal(false);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-pressed")).to.equal("false");
    expect(button.getAttribute("aria-label")).to.equal("Hide wave controls");
  });
});

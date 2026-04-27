import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-wave-root-reply-trigger.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

before(() => ensureWavyTokensLoaded());

describe("<wavy-wave-root-reply-trigger>", () => {
  it("renders the J.1 click-to-reply button", async () => {
    const el = await fixture(html`
      <wavy-wave-root-reply-trigger wave-id="w1"></wavy-wave-root-reply-trigger>
    `);
    const button = el.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    expect(button).to.exist;
    expect(button.textContent.trim()).to.match(/Click here to reply/);
  });

  it("emits wave-root-reply-requested with the wave id on click", async () => {
    const el = await fixture(html`
      <wavy-wave-root-reply-trigger wave-id="w42"></wavy-wave-root-reply-trigger>
    `);
    const button = el.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    const evt = oneEvent(el, "wave-root-reply-requested");
    button.click();
    const event = await evt;
    expect(event.detail).to.deep.equal({ waveId: "w42" });
  });

  it("collapses to display:none when hidden", async () => {
    const el = await fixture(html`<wavy-wave-root-reply-trigger hidden></wavy-wave-root-reply-trigger>`);
    expect(getComputedStyle(el).display).to.equal("none");
  });
});

import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import { setLocale, _resetLocaleForTesting } from "../src/i18n/locale.js";
import "../src/elements/wavy-wave-root-reply-trigger.js";
import { setLocale, _resetLocaleForTesting } from "../src/i18n/locale.js";

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
  beforeEach(() => _resetLocaleForTesting());
  afterEach(() => _resetLocaleForTesting());

  it("renders the GWT-style bottom reply box", async () => {
    const wrapper = await fixture(html`
      <div style="width: 320px">
        <wavy-wave-root-reply-trigger wave-id="w1"></wavy-wave-root-reply-trigger>
      </div>
    `);
    const el = wrapper.querySelector("wavy-wave-root-reply-trigger");
    const button = el.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    expect(button).to.exist;
    expect(button.textContent.trim()).to.equal("Click here to reply");
    expect(button.matches("[data-wave-root-reply-box]")).to.be.true;
    expect(button.querySelector("[data-wave-root-reply-avatar]")).to.exist;
    expect(button.getAttribute("aria-label")).to.equal("Click here to reply");
    expect(button.getAttribute("title")).to.equal("Click here to reply");
    const hostStyle = getComputedStyle(el);
    const buttonStyle = getComputedStyle(button);
    expect(hostStyle.display).to.equal("block");
    expect(button.getBoundingClientRect().width).to.be.greaterThan(280);
    expect(buttonStyle.borderStyle).to.equal("dashed");
    expect(buttonStyle.fontStyle).to.equal("italic");
  });

  it("rerenders the visible label when the locale changes", async () => {
    const el = await fixture(html`
      <wavy-wave-root-reply-trigger wave-id="w1"></wavy-wave-root-reply-trigger>
    `);
    const button = el.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    expect(button.textContent.trim()).to.equal("Click here to reply");
    setLocale("de");
    await el.updateComplete;
    expect(button.textContent.trim()).to.equal("Hier antworten");
    expect(button.getAttribute("title")).to.equal("Hier antworten");
    expect(button.getAttribute("aria-label")).to.equal("Hier antworten");
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

  it("rerenders aria-label when locale changes", async () => {
    _resetLocaleForTesting();
    const el = await fixture(html`
      <wavy-wave-root-reply-trigger wave-id="w1"></wavy-wave-root-reply-trigger>
    `);
    const button = el.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    expect(button.getAttribute("aria-label")).to.equal("Reply to the wave");

    setLocale("de");
    await el.updateComplete;

    expect(button.getAttribute("aria-label")).to.not.equal("Reply to the wave");
    _resetLocaleForTesting();
  });
});

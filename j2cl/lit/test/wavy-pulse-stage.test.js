import { fixture, expect, html } from "@open-wc/testing";
import "../src/design/wavy-pulse-stage.js";
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

describe("<wavy-pulse-stage>", () => {
  it("firePulse() sets live-pulse on the resolved target", async () => {
    const stage = await fixture(html`
      <wavy-pulse-stage>
        <wavy-blip-card></wavy-blip-card>
      </wavy-pulse-stage>
    `);
    const card = stage.querySelector("wavy-blip-card");
    expect(card.hasAttribute("live-pulse")).to.equal(false);
    stage.firePulse();
    expect(card.hasAttribute("live-pulse")).to.equal(true);
  });

  it("firePulse() removes the attribute after the resolved duration + 50ms", async () => {
    const stage = await fixture(html`
      <wavy-pulse-stage>
        <wavy-blip-card></wavy-blip-card>
      </wavy-pulse-stage>
    `);
    const card = stage.querySelector("wavy-blip-card");
    stage.firePulse();
    expect(card.hasAttribute("live-pulse")).to.equal(true);
    // Resolve --wavy-motion-pulse-duration at test time so the budget
    // adapts to prefers-reduced-motion (where it collapses to ~0ms).
    const cs = getComputedStyle(document.documentElement);
    const raw = cs.getPropertyValue("--wavy-motion-pulse-duration").trim();
    const ms = raw.endsWith("ms")
      ? parseFloat(raw)
      : raw.endsWith("s")
        ? parseFloat(raw) * 1000
        : 600;
    await new Promise((r) => setTimeout(r, Math.max(ms, 1) + 100));
    expect(card.hasAttribute("live-pulse")).to.equal(false);
  });

  it("targetSelector defaults to wavy-blip-card", async () => {
    const stage = await fixture(html`<wavy-pulse-stage></wavy-pulse-stage>`);
    expect(stage.targetSelector).to.equal("wavy-blip-card");
  });
});

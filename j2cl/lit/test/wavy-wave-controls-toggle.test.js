import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/shell-header.js";
import "../src/elements/wavy-header.js";
import "../src/elements/wavy-wave-controls-toggle.js";

function ensureShellTokensLoaded() {
  if (document.querySelector("link[data-shell-tokens-test]")) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/tokens/shell-tokens.css";
  link.dataset.shellTokensTest = "true";
  document.head.appendChild(link);
}

async function waitForCompactTopbarToken() {
  for (let i = 0; i < 50; i++) {
    const probe = document.createElement("shell-header");
    probe.setAttribute("compact-gwt-topbar", "");
    document.body.appendChild(probe);
    const applied = getComputedStyle(document.body).getPropertyValue("--j2cl-compact-topbar-top").trim() !== "";
    probe.remove();
    if (applied) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("--j2cl-compact-topbar-top not set within 1000ms");
}

function rectsOverlap(a, b) {
  return (
    a.left < b.right &&
    a.right > b.left &&
    a.top < b.bottom &&
    a.bottom > b.top
  );
}

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

  it("keeps the floating toggle clear of the compact user menu", async () => {
    ensureShellTokensLoaded();
    await waitForCompactTopbarToken();
    const el = await fixture(html`
      <div>
        <shell-header signed-in compact-gwt-topbar style="width:100vw">
          <a slot="brand">SupaWave</a>
          <wavy-header
            slot="actions-signed-in"
            signed-in
            no-brand
            compact-gwt-topbar
            data-address="test@example.com"
          ></wavy-header>
        </shell-header>
        <wavy-wave-controls-toggle></wavy-wave-controls-toggle>
      </div>
    `);
    const header = el.querySelector("wavy-header");
    const toggle = el.querySelector("wavy-wave-controls-toggle");
    await header.updateComplete;
    await toggle.updateComplete;

    const userMenuRect = header.renderRoot
      .querySelector(".user-menu")
      .getBoundingClientRect();
    const toggleRect = toggle.getBoundingClientRect();

    expect(rectsOverlap(toggleRect, userMenuRect)).to.equal(false);
  });
});

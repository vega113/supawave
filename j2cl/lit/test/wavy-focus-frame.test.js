import { fixture, expect, html, aTimeout } from "@open-wc/testing";
import "../src/elements/wavy-focus-frame.js";

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

describe("<wavy-focus-frame>", () => {
  it("registers the F-2 slice 2 custom element", () => {
    expect(customElements.get("wavy-focus-frame")).to.exist;
  });

  it("starts hidden when no focus is set", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true" style="position: relative; width: 400px; height: 300px;">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    await frame.updateComplete;
    const innerFrame = frame.renderRoot.querySelector(".frame");
    expect(innerFrame).to.exist;
    expect(innerFrame.hasAttribute("hidden")).to.be.true;
  });

  it("updates focusedBlipId + bounds on wavy-focus-changed event from parent", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true" style="position: relative; width: 400px; height: 300px;">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    await frame.updateComplete;

    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        bubbles: false,
        composed: true,
        detail: {
          blipId: "b+1",
          bounds: { top: 24, left: 16, width: 320, height: 64 },
          key: "ArrowDown"
        }
      })
    );
    await frame.updateComplete;

    expect(frame.focusedBlipId).to.equal("b+1");
    expect(frame.bounds).to.deep.equal({
      top: 24,
      left: 16,
      width: 320,
      height: 64
    });
    const innerFrame = frame.renderRoot.querySelector(".frame");
    expect(innerFrame.hasAttribute("hidden")).to.be.false;
    expect(innerFrame.getAttribute("data-focused-blip-id")).to.equal("b+1");
    expect(innerFrame.getAttribute("style")).to.contain("top: 24px");
    expect(innerFrame.getAttribute("style")).to.contain("left: 16px");
    expect(innerFrame.getAttribute("style")).to.contain("width: 320px");
    expect(innerFrame.getAttribute("style")).to.contain("height: 64px");
  });

  it("renders the GWT-light cyan focus ring via --wavy-focus-ring token (computed RGB)", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true" style="position: relative; width: 400px; height: 300px;">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        bubbles: false,
        composed: true,
        detail: {
          blipId: "b+1",
          bounds: { top: 0, left: 0, width: 100, height: 100 },
          key: ""
        }
      })
    );
    await frame.updateComplete;
    const innerFrame = frame.renderRoot.querySelector(".frame");
    const computed = getComputedStyle(innerFrame);
    // The token resolves to `0 0 0 2px rgba(0, 119, 182, 0.16)`. We must
    // assert the resolved RGB string, NOT the var() literal.
    expect(computed.boxShadow).to.contain("rgba(0, 119, 182, 0.16)");
    expect(computed.boxShadow).to.contain("2px");
  });

  it("re-hides when focusedBlipId resets to empty", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true" style="position: relative;">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        detail: { blipId: "b+1", bounds: { top: 0, left: 0, width: 10, height: 10 } }
      })
    );
    await frame.updateComplete;
    expect(frame.renderRoot.querySelector(".frame").hasAttribute("hidden")).to.be
      .false;

    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        detail: { blipId: "", bounds: { top: 0, left: 0, width: 0, height: 0 } }
      })
    );
    await frame.updateComplete;
    expect(frame.renderRoot.querySelector(".frame").hasAttribute("hidden")).to.be
      .true;
  });

  it("disconnects the listener cleanly when removed from DOM", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    await frame.updateComplete;
    const before = frame.focusedBlipId;
    host.removeChild(frame);

    // After removal, dispatching on the old parent must NOT mutate the
    // detached element (the listener was removed in disconnectedCallback).
    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        detail: { blipId: "b+99", bounds: { top: 1, left: 2, width: 3, height: 4 } }
      })
    );
    await aTimeout(20);
    expect(frame.focusedBlipId).to.equal(before);
  });

  it("survives parent re-render — same element identity preserved across focus updates", async () => {
    const host = await fixture(
      html`<div data-j2cl-read-surface="true" style="position: relative;">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        detail: { blipId: "b+1", bounds: { top: 10, left: 10, width: 50, height: 20 } }
      })
    );
    await frame.updateComplete;

    // Simulate a window swap: the renderer dispatches a fresh focus event
    // for the same blip after rebuilding the surface.
    host.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        detail: { blipId: "b+1", bounds: { top: 60, left: 10, width: 50, height: 20 } }
      })
    );
    await frame.updateComplete;

    // Same element identity — Lit element was not destroyed/recreated.
    expect(host.querySelector("wavy-focus-frame")).to.equal(frame);
    expect(frame.bounds.top).to.equal(60);
  });

  it("applies prefers-reduced-motion override on the inner frame", async () => {
    // We can't reliably toggle prefers-reduced-motion in the test env,
    // but we assert the CSS rule exists in the stylesheet so the
    // implementation is wired.
    const host = await fixture(
      html`<div data-j2cl-read-surface="true">
        <wavy-focus-frame></wavy-focus-frame>
      </div>`
    );
    const frame = host.querySelector("wavy-focus-frame");
    const styles = frame.constructor.styles;
    const cssText = Array.isArray(styles)
      ? styles.map((s) => s.cssText || "").join("\n")
      : (styles && styles.cssText) || "";
    expect(cssText).to.match(/prefers-reduced-motion:\s*reduce/);
    expect(cssText).to.contain("transition: none");
  });
});

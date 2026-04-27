/*
 * F-3.S1 (#1038, R-5.1) integration test: the inline `<wavy-composer>`
 * is mounted attached to the originating <wave-blip> when the blip's
 * toolbar emits `wave-blip-reply-requested` / `wave-blip-edit-requested`.
 *
 * The Java view layer (J2clComposeSurfaceView.java) is what listens for
 * these events in production. This test simulates the same handler
 * shape so we have a JS-land regression lock on the contract; the Java
 * harness in the worktree-local boot path exercises the same code path
 * via Elemental2.
 */
import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wave-blip.js";
import "../src/elements/wave-blip-toolbar.js";
import "../src/elements/wavy-composer.js";
import "../src/elements/wavy-wave-root-reply-trigger.js";

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
}

before(async () => {
  ensureWavyTokensLoaded();
  await waitForStylesheet();
});

/**
 * Mirror of J2clComposeSurfaceView.openInlineComposer — this is the
 * JS-land equivalent of the Java code path. Mounts a <wavy-composer>
 * inside the originating <wave-blip>.
 */
function attachInlineComposerHandler() {
  const composers = new Map();
  const handler = (event) => {
    const mode = event.type === "wave-blip-edit-requested" ? "edit" : "reply";
    const blipId = event.detail.blipId;
    if (composers.has(blipId)) return;
    const composer = document.createElement("wavy-composer");
    composer.setAttribute("reply-target-blip-id", blipId);
    composer.setAttribute("mode", mode);
    composer.setAttribute("data-inline-composer", "true");
    composer.available = true;
    const blip = document.querySelector(`wave-blip[data-blip-id="${blipId}"]`);
    if (blip) blip.appendChild(composer);
    composers.set(blipId, composer);
  };
  const cancelHandler = (event) => {
    const blipId = event.detail.replyTargetBlipId;
    const composer = composers.get(blipId);
    if (composer && composer.parentNode) {
      composer.parentNode.removeChild(composer);
    }
    composers.delete(blipId);
  };
  document.body.addEventListener("wave-blip-reply-requested", handler);
  document.body.addEventListener("wave-blip-edit-requested", handler);
  document.body.addEventListener("wavy-composer-cancelled", cancelHandler);
  return {
    teardown() {
      document.body.removeEventListener("wave-blip-reply-requested", handler);
      document.body.removeEventListener("wave-blip-edit-requested", handler);
      document.body.removeEventListener("wavy-composer-cancelled", cancelHandler);
      for (const composer of composers.values()) {
        if (composer.parentNode) composer.parentNode.removeChild(composer);
      }
    },
    getComposer(blipId) {
      return composers.get(blipId);
    }
  };
}

describe("F-3.S1 inline composer mount integration", () => {
  it("mounts a wavy-composer inside the wave-blip on Reply (R-5.1 step 1)", async () => {
    const blip = await fixture(html`
      <wave-blip data-blip-id="b42" data-wave-id="w1" author-name="Yuri" posted-at="2m">
      </wave-blip>
    `);
    const ctx = attachInlineComposerHandler();
    try {
      // Simulate F-2's <wave-blip-toolbar> Reply emit.
      blip.dispatchEvent(
        new CustomEvent("wave-blip-reply-requested", {
          detail: { blipId: "b42", waveId: "w1" },
          bubbles: true,
          composed: true
        })
      );
      // The handler is async (Lit requestUpdate); wait one frame.
      await new Promise((r) => requestAnimationFrame(r));
      const composer = blip.querySelector('wavy-composer[data-inline-composer="true"]');
      expect(composer).to.exist;
      expect(composer.getAttribute("reply-target-blip-id")).to.equal("b42");
      expect(composer.getAttribute("mode")).to.equal("reply");
      expect(composer.available).to.equal(true);
    } finally {
      ctx.teardown();
    }
  });

  it("mounts a wavy-composer in edit mode on Edit (R-5.1 step 1)", async () => {
    const blip = await fixture(html`
      <wave-blip data-blip-id="b1" data-wave-id="w1" author-name="A" posted-at="1m">
      </wave-blip>
    `);
    const ctx = attachInlineComposerHandler();
    try {
      blip.dispatchEvent(
        new CustomEvent("wave-blip-edit-requested", {
          detail: { blipId: "b1", waveId: "w1" },
          bubbles: true,
          composed: true
        })
      );
      await new Promise((r) => requestAnimationFrame(r));
      const composer = blip.querySelector("wavy-composer");
      expect(composer.getAttribute("mode")).to.equal("edit");
    } finally {
      ctx.teardown();
    }
  });

  it("removes the composer on wavy-composer-cancelled (R-5.1 step 7)", async () => {
    const blip = await fixture(html`
      <wave-blip data-blip-id="b9" data-wave-id="w1" author-name="A" posted-at="1m">
      </wave-blip>
    `);
    const ctx = attachInlineComposerHandler();
    try {
      blip.dispatchEvent(
        new CustomEvent("wave-blip-reply-requested", {
          detail: { blipId: "b9", waveId: "w1" },
          bubbles: true,
          composed: true
        })
      );
      await new Promise((r) => requestAnimationFrame(r));
      const composer = blip.querySelector("wavy-composer");
      expect(composer).to.exist;
      composer.dispatchEvent(
        new CustomEvent("wavy-composer-cancelled", {
          detail: { replyTargetBlipId: "b9", hadContent: false },
          bubbles: true,
          composed: true
        })
      );
      await new Promise((r) => requestAnimationFrame(r));
      expect(blip.querySelector("wavy-composer")).to.equal(null);
    } finally {
      ctx.teardown();
    }
  });

  it("does NOT mount any wavy-composer when the user has not opened Reply/Edit (preserves F-2.S6 gating)", async () => {
    await fixture(html`
      <wave-blip data-blip-id="b1" data-wave-id="w1" author-name="A" posted-at="1m">
      </wave-blip>
    `);
    expect(document.querySelectorAll('wavy-composer[data-inline-composer="true"]').length).to.equal(0);
    expect(document.querySelectorAll('wavy-format-toolbar').length).to.equal(0);
  });

  it("renders the J.1 wave-root reply trigger separately from blip composers", async () => {
    const trigger = await fixture(html`
      <wavy-wave-root-reply-trigger wave-id="w1"></wavy-wave-root-reply-trigger>
    `);
    const button = trigger.renderRoot.querySelector("[data-wave-root-reply-trigger]");
    expect(button).to.exist;
    const evt = oneEvent(trigger, "wave-root-reply-requested");
    button.click();
    const event = await evt;
    expect(event.detail.waveId).to.equal("w1");
  });
});

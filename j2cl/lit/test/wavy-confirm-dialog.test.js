import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-confirm-dialog.js";
import { ensureWavyConfirmDialogMounted } from "../src/elements/wavy-confirm-dialog.js";

function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

before(async () => {
  ensureWavyTokensLoaded();
});

afterEach(() => {
  // Clean up any body-level dialogs left behind by ensureMounted tests.
  const lingering = document.body.querySelectorAll("wavy-confirm-dialog");
  lingering.forEach((node) => node.remove());
});

describe("<wavy-confirm-dialog>", () => {
  it("registers the custom element", () => {
    expect(customElements.get("wavy-confirm-dialog")).to.exist;
  });

  it("opens on wavy-confirm-requested with the supplied message", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        bubbles: true,
        composed: true,
        detail: {
          requestId: "q1",
          message: "Delete this blip?",
          confirmLabel: "Delete",
          cancelLabel: "Cancel",
          tone: "destructive"
        }
      })
    );
    await el.updateComplete;
    expect(el.open).to.equal(true);
    const title = el.renderRoot.querySelector("h2.title");
    expect(title.textContent.trim()).to.equal("Delete this blip?");
    const confirmBtn = el.renderRoot.querySelector(
      'button[data-confirm-action="confirm"]'
    );
    expect(confirmBtn.textContent.trim()).to.equal("Delete");
    expect(confirmBtn.getAttribute("data-confirm-tone")).to.equal("destructive");
    el.remove();
  });

  it("emits wavy-confirm-resolved with confirmed=true on confirm click", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "q2", message: "Proceed?" }
      })
    );
    await el.updateComplete;
    const eventPromise = oneEvent(document.body, "wavy-confirm-resolved");
    el.renderRoot
      .querySelector('button[data-confirm-action="confirm"]')
      .click();
    const ev = await eventPromise;
    expect(ev.detail.requestId).to.equal("q2");
    expect(ev.detail.confirmed).to.equal(true);
    expect(el.open).to.equal(false);
    el.remove();
  });

  it("emits wavy-confirm-resolved with confirmed=false on cancel click", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "q3" }
      })
    );
    await el.updateComplete;
    const eventPromise = oneEvent(document.body, "wavy-confirm-resolved");
    el.renderRoot
      .querySelector('button[data-confirm-action="cancel"]')
      .click();
    const ev = await eventPromise;
    expect(ev.detail.confirmed).to.equal(false);
    el.remove();
  });

  it("Enter key does NOT confirm when cancel button has focus", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "q-enter-cancel", message: "Delete?" }
      })
    );
    await el.updateComplete;
    // Cancel button receives default focus on open; make it explicit.
    const cancelBtn = el.renderRoot.querySelector('button[data-confirm-action="cancel"]');
    cancelBtn.focus();
    // Fire Enter — should NOT trigger a confirmed=true resolution.
    let resolved = false;
    const listener = () => { resolved = true; };
    document.body.addEventListener("wavy-confirm-resolved", listener);
    cancelBtn.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true }));
    await el.updateComplete;
    document.body.removeEventListener("wavy-confirm-resolved", listener);
    expect(resolved).to.equal(false);
    expect(el.open).to.equal(true);
    el.remove();
  });

  it("Enter key confirms when confirm button has focus", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "q-enter-confirm", message: "Delete?" }
      })
    );
    await el.updateComplete;
    const confirmBtn = el.renderRoot.querySelector('button[data-confirm-action="confirm"]');
    confirmBtn.focus();
    // Dispatch keydown from the button so it bubbles to document with the
    // correct shadow active element set; composed:true crosses shadow boundaries.
    const eventPromise = oneEvent(document.body, "wavy-confirm-resolved");
    confirmBtn.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true })
    );
    const ev = await eventPromise;
    expect(ev.detail.requestId).to.equal("q-enter-confirm");
    expect(ev.detail.confirmed).to.equal(true);
    el.remove();
  });

  // F-3.S4 (#1038): a second wavy-confirm-requested while a prior
  // request is still pending must resolve the first request with
  // confirmed=false rather than silently dropping it.
  it("resolves a superseded request with confirmed=false before opening the next one", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        bubbles: true,
        composed: true,
        detail: { requestId: "first", message: "Delete A?" }
      })
    );
    await el.updateComplete;
    expect(el.open).to.equal(true);

    const resolved = [];
    const listener = (e) => resolved.push(e.detail);
    document.body.addEventListener("wavy-confirm-resolved", listener);

    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        bubbles: true,
        composed: true,
        detail: { requestId: "second", message: "Delete B?" }
      })
    );
    await el.updateComplete;
    document.body.removeEventListener("wavy-confirm-resolved", listener);

    // The first request must have been resolved with confirmed=false
    // before the second took over.
    expect(resolved.length).to.be.greaterThan(0);
    expect(resolved[0].requestId).to.equal("first");
    expect(resolved[0].confirmed).to.equal(false);

    // The dialog is now showing the second request.
    expect(el.open).to.equal(true);
    expect(el.requestId).to.equal("second");
    el.remove();
  });

  it("ensureWavyConfirmDialogMounted is idempotent", () => {
    const a = ensureWavyConfirmDialogMounted();
    const b = ensureWavyConfirmDialogMounted();
    expect(a).to.equal(b);
    expect(document.body.querySelectorAll("wavy-confirm-dialog").length).to.equal(1);
  });

  // review-1077 Bug 7: when a second wavy-confirm-requested arrives
  // while a prior request is still open, the prior request must
  // resolve as cancelled (confirmed=false) so its caller's promise
  // settles, instead of being silently overwritten.
  it("resolves a superseded request as cancelled before opening the next", async () => {
    const el = await fixture(html`<wavy-confirm-dialog></wavy-confirm-dialog>`);
    document.body.appendChild(el);
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "first", message: "First?" }
      })
    );
    await el.updateComplete;
    expect(el.requestId).to.equal("first");
    const eventPromise = oneEvent(document.body, "wavy-confirm-resolved");
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        detail: { requestId: "second", message: "Second?" }
      })
    );
    const supersededEv = await eventPromise;
    expect(supersededEv.detail.requestId).to.equal("first");
    expect(supersededEv.detail.confirmed).to.equal(false);
    await el.updateComplete;
    expect(el.requestId).to.equal("second");
    expect(el.open).to.equal(true);
    el.remove();
  });
});

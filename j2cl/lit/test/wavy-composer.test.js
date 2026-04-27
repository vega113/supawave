import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-composer.js";

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

function getBody(el) {
  return el.renderRoot.querySelector("[data-composer-body]");
}

function placeCaret(node, offset = 0) {
  const range = document.createRange();
  if (node.firstChild && node.firstChild.nodeType === 3) {
    range.setStart(node.firstChild, offset);
    range.setEnd(node.firstChild, offset);
  } else {
    range.selectNodeContents(node);
    range.collapse(false);
  }
  const selection = document.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
}

describe("<wavy-composer>", () => {
  it("collapses to display:none when not available (preserves F-2.S6 gating)", async () => {
    const el = await fixture(html`<wavy-composer></wavy-composer>`);
    expect(getComputedStyle(el).display).to.equal("none");
  });

  it("renders the contenteditable body when available", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = getBody(el);
    expect(body).to.exist;
    expect(body.getAttribute("contenteditable")).to.equal("true");
    expect(body.getAttribute("role")).to.equal("textbox");
    expect(body.getAttribute("aria-multiline")).to.equal("true");
  });

  it("reflects reply-target-blip-id as a data attribute on the inner wavy-compose-card", async () => {
    const el = await fixture(html`
      <wavy-composer available reply-target-blip-id="b42" target-label="Yuri"></wavy-composer>
    `);
    const card = el.renderRoot.querySelector("wavy-compose-card");
    expect(card).to.exist;
    expect(card.dataset.replyTargetBlipId).to.equal("b42");
  });

  it("renders the Replying-to chip when reply-target-blip-id is set", async () => {
    const el = await fixture(html`
      <wavy-composer available reply-target-blip-id="b42" target-label="Yuri"></wavy-composer>
    `);
    const chip = el.renderRoot.querySelector('[data-reply-chip="true"]');
    expect(chip).to.exist;
    expect(chip.textContent.trim()).to.match(/Replying to/);
    expect(chip.textContent).to.include("Yuri");
  });

  it("emits wavy-composer-cancelled when the chip × close is clicked", async () => {
    const el = await fixture(html`
      <wavy-composer available reply-target-blip-id="b42" target-label="Yuri"></wavy-composer>
    `);
    const close = el.renderRoot.querySelector('[data-reply-chip-close="true"]');
    const cancelEvent = oneEvent(el, "wavy-composer-cancelled");
    close.click();
    const event = await cancelEvent;
    expect(event.detail.replyTargetBlipId).to.equal("b42");
    expect(event.detail.hadContent).to.equal(false);
  });

  it("emits draft-change with the body text content on input", async () => {
    const el = await fixture(html`<wavy-composer available reply-target-blip-id="b42"></wavy-composer>`);
    const body = getBody(el);
    body.textContent = "hello";
    const draftEvent = oneEvent(el, "draft-change");
    body.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
    const event = await draftEvent;
    expect(event.detail.value).to.equal("hello");
    expect(event.detail.replyTargetBlipId).to.equal("b42");
  });

  it("emits reply-submit on Shift+Enter", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi"></wavy-composer>`);
    const body = getBody(el);
    const submit = oneEvent(el, "reply-submit");
    const event = new KeyboardEvent("keydown", {
      key: "Enter",
      shiftKey: true,
      bubbles: true,
      composed: true,
      cancelable: true
    });
    body.dispatchEvent(event);
    const submitted = await submit;
    expect(submitted.detail.value).to.equal("hi");
    expect(event.defaultPrevented).to.equal(true);
  });

  it("does NOT emit reply-submit on plain Enter", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi"></wavy-composer>`);
    const body = getBody(el);
    let submits = 0;
    el.addEventListener("reply-submit", () => submits++);
    body.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true, cancelable: true })
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(submits).to.equal(0);
  });

  it("emits wavy-composer-cancelled on Esc with hadContent flag", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi"></wavy-composer>`);
    const body = getBody(el);
    const cancel = oneEvent(el, "wavy-composer-cancelled");
    body.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true, composed: true, cancelable: true })
    );
    const event = await cancel;
    expect(event.detail.hadContent).to.equal(true);
  });

  it("does not submit when staleBasis is set", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi" stale-basis></wavy-composer>`);
    const body = getBody(el);
    let submits = 0;
    el.addEventListener("reply-submit", () => submits++);
    body.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Enter",
        shiftKey: true,
        bubbles: true,
        composed: true,
        cancelable: true
      })
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(submits).to.equal(0);
  });

  it("preserves caret position across host attribute updates (R-5.1 step 2)", async () => {
    const el = await fixture(html`<wavy-composer available draft="hello world"></wavy-composer>`);
    const body = getBody(el);
    document.body.appendChild(el); // ensure attached so focus works
    body.focus();
    placeCaret(body, 5);
    const beforeAnchor = document.getSelection().anchorOffset;

    // Mutate host properties — Lit re-renders the wrapper, but the body
    // element is the same node and selection is preserved.
    el.status = "Saving…";
    el.targetLabel = "Updated";
    el.activeCommand = "bold";
    await el.updateComplete;

    const afterAnchor = document.getSelection().anchorOffset;
    expect(getBody(el)).to.equal(body); // same node identity
    expect(afterAnchor).to.equal(beforeAnchor);
    el.remove();
  });

  it("emits attachment-paste-image on pasted image clipboard items", async () => {
    const el = await fixture(html`<wavy-composer available reply-target-blip-id="b1"></wavy-composer>`);
    const body = getBody(el);
    const imageFile = new File(["png"], "paste.png", { type: "image/png" });
    let pastedFile = null;
    let pastedTarget = null;
    el.addEventListener("attachment-paste-image", (event) => {
      pastedFile = event.detail.file;
      pastedTarget = event.detail.replyTargetBlipId;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [{ type: "image/png", getAsFile: () => imageFile }]
      }
    });
    body.dispatchEvent(pasteEvent);
    expect(pasteEvent.defaultPrevented).to.equal(true);
    expect(pastedFile).to.equal(imageFile);
    expect(pastedTarget).to.equal("b1");
  });

  it("ignores non-image pasted clipboard items", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = getBody(el);
    let pasteEvents = 0;
    el.addEventListener("attachment-paste-image", () => pasteEvents++);
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: { items: [{ type: "text/plain", getAsFile: () => null }] }
    });
    body.dispatchEvent(pasteEvent);
    expect(pasteEvents).to.equal(0);
  });

  it("focuses the body when composer-focus-request is dispatched", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi"></wavy-composer>`);
    document.body.appendChild(el);
    el.dispatchEvent(new Event("composer-focus-request"));
    await new Promise((r) => requestAnimationFrame(r));
    expect(el.renderRoot.activeElement).to.equal(getBody(el));
    el.remove();
  });

  it("renders the hint strip and save indicator", async () => {
    const el = await fixture(html`<wavy-composer available submitting></wavy-composer>`);
    expect(el.renderRoot.querySelector("[data-hint-strip]")).to.exist;
    const indicator = el.renderRoot.querySelector("[data-save-indicator]");
    expect(indicator).to.exist;
    expect(indicator.textContent).to.match(/Saving/);
  });

  it("forwards the compose-extension slot to wavy-compose-card", async () => {
    const el = await fixture(html`
      <wavy-composer available>
        <span slot="compose-extension" data-test-extension>plugin</span>
      </wavy-composer>
    `);
    const card = el.renderRoot.querySelector("wavy-compose-card");
    const slot = card.renderRoot.querySelector("slot[name='compose-extension']");
    expect(slot).to.exist;
  });

  it("composerState and activeSelection setters return frozen objects", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    el.composerState = { drafts: 3 };
    el.activeSelection = { collapsed: false };
    expect(Object.isFrozen(el.composerState)).to.equal(true);
    expect(Object.isFrozen(el.activeSelection)).to.equal(true);
    expect(el.composerState.drafts).to.equal(3);
    expect(el.activeSelection.collapsed).to.equal(false);
  });

  it("propagates composerState/activeSelection to the inner wavy-compose-card", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    el.composerState = { drafts: 7 };
    el.activeSelection = { startOffset: 1 };
    await el.updateComplete;
    const card = el.renderRoot.querySelector("wavy-compose-card");
    expect(card.composerState.drafts).to.equal(7);
    expect(card.activeSelection.startOffset).to.equal(1);
  });

  it("dispatches wavy-composer-selection-change when the body owns selection", async () => {
    const el = await fixture(html`<wavy-composer available draft="hello world"></wavy-composer>`);
    document.body.appendChild(el);
    const body = getBody(el);
    body.focus();
    const selectionEvent = oneEvent(el, "wavy-composer-selection-change");
    placeCaret(body, 3);
    document.dispatchEvent(new Event("selectionchange"));
    const event = await selectionEvent;
    expect(event.detail).to.have.property("collapsed");
    expect(event.detail).to.have.property("startOffset");
    expect(event.detail).to.have.property("activeAnnotations");
    el.remove();
  });

  it("emits collapsed selection descriptor when caret leaves the body", async () => {
    const el = await fixture(html`<wavy-composer available draft="hi"></wavy-composer>`);
    document.body.appendChild(el);
    const body = getBody(el);
    body.focus();
    placeCaret(body, 1);
    document.dispatchEvent(new Event("selectionchange"));
    // Move selection to a node outside the body.
    const outside = document.createElement("span");
    outside.textContent = "x";
    document.body.appendChild(outside);
    const r = document.createRange();
    r.selectNodeContents(outside);
    document.getSelection().removeAllRanges();
    document.getSelection().addRange(r);
    const evt = oneEvent(el, "wavy-composer-selection-change");
    document.dispatchEvent(new Event("selectionchange"));
    const detail = (await evt).detail;
    // Detail is empty when selection is outside the body.
    expect(detail.boundingRect).to.equal(undefined);
    outside.remove();
    el.remove();
  });
});

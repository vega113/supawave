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

describe("<wavy-composer> R-5.3 mentions", () => {
  const sampleParticipants = [
    { address: "alice@example.com", displayName: "Alice Adams" },
    { address: "bob@example.com", displayName: "Bob Brown" },
    { address: "yuri@example.com", displayName: "Юра" },
    { address: "ilker@example.com", displayName: "İlker" }
  ];

  function setBodyText(el, text) {
    const body = getBody(el);
    body.textContent = text;
    // Place caret at end of the text node.
    const range = document.createRange();
    range.setStart(body.firstChild, text.length);
    range.setEnd(body.firstChild, text.length);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    body.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
  }

  it("opens the mention popover when @ is typed at start-of-line", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    expect(popover).to.exist;
    expect(popover.hasAttribute("open")).to.equal(true);
    expect(popover.candidates.length).to.equal(sampleParticipants.length);
    el.remove();
  });

  it("opens the popover when @ follows whitespace, but NOT inside an email", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    // Inside an email: alice@example.com — should NOT trigger.
    setBodyText(el, "Email me at alice@example");
    await el.updateComplete;
    expect(el.renderRoot.querySelector("mention-suggestion-popover")).to.equal(null);
    // Reset and try after a space.
    setBodyText(el, "Hi @");
    await el.updateComplete;
    expect(el.renderRoot.querySelector("mention-suggestion-popover")).to.exist;
    el.remove();
  });

  it("filters candidates by the typed query (substring, locale-aware)", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    expect(popover).to.exist;
    expect(popover.candidates.length).to.equal(1);
    expect(popover.candidates[0].address).to.equal("alice@example.com");
    el.remove();
  });

  it("inserts a violet mention chip with data-mention-id when a candidate is picked", async () => {
    const el = await fixture(html`
      <wavy-composer available reply-target-blip-id="b1" .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    const pickedPromise = oneEvent(el, "wavy-composer-mention-picked");
    popover.dispatchEvent(
      new CustomEvent("mention-select", {
        bubbles: true,
        composed: true,
        detail: { address: "alice@example.com", displayName: "Alice Adams" }
      })
    );
    const event = await pickedPromise;
    expect(event.detail.address).to.equal("alice@example.com");
    expect(event.detail.displayName).to.equal("Alice Adams");
    // PR #1066 review thread PRRT_kwDOBwxLXs593gTR: the picked event
    // must carry the chip's plain-text offset so the controller can
    // bind picks by position rather than first-text-occurrence.
    expect(event.detail.chipTextOffset).to.be.a("number");
    expect(event.detail.chipTextOffset).to.equal(0);
    await el.updateComplete;
    const chip = getBody(el).querySelector(".wavy-mention-chip");
    expect(chip).to.exist;
    expect(chip.getAttribute("data-mention-id")).to.equal("alice@example.com");
    expect(chip.getAttribute("contenteditable")).to.equal("false");
    expect(chip.textContent).to.equal("@Alice Adams");
    // The popover should be closed after pick.
    await el.updateComplete;
    expect(el.renderRoot.querySelector("mention-suggestion-popover")).to.equal(null);
    el.remove();
  });

  it("emits a chipTextOffset reflecting the chip position when @ is preceded by text", async () => {
    // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — when the user
    // has already typed plain text before triggering the popover,
    // chipTextOffset must point at the chip's plain-text offset (not
    // 0). This is the disambiguator the controller uses to bind
    // duplicate-display-name picks to the right occurrence.
    const el = await fixture(html`
      <wavy-composer available reply-target-blip-id="b1" .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "Hi @al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    const pickedPromise = oneEvent(el, "wavy-composer-mention-picked");
    popover.dispatchEvent(
      new CustomEvent("mention-select", {
        bubbles: true,
        composed: true,
        detail: { address: "alice@example.com", displayName: "Alice Adams" }
      })
    );
    const event = await pickedPromise;
    expect(event.detail.chipTextOffset).to.equal(3);
    el.remove();
  });

  it("preserves the literal @query text on Esc dismissal", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    expect(popover).to.exist;
    const abandonedPromise = oneEvent(el, "wavy-composer-mention-abandoned");
    popover.dispatchEvent(
      new CustomEvent("overlay-close", {
        bubbles: true,
        composed: true,
        detail: { reason: "escape" }
      })
    );
    await abandonedPromise;
    await el.updateComplete;
    expect(el.renderRoot.querySelector("mention-suggestion-popover")).to.equal(null);
    expect(getBody(el).textContent).to.equal("@al");
    el.remove();
  });

  it("locale-aware filter matches Cyrillic 'юр' against 'Юра' under lang=ru", async () => {
    const previousLang = document.documentElement.lang;
    document.documentElement.lang = "ru";
    try {
      const el = await fixture(html`
        <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
      `);
      document.body.appendChild(el);
      setBodyText(el, "@юр");
      await el.updateComplete;
      const popover = el.renderRoot.querySelector("mention-suggestion-popover");
      expect(popover).to.exist;
      const addrs = popover.candidates.map((c) => c.address);
      expect(addrs).to.include("yuri@example.com");
      el.remove();
    } finally {
      document.documentElement.lang = previousLang;
    }
  });

  it("locale-aware filter matches Turkish 'ilk' against 'İlker' under lang=tr", async () => {
    const previousLang = document.documentElement.lang;
    document.documentElement.lang = "tr";
    try {
      const el = await fixture(html`
        <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
      `);
      document.body.appendChild(el);
      setBodyText(el, "@ilk");
      await el.updateComplete;
      const popover = el.renderRoot.querySelector("mention-suggestion-popover");
      expect(popover).to.exist;
      const addrs = popover.candidates.map((c) => c.address);
      expect(addrs).to.include("ilker@example.com");
      el.remove();
    } finally {
      document.documentElement.lang = previousLang;
    }
  });

  it("ArrowDown/ArrowUp navigates candidates and Enter inserts the chip", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@");
    await el.updateComplete;
    const body = getBody(el);
    body.focus();
    body.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true, cancelable: true })
    );
    await el.updateComplete;
    body.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true, cancelable: true })
    );
    await el.updateComplete;
    const pickedPromise = oneEvent(el, "wavy-composer-mention-picked");
    body.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true })
    );
    const event = await pickedPromise;
    expect(event.detail.address).to.equal(sampleParticipants[2].address);
    el.remove();
  });

  it("Backspace immediately after a chip removes the chip atomically", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    popover.dispatchEvent(
      new CustomEvent("mention-select", {
        bubbles: true,
        composed: true,
        detail: { address: "alice@example.com", displayName: "Alice Adams" }
      })
    );
    await el.updateComplete;
    const body = getBody(el);
    expect(body.querySelector(".wavy-mention-chip")).to.exist;
    // Caret is now after the chip on the trailing-space text node.
    const sel = document.getSelection();
    expect(sel.rangeCount).to.be.greaterThan(0);
    const beforeKey = new KeyboardEvent("keydown", {
      key: "Backspace",
      bubbles: true,
      cancelable: true
    });
    body.dispatchEvent(beforeKey);
    await el.updateComplete;
    expect(body.querySelector(".wavy-mention-chip")).to.equal(null);
    el.remove();
  });

  it("rich serializer emits annotated component for each chip", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    popover.dispatchEvent(
      new CustomEvent("mention-select", {
        bubbles: true,
        composed: true,
        detail: { address: "alice@example.com", displayName: "Alice Adams" }
      })
    );
    await el.updateComplete;
    const components = el.serializeRichComponents();
    const annotated = components.find(
      (c) => c.type === "annotated" && c.annotationKey === "link/manual"
    );
    expect(annotated).to.exist;
    expect(annotated.annotationValue).to.equal("alice@example.com");
    expect(annotated.text).to.equal("@Alice Adams");
    el.remove();
  });

  // F-3.S2 (#1038, PR #1066 review thread PRRT_kwDOBwxLXs592RVT) —
  // controller-driven resets (draft prop transitioning to empty after
  // submit/cancel/wave change) MUST clear the body even when rich
  // content (mention chips, task lists) is present. Without this,
  // stale chips remain visible and can be re-submitted.
  it("controller-driven reset clears body even when a mention chip is present", async () => {
    const el = await fixture(html`
      <wavy-composer available .participants=${sampleParticipants}></wavy-composer>
    `);
    document.body.appendChild(el);
    setBodyText(el, "@al");
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("mention-suggestion-popover");
    popover.dispatchEvent(
      new CustomEvent("mention-select", {
        bubbles: true,
        composed: true,
        detail: { address: "alice@example.com", displayName: "Alice Adams" }
      })
    );
    await el.updateComplete;
    const body = getBody(el);
    expect(body.querySelector(".wavy-mention-chip")).to.exist;
    // Move selection out of the body so the synchronous reset path runs
    // (mirrors what the Java view does when it focuses elsewhere after
    // submit), then trigger a controller-driven reset by clearing draft.
    document.getSelection().removeAllRanges();
    document.body.focus();
    el.draft = "";
    await el.updateComplete;
    expect(body.querySelector(".wavy-mention-chip")).to.equal(null);
    expect(body.textContent).to.equal("");
    el.remove();
  });

  it("controller-driven reset clears body even when a task list is present", async () => {
    const el = await fixture(html`<wavy-composer available draft=""></wavy-composer>`);
    document.body.appendChild(el);
    const body = getBody(el);
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    range.collapse(false);
    document.getSelection().removeAllRanges();
    document.getSelection().addRange(range);
    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        bubbles: true,
        composed: true,
        detail: { actionId: "insert-task" }
      })
    );
    await el.updateComplete;
    expect(body.querySelector("ul.wavy-task-list")).to.exist;
    document.getSelection().removeAllRanges();
    document.body.focus();
    el.draft = "";
    await el.updateComplete;
    expect(body.querySelector("ul.wavy-task-list")).to.equal(null);
    expect(body.textContent).to.equal("");
    el.remove();
  });

  it("Insert-task toolbar action inserts a wavy-task-list at the caret", async () => {
    const el = await fixture(html`<wavy-composer available draft=""></wavy-composer>`);
    document.body.appendChild(el);
    const body = getBody(el);
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    range.collapse(false);
    document.getSelection().removeAllRanges();
    document.getSelection().addRange(range);
    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        bubbles: true,
        composed: true,
        detail: { actionId: "insert-task" }
      })
    );
    await el.updateComplete;
    const list = body.querySelector("ul.wavy-task-list");
    expect(list).to.exist;
    const checkbox = list.querySelector("input[type='checkbox']");
    expect(checkbox).to.exist;
    expect(checkbox.disabled).to.equal(true);
    el.remove();
  });

  // F-3.S4 (#1038, R-5.6 step 1): drag-drop end-to-end tests.
  describe("drag-and-drop attachments (F-3.S4 R-5.6 step 1)", () => {
    function makeFile(name, type = "image/png") {
      return new File(["data"], name, { type });
    }

    function makeDataTransferLike(files) {
      // Some browsers' DataTransfer constructor accepts no arguments;
      // mock the bits we touch (types + files) with a plain object.
      return {
        types: ["Files"],
        files: files,
        dropEffect: ""
      };
    }

    it("flags the body with data-droptarget on dragover", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      const dragover = new Event("dragover", { bubbles: true, cancelable: true });
      Object.defineProperty(dragover, "dataTransfer", {
        value: makeDataTransferLike([])
      });
      body.dispatchEvent(dragover);
      expect(body.getAttribute("data-droptarget")).to.equal("true");
      el.remove();
    });

    it("clears data-droptarget on dragleave when relatedTarget is outside the body", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.setAttribute("data-droptarget", "true");
      const dragleave = new Event("dragleave", { bubbles: true });
      Object.defineProperty(dragleave, "relatedTarget", {
        value: document.body
      });
      body.dispatchEvent(dragleave);
      expect(body.getAttribute("data-droptarget")).to.equal(null);
      el.remove();
    });

    it("dispatches wavy-composer-attachment-dropped with the dropped File array", async () => {
      const el = await fixture(html`
        <wavy-composer available reply-target-blip-id="b42"></wavy-composer>
      `);
      document.body.appendChild(el);
      const body = getBody(el);
      const file = makeFile("photo.png");
      const eventPromise = oneEvent(el, "wavy-composer-attachment-dropped");
      const drop = new Event("drop", { bubbles: true, cancelable: true });
      Object.defineProperty(drop, "dataTransfer", {
        value: makeDataTransferLike([file])
      });
      body.dispatchEvent(drop);
      const event = await eventPromise;
      expect(event.detail.files).to.have.lengthOf(1);
      expect(event.detail.files[0].name).to.equal("photo.png");
      expect(event.detail.replyTargetBlipId).to.equal("b42");
      // The body's drop-hint clears after the drop fires.
      expect(body.getAttribute("data-droptarget")).to.equal(null);
      el.remove();
    });

    it("ignores drops without files (e.g. drag-text from another tab)", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      let fired = false;
      el.addEventListener(
        "wavy-composer-attachment-dropped",
        () => { fired = true; }
      );
      const drop = new Event("drop", { bubbles: true, cancelable: true });
      Object.defineProperty(drop, "dataTransfer", {
        value: { types: ["text/plain"], files: [] }
      });
      body.dispatchEvent(drop);
      expect(fired).to.equal(false);
      el.remove();
    });
  });

  // F-3.S4 (#1038, R-5.7): rich-component round-trip serializer arms.
  describe("serializeRichComponents (F-3.S4 R-5.7)", () => {
    it("emits a list/unordered component per <li> inside <ul>", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML = "<ul><li>Item one</li><li>Item two</li></ul>";
      const components = el.serializeRichComponents();
      const annotated = components.filter(c => c.type === "annotated");
      expect(annotated).to.have.lengthOf(2);
      expect(annotated[0].annotationKey).to.equal("list/unordered");
      expect(annotated[0].text).to.equal("Item one");
      expect(annotated[1].annotationKey).to.equal("list/unordered");
      expect(annotated[1].text).to.equal("Item two");
      el.remove();
    });

    it("emits a list/ordered component per <li> inside <ol>", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML = "<ol><li>First</li><li>Second</li></ol>";
      const components = el.serializeRichComponents();
      const annotated = components.filter(c => c.type === "annotated");
      expect(annotated).to.have.lengthOf(2);
      expect(annotated[0].annotationKey).to.equal("list/ordered");
      expect(annotated[1].annotationKey).to.equal("list/ordered");
      el.remove();
    });

    it("emits a block/quote component for <blockquote>", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML = "<blockquote>quoted text</blockquote>";
      const components = el.serializeRichComponents();
      const annotated = components.filter(c => c.type === "annotated");
      expect(annotated).to.have.lengthOf(1);
      expect(annotated[0].annotationKey).to.equal("block/quote");
      expect(annotated[0].text).to.equal("quoted text");
      el.remove();
    });

    it("preserves mention chip inside a <ul> list item", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML =
        '<ul><li>Hello <span class="wavy-mention-chip" data-mention-id="u1">@Alice</span> world</li></ul>';
      const components = el.serializeRichComponents();
      const annotated = components.filter(c => c.type === "annotated");
      // The mention chip must survive as a link/manual component, not be
      // swallowed by the list's textContent serialization.
      const mention = annotated.find(c => c.annotationKey === "link/manual");
      expect(mention).to.exist;
      expect(mention.text).to.equal("@Alice");
      expect(mention.annotationValue).to.equal("u1");
      el.remove();
    });

    it("preserves mention chip inside a <blockquote>", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML =
        '<blockquote>See <span class="wavy-mention-chip" data-mention-id="u2">@Bob</span></blockquote>';
      const components = el.serializeRichComponents();
      const mention = components.find(
        c => c.type === "annotated" && c.annotationKey === "link/manual"
      );
      expect(mention).to.exist;
      expect(mention.annotationValue).to.equal("u2");
      el.remove();
    });

    it("emits a link/manual component carrying href for <a>", async () => {
      const el = await fixture(html`<wavy-composer available></wavy-composer>`);
      document.body.appendChild(el);
      const body = getBody(el);
      body.innerHTML = '<a href="https://example.com">Example</a>';
      const components = el.serializeRichComponents();
      const annotated = components.filter(c => c.type === "annotated");
      expect(annotated).to.have.lengthOf(1);
      expect(annotated[0].annotationKey).to.equal("link/manual");
      expect(annotated[0].annotationValue).to.equal("https://example.com");
      expect(annotated[0].text).to.equal("Example");
      el.remove();
    });
  });
});

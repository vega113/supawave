import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-composer.js";
import "../src/elements/wavy-link-modal.js";

// J-UI-5 (#1083) — selection-driven toolbar action handlers.
//
// These tests cover the wavy-composer's `_handleToolbarAction` branch
// added by J-UI-5: bold / italic / underline / strikethrough / list /
// link / unlink / clear-formatting are applied to the active range,
// and reply-submit carries a `components` array reflecting the
// per-fragment formatting.

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

function bodyOf(el) {
  return el.renderRoot.querySelector("[data-composer-body]");
}

function selectAllInBody(el) {
  const body = bodyOf(el);
  body.focus();
  const range = document.createRange();
  range.selectNodeContents(body);
  const sel = document.getSelection();
  sel.removeAllRanges();
  sel.addRange(range);
  // Force the composer to capture the live range as it would on user
  // input.
  el._onSelectionChange();
}

function dispatchToolbarAction(el, actionId) {
  el.dispatchEvent(
    new CustomEvent("wavy-format-toolbar-action", {
      detail: { actionId, selectionDescriptor: {} },
      bubbles: true,
      composed: true
    })
  );
}

describe("wavy-composer toolbar action handlers", () => {
  beforeEach(() => {
    document
      .querySelectorAll('wavy-link-modal[data-j2cl-link-modal="true"]')
      .forEach((m) => m.remove());
  });

  it("wraps the active selection in <strong> on Bold", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "hello world";
    selectAllInBody(el);

    dispatchToolbarAction(el, "bold");

    expect(bodyOf(el).querySelector("strong")).to.exist;
    expect(bodyOf(el).querySelector("strong").textContent).to.equal("hello world");
  });

  it("wraps the active selection in <em> on Italic", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "hello";
    selectAllInBody(el);

    dispatchToolbarAction(el, "italic");

    expect(bodyOf(el).querySelector("em")).to.exist;
  });

  it("wraps in <u> and a follow-up click with caret inside <u> unwraps", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<u>u-test</u>";
    // Place selection inside the <u> so the toggle-off branch finds the
    // ancestor and unwraps.
    body.focus();
    const innerRange = document.createRange();
    innerRange.selectNodeContents(body.querySelector("u"));
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(innerRange);
    el._onSelectionChange();

    dispatchToolbarAction(el, "underline");

    expect(body.querySelector("u")).to.not.exist;
    expect(body.textContent).to.equal("u-test");
  });

  it("wraps lines in <ul><li>...</li></ul> on Unordered List", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "first\nsecond";
    selectAllInBody(el);

    dispatchToolbarAction(el, "unordered-list");

    const list = bodyOf(el).querySelector("ul");
    expect(list).to.exist;
    const items = list.querySelectorAll("li");
    expect(items.length).to.equal(2);
    expect(items[0].textContent).to.equal("first");
    expect(items[1].textContent).to.equal("second");
  });

  it("wraps lines in <ol><li>...</li></ol> on Ordered List", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "alpha\nbeta\ngamma";
    selectAllInBody(el);

    dispatchToolbarAction(el, "ordered-list");

    const list = bodyOf(el).querySelector("ol");
    expect(list).to.exist;
    expect(list.querySelectorAll("li").length).to.equal(3);
  });

  it("Insert link opens the wavy-link-modal and wraps the selection on submit", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "click here";
    selectAllInBody(el);

    dispatchToolbarAction(el, "link");

    const modal = document.querySelector("wavy-link-modal[data-j2cl-link-modal=\"true\"]");
    expect(modal).to.exist;
    expect(modal.open).to.equal(true);

    modal.dispatchEvent(
      new CustomEvent("wavy-link-modal-submit", {
        detail: { url: "https://example.com", display: "click here" },
        bubbles: true,
        composed: true
      })
    );
    await el.updateComplete;

    const anchor = bodyOf(el).querySelector("a");
    expect(anchor).to.exist;
    expect(anchor.getAttribute("href")).to.equal("https://example.com");
    expect(anchor.textContent).to.equal("click here");
    expect(modal.open).to.equal(false);
  });

  it("Remove link unwraps the surrounding <a>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = '<a href="https://example.com">linked</a>';
    // Select inside the anchor.
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body.querySelector("a"));
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "unlink");

    expect(body.querySelector("a")).to.not.exist;
    expect(body.textContent).to.equal("linked");
  });

  it("Clear formatting strips inline format wraps from the selection", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<strong><em>boldy</em></strong>";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "clear-formatting");

    expect(body.querySelector("strong")).to.not.exist;
    expect(body.querySelector("em")).to.not.exist;
    expect(body.textContent).to.equal("boldy");
  });

  it("emits draft-change after a toolbar mutation", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).textContent = "abc";
    selectAllInBody(el);

    const evtPromise = oneEvent(el, "draft-change");
    dispatchToolbarAction(el, "bold");
    const evt = await evtPromise;

    expect(evt.detail.value).to.equal("abc");
  });

  it("collapsed selection does not wrap (no empty <strong> created)", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.textContent = "hello";
    body.focus();
    const range = document.createRange();
    range.setStart(body.firstChild, 1);
    range.collapse(true);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "bold");

    expect(body.querySelector("strong")).to.not.exist;
  });
});

describe("wavy-composer reply-submit components payload", () => {
  it("emits components array with annotated bold run", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "plain <strong>bold</strong> tail";
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    expect(evt.detail.components).to.be.an("array");
    const bolded = evt.detail.components.find(
      (c) => c.type === "annotated" && c.annotationKey === "fontWeight" && c.annotationValue === "bold"
    );
    expect(bolded).to.exist;
    expect(bolded.text).to.equal("bold");
    // Plain runs flank the bold run.
    const plain = evt.detail.components.filter((c) => c.type === "text");
    expect(plain.length).to.be.greaterThan(0);
  });

  it("emits link/manual annotation for an inline anchor", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = '<a href="https://example.com">linked</a>';
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const linked = evt.detail.components.find(
      (c) => c.type === "annotated" && c.annotationKey === "link/manual"
    );
    expect(linked).to.exist;
    expect(linked.annotationValue).to.equal("https://example.com");
    expect(linked.text).to.equal("linked");
  });

  it("emits italic + underline + strikethrough annotations", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<em>i</em><u>u</u><s>s</s>";
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const i = evt.detail.components.find(
      (c) => c.type === "annotated" && c.annotationKey === "fontStyle" && c.annotationValue === "italic"
    );
    const u = evt.detail.components.find(
      (c) => c.type === "annotated" && c.annotationKey === "textDecoration" && c.annotationValue === "underline"
    );
    const s = evt.detail.components.find(
      (c) => c.type === "annotated" && c.annotationKey === "textDecoration" && c.annotationValue === "line-through"
    );
    expect(i).to.exist;
    expect(u).to.exist;
    expect(s).to.exist;
  });
});

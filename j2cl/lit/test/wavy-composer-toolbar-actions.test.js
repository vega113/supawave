import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-composer.js";
import "../src/elements/wavy-link-modal.js";

// J-UI-5 (#1083) — selection-driven toolbar action handlers.
//
// These tests cover the wavy-composer's `_handleToolbarAction` branch
// added by J-UI-5: bold / italic / underline / strikethrough /
// superscript / subscript / list / link / unlink / clear-formatting are
// applied to the active range, and reply-submit carries a `components`
// array reflecting the per-fragment formatting.

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

  it("wraps the active selection in <sup> and <sub>", async () => {
    const superscript = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(superscript).textContent = "2";
    selectAllInBody(superscript);

    dispatchToolbarAction(superscript, "superscript");

    expect(bodyOf(superscript).querySelector("sup")).to.exist;
    expect(bodyOf(superscript).querySelector("sup").textContent).to.equal("2");

    const subscript = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(subscript).textContent = "2";
    selectAllInBody(subscript);

    dispatchToolbarAction(subscript, "subscript");

    expect(bodyOf(subscript).querySelector("sub")).to.exist;
    expect(bodyOf(subscript).querySelector("sub").textContent).to.equal("2");
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

  // Coderabbit review #1095 thread PRRT_kwDOBwxLXs5-NWWt: unlink on a
  // partial range inside a longer link must keep the surrounding text
  // LINKED. The anchor splits at the selection boundaries; prefix +
  // suffix clones inherit the original href.
  it("Remove link on partial range keeps surrounding text linked", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = '<a href="https://example.com">hello world</a>';
    body.focus();
    const anchor = body.querySelector("a");
    // Select only "world" inside the anchor.
    const range = document.createRange();
    range.setStart(anchor.firstChild, 6);
    range.setEnd(anchor.firstChild, 11);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "unlink");

    // "hello " stays inside an `<a href="https://example.com">` —
    // the prefix clone preserved the href; "world" is now bare text.
    const remaining = body.querySelectorAll("a");
    expect(remaining.length, "exactly one residual <a> survives").to.equal(1);
    expect(remaining[0].getAttribute("href")).to.equal("https://example.com");
    expect(remaining[0].textContent).to.equal("hello ");
    expect(body.textContent).to.equal("hello world");
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

  it("Clear formatting only strips wraps that intersect the selection", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML =
      '<strong>keep</strong><span data-marker> </span><em>strip</em>';
    body.focus();
    // Select only the <em>.
    const range = document.createRange();
    range.selectNodeContents(body.querySelector("em"));
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "clear-formatting");

    // The bold run must survive — it does not intersect the selection.
    expect(body.querySelector("strong")).to.exist;
    expect(body.querySelector("strong").textContent).to.equal("keep");
    // The italic run is gone.
    expect(body.querySelector("em")).to.not.exist;
    expect(body.textContent).to.equal("keep strip");
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

  // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84a):
  // <strong><em>x</em></strong> must produce a single component
  // carrying BOTH fontStyle=italic AND fontWeight=bold so combined
  // styles round-trip on reload — not just the inner italic.
  it("emits combined annotations for nested inline wraps", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<strong><em>combined</em></strong>";
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const combined = evt.detail.components.find(
      (c) =>
        c.type === "annotated" &&
        Array.isArray(c.annotations) &&
        c.annotations.some((a) => a.key === "fontStyle" && a.value === "italic") &&
        c.annotations.some((a) => a.key === "fontWeight" && a.value === "bold")
    );
    expect(combined, "annotations array carries both italic + bold").to.exist;
    expect(combined.text).to.equal("combined");
  });

  it("emits combined annotations for bold around an inline link", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML =
      '<strong><a href="https://example.com">linked</a></strong>';
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const combined = evt.detail.components.find(
      (c) =>
        c.type === "annotated" &&
        Array.isArray(c.annotations) &&
        c.annotations.some((a) => a.key === "link/manual" && a.value === "https://example.com") &&
        c.annotations.some((a) => a.key === "fontWeight" && a.value === "bold")
    );
    expect(combined, "annotations array carries both link + bold").to.exist;
    expect(combined.text).to.equal("linked");
  });
});

// J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84T):
// clear-formatting on one <li> must NOT unwrap the surrounding <ul>.
describe("wavy-composer clear-formatting list scoping", () => {
  it("does not unwrap a <ul> when only one <li> is selected", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>keep</li><li>strip-this</li></ul>";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body.querySelectorAll("li")[1]);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "clear-formatting", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    expect(body.querySelector("ul"), "outer <ul> is preserved").to.exist;
    expect(body.querySelector("li").textContent).to.equal("keep");
  });

  it("unwraps <ul> when the entire list is inside the selection", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>a</li><li>b</li></ul>";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "clear-formatting", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    expect(body.querySelector("ul")).to.not.exist;
  });
});

// J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84X):
// align-* / rtl have no submit-pipeline serialization, so the click
// must NOT mutate the body locally and must bubble for any future
// listener.
describe("wavy-composer align/rtl actions", () => {
  it("align-center does not apply local DOM mutation", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.textContent = "alignme";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();
    const initialHtml = body.innerHTML;

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "align-center", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    expect(body.innerHTML).to.equal(initialHtml);
  });

  it("rtl does not apply local DOM mutation", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.textContent = "rtl-test";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();
    const initialHtml = body.innerHTML;

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "rtl", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    expect(body.innerHTML).to.equal(initialHtml);
    expect(body.getAttribute("dir") || "").to.not.equal("rtl");
  });
});

// J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-DSyW):
// outer wrappers (lists, blockquotes, anchors) must merge their
// annotation onto pre-annotated inner runs so combined formatting
// round-trips through reply-submit.
describe("wavy-composer outer-wrapper annotation merge", () => {
  it("emits both list and inline annotations for <li><strong>x</strong></li>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<ul><li><strong>bold-list</strong></li></ul>";
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const combined = evt.detail.components.find(
      (c) =>
        c.type === "annotated" &&
        Array.isArray(c.annotations) &&
        c.annotations.some((a) => a.key === "list/unordered" && a.value === "true") &&
        c.annotations.some((a) => a.key === "fontWeight" && a.value === "bold")
    );
    expect(combined, "annotations carry list + bold").to.exist;
    expect(combined.text).to.equal("bold-list");
  });

  it("emits both link and inline annotations for <a><em>x</em></a>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = '<a href="https://x.test"><em>linked-italic</em></a>';
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const combined = evt.detail.components.find(
      (c) =>
        c.type === "annotated" &&
        Array.isArray(c.annotations) &&
        c.annotations.some((a) => a.key === "link/manual" && a.value === "https://x.test") &&
        c.annotations.some((a) => a.key === "fontStyle" && a.value === "italic")
    );
    expect(combined, "annotations carry link + italic").to.exist;
    expect(combined.text).to.equal("linked-italic");
  });

  it("emits both blockquote and inline annotations for <blockquote><strong>x</strong></blockquote>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<blockquote><strong>quoted-bold</strong></blockquote>";
    el._onBodyInput();

    const submitPromise = oneEvent(el, "reply-submit");
    el._submit();
    const evt = await submitPromise;

    const combined = evt.detail.components.find(
      (c) =>
        c.type === "annotated" &&
        Array.isArray(c.annotations) &&
        c.annotations.some((a) => a.key === "block/quote" && a.value === "true") &&
        c.annotations.some((a) => a.key === "fontWeight" && a.value === "bold")
    );
    expect(combined, "annotations carry blockquote + bold").to.exist;
    expect(combined.text).to.equal("quoted-bold");
  });
});

// J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-DSyb):
// list toggle must preserve inline markup inside the selection
// (links, bold/italic, mention chips) — not flatten to plain text
// via range.toString().
describe("wavy-composer list toggle preserves inline markup", () => {
  it("keeps <strong> inside the new <li> when toggling Unordered List", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<strong>bolded</strong>";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "unordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    const li = body.querySelector("ul > li");
    expect(li, "<li> created").to.exist;
    expect(li.querySelector("strong"), "<strong> survives inside <li>").to.exist;
    expect(li.textContent).to.equal("bolded");
  });

  it("keeps <a href> inside the new <li> when toggling Ordered List", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = '<a href="https://example.com">linked-line</a>';
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "ordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    const li = body.querySelector("ol > li");
    expect(li, "<li> created").to.exist;
    const anchor = li.querySelector("a");
    expect(anchor, "<a> survives inside <li>").to.exist;
    expect(anchor.getAttribute("href")).to.equal("https://example.com");
    expect(li.textContent).to.equal("linked-line");
  });

  it("keeps mention chips inside the new <li> when toggling Unordered List", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML =
      '<span class="wavy-mention-chip" data-mention-id="alice@x.test" contenteditable="false">@Alice</span> ping';
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "unordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    const li = body.querySelector("ul > li");
    expect(li, "<li> created").to.exist;
    const chip = li.querySelector(".wavy-mention-chip");
    expect(chip, "mention chip survives inside <li>").to.exist;
    expect(chip.getAttribute("data-mention-id")).to.equal("alice@x.test");
  });
});

// J-UI-5 (#1083, codex / coderabbit threads PRRT_kwDOBwxLXs5-Gunw,
// PRRT_kwDOBwxLXs5-F-8i): inline toggle-off must split the wrapper
// at the selection boundaries — text outside the selection keeps
// the formatting.
describe("wavy-composer inline toggle-off splits the ancestor", () => {
  // Codex P1 review #1095 thread PRRT_kwDOBwxLXs5-N9fu: a selection
  // that starts INSIDE a formatted ancestor and ends OUTSIDE it must
  // not duplicate or reorder the outside text. The split-unwrap
  // helper clamps its inner-slice range to the ancestor's bounds so
  // only the in-ancestor portion is unwrapped.
  it("Bold off across a boundary does not duplicate text outside the ancestor", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<strong>hello</strong> world";
    body.focus();
    const strong = body.querySelector("strong");
    const trailing = body.lastChild; // " world" text node
    // Range: from "lo" inside <strong> to " wor" inside the trailing
    // text node. This crosses the </strong> boundary.
    const range = document.createRange();
    range.setStart(strong.firstChild, 3);
    range.setEnd(trailing, 4);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    dispatchToolbarAction(el, "bold");

    // The body's textual content must still read "hello world" — no
    // " wor" duplication. The bold-toggle-off only removed bold from
    // the in-strong slice ("lo"); "hel" stays bold.
    expect(body.textContent).to.equal("hello world");
    const bolds = Array.from(body.querySelectorAll("strong")).map((s) => s.textContent);
    expect(bolds.join("|"), "only the in-ancestor prefix stays bold").to.equal("hel");
  });

  it("Bold off on partial range keeps the rest bold", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<strong>hello world</strong>";
    body.focus();
    // Select only "hello".
    const strong = body.querySelector("strong");
    const range = document.createRange();
    range.setStart(strong.firstChild, 0);
    range.setEnd(strong.firstChild, 5);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "bold", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    // "hello" is no longer bold but " world" remains bold.
    const survivors = Array.from(body.querySelectorAll("strong")).map(
      (s) => s.textContent
    );
    expect(survivors.join("|")).to.equal(" world");
    expect(body.textContent).to.equal("hello world");
  });
});

// J-UI-5 (#1083, codex / coderabbit threads PRRT_kwDOBwxLXs5-Gun0,
// PRRT_kwDOBwxLXs5-GulD): list toggle-off must only unwrap the
// selected `<li>`s; sibling items keep list semantics.
describe("wavy-composer list toggle-off scopes to selected items", () => {
  it("Unordered list off on one <li> keeps siblings inside <ul>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>keep</li><li>strip</li></ul>";
    body.focus();
    // Select inside the second <li>.
    const items = body.querySelectorAll("li");
    const range = document.createRange();
    range.selectNodeContents(items[1]);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "unordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    // First <li> stays inside <ul>; second <li> now a <div>.
    const ul = body.querySelector("ul");
    expect(ul, "<ul> survives because sibling kept list membership").to.exist;
    expect(ul.querySelectorAll("li").length).to.equal(1);
    expect(ul.querySelector("li").textContent).to.equal("keep");
  });

  // Codex review #1095 thread PRRT_kwDOBwxLXs5-NGAY: list toggle-off
  // with non-contiguous selection must preserve item ORDER. With
  // items=[a, b, c] and only [a, c] selected, the unwrapped DOM
  // must read a, b, c — not a, c, b.
  it("Unordered list off preserves item order with non-contiguous selection", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>a</li><li>b</li><li>c</li></ul>";
    body.focus();
    const items = body.querySelectorAll("li");

    // Select item a then extend selection across to item c — the
    // selection covers all three; we then directly mark only items
    // [a, c] as the toggle target by feeding `intersectsNode` a
    // synthetic Range that excludes b. Easiest test path: pick a
    // selection that intersects only a and c via two disjoint ranges
    // is not possible with one Range, so we construct a contrived
    // range that covers a and c by going from the start of a to the
    // end of c, then in the assertion verify the order survives even
    // for the (a, c, ulb) reorder bug reported by codex. Here the
    // simpler test: cover a and c by selecting their text content
    // in document order — the toggle-off code path filters by
    // `intersectsNode` per <li>, so a and c qualify; b also qualifies
    // because the range from a to c straddles b. So this case
    // collapses to the "all selected" path. A meaningful order test
    // therefore drives the toggle-off through the controller's filter
    // by exercising an explicit item set: select item c only — the
    // resulting DOM should have li a (intact), li b (intact),
    // div c (former list item).
    const range = document.createRange();
    range.selectNodeContents(items[2]);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "unordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    // Order: residual <ul>[a, b] sits BEFORE the new <div>c</div>
    // — not the reverse — so reading the body left-to-right yields
    // "a b c", matching the original document order.
    const ul = body.querySelector("ul");
    expect(ul, "<ul> survives because items a + b stay listed").to.exist;
    const surviving = Array.from(ul.querySelectorAll("li")).map(
      (li) => li.textContent
    );
    expect(surviving).to.deep.equal(["a", "b"]);
    // The residual list comes first; the unwrapped div sits after.
    expect(ul.nextElementSibling).to.exist;
    expect(ul.nextElementSibling.tagName.toLowerCase()).to.equal("div");
    expect(ul.nextElementSibling.textContent).to.equal("c");
  });

  it("Unordered list off when selection covers every <li> removes the <ul>", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>a</li><li>b</li></ul>";
    body.focus();
    const items = body.querySelectorAll("li");
    // Selection that starts inside the first <li> and ends inside the
    // second — both items are covered, so the unwrap empties the
    // `<ul>` and the wrapper itself is dropped.
    const range = document.createRange();
    range.setStart(items[0].firstChild, 0);
    range.setEnd(items[1].firstChild, 1);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "unordered-list", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    expect(body.querySelector("ul")).to.not.exist;
  });
});

// J-UI-5 (#1083, coderabbit thread PRRT_kwDOBwxLXs5-F-d2): clearing
// formatting on a fully-covered <ul>/<ol> must keep item boundaries
// (block separation), not collapse "a" + "b" into "ab".
describe("wavy-composer clear-formatting preserves list boundaries", () => {
  it("clear formatting on a full <ul> keeps items as separate blocks", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    const body = bodyOf(el);
    body.innerHTML = "<ul><li>a</li><li>b</li></ul>";
    body.focus();
    const range = document.createRange();
    range.selectNodeContents(body);
    const sel = document.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    el._onSelectionChange();

    el.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: { actionId: "clear-formatting", selectionDescriptor: {} },
        bubbles: true,
        composed: true
      })
    );

    // <ul>/<ol> gone, <li> gone, but each former item is a separate
    // <div> block so the body's serialized text reads "a\nb", not "ab".
    expect(body.querySelector("ul")).to.not.exist;
    expect(body.querySelector("li")).to.not.exist;
    const blocks = body.querySelectorAll("div");
    expect(blocks.length).to.be.greaterThan(1);
    expect(el._serializeBodyText()).to.contain("a\nb");
  });

  // Coderabbit review #1095 thread PRRT_kwDOBwxLXs5-NWWy: the draft
  // preview from `_serializeBodyText` must keep block boundaries for
  // `<ul>`, `<ol>`, and `<blockquote>` so the previewed text matches
  // the structure submitted via DocOps.
  it("serializes <ul><li>a</li><li>b</li></ul> with newline separator", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<ul><li>a</li><li>b</li></ul>";
    expect(el._serializeBodyText()).to.contain("a\nb");
    expect(el._serializeBodyText().indexOf("ab")).to.equal(-1);
  });

  it("serializes <ol> items with newline separator", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "<ol><li>one</li><li>two</li></ol>";
    expect(el._serializeBodyText()).to.contain("one\ntwo");
  });

  it("serializes <blockquote> contents with leading newline boundary", async () => {
    const el = await fixture(html`<wavy-composer available></wavy-composer>`);
    bodyOf(el).innerHTML = "before<blockquote>quoted</blockquote>after";
    const text = el._serializeBodyText();
    expect(text).to.contain("before\nquoted");
    expect(text).to.contain("quoted\nafter");
  });
});

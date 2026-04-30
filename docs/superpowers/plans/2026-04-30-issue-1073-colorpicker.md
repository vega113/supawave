# Issue #1073 Colorpicker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add J2CL/Lit text-color and highlight-color rich-edit affordances with a keyboard-accessible color picker and DocOp annotation round-trip parity with GWT.

**Architecture:** Keep the color picker Lit-owned and composer-local: `<wavy-format-toolbar>` opens `<wavy-colorpicker-popover>`, then emits `wavy-format-toolbar-action` with `actionId` plus the selected color. `<wavy-composer>` applies the chosen color to the current selection and `serializeRichComponents()` emits annotation runs. Java remains the submit/DocOp bridge and verifies that the generic rich-content delta path persists the new annotation keys.

**Tech Stack:** J2CL Java, Lit web components, web-test-runner, SBT `j2clSearchTest`, existing `J2clRichContentDeltaFactory`.

---

## Scope And Evidence

Issue #1073 asks for H.10 Text color, H.11 Highlight color, and `<wavy-colorpicker-popover>`.

GWT evidence:
- `wave/src/main/java/com/google/wave/api/Annotation.java` defines `style/color` and `style/backgroundColor`.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/color/ColorHelper.java` writes `StyleAnnotationHandler.key("color")` and `StyleAnnotationHandler.key("backgroundColor")`.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/color/SimpleColorPicker.java` exposes the 80-color Google-docs-style RGB palette.

Plan decision:
- Use `style/color` for text color.
- Use `style/backgroundColor` for highlight, not `style/highlight`. The issue text says `style/highlight`, but that is not the GWT annotation key and would break parity.
- Return/store colors as canonical `rgb(r, g, b)` strings, matching GWT `SimpleColorPicker`.

## File Map

- Create `j2cl/lit/src/format/color-options.js`: shared GWT-compatible color palette and normalizers.
- Create `j2cl/lit/src/elements/wavy-colorpicker-popover.js`: ARIA grid color picker; no composer mutation logic.
- Create `j2cl/lit/test/wavy-colorpicker-popover.test.js`: keyboard, ARIA, selection event, and invalid-value tests.
- Modify `j2cl/lit/src/elements/wavy-format-toolbar.js`: add text/highlight color buttons, open popover, emit selected value.
- Modify `j2cl/lit/test/wavy-format-toolbar.test.js`: assert action ordering and colorpicker emission.
- Modify `j2cl/lit/src/icons/toolbar-icons.js`: add `text-color` and `highlight-color` icons.
- Modify `j2cl/lit/src/elements/wavy-composer.js`: apply color/highlight wraps and serialize annotation keys.
- Modify `j2cl/lit/test/wavy-composer-toolbar-actions.test.js`: add color/highlight DOM + serializer coverage.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java`: add stable enum IDs.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java`: assert stable ID lookup only, unless the Java toolbar is explicitly wired later.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java`: prove `style/color` and `style/backgroundColor` round-trip.
- Add `wave/config/changelog.d/2026-04-30-j2cl-colorpicker.json`: user-facing changelog fragment.

## Task 1: RED Tests For Color Picker And Toolbar Surface

**Files:**
- Create: `j2cl/lit/test/wavy-colorpicker-popover.test.js`
- Modify: `j2cl/lit/test/wavy-format-toolbar.test.js`

- [ ] **Step 1: Write failing popover tests**

Create `j2cl/lit/test/wavy-colorpicker-popover.test.js` with these behaviors:

```js
import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-colorpicker-popover.js";
import { WAVY_COLOR_PALETTE } from "../src/format/color-options.js";

describe("<wavy-colorpicker-popover>", () => {
  it("renders the GWT-compatible palette as an ARIA grid", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const grid = el.renderRoot.querySelector("[role='grid']");
    const cells = el.renderRoot.querySelectorAll("[role='gridcell']");

    expect(grid).to.exist;
    expect(grid.getAttribute("aria-label")).to.equal("Text color palette");
    expect(cells.length).to.equal(WAVY_COLOR_PALETTE.length);
    expect(cells[0].getAttribute("aria-selected")).to.equal("true");
    expect(cells[0].getAttribute("data-color")).to.equal("rgb(0, 0, 0)");
  });

  it("emits the selected color and closes on click", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const selected = oneEvent(el, "wavy-colorpicker-color-selected");
    el.renderRoot.querySelector("[data-color='rgb(204, 0, 0)']").click();

    const event = await selected;
    expect(event.detail.color).to.equal("rgb(204, 0, 0)");
    expect(event.detail.mode).to.equal("text");
    expect(el.open).to.equal(false);
  });

  it("moves active cell with arrow keys and selects with Enter", async () => {
    const el = await fixture(html`<wavy-colorpicker-popover open></wavy-colorpicker-popover>`);
    const grid = el.renderRoot.querySelector("[role='grid']");
    grid.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[aria-selected='true']").getAttribute("data-color"))
      .to.equal("rgb(67, 67, 67)");

    const selected = oneEvent(el, "wavy-colorpicker-color-selected");
    grid.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    const event = await selected;
    expect(event.detail.color).to.equal("rgb(67, 67, 67)");
  });

  it("uses highlight labeling when mode is highlight", async () => {
    const el = await fixture(
      html`<wavy-colorpicker-popover open mode="highlight"></wavy-colorpicker-popover>`
    );
    expect(el.renderRoot.querySelector("[role='grid']").getAttribute("aria-label"))
      .to.equal("Highlight color palette");
  });
});
```

- [ ] **Step 2: Write failing toolbar tests**

Add to `j2cl/lit/test/wavy-format-toolbar.test.js`:

```js
it("includes text and highlight color affordances beside font controls", async () => {
  const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
  const actionIds = Array.from(el.renderRoot.querySelectorAll("[data-toolbar-action]"))
    .map((node) => node.getAttribute("data-toolbar-action"));

  expect(actionIds).to.include("text-color");
  expect(actionIds).to.include("highlight-color");
  expect(actionIds.indexOf("text-color")).to.be.greaterThan(actionIds.indexOf("font-size"));
  expect(actionIds.indexOf("highlight-color")).to.equal(actionIds.indexOf("text-color") + 1);
});

it("opens a colorpicker and emits text-color with the selected color", async () => {
  const el = await fixture(html`<wavy-format-toolbar></wavy-format-toolbar>`);
  el.selectionDescriptor = {
    collapsed: false,
    boundingRect: { top: 100, left: 100, width: 60, height: 18 },
    activeAnnotations: []
  };
  await el.updateComplete;

  el.renderRoot.querySelector('[data-toolbar-action="text-color"]').dispatchEvent(
    new CustomEvent("toolbar-action", {
      detail: { action: "text-color" },
      bubbles: true,
      composed: true
    })
  );
  await el.updateComplete;

  const picker = el.renderRoot.querySelector("wavy-colorpicker-popover");
  expect(picker).to.exist;
  expect(picker.mode).to.equal("text");

  const action = oneEvent(el, "wavy-format-toolbar-action");
  picker.dispatchEvent(
    new CustomEvent("wavy-colorpicker-color-selected", {
      detail: { color: "rgb(204, 0, 0)", mode: "text" },
      bubbles: true,
      composed: true
    })
  );
  const event = await action;
  expect(event.detail.actionId).to.equal("text-color");
  expect(event.detail.value).to.equal("rgb(204, 0, 0)");
  expect(event.detail.selectionDescriptor).to.equal(el.selectionDescriptor);
});
```

- [ ] **Step 3: Run RED checks**

Run:

```bash
cd j2cl/lit && npm test -- --files test/wavy-colorpicker-popover.test.js test/wavy-format-toolbar.test.js
```

Expected: FAIL because `wavy-colorpicker-popover.js`, `color-options.js`, and the color toolbar actions do not exist yet.

## Task 2: Implement Palette, Popover, Toolbar, And Icons

**Files:**
- Create: `j2cl/lit/src/format/color-options.js`
- Create: `j2cl/lit/src/elements/wavy-colorpicker-popover.js`
- Modify: `j2cl/lit/src/elements/wavy-format-toolbar.js`
- Modify: `j2cl/lit/src/icons/toolbar-icons.js`

- [ ] **Step 1: Add the palette module**

Create `j2cl/lit/src/format/color-options.js`:

```js
export const WAVY_COLOR_PALETTE = [
  "rgb(0, 0, 0)", "rgb(67, 67, 67)", "rgb(102, 102, 102)", "rgb(153, 153, 153)",
  "rgb(183, 183, 183)", "rgb(204, 204, 204)", "rgb(217, 217, 217)", "rgb(239, 239, 239)",
  "rgb(243, 243, 243)", "rgb(255, 255, 255)", "rgb(152, 0, 0)", "rgb(255, 0, 0)",
  "rgb(255, 153, 0)", "rgb(255, 255, 0)", "rgb(0, 255, 0)", "rgb(0, 255, 255)",
  "rgb(74, 134, 232)", "rgb(0, 0, 255)", "rgb(153, 0, 255)", "rgb(255, 0, 255)",
  "rgb(230, 184, 175)", "rgb(244, 204, 204)", "rgb(252, 229, 205)", "rgb(255, 242, 204)",
  "rgb(217, 234, 211)", "rgb(208, 224, 227)", "rgb(201, 218, 248)", "rgb(207, 226, 243)",
  "rgb(217, 210, 233)", "rgb(234, 209, 220)", "rgb(221, 126, 107)", "rgb(234, 153, 153)",
  "rgb(249, 203, 156)", "rgb(255, 229, 153)", "rgb(182, 215, 168)", "rgb(162, 196, 201)",
  "rgb(164, 194, 244)", "rgb(159, 197, 232)", "rgb(180, 167, 214)", "rgb(213, 166, 189)",
  "rgb(204, 65, 37)", "rgb(224, 102, 102)", "rgb(246, 178, 107)", "rgb(255, 217, 102)",
  "rgb(147, 196, 125)", "rgb(118, 165, 175)", "rgb(109, 158, 235)", "rgb(111, 168, 220)",
  "rgb(142, 124, 195)", "rgb(194, 123, 160)", "rgb(166, 28, 0)", "rgb(204, 0, 0)",
  "rgb(230, 145, 56)", "rgb(241, 194, 50)", "rgb(106, 168, 79)", "rgb(69, 129, 142)",
  "rgb(60, 120, 216)", "rgb(61, 133, 198)", "rgb(103, 78, 167)", "rgb(166, 77, 121)",
  "rgb(133, 32, 12)", "rgb(153, 0, 0)", "rgb(180, 95, 6)", "rgb(191, 144, 0)",
  "rgb(56, 118, 29)", "rgb(19, 79, 92)", "rgb(17, 85, 204)", "rgb(11, 83, 148)",
  "rgb(53, 28, 117)", "rgb(116, 27, 71)", "rgb(91, 15, 0)", "rgb(102, 0, 0)",
  "rgb(120, 63, 4)", "rgb(127, 96, 0)", "rgb(39, 78, 19)", "rgb(12, 52, 61)",
  "rgb(28, 69, 135)", "rgb(7, 55, 99)", "rgb(32, 18, 77)", "rgb(76, 17, 48)"
];

export function normalizePaletteColor(value) {
  const raw = String(value || "").trim();
  return WAVY_COLOR_PALETTE.includes(raw) ? raw : "";
}
```

- [ ] **Step 2: Implement `<wavy-colorpicker-popover>`**

Create a Lit element that:
- exposes `open`, `mode`, and `activeIndex` properties;
- renders nothing when `open` is false;
- renders `[role="grid"]` with 10 columns and `WAVY_COLOR_PALETTE.length` `[role="gridcell"]` buttons when open;
- handles `ArrowRight`, `ArrowLeft`, `ArrowDown`, `ArrowUp`, `Home`, `End`, `Enter`, `Space`, and `Escape`;
- emits `wavy-colorpicker-color-selected` with `{color, mode}` and closes on color selection.

Implementation skeleton:

```js
import { LitElement, css, html, nothing } from "lit";
import { WAVY_COLOR_PALETTE } from "../format/color-options.js";

const COLS = 10;

export class WavyColorpickerPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    mode: { type: String, reflect: true },
    activeIndex: { type: Number, attribute: "active-index" }
  };

  constructor() {
    super();
    this.open = false;
    this.mode = "text";
    this.activeIndex = 0;
  }

  render() {
    if (!this.open) return nothing;
    return html`<div
      class="panel"
      role="grid"
      aria-label=${this.mode === "highlight" ? "Highlight color palette" : "Text color palette"}
      tabindex="0"
      @keydown=${this._onKeyDown}
    >
      ${WAVY_COLOR_PALETTE.map((color, index) => html`<button
        type="button"
        role="gridcell"
        data-color=${color}
        aria-label=${color}
        aria-selected=${String(index === this.activeIndex)}
        style=${`background: ${color}`}
        @click=${() => this._select(index)}
      ></button>`)}
    </div>`;
  }

  _onKeyDown(event) {
    // Move activeIndex within [0, WAVY_COLOR_PALETTE.length - 1],
    // preventDefault for handled keys, select on Enter/Space, close on Escape.
  }

  _select(index) {
    const color = WAVY_COLOR_PALETTE[index] || "";
    if (!color) return;
    this.activeIndex = index;
    this.open = false;
    this.dispatchEvent(new CustomEvent("wavy-colorpicker-color-selected", {
      detail: { color, mode: this.mode === "highlight" ? "highlight" : "text" },
      bubbles: true,
      composed: true
    }));
  }
}

if (!customElements.get("wavy-colorpicker-popover")) {
  customElements.define("wavy-colorpicker-popover", WavyColorpickerPopover);
}
```

- [ ] **Step 3: Wire toolbar color actions**

Modify `wavy-format-toolbar.js`:
- import `./wavy-colorpicker-popover.js`;
- add state `_colorPickerMode`;
- add `text-color` and `highlight-color` actions immediately after `font-size`;
- intercept those actions in `_onToolbarAction` and open the popover instead of emitting a value-less action;
- handle `wavy-colorpicker-color-selected` by emitting `wavy-format-toolbar-action` with `actionId: "text-color"` or `"highlight-color"` and `value: event.detail.color`.

Key logic:

```js
_onToolbarAction(event) {
  const actionId = (event.detail && event.detail.action) || "";
  if (!actionId) return;
  event.stopPropagation();
  if (actionId === "text-color" || actionId === "highlight-color") {
    this._colorPickerMode = actionId === "highlight-color" ? "highlight" : "text";
    return;
  }
  this._emitAction(actionId);
}

_onColorSelected(event) {
  event.stopPropagation();
  const mode = event.detail?.mode === "highlight" ? "highlight" : "text";
  this.dispatchEvent(new CustomEvent("wavy-format-toolbar-action", {
    detail: {
      actionId: mode === "highlight" ? "highlight-color" : "text-color",
      value: event.detail?.color || "",
      selectionDescriptor: this.selectionDescriptor
    },
    bubbles: true,
    composed: true
  }));
  this._colorPickerMode = "";
}
```

- [ ] **Step 4: Add icons**

Add `text-color` and `highlight-color` to `ICON_TEMPLATES` in `toolbar-icons.js`, using the GWT shapes from `EditToolbar.java`: `A` with underline and highlighter/bucket.

- [ ] **Step 5: Run GREEN checks**

Run:

```bash
cd j2cl/lit && npm test -- --files test/wavy-colorpicker-popover.test.js test/wavy-format-toolbar.test.js
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add j2cl/lit/src/format/color-options.js \
  j2cl/lit/src/elements/wavy-colorpicker-popover.js \
  j2cl/lit/src/elements/wavy-format-toolbar.js \
  j2cl/lit/src/icons/toolbar-icons.js \
  j2cl/lit/test/wavy-colorpicker-popover.test.js \
  j2cl/lit/test/wavy-format-toolbar.test.js
git commit -m "feat: add j2cl colorpicker toolbar affordances"
```

## Task 3: Composer Color Application And Serialization

**Files:**
- Modify: `j2cl/lit/src/elements/wavy-composer.js`
- Modify: `j2cl/lit/test/wavy-composer-toolbar-actions.test.js`

- [ ] **Step 1: Write failing composer tests**

Add tests:

```js
it("applies text color and serializes style/color", async () => {
  const el = await fixture(html`<wavy-composer available></wavy-composer>`);
  const body = bodyOf(el);
  body.textContent = "hello";
  selectAllInBody(el);

  dispatchToolbarAction(el, "text-color", "rgb(204, 0, 0)");

  const span = body.querySelector("span");
  expect(span).to.exist;
  expect(span.style.color).to.equal("rgb(204, 0, 0)");
  const run = el.serializeRichComponents().find(
    (c) => c.type === "annotated"
      && c.annotationKey === "style/color"
      && c.annotationValue === "rgb(204, 0, 0)"
  );
  expect(run).to.exist;
  expect(run.text).to.equal("hello");
});

it("applies highlight color and serializes GWT style/backgroundColor", async () => {
  const el = await fixture(html`<wavy-composer available></wavy-composer>`);
  const body = bodyOf(el);
  body.textContent = "hello";
  selectAllInBody(el);

  dispatchToolbarAction(el, "highlight-color", "rgb(255, 242, 204)");

  const mark = body.querySelector("mark");
  expect(mark).to.exist;
  expect(mark.style.backgroundColor).to.equal("rgb(255, 242, 204)");
  const run = el.serializeRichComponents().find(
    (c) => c.type === "annotated"
      && c.annotationKey === "style/backgroundColor"
      && c.annotationValue === "rgb(255, 242, 204)"
  );
  expect(run).to.exist;
  expect(run.text).to.equal("hello");
});

it("ignores color values outside the GWT palette", async () => {
  const el = await fixture(html`<wavy-composer available></wavy-composer>`);
  const body = bodyOf(el);
  body.textContent = "hello";
  selectAllInBody(el);

  dispatchToolbarAction(el, "text-color", "url(javascript:alert(1))");

  expect(body.innerHTML).to.equal("hello");
});
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd j2cl/lit && npm test -- --files test/wavy-composer-toolbar-actions.test.js
```

Expected: FAIL because the composer does not handle `text-color` / `highlight-color`.

- [ ] **Step 3: Implement composer color wrappers**

Modify `wavy-composer.js`:
- import `normalizePaletteColor`;
- extend `_handleToolbarAction` with:

```js
if (actionId === "text-color") {
  this._applyColorSelection("text", event.detail && event.detail.value);
  event.stopPropagation();
  return;
}
if (actionId === "highlight-color") {
  this._applyColorSelection("highlight", event.detail && event.detail.value);
  event.stopPropagation();
  return;
}
```

- implement `_applyColorSelection(kind, rawValue)`, `_createColorWrapper(kind, value)`, `_findColorWrapperAncestor(container, kind)`, and `_replaceColorWrapperAtSelection(...)` by reusing the existing font wrapper pattern;
- update `_bodyHasRichContent()` selector to include `span[style*='color']` and `mark[style*='background']`;
- update `inlineFormatAnnotationForNode(node)`:

```js
if (tag === "span") {
  const color = normalizePaletteColor(node.style && node.style.color);
  if (color) return { key: "style/color", value: color };
  const size = normalizeFontSizeValue(node.style && node.style.fontSize);
  return size ? { key: "style/fontSize", value: size } : null;
}
if (tag === "mark") {
  const background = normalizePaletteColor(node.style && node.style.backgroundColor);
  return background ? { key: "style/backgroundColor", value: background } : null;
}
```

- [ ] **Step 4: Run GREEN checks**

Run:

```bash
cd j2cl/lit && npm test -- --files test/wavy-composer-toolbar-actions.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add j2cl/lit/src/elements/wavy-composer.js \
  j2cl/lit/test/wavy-composer-toolbar-actions.test.js
git commit -m "feat: apply j2cl composer color annotations"
```

## Task 4: Java Stable IDs And Delta Round-Trip Tests

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java`

- [ ] **Step 1: Add failing Java tests**

Add to `J2clToolbarSurfaceControllerTest`:

```java
@Test
public void colorActionsResolveByStableId() {
  Assert.assertEquals(
      J2clDailyToolbarAction.TEXT_COLOR,
      J2clDailyToolbarAction.fromId("text-color"));
  Assert.assertEquals(
      J2clDailyToolbarAction.HIGHLIGHT_COLOR,
      J2clDailyToolbarAction.fromId("highlight-color"));
}
```

Add to `J2clRichContentDeltaFactoryTest`:

```java
@Test
public void createReplyRequestRoundTripsTextColorAndBackgroundColorAnnotations() {
  J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
  J2clSidecarWriteSession session =
      new J2clSidecarWriteSession(
          "example.com/w+abc", "chan-4", 3L, "HASH", "b+root");
  J2clComposerDocument document =
      J2clComposerDocument.builder()
          .annotatedText("style/color", "rgb(204, 0, 0)", "Red")
          .text(" and ")
          .annotatedText("style/backgroundColor", "rgb(255, 242, 204)", "Highlight")
          .build();

  String deltaJson =
      factory.createReplyRequest("user@example.com", session, document).getDeltaJson();

  assertContains(
      deltaJson,
      "{\"1\":{\"3\":[{\"1\":\"style/color\",\"3\":\"rgb(204, 0, 0)\"}]}}",
      "\"2\":\"Red\"",
      "{\"1\":{\"2\":[\"style/color\"]}}",
      "{\"1\":{\"3\":[{\"1\":\"style/backgroundColor\",\"3\":\"rgb(255, 242, 204)\"}]}}",
      "\"2\":\"Highlight\"",
      "{\"1\":{\"2\":[\"style/backgroundColor\"]}}");
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
sbt --batch compile j2clSearchTest
```

Expected: FAIL because `TEXT_COLOR` and `HIGHLIGHT_COLOR` are absent.

- [ ] **Step 3: Add enum entries**

Modify `J2clDailyToolbarAction.java` after `FONT_SIZE`:

```java
TEXT_COLOR("text-color", "Text color", "Edit", false, true),
HIGHLIGHT_COLOR("highlight-color", "Highlight color", "Edit", false, true),
```

Do not add these to `J2clToolbarSurfaceController.addEditActions()` in this issue. The functional color picker is owned by the composer-local floating Lit toolbar, which can carry a selected color value. The Java toolbar model carries enum stability only; rendering value-less global color buttons would expose nonfunctional controls.

- [ ] **Step 4: Run GREEN checks**

Run:

```bash
sbt --batch compile j2clSearchTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceControllerTest.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java
git commit -m "feat: add j2cl color annotation round-trip coverage"
```

## Task 5: Changelog, Full Verification, Browser Sanity, PR

**Files:**
- Create: `wave/config/changelog.d/20260430-j2cl-colorpicker.yaml`
- Create or update: `journal/local-verification/2026-04-30-issue-1073-colorpicker.md`

- [ ] **Step 1: Add changelog fragment**

Create:

```yaml
type: added
area: j2cl
summary: "Add J2CL rich-text text color and highlight color picker affordances."
```

- [ ] **Step 2: Run full local verification**

Run:

```bash
cd j2cl/lit && npm test -- --files \
  test/wavy-colorpicker-popover.test.js \
  test/wavy-format-toolbar.test.js \
  test/wavy-composer-toolbar-actions.test.js
```

Expected: PASS.

Run:

```bash
cd j2cl/lit && npm run build
```

Expected: PASS.

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
sbt --batch compile j2clSearchTest
git diff --check
```

Expected: all PASS.

- [ ] **Step 3: Run browser sanity with local accounts if needed**

Use port 9902 to avoid collision with recently closed #1074:

```bash
PORT=9902 WAVE_SERVER_HOST=127.0.0.1 WAVE_SERVER_PORT=9902 bash scripts/wave-smoke.sh start
PORT=9902 bash scripts/wave-smoke.sh check
```

Browser sanity should sign in with an existing local account or create throwaway local accounts, open `/?view=j2cl-root`, open an inline reply composer, select text, choose Text color and Highlight color, submit, reload, and verify the color/highlight remains visible or at least that the reply payload carries `style/color` and `style/backgroundColor` if the read renderer does not yet paint rich styles.

- [ ] **Step 4: Record issue evidence**

Comment on #1073 and #904 with:
- worktree path;
- branch;
- plan path;
- commit SHAs;
- verification commands and results;
- browser sanity screenshot path if captured;
- note that highlight uses GWT `style/backgroundColor`, not `style/highlight`.

- [ ] **Step 5: Review and PR**

Run the required implementation review loop:

```bash
REVIEW_TASK="#1073 J2CL text/highlight color picker parity" \
REVIEW_GOAL="Verify the J2CL/Lit colorpicker, composer wrapping, and DocOp annotation round-trip match GWT color/highlight semantics." \
REVIEW_ACCEPTANCE=$'- Text color and highlight color are available from the floating J2CL toolbar\n- Color picker is keyboard accessible and uses the GWT palette\n- Composer serializes style/color and style/backgroundColor annotations\n- SBT-only Java verification passes\n- Lit tests and build pass' \
REVIEW_RUNTIME="J2CL Java + Lit + SBT + web-test-runner" \
REVIEW_RISKY="selection range mutation, nested style serialization, accessibility grid keyboard handling, GWT annotation-key parity" \
REVIEW_TEST_COMMANDS="npm targeted tests; npm run build; sbt --batch compile j2clSearchTest; changelog validate; browser sanity" \
REVIEW_TEST_RESULTS="npm targeted tests PASS; npm run build PASS; sbt --batch compile j2clSearchTest PASS; changelog validation PASS; browser sanity PASS" \
REVIEW_DIFF_SPEC="$(git merge-base origin/main HEAD)..HEAD" \
CLAUDE_REVIEW_LIMIT_FALLBACK_MODEL=off \
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

If Claude Opus is still quota-blocked, record the exact blocker in #1073 and the PR body, as happened for #1148.

Open a PR only after local verification and review handling:

```bash
git push origin codex/issue-1073-colorpicker-20260430
gh pr create --repo vega113/supawave --base main --head codex/issue-1073-colorpicker-20260430 --title "Add J2CL text and highlight color picker" --body-file <prepared-body>
```

Monitor the PR until merged; address Copilot/CodeRabbit/Codex review comments with owner replies and GraphQL unresolved-thread checks.

## Self-Review

- Spec coverage: H.10/H.11 toolbar affordances are covered by Task 1/2; `<wavy-colorpicker-popover>` and ARIA keyboard behavior are covered by Task 1/2; composer DOM wrapping is covered by Task 3; DocOp round-trip is covered by Task 4; issue evidence, browser sanity, review loop, and PR monitor are covered by Task 5.
- GWT parity correction: The issue body says `style/highlight`, but GWT source uses `style/backgroundColor`. The plan uses `style/backgroundColor` and explicitly records the correction so reviewers do not chase a non-GWT key.
- Completeness scan: no incomplete tokens remain. The external-review command uses the exact expected result string that must be replaced only if a verification command does not pass.
- Type consistency: action IDs are consistently `text-color` and `highlight-color`; annotation keys are consistently `style/color` and `style/backgroundColor`; event names are consistently `wavy-colorpicker-color-selected` and `wavy-format-toolbar-action`.
- Scope control: The plan does not implement read-surface painting for persisted color annotations unless browser sanity proves that existing rendering already preserves them. If read painting is missing, file a follow-up rather than expanding #1073 beyond toolbar/composer/DocOp round-trip.

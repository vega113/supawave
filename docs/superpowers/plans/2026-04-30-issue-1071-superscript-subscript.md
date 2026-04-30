# Superscript And Subscript Toolbar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add H.5 superscript and H.6 subscript to the J2CL rich-edit toolbar and preserve them through composer serialization and DocOp submit deltas.

**Architecture:** Treat superscript and subscript as inline rich-text annotations, matching existing bold/italic/underline/strikethrough flow. The Lit toolbar exposes stable action ids, the composer wraps and serializes `<sup>` / `<sub>` as `style/verticalAlign=super|sub`, and `J2clRichContentDeltaFactory` already emits arbitrary annotated document runs, so Java work is a focused round-trip regression.

**Tech Stack:** Lit web components and @open-wc tests under `j2cl/lit`; J2CL Java unit tests through SBT; changelog fragment JSON under `wave/config/changelog.d`.

---

## Files

- Modify: `j2cl/lit/test/wavy-format-toolbar.test.js` to lock button order and event emission.
- Modify: `j2cl/lit/src/elements/wavy-format-toolbar.js` to add two text-group actions between Strikethrough and Heading.
- Modify: `j2cl/lit/src/icons/toolbar-icons.js` to add `superscript` and `subscript` SVG glyphs so the existing all-buttons-render-icons test remains meaningful.
- Modify: `j2cl/lit/test/wavy-composer.test.js` to assert `<sup>` and `<sub>` serialization.
- Modify: `j2cl/lit/src/elements/wavy-composer.js` to wrap selections with `<sup>` / `<sub>` and serialize them as `style/verticalAlign`.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java` to assert DocOp annotation start/end for `super` and `sub`.
- Add: `wave/config/changelog.d/2026-04-30-j2cl-superscript-subscript.json`.

## Task 1: Toolbar Buttons

- [ ] **Step 1: Write failing toolbar tests**

Add assertions in `j2cl/lit/test/wavy-format-toolbar.test.js`:

```js
expect(actionIds.slice(0, 7)).to.deep.equal([
  "bold",
  "italic",
  "underline",
  "strikethrough",
  "superscript",
  "subscript",
  "heading"
]);
expect(actionIds).to.include.members(["superscript", "subscript"]);
```

Add one event test per action:

```js
for (const actionId of ["superscript", "subscript"]) {
  const button = el.renderRoot.querySelector(`[data-toolbar-action="${actionId}"]`);
  const trigger = oneEvent(el, "wavy-format-toolbar-action");
  button.dispatchEvent(new CustomEvent("toolbar-action", {
    detail: { action: actionId },
    bubbles: true,
    composed: true
  }));
  const evt = await trigger;
  expect(evt.detail.actionId).to.equal(actionId);
}
```

- [ ] **Step 2: Verify toolbar tests fail**

Run:

```sh
cd j2cl/lit && npm test -- --files test/wavy-format-toolbar.test.js
```

Expected before implementation: FAIL because `superscript` / `subscript` buttons are missing.

- [ ] **Step 3: Implement toolbar buttons and icons**

In `j2cl/lit/src/elements/wavy-format-toolbar.js`, insert the two actions immediately after `strikethrough`:

```js
{ id: "superscript", label: "Superscript", group: "text", toggle: true },
{ id: "subscript", label: "Subscript", group: "text", toggle: true },
```

Update `ACTIVE_ANNOTATION_MAP` so active selection state can light the toggles:

```js
superscript: ["sup"],
subscript: ["sub"],
```

In `j2cl/lit/src/icons/toolbar-icons.js`, add `superscript` and `subscript` entries keyed by the action ids. Keep 16x16 `currentColor` SVGs consistent with the existing toolbar icon set.

- [ ] **Step 4: Verify toolbar tests pass**

Run:

```sh
cd j2cl/lit && npm test -- --files test/wavy-format-toolbar.test.js
```

Expected after implementation: PASS.

## Task 2: Composer Serialization

- [ ] **Step 1: Write failing composer tests**

Add tests in `j2cl/lit/test/wavy-composer.test.js` under `serializeRichComponents (F-3.S4 R-5.7)`:

```js
it("emits style/verticalAlign=super for <sup>", async () => {
  const el = await fixture(html`<wavy-composer available></wavy-composer>`);
  document.body.appendChild(el);
  const body = getBody(el);
  body.innerHTML = "x<sup>2</sup>";
  const components = el.serializeRichComponents();
  const superRun = components.find(
    c => c.type === "annotated"
      && c.annotationKey === "style/verticalAlign"
      && c.annotationValue === "super"
  );
  expect(superRun).to.exist;
  expect(superRun.text).to.equal("2");
  el.remove();
});
```

Mirror it for `<sub>2</sub>` expecting `annotationValue === "sub"`.

- [ ] **Step 2: Verify composer tests fail**

Run:

```sh
cd j2cl/lit && npm test -- --files test/wavy-composer.test.js
```

Expected before implementation: FAIL because `<sup>` / `<sub>` fall through as plain text.

- [ ] **Step 3: Implement composer wrapping and serialization**

In `_handleToolbarAction`, extend `inlineWraps`:

```js
superscript: { tag: "sup", siblings: [] },
subscript: { tag: "sub", siblings: [] }
```

In `inlineFormatAnnotation(tag)`, add:

```js
case "sup":
  return { key: "style/verticalAlign", value: "super" };
case "sub":
  return { key: "style/verticalAlign", value: "sub" };
```

- [ ] **Step 4: Verify composer tests pass**

Run:

```sh
cd j2cl/lit && npm test -- --files test/wavy-composer.test.js
```

Expected after implementation: PASS.

## Task 3: Java DocOp Round Trip

- [ ] **Step 1: Add Java round-trip tests**

Add two tests near the F-3.S4 round-trip block in `J2clRichContentDeltaFactoryTest.java`:

```java
@Test
public void createReplyRequestRoundTripsSuperscriptAnnotation() {
  J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
  J2clSidecarWriteSession session =
      new J2clSidecarWriteSession(
          "example.com/w+abc", "chan-4", 3L, "HASH", "b+root");
  J2clComposerDocument document =
      J2clComposerDocument.builder()
          .annotatedText("style/verticalAlign", "super", "x²")
          .build();
  String deltaJson =
      factory.createReplyRequest("user@example.com", session, document).getDeltaJson();
  assertContains(
      deltaJson,
      "{\"1\":{\"3\":[{\"1\":\"style/verticalAlign\",\"3\":\"super\"}]}}",
      "\"2\":\"x²\"",
      "{\"1\":{\"2\":[\"style/verticalAlign\"]}}");
}
```

Mirror it for `annotationValue` `sub` and text such as `H₂O`.

- [ ] **Step 2: Verify Java tests**

Run:

```sh
sbt --batch j2clSearchTest
```

Expected: PASS. The attempted narrower command
`sbt --batch "Test/testOnly org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactoryTest"`
does not run this J2CL test class in the current SBT layout (`No tests to run`), so
`j2clSearchTest` is the effective focused Java/J2CL gate for this file.

## Task 4: Changelog And Final Verification

- [ ] **Step 1: Add changelog fragment**

Create `wave/config/changelog.d/2026-04-30-j2cl-superscript-subscript.json`:

```json
{
  "releaseId": "2026-04-30-j2cl-superscript-subscript",
  "version": "PR #1071",
  "date": "2026-04-30",
  "title": "J2CL superscript and subscript toolbar buttons",
  "summary": "The J2CL rich-edit toolbar now exposes superscript and subscript controls and preserves their vertical-align annotations through rich reply submission.",
  "sections": [
    {
      "type": "feature",
      "items": [
        "Adds Superscript and Subscript buttons to the J2CL rich-edit toolbar between Strikethrough and Heading.",
        "Serializes <sup> and <sub> rich-composer content as style/verticalAlign annotations for DocOp round trips."
      ]
    }
  ]
}
```

- [ ] **Step 2: Run focused and broader gates**

Run:

```sh
cd j2cl/lit && npm test -- --files test/wavy-format-toolbar.test.js test/wavy-composer.test.js
```

Run:

```sh
cd j2cl/lit && npm run build
```

Run:

```sh
sbt --batch compile j2clSearchTest
```

Run:

```sh
python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Run:

```sh
git diff --check
```

Expected: all commands exit 0. If SBT exposes a narrower confirmed J2CL test command for `J2clRichContentDeltaFactoryTest`, run it before the broader `compile j2clSearchTest` gate and record both.

## Self-Review

- Spec coverage: The plan covers toolbar buttons/order/events, composer `<sup>/<sub>` serialization to `style/verticalAlign`, Java `J2clComposerDocument.builder().annotatedText("style/verticalAlign", ...)` reply delta round trips, changelog, and issue-visible verification.
- Placeholder scan: No `TBD`, `TODO`, or unspecified "add tests" steps remain; each test and implementation seam is named directly.
- Scope control: The plan does not implement font family, font size, colors, highlight, heading semantics, or alignment/RTL round-trip.
- Risk: Existing inline annotations use bare `fontWeight` / `fontStyle` keys, but #1071 explicitly requires `style/verticalAlign`; the new behavior should not rename existing keys.

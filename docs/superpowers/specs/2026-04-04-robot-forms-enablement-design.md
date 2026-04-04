# Robot Forms Enablement Design

Status: ready for implementation
Date: 2026-04-04
Scope: unblock robot form operations, add value-change events, convenience API, tests, and documentation
Relation: stepping stone toward full robot HTML injection (PR #570 design)

## 1. Problem

The Wave codebase has complete form element infrastructure (client doodads, serialization, schema support, button click events) but robots cannot fully use it because:

1. `DocumentModifyService.updateElement()` throws `UnsupportedOperationException` for non-gadget elements — robots can INSERT form elements but cannot UPDATE their values
2. No event fires when form input/checkbox/radio values change — only `FORM_BUTTON_CLICKED` exists
3. No convenience methods on `Blip` for common form patterns
4. No API documentation describing how robots should use forms

## 2. Approach

Fix the three code gaps in the existing infrastructure rather than building anything new. All changes follow existing patterns (gadget state change detection, gadget update path, existing serialization).

## 3. Changes

### 3.1 Fix UPDATE_ELEMENT for Form Elements

**File:** `DocumentModifyService.java` (~line 454)

Current code:
```java
if (element.isGadget()) {
  // ... update gadget
} else {
  throw new UnsupportedOperationException(
      "Can't update other elements than gadgets at the moment");
}
```

Change: add an `else if (element.isFormElement())` branch that:
1. Locates the target element in the document (same as gadget path)
2. Updates the element's attributes (`value`, `defaultValue`, `name`) from the API `FormElement` properties
3. Uses `doc.setElementAttribute()` for each property that is non-null in the update request

This mirrors `updateExistingGadgetElement()` but for form element attributes. The form element XML structures are simpler than gadgets (flat attributes vs nested state/pref children), so the update logic is straightforward.

### 3.2 Add FORM_VALUE_CHANGED Event

**New event type** following the exact pattern of `FormButtonClickedEvent` and `GadgetStateChangedEvent`.

#### 3.2.1 EventType enum

Add `FORM_VALUE_CHANGED(FormValueChangedEvent.class)` to `EventType.java`.

#### 3.2.2 FormValueChangedEvent class

New class in `com.google.wave.api.event` package:
- Extends `AbstractEvent`
- Fields: `elementName` (String), `elementType` (String — "input", "check", "radio", etc.), `oldValue` (String), `newValue` (String)
- Constructor follows same pattern as `FormButtonClickedEvent`

#### 3.2.3 EventGenerator detection

In `EventGenerator.onDocumentEvents()`, add detection for form element value changes, similar to the existing gadget state change detection (lines 282-331). The trigger is `DocumentEvent.Type.ATTRIBUTES_MODIFIED` where the target element is a form element (check tag name against the set: `input`, `check`, `password`, `textarea`, `radiogroup`) and the modified attribute is `value`.

Logic:
```
if capabilities contains FORM_VALUE_CHANGED:
  if event is ATTRIBUTES_MODIFIED:
    element = event target
    if element tag is form element AND "value" in modified attributes:
      create FormValueChangedEvent with:
        elementName = element.getAttribute("name")
        elementType = element.getTagName()
        oldValue = old attribute value
        newValue = new attribute value
      add event
```

#### 3.2.4 AbstractRobot dispatch

Add `onFormValueChanged(FormValueChangedEvent event)` handler stub (no-op by default) and dispatch case in `processEvents()`.

#### 3.2.5 RobotCapabilitiesParser

Register `FORM_VALUE_CHANGED` so robots can declare interest in this event via their capabilities XML.

### 3.3 Blip Convenience Methods

Add to `Blip.java`:

```java
/** Appends a button form element. */
public void appendButton(String name, String caption)

/** Appends a text input form element. */
public void appendTextInput(String name, String defaultValue)

/** Appends a textarea form element. */
public void appendTextArea(String name, String defaultValue)

/** Appends a checkbox form element. */
public void appendCheckBox(String name, boolean defaultChecked)

/** Appends a label form element. */
public void appendLabel(String forElement, String text)
```

Each method creates a `FormElement` of the appropriate type and calls `this.append(formElement)`. These are thin wrappers — the real work is in the existing `append(BlipContent)` path.

### 3.4 API Documentation

Create `/wave/src/main/resources/robot-api-docs/form-elements.md` documenting:
- Available form element types and their properties
- How to insert form elements via `document.modify`
- How to update form element values
- How to listen for `FORM_BUTTON_CLICKED` and `FORM_VALUE_CHANGED` events
- Convenience methods on `Blip`
- Example: building a simple survey form robot

Also update the existing robot API reference if one exists.

### 3.5 Tests

#### Unit tests:
- `DocumentModifyServiceTest`: add `testUpdateFormElement()` — insert a button, update its value, verify
- `FormValueChangedEventTest`: construct event, verify fields
- `EventGeneratorTest`: add test for FORM_VALUE_CHANGED detection from attribute modifications
- `BlipTest`: test convenience methods create correct operations

#### Integration test (if E2E framework supports):
- Robot inserts form → client renders → user clicks button → robot receives event → robot updates form value → verify round-trip

## 4. Files Changed

| File | Change |
|------|--------|
| `DocumentModifyService.java` | Add form element update branch |
| `EventType.java` | Add `FORM_VALUE_CHANGED` |
| `FormValueChangedEvent.java` | New event class |
| `EventGenerator.java` | Add value-change detection |
| `AbstractRobot.java` | Add handler + dispatch |
| `Blip.java` | Add convenience methods |
| `DocumentModifyServiceTest.java` | Add form update test |
| `FormValueChangedEventTest.java` | New test |
| `EventGeneratorTest.java` | Add value change test |
| `BlipTest.java` | Add convenience method tests |
| `form-elements.md` | New API docs |

## 5. Compatibility with Robot HTML Injection

This work is additive and fully compatible with the PR #570 robot HTML injection design:
- Form elements remain as they are — first-class Wave document elements
- Robot HTML injection adds a separate `<robot-html>` element type
- A robot can use both: forms for interactive inputs, HTML blocks for rich display
- The `FORM_VALUE_CHANGED` event complements the future HTML injection interactivity model
- No schema conflicts — form elements and robot-html elements coexist in `<body>`

## 6. Security

No new security surface:
- Form elements use the existing fixed XML schema — no arbitrary HTML
- Value updates go through `doc.setElementAttribute()` which only sets string attributes on known elements
- Event generation reads from the document model, not user input
- No iframe, no script execution, no DOM injection

# Robot Form Elements API

Robots can create interactive forms in wave blips using form elements. Users interact with these forms (clicking buttons, checking boxes, typing in inputs), and the robot receives events about these interactions.

## Supported Form Element Types

| Type | Tag | Description | Key Properties |
|------|-----|-------------|----------------|
| BUTTON | `<button>` | Clickable button | `name`, `value` (caption text) |
| INPUT | `<input>` | Single-line text field | `name`, `defaultValue`, `value` |
| TEXTAREA | `<textarea>` | Multi-line text field | `name`, `defaultValue`, `value` |
| CHECK | `<check>` | Checkbox (true/false) | `name`, `value` ("true"/"false") |
| RADIO_BUTTON | `<radio>` | Radio button (in a group) | `name`, `group` |
| RADIO_BUTTON_GROUP | `<radiogroup>` | Container for radio buttons | `name`, `value` (selected button name) |
| LABEL | `<label>` | Text label for another element | `name` (for attribute), `value` (text) |
| PASSWORD | `<password>` | Password input (masked) | `name`, `value` |

## Inserting Form Elements

### Using convenience methods (recommended)

```java
// In your robot's event handler:
Blip blip = event.getBlip();

blip.appendLabel("username", "Your Name:");
blip.appendTextInput("username", "Enter your name");
blip.appendLabel("agree", "I agree to terms:");
blip.appendCheckBox("agree", false);
blip.appendButton("submit", "Submit");
```

### Using the raw Element API

```java
Blip blip = event.getBlip();
blip.append(new FormElement(ElementType.BUTTON, "myButton", "Click Me"));
blip.append(new FormElement(ElementType.INPUT, "myField", "default text"));
```

## Updating Form Element Values

Robots can update form element values via the `document.modify` operation with `UPDATE_ELEMENT`:

```java
// Find the element by type and name, then update just its current value.
FormElement updated = new FormElement(ElementType.CHECK, "agree");
updated.setValue("true");
blip.first(ElementType.CHECK, FormElement.restrictByName("agree"))
    .updateElement(updated);
```

Use `setDefaultValue(...)` only when you want to change the element's on-wire
default as well.

## Listening for Events

### FORM_BUTTON_CLICKED

Fired when a user clicks a button element.

```java
@Override
public void onFormButtonClicked(FormButtonClickedEvent event) {
  String buttonName = event.getButtonName();
  // Handle button click
}
```

Declare in capabilities XML:
```xml
<w:capability name="FORM_BUTTON_CLICKED"/>
```

### FORM_VALUE_CHANGED

Fired when an attribute-backed form element's value changes, such as a checkbox
toggle, password update, or radio-group selection.

```java
@Override
public void onFormValueChanged(FormValueChangedEvent event) {
  String elementName = event.getElementName();  // e.g., "agree"
  String elementType = event.getElementType();   // e.g., "check"
  String oldValue = event.getOldValue();         // e.g., "false"
  String newValue = event.getNewValue();         // e.g., "true"
  // Handle value change
}
```

Declare in capabilities XML:
```xml
<w:capability name="FORM_VALUE_CHANGED"/>
```

Form elements whose content is stored as child text, such as `input`,
`textarea`, `button`, and `label`, are still updated through
`UPDATE_ELEMENT`, but they do not currently emit `FORM_VALUE_CHANGED`.

## Example: Survey Robot

```java
public class SurveyRobot extends AbstractRobot {

  @Override
  public void onWaveletSelfAdded(WaveletSelfAddedEvent event) {
    Blip blip = event.getWavelet().getRootBlip();
    blip.append("Please fill out this survey:\n");
    blip.appendLabel("name", "Your name:");
    blip.appendTextInput("name", "");
    blip.appendLabel("satisfied", "Are you satisfied?");
    blip.appendCheckBox("satisfied", false);
    blip.appendButton("submit", "Submit Survey");
  }

  @Override
  public void onFormButtonClicked(FormButtonClickedEvent event) {
    if ("submit".equals(event.getButtonName())) {
      Blip blip = event.getBlip();
      // Read form values from blip elements
      blip.append("\nThank you for your response!");
    }
  }

  @Override
  public void onFormValueChanged(FormValueChangedEvent event) {
    // React to value changes in real-time if needed
    if ("satisfied".equals(event.getElementName())) {
      // User toggled satisfaction checkbox
    }
  }
}
```

## Compatibility with Robot HTML Injection

Form elements and the future `<robot-html>` element (PR #570 design) are complementary:
- **Form elements** are best for structured input (buttons, text fields, checkboxes)
- **Robot HTML** will be best for rich display content (formatted text, images, layouts)

Both can coexist in the same blip.

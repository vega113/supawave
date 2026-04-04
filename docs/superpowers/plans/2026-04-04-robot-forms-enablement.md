# Robot Forms Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable robots to fully use Wave's existing form element infrastructure — fix UPDATE_ELEMENT for form elements, add FORM_VALUE_CHANGED event, add Blip convenience methods, write tests, and create API documentation.

**Architecture:** The Wave codebase already has complete form doodads, serialization, and schema support. We fix three gaps: (1) `DocumentModifyService.updateElement()` only handles gadgets — add form element branch, (2) `EventGenerator` only detects button clicks — add attribute-change detection for form elements, (3) `Blip` has no form convenience methods — add them. All changes follow existing patterns.

**Tech Stack:** Java 11, JUnit 3 (extends `RobotsTestBase`), SBT build, Wave document model, Robot API (JSON-RPC)

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `wave/src/main/java/com/google/wave/api/event/FormValueChangedEvent.java` | Create | New event class for form value changes |
| `wave/src/main/java/com/google/wave/api/event/EventType.java` | Modify | Add FORM_VALUE_CHANGED enum entry |
| `wave/src/main/java/com/google/wave/api/event/EventHandler.java` | Modify | Add onFormValueChanged handler method |
| `wave/src/main/java/com/google/wave/api/AbstractRobot.java` | Modify | Add dispatch + no-op handler |
| `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java` | Modify | Add form value change detection |
| `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java` | Modify | Add form element update branch |
| `wave/src/main/java/com/google/wave/api/Blip.java` | Modify | Add form convenience methods |
| `wave/src/test/java/com/google/wave/api/event/FormValueChangedEventTest.java` | Create | Unit test for new event |
| `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java` | Modify | Add form update tests |
| `wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java` | Modify | Add form value change event test |
| `wave/src/test/java/com/google/wave/api/BlipFormMethodsTest.java` | Create | Test Blip convenience methods |
| `docs/robot-form-elements-api.md` | Create | API documentation for robot forms |

---

### Task 1: Create FormValueChangedEvent class

**Files:**
- Create: `wave/src/main/java/com/google/wave/api/event/FormValueChangedEvent.java`
- Create: `wave/src/test/java/com/google/wave/api/event/FormValueChangedEventTest.java`

- [ ] **Step 1: Write the failing test**

Create `wave/src/test/java/com/google/wave/api/event/FormValueChangedEventTest.java`:

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.wave.api.event;

import junit.framework.TestCase;

/**
 * Unit tests for {@link FormValueChangedEvent}.
 */
public class FormValueChangedEventTest extends TestCase {

  public void testConstructorAndGetters() {
    FormValueChangedEvent event = new FormValueChangedEvent(
        null, null, "user@example.com", 12345L, "blip1",
        "myInput", "input", "oldVal", "newVal");

    assertEquals(EventType.FORM_VALUE_CHANGED, event.getType());
    assertEquals("myInput", event.getElementName());
    assertEquals("input", event.getElementType());
    assertEquals("oldVal", event.getOldValue());
    assertEquals("newVal", event.getNewValue());
    assertEquals("user@example.com", event.getModifiedBy());
    assertEquals(12345L, event.getTimestamp());
  }

  public void testAsWithCorrectType() {
    FormValueChangedEvent event = new FormValueChangedEvent(
        null, null, "user@example.com", 12345L, "blip1",
        "myInput", "input", "old", "new");
    FormValueChangedEvent cast = FormValueChangedEvent.as(event);
    assertNotNull(cast);
    assertEquals("myInput", cast.getElementName());
  }

  public void testAsWithWrongType() {
    FormButtonClickedEvent wrongEvent = new FormButtonClickedEvent(
        null, null, "user@example.com", 12345L, "blip1", "btn");
    FormValueChangedEvent cast = FormValueChangedEvent.as(wrongEvent);
    assertNull(cast);
  }

  public void testDefaultConstructor() {
    // Deserialization constructor should not throw
    FormValueChangedEvent event = new FormValueChangedEvent();
    assertNull(event.getElementName());
    assertNull(event.getElementType());
    assertNull(event.getOldValue());
    assertNull(event.getNewValue());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "wave/testOnly com.google.wave.api.event.FormValueChangedEventTest"`
Expected: Compilation failure — `FormValueChangedEvent` does not exist yet.

- [ ] **Step 3: Create FormValueChangedEvent**

Create `wave/src/main/java/com/google/wave/api/event/FormValueChangedEvent.java`:

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.wave.api.event;

import com.google.wave.api.Wavelet;
import com.google.wave.api.impl.EventMessageBundle;

/**
 * Event triggered when a form element's value changes.
 */
public class FormValueChangedEvent extends AbstractEvent {

  /** The name attribute of the form element whose value changed. */
  private final String elementName;

  /** The tag name of the form element (e.g., "input", "check", "radiogroup"). */
  private final String elementType;

  /** The previous value of the element. */
  private final String oldValue;

  /** The new value of the element. */
  private final String newValue;

  /**
   * Constructor.
   *
   * @param wavelet the wavelet where this event occurred.
   * @param bundle the message bundle this event belongs to.
   * @param modifiedBy the id of the participant that triggered this event.
   * @param timestamp the timestamp of this event.
   * @param blipId the id of the blip containing the form element.
   * @param elementName the name attribute of the form element.
   * @param elementType the tag name of the form element.
   * @param oldValue the previous value.
   * @param newValue the new value.
   */
  public FormValueChangedEvent(Wavelet wavelet, EventMessageBundle bundle, String modifiedBy,
      Long timestamp, String blipId, String elementName, String elementType,
      String oldValue, String newValue) {
    super(EventType.FORM_VALUE_CHANGED, wavelet, bundle, modifiedBy, timestamp, blipId);
    this.elementName = elementName;
    this.elementType = elementType;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  /**
   * Constructor for deserialization.
   */
  FormValueChangedEvent() {
    this.elementName = null;
    this.elementType = null;
    this.oldValue = null;
    this.newValue = null;
  }

  public String getElementName() {
    return elementName;
  }

  public String getElementType() {
    return elementType;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  /**
   * Helper method for type conversion.
   *
   * @return the concrete type of this event, or {@code null} if it is of a
   *     different event type.
   */
  public static FormValueChangedEvent as(Event event) {
    if (!(event instanceof FormValueChangedEvent)) {
      return null;
    }
    return FormValueChangedEvent.class.cast(event);
  }
}
```

- [ ] **Step 4: Add FORM_VALUE_CHANGED to EventType enum**

In `wave/src/main/java/com/google/wave/api/event/EventType.java`, add after line 42 (`FORM_BUTTON_CLICKED`):

```java
  FORM_VALUE_CHANGED(FormValueChangedEvent.class),
```

So the enum section becomes:
```java
  FORM_BUTTON_CLICKED(FormButtonClickedEvent.class),
  FORM_VALUE_CHANGED(FormValueChangedEvent.class),
  GADGET_STATE_CHANGED(GadgetStateChangedEvent.class),
```

- [ ] **Step 5: Run test to verify it passes**

Run: `sbt "wave/testOnly com.google.wave.api.event.FormValueChangedEventTest"`
Expected: All 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/com/google/wave/api/event/FormValueChangedEvent.java \
       wave/src/main/java/com/google/wave/api/event/EventType.java \
       wave/src/test/java/com/google/wave/api/event/FormValueChangedEventTest.java
git commit -m "feat(robot-api): add FormValueChangedEvent and FORM_VALUE_CHANGED event type"
```

---

### Task 2: Wire FormValueChangedEvent into EventHandler and AbstractRobot

**Files:**
- Modify: `wave/src/main/java/com/google/wave/api/event/EventHandler.java`
- Modify: `wave/src/main/java/com/google/wave/api/AbstractRobot.java`

- [ ] **Step 1: Add handler to EventHandler interface**

In `wave/src/main/java/com/google/wave/api/event/EventHandler.java`, add after line 89 (the `onFormButtonClicked` method):

```java
  /**
   * Handler for {@link FormValueChangedEvent}.
   *
   * @param event the form value changed event.
   */
  void onFormValueChanged(FormValueChangedEvent event);
```

- [ ] **Step 2: Add dispatch case in AbstractRobot.processEvents()**

In `wave/src/main/java/com/google/wave/api/AbstractRobot.java`, add after line 650 (the `FORM_BUTTON_CLICKED` case):

```java
        case FORM_VALUE_CHANGED:
          onFormValueChanged(FormValueChangedEvent.as(event));
          break;
```

- [ ] **Step 3: Add no-op handler in AbstractRobot**

In `wave/src/main/java/com/google/wave/api/AbstractRobot.java`, add after line 851 (the `onFormButtonClicked` no-op):

```java
  @Override
  public void onFormValueChanged(FormValueChangedEvent event) {
    // No-op.
  }
```

- [ ] **Step 4: Add import in AbstractRobot**

At the top of `AbstractRobot.java`, add import:

```java
import com.google.wave.api.event.FormValueChangedEvent;
```

- [ ] **Step 5: Verify compilation**

Run: `sbt wave/compile`
Expected: BUILD SUCCESS — no compilation errors.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/com/google/wave/api/event/EventHandler.java \
       wave/src/main/java/com/google/wave/api/AbstractRobot.java
git commit -m "feat(robot-api): wire FormValueChangedEvent into EventHandler and AbstractRobot"
```

---

### Task 3: Add form value change detection in EventGenerator

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

Add to `wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java`.

Add import at top:
```java
import com.google.wave.api.event.FormValueChangedEvent;
```

Add test method before the private helper methods section:

```java
  public void testGenerateFormValueChangedEvent() throws Exception {
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip rootBlip =
        conversation.getRoot().getRootThread().getFirstBlip();

    // Insert a check form element into the blip
    String checkXml = "<check name=\"agree\" value=\"false\"/>";
    LineContainers.appendToLastLine(
        rootBlip.getContent(), XmlStringBuilder.createFromXmlString(checkXml));

    // Clear ops from the insert — we only want to capture the value change
    output.clear();

    // Now change the value attribute on the check element to simulate user toggle
    Document doc = rootBlip.getContent();
    // Find the check element — it's after the line elements
    org.waveprotocol.wave.model.document.Doc.E checkElem = null;
    org.waveprotocol.wave.model.document.Doc.N node = doc.getFirstChild(doc.getDocumentElement());
    while (node != null) {
      org.waveprotocol.wave.model.document.Doc.E el = doc.asElement(node);
      if (el != null && "check".equals(doc.getTagName(el))) {
        checkElem = el;
        break;
      }
      node = doc.getNextSibling(node);
    }
    assertNotNull("Should find the check element", checkElem);
    doc.setElementAttribute(checkElem, "value", "true");

    EventMessageBundle messages = generateAndCheckEvents(EventType.FORM_VALUE_CHANGED);
    assertEquals("Expected one FORM_VALUE_CHANGED event", 1, messages.getEvents().size());
    FormValueChangedEvent event = FormValueChangedEvent.as(messages.getEvents().get(0));
    assertEquals("agree", event.getElementName());
    assertEquals("check", event.getElementType());
    assertEquals("false", event.getOldValue());
    assertEquals("true", event.getNewValue());
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest -- -t testGenerateFormValueChangedEvent"`
Expected: FAIL — no FORM_VALUE_CHANGED event generated.

- [ ] **Step 3: Add form value change detection to EventGenerator**

In `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java`, add import at top:

```java
import com.google.wave.api.event.FormValueChangedEvent;
import java.util.Set;
```

In the `onDocumentEvents()` method, add detection block. Insert after the existing `FORM_BUTTON_CLICKED` block (after line 348) and before the `DOCUMENT_CHANGED` block (before line 349):

```java
          if (capabilities.containsKey(EventType.FORM_VALUE_CHANGED)) {
            if (eventComponent.getType() == DocumentEvent.Type.ATTRIBUTES) {
              AttributesModified<N, E, T> attributesModified =
                  (AttributesModified<N, E, T>) eventComponent;
              org.waveprotocol.wave.model.document.raw.impl.Element rawElement =
                  (org.waveprotocol.wave.model.document.raw.impl.Element)
                      attributesModified.getElement();
              String tagName = rawElement.getTagName();
              if (isFormElementTag(tagName)
                  && attributesModified.getOldValues().containsKey("value")) {
                String elementName = rawElement.getAttribute("name");
                String oldValue = attributesModified.getOldValues().get("value");
                String newValue = rawElement.getAttribute("value");
                FormValueChangedEvent valueChangedEvent = new FormValueChangedEvent(
                    null, null, deltaAuthor.getAddress(), deltaTimestamp,
                    blip.getId(), elementName, tagName, oldValue, newValue);
                addEvent(valueChangedEvent, capabilities, blip.getId(), messages);
              }
            }
          }
```

Add a private helper method at the end of the `EventGenerator` class (before the closing brace):

```java
  /** Form element tag names that can have value changes. */
  private static final Set<String> FORM_ELEMENT_TAGS = Set.of(
      "input", "check", "password", "textarea", "radiogroup");

  private static boolean isFormElementTag(String tagName) {
    return FORM_ELEMENT_TAGS.contains(tagName);
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest -- -t testGenerateFormValueChangedEvent"`
Expected: PASS

- [ ] **Step 5: Run all EventGenerator tests**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"`
Expected: All tests PASS — existing tests unaffected.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java \
       wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java
git commit -m "feat(robot-api): detect form element value changes in EventGenerator"
```

---

### Task 4: Fix UPDATE_ELEMENT for form elements in DocumentModifyService

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java`

- [ ] **Step 1: Write the failing test — insert + update a form button**

Add to `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java`.

Add import at top:
```java
import com.google.wave.api.FormElement;
```

Add test method after `testUpdateGadget()`:

```java
  public void testInsertAndUpdateFormElement() throws Exception {
    // Step 1: Insert a check form element
    List<Element> insertElements = Lists.newArrayListWithCapacity(1);
    insertElements.add(new FormElement(ElementType.CHECK, "agree", "false", "false"));

    OperationRequest insertOperation =
        operationRequest(OperationType.DOCUMENT_MODIFY, rootBlipId,
            Parameter.of(ParamsProperty.MODIFY_ACTION,
                new DocumentModifyAction(ModifyHow.INSERT, NO_VALUES, NO_ANNOTATION_KEY,
                    insertElements, NO_BUNDLED_ANNOTATIONS, false)),
            Parameter.of(ParamsProperty.INDEX, CONTENT_START_TEXT));

    service.execute(insertOperation, helper.getContext(), ALEX);

    // Verify element was inserted
    FormElement insertedCheck = null;
    for (ApiView.ElementInfo info : getApiView().getElements()) {
      if (info.element.isFormElement() && info.element.getType() == ElementType.CHECK) {
        insertedCheck = (FormElement) info.element;
        break;
      }
    }
    assertNotNull("Check element should have been inserted", insertedCheck);
    assertEquals("false", insertedCheck.getValue());

    // Step 2: Update the check element value
    List<Element> updateElements = Lists.newArrayListWithCapacity(1);
    FormElement updatedElement = new FormElement(ElementType.CHECK, "agree");
    updatedElement.setValue("true");
    updateElements.add(updatedElement);

    OperationRequest updateOperation =
        operationRequest(OperationType.DOCUMENT_MODIFY, rootBlipId,
            Parameter.of(ParamsProperty.MODIFY_ACTION,
                new DocumentModifyAction(ModifyHow.UPDATE_ELEMENT,
                    NO_VALUES, NO_ANNOTATION_KEY, updateElements, NO_BUNDLED_ANNOTATIONS, false)),
            Parameter.of(ParamsProperty.MODIFY_QUERY,
                new DocumentModifyQuery(ElementType.CHECK,
                    ImmutableMap.of("name", "agree"), 1)));

    service.execute(updateOperation, helper.getContext(), ALEX);

    // Verify element was updated
    FormElement updatedCheck = null;
    for (ApiView.ElementInfo info : getApiView().getElements()) {
      if (info.element.isFormElement() && info.element.getType() == ElementType.CHECK) {
        updatedCheck = (FormElement) info.element;
        break;
      }
    }
    assertNotNull("Check element should still exist", updatedCheck);
    assertEquals("true", updatedCheck.getValue());
    assertEquals("agree", updatedCheck.getName());
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.operations.DocumentModifyServiceTest -- -t testInsertAndUpdateFormElement"`
Expected: FAIL with `UnsupportedOperationException: Can't update other elements than gadgets at the moment`

- [ ] **Step 3: Add form element update branch in DocumentModifyService**

In `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java`, replace the `else` block at lines 458-462:

```java
        } else {
          // TODO (Yuri Z.) Updating other elements.
          throw new UnsupportedOperationException(
              "Can't update other elements than gadgets at the moment");
        }
```

With:

```java
        } else if (element.isFormElement()) {
          int xmlStart = view.transformToXmlOffset(range.getStart());
          Doc.E docElem = Point.elementAfter(doc, doc.locate(xmlStart));
          updateExistingFormElement(doc, docElem, element);
        } else {
          throw new UnsupportedOperationException(
              "Can't update elements of type " + element.getType());
        }
```

Then add a new private method after `updateExistingGadgetElement()`:

```java
  /**
   * Updates the existing form element properties.
   *
   * @param doc the document to update elements in.
   * @param existingElement the form element to update.
   * @param element the element that describes what existingElement should be
   *        updated with.
   */
  private void updateExistingFormElement(Document doc, Doc.E existingElement, Element element) {
    Preconditions.checkArgument(element.isFormElement(),
        "Called with non-form element type %s", element.getType());

    // Update flat attributes: name, value, defaultValue
    String name = element.getProperty(FormElement.NAME);
    if (name != null) {
      doc.setElementAttribute(existingElement, "name", name);
    }
    String value = element.getProperty(FormElement.VALUE);
    if (value != null) {
      doc.setElementAttribute(existingElement, "value", value);
    }
    String defaultValue = element.getProperty(FormElement.DEFAULT_VALUE);
    if (defaultValue != null) {
      doc.setElementAttribute(existingElement, "defaultValue", defaultValue);
    }
  }
```

Add import at top of file:
```java
import com.google.wave.api.FormElement;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.operations.DocumentModifyServiceTest -- -t testInsertAndUpdateFormElement"`
Expected: PASS

- [ ] **Step 5: Run all DocumentModifyService tests**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.operations.DocumentModifyServiceTest"`
Expected: All tests PASS — existing gadget update test unaffected.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java \
       wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java
git commit -m "feat(robot-api): enable UPDATE_ELEMENT for form elements in DocumentModifyService"
```

---

### Task 5: Add Blip form convenience methods

**Files:**
- Modify: `wave/src/main/java/com/google/wave/api/Blip.java`
- Create: `wave/src/test/java/com/google/wave/api/BlipFormMethodsTest.java`

- [ ] **Step 1: Write the failing test**

Create `wave/src/test/java/com/google/wave/api/BlipFormMethodsTest.java`:

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.wave.api;

import com.google.wave.api.impl.WaveletData;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for form convenience methods on {@link Blip}.
 */
public class BlipFormMethodsTest extends TestCase {

  private Blip blip;
  private OperationQueue opQueue;

  @Override
  protected void setUp() throws Exception {
    opQueue = new OperationQueue();
    Map<String, Blip> blips = new HashMap<>();
    Map<String, BlipThread> threads = new HashMap<>();
    Wavelet wavelet = new Wavelet(
        "wave1", "wavelet1", "user@example.com", 0L, 0L, "title",
        "root", null, new HashMap<>(), blips, threads, opQueue);
    blip = new Blip("root", "\n", null, null, wavelet, opQueue);
    blips.put("root", blip);
  }

  public void testAppendButton() {
    blip.appendButton("submit", "Submit Form");
    // Verify an operation was queued that contains the button element
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.BUTTON && "submit".equals(el.getProperty("name"))) {
        assertEquals("Submit Form", el.getProperty("value"));
        found = true;
        break;
      }
    }
    assertTrue("Button element should be in blip elements", found);
  }

  public void testAppendTextInput() {
    blip.appendTextInput("username", "Enter name");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.INPUT && "username".equals(el.getProperty("name"))) {
        assertEquals("Enter name", el.getProperty("defaultValue"));
        found = true;
        break;
      }
    }
    assertTrue("Input element should be in blip elements", found);
  }

  public void testAppendCheckBox() {
    blip.appendCheckBox("agree", true);
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.CHECK && "agree".equals(el.getProperty("name"))) {
        assertEquals("true", el.getProperty("value"));
        found = true;
        break;
      }
    }
    assertTrue("Check element should be in blip elements", found);
  }

  public void testAppendTextArea() {
    blip.appendTextArea("comments", "Type here...");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.TEXTAREA && "comments".equals(el.getProperty("name"))) {
        assertEquals("Type here...", el.getProperty("defaultValue"));
        found = true;
        break;
      }
    }
    assertTrue("Textarea element should be in blip elements", found);
  }

  public void testAppendLabel() {
    blip.appendLabel("username", "Your Name:");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.LABEL && "username".equals(el.getProperty("name"))) {
        assertEquals("Your Name:", el.getProperty("value"));
        found = true;
        break;
      }
    }
    assertTrue("Label element should be in blip elements", found);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "wave/testOnly com.google.wave.api.BlipFormMethodsTest"`
Expected: Compilation failure — methods `appendButton`, `appendTextInput`, etc. don't exist.

- [ ] **Step 3: Add convenience methods to Blip**

In `wave/src/main/java/com/google/wave/api/Blip.java`, add after the `append(String argument)` method (after line 525):

```java
  /**
   * Appends a button form element to the blip.
   *
   * @param name the name of the button (used in FormButtonClickedEvent).
   * @param caption the display text on the button.
   * @return an instance of {@link BlipContentRefs}.
   */
  public BlipContentRefs appendButton(String name, String caption) {
    return append(new FormElement(ElementType.BUTTON, name, caption, caption));
  }

  /**
   * Appends a text input form element to the blip.
   *
   * @param name the name of the input element.
   * @param defaultValue the default/placeholder value.
   * @return an instance of {@link BlipContentRefs}.
   */
  public BlipContentRefs appendTextInput(String name, String defaultValue) {
    return append(new FormElement(ElementType.INPUT, name, defaultValue));
  }

  /**
   * Appends a textarea form element to the blip.
   *
   * @param name the name of the textarea element.
   * @param defaultValue the default/placeholder value.
   * @return an instance of {@link BlipContentRefs}.
   */
  public BlipContentRefs appendTextArea(String name, String defaultValue) {
    return append(new FormElement(ElementType.TEXTAREA, name, defaultValue));
  }

  /**
   * Appends a checkbox form element to the blip.
   *
   * @param name the name of the checkbox element.
   * @param defaultChecked whether the checkbox is initially checked.
   * @return an instance of {@link BlipContentRefs}.
   */
  public BlipContentRefs appendCheckBox(String name, boolean defaultChecked) {
    String value = String.valueOf(defaultChecked);
    return append(new FormElement(ElementType.CHECK, name, value, value));
  }

  /**
   * Appends a label form element to the blip.
   *
   * @param forElement the name of the form element this label is for.
   * @param text the label text.
   * @return an instance of {@link BlipContentRefs}.
   */
  public BlipContentRefs appendLabel(String forElement, String text) {
    return append(new FormElement(ElementType.LABEL, forElement, text, text));
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sbt "wave/testOnly com.google.wave.api.BlipFormMethodsTest"`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/com/google/wave/api/Blip.java \
       wave/src/test/java/com/google/wave/api/BlipFormMethodsTest.java
git commit -m "feat(robot-api): add form convenience methods to Blip class"
```

---

### Task 6: Write API documentation

**Files:**
- Create: `docs/robot-form-elements-api.md`

- [ ] **Step 1: Create API documentation**

Create `docs/robot-form-elements-api.md`:

```markdown
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

### Using document.modify with INSERT

```java
Blip blip = event.getBlip();
blip.append(new FormElement(ElementType.BUTTON, "myButton", "Click Me"));
blip.append(new FormElement(ElementType.INPUT, "myField", "default text"));
```

## Updating Form Element Values

Robots can update form element values via the `document.modify` operation with `UPDATE_ELEMENT`:

```java
// Find the element and update it
blip.first(ElementType.CHECK, FormElement.restrictByName("agree"))
    .updateElement(new FormElement(ElementType.CHECK, "agree", "false", "true"));
```

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

Fired when a form element's value changes (input text, checkbox toggle, radio selection).

```java
@Override
public void onFormValueChanged(FormValueChangedEvent event) {
  String elementName = event.getElementName();  // e.g., "agree"
  String elementType = event.getElementType();  // e.g., "check"
  String oldValue = event.getOldValue();        // e.g., "false"
  String newValue = event.getNewValue();        // e.g., "true"
  // Handle value change
}
```

Declare in capabilities XML:
```xml
<w:capability name="FORM_VALUE_CHANGED"/>
```

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

Form elements and the future `<robot-html>` element (PR #570) are complementary:
- **Form elements** are best for structured input (buttons, text fields, checkboxes)
- **Robot HTML** will be best for rich display content (formatted text, images, layouts)

Both can coexist in the same blip. A robot might use HTML for a rich header and form elements for user interaction below it.
```

- [ ] **Step 2: Commit**

```bash
git add docs/robot-form-elements-api.md
git commit -m "docs: add robot form elements API documentation"
```

---

### Task 7: Run full test suite and verify

- [ ] **Step 1: Run all robot-related tests**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.*" "wave/testOnly com.google.wave.api.*"`
Expected: All tests PASS.

- [ ] **Step 2: Run full project compile**

Run: `sbt wave/compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Verify no regressions**

Run: `sbt wave/test`
Expected: All tests PASS (or same failures as before this change — no new failures).

- [ ] **Step 4: Create final summary commit if needed**

If any fixes were needed, commit them. Otherwise skip.

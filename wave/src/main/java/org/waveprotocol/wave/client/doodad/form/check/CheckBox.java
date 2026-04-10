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

package org.waveprotocol.wave.client.doodad.form.check;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.user.client.Event;
import com.google.gwt.event.dom.client.KeyCodes;

import org.waveprotocol.wave.client.common.util.DomHelper;
import org.waveprotocol.wave.client.editor.ElementHandlerRegistry;
import org.waveprotocol.wave.client.editor.NodeEventHandler;
import org.waveprotocol.wave.client.editor.NodeEventHandlerImpl;
import org.waveprotocol.wave.client.editor.NodeEventHandlerImpl.Helper;
import org.waveprotocol.wave.client.editor.RenderingMutationHandler;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.event.EditorEvent;
import org.waveprotocol.wave.client.wavepanel.impl.edit.TaskMetadataPopup;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.util.Preconditions;

/** Doodad definition for a checkbox which stores its value in the doc. */
public final class CheckBox {
  private static final String TAGNAME = "check";

  private static final NodeEventHandler CHECKBOX_NODE_EVENT_HANDLER = new NodeEventHandlerImpl() {
    @Override
    public void onActivated(final ContentElement element) {
      if (!isTaskCheckBox(element)) {
        return;
      }
      refreshTaskMetadata(element);
      Element metadataNodelet = CheckBoxRenderingMutationHandler.getTaskMetadataNodelet(element);
      if (metadataNodelet == null) {
        return;
      }
      metadataNodelet.setTabIndex(0);
      metadataNodelet.setAttribute("role", "button");
      registerTaskMetadataPopupHandlers(element, metadataNodelet);
    }

    @Override
    public void onDeactivated(ContentElement element) {
      Helper.removeJsHandlers(element);
    }

    @Override
    public boolean handleClick(ContentElement element, EditorEvent event) {
      setChecked(element, !getChecked(element));
      event.allowBrowserDefault();
      return true;
    }
  };

  /**
   * Registers subclasses
   */
  public static void register(ElementHandlerRegistry handlerRegistry) {
    RenderingMutationHandler renderingMutationHandler =
        CheckBoxRenderingMutationHandler.getInstance();
    handlerRegistry.registerRenderingMutationHandler(TAGNAME, renderingMutationHandler);
    handlerRegistry.registerEventHandler(TAGNAME, CHECKBOX_NODE_EVENT_HANDLER);
  }

  /**
   * @param name
   * @param value
   * @return A content xml string containing a checkbox
   */
  public static XmlStringBuilder constructXml(String name, boolean value) {
    return XmlStringBuilder.createEmpty().wrap(
        TAGNAME, ContentElement.NAME, name, CheckConstants.VALUE, String.valueOf(value));
  }

  /**
   * @param name
   * @param value
   * @return A content xml string containing a checkbox
   */
  public static XmlStringBuilder constructXml(String name, String submit, boolean value) {
    return XmlStringBuilder.createEmpty().wrap(TAGNAME, ContentElement.SUBMIT,
        submit, ContentElement.NAME, name, CheckConstants.VALUE, String.valueOf(value));
  }

  private static final class CheckBoxRenderingMutationHandler extends RenderingMutationHandler {
    private static final String TASK_COMPLETED_CLASS = "task-completed";
    private static final String TASK_SPAN_CLASS = "task-check-span";
    private static final String TASK_METADATA_CLASS = "task-meta";
    private static final String TASK_PILL_CLASS = "task-pill";
    private static final String TASK_OWNER_PILL_CLASS = "task-owner-pill";
    private static final String TASK_DUE_PILL_CLASS = "task-due-pill";
    private static final String TASK_EMPTY_PILL_CLASS = "task-meta-empty";
    private static final String PARAGRAPH_PROPERTY = "__waveTaskParagraph";
    private static final String TASK_METADATA_PROPERTY = "__waveTaskMetadata";

    private static CheckBoxRenderingMutationHandler instance;

    public static RenderingMutationHandler getInstance() {
      if (instance == null) {
        instance = new CheckBoxRenderingMutationHandler();
      }
      return instance;
    }

    @Override
    public Element createDomImpl(Renderable element) {
      InputElement inputElem = Document.get().createCheckInputElement();
      inputElem.setClassName(CheckConstants.css.check());

      // Wrap in non-editable span- Firefox does not fire events for checkboxes
      // inside contentEditable region.
      SpanElement nonEditableSpan = Document.get().createSpanElement();
      DomHelper.setContentEditable(nonEditableSpan, false, false);
      nonEditableSpan.appendChild(inputElem);

      return nonEditableSpan;
    }

    @Override
    public void onActivationStart(ContentElement element) {
      fanoutAttrs(element);
      refreshTaskMetadata(element);
    }

    @Override
    public void onActivatedSubtree(ContentElement element) {
      if (!isTaskCheckBox(element)) {
        return;
      }
      refreshTaskMetadata(element);
      if (!getChecked(element)) {
        return;
      }
      final Element implNodelet = element.getImplNodelet();
      if (implNodelet == null) {
        return;
      }
      if (implNodelet.getPropertyObject(PARAGRAPH_PROPERTY) != null) {
        return;
      }
      Scheduler.get().scheduleFinally(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          Element firstChild = implNodelet.getFirstChildElement();
          if (firstChild == null || !InputElement.as(firstChild).isChecked()) {
            return;
          }
          if (implNodelet.getPropertyObject(PARAGRAPH_PROPERTY) != null) {
            return;
          }
          Element paragraph = implNodelet.getParentElement();
          if (paragraph != null) {
            implNodelet.setPropertyObject(PARAGRAPH_PROPERTY, paragraph);
            paragraph.addClassName(TASK_COMPLETED_CLASS);
          }
        }
      });
    }

    @Override
    public void onAttributeModified(ContentElement element, String name, String oldValue,
        String newValue) {
      if (CheckConstants.VALUE.equalsIgnoreCase(name)) {
        updateCheckboxDom(element, getChecked(element));
      }
    }

    private void updateCheckboxDom(ContentElement checkbox, boolean isChecked) {
      Element implNodelet = checkbox.getImplNodelet();
      InputElement checkboxElem = (InputElement) implNodelet.getFirstChild();
      checkboxElem.setChecked(isChecked);
      refreshTaskMetadata(checkbox);

      if (isTaskCheckBox(checkbox)) {
        implNodelet.addClassName(TASK_SPAN_CLASS);
        Element paragraph = implNodelet.getParentElement();
        if (paragraph != null) {
          implNodelet.setPropertyObject(PARAGRAPH_PROPERTY, paragraph);
          if (isChecked) {
            paragraph.addClassName(TASK_COMPLETED_CLASS);
          } else if (!hasAnyOtherCheckedTaskSpan(paragraph, implNodelet)) {
            paragraph.removeClassName(TASK_COMPLETED_CLASS);
          }
        }
      }
    }

    private static void refreshTaskMetadata(ContentElement checkbox) {
      if (!isTaskCheckBox(checkbox)) {
        return;
      }
      Element implNodelet = checkbox.getImplNodelet();
      if (implNodelet == null) {
        return;
      }
      implNodelet.addClassName(TASK_SPAN_CLASS);

      Element metadata = getOrCreateTaskMetadataNodelet(implNodelet);
      clearChildren(metadata);

      String assignee = TaskDocumentUtil.getTaskAssignee(checkbox);
      long dueTimestamp = TaskDocumentUtil.getTaskDueTimestamp(checkbox);
      boolean hasDetails = false;

      if (assignee != null) {
        appendPill(metadata, TaskDocumentUtil.formatTaskAssigneeLabel(assignee),
            TASK_PILL_CLASS + " " + TASK_OWNER_PILL_CLASS);
        hasDetails = true;
      }
      if (dueTimestamp > 0) {
        appendPill(metadata, TaskDocumentUtil.formatTaskDueLabel(dueTimestamp),
            TASK_PILL_CLASS + " " + TASK_DUE_PILL_CLASS);
        hasDetails = true;
      }
      if (!hasDetails) {
        appendPill(metadata, "Add details", TASK_PILL_CLASS + " " + TASK_EMPTY_PILL_CLASS);
      }
    }

    private static Element getTaskMetadataNodelet(ContentElement checkbox) {
      Element implNodelet = checkbox == null ? null : checkbox.getImplNodelet();
      return implNodelet == null ? null
          : (Element) implNodelet.getPropertyObject(TASK_METADATA_PROPERTY);
    }

    private static Element getOrCreateTaskMetadataNodelet(Element implNodelet) {
      Element metadata = (Element) implNodelet.getPropertyObject(TASK_METADATA_PROPERTY);
      if (metadata == null) {
        SpanElement span = Document.get().createSpanElement();
        span.setClassName(TASK_METADATA_CLASS);
        implNodelet.appendChild(span);
        implNodelet.setPropertyObject(TASK_METADATA_PROPERTY, span);
        metadata = span;
      }
      return metadata;
    }

    private static void clearChildren(Element element) {
      while (element.getFirstChild() != null) {
        element.removeChild(element.getFirstChild());
      }
    }

    private static void appendPill(Element metadata, String text, String classes) {
      SpanElement pill = Document.get().createSpanElement();
      pill.setClassName(classes);
      pill.setInnerText(text);
      metadata.appendChild(pill);
    }

    @Override
    public void onRemovedFromParent(ContentElement element, ContentElement newParent) {
      if (newParent == null || !isTaskCheckBox(element) || !getChecked(element)) {
        return;
      }
      Element implNodelet = element.getImplNodelet();
      if (implNodelet == null) {
        return;
      }
      Element oldParagraph = (Element) implNodelet.getPropertyObject(PARAGRAPH_PROPERTY);
      if (oldParagraph != null && !hasAnyOtherCheckedTaskSpan(oldParagraph, implNodelet)) {
        oldParagraph.removeClassName(TASK_COMPLETED_CLASS);
      }
    }

    @Override
    public void onAddedToParent(ContentElement element, ContentElement oldParent) {
      if (!isTaskCheckBox(element) || !getChecked(element)) {
        return;
      }
      Element implNodelet = element.getImplNodelet();
      if (implNodelet == null) {
        return;
      }
      Element newParagraph = implNodelet.getParentElement();
      if (newParagraph != null) {
        implNodelet.setPropertyObject(PARAGRAPH_PROPERTY, newParagraph);
        newParagraph.addClassName(TASK_COMPLETED_CLASS);
      }
    }

    @Override
    public void onDeactivated(ContentElement element) {
      if (!isTaskCheckBox(element) || !getChecked(element)) {
        return;
      }
      Element implNodelet = element.getImplNodelet();
      if (implNodelet == null) {
        return;
      }
      Element paragraph = (Element) implNodelet.getPropertyObject(PARAGRAPH_PROPERTY);
      if (paragraph != null && !hasAnyOtherCheckedTaskSpan(paragraph, implNodelet)) {
        paragraph.removeClassName(TASK_COMPLETED_CLASS);
      }
    }

    /**
     * Returns true if {@code paragraph} contains any task checkbox span other than
     * {@code excludeSpan} whose input is currently checked.
     */
    private boolean hasAnyOtherCheckedTaskSpan(Element paragraph, Element excludeSpan) {
      NodeList<Element> spans = DomHelper.getElementsByClassName(paragraph, TASK_SPAN_CLASS);
      for (int i = 0; i < spans.getLength(); i++) {
        Element span = spans.getItem(i);
        if (span.equals(excludeSpan)) {
          continue;
        }
        Element firstChild = span.getFirstChildElement();
        if (firstChild != null && InputElement.as(firstChild).isChecked()) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * @return the value of the checkbox.
   * @param checkbox
   */
  public static boolean getChecked(ContentElement checkbox) {
    Preconditions.checkArgument(isCheckBox(checkbox), "Argument is not a checkbox");
    return Boolean.valueOf(checkbox.getAttribute(CheckConstants.VALUE));
  }

  /**
   * Sets the value of the checkbox
   * @param checkbox
   * @param checkValue
   */
  public static void setChecked(ContentElement checkbox, boolean checkValue) {
    Preconditions.checkArgument(isCheckBox(checkbox), "Argument is not a checkbox");
    checkbox.getMutableDoc().setElementAttribute(checkbox, CheckConstants.VALUE,
        String.valueOf(checkValue));
  }

  public static void refreshTaskMetadata(ContentElement checkbox) {
    CheckBoxRenderingMutationHandler.refreshTaskMetadata(checkbox);
  }

  private static void registerTaskMetadataPopupHandlers(final ContentElement element,
      Element metadataNodelet) {
    Helper.registerJsHandler(element, metadataNodelet, "click",
        new DomHelper.JavaScriptEventListener() {
          @Override
          public void onJavaScriptEvent(String name, Event event) {
            event.preventDefault();
            event.stopPropagation();
            TaskMetadataPopup.show(element);
          }
        });
    Helper.registerJsHandler(element, metadataNodelet, "keydown",
        new DomHelper.JavaScriptEventListener() {
          @Override
          public void onJavaScriptEvent(String name, Event event) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == ' ') {
              event.preventDefault();
              event.stopPropagation();
              TaskMetadataPopup.show(element);
            }
          }
        });
  }

  /**
   * @param element
   * @return true iff the element is a checkbox element
   */
  public static boolean isCheckBox(ContentElement element) {
    return TAGNAME.equalsIgnoreCase(element.getTagName());
  }

  private static boolean isTaskCheckBox(ContentElement element) {
    if (!isCheckBox(element)) {
      return false;
    }
    String name = element.getAttribute(ContentElement.NAME);
    return name != null && name.startsWith(TaskDocumentUtil.TASK_NAME_PREFIX);
  }

  /** Utility class */
  private CheckBox() {
  }
}

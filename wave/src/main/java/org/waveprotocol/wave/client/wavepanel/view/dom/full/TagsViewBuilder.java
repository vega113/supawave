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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import org.waveprotocol.wave.client.uibuilder.HtmlClosureCollection;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.TagMessages;

import org.waveprotocol.wave.client.uibuilder.OutputHelper;

/**
 * UiBuilder for a collection of tags.
 *
 * Ported from Wiab.pro, adapted for Apache Wave (removed KeyComboManager
 * dependency which does not exist upstream).
 */
public final class TagsViewBuilder implements UiBuilder {

  static final class CssConstants {
    static final int PANEL_HEIGHT_PX = 36;
    static final int PANEL_BORDER_TOP_PX = 1;
    static final int PANEL_TOTAL_HEIGHT_PX = PANEL_HEIGHT_PX + PANEL_BORDER_TOP_PX;
    static final String PANEL_HEIGHT_CSS = PANEL_HEIGHT_PX + "px";
    static final String PANEL_TOTAL_HEIGHT_CSS = PANEL_TOTAL_HEIGHT_PX + "px";

    private CssConstants() {
    }
  }

  /** A well-known ID for the add-tag button, used by TagController. */
  public static final String ADD_TAG_BUTTON_ID = "add-tag-button";

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {

    /** CSS */
    @Source("Tags.css")
    Css css();
  }

  /** CSS for this widget. */
  public interface Css extends CssResource {

    String panel();
    String flow();
    String tag();
    String tagLabel();
    String normal();
    String added();
    String removed();
    String title();
    String toggleGroup();
    String simple();
    String extra();
    String expandButton();
    String collapseButton();
    String addButton();
    String addButtonPressed();
    String removeButton();
    String inlineEditor();
    String inlineEditorVisible();
    String inlineInput();
    String inlineActionButton();
    String inlineSubmitButton();
    String inlineCancelButton();
  }

  /** An enum for all the components of a tags view. */
  public enum Components implements Component {

    /** Element to which tag UIs are attached. */
    CONTAINER("C"),
    /** Add button. */
    ADD("A"),
    /** Inline composer wrapper. */
    INLINE_EDITOR("E"),
    /** Inline composer input. */
    INLINE_INPUT("I"),
    /** Inline composer submit button. */
    INLINE_SUBMIT("S"),
    /** Inline composer cancel button. */
    INLINE_CANCEL("X");

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  private static final TagMessages messages = GWT.create(TagMessages.class);

  /**
   * Creates a new TagsViewBuilder.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   * @param tagUis collection of tag UiBuilders
   */
  public static TagsViewBuilder create(String id, HtmlClosureCollection tagUis) {
    return new TagsViewBuilder(
        WavePanelResourceLoader.getTags().css(), id, tagUis);
  }

  private final Css css;
  private final HtmlClosureCollection tagUis;
  private final String id;

  TagsViewBuilder(Css css, String id, HtmlClosureCollection tagUis) {
    this.css = css;
    this.id = id;
    this.tagUis = tagUis;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    OutputHelper.openWith(output, id, css.panel(), TypeCodes.kind(Type.TAGS),
        "data-mobile-role='wave-tags'");
    {
      OutputHelper.open(output, null, css.flow(), null);
      {
        OutputHelper.open(output, Components.CONTAINER.getDomId(id), null, null);
        {
          // Append title
          OutputHelper.open(output, null, css.title(), null);
          output.appendPlainText(messages.tags());
          OutputHelper.close(output);

          tagUis.outputHtml(output);
          appendInlineEditor(output);

          // Overflow-mode panel.
          OutputHelper.openSpan(output, null, css.extra(), null);
          {
            OutputHelper.openSpanWith(output, null, css.toggleGroup(), null,
                "onclick=\"" + onClickJs() + "\"");
            {
              OutputHelper.appendSpan(output, null, css.expandButton(), null);
              OutputHelper.openSpan(output, null, null, null);
              {
                output.appendPlainText(messages.more());
              }
              OutputHelper.closeSpan(output);
            }
            OutputHelper.closeSpan(output);
            OutputHelper.appendSpanWith(output, null, css.addButton(),
                TypeCodes.kind(Type.ADD_TAG), null, messages.addTagHint(), null);
          }
          OutputHelper.closeSpan(output);

          // Single-line mode panel.
          OutputHelper.openSpan(output, null, css.simple(), null);
          {
            OutputHelper.appendSpanWith(output, ADD_TAG_BUTTON_ID, css.addButton(),
                TypeCodes.kind(Type.ADD_TAG), null, messages.addTagHint(), null);
          }
          OutputHelper.closeSpan(output);
        }
        OutputHelper.close(output);
      }
      OutputHelper.close(output);
    }
    OutputHelper.close(output);
  }

  /**
   * Rather than install a regular handler, this is an experiment at injecting
   * JS directly, so that this piece of UI is functional from the initial
   * rendering, without needing to wait for any scripts to load (like the GWT
   * app).
   *
   * @return a JS click handler for toggling expanded and collapsed modes.
   */
  private String onClickJs() {
    String escapedId = escapeJsSingleQuoted(id);
    String more = escapeJsSingleQuoted(messages.more());
    String less = escapeJsSingleQuoted(messages.less());
    StringBuilder sb = new StringBuilder();
    sb.append("var p=document.getElementById('").append(escapedId).append("');")
        .append("var x=p.getAttribute('s')=='e';")
        .append("p.style.height=x?'':'auto';")
        .append("p.setAttribute('s',x?'':'e');")
        .append("lastChild.innerText=x?'").append(more)
        .append("':'").append(less).append("';")
        .append("firstChild.className=x?'").append(css.expandButton())
        .append("':'").append(css.collapseButton()).append("';")
        .append("parentNode.nextSibling.style.display=x?'':'none';");
    String js = escapeHtmlAttribute(sb.toString());
    assert !js.contains("\"");
    return js;
  }

  private static String escapeJsSingleQuoted(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private void appendInlineEditor(SafeHtmlBuilder output) {
    OutputHelper.openSpan(output, Components.INLINE_EDITOR.getDomId(id), css.inlineEditor(), null);
    {
      output.appendHtmlConstant("<input"
          + " type='text'"
          + " id='" + escapeHtmlAttribute(Components.INLINE_INPUT.getDomId(id)) + "'"
          + " class='" + escapeHtmlAttribute(css.inlineInput()) + "'"
          + " placeholder='" + escapeHtmlAttribute(messages.tagInputPlaceholder()) + "'"
          + " title='" + escapeHtmlAttribute(messages.tagInputHint()) + "'"
          + " aria-label='" + escapeHtmlAttribute(messages.tagInputPlaceholder()) + "'"
          + " autocomplete='off'"
          + " spellcheck='false'"
          + " />");
      OutputHelper.button(
          output,
          Components.INLINE_SUBMIT.getDomId(id),
          css.inlineActionButton() + " " + css.inlineSubmitButton(),
          null,
          messages.confirmAddTagHint(),
          messages.confirmAddTagHint(),
          "");
      OutputHelper.button(
          output,
          Components.INLINE_CANCEL.getDomId(id),
          css.inlineActionButton() + " " + css.inlineCancelButton(),
          null,
          messages.cancelAddTagHint(),
          messages.cancelAddTagHint(),
          "");
    }
    OutputHelper.closeSpan(output);
  }

  private static String escapeHtmlAttribute(String value) {
    return value
        .replace("&", "&amp;")
        .replace("'", "&#39;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}

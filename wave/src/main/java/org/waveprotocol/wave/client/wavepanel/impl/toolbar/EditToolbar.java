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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.DOM;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.EditorContextAdapter;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph.LineStyle;
import org.waveprotocol.wave.client.editor.toolbar.ButtonUpdater;
import org.waveprotocol.wave.client.editor.toolbar.ParagraphApplicationController;
import org.waveprotocol.wave.client.editor.toolbar.ParagraphTraversalController;
import org.waveprotocol.wave.client.editor.toolbar.TextSelectionController;
import org.waveprotocol.wave.client.editor.util.EditorAnnotationUtil;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.ClipboardImageUploader;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.color.ColorHelper;
import org.waveprotocol.wave.client.wavepanel.impl.edit.TaskMetadataPopup;
import org.waveprotocol.wave.client.wavepanel.view.AttachmentPopupView;
import org.waveprotocol.wave.client.wavepanel.view.AttachmentPopupView.Listener;
import org.waveprotocol.wave.client.widget.toolbar.SubmenuToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarToggleButton;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.media.model.AttachmentIdGenerator;
import org.waveprotocol.wave.media.model.AttachmentIdGeneratorImpl;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;
import org.waveprotocol.wave.client.editor.content.FocusedContentRange;
import org.waveprotocol.wave.client.doodad.attachment.ImageThumbnail;
import org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailWrapper;
import org.waveprotocol.wave.client.doodad.form.check.TaskDocumentUtil;

/**
 * Attaches actions that can be performed in a Wave's "edit mode" to a toolbar.
 * <p>
 * Also constructs an initial set of such actions.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public class EditToolbar {

  /**
   * Handler for click buttons added with {@link EditToolbar#addClickButton}.
   */
  public interface ClickHandler {
    void onClicked(EditorContext context);
  }

  /**
   * Container for a font family.
   */
  private static final class FontFamily {
    public final String description;
    public final String style;
    public FontFamily(String description, String style) {
      this.description = description;
      this.style = style;
    }
  }

  /**
   * Container for an alignment.
   */
  private static final class Alignment {
    public final String description;
    public final LineStyle style;
    public Alignment(String description, LineStyle style) {
      this.description = description;
      this.style = style;
    }
  }

  // Inline SVG icon constants (Lucide-inspired, clean rounded strokes).
  // 18px display size, 24-unit viewBox, 1.75 stroke for refined weight.
  // Consistent with ViewToolbar icon style.
  private static final String SVG_OPEN =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
      + "stroke-width=\"1.75\" stroke-linecap=\"round\" stroke-linejoin=\"round\" "
      + "style=\"display:block\">";

  /** Bold: B letter form. */
  private static final String ICON_BOLD = SVG_OPEN
      + "<path d=\"M6 4h8a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z\"></path>"
      + "<path d=\"M6 12h9a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z\"></path></svg>";

  /** Italic: slanted I. */
  private static final String ICON_ITALIC = SVG_OPEN
      + "<path d=\"M19 4h-9\"></path>"
      + "<path d=\"M14 20H5\"></path>"
      + "<path d=\"M15 4L9 20\"></path></svg>";

  /** Underline: U with baseline. */
  private static final String ICON_UNDERLINE = SVG_OPEN
      + "<path d=\"M6 3v7a6 6 0 0 0 6 6 6 6 0 0 0 6-6V3\"></path>"
      + "<path d=\"M4 21h16\"></path></svg>";

  /** Strikethrough: S with strike line. */
  private static final String ICON_STRIKETHROUGH = SVG_OPEN
      + "<path d=\"M16 4H9a3 3 0 0 0-2.83 4\"></path>"
      + "<path d=\"M14 12a4 4 0 0 1 0 8H6\"></path>"
      + "<path d=\"M4 12h16\"></path></svg>";

  /** Superscript: X with raised 2. */
  private static final String ICON_SUPERSCRIPT = SVG_OPEN
      + "<path d=\"m4 19 8-8\"></path>"
      + "<path d=\"m12 19-8-8\"></path>"
      + "<path d=\"M20 12h-4c0-1.5.44-2 1.5-2.5S20 8.33 20 7c0-1.11-.67-2-2-2"
      + "-1.1 0-1.73.5-2 1\"></path></svg>";

  /** Subscript: X with lowered 2. */
  private static final String ICON_SUBSCRIPT = SVG_OPEN
      + "<path d=\"m4 5 8 8\"></path>"
      + "<path d=\"m12 5-8 8\"></path>"
      + "<path d=\"M20 19h-4c0-1.5.44-2 1.5-2.5S20 15.33 20 14c0-1.11-.67-2-2-2"
      + "-1.1 0-1.73.5-2 1\"></path></svg>";

  /** Font size: large A / small A. */
  private static final String ICON_FONT_SIZE = SVG_OPEN
      + "<path d=\"M3.5 21L8 7l4.5 14\"></path>"
      + "<path d=\"M5.5 16h5\"></path>"
      + "<path d=\"M16 16l2.5-6 2.5 6\"></path>"
      + "<path d=\"M17 14h4\"></path></svg>";

  /** Font family: type / T letterform. */
  private static final String ICON_FONT_FAMILY = SVG_OPEN
      + "<path d=\"M4 7V4h16v3\"></path>"
      + "<path d=\"M9 20h6\"></path>"
      + "<path d=\"M12 4v16\"></path></svg>";

  /** Heading: H letterform. */
  private static final String ICON_HEADING = SVG_OPEN
      + "<path d=\"M6 12h12\"></path>"
      + "<path d=\"M6 20V4\"></path>"
      + "<path d=\"M18 20V4\"></path></svg>";

  /** Font color: A with colored underline bar. */
  private static final String ICON_COLOR = SVG_OPEN
      + "<path d=\"M4 20h16\"></path>"
      + "<path d=\"M7 16l5-12 5 12\"></path>"
      + "<path d=\"M9 11h6\"></path></svg>";

  /** Background color: paint bucket / highlighter. */
  private static final String ICON_BACKCOLOR = SVG_OPEN
      + "<path d=\"m19 11-8-8-8.6 8.6a2 2 0 0 0 0 2.8l5.2 5.2c.8.8 2 .8 2.8 0L19 11z\"></path>"
      + "<path d=\"m5 2 5 5\"></path>"
      + "<path d=\"M2 13h15\"></path>"
      + "<path d=\"M22 20a2 2 0 1 1-4 0c0-1.6 2-3 2-3s2 1.4 2 3z\"></path></svg>";

  /** Indent: increase indent. */
  private static final String ICON_INDENT = SVG_OPEN
      + "<path d=\"M3 8h18\"></path>"
      + "<path d=\"M7 12h14\"></path>"
      + "<path d=\"M7 16h14\"></path>"
      + "<path d=\"M3 12l3 2-3 2\"></path></svg>";

  /** Outdent: decrease indent. */
  private static final String ICON_OUTDENT = SVG_OPEN
      + "<path d=\"M3 8h18\"></path>"
      + "<path d=\"M7 12h14\"></path>"
      + "<path d=\"M7 16h14\"></path>"
      + "<path d=\"M7 12l-3 2 3 2\"></path></svg>";

  /** Unordered list: bullet list. */
  private static final String ICON_UNORDERED_LIST = SVG_OPEN
      + "<path d=\"M8 6h13\"></path>"
      + "<path d=\"M8 12h13\"></path>"
      + "<path d=\"M8 18h13\"></path>"
      + "<circle cx=\"3\" cy=\"6\" r=\"1\" fill=\"currentColor\" stroke=\"none\"></circle>"
      + "<circle cx=\"3\" cy=\"12\" r=\"1\" fill=\"currentColor\" stroke=\"none\"></circle>"
      + "<circle cx=\"3\" cy=\"18\" r=\"1\" fill=\"currentColor\" stroke=\"none\"></circle></svg>";

  /** Ordered list: numbered list. */
  private static final String ICON_ORDERED_LIST = SVG_OPEN
      + "<path d=\"M10 6h11\"></path>"
      + "<path d=\"M10 12h11\"></path>"
      + "<path d=\"M10 18h11\"></path>"
      + "<path d=\"M4 6h1v4\"></path>"
      + "<path d=\"M4 10h2\"></path>"
      + "<path d=\"M6 18H4c0-1 2-2 2-3s-1-1.5-2-1\"></path></svg>";

  /** Align left. */
  private static final String ICON_ALIGN_LEFT = SVG_OPEN
      + "<path d=\"M21 6H3\"></path>"
      + "<path d=\"M15 12H3\"></path>"
      + "<path d=\"M17 18H3\"></path></svg>";

  /** Align center. */
  private static final String ICON_ALIGN_CENTER = SVG_OPEN
      + "<path d=\"M21 6H3\"></path>"
      + "<path d=\"M17 12H7\"></path>"
      + "<path d=\"M19 18H5\"></path></svg>";

  /** Align right. */
  private static final String ICON_ALIGN_RIGHT = SVG_OPEN
      + "<path d=\"M21 6H3\"></path>"
      + "<path d=\"M21 12H9\"></path>"
      + "<path d=\"M21 18H7\"></path></svg>";

  /** RTL direction: right-to-left text. */
  private static final String ICON_RTL = SVG_OPEN
      + "<path d=\"M16 4H9.5a3.5 3.5 0 1 0 0 7H13\"></path>"
      + "<path d=\"M13 4v16\"></path>"
      + "<path d=\"M16 4v16\"></path>"
      + "<path d=\"M2 18l3-3-3-3\"></path></svg>";

  /** Clear formatting: eraser. */
  private static final String ICON_CLEAR_FORMATTING = SVG_OPEN
      + "<path d=\"M4 7h16\"></path>"
      + "<path d=\"M7 4v6\"></path>"
      + "<path d=\"M17 4v6\"></path>"
      + "<path d=\"M10 20h4\"></path>"
      + "<path d=\"M12 14v6\"></path>"
      + "<path d=\"M2 14l5 5\"></path>"
      + "<path d=\"M7 14l-5 5\"></path></svg>";

  /** Insert link: chain link. */
  private static final String ICON_LINK = SVG_OPEN
      + "<path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path>"
      + "<path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\"></path></svg>";

  /** Remove link: unlink. */
  private static final String ICON_UNLINK = SVG_OPEN
      + "<path d=\"m18.84 12.25 1.72-1.71a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path>"
      + "<path d=\"m5.16 11.75-1.72 1.71a5 5 0 0 0 7.07 7.07l1.72-1.71\"></path>"
      + "<path d=\"M8 2v3\"></path>"
      + "<path d=\"M2 8h3\"></path>"
      + "<path d=\"M16 22v-3\"></path>"
      + "<path d=\"M22 16h-3\"></path></svg>";

  /** Insert attachment: paperclip. */
  private static final String ICON_ATTACHMENT = SVG_OPEN
      + "<path d=\"m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1"
      + " 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48\"></path></svg>";

  /** Insert task: checkbox / square check. */
  private static final String ICON_TASK = SVG_OPEN
      + "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\"></rect>"
      + "<path d=\"m9 12 2 2 4-4\"></path></svg>";

  /**
   * Creates an icon element from inline SVG markup for use in toolbar buttons.
   * Wrapper div has toolbar-svg-icon class for CSS hover/transition effects.
   */
  private static Element createSvgIcon(String svgHtml) {
    Element wrapper = DOM.createDiv();
    wrapper.setClassName("toolbar-svg-icon");
    wrapper.setInnerHTML(svgHtml);
    return wrapper;
  }

  private final ToplevelToolbarWidget toolbarUi;
  private final ParticipantId user;
  private final AttachmentIdGenerator attachmentIdGenerator;

  /** The id of the currently loaded wave. */
  private WaveId waveId;

  private final EditorContextAdapter editor = new EditorContextAdapter(null);
  private final ButtonUpdater updater = new ButtonUpdater(editor);

  private EditToolbar(ToplevelToolbarWidget toolbarUi,
      ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
    this.toolbarUi = toolbarUi;
    this.user = user;
    this.waveId = waveId;
    attachmentIdGenerator = new AttachmentIdGeneratorImpl(idGenerator);
    editor.setImagePasteHandler(
        new ClipboardImageUploader(attachmentIdGenerator, waveId, editor));
  }

  /**
   * Attaches editor behaviour to a toolbar, adding all the edit buttons.
   */
  public static EditToolbar create(ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
    ToplevelToolbarWidget toolbarUi = new ToplevelToolbarWidget();
    return new EditToolbar(toolbarUi, user, idGenerator, waveId);
  }

  /** Constructs the initial set of actions in the toolbar. */
  public void init() {
    ToolbarView group = toolbarUi.addGroup();
    createBoldButton(group);
    createItalicButton(group);
    createUnderlineButton(group);
    createStrikethroughButton(group);

    group = toolbarUi.addGroup();
    createSuperscriptButton(group);
    createSubscriptButton(group);

    group = toolbarUi.addGroup();
    createFontSizeButton(group);
    createFontFamilyButton(group);
    createHeadingButton(group);

    group = toolbarUi.addGroup();
    createFontColorButton(group);
    createFontBackColorButton(group);

    group = toolbarUi.addGroup();
    createIndentButton(group);
    createOutdentButton(group);

    group = toolbarUi.addGroup();
    createUnorderedListButton(group);
    createOrderedListButton(group);

    group = toolbarUi.addGroup();
    createAlignButtons(group);
    createRtlDirectionButton(group);
    createClearFormattingButton(group);

    group = toolbarUi.addGroup();
    createInsertLinkButton(group);
    createRemoveLinkButton(group);

    group = toolbarUi.addGroup();
    createInsertAttachmentButton(group, user);

    group = toolbarUi.addGroup();
    createInsertTaskButton(group);
  }

  private void createBoldButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Bold")
        .applyTo(b, createTextSelectionController(b, "fontWeight", "bold"));
    b.setE2eAction("bold");
    b.setVisualElement(createSvgIcon(ICON_BOLD));
  }

  private void createItalicButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Italic")
        .applyTo(b, createTextSelectionController(b, "fontStyle", "italic"));
    b.setVisualElement(createSvgIcon(ICON_ITALIC));
  }

  private void createUnderlineButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Underline")
        .applyTo(b, createTextSelectionController(b, "textDecoration", "underline"));
    b.setVisualElement(createSvgIcon(ICON_UNDERLINE));
  }

  private void createFontBackColorButton(ToolbarView toolbar) {
    final ToolbarClickButton button = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Highlight color")
        .applyTo(button, new ToolbarClickButton.Listener() {
          @Override  public void onClicked() {
            ColorHelper.onSetBackColor(editor, button);
          }
        });
    button.setVisualElement(createSvgIcon(ICON_BACKCOLOR));
  }

  private void createFontColorButton(ToolbarView toolbar) {
    final ToolbarClickButton button = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Text color")
        .applyTo(button, new ToolbarClickButton.Listener() {
          @Override  public void onClicked() {
            ColorHelper.onSetColor(editor, button);
          }
        });
    button.setVisualElement(createSvgIcon(ICON_COLOR));
  }

  private void createStrikethroughButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Strikethrough")
        .applyTo(b, createTextSelectionController(b, "textDecoration", "line-through"));
    b.setVisualElement(createSvgIcon(ICON_STRIKETHROUGH));
  }

  private void createSuperscriptButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Superscript")
        .applyTo(b, createTextSelectionController(b, "verticalAlign", "super"));
    b.setVisualElement(createSvgIcon(ICON_SUPERSCRIPT));
  }

  private void createSubscriptButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Subscript")
        .applyTo(b, createTextSelectionController(b, "verticalAlign", "sub"));
    b.setVisualElement(createSvgIcon(ICON_SUBSCRIPT));
  }

  private void createFontSizeButton(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder()
        .setTooltip("Font size")
        .applyTo(submenu, null);
    submenu.setVisualElement(createSvgIcon(ICON_FONT_SIZE));
    submenu.setShowDropdownArrow(true);
    // TODO(kalman): default text size option.
    ToolbarView group = submenu.addGroup();
    for (int size : asArray(8, 9, 10, 11, 12, 14, 16, 18, 21, 24, 28, 32, 36, 42, 48, 56, 64, 72)) {
      ToolbarToggleButton b = group.addToggleButton();
      double baseSize = 12.0;
      b.setVisualElement(createFontSizeElement(baseSize, size));
      b.setListener(createTextSelectionController(b, "fontSize", (size / baseSize) + "em"));
    }
  }

  private Element createFontSizeElement(double baseSize, double size) {
    Element e = Document.get().createSpanElement();
    e.getStyle().setFontSize(size / baseSize, Unit.EM);
    e.setInnerText(((int) size) + "");
    return e;
  }

  private void createFontFamilyButton(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder()
        .setTooltip("Font family")
        .applyTo(submenu, null);
    submenu.setVisualElement(createSvgIcon(ICON_FONT_FAMILY));
    submenu.setShowDropdownArrow(true);
    createFontFamilyGroup(submenu.addGroup(), new FontFamily("Default", null));
    createFontFamilyGroup(submenu.addGroup(),
        new FontFamily("Sans Serif", "sans-serif"),
        new FontFamily("Serif", "serif"),
        new FontFamily("Wide", "arial black,sans-serif"),
        new FontFamily("Narrow", "arial narrow,sans-serif"),
        new FontFamily("Fixed Width", "monospace"));
    createFontFamilyGroup(submenu.addGroup(),
        new FontFamily("Arial", "arial,helvetica,sans-serif"),
        new FontFamily("Comic Sans MS", "comic sans ms,sans-serif"),
        new FontFamily("Courier New", "courier new,monospace"),
        new FontFamily("Garamond", "garamond,serif"),
        new FontFamily("Georgia", "georgia,serif"),
        new FontFamily("Tahoma", "tahoma,sans-serif"),
        new FontFamily("Times New Roman", "times new roman,serif"),
        new FontFamily("Trebuchet MS", "trebuchet ms,sans-serif"),
        new FontFamily("Verdana", "verdana,sans-serif"));
  }

  private void createFontFamilyGroup(ToolbarView toolbar, FontFamily... families) {
    for (FontFamily family : families) {
      ToolbarToggleButton b = toolbar.addToggleButton();
      b.setVisualElement(createFontFamilyElement(family));
      b.setListener(createTextSelectionController(b, "fontFamily", family.style));
    }
  }

  private Element createFontFamilyElement(FontFamily family) {
    Element e = Document.get().createSpanElement();
    e.getStyle().setProperty("fontFamily", family.style);
    e.setInnerText(family.description);
    return e;
  }

  private void createClearFormattingButton(ToolbarView toolbar) {
    ToolbarClickButton btn = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Clear formatting")
        .applyTo(btn, new ToolbarClickButton.Listener() {
          @Override public void onClicked() {
            EditorAnnotationUtil.clearAnnotationsOverSelection(editor, asArray(
                StyleAnnotationHandler.key("backgroundColor"),
                StyleAnnotationHandler.key("color"),
                StyleAnnotationHandler.key("fontFamily"),
                StyleAnnotationHandler.key("fontSize"),
                StyleAnnotationHandler.key("fontStyle"),
                StyleAnnotationHandler.key("fontWeight"),
                StyleAnnotationHandler.key("textDecoration"),
                StyleAnnotationHandler.key("verticalAlign")
                // NOTE: add more as required.
            ));
            createClearHeadingsListener().onClicked();
          }
        });
    btn.setVisualElement(createSvgIcon(ICON_CLEAR_FORMATTING));
  }

  private void createInsertAttachmentButton(ToolbarView toolbar, final ParticipantId user) {
    WaveRef waveRef = WaveRef.of(waveId);
    Preconditions.checkState(waveRef != null, "waveRef != null");
    final String waveRefToken = URL.encode(GwtWaverefEncoder.encodeToUriQueryString(waveRef));

    ToolbarClickButton attachBtn = toolbar.addClickButton();
    new ToolbarButtonViewBuilder().setTooltip("Insert attachment")
        .applyTo(attachBtn, new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            AttachmentPopupView attachmentView = new AttachmentPopupWidget();
            attachmentView.init(new Listener() {

              @Override
              public void onShow() {
              }

              @Override
              public void onHide() {
              }

              @Override
              public AttachmentId requestNewAttachmentId() {
                return attachmentIdGenerator.newAttachmentId();
              }

              @Override
              public void onDone(String encodedWaveRef, String attachmentId, String fullFileName) {
                onDoneWithSizeAndCaption(encodedWaveRef, attachmentId, fullFileName, "small", "");
              }

              @Override
              public void onDoneWithSize(String encodedWaveRef, String attachmentId,
                  String fullFileName, String displaySize) {
                onDoneWithSizeAndCaption(encodedWaveRef, attachmentId, fullFileName, displaySize, "");
              }

              @Override
              public void onDoneWithSizeAndCaption(String encodedWaveRef, String attachmentId,
                  String fullFileName, String displaySize, String caption) {
                int lastSlashPos = fullFileName.lastIndexOf("/");
                int lastBackSlashPos = fullFileName.lastIndexOf("\\");
                String fileName = fullFileName;
                if (lastSlashPos != -1) {
                  fileName = fullFileName.substring(lastSlashPos + 1);
                } else if (lastBackSlashPos != -1) {
                  fileName = fullFileName.substring(lastBackSlashPos + 1);
                }
                // Use caption if provided, otherwise fall back to filename
                String captionText = (caption != null && !caption.trim().isEmpty())
                    ? caption.trim() : fileName;

                CMutableDocument doc = editor.getDocument();
                FocusedContentRange selection = editor.getSelectionHelper().getSelectionPoints();
                Point<ContentNode> point;
                if (selection != null) {
                  point = selection.getFocus();
                } else {
                  editor.focus(false);
                  selection = editor.getSelectionHelper().getSelectionPoints();
                  if (selection != null) {
                    point = selection.getFocus();
                  } else {
                    point = doc.locate(doc.size() - 1);
                  }
                }
                XmlStringBuilder content = ImageThumbnail.constructXmlWithSize(
                    attachmentId, displaySize, captionText);
                ImageThumbnailWrapper thumbnail = ImageThumbnailWrapper.of(
                    doc.insertXml(point, content));
                thumbnail.setAttachmentId(attachmentId);
              }
            });

            attachmentView.setWaveRef(waveRefToken);
            attachmentView.show();
          }
        });
    attachBtn.setVisualElement(createSvgIcon(ICON_ATTACHMENT));
  }

  private void createInsertTaskButton(ToolbarView toolbar) {
    ToolbarClickButton taskBtn = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Insert task")
        .applyTo(taskBtn, new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            CMutableDocument doc = editor.getDocument();
            if (doc == null) {
              return;
            }
            FocusedContentRange selection = editor.getSelectionHelper().getSelectionPoints();
            Point<ContentNode> point;
            if (selection != null) {
              point = selection.getFocus();
            } else {
              // Focus was probably lost.  Bring it back.
              editor.focus(false);
              selection = editor.getSelectionHelper().getSelectionPoints();
              if (selection != null) {
                point = selection.getFocus();
              } else {
                // Still no selection.  Put it at the end.
                point = doc.locate(doc.size() - 1);
              }
            }
            String taskId = TaskDocumentUtil.generateTaskId();
            ContentElement inserted = TaskDocumentUtil.insertTask(doc, point, taskId, user.getAddress());
            TaskMetadataPopup.show(inserted);
          }
        });
    taskBtn.setVisualElement(createSvgIcon(ICON_TASK));
  }

  private void createRtlDirectionButton(ToolbarView toolbar) {
    ToolbarToggleButton rtlButton = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Right-to-left text direction")
        .applyTo(rtlButton, createParagraphApplicationController(rtlButton, Paragraph.Direction.RTL));
    rtlButton.setVisualElement(createSvgIcon(ICON_RTL));
  }

  private void createInsertLinkButton(ToolbarView toolbar) {
    ToolbarClickButton linkBtn = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Insert link")
        .applyTo(linkBtn, new ToolbarClickButton.Listener() {
              @Override  public void onClicked() {
                LinkerHelper.onCreateLink(editor);
              }
            });
    linkBtn.setVisualElement(createSvgIcon(ICON_LINK));
  }

  private void createRemoveLinkButton(ToolbarView toolbar) {
    ToolbarClickButton unlinkBtn = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Remove link")
        .applyTo(unlinkBtn, new ToolbarClickButton.Listener() {
          @Override public void onClicked() {
            LinkerHelper.onClearLink(editor);
          }
        });
    unlinkBtn.setVisualElement(createSvgIcon(ICON_UNLINK));
  }

  private ToolbarClickButton.Listener createClearHeadingsListener() {
    return new ParagraphTraversalController(editor, new ContentElement.Action() {
        @Override public void execute(ContentElement e) {
          e.getMutableDoc().setElementAttribute(e, Paragraph.SUBTYPE_ATTR, null);
        }
      });
  }

  private void createHeadingButton(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder()
        .setTooltip("Heading")
        .applyTo(submenu, null);
    submenu.setVisualElement(createSvgIcon(ICON_HEADING));
    submenu.setShowDropdownArrow(true);
    ToolbarClickButton defaultButton = submenu.addClickButton();
    new ToolbarButtonViewBuilder()
        .setText("Default")
        .applyTo(defaultButton, createClearHeadingsListener());
    ToolbarView group = submenu.addGroup();
    for (int level : asArray(1, 2, 3, 4)) {
      ToolbarToggleButton b = group.addToggleButton();
      b.setVisualElement(createHeadingElement(level));
      b.setListener(createParagraphApplicationController(b, Paragraph.regularStyle("h" + level)));
    }
  }

  private Element createHeadingElement(int level) {
    Element e = Document.get().createElement("h" + level);
    e.getStyle().setMarginTop(2, Unit.PX);
    e.getStyle().setMarginBottom(2, Unit.PX);
    e.setInnerText("Heading " + level);
    return e;
  }

  private void createIndentButton(ToolbarView toolbar) {
    ToolbarClickButton b = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Increase indent")
        .applyTo(b, new ParagraphTraversalController(editor, Paragraph.INDENTER));
    b.setVisualElement(createSvgIcon(ICON_INDENT));
  }

  private void createOutdentButton(ToolbarView toolbar) {
    ToolbarClickButton b = toolbar.addClickButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Decrease indent")
        .applyTo(b, new ParagraphTraversalController(editor, Paragraph.OUTDENTER));
    b.setVisualElement(createSvgIcon(ICON_OUTDENT));
  }

  private void createUnorderedListButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Bullet list")
        .applyTo(b, createParagraphApplicationController(b, Paragraph.listStyle(null)));
    b.setVisualElement(createSvgIcon(ICON_UNORDERED_LIST));
  }

  private void createOrderedListButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Numbered list")
        .applyTo(b, createParagraphApplicationController(
            b, Paragraph.listStyle(Paragraph.LIST_STYLE_DECIMAL)));
    b.setVisualElement(createSvgIcon(ICON_ORDERED_LIST));
  }

  private void createAlignButtons(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder()
        .setTooltip("Text alignment")
        .applyTo(submenu, null);
    submenu.setVisualElement(createSvgIcon(ICON_ALIGN_LEFT));
    submenu.setShowDropdownArrow(true);
    ToolbarView group = submenu.addGroup();
    String[] alignIcons = {ICON_ALIGN_LEFT, ICON_ALIGN_CENTER, ICON_ALIGN_RIGHT};
    Alignment[] alignments = asArray(
        new Alignment("Left", Paragraph.Alignment.LEFT),
        new Alignment("Centre", Paragraph.Alignment.CENTER),
        new Alignment("Right", Paragraph.Alignment.RIGHT));
    for (int i = 0; i < alignments.length; i++) {
      Alignment alignment = alignments[i];
      ToolbarToggleButton b = group.addToggleButton();
      new ToolbarButtonViewBuilder()
          .setText(alignment.description)
          .applyTo(b, createParagraphApplicationController(b, alignment.style));
      b.setVisualElement(createSvgIcon(alignIcons[i]));
    }
  }

  /**
   * Adds a button to this toolbar.
   */
  public void addClickButton(String icon, final ClickHandler handler) {
    ToolbarClickButton.Listener uiHandler = new ToolbarClickButton.Listener() {
      @Override
      public void onClicked() {
        handler.onClicked(editor);
      }
    };
    new ToolbarButtonViewBuilder().setIcon(icon).applyTo(toolbarUi.addClickButton(), uiHandler);
  }

  /**
   * Starts listening to editor changes.
   *
   * @throws IllegalStateException if this toolbar is already enabled
   * @throws IllegalArgumentException if the editor is <code>null</code>
   */
  public void enable(Editor editor) {
    this.editor.checkEditor(null);
    Preconditions.checkArgument(editor != null, "editor != null");
    this.editor.switchEditor(editor);
    editor.addUpdateListener(updater);
    updater.updateButtonStates();
  }

  /**
   * Stops listening to editor changes.
   *
   * @throws IllegalStateException if this toolbar is not currently enabled
   * @throws IllegalArgumentException if this toolbar is currently enabled for a
   *         different editor
   */
  public void disable(Editor editor) {
    this.editor.checkEditor(editor);
    // The above won't throw if we're not currently enabled, but it makes sure
    // 'editor' is the same as the current editor, if any. So if 'editor' is
    // null, it means we aren't enabled (the wrapped editor is null too).
    Preconditions.checkState(editor != null, "editor != null");
    editor.removeUpdateListener(updater);
    this.editor.switchEditor(null);
  }

  /**
   * @return the {@link ToplevelToolbarWidget} backing this toolbar.
   */
  public ToplevelToolbarWidget getWidget() {
    return toolbarUi;
  }

  private ToolbarToggleButton.Listener createParagraphApplicationController(ToolbarToggleButton b,
      LineStyle style) {
    return updater.add(new ParagraphApplicationController(b, editor, style));
  }

  private ToolbarToggleButton.Listener createTextSelectionController(ToolbarToggleButton b,
      String styleName, String value) {
    return updater.add(new TextSelectionController(b, editor,
        StyleAnnotationHandler.key(styleName), value));
  }

  @SuppressWarnings("unchecked")
  private static <E> E[] asArray(E... elements) {
    return elements;
  }
}

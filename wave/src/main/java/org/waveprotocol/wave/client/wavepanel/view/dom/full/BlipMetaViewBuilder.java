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

import static org.waveprotocol.wave.client.uibuilder.BuilderHelper.nonNull;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.image;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openWith;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpanWith;

import org.waveprotocol.wave.model.util.Preconditions;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.impl.collapse.MobileDetector;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.StringMap;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 */
public final class BlipMetaViewBuilder implements UiBuilder, IntrinsicBlipMetaView {


  /** An enum for all the components of a blip view. */
  public enum Components implements Component {
    /** The avatar element. */
    AVATAR("A"),
    /** The text inside the information bar. */
    METALINE("M"),
    /** The element for the information bar. */
    METABAR("B"),
    /** The element containing the time text. */
    TIME("T"),
    /** The element containing the document. */
    CONTENT("C"),
    /** The element containing menu options. */
    MENU("N"),
    /** The element containing draft-mode controls. */
    DRAFTMODECONTROLS("E"), ;

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  // The consistent iterator ordering of EnumMap is relied upon, to ensure that
  // the same menu options are always rendered in the same order.
  private final static Map<MenuOption, SafeHtml> MENU_CODES =
      new EnumMap<MenuOption, SafeHtml>(MenuOption.class);

  private final static Map<MenuOption, SafeHtml> MENU_LABELS =
      new EnumMap<MenuOption, SafeHtml>(MenuOption.class);

  /** Inline SVG icon markup for each menu option. */
  private final static Map<MenuOption, SafeHtml> MENU_ICONS =
      new EnumMap<MenuOption, SafeHtml>(MenuOption.class);

  private final static StringMap<MenuOption> MENU_OPTIONS = CollectionUtils.createStringMap();

  /**
   * Inline SVG icons (Lucide, MIT) — 16x16, stroke-based.
   * Note: self-closing tags (e.g. {@code <path/>}) are avoided because the
   * existing test suite asserts that rendered HTML contains no {@code />}.
   * Instead we use explicit close tags ({@code <path></path>}).
   */
  private static final String SVG_OPEN =
      "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" "
      + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
      + "stroke-linejoin=\"round\">";

  private static final String REPLY_SVG = SVG_OPEN
      + "<polyline points=\"9 17 4 12 9 7\"></polyline>"
      + "<path d=\"M20 18v-2a4 4 0 00-4-4H4\"></path></svg>";

  private static final String EDIT_SVG = SVG_OPEN
      + "<path d=\"M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7\"></path>"
      + "<path d=\"M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z\"></path></svg>";

  private static final String DELETE_SVG = SVG_OPEN
      + "<polyline points=\"3 6 5 6 21 6\"></polyline>"
      + "<path d=\"M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2\">"
      + "</path></svg>";

  private static final String LINK_SVG = SVG_OPEN
      + "<path d=\"M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71\"></path>"
      + "<path d=\"M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71\"></path></svg>";

  private static final String DRAFT_SVG = SVG_OPEN
      + "<path d=\"M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z\"></path>"
      + "<polyline points=\"14 2 14 8 20 8\"></polyline>"
      + "<line x1=\"16\" y1=\"13\" x2=\"8\" y2=\"13\"></line>"
      + "<line x1=\"16\" y1=\"17\" x2=\"8\" y2=\"17\"></line></svg>";

  private static final String DONE_SVG = SVG_OPEN
      + "<polyline points=\"20 6 9 17 4 12\"></polyline></svg>";

  public static final String OPTION_ID_ATTRIBUTE = "o";
  public static final String OPTION_SELECTED_ATTRIBUTE = "s";
  private static final EnumSet<MenuOption> MENU_OPTIONS_BEFORE_EDITING = EnumSet.of(
      IntrinsicBlipMetaView.MenuOption.REPLY,
      IntrinsicBlipMetaView.MenuOption.EDIT,
      IntrinsicBlipMetaView.MenuOption.DELETE,
      IntrinsicBlipMetaView.MenuOption.LINK);
  public final static Set<MenuOption> ENABLED_WHILE_EDITING_MENU_OPTIONS_SET = EnumSet.of(
      IntrinsicBlipMetaView.MenuOption.DRAFT,
      IntrinsicBlipMetaView.MenuOption.EDIT_DONE);
  public final static Set<MenuOption> DISABLED_WHILE_EDITING_MENU_OPTIONS_SET = MENU_OPTIONS_BEFORE_EDITING;

  /**
   * A unique id for this builder.
   */
  private final String id;
  private final BlipViewBuilder.Css css;

  //
  // Intrinsic state.
  //

  private String time;
  private String timeTooltip;
  private String metaline;
  private String avatarUrl;
  private String authorAddress;
  private boolean read = true;
  private final Set<MenuOption> options = EnumSet.copyOf(MENU_OPTIONS_BEFORE_EDITING);
  private final Set<MenuOption> selected = EnumSet.noneOf(MenuOption.class);

  //
  // Structural components.
  //

  private final UiBuilder content;

  /**
   * Creates a new blip view builder with the given id.
   *
   * @param id unique id for this builder, it must only contains alphanumeric
   *        characters
   */
  public static BlipMetaViewBuilder create(String id, UiBuilder content) {
    return new BlipMetaViewBuilder(WavePanelResourceLoader.getBlip().css(),
        WavePanelResourceLoader.getBlipMessages(), id, nonNull(content));
  }

  BlipMetaViewBuilder(BlipViewBuilder.Css css, BlipMessages messages, String id, UiBuilder content) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"), "!id.contains(\"\\'\")");
    this.css = css;
    this.id = id;
    this.content = content;
    buildMenuModel(messages);
  }

  @Override
  public void setAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  @Override
  public void setAuthorAddress(String address) {
    this.authorAddress = address;
  }

  @Override
  public void setTime(String time) {
    this.time = time;
  }

  @Override
  public void setTimeTooltip(String fullDateTime) {
    this.timeTooltip = fullDateTime;
  }

  @Override
  public void setMetaline(String metaline) {
    this.metaline = metaline;
  }

  @Override
  public void setRead(boolean read) {
    this.read = read;
  }

  @Override
  public void enable(Set<MenuOption> options) {
    this.options.addAll(options);
  }

  @Override
  public void disable(Set<MenuOption> options) {
    this.options.removeAll(options);
    this.selected.removeAll(options);
  }

  @Override
  public void select(MenuOption option) {
    this.selected.add(option);
  }

  @Override
  public void deselect(MenuOption option) {
    this.selected.remove(option);
  }

  @Override
  public void setDraftActive(boolean active) {
    // Draft mode is only activated client-side during editing, so the
    // initial HTML render never needs to include the draft-active indicator.
  }

  //
  // DomImpl nature.
  //

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // HACK HACK HACK
    // This code should be automatically generated from UiBinder template, not
    // hand written.

    open(output, id, css.meta(), TypeCodes.kind(Type.META));
    {
      // Author avatar — includes data-address for the profile card popup.
      // G-PORT-3 (#1112): also stamps data-blip-author for the J2CL ↔ GWT
      // parity test (mirrors the existing data-address; the parity hook
      // is intentionally a duplicate so a single Playwright selector
      // works against both views).
      {
        String avatarId = Components.AVATAR.getDomId(id);
        String safeUrl = avatarUrl != null ? EscapeUtils.sanitizeUri(avatarUrl) : null;
        StringBuilder img = new StringBuilder("<img ");
        img.append("id='").append(avatarId).append("' ");
        img.append("class='").append(css.avatar()).append("' ");
        if (safeUrl != null) {
          img.append("src='").append(EscapeUtils.htmlEscape(safeUrl)).append("' ");
        }
        img.append("alt='author' ");
        if (authorAddress != null) {
          String safeAddress = EscapeUtils.htmlEscape(authorAddress);
          img.append("data-address='").append(safeAddress).append("' ");
          img.append("data-blip-author='").append(safeAddress).append("' ");
        }
        img.append("></img>");
        output.append(EscapeUtils.fromSafeConstant(img.toString()));
      }

      // Metabar.
      open(output, Components.METABAR.getDomId(id),
          css.metabar() + " " + (read ? css.read() : css.unread()), null);
      {
        open(output, Components.MENU.getDomId(id), css.menu(), null);
        menuBuilder(options, selected, css).outputHtml(output);
        close(output);

        // Time.
        // G-PORT-3 (#1112): stamps data-blip-time alongside the
        // existing title= tooltip so the J2CL ↔ GWT parity test can
        // assert a single attribute on both views. The parity contract
        // only requires the attribute to be non-empty; the format is
        // the same human-readable string the tooltip already shows
        // (the J2CL side stamps an ISO timestamp — the test does not
        // depend on either format being equal across views).
        {
          StringBuilder extra = new StringBuilder();
          if (timeTooltip != null) {
            String safeTooltip = EscapeUtils.htmlEscape(timeTooltip);
            extra.append("title='").append(safeTooltip).append("'");
            extra.append(" data-blip-time='").append(safeTooltip).append("'");
          } else if (time != null) {
            extra.append("data-blip-time='").append(EscapeUtils.htmlEscape(time)).append("'");
          }
          String extraAttr = extra.length() == 0 ? null : extra.toString();
          openWith(output, Components.TIME.getDomId(id), css.time(), null, extraAttr);
        }
        if (time != null) {
          output.appendEscaped(time);
        }
        close(output);

        // Metaline.
        open(output, Components.METALINE.getDomId(id), css.metaline(), null);
        if (metaline != null) {
          output.appendEscaped(metaline);
        }
        close(output);
      }
      close(output);

      // Content.
      open(output, Components.CONTENT.getDomId(id), css.contentContainer(), "document");
      content.outputHtml(output);
      close(output);

      // Draft-mode controls container (hidden by default).
      output.appendHtmlConstant("<div id=\"");
      output.appendEscaped(Components.DRAFTMODECONTROLS.getDomId(id));
      output.appendHtmlConstant("\" style=\"display:none\"></div>");
    }
    close(output);
  }

  /** CSS class name for the edit-mode keyboard hint. */
  public static final String EDIT_HINT_CLASS = "editHint";

  /**
   * Creates a builder for a blip menu with SVG icon buttons.
   */
  public static UiBuilder menuBuilder(final Set<MenuOption> options, final Set<MenuOption> selected,
      final BlipViewBuilder.Css css) {
    return new UiBuilder() {
      @Override
      public void outputHtml(SafeHtmlBuilder out) {
        for (MenuOption option : options) {
          String style = selected.contains(option) //
              ? css.menuOption() + " " + css.menuOptionSelected() : css.menuOption();
          String title = EscapeUtils.htmlEscape(MENU_LABELS.get(option).asString());
          String dataOption = option.name().toLowerCase();
          String extra = OPTION_ID_ATTRIBUTE + "='" + MENU_CODES.get(option).asString() + "'"
              + " title='" + title + "'"
              + " data-option='" + dataOption + "'"
              + (selected.contains(option) ? " " + OPTION_SELECTED_ATTRIBUTE + "='s'" : "");
          String e2eAction = e2eActionFor(option);
          if (e2eAction != null) {
            extra += " data-e2e-action='" + e2eAction + "'";
          }
          openSpanWith(out, null, style, TypeCodes.kind(Type.MENU_ITEM), extra);
          out.append(MENU_ICONS.get(option));
          closeSpan(out);
        }
        // Show a subtle keyboard hint when in edit mode (desktop only — not useful on mobile)
        if (options.contains(MenuOption.EDIT_DONE) && !MobileDetector.isMobile()) {
          out.appendHtmlConstant(
              "<span class='" + EDIT_HINT_CLASS + "'>Shift+Enter to finish, Esc to exit</span>");
        }
      }
    };
  }

  private static String e2eActionFor(MenuOption option) {
    switch (option) {
      case REPLY:
        return "reply";
      case EDIT_DONE:
        return "edit-done";
      default:
        return null;
    }
  }

  public static MenuOption getMenuOption(String id) {
    MenuOption option = MENU_OPTIONS.get(id);
    if (option == null) {
      throw new IllegalArgumentException("No such option: " + id);
    }
    return option;
  }

  public static SafeHtml getMenuOptionId(MenuOption option) {
    SafeHtml code = MENU_CODES.get(option);
    if (code == null) {
      throw new IllegalArgumentException("No such option: " + option);
    }
    return code;
  }

  private static void buildMenuModel(BlipMessages messages) {
    MENU_CODES.put(MenuOption.REPLY, EscapeUtils.fromSafeConstant("r"));
    MENU_CODES.put(MenuOption.EDIT, EscapeUtils.fromSafeConstant("e"));
    MENU_CODES.put(MenuOption.DELETE, EscapeUtils.fromSafeConstant("d"));
    MENU_CODES.put(MenuOption.LINK, EscapeUtils.fromSafeConstant("l"));
    MENU_CODES.put(MenuOption.DRAFT, EscapeUtils.fromSafeConstant("f"));
    MENU_CODES.put(MenuOption.EDIT_DONE, EscapeUtils.fromSafeConstant("x"));

    MENU_LABELS.put(MenuOption.REPLY, EscapeUtils.fromSafeConstant(messages.reply()));
    MENU_LABELS.put(MenuOption.EDIT, EscapeUtils.fromSafeConstant(messages.edit()));
    MENU_LABELS.put(MenuOption.DELETE, EscapeUtils.fromSafeConstant(messages.delete()));
    MENU_LABELS.put(MenuOption.LINK, EscapeUtils.fromSafeConstant(messages.link()));
    MENU_LABELS.put(MenuOption.DRAFT, EscapeUtils.fromSafeConstant(messages.draft()));
    MENU_LABELS.put(MenuOption.EDIT_DONE, EscapeUtils.fromSafeConstant(messages.done()));

    MENU_ICONS.put(MenuOption.REPLY, EscapeUtils.fromSafeConstant(REPLY_SVG));
    MENU_ICONS.put(MenuOption.EDIT, EscapeUtils.fromSafeConstant(EDIT_SVG));
    MENU_ICONS.put(MenuOption.DELETE, EscapeUtils.fromSafeConstant(DELETE_SVG));
    MENU_ICONS.put(MenuOption.LINK, EscapeUtils.fromSafeConstant(LINK_SVG));
    MENU_ICONS.put(MenuOption.DRAFT, EscapeUtils.fromSafeConstant(DRAFT_SVG));
    MENU_ICONS.put(MenuOption.EDIT_DONE, EscapeUtils.fromSafeConstant(DONE_SVG));

    for (MenuOption option : MENU_CODES.keySet()) {
      MENU_OPTIONS.put(MENU_CODES.get(option).asString(), option);
    }
    assert MENU_CODES.keySet().equals(MENU_LABELS.keySet());
    assert MENU_OPTIONS.countEntries() == MENU_CODES.size();
    assert new HashSet<MenuOption>(Arrays.asList(MenuOption.values())).equals(MENU_LABELS.keySet());
  }
}

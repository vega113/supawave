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

import static org.waveprotocol.wave.client.uibuilder.OutputHelper.appendSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openWith;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpanWith;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import org.waveprotocol.wave.client.uibuilder.HtmlClosureCollection;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.ParticipantMessages;
import org.waveprotocol.wave.model.conversation.WaveLockState;

/**
 * UiBuilder for a collection of participants.
 *
 */
public final class ParticipantsViewBuilder implements UiBuilder {

  /** Height of the regular (collapsed) panel. */
  final static int COLLAPSED_HEIGHT_PX = 51;

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    /** CSS */
    @Source("Participants.css")
    Css css();
    @Source("expand_button.png")
    ImageResource expandButton();

    @Source("collapse_button.png")
    ImageResource collapseButton();

    @Source("add_button.png")
    ImageResource addButton();
  }

  /** CSS for this widget. */
  public interface Css extends CssResource {
    String participant();
    String panel();
    String flow();
    String extra();
    String toggleGroup();
    String simple();
    String expandButton();
    String collapseButton();
    String addButton();
    String newWaveWithParticipantsButton();
    String publicToggleButton();
    String shareLinkButton();
    String dmLabel();
    String lockToggleButton();
  }

  private final static ParticipantMessages messages = GWT.create(ParticipantMessages.class);

  /** An enum for all the components of a participants view. */
  public enum Components implements Component {
    /** Element to which participant UIs are attached. */
    CONTAINER("C"),
    /** Add button. */
    ADD("A");
    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  private final Css css;
  private final HtmlClosureCollection participantUis;
  private final String id;
  private final boolean isPublic;
  private final boolean isDm;
  private final WaveLockState lockState;
  ParticipantsViewBuilder(Css css, String id, HtmlClosureCollection participantUis,
      boolean isPublic, boolean isDm) {
    this(css, id, participantUis, isPublic, isDm, WaveLockState.UNLOCKED);
  }

  ParticipantsViewBuilder(Css css, String id, HtmlClosureCollection participantUis,
      boolean isPublic, boolean isDm, WaveLockState lockState) {
    this.css = css;
    this.id = id;
    this.participantUis = participantUis;
    this.isPublic = isPublic;
    this.isDm = isDm;
    this.lockState = lockState != null ? lockState : WaveLockState.UNLOCKED;
  }

  /**
   * Creates a new ParticipantsViewBuilder.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   */
  public static ParticipantsViewBuilder create(String id, HtmlClosureCollection participantUis) {
    return new ParticipantsViewBuilder(
        WavePanelResourceLoader.getParticipants().css(), id, participantUis, false, false);
  }

  /**
   * Creates a new ParticipantsViewBuilder with public/private and DM state.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   * @param isPublic true if the wave is currently public (shared with domain)
   * @param isDm true if this wave carries the explicit direct-message tag
   */
  public static ParticipantsViewBuilder create(String id, HtmlClosureCollection participantUis,
      boolean isPublic, boolean isDm) {
    return new ParticipantsViewBuilder(
        WavePanelResourceLoader.getParticipants().css(), id, participantUis, isPublic, isDm,
        WaveLockState.UNLOCKED);
  }

  /**
   * Creates a new ParticipantsViewBuilder with public/private, DM, and lock state.
   */
  public static ParticipantsViewBuilder create(String id, HtmlClosureCollection participantUis,
      boolean isPublic, boolean isDm, WaveLockState lockState) {
    return new ParticipantsViewBuilder(
        WavePanelResourceLoader.getParticipants().css(), id, participantUis, isPublic, isDm,
        lockState);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    openWith(output, id, css.panel(), TypeCodes.kind(Type.PARTICIPANTS),
        "data-mobile-role='wave-participants'");
    {
      open(output, null, css.flow(), null);
      {
        open(output, Components.CONTAINER.getDomId(id), null, null);
        {
          participantUis.outputHtml(output);

          if (isDm) {
            // DM wave: show only the "Direct Message" label, hide action buttons.
            openSpan(output, null, css.extra(), null);
            {
              openSpanWith(output, null, css.toggleGroup(), null, "onclick=\"" + onClickJs() + "\"");
              {
                appendSpan(output, null, css.expandButton(), null);
                openSpan(output, null, null, null);
                {
                  output.appendPlainText(messages.more());
                }
                closeSpan(output);
              }
              closeSpan(output);
              dmLabel(output, css.dmLabel(), messages.directMessage());
            }
            closeSpan(output);

            openSpan(output, null, css.simple(), null);
            {
              dmLabel(output, css.dmLabel(), messages.directMessage());
            }
            closeSpan(output);
          } else {
            // Normal wave: show action buttons.

            // Overflow-mode panel (toggle + add, but NO new-wave button to
            // avoid duplication with the single-line panel).
            openSpan(output, null, css.extra(), null);
            {
              openSpanWith(output, null, css.toggleGroup(), null, "onclick=\"" + onClickJs() + "\"");
              {
                appendSpan(output, null, css.expandButton(), null);
                openSpan(output, null, null, null);
                {
                  output.appendPlainText(messages.more());
                }
                closeSpan(output);
              }
              closeSpan(output);
              addParticipantIcon(output, css.addButton(),
                  TypeCodes.kind(Type.ADD_PARTICIPANT),
                  messages.addParticipantToThisWave());
              newWaveIcon(output, css.newWaveWithParticipantsButton(),
                  TypeCodes.kind(Type.NEW_WAVE_WITH_PARTICIPANTS),
                  messages.newWaveWithParticipantsOfCurrentWave());
              publicToggleIcon(output, css.publicToggleButton(),
                  TypeCodes.kind(Type.TOGGLE_PUBLIC),
                  isPublic ? messages.makeWavePrivate() : messages.makeWavePublic(),
                  isPublic);
              shareLinkIcon(output, css.shareLinkButton(),
                  TypeCodes.kind(Type.SHARE_LINK),
                  messages.sharePublicLink(),
                  isPublic);
              lockToggleIcon(output, css.lockToggleButton(),
                  TypeCodes.kind(Type.TOGGLE_LOCK),
                  lockTooltip(lockState), lockState);
            }
            closeSpan(output);

            // Single-line mode panel.
            openSpan(output, null, css.simple(), null);
            {
              addParticipantIcon(output, css.addButton(),
                  TypeCodes.kind(Type.ADD_PARTICIPANT),
                  messages.addParticipantToThisWave());
              newWaveIcon(output, css.newWaveWithParticipantsButton(),
                  TypeCodes.kind(Type.NEW_WAVE_WITH_PARTICIPANTS),
                  messages.newWaveWithParticipantsOfCurrentWave());
              publicToggleIcon(output, css.publicToggleButton(),
                  TypeCodes.kind(Type.TOGGLE_PUBLIC),
                  isPublic ? messages.makeWavePrivate() : messages.makeWavePublic(),
                  isPublic);
              shareLinkIcon(output, css.shareLinkButton(),
                  TypeCodes.kind(Type.SHARE_LINK),
                  messages.sharePublicLink(),
                  isPublic);
              lockToggleIcon(output, css.lockToggleButton(),
                  TypeCodes.kind(Type.TOGGLE_LOCK),
                  lockTooltip(lockState), lockState);
            }
            closeSpan(output);
          }
        }
        close(output);
      }
      close(output);
    }
    close(output);
  }

  /**
   * Renders a circular icon button for "add participant to this wave".
   * Uses a Material-style person-add SVG icon.
   */
  private static void addParticipantIcon(
      SafeHtmlBuilder output, String clazz, String kind, String title) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;
    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + (escapedKind != null ? " kind='" + escapedKind + "'" : "")
        + (escapedTitle != null ? " title='" + escapedTitle + "'" : "")
        + " role='button' tabindex='0'"
        + (escapedTitle != null ? " aria-label='" + escapedTitle + "'" : "")
        + ">"
        // Material "person_add" icon (person silhouette + plus sign)
        + "<svg width='15' height='15' viewBox='0 0 24 24' fill='currentColor'>"
        + "<path d='M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 "
        + "4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/>"
        + "</svg>"
        + "</span>");
  }

  /**
   * Renders a circular icon button for "new wave with participants".
   * Uses a wave-style SVG icon to distinguish it from the add-participant button.
   */
  private static void newWaveIcon(SafeHtmlBuilder output, String clazz, String kind, String title) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;
    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + (escapedKind != null ? " kind='" + escapedKind + "'" : "")
        + (escapedTitle != null ? " title='" + escapedTitle + "'" : "")
        + " role='button' tabindex='0'"
        + (escapedTitle != null ? " aria-label='" + escapedTitle + "'" : "")
        + ">"
        // Wave/water icon with a small plus: three wavy lines + plus symbol
        + "<svg width='15' height='15' viewBox='0 0 24 24' fill='currentColor'>"
        + "<path d='M21 14c-1.11 0-1.98-.42-2.76-1.13-.78.71-1.64 1.13-2.74 1.13s-1.97-.42-2.75-1.13c-.78.71-1.64 "
        + "1.13-2.75 1.13s-1.96-.42-2.74-1.13C6.47 13.58 5.61 14 4.5 14c-1.11 0-1.97-.42-2.75-1.13-.15.14-.31.26-."
        + "48.38v2.49c.34-.11.69-.24 1.23-.24 1.11 0 1.97.42 2.75 1.13.78-.71 1.64-1.13 2.75-1.13s1.96.42 2.74 1."
        + "13c.78-.71 1.65-1.13 2.76-1.13s1.97.42 2.75 1.13c.78-.71 1.64-1.13 2.75-1.13.54 0 .89.13 1.23.24v-2.49"
        + "c-.17-.12-.33-.24-.48-.38-.78.71-1.64 1.13-2.75 1.13zm0-5c-1.11 0-1.98-.42-2.76-1.13-.78.71-1.64 1.13-2"
        + ".74 1.13s-1.97-.42-2.75-1.13c-.78.71-1.64 1.13-2.75 1.13s-1.96-.42-2.74-1.13C6.47 8.58 5.61 9 4.5 9c-1"
        + ".11 0-1.97-.42-2.75-1.13-.15.14-.31.26-.48.38v2.49c.34-.11.69-.24 1.23-.24 1.11 0 1.97.42 2.75 1.13.78-"
        + ".71 1.64-1.13 2.75-1.13s1.96.42 2.74 1.13c.78-.71 1.65-1.13 2.76-1.13s1.97.42 2.75 1.13c.78-.71 1.64-1"
        + ".13 2.75-1.13.54 0 .89.13 1.23.24V8.25c-.17-.12-.33-.24-.48-.38C22.98 8.58 22.11 9 21 9zm0 10c-1.11 0-1"
        + ".98-.42-2.76-1.13-.78.71-1.64 1.13-2.74 1.13s-1.97-.42-2.75-1.13c-.78.71-1.64 1.13-2.75 1.13s-1.96-.42"
        + "-2.74-1.13C6.47 18.58 5.61 19 4.5 19c-1.11 0-1.97-.42-2.75-1.13-.15.14-.31.26-.48.38v2.49c.34-.11.69-.2"
        + "4 1.23-.24 1.11 0 1.97.42 2.75 1.13.78-.71 1.64-1.13 2.75-1.13s1.96.42 2.74 1.13c.78-.71 1.65-1.13 2.7"
        + "6-1.13s1.97.42 2.75 1.13c.78-.71 1.64-1.13 2.75-1.13.54 0 .89.13 1.23.24v-2.49c-.17-.12-.33-.24-.48-.3"
        + "8-.78.71-1.64 1.13-2.75 1.13z'/>"
        + "</svg>"
        + "</span>");
  }

  /**
   * Renders a compact circular icon button for toggling wave public/private visibility.
   * Shows an open-lock icon when public and a closed-lock icon when private.
   * This avoids confusion with the globe icon used in the search panel filters.
   */
  private static void publicToggleIcon(SafeHtmlBuilder output, String clazz, String kind,
      String title, boolean isPublic) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;

    String svgIcon;
    if (isPublic) {
      // Open-lock icon for public state (unlocked = everyone can see)
      svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
          + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
          + "stroke-linejoin='round'>"
          + "<rect x='3' y='11' width='18' height='11' rx='2' ry='2'/>"
          + "<path d='M7 11V7a5 5 0 0 1 9.9-1'/>"
          + "</svg>";
    } else {
      // Closed-lock icon for private state (locked = only participants)
      svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
          + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
          + "stroke-linejoin='round'>"
          + "<rect x='3' y='11' width='18' height='11' rx='2' ry='2'/>"
          + "<path d='M7 11V7a5 5 0 0 1 10 0v4'/>"
          + "</svg>";
    }

    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + (escapedKind != null ? " kind='" + escapedKind + "'" : "")
        + (escapedTitle != null ? " title='" + escapedTitle + "'" : "")
        + " role='button' tabindex='0'"
        + (escapedTitle != null ? " aria-label='" + escapedTitle + "'" : "")
        + ">"
        + svgIcon
        + "</span>");
  }

  /**
   * Renders a compact circular icon button for sharing the public wave link.
   * Uses a link/chain SVG icon with a green gradient background.
   */
  private static void shareLinkIcon(SafeHtmlBuilder output, String clazz, String kind,
      String title, boolean visible) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;

    // Link/chain icon for sharing
    String svgIcon = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' "
        + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
        + "stroke-linejoin='round'>"
        + "<path d='M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71'/>"
        + "<path d='M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71'/>"
        + "</svg>";

    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + (escapedKind != null ? " kind='" + escapedKind + "'" : "")
        + (escapedTitle != null ? " title='" + escapedTitle + "'" : "")
        + " role='button' tabindex='0'"
        + (escapedTitle != null ? " aria-label='" + escapedTitle + "'" : "")
        + (visible ? "" : " style='display:none'")
        + ">"
        + svgIcon
        + "</span>");
  }

  /**
   * Renders a subtle "Direct Message" label for DM waves. Uses a small
   * chat-bubble SVG icon followed by the label text.
   */
  private static void dmLabel(SafeHtmlBuilder output, String clazz, String label) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedLabel = label != null ? EscapeUtils.htmlEscape(label) : "";
    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + " title='" + escapedLabel + "'"
        + ">"
        // Small chat-bubble icon
        + "<svg width='12' height='12' viewBox='0 0 24 24' fill='currentColor' "
        + "style='vertical-align: middle; margin-right: 3px;'>"
        + "<path d='M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4V4c0-1.1-.9-2-2-2z'/>"
        + "</svg>"
        + escapedLabel
        + "</span>");
  }

  /**
   * Renders a compact circular icon button for toggling wave lock state.
   * Shows different icons depending on the current lock state:
   * UNLOCKED: empty shield, ROOT_LOCKED: shield with line, ALL_LOCKED: filled shield.
   */
  private static void lockToggleIcon(SafeHtmlBuilder output, String clazz, String kind,
      String title, WaveLockState lockState) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;

    String svgIcon;
    switch (lockState) {
      case ROOT_LOCKED:
        svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
            + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
            + "stroke-linejoin='round'>"
            + "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>"
            + "<line x1='12' y1='8' x2='12' y2='16'/>"
            + "</svg>";
        break;
      case ALL_LOCKED:
        svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='currentColor' "
            + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
            + "stroke-linejoin='round'>"
            + "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>"
            + "</svg>";
        break;
      default:
        svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
            + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
            + "stroke-linejoin='round'>"
            + "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>"
            + "</svg>";
        break;
    }

    output.appendHtmlConstant(
        "<span"
        + (escapedClazz != null ? " class='" + escapedClazz + "'" : "")
        + (escapedKind != null ? " kind='" + escapedKind + "'" : "")
        + (escapedTitle != null ? " title='" + escapedTitle + "'" : "")
        + " role='button' tabindex='0'"
        + (escapedTitle != null ? " aria-label='" + escapedTitle + "'" : "")
        + ">"
        + svgIcon
        + "</span>");
  }

  /** Returns a tooltip string for the current lock state. */
  private static String lockTooltip(WaveLockState lockState) {
    switch (lockState) {
      case ROOT_LOCKED:
        return messages.waveRootLocked();
      case ALL_LOCKED:
        return messages.waveAllLocked();
      default:
        return messages.waveUnlocked();
    }
  }

  // Rather than install a regular handler, this is an experiment at injecting
  // JS directly, so that this piece of UI is functional from the initial
  // rendering, without needing to wait for any scripts to load (like the GWT
  // app).
  /** @return a JS click handler for toggling expanded and collapsed modes. */
  private String onClickJs() {
    String escapedId = escapeJsSingleQuoted(id);
    String more = escapeJsSingleQuoted(messages.more());
    String less = escapeJsSingleQuoted(messages.less());
    StringBuilder sb = new StringBuilder();
    sb.append("var p=document.getElementById('").append(escapedId).append("');")
        .append("var x=p.getAttribute('s')=='e';")
        .append("var l=this.lastChild;")
        .append("p.style.height=x?'':'auto';")
        .append("p.setAttribute('s',x?'':'e');")
        .append("lastChild.innerText=x?'").append(more).append("':'").append(less).append("';")
        .append("firstChild.className=x?'").append(css.expandButton())
        .append("':'").append(css.collapseButton()).append("';")
        .append("parentNode.nextSibling.style.display=x?'':'none';");
    return escapeHtmlAttribute(sb.toString());
  }

  private static String escapeHtmlAttribute(String value) {
    return value
        .replace("&", "&amp;")
        .replace("'", "&#39;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private static String escapeJsSingleQuoted(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }
}

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
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpanWith;

import com.google.common.annotations.VisibleForTesting;
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
  @VisibleForTesting
  ParticipantsViewBuilder(Css css, String id, HtmlClosureCollection participantUis,
      boolean isPublic) {
    this.css = css;
    this.id = id;
    this.participantUis = participantUis;
    this.isPublic = isPublic;
  }

  /**
   * Creates a new ParticipantsViewBuilder.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   */
  public static ParticipantsViewBuilder create(String id, HtmlClosureCollection participantUis) {
    return new ParticipantsViewBuilder(
        WavePanelResourceLoader.getParticipants().css(), id, participantUis, false);
  }

  /**
   * Creates a new ParticipantsViewBuilder with public/private state.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   * @param isPublic true if the wave is currently public (shared with domain)
   */
  public static ParticipantsViewBuilder create(String id, HtmlClosureCollection participantUis,
      boolean isPublic) {
    return new ParticipantsViewBuilder(
        WavePanelResourceLoader.getParticipants().css(), id, participantUis, isPublic);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    open(output, id, css.panel(), TypeCodes.kind(Type.PARTICIPANTS));
    {
      open(output, null, css.flow(), null);
      {
        open(output, Components.CONTAINER.getDomId(id), null, null);
        {
          participantUis.outputHtml(output);

          // Overflow-mode panel.
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
            appendSpan(output, null, css.addButton(), TypeCodes.kind(Type.ADD_PARTICIPANT));
            newWaveIcon(output, css.newWaveWithParticipantsButton(),
                TypeCodes.kind(Type.NEW_WAVE_WITH_PARTICIPANTS),
                messages.newWaveWithParticipantsOfCurrentWave());
            publicToggleIcon(output, css.publicToggleButton(),
                TypeCodes.kind(Type.TOGGLE_PUBLIC),
                isPublic ? messages.makeWavePrivate() : messages.makeWavePublic(),
                isPublic);
          }
          closeSpan(output);

          // Single-line mode panel.
          openSpan(output, null, css.simple(), null);
          {
            appendSpan(output, null, css.addButton(), TypeCodes.kind(Type.ADD_PARTICIPANT));
            newWaveIcon(output, css.newWaveWithParticipantsButton(),
                TypeCodes.kind(Type.NEW_WAVE_WITH_PARTICIPANTS),
                messages.newWaveWithParticipantsOfCurrentWave());
            publicToggleIcon(output, css.publicToggleButton(),
                TypeCodes.kind(Type.TOGGLE_PUBLIC),
                isPublic ? messages.makeWavePrivate() : messages.makeWavePublic(),
                isPublic);
          }
          closeSpan(output);
        }
        close(output);
      }
      close(output);
    }
    close(output);
  }

  /**
   * Renders a compact circular icon button for "new wave with participants".
   * Replaces the former bulky {@code <button>} with an inline SVG "+" icon.
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
        + "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
        + "stroke='currentColor' stroke-width='2'>"
        + "<line x1='12' y1='5' x2='12' y2='19'/>"
        + "<line x1='5' y1='12' x2='19' y2='12'/>"
        + "</svg>"
        + "</span>");
  }

  /**
   * Renders a compact circular icon button for toggling wave public/private visibility.
   * Shows a globe icon when public and a lock icon when private.
   */
  private static void publicToggleIcon(SafeHtmlBuilder output, String clazz, String kind,
      String title, boolean isPublic) {
    String escapedClazz = clazz != null ? EscapeUtils.htmlEscape(clazz) : null;
    String escapedKind = kind != null ? EscapeUtils.htmlEscape(kind) : null;
    String escapedTitle = title != null ? EscapeUtils.htmlEscape(title) : null;

    String svgIcon;
    if (isPublic) {
      // Globe icon for public state
      svgIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' "
          + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
          + "stroke-linejoin='round'>"
          + "<circle cx='12' cy='12' r='10'/>"
          + "<line x1='2' y1='12' x2='22' y2='12'/>"
          + "<path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10"
          + " 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'/>"
          + "</svg>";
    } else {
      // Lock icon for private state
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

  // Rather than install a regular handler, this is an experiment at injecting
  // JS directly, so that this piece of UI is functional from the initial
  // rendering, without needing to wait for any scripts to load (like the GWT
  // app).
  /** @return a JS click handler for toggling expanded and collapsed modes. */
  private String onClickJs() {
    String js = "" //
        + "var p=document.getElementById('" + id + "');" //
        + "var x=p.getAttribute('s')=='e';" //
        + "var l=this.lastChild;" //
        + "p.style.height=x?'':'auto';" //
        + "p.setAttribute('s',x?'':'e');" //
        + "lastChild.innerHTML=x?'" + messages.more() + "':'" + messages.less() + "';" //
        + "firstChild.className=x?'" + css.expandButton() + "':'" + css.collapseButton() + "';" //
        + "parentNode.nextSibling.style.display=x?'':'none';" //
    ;
    // The constructed string has no double-quote characters in it, so it can be
    // double-quoted verbatim.
    assert !js.contains("\"");
    return js;
  }
}

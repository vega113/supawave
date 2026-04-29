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
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openWith;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;

/**
 * A implementation of the BlipView that output content as HTML string. This
 * class should be automatically generated from a template, but the template
 * generator is not ready yet.
 *
 */
public class BlipViewBuilder implements UiBuilder, IntrinsicBlipView {
  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    /** CSS */
    @Source("Blip.css")
    Css css();
  }

  /** CSS for this widget. */
  public interface Css extends CssResource {
    /** The topmost blip element. */
    String blip();

    String meta();

    String avatar();

    String metabar();

    String read();

    String unread();

    String metaline();

    String menu();

    String menuOption();

    String menuOptionSelected();

    String time();

    String contentContainer();

    String reactions();

    String replies();

    String privateReplies();

    // Quasi-deleted state class
    String deleted();

    // Draft-mode active indicator class
    String draftActive();
  }

  /** An enum for all the components of a blip view. */
  public enum Components implements Component {
    /** Container for the per-blip reactions row. */
    REACTIONS("J"),
    /** Container for default anchors of reply threads. */
    REPLIES("R"),
    /** Container for nested conversations. */
    PRIVATE_REPLIES("P"),
    ;

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  /**
   * A unique id for this builder.
   */
  private final String id;
  /**
   * G-PORT-3 (#1112): the model blip id (without the {@code B} suffix
   * that {@link org.waveprotocol.wave.client.wavepanel.view.dom.ViewIdMapper#blipOf}
   * appends). Stamped on the rendered DOM as a {@code data-blip-id}
   * attribute so the J2CL ↔ GWT parity Playwright spec can use a
   * single selector on both views. May be null only when the legacy
   * factory overload is used (for tests / fixtures); the renderer
   * production path always supplies it.
   */
  private final String modelBlipId;
  private final Css css;

  //
  // Structural components.
  //

  private final UiBuilder meta;
  private final UiBuilder reactions;
  private final UiBuilder replies;
  private final UiBuilder privateReplies;

  /**
   * Creates a new blip view builder with the given id.
   *
   * @param id unique id for this builder, it must only contains alphanumeric
   *        characters
   * @param replies collection of non-inline replies
   */
  public static BlipViewBuilder create(String id, UiBuilder meta, UiBuilder reactions,
      UiBuilder replies, UiBuilder privateReplies) {
    return create(id, /* modelBlipId */ null, meta, reactions, replies, privateReplies);
  }

  /**
   * Creates a new blip view builder with the model blip id supplied
   * for the G-PORT-3 (#1112) {@code data-blip-id} parity hook.
   */
  public static BlipViewBuilder create(String id, String modelBlipId, UiBuilder meta,
      UiBuilder reactions, UiBuilder replies, UiBuilder privateReplies) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"), "!id.contains(\"\\'\")");
    return new BlipViewBuilder(id, modelBlipId, nonNull(meta), nonNull(reactions), nonNull(replies),
        nonNull(privateReplies), WavePanelResourceLoader.getBlip().css());
  }

  BlipViewBuilder(String id, String modelBlipId, UiBuilder meta, UiBuilder reactions,
      UiBuilder replies, UiBuilder privateReplies, Css css) {
    this.id = id;
    this.modelBlipId = modelBlipId;
    this.meta = meta;
    this.reactions = reactions;
    this.replies = replies;
    this.privateReplies = privateReplies;
    this.css = css;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // HACK HACK HACK
    // This code should be automatically generated from UiBinder template, not
    // hand written.
    // G-PORT-3 (#1112): emit data-blip-id with the *model* blip id (no
    // ViewIdMapper "B" suffix) so the J2CL ↔ GWT parity test can use a
    // single selector on both views. When modelBlipId is null (legacy
    // factory overload, tests / fixtures) we fall back to the plain
    // open() so rendered HTML remains unchanged.
    if (modelBlipId != null && !modelBlipId.isEmpty()) {
      String extra = "data-blip-id='" + EscapeUtils.htmlEscape(modelBlipId) + "'";
      openWith(output, id, css.blip(), TypeCodes.kind(Type.BLIP), extra);
    } else {
      open(output, id, css.blip(), TypeCodes.kind(Type.BLIP));
    }

    // Meta (no wrapper).
    meta.outputHtml(output);

    // Reactions row (initially empty; populated by the reaction controller).
    open(output, Components.REACTIONS.getDomId(id), css.reactions(), null);
    reactions.outputHtml(output);
    close(output);

    // Replies.
    open(output, Components.REPLIES.getDomId(id), css.replies(), null);
    replies.outputHtml(output);
    close(output);

    // Private Replies.
    open(output, Components.PRIVATE_REPLIES.getDomId(id), css.privateReplies(), null);
    privateReplies.outputHtml(output);
    close(output);

    close(output);
  }
}

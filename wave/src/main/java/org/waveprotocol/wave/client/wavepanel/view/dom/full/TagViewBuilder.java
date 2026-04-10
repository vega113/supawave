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


import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.OutputHelper;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.Css;

/**
 * UiBuilder for a single tag.
 *
 * Ported from Wiab.pro.
 */
public final class TagViewBuilder implements IntrinsicTagView, UiBuilder {

  public static TagViewBuilder create(
      String id, String name, TagState state, String hint) {
    return new TagViewBuilder(
        id, name, state, hint, WavePanelResourceLoader.getTags().css());
  }

  private final String id;
  private String name;
  private TagState state;
  private String hint;
  private final Css css;

  TagViewBuilder(String id, String name, TagState state, String hint, Css css) {
    this.id = id;
    this.name = name;
    this.state = state;
    this.hint = hint;
    this.css = css;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public TagState getState() {
    return state;
  }

  @Override
  public void setState(TagState state) {
    this.state = state;
  }

  @Override
  public String getHint() {
    return hint;
  }

  @Override
  public void setHint(String hint) {
    this.hint = hint;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    String className = css.tag() + " ";
    switch (state) {
      case NORMAL:  className += css.normal();  break;
      case ADDED:   className += css.added();   break;
      case REMOVED: className += css.removed(); break;
    }

    OutputHelper.openWith(output, id, className, TypeCodes.kind(Type.TAG), null, hint, null);
    {
      OutputHelper.openSpan(output, null, css.tagLabel(), null);
      output.appendEscaped(name);
      OutputHelper.closeSpan(output);
      OutputHelper.button(
          output,
          null,
          css.removeButton(),
          TypeCodes.kind(Type.REMOVE_TAG),
          "Remove tag " + name,
          "Remove tag " + name,
          "&times;");
    }
    OutputHelper.close(output);
  }
}

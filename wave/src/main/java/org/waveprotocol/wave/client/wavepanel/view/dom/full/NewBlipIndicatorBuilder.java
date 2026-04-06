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

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

/**
 * CSS resource bundle for the "N new messages" floating pill indicator.
 * The pill DOM element is created at runtime by {@code NewBlipIndicatorPresenter},
 * not via a UiBuilder, since it is not part of the initial HTML rendering tree.
 */
public final class NewBlipIndicatorBuilder {

  /** Position the pill above the tags panel (37px) plus padding. */
  static final class CssConstants {
    private static final int TAGS_TOTAL_HEIGHT_PX =
        TagsViewBuilder.CssConstants.PANEL_TOTAL_HEIGHT_PX;
    private static final int PILL_PADDING_PX = 12;
    static final String PILL_BOTTOM_CSS = (TAGS_TOTAL_HEIGHT_PX + PILL_PADDING_PX) + "px";
  }

  public interface Resources extends ClientBundle {
    @Source("NewBlipIndicator.css")
    Css css();
  }

  public interface Css extends CssResource {
    String pill();
    String pillVisible();
  }

  private NewBlipIndicatorBuilder() {}
}

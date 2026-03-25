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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;

/**
 * Implements a tag view by delegating primitive state matters to a view
 * object, and structural state matters to a helper.
 *
 * Ported from Wiab.pro.
 *
 * @param <I> intrinsic tag implementation
 */
public final class TagViewImpl<I extends IntrinsicTagView>
    extends AbstractStructuredView<TagViewImpl.Helper<? super I>, I>
    implements TagView {

  /**
   * Handles structural queries on tag views.
   *
   * @param <I> intrinsic tag implementation
   */
  public interface Helper<I> {
    void remove(I impl);
  }

  public TagViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public String getId() {
    return impl.getId();
  }

  @Override
  public Type getType() {
    return Type.TAG;
  }

  @Override
  public void remove() {
    helper.remove(impl);
  }

  @Override
  public String getName() {
    return impl.getName();
  }

  @Override
  public void setName(String name) {
    impl.setName(name);
  }

  @Override
  public TagState getState() {
    return impl.getState();
  }

  @Override
  public void setState(TagState state) {
    impl.setState(state);
  }

  @Override
  public String getHint() {
    return impl.getHint();
  }

  @Override
  public void setHint(String hint) {
    impl.setHint(hint);
  }
}

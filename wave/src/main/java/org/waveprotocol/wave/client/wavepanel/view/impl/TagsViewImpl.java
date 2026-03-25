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

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagsView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * Implements a tags-collection view by delegating primitive state
 * matters to a view object, and structural state matters to a helper.
 *
 * Ported from Wiab.pro.
 *
 * @param <I> intrinsic tags implementation
 */
public final class TagsViewImpl<I extends IntrinsicTagsView>
    extends AbstractStructuredView<TagsViewImpl.Helper<? super I>, I>
    implements TagsView {

  /**
   * Handles structural queries on tags views.
   *
   * @param <I> intrinsic tags implementation
   */
  public interface Helper<I> {

    TagView append(
        I impl, Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff);

    TagView remove(
        I impl, Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff);

    void clearDiffs(I impl);

    void remove(I impl);
  }

  public TagsViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public Type getType() {
    return Type.TAGS;
  }

  @Override
  public TagView appendTag(
      Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff) {
    return helper.append(impl, conv, tag, opContext, showDiff);
  }

  @Override
  public TagView removeTag(
      Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff) {
    return helper.remove(impl, conv, tag, opContext, showDiff);
  }

  @Override
  public void clearDiffs() {
    helper.clearDiffs(impl);
  }

  @Override
  public void remove() {
    helper.remove(impl);
  }

  @Override
  public String getId() {
    return impl.getId();
  }
}

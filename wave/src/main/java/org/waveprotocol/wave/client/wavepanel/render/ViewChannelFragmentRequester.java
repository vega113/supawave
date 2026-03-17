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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.core.client.GWT;

import org.waveprotocol.wave.client.wavepanel.render.FragmentRequester.FailureKind;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel;

/**
 * GWT-safe requester that relies on the ViewChannel stream to deliver
 * fragments (ProtocolFragments) rather than issuing HTTP calls.
 */
public final class ViewChannelFragmentRequester implements FragmentRequester {

  /** Functional interface to obtain the current view channel lazily. */
  public interface ChannelSupplier {
    ViewChannel get();
  }

  private final ChannelSupplier channelSupplier;

  public ViewChannelFragmentRequester(ChannelSupplier channelSupplier) {
    this.channelSupplier = channelSupplier;
  }

  @Override
  public void fetch(RequestContext request, Callback cb) {
    ViewChannel channel = channelSupplier != null ? channelSupplier.get() : null;
    if (channel == null || request == null || !request.isValid()) {
      if (cb != null) {
        cb.onSuccess();
      }
      return;
    }
    try {
      channel.fetchFragments(request.waveletId, request.segments,
          request.startVersion, request.endVersion);
    } catch (Throwable ex) {
      try {
        GWT.log("ViewChannelFragmentRequester: fetchFragments failed", ex);
      } catch (Throwable ignored) {
        // ignore logging errors
      }
      if (cb != null) {
        cb.onError(new RequestException("fetchFragments failed", FailureKind.RETRIABLE, ex));
      }
      return;
    }
    if (cb != null) {
      cb.onSuccess();
    }
  }
}

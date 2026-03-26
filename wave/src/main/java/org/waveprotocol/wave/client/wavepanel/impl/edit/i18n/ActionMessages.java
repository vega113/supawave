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

package org.waveprotocol.wave.client.wavepanel.impl.edit.i18n;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface ActionMessages extends Messages {
  @DefaultMessage("Invalid wavelet Id {0}")
  String invalidWaveletId(String waveletId);

  @DefaultMessage("Maximum reply depth reached. Try replying to a parent message instead.")
  String maxReplyDepthReached();

  @DefaultMessage("Maximum reply depth reached. Your reply will be added to the current thread.")
  String maxReplyDepthContinueInThread();
}

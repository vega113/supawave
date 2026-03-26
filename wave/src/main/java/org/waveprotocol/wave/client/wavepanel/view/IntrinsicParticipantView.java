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

package org.waveprotocol.wave.client.wavepanel.view;

/**
 * View of a participant.
 *
 */
public interface IntrinsicParticipantView {
  String getId();

  void setAvatar(String url);
  void setName(String name);

  /**
   * Stores the participant's wave address (e.g. "user@example.com") as a
   * {@code data-address} attribute on the DOM element so that page-level
   * JavaScript (profile card popup) can read it.
   */
  default void setAddress(String address) {
    // no-op by default; DOM-backed implementations override
  }
}

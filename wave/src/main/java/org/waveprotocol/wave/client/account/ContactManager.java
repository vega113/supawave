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

package org.waveprotocol.wave.client.account;

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.SourcesEvents;

import java.util.List;

/**
 * Manages the user's contacts list (participants they have previously
 * interacted with), sorted by interaction frequency.
 *
 * <p>Contacts are fetched from the server and cached locally. The list is
 * sorted by score so that most-frequently-contacted participants appear first.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public interface ContactManager extends SourcesEvents<ContactListener> {

  /**
   * Returns the current list of contacts, sorted by score (highest first).
   *
   * @return an unmodifiable list of participant ids
   */
  List<ParticipantId> getContacts();

  /**
   * Triggers an asynchronous fetch of the contacts list from the server.
   * Listeners will be notified when the update completes.
   */
  void update();
}

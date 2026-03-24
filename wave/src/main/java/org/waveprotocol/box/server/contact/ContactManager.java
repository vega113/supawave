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

package org.waveprotocol.box.server.contact;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;

/**
 * Serves and caches reads and updates of contacts.
 *
 * <p>A "contact" records that two participants have interacted on the server.
 * Contacts are scored so that frequently and recently interacted users rank
 * higher (used for participant autocomplete).
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public interface ContactManager {

  /** Bonuses decay to zero over this period (1 year in ms). */
  long BONUSES_EXPIRATION_MS = 365L * 24 * 60 * 60 * 1000;

  /** Bonus for an indirect incoming interaction (co-participant was added to a shared wave). */
  long INCOMING_INDIRECT_CALL_BONUS_MS = 0;

  /** Bonus for an indirect outgoing interaction. */
  long OUTGOING_INDIRECT_CALL_BONUS_MS = BONUSES_EXPIRATION_MS; // 1 year

  /** Bonus for a direct incoming interaction (someone else added you). */
  long INCOMING_DIRECT_CALL_BONUS_MS = 12 * BONUSES_EXPIRATION_MS; // ~12 years

  /** Bonus for a direct outgoing interaction (you added someone). */
  long OUTGOING_DIRECT_CALL_BONUS_MS = 120 * BONUSES_EXPIRATION_MS; // ~120 years

  /**
   * Returns contacts for a participant updated since {@code fromTime}.
   * Pass 0 to get all contacts.
   */
  List<Contact> getContacts(ParticipantId participant, long fromTime) throws PersistenceException;

  /**
   * Records a new interaction ("call") between two participants.
   *
   * @param caller  the user who performed the action
   * @param receptor the user who was the target of the action
   * @param time    epoch ms when the interaction occurred
   * @param direct  true if caller explicitly added receptor (vs. being a bystander)
   */
  void newCall(ParticipantId caller, ParticipantId receptor, long time, boolean direct)
      throws PersistenceException;

  /**
   * Computes the effective score bonus for a contact at the given time,
   * accounting for time-based decay.
   */
  double getScoreBonusAtTime(Contact contact, long time);
}

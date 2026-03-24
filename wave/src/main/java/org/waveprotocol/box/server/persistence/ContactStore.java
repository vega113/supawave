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

package org.waveprotocol.box.server.persistence;

import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;

/**
 * Storage interface for per-user {@link Contact} lists.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public interface ContactStore {

  /**
   * Initializes the contact store (e.g. create directories, verify connectivity).
   *
   * @throws PersistenceException if initialization fails
   */
  void initializeContactStore() throws PersistenceException;

  /**
   * Returns the contacts for the given participant, or {@code null} if no
   * contacts have been stored yet.
   */
  List<Contact> getContacts(ParticipantId participant) throws PersistenceException;

  /**
   * Replaces the stored contacts for the given participant.
   */
  void storeContacts(ParticipantId participant, List<Contact> contacts)
      throws PersistenceException;
}

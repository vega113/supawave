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

package org.waveprotocol.box.webclient.contact;

import org.waveprotocol.wave.client.account.impl.AbstractContactManager;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A contact manager that fetches contacts from the server and maintains
 * a locally cached, score-sorted list.
 *
 * <p>When {@link #update()} is called, a request is made to the server's
 * {@code /contacts} endpoint. The response contains contacts with scores;
 * these are merged into the local cache and re-sorted so that
 * most-frequently-contacted participants appear first.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public final class RemoteContactManagerImpl extends AbstractContactManager {

  private static final LoggerBundle LOG = new DomLogger("fetchContacts");

  private final FetchContactsServiceImpl fetchContactsService;

  private final List<ParticipantId> contacts = new ArrayList<ParticipantId>();
  private final Map<ParticipantId, Double> contactScores = new HashMap<ParticipantId, Double>();

  /** Timestamp from the last successful server response; -1 means never fetched. */
  private long timestamp = -1;

  public RemoteContactManagerImpl() {
    fetchContactsService = FetchContactsServiceImpl.create();
  }

  @Override
  public List<ParticipantId> getContacts() {
    return Collections.unmodifiableList(contacts);
  }

  @Override
  public void update() {
    fetchContacts();
  }

  private void fetchContacts() {
    // Use timestamp 0 for the initial fetch to get all contacts.
    long requestTimestamp = timestamp < 0 ? 0 : timestamp;
    fetchContactsService.fetch(requestTimestamp, new FetchContactsService.Callback() {

      @Override
      public void onFailure(String message) {
        LOG.error().log("Failed to fetch contacts: " + message);
      }

      @Override
      public void onSuccess(long serverTimestamp, List<FetchContactsService.ContactEntry> entries) {
        if (serverTimestamp < timestamp) {
          LOG.trace().log("Ignoring stale contacts response. serverTimestamp=" + serverTimestamp
              + ", currentTimestamp=" + timestamp);
          return;
        }
        timestamp = serverTimestamp;
        if (!entries.isEmpty()) {
          for (FetchContactsService.ContactEntry entry : entries) {
            ParticipantId participant = entry.getParticipantId();
            contactScores.put(participant, entry.getScore());
            if (!contacts.contains(participant)) {
              contacts.add(participant);
            }
          }
          // Sort by score descending (highest score first).
          Collections.sort(contacts, new Comparator<ParticipantId>() {
            @Override
            public int compare(ParticipantId p1, ParticipantId p2) {
              Double score1 = contactScores.get(p1);
              Double score2 = contactScores.get(p2);
              if (score1 == null) score1 = 0.0;
              if (score2 == null) score2 = 0.0;
              return score2.compareTo(score1);
            }
          });
          fireOnUpdated();
        }
      }
    });
  }
}

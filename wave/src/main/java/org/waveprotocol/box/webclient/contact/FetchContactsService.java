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

import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;

/**
 * Interface for fetching scored contacts from the server.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public interface FetchContactsService {

  /** Holds a single contact entry with its score. */
  public static class ContactEntry {
    private final ParticipantId participantId;
    private final double score;

    public ContactEntry(ParticipantId participantId, double score) {
      this.participantId = participantId;
      this.score = score;
    }

    public ParticipantId getParticipantId() {
      return participantId;
    }

    public double getScore() {
      return score;
    }
  }

  /** Callback for asynchronous contact fetching. */
  public interface Callback {
    void onFailure(String message);

    /**
     * Notifies this callback of a successful contact fetch.
     *
     * @param timestamp the server timestamp for incremental fetches
     * @param contacts the list of contacts with their scores
     */
    void onSuccess(long timestamp, List<ContactEntry> contacts);
  }

  /**
   * Fetches contacts from the server.
   *
   * @param timestamp the timestamp from the previous response, or 0 for all contacts
   * @param callback the callback to receive results
   */
  void fetch(long timestamp, Callback callback);
}

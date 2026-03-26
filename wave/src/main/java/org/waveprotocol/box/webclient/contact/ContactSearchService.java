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

import java.util.List;

/**
 * Interface for searching contacts on the server via the
 * {@code GET /contacts/search?q=<prefix>&limit=<n>} endpoint.
 */
public interface ContactSearchService {

  /** Holds a single search result entry. */
  public static class SearchResult {
    private final String participant;
    private final double score;
    private final long lastContact;

    public SearchResult(String participant, double score, long lastContact) {
      this.participant = participant;
      this.score = score;
      this.lastContact = lastContact;
    }

    public String getParticipant() {
      return participant;
    }

    public double getScore() {
      return score;
    }

    public long getLastContact() {
      return lastContact;
    }
  }

  /** Callback for asynchronous contact search. */
  public interface Callback {
    void onFailure(String message);

    /**
     * Notifies this callback of a successful search.
     *
     * @param results the list of matching contacts
     * @param total the total number of matches before truncation
     */
    void onSuccess(List<SearchResult> results, int total);
  }

  /**
   * Searches contacts on the server by prefix.
   *
   * @param prefix the prefix to match against contact addresses
   * @param limit the maximum number of results to return
   * @param callback the callback to receive results
   */
  void search(String prefix, int limit, Callback callback);
}

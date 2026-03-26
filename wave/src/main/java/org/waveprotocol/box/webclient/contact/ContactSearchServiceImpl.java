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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Searches contacts via the server's {@code GET /contacts/search?q=<prefix>&limit=<n>}
 * endpoint and parses the JSON response.
 *
 * <p>The response format is:
 * <pre>{
 *   "results": [
 *     {"participant": "alice@example.com", "score": 1711234567890.0, "lastContact": 1711234567890},
 *     ...
 *   ],
 *   "total": 42
 * }</pre>
 */
public class ContactSearchServiceImpl implements ContactSearchService {

  private static final LoggerBundle LOG = new DomLogger("ContactSearchService");

  /** The search endpoint URL base. */
  private static final String SEARCH_URL_BASE = "/contacts/search";

  public static ContactSearchServiceImpl create() {
    return new ContactSearchServiceImpl();
  }

  @Override
  public void search(String prefix, int limit, final Callback callback) {
    String encodedPrefix = (prefix != null) ? URL.encodeQueryString(prefix) : "";
    String url = SEARCH_URL_BASE + "?q=" + encodedPrefix + "&limit=" + limit;
    LOG.trace().log("Searching contacts, prefix=" + prefix + ", limit=" + limit);

    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);
    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
          return;
        }
        String contentType = response.getHeader("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
          callback.onFailure("Contact search service did not return JSON");
          return;
        }
        try {
          parseAndDeliver(response.getText(), callback);
        } catch (Exception e) {
          callback.onFailure("Failed to parse search response: " + e.getMessage());
        }
      }

      @Override
      public void onError(Request request, Throwable e) {
        callback.onFailure(e.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      LOG.error().log("Failed to send contact search request: " + e.getMessage());
      callback.onFailure(e.getMessage());
    }
  }

  /**
   * Parses the JSON response text and delivers results to the callback.
   */
  private void parseAndDeliver(String jsonText, Callback callback) {
    SearchResponseJso jso = parseJson(jsonText);
    int total = (int) jso.getTotal();
    JsArray<SearchResultJso> resultsArray = jso.getResults();

    List<SearchResult> results = new ArrayList<SearchResult>();
    if (resultsArray != null) {
      for (int i = 0; i < resultsArray.length(); i++) {
        SearchResultJso r = resultsArray.get(i);
        // Use String.valueOf() to coerce JSNI return values that may not be
        // true JS strings (e.g. if the server returns an unexpected type for
        // the "participant" field).  Without this, calling indexOf() on a
        // non-string produces "b.indexOf is not a function" in GWT-compiled JS.
        String raw = r.getParticipant();
        String participant = raw == null ? null : String.valueOf((Object) raw);
        if (participant == null || participant.isEmpty()
            || "undefined".equals(participant) || "null".equals(participant)) {
          LOG.trace().log("Skipping search result with missing participant");
          continue;
        }
        results.add(new SearchResult(participant, r.getScore(), (long) r.getLastContact()));
      }
    }
    callback.onSuccess(results, total);
  }

  /** Parses JSON text into a native JSO. */
  private static native SearchResponseJso parseJson(String json) /*-{
    return JSON.parse(json);
  }-*/;

  /** JSO overlay for the top-level search response object. */
  private static class SearchResponseJso extends JavaScriptObject {
    protected SearchResponseJso() {
    }

    public final native JsArray<SearchResultJso> getResults() /*-{
      return this.results || [];
    }-*/;

    public final native double getTotal() /*-{
      return this.total || 0;
    }-*/;
  }

  /** JSO overlay for a single search result entry. */
  private static class SearchResultJso extends JavaScriptObject {
    protected SearchResultJso() {
    }

    public final native String getParticipant() /*-{
      return this.participant;
    }-*/;

    public final native double getScore() /*-{
      return this.score || 0;
    }-*/;

    public final native double getLastContact() /*-{
      return this.lastContact || 0;
    }-*/;
  }
}

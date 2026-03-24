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

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches contacts from the server's {@code GET /contacts?timestamp=<ms>}
 * endpoint and parses the JSON response.
 *
 * <p>The response format is:
 * <pre>{
 *   "timestamp": 1711234567890,
 *   "contacts": [
 *     {"participant": "alice@example.com", "score": 1711234567890.0},
 *     ...
 *   ]
 * }</pre>
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com),
 * adapted to use plain JSON instead of proto-generated JSO.
 */
public class FetchContactsServiceImpl implements FetchContactsService {

  private static final LoggerBundle LOG = new DomLogger("FetchContactsService");

  /** The contacts endpoint URL. */
  private static final String CONTACTS_URL_BASE = "/contacts";

  public static FetchContactsServiceImpl create() {
    return new FetchContactsServiceImpl();
  }

  @Override
  public void fetch(long timestamp, final Callback callback) {
    String url = CONTACTS_URL_BASE + "?timestamp=" + timestamp;
    LOG.trace().log("Fetching contacts, timestamp=" + timestamp);

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
          callback.onFailure("Contacts service did not return JSON");
          return;
        }
        try {
          parseAndDeliver(response.getText(), callback);
        } catch (Exception e) {
          callback.onFailure("Failed to parse contacts response: " + e.getMessage());
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
      LOG.error().log("Failed to send contacts request: " + e.getMessage());
      callback.onFailure(e.getMessage());
    }
  }

  /**
   * Parses the JSON response text and delivers results to the callback.
   */
  private void parseAndDeliver(String jsonText, Callback callback) {
    ContactResponseJso jso = parseJson(jsonText);
    long timestamp = (long) jso.getTimestamp();
    JsArray<ContactJso> contactsArray = jso.getContacts();

    List<ContactEntry> contacts = new ArrayList<ContactEntry>();
    if (contactsArray != null) {
      for (int i = 0; i < contactsArray.length(); i++) {
        ContactJso c = contactsArray.get(i);
        // Use String.valueOf() to coerce JSNI return values that may not be
        // true JS strings (e.g. if the server returns an unexpected type for
        // the "participant" field).  Without this, calling indexOf() on a
        // non-string produces "b.indexOf is not a function" in GWT-compiled JS.
        String raw = c.getParticipant();
        String participantAddress = raw == null ? null : String.valueOf((Object) raw);
        if (participantAddress == null || participantAddress.isEmpty()
            || "undefined".equals(participantAddress) || "null".equals(participantAddress)) {
          LOG.trace().log("Skipping contact entry with missing participant");
          continue;
        }
        ParticipantId participantId = ParticipantId.ofUnsafe(participantAddress);
        contacts.add(new ContactEntry(participantId, c.getScore()));
      }
    }
    callback.onSuccess(timestamp, contacts);
  }

  /** Parses JSON text into a native JSO. */
  private static native ContactResponseJso parseJson(String json) /*-{
    return JSON.parse(json);
  }-*/;

  /** JSO overlay for the top-level response object. */
  private static class ContactResponseJso extends JavaScriptObject {
    protected ContactResponseJso() {
    }

    public final native double getTimestamp() /*-{
      return this.timestamp || 0;
    }-*/;

    public final native JsArray<ContactJso> getContacts() /*-{
      return this.contacts || [];
    }-*/;
  }

  /** JSO overlay for a single contact entry. */
  private static class ContactJso extends JavaScriptObject {
    protected ContactJso() {
    }

    public final native String getParticipant() /*-{
      return this.participant;
    }-*/;

    public final native double getScore() /*-{
      return this.score || 0;
    }-*/;
  }
}

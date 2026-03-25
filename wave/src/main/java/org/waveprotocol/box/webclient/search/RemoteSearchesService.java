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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages stored search patterns list on server via the /searches REST endpoint.
 *
 * Ported from Wiab.pro, adapted to use GWT JSONParser instead of
 * protobuf-generated JSO types which are not available upstream.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class RemoteSearchesService implements SearchesService {

  private static final LoggerBundle LOG = new DomLogger(RemoteSearchesService.class.getName());

  private static final String SEARCHES_URL_BASE = "/searches";

  public RemoteSearchesService() {
  }

  @Override
  public void storeSearches(List<SearchesItem> searches, final StoreCallback callback) {
    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.POST, SEARCHES_URL_BASE);
    requestBuilder.setHeader("Content-Type", "application/json");
    requestBuilder.setRequestData(serializeSearches(searches));

    LOG.trace().log("Store searches");

    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else {
          callback.onSuccess();
        }
      }

      @Override
      public void onError(Request request, Throwable exception) {
        LOG.error().log("Storing searches error: ", exception);
        callback.onFailure(exception.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      callback.onFailure(e.getMessage());
    }
  }

  @Override
  public void getSearches(final GetCallback callback) {
    String url = SEARCHES_URL_BASE;
    LOG.trace().log("Getting searches");

    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);

    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        LOG.trace().log("Searches was received: ", response.getText());
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else if (response.getHeader("Content-Type") == null
            || !response.getHeader("Content-Type").startsWith("application/json")) {
          callback.onFailure("Search service did not return json");
        } else {
          try {
            List<SearchesItem> searches = deserializeSearches(response.getText());
            callback.onSuccess(searches);
          } catch (Exception e) {
            callback.onFailure(e.getMessage());
          }
        }
      }

      @Override
      public void onError(Request request, Throwable exception) {
        LOG.error().log("Getting searches error: ", exception);
        callback.onFailure(exception.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      callback.onFailure(e.getMessage());
    }
  }

  private static String serializeSearches(List<SearchesItem> searches) {
    JSONArray array = new JSONArray();
    for (int i = 0; i < searches.size(); i++) {
      SearchesItem item = searches.get(i);
      JSONObject obj = new JSONObject();
      obj.put("name", new JSONString(item.getName() != null ? item.getName() : ""));
      obj.put("query", new JSONString(item.getQuery() != null ? item.getQuery() : ""));
      array.set(i, obj);
    }
    return array.toString();
  }

  private static List<SearchesItem> deserializeSearches(String json) {
    List<SearchesItem> searches = new ArrayList<SearchesItem>();
    JSONValue parsed = JSONParser.parseStrict(json);
    JSONArray array = parsed.isArray();
    if (array != null) {
      for (int i = 0; i < array.size(); i++) {
        JSONObject itemObj = array.get(i).isObject();
        if (itemObj != null) {
          String name = getStringField(itemObj, "name");
          String query = getStringField(itemObj, "query");
          searches.add(new SearchesItem(name, query));
        }
      }
    }
    return searches;
  }

  private static String getStringField(JSONObject obj, String field) {
    JSONValue val = obj.get(field);
    if (val != null) {
      JSONString str = val.isString();
      if (str != null) {
        return str.stringValue();
      }
    }
    return "";
  }
}

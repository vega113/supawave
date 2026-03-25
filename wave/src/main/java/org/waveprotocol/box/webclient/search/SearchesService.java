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

import java.util.List;

import org.waveprotocol.box.searches.SearchesItem;

/**
 * Asynchronous RPC to server /searches servlet.
 *
 * Ported from Wiab.pro.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface SearchesService {

  interface StoreCallback {

    void onFailure(String message);

    void onSuccess();
  }

  interface GetCallback {

    void onFailure(String message);

    void onSuccess(List<SearchesItem> searches);
  }

  public void storeSearches(List<SearchesItem> searches, final StoreCallback callback);

  public void getSearches(final GetCallback callback);
}

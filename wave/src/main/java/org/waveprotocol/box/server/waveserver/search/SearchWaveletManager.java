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

package org.waveprotocol.box.server.waveserver.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of per-user search wavelets: creation, lookup,
 * and cleanup.
 *
 * <p>Each user+query pair maps to a dedicated wavelet identified by:
 * <ul>
 *   <li>WaveId: {@code <domain>/search~<local-part>}</li>
 *   <li>WaveletId: {@code <domain>/search+<md5-hex-of-query>}</li>
 * </ul>
 *
 * <p>Search wavelets are purely virtual (in-memory metadata). They are not
 * persisted to the delta store; they exist as long as there is an active
 * subscription. The actual DocOp deltas are submitted to the wavelet
 * infrastructure by {@link SearchWaveletUpdater}.
 */
@Singleton
public class SearchWaveletManager {

  private static final Log LOG = Log.get(SearchWaveletManager.class);

  /** Prefix for search wave IDs (wave-level). */
  private static final String SEARCH_WAVE_PREFIX = "search~";

  /** Prefix for search wavelet IDs (wavelet-level). */
  private static final String SEARCH_WAVELET_PREFIX = "search+";

  /**
   * Fast lookup cache: "user|queryHash" -> WaveletName.
   * Avoids recomputing MD5 on every access.
   */
  private final ConcurrentHashMap<String, WaveletName> waveletNameCache =
      new ConcurrentHashMap<>();

  /**
   * Tracks which search wavelets have been initialised (i.e. have had their
   * initial empty document structure created). The value is the raw query string.
   */
  private final ConcurrentHashMap<String, String> initialisedWavelets =
      new ConcurrentHashMap<>();

  @Inject
  public SearchWaveletManager() {
  }

  /**
   * Returns the WaveletName for a user+query pair, creating the mapping if
   * it does not yet exist. This method is idempotent.
   *
   * @param user the participant whose search wavelet to manage
   * @param query the raw search query string
   * @return the stable WaveletName for this user+query combination
   */
  public WaveletName getOrCreateSearchWavelet(ParticipantId user, String query) {
    String cacheKey = cacheKey(user, query);
    WaveletName existing = waveletNameCache.get(cacheKey);
    if (existing != null) {
      return existing;
    }
    WaveletName computed = computeWaveletName(user, query);
    WaveletName prev = waveletNameCache.putIfAbsent(cacheKey, computed);
    if (prev != null) {
      return prev;
    }
    initialisedWavelets.putIfAbsent(cacheKey, query);
    LOG.info("Created search wavelet mapping for " + user.getAddress()
        + " query='" + query + "' -> " + computed);
    return computed;
  }

  /**
   * Computes the WaveletName for a user+query without creating or caching it.
   */
  public WaveletName computeWaveletName(ParticipantId user, String query) {
    String domain = user.getDomain();
    String localPart = localPart(user);
    String queryHash = md5Hex(query);
    WaveId waveId = WaveId.of(domain, SEARCH_WAVE_PREFIX + localPart);
    WaveletId waveletId = WaveletId.of(domain, SEARCH_WAVELET_PREFIX + queryHash);
    return WaveletName.of(waveId, waveletId);
  }

  /**
   * Checks whether a given WaveletName refers to a search wavelet.
   */
  public boolean isSearchWavelet(WaveletName waveletName) {
    String waveletIdStr = waveletName.waveletId.getId();
    return waveletIdStr.startsWith(SEARCH_WAVELET_PREFIX);
  }

  /**
   * Checks whether a given WaveletId refers to a search wavelet.
   */
  public boolean isSearchWavelet(WaveletId waveletId) {
    return waveletId.getId().startsWith(SEARCH_WAVELET_PREFIX);
  }

  /**
   * Removes the search wavelet mapping for a user+query pair.
   * Called when the user unsubscribes or disconnects.
   *
   * @param user the participant
   * @param query the raw search query string
   */
  public void removeSearchWavelet(ParticipantId user, String query) {
    String cacheKey = cacheKey(user, query);
    WaveletName removed = waveletNameCache.remove(cacheKey);
    initialisedWavelets.remove(cacheKey);
    if (removed != null) {
      LOG.info("Removed search wavelet mapping for " + user.getAddress()
          + " query='" + query + "'");
    }
  }

  /**
   * Returns the raw query string for an initialised search wavelet, or null
   * if not found.
   */
  public String getRawQuery(ParticipantId user, String queryHash) {
    // Walk initialised wavelets to find by user + hash
    String prefix = user.getAddress() + "|";
    for (java.util.Map.Entry<String, String> entry : initialisedWavelets.entrySet()) {
      if (entry.getKey().startsWith(prefix)) {
        String rawQuery = entry.getValue();
        if (md5Hex(rawQuery).equals(queryHash)) {
          return rawQuery;
        }
      }
    }
    return null;
  }

  /**
   * Returns the number of currently tracked search wavelets.
   */
  public int getActiveCount() {
    return waveletNameCache.size();
  }

  // ---- Internal helpers ----

  private static String cacheKey(ParticipantId user, String query) {
    return user.getAddress() + "|" + md5Hex(query);
  }

  private static String localPart(ParticipantId user) {
    String address = user.getAddress();
    int atIndex = address.indexOf('@');
    return atIndex >= 0 ? address.substring(0, atIndex) : address;
  }

  /**
   * Computes the MD5 hex digest of the given string. MD5 is used here purely
   * as a deterministic hash for wavelet ID generation -- not for security.
   */
  static String md5Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(32);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      // MD5 is guaranteed to be available in any Java implementation.
      throw new AssertionError("MD5 not available", e);
    }
  }
}

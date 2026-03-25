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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Client-side transport layer for the version history API. Communicates with
 * the VersionHistoryServlet and VersionedFetchServlet endpoints to fetch
 * delta history and versioned snapshots.
 *
 * <p>Provides client-side delta grouping (by author + time gap), snapshot
 * caching, and debounce support for scrubber movement.
 */
public final class HistoryApiClient {

  /** Callback for async operations. */
  public interface Callback<T> {
    void onSuccess(T result);
    void onFailure(String error);
  }

  /** Represents a single delta entry from the history API. */
  public static final class DeltaEntry {
    private final long appliedAt;
    private final long resultingVersion;
    private final String author;
    private final long timestamp;
    private final int opCount;

    public DeltaEntry(long appliedAt, long resultingVersion, String author,
        long timestamp, int opCount) {
      this.appliedAt = appliedAt;
      this.resultingVersion = resultingVersion;
      this.author = author;
      this.timestamp = timestamp;
      this.opCount = opCount;
    }

    public long getAppliedAt() { return appliedAt; }
    public long getResultingVersion() { return resultingVersion; }
    public String getAuthor() { return author; }
    public long getTimestamp() { return timestamp; }
    public int getOpCount() { return opCount; }
  }

  /** Represents a group of consecutive deltas by the same author within a time gap. */
  public static final class DeltaGroup {
    private final String author;
    private final long startVersion;
    private final long endVersion;
    private final long startTimestamp;
    private final long endTimestamp;
    private final int totalOps;
    private final int deltaCount;

    public DeltaGroup(String author, long startVersion, long endVersion,
        long startTimestamp, long endTimestamp, int totalOps, int deltaCount) {
      this.author = author;
      this.startVersion = startVersion;
      this.endVersion = endVersion;
      this.startTimestamp = startTimestamp;
      this.endTimestamp = endTimestamp;
      this.totalOps = totalOps;
      this.deltaCount = deltaCount;
    }

    public String getAuthor() { return author; }
    public long getStartVersion() { return startVersion; }
    public long getEndVersion() { return endVersion; }
    public long getStartTimestamp() { return startTimestamp; }
    public long getEndTimestamp() { return endTimestamp; }
    public int getTotalOps() { return totalOps; }
    public int getDeltaCount() { return deltaCount; }
  }

  /** Represents a snapshot with per-blip text content. */
  public static final class SnapshotData {
    private final long version;
    private final String creator;
    private final long lastModifiedTime;
    private final List<String> participants;
    private final List<BlipData> documents;

    public SnapshotData(long version, String creator, long lastModifiedTime,
        List<String> participants, List<BlipData> documents) {
      this.version = version;
      this.creator = creator;
      this.lastModifiedTime = lastModifiedTime;
      this.participants = participants;
      this.documents = documents;
    }

    public long getVersion() { return version; }
    public String getCreator() { return creator; }
    public long getLastModifiedTime() { return lastModifiedTime; }
    public List<String> getParticipants() { return participants; }
    public List<BlipData> getDocuments() { return documents; }
  }

  /** Represents a single blip document in a snapshot. */
  public static final class BlipData {
    private final String id;
    private final String author;
    private final long lastModified;
    private final String content;

    public BlipData(String id, String author, long lastModified, String content) {
      this.id = id;
      this.author = author;
      this.lastModified = lastModified;
      this.content = content;
    }

    public String getId() { return id; }
    public String getAuthor() { return author; }
    public long getLastModified() { return lastModified; }
    public String getContent() { return content; }
  }

  /** Default gap threshold for grouping deltas: 5 minutes in milliseconds. */
  private static final long DEFAULT_MAX_GAP_MS = 5 * 60 * 1000;

  /** Debounce delay for scrubber movement in milliseconds. */
  private static final int DEBOUNCE_DELAY_MS = 200;

  /** Cache of version -> snapshot data. */
  private final HashMap<Long, SnapshotData> snapshotCache = new HashMap<Long, SnapshotData>();

  /** Debounce timer for scrubber-driven fetches. */
  private Timer debounceTimer;

  /** The pending debounced action (version + callback). */
  private Callback<SnapshotData> pendingCallback;
  private long pendingVersion;
  private String pendingWaveDomain;
  private String pendingWaveId;
  private String pendingWaveletDomain;
  private String pendingWaveletId;

  public HistoryApiClient() {
  }

  /**
   * Fetches delta history from the VersionHistoryServlet and groups the results
   * client-side by author + time gap.
   *
   * @param waveDomain the wave domain
   * @param waveId the wave id
   * @param waveletDomain the wavelet domain
   * @param waveletId the wavelet id
   * @param maxGapMs maximum time gap in ms for grouping (use 0 for default)
   * @param callback receives the list of DeltaGroups
   */
  public void fetchGroups(String waveDomain, String waveId,
      String waveletDomain, String waveletId, long maxGapMs,
      final Callback<List<DeltaGroup>> callback) {

    final long gapThreshold = maxGapMs > 0 ? maxGapMs : DEFAULT_MAX_GAP_MS;

    String url = "/history/" + enc(waveDomain) + "/" + enc(waveId) + "/"
        + enc(waveletDomain) + "/" + enc(waveletId) + "/api/history";

    RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
    rb.setCallback(new RequestCallback() {
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("HTTP " + response.getStatusCode()
              + ": " + response.getStatusText());
          return;
        }
        try {
          List<DeltaEntry> deltas = parseDeltaArray(response.getText());
          List<DeltaGroup> groups = groupDeltas(deltas, gapThreshold);
          callback.onSuccess(groups);
        } catch (Exception e) {
          callback.onFailure("Parse error: " + e.getMessage());
        }
      }

      public void onError(Request request, Throwable exception) {
        callback.onFailure("Request failed: " + exception.getMessage());
      }
    });

    try {
      rb.send();
    } catch (RequestException e) {
      callback.onFailure("Send failed: " + e.getMessage());
    }
  }

  /**
   * Fetches a snapshot at a specific version from the VersionHistoryServlet.
   * Results are cached; subsequent requests for the same version return the
   * cached value immediately.
   *
   * @param waveDomain the wave domain
   * @param waveId the wave id
   * @param waveletDomain the wavelet domain
   * @param waveletId the wavelet id
   * @param version the target version
   * @param callback receives the SnapshotData
   */
  public void fetchSnapshot(String waveDomain, String waveId,
      String waveletDomain, String waveletId, long version,
      final Callback<SnapshotData> callback) {

    // Check cache first
    Long key = Long.valueOf(version);
    if (snapshotCache.containsKey(key)) {
      callback.onSuccess(snapshotCache.get(key));
      return;
    }

    String url = "/history/" + enc(waveDomain) + "/" + enc(waveId) + "/"
        + enc(waveletDomain) + "/" + enc(waveletId)
        + "/api/snapshot?version=" + version;

    RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
    rb.setCallback(new RequestCallback() {
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("HTTP " + response.getStatusCode()
              + ": " + response.getStatusText());
          return;
        }
        try {
          SnapshotData snap = parseSnapshot(response.getText());
          snapshotCache.put(Long.valueOf(snap.getVersion()), snap);
          callback.onSuccess(snap);
        } catch (Exception e) {
          callback.onFailure("Parse error: " + e.getMessage());
        }
      }

      public void onError(Request request, Throwable exception) {
        callback.onFailure("Request failed: " + exception.getMessage());
      }
    });

    try {
      rb.send();
    } catch (RequestException e) {
      callback.onFailure("Send failed: " + e.getMessage());
    }
  }

  /**
   * Debounced snapshot fetch. If called again within 200ms, the previous
   * request is cancelled and only the latest one fires.
   */
  public void fetchSnapshotDebounced(final String waveDomain, final String waveId,
      final String waveletDomain, final String waveletId,
      final long version, final Callback<SnapshotData> callback) {

    pendingWaveDomain = waveDomain;
    pendingWaveId = waveId;
    pendingWaveletDomain = waveletDomain;
    pendingWaveletId = waveletId;
    pendingVersion = version;
    pendingCallback = callback;

    if (debounceTimer != null) {
      debounceTimer.cancel();
    }
    debounceTimer = new Timer() {
      public void run() {
        fetchSnapshot(pendingWaveDomain, pendingWaveId,
            pendingWaveletDomain, pendingWaveletId,
            pendingVersion, pendingCallback);
      }
    };
    debounceTimer.schedule(DEBOUNCE_DELAY_MS);
  }

  /** Clears the snapshot cache. */
  public void clearCache() {
    snapshotCache.clear();
  }

  // =========================================================================
  // Parsing helpers
  // =========================================================================

  /** Parses the JSON array of delta entries from the history API response. */
  private List<DeltaEntry> parseDeltaArray(String json) {
    List<DeltaEntry> result = new ArrayList<DeltaEntry>();
    JSONValue parsed = JSONParser.parseStrict(json);
    JSONArray arr = parsed.isArray();
    if (arr == null) {
      return result;
    }
    for (int i = 0; i < arr.size(); i++) {
      JSONObject obj = arr.get(i).isObject();
      if (obj == null) continue;
      long appliedAt = (long) obj.get("appliedAt").isNumber().doubleValue();
      long resultingVersion = (long) obj.get("resultingVersion").isNumber().doubleValue();
      String author = obj.get("author").isString().stringValue();
      long timestamp = (long) obj.get("timestamp").isNumber().doubleValue();
      int opCount = (int) obj.get("opCount").isNumber().doubleValue();
      result.add(new DeltaEntry(appliedAt, resultingVersion, author, timestamp, opCount));
    }
    return result;
  }

  /** Parses a snapshot JSON response from the history API. */
  private SnapshotData parseSnapshot(String json) {
    JSONObject obj = JSONParser.parseStrict(json).isObject();
    long version = (long) obj.get("version").isNumber().doubleValue();
    String creator = obj.get("creator").isString().stringValue();
    long lastModifiedTime = (long) obj.get("lastModifiedTime").isNumber().doubleValue();

    List<String> participants = new ArrayList<String>();
    JSONArray partsArr = obj.get("participants").isArray();
    if (partsArr != null) {
      for (int i = 0; i < partsArr.size(); i++) {
        participants.add(partsArr.get(i).isString().stringValue());
      }
    }

    List<BlipData> documents = new ArrayList<BlipData>();
    JSONArray docsArr = obj.get("documents").isArray();
    if (docsArr != null) {
      for (int i = 0; i < docsArr.size(); i++) {
        JSONObject doc = docsArr.get(i).isObject();
        if (doc == null) continue;
        String id = doc.get("id").isString().stringValue();
        String author = doc.get("author").isString().stringValue();
        long lastModified = (long) doc.get("lastModified").isNumber().doubleValue();
        String content = doc.get("content").isString().stringValue();
        documents.add(new BlipData(id, author, lastModified, content));
      }
    }

    return new SnapshotData(version, creator, lastModifiedTime, participants, documents);
  }

  // =========================================================================
  // Client-side delta grouping
  // =========================================================================

  /**
   * Groups a list of deltas by author and time proximity. Consecutive deltas
   * by the same author within {@code maxGapMs} of each other are merged into
   * a single group.
   */
  private List<DeltaGroup> groupDeltas(List<DeltaEntry> deltas, long maxGapMs) {
    List<DeltaGroup> groups = new ArrayList<DeltaGroup>();
    if (deltas.isEmpty()) {
      return groups;
    }

    String currentAuthor = deltas.get(0).getAuthor();
    long startVersion = deltas.get(0).getAppliedAt();
    long endVersion = deltas.get(0).getResultingVersion();
    long startTimestamp = deltas.get(0).getTimestamp();
    long endTimestamp = deltas.get(0).getTimestamp();
    int totalOps = deltas.get(0).getOpCount();
    int deltaCount = 1;

    for (int i = 1; i < deltas.size(); i++) {
      DeltaEntry d = deltas.get(i);
      boolean sameAuthor = d.getAuthor().equals(currentAuthor);
      boolean withinGap = (d.getTimestamp() - endTimestamp) <= maxGapMs;

      if (sameAuthor && withinGap) {
        // Extend current group
        endVersion = d.getResultingVersion();
        endTimestamp = d.getTimestamp();
        totalOps += d.getOpCount();
        deltaCount++;
      } else {
        // Finalize current group, start new one
        groups.add(new DeltaGroup(currentAuthor, startVersion, endVersion,
            startTimestamp, endTimestamp, totalOps, deltaCount));

        currentAuthor = d.getAuthor();
        startVersion = d.getAppliedAt();
        endVersion = d.getResultingVersion();
        startTimestamp = d.getTimestamp();
        endTimestamp = d.getTimestamp();
        totalOps = d.getOpCount();
        deltaCount = 1;
      }
    }
    // Add the last group
    groups.add(new DeltaGroup(currentAuthor, startVersion, endVersion,
        startTimestamp, endTimestamp, totalOps, deltaCount));

    return groups;
  }

  /** URL-encodes a path component. */
  private static String enc(String s) {
    return URL.encodePathSegment(s);
  }
}

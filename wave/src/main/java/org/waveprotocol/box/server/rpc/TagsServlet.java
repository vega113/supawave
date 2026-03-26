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
package org.waveprotocol.box.server.rpc;

import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet that returns all tags across the authenticated user's waves as JSON.
 *
 * <p>Request: {@code GET /tags}
 * <p>Optional query parameter: {@code prefix} -- case-insensitive prefix filter.
 *
 * <p>Response (JSON):
 * <pre>{
 *   "tags": [
 *     {"name": "important", "count": 5},
 *     {"name": "todo", "count": 3},
 *     ...
 *   ]
 * }</pre>
 *
 * <p>Tags are sorted by frequency (most-used first), then alphabetically.
 */
@SuppressWarnings("serial")
@Singleton
public final class TagsServlet extends HttpServlet {

  private static final Log LOG = Log.get(TagsServlet.class);

  /** Cache tags per user for 2 minutes to avoid repeatedly scanning all wavelets. */
  private static final long CACHE_TTL_MS = 2 * 60 * 1000;

  private final SessionManager sessionManager;
  private final PerUserWaveViewProvider waveViewProvider;
  private final WaveletProvider waveletProvider;

  /** Simple per-user cache: participant address -> CacheEntry. */
  private final Map<String, CacheEntry> cache = new HashMap<>();

  private static final class CacheEntry {
    final List<TagCount> tags;
    final long createdAt;

    CacheEntry(List<TagCount> tags) {
      this.tags = tags;
      this.createdAt = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
    }
  }

  private static final class TagCount implements Comparable<TagCount> {
    final String name;
    final int count;

    TagCount(String name, int count) {
      this.name = name;
      this.count = count;
    }

    @Override
    public int compareTo(TagCount other) {
      // Higher count first, then alphabetical
      int cmp = Integer.compare(other.count, this.count);
      return cmp != 0 ? cmp : this.name.compareToIgnoreCase(other.name);
    }
  }

  @Inject
  public TagsServlet(SessionManager sessionManager,
                     PerUserWaveViewProvider waveViewProvider,
                     WaveletProvider waveletProvider) {
    this.sessionManager = sessionManager;
    this.waveViewProvider = waveViewProvider;
    this.waveletProvider = waveletProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String prefix = req.getParameter("prefix");
    if (prefix != null) {
      prefix = prefix.trim().toLowerCase();
    }

    List<TagCount> allTags = getTagsForUser(user);

    // Filter by prefix if provided
    List<TagCount> filtered;
    if (prefix != null && !prefix.isEmpty()) {
      final String pfx = prefix;
      filtered = new ArrayList<>();
      for (TagCount tc : allTags) {
        if (tc.name.toLowerCase().startsWith(pfx)) {
          filtered.add(tc);
        }
      }
    } else {
      filtered = allTags;
    }

    // Build JSON response
    JsonObject json = new JsonObject();
    JsonArray tagsArray = new JsonArray();
    for (TagCount tc : filtered) {
      JsonObject tagObj = new JsonObject();
      tagObj.addProperty("name", tc.name);
      tagObj.addProperty("count", tc.count);
      tagsArray.add(tagObj);
    }
    json.add("tags", tagsArray);

    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");
    resp.getWriter().append(json.toString());
  }

  private List<TagCount> getTagsForUser(ParticipantId user) {
    String key = user.getAddress();
    synchronized (cache) {
      CacheEntry entry = cache.get(key);
      if (entry != null && !entry.isExpired()) {
        return entry.tags;
      }
    }

    List<TagCount> tags = collectTagsFromWaves(user);

    synchronized (cache) {
      cache.put(key, new CacheEntry(tags));
    }
    return tags;
  }

  private List<TagCount> collectTagsFromWaves(ParticipantId user) {
    Map<String, Integer> tagCounts = new HashMap<>();

    try {
      Multimap<WaveId, WaveletId> userWaves = waveViewProvider.retrievePerUserWaveView(user);
      for (Map.Entry<WaveId, Collection<WaveletId>> entry : userWaves.asMap().entrySet()) {
        WaveId waveId = entry.getKey();
        for (WaveletId waveletId : entry.getValue()) {
          // Only look at conversation wavelets (conv+root) for tags
          if (!waveletId.getId().startsWith("conv+")) {
            continue;
          }
          try {
            WaveletName waveletName = WaveletName.of(waveId, waveletId);
            CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
            if (snapshot == null) {
              continue;
            }
            ReadableWaveletData waveletData = snapshot.snapshot;
            extractTags(waveletData, tagCounts);
          } catch (WaveServerException e) {
            LOG.warning("Failed to get snapshot for " + waveId + "/" + waveletId, e);
          }
        }
      }
    } catch (Exception e) {
      LOG.severe("Failed to collect tags for " + user.getAddress(), e);
    }

    List<TagCount> result = new ArrayList<>();
    for (Map.Entry<String, Integer> e : tagCounts.entrySet()) {
      result.add(new TagCount(e.getKey(), e.getValue()));
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Extracts tags from the "tags" document in a wavelet snapshot by walking
   * the DocInitialization with a cursor that looks for text within &lt;tag&gt; elements.
   */
  private void extractTags(ReadableWaveletData waveletData, Map<String, Integer> tagCounts) {
    ReadableBlipData tagsDoc = waveletData.getDocument(IdConstants.TAGS_DOC_ID);
    if (tagsDoc == null) {
      return;
    }
    try {
      DocInitialization docInit = tagsDoc.getContent().asOperation();
      if (docInit == null) {
        return;
      }
      final List<String> foundTags = new ArrayList<>();
      final boolean[] insideTag = {false};
      final StringBuilder tagText = new StringBuilder();

      docInit.apply(new DocInitializationCursor() {
        @Override
        public void elementStart(String type, org.waveprotocol.wave.model.document.operation.Attributes attrs) {
          if ("tag".equals(type)) {
            insideTag[0] = true;
            tagText.setLength(0);
          }
        }

        @Override
        public void elementEnd() {
          if (insideTag[0]) {
            String text = tagText.toString().trim();
            if (!text.isEmpty()) {
              foundTags.add(text);
            }
            insideTag[0] = false;
          }
        }

        @Override
        public void characters(String chars) {
          if (insideTag[0]) {
            tagText.append(chars);
          }
        }

        @Override
        public void annotationBoundary(
            org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
          // no-op
        }
      });

      for (String tag : foundTags) {
        tagCounts.merge(tag, 1, Integer::sum);
      }
    } catch (Exception e) {
      LOG.warning("Failed to extract tags from document", e);
    }
  }
}

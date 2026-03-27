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

import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes the DocOp diff between old and new search results, generating
 * the minimal mutation to update a search wavelet's document.
 *
 * <p>Also maintains a cache of current search results per search wavelet
 * so that diffs can be computed incrementally.
 *
 * <p><strong>Note on DocOp text handling:</strong> DocOp {@code characters()}
 * calls can be split by {@code annotationBoundary}. Always use
 * {@link StringBuilder} when accumulating text from DocOps. However, search
 * wavelets are attribute-only (no text content in result elements), so this
 * is low risk here. The caveat is noted for future maintainers.
 */
@Singleton
public class SearchWaveletDataProvider {

  private static final Log LOG = Log.get(SearchWaveletDataProvider.class);

  /**
   * Represents a single search result entry.
   */
  public static final class SearchResultEntry {
    private final String waveId;
    private final String title;
    private final String snippet;
    private final long modified;
    private final String creator;
    private final int participants;
    private final int unread;
    private final int blips;

    public SearchResultEntry(String waveId, String title, String snippet,
        long modified, String creator, int participants, int unread, int blips) {
      this.waveId = waveId;
      this.title = title;
      this.snippet = snippet;
      this.modified = modified;
      this.creator = creator;
      this.participants = participants;
      this.unread = unread;
      this.blips = blips;
    }

    public String getWaveId() { return waveId; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public long getModified() { return modified; }
    public String getCreator() { return creator; }
    public int getParticipants() { return participants; }
    public int getUnread() { return unread; }
    public int getBlips() { return blips; }

    /**
     * Checks whether this entry's attributes differ from another entry
     * with the same wave ID.
     */
    public boolean attributesDiffer(SearchResultEntry other) {
      if (other == null) return true;
      return !Objects.equals(title, other.title)
          || !Objects.equals(snippet, other.snippet)
          || modified != other.modified
          || !Objects.equals(creator, other.creator)
          || participants != other.participants
          || unread != other.unread
          || blips != other.blips;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SearchResultEntry)) return false;
      SearchResultEntry that = (SearchResultEntry) o;
      return modified == that.modified
          && participants == that.participants
          && unread == that.unread
          && blips == that.blips
          && Objects.equals(waveId, that.waveId)
          && Objects.equals(title, that.title)
          && Objects.equals(snippet, that.snippet)
          && Objects.equals(creator, that.creator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(waveId, title, snippet, modified, creator, participants, unread, blips);
    }
  }

  /**
   * Describes the diff between old and new search results.
   */
  public static final class SearchDiff {
    private final List<SearchResultEntry> added;
    private final List<SearchResultEntry> removed;
    private final List<SearchResultEntry> modified;
    private final DocOp docOp;

    SearchDiff(List<SearchResultEntry> added, List<SearchResultEntry> removed,
        List<SearchResultEntry> modified, DocOp docOp) {
      this.added = added;
      this.removed = removed;
      this.modified = modified;
      this.docOp = docOp;
    }

    public int getAddedCount() { return added.size(); }
    public int getRemovedCount() { return removed.size(); }
    public int getModifiedCount() { return modified.size(); }
    public DocOp getDocOp() { return docOp; }
  }

  /**
   * Cache of current search results per search wavelet. Keyed by
   * WaveletName serialisation.
   */
  private final ConcurrentHashMap<String, SearchWaveletState> resultCache =
      new ConcurrentHashMap<>();

  private static final class SearchWaveletState {
    private final List<SearchResultEntry> results;
    private final int totalCount;

    private SearchWaveletState(List<SearchResultEntry> results, int totalCount) {
      this.results = new ArrayList<>(results);
      this.totalCount = totalCount;
    }
  }

  @Inject
  public SearchWaveletDataProvider() {
  }

  /**
   * Returns the current cached results for a search wavelet.
   *
   * @param waveletName the search wavelet
   * @return the current result list, or an empty list if not cached
   */
  public List<SearchResultEntry> getCurrentResults(WaveletName waveletName) {
    String key = waveletNameKey(waveletName);
    SearchWaveletState state = resultCache.get(key);
    return state != null ? new ArrayList<>(state.results) : new ArrayList<>();
  }

  /**
   * Returns the cached total result count for a search wavelet.
   *
   * @param waveletName the search wavelet
   * @return the cached total count, or -1 if not cached
   */
  public int getCurrentTotal(WaveletName waveletName) {
    String key = waveletNameKey(waveletName);
    SearchWaveletState state = resultCache.get(key);
    return state != null ? state.totalCount : -1;
  }

  /**
   * Updates the cached results for a search wavelet.
   *
   * @param waveletName the search wavelet
   * @param results the new result list
   */
  public void updateCurrentResults(WaveletName waveletName, List<SearchResultEntry> results) {
    updateCurrentResults(waveletName, results, results.size());
  }

  /**
   * Updates the cached results and total count for a search wavelet.
   *
   * @param waveletName the search wavelet
   * @param results the new result list
   * @param totalCount the total matching result count
   */
  public void updateCurrentResults(WaveletName waveletName, List<SearchResultEntry> results,
      int totalCount) {
    String key = waveletNameKey(waveletName);
    resultCache.put(key, new SearchWaveletState(results, totalCount));
  }

  /**
   * Removes the cached results for a search wavelet.
   */
  public void clearResults(WaveletName waveletName) {
    resultCache.remove(waveletNameKey(waveletName));
  }

  /**
   * Computes the diff between old and new search results.
   *
   * @param oldResults the current result list in the search wavelet
   * @param newResults the desired result list
   * @return a SearchDiff describing the changes, or null if no changes
   */
  public SearchDiff computeDiff(List<SearchResultEntry> oldResults,
      List<SearchResultEntry> newResults) {
    return computeDiff(oldResults, oldResults != null ? oldResults.size() : 0, newResults,
        newResults != null ? newResults.size() : 0);
  }

  /**
   * Computes the diff between old and new search results and total counts.
   *
   * @param oldResults the current result list in the search wavelet
   * @param oldTotalCount the current total result count
   * @param newResults the desired result list
   * @param newTotalCount the desired total result count
   * @return a SearchDiff describing the changes, or null if no changes
   */
  public SearchDiff computeDiff(List<SearchResultEntry> oldResults, int oldTotalCount,
      List<SearchResultEntry> newResults, int newTotalCount) {
    if (oldResults == null) {
      oldResults = Collections.emptyList();
    }
    if (newResults == null) {
      newResults = Collections.emptyList();
    }

    int normalizedOldTotal = normalizeTotalCount(oldResults, oldTotalCount);
    int normalizedNewTotal = normalizeTotalCount(newResults, newTotalCount);
    boolean totalChanged = normalizedOldTotal != normalizedNewTotal;

    // Build maps keyed by waveId
    Map<String, SearchResultEntry> oldMap = new HashMap<>();
    for (SearchResultEntry entry : oldResults) {
      oldMap.put(entry.getWaveId(), entry);
    }
    Map<String, SearchResultEntry> newMap = new HashMap<>();
    for (SearchResultEntry entry : newResults) {
      newMap.put(entry.getWaveId(), entry);
    }

    // Compute three sets
    List<SearchResultEntry> added = new ArrayList<>();
    List<SearchResultEntry> removed = new ArrayList<>();
    List<SearchResultEntry> modified = new ArrayList<>();

    // Find removed entries (in old but not in new)
    Set<String> removedIds = new HashSet<>();
    for (SearchResultEntry oldEntry : oldResults) {
      if (!newMap.containsKey(oldEntry.getWaveId())) {
        removed.add(oldEntry);
        removedIds.add(oldEntry.getWaveId());
      }
    }

    // Find added and modified entries
    Set<String> addedIds = new HashSet<>();
    for (SearchResultEntry newEntry : newResults) {
      SearchResultEntry oldEntry = oldMap.get(newEntry.getWaveId());
      if (oldEntry == null) {
        added.add(newEntry);
        addedIds.add(newEntry.getWaveId());
      } else if (newEntry.attributesDiffer(oldEntry)) {
        modified.add(newEntry);
      }
    }

    // Also detect position changes (reordering)
    boolean positionsChanged = false;
    if (added.isEmpty() && removed.isEmpty()) {
      // Check if the order changed
      int minSize = Math.min(oldResults.size(), newResults.size());
      for (int i = 0; i < minSize; i++) {
        if (!oldResults.get(i).getWaveId().equals(newResults.get(i).getWaveId())) {
          positionsChanged = true;
          break;
        }
      }
    }

    // No changes at all
    if (added.isEmpty() && removed.isEmpty() && modified.isEmpty() && !positionsChanged
        && !totalChanged) {
      return null;
    }

    // Build the DocOp.
    // For simplicity, when there are structural changes (adds, removes, reorders),
    // we rebuild the entire document. For attribute-only changes (modified),
    // we generate targeted replaceAttributes ops.
    DocOp docOp = buildDocOp(oldResults, newResults, added, removed, modified, positionsChanged,
        totalChanged, normalizedNewTotal);

    return new SearchDiff(added, removed, modified, docOp);
  }

  /**
   * Builds a DocOp that transforms the old document into the new one.
   *
   * <p>The document structure is:
   * <pre>
   * &lt;body&gt;
   *   &lt;metadata query="..." updated="..." total="..."/&gt;
   *   &lt;results&gt;
   *     &lt;result id="..." title="..." .../&gt;
   *     ...
   *   &lt;/results&gt;
   * &lt;/body&gt;
   * </pre>
   *
   * <p>Each element (start + end) consumes 2 items in the document.
   * An empty element like {@code <metadata .../>} also consumes 2 items.
   */
  private DocOp buildDocOp(List<SearchResultEntry> oldResults,
      List<SearchResultEntry> newResults,
      List<SearchResultEntry> added, List<SearchResultEntry> removed,
      List<SearchResultEntry> modified, boolean positionsChanged, boolean totalChanged,
      int newTotalCount) {

    // For structural changes, we use a full rebuild strategy:
    // delete all old content, insert new content.
    // This is simpler and safer than computing positional retains
    // for the initial implementation. It can be optimised later
    // to emit minimal positional ops.
    if (!added.isEmpty() || !removed.isEmpty() || positionsChanged || totalChanged) {
      return buildFullRebuildDocOp(newResults, newTotalCount);
    }

    // Attribute-only changes: emit replaceAttributes for each modified entry.
    return buildAttributeUpdateDocOp(oldResults, newResults, modified);
  }

  /**
   * Builds a DocInitialization that represents the complete search wavelet
   * document from scratch. Used for initial creation and full rebuilds.
   */
  DocOp buildFullRebuildDocOp(List<SearchResultEntry> results) {
    return buildFullRebuildDocOp(results, results.size());
  }

  /**
   * Builds a DocInitialization that represents the complete search wavelet
   * document from scratch. Used for initial creation and full rebuilds.
   */
  DocOp buildFullRebuildDocOp(List<SearchResultEntry> results, int totalCount) {
    DocOpBuilder builder = new DocOpBuilder();

    // <body>
    builder.elementStart("body", new AttributesImpl());

    // <metadata query="..." updated="..." total="..."/>
    long now = System.currentTimeMillis();
    builder.elementStart("metadata", new AttributesImpl(
        "query", "",  // query is filled in by the caller context
        "total", String.valueOf(totalCount),
        "updated", String.valueOf(now)));
    builder.elementEnd(); // </metadata>

    // <results>
    builder.elementStart("results", new AttributesImpl());

    for (SearchResultEntry entry : results) {
      builder.elementStart("result", resultAttributes(entry));
      builder.elementEnd(); // </result>
    }

    builder.elementEnd(); // </results>
    builder.elementEnd(); // </body>

    return builder.buildUnchecked();
  }

  private static int normalizeTotalCount(List<SearchResultEntry> results, int totalCount) {
    return totalCount >= 0 ? totalCount : results.size();
  }

  /**
   * Builds a DocOp with replaceAttributes for modified entries only.
   * The document structure must be traversed with retain operations
   * to reach the correct positions.
   */
  private DocOp buildAttributeUpdateDocOp(List<SearchResultEntry> oldResults,
      List<SearchResultEntry> newResults, List<SearchResultEntry> modifiedEntries) {

    // Build a set of modified wave IDs for quick lookup
    Set<String> modifiedIds = new HashSet<>();
    Map<String, SearchResultEntry> oldMap = new HashMap<>();
    for (SearchResultEntry entry : modifiedEntries) {
      modifiedIds.add(entry.getWaveId());
    }
    for (SearchResultEntry entry : oldResults) {
      oldMap.put(entry.getWaveId(), entry);
    }

    DocOpBuilder builder = new DocOpBuilder();

    // Document structure (item counts):
    // elementStart("body") = 1 item
    // elementStart("metadata" ...) = 1, elementEnd = 1 => 2 items for metadata
    // elementStart("results") = 1 item
    // for each result: elementStart("result" ...) = 1, elementEnd = 1 => 2 items
    // elementEnd("results") = 1
    // elementEnd("body") = 1

    // Retain past <body> open + <metadata .../> + <results> open
    // = 1 + 2 + 1 = 4 items
    int retainCount = 4;

    for (int i = 0; i < oldResults.size(); i++) {
      SearchResultEntry oldEntry = oldResults.get(i);
      if (modifiedIds.contains(oldEntry.getWaveId())) {
        // Retain to this position
        if (retainCount > 0) {
          builder.retain(retainCount);
          retainCount = 0;
        }
        // Replace attributes on the elementStart
        SearchResultEntry newEntry = null;
        for (SearchResultEntry ne : newResults) {
          if (ne.getWaveId().equals(oldEntry.getWaveId())) {
            newEntry = ne;
            break;
          }
        }
        if (newEntry != null) {
          builder.replaceAttributes(resultAttributes(oldEntry), resultAttributes(newEntry));
          // Skip past the elementEnd of this result
          retainCount = 1;
        } else {
          // Should not happen in attribute-only mode
          retainCount += 2;
        }
      } else {
        // Skip past this unmodified result (elementStart + elementEnd)
        retainCount += 2;
      }
    }

    // Retain past </results> + </body>
    retainCount += 2;
    if (retainCount > 0) {
      builder.retain(retainCount);
    }

    return builder.buildUnchecked();
  }

  /**
   * Builds the Attributes for a result element.
   * Attributes must be in sorted order (AttributesImpl enforces this).
   */
  private static AttributesImpl resultAttributes(SearchResultEntry entry) {
    // AttributesImpl(String... pairs) expects key-value pairs in sorted key order
    return new AttributesImpl(
        "blips", String.valueOf(entry.getBlips()),
        "creator", entry.getCreator(),
        "id", entry.getWaveId(),
        "modified", String.valueOf(entry.getModified()),
        "participants", String.valueOf(entry.getParticipants()),
        "snippet", entry.getSnippet(),
        "title", entry.getTitle(),
        "unread", String.valueOf(entry.getUnread()));
  }

  private static String waveletNameKey(WaveletName name) {
    return name.waveId.serialise() + "/" + name.waveletId.serialise();
  }
}

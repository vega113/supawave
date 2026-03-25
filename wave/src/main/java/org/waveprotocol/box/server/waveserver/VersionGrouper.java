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

package org.waveprotocol.box.server.waveserver;

import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure logic for grouping consecutive wavelet deltas into {@link ChangeGroup}s.
 *
 * <p>Grouping rule: consecutive deltas from the same author whose timestamps
 * differ by no more than {@code maxGapMs} milliseconds are merged into a single
 * group. When the author changes or the time gap exceeds the threshold, a new
 * group begins.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class VersionGrouper {

  /** Default maximum gap between consecutive deltas to merge (30 seconds). */
  public static final long DEFAULT_MAX_GAP_MS = 30_000L;

  private VersionGrouper() {}

  /**
   * Groups a list of transformed wavelet deltas into change groups.
   *
   * @param deltas the deltas to group, assumed to be in version order
   * @param maxGapMs maximum timestamp gap (ms) between consecutive deltas
   *                 by the same author to be merged into one group
   * @return an ordered list of change groups
   */
  public static List<ChangeGroup> group(List<TransformedWaveletDelta> deltas, long maxGapMs) {
    List<ChangeGroup> groups = new ArrayList<>();
    if (deltas.isEmpty()) {
      return groups;
    }

    int groupId = 0;

    // State for the current group being built
    String currentAuthor = null;
    long groupStartVersion = 0;
    long groupEndVersion = 0;
    long groupTimestamp = 0;
    int groupOpCount = 0;
    Set<String> groupBlipIds = new LinkedHashSet<>();

    for (TransformedWaveletDelta delta : deltas) {
      String author = delta.getAuthor().getAddress();
      long timestamp = delta.getApplicationTimestamp();

      boolean startNewGroup;
      if (currentAuthor == null) {
        // First delta
        startNewGroup = true;
      } else if (!author.equals(currentAuthor)) {
        // Different author
        startNewGroup = true;
      } else if (timestamp - groupTimestamp > maxGapMs) {
        // Same author but gap too large
        startNewGroup = true;
      } else {
        startNewGroup = false;
      }

      if (startNewGroup && currentAuthor != null) {
        // Flush the current group
        groups.add(new ChangeGroup(groupId++, currentAuthor, groupStartVersion,
            groupEndVersion, groupTimestamp, new ArrayList<>(groupBlipIds), groupOpCount));
        groupBlipIds.clear();
        groupOpCount = 0;
      }

      if (startNewGroup) {
        currentAuthor = author;
        groupStartVersion = delta.getAppliedAtVersion();
        groupTimestamp = timestamp;
      }

      // Extend group
      groupEndVersion = delta.getResultingVersion().getVersion();
      groupTimestamp = timestamp; // always the latest timestamp in the group
      groupOpCount += delta.size();
      extractBlipIds(delta, groupBlipIds);
    }

    // Flush final group
    if (currentAuthor != null) {
      groups.add(new ChangeGroup(groupId, currentAuthor, groupStartVersion,
          groupEndVersion, groupTimestamp, new ArrayList<>(groupBlipIds), groupOpCount));
    }

    return groups;
  }

  /**
   * Convenience overload using the default 30-second gap.
   */
  public static List<ChangeGroup> group(List<TransformedWaveletDelta> deltas) {
    return group(deltas, DEFAULT_MAX_GAP_MS);
  }

  /**
   * Extracts the blip (document) IDs affected by operations in the given delta.
   * Only {@link WaveletBlipOperation}s carry a blip ID; participant-level ops
   * (AddParticipant, RemoveParticipant, NoOp) do not affect any blip.
   *
   * @param delta the delta to inspect
   * @param out   set to which extracted blip IDs are added
   */
  public static void extractBlipIds(TransformedWaveletDelta delta, Set<String> out) {
    for (WaveletOperation op : delta) {
      if (op instanceof WaveletBlipOperation) {
        out.add(((WaveletBlipOperation) op).getBlipId());
      }
    }
  }

  /**
   * Checks whether a delta contains only "metadata" operations (participant
   * changes, no-ops) and no blip content modifications. Useful for filtering
   * out groups that don't affect document content.
   *
   * @param delta the delta to inspect
   * @return true if the delta has no blip operations
   */
  public static boolean isMetadataOnly(TransformedWaveletDelta delta) {
    for (WaveletOperation op : delta) {
      if (op instanceof WaveletBlipOperation) {
        return false;
      }
    }
    return true;
  }
}

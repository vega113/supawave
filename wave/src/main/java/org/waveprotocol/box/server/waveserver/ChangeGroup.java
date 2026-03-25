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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data class representing a group of consecutive deltas from the same
 * author within a configurable time gap. Used by {@link VersionGrouper} to
 * collapse fine-grained deltas into human-readable "change groups" for the
 * version history UI.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id} - sequential group index (0-based)</li>
 *   <li>{@code author} - the participant address that authored all deltas in this group</li>
 *   <li>{@code startVersion} - the appliedAt version of the first delta in the group</li>
 *   <li>{@code endVersion} - the resulting version of the last delta in the group</li>
 *   <li>{@code timestamp} - the application timestamp of the last delta (most recent)</li>
 *   <li>{@code blipIds} - distinct blip document IDs modified by ops in this group</li>
 *   <li>{@code opCount} - total number of operations across all deltas in the group</li>
 * </ul>
 */
public final class ChangeGroup {

  private final int id;
  private final String author;
  private final long startVersion;
  private final long endVersion;
  private final long timestamp;
  private final List<String> blipIds;
  private final int opCount;

  public ChangeGroup(int id, String author, long startVersion, long endVersion,
      long timestamp, List<String> blipIds, int opCount) {
    this.id = id;
    this.author = author;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.timestamp = timestamp;
    this.blipIds = Collections.unmodifiableList(blipIds);
    this.opCount = opCount;
  }

  public int getId() {
    return id;
  }

  public String getAuthor() {
    return author;
  }

  public long getStartVersion() {
    return startVersion;
  }

  public long getEndVersion() {
    return endVersion;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public List<String> getBlipIds() {
    return blipIds;
  }

  public int getOpCount() {
    return opCount;
  }

  /**
   * Serializes this change group to a JSON object suitable for API responses.
   */
  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", id);
    obj.addProperty("author", author);
    obj.addProperty("startVersion", startVersion);
    obj.addProperty("endVersion", endVersion);
    obj.addProperty("timestamp", timestamp);
    obj.addProperty("opCount", opCount);

    JsonArray blips = new JsonArray();
    for (String blipId : blipIds) {
      blips.add(blipId);
    }
    obj.add("blipIds", blips);

    return obj;
  }

  @Override
  public String toString() {
    return "ChangeGroup{id=" + id + ", author=" + author
        + ", versions=" + startVersion + ".." + endVersion
        + ", timestamp=" + timestamp + ", opCount=" + opCount
        + ", blipIds=" + blipIds + "}";
  }
}

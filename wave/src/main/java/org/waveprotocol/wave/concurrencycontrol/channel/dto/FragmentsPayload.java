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
package org.waveprotocol.wave.concurrencycontrol.channel.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.waveprotocol.wave.model.id.SegmentId;

/**
 * DTO carrying server-side fragments window information.
 * Minimal by design: client can use it to request/apply fragments.
 */
public final class FragmentsPayload {
  public static final class Range {
    public final SegmentId segment;
    public final long from;
    public final long to;
    public Range(SegmentId segment, long from, long to) {
      this.segment = segment; this.from = from; this.to = to;
    }
  }

  public static final class Operation {
    public final String operations;
    public final String author;
    public final long targetVersion;
    public final long timestamp;

    public Operation(String operations, String author, long targetVersion, long timestamp) {
      this.operations = Objects.requireNonNull(operations, "operations");
      this.author = author;
      this.targetVersion = targetVersion;
      this.timestamp = timestamp;
    }
  }

  public static final class Fragment {
    public final SegmentId segment;
    public final String rawSnapshot;
    public final int bodyItemCount;
    public final List<Operation> adjustOperations;
    public final List<Operation> diffOperations;

    public Fragment(SegmentId segment, String rawSnapshot,
        List<Operation> adjustOperations, List<Operation> diffOperations) {
      this(segment, rawSnapshot, rawSnapshot == null ? 0 : rawSnapshot.length(),
          adjustOperations, diffOperations);
    }

    public Fragment(SegmentId segment, String rawSnapshot, int bodyItemCount,
        List<Operation> adjustOperations, List<Operation> diffOperations) {
      this.segment = Objects.requireNonNull(segment, "segment");
      this.rawSnapshot = rawSnapshot;
      this.bodyItemCount = Math.max(0, bodyItemCount);
      this.adjustOperations = Collections.unmodifiableList(new ArrayList<>(adjustOperations));
      this.diffOperations = Collections.unmodifiableList(new ArrayList<>(diffOperations));
    }
  }

  public final long snapshotVersion;
  public final long startVersion;
  public final long endVersion;
  public final List<Range> ranges;
  public final List<Fragment> fragments;

  private FragmentsPayload(long snapshotVersion, long startVersion, long endVersion,
      List<Range> ranges, List<Fragment> fragments) {
    this.snapshotVersion = snapshotVersion;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
    this.fragments = Collections.unmodifiableList(new ArrayList<>(fragments));
  }

  public static FragmentsPayload of(long snapshotVersion, long startVersion, long endVersion,
      List<Range> ranges, List<Fragment> fragments) {
    return new FragmentsPayload(snapshotVersion, startVersion, endVersion, ranges,
        fragments == null ? Collections.<Fragment>emptyList() : fragments);
  }

  public static FragmentsPayload of(long snapshotVersion, long startVersion, long endVersion,
      List<Range> ranges) {
    return new FragmentsPayload(snapshotVersion, startVersion, endVersion, ranges,
        Collections.<Fragment>emptyList());
  }
}

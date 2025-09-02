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
package org.waveprotocol.box.server.frontend;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;

/** Compat FragmentsRequest builder mirroring wiab.pro shape. */
public final class FragmentsRequest {
  public static final long NO_VERSION = -1L;

  public static final class Builder {
    private final ImmutableMap.Builder<SegmentId, VersionRange> ranges = ImmutableMap.builder();
    private long startVersion = NO_VERSION;
    private long endVersion = NO_VERSION;

    public Builder addRange(SegmentId id, VersionRange vr) { ranges.put(id, vr); return this; }
    public Builder addRange(SegmentId id, long v) { ranges.put(id, VersionRange.of(v, v)); return this; }
    public Builder addRanges(Map<SegmentId, VersionRange> m) { ranges.putAll(m); return this; }
    public Builder setStartVersion(long v) { startVersion = v; return this; }
    public Builder setEndVersion(long v) { endVersion = v; return this; }
    public FragmentsRequest build() { return new FragmentsRequest(ranges.build(), startVersion, endVersion); }
  }

  public final ImmutableMap<SegmentId, VersionRange> ranges;
  public final long startVersion;
  public final long endVersion;

  private FragmentsRequest(ImmutableMap<SegmentId, VersionRange> r, long s, long e) {
    this.ranges = r; this.startVersion = s; this.endVersion = e;
  }
}

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
package org.waveprotocol.wave.model.id;

import java.io.Serializable;

/** Minimal SegmentId compatible with wiab.pro naming. */
public final class SegmentId implements Comparable<SegmentId>, Serializable {
  private static final long serialVersionUID = 1L;

  public static final SegmentId INDEX_ID = new SegmentId("index");
  public static final SegmentId MANIFEST_ID = new SegmentId("manifest");
  public static final SegmentId PARTICIPANTS_ID = new SegmentId("participants");
  public static final SegmentId TAGS_ID = new SegmentId("tags");

  private final String id;

  private SegmentId(String id) { this.id = id; }

  public static SegmentId ofBlipId(String blipId) { return new SegmentId("blip:" + blipId); }

  public boolean isBlip() { return id.startsWith("blip:"); }

  public String asString() { return id; }

  @Override public int compareTo(SegmentId o) { return this.id.compareTo(o.id); }
  @Override public boolean equals(Object o) { return (o instanceof SegmentId) && ((SegmentId)o).id.equals(id); }
  @Override public int hashCode() { return id.hashCode(); }
  @Override public String toString() { return id; }
}

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
package org.waveprotocol.box.server.persistence.blocks;

public final class VersionRange {
  private final long from;
  private final long to;

  private VersionRange(long from, long to) {
    if (from > to) throw new IllegalArgumentException("from > to");
    this.from = from; this.to = to;
  }
  public static VersionRange of(long from, long to) { return new VersionRange(from, to); }
  public long from() { return from; }
  public long to() { return to; }
  @Override public String toString() { return "["+from+","+to+"]"; }
}

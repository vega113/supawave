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

import java.util.Collections;
import java.util.Map;

/** Minimal fragment range DTO for client appliers. */
public final class RawFragment {
  public final String segment; // e.g., "index", "manifest", or "blip:b+..."
  public final long from;
  public final long to;
  public final Map<String, String> meta;

  public RawFragment(String segment, long from, long to) {
    this(segment, from, to, Collections.emptyMap());
  }

  public RawFragment(String segment, long from, long to, Map<String, String> meta) {
    this.segment = segment;
    this.from = from;
    this.to = to;
    this.meta = (meta == null) ? Collections.emptyMap() : Collections.unmodifiableMap(meta);
  }
}


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
 * specific language governing permissions and limitations under the
 * License.
 */
package org.waveprotocol.box.server.waveserver;

import java.util.Locale;
import org.waveprotocol.wave.model.wave.ParticipantId;

/** Utility methods for normalizing `from:` query values. */
public final class FromQueryNormalizer {

  private FromQueryNormalizer() {
  }

  /**
   * Normalizes a raw from token to the canonical lower-case participant address.
   *
   * @param raw raw query token value
   * @param user current user, used to resolve `me` and local-domain names
   * @return canonical lower-case participant address
   */
  public static String normalize(String raw, ParticipantId user) {
    if (raw == null || raw.trim().isEmpty()) {
      throw new IllegalArgumentException("raw from value cannot be null or empty");
    }
    if (user == null) {
      throw new IllegalArgumentException("user cannot be null");
    }
    String address = user.getAddress();
    String domain = user.getDomain();
    if (address == null || domain == null) {
      throw new IllegalArgumentException("user address and domain cannot be null");
    }

    String trimmed = raw.trim();
    String normalized;
    if ("me".equalsIgnoreCase(trimmed)) {
      normalized = address;
    } else if (trimmed.contains("@")) {
      normalized = trimmed;
    } else {
      normalized = trimmed + "@" + domain;
    }
    return normalized.toLowerCase(Locale.ROOT);
  }
}

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

package org.waveprotocol.wave.model.util;

/**
 * Helpers for thread-navigation metadata carried inside the browser history token.
 */
public final class ThreadNavigationHistory {
  public static final String HISTORY_PARAM_FOCUS = "focus";
  public static final String HISTORY_PARAM_DEPTH = "slide-nav";

  private ThreadNavigationHistory() {
  }

  public static String appendMetadata(String baseToken, String encodedFocusedBlipId, int depth) {
    String normalizedBase = stripMetadata(baseToken);
    StringBuilder token = new StringBuilder(normalizedBase == null ? "" : normalizedBase);

    if (encodedFocusedBlipId != null && !encodedFocusedBlipId.isEmpty()) {
      appendParam(token, HISTORY_PARAM_FOCUS, encodedFocusedBlipId);
    }
    if (depth > 0) {
      appendParam(token, HISTORY_PARAM_DEPTH, String.valueOf(depth));
    }
    return token.toString();
  }

  public static boolean hasMetadata(String token) {
    return token != null && token.indexOf('&') >= 0;
  }

  public static String stripMetadata(String token) {
    if (token == null || token.isEmpty()) {
      return token;
    }
    int metadataStart = token.indexOf('&');
    return metadataStart >= 0 ? token.substring(0, metadataStart) : token;
  }

  public static String extractParam(String token, String key) {
    if (token == null || token.isEmpty()) {
      return null;
    }

    String[] parts = token.split("&");
    for (String part : parts) {
      int idx = part.indexOf('=');
      if (idx <= 0) {
        continue;
      }
      if (key.equals(part.substring(0, idx))) {
        return part.substring(idx + 1);
      }
    }
    return null;
  }

  private static void appendParam(StringBuilder token, String key, String value) {
    if (token.length() > 0) {
      token.append("&");
    }
    token.append(key).append("=").append(value);
  }
}

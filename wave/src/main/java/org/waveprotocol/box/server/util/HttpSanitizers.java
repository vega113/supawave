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
package org.waveprotocol.box.server.util;

/**
 * Small helpers to sanitize values used in HTTP response headers to prevent
 * header injection / response splitting.
 */
public final class HttpSanitizers {
  private HttpSanitizers() {}

  /**
   * Removes CR/LF and other ASCII control characters (except TAB) that could
   * break a header line. Returns null if input is null.
   */
  public static String stripHeaderBreakingChars(String value) {
    if (value == null) return null;
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      // allow HT (0x09), visible chars 0x20..0x7E, and extended Unicode;
      // drop CR (0x0D), LF (0x0A) and other C0 controls
      if ((c >= 0x20 && c != 0x7F) || c == '\t' || c > 0x7F) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Validates that a redirect target is a safe relative path for this origin.
   * - must start with a single '/'
   * - must not start with '//'
   * - must not contain CR/LF or backslashes
   */
  public static boolean isSafeRelativeRedirect(String path) {
    if (path == null) return false;
    if (!path.startsWith("/")) return false;
    if (path.startsWith("//")) return false; // protocol-relative external URL
    if (path.indexOf('\\') >= 0) return false;
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\r' || c == '\n') return false;
    }
    return true;
  }
}


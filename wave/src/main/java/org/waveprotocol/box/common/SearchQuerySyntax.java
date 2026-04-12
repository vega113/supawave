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

package org.waveprotocol.box.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared query token encoding/parsing rules used by the search UI and server parser.
 */
public final class SearchQuerySyntax {

  /**
   * Encodes a token value so it survives round-tripping through the search parser.
   */
  public static String serializeTokenValue(String value) {
    if (value == null) {
      return "\"\"";
    }
    if (!needsQuoting(value)) {
      return value;
    }
    StringBuilder escaped = new StringBuilder(value.length() + 2);
    escaped.append('"');
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '"' || ch == '\\') {
        escaped.append('\\');
      }
      escaped.append(ch);
    }
    escaped.append('"');
    return escaped.toString();
  }

  /**
   * Splits a query into whitespace-delimited tokens while preserving quoted spans.
   */
  public static List<String> tokenize(String query) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    boolean escaping = false;
    for (int i = 0; i < query.length(); i++) {
      char ch = query.charAt(i);
      if (inQuotes) {
        current.append(ch);
        if (escaping) {
          escaping = false;
        } else if (ch == '\\') {
          escaping = true;
        } else if (ch == '"') {
          inQuotes = false;
        }
        continue;
      }
      if (Character.isWhitespace(ch)) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
      if (ch == '"') {
        inQuotes = true;
      }
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  /**
   * Decodes a possibly quoted token value back to its literal contents.
   */
  public static String decodeTokenValue(String tokenValue) {
    if (tokenValue.length() >= 2
        && tokenValue.charAt(0) == '"'
        && tokenValue.charAt(tokenValue.length() - 1) == '"') {
      return unescapeQuoted(tokenValue.substring(1, tokenValue.length() - 1));
    }
    return tokenValue;
  }

  private static boolean needsQuoting(String value) {
    if (value.isEmpty()) {
      return true;
    }
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isWhitespace(ch) || ch == '"' || ch == '\\') {
        return true;
      }
    }
    return false;
  }

  private static String unescapeQuoted(String value) {
    StringBuilder decoded = new StringBuilder(value.length());
    boolean escaping = false;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (escaping) {
        if (ch == '"' || ch == '\\') {
          decoded.append(ch);
        } else {
          decoded.append('\\').append(ch);
        }
        escaping = false;
        continue;
      }
      if (ch == '\\') {
        escaping = true;
      } else {
        decoded.append(ch);
      }
    }
    if (escaping) {
      decoded.append('\\');
    }
    return decoded.toString();
  }

  private SearchQuerySyntax() {
  }
}

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
package org.waveprotocol.box.server.waveserver.lucene9;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.waveprotocol.box.server.waveserver.TokenQueryType;

public final class Lucene9QueryModel {

  public static final class Token {
    private final TokenQueryType type;
    private final String value;

    Token(TokenQueryType type, String value) {
      this.type = type;
      this.value = value;
    }

    public TokenQueryType getType() {
      return type;
    }

    public String getValue() {
      return value;
    }
  }

  private final List<Token> tokens;

  Lucene9QueryModel(List<Token> tokens) {
    this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
  }

  public List<Token> getTokens() {
    return tokens;
  }

  public List<String> values(TokenQueryType type) {
    List<String> values = new ArrayList<>();
    for (Token token : tokens) {
      if (token.getType() == type) {
        values.add(token.getValue());
      }
    }
    return values;
  }

  public boolean hasToken(TokenQueryType type) {
    for (Token token : tokens) {
      if (token.getType() == type) {
        return true;
      }
    }
    return false;
  }

  public boolean hasTextQuery() {
    return hasToken(TokenQueryType.TITLE) || hasToken(TokenQueryType.CONTENT);
  }

  public boolean usesLuceneIndex() {
    return hasToken(TokenQueryType.TITLE)
        || hasToken(TokenQueryType.CONTENT)
        || hasToken(TokenQueryType.TAG);
  }

  public boolean hasTaskQuery() {
    return hasToken(TokenQueryType.TASKS);
  }

  public String toLegacyQuery() {
    StringBuilder builder = new StringBuilder();
    for (Token token : tokens) {
      if (isLuceneHandledToken(token.getType())) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(' ');
      }
      String value = token.getValue();
      boolean needsQuoting = value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0;
      builder.append(token.getType().name().toLowerCase(Locale.ROOT))
          .append(':');
      if (needsQuoting) {
        builder.append('"').append(value).append('"');
      } else {
        builder.append(value);
      }
    }
    return builder.toString();
  }

  private static boolean isLuceneHandledToken(TokenQueryType type) {
    return type == TokenQueryType.TITLE
        || type == TokenQueryType.CONTENT;
  }
}

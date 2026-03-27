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

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.waveprotocol.box.server.waveserver.QueryHelper.InvalidQueryException;
import org.waveprotocol.box.server.waveserver.TokenQueryType;

@Singleton
public class Lucene9QueryParser {

  public Lucene9QueryModel parse(String query) throws InvalidQueryException {
    List<Lucene9QueryModel.Token> tokens = new ArrayList<>();
    int index = 0;
    while (index < query.length()) {
      index = skipWhitespace(query, index);
      if (index >= query.length()) {
        break;
      }
      int colonIndex = query.indexOf(':', index);
      if (colonIndex <= index) {
        throw new InvalidQueryException("Invalid query token near: " + query.substring(index));
      }
      String tokenName = query.substring(index, colonIndex);
      if (!TokenQueryType.hasToken(tokenName)) {
        throw new InvalidQueryException("Illegal query param: " + tokenName);
      }
      TokenQueryType type = TokenQueryType.fromToken(tokenName);
      int valueStart = colonIndex + 1;
      if (valueStart >= query.length()) {
        throw new InvalidQueryException("Missing value for token: " + tokenName);
      }
      String value;
      int nextIndex;
      if (query.charAt(valueStart) == '"') {
        int closingQuote = query.indexOf('"', valueStart + 1);
        if (closingQuote < 0) {
          throw new InvalidQueryException("Unterminated quote for token: " + tokenName);
        }
        value = query.substring(valueStart + 1, closingQuote);
        nextIndex = closingQuote + 1;
      } else {
        int valueEnd = valueStart;
        while (valueEnd < query.length() && !Character.isWhitespace(query.charAt(valueEnd))) {
          valueEnd++;
        }
        value = query.substring(valueStart, valueEnd);
        nextIndex = valueEnd;
      }
      tokens.add(new Lucene9QueryModel.Token(type, value));
      index = nextIndex;
    }
    return new Lucene9QueryModel(tokens);
  }

  private static int skipWhitespace(String query, int index) {
    int current = index;
    while (current < query.length() && Character.isWhitespace(query.charAt(current))) {
      current++;
    }
    return current;
  }
}

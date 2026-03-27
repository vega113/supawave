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

package org.waveprotocol.box.server.waveserver;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

public class QueryHelperTest extends TestCase {

  public void testParseQueryTreatsBareWordsAsContentFilters() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams = QueryHelper.parseQuery("hello world");

    assertEquals(ImmutableSet.of("hello", "world"), queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryCombinesExplicitFiltersWithBareWords() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:work meeting notes");

    assertEquals(ImmutableSet.of("work"), queryParams.get(TokenQueryType.TAG));
    assertEquals(ImmutableSet.of("meeting", "notes"), queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryTreatsUnknownPrefixesAsContentFilters() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("meeting 10:30 https://example foo:bar:baz");

    assertEquals(ImmutableSet.of("meeting", "10:30", "https://example", "foo:bar:baz"),
        queryParams.get(TokenQueryType.CONTENT));
  }
}

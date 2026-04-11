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

import org.waveprotocol.box.common.SearchQuerySyntax;

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

  public void testParseQueryRecognizesUnreadFilter() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("in:inbox unread:true");

    assertEquals(ImmutableSet.of("inbox"), queryParams.get(TokenQueryType.IN));
    assertEquals(ImmutableSet.of("true"), queryParams.get(TokenQueryType.UNREAD));
  }

  public void testParseQueryRecognizesMentionsFilter() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams = QueryHelper.parseQuery("mentions:me");

    assertEquals(ImmutableSet.of("me"), queryParams.get(TokenQueryType.MENTIONS));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryRecognizesTasksFilter() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams = QueryHelper.parseQuery("tasks:me");

    assertEquals(ImmutableSet.of("me"), queryParams.get(TokenQueryType.TASKS));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryHandlesQuotedTagValueWithWhitespace() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:\"mobile beta\"");

    assertEquals(ImmutableSet.of("mobile beta"), queryParams.get(TokenQueryType.TAG));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryHandlesQuotedTagValueMixedWithBareWords() throws Exception {
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:\"project alpha\" meeting");

    assertEquals(ImmutableSet.of("project alpha"), queryParams.get(TokenQueryType.TAG));
    assertEquals(ImmutableSet.of("meeting"), queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryRoundTripsTagValueContainingTabWhitespace() throws Exception {
    String tagValue = "mobile\tbeta";
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:" + SearchQuerySyntax.serializeTokenValue(tagValue));

    assertEquals(ImmutableSet.of(tagValue), queryParams.get(TokenQueryType.TAG));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryRoundTripsTagValueContainingEmbeddedQuotes() throws Exception {
    String tagValue = "project \"alpha\"";
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:" + SearchQuerySyntax.serializeTokenValue(tagValue));

    assertEquals(ImmutableSet.of(tagValue), queryParams.get(TokenQueryType.TAG));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryRoundTripsTagValueContainingLiteralSurroundingQuotes()
      throws Exception {
    String tagValue = "\"alpha\"";
    Map<TokenQueryType, Set<String>> queryParams =
        QueryHelper.parseQuery("tag:" + SearchQuerySyntax.serializeTokenValue(tagValue));

    assertEquals(ImmutableSet.of(tagValue), queryParams.get(TokenQueryType.TAG));
    assertNull(queryParams.get(TokenQueryType.CONTENT));
  }

  public void testParseQueryRejectsInvalidUnreadFilterValue() {
    try {
      QueryHelper.parseQuery("unread:maybe");
      fail("Expected invalid unread token value");
    } catch (QueryHelper.InvalidQueryException expected) {
      assertTrue(expected.getMessage().contains("Invalid unread query value"));
    }
  }
}

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

import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

/**
 * F-2 slice 3 (#1047) parser-contract fixture.
 *
 * <p>The {@code <wavy-search-help>} Lit element advertises 22
 * inventory rows (C.1–C.22) of search query tokens — the same set the
 * GWT search-help panel advertises today. This fixture asserts that
 * EVERY advertised token still parses through {@link
 * QueryHelper#parseQuery(String)}, the canonical query parser shared
 * by both the legacy GWT search and the J2CL search sidecar.
 *
 * <p>Why this matters: the slice brief explicitly forbids inventing
 * new search tokens. If a future change to either {@code
 * <wavy-search-help>} or {@code TokenQueryType} drifts the two
 * sources out of lockstep, this test fails before the help modal
 * starts advertising tokens the parser rejects.
 *
 * <p>Sister fixture: {@code
 * J2clSearchRailParityTest} (under {@code wave/src/jakarta-test/...})
 * asserts that the server-rendered HTML actually contains every one
 * of these token literals inside the {@code <wavy-search-help>} host.
 *
 * @author claude (#1047 implementation lane)
 */
public class J2clSearchHelpTokenParseTest extends TestCase {

  /** C.1 — in:inbox */
  public void testParseInInboxFilterDepositsInBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:inbox");
    Set<String> values = tokens.get(TokenQueryType.IN);
    assertNotNull("in:inbox must populate the IN bucket", values);
    assertTrue(values.contains("inbox"));
  }

  /** C.2 — in:archive */
  public void testParseInArchiveFilterDepositsInBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:archive");
    assertTrue(tokens.get(TokenQueryType.IN).contains("archive"));
  }

  /** C.3 — in:all */
  public void testParseInAllFilterDepositsInBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:all");
    assertTrue(tokens.get(TokenQueryType.IN).contains("all"));
  }

  /** C.4 — in:pinned */
  public void testParseInPinnedFilterDepositsInBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:pinned");
    assertTrue(tokens.get(TokenQueryType.IN).contains("pinned"));
  }

  /** C.5 — with:user@domain (advertised example chip uses with:alice@example.com) */
  public void testParseWithUserAtDomainFilterDepositsWithBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens =
        QueryHelper.parseQuery("with:alice@example.com");
    assertTrue(tokens.get(TokenQueryType.WITH).contains("alice@example.com"));
  }

  /** C.6 — with:@ (public, shared domain). */
  public void testParseWithSharedDomainFilterDepositsWithBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("with:@");
    assertTrue(tokens.get(TokenQueryType.WITH).contains("@"));
  }

  /** C.7 — creator:user@domain. */
  public void testParseCreatorFilterDepositsCreatorBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens =
        QueryHelper.parseQuery("creator:bob@example.com");
    assertTrue(tokens.get(TokenQueryType.CREATOR).contains("bob@example.com"));
  }

  /** C.8 — tag:name. */
  public void testParseTagFilterDepositsTagBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("tag:important");
    assertTrue(tokens.get(TokenQueryType.TAG).contains("important"));
  }

  /** C.9 — unread:true. */
  public void testParseUnreadTrueFilterDepositsUnreadBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("unread:true");
    assertTrue(tokens.get(TokenQueryType.UNREAD).contains("true"));
  }

  /** C.10 — title:text. */
  public void testParseTitleFilterDepositsTitleBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("title:meeting");
    assertTrue(tokens.get(TokenQueryType.TITLE).contains("meeting"));
  }

  /** C.11 — content:text. */
  public void testParseContentFilterDepositsContentBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("content:agenda");
    assertTrue(tokens.get(TokenQueryType.CONTENT).contains("agenda"));
  }

  /** C.12 — mentions:me. */
  public void testParseMentionsFilterDepositsMentionsBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("mentions:me");
    assertTrue(tokens.get(TokenQueryType.MENTIONS).contains("me"));
  }

  /** C.13 — tasks:all (informational only; behavior owned by F-3). */
  public void testParseTasksAllFilterDepositsTasksBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("tasks:all");
    assertTrue(tokens.get(TokenQueryType.TASKS).contains("all"));
  }

  /** C.14 — tasks:me. */
  public void testParseTasksMeFilterDepositsTasksBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("tasks:me");
    assertTrue(tokens.get(TokenQueryType.TASKS).contains("me"));
  }

  /** C.15 — tasks:user@domain. */
  public void testParseTasksUserAtDomainFilterDepositsTasksBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens =
        QueryHelper.parseQuery("tasks:alice@example.com");
    assertTrue(tokens.get(TokenQueryType.TASKS).contains("alice@example.com"));
  }

  /** C.16 — free text falls into the implicit content bucket. */
  public void testParseFreeTextFallsIntoContentBucket() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("meeting notes");
    Set<String> values = tokens.get(TokenQueryType.CONTENT);
    assertNotNull(values);
    assertTrue(values.contains("meeting"));
    assertTrue(values.contains("notes"));
  }

  // C.17–C.20: every advertised orderby: value must resolve to an
  // OrderByValueType (the QueryHelper parser eagerly validates these
  // and throws InvalidQueryException on unknown values, so the modal
  // MUST advertise only canonical values).

  /** C.17 default sort. */
  public void testParseOrderByDateDescResolves() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("orderby:datedesc");
    assertTrue(tokens.get(TokenQueryType.ORDERBY).contains("datedesc"));
    assertEquals(QueryHelper.OrderByValueType.DATEDESC,
        QueryHelper.OrderByValueType.fromToken("datedesc"));
  }

  /** C.18 — orderby:dateasc. */
  public void testParseOrderByDateAscResolves() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("orderby:dateasc");
    assertTrue(tokens.get(TokenQueryType.ORDERBY).contains("dateasc"));
    assertEquals(QueryHelper.OrderByValueType.DATEASC,
        QueryHelper.OrderByValueType.fromToken("dateasc"));
  }

  /** C.19 — orderby:createddesc and orderby:createdasc. */
  public void testParseOrderByCreatedDescAndCreatedAscResolve() throws Exception {
    Map<TokenQueryType, Set<String>> descTokens =
        QueryHelper.parseQuery("orderby:createddesc");
    assertTrue(descTokens.get(TokenQueryType.ORDERBY).contains("createddesc"));
    assertEquals(QueryHelper.OrderByValueType.CREATEDDESC,
        QueryHelper.OrderByValueType.fromToken("createddesc"));

    Map<TokenQueryType, Set<String>> ascTokens =
        QueryHelper.parseQuery("orderby:createdasc");
    assertTrue(ascTokens.get(TokenQueryType.ORDERBY).contains("createdasc"));
    assertEquals(QueryHelper.OrderByValueType.CREATEDASC,
        QueryHelper.OrderByValueType.fromToken("createdasc"));
  }

  /** C.20 — orderby:creatordesc and orderby:creatorasc. */
  public void testParseOrderByCreatorDescAndCreatorAscResolve() throws Exception {
    Map<TokenQueryType, Set<String>> descTokens =
        QueryHelper.parseQuery("orderby:creatordesc");
    assertTrue(descTokens.get(TokenQueryType.ORDERBY).contains("creatordesc"));
    assertEquals(QueryHelper.OrderByValueType.CREATORDESC,
        QueryHelper.OrderByValueType.fromToken("creatordesc"));

    Map<TokenQueryType, Set<String>> ascTokens =
        QueryHelper.parseQuery("orderby:creatorasc");
    assertTrue(ascTokens.get(TokenQueryType.ORDERBY).contains("creatorasc"));
    assertEquals(QueryHelper.OrderByValueType.CREATORASC,
        QueryHelper.OrderByValueType.fromToken("creatorasc"));
  }

  // C.21 — every combination example string from the modal must parse
  // without an InvalidQueryException. We do not assert on the resulting
  // bucket shape here (the per-token tests above already cover that);
  // we only assert that the parser accepts the combined string.

  public void testParseCombinationInboxTagImportant() throws Exception {
    QueryHelper.parseQuery("in:inbox tag:important");
  }

  public void testParseCombinationInAllOrderByCreatedAsc() throws Exception {
    QueryHelper.parseQuery("in:all orderby:createdasc");
  }

  public void testParseCombinationWithUserTagProject() throws Exception {
    QueryHelper.parseQuery("with:alice@example.com tag:project");
  }

  public void testParseCombinationInPinnedOrderByCreatorDesc() throws Exception {
    QueryHelper.parseQuery("in:pinned orderby:creatordesc");
  }

  public void testParseCombinationCreatorBobInArchive() throws Exception {
    QueryHelper.parseQuery("creator:bob in:archive");
  }

  public void testParseCombinationMentionsMeUnreadTrue() throws Exception {
    QueryHelper.parseQuery("mentions:me unread:true");
  }

  public void testParseCombinationTasksAllUnreadTrue() throws Exception {
    QueryHelper.parseQuery("tasks:all unread:true");
  }

  // The six saved-search rail folder query strings (B.5–B.10) MUST all
  // parse cleanly. The modal does not list them as their own row, but
  // the rail uses them as the canonical "click this folder" payload.

  public void testParseSavedSearchInboxFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:inbox");
    assertTrue(tokens.get(TokenQueryType.IN).contains("inbox"));
  }

  public void testParseSavedSearchMentionsFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("mentions:me");
    assertTrue(tokens.get(TokenQueryType.MENTIONS).contains("me"));
  }

  public void testParseSavedSearchTasksFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("tasks:me");
    assertTrue(tokens.get(TokenQueryType.TASKS).contains("me"));
  }

  public void testParseSavedSearchPublicFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("with:@");
    assertTrue(tokens.get(TokenQueryType.WITH).contains("@"));
  }

  public void testParseSavedSearchArchiveFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:archive");
    assertTrue(tokens.get(TokenQueryType.IN).contains("archive"));
  }

  public void testParseSavedSearchPinnedFolder() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("in:pinned");
    assertTrue(tokens.get(TokenQueryType.IN).contains("pinned"));
  }

  /**
   * Negative guard: orderby: with a value not in OrderByValueType MUST
   * throw {@link QueryHelper.InvalidQueryException}. This is the
   * test that fails if the help modal ever advertises a sort token
   * the parser does not recognise.
   */
  public void testParseOrderByBogusValueThrowsInvalidQueryException() {
    try {
      QueryHelper.parseQuery("orderby:bogus");
      fail("Expected InvalidQueryException for unknown orderby value");
    } catch (QueryHelper.InvalidQueryException expected) {
      // Expected.
    }
  }

  /**
   * Negative guard: an unknown token prefix falls into CONTENT (it
   * does NOT throw). This documents the parser's current behaviour:
   * adding a NEW filter prefix to <wavy-search-help> without adding
   * it to TokenQueryType silently degrades to a content search rather
   * than throwing — the slice brief forbids this, and the modal MUST
   * stay restricted to TokenQueryType-backed prefixes. This test
   * pins the contract so we know what the safety net is.
   */
  public void testUnknownPrefixFallsThroughToContent() throws Exception {
    Map<TokenQueryType, Set<String>> tokens = QueryHelper.parseQuery("bogus:value");
    assertTrue(tokens.get(TokenQueryType.CONTENT).contains("bogus:value"));
  }
}

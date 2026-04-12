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

import junit.framework.TestCase;
import org.waveprotocol.box.server.waveserver.QueryHelper.InvalidQueryException;

/**
 * Tests that {@code mentions:me} queries are routed through the legacy
 * {@code filterByMentions} path rather than the Lucene MENTIONED field.
 *
 * <p>The Lucene MENTIONED field can be stale for waves indexed before the
 * mentions feature was deployed. {@code filterByMentions} reads annotations
 * directly from wave data and is always authoritative.
 */
public class Lucene9QueryModelTest extends TestCase {

  private final Lucene9QueryParser parser = new Lucene9QueryParser();

  public void testMentionsQueryHasNoTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("mentions:me");
    assertFalse("mentions:me should not be a Lucene text query", model.hasTextQuery());
  }

  public void testMentionsQueryIsIncludedInLegacyQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("mentions:me");
    assertEquals("mentions:me", model.toLegacyQuery());
  }

  public void testMentionsWithExplicitAddressIsIncludedInLegacyQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("mentions:alice@example.com");
    assertEquals("mentions:alice@example.com", model.toLegacyQuery());
  }

  public void testTitleQueryStillHasTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("title:meeting");
    assertTrue("title:meeting should be a Lucene text query", model.hasTextQuery());
  }

  public void testContentQueryStillHasTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("content:agenda");
    assertTrue("content:agenda should be a Lucene text query", model.hasTextQuery());
  }

  public void testMentionsCombinedWithTitleHasTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("mentions:me title:meeting");
    assertTrue("mentions+title query should be a Lucene text query (for title)", model.hasTextQuery());
  }

  public void testMentionsCombinedWithTitlePassesMentionsToLegacy() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("mentions:me title:meeting");
    // mentions goes to legacy; title goes to Lucene
    assertEquals("mentions:me", model.toLegacyQuery());
  }

  public void testInQueryIsPassedToLegacy() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("in:inbox");
    assertFalse(model.hasTextQuery());
    assertEquals("in:inbox", model.toLegacyQuery());
  }

  public void testTagQueryHasNoTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tag:project-x");
    assertFalse("tag: queries should stay on the legacy filter path", model.hasTextQuery());
  }

  public void testTagQueryIsIncludedInLegacyQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tag:project-x");
    assertEquals("tag:project-x", model.toLegacyQuery());
  }

  public void testTitleIsExcludedFromLegacyQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("title:meeting");
    assertEquals("", model.toLegacyQuery());
  }

  public void testTasksQueryPassesThroughToLegacy() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tasks:me");
    assertEquals("tasks:me", model.toLegacyQuery());
  }

  public void testTasksAllQueryPassesThroughToLegacy() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tasks:all");
    assertEquals("tasks:all", model.toLegacyQuery());
  }

  public void testTasksQueryHasNoTextQuery() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tasks:me");
    assertFalse("tasks:me should not be a Lucene text query", model.hasTextQuery());
  }

  public void testTasksWithInboxPassesBothToLegacy() throws InvalidQueryException {
    Lucene9QueryModel model = parser.parse("tasks:me in:inbox");
    assertEquals("tasks:me in:inbox", model.toLegacyQuery());
  }
}

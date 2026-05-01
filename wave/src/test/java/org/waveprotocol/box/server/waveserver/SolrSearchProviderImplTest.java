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
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.waveprotocol.box.server.waveserver;

import com.google.common.collect.ImmutableMap;
import junit.framework.TestCase;
import com.google.wave.api.SearchResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class SolrSearchProviderImplTest extends TestCase {

  public void testStripUnreadFilterTokensRemovesStandaloneTokenOnly() {
    assertEquals(
        "project https://example.com/unread:true update",
        SolrSearchProviderImpl.stripUnreadFilterTokens(
            "project https://example.com/unread:true unread:true update"));
  }

  /**
   * J-UI-2 (#1080 / R-4.5): the rail's filter chips emit
   * {@code is:unread}, {@code has:attachment}, and {@code from:me}.
   * Solr's user-query clause must not see these as literal terms — they
   * are either post-filtered ({@code is:unread} → unread post-filter) or
   * URL-only this slice. Verify each one is stripped before the
   * user-query clause is built.
   */
  public void testStripUnreadFilterTokensRemovesIsUnreadChipToken() {
    assertEquals(
        "in:inbox",
        SolrSearchProviderImpl.stripUnreadFilterTokens("in:inbox is:unread"));
    assertEquals(
        "in:inbox",
        SolrSearchProviderImpl.stripUnreadFilterTokens("in:inbox IS:UNREAD"));
  }

  public void testStripUnreadFilterTokensRemovesHasAndFromChipTokens() {
    assertEquals(
        "in:archive",
        SolrSearchProviderImpl.stripUnreadFilterTokens("in:archive has:attachment from:me"));
  }

  public void testStripUnreadFilterTokensLeavesOtherTokensUntouched() {
    assertEquals(
        "in:inbox tag:work mentions:me",
        SolrSearchProviderImpl.stripUnreadFilterTokens("in:inbox tag:work mentions:me"));
  }

  public void testSearchRejectsMentionsQueries() {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
        "core.wave_server_domain", "example.com",
        "core.solr_base_url", "http://localhost:8983/solr"));
    SolrSearchProviderImpl searchProvider = new SolrSearchProviderImpl(
        new WaveDigester(new ConversationUtil((IdGenerator) null)), null, config);

    SearchResult results = searchProvider.search(
        ParticipantId.ofUnsafe("user@example.com"), "mentions:me", 0, 10);

    assertEquals(0, results.getNumResults());
    assertEquals(0, results.getDigests().size());
  }
}

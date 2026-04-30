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
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SolrSearchProviderImplTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@" + DOMAIN);

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

  public void testAttachmentFilterKeepsModernAttachmentDocument() throws Exception {
    WaveViewData modernAttachmentWave =
        waveWithDocument("modern", IdUtil.join(IdConstants.ATTACHMENT_METADATA_PREFIX, "modern"));
    WaveViewData plainWave = waveWithDocument("plain", "b+plain");
    List<WaveViewData> results = new ArrayList<WaveViewData>();
    results.add(modernAttachmentWave);
    results.add(plainWave);

    AttachmentSearchFilter.filterByHasAttachment(results);

    assertEquals(1, results.size());
    assertSame(modernAttachmentWave, results.get(0));
  }

  public void testAttachmentFilterKeepsLegacyAttachmentDocument() throws Exception {
    WaveViewData legacyAttachmentWave =
        waveWithDocument("legacy", "m/attachment/legacy-file");
    WaveViewData plainWave = waveWithDocument("plain-legacy", "b+plain");
    List<WaveViewData> results = new ArrayList<WaveViewData>();
    results.add(plainWave);
    results.add(legacyAttachmentWave);

    AttachmentSearchFilter.filterByHasAttachment(results);

    assertEquals(1, results.size());
    assertSame(legacyAttachmentWave, results.get(0));
  }

  public void testAttachmentFilterIgnoresNonConversationalWaveletDocuments() throws Exception {
    WaveId waveId = WaveId.of(DOMAIN, "user-data");
    WaveletName waveletName =
        WaveletName.of(waveId, WaveletId.of(DOMAIN, "user+" + USER.getAddress()));
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, USER, HashedVersion.unsigned(0), 1234567890);
    wavelet.createDocument(
        IdUtil.join(IdConstants.ATTACHMENT_METADATA_PREFIX, "metadata"),
        USER,
        Collections.<ParticipantId>emptySet(),
        new DocInitializationBuilder().characters("metadata").build(),
        1234567890,
        0);
    WaveViewDataImpl wave = WaveViewDataImpl.create(waveId);
    wave.addWavelet(wavelet);
    List<WaveViewData> results = new ArrayList<WaveViewData>();
    results.add(wave);

    AttachmentSearchFilter.filterByHasAttachment(results);

    assertTrue(results.isEmpty());
  }

  public void testIsHasAttachmentQueryOnlyMatchesAttachmentValue() throws Exception {
    assertTrue(AttachmentSearchFilter.isHasAttachmentQuery(
        QueryHelper.parseQuery("in:inbox has:attachment")));
    assertTrue(AttachmentSearchFilter.isHasAttachmentQuery(
        QueryHelper.parseQuery("has:ATTACHMENT")));
    assertFalse(AttachmentSearchFilter.isHasAttachmentQuery(
        QueryHelper.parseQuery("has:unknown")));
  }

  public void testSolrQueriesStartAtZeroBeforeInMemoryPagination() {
    assertEquals(0, SolrSearchProviderImpl.computeSolrStart(5, true, false));
    assertEquals(0, SolrSearchProviderImpl.computeSolrStart(5, false, true));
    assertEquals(0, SolrSearchProviderImpl.computeSolrStart(5, false, false));
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

  private static WaveViewData waveWithDocument(String waveIdToken, String documentId)
      throws Exception {
    WaveId waveId = WaveId.of(DOMAIN, waveIdToken);
    WaveletName waveletName =
        WaveletName.of(waveId, WaveletId.of(DOMAIN, IdConstants.CONVERSATION_ROOT_WAVELET));
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, USER, HashedVersion.unsigned(0), 1234567890);
    wavelet.createDocument(
        documentId,
        USER,
        Collections.<ParticipantId>emptySet(),
        new DocInitializationBuilder().characters("metadata").build(),
        1234567890,
        0);
    WaveViewDataImpl wave = WaveViewDataImpl.create(waveId);
    wave.addWavelet(wavelet);
    return wave;
  }
}

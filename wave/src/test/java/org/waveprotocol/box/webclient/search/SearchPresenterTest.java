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

package org.waveprotocol.box.webclient.search;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.document.util.DocProviders;
import org.waveprotocol.wave.model.id.WaveletName;

public final class SearchPresenterTest extends TestCase {

  public void testComputeSearchWaveletNameMatchesServerScheme() {
    WaveletName waveletName =
        SearchPresenter.computeSearchWaveletName("alice@example.com", "in:inbox");

    assertEquals("search~alice", waveletName.waveId.getId());
    assertEquals("example.com", waveletName.waveId.getDomain());
    assertEquals("search+30ae99f507d4f206af60f6470b4de013", waveletName.waveletId.getId());
    assertEquals("example.com", waveletName.waveletId.getDomain());
  }

  public void testParseOtSearchDocumentExtractsDigestsAndMetadata() {
    DocInitialization document = DocProviders.POJO.parse(
        "<body>"
            + "<metadata query=\"in:inbox\" total=\"2\" updated=\"1711411200000\"></metadata>"
            + "<results>"
            + "<result blips=\"7\" creator=\"bob@example.com\" id=\"example.com/w+abc\""
            + " modified=\"1711411100000\" participants=\"3\""
            + " snippet=\"Latest reply\" title=\"Project standup\" unread=\"2\"></result>"
            + "<result blips=\"2\" creator=\"carol@example.com\" id=\"example.com/w+def\""
            + " modified=\"1711410900000\" participants=\"1\""
            + " snippet=\"Draft\" title=\"Design review\" unread=\"0\"></result>"
            + "</results>"
            + "</body>").asOperation();

    SearchPresenter.OtSearchSnapshot snapshot =
        SearchPresenter.parseOtSearchSnapshot(document);

    assertEquals(2, snapshot.getTotal());
    assertEquals(2, snapshot.getDigests().size());
    assertEquals("Project standup", snapshot.getDigests().get(0).getTitle());
    assertEquals("Latest reply", snapshot.getDigests().get(0).getSnippet());
    assertEquals(2, snapshot.getDigests().get(0).getUnreadCount());
    assertEquals(7, snapshot.getDigests().get(0).getBlipCount());
    assertEquals("bob@example.com", snapshot.getDigests().get(0).getAuthor().getAddress());
    assertEquals(0, snapshot.getDigests().get(0).getParticipantsSnippet().size());
  }

  public void testApplyOtSearchDiffUpdatesCurrentDocumentState() {
    DocInitialization document = DocProviders.POJO.parse(
        "<body>"
            + "<metadata query=\"in:inbox\" total=\"1\" updated=\"1711411200000\"></metadata>"
            + "<results>"
            + "<result blips=\"7\" creator=\"bob@example.com\" id=\"example.com/w+abc\""
            + " modified=\"1711411100000\" participants=\"3\""
            + " snippet=\"Latest reply\" title=\"Project standup\" unread=\"2\"></result>"
            + "</results>"
            + "</body>").asOperation();
    DocOp diff = new DocOpBuilder()
        .retain(4)
        .replaceAttributes(
            SearchPresenter.resultAttributesForTesting(
                "example.com/w+abc", "Project standup", "Latest reply",
                1711411100000L, "bob@example.com", 3, 2, 7),
            SearchPresenter.resultAttributesForTesting(
                "example.com/w+abc", "Project standup", "Fresh update",
                1711411300000L, "bob@example.com", 4, 5, 8))
        .retain(3)
        .buildUnchecked();

    DocInitialization updated = SearchPresenter.applyOtSearchDiff(document, diff);
    SearchPresenter.OtSearchSnapshot snapshot = SearchPresenter.parseOtSearchSnapshot(updated);

    assertEquals(1, snapshot.getDigests().size());
    assertEquals("Fresh update", snapshot.getDigests().get(0).getSnippet());
    assertEquals(5, snapshot.getDigests().get(0).getUnreadCount());
    assertEquals(8, snapshot.getDigests().get(0).getBlipCount());
    assertEquals(0, snapshot.getDigests().get(0).getParticipantsSnippet().size());
  }
}

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

import com.google.gwt.http.client.Request;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.document.util.DocProviders;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.client.scheduler.testing.FakeTimerService;
import org.waveprotocol.wave.client.account.ProfileListener;
import org.waveprotocol.wave.model.wave.SourcesEvents;
import org.waveprotocol.wave.client.widget.toolbar.GroupingToolbar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchPresenterTest extends TestCase {

  private static final SearchPresenter.WaveActionHandler NO_OP_ACTION_HANDLER =
      new SearchPresenter.WaveActionHandler() {
        @Override
        public void onCreateWave() {
        }

        @Override
        public void onWaveSelected(WaveId id) {
        }
      };

  public void testComputeSearchWaveletNameMatchesServerScheme() {
    WaveletName waveletName =
        SearchPresenter.computeSearchWaveletName("alice@example.com", "in:inbox");

    assertEquals("search~alice", waveletName.waveId.getId());
    assertEquals("example.com", waveletName.waveId.getDomain());
    assertEquals("search+30ae99f507d4f206af60f6470b4de013", waveletName.waveletId.getId());
    assertEquals("example.com", waveletName.waveletId.getDomain());
  }

  public void testNormalizeSearchQueryFallsBackToInbox() {
    assertEquals("in:inbox", SearchPresenter.normalizeSearchQuery(null));
    assertEquals("in:inbox", SearchPresenter.normalizeSearchQuery(""));
    assertEquals("in:inbox", SearchPresenter.normalizeSearchQuery("   "));
    assertEquals("creator:bob", SearchPresenter.normalizeSearchQuery("creator:bob"));
  }

  public void testShouldSubmitQueryRejectsRepeatedDefaultSubmission() {
    assertFalse(SearchWidget.shouldSubmitQuery(
        "",
        false,
        SearchPresenter.DEFAULT_SEARCH));
  }

  public void testShouldSubmitQueryRejectsDeferredDefaultSubmission() {
    assertFalse(SearchWidget.shouldSubmitQuery(
        "",
        true,
        null));
  }

  public void testShouldSubmitQueryAllowsNewProgrammaticQueryAfterDeferredDefault() {
    assertTrue(SearchWidget.shouldSubmitQuery(
        "creator:bob",
        true,
        SearchPresenter.DEFAULT_SEARCH));
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

  public void testShouldUsePollingWhenOtSearchIsDisabled() {
    assertTrue(SearchPresenter.shouldUsePolling(false, false));
  }

  public void testShouldUsePollingWhenOtSearchHasNotDeliveredUsableDataYet() {
    assertTrue(SearchPresenter.shouldUsePolling(true, false));
  }

  public void testShouldUseOtSearchOnlyAfterUsableOtDataArrives() {
    assertFalse(SearchPresenter.shouldUsePolling(true, true));
  }

  public void testBootstrapOtSearchUsesImmediateDirectSearchAndKeepsPollingSafety()
      throws Exception {
    FakeTimerService scheduler = new FakeTimerService();
    FakeSearch search = new FakeSearch();
    SearchPresenter presenter = new SearchPresenter(
        scheduler, search, new FakeSearchPanelView(), NO_OP_ACTION_HANDLER, new FakeProfiles(),
        null);

    setBooleanField(presenter, "otSearchEnabled", true);

    presenter.bootstrapOtSearch();

    assertEquals(1, search.findCalls);
    assertEquals("in:inbox", search.lastQuery);
    assertEquals(30, search.lastSize);
    assertEquals(1, scheduler.countTasksScheduled());
  }

  public void testOnFolderActionCompletedUsesImmediateDirectSearchWhenOtSearchIsEnabled()
      throws Exception {
    FakeTimerService scheduler = new FakeTimerService();
    FakeSearch search = new FakeSearch();
    SearchPresenter presenter = new SearchPresenter(
        scheduler, search, new FakeSearchPanelView(), NO_OP_ACTION_HANDLER, new FakeProfiles(),
        null);

    setBooleanField(presenter, "otSearchEnabled", true);
    setBooleanField(presenter, "useOtSearch", true);

    presenter.onFolderActionCompleted("archive");

    assertEquals(1, search.cancelCalls);
    assertEquals(1, search.findCalls);
    assertEquals("in:inbox", search.lastQuery);
    assertEquals(30, search.lastSize);
    assertEquals(1, scheduler.countTasksScheduled());
  }

  public void testOnShowMoreFallsBackToPollingWhenOtSnapshotCannotGrowWindow()
      throws Exception {
    FakeTimerService scheduler = new FakeTimerService();
    SimpleSearch search = new SimpleSearch(new FakeSearchService(), null);
    SearchPresenter presenter = new SearchPresenter(
        scheduler, search, new FakeSearchPanelView(), NO_OP_ACTION_HANDLER, new FakeProfiles(),
        null);

    setBooleanField(presenter, "otSearchEnabled", true);
    setBooleanField(presenter, "useOtSearch", true);
    setIntField(presenter, "querySize", 45);
    setField(
        presenter,
        "otSearchSnapshot",
        new SearchPresenter.OtSearchSnapshot(80, createDigestSnapshots(50)));

    presenter.onShowMoreClicked();

    assertFalse(getBooleanField(presenter, "useOtSearch"));
    assertEquals(1, scheduler.countTasksScheduled());
  }

  private static void setBooleanField(SearchPresenter presenter, String fieldName, boolean value)
      throws Exception {
    Field field = SearchPresenter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setBoolean(presenter, value);
  }

  private static boolean getBooleanField(SearchPresenter presenter, String fieldName)
      throws Exception {
    Field field = SearchPresenter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.getBoolean(presenter);
  }

  private static void setIntField(SearchPresenter presenter, String fieldName, int value)
      throws Exception {
    Field field = SearchPresenter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(presenter, value);
  }

  private static void setField(SearchPresenter presenter, String fieldName, Object value)
      throws Exception {
    Field field = SearchPresenter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(presenter, value);
  }

  private static List<SearchService.DigestSnapshot> createDigestSnapshots(int count) {
    List<SearchService.DigestSnapshot> digests = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      digests.add(new SearchService.DigestSnapshot(
          "Title " + i,
          "Snippet " + i,
          WaveId.of("example.com", "w+" + i),
          ParticipantId.ofUnsafe("author@example.com"),
          Collections.<ParticipantId>emptyList(),
          i,
          0,
          1));
    }
    return digests;
  }

  private static final class FakeSearch implements Search {
    private int findCalls;
    private int cancelCalls;
    private String lastQuery;
    private int lastSize;

    @Override
    public State getState() {
      return State.READY;
    }

    @Override
    public void find(String query, int size) {
      findCalls++;
      lastQuery = query;
      lastSize = size;
    }

    @Override
    public void cancel() {
      cancelCalls++;
    }

    @Override
    public int getTotal() {
      return 0;
    }

    @Override
    public int getMinimumTotal() {
      return 0;
    }

    @Override
    public Digest getDigest(int index) {
      return null;
    }

    @Override
    public void addListener(Listener listener) {
    }

    @Override
    public void removeListener(Listener listener) {
    }
  }

  private static final class FakeProfiles implements SourcesEvents<ProfileListener> {
    @Override
    public void addListener(ProfileListener listener) {
    }

    @Override
    public void removeListener(ProfileListener listener) {
    }
  }

  private static final class FakeSearchService implements SearchService {
    @Override
    public Request search(String query, int index, int numResults, Callback callback) {
      return null;
    }
  }

  private static final class FakeSearchPanelView implements SearchPanelView {
    private final FakeSearchView searchView = new FakeSearchView();

    @Override
    public void init(Listener listener) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void setTitleText(String text) {
    }

    @Override
    public void setWaveCountText(String text) {
    }

    @Override
    public SearchView getSearch() {
      return searchView;
    }

    @Override
    public GroupingToolbar.View getToolbar() {
      return null;
    }

    @Override
    public DigestView getFirst() {
      return null;
    }

    @Override
    public DigestView getLast() {
      return null;
    }

    @Override
    public DigestView getNext(DigestView ref) {
      return null;
    }

    @Override
    public DigestView getPrevious(DigestView ref) {
      return null;
    }

    @Override
    public DigestView insertBefore(DigestView ref, Digest digest) {
      return null;
    }

    @Override
    public DigestView insertAfter(DigestView ref, Digest digest) {
      return null;
    }

    @Override
    public void renderDigest(DigestView digestUi, Digest digest) {
    }

    @Override
    public void clearDigests() {
    }

    @Override
    public void setShowMoreVisible(boolean visible) {
    }
  }

  private static final class FakeSearchView implements SearchView {
    private String query = "in:inbox";

    @Override
    public void init(Listener listener) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void setQuery(String text) {
      query = text;
    }

    @Override
    public String getQuery() {
      return query;
    }
  }
}

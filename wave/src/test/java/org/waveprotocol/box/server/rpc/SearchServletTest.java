package org.waveprotocol.box.server.rpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.wave.api.SearchResult;
import com.google.wave.api.data.converter.EventDataConverterManager;

import junit.framework.TestCase;

import org.mockito.stubbing.Answer;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.search.SearchIndexer;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletDataProvider;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletManager;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletSnapshotPublisher;
import org.waveprotocol.box.search.SearchProto.SearchRequest;
import org.waveprotocol.wave.model.wave.ParticipantId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SearchServletTest extends TestCase {

  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");

  public void testDoGetRequeriesCanonicalLiveSearchWindowForBootstrap() throws Exception {
    TestSearchServlet servlet = createServlet(createSnapshotPublisher());
    HttpServletRequest request = requestWithParams(Map.of(
        "query", "in:inbox",
        "index", "5",
        "numResults", "3"));
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    assertEquals(2, servlet.getPerformedRequests().size());
    assertEquals(5, servlet.getPerformedRequests().get(0).getIndex());
    assertEquals(3, servlet.getPerformedRequests().get(0).getNumResults());
    assertEquals(0, servlet.getPerformedRequests().get(1).getIndex());
    assertEquals(
        SearchWaveletSnapshotPublisher.LIVE_SEARCH_NUM_RESULTS,
        servlet.getPerformedRequests().get(1).getNumResults());
  }

  public void testDoGetSkipsDuplicateCanonicalBootstrapSearch() throws Exception {
    TestSearchServlet servlet = createServlet(createSnapshotPublisher());
    HttpServletRequest request = requestWithParams(Map.of(
        "query", "in:inbox",
        "index", "0",
        "numResults", "50"));
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    assertEquals(1, servlet.getPerformedRequests().size());
    assertEquals(0, servlet.getPerformedRequests().get(0).getIndex());
    assertEquals(
        SearchWaveletSnapshotPublisher.LIVE_SEARCH_NUM_RESULTS,
        servlet.getPerformedRequests().get(0).getNumResults());
  }

  private static TestSearchServlet createServlet(SearchWaveletSnapshotPublisher snapshotPublisher) {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(USER);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(USER);

    return new TestSearchServlet(
        sessionManager,
        mock(EventDataConverterManager.class),
        mock(OperationServiceRegistry.class),
        mock(WaveletProvider.class),
        mock(ConversationUtil.class),
        new ProtoSerializer(),
        snapshotPublisher);
  }

  private static SearchWaveletSnapshotPublisher createSnapshotPublisher() {
    return new SearchWaveletSnapshotPublisher(
        new SearchWaveletDispatcher(),
        new SearchWaveletManager(),
        new SearchIndexer(),
        new SearchWaveletDataProvider());
  }

  private static HttpServletRequest requestWithParams(Map<String, String> params) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getParameter(any())).thenAnswer((Answer<String>) invocation -> {
      String key = (String) invocation.getArguments()[0];
      return params.get(key);
    });
    return request;
  }

  private static HttpServletResponse responseWithWriter() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    return response;
  }

  private static final class TestSearchServlet extends SearchServlet {
    private final List<SearchRequest> performedRequests = new ArrayList<>();

    private TestSearchServlet(
        SessionManager sessionManager,
        EventDataConverterManager converterManager,
        OperationServiceRegistry operationRegistry,
        WaveletProvider waveletProvider,
        ConversationUtil conversationUtil,
        ProtoSerializer serializer,
        SearchWaveletSnapshotPublisher snapshotPublisher) {
      super(
          sessionManager,
          converterManager,
          operationRegistry,
          waveletProvider,
          conversationUtil,
          serializer,
          snapshotPublisher);
    }

    @Override
    protected SearchResult performSearch(SearchRequest searchRequest, ParticipantId user) {
      performedRequests.add(searchRequest);
      SearchResult searchResult = new SearchResult(searchRequest.getQuery());
      searchResult.addDigest(new SearchResult.Digest(
          "title",
          "snippet",
          "example.com/w+1",
          List.of("a@example.com"),
          0L,
          0L,
          0,
          1));
      return searchResult;
    }

    private List<SearchRequest> getPerformedRequests() {
      return performedRequests;
    }
  }
}

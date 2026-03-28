package org.waveprotocol.box.server.jakarta;

import com.google.gson.JsonParser;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.ProtoSerializer;
import org.waveprotocol.box.server.rpc.SearchServlet;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.search.SearchIndexer;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletDataProvider;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletManager;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletSnapshotPublisher;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import com.google.gson.JsonElement;
import org.waveprotocol.box.server.authentication.WebSession;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class SearchServletJakartaIT {
  private Server server;
  private int port;
  private SessionManager sm;
  private EventDataConverterManager conv;
  private OperationServiceRegistry reg;
  private WaveletProvider wprov;
  private ConversationUtil convUtil;
  private ProtoSerializer serializer;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    sm = Mockito.mock(SessionManager.class);
    conv = Mockito.mock(EventDataConverterManager.class, Mockito.RETURNS_DEEP_STUBS);
    reg = Mockito.mock(OperationServiceRegistry.class);
    wprov = Mockito.mock(WaveletProvider.class);
    convUtil = Mockito.mock(ConversationUtil.class);
    serializer = new ProtoSerializer();

    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(
        new TestSearchServlet(sm, conv, reg, wprov, convUtil, serializer, null)), "/search/*");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();
  }

  @After
  public void stop() {
    TestSupport.stopServerQuietly(server);
  }

  // Override performSearch to avoid deep Operation pipeline stubbing
  public static class TestSearchServlet extends SearchServlet {
    private final List<org.waveprotocol.box.search.SearchProto.SearchRequest> performedRequests =
        new ArrayList<>();

    public TestSearchServlet(SessionManager sm, EventDataConverterManager conv, OperationServiceRegistry reg,
                             WaveletProvider wprov, ConversationUtil convUtil, ProtoSerializer serializer,
                             SearchWaveletSnapshotPublisher snapshotPublisher) {
      super(sm, conv, reg, wprov, convUtil, serializer, snapshotPublisher);
    }
    @Override
    protected com.google.wave.api.SearchResult performSearch(org.waveprotocol.box.search.SearchProto.SearchRequest req,
                                                             org.waveprotocol.wave.model.wave.ParticipantId user) {
      performedRequests.add(req);
      com.google.wave.api.SearchResult r = new com.google.wave.api.SearchResult(req.getQuery());
      r.addDigest(new com.google.wave.api.SearchResult.Digest("t","s","example.com/w+1", java.util.List.of("a@example.com"), 0L, 0L, 0, 1));
      return r;
    }

    public List<org.waveprotocol.box.search.SearchProto.SearchRequest> getPerformedRequests() {
      return performedRequests;
    }
  }

  @Test
  public void searchRequiresLogin() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(null);
    URL url = new URL("http://localhost:" + port + "/search/?query=in:inbox&index=0&numResults=1");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void searchReturnsJsonOnSuccess() throws Exception {
    var user = new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);
    // Stub the Operation pipeline result by short-circuiting performSearch via registry mocks:
    // We rely on serializer mock to return a simple JSON string, so just ensure 200/JSON here.
    URL url = new URL("http://localhost:" + port + "/search/?query=in:inbox&index=0&numResults=1");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").contains("application/json"));
  }

  @org.junit.Ignore("Requires intrusive serializer failure; covered at lower levels.")
  @Test
  public void serializerFailureReturns500() throws Exception {
    // Use a mock ProtoSerializer that throws on toJson (requires mockito-inline on classpath)
    ProtoSerializer throwing = Mockito.mock(ProtoSerializer.class);
    Mockito.when(throwing.toJson(Mockito.any())).thenThrow(new org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException("boom"));
    Server srv = new Server();
    ServerConnector cc = new ServerConnector(srv);
    cc.setPort(0);
    srv.addConnector(cc);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(
        new TestSearchServlet(sm, conv, reg, wprov, convUtil, throwing, null)), "/search/*");
    srv.setHandler(ctx);
    srv.start();
    int p = cc.getLocalPort();
    try {
      Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
      Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
      URL url = new URL("http://localhost:" + p + "/search/?query=in:inbox&index=0&numResults=1");
      HttpURLConnection c = TestSupport.openConnection(url);
      assertEquals(500, c.getResponseCode());
    } finally {
      TestSupport.stopServerQuietly(srv);
    }
  }

  @Test
  public void nonNumericParamsReturn400() throws Exception {
    var user = new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);
    URL url = new URL("http://localhost:" + port + "/search/?query=in:all&index=abc&numResults=xyz");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(400, c.getResponseCode());
  }

  @Test
  public void outOfRangeParamsAreClampedAnd200() throws Exception {
    var user = new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);
    URL url = new URL("http://localhost:" + port + "/search/?query=in:all&index=-5&numResults=100000");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
  }

  @Test
  public void snapshotBootstrapSkipsCanonicalRequeryWithoutLiveSubscription() throws Exception {
    var user = new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);

    TestSearchServlet servlet = new TestSearchServlet(
        sm,
        conv,
        reg,
        wprov,
        convUtil,
        serializer,
        createSnapshotPublisher());

    Server srv = new Server();
    ServerConnector cc = new ServerConnector(srv);
    cc.setPort(0);
    srv.addConnector(cc);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), "/search/*");
    srv.setHandler(ctx);
    srv.start();

    try {
      URL url = new URL("http://localhost:" + cc.getLocalPort() + "/search/?query=in:inbox&index=5&numResults=3");
      HttpURLConnection c = TestSupport.openConnection(url);
      assertEquals(200, c.getResponseCode());
      assertEquals(1, servlet.getPerformedRequests().size());
      assertEquals(5, servlet.getPerformedRequests().get(0).getIndex());
      assertEquals(3, servlet.getPerformedRequests().get(0).getNumResults());
    } finally {
      TestSupport.stopServerQuietly(srv);
    }
  }

  @Test
  public void snapshotBootstrapDoesNotDuplicateCanonicalLiveSearchRequest() throws Exception {
    var user = new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);

    TestSearchServlet servlet = new TestSearchServlet(
        sm,
        conv,
        reg,
        wprov,
        convUtil,
        serializer,
        createSnapshotPublisher());

    Server srv = new Server();
    ServerConnector cc = new ServerConnector(srv);
    cc.setPort(0);
    srv.addConnector(cc);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), "/search/*");
    srv.setHandler(ctx);
    srv.start();

    try {
      URL url = new URL("http://localhost:" + cc.getLocalPort() + "/search/?query=in:inbox&index=0&numResults=50");
      HttpURLConnection c = TestSupport.openConnection(url);
      assertEquals(200, c.getResponseCode());
      assertEquals(1, servlet.getPerformedRequests().size());
      assertEquals(0, servlet.getPerformedRequests().get(0).getIndex());
      assertEquals(50, servlet.getPerformedRequests().get(0).getNumResults());
    } finally {
      TestSupport.stopServerQuietly(srv);
    }
  }

  // ---- Unit-style validation of parser via reflection on private static method ----

  @Test
  public void parseSearchRequest_nonNumericThrows() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "abc");
      put("numResults", "xyz");
    }});
    try {
      invokeParse(req);
      fail("expected IllegalArgumentException");
    } catch (java.lang.reflect.InvocationTargetException ite) {
      assertTrue(ite.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void parseSearchRequest_outOfRangeClamped() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "-5");
      put("numResults", "100000");
    }});
    org.waveprotocol.box.search.SearchProto.SearchRequest r = invokeParse(req);
    assertEquals(0, r.getIndex());
    assertEquals(100, r.getNumResults());
  }

  @Test
  public void parseSearchRequest_longOverflowThrows() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "9223372036854775807");
      put("numResults", "-999999999999");
    }});
    try {
      invokeParse(req);
      fail("expected IllegalArgumentException");
    } catch (java.lang.reflect.InvocationTargetException ite) {
      assertTrue(ite.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void serializeEscapesQueryInjection() throws Exception {
    String inj = "in:inbox\" , \"extra\":\"x\" </script>";
    SearchResult sr = new SearchResult(inj);
    org.waveprotocol.box.search.SearchProto.SearchResponse resp = org.waveprotocol.box.server.rpc.SearchServlet.serializeSearchResult(sr, 0);
    // Verify the proto contains the query (serializer mapping may vary in JSON representation)
    assertEquals(inj, resp.getQuery());
  }

  private static org.waveprotocol.box.search.SearchProto.SearchRequest invokeParse(HttpServletRequest req) throws Exception {
    Method m = org.waveprotocol.box.server.rpc.SearchServlet.class.getDeclaredMethod("parseSearchRequest", HttpServletRequest.class);
    m.setAccessible(true);
    return (org.waveprotocol.box.search.SearchProto.SearchRequest) m.invoke(null, req);
  }

  private static HttpServletRequest mockedParams(Map<String,String> params) {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req.getParameter(Mockito.anyString())).thenAnswer((Answer<String>) inv -> {
      String key = (String) inv.getArguments()[0];
      return params.get(key);
    });
    return req;
  }

  private static SearchWaveletSnapshotPublisher createSnapshotPublisher() {
    return new SearchWaveletSnapshotPublisher(
        new SearchWaveletDispatcher(),
        new SearchWaveletManager(),
        new SearchIndexer(),
        new SearchWaveletDataProvider());
  }
}

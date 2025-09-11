package org.waveprotocol.box.server.jakarta;

import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.AttachmentServlet;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class AttachmentServletJakartaIT {
  private Server server;
  private int port;

  private AttachmentService svc;
  private WaveletProvider wprov;
  private SessionManager sm;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    try {
      svc = Mockito.mock(AttachmentService.class);
      wprov = Mockito.mock(WaveletProvider.class);
      sm = Mockito.mock(SessionManager.class);
      server = new Server();
      ServerConnector c = new ServerConnector(server);
      c.setPort(0);
      server.addConnector(c);
      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      AttachmentServlet servlet = new AttachmentServlet(svc, wprov, sm, ConfigFactory.parseString("core.thumbnail_patterns_directory=\".\""));
      ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), AttachmentServlet.ATTACHMENT_URL + "/*");
      server.setHandler(ctx);
      server.start();
      port = c.getLocalPort();
    } catch (NoClassDefFoundError | IncompatibleClassChangeError e) {
      TestSupport.assumeJettyEe10PresentOrSkip();
    }
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void forbiddenWhenUnauthenticated() throws Exception {
    // No user
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(null);
    // Ensure metadata exists so servlet reaches authorization
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void thumbnailPatternFallbackWhenDirInvalid() throws Exception {
    // Spin a local server with an invalid patterns directory
    Server srv = new Server();
    ServerConnector c = new ServerConnector(srv);
    c.setPort(0);
    srv.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    AttachmentService svc2 = Mockito.mock(AttachmentService.class);
    WaveletProvider wprov2 = Mockito.mock(WaveletProvider.class);
    SessionManager sm2 = Mockito.mock(SessionManager.class);
    Mockito.when(sm2.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(new ParticipantId("user@example.com"));
    Mockito.when(wprov2.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any())).thenReturn(true);
    AttachmentServlet servlet = new AttachmentServlet(svc2, wprov2, sm2, com.typesafe.config.ConfigFactory.parseString("core.thumbnail_patterns_directory=\"/path/does/not/exist\""));
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), AttachmentServlet.THUMBNAIL_URL + "/*");
    srv.setHandler(ctx);
    srv.start();
    int p = c.getLocalPort();
    try {
      AttachmentId aid = AttachmentId.deserialise("att+123");
      AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
      Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
      Mockito.when(meta.getMimeType()).thenReturn("application/pdf"); // non-image -> pattern path
      Mockito.when(meta.hasImageMetadata()).thenReturn(false);
      Mockito.when(svc2.getMetadata(aid)).thenReturn(meta);

      URL url = new URL("http://localhost:" + p + AttachmentServlet.THUMBNAIL_URL + "/att+123?waveRef=local:wave/local/wavelet");
      HttpURLConnection hc = (HttpURLConnection) url.openConnection();
      assertEquals(200, hc.getResponseCode());
      // Explicitly validate fallback PNG is used (invalid dir forces generated pattern)
      byte[] bytes = hc.getInputStream().readAllBytes();
      assertTrue("expected non-empty thumbnail bytes", bytes.length > 8);
      // PNG signature: 89 50 4E 47 0D 0A 1A 0A
      assertEquals((byte)0x89, bytes[0]);
      assertEquals((byte)0x50, bytes[1]);
      assertEquals((byte)0x4E, bytes[2]);
      assertEquals((byte)0x47, bytes[3]);
      assertEquals((byte)0x0D, bytes[4]);
      assertEquals((byte)0x0A, bytes[5]);
      assertEquals((byte)0x1A, bytes[6]);
      assertEquals((byte)0x0A, bytes[7]);
      String ct = hc.getHeaderField("Content-Type");
      assertNotNull(ct);
      assertTrue(ct.toLowerCase().contains("png"));
    } finally {
      srv.stop();
    }
  }

  @Test
  public void servesAttachmentWhenAuthorized() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData data = new org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream("OK".getBytes()); }
      @Override public long getSize() { return 2; }
    };
    Mockito.when(svc.getAttachment(aid)).thenReturn(data);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertTrue(String.valueOf(c.getHeaderField("Content-Type")).contains("text/plain"));
    String disp = c.getHeaderField("Content-Disposition");
    assertNotNull(disp);
    assertTrue(disp.contains("hello.txt"));
  }

  @Test
  public void rejectsExtraPathSegments() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(new ParticipantId("user@example.com"));
    // Even if metadata exists for the base id, extra segments must 404
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123/evil?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertTrue("expected client error for backslash in id", c.getResponseCode() >= 400);
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(), Mockito.any());
  }

  @Test
  public void rejectsBackslashInId() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(new ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att\\123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    // Jetty 12 normalizes/flags invalid path chars; accept any client error
    assertTrue("expected client error for backslash in id", c.getResponseCode() >= 400);
  }

  @Test
  public void rejectsDotOrDotDot() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(new ParticipantId("user@example.com"));
    URL u1 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/.." );
    assertEquals(404, ((HttpURLConnection) u1.openConnection()).getResponseCode());
    URL u2 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/." );
    assertEquals(404, ((HttpURLConnection) u2.openConnection()).getResponseCode());
  }

  @Test
  public void rejectsExcessiveLength() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(new ParticipantId("user@example.com"));
    String longId = "a".repeat(1024);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/" + longId);
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    Mockito.verify(svc, Mockito.never()).getMetadata(Mockito.any());
  }

  @Test
  public void acceptsDomainSlashIdSingleSegment() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);
    AttachmentId aid = AttachmentId.deserialise("example.com/att123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData data = new org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream("OK".getBytes()); }
      @Override public long getSize() { return 2; }
    };
    Mockito.when(svc.getAttachment(aid)).thenReturn(data);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/example.com/att123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
  }

  @Test
  public void forbiddenWhenAuthorizedButNoPermission() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+nope");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("nope.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+secret/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+nope?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void ignoresWaveRefParamUsesMetadataForAuth() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    // Return false regardless; we assert the wavelet used equals metadata's
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+meta");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("meta.txt");
    // Metadata points to a different wave than the request parameter
    String metadataWaveRef = "local:wave/other/wavelet";
    Mockito.when(meta.getWaveRef()).thenReturn(metadataWaveRef);
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+meta?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());

    ArgumentCaptor<WaveletName> cap = ArgumentCaptor.forClass(WaveletName.class);
    Mockito.verify(wprov).checkAccessPermission(cap.capture(), Mockito.eq(user));
    WaveletName used = cap.getValue();
    WaveletName expected = AttachmentUtil.waveRef2WaveletName(metadataWaveRef);
    assertEquals(expected, used);
  }

  @Test
  public void missingWaveRefParamStillUsesMetadataForAuth() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(javax.servlet.http.HttpSession.class))).thenReturn(user);
    // Provider denies based on metadata; request has no waveRef param
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+meta2");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("meta2.txt");
    String metadataWaveRef = "example.com/w+meta2/example.com/conv+root";
    Mockito.when(meta.getWaveRef()).thenReturn(metadataWaveRef);
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+meta2");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());

    ArgumentCaptor<WaveletName> cap = ArgumentCaptor.forClass(WaveletName.class);
    Mockito.verify(wprov).checkAccessPermission(cap.capture(), Mockito.eq(user));
    WaveletName used = cap.getValue();
    WaveletName expected = AttachmentUtil.waveRef2WaveletName(metadataWaveRef);
    assertEquals(expected, used);
    Mockito.verify(svc, Mockito.never()).getAttachment(Mockito.any());
  }

  @Test
  public void ignoresWaveRefOnThumbnailAuth() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(sm.getLoggedInUser(Mockito.isNull(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);

    AttachmentId aid = AttachmentId.deserialise("att+img");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("image/jpeg");
    Mockito.when(meta.getFileName()).thenReturn("pic.jpg");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+img/example.com/conv+root");
    Mockito.when(meta.hasImageMetadata()).thenReturn(true);
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData thumb = new org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[]{1,2,3}); }
      @Override public long getSize() { return 3; }
    };
    Mockito.when(svc.getThumbnail(aid)).thenReturn(thumb);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.THUMBNAIL_URL + "/att+img?waveRef=local:wave/IGNORED/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    // ensure attachment data was not fetched for thumbnail path
    Mockito.verify(svc, Mockito.never()).getAttachment(Mockito.any());
  }

  @Test
  public void doesNotBuildMetadataFromRequestParams() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    AttachmentId aid = AttachmentId.deserialise("att+missing2");
    Mockito.when(svc.getMetadata(aid)).thenReturn(null);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+missing2?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    // Must not attempt to create metadata using request parameters
    Mockito.verify(svc, Mockito.never()).buildAndStoreMetadataWithThumbnail(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void unauthorizedDoesNotStreamAttachment() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(javax.servlet.http.HttpSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);
    AttachmentId aid = AttachmentId.deserialise("att+deny");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("deny.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+deny/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+deny?waveRef=local:wave/ALSO_IGNORED/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
    Mockito.verify(svc, Mockito.never()).getAttachment(Mockito.any());
  }

  @Test
  public void notFoundWhenMetadataMissingEvenIfWaveRefProvided() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    AttachmentId aid = AttachmentId.deserialise("att+missing");
    Mockito.when(svc.getMetadata(aid)).thenReturn(null);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+missing?waveRef=example.com/w+miss/example.com/conv+root");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any());
  }
}

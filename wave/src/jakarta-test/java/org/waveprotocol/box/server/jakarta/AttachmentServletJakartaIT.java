package org.waveprotocol.box.server.jakarta;

import com.typesafe.config.ConfigFactory;
import jakarta.servlet.MultipartConfigElement;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.rpc.AttachmentServlet;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

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
      ServletHolder holder = new ServletHolder(servlet);
      holder.getRegistration().setMultipartConfig(new MultipartConfigElement(""));
      ctx.addServlet(holder, AttachmentServlet.ATTACHMENT_URL + "/*");
      // Map thumbnail as well for tests that hit /thumbnail/* on the base server
      ctx.addServlet(holder, AttachmentServlet.THUMBNAIL_URL + "/*");
      server.setHandler(ctx);
      server.start();
      port = c.getLocalPort();
    } catch (NoClassDefFoundError | IncompatibleClassChangeError e) {
      TestSupport.assumeJettyEe10PresentOrSkip();
    }
  }

  @After
  public void stop() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void forbiddenWhenUnauthenticated() throws Exception {
    // No user
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(null);
    // Ensure metadata exists so servlet reaches authorization
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
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
    stubLoggedIn(sm2, new ParticipantId("user@example.com"));
    Mockito.when(wprov2.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any())).thenReturn(true);
    AttachmentServlet servlet = new AttachmentServlet(svc2, wprov2, sm2, com.typesafe.config.ConfigFactory.parseString("core.thumbnail_patterns_directory=\"/path/does/not/exist\""));
    ctx.addServlet(new ServletHolder(servlet), AttachmentServlet.THUMBNAIL_URL + "/*");
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
      HttpURLConnection hc = TestSupport.openConnection(url);
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
      TestSupport.stopServerQuietly(srv);
    }
  }

  @Test
  public void servesAttachmentWhenAuthorized() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
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
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
    assertTrue(String.valueOf(c.getHeaderField("Content-Type")).contains("text/plain"));
    String disp = c.getHeaderField("Content-Disposition");
    assertNotNull(disp);
    assertTrue(disp.contains("hello.txt"));
  }

  @Test
  public void uploadsAttachmentWhenAuthorized() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);

    AttachmentId aid = AttachmentId.deserialise("att+upload");
    String waveRef = "example.com/w+upload/example.com/conv+root";
    WaveletName expectedWavelet = AttachmentUtil.waveRef2WaveletName(waveRef);
    byte[] fileBytes = "uploaded payload".getBytes(StandardCharsets.UTF_8);
    AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
    Mockito.doAnswer(invocation -> {
      InputStream stream = invocation.getArgument(1);
      uploadedBytes.set(stream.readAllBytes());
      return null;
    }).when(svc).storeAttachment(
        Mockito.eq(aid),
        Mockito.any(InputStream.class),
        Mockito.eq(expectedWavelet),
        Mockito.eq("hello.txt"),
        Mockito.eq(user));

    HttpURLConnection c = postMultipart(
        AttachmentServlet.ATTACHMENT_URL + "/upload",
        aid.serialise(),
        waveRef,
        "nested/path/hello.txt",
        fileBytes);

    assertEquals(201, c.getResponseCode());
    assertArrayEquals(fileBytes, uploadedBytes.get());
    Mockito.verify(svc).storeAttachment(
        Mockito.eq(aid),
        Mockito.any(InputStream.class),
        Mockito.eq(expectedWavelet),
        Mockito.eq("hello.txt"),
        Mockito.eq(user));
  }

  @Test
  public void rejectsExtraPathSegments() throws Exception {
    stubLoggedIn(sm, new ParticipantId("user@example.com"));
    // Even if metadata exists for the base id, extra segments must 404
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+abc/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123/evil?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertTrue("expected client error for backslash in id", c.getResponseCode() >= 400);
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(), Mockito.any());
  }

  @Test
  public void rejectsBackslashInId() throws Exception {
    stubLoggedIn(sm, new ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att\\123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
    // Jetty 12 normalizes/flags invalid path chars; accept any client error
    assertTrue("expected client error for backslash in id", c.getResponseCode() >= 400);
  }

  @Test
  public void rejectsDotOrDotDot() throws Exception {
    stubLoggedIn(sm, new ParticipantId("user@example.com"));
    URL u1 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/.." );
    HttpURLConnection c1 = TestSupport.openConnection(u1);
    assertEquals(404, c1.getResponseCode());
    URL u2 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/." );
    HttpURLConnection c2 = TestSupport.openConnection(u2);
    assertEquals(404, c2.getResponseCode());
  }

  @Test
  public void rejectsExcessiveLength() throws Exception {
    stubLoggedIn(sm, new ParticipantId("user@example.com"));
    String longId = "a".repeat(1024);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/" + longId);
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(404, c.getResponseCode());
    Mockito.verify(svc, Mockito.never()).getMetadata(Mockito.any());
  }

  @Test
  public void acceptsDomainSlashIdSingleSegment() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
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
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
  }

  @Test
  public void forbiddenWhenAuthorizedButNoPermission() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+nope");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("nope.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+secret/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+nope?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void ignoresWaveRefParamUsesMetadataForAuth() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
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
    HttpURLConnection c = TestSupport.openConnection(url);
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
    stubLoggedIn(sm, user);
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
    HttpURLConnection c = TestSupport.openConnection(url);
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
    stubLoggedIn(sm, user);
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
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
    // ensure attachment data was not fetched for thumbnail path
    Mockito.verify(svc, Mockito.never()).getAttachment(Mockito.any());
  }

  @Test
  public void doesNotBuildMetadataFromRequestParams() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    AttachmentId aid = AttachmentId.deserialise("att+missing2");
    Mockito.when(svc.getMetadata(aid)).thenReturn(null);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+missing2?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(404, c.getResponseCode());
    // Must not attempt to create metadata using request parameters
    Mockito.verify(svc, Mockito.never()).buildAndStoreMetadataWithThumbnail(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void unauthorizedDoesNotStreamAttachment() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);
    AttachmentId aid = AttachmentId.deserialise("att+deny");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("deny.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("example.com/w+deny/example.com/conv+root");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+deny?waveRef=local:wave/ALSO_IGNORED/wavelet");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(403, c.getResponseCode());
    Mockito.verify(svc, Mockito.never()).getAttachment(Mockito.any());
  }

  @Test
  public void notFoundWhenMetadataMissingEvenIfWaveRefProvided() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    stubLoggedIn(sm, user);
    AttachmentId aid = AttachmentId.deserialise("att+missing");
    Mockito.when(svc.getMetadata(aid)).thenReturn(null);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+missing?waveRef=example.com/w+miss/example.com/conv+root");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(404, c.getResponseCode());
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any());
  }

  private static void stubLoggedIn(SessionManager manager, ParticipantId user) {
    Mockito.when(manager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(user);
    Mockito.when(manager.getLoggedInUser(Mockito.isNull(WebSession.class))).thenReturn(user);
  }

  private HttpURLConnection postMultipart(String path, String attachmentId, String waveRef,
                                          String submittedFileName, byte[] fileBytes) throws Exception {
    String boundary = "----WaveMultipart" + System.nanoTime();
    byte[] body = buildMultipartBody(boundary, attachmentId, waveRef, submittedFileName, fileBytes);
    URL url = new URL("http://localhost:" + port + path);
    HttpURLConnection connection = TestSupport.openConnection(url);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    connection.setRequestProperty("Content-Length", String.valueOf(body.length));
    try (OutputStream out = connection.getOutputStream()) {
      out.write(body);
      out.flush();
    }
    return connection;
  }

  private static byte[] buildMultipartBody(String boundary, String attachmentId, String waveRef,
                                           String submittedFileName, byte[] fileBytes) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeFormField(out, boundary, "attachmentId", attachmentId);
    writeFormField(out, boundary, "waveRef", waveRef);
    writeFileField(out, boundary, "file", submittedFileName, fileBytes);
    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  private static void writeFormField(ByteArrayOutputStream out, String boundary, String name,
                                     String value) throws Exception {
    writeMultipartHeader(out, boundary, name, null, null);
    out.write(value.getBytes(StandardCharsets.UTF_8));
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private static void writeFileField(ByteArrayOutputStream out, String boundary, String name,
                                     String fileName, byte[] content) throws Exception {
    writeMultipartHeader(out, boundary, name, fileName, "text/plain");
    out.write(content);
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private static void writeMultipartHeader(ByteArrayOutputStream out, String boundary, String name,
                                           String fileName, String contentType) throws Exception {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
    if (fileName != null) {
      disposition += "; filename=\"" + fileName + "\"";
    }
    out.write((disposition + "\r\n").getBytes(StandardCharsets.UTF_8));
    if (contentType != null) {
      out.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }
}

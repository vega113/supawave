package org.waveprotocol.box.server.jakarta;

import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.rpc.AuthenticationServlet;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.wave.model.wave.ParticipantId;

import javax.security.auth.login.Configuration;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class AuthenticationServletJakartaIT {
  private Server server;
  private int port;

  private SessionManager sessionManager;
  private BrowserSessionJwtIssuer browserSessionJwtIssuer;
  private AccountStore accountStore;
  private EmailTokenIssuer emailTokenIssuer;
  private MailProvider mailProvider;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    sessionManager = Mockito.mock(SessionManager.class);
    browserSessionJwtIssuer = Mockito.mock(BrowserSessionJwtIssuer.class);
    accountStore = Mockito.mock(AccountStore.class);
    emailTokenIssuer = Mockito.mock(EmailTokenIssuer.class);
    mailProvider = Mockito.mock(MailProvider.class);

    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);

    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");

    var cfg = ConfigFactory.parseString(
        "security.enable_ssl=false\n" +
        "security.enable_clientauth=false\n" +
        "security.clientauth_cert_domain=example.com\n" +
        "administration.disable_registration=true\n" +
        "administration.disable_loginpage=false\n"
    );
    AuthEmailService authEmailService = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        java.time.Clock.systemUTC(),
        cfg
    );
    AuthenticationServlet servlet = new AuthenticationServlet(
        accountStore,
        Configuration.getConfiguration(),
        sessionManager,
        "example.com",
        cfg,
        browserSessionJwtIssuer,
        authEmailService,
        Mockito.mock(AnalyticsRecorder.class)
    );

    ctx.addServlet(new ServletHolder(servlet), "/auth/signin");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();

    stubLoggedOut();
  }

  @After
  public void stop() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void get_withoutLogin_showsLoginPage200() throws Exception {
    stubLoggedOut();
    URL url = new URL("http://localhost:" + port + "/auth/signin");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").contains("text/html"));
  }

  @Test
  public void get_whenLoggedIn_redirectsToR() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + "/auth/signin?r=%2Fhome");
    HttpURLConnection c = TestSupport.openConnection(url);
    // Jetty follows redirects by default if using HttpURLConnection? It does not automatically follow for 302 unless setInstanceFollowRedirects.
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/home", c.getHeaderField("Location"));
  }

  @Test
  public void redirectsToSafePathWithQuery() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    // r=/home?x=1&y=2 -> urlencoded as %2Fhome%3Fx%3D1%26y%3D2
    URL url = new URL("http://localhost:" + port + "/auth/signin?r=%2Fhome%3Fx%3D1%26y%3D2");
    HttpURLConnection c = TestSupport.openConnection(url);
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/home?x=1&y=2", c.getHeaderField("Location"));
  }

  @Test
  public void redirectsToSafePathWithFragment() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    // r=/app#section -> urlencoded as %2Fapp%23section
    URL url = new URL("http://localhost:" + port + "/auth/signin?r=%2Fapp%23section");
    HttpURLConnection c = TestSupport.openConnection(url);
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/app#section", c.getHeaderField("Location"));
  }

  @Test
  public void rejectsAbsoluteUrlInR() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    // r=http://evil.example
    URL url = new URL("http://localhost:" + port + "/auth/signin?r=http%3A%2F%2Fevil.example");
    HttpURLConnection c = TestSupport.openConnection(url);
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/", c.getHeaderField("Location"));
  }

  @Test
  public void rejectsSchemeRelativeAndTraversal() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    // r=//evil.example -> fallback
    URL u1 = new URL("http://localhost:" + port + "/auth/signin?r=%2F%2Fevil.example");
    HttpURLConnection c1 = TestSupport.openConnection(u1);
    c1.setInstanceFollowRedirects(false);
    assertEquals(302, c1.getResponseCode());
    assertEquals("/", c1.getHeaderField("Location"));

    // r=/../secret -> fallback
    URL u2 = new URL("http://localhost:" + port + "/auth/signin?r=%2F..%2Fsecret");
    HttpURLConnection c2 = TestSupport.openConnection(u2);
    c2.setInstanceFollowRedirects(false);
    assertEquals(302, c2.getResponseCode());
    assertEquals("/", c2.getHeaderField("Location"));
  }

  @Test
  public void rejectsCRLFInR() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    URL u = new URL("http://localhost:" + port + "/auth/signin?r=%2Fok%0D%0AX-Evil%3Ayes");
    HttpURLConnection c = TestSupport.openConnection(u);
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/", c.getHeaderField("Location"));
  }

  @Test
  public void rejectsEncodedTraversalAndBackslashes() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));

    // Encoded traversal in path
    URL u1 = new URL("http://localhost:" + port + "/auth/signin?r=%2Fa%2F%252e%252e%2Fb");
    HttpURLConnection c1 = TestSupport.openConnection(u1);
    c1.setInstanceFollowRedirects(false);
    assertEquals(302, c1.getResponseCode());
    assertEquals("/", c1.getHeaderField("Location"));

    // Encoded backslash
    URL u2 = new URL("http://localhost:" + port + "/auth/signin?r=%5Ca%5Cb");
    HttpURLConnection c2 = TestSupport.openConnection(u2);
    c2.setInstanceFollowRedirects(false);
    assertEquals(302, c2.getResponseCode());
    assertEquals("/", c2.getHeaderField("Location"));

    // Literal backslash after decode
    URL u3 = new URL("http://localhost:" + port + "/auth/signin?r=%2Fa\\..\\b");
    HttpURLConnection c3 = TestSupport.openConnection(u3);
    c3.setInstanceFollowRedirects(false);
    assertEquals(302, c3.getResponseCode());
    assertEquals("/", c3.getHeaderField("Location"));
  }

  @Test
  public void rejectsLeadingWhitespace() throws Exception {
    stubLoggedIn(new ParticipantId("user@example.com"));
    URL u = new URL("http://localhost:" + port + "/auth/signin?r=%20/home");
    HttpURLConnection c = TestSupport.openConnection(u);
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/", c.getHeaderField("Location"));
  }

  private void stubLoggedOut() {
    Mockito.reset(sessionManager);
    Mockito.doReturn(null).when(sessionManager).getLoggedInUser(Mockito.any());
  }

  private void stubLoggedIn(ParticipantId id) {
    Mockito.reset(sessionManager);
    Mockito.doReturn(id).when(sessionManager).getLoggedInUser(Mockito.any());
  }
}

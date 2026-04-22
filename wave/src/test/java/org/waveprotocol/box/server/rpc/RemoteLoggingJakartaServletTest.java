package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class RemoteLoggingJakartaServletTest {
  @Test
  public void doPostRejectsAnonymousRequests() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    RemoteLoggingJakartaServlet servlet = new RemoteLoggingJakartaServlet(sessionManager);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getSession(false)).thenReturn(null);

    servlet.doPost(request, response);

    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Authentication required");
    verify(request, never()).getReader();
  }

  @Test
  public void doPostAcceptsAuthenticatedPlainTextLogs() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    RemoteLoggingJakartaServlet servlet = new RemoteLoggingJakartaServlet(sessionManager);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(sessionManager.getLoggedInUser(any(WebSession.class)))
        .thenReturn(ParticipantId.ofUnsafe("alice@example.com"));
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("hello from gwt")));
    when(request.getContentType()).thenReturn("text/plain");
    when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {}

      @Override
      public void write(int b) {
        output.write(b);
      }
    });

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("text/plain; charset=utf-8");
    assertEquals("OK", output.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void doPostLogsPlainTextPayloadLinesIndividuallyWithUserContext() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    RemoteLoggingJakartaServlet servlet = new RemoteLoggingJakartaServlet(sessionManager);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Logger logger = Logger.getLogger(RemoteLoggingJakartaServlet.class.getName());
    RecordingHandler handler = new RecordingHandler();
    Level previousLevel = logger.getLevel();
    boolean previousUseParentHandlers = logger.getUseParentHandlers();

    logger.addHandler(handler);
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    try {
      when(request.getSession(false)).thenReturn(mock(HttpSession.class));
      when(sessionManager.getLoggedInUser(any(WebSession.class)))
          .thenReturn(ParticipantId.ofUnsafe("alice@example.com"));
      when(request.getReader()).thenReturn(
          new BufferedReader(new StringReader("line one\nline two\n")));
      when(request.getContentType()).thenReturn("text/plain; charset=utf-8");
      when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {}

        @Override
        public void write(int b) {
          output.write(b);
        }
      });

      servlet.doPost(request, response);
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(previousUseParentHandlers);
      logger.setLevel(previousLevel);
    }

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("text/plain; charset=utf-8");
    assertEquals("OK", output.toString(StandardCharsets.UTF_8));
    assertEquals(2, handler.messages.size());
    assertEquals("[remote_log] user=alice@example.com line one", handler.messages.get(0));
    assertEquals("[remote_log] user=alice@example.com line two", handler.messages.get(1));
  }

  private static final class RecordingHandler extends Handler {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      messages.add(record.getMessage());
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}

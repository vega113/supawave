package org.waveprotocol.box.server.jakarta;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.stat.MultiThreadedRequestScope;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.wave.ParticipantId;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class RequestScopeFilterJakartaTest {
  private SessionManager sessionManager;
  private RequestScopeFilter filter;

  @Before
  public void setUp() {
    sessionManager = mock(SessionManager.class);
    filter = new RequestScopeFilter(sessionManager);
    Timing.setScope(new MultiThreadedRequestScope());
    Timing.setEnabled(true);
  }

  @After
  public void tearDown() {
    Timing.setEnabled(false);
    Timing.setScope(null);
    Timing.exitScope();
  }

  @Test
  public void wrapsServletSessionForSessionManager() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    HttpSession servletSession = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(servletSession);
    when(servletSession.getId()).thenReturn("session-1");
    ParticipantId user = ParticipantId.ofUnsafe("user@example.com");
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);

    filter.doFilter(request, response, chain);

    ArgumentCaptor<WebSession> captor = ArgumentCaptor.forClass(WebSession.class);
    verify(sessionManager).getLoggedInUser(captor.capture());
    assertNotNull("WebSession wrapper should be provided", captor.getValue());
    verify(chain).doFilter(request, response);
  }

  @Test
  public void toleratesMissingSessionManager() throws IOException, ServletException {
    RequestScopeFilter noManagerFilter = new RequestScopeFilter();
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getSession(false)).thenReturn(null);

    noManagerFilter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}

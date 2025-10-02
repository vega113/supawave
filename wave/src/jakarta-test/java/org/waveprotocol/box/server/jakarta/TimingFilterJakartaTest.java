package org.waveprotocol.box.server.jakarta;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.stat.MultiThreadedRequestScope;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.stat.Timing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TimingFilterJakartaTest {
  private TimingFilter filter;

  @Before
  public void setUp() {
    filter = new TimingFilter();
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
  public void delegatesToChain() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getRequestURI()).thenReturn("/test");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}

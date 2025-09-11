package org.waveprotocol.box.server.jakarta;

import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.JakartaSessionAdapters;

import static org.junit.Assert.*;

public class JakartaSessionAdaptersTest {

  @Test
  public void returnsNullWhenRequestNull() {
    assertNull(JakartaSessionAdapters.fromRequest(null, false));
  }

  @Test
  public void returnsNullWhenGetSessionFalseReturnsNull() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(req.getSession(false)).thenReturn(null);
    assertNull(JakartaSessionAdapters.fromRequest(req, false));
  }

  @Test
  public void wrapsDelegateWhenPresent() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    jakarta.servlet.http.HttpSession js = Mockito.mock(jakarta.servlet.http.HttpSession.class);
    Mockito.when(req.getSession(false)).thenReturn(js);
    Mockito.when(js.getAttribute("k")).thenReturn("v");

    javax.servlet.http.HttpSession jx = JakartaSessionAdapters.fromRequest(req, false);
    assertNotNull(jx);
    assertEquals("v", jx.getAttribute("k"));

    jx.setAttribute("k2", "v2");
    Mockito.verify(js).setAttribute("k2", "v2");
  }

  @Test
  public void catchesIllegalStateException() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(req.getSession(false)).thenThrow(new IllegalStateException("sessions disabled"));
    assertNull(JakartaSessionAdapters.fromRequest(req, false));
  }
}


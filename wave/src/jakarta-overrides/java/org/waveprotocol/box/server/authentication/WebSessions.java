package org.waveprotocol.box.server.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Helpers to adapt container sessions to WebSession. */
public final class WebSessions {
  private WebSessions() {}

  public static WebSession from(HttpServletRequest req, boolean create) {
    HttpSession js = req != null ? req.getSession(create) : null;
    return (js != null) ? wrap(js) : null;
  }

  public static WebSession wrap(HttpSession js) {
    return (js == null) ? null : new JakartaWebSession(js);
  }

  static final class JakartaWebSession implements WebSession {
    private final HttpSession d;
    JakartaWebSession(HttpSession d) { this.d = d; }
    @Override public Object getAttribute(String name) { return d.getAttribute(name); }
    @Override public void setAttribute(String name, Object value) { d.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { d.removeAttribute(name); }
  }
}


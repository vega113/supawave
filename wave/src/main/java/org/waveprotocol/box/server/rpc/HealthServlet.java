package org.waveprotocol.box.server.rpc;

import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Simple health/readiness endpoint. Returns 200 OK with "ok" body.
 * Registered for /healthz and /readyz.
 */
@Singleton
public class HealthServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=UTF-8");
    resp.getWriter().write("ok\n");
  }
}


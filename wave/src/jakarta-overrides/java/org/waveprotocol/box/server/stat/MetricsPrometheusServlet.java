package org.waveprotocol.box.server.stat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Exposes Prometheus metrics at /metrics for the Jakarta path.
 */
public class MetricsPrometheusServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String scrape = MetricsHolder.prometheus().scrape();
    byte[] out = scrape.getBytes(StandardCharsets.UTF_8);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
    resp.setContentLength(out.length);
    try (var os = resp.getOutputStream()) { os.write(out); os.flush(); }
  }
}


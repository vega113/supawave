package org.waveprotocol.box.server.rpc;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Jakarta-compatible remote logging endpoint. Accepts POST bodies
 * (including text/x-gwt-rpc or JSON/text) and writes them to server logs.
 * This is a best-effort compatibility shim; it does not implement full GWT RPC.
 */
public class RemoteLoggingJakartaServlet extends HttpServlet {
  private static final Log LOG = Log.get(RemoteLoggingJakartaServlet.class);

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Read body upto 32 KiB to avoid unbounded growth
    final int max = 32 * 1024; // 32 KiB hard cap
    int cl = req.getContentLength();
    int initial = 256;
    if (cl > 0) {
      // Bound initial capacity strictly to max to avoid large preallocation
      initial = Math.min(max, Math.max(initial, cl));
    }
    StringBuilder sb = new StringBuilder(initial);
    char[] buf = new char[2048];
    int n, total = 0;
    try (var reader = req.getReader()) {
      while ((n = reader.read(buf)) != -1) {
        if (total >= max) break;
        int remaining = max - total;
        int toAppend = Math.min(n, remaining);
        sb.append(buf, 0, toAppend);
        total += toAppend;
        if (toAppend < n) break; // stop if we truncated this chunk
      }
    }
    String payload = sb.toString();
    String ct = req.getContentType();
    if (ct == null) ct = "";

    if (ct.startsWith("text/x-gwt-rpc")) {
      // Heuristic parser for GWT RPC RemoteLogging payloads
      String level = null, message = null, logger = null;
      try {
        // Typical format: tokens separated by '|', includes interface and method names
        String[] tok = payload.split("\\|");
        for (int i = 0; i < tok.length; i++) {
          if ("logOnServer".equals(tok[i])) {
            // Walk forward to find a plausible message/level/logger triplet
            for (int j = i + 1; j < tok.length; j++) {
              String t = tok[j];
              if (t == null) continue;
              t = t.trim();
              if (t.equals("SEVERE") || t.equals("WARNING") || t.equals("INFO") || t.equals("CONFIG") ||
                  t.equals("FINE") || t.equals("FINER") || t.equals("FINEST")) {
                level = t;
                // Usually next non-empty after level is message, then logger
                message = (j + 1 < tok.length) ? tok[j + 1] : null;
                logger = (j + 2 < tok.length) ? tok[j + 2] : null;
                break;
              }
            }
            break;
          }
        }
      } catch (Throwable ignore) { /* fall through to summary */ }

      if (level == null) level = "INFO";
      String line = "[remote_log][gwt] level=" + level +
          (logger != null ? (" logger=" + sanitize(logger)) : "") +
          (message != null ? (" msg=" + sanitize(message)) : (" msg=" + summarize(payload)));
      switch (level) {
        case "SEVERE": LOG.severe(line); break;
        case "WARNING": LOG.warning(line); break;
        case "INFO": LOG.info(line); break;
        default: LOG.fine(line); break;
      }
      resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
      return;
    }

    LOG.info("[remote_log] " + summarize(payload));
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=utf-8");
    resp.getOutputStream().write("OK".getBytes(StandardCharsets.UTF_8));
  }

  private static String summarize(String s) {
    if (s == null) return "<null>";
    s = s.replace('\r', ' ').replace('\n', ' ');
    return (s.length() > 500) ? (s.substring(0, 500) + "…") : s;
  }
  private static String sanitize(String s) {
    if (s == null) return "";
    s = s.replace('\r', ' ').replace('\n', ' ');
    if (s.length() > 2000) s = s.substring(0, 2000) + "…";
    return s;
  }
}

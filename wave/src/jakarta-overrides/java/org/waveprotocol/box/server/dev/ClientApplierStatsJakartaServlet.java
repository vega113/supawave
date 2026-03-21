package org.waveprotocol.box.server.dev;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import org.waveprotocol.wave.util.logging.Log;

public final class ClientApplierStatsJakartaServlet extends HttpServlet {
  private static final Log LOG = Log.get(ClientApplierStatsJakartaServlet.class);
  private static final String ATTR = "clientApplierStats";

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession session = req.getSession(true);
    int applied = parseIntSafe(req.getParameter("applied"), "applied");
    int rejected = parseIntSafe(req.getParameter("rejected"), "rejected");
    session.setAttribute(ATTR, new Stats(applied, rejected));
    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession session = req.getSession(false);
    Stats stats = session != null ? (Stats) session.getAttribute(ATTR) : null;
    resp.setContentType("application/json;charset=UTF-8");
    try (PrintWriter writer = resp.getWriter()) {
      if (stats == null) {
        writer.write("{\"applied\":0,\"rejected\":0}");
      } else {
        writer.write("{\"applied\":" + stats.applied + ",\"rejected\":" + stats.rejected + "}");
      }
    }
  }

  private static int parseIntSafe(String value, String field) {
    int parsed = 0;
    if (value == null) {
      LOG.fine("client-applier-stats: missing '" + field + "' parameter; defaulting to 0");
    } else {
      try {
        parsed = Integer.parseInt(value.trim());
      } catch (NumberFormatException e) {
        LOG.warning("client-applier-stats: invalid integer for '" + field + "': '" + value + "'", e);
        parsed = 0;
      } catch (Throwable t) {
        LOG.warning("client-applier-stats: error parsing '" + field + "': '" + value + "'", t);
        parsed = 0;
      }
    }
    return parsed;
  }

  private static final class Stats implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final int applied;
    private final int rejected;

    private Stats(int applied, int rejected) {
      this.applied = applied;
      this.rejected = rejected;
    }
  }
}

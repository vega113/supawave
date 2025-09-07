/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.stat;

import com.google.inject.Singleton;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.waveprotocol.box.stat.Timing;

/**
 * Servlet to show server statistic.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
@Singleton
public class StatuszServlet extends HttpServlet {
  private final String SHOW_SESSION_MEASUREMENTS = "session-measurements";
  private final String SHOW_GLOBAL_MEASUREMENTS = "global-measurements";
  private final String SHOW_STATS = "stats";
  private final String SHOW_FRAGMENTS = "fragments";

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/html");

    PrintWriter writer = resp.getWriter();
    writeHeader(writer);

    String show = req.getParameter("show");
    if (show == null) {
      show = SHOW_SESSION_MEASUREMENTS;
    }
    switch (show) {
      case SHOW_SESSION_MEASUREMENTS:
        writeSessionMeasurements(writer);
        break;
      case SHOW_GLOBAL_MEASUREMENTS:
        writeGlobalMeasurements(writer);
        break;
      case SHOW_STATS:
        writeStats(writer);
        break;
      case SHOW_FRAGMENTS:
        writeFragments(writer);
        break;
    }
  }

  protected void writeHeader(PrintWriter writer) {
    writer.write("<a href=\"?show=" + SHOW_SESSION_MEASUREMENTS + "\">Session measurements</a>");
    writer.write(" | <a href=\"?show=" + SHOW_GLOBAL_MEASUREMENTS + "\">Global measurements</a>");
    writer.write(" | <a href=\"?show=" + SHOW_STATS + "\">Stats</a>");
    writer.write(" | <a href=\"?show=" + SHOW_FRAGMENTS + "\">Fragments</a>");
  }

  protected void writeSessionMeasurements(PrintWriter writer) {
    writer.write(Timing.renderTitle("Session measurements", 2));
    writer.write(Timing.renderSessionStatistics());
  }

  protected void writeGlobalMeasurements(PrintWriter writer) {
    writer.write(Timing.renderTitle("Global measurements", 2));
    writer.write(Timing.renderGlobalStatistics());
  }

  protected void writeStats(PrintWriter writer) {
    writer.write(Timing.renderTitle("Stats", 2));
    writer.write(Timing.renderStats());
  }

  protected void writeFragments(PrintWriter writer) {
    writer.write(Timing.renderTitle("Fragments Metrics", 2));
    try {
      Class<?> cls = Class.forName("org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics");
      boolean enabled = (Boolean) cls.getMethod("isEnabled").invoke(null);
      java.util.concurrent.atomic.AtomicLong emissionCount =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("emissionCount").get(null);
      java.util.concurrent.atomic.AtomicLong emissionErrors =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("emissionErrors").get(null);
      java.util.concurrent.atomic.AtomicLong applierEvents =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("applierEvents").get(null);
      java.util.concurrent.atomic.AtomicLong applierDurationsMs =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("applierDurationsMs").get(null);
      java.util.concurrent.atomic.AtomicLong emissionRanges =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("emissionRanges").get(null);
      java.util.concurrent.atomic.AtomicLong httpRequests =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("httpRequests").get(null);
      java.util.concurrent.atomic.AtomicLong httpOk =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("httpOk").get(null);
      java.util.concurrent.atomic.AtomicLong httpErrors =
          (java.util.concurrent.atomic.AtomicLong) cls.getField("httpErrors").get(null);
      writer.write("<pre>enabled=" + enabled + "\n" +
          "emissionCount=" + emissionCount.get() + "\n" +
          "emissionErrors=" + emissionErrors.get() + "\n" +
          "emissionRanges=" + emissionRanges.get() + "\n" +
          "applierEvents=" + applierEvents.get() + "\n" +
          "applierDurationsMs=" + applierDurationsMs.get() + "\n" +
          "httpRequests=" + httpRequests.get() + "\n" +
          "httpOk=" + httpOk.get() + "\n" +
          "httpErrors=" + httpErrors.get() +
          "</pre>");
    } catch (Throwable t) {
      writer.write("<pre>Fragments metrics unavailable: " + t + "</pre>");
    }
  }
}

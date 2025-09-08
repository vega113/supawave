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
import java.util.concurrent.atomic.AtomicLong;

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
      AtomicLong emissionCount = (AtomicLong) cls.getField("emissionCount").get(null);
      AtomicLong emissionErrors = (AtomicLong) cls.getField("emissionErrors").get(null);
      AtomicLong applierEvents = (AtomicLong) cls.getField("applierEvents").get(null);
      AtomicLong applierDurationsMs = (AtomicLong) cls.getField("applierDurationsMs").get(null);
      AtomicLong emissionRanges = (AtomicLong) cls.getField("emissionRanges").get(null);
      AtomicLong httpRequests = (AtomicLong) cls.getField("httpRequests").get(null);
      AtomicLong httpOk = (AtomicLong) cls.getField("httpOk").get(null);
      AtomicLong httpErrors = (AtomicLong) cls.getField("httpErrors").get(null);
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

    writer.write(Timing.renderTitle("Fragments Caches", 2));
    try {
      // Manifest order cache counters
      Class<?> mcls = Class.forName("org.waveprotocol.box.server.frontend.ManifestOrderCache");
      AtomicLong mh = (AtomicLong) mcls.getField("hits").get(null);
      AtomicLong mm = (AtomicLong) mcls.getField("misses").get(null);
      AtomicLong me = (AtomicLong) mcls.getField("evictions").get(null);
      AtomicLong mx = (AtomicLong) mcls.getField("expirations").get(null);

      // Segment state registry counters
      Class<?> rcls = Class.forName("org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry");
      AtomicLong rh = (AtomicLong) rcls.getField("hits").get(null);
      AtomicLong rm = (AtomicLong) rcls.getField("misses").get(null);
      AtomicLong re = (AtomicLong) rcls.getField("evictions").get(null);
      AtomicLong rx = (AtomicLong) rcls.getField("expirations").get(null);

      writer.write("<pre>manifestOrderCache: hits=" + mh.get() + ", misses=" + mm.get() + ", evictions=" + me.get() + ", expirations=" + mx.get() + "\n" +
                   "segmentStateRegistry: hits=" + rh.get() + ", misses=" + rm.get() + ", evictions=" + re.get() + ", expirations=" + rx.get() +
                   "</pre>");
    } catch (Throwable t) {
      writer.write("<pre>Cache metrics unavailable: " + t + "</pre>");
    }
  }
}

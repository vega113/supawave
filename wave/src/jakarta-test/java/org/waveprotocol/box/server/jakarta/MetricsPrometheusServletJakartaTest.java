/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.jakarta;

import io.micrometer.core.instrument.Counter;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.stat.MetricsHolder;
import org.waveprotocol.box.server.stat.MetricsPrometheusServlet;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the Jakarta Prometheus servlet exports built-in JVM/process binders in addition to custom counters.
 */
public final class MetricsPrometheusServletJakartaTest {
  private Server server;
  private int port;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);
    server.addConnector(connector);

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath("/");
    handler.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new MetricsPrometheusServlet()), "/metrics");
    server.setHandler(handler);

    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void stop() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void metricsEndpointExposesJvmAndProcessBinders() throws Exception {
    Counter counter = MetricsHolder.registry().counter("wave_metrics_test_counter", "state", "ok");
    counter.increment(2.0);

    HttpURLConnection conn = TestSupport.openConnection(new URL("http://localhost:" + port + "/metrics"));
    assertEquals(200, conn.getResponseCode());
    assertEquals("text/plain; version=0.0.4; charset=utf-8", conn.getHeaderField("Content-Type"));

    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue("Counter sample should be exported", body.contains("wave_metrics_test_counter_total"));
    assertTrue("JVM memory metrics should be exported", body.contains("jvm_memory_used_bytes"));
    assertTrue("JVM GC metrics should be exported", body.contains("jvm_gc_memory_allocated_bytes_total"));
    assertTrue("JVM thread metrics should be exported", body.contains("jvm_threads_live_threads"));
    assertTrue("Process CPU metrics should be exported", body.contains("system_cpu_count"));
  }
}

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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class StatuszServletConfigTest {
  private Properties originalProperties;

  @Before
  public void captureSystemProperties() {
    originalProperties = (Properties) System.getProperties().clone();
  }

  @After
  public void restoreSystemProperties() {
    System.setProperties((Properties) originalProperties.clone());
  }

  @Test
  public void fragmentsViewUsesInjectedConfigInsteadOfJvmProperties() throws Exception {
    Config config = ConfigFactory.parseString(
        "server.fragments.transport=\"stream\"\n" +
        "server.preferSegmentState=true\n" +
        "server.enableStorageSegmentState=true");

    System.setProperty("server.fragments.transport", "off");
    System.setProperty("server.preferSegmentState", "false");
    System.setProperty("server.enableStorageSegmentState", "false");

    StatuszServlet servlet = new StatuszServlet(config);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter buffer = new StringWriter();
    when(request.getParameter("show")).thenReturn("fragments");
    when(response.getWriter()).thenReturn(new PrintWriter(buffer));

    servlet.service(request, response);

    String output = buffer.toString();
    assertTrue(output.contains(
        "<pre>transport=stream; preferSegmentState=true; enableStorageSegmentState=true</pre>"));
  }
}

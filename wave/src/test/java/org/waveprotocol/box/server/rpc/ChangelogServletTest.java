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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

public final class ChangelogServletTest {
  private ChangelogServlet servlet;

  @Before
  public void setUp() {
    servlet = new ChangelogServlet(
        new ChangelogProvider(
            new JSONArray(
                "[{\"releaseId\":\"2026-03-27-unread-only-search-filter\","
                    + "\"version\":\"2026-03-27.403\",\"date\":\"2026-03-27\","
                    + "\"title\":\"Changelog System\","
                    + "\"summary\":\"You can now see what's new after each deploy.\","
                    + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]}]")));
  }

  @Test
  public void apiPathReturnsJsonArray() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", "/api", null);
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("application/json; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().startsWith("["));
    assertTrue(body.toString().contains("\"releaseId\":\"2026-03-27-unread-only-search-filter\""));
  }

  @Test
  public void latestPathReturnsFirstEntryOnly() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", "/latest", null);
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("application/json; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().startsWith("{"));
    assertTrue(body.toString().contains("\"title\":\"Changelog System\""));
  }

  @Test
  public void currentPathReturnsCurrentReleaseEntry() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", "/current", null);
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("application/json; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().contains("\"releaseId\":\"2026-03-27-unread-only-search-filter\""));
  }

  @Test
  public void basePathReturnsRenderedHtmlPage() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", null, "text/html");
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("text/html; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().contains("<title>What's New - SupaWave</title>"));
    assertTrue(body.toString().contains("Changelog System"));
  }

  @Test
  public void basePathIgnoresJsonAcceptHeader() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", null, "application/json");
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("text/html; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().contains("<title>What's New - SupaWave</title>"));
    assertTrue(body.toString().contains("Changelog System"));
    assertTrue(!body.toString().startsWith("["));
  }

  @Test
  public void apiPathRemainsJsonEvenWhenHtmlIsPreferred() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    HttpServletRequest request = request("/changelog", "/api", "text/html");
    HttpServletResponse response = response(body, contentType);

    servlet.doGet(request, response);

    assertEquals("application/json; charset=UTF-8", contentType[0]);
    assertTrue(body.toString().startsWith("["));
    assertTrue(body.toString().contains("\"releaseId\":\"2026-03-27-unread-only-search-filter\""));
  }

  private static HttpServletRequest request(String servletPath, String pathInfo, String accept) {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            ChangelogServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "getServletPath" -> servletPath;
              case "getPathInfo" -> pathInfo;
              case "getHeader" -> "Accept".equals(args[0]) ? accept : null;
              default -> defaultValue(method.getReturnType());
            });
  }

  private static HttpServletResponse response(StringWriter body, String[] contentType) {
    PrintWriter writer = new PrintWriter(body);
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            ChangelogServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "getWriter" -> writer;
              case "setContentType" -> {
                contentType[0] = (String) args[0];
                yield null;
              }
              case "setStatus", "setHeader" -> null;
              case "sendError" -> {
                throw new AssertionError("Unexpected sendError call");
              }
              default -> defaultValue(method.getReturnType());
            });
  }

  private static Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0d;
    }
    if (returnType == float.class) {
      return 0f;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == char.class) {
      return (char) 0;
    }
    return null;
  }
}

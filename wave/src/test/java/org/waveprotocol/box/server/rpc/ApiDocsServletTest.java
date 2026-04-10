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

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.junit.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class ApiDocsServletTest {
  @Test
  public void apiDocsHtmlIncludesBuildWithAiSection() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/api-docs", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue(body.toString().contains("Build with AI"));
    assertTrue(body.toString().contains("Google AI Studio / Gemini"));
    assertTrue(body.toString().contains("SUPAWAVE_DATA_API_TOKEN"));
  }

  @Test
  public void apiDocsHtmlDocumentsRobotTokenLifecycleAndBundleCompatibility() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/api-docs", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue(body.toString().contains("Refresh the JWT after any HTTP 401"));
    assertTrue(body.toString().contains("tokenVersion"));
    assertTrue(body.toString().contains("robotAddress"));
    assertTrue(body.toString().contains("Treat missing threads as {}"));
  }

  @Test
  public void apiDocsHtmlDocumentsAttachmentBackedInlineImageFlow() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/api-docs", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue(body.toString().contains("attachment-backed inline image"));
    assertTrue(body.toString().contains("display-size"));
    assertTrue(body.toString().contains("attachmentId"));
  }

  @Test
  public void llmsIndexEndpointReturnsRootLevelPointers() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/llms.txt", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue("text/plain;charset=utf-8".equals(recorder.contentType));
    assertTrue("UTF-8".equals(recorder.characterEncoding));
    assertTrue(body.toString().contains("https://docs.example.com/api-docs"));
    assertTrue(body.toString().contains("https://docs.example.com/api/openapi.json"));
    assertTrue(body.toString().contains("https://docs.example.com/llms-full.txt"));
  }

  @Test
  public void llmsFullEndpointReturnsDetailedApiContract() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/llms-full.txt", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue("text/plain;charset=utf-8".equals(recorder.contentType));
    assertTrue("UTF-8".equals(recorder.characterEncoding));
    assertTrue(body.toString().contains("SupaWave Data API LLM Reference"));
    assertTrue(body.toString().contains("Canonical RPC path: /robot/dataapi/rpc"));
    assertTrue(body.toString().contains("https://docs.example.com/api/openapi.json"));
    assertTrue(body.toString().contains("Refresh the JWT after any HTTP 401"));
    assertTrue(body.toString().contains("tokenVersion"));
    assertTrue(body.toString().contains("robotAddress"));
    assertTrue(body.toString().contains("Treat missing threads as {}"));
  }

  @Test
  public void llmAliasEndpointStillReturnsDetailedApiContract() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/api/llm.txt", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_OK);
    assertTrue("text/plain;charset=utf-8".equals(recorder.contentType));
    assertTrue("UTF-8".equals(recorder.characterEncoding));
    assertTrue(body.toString().contains("SupaWave Data API LLM Reference"));
    assertTrue(body.toString().contains("Canonical RPC path: /robot/dataapi/rpc"));
    assertTrue(body.toString().contains("https://docs.example.com/api/openapi.json"));
    assertTrue(body.toString().contains("Google AI Studio / Gemini starter prompt"));
    assertTrue(body.toString().contains("SUPAWAVE_ROBOT_SECRET"));
    assertTrue(body.toString().contains("Minimal common operations"));
  }

  @Test
  public void unknownEndpointReturnsNotFound() throws Exception {
    ApiDocsServlet servlet = new ApiDocsServlet("docs.example.com");
    StringWriter body = new StringWriter();
    ResponseRecorder recorder = new ResponseRecorder();
    HttpServletRequest request = request("/nope", "https", "docs.example.com");
    HttpServletResponse response = response(recorder, body);

    servlet.doGet(request, response);

    assertTrue(recorder.status == HttpServletResponse.SC_NOT_FOUND);
  }

  private static HttpServletRequest request(String servletPath, String scheme, String host) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          String name = method.getName();
          if ("getServletPath".equals(name)) {
            return servletPath;
          }
          if ("getRequestURI".equals(name)) {
            return servletPath;
          }
          if ("getScheme".equals(name)) {
            return scheme;
          }
          if ("getHeader".equals(name)) {
            String headerName = (String) args[0];
            if ("Host".equalsIgnoreCase(headerName)) {
              return host;
            }
            return null;
          }
          if ("getCharacterEncoding".equals(name)) {
            return "UTF-8";
          }
          if ("isSecure".equals(name)) {
            return "https".equalsIgnoreCase(scheme);
          }
          return defaultValue(method.getReturnType());
        };
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            ApiDocsServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            handler);
  }

  private static HttpServletResponse response(ResponseRecorder recorder, StringWriter body) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          String name = method.getName();
          if ("getWriter".equals(name)) {
            return new PrintWriter(body);
          }
          if ("setStatus".equals(name)) {
            recorder.status = (Integer) args[0];
            return null;
          }
          if ("setContentType".equals(name)) {
            recorder.contentType = (String) args[0];
            return null;
          }
          if ("setCharacterEncoding".equals(name)) {
            recorder.characterEncoding = (String) args[0];
            return null;
          }
          if ("setHeader".equals(name)) {
            return null;
          }
          if ("sendError".equals(name)) {
            recorder.status = (Integer) args[0];
            return null;
          }
          return defaultValue(method.getReturnType());
        };
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            ApiDocsServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            handler);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (Boolean.TYPE.equals(type)) {
      return false;
    }
    if (Character.TYPE.equals(type)) {
      return Character.valueOf('\0');
    }
    if (Byte.TYPE.equals(type)) {
      return Byte.valueOf((byte) 0);
    }
    if (Short.TYPE.equals(type)) {
      return Short.valueOf((short) 0);
    }
    if (Integer.TYPE.equals(type)) {
      return Integer.valueOf(0);
    }
    if (Long.TYPE.equals(type)) {
      return Long.valueOf(0L);
    }
    if (Float.TYPE.equals(type)) {
      return Float.valueOf(0.0f);
    }
    if (Double.TYPE.equals(type)) {
      return Double.valueOf(0.0d);
    }
    return null;
  }

  private static final class ResponseRecorder {
    private int status;
    private String contentType;
    private String characterEncoding;

    private ResponseRecorder() {
    }
  }
}

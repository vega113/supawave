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

package org.waveprotocol.examples.robots.gptbot;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

public class OpenAiCodexClientTest extends TestCase {

  public void testCompleteMessagesStreamingParsesSseDeltas() {
    StreamingHttpClient httpClient = new StreamingHttpClient();
    OpenAiCodexClient client = new OpenAiCodexClient("test-key", "https://api.example.com",
        "gpt-test", httpClient, false);
    List<String> snapshots = new ArrayList<String>();

    String response = client.completeMessagesStreaming(exampleMessages(), snapshots::add);

    assertEquals("Hello world", response);
    assertEquals(Arrays.asList("Hel", "Hello", "Hello world"), snapshots);
    assertTrue(httpClient.lastRequestBody.contains("\"stream\":true"));
  }

  public void testCompleteMessagesStreamingFallsBackToOneShotWhenWebSearchEnabled() {
    StringHttpClient httpClient = new StringHttpClient(
        "{\"choices\":[{\"message\":{\"content\":\"One shot\"}}]}");
    OpenAiCodexClient client = new OpenAiCodexClient("test-key", "https://api.example.com",
        "gpt-test", httpClient, true);
    List<String> snapshots = new ArrayList<String>();

    String response = client.completeMessagesStreaming(exampleMessages(), snapshots::add);

    assertEquals("One shot", response);
    assertEquals(Arrays.asList("One shot"), snapshots);
    assertFalse(httpClient.lastRequestBody.contains("\"stream\":true"));
  }

  private static List<Map<String, String>> exampleMessages() {
    List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
    Map<String, String> system = new LinkedHashMap<String, String>();
    system.put("role", "system");
    system.put("content", "Be concise.");
    messages.add(system);
    Map<String, String> user = new LinkedHashMap<String, String>();
    user.put("role", "user");
    user.put("content", "Say hello");
    messages.add(user);
    return messages;
  }

  private abstract static class BaseHttpClient extends HttpClient {
    protected String lastRequestBody = "";

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    protected void capture(HttpRequest request) {
      lastRequestBody = bodyString(request);
    }

    private static String bodyString(HttpRequest request) {
      Optional<HttpRequest.BodyPublisher> bodyPublisher = request.bodyPublisher();
      if (!bodyPublisher.isPresent()) {
        return "";
      }
      ByteCollector collector = new ByteCollector();
      bodyPublisher.get().subscribe(collector);
      return collector.body();
    }
  }

  private static final class StreamingHttpClient extends BaseHttpClient {
    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      capture(request);
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new StreamingHttpResponse(Stream.of(
          "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
          "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
          "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}",
          "data: [DONE]"));
      return response;
    }
  }

  private static final class StringHttpClient extends BaseHttpClient {
    private final String body;

    private StringHttpClient(String body) {
      this.body = body;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      capture(request);
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new StringHttpResponse(body);
      return response;
    }
  }

  private static final class ByteCollector implements Flow.Subscriber<ByteBuffer> {
    private final StringBuilder body = new StringBuilder();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(ByteBuffer item) {
      ByteBuffer copy = item.asReadOnlyBuffer();
      byte[] bytes = new byte[copy.remaining()];
      copy.get(bytes);
      body.append(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void onError(Throwable throwable) {
      throw new RuntimeException(throwable);
    }

    @Override
    public void onComplete() {
    }

    private String body() {
      return body.toString();
    }
  }

  private static final class StringHttpResponse implements HttpResponse<String> {
    private final String body;

    private StringHttpResponse(String body) {
      this.body = body;
    }

    @Override
    public int statusCode() {
      return 200;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(java.util.Collections.emptyMap(), (a, b) -> true);
    }

    @Override
    public String body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return URI.create("https://api.example.com/chat/completions");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class StreamingHttpResponse implements HttpResponse<Stream<String>> {
    private final Stream<String> body;

    private StreamingHttpResponse(Stream<String> body) {
      this.body = body;
    }

    @Override
    public int statusCode() {
      return 200;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<Stream<String>>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(java.util.Collections.emptyMap(), (a, b) -> true);
    }

    @Override
    public Stream<String> body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return URI.create("https://api.example.com/chat/completions");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}

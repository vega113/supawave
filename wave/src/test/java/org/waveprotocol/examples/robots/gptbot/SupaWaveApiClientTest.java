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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

public class SupaWaveApiClientTest extends TestCase {

  public void testSearchRestoresInterruptStatusWhenRequestInterrupted() {
    GptBotConfig config = testConfig();
    SupaWaveApiClient client = new SupaWaveApiClient(config, new InterruptingHttpClient());

    try {
      Optional<String> summary = client.search("wave");
      assertFalse(summary.isPresent());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  public void testCreateReplyUsesRobotTokenForRobotRpcAndParsesNewBlipId() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<String> replyId = client.createReply("example.com!w+abc123",
        "example.com!conv+root", "b+parent", "\nHello",
        "https://wave.example.com/robot/rpc");

    assertEquals(Optional.of("b+child"), replyId);
    assertTrue(httpClient.lastTokenRequestBody.contains("token_type=robot"));
    assertEquals("https://wave.example.com/robot/rpc", httpClient.lastRpcRequestUri.toString());
    assertTrue(httpClient.lastRpcRequestBody.contains("\"method\":\"blip.createChild\""));
  }

  public void testCreateReplyUsesDataTokenForDataRpcEndpoint() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<String> replyId = client.createReply("example.com!w+abc123",
        "example.com!conv+root", "b+parent", "\nHello",
        "https://wave.example.com/robot/dataapi/rpc");

    assertEquals(Optional.of("b+child"), replyId);
    assertFalse(httpClient.lastTokenRequestBody.contains("token_type=robot"));
    assertEquals("https://wave.example.com/robot/dataapi/rpc", httpClient.lastRpcRequestUri.toString());
  }

  public void testReplaceReplyUsesDocumentModifyAtSuppliedRpcEndpoint() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    boolean replaced = client.replaceReply("example.com!w+abc123", "example.com!conv+root",
        "b+reply", "Hello world", "https://wave.example.com/robot/dataapi/rpc");

    assertTrue(replaced);
    assertEquals("https://wave.example.com/robot/dataapi/rpc", httpClient.lastRpcRequestUri.toString());
    assertTrue(httpClient.lastRpcRequestBody.contains("\"method\":\"document.modify\""));
    assertTrue(httpClient.lastRpcRequestBody.contains("\"blipId\":\"b+reply\""));
  }

  public void testExportAttachmentPostsRobotExportAttachmentAndParsesData() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    httpClient.rpcResponseBody =
        "[{\"id\":\"gpt-bot-attachment-1\",\"data\":{\"attachmentData\":{"
            + "\"fileName\":\"voice.mp3\","
            + "\"creator\":\"alice@example.com\","
            + "\"data\":\"dm9pY2UtYnl0ZXM=\"}}}]";
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<BotAttachmentContext.RawAttachment> attachment =
        client.exportAttachment("att-voice", "https://wave.example.com/robot/dataapi/rpc");

    assertTrue(attachment.isPresent());
    assertEquals("voice.mp3", attachment.get().getFileName());
    assertEquals("voice-bytes", new String(attachment.get().getData(), StandardCharsets.UTF_8));
    assertEquals("https://wave.example.com/robot/dataapi/rpc", httpClient.lastRpcRequestUri.toString());
    assertTrue(httpClient.lastRpcRequestBody.contains("\"method\":\"robot.exportAttachment\""));
    assertTrue(httpClient.lastRpcRequestBody.contains("\"attachmentId\":\"att-voice\""));
  }

  public void testCreateReplyFallsBackToTrustedRobotEndpointWhenRpcServerUrlIsUntrusted() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<String> replyId = client.createReply("example.com!w+abc123",
        "example.com!conv+root", "b+parent", "\nHello",
        "https://evil.example.net/robot/dataapi/rpc");

    assertEquals(Optional.of("b+child"), replyId);
    assertEquals("https://wave.example.com/robot/rpc", httpClient.lastRpcRequestUri.toString());
    assertTrue(httpClient.lastTokenRequestBody.contains("token_type=robot"));
  }

  public void testCreateReplyAcceptsRobotRpcUrlsWithTrailingSlashAndQueryParameters() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<String> replyId = client.createReply("example.com!w+abc123",
        "example.com!conv+root", "b+parent", "\nHello",
        "https://wave.example.com/robot/rpc/?tenant=x");

    assertEquals(Optional.of("b+child"), replyId);
    assertTrue(httpClient.lastTokenRequestBody.contains("token_type=robot"));
    assertEquals("https://wave.example.com/robot/rpc/?tenant=x", httpClient.lastRpcRequestUri.toString());
  }

  public void testCreateReplyFallsBackToTrustedRobotEndpointWhenRpcServerUrlHasUnexpectedPath() {
    GptBotConfig config = testConfig();
    RecordingHttpClient httpClient = new RecordingHttpClient();
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient);

    Optional<String> replyId = client.createReply("example.com!w+abc123",
        "example.com!conv+root", "b+parent", "\nHello",
        "https://wave.example.com/not-a-rpc-endpoint");

    assertEquals(Optional.of("b+child"), replyId);
    assertEquals("https://wave.example.com/robot/rpc", httpClient.lastRpcRequestUri.toString());
    assertTrue(httpClient.lastTokenRequestBody.contains("token_type=robot"));
  }

  private static GptBotConfig testConfig() {
    return GptBotConfig.forTest().withBaseUrl("https://wave.example.com");
  }

  private static final class InterruptingHttpClient extends HttpClient {

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
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      throw new InterruptedException("interrupted");
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
  }

  private static final class RecordingHttpClient extends HttpClient {

    private String lastTokenRequestBody = "";
    private URI lastRpcRequestUri;
    private String lastRpcRequestBody = "";
    private String rpcResponseBody =
        "[{\"id\":\"gpt-bot-reply-1\",\"data\":{\"newBlipId\":\"b+child\"}}]";

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
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      String path = request.uri().getPath();
      String body = bodyString(request);
      if (path.endsWith("/robot/dataapi/token")) {
        lastTokenRequestBody = body;
        String token = body.contains("token_type=robot") ? "robot-token" : "data-token";
        @SuppressWarnings("unchecked")
        HttpResponse<T> response = (HttpResponse<T>) new StringHttpResponse(
            200, "{\"access_token\":\"" + token + "\",\"expires_in\":3600}");
        return response;
      }
      lastRpcRequestUri = request.uri();
      lastRpcRequestBody = body;
      if (body.contains("\"robot.exportAttachment\"")) {
        @SuppressWarnings("unchecked")
        HttpResponse<T> response = (HttpResponse<T>) new InputStreamHttpResponse(200,
            new ByteArrayInputStream(rpcResponseBody.getBytes(StandardCharsets.UTF_8)));
        return response;
      }
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new StringHttpResponse(200, rpcResponseBody);
      return response;
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

  private static final class ByteCollector implements Flow.Subscriber<ByteBuffer> {
    private final StringBuilder body = new StringBuilder();
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
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
      if (subscription != null) {
        subscription.cancel();
      }
    }

    private String body() {
      return body.toString();
    }
  }

  private static final class StringHttpResponse implements HttpResponse<String> {
    private final int statusCode;
    private final String body;

    private StringHttpResponse(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return statusCode;
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
      return URI.create("https://wave.example.com");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class InputStreamHttpResponse implements HttpResponse<InputStream> {
    private final int statusCode;
    private final InputStream body;

    private InputStreamHttpResponse(int statusCode, InputStream body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<InputStream>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(java.util.Collections.emptyMap(), (a, b) -> true);
    }

    @Override
    public InputStream body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return URI.create("https://wave.example.com");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}

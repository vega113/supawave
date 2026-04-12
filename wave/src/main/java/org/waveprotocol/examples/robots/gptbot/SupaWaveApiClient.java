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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches extra context from SupaWave and can optionally post replies through the active API.
 */
public final class SupaWaveApiClient implements SupaWaveClient {

  private static final Log LOG = Log.get(SupaWaveApiClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

  private final GptBotConfig config;
  private final HttpClient httpClient;
  private volatile AccessToken dataApiToken;
  private volatile AccessToken robotApiToken;

  public SupaWaveApiClient(GptBotConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  SupaWaveApiClient(GptBotConfig config, HttpClient httpClient) {
    this.config = config;
    this.httpClient = httpClient;
  }

  @Override
  public Optional<String> fetchWaveContext(String waveId, String waveletId) {
    Optional<String> context = Optional.empty();
    if (config.getContextMode() != GptBotConfig.ContextMode.NONE && config.hasApiCredentials()) {
      try {
        JsonArray response = postRpc(fetchWaveRequest(waveId, waveletId), contextRpcEndpoint(),
            contextAccessToken());
        context = Optional.ofNullable(summarizeFetchResponse(response)).filter(value -> !value.isBlank());
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to fetch SupaWave context", e);
      }
    }
    return context;
  }

  @Override
  public Optional<String> search(String query) {
    Optional<String> summary = Optional.empty();
    if (!query.isBlank() && config.hasApiCredentials()) {
      try {
        JsonArray response = postRpc(searchRequest(query), searchRpcEndpoint(), searchAccessToken());
        summary = Optional.ofNullable(summarizeSearchResponse(response)).filter(value -> !value.isBlank());
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to search SupaWave", e);
      }
    }
    return summary;
  }

  @Override
  public boolean appendReply(String waveId, String waveletId, String blipId, String content,
      String rpcServerUrl) {
    return createReply(waveId, waveletId, blipId, content, rpcServerUrl).isPresent();
  }

  @Override
  public Optional<String> createReply(String waveId, String waveletId, String parentBlipId,
      String initialContent, String rpcServerUrl) {
    Optional<String> replyId = Optional.empty();
    if (config.hasApiCredentials()) {
      try {
        String endpoint = resolveRpcEndpoint(rpcServerUrl);
        JsonArray response = postRpc(createChildRequest(waveId, waveletId, parentBlipId,
            initialContent), endpoint, accessTokenForEndpoint(endpoint));
        if (!responseContainsError(response)) {
          replyId = extractStringField(response, "newBlipId");
        }
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to create a reply through SupaWave RPC", e);
      }
    }
    return replyId;
  }

  @Override
  public boolean replaceReply(String waveId, String waveletId, String replyBlipId, String content,
      String rpcServerUrl) {
    boolean replaced = false;
    if (config.hasApiCredentials()) {
      try {
        String endpoint = resolveRpcEndpoint(rpcServerUrl);
        JsonArray response = postRpc(replaceReplyRequest(waveId, waveletId, replyBlipId, content),
            endpoint, accessTokenForEndpoint(endpoint));
        replaced = !responseContainsError(response);
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to replace a streamed reply through SupaWave RPC", e);
      }
    }
    return replaced;
  }

  private JsonArray fetchWaveRequest(String waveId, String waveletId) {
    JsonObject params = new JsonObject();
    params.addProperty("waveId", waveId);
    params.addProperty("waveletId", waveletId);

    JsonObject request = new JsonObject();
    request.addProperty("id", "gpt-bot-context-1");
    request.addProperty("method", "robot.fetchWave");
    request.add("params", params);

    JsonArray body = new JsonArray();
    body.add(request);
    return body;
  }

  private JsonArray searchRequest(String query) {
    JsonObject params = new JsonObject();
    params.addProperty("query", query);
    params.addProperty("index", 0);
    params.addProperty("numResults", 5);

    JsonObject request = new JsonObject();
    request.addProperty("id", "gpt-bot-search-1");
    request.addProperty("method", "robot.search");
    request.add("params", params);

    JsonArray body = new JsonArray();
    body.add(request);
    return body;
  }

  private JsonArray createChildRequest(String waveId, String waveletId, String blipId,
      String content) {
    JsonObject blipData = new JsonObject();
    blipData.addProperty("blipId", "TBD_gptbot_reply_" + UUID.randomUUID().toString()
        .replace("-", ""));
    blipData.addProperty("content", "\n" + content.strip());

    JsonObject params = new JsonObject();
    params.addProperty("waveId", waveId);
    params.addProperty("waveletId", waveletId);
    params.addProperty("blipId", blipId);
    params.add("blipData", blipData);

    JsonObject request = new JsonObject();
    request.addProperty("id", "gpt-bot-reply-1");
    request.addProperty("method", "blip.createChild");
    request.add("params", params);

    JsonArray body = new JsonArray();
    body.add(request);
    return body;
  }

  private JsonArray replaceReplyRequest(String waveId, String waveletId, String replyBlipId,
      String content) {
    JsonObject modifyAction = new JsonObject();
    modifyAction.addProperty("modifyHow", "REPLACE");
    JsonArray values = new JsonArray();
    values.add(content == null ? "" : content);
    modifyAction.add("values", values);
    modifyAction.addProperty("annotationKey", "");
    modifyAction.add("elements", new JsonArray());
    modifyAction.add("bundledAnnotations", new JsonArray());
    modifyAction.addProperty("useMarkup", false);

    JsonObject params = new JsonObject();
    params.addProperty("waveId", waveId);
    params.addProperty("waveletId", waveletId);
    params.addProperty("blipId", replyBlipId);
    params.add("modifyAction", modifyAction);

    JsonObject request = new JsonObject();
    request.addProperty("id", "gpt-bot-stream-1");
    request.addProperty("method", "document.modify");
    request.add("params", params);

    JsonArray body = new JsonArray();
    body.add(request);
    return body;
  }

  private JsonArray postRpc(JsonArray body, String endpoint, String accessToken)
      throws IOException, InterruptedException {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .timeout(REQUEST_TIMEOUT)
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();

    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("Unexpected SupaWave RPC status: " + response.statusCode());
    }
    return JsonParser.parseString(response.body()).getAsJsonArray();
  }

  private String contextAccessToken() throws IOException, InterruptedException {
    String token = getDataApiAccessToken();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      token = getRobotAccessToken();
    }
    return token;
  }

  private String searchAccessToken() throws IOException, InterruptedException {
    String token = getDataApiAccessToken();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      token = getRobotAccessToken();
    }
    return token;
  }

  private String accessTokenForEndpoint(String endpoint) throws IOException, InterruptedException {
    if (isRobotRpcEndpoint(endpoint)) {
      return getRobotAccessToken();
    }
    return getDataApiAccessToken();
  }

  private boolean isRobotRpcEndpoint(String endpoint) {
    try {
      URI uri = URI.create(endpoint);
      String path = uri.getPath();
      if (path == null) {
        return false;
      }
      while (path.endsWith("/") && path.length() > 1) {
        path = path.substring(0, path.length() - 1);
      }
      return "/robot/rpc".equals(path);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private synchronized String getDataApiAccessToken() throws IOException, InterruptedException {
    AccessToken current = dataApiToken;
    String token = null;
    if (current != null && current.isFresh()) {
      token = current.token;
    }
    if (token == null) {
      token = requestAccessToken(false);
    }
    return token;
  }

  private synchronized String getRobotAccessToken() throws IOException, InterruptedException {
    AccessToken current = robotApiToken;
    String token = null;
    if (current != null && current.isFresh()) {
      token = current.token;
    }
    if (token == null) {
      token = requestAccessToken(true);
    }
    return token;
  }

  private String requestAccessToken(boolean robotToken) throws IOException, InterruptedException {
    StringBuilder form = new StringBuilder();
    form.append("grant_type=client_credentials");
    form.append("&client_id=").append(encode(config.getApiRobotId()));
    form.append("&client_secret=").append(encode(config.getApiRobotSecret()));
    form.append("&expiry=3600");
    if (robotToken) {
      form.append("&token_type=robot");
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(tokenEndpoint()))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("Token endpoint returned HTTP " + response.statusCode());
    }

    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
    String token = json.get("access_token").getAsString();
    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
    AccessToken freshToken = new AccessToken(token,
        Instant.now().plusSeconds(Math.max(60L, expiresIn)));
    if (robotToken) {
      robotApiToken = freshToken;
    } else {
      dataApiToken = freshToken;
    }
    return token;
  }

  private String summarizeFetchResponse(JsonArray response) {
    StringBuilder summary = new StringBuilder();
    if (response.size() > 0) {
      JsonElement first = response.get(0);
      if (first.isJsonObject()) {
        JsonObject result = first.getAsJsonObject();
        if (result.has("data") && result.get("data").isJsonObject()) {
          JsonObject data = result.getAsJsonObject("data");
          appendField(summary, data, "message", "Fetch tag");
          if (data.has("waveletData") && data.get("waveletData").isJsonObject()) {
            JsonObject waveletData = data.getAsJsonObject("waveletData");
            appendField(summary, waveletData, "title", "Title");
            appendField(summary, waveletData, "waveId", "Wave ID");
            appendField(summary, waveletData, "waveletId", "Wavelet ID");
            appendField(summary, waveletData, "rootBlipId", "Root blip");
          }
          if (data.has("blips") && data.get("blips").isJsonObject()) {
            JsonObject blips = data.getAsJsonObject("blips");
            int count = 0;
            for (String currentBlipId : blips.keySet()) {
              if (count >= 4) {
                break;
              }
              JsonElement blipElement = blips.get(currentBlipId);
              if (blipElement != null && blipElement.isJsonObject()) {
                JsonObject blip = blipElement.getAsJsonObject();
                String content = blip.has("content") ? blip.get("content").getAsString() : "";
                summary.append("Blip ").append(currentBlipId).append(": ")
                    .append(content.strip()).append('\n');
                count++;
              }
            }
          }
        }
      }
    }
    return clamp(summary.toString());
  }

  private String summarizeSearchResponse(JsonArray response) {
    StringBuilder summary = new StringBuilder();
    if (response.size() > 0) {
      JsonElement first = response.get(0);
      if (first.isJsonObject()) {
        JsonObject result = first.getAsJsonObject();
        if (result.has("data") && result.get("data").isJsonObject()) {
          JsonObject data = result.getAsJsonObject("data");
          if (data.has("searchResults") && data.get("searchResults").isJsonObject()) {
            JsonObject searchResults = data.getAsJsonObject("searchResults");
            appendField(summary, searchResults, "query", "Search query");
            if (searchResults.has("digests") && searchResults.get("digests").isJsonArray()) {
              JsonArray digests = searchResults.getAsJsonArray("digests");
              int count = Math.min(3, digests.size());
              for (int index = 0; index < count; index++) {
                JsonObject digest = digests.get(index).getAsJsonObject();
                appendField(summary, digest, "title", "Result title");
                appendField(summary, digest, "waveId", "Result wave");
                appendField(summary, digest, "snippet", "Snippet");
              }
            }
          }
        }
      }
    }
    return clamp(summary.toString());
  }

  private static boolean responseContainsError(JsonArray response) {
    boolean containsError = false;
    for (JsonElement element : response) {
      if (element.isJsonObject() && element.getAsJsonObject().has("error")) {
        containsError = true;
      }
    }
    return containsError;
  }

  private Optional<String> extractStringField(JsonArray response, String fieldName) {
    for (JsonElement element : response) {
      if (element.isJsonObject()) {
        JsonObject object = element.getAsJsonObject();
        if (object.has(fieldName)) {
          return Optional.of(object.get(fieldName).getAsString());
        }
        if (object.has("data") && object.get("data").isJsonObject()) {
          JsonObject data = object.getAsJsonObject("data");
          if (data.has(fieldName)) {
            return Optional.of(data.get(fieldName).getAsString());
          }
        }
      }
    }
    return Optional.empty();
  }

  private static void appendField(StringBuilder summary, JsonObject object, String key,
      String label) {
    if (object.has(key)) {
      summary.append(label).append(": ").append(object.get(key).getAsString()).append('\n');
    }
  }

  private String contextRpcEndpoint() {
    String endpoint = dataRpcEndpoint();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      endpoint = activeRpcEndpoint();
    }
    return endpoint;
  }

  private String searchRpcEndpoint() {
    String endpoint = dataRpcEndpoint();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      endpoint = activeRpcEndpoint();
    }
    return endpoint;
  }

  private String dataRpcEndpoint() {
    return config.getBaseUrl() + "/robot/dataapi/rpc";
  }

  private String activeRpcEndpoint() {
    return config.getBaseUrl() + "/robot/rpc";
  }

  private String resolveRpcEndpoint(String rpcServerUrl) {
    String endpoint = rpcServerUrl == null ? "" : rpcServerUrl.trim();
    if (endpoint.isEmpty()) {
      return activeRpcEndpoint();
    }
    if (isTrustedRpcEndpoint(endpoint)) {
      return endpoint;
    }
    LOG.warning("Ignoring untrusted rpcServerUrl from bundle: " + clamp(endpoint));
    return activeRpcEndpoint();
  }

  private boolean isTrustedRpcEndpoint(String endpoint) {
    try {
      URI configured = URI.create(config.getBaseUrl());
      URI candidate = URI.create(endpoint);
      if (!configured.getScheme().equalsIgnoreCase(candidate.getScheme())) {
        return false;
      }
      if (!configured.getHost().equalsIgnoreCase(candidate.getHost())) {
        return false;
      }
      int configuredPort = configured.getPort() == -1
          ? ("https".equalsIgnoreCase(configured.getScheme()) ? 443 : 80)
          : configured.getPort();
      int candidatePort = candidate.getPort() == -1
          ? ("https".equalsIgnoreCase(candidate.getScheme()) ? 443 : 80)
          : candidate.getPort();
      if (configuredPort != candidatePort) {
        return false;
      }
      String candidatePath = normalizeRpcPath(candidate.getPath());
      return candidatePath.equals(expectedRpcPath(configured.getPath(), "/robot/rpc"))
          || candidatePath.equals(expectedRpcPath(configured.getPath(), "/robot/dataapi/rpc"));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static String expectedRpcPath(String basePath, String rpcPath) {
    String normalizedBasePath = normalizeRpcPath(basePath);
    if (normalizedBasePath.isEmpty()) {
      return rpcPath;
    }
    if (rpcPath.startsWith("/")) {
      return normalizedBasePath + rpcPath;
    }
    return normalizedBasePath + "/" + rpcPath;
  }

  private static String normalizeRpcPath(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "";
    }
    String normalized = path.trim();
    while (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private String tokenEndpoint() {
    return config.getBaseUrl() + "/robot/dataapi/token";
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void logWarningAndRestoreInterrupt(String message, Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOG.warning(message, e);
  }

  private static String clamp(String value) {
    String clamped = value.trim();
    if (clamped.length() > 2000) {
      clamped = clamped.substring(0, 2000).trim() + "…";
    }
    return clamped;
  }

  private static final class AccessToken {

    private final String token;
    private final Instant expiresAt;

    private AccessToken(String token, Instant expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }

    private boolean isFresh() {
      return Instant.now().isBefore(expiresAt.minusSeconds(30L));
    }
  }
}

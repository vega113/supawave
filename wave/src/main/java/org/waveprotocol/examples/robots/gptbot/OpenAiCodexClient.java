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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A {@link CodexClient} that calls the OpenAI Chat Completions API directly.
 * Requires {@code OPENAI_API_KEY} environment variable.  Optionally reads
 * {@code OPENAI_BASE_URL} (defaults to {@code https://api.openai.com/v1}) and
 * {@code GPTBOT_OPENAI_MODEL} (defaults to {@code gpt-4o-mini}).
 */
public final class OpenAiCodexClient implements CodexClient {

  private static final Log LOG = Log.get(OpenAiCodexClient.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;

  public OpenAiCodexClient() {
    this.apiKey = requireEnv("OPENAI_API_KEY");
    String base = System.getenv("OPENAI_BASE_URL");
    this.baseUrl = (base != null && !base.isBlank()) ? stripTrailingSlash(base) : "https://api.openai.com/v1";
    String envModel = System.getenv("GPTBOT_OPENAI_MODEL");
    this.model = (envModel != null && !envModel.isBlank()) ? envModel.trim() : "gpt-4o-mini";
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    LOG.info("OpenAI client initialized (model=" + model + ", base=" + baseUrl + ")");
  }

  OpenAiCodexClient(String apiKey, String baseUrl, String model, HttpClient httpClient) {
    this.apiKey = apiKey;
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.model = model;
    this.httpClient = httpClient;
  }

  @Override
  public String complete(String prompt) {
    try {
      JsonObject body = buildRequestBody(prompt);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .timeout(REQUEST_TIMEOUT)
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warning("OpenAI API returned HTTP " + response.statusCode() + ": "
            + truncate(response.body(), 200));
        return "I'm having trouble generating a response right now.";
      }

      return extractContent(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.warning("OpenAI API call failed", e);
      return "I'm having trouble generating a response right now.";
    }
  }

  private JsonObject buildRequestBody(String prompt) {
    JsonObject systemMessage = new JsonObject();
    systemMessage.addProperty("role", "system");
    systemMessage.addProperty("content",
        "You are a helpful assistant in a collaborative document (SupaWave). "
        + "Keep replies concise, clear, and directly useful. "
        + "Do not mention that you are an AI unless asked.");

    JsonObject userMessage = new JsonObject();
    userMessage.addProperty("role", "user");
    userMessage.addProperty("content", prompt);

    JsonArray messages = new JsonArray();
    messages.add(systemMessage);
    messages.add(userMessage);

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messages);
    body.addProperty("max_tokens", 512);
    body.addProperty("temperature", 0.7);
    return body;
  }

  private static String extractContent(String responseBody) {
    try {
      JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
      JsonArray choices = json.getAsJsonArray("choices");
      if (choices != null && choices.size() > 0) {
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message != null && message.has("content")) {
          String content = message.get("content").getAsString().trim();
          if (!content.isEmpty()) {
            return content;
          }
        }
      }
    } catch (Exception e) {
      LOG.warning("Failed to parse OpenAI response", e);
    }
    return "I received your message but couldn't generate a response.";
  }

  private static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " environment variable is required for OpenAI engine");
    }
    return value.trim();
  }

  private static String stripTrailingSlash(String value) {
    String result = value == null ? "" : value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) return "";
    return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
  }
}

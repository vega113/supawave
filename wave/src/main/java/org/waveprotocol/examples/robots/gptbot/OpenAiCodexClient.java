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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A {@link CodexClient} that calls the OpenAI Chat Completions API directly.
 * Requires {@code OPENAI_API_KEY} environment variable.  Optionally reads
 * {@code OPENAI_BASE_URL} (defaults to {@code https://api.openai.com/v1}) and
 * {@code GPTBOT_OPENAI_MODEL} (defaults to {@code gpt-4o-mini}).
 */
public final class OpenAiCodexClient implements CodexClient {

  private static final Log LOG = Log.get(OpenAiCodexClient.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  private static final String WEB_SEARCH_TOOL_JSON = "[{\"type\":\"function\",\"function\":{"
      + "\"name\":\"web_search\","
      + "\"description\":\"Search the web for current information\","
      + "\"parameters\":{\"type\":\"object\","
      + "\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"The search query\"}},"
      + "\"required\":[\"query\"]}}}]";

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;
  private final boolean webSearchEnabled;

  public OpenAiCodexClient() {
    this.apiKey = requireEnv("OPENAI_API_KEY");
    String base = System.getenv("OPENAI_BASE_URL");
    this.baseUrl = (base != null && !base.isBlank()) ? stripTrailingSlash(base) : "https://api.openai.com/v1";
    String envModel = System.getenv("GPTBOT_OPENAI_MODEL");
    this.model = (envModel != null && !envModel.isBlank()) ? envModel.trim() : "gpt-4o-mini";
    String webSearch = System.getenv("GPTBOT_WEB_SEARCH_ENABLED");
    this.webSearchEnabled = "true".equalsIgnoreCase(webSearch == null ? "" : webSearch.trim());
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    LOG.info("OpenAI client initialized (model=" + model + ", base=" + baseUrl
        + ", webSearch=" + webSearchEnabled + ")");
  }

  OpenAiCodexClient(String apiKey, String baseUrl, String model, HttpClient httpClient,
      boolean webSearchEnabled) {
    this.apiKey = apiKey;
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.model = model;
    this.httpClient = httpClient;
    this.webSearchEnabled = webSearchEnabled;
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

  @Override
  public String completeMessages(List<Map<String, String>> messages) {
    if (messages == null) {
      throw new IllegalArgumentException("messages must not be null");
    }
    try {
      JsonArray messagesArray = buildMessagesArray(messages);
      JsonObject body = buildChatCompletionsRequest(messagesArray, false);
      if (webSearchEnabled) {
        body.add("tools", JsonParser.parseString(WEB_SEARCH_TOOL_JSON));
        body.addProperty("tool_choice", "auto");
      }

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
      if (webSearchEnabled) {
        String toolCallResult = handleToolCalls(response.body());
        if (toolCallResult != null) {
          return completeWithToolResult(messagesArray, response.body(), toolCallResult);
        }
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

  @Override
  public String completeMessagesStreaming(List<Map<String, String>> messages,
      StreamingListener listener) {
    if (messages == null) {
      throw new IllegalArgumentException("messages must not be null");
    }
    if (webSearchEnabled) {
      return CodexClient.super.completeMessagesStreaming(messages, listener);
    }
    try {
      JsonArray messagesArray = buildMessagesArray(messages);
      JsonObject body = buildChatCompletionsRequest(messagesArray, true);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .timeout(REQUEST_TIMEOUT)
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();

      HttpResponse<Stream<String>> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
      StringBuilder accumulated = new StringBuilder();
      try (Stream<String> lines = response.body()) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          LOG.warning("OpenAI API returned HTTP " + response.statusCode());
          return "I'm having trouble generating a response right now.";
        }
        lines.forEach(line -> applyStreamingLine(line, accumulated, listener));
      }

      String content = accumulated.toString().trim();
      if (content.isEmpty()) {
        content = "I'm here — what would you like me to help with?";
      }
      return content;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.warning("OpenAI streaming API call failed", e);
      return "I'm having trouble generating a response right now.";
    }
  }

  /**
   * If the response contains tool_calls, extracts the first web_search call,
   * executes the DuckDuckGo query, and returns the search result string.
   * Returns null if no tool calls are present.
   */
  private String handleToolCalls(String responseBody) {
    try {
      JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
      JsonArray choices = json.getAsJsonArray("choices");
      if (choices == null || choices.size() == 0) return null;
      JsonObject choice = choices.get(0).getAsJsonObject();
      JsonObject message = choice.getAsJsonObject("message");
      if (message == null || !message.has("tool_calls")) return null;
      JsonArray toolCalls = message.getAsJsonArray("tool_calls");
      if (toolCalls == null || toolCalls.size() == 0) return null;

      JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
      JsonObject function = toolCall.getAsJsonObject("function");
      if (function == null) return null;
      String name = function.has("name") ? function.get("name").getAsString() : "";
      if (!"web_search".equals(name)) return null;

      String argsJson = function.has("arguments") ? function.get("arguments").getAsString() : "{}";
      JsonObject args = JsonParser.parseString(argsJson).getAsJsonObject();
      String query = args.has("query") ? args.get("query").getAsString() : "";
      if (query.isBlank()) return "No search results found.";

      return executeDuckDuckGoSearch(query);
    } catch (Exception e) {
      LOG.warning("Failed to handle tool_calls", e);
      return null;
    }
  }

  private String executeDuckDuckGoSearch(String query) {
    try {
      String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
      String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .header("User-Agent", "SupaWave-GptBot/1.0")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warning("DuckDuckGo returned HTTP " + response.statusCode());
        return "Search unavailable.";
      }
      return extractDuckDuckGoResult(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      LOG.warning("DuckDuckGo search failed", e);
      return "Search unavailable.";
    }
  }

  private static String extractDuckDuckGoResult(String responseBody) {
    try {
      JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
      // Try AbstractText first (best single-sentence answer)
      if (json.has("AbstractText")) {
        String text = json.get("AbstractText").getAsString().trim();
        if (!text.isEmpty()) return text;
      }
      // Fall back to Answer (e.g., calculator results)
      if (json.has("Answer")) {
        String answer = json.get("Answer").getAsString().trim();
        if (!answer.isEmpty()) return answer;
      }
      // Fall back to first RelatedTopic
      if (json.has("RelatedTopics")) {
        JsonArray topics = json.getAsJsonArray("RelatedTopics");
        if (topics != null && topics.size() > 0) {
          JsonObject first = topics.get(0).getAsJsonObject();
          if (first.has("Text")) {
            String text = first.get("Text").getAsString().trim();
            if (!text.isEmpty()) return text;
          }
        }
      }
      return "No relevant results found.";
    } catch (Exception e) {
      return "No relevant results found.";
    }
  }

  private String completeWithToolResult(JsonArray originalMessages, String assistantResponseBody,
      String toolResult) {
    try {
      // Reconstruct messages: original + assistant tool_call message + tool result message
      JsonObject json = JsonParser.parseString(assistantResponseBody).getAsJsonObject();
      JsonArray choices = json.getAsJsonArray("choices");
      JsonObject assistantMsg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
      // Get the tool_call id
      JsonArray toolCalls = assistantMsg.getAsJsonArray("tool_calls");
      String toolCallId = toolCalls.get(0).getAsJsonObject().get("id").getAsString();

      JsonArray messages = originalMessages.deepCopy();
      messages.add(assistantMsg);

      JsonObject toolResultMsg = new JsonObject();
      toolResultMsg.addProperty("role", "tool");
      toolResultMsg.addProperty("tool_call_id", toolCallId);
      toolResultMsg.addProperty("content", toolResult);
      messages.add(toolResultMsg);

      JsonObject body = new JsonObject();
      body.addProperty("model", model);
      body.add("messages", messages);
      body.addProperty("max_tokens", 512);
      body.addProperty("temperature", 0.7);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .timeout(REQUEST_TIMEOUT)
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warning("OpenAI tool-result call returned HTTP " + response.statusCode());
        return "I received search results but couldn't generate a response.";
      }
      return extractContent(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      LOG.warning("OpenAI tool-result call failed", e);
      return "I received search results but couldn't generate a response.";
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

  private JsonArray buildMessagesArray(List<Map<String, String>> messages) {
    JsonArray messagesArray = new JsonArray();
    for (Map<String, String> msg : messages) {
      if (msg == null || !msg.containsKey("role") || !msg.containsKey("content")) {
        throw new IllegalArgumentException("Each message must have 'role' and 'content' keys");
      }
      JsonObject message = new JsonObject();
      message.addProperty("role", msg.get("role"));
      message.addProperty("content", msg.get("content"));
      messagesArray.add(message);
    }
    return messagesArray;
  }

  private JsonObject buildChatCompletionsRequest(JsonArray messagesArray, boolean stream) {
    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messagesArray);
    body.addProperty("max_tokens", 512);
    body.addProperty("temperature", 0.7);
    if (stream) {
      body.addProperty("stream", true);
    }
    return body;
  }

  private void applyStreamingLine(String line, StringBuilder accumulated, StreamingListener listener) {
    if (line == null) {
      return;
    }
    String trimmed = line.trim();
    if (!trimmed.startsWith("data:")) {
      return;
    }
    String payload = trimmed.substring("data:".length()).trim();
    if (payload.isEmpty() || "[DONE]".equals(payload)) {
      return;
    }
    try {
      JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
      JsonArray choices = json.getAsJsonArray("choices");
      if (choices == null || choices.size() == 0) {
        return;
      }
      JsonObject choice = choices.get(0).getAsJsonObject();
      JsonObject delta = choice.has("delta") && choice.get("delta").isJsonObject()
          ? choice.getAsJsonObject("delta") : null;
      if (delta != null && delta.has("content")) {
        String deltaText = delta.get("content").getAsString();
        if (!deltaText.isEmpty()) {
          accumulated.append(deltaText);
          if (listener != null) {
            listener.onText(accumulated.toString());
          }
        }
      }
    } catch (RuntimeException e) {
      LOG.warning("Unable to parse OpenAI streaming payload", e);
    }
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

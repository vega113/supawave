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

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP server for the gpt-bot example robot.
 */
public final class GptBotServer {

  private static final Log LOG = Log.get(GptBotServer.class);
  private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

  public static void main(String[] args) throws Exception {
    GptBotConfig config = GptBotConfig.fromEnvironment();

    // Startup diagnostics — log all key configuration so failures are immediately visible in logs
    String logConfigFile = System.getProperty("java.util.logging.config.file");
    LOG.info("gpt-bot startup: java.util.logging config="
        + (logConfigFile != null ? logConfigFile : "default (console/stderr)"));
    LOG.info("gpt-bot startup: robotName=" + config.getRobotName()
        + " participantId=" + config.getParticipantId());
    LOG.info("gpt-bot startup: replyMode=" + config.getReplyMode().name().toLowerCase()
        + " contextMode=" + config.getContextMode().name().toLowerCase());
    LOG.info("gpt-bot startup: apiRobotId=" + config.getApiRobotId()
        + " apiCredentialsPresent=" + config.hasApiCredentials());
    LOG.info("gpt-bot startup: supaWaveBaseUrl=" + config.getBaseUrl());

    String codexEngine = System.getenv("GPTBOT_CODEX_ENGINE");
    String engineName = codexEngine != null ? codexEngine.trim().toLowerCase() : "codex";
    LOG.info("gpt-bot startup: LLM engine=" + engineName);
    String openAiKey = System.getenv("OPENAI_API_KEY");
    CodexClient codexClient = selectCodexClient(engineName, openAiKey != null ? openAiKey : "",
        config);
    GptBotReplyPlanner replyPlanner = new GptBotReplyPlanner(config.getRobotName(), codexClient);
    SupaWaveApiClient apiClient = new SupaWaveApiClient(config);
    GptBotRobot robot = new GptBotRobot(config, replyPlanner, apiClient);
    if (!config.getPublicBaseUrl().isBlank() && config.getCallbackToken().isBlank()) {
      LOG.warning("GPTBOT_PUBLIC_BASE_URL is set without GPTBOT_CALLBACK_TOKEN; callback "
          + "requests will be unauthenticated");
    }

    HttpServer server = HttpServer.create(new InetSocketAddress(config.getListenHost(),
        config.getListenPort()), 0);
    server.createContext("/healthz", new TextHandler(200, "ok\n", "text/plain; charset=utf-8"));
    server.createContext("/", new RootHandler(config, robot));
    server.createContext(robot.getCapabilitiesPath(), new TextHandler(200, robot.getCapabilitiesXml(),
        "application/xml; charset=utf-8"));
    server.createContext(robot.getProfilePath(), new TextHandler(200, robot.getProfileJson(),
        "application/json; charset=utf-8"));
    server.createContext(robot.getRpcPath(), new CallbackHandler(config, robot));
    int workerThreads = config.getHttpWorkerThreads();
    server.setExecutor(new ThreadPoolExecutor(workerThreads, workerThreads, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>(workerThreads), new ThreadPoolExecutor.CallerRunsPolicy()));
    server.start();

    LOG.info("gpt-bot listening on " + config.getLocalBaseUrl());
    LOG.info("capabilities available at " + robot.getCapabilitiesPath());
    LOG.info("profile available at " + robot.getProfilePath());
    LOG.info("callback endpoint available at " + robot.getRpcPath());
  }

  private static final class CallbackHandler implements HttpHandler {

    private final GptBotConfig config;
    private final GptBotRobot robot;

    private CallbackHandler(GptBotConfig config, GptBotRobot robot) {
      this.config = config;
      this.robot = robot;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.getRequestBody().close();
        new TextHandler(405, "method not allowed\n", "text/plain; charset=utf-8")
            .handle(exchange);
      } else {
        if (!isCallbackAuthorized(exchange.getRequestURI().getRawQuery(),
            config.getCallbackToken())) {
          exchange.getRequestBody().close();
          new TextHandler(403, "{\"error\":\"unauthorized\"}\n",
              "application/json; charset=utf-8").handle(exchange);
        } else if (isRequestTooLarge(exchange)) {
          exchange.getRequestBody().close();
          new TextHandler(413, "{\"error\":\"request too large\"}\n",
              "application/json; charset=utf-8").handle(exchange);
        } else {
          try (InputStream requestBody = exchange.getRequestBody()) {
            byte[] bodyBytes = requestBody.readNBytes(MAX_REQUEST_BODY_BYTES + 1);
            if (bodyBytes.length > MAX_REQUEST_BODY_BYTES) {
              new TextHandler(413, "{\"error\":\"request too large\"}\n",
                  "application/json; charset=utf-8").handle(exchange);
            } else {
              String body = new String(bodyBytes, StandardCharsets.UTF_8);
              try {
                String response = robot.handleEventBundle(body);
                new TextHandler(200, response, "application/json; charset=utf-8")
                    .handle(exchange);
              } catch (IllegalArgumentException | JsonSyntaxException e) {
                LOG.warning("gpt-bot callback rejected invalid bundle", e);
                new TextHandler(400, "{\"error\":\"invalid event bundle\"}\n",
                    "application/json; charset=utf-8").handle(exchange);
              } catch (RuntimeException e) {
                LOG.warning("gpt-bot callback failed", e);
                new TextHandler(500, "{\"error\":\"internal server error\"}\n",
                    "application/json; charset=utf-8").handle(exchange);
              }
            }
          }
        }
      }
    }

    private boolean isRequestTooLarge(HttpExchange exchange) {
      String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
      boolean tooLarge = false;
      if (contentLength != null) {
        try {
          tooLarge = Long.parseLong(contentLength) > MAX_REQUEST_BODY_BYTES;
        } catch (NumberFormatException e) {
          tooLarge = false;
        }
      }
      return tooLarge;
    }
  }

  /**
   * Selects and creates the appropriate {@link CodexClient} based on the engine name and
   * available credentials. Exposed package-private for testing.
   *
   * @param engineName   one of "echo", "openai", or anything else (Codex CLI)
   * @param openAiKey    the OpenAI API key, or blank/null when not configured
   * @param config       the bot configuration (used for Codex CLI parameters)
   * @return the appropriate {@link CodexClient} — never null
   */
  static CodexClient selectCodexClient(String engineName, String openAiKey, GptBotConfig config) {
    switch (engineName == null ? "" : engineName) {
      case "echo":
        LOG.info("Using echo engine (no external LLM required)");
        return new EchoCodexClient();
      case "openai":
        if (openAiKey == null || openAiKey.isBlank()) {
          LOG.warning("OPENAI_API_KEY is not set — falling back to echo engine. "
              + "Set OPENAI_API_KEY or use GPTBOT_CODEX_ENGINE=echo to suppress this warning.");
          return new EchoCodexClient();
        }
        LOG.info("Using OpenAI Chat Completions API engine");
        return new OpenAiCodexClient();
      default:
        LOG.info("Using Codex CLI engine: binary=" + config.getCodexBinary()
            + " model=" + config.getCodexModel()
            + " reasoningEffort=" + config.getCodexReasoningEffort()
            + " timeoutSeconds=" + config.getCodexTimeout().getSeconds()
            + " unsafeBypass=" + config.isCodexUnsafeBypassEnabled());
        return new ProcessCodexClient(config.getCodexBinary(), config.getCodexModel(),
            config.getCodexReasoningEffort(), config.getCodexTimeout(),
            config.isCodexUnsafeBypassEnabled());
    }
  }

  static boolean isCallbackAuthorized(String rawQuery, String callbackToken) {
    if (callbackToken.isBlank()) {
      return true;
    }
    String providedToken = queryParameter(rawQuery, "token");
    return callbackToken.equals(providedToken);
  }

  private static String queryParameter(String rawQuery, String parameterName) {
    String value = "";
    if (rawQuery != null && !rawQuery.isBlank()) {
      String[] parameters = rawQuery.split("&");
      for (String parameter : parameters) {
        int separator = parameter.indexOf('=');
        String rawName = separator >= 0 ? parameter.substring(0, separator) : parameter;
        String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        if (parameterName.equals(name)) {
          String rawValue = separator >= 0 ? parameter.substring(separator + 1) : "";
          value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
          break;
        }
      }
    }
    return value;
  }

  private static final class RootHandler implements HttpHandler {

    private final GptBotConfig config;
    private final GptBotRobot robot;

    private RootHandler(GptBotConfig config, GptBotRobot robot) {
      this.config = config;
      this.robot = robot;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      StringBuilder body = new StringBuilder();
      body.append("gpt-bot is running\n");
      body.append("local base URL: ").append(config.getLocalBaseUrl()).append('\n');
      body.append("advertised base URL: ").append(config.getAdvertisedBaseUrl()).append('\n');
      body.append("SupaWave base URL: ").append(config.getBaseUrl()).append('\n');
      body.append("reply mode: ").append(config.getReplyMode().name().toLowerCase()).append('\n');
      body.append("context mode: ").append(config.getContextMode().name().toLowerCase()).append('\n');
      body.append("profile: ").append(robot.getProfilePath()).append('\n');
      body.append("capabilities: ").append(robot.getCapabilitiesPath()).append('\n');
      body.append("callback: ").append(config.getRedactedCallbackUrl(robot.getRpcPath()))
          .append('\n');
      new TextHandler(200, body.toString(), "text/plain; charset=utf-8").handle(exchange);
    }
  }

  private static final class TextHandler implements HttpHandler {

    private final int status;
    private final String body;
    private final String contentType;

    private TextHandler(int status, String body, String contentType) {
      this.status = status;
      this.body = body;
      this.contentType = contentType;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(status, payload.length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(payload);
      }
    }
  }
}

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

import org.waveprotocol.wave.util.logging.Log;

import java.time.Duration;
import java.util.Locale;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Immutable configuration for the gpt-bot example robot.
 */
public final class GptBotConfig {

  private static final Log LOG = Log.get(GptBotConfig.class);
  private static final String DEFAULT_BOT_NAME = "gpt-bot";
  private static final String DEFAULT_PARTICIPANT_ID = "gpt-bot@example.com";
  private static final String DEFAULT_BASE_URL = "https://supawave.ai";
  private static final String DEFAULT_PROFILE_URL = "https://supawave.ai/api-docs";
  private static final String DEFAULT_AVATAR_URL =
      "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 128 128'%3E"
          + "%3Crect width='128' height='128' rx='24' fill='%23111827'/%3E"
          + "%3Ccircle cx='64' cy='48' r='22' fill='%233b82f6'/%3E"
          + "%3Cpath d='M32 102c6-20 20-30 32-30s26 10 32 30' fill='%233b82f6'/%3E"
          + "%3C/svg%3E";
  private static final String DEFAULT_CONTEXT_MODE = "none";
  private static final String DEFAULT_REPLY_MODE = "passive";
  private static final String DEFAULT_CODEX_BINARY = "codex";
  private static final String DEFAULT_CODEX_MODEL = "gpt-5.4-mini";
  private static final String DEFAULT_CODEX_REASONING_EFFORT = "low";

  private final String robotName;
  private final String participantId;
  private final String baseUrl;
  private final String publicBaseUrl;
  private final String profilePageUrl;
  private final String avatarUrl;
  private final String listenHost;
  private final int listenPort;
  private final String codexBinary;
  private final String codexModel;
  private final String codexReasoningEffort;
  private final Duration codexTimeout;
  private final int httpWorkerThreads;
  private final boolean codexUnsafeBypass;
  private final boolean submittedOnly;
  private final String callbackToken;
  private final ContextMode contextMode;
  private final ReplyMode replyMode;
  private final String apiRobotId;
  private final String apiRobotSecret;

  private GptBotConfig(String robotName, String participantId, String baseUrl,
      String publicBaseUrl, String profilePageUrl, String avatarUrl, String listenHost,
      int listenPort, String codexBinary, String codexModel, String codexReasoningEffort,
      Duration codexTimeout, int httpWorkerThreads, boolean codexUnsafeBypass,
      boolean submittedOnly, String callbackToken,
      ContextMode contextMode, ReplyMode replyMode, String apiRobotId, String apiRobotSecret) {
    this.robotName = robotName;
    this.participantId = participantId;
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    this.profilePageUrl = profilePageUrl;
    this.avatarUrl = avatarUrl;
    this.listenHost = listenHost;
    this.listenPort = listenPort;
    this.codexBinary = codexBinary;
    this.codexModel = codexModel;
    this.codexReasoningEffort = codexReasoningEffort;
    this.codexTimeout = codexTimeout;
    this.httpWorkerThreads = httpWorkerThreads;
    this.codexUnsafeBypass = codexUnsafeBypass;
    this.submittedOnly = submittedOnly;
    this.callbackToken = callbackToken;
    this.contextMode = contextMode;
    this.replyMode = replyMode;
    this.apiRobotId = apiRobotId;
    this.apiRobotSecret = apiRobotSecret;
  }

  public static GptBotConfig fromEnvironment() {
    String robotName = readString("GPTBOT_ROBOT_NAME", null, DEFAULT_BOT_NAME);
    String participantId = readString("GPTBOT_PARTICIPANT_ID", null, DEFAULT_PARTICIPANT_ID);
    String baseUrl = readString("SUPAWAVE_BASE_URL", null, DEFAULT_BASE_URL);
    String publicBaseUrl = readString("GPTBOT_PUBLIC_BASE_URL", null, "");
    String profilePageUrl = readString("GPTBOT_PROFILE_URL", null, DEFAULT_PROFILE_URL);
    String avatarUrl = readString("GPTBOT_AVATAR_URL", null, DEFAULT_AVATAR_URL);
    String listenHost = readString("GPTBOT_LISTEN_HOST", null, "0.0.0.0");
    int listenPort = readInt("GPTBOT_LISTEN_PORT", null, 8087, 1, 65535);
    String codexBinary = readString("GPTBOT_CODEX_BINARY", null, DEFAULT_CODEX_BINARY);
    String codexModel = readString("GPTBOT_CODEX_MODEL", null, DEFAULT_CODEX_MODEL);
    String codexReasoningEffort = readString("GPTBOT_CODEX_REASONING_EFFORT", null,
        DEFAULT_CODEX_REASONING_EFFORT);
    Duration codexTimeout = Duration.ofSeconds(readInt("GPTBOT_CODEX_TIMEOUT_SECONDS", null,
        120, 1, 3600));
    int httpWorkerThreads = readInt("GPTBOT_HTTP_WORKERS", null, 4, 1, 128);
    boolean codexUnsafeBypass = readBoolean("GPTBOT_CODEX_UNSAFE_BYPASS", null, false);
    boolean submittedOnly = readBoolean("GPTBOT_SUBMITTED_ONLY", null, false);
    String callbackToken = readString("GPTBOT_CALLBACK_TOKEN", null, "");
    ContextMode contextMode = ContextMode.from(readString("GPTBOT_CONTEXT_MODE", null,
        DEFAULT_CONTEXT_MODE), "GPTBOT_CONTEXT_MODE", DEFAULT_CONTEXT_MODE);
    ReplyMode replyMode = ReplyMode.from(readString("GPTBOT_REPLY_MODE", null,
        DEFAULT_REPLY_MODE), "GPTBOT_REPLY_MODE", DEFAULT_REPLY_MODE);
    String apiRobotId = readString("GPTBOT_API_ROBOT_ID", null, participantId);
    String apiRobotSecret = readString("GPTBOT_API_ROBOT_SECRET", null, "");
    return new GptBotConfig(robotName, participantId, baseUrl, publicBaseUrl, profilePageUrl,
        avatarUrl, listenHost, listenPort, codexBinary, codexModel, codexReasoningEffort,
        codexTimeout, httpWorkerThreads, codexUnsafeBypass, submittedOnly, callbackToken,
        contextMode, replyMode, apiRobotId, apiRobotSecret);
  }

  static GptBotConfig forTest() {
    return new GptBotConfig(DEFAULT_BOT_NAME, DEFAULT_PARTICIPANT_ID, DEFAULT_BASE_URL, "",
        DEFAULT_PROFILE_URL, DEFAULT_AVATAR_URL, "0.0.0.0", 8087, DEFAULT_CODEX_BINARY,
        DEFAULT_CODEX_MODEL, DEFAULT_CODEX_REASONING_EFFORT, Duration.ofSeconds(120), 4, false,
        false, "", ContextMode.NONE, ReplyMode.PASSIVE, "test-robot", "test-secret");
  }

  public String getRobotName() {
    return robotName;
  }

  public String getParticipantId() {
    return participantId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public String getAdvertisedBaseUrl() {
    String advertisedBaseUrl = publicBaseUrl;
    if (advertisedBaseUrl.isBlank()) {
      advertisedBaseUrl = getLocalBaseUrl();
    }
    return advertisedBaseUrl;
  }

  public String getLocalBaseUrl() {
    String host = listenHost;
    if ("0.0.0.0".equals(host)) {
      host = "127.0.0.1";
    }
    return "http://" + host + ":" + listenPort;
  }

  public String getProfilePageUrl() {
    return profilePageUrl;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getListenHost() {
    return listenHost;
  }

  public int getListenPort() {
    return listenPort;
  }

  public String getCodexBinary() {
    return codexBinary;
  }

  public String getCodexModel() {
    return codexModel;
  }

  public String getCodexReasoningEffort() {
    return codexReasoningEffort;
  }

  public Duration getCodexTimeout() {
    return codexTimeout;
  }

  public int getHttpWorkerThreads() {
    return httpWorkerThreads;
  }

  public boolean isCodexUnsafeBypassEnabled() {
    return codexUnsafeBypass;
  }

  /**
   * @deprecated This flag has no effect. Use BLIP_EDITING_DONE event instead.
   */
  @Deprecated
  public boolean isSubmittedOnly() {
    return submittedOnly;
  }

  public String getCallbackToken() {
    return callbackToken;
  }

  public ContextMode getContextMode() {
    return contextMode;
  }

  public ReplyMode getReplyMode() {
    return replyMode;
  }

  public String getApiRobotId() {
    return apiRobotId;
  }

  public String getApiRobotSecret() {
    return apiRobotSecret;
  }

  public boolean hasApiCredentials() {
    return !apiRobotId.isBlank() && !apiRobotSecret.isBlank();
  }

  public boolean usesActiveApi() {
    return contextMode == ContextMode.ACTIVE
        || replyMode == ReplyMode.ACTIVE
        || replyMode == ReplyMode.ACTIVE_STREAM;
  }

  public GptBotConfig withSubmittedOnly(boolean newSubmittedOnly) {
    return new GptBotConfig(robotName, participantId, baseUrl, publicBaseUrl, profilePageUrl,
        avatarUrl, listenHost, listenPort, codexBinary, codexModel, codexReasoningEffort,
        codexTimeout, httpWorkerThreads, codexUnsafeBypass, newSubmittedOnly, callbackToken,
        contextMode, replyMode, apiRobotId, apiRobotSecret);
  }

  public GptBotConfig withBaseUrl(String newBaseUrl) {
    return new GptBotConfig(robotName, participantId, newBaseUrl, publicBaseUrl, profilePageUrl,
        avatarUrl, listenHost, listenPort, codexBinary, codexModel, codexReasoningEffort,
        codexTimeout, httpWorkerThreads, codexUnsafeBypass, submittedOnly, callbackToken,
        contextMode, replyMode, apiRobotId, apiRobotSecret);
  }

  public GptBotConfig withReplyMode(ReplyMode newReplyMode) {
    return new GptBotConfig(robotName, participantId, baseUrl, publicBaseUrl, profilePageUrl,
        avatarUrl, listenHost, listenPort, codexBinary, codexModel, codexReasoningEffort,
        codexTimeout, httpWorkerThreads, codexUnsafeBypass, submittedOnly, callbackToken,
        contextMode, newReplyMode, apiRobotId, apiRobotSecret);
  }

  public GptBotConfig withCallbackToken(String newCallbackToken) {
    return new GptBotConfig(robotName, participantId, baseUrl, publicBaseUrl, profilePageUrl,
        avatarUrl, listenHost, listenPort, codexBinary, codexModel, codexReasoningEffort,
        codexTimeout, httpWorkerThreads, codexUnsafeBypass, submittedOnly, newCallbackToken,
        contextMode, replyMode, apiRobotId, apiRobotSecret);
  }

  public String getCallbackUrl(String rpcPath) {
    String callbackUrl = getAdvertisedBaseUrl() + rpcPath;
    if (!callbackToken.isBlank()) {
      callbackUrl = callbackUrl + "?token=" + URLEncoder.encode(callbackToken, StandardCharsets.UTF_8);
    }
    return callbackUrl;
  }

  public String getRedactedCallbackUrl(String rpcPath) {
    String callbackUrl = getAdvertisedBaseUrl() + rpcPath;
    if (!callbackToken.isBlank()) {
      callbackUrl = callbackUrl + "?token=<redacted>";
    }
    return callbackUrl;
  }

  private static String readString(String primaryName, String legacyName, String defaultValue) {
    String value = readEnvironment(primaryName);
    if (value.isBlank()) {
      value = readEnvironment(legacyName);
    }
    if (value.isBlank()) {
      value = defaultValue;
    }
    return value.trim();
  }

  private static int readInt(String primaryName, String legacyName, int defaultValue) {
    return readInt(primaryName, legacyName, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  private static int readInt(String primaryName, String legacyName, int defaultValue,
      int minimumValue, int maximumValue) {
    String value = readEnvironment(primaryName);
    if (value.isBlank()) {
      value = readEnvironment(legacyName);
    }
    int parsed = defaultValue;
    if (!value.isBlank()) {
      String trimmed = value.trim();
      try {
        int candidate = Integer.parseInt(trimmed);
        if (candidate >= minimumValue && candidate <= maximumValue) {
          parsed = candidate;
        } else {
          LOG.warning("Ignoring out-of-range value for " + environmentName(primaryName, legacyName)
              + "; using default " + defaultValue);
        }
      } catch (NumberFormatException e) {
        LOG.warning("Ignoring invalid integer value for " + environmentName(primaryName, legacyName)
            + "; using default " + defaultValue, e);
      }
    }
    return parsed;
  }

  private static boolean readBoolean(String primaryName, String legacyName, boolean defaultValue) {
    String value = readEnvironment(primaryName);
    if (value.isBlank()) {
      value = readEnvironment(legacyName);
    }
    boolean parsed = defaultValue;
    if (!value.isBlank()) {
      String trimmed = value.trim().toLowerCase(Locale.ROOT);
      if ("true".equals(trimmed)) {
        parsed = true;
      } else if ("false".equals(trimmed)) {
        parsed = false;
      } else {
        LOG.warning("Ignoring unrecognized boolean value for "
            + environmentName(primaryName, legacyName) + ": " + value
            + "; using default " + defaultValue);
      }
    }
    return parsed;
  }

  private static String readEnvironment(String name) {
    String value = "";
    if (name != null) {
      String current = System.getenv(name);
      if (current != null) {
        value = current;
      }
    }
    return value;
  }

  private static String environmentName(String primaryName, String legacyName) {
    String name = primaryName;
    if (name == null || name.isBlank()) {
      name = legacyName;
    }
    return name;
  }

  private static String stripTrailingSlash(String value) {
    String stripped = value == null ? "" : value.trim();
    while (stripped.endsWith("/")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    return stripped;
  }

  /**
   * The transport used when the robot reaches back to SupaWave for extra context.
   */
  public enum ContextMode {
    NONE,
    DATA,
    ACTIVE;

    private static ContextMode from(String value, String environmentName, String defaultValue) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      ContextMode mode = NONE;
      if ("data".equals(normalized)) {
        mode = DATA;
      } else if ("active".equals(normalized)) {
        mode = ACTIVE;
      } else if (!normalized.isEmpty() && !"none".equals(normalized)) {
        LOG.warning("Ignoring invalid value for " + environmentName + ": " + value
            + "; allowed values: none,data,active; using default " + defaultValue);
      }
      return mode;
    }
  }

  /**
   * The way replies are posted back into SupaWave.
   */
  public enum ReplyMode {
    PASSIVE,
    ACTIVE,
    ACTIVE_STREAM;

    private static ReplyMode from(String value, String environmentName, String defaultValue) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      ReplyMode mode = PASSIVE;
      if ("active".equals(normalized)) {
        mode = ACTIVE;
      } else if ("active-stream".equals(normalized) || "active_stream".equals(normalized)
          || "activestream".equals(normalized)) {
        mode = ACTIVE_STREAM;
      } else if (!normalized.isEmpty() && !"passive".equals(normalized)) {
        LOG.warning("Ignoring invalid value for " + environmentName + ": " + value
            + "; allowed values: passive,active,active-stream; using default " + defaultValue);
      }
      return mode;
    }
  }
}

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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spawns the local Codex CLI in headless mode.
 */
public final class ProcessCodexClient implements CodexClient {

  private static final Log LOG = Log.get(ProcessCodexClient.class);
  private static final String[] CHILD_ENVIRONMENT_VARIABLES = {
      "HOME",
      "PATH",
      "USER",
      "LOGNAME",
      "SHELL",
      "LANG",
      "LC_ALL",
      "TMPDIR",
      "XDG_CONFIG_HOME",
      "XDG_CACHE_HOME",
      "XDG_STATE_HOME",
      "XDG_DATA_HOME",
      "CODEX_HOME",
      "NO_PROXY",
      "HTTPS_PROXY",
      "HTTP_PROXY",
      "ALL_PROXY",
      "SSL_CERT_FILE",
      "SSL_CERT_DIR",
      "REQUESTS_CA_BUNDLE",
      "CURL_CA_BUNDLE",
      "OPENAI_API_KEY",
      "OPENAI_BASE_URL",
      "OPENAI_ORGANIZATION",
      "OPENAI_PROJECT"
  };

  private final String codexBinary;
  private final String model;
  private final String reasoningEffort;
  private final Duration timeout;
  private final boolean unsafeBypassApprovalsAndSandbox;
  private final ProcessLauncher processLauncher;

  public ProcessCodexClient(String codexBinary, String model, String reasoningEffort,
      Duration timeout, boolean unsafeBypassApprovalsAndSandbox) {
    this(codexBinary, model, reasoningEffort, timeout, unsafeBypassApprovalsAndSandbox,
        ProcessCodexClient::startProcess);
  }

  ProcessCodexClient(String codexBinary, String model, String reasoningEffort, Duration timeout,
      boolean unsafeBypassApprovalsAndSandbox, ProcessLauncher processLauncher) {
    this.codexBinary = codexBinary;
    this.model = model;
    this.reasoningEffort = reasoningEffort;
    this.timeout = timeout;
    this.unsafeBypassApprovalsAndSandbox = unsafeBypassApprovalsAndSandbox;
    this.processLauncher = processLauncher;
  }

  @Override
  public String complete(String prompt) {
    Path outputFile = null;
    Path errorFile = null;
    Process process = null;
    String response = "";
    try {
      outputFile = Files.createTempFile("gpt-bot-codex-", ".txt");
      errorFile = Files.createTempFile("gpt-bot-codex-", ".log");
      List<String> command = new ArrayList<String>();
      command.add(codexBinary);
      command.add("exec");
      command.add("--model");
      command.add(model);
      String sanitizedReasoningEffort = normalizedReasoningEffort();
      if (!sanitizedReasoningEffort.isEmpty()) {
        command.add("-c");
        command.add("model_reasoning_effort=\"" + sanitizedReasoningEffort + "\"");
      }
      command.add("--color");
      command.add("never");
      command.add("--output-last-message");
      command.add(outputFile.toString());
      if (unsafeBypassApprovalsAndSandbox) {
        command.add("--dangerously-bypass-approvals-and-sandbox");
      }
      command.add("-");

      process = processLauncher.start(command, errorFile);
      try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
          process.getOutputStream(), StandardCharsets.UTF_8))) {
        writer.write(prompt);
      } catch (IOException e) {
        destroyProcess(process);
        throw new IllegalStateException("Unable to write Codex prompt", e);
      }

      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Codex timed out after " + timeout.toSeconds() + "s");
      }
      int exitCode = process.exitValue();
      if (exitCode != 0) {
        throw new IllegalStateException("Codex exited with status " + exitCode + ": "
            + readErrorSummary(errorFile));
      }

      if (Files.exists(outputFile)) {
        response = Files.readString(outputFile).trim();
      }
      if (response.isEmpty()) {
        response = "I’m here — what would you like me to help with?";
      }
    } catch (InterruptedException e) {
      destroyProcess(process);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Codex execution was interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to execute Codex", e);
    } finally {
      deleteIfPresent(outputFile);
      deleteIfPresent(errorFile);
    }
    return response;
  }

  static Map<String, String> buildChildEnvironment(Map<String, String> parentEnvironment) {
    Map<String, String> childEnvironment = new LinkedHashMap<String, String>();
    for (String variableName : CHILD_ENVIRONMENT_VARIABLES) {
      String value = parentEnvironment.get(variableName);
      if (value != null) {
        childEnvironment.put(variableName, value);
      }
    }
    return childEnvironment;
  }

  private String normalizedReasoningEffort() {
    String normalized = reasoningEffort == null ? "" : reasoningEffort.trim();
    if (normalized.isEmpty()) {
      return "";
    }
    if (normalized.matches("[A-Za-z0-9._-]+")) {
      return normalized;
    }
    LOG.warning("Ignoring invalid reasoning effort value: " + reasoningEffort);
    return "";
  }

  private static void destroyProcess(Process process) {
    if (process != null) {
      process.destroyForcibly();
    }
  }

  private static String readErrorSummary(Path errorFile) {
    String summary = "no stderr captured";
    try {
      if (errorFile != null && Files.exists(errorFile)) {
        String stderr = Files.readString(errorFile).trim();
        if (!stderr.isEmpty()) {
          summary = stderr.length() > 240 ? stderr.substring(0, 240).trim() + "…" : stderr;
        }
      }
    } catch (IOException e) {
      summary = "unable to read stderr";
    }
    return summary;
  }

  private static void deleteIfPresent(Path file) {
    if (file != null) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException e) {
        LOG.warning("Unable to delete temporary Codex file", e);
      }
    }
  }

  private static Process startProcess(List<String> command, Path errorFile) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(command);
    Map<String, String> environment = builder.environment();
    environment.clear();
    environment.putAll(buildChildEnvironment(System.getenv()));
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    builder.redirectError(errorFile.toFile());
    return builder.start();
  }

  interface ProcessLauncher {
    Process start(List<String> command, Path errorFile) throws IOException;
  }
}

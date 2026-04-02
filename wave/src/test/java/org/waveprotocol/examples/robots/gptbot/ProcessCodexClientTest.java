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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProcessCodexClientTest extends TestCase {

  public void testCompleteFailsFastWhenPromptWriteFails() {
    ProcessCodexClient client = new ProcessCodexClient("codex", "gpt-5.4-mini", "low",
        Duration.ofSeconds(1), false, new ProcessCodexClient.ProcessLauncher() {
          @Override
          public Process start(List<String> command, Path errorFile) {
            return new FailingPromptProcess();
          }
        });

    try {
      client.complete("hello");
      fail("Expected prompt write failure");
    } catch (IllegalStateException e) {
      assertEquals("Unable to write Codex prompt", e.getMessage());
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  public void testCompleteDestroysProcessWhenInterrupted() {
    final RecordingInterruptedProcess process = new RecordingInterruptedProcess();
    ProcessCodexClient client = new ProcessCodexClient("codex", "gpt-5.4-mini", "low",
        Duration.ofSeconds(1), false, new ProcessCodexClient.ProcessLauncher() {
          @Override
          public Process start(List<String> command, Path errorFile) {
            return process;
          }
        });

    try {
      client.complete("hello");
      fail("Expected interruption failure");
    } catch (IllegalStateException e) {
      assertEquals("Codex execution was interrupted", e.getMessage());
      assertTrue(process.destroyed);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  public void testCompleteSkipsInvalidReasoningEffortValue() {
    RecordingProcessLauncher launcher = new RecordingProcessLauncher();
    ProcessCodexClient client = new ProcessCodexClient("codex", "gpt-5.4-mini",
        "low\";rm -rf /", Duration.ofSeconds(1), false, launcher);

    String response = client.complete("hello");

    assertEquals("I’m here — what would you like me to help with?", response);
    assertFalse(launcher.command.contains("-c"));
  }

  public void testBuildChildEnvironmentKeepsOnlyWhitelistedVariables() {
    Map<String, String> parentEnvironment = new HashMap<String, String>();
    parentEnvironment.put("HOME", "/home/test");
    parentEnvironment.put("PATH", "/usr/bin");
    parentEnvironment.put("OPENAI_API_KEY", "api-key");
    parentEnvironment.put("GPTBOT_API_ROBOT_SECRET", "secret");
    parentEnvironment.put("AWS_SECRET_ACCESS_KEY", "other-secret");

    Map<String, String> childEnvironment = ProcessCodexClient.buildChildEnvironment(
        parentEnvironment);

    assertEquals("/home/test", childEnvironment.get("HOME"));
    assertEquals("/usr/bin", childEnvironment.get("PATH"));
    assertEquals("api-key", childEnvironment.get("OPENAI_API_KEY"));
    assertFalse(childEnvironment.containsKey("GPTBOT_API_ROBOT_SECRET"));
    assertFalse(childEnvironment.containsKey("AWS_SECRET_ACCESS_KEY"));
  }

  private static final class RecordingProcessLauncher implements ProcessCodexClient.ProcessLauncher {

    private List<String> command;

    @Override
    public Process start(List<String> command, Path errorFile) {
      this.command = command;
      return new SuccessfulProcess();
    }
  }

  private static final class SuccessfulProcess extends Process {

    private final OutputStream outputStream = new ByteArrayOutputStream();

    @Override
    public OutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Process destroyForcibly() {
      return this;
    }
  }

  private static final class FailingPromptProcess extends Process {

    private final OutputStream outputStream = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("stdin closed");
      }
    };

    @Override
    public OutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Process destroyForcibly() {
      return this;
    }
  }

  private static final class RecordingInterruptedProcess extends Process {

    private final OutputStream outputStream = new ByteArrayOutputStream();
    private boolean destroyed;

    @Override
    public OutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
      throw new InterruptedException("interrupted");
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      throw new InterruptedException("interrupted");
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Process destroyForcibly() {
      destroyed = true;
      return this;
    }
  }
}

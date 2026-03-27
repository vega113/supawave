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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.junit.Test;

public final class ChangelogProviderTest {
  @Test
  public void loadsEntriesAndLatestMetadataFromJsonFile() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider");
    Path changelogFile = tempDir.resolve("changelog.json");
    Files.writeString(
        changelogFile,
        "[{\"version\":\"2026-03-27\",\"date\":\"2026-03-27\",\"title\":\"Changelog System\","
            + "\"summary\":\"You can now see what's new after each deploy.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]},"
            + "{\"version\":\"2026-03-20\",\"date\":\"2026-03-20\",\"title\":\"Public Waves\","
            + "\"summary\":\"Public wave sharing is now available.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Shared public waves\"]}]}]",
        StandardCharsets.UTF_8);

    ChangelogProvider provider = new ChangelogProvider(changelogFile);

    JSONArray entries = provider.getEntries();
    assertEquals(2, entries.length());
    assertEquals("2026-03-27", provider.getLatestVersion());
    assertEquals("Changelog System", provider.getLatestTitle());
    assertEquals("You can now see what's new after each deploy.", provider.getLatestSummary());
  }

  @Test
  public void returnsEmptyStateWhenJsonIsMalformed() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-bad");
    Path changelogFile = tempDir.resolve("changelog.json");
    Files.writeString(changelogFile, "{not-json", StandardCharsets.UTF_8);

    ChangelogProvider provider = new ChangelogProvider(changelogFile);

    assertEquals(0, provider.getEntries().length());
    assertNull(provider.getLatestVersion());
    assertNull(provider.getLatestTitle());
    assertNull(provider.getLatestSummary());
  }

  @Test
  public void configPathLoadsEntriesWithoutDependingOnWorkingDirectory() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-config");
    Path changelogFile = tempDir.resolve("custom-changelog.json");
    Path configFile = tempDir.resolve("application.conf");
    Files.writeString(
        changelogFile,
        "[{\"version\":\"2026-03-27\",\"date\":\"2026-03-27\",\"title\":\"Changelog System\","
            + "\"summary\":\"You can now see what's new after each deploy.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]}]",
        StandardCharsets.UTF_8);
    Files.writeString(configFile, "core.changelog_path=\"custom-changelog.json\"", StandardCharsets.UTF_8);

    String previousConfigPath = System.getProperty("wave.server.config");
    System.setProperty("wave.server.config", configFile.toString());
    try {
      Config config = ConfigFactory.parseMap(
          java.util.Map.of("core.changelog_path", "custom-changelog.json"));

      ChangelogProvider provider = new ChangelogProvider(config);

      assertEquals(1, provider.getEntries().length());
      assertEquals("2026-03-27", provider.getLatestVersion());
      assertEquals("Changelog System", provider.getLatestTitle());
      assertEquals("You can now see what's new after each deploy.", provider.getLatestSummary());
    } finally {
      if (previousConfigPath == null) {
        System.clearProperty("wave.server.config");
      } else {
        System.setProperty("wave.server.config", previousConfigPath);
      }
    }
  }

  @Test
  public void defaultProviderLoadsClasspathChangelogFromDifferentWorkingDirectory() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-cwd");
    Process process =
        new ProcessBuilder(
                javaBinary(),
                "-cp",
                absoluteClassPath(),
                ChangelogProviderLaunchProbe.class.getName())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();

    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertEquals(0, exitCode);
    assertTrue(output, output.contains("2026-03-27"));
    assertTrue(output, output.contains("Search and Discovery"));
  }

  private static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  private static String absoluteClassPath() {
    StringBuilder classPath = new StringBuilder();
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      if (classPath.length() > 0) {
        classPath.append(File.pathSeparator);
      }
      classPath.append(Path.of(entry).toAbsolutePath().normalize());
    }
    return classPath.toString();
  }

  private static String readProcessOutput(Process process) throws Exception {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      while (line != null) {
        output.append(line).append(System.lineSeparator());
        line = reader.readLine();
      }
    }
    return output.toString();
  }
}

final class ChangelogProviderLaunchProbe {
  public static void main(String[] args) {
    ChangelogProvider provider = new ChangelogProvider();
    if (provider.getEntries().length() == 0) {
      throw new IllegalStateException("Expected changelog entries to load from the classpath");
    }
    System.out.println(provider.getLatestVersion());
    System.out.println(provider.getLatestTitle());
  }
}

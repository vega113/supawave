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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.junit.Test;

public final class ChangelogProviderTest {
  @Test
  public void loadsEntriesAndLatestMetadataFromJsonFile() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider");
    Path changelogFile = tempDir.resolve("changelog.json");
    Files.writeString(
        changelogFile,
        sampleEntriesJson(),
        StandardCharsets.UTF_8);

    ChangelogProvider provider = new ChangelogProvider(changelogFile);

    JSONArray entries = provider.getEntries();
    assertEquals(3, entries.length());
    assertEquals("2026-03-27-unread-only-search-filter", provider.getCurrentReleaseId());
    assertEquals("Unread-Only Search Filter", provider.getLatestTitle());
    assertEquals(
        "You can now filter the wave list down to waves with unread blips only.",
        provider.getLatestSummary());
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
        sampleEntriesJson(),
        StandardCharsets.UTF_8);
    Files.writeString(
        configFile,
        "core.changelog_path=\"custom-changelog.json\"",
        StandardCharsets.UTF_8);

    String previousConfigPath = System.getProperty("wave.server.config");
    System.setProperty("wave.server.config", configFile.toString());
    try {
      Config config =
          ConfigFactory.parseMap(java.util.Map.of("core.changelog_path", "custom-changelog.json"));

      ChangelogProvider provider = new ChangelogProvider(config);

      assertEquals(3, provider.getEntries().length());
      assertEquals("2026-03-27-unread-only-search-filter", provider.getCurrentReleaseId());
      assertEquals("Unread-Only Search Filter", provider.getLatestTitle());
    } finally {
      if (previousConfigPath == null) {
        System.clearProperty("wave.server.config");
      } else {
        System.setProperty("wave.server.config", previousConfigPath);
      }
    }
  }

  @Test
  public void defaultProviderLoadsClasspathChangelogFromDifferentWorkingDirectory()
      throws Exception {
    ChangelogProvider expectedProvider = new ChangelogProvider();
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
    assertNotNull(expectedProvider.getLatestVersion());
    assertNotNull(expectedProvider.getLatestTitle());
    assertTrue(output, output.contains(expectedProvider.getLatestVersion()));
    assertTrue(output, output.contains(expectedProvider.getLatestTitle()));
  }

  @Test
  public void bundledAndFallbackChangelogFilesStayAligned() {
    ChangelogProvider classpathProvider = new ChangelogProvider();
    ChangelogProvider fallbackProvider = new ChangelogProvider(fallbackChangelogPath());

    assertNotNull(classpathProvider.getLatestEntry());
    assertNotNull(fallbackProvider.getLatestEntry());
    assertEquals(
        classpathProvider.getEntries().toString(),
        fallbackProvider.getEntries().toString());
  }

  @Test
  public void resolvesFallbackChangelogPathFromModuleRoot() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-module-root");
    Path moduleRoot = tempDir.resolve("wave");
    Path changelogFile = moduleRoot.resolve("config").resolve("changelog.json");

    Files.createDirectories(changelogFile.getParent());
    Files.writeString(changelogFile, sampleEntriesJson(), StandardCharsets.UTF_8);

    assertEquals(changelogFile, resolveFallbackChangelogPath(moduleRoot));
  }

  @Test
  public void resolvesFallbackChangelogPathFromRepoRoot() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-repo-root");
    Path changelogFile = tempDir.resolve("wave").resolve("config").resolve("changelog.json");

    Files.createDirectories(changelogFile.getParent());
    Files.writeString(changelogFile, sampleEntriesJson(), StandardCharsets.UTF_8);

    assertEquals(changelogFile, resolveFallbackChangelogPath(tempDir));
  }

  @Test
  public void resolvesExactReleaseRangeBetweenOlderClientAndCurrentRelease() {
    ChangelogProvider provider = new ChangelogProvider(sampleEntries());

    ChangelogProvider.ReleaseRange range =
        provider.getReleaseRange(
            "2026-03-27-changelog-system",
            "2026-03-27-unread-only-search-filter");

    assertEquals("exact", range.getStatus());
    assertEquals(2, range.getEntries().length());
    assertEquals(
        "2026-03-27-unread-only-search-filter",
        range.getEntries().getJSONObject(0).getString("releaseId"));
    assertEquals(
        "2026-03-27-restore-last-wave",
        range.getEntries().getJSONObject(1).getString("releaseId"));
  }

  @Test
  public void resolvesPartialRangeWhenClientReleaseIsUnknown() {
    ChangelogProvider provider = new ChangelogProvider(sampleEntries());

    ChangelogProvider.ReleaseRange range =
        provider.getReleaseRange("unknown-release", "2026-03-27-unread-only-search-filter");

    assertEquals("partial", range.getStatus());
    assertEquals(1, range.getEntries().length());
    assertEquals(
        "2026-03-27-unread-only-search-filter",
        range.getEntries().getJSONObject(0).getString("releaseId"));
  }

  @Test
  public void returnsNonForwardWhenClientReleaseIsNewerThanCurrentRelease() {
    ChangelogProvider provider = new ChangelogProvider(sampleEntries());

    ChangelogProvider.ReleaseRange range =
        provider.getReleaseRange(
            "2026-03-27-unread-only-search-filter",
            "2026-03-27-changelog-system");

    assertEquals("non_forward", range.getStatus());
    assertEquals(0, range.getEntries().length());
  }

  @Test
  public void rejectsEntriesMissingReleaseId() {
    ChangelogProvider provider =
        new ChangelogProvider(
            new JSONArray(
                "[{\"date\":\"2026-03-27\",\"title\":\"Broken\",\"summary\":\"Broken entry\","
                    + "\"sections\":[{\"type\":\"feature\",\"items\":[\"No release key\"]}]}]"));

    assertEquals(0, provider.getEntries().length());
    assertNull(provider.getCurrentReleaseId());
  }

  @Test
  public void trimsReleaseIdsBeforePersistingAndMatchingRanges() {
    ChangelogProvider provider =
        new ChangelogProvider(
            new JSONArray(
                "[{\"releaseId\":\" 2026-03-27-unread-only-search-filter \","
                    + "\"version\":\"2026-03-27.403\",\"date\":\"2026-03-27\","
                    + "\"title\":\"Unread-Only Search Filter\","
                    + "\"summary\":\"You can now filter the wave list down to waves with unread blips only.\","
                    + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Added the unread:true search filter\"]}]}]"));

    assertEquals("2026-03-27-unread-only-search-filter", provider.getCurrentReleaseId());
    assertEquals(
        "2026-03-27-unread-only-search-filter",
        provider.getEntries().getJSONObject(0).getString("releaseId"));
    assertEquals(
        "same_release",
        provider
            .getReleaseRange(
                "2026-03-27-unread-only-search-filter",
                "2026-03-27-unread-only-search-filter")
            .getStatus());
  }

  @Test
  public void doesNotFallbackToClasspathWhenConfiguredChangelogIsInvalid() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-invalid-config");
    Path changelogFile = tempDir.resolve("broken-changelog.json");
    Path configFile = tempDir.resolve("application.conf");
    Files.writeString(
        changelogFile,
        "[{\"date\":\"2026-03-27\",\"title\":\"Broken\",\"summary\":\"Broken entry\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"No release key\"]}]}]",
        StandardCharsets.UTF_8);
    Files.writeString(
        configFile,
        "core.changelog_path=\"broken-changelog.json\"",
        StandardCharsets.UTF_8);

    String previousConfigPath = System.getProperty("wave.server.config");
    System.setProperty("wave.server.config", configFile.toString());
    try {
      Config config =
          ConfigFactory.parseMap(java.util.Map.of("core.changelog_path", "broken-changelog.json"));

      ChangelogProvider provider = new ChangelogProvider(config);

      assertEquals(0, provider.getEntries().length());
      assertNull(provider.getCurrentReleaseId());
      assertNull(provider.getLatestTitle());
    } finally {
      if (previousConfigPath == null) {
        System.clearProperty("wave.server.config");
      } else {
        System.setProperty("wave.server.config", previousConfigPath);
      }
    }
  }

  private static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  static Path fallbackChangelogPath() {
    return resolveFallbackChangelogPath(Path.of("").toAbsolutePath().normalize());
  }

  static Path resolveFallbackChangelogPath(Path startDirectory) {
    Path currentDirectory = startDirectory.toAbsolutePath().normalize();
    while (currentDirectory != null) {
      Path moduleConfig = currentDirectory.resolve("config").resolve("changelog.json");
      if (Files.exists(moduleConfig)) {
        return moduleConfig;
      }
      Path repoConfig = currentDirectory.resolve("wave").resolve("config").resolve("changelog.json");
      if (Files.exists(repoConfig)) {
        return repoConfig;
      }
      currentDirectory = currentDirectory.getParent();
    }
    return startDirectory
        .toAbsolutePath()
        .normalize()
        .resolve("wave")
        .resolve("config")
        .resolve("changelog.json");
  }

  private static String absoluteClassPath() {
    StringBuilder classPath = new StringBuilder();
    appendClassPathEntry(classPath, codeSourcePath(ChangelogProviderTest.class));
    appendClassPathEntry(classPath, codeSourcePath(ChangelogProvider.class));
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      appendClassPathEntry(classPath, Path.of(entry).toAbsolutePath().normalize());
    }
    return classPath.toString();
  }

  private static Path codeSourcePath(Class<?> clazz) {
    try {
      return Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toAbsolutePath()
          .normalize();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Failed to resolve code source for " + clazz.getName(), e);
    }
  }

  private static void appendClassPathEntry(StringBuilder classPath, Path entry) {
    String normalizedEntry = entry.toString();
    if (classPath.indexOf(normalizedEntry) >= 0) {
      return;
    }
    if (classPath.length() > 0) {
      classPath.append(File.pathSeparator);
    }
    classPath.append(normalizedEntry);
  }

  private static String readProcessOutput(Process process) throws Exception {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      while (line != null) {
        output.append(line).append(System.lineSeparator());
        line = reader.readLine();
      }
    }
    return output.toString();
  }

  private static JSONArray sampleEntries() {
    return new JSONArray(sampleEntriesJson());
  }

  private static String sampleEntriesJson() {
    return "[{\"releaseId\":\"2026-03-27-unread-only-search-filter\","
        + "\"version\":\"2026-03-27.403\",\"date\":\"2026-03-27\","
        + "\"title\":\"Unread-Only Search Filter\","
        + "\"summary\":\"You can now filter the wave list down to waves with unread blips only.\","
        + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Added the unread:true search filter\"]}]},"
        + "{\"releaseId\":\"2026-03-27-restore-last-wave\","
        + "\"version\":\"2026-03-27.394\",\"date\":\"2026-03-27\","
        + "\"title\":\"Restore Last Opened Wave\","
        + "\"summary\":\"SupaWave can reopen your last wave on login.\","
        + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Restores the last opened wave and focuses the last unread blip\"]}]},"
        + "{\"releaseId\":\"2026-03-27-changelog-system\","
        + "\"version\":\"2026-03-27.383\",\"date\":\"2026-03-27\","
        + "\"title\":\"Changelog System\","
        + "\"summary\":\"You can now see what's new after each deploy.\","
        + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]}]";
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

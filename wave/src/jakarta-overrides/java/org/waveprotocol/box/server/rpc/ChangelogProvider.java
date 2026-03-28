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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.waveprotocol.wave.util.logging.Log;

@Singleton
public final class ChangelogProvider {
  public static final String STATUS_EXACT = "exact";
  public static final String STATUS_PARTIAL = "partial";
  public static final String STATUS_CURRENT_ONLY = "current_only";
  public static final String STATUS_SAME_RELEASE = "same_release";
  public static final String STATUS_NON_FORWARD = "non_forward";
  public static final String STATUS_UNMAPPED = "unmapped";

  private static final Log LOG = Log.get(ChangelogProvider.class);

  private final JSONArray entries;
  private final String currentReleaseId;
  private final String latestVersion;
  private final String latestTitle;
  private final String latestSummary;

  @Inject
  public ChangelogProvider(Config config) {
    this(loadDefaultEntries(config));
  }

  ChangelogProvider(Path changelogPath) {
    this(loadEntries(changelogPath));
  }

  public ChangelogProvider() {
    this(loadDefaultEntries(null));
  }

  public ChangelogProvider(JSONArray entries) {
    this.entries = sanitizeEntries(entries);
    JSONObject latestEntry = this.entries.length() > 0 ? this.entries.optJSONObject(0) : null;
    this.currentReleaseId = latestEntry != null ? latestEntry.optString("releaseId", null) : null;
    this.latestVersion = latestEntry != null ? latestEntry.optString("version", null) : null;
    this.latestTitle = latestEntry != null ? latestEntry.optString("title", null) : null;
    this.latestSummary = latestEntry != null ? latestEntry.optString("summary", null) : null;
  }

  public JSONArray getEntries() {
    return new JSONArray(entries.toString());
  }

  public JSONObject getLatestEntry() {
    JSONObject latestEntry = entries.length() > 0 ? entries.optJSONObject(0) : null;
    return latestEntry != null ? new JSONObject(latestEntry.toString()) : null;
  }

  public JSONObject getCurrentReleaseEntry() {
    return getLatestEntry();
  }

  public String getCurrentReleaseId() {
    return currentReleaseId;
  }

  public String getLatestVersion() {
    return latestVersion;
  }

  public String getLatestTitle() {
    return latestTitle;
  }

  public String getLatestSummary() {
    return latestSummary;
  }

  public JSONObject getReleaseEntry(String releaseId) {
    int releaseIndex = indexOfReleaseId(releaseId);
    if (releaseIndex < 0) {
      return null;
    }
    JSONObject entry = entries.optJSONObject(releaseIndex);
    return entry != null ? new JSONObject(entry.toString()) : null;
  }

  public ReleaseRange getReleaseRange(String sinceReleaseId, String targetReleaseId) {
    if (targetReleaseId == null || targetReleaseId.isBlank()) {
      return new ReleaseRange(STATUS_UNMAPPED, new JSONArray());
    }
    int targetIndex = indexOfReleaseId(targetReleaseId);
    if (targetIndex < 0) {
      return new ReleaseRange(STATUS_UNMAPPED, new JSONArray());
    }
    if (sinceReleaseId == null || sinceReleaseId.isBlank()) {
      return new ReleaseRange(STATUS_CURRENT_ONLY, copyEntries(targetIndex, targetIndex + 1));
    }
    int sinceIndex = indexOfReleaseId(sinceReleaseId);
    if (sinceIndex < 0) {
      return new ReleaseRange(STATUS_PARTIAL, copyEntries(targetIndex, targetIndex + 1));
    }
    if (sinceIndex == targetIndex) {
      return new ReleaseRange(STATUS_SAME_RELEASE, new JSONArray());
    }
    if (sinceIndex < targetIndex) {
      return new ReleaseRange(STATUS_NON_FORWARD, new JSONArray());
    }
    return new ReleaseRange(STATUS_EXACT, copyEntries(targetIndex, sinceIndex));
  }

  private int indexOfReleaseId(String releaseId) {
    if (releaseId == null || releaseId.isBlank()) {
      return -1;
    }
    String normalizedReleaseId = releaseId.trim();
    for (int i = 0; i < entries.length(); i++) {
      JSONObject entry = entries.optJSONObject(i);
      if (entry != null && normalizedReleaseId.equals(entry.optString("releaseId"))) {
        return i;
      }
    }
    return -1;
  }

  private JSONArray copyEntries(int fromInclusive, int toExclusive) {
    JSONArray copy = new JSONArray();
    for (int i = fromInclusive; i < toExclusive && i < entries.length(); i++) {
      JSONObject entry = entries.optJSONObject(i);
      if (entry != null) {
        copy.put(new JSONObject(entry.toString()));
      }
    }
    return copy;
  }

  private static JSONArray loadDefaultEntries(Config config) {
    ConfiguredEntries configuredEntries = loadConfiguredEntries(config);
    if (configuredEntries.hasConfiguredPath()) {
      return configuredEntries.entries();
    }
    JSONArray loadedEntries = loadEntriesFromClasspath("config/changelog.json");
    if (loadedEntries.length() == 0) {
      loadedEntries = loadEntries(Paths.get("config", "changelog.json"));
    }
    return loadedEntries;
  }

  private static ConfiguredEntries loadConfiguredEntries(Config config) {
    if (config == null || !config.hasPath("core.changelog_path")) {
      return new ConfiguredEntries(new JSONArray(), false);
    }
    Path configuredPath = resolveConfiguredPath(config.getString("core.changelog_path"));
    if (!Files.exists(configuredPath)) {
      LOG.warning("Changelog file not found at " + configuredPath.toAbsolutePath());
      return new ConfiguredEntries(new JSONArray(), false);
    }
    return new ConfiguredEntries(loadEntries(configuredPath), true);
  }

  private static Path resolveConfiguredPath(String changelogPath) {
    Path configuredPath = Paths.get(changelogPath);
    if (configuredPath.isAbsolute()) {
      return configuredPath;
    }
    String serverConfigPath = System.getProperty("wave.server.config");
    if (serverConfigPath != null && !serverConfigPath.isBlank()) {
      Path configDirectory = Paths.get(serverConfigPath).toAbsolutePath().getParent();
      if (configDirectory != null) {
        return configDirectory.resolve(configuredPath).normalize();
      }
    }
    return configuredPath.toAbsolutePath();
  }

  private static JSONArray loadEntriesFromClasspath(String resourceName) {
    JSONArray loadedEntries = new JSONArray();
    try (InputStream inputStream =
        ChangelogProvider.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (inputStream != null) {
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        loadedEntries = sanitizeEntries(new JSONArray(json));
      } else {
        LOG.warning("Changelog resource not found at " + resourceName);
      }
    } catch (IOException | RuntimeException e) {
      LOG.warning("Failed to load changelog resource " + resourceName, e);
    }
    return loadedEntries;
  }

  private static JSONArray loadEntries(Path changelogPath) {
    JSONArray loadedEntries = new JSONArray();
    if (Files.exists(changelogPath)) {
      try {
        String json = Files.readString(changelogPath, StandardCharsets.UTF_8);
        loadedEntries = sanitizeEntries(new JSONArray(json));
      } catch (IOException | RuntimeException e) {
        LOG.warning("Failed to load changelog from " + changelogPath.toAbsolutePath(), e);
      }
    } else {
      LOG.warning("Changelog file not found at " + changelogPath.toAbsolutePath());
    }
    return loadedEntries;
  }

  private static JSONArray sanitizeEntries(JSONArray rawEntries) {
    JSONArray sanitizedEntries = new JSONArray();
    Set<String> seenReleaseIds = new HashSet<>();
    for (int i = 0; i < rawEntries.length(); i++) {
      JSONObject rawEntry = rawEntries.optJSONObject(i);
      if (rawEntry == null || !isValidEntry(rawEntry, seenReleaseIds)) {
        return new JSONArray();
      }
      JSONObject sanitizedEntry = new JSONObject(rawEntry.toString());
      sanitizedEntry.put("releaseId", sanitizedEntry.getString("releaseId").trim());
      sanitizedEntries.put(sanitizedEntry);
    }
    return sanitizedEntries;
  }

  private static boolean isValidEntry(JSONObject entry, Set<String> seenReleaseIds) {
    String releaseId = entry.optString("releaseId", "").trim();
    String date = entry.optString("date", "").trim();
    String title = entry.optString("title", "").trim();
    String summary = entry.optString("summary", "").trim();
    JSONArray sections = entry.optJSONArray("sections");
    if (releaseId.isEmpty() || date.isEmpty() || title.isEmpty() || summary.isEmpty()
        || sections == null || sections.length() == 0) {
      LOG.warning("Changelog entry is missing required fields: " + entry);
      return false;
    }
    if (!seenReleaseIds.add(releaseId)) {
      LOG.warning("Duplicate changelog releaseId " + releaseId);
      return false;
    }
    return true;
  }

  public static final class ReleaseRange {
    private final String status;
    private final JSONArray entries;

    ReleaseRange(String status, JSONArray entries) {
      this.status = status;
      this.entries = new JSONArray(entries.toString());
    }

    public String getStatus() {
      return status;
    }

    public JSONArray getEntries() {
      return new JSONArray(entries.toString());
    }
  }

  private record ConfiguredEntries(JSONArray entries, boolean hasConfiguredPath) {}
}

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

package org.waveprotocol.box.server.persistence.file;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotRecord;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-based implementation of {@link SnapshotStore}.
 *
 * <p>Stores snapshots as protobuf files under:
 * {@code _snapshots/{encoded-wave-id}/{encoded-wavelet-id}/v{version}.snapshot}
 */
public class FileSnapshotStore implements SnapshotStore {

  private static final Log LOG = Log.get(FileSnapshotStore.class);

  private static final String SNAPSHOT_SUFFIX = ".snapshot";
  private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.snapshot");
  private static final int MAX_SNAPSHOTS_PER_WAVELET = 3;

  private final String basePath;

  @Inject
  public FileSnapshotStore(Config config) {
    this.basePath = config.getString("core.delta_store_directory") + "/../_snapshots";
  }

  /** Constructor for testing with explicit base path. */
  public FileSnapshotStore(String basePath) {
    this.basePath = basePath;
  }

  private File waveletDir(WaveletName waveletName) {
    String wavePart = FileUtils.waveIdToPathSegment(waveletName.waveId);
    String waveletPart = FileUtils.waveletIdToPathSegment(waveletName.waveletId);
    return new File(new File(basePath, wavePart), waveletPart);
  }

  @Override
  public SnapshotRecord getLatestSnapshot(WaveletName waveletName) throws PersistenceException {
    File dir = waveletDir(waveletName);
    if (!dir.exists()) {
      return null;
    }

    File[] files = dir.listFiles((d, name) -> VERSION_PATTERN.matcher(name).matches());
    if (files == null || files.length == 0) {
      return null;
    }

    // Find file with highest version
    File latest = null;
    long latestVersion = -1;
    for (File f : files) {
      Matcher m = VERSION_PATTERN.matcher(f.getName());
      if (m.matches()) {
        long ver = Long.parseLong(m.group(1));
        if (ver > latestVersion) {
          latestVersion = ver;
          latest = f;
        }
      }
    }

    if (latest == null) {
      return null;
    }

    try {
      byte[] data = Files.readAllBytes(latest.toPath());
      return new SnapshotRecord(latestVersion, data);
    } catch (IOException e) {
      throw new PersistenceException(
          "Failed to read snapshot file " + latest.getAbsolutePath(), e);
    }
  }

  @Override
  public void storeSnapshot(WaveletName waveletName, byte[] snapshotData, long version)
      throws PersistenceException {
    File dir = waveletDir(waveletName);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new PersistenceException("Failed to create snapshot directory: " + dir);
    }

    File targetFile = new File(dir, "v" + version + SNAPSHOT_SUFFIX);
    // Atomic write: write to temp file then rename
    File tempFile = new File(dir, "v" + version + ".snapshot.tmp");
    try {
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        fos.write(snapshotData);
        fos.getFD().sync();
      }
      // Atomic move with replace — prefer ATOMIC_MOVE, fall back to REPLACE_EXISTING
      try {
        Files.move(tempFile.toPath(), targetFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      } catch (java.nio.file.AtomicMoveNotSupportedException amex) {
        Files.move(tempFile.toPath(), targetFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      tempFile.delete();
      throw new PersistenceException("Failed to write snapshot file " + targetFile, e);
    }

    // Prune old snapshots, keeping the most recent MAX_SNAPSHOTS_PER_WAVELET
    pruneOldSnapshots(dir);
  }

  @Override
  public void deleteSnapshots(WaveletName waveletName) throws PersistenceException {
    File dir = waveletDir(waveletName);
    if (!dir.exists()) {
      return;
    }

    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (!f.delete()) {
          LOG.warning("Failed to delete snapshot file: " + f.getAbsolutePath());
        }
      }
    }
    dir.delete();
  }

  private void pruneOldSnapshots(File dir) {
    File[] files = dir.listFiles((d, name) -> VERSION_PATTERN.matcher(name).matches());
    if (files == null || files.length <= MAX_SNAPSHOTS_PER_WAVELET) {
      return;
    }

    // Sort by version ascending
    Arrays.sort(files, Comparator.comparingLong(f -> {
      Matcher m = VERSION_PATTERN.matcher(f.getName());
      return m.matches() ? Long.parseLong(m.group(1)) : 0;
    }));

    // Delete all but the last MAX_SNAPSHOTS_PER_WAVELET
    int toDelete = files.length - MAX_SNAPSHOTS_PER_WAVELET;
    for (int i = 0; i < toDelete; i++) {
      if (!files[i].delete()) {
        LOG.warning("Failed to prune old snapshot: " + files[i].getAbsolutePath());
      }
    }
  }
}

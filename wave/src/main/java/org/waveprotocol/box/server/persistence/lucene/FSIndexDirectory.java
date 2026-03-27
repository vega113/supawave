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
package org.waveprotocol.box.server.persistence.lucene;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.file.FileUtils;
import org.waveprotocol.box.server.waveserver.IndexException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * File system based {@link IndexDirectory}.
 *
 * @author A. Kaplanov
 */
public class FSIndexDirectory implements IndexDirectory {

  private final Directory directory;

  @Inject
  public FSIndexDirectory(Config config) {
    this(config, "core.index_directory");
  }

  protected FSIndexDirectory(Config config, String configPath) {
    this.directory = openDirectory(config.getString(configPath));
  }

  private static Directory openDirectory(String directoryName) {
    Path path;
    try {
      path = FileUtils.createDirIfNotExists(directoryName, "").toPath();
    } catch (PersistenceException e) {
      throw new IndexException("Cannot create index directory " + directoryName, e);
    }
    try {
      return FSDirectory.open(path);
    } catch (IOException e) {
      throw new IndexException("Cannot open index directory " + directoryName, e);
    }
  }

  @Override
  public Directory getDirectory() throws IndexException {
    return directory;
  }
}

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
package org.eclipse.jetty.server.session;

import java.io.File;

/**
 * Minimal compile-time stub of Jetty's FileSessionDataStore. Provides enough
 * API surface for configuration in ServerModule and adds simple helpers for
 * directory validation and session file resolution. Not used at runtime.
 */
public class FileSessionDataStore {
  private File storeDir;

  /** Sets the directory used to store session data files. */
  public void setStoreDir(File dir) { this.storeDir = dir; }

  /** Returns the configured store directory (may be null). */
  public File getStoreDir() { return storeDir; }

  /**
   * Validates that the store directory exists or can be created and is writable.
   * Returns true if usable.
   */
  public boolean validateStoreDir() {
    if (storeDir == null) return false;
    if (!storeDir.exists() && !storeDir.mkdirs()) return false;
    return storeDir.isDirectory() && storeDir.canWrite();
  }

  /** Resolves a session data file path for the given session id. */
  public File resolveSessionFile(String sessionId) {
    if (storeDir == null) return null;
    return new File(storeDir, sessionId + ".session");
  }
}

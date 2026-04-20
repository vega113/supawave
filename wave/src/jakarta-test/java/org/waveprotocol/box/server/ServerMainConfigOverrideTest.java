/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server;

import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class ServerMainConfigOverrideTest {
  @Test
  public void loadCoreConfigHonorsWaveServerConfigOverride() throws Exception {
    Path tempDir = Files.createTempDirectory("server-main-config-override");
    Path applicationConfig = tempDir.resolve("application.conf");
    Files.writeString(
        applicationConfig,
        "ui.j2cl_root_bootstrap_enabled = true\n",
        StandardCharsets.UTF_8);

    String previousConfigPath = System.getProperty("wave.server.config");
    System.setProperty("wave.server.config", applicationConfig.toString());
    try {
      Config config = ServerMain.loadCoreConfig();

      assertTrue(config.getBoolean("ui.j2cl_root_bootstrap_enabled"));
    } finally {
      if (previousConfigPath == null) {
        System.clearProperty("wave.server.config");
      } else {
        System.setProperty("wave.server.config", previousConfigPath);
      }
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void loadCoreConfigFailsFastWhenWaveServerConfigIsMissing() {
    String previousConfigPath = System.getProperty("wave.server.config");
    System.setProperty("wave.server.config", "/tmp/does-not-exist-issue-923.application.conf");
    try {
      ServerMain.loadCoreConfig();
    } finally {
      if (previousConfigPath == null) {
        System.clearProperty("wave.server.config");
      } else {
        System.setProperty("wave.server.config", previousConfigPath);
      }
    }
  }
}

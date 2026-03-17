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
package org.waveprotocol.box.server;

import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

/** Validation tests for applier-related configuration in ServerMain.applyFragmentsConfig. */
public final class ServerMainApplierConfigValidationTest {

  @Test(expected = RuntimeException.class)
  public void invalidApplierWarnMsNegativeFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.applier.warnMs=-5");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidApplierWarnMsTypeFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.applier.warnMs=\"fast\"");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidApplierImplValueFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.applier.impl=\"fancy\"");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test(expected = RuntimeException.class)
  public void invalidApplierImplTypeFailsStartup() {
    Config cfg = ConfigFactory.parseString("wave.fragments.applier.impl=123");
    ServerMain.applyFragmentsConfig(cfg);
  }

  @Test
  public void validApplierImplsPass() {
    String[] values = {"noop", "skeleton", "real", "NOOP", " Real "};
    for (String v : values) {
      Config cfg = ConfigFactory.parseString("wave.fragments.applier.impl=\"" + v + "\"");
      ServerMain.applyFragmentsConfig(cfg);
    }
    assertTrue(true);
  }
}


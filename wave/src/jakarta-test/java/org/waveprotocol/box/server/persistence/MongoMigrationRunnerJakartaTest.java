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

package org.waveprotocol.box.server.persistence;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;

public final class MongoMigrationRunnerJakartaTest extends TestCase {

  public void testRunsMigrationsForMongoBackedV4Config() {
    RecordingMongoMigrationRunnerFactory factory = new RecordingMongoMigrationRunnerFactory();
    PersistenceModule module = new PersistenceModule(mongoBackedConfig(), factory);

    module.runMongoMigrationsIfNeeded();

    assertEquals(1, factory.createCalls);
    assertEquals(1, factory.runner.runCalls);
  }

  public void testSkipsMigrationsForFileBackedConfigEvenWhenMongoDriverIsV4() {
    RecordingMongoMigrationRunnerFactory factory = new RecordingMongoMigrationRunnerFactory();
    PersistenceModule module = new PersistenceModule(fileBackedConfigWithMongoDriverV4(), factory);

    module.runMongoMigrationsIfNeeded();

    assertEquals(0, factory.createCalls);
    assertEquals(0, factory.runner.runCalls);
  }

  public void testRunsMigrationsOnlyOncePerModuleInstance() {
    RecordingMongoMigrationRunnerFactory factory = new RecordingMongoMigrationRunnerFactory();
    PersistenceModule module = new PersistenceModule(mongoBackedConfig(), factory);

    module.runMongoMigrationsIfNeeded();
    module.runMongoMigrationsIfNeeded();

    assertEquals(1, factory.createCalls);
    assertEquals(1, factory.runner.runCalls);
  }

  private static Config mongoBackedConfig() {
    return ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"mongodb\"\n"
            + "  attachment_store_type = \"mongodb\"\n"
            + "  account_store_type = \"mongodb\"\n"
            + "  contact_store_type = \"mongodb\"\n"
            + "  delta_store_type = \"mongodb\"\n"
            + "  mongodb_host = \"mongo\"\n"
            + "  mongodb_port = 27017\n"
            + "  mongodb_database = \"wiab\"\n"
            + "  mongodb_driver = \"v4\"\n"
            + "}\n");
  }

  private static Config fileBackedConfigWithMongoDriverV4() {
    return ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"file\"\n"
            + "  attachment_store_type = \"disk\"\n"
            + "  account_store_type = \"file\"\n"
            + "  contact_store_type = \"memory\"\n"
            + "  delta_store_type = \"file\"\n"
            + "  mongodb_host = \"mongo\"\n"
            + "  mongodb_port = 27017\n"
            + "  mongodb_database = \"wiab\"\n"
            + "  mongodb_driver = \"v4\"\n"
            + "}\n");
  }

  private static final class RecordingMongoMigrationRunnerFactory
      implements MongoMigrationRunnerFactory {
    private int createCalls;
    private final RecordingMongoMigrationRunner runner = new RecordingMongoMigrationRunner();

    @Override
    public MongoMigrationRunner create(MongoMigrationConfig config) {
      createCalls++;
      return runner;
    }
  }

  private static final class RecordingMongoMigrationRunner implements MongoMigrationRunner {
    private int runCalls;

    @Override
    public void run() {
      runCalls++;
    }
  }
}

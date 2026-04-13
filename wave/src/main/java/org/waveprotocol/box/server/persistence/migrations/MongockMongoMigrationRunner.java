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

package org.waveprotocol.box.server.persistence.migrations;

import com.mongodb.ReadPreference;
import com.mongodb.client.MongoDatabase;
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import io.mongock.runner.core.executor.MongockRunner;
import io.mongock.runner.standalone.MongockStandalone;
import org.bson.Document;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;
import org.waveprotocol.box.server.persistence.MongoMigrationRunner;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DbProvider;
import org.waveprotocol.box.server.persistence.migrations.changesets.BaselineMongoSchema_001;
import org.waveprotocol.box.server.persistence.migrations.changesets.DeltaAppliedVersionUniqueIndex_002;

/**
 * Runs the Mongock startup migration pass for MongoDB v4-backed deployments.
 */
final class MongockMongoMigrationRunner implements MongoMigrationRunner {

  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(MongockMongoMigrationRunner.class.getName());
  private static final String EXECUTION_ID = "wave-startup";

  private final MongoMigrationConfig config;

  MongockMongoMigrationRunner(MongoMigrationConfig config) {
    this.config = config;
  }

  @Override
  public void run() {
    if (!config.isMongoV4Driver()) {
      throw new IllegalStateException(
          "Mongo migrations require mongodb_driver=v4, found " + config.getMongoDriver());
    }

    try (Mongo4DbProvider provider = new Mongo4DbProvider(
        config.getHost(),
        config.getPort(),
        config.getDatabase(),
        config.getUsername(),
        config.getPassword())) {
      MongoDatabase database = provider.provideMongoDatabase();
      MongoSync4Driver driver =
          MongoSync4Driver.withDefaultLock(provider.provideMongoClient(), config.getDatabase());
      configureDriverDefaults(driver, database);

      MongockRunner runner = MongockStandalone.builder()
          .setDriver(driver)
          .setExecutionId(EXECUTION_ID)
          .addMigrationClass(BaselineMongoSchema_001.class)
          .addMigrationClass(DeltaAppliedVersionUniqueIndex_002.class)
          .addDependency(MongoMigrationConfig.class, config)
          .addDependency(MongoDatabase.class, database)
          .buildRunner();

      LOG.info("Running Mongock Mongo schema migrations");
      runner.execute();
      LOG.info("Completed Mongock Mongo schema migrations");
    }
  }

  static void configureDriverDefaults(MongoSync4Driver driver, MongoDatabase database) {
    if (shouldDisableTransactions(database)) {
      driver.disableTransaction();
    }
    driver.setReadPreference(ReadPreference.primary());
  }

  static boolean shouldDisableTransactions(MongoDatabase database) {
    Document topology = database.runCommand(new Document("isMaster", 1));
    return !topology.containsKey("setName") && !"isdbgrid".equals(topology.getString("msg"));
  }
}

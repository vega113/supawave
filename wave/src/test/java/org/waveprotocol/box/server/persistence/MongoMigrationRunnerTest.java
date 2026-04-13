package org.waveprotocol.box.server.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MongoMigrationRunnerTest {

  @Test
  public void runsMigrationsWhenMongoBackedV4PersistenceIsConfigured() {
    AtomicInteger factoryCalls = new AtomicInteger();
    AtomicInteger runCalls = new AtomicInteger();
    PersistenceModule module = new PersistenceModule(
        mongoBackedConfig(),
        migrationConfig -> {
          factoryCalls.incrementAndGet();
          assertTrue(migrationConfig.isMongoV4Driver());
          assertTrue(migrationConfig.usesMongoBackedCoreStore());
          return () -> runCalls.incrementAndGet();
        });

    module.runMongoMigrationsIfNeeded();

    assertEquals(1, factoryCalls.get());
    assertEquals(1, runCalls.get());
  }

  @Test
  public void skipsMigrationsWhenPersistenceIsNotMongoBacked() {
    AtomicInteger factoryCalls = new AtomicInteger();
    PersistenceModule module = new PersistenceModule(
        fileBackedConfig(),
        migrationConfig -> {
          factoryCalls.incrementAndGet();
          return () -> { throw new AssertionError("runner should not execute"); };
        });

    module.runMongoMigrationsIfNeeded();

    assertEquals(0, factoryCalls.get());
  }

  @Test
  public void skipsMigrationsWhenOnlyOptionalContactStoreUsesMongo() {
    AtomicInteger factoryCalls = new AtomicInteger();
    PersistenceModule module = new PersistenceModule(
        contactOnlyMongoConfig(),
        migrationConfig -> {
          factoryCalls.incrementAndGet();
          return () -> { throw new AssertionError("runner should not execute"); };
        });

    module.runMongoMigrationsIfNeeded();

    assertEquals(0, factoryCalls.get());
  }

  private static Config mongoBackedConfig() {
    return baseConfig().withFallback(ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"mongodb\"\n"
            + "  attachment_store_type = \"mongodb\"\n"
            + "  account_store_type = \"mongodb\"\n"
            + "  contact_store_type = \"mongodb\"\n"
            + "  delta_store_type = \"mongodb\"\n"
            + "  analytics_counters_enabled = true\n"
            + "  mongodb_driver = \"v4\"\n"
            + "}\n"));
  }

  private static Config fileBackedConfig() {
    return baseConfig().withFallback(ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"file\"\n"
            + "  attachment_store_type = \"disk\"\n"
            + "  account_store_type = \"file\"\n"
            + "  contact_store_type = \"memory\"\n"
            + "  delta_store_type = \"file\"\n"
            + "  analytics_counters_enabled = false\n"
            + "  mongodb_driver = \"v4\"\n"
            + "}\n"));
  }

  private static Config contactOnlyMongoConfig() {
    return baseConfig().withFallback(ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"file\"\n"
            + "  attachment_store_type = \"disk\"\n"
            + "  account_store_type = \"file\"\n"
            + "  contact_store_type = \"mongodb\"\n"
            + "  delta_store_type = \"file\"\n"
            + "  analytics_counters_enabled = false\n"
            + "  mongodb_driver = \"v4\"\n"
            + "}\n"));
  }

  private static Config baseConfig() {
    return ConfigFactory.parseString(
        "core {\n"
            + "  mongodb_host = \"localhost\"\n"
            + "  mongodb_port = 27017\n"
            + "  mongodb_database = \"wiab_test\"\n"
            + "  mongodb_username = \"\"\n"
            + "  mongodb_password = \"\"\n"
            + "}\n");
  }
}

package org.waveprotocol.box.server.persistence;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.Test;
import org.junit.Assume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoMigrationBaselineTest {
  private static final Logger LOG = LoggerFactory.getLogger(MongoMigrationBaselineTest.class);
  private static final DockerImageName MONGO_IMAGE =
      DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");

  private static final String DELTAS_COLLECTION = "deltas";
  private static final String SNAPSHOTS_COLLECTION = "snapshots";
  private static final String CONTACT_MESSAGES_COLLECTION = "contact_messages";
  private static final String ANALYTICS_COLLECTION = "analytics_hourly";
  private static final String CHANGELOG_COLLECTION = "mongockChangeLog";
  private static final String LOCK_COLLECTION = "mongockLock";
  private static final String APPLIED_VERSION_INDEX_NAME =
      "waveId_1_waveletId_1_transformed.appliedAtVersion_1";

  @Test
  public void emptyDatabaseReachesCanonicalMongoBaseline() {
    preferColimaIfDockerHostInvalid();

    MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE);
    PersistenceModule module = null;
    try {
      startOrSkip(mongo);
      module = new PersistenceModule(mongoBackedConfig(mongo));

      module.runMongoMigrationsIfNeeded();

      MongoDatabase database = module.getMongo4Provider().provideMongoDatabase();
      assertNotNull(findIndex(database.getCollection(DELTAS_COLLECTION),
          new Document("waveId", 1).append("waveletId", 1)));
      assertUniqueIndex(database.getCollection(DELTAS_COLLECTION),
          new Document("waveId", 1)
              .append("waveletId", 1)
              .append("transformed.appliedAtVersion", 1));
      assertNotNull(findIndex(database.getCollection(DELTAS_COLLECTION),
          new Document("waveId", 1)
              .append("waveletId", 1)
              .append("transformed.resultingVersion.version", 1)));
      assertNotNull(findIndex(database.getCollection(SNAPSHOTS_COLLECTION),
          new Document("waveId", 1).append("waveletId", 1).append("version", -1)));
      assertNotNull(findIndex(database.getCollection(CONTACT_MESSAGES_COLLECTION),
          new Document("createdAt", -1)));
      assertNotNull(findIndex(database.getCollection(CONTACT_MESSAGES_COLLECTION),
          new Document("status", 1)));
      assertUniqueIndex(database.getCollection(ANALYTICS_COLLECTION), new Document("hour", 1));
      assertNotNull(database.getCollection(CHANGELOG_COLLECTION).find().first());
      assertTrue(
          "missing collection " + LOCK_COLLECTION,
          database.listCollectionNames().into(new ArrayList<>()).contains(LOCK_COLLECTION));
    } finally {
      closeQuietly(module);
      stopQuietly(mongo);
    }
  }

  @Test
  public void upgradesLegacyAppliedVersionIndexToUnique() {
    preferColimaIfDockerHostInvalid();

    MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE);
    PersistenceModule module = null;
    try {
      startOrSkip(mongo);
      module = new PersistenceModule(mongoBackedConfig(mongo));
      MongoDatabase database = module.getMongo4Provider().provideMongoDatabase();
      MongoCollection<Document> deltas = database.getCollection(DELTAS_COLLECTION);

      deltas.createIndex(
          Indexes.ascending("waveId", "waveletId", "transformed.appliedAtVersion"),
          new IndexOptions().name(APPLIED_VERSION_INDEX_NAME).background(true));

      module.runMongoMigrationsIfNeeded();

      assertUniqueIndex(deltas,
          new Document("waveId", 1)
              .append("waveletId", 1)
              .append("transformed.appliedAtVersion", 1));
    } finally {
      closeQuietly(module);
      stopQuietly(mongo);
    }
  }

  private static void assertUniqueIndex(MongoCollection<Document> collection, Document keySpec) {
    Document index = findIndex(collection, keySpec);
    assertNotNull("missing index for " + keySpec.toJson(), index);
    assertTrue("expected unique index for " + keySpec.toJson(),
        Boolean.TRUE.equals(index.getBoolean("unique")));
  }

  private static Document findIndex(MongoCollection<Document> collection, Document keySpec) {
    List<Document> indexes = collection.listIndexes().into(new ArrayList<>());
    for (Document index : indexes) {
      Document key = (Document) index.get("key");
      if (keySpec.equals(key)) {
        return index;
      }
    }
    return null;
  }

  private static Config mongoBackedConfig(MongoDBContainer mongo) {
    return ConfigFactory.parseString(
        "core {\n"
            + "  signer_info_store_type = \"mongodb\"\n"
            + "  attachment_store_type = \"mongodb\"\n"
            + "  account_store_type = \"mongodb\"\n"
            + "  contact_store_type = \"mongodb\"\n"
            + "  delta_store_type = \"mongodb\"\n"
            + "  mongodb_host = \"" + mongo.getHost() + "\"\n"
            + "  mongodb_port = " + mongo.getMappedPort(27017) + "\n"
            + "  mongodb_database = \"wiab_migration_it\"\n"
            + "  mongodb_driver = \"v4\"\n"
            + "  analytics_counters_enabled = true\n"
            + "}\n");
  }

  private static void preferColimaIfDockerHostInvalid() {
    try {
      String envHost = System.getenv("DOCKER_HOST");
      String userHome = System.getProperty("user.home");
      java.io.File colimaSock = new java.io.File(userHome + "/.colima/default/docker.sock");
      String effective = null;
      if (envHost == null || envHost.trim().isEmpty()) {
        if (colimaSock.exists()) {
          effective = "unix://" + colimaSock.getAbsolutePath();
          System.setProperty("DOCKER_HOST", effective);
          System.setProperty("docker.host", effective);
          LOG.info("[IT] Using Colima Docker socket at {}", effective);
        }
      } else if (envHost.startsWith("unix://")) {
        String path = envHost.replaceFirst("^unix://", "");
        if (!(new java.io.File(path).exists()) && colimaSock.exists()) {
          effective = "unix://" + colimaSock.getAbsolutePath();
          System.setProperty("DOCKER_HOST", effective);
          System.setProperty("docker.host", effective);
          LOG.info("[IT] Using Colima Docker socket at {}", effective);
        }
      } else if ("unix://localhost:2375".equals(envHost) && colimaSock.exists()) {
        effective = "unix://" + colimaSock.getAbsolutePath();
        System.setProperty("DOCKER_HOST", effective);
        System.setProperty("docker.host", effective);
        LOG.info("[IT] Using Colima Docker socket at {}", effective);
      }
    } catch (Throwable t) {
      LOG.debug("[IT] Colima hint failed (ignored)", t);
    }
  }

  private static void startOrSkip(MongoDBContainer mongo) {
    try {
      mongo.start();
    } catch (ContainerLaunchException e) {
      LOG.warn("MongoDBContainer failed to launch; skipping IT", e);
      Assume.assumeNoException("Skipping Mongo IT due to container launch failure", e);
    } catch (Throwable t) {
      LOG.warn("Docker/Testcontainers error encountered; skipping IT", t);
      Assume.assumeNoException("Skipping Mongo IT due to Docker/Testcontainers error", t);
    }
  }

  private static void stopQuietly(MongoDBContainer mongo) {
    try {
      if (mongo != null) {
        mongo.stop();
      }
    } catch (Exception e) {
      LOG.warn("Ignored exception while stopping MongoDBContainer.", e);
    }
  }

  private static void closeQuietly(PersistenceModule module) {
    try {
      if (module != null) {
        module.getMongo4Provider().close();
      }
    } catch (Exception e) {
      LOG.warn("Ignored exception while closing Mongo4DbProvider.", e);
    }
  }
}

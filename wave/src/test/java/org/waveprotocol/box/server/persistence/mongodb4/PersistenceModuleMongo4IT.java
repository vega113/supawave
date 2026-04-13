package org.waveprotocol.box.server.persistence.mongodb4;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.box.server.waveserver.DeltaStore;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PersistenceModuleMongo4IT {
  private static final Logger LOG = LoggerFactory.getLogger(PersistenceModuleMongo4IT.class);
  private static final DockerImageName MONGO_IMAGE =
      DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");

  @Test
  public void bindsMongo4StoresWhenDriverIsV4() {
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);

    MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE);
    try {
      MongoItTestUtil.startOrSkip(mongo, LOG);

      Config config = ConfigFactory.parseString(
          "core {\n"
              + "  signer_info_store_type = \"mongodb\"\n"
              + "  attachment_store_type = \"mongodb\"\n"
              + "  account_store_type = \"mongodb\"\n"
              + "  contact_store_type = \"mongodb\"\n"
              + "  delta_store_type = \"mongodb\"\n"
              + "  mongodb_host = \"" + mongo.getHost() + "\"\n"
              + "  mongodb_port = " + mongo.getMappedPort(27017) + "\n"
              + "  mongodb_database = \"wiab_it\"\n"
              + "  mongodb_driver = \"v4\"\n"
              + "}\n");

      Injector injector = Guice.createInjector(new PersistenceModule(config));

      assertTrue(injector.getInstance(CertPathStore.class) instanceof Mongo4SignerInfoStore);
      assertTrue(injector.getInstance(AttachmentStore.class) instanceof Mongo4AttachmentStore);
      assertTrue(injector.getInstance(AccountStore.class) instanceof Mongo4AccountStore);
      assertTrue(injector.getInstance(DeltaStore.class) instanceof Mongo4DeltaStore);
      assertTrue(injector.getInstance(ContactStore.class) instanceof Mongo4ContactStore);
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
    }
  }

  /**
   * Regression test for the case where account_store_type is NOT "mongodb" but
   * mongodb_driver=v4 is set (e.g. contabo: file accounts, v4 driver). Before the
   * fix, FeatureFlagStore was silently bound to MemoryFeatureFlagStore and the
   * ot-search flag was never written to MongoDB on fresh installs.
   */
  @Test
  public void bindsFeatureFlagStoreToMongo4WhenDriverIsV4AndAccountStoreIsFile() throws Exception {
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);

    MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE);
    try {
      MongoItTestUtil.startOrSkip(mongo, LOG);

      Config config = ConfigFactory.parseString(
          "core {\n"
              + "  signer_info_store_type = \"file\"\n"
              + "  attachment_store_type = \"disk\"\n"
              + "  account_store_type = \"file\"\n"
              + "  contact_store_type = \"memory\"\n"
              + "  delta_store_type = \"file\"\n"
              + "  mongodb_host = \"" + mongo.getHost() + "\"\n"
              + "  mongodb_port = " + mongo.getMappedPort(27017) + "\n"
              + "  mongodb_database = \"wiab_it\"\n"
              + "  mongodb_driver = \"v4\"\n"
              + "}\n");

      Injector injector = Guice.createInjector(new PersistenceModule(config));

      // FeatureFlagStore must be MongoDB-backed even though accounts use file storage.
      assertTrue(injector.getInstance(FeatureFlagStore.class) instanceof Mongo4FeatureFlagStore);

      // Seeder must write the ot-search flag into MongoDB on a fresh install.
      FeatureFlagStore store = injector.getInstance(FeatureFlagStore.class);
      Config searchConfig = config.withFallback(
          ConfigFactory.parseString("search.ot_search_enabled = true"));
      FeatureFlagSeeder.seedSearchFeatureFlags(store, searchConfig);
      assertNotNull("ot-search flag must be persisted to MongoDB", store.get("ot-search"));
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
    }
  }
}

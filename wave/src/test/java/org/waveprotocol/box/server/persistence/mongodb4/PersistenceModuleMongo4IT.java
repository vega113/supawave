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
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.box.server.waveserver.DeltaStore;

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
              + "  delta_store_type = \"mongodb\"\n"
              + "  mongodb_host = \"" + mongo.getHost() + "\"\n"
              + "  mongodb_port = \"" + mongo.getMappedPort(27017) + "\"\n"
              + "  mongodb_database = \"wiab_it\"\n"
              + "  mongodb_driver = \"v4\"\n"
              + "}\n");

      Injector injector = Guice.createInjector(new PersistenceModule(config));

      assertTrue(injector.getInstance(CertPathStore.class) instanceof Mongo4SignerInfoStore);
      assertTrue(injector.getInstance(AttachmentStore.class) instanceof Mongo4AttachmentStore);
      assertTrue(injector.getInstance(AccountStore.class) instanceof Mongo4AccountStore);
      assertTrue(injector.getInstance(DeltaStore.class) instanceof Mongo4DeltaStore);
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
    }
  }
}

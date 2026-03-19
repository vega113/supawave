package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.waveprotocol.box.server.persistence.CertPathStoreTestBase;
import org.waveprotocol.wave.crypto.CertPathStore;

public class Mongo4SignerInfoStoreIT extends CertPathStoreTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(Mongo4SignerInfoStoreIT.class);
  private static final DockerImageName MONGO_IMAGE =
      DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");

  private MongoDBContainer mongo;
  private com.mongodb.client.MongoClient client;
  private MongoDatabase database;
  private CertPathStore certPathStore;

  public Mongo4SignerInfoStoreIT() throws Exception {
    super();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);
    mongo = new MongoDBContainer(MONGO_IMAGE);
    MongoItTestUtil.startOrSkip(mongo, LOG);
    client = MongoClients.create(mongo.getConnectionString());
    database = client.getDatabase("wiab_it");
    certPathStore = new Mongo4SignerInfoStore(database);
  }

  @Override
  protected CertPathStore newCertPathStore() {
    database.drop();
    return certPathStore;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (client != null) {
        client.close();
      }
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
      super.tearDown();
    }
  }
}

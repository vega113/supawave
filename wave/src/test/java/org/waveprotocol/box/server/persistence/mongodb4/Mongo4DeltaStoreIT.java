package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.waveprotocol.box.server.persistence.DeltaStoreTestBase;
import org.waveprotocol.box.server.waveserver.DeltaStore;

public class Mongo4DeltaStoreIT extends DeltaStoreTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(Mongo4DeltaStoreIT.class);
  private static final DockerImageName MONGO_IMAGE =
      DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");

  private MongoDBContainer mongo;
  private com.mongodb.client.MongoClient client;
  private MongoDatabase database;
  private DeltaStore deltaStore;

  public Mongo4DeltaStoreIT() throws Exception {
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
    deltaStore = new Mongo4DeltaStore(database);
  }

  @Override
  protected DeltaStore newDeltaStore() {
    database.drop();
    return deltaStore;
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

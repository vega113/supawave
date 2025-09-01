package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.crypto.CertPathStore;

/**
 * Spike provider for MongoDB Java Driver 4.x (synchronous API).
 * Not wired by default; kept for compile-time prototyping of the migration.
 */
public class Mongo4DbProvider implements AutoCloseable {
  private final String host;
  private final String port;
  private final String database;

  private MongoClient client;
  private MongoDatabase db;

  public Mongo4DbProvider(String host, String port, String database) {
    this.host = host;
    this.port = port;
    this.database = database;
  }

  private void ensure() {
    if (client == null) {
      String uri = "mongodb://" + host + ":" + port + "/" + database;
      MongoClientSettings settings = MongoClientSettings.builder()
          .applyConnectionString(new ConnectionString(uri))
          .build();
      client = MongoClients.create(settings);
      db = client.getDatabase(database);
    }
  }

  @Override
  public void close() {
    try {
      if (client != null) client.close();
    } catch (Exception ignore) {}
  }

  // Placeholders for future mapping of old interfaces to new driver API
  public CertPathStore provideMongoDbStore() { ensure(); return new Mongo4SignerInfoStore(db); }

  public AttachmentStore provideMongoDbAttachmentStore() { ensure(); return new Mongo4AttachmentStore(db); }

  public AccountStore provideMongoDbAccountStore() { ensure(); return new Mongo4AccountStore(db); }

  // Delta store not yet implemented for v4
}

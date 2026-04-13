package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.wave.crypto.CertPathStore;

/**
 * Provider for MongoDB Java Driver 4.x (synchronous API).
 *
 * <p>Supports optional authentication: when {@code username} is non-empty the
 * connection URI includes credentials and {@code ?authSource=admin}.
 */
public class Mongo4DbProvider implements AutoCloseable {
  private final String host;
  private final String port;
  private final String database;
  private final String username;
  private final String password;

  private MongoClient client;
  private MongoDatabase db;

  /**
   * Creates a provider with optional authentication credentials.
   *
   * @param host     MongoDB hostname
   * @param port     MongoDB port
   * @param database target database name
   * @param username MongoDB username; empty or null for unauthenticated
   * @param password MongoDB password; ignored when username is empty
   */
  public Mongo4DbProvider(String host, String port, String database,
                          String username, String password) {
    this.host = host;
    this.port = port;
    this.database = database;
    this.username = username != null ? username : "";
    this.password = password != null ? password : "";
  }

  /** Convenience constructor for unauthenticated connections. */
  public Mongo4DbProvider(String host, String port, String database) {
    this(host, port, database, "", "");
  }

  private void ensure() {
    if (client == null) {
      String userInfo = "";
      if (!username.isEmpty()) {
        String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedPass = URLEncoder.encode(password, StandardCharsets.UTF_8).replace("+", "%20");
        userInfo = encodedUser + ":" + encodedPass + "@";
      }
      String authQuery = userInfo.isEmpty() ? "" : "?authSource=admin";
      String uri = "mongodb://" + userInfo + host + ":" + port + "/" + database + authQuery;
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

  public MongoClient provideMongoClient() {
    ensure();
    return client;
  }

  public MongoDatabase provideMongoDatabase() {
    ensure();
    return db;
  }

  public CertPathStore provideMongoDbStore() { ensure(); return new Mongo4SignerInfoStore(db); }

  public AttachmentStore provideMongoDbAttachmentStore() { ensure(); return new Mongo4AttachmentStore(db); }

  public AccountStore provideMongoDbAccountStore() { ensure(); return new Mongo4AccountStore(db); }

  public DeltaStore provideMongoDbDeltaStore() { ensure(); return new Mongo4DeltaStore(db); }

  public SnapshotStore provideMongoDbSnapshotStore() { ensure(); return new Mongo4SnapshotStore(db); }

  public ContactStore provideMongoDbContactStore() { ensure(); return new Mongo4ContactStore(db); }

  public ContactMessageStore provideMongoDbContactMessageStore() { ensure(); return new Mongo4ContactMessageStore(db); }

  public FeatureFlagStore provideMongoDbFeatureFlagStore() { ensure(); return new Mongo4FeatureFlagStore(db); }

  public AnalyticsCounterStore provideMongoDbAnalyticsCounterStore() { ensure(); return new Mongo4AnalyticsCounterStore(db); }
}

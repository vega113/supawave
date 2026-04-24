package org.waveprotocol.box.server.persistence.migrations.changesets;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;

/** Adds the indexed provider-subject lookup used by social sign-in. */
@ChangeUnit(id = "social-identity-unique-index", order = "003", author = "codex")
public final class SocialIdentityUniqueIndex_003 {
  static final String INDEX_NAME = "human_social_identity_provider_subject_unique";
  static final String PROVIDER_PATH = "human.socialIdentities.provider";
  static final String SUBJECT_PATH = "human.socialIdentities.subject";

  private final MongoDatabase database;
  private final MongoMigrationConfig config;

  @ChangeUnitConstructor
  public SocialIdentityUniqueIndex_003(MongoDatabase database, MongoMigrationConfig config) {
    this.database = database;
    this.config = config;
  }

  @Execution
  public void execution() {
    if (!config.usesMongoAccountStore()) {
      return;
    }
    MongoCollection<Document> accounts = database.getCollection("account");
    Bson partial = Filters.and(Filters.exists(PROVIDER_PATH), Filters.exists(SUBJECT_PATH));
    accounts.createIndex(
        Indexes.compoundIndex(Indexes.ascending(PROVIDER_PATH), Indexes.ascending(SUBJECT_PATH)),
        new IndexOptions()
            .name(INDEX_NAME)
            .unique(true)
            .background(true)
            .partialFilterExpression(partial));
  }

  @RollbackExecution
  public void rollbackExecution() {
    // Compatible additive index; do not drop automatically.
  }
}

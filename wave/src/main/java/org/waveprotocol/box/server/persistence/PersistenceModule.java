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

package org.waveprotocol.box.server.persistence;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.contact.ContactManagerImpl;
import org.waveprotocol.box.server.contact.ContactsRecorder;
import org.waveprotocol.box.server.persistence.file.FileAccountStore;
import org.waveprotocol.box.server.persistence.file.FileAttachmentStore;
import org.waveprotocol.box.server.persistence.file.FileDeltaStore;
import org.waveprotocol.box.server.persistence.file.FileSignerInfoStore;
import org.waveprotocol.box.server.persistence.file.FileSnapshotStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.memory.NoOpAnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.memory.MemoryContactMessageStore;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.persistence.memory.MemorySnapshotStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.box.server.persistence.migrations.MongockMongoMigrationRunnerFactory;
import org.waveprotocol.box.server.persistence.mongodb.MongoDbProvider;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Module for setting up the different persistence stores.
 *
 *<p>
 * The valid names for the cert store are 'memory', 'file' and 'mongodb'
 *
 *<p>
 *The valid names for the attachment store are 'disk' and 'mongodb'
 *
 *<p>
 *The valid names for the account store are 'memory', 'file' and 'mongodb'.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class PersistenceModule extends AbstractModule {
  private static final Log LOG = Log.get(PersistenceModule.class);

  private final String signerInfoStoreType;

  private final String attachmentStoreType;

  private final String accountStoreType;

  private final String deltaStoreType;

  private final String contactStoreType;

  private MongoDbProvider mongoDbProvider;
  private org.waveprotocol.box.server.persistence.mongodb4.Mongo4DbProvider mongo4Provider;

  private final String mongoDBHost;

  private final String mongoDBPort;

  private final String mongoDBdatabase;
  private final String mongoDBUsername;
  private final String mongoDBPassword;
  private final String mongoDriver;
  private final boolean analyticsCountersEnabled;
  private final MongoMigrationConfig mongoMigrationConfig;
  private final MongoMigrationRunnerFactory mongoMigrationRunnerFactory;
  private boolean mongoMigrationsExecuted;


  @Inject
  public PersistenceModule(Config config) {
    this(config, new MongockMongoMigrationRunnerFactory());
  }

  PersistenceModule(Config config, MongoMigrationRunnerFactory mongoMigrationRunnerFactory) {
    this.signerInfoStoreType = config.getString("core.signer_info_store_type");
    this.attachmentStoreType = config.getString("core.attachment_store_type");
    this.accountStoreType = config.getString("core.account_store_type");
    this.deltaStoreType = config.getString("core.delta_store_type");
    this.contactStoreType = config.hasPath("core.contact_store_type")
        ? config.getString("core.contact_store_type") : "memory";
    this.mongoDBHost = config.getString("core.mongodb_host");
    this.mongoDBPort = config.getString("core.mongodb_port");
    this.mongoDBdatabase = config.getString("core.mongodb_database");
    this.mongoDBUsername = config.hasPath("core.mongodb_username") ? config.getString("core.mongodb_username") : "";
    this.mongoDBPassword = config.hasPath("core.mongodb_password") ? config.getString("core.mongodb_password") : "";
    this.mongoDriver = config.hasPath("core.mongodb_driver") ? config.getString("core.mongodb_driver") : "v2";
    this.analyticsCountersEnabled = config.hasPath("core.analytics_counters_enabled")
        && config.getBoolean("core.analytics_counters_enabled");
    this.mongoMigrationConfig = new MongoMigrationConfig(
        signerInfoStoreType,
        attachmentStoreType,
        accountStoreType,
        deltaStoreType,
        contactStoreType,
        mongoDBHost,
        mongoDBPort,
        mongoDBdatabase,
        mongoDBUsername,
        mongoDBPassword,
        mongoDriver,
        analyticsCountersEnabled);
    this.mongoMigrationRunnerFactory = mongoMigrationRunnerFactory;
  }

  /**
   * Returns a {@link MongoDbProvider} instance.
   */
  public MongoDbProvider getMongoDbProvider() {
    if (mongoDbProvider == null) {
      mongoDbProvider = new MongoDbProvider(mongoDBHost, mongoDBPort, mongoDBdatabase);
    }
    return mongoDbProvider;
  }

  public org.waveprotocol.box.server.persistence.mongodb4.Mongo4DbProvider getMongo4Provider() {
    if (mongo4Provider == null) {
      mongo4Provider = new org.waveprotocol.box.server.persistence.mongodb4.Mongo4DbProvider(
          mongoDBHost, mongoDBPort, mongoDBdatabase, mongoDBUsername, mongoDBPassword);
    }
    return mongo4Provider;
  }

  @Override
  protected void configure() {
    runMongoMigrationsIfNeeded();
    bindCertPathStore();
    bindAttachmentStore();
    bindAccountStore();
    bindDeltaStore();
    bindSnapshotStore();
    bindContactStore();
    bindContactMessageStore();
    bindFeatureFlagStore();
    bindAnalyticsCounterStore();
  }

  void runMongoMigrationsIfNeeded() {
    if (mongoMigrationsExecuted || !shouldRunMongoMigrations()) {
      return;
    }
    MongoMigrationRunner runner = mongoMigrationRunnerFactory.create(mongoMigrationConfig);
    runner.run();
    mongoMigrationsExecuted = true;
  }

  boolean shouldRunMongoMigrations() {
    return mongoMigrationConfig.isMongoV4Driver()
        && mongoMigrationConfig.usesMongoBackedCoreStore();
  }

  /**
   * Binds the CertPathStore implementation to the store specified in the
   * properties.
   */
  private void bindCertPathStore() {
    if (signerInfoStoreType.equalsIgnoreCase("memory")) {
      bind(CertPathStore.class).to(MemoryStore.class).in(Singleton.class);
    } else if (signerInfoStoreType.equalsIgnoreCase("file")) {
      bind(CertPathStore.class).to(FileSignerInfoStore.class).in(Singleton.class);
    } else if (signerInfoStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(CertPathStore.class).toInstance(getMongo4Provider().provideMongoDbStore());
      } else {
        bind(CertPathStore.class).toInstance(getMongoDbProvider().provideMongoDbStore());
      }
    } else {
      throw new RuntimeException(
          "Invalid certificate path store type: '" + signerInfoStoreType + "'");
    }
  }

  private void bindAttachmentStore() {
    if (attachmentStoreType.equalsIgnoreCase("disk")) {
      bind(AttachmentStore.class).to(FileAttachmentStore.class).in(Singleton.class);
    } else if (attachmentStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(AttachmentStore.class).toInstance(getMongo4Provider().provideMongoDbAttachmentStore());
      } else {
        bind(AttachmentStore.class).toInstance(getMongoDbProvider().provideMongoDbStore());
      }
    } else {
      throw new RuntimeException("Invalid attachment store type: '" + attachmentStoreType + "'");
    }
  }

  private void bindAccountStore() {
    if (accountStoreType.equalsIgnoreCase("memory")) {
      bind(AccountStore.class).to(MemoryStore.class).in(Singleton.class);
    } else if (accountStoreType.equalsIgnoreCase("file")) {
      bind(AccountStore.class).to(FileAccountStore.class).in(Singleton.class);
    } else if (accountStoreType.equalsIgnoreCase("fake")) {
      bind(AccountStore.class).to(FakePermissiveAccountStore.class).in(Singleton.class);
    } else if (accountStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(AccountStore.class).toInstance(getMongo4Provider().provideMongoDbAccountStore());
      } else {
        bind(AccountStore.class).toInstance(getMongoDbProvider().provideMongoDbStore());
      }
    } else {
      throw new RuntimeException("Invalid account store type: '" + accountStoreType + "'");
    }
  }

  /**
   * Binds the ContactStore and ContactManager.
   * Supported types: memory, mongodb.
   */
  private void bindContactStore() {
    if (contactStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(ContactStore.class).toInstance(getMongo4Provider().provideMongoDbContactStore());
      } else {
        throw new IllegalStateException(
            "contact_store_type is set to 'mongodb' but the legacy v2 MongoDB driver "
            + "does not support ContactStore. Set core.mongodb_driver to 'v4' or use "
            + "contact_store_type 'memory'.");
      }
    } else if (contactStoreType.equalsIgnoreCase("memory")) {
      bind(ContactStore.class).to(MemoryStore.class).in(Singleton.class);
    } else {
      throw new IllegalStateException(
          "Unknown contact_store_type: '" + contactStoreType
          + "'. Supported values are 'memory' and 'mongodb'.");
    }
    bind(ContactManager.class).to(ContactManagerImpl.class).in(Singleton.class);
    bind(ContactsRecorder.class).in(Singleton.class);
  }

  /**
   * Binds the SnapshotStore based on the delta_store_type.
   * Snapshots follow the same backend as deltas.
   */
  private void bindSnapshotStore() {
    if (deltaStoreType.equalsIgnoreCase("memory")) {
      bind(SnapshotStore.class).to(MemorySnapshotStore.class).in(Singleton.class);
    } else if (deltaStoreType.equalsIgnoreCase("file")) {
      bind(SnapshotStore.class).to(FileSnapshotStore.class).in(Singleton.class);
    } else if (deltaStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(SnapshotStore.class).toInstance(getMongo4Provider().provideMongoDbSnapshotStore());
      } else {
        // Legacy v2 driver: fall back to memory-based snapshot store since
        // the v2 driver module does not have a snapshot store implementation.
        bind(SnapshotStore.class).to(MemorySnapshotStore.class).in(Singleton.class);
      }
    } else {
      // Unknown store type: bind memory snapshot store as safe fallback
      bind(SnapshotStore.class).to(MemorySnapshotStore.class).in(Singleton.class);
    }
  }

  private void bindDeltaStore() {
    if (deltaStoreType.equalsIgnoreCase("memory")) {
      bind(DeltaStore.class).to(MemoryDeltaStore.class).in(Singleton.class);
    } else if (deltaStoreType.equalsIgnoreCase("file")) {
      bind(DeltaStore.class).to(FileDeltaStore.class).in(Singleton.class);
    } else if (deltaStoreType.equalsIgnoreCase("mongodb")) {
      if ("v4".equalsIgnoreCase(mongoDriver)) {
        bind(DeltaStore.class).toInstance(getMongo4Provider().provideMongoDbDeltaStore());
      } else {
        bind(DeltaStore.class).toInstance(getMongoDbProvider().provideMongoDbDeltaStore());
      }
    } else {
      throw new RuntimeException("Invalid delta store type: '" + deltaStoreType + "'");
    }
  }

  /**
   * Binds the ContactMessageStore for contact form submissions.
   * Uses the same backend type as the account store (mongodb or memory).
   */
  private void bindContactMessageStore() {
    if (accountStoreType.equalsIgnoreCase("mongodb") && "v4".equalsIgnoreCase(mongoDriver)) {
      bind(ContactMessageStore.class)
          .toInstance(getMongo4Provider().provideMongoDbContactMessageStore());
    } else {
      bind(ContactMessageStore.class)
          .to(MemoryContactMessageStore.class).in(Singleton.class);
    }
  }

  /**
   * Binds the FeatureFlagStore for feature flag management.
   * Uses MongoDB when the v4 driver is configured (regardless of account store type),
   * since mongodb_driver=v4 explicitly means MongoDB is available.
   * Falls back to an in-memory store when using the legacy v2 driver.
   */
  private void bindFeatureFlagStore() {
    if ("v4".equalsIgnoreCase(mongoDriver)) {
      bind(FeatureFlagStore.class)
          .toInstance(getMongo4Provider().provideMongoDbFeatureFlagStore());
    } else {
      bind(FeatureFlagStore.class)
          .to(MemoryFeatureFlagStore.class).in(Singleton.class);
    }
    bind(FeatureFlagService.class).in(Singleton.class);
  }

  /**
   * Binds the AnalyticsCounterStore for incremental analytics counters.
   * Uses NoOp when disabled, MongoDB when available, otherwise falls back to in-memory.
   */
  private void bindAnalyticsCounterStore() {
    if (!analyticsCountersEnabled) {
      bind(AnalyticsCounterStore.class)
          .to(NoOpAnalyticsCounterStore.class).in(Singleton.class);
      return;
    }
    if (accountStoreType.equalsIgnoreCase("mongodb") && "v4".equalsIgnoreCase(mongoDriver)) {
      bind(AnalyticsCounterStore.class)
          .toInstance(getMongo4Provider().provideMongoDbAnalyticsCounterStore());
    } else if (accountStoreType.equalsIgnoreCase("file")) {
      LOG.warning(
          "Analytics counters are enabled but file-based storage does not support analytics persistence; "
              + "analytics will be unavailable.");
      bind(AnalyticsCounterStore.class)
          .to(NoOpAnalyticsCounterStore.class).in(Singleton.class);
    } else {
      LOG.warning(
          "Analytics counters are enabled but analytics persistence is using in-memory storage; "
              + "analytics data will be lost on restart.");
      bind(AnalyticsCounterStore.class)
          .to(MemoryAnalyticsCounterStore.class).in(Singleton.class);
    }
  }
}

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

/**
 * Effective Mongo migration input derived from the Wave persistence config.
 */
public final class MongoMigrationConfig {

  private final String signerInfoStoreType;
  private final String attachmentStoreType;
  private final String accountStoreType;
  private final String deltaStoreType;
  private final String contactStoreType;
  private final String host;
  private final String port;
  private final String database;
  private final String username;
  private final String password;
  private final String mongoDriver;
  private final boolean analyticsCountersEnabled;

  public MongoMigrationConfig(String signerInfoStoreType, String attachmentStoreType,
      String accountStoreType, String deltaStoreType, String contactStoreType, String host,
      String port, String database, String username, String password, String mongoDriver,
      boolean analyticsCountersEnabled) {
    this.signerInfoStoreType = signerInfoStoreType;
    this.attachmentStoreType = attachmentStoreType;
    this.accountStoreType = accountStoreType;
    this.deltaStoreType = deltaStoreType;
    this.contactStoreType = contactStoreType;
    this.host = host;
    this.port = port;
    this.database = database;
    this.username = username;
    this.password = password;
    this.mongoDriver = mongoDriver;
    this.analyticsCountersEnabled = analyticsCountersEnabled;
  }

  public String getSignerInfoStoreType() {
    return signerInfoStoreType;
  }

  public String getAttachmentStoreType() {
    return attachmentStoreType;
  }

  public String getAccountStoreType() {
    return accountStoreType;
  }

  public String getDeltaStoreType() {
    return deltaStoreType;
  }

  public String getContactStoreType() {
    return contactStoreType;
  }

  public String getHost() {
    return host;
  }

  public String getPort() {
    return port;
  }

  public String getDatabase() {
    return database;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getMongoDriver() {
    return mongoDriver;
  }

  public boolean isAnalyticsCountersEnabled() {
    return analyticsCountersEnabled;
  }

  public boolean isMongoV4Driver() {
    return "v4".equalsIgnoreCase(mongoDriver);
  }

  public boolean usesMongoBackedCoreStore() {
    return isMongoStoreType(signerInfoStoreType)
        || isMongoStoreType(attachmentStoreType)
        || isMongoStoreType(accountStoreType)
        || isMongoStoreType(deltaStoreType);
  }

  public boolean usesMongoDeltaStore() {
    return isMongoStoreType(deltaStoreType);
  }

  public boolean usesMongoContactStore() {
    return isMongoStoreType(contactStoreType);
  }

  public boolean usesMongoContactMessageStore() {
    return isMongoStoreType(accountStoreType) && isMongoV4Driver();
  }

  public boolean usesMongoAnalyticsCounters() {
    return analyticsCountersEnabled && isMongoStoreType(accountStoreType) && isMongoV4Driver();
  }

  private static boolean isMongoStoreType(String storeType) {
    return "mongodb".equalsIgnoreCase(storeType);
  }
}

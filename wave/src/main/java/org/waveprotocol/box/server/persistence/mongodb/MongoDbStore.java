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

package org.waveprotocol.box.server.persistence.mongodb;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import org.bson.types.BasicBSONList;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.attachment.AttachmentProto;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.crypto.SignatureException;
import org.waveprotocol.wave.crypto.SignerInfo;
import org.waveprotocol.wave.federation.Proto.ProtocolSignerInfo;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>CertPathStore:</b><br/>
 * <i>Collection(signerInfo):</i>
 * <ul>
 * <li>_id : signerId byte array.</li>
 * <li>protoBuff : byte array representing the protobuff message of a
 * {@link ProtocolSignerInfo}.</li>
 * </ul>
 * <p>
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 * @author josephg@gmail.com (Joseph Gentle)
 *
 */
public final class MongoDbStore implements SignerInfoStore, AttachmentStore, AccountStore {

  private static final String ACCOUNT_COLLECTION = "account";
  private static final String ACCOUNT_HUMAN_DATA_FIELD = "human";
  private static final String ACCOUNT_ROBOT_DATA_FIELD = "robot";

  private static final String HUMAN_PASSWORD_FIELD = "passwordDigest";
  private static final String HUMAN_SEARCHES_FIELD = "searches";
  private static final String HUMAN_SOCIAL_IDENTITIES_FIELD = "socialIdentities";
  private static final String SEARCH_NAME_FIELD = "name";
  private static final String SEARCH_QUERY_FIELD = "query";
  private static final String SEARCH_PINNED_FIELD = "pinned";
  private static final String SOCIAL_PROVIDER_FIELD = "provider";
  private static final String SOCIAL_SUBJECT_FIELD = "subject";
  private static final String SOCIAL_EMAIL_FIELD = "email";
  private static final String SOCIAL_DISPLAY_NAME_FIELD = "displayName";
  private static final String SOCIAL_LINKED_AT_FIELD = "linkedAtMillis";

  private static final String PASSWORD_DIGEST_FIELD = "digest";
  private static final String PASSWORD_SALT_FIELD = "salt";

  private static final String ROBOT_URL_FIELD = "url";
  private static final String ROBOT_SECRET_FIELD = "secret";
  private static final String ROBOT_CAPABILITIES_FIELD = "capabilities";
  private static final String ROBOT_VERIFIED_FIELD = "verified";
  private static final String ROBOT_OWNER_FIELD = "ownerAddress";
  private static final String ROBOT_DESCRIPTION_FIELD = "description";
  private static final String ROBOT_CREATED_AT_FIELD = "createdAtMillis";
  private static final String ROBOT_UPDATED_AT_FIELD = "updatedAtMillis";
  private static final String ROBOT_PAUSED_FIELD = "paused";
  private static final String ROBOT_TOKEN_VERSION_FIELD = "tokenVersion";
  private static final String ROBOT_LAST_ACTIVE_AT_FIELD = "lastActiveAtMillis";

  private static final String CAPABILITIES_VERSION_FIELD = "version";
  private static final String CAPABILITIES_HASH_FIELD = "capabilitiesHash";
  private static final String CAPABILITIES_CAPABILITIES_FIELD = "capabilities";
  private static final String CAPABILITIES_RPC_SERVER_URL_FIELD = "rpcServerUrl";
  private static final String CAPABILITY_CONTEXTS_FIELD = "contexts";
  private static final String CAPABILITY_FILTER_FIELD = "filter";

  private static final Logger LOG = Logger.getLogger(MongoDbStore.class.getName());

  private final DB database;
  private final GridFS attachmentGrid;
  private final GridFS thumbnailGrid;
  private final GridFS metadataGrid;

  MongoDbStore(DB database) {
    this.database = database;
    attachmentGrid = new GridFS(database, "attachments");
    thumbnailGrid = new GridFS(database, "thumbnails");
    metadataGrid = new GridFS(database, "metadata");
  }

  @Override
  public void initializeSignerInfoStore() throws PersistenceException {
    // Nothing to initialize
  }

  @Override
  public SignerInfo getSignerInfo(byte[] signerId) {
    DBObject query = getDBObjectForSignerId(signerId);
    DBCollection signerInfoCollection = getSignerInfoCollection();
    DBObject signerInfoDBObject = signerInfoCollection.findOne(query);

    // Sub-class contract specifies return null when not found
    SignerInfo signerInfo = null;

    if (signerInfoDBObject != null) {
      byte[] protobuff = (byte[]) signerInfoDBObject.get("protoBuff");
      try {
        signerInfo = new SignerInfo(ProtocolSignerInfo.parseFrom(protobuff));
      } catch (InvalidProtocolBufferException e) {
        LOG.log(Level.SEVERE, "Couldn't parse the protobuff stored in MongoDB: " + protobuff, e);
      } catch (SignatureException e) {
        LOG.log(Level.SEVERE, "Couldn't parse the certificate chain or domain properly", e);
      }
    }
    return signerInfo;
  }

  @Override
  public void putSignerInfo(ProtocolSignerInfo protocolSignerInfo) throws SignatureException {
    SignerInfo signerInfo = new SignerInfo(protocolSignerInfo);
    byte[] signerId = signerInfo.getSignerId();

    // Not using a modifier here because rebuilding the object is not a lot of
    // work. Doing implicit upsert by using save with a DBOBject that has an _id
    // set.
    DBObject signerInfoDBObject = getDBObjectForSignerId(signerId);
    signerInfoDBObject.put("protoBuff", protocolSignerInfo.toByteArray());
    getSignerInfoCollection().save(signerInfoDBObject);
  }

  /**
   * Returns an instance of {@link DBCollection} for storing SignerInfo.
   */
  private DBCollection getSignerInfoCollection() {
    return database.getCollection("signerInfo");
  }

  /**
   * Returns a {@link DBObject} which contains the key-value pair used to
   * signify the signerId.
   *
   * @param signerId the signerId value to set.
   * @return a new {@link DBObject} with the (_id,signerId) entry.
   */
  private DBObject getDBObjectForSignerId(byte[] signerId) {
    DBObject query = new BasicDBObject();
    query.put("_id", signerId);
    return query;
  }

  // *********** Attachments.

  @Override
  public AttachmentData getAttachment(AttachmentId attachmentId) {

    final GridFSDBFile attachment = attachmentGrid.findOne(attachmentId.serialise());
    return fileToAttachmentData(attachment);
  }

  @Override
  public void storeAttachment(AttachmentId attachmentId, InputStream data)
      throws IOException {
    saveFile(attachmentGrid.createFile(data, attachmentId.serialise()));
  }

  @Override
  public void deleteAttachment(AttachmentId attachmentId) {
    attachmentGrid.remove(attachmentId.serialise());
    thumbnailGrid.remove(attachmentId.serialise());
    metadataGrid.remove(attachmentId.serialise());
  }


  @Override
  public AttachmentMetadata getMetadata(AttachmentId attachmentId) throws IOException {
    final GridFSDBFile metadata = metadataGrid.findOne(attachmentId.serialise());

    if (metadata == null) {
      return null;
    }
    AttachmentProto.AttachmentMetadata protoMetadata =
        AttachmentProto.AttachmentMetadata.parseFrom(metadata.getInputStream());
    return new AttachmentMetadataProtoImpl(protoMetadata);
  }

  @Override
  public AttachmentData getThumbnail(AttachmentId attachmentId) throws IOException {
    final GridFSDBFile thumbnail = thumbnailGrid.findOne(attachmentId.serialise());
    return fileToAttachmentData(thumbnail);
  }

  @Override
  public void storeMetadata(AttachmentId attachmentId, AttachmentMetadata metaData)
      throws IOException {
    AttachmentMetadataProtoImpl proto = new AttachmentMetadataProtoImpl(metaData);
    byte[] bytes = proto.getPB().toByteArray();
    GridFSInputFile file =
        metadataGrid.createFile(new ByteArrayInputStream(bytes), attachmentId.serialise());
    saveFile(file);
  }

  @Override
  public void storeThumbnail(AttachmentId attachmentId, InputStream dataData) throws IOException {
    saveFile(thumbnailGrid.createFile(dataData, attachmentId.serialise()));
  }

  private void saveFile(GridFSInputFile file) throws IOException {
    try {
      file.save();
    } catch (MongoException e) {
      // Unfortunately, file.save() wraps any IOException thrown in a
      // 'MongoException'. Since the interface explicitly throws IOExceptions,
      // we unwrap any IOExceptions thrown.
      Throwable innerException = e.getCause();
      if (innerException instanceof IOException) {
        throw (IOException) innerException;
      } else {
        throw e;
      }
    };
  }

  private AttachmentData fileToAttachmentData(final GridFSDBFile attachmant) {
    if (attachmant == null) {
      return null;
    } else {
      return new AttachmentData() {

        @Override
        public InputStream getInputStream() throws IOException {
          return attachmant.getInputStream();
        }

        @Override
        public long getSize() {
          return attachmant.getLength();
        }
      };
    }
  }

  // ******** AccountStore

  @Override
  public void initializeAccountStore() throws PersistenceException {
    // TODO: Sanity checks not handled by MongoDBProvider???
  }

  @Override
  public AccountData getAccount(ParticipantId id) {
    DBObject query = getDBObjectForParticipant(id);
    DBObject result = getAccountCollection().findOne(query);

    if (result == null) {
      return null;
    }

    DBObject human = (DBObject) result.get(ACCOUNT_HUMAN_DATA_FIELD);
    if (human != null) {
      return objectToHuman(id, human);
    }

    DBObject robot = (DBObject) result.get(ACCOUNT_ROBOT_DATA_FIELD);
    if (robot != null) {
      return objectToRobot(id, robot);
    }

    throw new IllegalStateException("DB object contains neither a human nor a robot");
  }

  @Override
  public AccountData getAccountBySocialIdentity(String provider, String subject)
      throws PersistenceException {
    if (provider == null || provider.trim().isEmpty()
        || subject == null || subject.trim().isEmpty()) {
      return null;
    }
    try {
      DBObject match = new BasicDBObject(SOCIAL_PROVIDER_FIELD,
          provider.trim().toLowerCase(Locale.ROOT))
          .append(SOCIAL_SUBJECT_FIELD, subject.trim());
      DBObject query = new BasicDBObject(
          ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_SOCIAL_IDENTITIES_FIELD,
          new BasicDBObject("$elemMatch", match));
      DBObject result = getAccountCollection().findOne(query);
      if (result == null) {
        return null;
      }
      String idAddress = (String) result.get("_id");
      DBObject human = (DBObject) result.get(ACCOUNT_HUMAN_DATA_FIELD);
      if (human == null) {
        return null;
      }
      return objectToHuman(ParticipantId.ofUnsafe(idAddress), human);
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to look up social identity", e);
    }
  }

  @Override
  public void putAccount(AccountData account) {
    getAccountCollection().save(accountToDBObject(account));
  }

  @Override
  public void updateRobotLastActive(ParticipantId id, long lastActiveAtMillis)
      throws PersistenceException {
    try {
      DBObject query = new BasicDBObject("_id", id.getAddress())
          .append(ACCOUNT_ROBOT_DATA_FIELD, new BasicDBObject("$exists", true));
      DBObject update = new BasicDBObject("$set",
          new BasicDBObject(ACCOUNT_ROBOT_DATA_FIELD + "." + ROBOT_LAST_ACTIVE_AT_FIELD,
              lastActiveAtMillis));
      getAccountCollection().update(query, update);
    } catch (MongoException e) {
      throw new PersistenceException("Failed to update lastActiveAtMillis for robot "
          + id.getAddress(), e);
    }
  }

  @Override
  public void removeAccount(ParticipantId id) {
    DBObject object = getDBObjectForParticipant(id);
    getAccountCollection().remove(object);
  }

  @Override
  public List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress) {
    if (ownerAddress == null || ownerAddress.trim().isEmpty()) {
      return new ArrayList<RobotAccountData>();
    }
    List<RobotAccountData> ownedRobots = new ArrayList<RobotAccountData>();
    DBObject query = new BasicDBObject(ACCOUNT_ROBOT_DATA_FIELD + "." + ROBOT_OWNER_FIELD,
        ownerAddress);
    for (DBObject result : getAccountCollection().find(query)) {
      String idAddress = (String) result.get("_id");
      ParticipantId id = ParticipantId.ofUnsafe(idAddress);
      DBObject robot = (DBObject) result.get(ACCOUNT_ROBOT_DATA_FIELD);
      if (robot != null) {
        ownedRobots.add((RobotAccountData) objectToRobot(id, robot));
      }
    }
    return ownedRobots;
  }

  @Override
  public List<AccountData> getAllAccounts() throws PersistenceException {
    try {
      List<AccountData> result = new ArrayList<AccountData>();
      for (DBObject object : getAccountCollection().find()) {
        String idAddress = (String) object.get("_id");
        DBObject human = (DBObject) object.get(ACCOUNT_HUMAN_DATA_FIELD);
        if (human != null) {
          result.add(objectToHuman(ParticipantId.ofUnsafe(idAddress), human));
        }
      }
      return result;
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to list human accounts", e);
    }
  }

  @Override
  public long getAccountCount() throws PersistenceException {
    try {
      return getAccountCollection().count(
          new BasicDBObject(ACCOUNT_HUMAN_DATA_FIELD, new BasicDBObject("$exists", true)));
    } catch (MongoException e) {
      throw new PersistenceException("Failed to count human accounts", e);
    }
  }

  @Override
  public synchronized AccountCreationResult putNewAccountWithOwnerAssignmentResult(
      AccountData account, SocialIdentity socialIdentity) throws PersistenceException {
    if (getAccount(account.getId()) != null) {
      return AccountCreationResult.ACCOUNT_EXISTS;
    }
    if (socialIdentity != null
        && getAccountBySocialIdentity(socialIdentity.getProvider(), socialIdentity.getSubject())
            != null) {
      return AccountCreationResult.SOCIAL_IDENTITY_EXISTS;
    }
    if (account.isHuman() && getAccountCount() == 0) {
      account.asHuman().setRole(HumanAccountData.ROLE_OWNER);
    }
    try {
      getAccountCollection().insert(accountToDBObject(account));
      return AccountCreationResult.CREATED;
    } catch (MongoException e) {
      if (isDuplicateKey(e)) {
        return duplicateAccountCreationResult(account, socialIdentity);
      }
      throw new PersistenceException("Failed to create account", e);
    }
  }

  private AccountCreationResult duplicateAccountCreationResult(AccountData account,
      SocialIdentity socialIdentity) throws PersistenceException {
    if (getAccount(account.getId()) != null) {
      return AccountCreationResult.ACCOUNT_EXISTS;
    }
    if (socialIdentity != null
        && getAccountBySocialIdentity(socialIdentity.getProvider(), socialIdentity.getSubject())
            != null) {
      return AccountCreationResult.SOCIAL_IDENTITY_EXISTS;
    }
    return AccountCreationResult.ACCOUNT_EXISTS;
  }

  private static boolean isDuplicateKey(MongoException e) {
    // 11000 is the modern unique-index violation code; keep legacy duplicate-key codes for older
    // Mongo servers still reachable through this driver.
    int code = e.getCode();
    return code == 11000 || code == 11001 || code == 12582;
  }

  private DBObject getDBObjectForParticipant(ParticipantId id) {
    DBObject query = new BasicDBObject();
    query.put("_id", id.getAddress());
    return query;
  }

  private DBObject accountToDBObject(AccountData account) {
    DBObject object = getDBObjectForParticipant(account.getId());

    if (account.isHuman()) {
      object.put(ACCOUNT_HUMAN_DATA_FIELD, humanToObject(account.asHuman()));
    } else if (account.isRobot()) {
      object.put(ACCOUNT_ROBOT_DATA_FIELD, robotToObject(account.asRobot()));
    } else {
      throw new IllegalStateException("Account is neither a human nor a robot");
    }
    return object;
  }

  private DBCollection getAccountCollection() {
    return database.getCollection(ACCOUNT_COLLECTION);
  }

  // ****** HumanAccountData serialization

  private DBObject humanToObject(HumanAccountData account) {
    DBObject object = new BasicDBObject();

    PasswordDigest digest = account.getPasswordDigest();
    if (digest != null) {
      DBObject digestObj = new BasicDBObject();
      digestObj.put(PASSWORD_SALT_FIELD, digest.getSalt());
      digestObj.put(PASSWORD_DIGEST_FIELD, digest.getDigest());

      object.put(HUMAN_PASSWORD_FIELD, digestObj);
    }

    // Saved searches
    List<SearchesItem> searches = account.getSearches();
    if (searches != null && !searches.isEmpty()) {
      BasicBSONList searchList = new BasicBSONList();
      for (int i = 0; i < searches.size(); i++) {
        SearchesItem item = searches.get(i);
        DBObject sObj = new BasicDBObject();
        sObj.put(SEARCH_NAME_FIELD, item.getName());
        sObj.put(SEARCH_QUERY_FIELD, item.getQuery());
        sObj.put(SEARCH_PINNED_FIELD, item.isPinned());
        searchList.add(sObj);
      }
      object.put(HUMAN_SEARCHES_FIELD, searchList);
    }

    List<SocialIdentity> identities = account.getSocialIdentities();
    if (identities != null && !identities.isEmpty()) {
      BasicBSONList identityList = new BasicBSONList();
      for (SocialIdentity identity : identities) {
        identityList.add(socialIdentityToObject(identity));
      }
      object.put(HUMAN_SOCIAL_IDENTITIES_FIELD, identityList);
    }

    return object;
  }

  private DBObject socialIdentityToObject(SocialIdentity identity) {
    DBObject object = new BasicDBObject()
        .append(SOCIAL_PROVIDER_FIELD, identity.getProvider())
        .append(SOCIAL_SUBJECT_FIELD, identity.getSubject());
    if (identity.getEmail() != null) {
      object.put(SOCIAL_EMAIL_FIELD, identity.getEmail());
    }
    if (identity.getDisplayName() != null) {
      object.put(SOCIAL_DISPLAY_NAME_FIELD, identity.getDisplayName());
    }
    if (identity.getLinkedAtMillis() != 0L) {
      object.put(SOCIAL_LINKED_AT_FIELD, identity.getLinkedAtMillis());
    }
    return object;
  }

  private HumanAccountData objectToHuman(ParticipantId id, DBObject object) {
    PasswordDigest passwordDigest = null;

    DBObject digestObj = (DBObject) object.get(HUMAN_PASSWORD_FIELD);
    if (digestObj != null) {
      byte[] salt = (byte[]) digestObj.get(PASSWORD_SALT_FIELD);
      byte[] digest = (byte[]) digestObj.get(PASSWORD_DIGEST_FIELD);
      passwordDigest = PasswordDigest.from(salt, digest);
    }

    HumanAccountDataImpl account = new HumanAccountDataImpl(id, passwordDigest);

    // Saved searches
    @SuppressWarnings("unchecked")
    List<DBObject> searchList = (List<DBObject>) object.get(HUMAN_SEARCHES_FIELD);
    if (searchList != null && !searchList.isEmpty()) {
      List<SearchesItem> searches = new ArrayList<>();
      for (DBObject sObj : searchList) {
        String sName = (String) sObj.get(SEARCH_NAME_FIELD);
        String sQuery = (String) sObj.get(SEARCH_QUERY_FIELD);
        Object sPinnedObj = sObj.get(SEARCH_PINNED_FIELD);
        boolean sPinned = sPinnedObj instanceof Boolean && (Boolean) sPinnedObj;
        searches.add(new SearchesItem(
            sName != null ? sName : "", sQuery != null ? sQuery : "", sPinned));
      }
      account.setSearches(searches);
    }

    @SuppressWarnings("unchecked")
    List<DBObject> socialList = (List<DBObject>) object.get(HUMAN_SOCIAL_IDENTITIES_FIELD);
    if (socialList != null && !socialList.isEmpty()) {
      List<SocialIdentity> identities = new ArrayList<SocialIdentity>();
      for (DBObject socialObject : socialList) {
        String provider = (String) socialObject.get(SOCIAL_PROVIDER_FIELD);
        String subject = (String) socialObject.get(SOCIAL_SUBJECT_FIELD);
        if (provider != null && subject != null) {
          identities.add(new SocialIdentity(
              provider,
              subject,
              (String) socialObject.get(SOCIAL_EMAIL_FIELD),
              (String) socialObject.get(SOCIAL_DISPLAY_NAME_FIELD),
              longValue(socialObject.get(SOCIAL_LINKED_AT_FIELD))));
        }
      }
      account.setSocialIdentities(identities);
    }

    return account;
  }

  private static long longValue(Object value) {
    return value instanceof Number ? ((Number) value).longValue() : 0L;
  }

  // ****** RobotAccountData serialization

  private DBObject robotToObject(RobotAccountData account) {
    return new BasicDBObject()
        .append(ROBOT_URL_FIELD, account.getUrl())
        .append(ROBOT_SECRET_FIELD, account.getConsumerSecret())
        .append(ROBOT_CAPABILITIES_FIELD, capabilitiesToObject(account.getCapabilities()))
        .append(ROBOT_VERIFIED_FIELD, account.isVerified())
        .append("tokenExpirySeconds", account.getTokenExpirySeconds())
        .append(ROBOT_OWNER_FIELD, account.getOwnerAddress())
        .append(ROBOT_DESCRIPTION_FIELD, account.getDescription())
        .append(ROBOT_CREATED_AT_FIELD, account.getCreatedAtMillis())
        .append(ROBOT_UPDATED_AT_FIELD, account.getUpdatedAtMillis())
        .append(ROBOT_PAUSED_FIELD, account.isPaused())
        .append(ROBOT_TOKEN_VERSION_FIELD, account.getTokenVersion())
        .append(ROBOT_LAST_ACTIVE_AT_FIELD, account.getLastActiveAtMillis());
  }

  private DBObject capabilitiesToObject(RobotCapabilities capabilities) {
    if (capabilities == null) {
      return null;
    }

    BasicDBObject capabilitiesObj = new BasicDBObject();
    for (Capability capability : capabilities.getCapabilitiesMap().values()) {
      BasicBSONList contexts = new BasicBSONList();
      for (Context c : capability.getContexts()) {
        contexts.add(c.name());
      }
      capabilitiesObj.put(capability.getEventType().name(),
          new BasicDBObject()
              .append(CAPABILITY_CONTEXTS_FIELD, contexts)
              .append(CAPABILITY_FILTER_FIELD, capability.getFilter()));
    }

    BasicDBObject object =
        new BasicDBObject()
            .append(CAPABILITIES_CAPABILITIES_FIELD, capabilitiesObj)
            .append(CAPABILITIES_HASH_FIELD, capabilities.getCapabilitiesHash())
            .append(CAPABILITIES_VERSION_FIELD, capabilities.getProtocolVersion().name())
            .append(CAPABILITIES_RPC_SERVER_URL_FIELD, capabilities.getRpcServerUrl());

    return object;
  }

  private AccountData objectToRobot(ParticipantId id, DBObject robot) {
    String url = (String) robot.get(ROBOT_URL_FIELD);
    String secret = (String) robot.get(ROBOT_SECRET_FIELD);
    RobotCapabilities capabilities =
        objectToCapabilities((DBObject) robot.get(ROBOT_CAPABILITIES_FIELD));
    boolean verified = (Boolean) robot.get(ROBOT_VERIFIED_FIELD);
    Object tokenExpiryObj = robot.get("tokenExpirySeconds");
    long tokenExpirySeconds = tokenExpiryObj instanceof Number ? ((Number) tokenExpiryObj).longValue() : 0L;
    String ownerAddress = (String) robot.get(ROBOT_OWNER_FIELD);
    String description = (String) robot.get(ROBOT_DESCRIPTION_FIELD);
    Object createdAtObj = robot.get(ROBOT_CREATED_AT_FIELD);
    Object updatedAtObj = robot.get(ROBOT_UPDATED_AT_FIELD);
    long createdAtMillis = createdAtObj instanceof Number ? ((Number) createdAtObj).longValue() : 0L;
    long updatedAtMillis = updatedAtObj instanceof Number ? ((Number) updatedAtObj).longValue() : 0L;
    boolean paused = Boolean.TRUE.equals(robot.get(ROBOT_PAUSED_FIELD));
    Object tokenVersionObj = robot.get(ROBOT_TOKEN_VERSION_FIELD);
    long tokenVersion = tokenVersionObj instanceof Number ? ((Number) tokenVersionObj).longValue() : 0L;
    Object lastActiveAtObj = robot.get(ROBOT_LAST_ACTIVE_AT_FIELD);
    long lastActiveAtMillis =
        lastActiveAtObj instanceof Number ? ((Number) lastActiveAtObj).longValue() : 0L;
    return new RobotAccountDataImpl(id, url, secret, capabilities, verified, tokenExpirySeconds,
        ownerAddress, description != null ? description : "",
        createdAtMillis, updatedAtMillis, paused, tokenVersion, lastActiveAtMillis);
  }

  @SuppressWarnings("unchecked")
  private RobotCapabilities objectToCapabilities(DBObject object) {
    if (object == null) {
      return null;
    }

    Map<String, Object> capabilitiesObj =
	(Map<String, Object>) object.get(CAPABILITIES_CAPABILITIES_FIELD);
    Map<EventType, Capability> capabilities = CollectionUtils.newHashMap();

    for (Entry<String, Object> capability : capabilitiesObj.entrySet()) {
      EventType eventType = EventType.valueOf(capability.getKey());
      List<Context> contexts = CollectionUtils.newArrayList();
      DBObject capabilityObj = (DBObject) capability.getValue();
      DBObject contextsObj = (DBObject) capabilityObj.get(CAPABILITY_CONTEXTS_FIELD);
      for (String contextId : contextsObj.keySet()) {
        contexts.add(Context.valueOf((String) contextsObj.get(contextId)));
      }
      String filter = (String) capabilityObj.get(CAPABILITY_FILTER_FIELD);

      capabilities.put(eventType, new Capability(eventType, contexts, filter));
    }

    String capabilitiesHash = (String) object.get(CAPABILITIES_HASH_FIELD);
    ProtocolVersion version =
        ProtocolVersion.valueOf((String) object.get(CAPABILITIES_VERSION_FIELD));
    String rpcServerUrl = (String) object.get(CAPABILITIES_RPC_SERVER_URL_FIELD);

    return new RobotCapabilities(capabilities, capabilitiesHash, version,
        rpcServerUrl != null ? rpcServerUrl : "");
  }
}

package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import com.google.wave.api.robot.Capability;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;
import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

/** MongoDB 4.x AccountStore implementation (human + robot). */
final class Mongo4AccountStore implements AccountStore {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Mongo4AccountStore.class.getName());

  private static final String ACCOUNT_COLLECTION = "account";
  private static final String ACCOUNT_HUMAN_DATA_FIELD = "human";
  private static final String ACCOUNT_ROBOT_DATA_FIELD = "robot";

  private static final String HUMAN_PASSWORD_FIELD = "passwordDigest";
  private static final String HUMAN_EMAIL_FIELD = "email";
  private static final String HUMAN_EMAIL_CONFIRMED_FIELD = "emailConfirmed";
  private static final String HUMAN_ROLE_FIELD = "role";
  private static final String HUMAN_STATUS_FIELD = "status";
  private static final String HUMAN_TIER_FIELD = "tier";
  private static final String HUMAN_REGISTRATION_TIME_FIELD = "registrationTime";
  private static final String HUMAN_LAST_LOGIN_TIME_FIELD = "lastLoginTime";
  private static final String HUMAN_LAST_ACTIVITY_TIME_FIELD = "lastActivityTime";
  private static final String HUMAN_FIRST_NAME_FIELD = "firstName";
  private static final String HUMAN_LAST_NAME_FIELD = "lastName";
  private static final String HUMAN_BIO_FIELD = "bio";
  private static final String HUMAN_PROFILE_IMAGE_FIELD = "profileImageAttachmentId";
  private static final String HUMAN_SHOW_LAST_SEEN_FIELD = "showLastSeen";
  private static final String HUMAN_SEARCHES_FIELD = "searches";
  private static final String HUMAN_SOCIAL_IDENTITIES_FIELD = "socialIdentities";
  private static final String SEARCH_NAME_FIELD = "name";
  private static final String SEARCH_QUERY_FIELD = "query";
  private static final String SEARCH_PINNED_FIELD = "pinned";
  private static final String PASSWORD_DIGEST_FIELD = "digest";
  private static final String PASSWORD_SALT_FIELD = "salt";
  private static final String SOCIAL_PROVIDER_FIELD = "provider";
  private static final String SOCIAL_SUBJECT_FIELD = "subject";
  private static final String SOCIAL_EMAIL_FIELD = "email";
  private static final String SOCIAL_DISPLAY_NAME_FIELD = "displayName";
  private static final String SOCIAL_LINKED_AT_FIELD = "linkedAtMillis";

  private static final String ROBOT_URL_FIELD = "url";
  private static final String ROBOT_SECRET_FIELD = "secret";
  private static final String ROBOT_CAPABILITIES_FIELD = "capabilities";
  private static final String ROBOT_VERIFIED_FIELD = "verified";
  private static final String ROBOT_TOKEN_EXPIRY_FIELD = "tokenExpirySeconds";
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

  private final MongoCollection<Document> col;

  Mongo4AccountStore(MongoDatabase db) {
    this.col = db.getCollection(ACCOUNT_COLLECTION);
  }

  @Override
  public void initializeAccountStore() throws PersistenceException { /* no-op */ }

  @Override
  public AccountData getAccount(ParticipantId id) throws PersistenceException {
    try {
      Document doc = col.find(eq("_id", id.getAddress())).first();
      if (doc == null) return null;
      Document human = (Document) doc.get(ACCOUNT_HUMAN_DATA_FIELD);
      if (human != null) return objectToHuman(id, human);
      Document robot = (Document) doc.get(ACCOUNT_ROBOT_DATA_FIELD);
      if (robot != null) return objectToRobot(id, robot);
      throw new IllegalStateException("Account doc has neither human nor robot");
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void putAccount(AccountData account) throws PersistenceException {
    try {
      Document doc = accountToDocument(account);
      col.replaceOne(eq("_id", account.getId().getAddress()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void updateRobotLastActive(ParticipantId id, long lastActiveAtMillis)
      throws PersistenceException {
    try {
      col.updateOne(
          and(eq("_id", id.getAddress()), exists(ACCOUNT_ROBOT_DATA_FIELD)),
          set(ACCOUNT_ROBOT_DATA_FIELD + "." + ROBOT_LAST_ACTIVE_AT_FIELD, lastActiveAtMillis));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void updateHumanLoginTimestamps(ParticipantId id, long lastLoginTime,
      long lastActivityTime) throws PersistenceException {
    try {
      col.updateOne(
          and(eq("_id", id.getAddress()), exists(ACCOUNT_HUMAN_DATA_FIELD)),
          combine(
              set(ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_LAST_LOGIN_TIME_FIELD, lastLoginTime),
              set(ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_LAST_ACTIVITY_TIME_FIELD,
                  lastActivityTime)));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void removeAccount(ParticipantId id) throws PersistenceException {
    try {
      col.deleteOne(eq("_id", id.getAddress()));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public AccountData getAccountByEmail(String email) throws PersistenceException {
    if (email == null || email.isEmpty()) return null;
    try {
      String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
      if (normalizedEmail.isEmpty()) return null;
      Document doc = col.find(eq(
          ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_EMAIL_FIELD, normalizedEmail)).first();
      if (doc == null) {
        doc = col.find(regex(
            ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_EMAIL_FIELD,
            "^" + Pattern.quote(normalizedEmail) + "$",
            "i")).first();
      }
      if (doc == null) return null;
      String idStr = doc.getString("_id");
      ParticipantId id = ParticipantId.ofUnsafe(idStr);
      Document human = (Document) doc.get(ACCOUNT_HUMAN_DATA_FIELD);
      if (human != null) return objectToHuman(id, human);
      return null;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public AccountData getAccountBySocialIdentity(String provider, String subject)
      throws PersistenceException {
    if (provider == null || provider.isEmpty() || subject == null || subject.isEmpty()) return null;
    try {
      Document match = new Document(SOCIAL_PROVIDER_FIELD, provider.trim().toLowerCase(Locale.ROOT))
          .append(SOCIAL_SUBJECT_FIELD, subject.trim());
      Document doc = col.find(elemMatch(
          ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_SOCIAL_IDENTITIES_FIELD, match)).first();
      if (doc == null) return null;
      String idStr = doc.getString("_id");
      ParticipantId id = ParticipantId.ofUnsafe(idStr);
      Document human = (Document) doc.get(ACCOUNT_HUMAN_DATA_FIELD);
      if (human != null) return objectToHuman(id, human);
      return null;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void linkSocialIdentity(ParticipantId id, SocialIdentity socialIdentity)
      throws PersistenceException {
    try {
      UpdateResult result = col.updateOne(
          and(eq("_id", id.getAddress()), exists(ACCOUNT_HUMAN_DATA_FIELD)),
          List.of(replaceSocialIdentityStage(socialIdentity)));
      if (result.getMatchedCount() == 0L) {
        throw new PersistenceException("No human account found for " + id);
      }
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void putAccountWithUniqueSocialIdentity(AccountData account, SocialIdentity socialIdentity)
      throws PersistenceException {
    synchronized (this) {
      // Existing-account updates rely on the same social-identity unique index as account creation;
      // the pre-check keeps the error path user-friendly within this store instance.
      AccountData existing = getAccountBySocialIdentity(
          socialIdentity.getProvider(), socialIdentity.getSubject());
      if (existing != null && !existing.getId().equals(account.getId())) {
        throw new PersistenceException("Social identity is already linked");
      }
      putAccount(account);
    }
  }

  @Override
  public synchronized AccountCreationResult putNewAccountWithOwnerAssignmentResult(AccountData account,
      SocialIdentity socialIdentity) throws PersistenceException {
    try {
      if (getAccount(account.getId()) != null) {
        return AccountCreationResult.ACCOUNT_EXISTS;
      }
      if (socialIdentity != null) {
        AccountData existing = getAccountBySocialIdentity(
            socialIdentity.getProvider(), socialIdentity.getSubject());
        if (existing != null) {
          return AccountCreationResult.SOCIAL_IDENTITY_EXISTS;
        }
      }
      if (account.isHuman() && getAccountCount() == 0) {
        account.asHuman().setRole(HumanAccountData.ROLE_OWNER);
      }
      col.insertOne(accountToDocument(account));
      return AccountCreationResult.CREATED;
    } catch (MongoWriteException e) {
      if (e.getError() != null
          && e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
        return duplicateAccountCreationResult(account, socialIdentity);
      }
      throw new PersistenceException(e);
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
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
    // Unexpected duplicate-key source; preserve the account-collision ordering rule.
    return AccountCreationResult.ACCOUNT_EXISTS;
  }

  @Override
  public List<AccountData> getAllAccounts() throws PersistenceException {
    try {
      List<AccountData> result = new ArrayList<>();
      for (Document doc : col.find()) {
        String idStr = doc.getString("_id");
        ParticipantId id = ParticipantId.ofUnsafe(idStr);
        Document human = (Document) doc.get(ACCOUNT_HUMAN_DATA_FIELD);
        if (human != null) {
          result.add(objectToHuman(id, human));
        }
        // Intentionally skip robots — admin page only shows human accounts
      }
      return result;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress)
      throws PersistenceException {
    if (ownerAddress == null || ownerAddress.trim().isEmpty()) {
      return new ArrayList<>();
    }
    try {
      List<RobotAccountData> ownedRobots = new ArrayList<>();
      for (Document doc : col.find(eq(ACCOUNT_ROBOT_DATA_FIELD + "." + ROBOT_OWNER_FIELD, ownerAddress))) {
        String idStr = doc.getString("_id");
        ParticipantId id = ParticipantId.ofUnsafe(idStr);
        Document robot = (Document) doc.get(ACCOUNT_ROBOT_DATA_FIELD);
        if (robot != null) {
          ownedRobots.add((RobotAccountData) objectToRobot(id, robot));
        }
      }
      return ownedRobots;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public long getAccountCount() throws PersistenceException {
    try {
      return col.countDocuments(humanAccountsFilter());
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  /**
   * Returns a filter for documents that contain a human account.
   * Robot agents also live in this collection but must be excluded
   * to prevent them from affecting first-user owner assignment.
   */
  private static org.bson.conversions.Bson humanAccountsFilter() {
    return exists(ACCOUNT_HUMAN_DATA_FIELD);
  }

  private static Document accountToDocument(AccountData account) {
    Document doc = new Document("_id", account.getId().getAddress());
    if (account.isHuman()) {
      doc.append(ACCOUNT_HUMAN_DATA_FIELD, humanToObject(account.asHuman()));
    } else if (account.isRobot()) {
      doc.append(ACCOUNT_ROBOT_DATA_FIELD, robotToObject(account.asRobot()));
    } else {
      throw new IllegalStateException("Account is neither human nor robot");
    }
    return doc;
  }

  private static Document humanToObject(HumanAccountData account) {
    Document doc = new Document();
    PasswordDigest digest = account.getPasswordDigest();
    if (digest != null) {
      doc.append(HUMAN_PASSWORD_FIELD, new Document()
          .append(PASSWORD_SALT_FIELD, new Binary(digest.getSalt()))
          .append(PASSWORD_DIGEST_FIELD, new Binary(digest.getDigest())));
    }
    if (account.getEmail() != null) {
      doc.append(HUMAN_EMAIL_FIELD, account.getEmail());
    }
    doc.append(HUMAN_EMAIL_CONFIRMED_FIELD, account.isEmailConfirmed());
    // Admin / role / status fields
    doc.append(HUMAN_ROLE_FIELD, account.getRole());
    doc.append(HUMAN_STATUS_FIELD, account.getStatus());
    doc.append(HUMAN_TIER_FIELD, account.getTier());
    if (account.getRegistrationTime() != 0) {
      doc.append(HUMAN_REGISTRATION_TIME_FIELD, account.getRegistrationTime());
    }
    if (account.getLastLoginTime() != 0) {
      doc.append(HUMAN_LAST_LOGIN_TIME_FIELD, account.getLastLoginTime());
    }
    if (account.getLastActivityTime() != 0) {
      doc.append(HUMAN_LAST_ACTIVITY_TIME_FIELD, account.getLastActivityTime());
    }
    // Profile fields
    if (account.getFirstName() != null) {
      doc.append(HUMAN_FIRST_NAME_FIELD, account.getFirstName());
    }
    if (account.getLastName() != null) {
      doc.append(HUMAN_LAST_NAME_FIELD, account.getLastName());
    }
    if (account.getBio() != null) {
      doc.append(HUMAN_BIO_FIELD, account.getBio());
    }
    if (account.getProfileImageAttachmentId() != null) {
      doc.append(HUMAN_PROFILE_IMAGE_FIELD, account.getProfileImageAttachmentId());
    }
    doc.append(HUMAN_SHOW_LAST_SEEN_FIELD, account.isShowLastSeen());
    // Saved searches
    List<SearchesItem> searches = account.getSearches();
    if (searches != null && !searches.isEmpty()) {
      List<Document> searchDocs = new ArrayList<>();
      for (SearchesItem item : searches) {
        searchDocs.add(new Document()
            .append(SEARCH_NAME_FIELD, item.getName())
            .append(SEARCH_QUERY_FIELD, item.getQuery())
            .append(SEARCH_PINNED_FIELD, item.isPinned()));
      }
      doc.append(HUMAN_SEARCHES_FIELD, searchDocs);
    }
    List<SocialIdentity> identities = account.getSocialIdentities();
    if (identities != null && !identities.isEmpty()) {
      List<Document> identityDocs = new ArrayList<>();
      for (SocialIdentity identity : identities) {
        identityDocs.add(socialIdentityToObject(identity));
      }
      doc.append(HUMAN_SOCIAL_IDENTITIES_FIELD, identityDocs);
    }
    return doc;
  }

  private static Document socialIdentityToObject(SocialIdentity identity) {
    Document doc = new Document()
        .append(SOCIAL_PROVIDER_FIELD, identity.getProvider())
        .append(SOCIAL_SUBJECT_FIELD, identity.getSubject());
    if (identity.getEmail() != null) {
      doc.append(SOCIAL_EMAIL_FIELD, identity.getEmail());
    }
    if (identity.getDisplayName() != null) {
      doc.append(SOCIAL_DISPLAY_NAME_FIELD, identity.getDisplayName());
    }
    if (identity.getLinkedAtMillis() != 0L) {
      doc.append(SOCIAL_LINKED_AT_FIELD, identity.getLinkedAtMillis());
    }
    return doc;
  }

  private static Document replaceSocialIdentityStage(SocialIdentity identity) {
    String identityPath = ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_SOCIAL_IDENTITIES_FIELD;
    Document existingDoesNotMatch = new Document("$not", List.of(new Document("$and", List.of(
        new Document("$eq", List.of("$$identity." + SOCIAL_PROVIDER_FIELD, identity.getProvider())),
        new Document("$eq", List.of("$$identity." + SOCIAL_SUBJECT_FIELD, identity.getSubject()))))));
    Document existingWithoutIdentity = new Document("$filter", new Document()
        .append("input", new Document("$ifNull", List.of("$" + identityPath, List.of())))
        .append("as", "identity")
        .append("cond", existingDoesNotMatch));
    return new Document("$set", new Document(identityPath, new Document("$concatArrays", List.of(
        existingWithoutIdentity,
        List.of(socialIdentityToObject(identity))))));
  }

  private static HumanAccountData objectToHuman(ParticipantId id, Document doc) {
    PasswordDigest pd = null;
    Document d = (Document) doc.get(HUMAN_PASSWORD_FIELD);
    if (d != null) {
      Binary salt = (Binary) d.get(PASSWORD_SALT_FIELD);
      Binary dig = (Binary) d.get(PASSWORD_DIGEST_FIELD);
      if (salt != null && dig != null) pd = PasswordDigest.from(salt.getData(), dig.getData());
    }
    HumanAccountDataImpl account = new HumanAccountDataImpl(id, pd);
    String email = doc.getString(HUMAN_EMAIL_FIELD);
    if (email != null) {
      account.setEmail(email);
    }
    Boolean emailConfirmed = doc.getBoolean(HUMAN_EMAIL_CONFIRMED_FIELD);
    if (emailConfirmed != null) {
      account.setEmailConfirmed(emailConfirmed);
    }
    // Admin / role / status fields
    String role = doc.getString(HUMAN_ROLE_FIELD);
    if (role != null) {
      account.setRole(role);
    }
    String status = doc.getString(HUMAN_STATUS_FIELD);
    if (status != null) {
      account.setStatus(status);
    }
    String tier = doc.getString(HUMAN_TIER_FIELD);
    if (tier != null) {
      account.setTier(tier);
    }
    Long registrationTime = doc.getLong(HUMAN_REGISTRATION_TIME_FIELD);
    if (registrationTime != null) {
      account.setRegistrationTime(registrationTime);
    }
    Long lastLoginTime = doc.getLong(HUMAN_LAST_LOGIN_TIME_FIELD);
    if (lastLoginTime != null) {
      account.setLastLoginTime(lastLoginTime);
    }
    Long lastActivityTime = doc.getLong(HUMAN_LAST_ACTIVITY_TIME_FIELD);
    if (lastActivityTime != null) {
      account.setLastActivityTime(lastActivityTime);
    }
    // Profile fields
    String firstName = doc.getString(HUMAN_FIRST_NAME_FIELD);
    if (firstName != null) {
      account.setFirstName(firstName);
    }
    String lastName = doc.getString(HUMAN_LAST_NAME_FIELD);
    if (lastName != null) {
      account.setLastName(lastName);
    }
    String bio = doc.getString(HUMAN_BIO_FIELD);
    if (bio != null) {
      account.setBio(bio);
    }
    String profileImageId = doc.getString(HUMAN_PROFILE_IMAGE_FIELD);
    if (profileImageId != null) {
      account.setProfileImageAttachmentId(profileImageId);
    }
    Boolean showLastSeen = doc.getBoolean(HUMAN_SHOW_LAST_SEEN_FIELD);
    if (showLastSeen != null) {
      account.setShowLastSeen(showLastSeen);
    }
    // Saved searches
    List<?> searchList = (List<?>) doc.get(HUMAN_SEARCHES_FIELD);
    if (searchList != null && !searchList.isEmpty()) {
      List<SearchesItem> searches = new ArrayList<>();
      for (Object obj : searchList) {
        Document sDoc = (Document) obj;
        String sName = sDoc.getString(SEARCH_NAME_FIELD);
        String sQuery = sDoc.getString(SEARCH_QUERY_FIELD);
        Boolean sPinned = sDoc.getBoolean(SEARCH_PINNED_FIELD);
        searches.add(new SearchesItem(
            sName != null ? sName : "", sQuery != null ? sQuery : "",
            sPinned != null && sPinned));
      }
      account.setSearches(searches);
    }
    List<?> socialList = (List<?>) doc.get(HUMAN_SOCIAL_IDENTITIES_FIELD);
    if (socialList != null && !socialList.isEmpty()) {
      List<SocialIdentity> identities = new ArrayList<>();
      for (Object obj : socialList) {
        Document sDoc = (Document) obj;
        String provider = sDoc.getString(SOCIAL_PROVIDER_FIELD);
        String subject = sDoc.getString(SOCIAL_SUBJECT_FIELD);
        if (provider != null && subject != null) {
          Long linkedAt = sDoc.getLong(SOCIAL_LINKED_AT_FIELD);
          identities.add(new SocialIdentity(
              provider,
              subject,
              sDoc.getString(SOCIAL_EMAIL_FIELD),
              sDoc.getString(SOCIAL_DISPLAY_NAME_FIELD),
              linkedAt != null ? linkedAt : 0L));
        }
      }
      account.setSocialIdentities(identities);
    }
    return account;
  }

  private static Document robotToObject(RobotAccountData account) {
    return new Document()
        .append(ROBOT_URL_FIELD, account.getUrl())
        .append(ROBOT_SECRET_FIELD, account.getConsumerSecret())
        .append(ROBOT_CAPABILITIES_FIELD, capabilitiesToObject(account.getCapabilities()))
        .append(ROBOT_VERIFIED_FIELD, account.isVerified())
        .append(ROBOT_TOKEN_EXPIRY_FIELD, account.getTokenExpirySeconds())
        .append(ROBOT_OWNER_FIELD, account.getOwnerAddress())
        .append(ROBOT_DESCRIPTION_FIELD, account.getDescription())
        .append(ROBOT_CREATED_AT_FIELD, account.getCreatedAtMillis())
        .append(ROBOT_UPDATED_AT_FIELD, account.getUpdatedAtMillis())
        .append(ROBOT_PAUSED_FIELD, account.isPaused())
        .append(ROBOT_TOKEN_VERSION_FIELD, account.getTokenVersion())
        .append(ROBOT_LAST_ACTIVE_AT_FIELD, account.getLastActiveAtMillis());
  }

  private static Document capabilitiesToObject(RobotCapabilities caps) {
    if (caps == null) return null;
    Document capsMap = new Document();
    for (Capability c : caps.getCapabilitiesMap().values()) {
      List<String> ctx = CollectionUtils.newArrayList();
      for (Context k : c.getContexts()) ctx.add(k.name());
      capsMap.append(c.getEventType().name(), new Document()
          .append(CAPABILITY_CONTEXTS_FIELD, ctx)
          .append(CAPABILITY_FILTER_FIELD, c.getFilter()));
    }
    return new Document()
        .append(CAPABILITIES_CAPABILITIES_FIELD, capsMap)
        .append(CAPABILITIES_HASH_FIELD, caps.getCapabilitiesHash())
        .append(CAPABILITIES_VERSION_FIELD, caps.getProtocolVersion().name())
        .append(CAPABILITIES_RPC_SERVER_URL_FIELD, caps.getRpcServerUrl());
  }

  private static AccountData objectToRobot(ParticipantId id, Document robot) {
    String url = robot.getString(ROBOT_URL_FIELD);
    String secret = robot.getString(ROBOT_SECRET_FIELD);
    RobotCapabilities caps = objectToCapabilities((Document) robot.get(ROBOT_CAPABILITIES_FIELD));
    boolean verified = Boolean.TRUE.equals(robot.getBoolean(ROBOT_VERIFIED_FIELD));
    Long tokenExpiry = robot.getLong(ROBOT_TOKEN_EXPIRY_FIELD);
    long tokenExpirySeconds = tokenExpiry != null ? tokenExpiry : 0L;
    String ownerAddress = robot.getString(ROBOT_OWNER_FIELD);
    String description = robot.getString(ROBOT_DESCRIPTION_FIELD);
    Long createdAt = robot.getLong(ROBOT_CREATED_AT_FIELD);
    Long updatedAt = robot.getLong(ROBOT_UPDATED_AT_FIELD);
    boolean paused = Boolean.TRUE.equals(robot.getBoolean(ROBOT_PAUSED_FIELD));
    Long tokenVer = robot.getLong(ROBOT_TOKEN_VERSION_FIELD);
    long tokenVersion = tokenVer != null ? tokenVer : 0L;
    Long lastActiveAt = robot.getLong(ROBOT_LAST_ACTIVE_AT_FIELD);
    return new RobotAccountDataImpl(id, url, secret, caps, verified, tokenExpirySeconds,
        ownerAddress, description != null ? description : "",
        createdAt != null ? createdAt : 0L,
        updatedAt != null ? updatedAt : 0L,
        paused, tokenVersion, lastActiveAt != null ? lastActiveAt : 0L);
  }

  private static RobotCapabilities objectToCapabilities(Document obj) {
    if (obj == null) return null;
    Document cmap = (Document) obj.get(CAPABILITIES_CAPABILITIES_FIELD);
    Map<EventType, Capability> map = CollectionUtils.newHashMap();
    for (Map.Entry<String, Object> e : cmap.entrySet()) {
      EventType type = EventType.valueOf(e.getKey());
      Document cdoc = (Document) e.getValue();
      List<Context> ctx = CollectionUtils.newArrayList();
      List<?> ctxList = (List<?>) cdoc.get(CAPABILITY_CONTEXTS_FIELD);
      if (ctxList != null) for (Object s : ctxList) ctx.add(Context.valueOf(String.valueOf(s)));
      String filter = (String) cdoc.get(CAPABILITY_FILTER_FIELD);
      map.put(type, new Capability(type, ctx, filter));
    }
    String hash = (String) obj.get(CAPABILITIES_HASH_FIELD);
    ProtocolVersion ver = ProtocolVersion.valueOf((String) obj.get(CAPABILITIES_VERSION_FIELD));
    String rpcServerUrl = (String) obj.get(CAPABILITIES_RPC_SERVER_URL_FIELD);
    return new RobotCapabilities(map, hash, ver, rpcServerUrl != null ? rpcServerUrl : "");
  }
}

package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
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
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

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
  private static final String PASSWORD_DIGEST_FIELD = "digest";
  private static final String PASSWORD_SALT_FIELD = "salt";

  private static final String ROBOT_URL_FIELD = "url";
  private static final String ROBOT_SECRET_FIELD = "secret";
  private static final String ROBOT_CAPABILITIES_FIELD = "capabilities";
  private static final String ROBOT_VERIFIED_FIELD = "verified";
  private static final String ROBOT_TOKEN_EXPIRY_FIELD = "tokenExpirySeconds";

  private static final String CAPABILITIES_VERSION_FIELD = "version";
  private static final String CAPABILITIES_HASH_FIELD = "capabilitiesHash";
  private static final String CAPABILITIES_CAPABILITIES_FIELD = "capabilities";
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
      Document doc = new Document("_id", account.getId().getAddress());
      if (account.isHuman()) {
        doc.append(ACCOUNT_HUMAN_DATA_FIELD, humanToObject(account.asHuman()));
      } else if (account.isRobot()) {
        doc.append(ACCOUNT_ROBOT_DATA_FIELD, robotToObject(account.asRobot()));
      } else {
        throw new IllegalStateException("Account is neither human nor robot");
      }
      col.replaceOne(eq("_id", account.getId().getAddress()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
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
      Document doc = col.find(eq(ACCOUNT_HUMAN_DATA_FIELD + "." + HUMAN_EMAIL_FIELD, email)).first();
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
  public long getAccountCount() throws PersistenceException {
    try {
      return col.countDocuments();
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
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
    return doc;
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
    return account;
  }

  private static Document robotToObject(RobotAccountData account) {
    return new Document()
        .append(ROBOT_URL_FIELD, account.getUrl())
        .append(ROBOT_SECRET_FIELD, account.getConsumerSecret())
        .append(ROBOT_CAPABILITIES_FIELD, capabilitiesToObject(account.getCapabilities()))
        .append(ROBOT_VERIFIED_FIELD, account.isVerified())
        .append(ROBOT_TOKEN_EXPIRY_FIELD, account.getTokenExpirySeconds());
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
        .append(CAPABILITIES_VERSION_FIELD, caps.getProtocolVersion().name());
  }

  private static AccountData objectToRobot(ParticipantId id, Document robot) {
    String url = robot.getString(ROBOT_URL_FIELD);
    String secret = robot.getString(ROBOT_SECRET_FIELD);
    RobotCapabilities caps = objectToCapabilities((Document) robot.get(ROBOT_CAPABILITIES_FIELD));
    boolean verified = Boolean.TRUE.equals(robot.getBoolean(ROBOT_VERIFIED_FIELD));
    Long tokenExpiry = robot.getLong(ROBOT_TOKEN_EXPIRY_FIELD);
    long tokenExpirySeconds = tokenExpiry != null ? tokenExpiry : 0L;
    return new RobotAccountDataImpl(id, url, secret, caps, verified, tokenExpirySeconds);
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
    return new RobotCapabilities(map, hash, ver);
  }
}

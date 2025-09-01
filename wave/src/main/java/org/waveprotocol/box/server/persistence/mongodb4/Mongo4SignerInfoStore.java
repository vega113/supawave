package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.wave.crypto.SignatureException;
import org.waveprotocol.wave.crypto.SignerInfo;
import org.waveprotocol.wave.federation.Proto.ProtocolSignerInfo;

import static com.mongodb.client.model.Filters.eq;

/** MongoDB 4.x SignerInfoStore */
final class Mongo4SignerInfoStore implements SignerInfoStore {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Mongo4SignerInfoStore.class.getName());
  private final MongoCollection<Document> col;

  Mongo4SignerInfoStore(MongoDatabase db) {
    this.col = db.getCollection("signerInfo");
  }

  @Override
  public void initializeSignerInfoStore() throws PersistenceException {
    // No-op for now
  }

  @Override
  public SignerInfo getSignerInfo(byte[] signerId) {
    Document doc = col.find(eq("_id", new Binary(signerId))).first();
    if (doc == null) return null;
    Binary b = (Binary) doc.get("protoBuff");
    byte[] bytes = b != null ? b.getData() : null;
    if (bytes == null) return null;
    try {
      return new SignerInfo(ProtocolSignerInfo.parseFrom(bytes));
    } catch (Exception e) {
      LOG.warning("Failed to parse ProtocolSignerInfo for signer: " + java.util.Arrays.toString(signerId) + ", err=" + e.getMessage());
      return null;
    }
  }

  @Override
  public void putSignerInfo(ProtocolSignerInfo protocolSignerInfo) throws SignatureException {
    try {
      SignerInfo si = new SignerInfo(protocolSignerInfo);
      Document doc = new Document("_id", new Binary(si.getSignerId()))
          .append("protoBuff", new Binary(protocolSignerInfo.toByteArray()));
      col.replaceOne(eq("_id", new Binary(si.getSignerId())), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    } catch (SignatureException e) {
      throw e;
    } catch (Exception e) {
      throw new SignatureException(e);
    }
  }
}

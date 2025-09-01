package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSDownloadOptions;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.attachment.AttachmentProto;
import org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.wave.media.model.AttachmentId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** MongoDB 4.x AttachmentStore backed by GridFS buckets. */
final class Mongo4AttachmentStore implements AttachmentStore {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Mongo4AttachmentStore.class.getName());
  private final GridFSBucket attachments;
  private final GridFSBucket thumbnails;
  private final GridFSBucket metadata;

  Mongo4AttachmentStore(MongoDatabase db) {
    this.attachments = GridFSBuckets.create(db, "attachments");
    this.thumbnails = GridFSBuckets.create(db, "thumbnails");
    this.metadata = GridFSBuckets.create(db, "metadata");
  }

  @Override
  public AttachmentMetadata getMetadata(AttachmentId attachmentId) throws IOException {
    String key = attachmentId.serialise();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      metadata.downloadToStream(key, out);
    } catch (Exception e) {
      LOG.warning("getMetadata failed for key=" + key + ": " + e.getMessage());
      return null;
    } finally {
      try { out.close(); } catch (Exception ignore) {}
    }
    byte[] bytes = out.toByteArray();
    if (bytes.length == 0) return null;
    try {
      AttachmentProto.AttachmentMetadata pb = AttachmentProto.AttachmentMetadata.parseFrom(bytes);
      return new AttachmentMetadataProtoImpl(pb);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public AttachmentData getAttachment(AttachmentId attachmentId) throws IOException {
    return readFromBucket(attachments, attachmentId);
  }

  @Override
  public AttachmentData getThumbnail(AttachmentId attachmentId) throws IOException {
    return readFromBucket(thumbnails, attachmentId);
  }

  @Override
  public void storeMetadata(AttachmentId attachmentId, AttachmentMetadata metaData) throws IOException {
    AttachmentMetadataProtoImpl proto = new AttachmentMetadataProtoImpl(metaData);
    byte[] bytes = proto.getPB().toByteArray();
    writeToBucket(metadata, attachmentId, new ByteArrayInputStream(bytes));
  }

  @Override
  public void storeAttachment(AttachmentId attachmentId, InputStream data) throws IOException {
    writeToBucket(attachments, attachmentId, data);
  }

  @Override
  public void storeThumbnail(AttachmentId attachmentId, InputStream data) throws IOException {
    writeToBucket(thumbnails, attachmentId, data);
  }

  @Override
  public void deleteAttachment(AttachmentId attachmentId) {
    String key = attachmentId.serialise();
    try { attachments.find().forEach(f -> { if (key.equals(f.getFilename())) attachments.delete(f.getObjectId()); }); } catch (Exception ignored) {}
    try { thumbnails.find().forEach(f -> { if (key.equals(f.getFilename())) thumbnails.delete(f.getObjectId()); }); } catch (Exception ignored) {}
    try { metadata.find().forEach(f -> { if (key.equals(f.getFilename())) metadata.delete(f.getObjectId()); }); } catch (Exception ignored) {}
  }

  private static AttachmentData readFromBucket(GridFSBucket bucket, AttachmentId id) {
    String key = id.serialise();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      bucket.downloadToStream(key, out, new GridFSDownloadOptions());
      byte[] data = out.toByteArray();
      if (data.length == 0) return null;
      return new AttachmentData() {
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public long getSize() { return data.length; }
      };
    } catch (Exception e) {
      LOG.warning("readFromBucket failed for key=" + key + ": " + e.getMessage());
      return null;
    }
  }

  private static void writeToBucket(GridFSBucket bucket, AttachmentId id, InputStream data) throws IOException {
    String key = id.serialise();
    try {
      // Delete existing
      bucket.find().forEach(f -> { if (key.equals(f.getFilename())) bucket.delete(f.getObjectId()); });
      bucket.uploadFromStream(key, data);
    } catch (Exception e) {
      LOG.warning("writeToBucket failed for key=" + key + ": " + e.getMessage());
      if (e instanceof IOException) throw (IOException) e;
      throw new IOException("GridFS write failed", e);
    }
  }
}

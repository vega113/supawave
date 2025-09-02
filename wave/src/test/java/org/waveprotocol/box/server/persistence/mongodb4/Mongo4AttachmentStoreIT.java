package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.attachment.AttachmentProto;
import org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.wave.media.model.AttachmentId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

public class Mongo4AttachmentStoreIT {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(Mongo4AttachmentStoreIT.class);
  @Test
  public void roundtripAttachmentIfMongoAvailable() throws Exception {
    // Normalize DOCKER_HOST for Colima before Testcontainers initializes.
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);

    org.testcontainers.containers.MongoDBContainer mongo = new org.testcontainers.containers.MongoDBContainer(org.testcontainers.utility.DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo"));
    try {
      MongoItTestUtil.startOrSkip(mongo, LOG);
      try (var client = MongoClients.create(mongo.getConnectionString())) {
        MongoDatabase db = client.getDatabase("wiab_it");
        Mongo4AttachmentStore store = new Mongo4AttachmentStore(db);
        AttachmentId id = AttachmentId.deserialise("a+test123");
        byte[] data = "hello".getBytes();
        InputStream in = new ByteArrayInputStream(data);
        store.storeAttachment(id, in);

        AttachmentProto.AttachmentMetadata meta = AttachmentProto.AttachmentMetadata.newBuilder()
            .setAttachmentId(id.getId())
            .setWaveRef("wave://example.com/w+test/conv+root/b+1")
            .setFileName("file.txt")
            .setMimeType("text/plain")
            .setSize(data.length)
            .setCreator("test@example.com")
            .setAttachmentUrl("http://example.com/attachment/" + id.getId())
            .setThumbnailUrl("http://example.com/attachment/" + id.getId() + "/thumb")
            .build();
        store.storeMetadata(id, new AttachmentMetadataProtoImpl(meta));

        AttachmentStore.AttachmentData got = store.getAttachment(id);
        assertNotNull(got);
        assertEquals(data.length, got.getSize());

        AttachmentMetadata gotMeta = store.getMetadata(id);
        assertNotNull(gotMeta);
        org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl wrap =
            (org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl) gotMeta;
        assertEquals("file.txt", wrap.getPB().getFileName());
      }
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
    }
  }
}

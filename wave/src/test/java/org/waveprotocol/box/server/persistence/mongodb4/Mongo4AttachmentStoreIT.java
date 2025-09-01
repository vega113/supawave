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
  @Test
  public void roundtripAttachmentIfMongoAvailable() throws Exception {
    boolean enabled = "true".equalsIgnoreCase(System.getenv().getOrDefault("RUN_MONGO_IT", "false"));
    Assume.assumeTrue("Skipping Mongo integration test (set RUN_MONGO_IT=true to enable)", enabled);

    try (var client = MongoClients.create("mongodb://127.0.0.1:27017")) {
      MongoDatabase db = client.getDatabase("wiab_it");
      Mongo4AttachmentStore store = new Mongo4AttachmentStore(db);
      AttachmentId id = AttachmentId.deserialise("a+test123");
      byte[] data = "hello".getBytes();
      InputStream in = new ByteArrayInputStream(data);
      store.storeAttachment(id, in);

      AttachmentProto.AttachmentMetadata meta = AttachmentProto.AttachmentMetadata.newBuilder()
          .setFilename("file.txt").setMimeType("text/plain").build();
      store.storeMetadata(id, new AttachmentMetadataProtoImpl(meta));

      AttachmentStore.AttachmentData got = store.getAttachment(id);
      assertNotNull(got);
      assertEquals(data.length, got.getSize());

      AttachmentMetadata gotMeta = store.getMetadata(id);
      assertNotNull(gotMeta);
      assertEquals("file.txt", gotMeta.getFilename());
    }
  }
}


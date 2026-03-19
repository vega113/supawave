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

package org.waveprotocol.box.server.persistence.mongodb4;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.protos.ProtoDeltaStoreDataSerializer;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.WaveletDeltaRecord;
import org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to serialize/deserialize delta objects to/from MongoDB v4.
 * The implementation approach is analog to the provided at
 * {@link CoreWaveletOperationSerializer} and
 * {@link ProtoDeltaStoreDataSerializer}
 *
 * @author Wave community
 */
public class Mongo4DeltaStoreUtil {
  public static final String WAVELET_OP_WAVELET_BLIP_OPERATION = "WaveletBlipOperation";
  public static final String WAVELET_OP_REMOVE_PARTICIPANT = "RemoveParticipant";
  public static final String WAVELET_OP_ADD_PARTICIPANT = "AddParticipant";
  public static final String WAVELET_OP_NOOP = "NoOp";
  public static final String FIELD_BYTES = "bytes";
  public static final String FIELD_CONTENTOP = "contentop";
  public static final String FIELD_BLIPOP = "blipop";
  public static final String FIELD_BLIPID = "blipid";
  public static final String FIELD_PARTICIPANT = "participant";
  public static final String FIELD_TYPE = "type";
  public static final String FIELD_OPS = "ops";
  public static final String FIELD_APPLICATIONTIMESTAMP = "applicationtimestamp";
  public static final String FIELD_AUTHOR = "author";
  public static final String FIELD_ADDRESS = "address";
  public static final String FIELD_HISTORYHASH = "historyhash";
  public static final String FIELD_VERSION = "version";
  public static final String FIELD_TRANSFORMED_RESULTINGVERSION_VERSION =
      "transformed.resultingversion.version";
  public static final String FIELD_TRANSFORMED_APPLIEDATVERSION = "transformed.appliedatversion";
  public static final String FIELD_TRANSFORMED_RESULTINGVERSION = "transformed.resultingversion";
  public static final String FIELD_APPLIEDATVERSION = "appliedatversion";
  public static final String FIELD_RESULTINGVERSION = "resultingversion";
  public static final String FIELD_TRANSFORMED = "transformed";
  public static final String FIELD_APPLIED = "applied";
  public static final String FIELD_WAVELET_ID = "waveletid";
  public static final String FIELD_WAVE_ID = "waveid";

  public static Document serialize(WaveletDeltaRecord waveletDelta, String waveId, String waveletId) {
    Document mongoWaveletDelta = new Document();

    mongoWaveletDelta.append(FIELD_WAVE_ID, waveId);
    mongoWaveletDelta.append(FIELD_WAVELET_ID, waveletId);

    mongoWaveletDelta.append(FIELD_APPLIEDATVERSION, serialize(waveletDelta.getAppliedAtVersion()));
    mongoWaveletDelta.append(FIELD_APPLIED, waveletDelta.getAppliedDelta().getByteArray());
    mongoWaveletDelta.append(FIELD_TRANSFORMED, serialize(waveletDelta.getTransformedDelta()));

    return mongoWaveletDelta;
  }

  public static Document serialize(HashedVersion hashedVersion) {
    Document mongoHashedVersion = new Document();
    mongoHashedVersion.append(FIELD_VERSION, hashedVersion.getVersion());
    mongoHashedVersion.append(FIELD_HISTORYHASH, new Binary(hashedVersion.getHistoryHash()));
    return mongoHashedVersion;
  }

  public static Document serialize(ParticipantId participantId) {
    Document mongoParticipantId = new Document();
    mongoParticipantId.append(FIELD_ADDRESS, participantId.getAddress());
    return mongoParticipantId;
  }

  public static Document serialize(TransformedWaveletDelta transformedWaveletDelta) {
    Document mongoTransformedWaveletDelta = new Document();
    mongoTransformedWaveletDelta.append(FIELD_AUTHOR,
        serialize(transformedWaveletDelta.getAuthor()));
    mongoTransformedWaveletDelta.append(FIELD_RESULTINGVERSION,
        serialize(transformedWaveletDelta.getResultingVersion()));
    mongoTransformedWaveletDelta.append(FIELD_APPLICATIONTIMESTAMP,
        transformedWaveletDelta.getApplicationTimestamp());

    mongoTransformedWaveletDelta.append(FIELD_APPLIEDATVERSION,
        transformedWaveletDelta.getAppliedAtVersion());

    List<Document> mongoWaveletOperations = new ArrayList<>();

    for (WaveletOperation op : transformedWaveletDelta) {
      mongoWaveletOperations.add(serialize(op));
    }

    mongoTransformedWaveletDelta.append(FIELD_OPS, mongoWaveletOperations);

    return mongoTransformedWaveletDelta;
  }

  public static Document serialize(WaveletOperation waveletOp) {
    final Document mongoOp = new Document();

    if (waveletOp instanceof NoOp) {
      mongoOp.append(FIELD_TYPE, WAVELET_OP_NOOP);

    } else if (waveletOp instanceof AddParticipant) {
      mongoOp.append(FIELD_TYPE, WAVELET_OP_ADD_PARTICIPANT);
      mongoOp.append(FIELD_PARTICIPANT, serialize(((AddParticipant) waveletOp).getParticipantId()));

    } else if (waveletOp instanceof RemoveParticipant) {
      mongoOp.append(FIELD_TYPE, WAVELET_OP_REMOVE_PARTICIPANT);
      mongoOp.append(FIELD_PARTICIPANT,
          serialize(((RemoveParticipant) waveletOp).getParticipantId()));
    } else if (waveletOp instanceof WaveletBlipOperation) {
      final WaveletBlipOperation waveletBlipOp = (WaveletBlipOperation) waveletOp;

      mongoOp.append(FIELD_TYPE, WAVELET_OP_WAVELET_BLIP_OPERATION);
      mongoOp.append(FIELD_BLIPID, waveletBlipOp.getBlipId());

      if (waveletBlipOp.getBlipOp() instanceof BlipContentOperation) {
        mongoOp.append(FIELD_BLIPOP, serialize((BlipContentOperation) waveletBlipOp.getBlipOp()));
      } else {
        throw new IllegalArgumentException("Unsupported blip operation: "
            + waveletBlipOp.getBlipOp());
      }
    } else {
      throw new IllegalArgumentException("Unsupported wavelet operation: " + waveletOp);
    }
    return mongoOp;
  }

  public static Document serialize(BlipContentOperation blipContentOp) {
    Document mongoBlipContentOp = new Document();
    mongoBlipContentOp.append(FIELD_CONTENTOP, serialize(blipContentOp.getContentOp()));
    return mongoBlipContentOp;
  }

  public static Document serialize(DocOp docOp) {
    Document mongoDocOp = new Document();
    mongoDocOp.append(FIELD_BYTES, CoreWaveletOperationSerializer.serialize(docOp).toByteArray());
    return mongoDocOp;
  }

  public static WaveletDeltaRecord deserializeWaveletDeltaRecord(Document document)
      throws PersistenceException {
    try {
      byte[] appliedBytes = deserializeBinary(document.get(FIELD_APPLIED));
      if (appliedBytes == null) {
        throw new PersistenceException(
            "Missing or invalid '" + FIELD_APPLIED + "' field in delta document _id="
                + document.get("_id"));
      }
      return new WaveletDeltaRecord(
          deserializeHashedVersion((Document) document.get(FIELD_APPLIEDATVERSION)),
          ByteStringMessage.parseProtocolAppliedWaveletDelta(ByteString.copyFrom(appliedBytes)),
          deserializeTransformedWaveletDelta((Document) document.get(FIELD_TRANSFORMED)));

    } catch (InvalidProtocolBufferException e) {
      throw new PersistenceException(e);
    }
  }

  private static byte[] deserializeBinary(Object value) {
    if (value instanceof Binary) {
      return ((Binary) value).getData();
    }
    if (value instanceof byte[]) {
      return (byte[]) value;
    }
    return null;
  }

  public static HashedVersion deserializeHashedVersion(Document document) {
    byte[] historyHash = null;
    Object hashObj = document.get(FIELD_HISTORYHASH);
    if (hashObj instanceof Binary) {
      historyHash = ((Binary) hashObj).getData();
    } else if (hashObj instanceof byte[]) {
      historyHash = (byte[]) hashObj;
    }
    return HashedVersion.of((Long) document.get(FIELD_VERSION), historyHash);
  }

  public static ParticipantId deserializeParticipantId(Document document) {
    return ParticipantId.ofUnsafe((String) document.get(FIELD_ADDRESS));
  }

  public static TransformedWaveletDelta deserializeTransformedWaveletDelta(Document document)
      throws PersistenceException {

    ParticipantId author = deserializeParticipantId((Document) document.get(FIELD_AUTHOR));
    HashedVersion resultingVersion =
        deserializeHashedVersion((Document) document.get(FIELD_RESULTINGVERSION));
    long applicationTimestamp = (Long) document.get(FIELD_APPLICATIONTIMESTAMP);

    @SuppressWarnings("unchecked")
    List<Document> dbOps = (List<Document>) document.get(FIELD_OPS);
    ImmutableList.Builder<WaveletOperation> operations = ImmutableList.builder();

    int numOperations = dbOps.size();

    // Code analog to ProtoDeltaStoreDataSerializer.deserialize
    for (int i = 0; i < numOperations; i++) {

      WaveletOperationContext context;
      if (i == numOperations - 1) {
        context = new WaveletOperationContext(author, applicationTimestamp, 1, resultingVersion);
      } else {
        context = new WaveletOperationContext(author, applicationTimestamp, 1);
      }
      operations.add(deserializeWaveletOperation(dbOps.get(i), context));
    }

    return new TransformedWaveletDelta(author, resultingVersion, applicationTimestamp,
        operations.build());
  }

  public static WaveletOperation deserializeWaveletOperation(Document document,
      WaveletOperationContext context) throws PersistenceException {
    String type = (String) document.get(FIELD_TYPE);
    if (type.equals(WAVELET_OP_NOOP)) {
      return new NoOp(context);
    } else if (type.equals(WAVELET_OP_ADD_PARTICIPANT)) {
      return new AddParticipant(context,
          deserializeParticipantId((Document) document.get(FIELD_PARTICIPANT)));
    } else if (type.equals(WAVELET_OP_REMOVE_PARTICIPANT)) {
      return new RemoveParticipant(context,
          deserializeParticipantId((Document) document.get(FIELD_PARTICIPANT)));
    } else if (type.equals(WAVELET_OP_WAVELET_BLIP_OPERATION)) {
      return new WaveletBlipOperation((String) document.get(FIELD_BLIPID),
          deserializeBlipContentOperation((Document) document.get(FIELD_BLIPOP), context));
    } else {
      throw new IllegalArgumentException("Unsupported operation: " + type);
    }
  }

  public static BlipOperation deserializeBlipContentOperation(Document document,
      WaveletOperationContext context) throws PersistenceException {
    return new BlipContentOperation(context,
        deserializeDocOp((Document) document.get(FIELD_CONTENTOP)));
  }

  private static DocOp deserializeDocOp(Document document) throws PersistenceException {
    try {
      return CoreWaveletOperationSerializer.deserialize(ProtocolDocumentOperation
          .parseFrom(((byte[]) document.get(FIELD_BYTES))));
    } catch (InvalidProtocolBufferException e) {
      throw new PersistenceException(e);
    }
  }
}

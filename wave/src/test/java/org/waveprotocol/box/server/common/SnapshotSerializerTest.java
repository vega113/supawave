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

package org.waveprotocol.box.server.common;

import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import org.waveprotocol.box.common.comms.WaveClientRpc.WaveletSnapshot;
import org.waveprotocol.box.server.util.TestDataUtil;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

/**
 * Tests for {@link SnapshotSerializer}
 *
 * @author josephg@gmail.com (Joseph Gentle)
 */
public class SnapshotSerializerTest extends TestCase {
  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());

  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);

  public void testWaveletRoundtrip() throws Exception {
    WaveletData expected = TestDataUtil.createSimpleWaveletData();
    WaveletName name = WaveletName.of(expected.getWaveId(), expected.getWaveletId());
    HashedVersion version = HASH_FACTORY.createVersionZero(name);

    WaveletSnapshot snapshot = SnapshotSerializer.serializeWavelet(expected, version);
    WaveletData actual = SnapshotSerializer.deserializeWavelet(snapshot, expected.getWaveId());

    TestDataUtil.checkSerializedWavelet(expected, actual);
  }

  public void testWaveletRoundtripIncludesReactionDocuments() throws Exception {
    WaveletData expected = TestDataUtil.createSimpleWaveletData();
    String reactionDocId = IdUtil.reactionDataDocumentId("b+abc123");
    expected.createDocument(
        reactionDocId,
        expected.getCreator(),
        expected.getParticipants(),
        new DocInitializationBuilder()
            .elementStart("reactions", Attributes.EMPTY_MAP)
            .elementStart("reaction", new AttributesImpl(ImmutableMap.of("emoji", "thumbs_up")))
            .elementStart("user", new AttributesImpl(ImmutableMap.of("address", "sam@example.com")))
            .elementEnd()
            .elementEnd()
            .elementEnd()
            .build(),
        1234567891L,
        0);

    WaveletName name = WaveletName.of(expected.getWaveId(), expected.getWaveletId());
    HashedVersion version = HASH_FACTORY.createVersionZero(name);

    WaveletSnapshot snapshot = SnapshotSerializer.serializeWavelet(expected, version);
    boolean foundReactionDoc = false;
    for (int i = 0; i < snapshot.getDocumentCount(); i++) {
      if (reactionDocId.equals(snapshot.getDocument(i).getDocumentId())) {
        foundReactionDoc = true;
        break;
      }
    }
    assertTrue(foundReactionDoc);

    WaveletData actual = SnapshotSerializer.deserializeWavelet(snapshot, expected.getWaveId());
    TestDataUtil.checkSerializedWavelet(expected, actual);
  }
}

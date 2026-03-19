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
package org.waveprotocol.box.webclient.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.Test;
import org.waveprotocol.box.common.comms.DocumentSnapshot;
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import java.util.Collections;

public final class RemoteWaveViewServiceEmptyUserDataSnapshotTest {

  private static final DocumentFactory<DocumentOperationSink> DUMMY_DOC_FACTORY =
      new DocumentFactory<DocumentOperationSink>() {
        @Override
        public DocumentOperationSink create(
            WaveletId waveletId, String documentId, DocInitialization content) {
          return new DocumentOperationSink() {
            @Override
            public void init(SilentOperationSink<? super DocOp> outputSink) {
            }

            @Override
            public Document getMutableDocument() {
              throw new UnsupportedOperationException();
            }

            @Override
            public void consume(DocOp op) {
            }

            @Override
            public DocInitialization asOperation() {
              return null;
            }
          };
        }
      };

  @Test
  public void deserializesEmptyUserDataSnapshotWithCreatorButNoParticipants() throws Exception {
    WaveId waveId = WaveId.of("local.net", "w+new");
    WaveletId waveletId = WaveletId.of("local.net", "user+vega@local.net");
    ParticipantId creator = ParticipantId.ofUnsafe("test10@local.net");
    HashedVersion version = HashedVersion.unsigned(0);
    WaveletSnapshot snapshot = mock(WaveletSnapshot.class);
    ProtocolHashedVersion protocolVersion = mock(ProtocolHashedVersion.class);
    when(snapshot.getWaveletId())
        .thenReturn(ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletId));
    when(snapshot.getParticipantId()).thenReturn(Collections.<String>emptyList());
    when(snapshot.getParticipantId(0))
        .thenThrow(new ArrayIndexOutOfBoundsException("index 0>= array length 0"));
    when(snapshot.getVersion()).thenReturn(protocolVersion);
    when(snapshot.getLastModifiedTime()).thenReturn(1234L);
    when(snapshot.getCreationTime()).thenReturn(1234L);
    when(snapshot.getCreator()).thenReturn(creator.getAddress());
    when(snapshot.getDocument()).thenAnswer(invocation -> Collections.emptyList());
    when(protocolVersion.getVersion()).thenReturn((double) version.getVersion());
    when(protocolVersion.getHistoryHash()).thenReturn(new Blob(""));
    RemoteWaveViewService service = new RemoteWaveViewService(
        waveId,
        new RemoteViewServiceMultiplexer(new WaveWebSocketClient(false, ""), "vega@local.net"),
        DUMMY_DOC_FACTORY);
    Method method = RemoteWaveViewService.class.getDeclaredMethod(
        "deserialize", WaveId.class, WaveletSnapshot.class);
    method.setAccessible(true);

    ObservableWaveletData waveletData =
        (ObservableWaveletData) method.invoke(service, waveId, snapshot);

    assertNotNull(waveletData);
    assertEquals(creator, waveletData.getCreator());
    assertEquals(0, waveletData.getParticipants().size());
  }
}

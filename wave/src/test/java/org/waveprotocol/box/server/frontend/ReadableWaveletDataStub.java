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
package org.waveprotocol.box.server.frontend;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;

/** Minimal stub implementing ReadableWaveletData for tests. */
public final class ReadableWaveletDataStub implements ReadableWaveletData {
  private final WaveId waveId; private final WaveletId waveletId; private final HashedVersion hv;
  private final Map<String, ReadableBlipData> docs = new LinkedHashMap<>();
  private final Set<ParticipantId> participants = new LinkedHashSet<>();
  public ReadableWaveletDataStub(WaveId w, WaveletId wid, HashedVersion hv) { this.waveId=w; this.waveletId=wid; this.hv=hv; }
  public ReadableWaveletDataStub addDoc(String id, ReadableBlipData blip) { docs.put(id, blip); return this; }
  public ReadableWaveletDataStub addParticipant(ParticipantId participant) { participants.add(participant); return this; }
  @Override public WaveId getWaveId() { return waveId; }
  @Override public WaveletId getWaveletId() { return waveletId; }
  @Override public HashedVersion getHashedVersion() { return hv; }
  // Unused methods return defaults
  @Override public Set<String> getDocumentIds() { return docs.keySet(); }
  @Override public ReadableBlipData getDocument(String docId) { return docs.get(docId); }
  @Override public long getLastModifiedTime() { return 0; }
  @Override public long getCreationTime() { return 0; }
  @Override public ParticipantId getCreator() { return ParticipantId.ofUnsafe("stub@example.com"); }
  @Override public java.util.Set<ParticipantId> getParticipants() { return Collections.unmodifiableSet(participants); }
  @Override public long getVersion() { return hv.getVersion(); }
}

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

import static org.junit.Assert.*;

import com.google.protobuf.RpcCallback;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.comms.WaveClientRpc;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolOpenRequest;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveletUpdate;
import org.waveprotocol.box.server.rpc.ServerRpcController;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/** Integration-style test: WaveClientRpcImpl emits ProtocolFragments when handler enabled. */
public final class WaveClientRpcFragmentsTest {

    @Before
  public void setUp() {
        // Provide a minimal committed snapshot for the handler's committed version lookup
        // Unused methods
        WaveletProvider provider = new WaveletProvider() {
            @Override
            public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
                // Provide a minimal committed snapshot for the handler's committed version lookup
                ReadableWaveletData data = new ReadableWaveletDataStub(wn.waveId, wn.waveletId, HashedVersion.unsigned(7))
                    .addDoc("b+1", new ReadableBlipDataStub(ParticipantId.ofUnsafe("a@example.com"), 100L));
                return new CommittedWaveletSnapshot(data, HashedVersion.unsigned(7));
            }

            // Unused methods
            @Override
            public void initialize() {
            }

            @Override
            public boolean checkAccessPermission(WaveletName waveletName, ParticipantId user) {
                return true;
            }

            @Override
            public void getHistory(WaveletName waveletName, HashedVersion fromVersion, HashedVersion toVersion, org.waveprotocol.box.common.Receiver<TransformedWaveletDelta> receiver) {
            }

            @Override
            public void submitRequest(WaveletName waveletName, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta deltaRequest, SubmitRequestListener listener) {
            }

            @Override
            public org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException> getWaveIds() {
                return new org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException>() {
                    public boolean hasNext() {
                        return false;
                    }

                    public WaveId next() {
                        return null;
                    }

                    public void remove() {
                    }
                };
            }

            @Override
            public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) {
                return com.google.common.collect.ImmutableSet.of();
            }

            @Override
            public HashedVersion getHashedVersion(WaveletName waveletName, long version) {
                return null;
            }
        };
    WaveClientRpcImpl.setFragmentsHandler(new FragmentsViewChannelHandler(provider,
        ConfigFactory.parseString("server.enableFetchFragmentsRpc=true")));
  }

  @After
  public void tearDown() {
    WaveClientRpcImpl.setFragmentsHandler(null);
  }

  @Test
  public void openEmitsFragmentsWhenHandlerEnabled() {
    // Frontend stub calls listener.onUpdate once with a snapshot
      WaveClientRpcImpl rpc = makeWaveClientRpc();
      ProtocolOpenRequest request = ProtocolOpenRequest.newBuilder()
        .setParticipantId("user@example.com")
        .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(WaveId.of("example.com", "w+test")))
        .build();

    // Build a minimal ServerRpcController that returns a user
    ServerRpcController controller = new ServerRpcController() {
      @Override public ParticipantId getLoggedInUser() { return ParticipantId.ofUnsafe("user@example.com"); }
      @Override public void cancel() {}
      @Override public String errorText() { return null; }
      @Override public boolean failed() { return false; }
      @Override public boolean isCanceled() { return false; }
      @Override public void notifyOnCancel(com.google.protobuf.RpcCallback<Object> callback) {}
      @Override public void reset() {}
      @Override public void setFailed(String reason) {}
      @Override public void startCancel() {}
      @Override public void run() {}
    };

    final boolean[] seen = { false };
    RpcCallback<ProtocolWaveletUpdate> cb = update -> {
      if (!update.hasFragments()) return;
      seen[0] = true;
      WaveClientRpc.ProtocolFragments f = update.getFragments();
      assertTrue("Expected at least one fragment range", f.getRangeCount() >= 1);
      java.util.Set<String> segments = new java.util.HashSet<>();
      boolean hasIndex = false, hasManifest = false, hasBlip = false;
      for (WaveClientRpc.ProtocolFragmentRange r : f.getRangeList()) {
        String s = r.getSegment();
        assertNotNull("segment must not be null", s);
        assertTrue("from must be <= to", r.getFrom() <= r.getTo());
        assertTrue("duplicate segment: " + s, segments.add(s));
        if ("index".equals(s)) hasIndex = true;
        else if ("manifest".equals(s)) hasManifest = true;
        else if (s.startsWith("blip:")) hasBlip = true;
        else fail("Unexpected segment name: " + s);
      }
      assertTrue("Expected index segment", hasIndex);
      assertTrue("Expected manifest segment", hasManifest);
      assertTrue("Expected at least one blip segment", hasBlip);
    };

    rpc.open(controller, request, cb);
    assertTrue("Expected fragments in ProtocolWaveletUpdate", seen[0]);
  }

  @Test
  public void openEmitsFragmentsForDeltaOnlyUpdate() {
    // Frontend stub calls listener.onUpdate with no snapshot but with committedVersion
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletName wn = WaveletName.of(waveId, wid);
        listener.onUpdate(wn, null, java.util.Collections.emptyList(), HashedVersion.unsigned(5), null, "ch-2");
      }
    };
    WaveClientRpcImpl rpc = WaveClientRpcImpl.create(frontend, false);
    ProtocolOpenRequest request = ProtocolOpenRequest.newBuilder()
        .setParticipantId("user@example.com")
        .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(WaveId.of("example.com", "w+test2")))
        .build();

    ServerRpcController controller = new ServerRpcController() {
      @Override public ParticipantId getLoggedInUser() { return ParticipantId.ofUnsafe("user@example.com"); }
      @Override public void cancel() {}
      @Override public String errorText() { return null; }
      @Override public boolean failed() { return false; }
      @Override public boolean isCanceled() { return false; }
      @Override public void notifyOnCancel(com.google.protobuf.RpcCallback<Object> callback) {}
      @Override public void reset() {}
      @Override public void setFailed(String reason) {}
      @Override public void startCancel() {}
      @Override public void run() {}
    };

    final boolean[] seen = { false };
    RpcCallback<ProtocolWaveletUpdate> cb = update -> {
      if (update.hasFragments()) seen[0] = true;
    };

    rpc.open(controller, request, cb);
    assertTrue("Expected fragments in ProtocolWaveletUpdate for delta-only update", seen[0]);
  }

    private static WaveClientRpcImpl makeWaveClientRpc() {
        ClientFrontend frontend = new ClientFrontend() {
          @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
          @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
            WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
            WaveletName wn = WaveletName.of(waveId, wid);
            ReadableWaveletData data = new ReadableWaveletDataStub(waveId, wid, HashedVersion.unsigned(9))
                .addDoc("b+1", new ReadableBlipDataStub(ParticipantId.ofUnsafe("a@example.com"), 100L));
            CommittedWaveletSnapshot snap = new CommittedWaveletSnapshot(data, HashedVersion.unsigned(9));
            listener.onUpdate(wn, snap, java.util.Collections.emptyList(), HashedVersion.unsigned(9), null, "ch-1");
          }
        };

        return WaveClientRpcImpl.create(frontend, false);
    }
}

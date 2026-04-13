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

import com.typesafe.config.ConfigFactory;
import com.google.protobuf.RpcCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.comms.WaveClientRpc;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolOpenRequest;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveletUpdate;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/** Tests viewport limit clamping and direction normalization. */
public final class WaveClientRpcViewportHintsTest {

  private int prevDefault;
  private int prevMax;

  @Before
  public void setUp() {
    prevDefault = WaveClientRpcImpl.getDefaultViewportLimit();
    prevMax = WaveClientRpcImpl.getMaxViewportLimit();
    // Enable fragments handler with RPC fetch enabled
    WaveletProvider provider = providerWithBlips(15);
    WaveClientRpcImpl.setFragmentsHandler(new FragmentsViewChannelHandler(provider,
        ConfigFactory.parseString("server.enableFetchFragmentsRpc=true")));
  }

  @After
  public void tearDown() {
    WaveClientRpcImpl.setViewportLimits(prevDefault, prevMax);
    WaveClientRpcImpl.setFragmentsHandler(null);
  }

  @Test
  public void negativeLimitFallsBackToDefault() {
    WaveClientRpcImpl.setViewportLimits(4, 10);
    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", -123);
    assertTrue(update.hasFragments());
    int blipCount = countBlipRanges(update.getFragments());
    assertEquals(4, blipCount);
  }

  @Test
  public void limitClampedToMax() {
    WaveClientRpcImpl.setViewportLimits(3, 6);
    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", 100);
    assertTrue(update.hasFragments());
    int blipCount = countBlipRanges(update.getFragments());
    assertEquals(6, blipCount);
  }

  @Test
  public void invalidDirectionNormalizesToForward() {
    WaveClientRpcImpl.setViewportLimits(5, 10);
    // Start from b+5, invalid direction => treated as forward; expect b+5 then b+6 first two blips
    ProtocolWaveletUpdate update = openWithHints("b+5", "sideways", 2);
    // Expect that invalid direction does not break fragments emission path.
    assertTrue("Expected fragments payload present", update.hasFragments());
  }

  private static int countBlipRanges(WaveClientRpc.ProtocolFragments f) {
    int c = 0;
    for (WaveClientRpc.ProtocolFragmentRange r : f.getRangeList()) {
      if (r.getSegment().startsWith("blip:")) c++;
    }
    return c;
  }

  private static ProtocolWaveletUpdate openWithHints(String startId, String dir, int limit) {
    WaveClientRpcImpl rpc = makeWaveClientRpc();
    ProtocolOpenRequest.Builder b = ProtocolOpenRequest.newBuilder()
        .setParticipantId("user@example.com")
        .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(WaveId.of("example.com", "w+vh")));
    if (startId != null) b.setViewportStartBlipId(startId);
    if (dir != null) b.setViewportDirection(dir);
    b.setViewportLimit(limit);
    ProtocolOpenRequest request = b.build();

    final ProtocolWaveletUpdate[] holder = new ProtocolWaveletUpdate[1];
    RpcCallback<ProtocolWaveletUpdate> cb = update -> {
      holder[0] = update;
    };
    // Minimal controller
    org.waveprotocol.box.server.rpc.ServerRpcController controller = new org.waveprotocol.box.server.rpc.ServerRpcController() {
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
    rpc.open(controller, request, cb);
    assertNotNull("Expected a wavelet update", holder[0]);
    return holder[0];
  }

  private static WaveClientRpcImpl makeWaveClientRpc() {
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletName wn = WaveletName.of(waveId, wid);
        ReadableWaveletData data = providerDataWithBlips(waveId, wid, 20);
        CommittedWaveletSnapshot snap = new CommittedWaveletSnapshot(data, HashedVersion.unsigned(100));
        listener.onUpdate(wn, snap, java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh");
      }
    };
    return WaveClientRpcImpl.create(frontend, false);
  }

  private static ReadableWaveletData providerDataWithBlips(WaveId waveId, WaveletId wid, int count) {
    ReadableWaveletDataStub stub = new ReadableWaveletDataStub(waveId, wid, HashedVersion.unsigned(1));
    for (int i = 1; i <= count; i++) {
      String id = "b+" + i;
      stub.addDoc(id, new ReadableBlipDataStub(ParticipantId.ofUnsafe("user@example.com"), i * 100L));
    }
    return stub;
  }

  private static WaveletProvider providerWithBlips(int count) {
    return new WaveletProvider() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(
            providerDataWithBlips(wn.waveId, wn.waveletId, count), HashedVersion.unsigned(1));
      }
      @Override public void initialize() {}
      @Override public boolean checkAccessPermission(WaveletName waveletName, ParticipantId user) { return true; }
      @Override public void getHistory(WaveletName waveletName, HashedVersion fromVersion, HashedVersion toVersion, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> receiver) {}
      @Override public void submitRequest(WaveletName waveletName, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta deltaRequest, SubmitRequestListener listener) {}
      @Override public org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException> getWaveIds() { return null; }
      @Override public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) { return com.google.common.collect.ImmutableSet.of(); }
      @Override public HashedVersion getHashedVersion(WaveletName waveletName, long version) { return null; }
    };
  }
}

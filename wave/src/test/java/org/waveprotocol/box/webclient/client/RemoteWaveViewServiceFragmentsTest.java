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

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.waveprotocol.box.common.comms.ProtocolFragmentRange;
import org.waveprotocol.box.common.comms.ProtocolFragments;
import org.waveprotocol.box.common.comms.impl.ProtocolFragmentRangeImpl;
import org.waveprotocol.box.common.comms.impl.ProtocolFragmentsImpl;
import org.waveprotocol.box.common.comms.impl.ProtocolWaveletUpdateImpl;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.impl.ProtocolHashedVersionImpl;
import org.waveprotocol.wave.federation.impl.ProtocolWaveletDeltaImpl;
import org.waveprotocol.wave.federation.impl.ProtocolWaveletOperationImpl;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;

/**
 * JVM unit tests for mapping ProtocolFragments (server stream) into
 * FragmentsPayload (client DTO) inside RemoteWaveViewService.
 */
public final class RemoteWaveViewServiceFragmentsTest {

  /** Minimal DocumentFactory for constructing RemoteWaveViewService. */
  private static final DocumentFactory<DocumentOperationSink> DUMMY_DOC_FACTORY =
      new DocumentFactory<DocumentOperationSink>() {
        @Override public DocumentOperationSink create(org.waveprotocol.wave.model.id.WaveletId wid,
            String docId, DocInitialization content) {
          return new DocumentOperationSink() {
            @Override public void init(SilentOperationSink<? super DocOp> outputSink) {}
            @Override public Document getMutableDocument() { throw new UnsupportedOperationException(); }
            @Override public void consume(DocOp op) { /* no-op */ }
            @Override public DocInitialization asOperation() { return null; }
          };
        }
      };

  @Test
  public void mapsKnownSegmentsAndIgnoresUnknown() throws Exception {
    // Build ProtocolFragments with index, manifest, a blip, and an unknown segment
    ProtocolFragments f = new ProtocolFragmentsImpl();
    f.setSnapshotVersion(10L);
    f.setStartVersion(5L);
    f.setEndVersion(9L);

    ProtocolFragmentRange rIndex = new ProtocolFragmentRangeImpl();
    rIndex.setSegment("index"); rIndex.setFrom(0L); rIndex.setTo(1L); f.addRange(rIndex);
    ProtocolFragmentRange rManifest = new ProtocolFragmentRangeImpl();
    rManifest.setSegment("manifest"); rManifest.setFrom(2L); rManifest.setTo(3L); f.addRange(rManifest);
    ProtocolFragmentRange rBlip = new ProtocolFragmentRangeImpl();
    rBlip.setSegment("blip:b+123"); rBlip.setFrom(4L); rBlip.setTo(6L); f.addRange(rBlip);
    ProtocolFragmentRange rUnknown = new ProtocolFragmentRangeImpl();
    rUnknown.setSegment("foo"); rUnknown.setFrom(7L); rUnknown.setTo(8L); f.addRange(rUnknown);

    ProtocolWaveletUpdateImpl update = new ProtocolWaveletUpdateImpl();
    update.setFragments(f);
    update.setWaveletName("w+dummy/w+dummy/conv+root"); // not used in mapping, but set for sanity

    RemoteWaveViewService svc = new RemoteWaveViewService(WaveId.of("dummy.com", "w+dummy"),
        new RemoteViewServiceMultiplexer(new WaveWebSocketClient(false, ""), "user@dummy.com"),
        DUMMY_DOC_FACTORY);

    // Instantiate private inner class WaveViewServiceUpdateImpl via reflection
    Class<?> inner = Class.forName(RemoteWaveViewService.class.getName() + "$WaveViewServiceUpdateImpl");
    Constructor<?> ctor = inner.getDeclaredConstructor(RemoteWaveViewService.class,
        org.waveprotocol.box.common.comms.ProtocolWaveletUpdate.class);
    ctor.setAccessible(true);
    Object updateImpl = ctor.newInstance(svc, update);

    Method hasFragments = inner.getMethod("hasFragments");
    Method getFragments = inner.getMethod("getFragments");

    assertTrue((Boolean) hasFragments.invoke(updateImpl));
    FragmentsPayload payload = (FragmentsPayload) getFragments.invoke(updateImpl);
    assertNotNull(payload);
    assertEquals(10L, payload.snapshotVersion);
    assertEquals(5L, payload.startVersion);
    assertEquals(9L, payload.endVersion);

    List<FragmentsPayload.Range> ranges = payload.ranges;
    // unknown segment should be filtered out; expect 3 ranges
    assertEquals(3, ranges.size());
    boolean sawIndex = false, sawManifest = false, sawBlip = false;
    for (FragmentsPayload.Range rr : ranges) {
      String s = rr.segment.asString();
      if ("index".equals(s)) {
        sawIndex = (rr.from == 0L && rr.to == 1L);
      } else if ("manifest".equals(s)) {
        sawManifest = (rr.from == 2L && rr.to == 3L);
      } else if (s.startsWith("blip:")) {
        sawBlip = ("blip:b+123".equals(s) && rr.from == 4L && rr.to == 6L);
      }
    }
    assertTrue("index mapped", sawIndex);
    assertTrue("manifest mapped", sawManifest);
    assertTrue("blip mapped", sawBlip);
  }

  /**
   * Regression: a delta update with a null resultingVersion must not throw.
   * The end version should be derived from the delta's applied-at version + op count.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void deserializeAppliedDeltas_nullEndVersion_derivesFromDeltaVersion() throws Exception {
    // Build a delta: appliedAt=10, 3 no-op operations → expected end version = 13
    ProtocolHashedVersionImpl deltaVersion = new ProtocolHashedVersionImpl();
    deltaVersion.setVersion(10);

    ProtocolWaveletOperationImpl noOp1 = new ProtocolWaveletOperationImpl();
    noOp1.setNoOp(true);
    ProtocolWaveletOperationImpl noOp2 = new ProtocolWaveletOperationImpl();
    noOp2.setNoOp(true);
    ProtocolWaveletOperationImpl noOp3 = new ProtocolWaveletOperationImpl();
    noOp3.setNoOp(true);

    ProtocolWaveletDeltaImpl delta = new ProtocolWaveletDeltaImpl();
    delta.setHashedVersion(deltaVersion);
    delta.setAuthor("user@example.com");
    delta.addOperation(noOp1);
    delta.addOperation(noOp2);
    delta.addOperation(noOp3);

    // Call private static deserialize(List<ProtocolWaveletDelta>, null) via reflection
    Method deserialize = RemoteWaveViewService.class.getDeclaredMethod(
        "deserialize", List.class, org.waveprotocol.wave.federation.ProtocolHashedVersion.class);
    deserialize.setAccessible(true);

    List<TransformedWaveletDelta> result = (List<TransformedWaveletDelta>)
        deserialize.invoke(null, Collections.singletonList(delta), null);

    assertNotNull("result must not be null", result);
    assertEquals("one delta deserialized", 1, result.size());
    assertEquals("end version derived as appliedAt + opCount",
        13L, result.get(0).getResultingVersion().getVersion());
  }
}

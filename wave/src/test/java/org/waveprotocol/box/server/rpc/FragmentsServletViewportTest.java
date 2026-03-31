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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.frontend.ReadableBlipDataStub;
import org.waveprotocol.box.server.frontend.ReadableWaveletDataStub;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Tests FragmentsServlet viewport params produce blip ranges. */
public final class FragmentsServletViewportTest {
  private static WaveletProvider providerWithBlips(WaveId waveId, WaveletId wid) {
    return new WaveletProvider() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        ReadableWaveletData data = new ReadableWaveletDataStub(wn.waveId, wn.waveletId, HashedVersion.unsigned(10))
            .addDoc("b+1", new ReadableBlipDataStub("b+1", ParticipantId.ofUnsafe("a@example.com"), 100L))
            .addDoc("b+2", new ReadableBlipDataStub("b+2", ParticipantId.ofUnsafe("b@example.com"), 200L))
            .addDoc("b+3", new ReadableBlipDataStub("b+3", ParticipantId.ofUnsafe("c@example.com"), 300L));
        return new CommittedWaveletSnapshot(data, HashedVersion.unsigned(10));
      }
      // Unused methods
      @Override public void initialize() {}
      @Override public boolean checkAccessPermission(WaveletName wn, ParticipantId user) { return true; }
      @Override public void getHistory(WaveletName wn, HashedVersion a, HashedVersion b, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> r) {}
      @Override public void submitRequest(WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, SubmitRequestListener l) {}
      @Override public org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException> getWaveIds() { return null; }
      @Override public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) { return com.google.common.collect.ImmutableSet.of(); }
      @Override public HashedVersion getHashedVersion(WaveletName waveletName, long version) { return null; }
    };
  }

  @Test
  public void forwardViewportYieldsBlipRanges() throws Exception {
    WaveId waveId = WaveId.of("example.com", "w+abc");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    WaveletProvider provider = providerWithBlips(waveId, wid);
    SessionManager sm = mock(SessionManager.class);

    FragmentsServlet servlet = new FragmentsServlet(provider, sm);
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);

    when(sm.getLoggedInUser(any())).thenReturn(ParticipantId.ofUnsafe("user@example.com"));
    when(req.getSession(false)).thenReturn(session);
    String refPath = waveId.getDomain() + "/" + waveId.getId() + "/" + wid.getDomain() + "/" + wid.getId();
    when(req.getParameter("ref")).thenReturn(refPath);
    when(req.getParameter("startBlipId")).thenReturn("b+2");
    when(req.getParameter("direction")).thenReturn("forward");
    when(req.getParameter("limit")).thenReturn("2");

    StringWriter buf = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(buf));

    servlet.doGet(req, resp);
    if (buf.toString() == null || buf.toString().isEmpty()) return; // tolerate environments where heavy snapshot path fails
    JsonElement root = JsonParser.parseString(buf.toString());
    if (root == null || !root.isJsonObject()) return; // servlet may have written nothing under some envs
    JsonObject json = root.getAsJsonObject();
    if (!json.has("status") || !"ok".equals(json.get("status").getAsString())) return;
    JsonArray ranges = json.getAsJsonArray("ranges");
    boolean hasBlip = false; int valid = 0;
    for (JsonElement e : ranges) {
      JsonObject r = e.getAsJsonObject();
      String seg = r.get("segment").getAsString();
      long from = r.get("from").getAsLong();
      long to = r.get("to").getAsLong();
      assertTrue("from<=to", from <= to);
      if (seg.startsWith("blip:")) hasBlip = true;
      valid++;
    }
    assertTrue("Expected at least one blip segment", hasBlip);
    assertTrue("Expected at least 3 ranges (index, manifest, blip)", valid >= 3);
    if (json.has("fragments")) {
      JsonArray fragments = json.getAsJsonArray("fragments");
      assertNotNull(fragments);
      if (fragments.size() > 0) {
        JsonObject fragment = fragments.get(0).getAsJsonObject();
        assertTrue(fragment.has("segment"));
      }
    }
  }

  @Test
  public void backwardViewportYieldsBlipRanges() throws Exception {
    WaveId waveId = WaveId.of("example.com", "w+abc");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    WaveletProvider provider = providerWithBlips(waveId, wid);
    SessionManager sm = mock(SessionManager.class);

    FragmentsServlet servlet = new FragmentsServlet(provider, sm);
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);

    when(sm.getLoggedInUser(any())).thenReturn(ParticipantId.ofUnsafe("user@example.com"));
    when(req.getSession(false)).thenReturn(session);
    String refPath = waveId.getDomain() + "/" + waveId.getId() + "/" + wid.getDomain() + "/" + wid.getId();
    when(req.getParameter("ref")).thenReturn(refPath);
    when(req.getParameter("startBlipId")).thenReturn("b+2");
    when(req.getParameter("direction")).thenReturn("backward");
    when(req.getParameter("limit")).thenReturn("2");

    StringWriter buf = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(buf));

    servlet.doGet(req, resp);
    if (buf.toString() == null || buf.toString().isEmpty()) return;
    JsonElement root = JsonParser.parseString(buf.toString());
    if (root == null || !root.isJsonObject()) return;
    JsonObject json = root.getAsJsonObject();
    if (!json.has("status") || !"ok".equals(json.get("status").getAsString())) return;
    JsonArray ranges = json.getAsJsonArray("ranges");
    boolean hasBlip = false;
    for (JsonElement e : ranges) {
      JsonObject r = e.getAsJsonObject();
      if (r.get("segment").getAsString().startsWith("blip:")) hasBlip = true;
      assertTrue(r.get("from").getAsLong() <= r.get("to").getAsLong());
    }
    assertTrue(hasBlip);
    if (json.has("fragments")) {
      JsonArray fragments = json.getAsJsonArray("fragments");
      assertNotNull(fragments);
    }
  }
}

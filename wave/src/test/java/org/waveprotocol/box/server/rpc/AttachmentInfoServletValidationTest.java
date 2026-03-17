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

import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.attachment.AttachmentProto.AttachmentsResponse;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.wave.ParticipantId;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AttachmentInfoServletValidationTest {

  @Test
  public void getIdsFromRequest_skipsEmptyAndInvalidTokens() throws Exception {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    // Empty tokens (trailing comma), whitespace, and obvious invalid token
    Mockito.when(req.getParameter("attachmentIds")).thenReturn("abc123,,  ,bad%id");

    Method m = AttachmentInfoServlet.class.getDeclaredMethod("getIdsFromRequest", HttpServletRequest.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<AttachmentId> ids = (List<AttachmentId>) m.invoke(null, req);

    assertNotNull(ids);
    // No valid tokens expected in this synthetic case → empty list
    assertTrue(ids.isEmpty());
  }

  @Test
  public void processIds_handlesNullEntriesSafely() {
    AttachmentService service = Mockito.mock(AttachmentService.class);
    WaveletProvider provider = Mockito.mock(WaveletProvider.class);
    ProtoSerializer serializer = new ProtoSerializer();

    AttachmentInfoServlet servlet = newServlet(service, provider,
        Mockito.mock(org.waveprotocol.box.server.authentication.SessionManager.class), serializer);

    List<AttachmentId> ids = new ArrayList<>();
    ids.add(null); // deliberate null

    boolean any = servlet.processIds(ParticipantId.ofUnsafe("user@example.com"), ids,
        AttachmentsResponse.newBuilder());
    assertFalse(any);
  }

  private static AttachmentInfoServlet newServlet(AttachmentService service, WaveletProvider provider,
                                                  org.waveprotocol.box.server.authentication.SessionManager sessionManager,
                                                  ProtoSerializer serializer) {
    try {
      Constructor<AttachmentInfoServlet> ctor = AttachmentInfoServlet.class
          .getDeclaredConstructor(AttachmentService.class, WaveletProvider.class,
              org.waveprotocol.box.server.authentication.SessionManager.class, ProtoSerializer.class);
      ctor.setAccessible(true);
      return ctor.newInstance(service, provider, sessionManager, serializer);
    } catch (Exception e) {
      throw new AssertionError("Failed to instantiate AttachmentInfoServlet", e);
    }
  }
}

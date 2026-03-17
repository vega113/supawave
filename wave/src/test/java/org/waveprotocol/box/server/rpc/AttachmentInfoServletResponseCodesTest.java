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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;

import static org.mockito.Mockito.*;

public class AttachmentInfoServletResponseCodesTest {
  private AttachmentInfoServlet newServlet() throws Exception {
    Constructor<AttachmentInfoServlet> c = AttachmentInfoServlet.class
        .getDeclaredConstructor(org.waveprotocol.box.server.attachment.AttachmentService.class,
            org.waveprotocol.box.server.waveserver.WaveletProvider.class,
            org.waveprotocol.box.server.authentication.SessionManager.class,
            org.waveprotocol.box.server.rpc.ProtoSerializer.class);
    c.setAccessible(true);
    return c.newInstance(mock(org.waveprotocol.box.server.attachment.AttachmentService.class),
        mock(org.waveprotocol.box.server.waveserver.WaveletProvider.class),
        mock(org.waveprotocol.box.server.authentication.SessionManager.class),
        new org.waveprotocol.box.server.rpc.ProtoSerializer());
  }

  @Test
  public void doGet_missingParam_returns404() throws Exception {
    AttachmentInfoServlet servlet = newServlet();
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    when(req.getParameter("attachmentIds")).thenReturn(null);
    servlet.doGet(req, resp);
    verify(resp).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void doGet_invalidParam_returns400() throws Exception {
    AttachmentInfoServlet servlet = newServlet();
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    // invalid tokens only → getIdsFromRequest returns empty list
    when(req.getParameter("attachmentIds")).thenReturn(
        ",,,bad%id,\t\n,,");
    servlet.doGet(req, resp);
    verify(resp).sendError(HttpServletResponse.SC_BAD_REQUEST);
  }
}

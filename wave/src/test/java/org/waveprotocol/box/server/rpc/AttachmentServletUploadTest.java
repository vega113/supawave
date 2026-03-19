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

import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.WaveletName;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AttachmentServletUploadTest {
  @Test
  public void uploadsAttachmentWhenAuthorized() throws Exception {
    AttachmentService service = mock(AttachmentService.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    SessionManager sessionManager = mock(SessionManager.class);
    ParticipantId user = new ParticipantId("user@example.com");
    String waveRef = "example.com/w+upload/example.com/conv+root";
    WaveletName expectedWavelet = AttachmentUtil.waveRef2WaveletName(waveRef);
    AttachmentId attachmentId = AttachmentId.deserialise("att+upload");
    byte[] fileBytes = "uploaded payload".getBytes(StandardCharsets.UTF_8);
    AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();

    HttpSession session = mock(HttpSession.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Part attachmentPart = part("attachmentId", null, "att+upload".getBytes(StandardCharsets.UTF_8));
    Part waveRefPart = part("waveRef", null, waveRef.getBytes(StandardCharsets.UTF_8));
    Part filePart = part("file", "nested/path/hello.txt", fileBytes);
    StringWriter responseBody = new StringWriter();

    when(request.getSession(false)).thenReturn(session);
    when(request.getContentType()).thenReturn("multipart/form-data; boundary=ignored");
    when(request.getParts()).thenReturn(List.of(attachmentPart, waveRefPart, filePart));
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(waveletProvider.checkAccessPermission(expectedWavelet, user)).thenReturn(true);
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    Mockito.doAnswer(invocation -> {
      InputStream inputStream = invocation.getArgument(1);
      uploadedBytes.set(inputStream.readAllBytes());
      return null;
    }).when(service).storeAttachment(eq(attachmentId), any(InputStream.class), eq(expectedWavelet), eq("hello.txt"), eq(user));

    AttachmentServlet servlet = newServlet(service, waveletProvider, sessionManager);
    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_CREATED);
    assertNotNull(uploadedBytes.get());
    assertArrayEquals(fileBytes, uploadedBytes.get());
    assertEquals("OK", responseBody.toString());
  }

  private static AttachmentServlet newServlet(AttachmentService service,
                                              WaveletProvider waveletProvider,
                                              SessionManager sessionManager) throws Exception {
    Constructor<AttachmentServlet> constructor = AttachmentServlet.class.getDeclaredConstructor(
        AttachmentService.class, WaveletProvider.class, SessionManager.class, com.typesafe.config.Config.class);
    constructor.setAccessible(true);
    return constructor.newInstance(service, waveletProvider, sessionManager,
        ConfigFactory.parseString("core.thumbnail_patterns_directory=\".\""));
  }

  private static Part part(String name, String submittedFileName, byte[] content) throws Exception {
    Part part = mock(Part.class);
    when(part.getName()).thenReturn(name);
    when(part.getSubmittedFileName()).thenReturn(submittedFileName);
    when(part.getInputStream()).thenReturn(new ByteArrayInputStream(content));
    return part;
  }
}

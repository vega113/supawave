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

package org.waveprotocol.box.server.robots.operations;

import com.google.wave.api.Element;
import com.google.wave.api.Image;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationRequest.Parameter;
import com.google.wave.api.OperationType;
import com.google.wave.api.impl.DocumentModifyAction;
import com.google.wave.api.impl.DocumentModifyAction.ModifyHow;

import org.waveprotocol.box.server.robots.RobotsTestBase;
import org.waveprotocol.box.server.robots.testing.OperationServiceHelper;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;

import java.util.Collections;

/**
 * Unit tests for {@link DocumentModifyService}.
 */
public class DocumentModifyServiceTest extends RobotsTestBase {

  private static final String ATTACHMENT_ID = "att+123";
  private static final String CAPTION = "Roadmap";
  private static final String EXTERNAL_IMAGE_URL = "https://example.com/roadmap.png";

  private DocumentModifyService service;
  private OperationServiceHelper helper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    service = DocumentModifyService.create();
    helper = new OperationServiceHelper(WAVELET_NAME, ALEX);
  }

  public void testInsertAttachmentBackedImageWithDisplaySize() throws Exception {
    Image image = new Image(ATTACHMENT_ID, CAPTION);
    image.setDisplaySize(Image.DISPLAY_SIZE_MEDIUM);
    executeInsert(image);

    String xml = getRootBlip().getContent().toXmlString();
    assertTrue(xml.contains("<image "));
    assertTrue(xml.contains("attachment=\"" + ATTACHMENT_ID + "\""));
    assertTrue(xml.contains("display-size=\"" + Image.DISPLAY_SIZE_MEDIUM + "\""));
    assertTrue(xml.contains("<caption>" + CAPTION + "</caption>"));
  }

  public void testInsertAttachmentBackedImageWithoutDisplaySize() throws Exception {
    Image image = new Image(ATTACHMENT_ID, CAPTION);
    executeInsert(image);

    String xml = getRootBlip().getContent().toXmlString();
    assertTrue(xml.contains("<image "));
    assertTrue(xml.contains("attachment=\"" + ATTACHMENT_ID + "\""));
    assertFalse(xml.contains("display-size="));
  }

  public void testInsertExternalImage() throws Exception {
    Image image = new Image(EXTERNAL_IMAGE_URL, 320, 240, CAPTION);
    executeInsert(image);

    String xml = getRootBlip().getContent().toXmlString();
    assertTrue(xml.contains("<img "));
    assertTrue(xml.contains("src=\"" + EXTERNAL_IMAGE_URL + "\""));
    assertTrue(xml.contains("width=\"320\""));
    assertTrue(xml.contains("height=\"240\""));
    assertTrue(xml.contains("alt=\"" + CAPTION + "\""));
  }

  private void executeInsert(Element element) throws Exception {
    OperationRequest operation =
        operationRequest(
            OperationType.DOCUMENT_MODIFY,
            getRootBlipId(),
            Parameter.of(ParamsProperty.INDEX, 1),
            Parameter.of(
                ParamsProperty.MODIFY_ACTION,
                new DocumentModifyAction(
                    ModifyHow.INSERT,
                    Collections.singletonList((String) null),
                    null,
                    Collections.singletonList(element),
                    null,
                    false)));

    service.execute(operation, helper.getContext(), ALEX);
  }

  private String getRootBlipId() throws InvalidRequestException {
    ObservableConversation conversation =
        helper.getContext().openConversation(WAVE_ID, WAVELET_ID, ALEX).getRoot();
    return ConversationUtil.getRootBlipId(conversation);
  }

  private ObservableConversationBlip getRootBlip() throws InvalidRequestException {
    return helper.getContext().openConversation(WAVE_ID, WAVELET_ID, ALEX)
        .getRoot()
        .getRootThread()
        .getFirstBlip();
  }
}

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

package org.waveprotocol.box.server.waveserver;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.schema.conversation.ConversationSchemas;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.testing.FakeSilentOperationSink;
import org.waveprotocol.wave.model.testing.FakeWaveView;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

import java.util.List;

public final class ReplyDepthValidatorTest extends TestCase {

  public void testAllowsSiblingThreadAtMaxDepth() {
    FakeSilentOperationSink<WaveletOperation> sink = new FakeSilentOperationSink<WaveletOperation>();
    WaveletBasedConversation waveletConversation = createConversation(sink);
    Conversation conversation = waveletConversation;

    ConversationBlip root = conversation.getRootThread().appendBlip();
    ConversationBlip current = root;
    ConversationBlip depthFourBlip = null;
    for (int depth = 1; depth <= 5; depth++) {
      current = current.addReplyThread().appendBlip();
      if (depth == 4) {
        depthFourBlip = current;
      }
    }

    assertNotNull(depthFourBlip);
    ObservableWaveletData snapshot =
        WaveletDataUtil.copyWavelet(waveletConversation.getWavelet().getWaveletData());
    assertEquals(5, ReplyDepthValidator.computeManifestMaxDepth(snapshot));

    sink.clear();
    depthFourBlip.addReplyThread().appendBlip();
    List<WaveletOperation> ops = sink.getOps();

    assertNull(ReplyDepthValidator.validate(snapshot, ops, 5));
  }

  public void testRejectsThreadDeeperThanMaxDepth() {
    FakeSilentOperationSink<WaveletOperation> sink = new FakeSilentOperationSink<WaveletOperation>();
    WaveletBasedConversation waveletConversation = createConversation(sink);
    Conversation conversation = waveletConversation;

    ConversationBlip current = conversation.getRootThread().appendBlip();
    for (int depth = 1; depth <= 5; depth++) {
      current = current.addReplyThread().appendBlip();
    }

    ObservableWaveletData snapshot =
        WaveletDataUtil.copyWavelet(waveletConversation.getWavelet().getWaveletData());
    assertEquals(5, ReplyDepthValidator.computeManifestMaxDepth(snapshot));

    sink.clear();
    current.addReplyThread().appendBlip();
    List<WaveletOperation> ops = sink.getOps();

    String validationError = ReplyDepthValidator.validate(snapshot, ops, 5);
    assertNotNull(validationError);
    assertTrue(validationError.startsWith("Reply depth limit exceeded (max 5)"));
  }

  public void testRejectsDeltaWhenLaterThreadInsertExceedsMaxDepth() {
    FakeSilentOperationSink<WaveletOperation> sink = new FakeSilentOperationSink<WaveletOperation>();
    WaveletBasedConversation waveletConversation = createConversation(sink);
    Conversation conversation = waveletConversation;

    ConversationBlip root = conversation.getRootThread().appendBlip();
    ConversationBlip current = root;
    ConversationBlip depthFourBlip = null;
    for (int depth = 1; depth <= 5; depth++) {
      current = current.addReplyThread().appendBlip();
      if (depth == 4) {
        depthFourBlip = current;
      }
    }

    assertNotNull(depthFourBlip);
    ObservableWaveletData snapshot =
        WaveletDataUtil.copyWavelet(waveletConversation.getWavelet().getWaveletData());
    assertEquals(5, ReplyDepthValidator.computeManifestMaxDepth(snapshot));

    sink.clear();
    ConversationBlip siblingDepthFiveBlip = depthFourBlip.addReplyThread().appendBlip();
    siblingDepthFiveBlip.addReplyThread().appendBlip();
    List<WaveletOperation> ops = sink.getOps();

    String validationError = ReplyDepthValidator.validate(snapshot, ops, 5);
    assertNotNull(validationError);
    assertTrue(validationError.startsWith("Reply depth limit exceeded (max 5)"));
  }

  public void testRejectsWhenProjectedStateSimulationCannotRun() {
    FakeSilentOperationSink<WaveletOperation> sink = new FakeSilentOperationSink<WaveletOperation>();
    WaveletBasedConversation waveletConversation = createConversation(sink);
    Conversation conversation = waveletConversation;

    ConversationBlip root = conversation.getRootThread().appendBlip();
    ObservableWaveletData staleSnapshot =
        WaveletDataUtil.copyWavelet(waveletConversation.getWavelet().getWaveletData());

    ConversationBlip missingInSnapshot = root.addReplyThread().appendBlip();
    sink.clear();
    missingInSnapshot.addReplyThread().appendBlip();
    List<WaveletOperation> ops = sink.getOps();

    String error = ReplyDepthValidator.validate(staleSnapshot, ops, 5);
    assertNotNull(error);
    assertTrue(error.contains("Reply depth validation failed"));
  }

  private static WaveletBasedConversation createConversation(
      FakeSilentOperationSink<WaveletOperation> sink) {
    IdGenerator idGenerator = FakeIdGenerator.create();
    FakeWaveView waveView = FakeWaveView.builder(new ConversationSchemas())
        .with(idGenerator)
        .with(sink)
        .build();
    WaveBasedConversationView conversationView = WaveBasedConversationView.create(waveView, idGenerator);
    WaveletBasedConversation conversation = conversationView.createConversation();
    sink.clear();
    return conversation;
  }
}

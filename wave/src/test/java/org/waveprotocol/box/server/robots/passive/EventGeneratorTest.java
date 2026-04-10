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

package org.waveprotocol.box.server.robots.passive;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.wave.api.data.converter.EventDataConverter;
import com.google.wave.api.data.converter.v22.EventDataConverterV22;
import com.google.wave.api.event.AnnotatedTextChangedEvent;
import com.google.wave.api.event.DocumentChangedEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.EventType;
import com.google.wave.api.event.FormValueChangedEvent;
import com.google.wave.api.event.WaveletBlipCreatedEvent;
import com.google.wave.api.event.WaveletBlipRemovedEvent;
import com.google.wave.api.event.WaveletParticipantsChangedEvent;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.robot.Capability;
import com.google.wave.api.robot.RobotName;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.robots.RobotsTestBase;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.operation.CapturingOperationSink;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipationHelper;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for the {@link EventGenerator}.
 *
 * This class constructs an {@link OpBasedWavelet} on which the operations
 * performed are captured. These operations will later be used for in the
 * {@link EventGenerator}.
 *
 * The wavelet will have {@code ALEX} as participant and an empty root blip in
 * its conversation structure.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class EventGeneratorTest extends RobotsTestBase {

  private final static RobotName ROBOT_NAME = RobotName.fromAddress(ROBOT.getAddress());
  private final static EventDataConverter CONVERTER = new EventDataConverterV22();
  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);
  private static final DocumentFactory<?> DOCUMENT_FACTORY =
      ObservablePluggableMutableDocument.createFactory(SchemaCollection.empty());
  private static final WaveletOperationContext.Factory CONTEXT_FACTORY =
      new WaveletOperationContext.Factory() {
        @Override
        public WaveletOperationContext createContext() {
          return new WaveletOperationContext(ALEX, 0L, 1);
        }

        @Override
        public WaveletOperationContext createContext(ParticipantId creator) {
          throw new UnsupportedOperationException();
        }
      };

  /** Map containing a subscription to all possible events */
  private static final Map<EventType, Capability> ALL_CAPABILITIES;
  static {
    ImmutableMap.Builder<EventType, Capability> builder = ImmutableMap.builder();
    for (EventType event : EventType.values()) {
      if (!event.equals(EventType.UNKNOWN)) {
        builder.put(event, new Capability(event));
      }
    }
    ALL_CAPABILITIES = builder.build();
  }

  private EventGenerator eventGenerator;
  private ObservableWaveletData waveletData;
  private OpBasedWavelet wavelet;
  private CapturingOperationSink<WaveletOperation> output;
  private ConversationUtil conversationUtil;

  private static final String TEST_RPC_SERVER_URL = "https://example.com/robot/dataapi/rpc";

  @Override
  protected void setUp() throws Exception {
    conversationUtil = new ConversationUtil(FakeIdGenerator.create());
    eventGenerator = new EventGenerator(ROBOT_NAME, conversationUtil);

    waveletData = WaveletDataImpl.Factory.create(DOCUMENT_FACTORY).create(
        new EmptyWaveletSnapshot(WAVELET_NAME.waveId, WAVELET_NAME.waveletId, ALEX,
            HASH_FACTORY.createVersionZero(WAVELET_NAME), 0L));

    // Robot should be participant in snapshot before deltas
    // otherwise events will be filtered out.
    waveletData.addParticipant(ROBOT);
    waveletData.addParticipant(ALEX);
    waveletData.setVersion(1);

    SilentOperationSink<WaveletOperation> executor =
        SilentOperationSink.Executor.<WaveletOperation, WaveletData> build(waveletData);
    output = new CapturingOperationSink<WaveletOperation>();
    wavelet =
        new OpBasedWavelet(waveletData.getWaveId(), waveletData, CONTEXT_FACTORY,
            ParticipationHelper.DEFAULT, executor, output);

    // Make a conversation and clear the sink
    WaveletBasedConversation.makeWaveletConversational(wavelet);
    conversationUtil.buildConversation(wavelet).getRoot().getRootThread().appendBlip();
    output.clear();
  }

  public void testGenerateEventsPopulatesRpcServerUrl() throws Exception {
    wavelet.addParticipant(BOB);
    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta = makeDeltaFromCapturedOps(ALEX, ops, endVersion, 0L);
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(delta));

    EventMessageBundle bundle =
        generateEvents(waveletAndDeltas, ALL_CAPABILITIES);

    assertEquals("rpcServerUrl must be populated in the event bundle",
        TEST_RPC_SERVER_URL, bundle.getRpcServerUrl());
  }

  public void testGenerateWaveletParticipantsChangedEventOnAdd() throws Exception {
    wavelet.addParticipant(BOB);
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_PARTICIPANTS_CHANGED);
    assertTrue("Only expected one event", messages.getEvents().size() == 1);
    WaveletParticipantsChangedEvent event =
        WaveletParticipantsChangedEvent.as(messages.getEvents().get(0));
    assertTrue("Bob should be added", event.getParticipantsAdded().contains(BOB.getAddress()));
  }

  public void testGenerateWaveletParticipantsChangedEventOnRemove() throws Exception {
    wavelet.removeParticipant(ALEX);
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_PARTICIPANTS_CHANGED);
    assertEquals("Expected one event", 1, messages.getEvents().size());
    WaveletParticipantsChangedEvent event =
      WaveletParticipantsChangedEvent.as(messages.getEvents().get(0));
    assertTrue(
        "Alex should be removed", event.getParticipantsRemoved().contains(ALEX.getAddress()));
  }

  public void testGenerateWaveletSelfAddedEvent() throws Exception {
    waveletData.removeParticipant(ROBOT);
    wavelet.addParticipant(ROBOT);
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_SELF_ADDED);
    assertEquals("Expected two events", 2, messages.getEvents().size());
  }

  public void testGenerateWaveletSelfRemovedEvent() throws Exception {
    wavelet.removeParticipant(ROBOT);
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_SELF_REMOVED);
    // Participant changed event,  after self removed event is filtered.
    assertEquals("Expected only one event", 1, messages.getEvents().size());
  }

  /**
   * Tests that events from a robot delta are filtered, after events from a
   * human delta are received.
   */
  public void testRobotSelfEventsFilteredAfterHuman() throws Exception {
    // Robot receives two deltas, it is participant in wavelet before deltas.
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    // Delta1 start events: event #1.
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();
    // Delta1 event #2.
    XmlStringBuilder builder = XmlStringBuilder.createText("some random content by alex");
    LineContainers.appendToLastLine(newBlip.getContent(), builder);

    List<WaveletOperation> ops1 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta1 = makeDeltaFromCapturedOps(ALEX, ops1, endVersion, 0L);
    output.clear();

    // Delta2 event #1.
    conversation = conversationUtil.buildConversation(wavelet);
    newBlip = conversation.getRoot().getRootThread().appendBlip();
    // Delta2 event #2.
    wavelet.addParticipant(BOB);
    // Delta2 event #3.
    builder = XmlStringBuilder.createText("some random content by robot");
    LineContainers.appendToLastLine(newBlip.getContent(), builder);
    List<WaveletOperation> ops2 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion2 = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta2 = makeDeltaFromCapturedOps(ROBOT, ops2, endVersion2, 0L);
    output.clear();

    assertTrue("Ops should not be empty", (!ops1.isEmpty()) && (!ops2.isEmpty()));

    EventMessageBundle messages = generateEventsFromDeltas(delta1, delta2);
    assertEquals("Expected two events", 2, messages.getEvents().size());
  }

  /**
   * Tests that events from a robot delta are filtered, before events from a
   * human delta are received.
   */
  public void testRobotSelfEventsFilteredBeforeHuman() throws Exception {
    // Robot receives two deltas, it is participant in wavelet before deltas.
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    // Delta1 start events: event #1.
    ObservableConversationBlip rootBlip = conversation.getRoot().getRootThread().getFirstBlip();
    // Delta1 event #2.
    XmlStringBuilder builder = XmlStringBuilder.createText("some random content by robot");
    LineContainers.appendToLastLine(rootBlip.getContent(), builder);
    // Delta1 event #3.
    wavelet.addParticipant(BOB);

    List<WaveletOperation> ops1 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta1 = makeDeltaFromCapturedOps(ROBOT, ops1, endVersion, 0L);
    output.clear();

    // Delta2 event #1.
    conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();
    // Delta2 event #2.
    builder = XmlStringBuilder.createText("some random content by alex");
    LineContainers.appendToLastLine(newBlip.getContent(), builder);

    List<WaveletOperation> ops2 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion2 = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta2 = makeDeltaFromCapturedOps(ALEX, ops2, endVersion2, 0L);
    output.clear();

    EventMessageBundle messages = generateEventsFromDeltas(delta1, delta2);
    assertEquals("Expected two events", 2, messages.getEvents().size());
  }

  public void testGenerateWaveletBlipCreatedEvent() throws Exception {
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_BLIP_CREATED);
    assertEquals("Expected one event", 1, messages.getEvents().size());
    WaveletBlipCreatedEvent event = WaveletBlipCreatedEvent.as(messages.getEvents().get(0));
    assertEquals("Expected the same id as the new blip", newBlip.getId(), event.getNewBlipId());
  }

  public void testGenerateWaveletBlipRemovedEvent() throws Exception {
    ObservableConversationThread rootThread =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread();
    ObservableConversationBlip newBlip = rootThread.appendBlip();
    newBlip.delete();
    EventMessageBundle messages = generateAndCheckEvents(EventType.WAVELET_BLIP_REMOVED);
    assertEquals("Expected two events", 2, messages.getEvents().size());
    // Blip removed should be the second event.
    WaveletBlipRemovedEvent event = WaveletBlipRemovedEvent.as(messages.getEvents().get(1));
    assertEquals("Expected the same id as the removed blip", newBlip.getId(),
        event.getRemovedBlipId());
  }

  public void testGenerateDocumentChangedEvent() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    XmlStringBuilder builder = XmlStringBuilder.createText("some random content");
    LineContainers.appendToLastLine(rootBlip.getContent(), builder);

    EventMessageBundle messages = generateAndCheckEvents(EventType.DOCUMENT_CHANGED);
    assertEquals("Expected one event", 1, messages.getEvents().size());
    // Can not check the blip id because it is not accessible, however the line
    // here below will confirm that there was actually a real
    // DocumentChangedEvent put into the message bundle.
    DocumentChangedEvent event = DocumentChangedEvent.as(messages.getEvents().get(0));
    assertEquals(ALEX.getAddress(), event.getModifiedBy());
  }

  public void testGenerateDocumentChangedEventOnlyOnce() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    // Change the document twice
    XmlStringBuilder builder = XmlStringBuilder.createText("some random content");
    LineContainers.appendToLastLine(rootBlip.getContent(), builder);
    LineContainers.appendToLastLine(rootBlip.getContent(), builder);

    EventMessageBundle messages = generateAndCheckEvents(EventType.DOCUMENT_CHANGED);
    assertEquals("Expected one event only", 1, messages.getEvents().size());
  }

  public void testGenerateAnnotatedTextChangedEvent() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    String annotationKey = "key";
    String annotationValue = "value";
    rootBlip.getContent().setAnnotation(0, 1, annotationKey, annotationValue);

    EventMessageBundle messages = generateAndCheckEvents(EventType.ANNOTATED_TEXT_CHANGED);
    assertEquals("Expected one event only", 1, messages.getEvents().size());
    AnnotatedTextChangedEvent event = AnnotatedTextChangedEvent.as(messages.getEvents().get(0));
    assertEquals("Expected the key of the annotation", annotationKey, event.getName());
    assertEquals("Expected the value of the annotation", annotationValue, event.getValue());
  }

  /**
   * Tests that BLIP_EDITING_DONE is synthesized when a user/d/ annotation gets its
   * end timestamp filled in (all editing sessions closed).
   */
  public void testGenerateBlipEditingDoneEventWhenEditingCompletes() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    // Simulate a user/d/ annotation with end timestamp filled in (editing complete).
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-1234",
        "alice@example.com,1775485999253,1775486001215");

    EventMessageBundle messages = generateAndCheckEvents(EventType.BLIP_EDITING_DONE);
    boolean found = false;
    for (Event event : messages.getEvents()) {
      if (event.getType() == EventType.BLIP_EDITING_DONE) {
        found = true;
        assertEquals(ALEX.getAddress(), event.getModifiedBy());
      }
    }
    assertTrue("Expected BLIP_EDITING_DONE event", found);
  }

  /**
   * Tests that BLIP_EDITING_DONE is NOT fired when the user/d/ annotation still has
   * an empty end timestamp (user is still editing).
   */
  public void testNoBlipEditingDoneWhileStillEditing() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    // Simulate a user/d/ annotation with empty end timestamp (still editing).
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-1234",
        "alice@example.com,1775485999253,");

    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta = makeDeltaFromCapturedOps(ALEX, ops, endVersion, 0L);
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(delta));

    Map<EventType, Capability> capabilities = Maps.newHashMap();
    capabilities.put(EventType.BLIP_EDITING_DONE, new Capability(EventType.BLIP_EDITING_DONE));

    EventMessageBundle messages =
        generateEvents(waveletAndDeltas, capabilities);

    for (Event event : messages.getEvents()) {
      assertFalse("BLIP_EDITING_DONE must not fire while editing is in progress",
          event.getType() == EventType.BLIP_EDITING_DONE);
    }
  }

  /**
   * Tests that BLIP_EDITING_DONE is NOT fired when one of multiple user/d/ annotations
   * still has an empty end timestamp (one user still editing).
   * Uses recent startTimeMs values so that neither session is treated as stale by the TTL check.
   */
  public void testNoBlipEditingDoneWhenOneSessionStillActive() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    // Use recent timestamps (well within the 30-minute staleness window).
    long startMs1 = System.currentTimeMillis() - 2000L; // 2 seconds ago
    long startMs2 = System.currentTimeMillis() - 3000L; // 3 seconds ago
    long endMs1 = System.currentTimeMillis() - 1000L;   // closed 1 second ago

    // Seed two active sessions.
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-1111",
        "alice@example.com," + startMs1 + ",");
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-3333",
        "bob@example.com," + startMs2 + ",");

    // Only capture the "close one session" transition in this delta.
    output.clear();

    // Close first session; second remains active — exercises allEditingSessionsClosed branch.
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-1111",
        "alice@example.com," + startMs1 + "," + endMs1);

    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta = makeDeltaFromCapturedOps(ALEX, ops, endVersion, 0L);
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(delta));

    Map<EventType, Capability> capabilities = Maps.newHashMap();
    capabilities.put(EventType.BLIP_EDITING_DONE, new Capability(EventType.BLIP_EDITING_DONE));

    EventMessageBundle messages =
        generateEvents(waveletAndDeltas, capabilities);

    for (Event event : messages.getEvents()) {
      assertFalse("BLIP_EDITING_DONE must not fire when any session is still active",
          event.getType() == EventType.BLIP_EDITING_DONE);
    }
  }

  /**
   * Tests that BLIP_EDITING_DONE fires when the last non-stale editing session closes, even if
   * another session has an empty end timestamp but a very old startTimeMs (stale/crashed client).
   * Without the TTL fix, the stale session would permanently block BLIP_EDITING_DONE.
   */
  public void testBlipEditingDoneFiresWhenOnlyStaleSessionsRemainOpen() throws Exception {
    ConversationBlip rootBlip =
        conversationUtil.buildConversation(wavelet).getRoot().getRootThread().getFirstBlip();

    // Seed a STALE session: startTimeMs is epoch+1s, well beyond the 30-minute threshold.
    long staleStartMs = 1000L;
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-stale",
        "alice@example.com," + staleStartMs + ",");

    // Seed an active (non-stale) session that started 1 second ago.
    long activeStartMs = System.currentTimeMillis() - 1000L;
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-active",
        "bob@example.com," + activeStartMs + ",");

    // Only capture the "close active session" transition in this delta.
    output.clear();

    long endMs = System.currentTimeMillis();
    // Close the active session by filling in endTimeMs.
    rootBlip.getContent().setAnnotation(0, 1, "user/d/session-active",
        "bob@example.com," + activeStartMs + "," + endMs);

    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta = makeDeltaFromCapturedOps(ALEX, ops, endVersion, 0L);
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(delta));

    Map<EventType, Capability> capabilities = Maps.newHashMap();
    capabilities.put(EventType.BLIP_EDITING_DONE, new Capability(EventType.BLIP_EDITING_DONE));

    EventMessageBundle messages =
        generateEvents(waveletAndDeltas, capabilities);

    boolean found = false;
    for (Event event : messages.getEvents()) {
      if (event.getType() == EventType.BLIP_EDITING_DONE) {
        found = true;
      }
    }
    assertTrue("BLIP_EDITING_DONE must fire when only stale (crashed-client) sessions remain",
        found);
  }

  public void testSelfEventsAreFiltered() throws Exception {
    // Robot receives two deltas, it is not participant in wavelet before deltas.
    waveletData.removeParticipant(ROBOT);
    // Delta1 event #1.
    wavelet.addParticipant(ROBOT);
    // Delta1 event #2.
    wavelet.addParticipant(BOB);
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    List<WaveletOperation> ops1 = Lists.newArrayList(output.getOps());
    TransformedWaveletDelta delta1 = makeDeltaFromCapturedOps(ALEX, ops1, endVersion, 0L);
    output.clear();

    // Delta2 event #1.
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();
    XmlStringBuilder builder = XmlStringBuilder.createText("some random content");
    // Delta2 event #2.
    LineContainers.appendToLastLine(newBlip.getContent(), builder);
    // Delta2 event #3.
    XmlStringBuilder.createText("some more random content by robot");
    LineContainers.appendToLastLine(newBlip.getContent(), builder);

    List<WaveletOperation> ops2 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion2 = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta2 = makeDeltaFromCapturedOps(ROBOT, ops2, endVersion2, 0L);
    output.clear();

    EventMessageBundle messages = generateEventsFromDeltas(delta1, delta2);
    assertEquals("Expected two events", 2, messages.getEvents().size());
    checkEventTypeWasGenerated(messages, EventType.WAVELET_SELF_ADDED,
        EventType.WAVELET_PARTICIPANTS_CHANGED);
  }

  public void testEventsFromFirstDeltaAreFiltered() throws Exception {
    // Robot receives two deltas, it is participant in wavelet before deltas.
    wavelet.addParticipant(BOB);
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    List<WaveletOperation> ops1 = Lists.newArrayList(output.getOps());
    TransformedWaveletDelta delta1 = makeDeltaFromCapturedOps(ROBOT, ops1, endVersion, 0L);
    output.clear();

    // Delta2 event #1.
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();

    XmlStringBuilder builder = XmlStringBuilder.createText("some random content");
    // Delta2 event #2.
    LineContainers.appendToLastLine(newBlip.getContent(), builder);
    // Delta2 event #3.
    wavelet.removeParticipant(BOB);

    List<WaveletOperation> ops2 = Lists.newArrayList(output.getOps());
    HashedVersion endVersion2 = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta2 = makeDeltaFromCapturedOps(ALEX, ops2, endVersion2, 0L);
    output.clear();

    EventMessageBundle messages = generateEventsFromDeltas(delta1, delta2);
    assertEquals("Expected three events", 3, messages.getEvents().size());
    checkEventTypeWasGenerated(messages, EventType.WAVELET_BLIP_CREATED,
        EventType.DOCUMENT_CHANGED, EventType.WAVELET_PARTICIPANTS_CHANGED);
  }

  public void testEventsFromSecondDeltaAreFiltered() throws Exception {
    // Robot receives two deltas, it is participant in wavelet before deltas
    // Delta1 event #1 - should be delivered to robot.
    wavelet.addParticipant(BOB);

    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta1 = makeDeltaFromCapturedOps(ALEX, ops, endVersion, 0L);
    output.clear();

    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    // Delta2 event #1.
    ObservableConversationBlip newBlip = conversation.getRoot().getRootThread().appendBlip();
    // Delta2 event #2.
    wavelet.removeParticipant(ROBOT);
    // Delta2 event #3 - should be filtered.
    XmlStringBuilder builder = XmlStringBuilder.createText("some random content");
    LineContainers.appendToLastLine(newBlip.getContent(), builder);


    List<WaveletOperation> ops2 = output.getOps();
    HashedVersion endVersion2 = HashedVersion.unsigned(waveletData.getVersion());
    TransformedWaveletDelta delta2 = makeDeltaFromCapturedOps(ALEX, ops2, endVersion2, 0L);
    output.clear();

    EventMessageBundle messages = generateEventsFromDeltas(delta1, delta2);
    assertEquals("Expected three events", 3, messages.getEvents().size());
    checkEventTypeWasGenerated(messages, EventType.WAVELET_PARTICIPANTS_CHANGED,
        EventType.WAVELET_BLIP_CREATED, EventType.WAVELET_SELF_REMOVED);
  }

  public void testGenerateFormValueChangedEvent() throws Exception {
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);
    ObservableConversationBlip rootBlip =
        conversation.getRoot().getRootThread().getFirstBlip();

    // Insert a check form element into the blip
    String checkXml = "<check name=\"agree\" value=\"false\"/>";
    LineContainers.appendToLastLine(
        rootBlip.getContent(), XmlStringBuilder.createFromXmlString(checkXml));

    // Clear ops from the insert — we only want to capture the value change
    output.clear();

    // Now change the value attribute on the check element to simulate user toggle
    Document doc = rootBlip.getContent();
    // Find the check element (may be nested inside body/line containers)
    org.waveprotocol.wave.model.document.Doc.E checkElem =
        findElementByTag(doc, doc.getDocumentElement(), "check");
    assertNotNull("Should find the check element", checkElem);
    doc.setElementAttribute(checkElem, "value", "true");

    EventMessageBundle messages = generateAndCheckEvents(EventType.FORM_VALUE_CHANGED);
    // Find the FORM_VALUE_CHANGED event in the bundle (DOCUMENT_CHANGED may also appear)
    FormValueChangedEvent event = null;
    for (com.google.wave.api.event.Event e : messages.getEvents()) {
      if (e instanceof FormValueChangedEvent) {
        event = (FormValueChangedEvent) e;
        break;
      }
    }
    assertNotNull("Expected a FORM_VALUE_CHANGED event", event);
    assertEquals("agree", event.getElementName());
    assertEquals("check", event.getElementType());
    assertEquals("false", event.getOldValue());
    assertEquals("true", event.getNewValue());
  }

  // Helper Methods.

  /**
   * Recursively searches for the first element with the given tag name in the document tree.
   */
  private org.waveprotocol.wave.model.document.Doc.E findElementByTag(
      Document doc,
      org.waveprotocol.wave.model.document.Doc.E parent,
      String tagName) {
    org.waveprotocol.wave.model.document.Doc.N child = doc.getFirstChild(parent);
    while (child != null) {
      org.waveprotocol.wave.model.document.Doc.E el = doc.asElement(child);
      if (el != null) {
        if (tagName.equals(doc.getTagName(el))) {
          return el;
        }
        org.waveprotocol.wave.model.document.Doc.E found = findElementByTag(doc, el, tagName);
        if (found != null) {
          return found;
        }
      }
      child = doc.getNextSibling(child);
    }
    return null;
  }

  /**
   * Collects the ops applied to wavelet and creates a delta for processing in
   * the event generator. The delta author is default human participantId
   *
   * @param eventType the type of event that should have been generated.
   * @return the {@link EventMessageBundle} with the events generated when a
   *         robot is subscribed to all possible events.
   *
   * @see #generateAndCheckEvents(EventType, ParticipantId)
   */
  private EventMessageBundle generateAndCheckEvents(EventType eventType) throws Exception {
    EventMessageBundle eventMessageBundle = generateAndCheckEvents(eventType, ALEX);
    return eventMessageBundle;
  }

  /**
   * Collects the ops applied to wavelet and creates a delta for processing in
   * the event generator.
   *
   * @param eventType the type of event that should have been generated.
   * @param participantId the delta author (modifier)
   * @return the {@link EventMessageBundle} with the events generated when a
   *         robot is subscribed to all possible events.
   *
   * @see #generateAndCheckEvents(EventType)
   */
  private EventMessageBundle generateAndCheckEvents(EventType eventType,
      ParticipantId participantId) throws Exception {
    List<WaveletOperation> ops = output.getOps();
    HashedVersion endVersion = HashedVersion.unsigned(waveletData.getVersion());
    // Create the delta.
    TransformedWaveletDelta delta = makeDeltaFromCapturedOps(participantId, ops, endVersion, 0L);
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(delta));

    // Put the wanted event in the capabilities map
    Map<EventType, Capability> capabilities = Maps.newHashMap();
    capabilities.put(eventType, new Capability(eventType));

    // Generate the events
    EventMessageBundle messages =
        generateEvents(waveletAndDeltas, capabilities);

    // Check that the event was generated and that no other types were generated
    checkEventTypeWasGenerated(messages, eventType);
    checkAllEventsAreInCapabilites(messages, capabilities);

    // Generate events with all capabilities
    messages = generateEvents(waveletAndDeltas, ALL_CAPABILITIES);
    checkEventTypeWasGenerated(messages, eventType);

    return messages;
  }

  /**
   * Builds a "transformed" delta from client ops (no transformation happens).
   */
  private TransformedWaveletDelta makeDeltaFromCapturedOps(ParticipantId author,
      List<WaveletOperation> ops, HashedVersion endVersion, long timestamp) {
    WaveletDelta clientDelta =
        new WaveletDelta(author, HashedVersion.unsigned(endVersion.getVersion() - ops.size()), ops);
    return TransformedWaveletDelta.cloneOperations(endVersion, timestamp, clientDelta);
  }

  /**
   * Checks whether events of the given types were put in the bundle.
   */
  private void checkEventTypeWasGenerated(EventMessageBundle messages, EventType... types) {
    Set<EventType> eventsTypeSet = Sets.newHashSet();
    for (EventType eventType : types) {
      eventsTypeSet.add(eventType);
    }

    for (Event event : messages.getEvents()) {
      if (eventsTypeSet.contains(event.getType())) {
        eventsTypeSet.remove(event.getType());
      }
    }
    if (eventsTypeSet.size() != 0) {
      fail("Event of type " + eventsTypeSet.iterator().next() + " has not been generated");
    }
  }

  /**
   * Generate events from deltas
   */
  private EventMessageBundle generateEventsFromDeltas(TransformedWaveletDelta... deltas)
      throws OperationException {
    WaveletAndDeltas waveletAndDeltas =
        WaveletAndDeltas.create(waveletData, DeltaSequence.of(deltas));

    Map<EventType, Capability> capabilities = ALL_CAPABILITIES;
    // Generate the events
    EventMessageBundle messages =
        generateEvents(waveletAndDeltas, capabilities);
    return messages;
  }

  private EventMessageBundle generateEvents(WaveletAndDeltas waveletAndDeltas,
      Map<EventType, Capability> capabilities) {
    return eventGenerator.generateEvents(
        waveletAndDeltas, capabilities, CONVERTER, TEST_RPC_SERVER_URL);
  }

  /**
   * Checks whether all events generated are in the capabilities map.
   */
  private void checkAllEventsAreInCapabilites(EventMessageBundle messages,
      Map<EventType, Capability> capabilities) {
    for (Event event : messages.getEvents()) {
      if (!capabilities.containsKey(event.getType())) {
        fail("Generated event of type" + event.getType() + " which is not in the capabilities");
      }
    }
  }
}

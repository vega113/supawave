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
 * specific language governing permissions and limitations under the
 * License.
 */

package org.waveprotocol.box.server.waveserver;

import junit.framework.TestCase;

import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.AnnotationBoundaryMapImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;

import java.util.Collections;
import java.util.Set;

public final class TaskDocumentExtractorTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final ParticipantId CREATOR = ParticipantId.ofUnsafe("creator@" + DOMAIN);

  public void testExtractsTaskAssigneeFromAnnotation() {
    WaveViewData wave = createWaveWithTaskAssignee("alice@example.com");

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(wave);

    assertEquals(1, assignees.size());
    assertTrue(assignees.contains("alice@example.com"));
  }

  public void testReturnsEmptySetWhenNoTaskAnnotations() {
    WaveViewData wave = createWaveWithPlainContent("Hello world");

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(wave);

    assertTrue(assignees.isEmpty());
  }

  public void testExtractsMultipleAssigneesFromDifferentBlips() {
    WaveId waveId = WaveId.of(DOMAIN, "w+multi");
    WaveletId waveletId = WaveletId.of(DOMAIN, "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, CREATOR, HashedVersion.unsigned(0), 1234567890);

    // First blip with task assigned to alice
    DocInitialization doc1 = new DocInitializationBuilder()
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationValues(AnnotationConstants.TASK_ASSIGNEE, "Alice@Example.com")
            .build())
        .characters("task one")
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
            .build())
        .build();
    wavelet.createDocument("b+blip1", CREATOR,
        Collections.<ParticipantId>emptySet(), doc1, 1234567890, 0);

    // Second blip with task assigned to bob
    DocInitialization doc2 = new DocInitializationBuilder()
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationValues(AnnotationConstants.TASK_ASSIGNEE, "Bob@Example.com")
            .build())
        .characters("task two")
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
            .build())
        .build();
    wavelet.createDocument("b+blip2", CREATOR,
        Collections.<ParticipantId>emptySet(), doc2, 1234567890, 0);

    WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
    waveView.addWavelet(wavelet);

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(waveView);

    assertEquals(2, assignees.size());
    assertTrue(assignees.contains("alice@example.com"));
    assertTrue(assignees.contains("bob@example.com"));
  }

  public void testAssigneesAreLowerCased() {
    WaveViewData wave = createWaveWithTaskAssignee("Alice@EXAMPLE.COM");

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(wave);

    assertEquals(1, assignees.size());
    assertTrue(assignees.contains("alice@example.com"));
  }

  public void testSkipsNonConversationalWavelets() {
    WaveId waveId = WaveId.of(DOMAIN, "w+skip");
    // Use a non-conversational wavelet id (e.g., user data wavelet)
    WaveletId waveletId = WaveletId.of(DOMAIN, "user+data");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, CREATOR, HashedVersion.unsigned(0), 1234567890);

    DocInitialization doc = new DocInitializationBuilder()
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationValues(AnnotationConstants.TASK_ASSIGNEE, "alice@example.com")
            .build())
        .characters("task text")
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
            .build())
        .build();
    wavelet.createDocument("b+blip1", CREATOR,
        Collections.<ParticipantId>emptySet(), doc, 1234567890, 0);

    WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
    waveView.addWavelet(wavelet);

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(waveView);

    assertTrue("Non-conversational wavelets should be skipped", assignees.isEmpty());
  }

  public void testIgnoresEmptyAnnotationValues() {
    WaveId waveId = WaveId.of(DOMAIN, "w+empty");
    WaveletId waveletId = WaveletId.of(DOMAIN, "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, CREATOR, HashedVersion.unsigned(0), 1234567890);

    DocInitialization doc = new DocInitializationBuilder()
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationValues(AnnotationConstants.TASK_ASSIGNEE, "")
            .build())
        .characters("task text")
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
            .build())
        .build();
    wavelet.createDocument("b+blip1", CREATOR,
        Collections.<ParticipantId>emptySet(), doc, 1234567890, 0);

    WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
    waveView.addWavelet(wavelet);

    Set<String> assignees = TaskDocumentExtractor.extractTaskAssignees(waveView);

    assertTrue("Empty annotation values should be ignored", assignees.isEmpty());
  }

  // ---- Helper methods ----

  private WaveViewData createWaveWithTaskAssignee(String assigneeAddress) {
    WaveId waveId = WaveId.of(DOMAIN, "w+task1");
    WaveletId waveletId = WaveletId.of(DOMAIN, "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, CREATOR, HashedVersion.unsigned(0), 1234567890);

    DocInitialization doc = new DocInitializationBuilder()
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationValues(AnnotationConstants.TASK_ASSIGNEE, assigneeAddress)
            .build())
        .characters("task text")
        .annotationBoundary(AnnotationBoundaryMapImpl.builder()
            .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
            .build())
        .build();
    wavelet.createDocument("b+blip1", CREATOR,
        Collections.<ParticipantId>emptySet(), doc, 1234567890, 0);

    WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
    waveView.addWavelet(wavelet);
    return waveView;
  }

  private WaveViewData createWaveWithPlainContent(String text) {
    WaveId waveId = WaveId.of(DOMAIN, "w+plain1");
    WaveletId waveletId = WaveletId.of(DOMAIN, "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, CREATOR, HashedVersion.unsigned(0), 1234567890);

    DocInitialization doc = new DocInitializationBuilder()
        .characters(text)
        .build();
    wavelet.createDocument("b+blip1", CREATOR,
        Collections.<ParticipantId>emptySet(), doc, 1234567890, 0);

    WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
    waveView.addWavelet(wavelet);
    return waveView;
  }
}

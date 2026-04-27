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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.wave.api.data.converter.EventDataConverterManager;

import junit.framework.TestCase;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Unit tests for {@link MarkBlipReadHelper}. The deep supplement-op write path
 * is covered end-to-end by
 * {@code wave/src/jakarta-test/.../J2clMarkBlipReadParityTest} (which boots a
 * real Wave server fixture); this unit test pins the outcome-mapping
 * (BAD_REQUEST, NOT_FOUND, INTERNAL_ERROR) and the access-guard contract.
 */
public final class MarkBlipReadHelperTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "w+abc");
  private static final WaveletId CONV_ROOT = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletId NOT_CONVERSATIONAL =
      IdUtil.buildUserDataWaveletId(USER);
  private static final String BLIP_ID = "b+abc";

  private SelectedWaveReadStateHelper readStateHelper;
  private MarkBlipReadHelper helper;

  @Override
  protected void setUp() {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    EventDataConverterManager converterManager = mock(EventDataConverterManager.class);
    ConversationUtil conversationUtil = mock(ConversationUtil.class);
    readStateHelper = mock(SelectedWaveReadStateHelper.class);
    helper = new MarkBlipReadHelper(
        waveletProvider, converterManager, conversationUtil, readStateHelper);
  }

  public void testMarkBlipReadReturnsBadRequestWhenUserIsNull() {
    MarkBlipReadHelper.Result result = helper.markBlipRead(null, WAVE_ID, CONV_ROOT, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST, result.getOutcome());
    assertEquals(-1, result.getUnreadCountAfter());
  }

  public void testMarkBlipReadReturnsBadRequestWhenWaveIdIsNull() {
    MarkBlipReadHelper.Result result = helper.markBlipRead(USER, null, CONV_ROOT, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST, result.getOutcome());
  }

  public void testMarkBlipReadReturnsBadRequestWhenWaveletIdIsNull() {
    MarkBlipReadHelper.Result result = helper.markBlipRead(USER, WAVE_ID, null, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST, result.getOutcome());
  }

  public void testMarkBlipReadReturnsBadRequestWhenBlipIdIsEmpty() {
    MarkBlipReadHelper.Result result = helper.markBlipRead(USER, WAVE_ID, CONV_ROOT, "");
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST, result.getOutcome());
  }

  public void testMarkBlipReadReturnsBadRequestWhenWaveletIsNotConversational() {
    // The mark-blip-read pipeline only mutates the UDW. Refuse non-conversational
    // wavelet ids before any backend call so we never accidentally probe
    // unrelated wavelets.
    MarkBlipReadHelper.Result result =
        helper.markBlipRead(USER, WAVE_ID, NOT_CONVERSATIONAL, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST, result.getOutcome());
  }

  public void testMarkBlipReadReturnsNotFoundWhenAccessProbeReportsMissingWave() {
    when(readStateHelper.computeReadState(USER, WAVE_ID))
        .thenReturn(SelectedWaveReadStateHelper.Result.notFound());

    MarkBlipReadHelper.Result result = helper.markBlipRead(USER, WAVE_ID, CONV_ROOT, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.NOT_FOUND, result.getOutcome());
    assertEquals(-1, result.getUnreadCountAfter());
  }

  public void testMarkBlipReadReturnsInternalErrorWhenAccessProbeRaises() {
    when(readStateHelper.computeReadState(USER, WAVE_ID))
        .thenThrow(new RuntimeException("transient backend failure"));

    MarkBlipReadHelper.Result result = helper.markBlipRead(USER, WAVE_ID, CONV_ROOT, BLIP_ID);
    assertEquals(MarkBlipReadHelper.Outcome.INTERNAL_ERROR, result.getOutcome());
  }

  public void testResultFactories() {
    MarkBlipReadHelper.Result ok = MarkBlipReadHelper.Result.ok(7);
    assertEquals(MarkBlipReadHelper.Outcome.OK, ok.getOutcome());
    assertEquals(7, ok.getUnreadCountAfter());

    MarkBlipReadHelper.Result already = MarkBlipReadHelper.Result.alreadyRead(0);
    assertEquals(MarkBlipReadHelper.Outcome.ALREADY_READ, already.getOutcome());
    assertEquals(0, already.getUnreadCountAfter());

    // Negative counts are clamped to 0 to keep the wire response well-formed.
    MarkBlipReadHelper.Result clamped = MarkBlipReadHelper.Result.ok(-3);
    assertEquals(0, clamped.getUnreadCountAfter());

    assertEquals(MarkBlipReadHelper.Outcome.NOT_FOUND,
        MarkBlipReadHelper.Result.notFound().getOutcome());
    assertEquals(MarkBlipReadHelper.Outcome.BAD_REQUEST,
        MarkBlipReadHelper.Result.badRequest().getOutcome());
    assertEquals(MarkBlipReadHelper.Outcome.INTERNAL_ERROR,
        MarkBlipReadHelper.Result.internalError().getOutcome());
  }
}

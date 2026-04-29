package org.waveprotocol.box.server.rpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverter;
import com.google.wave.api.data.converter.EventDataConverterManager;
import junit.framework.TestCase;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class FolderServletTest extends TestCase {

  private static final ParticipantId PARTICIPANT = ParticipantId.ofUnsafe("viewer@example.com");
  private static final WaveId WAVE_ID = WaveId.of("example.com", "waveid");
  private static final WaveletId CONVERSATION_ROOT_ID =
      WaveletId.of("example.com", "conv+root");
  private static final WaveletName CONVERSATION_ROOT_WAVELET_NAME =
      WaveletName.of(WAVE_ID, CONVERSATION_ROOT_ID);
  private static final WaveletId USER_DATA_WAVELET_ID = IdUtil.buildUserDataWaveletId(PARTICIPANT);
  private static final WaveletName USER_DATA_WAVELET_NAME =
      WaveletName.of(WAVE_ID, USER_DATA_WAVELET_ID);

  private SessionManager sessionManager;
  private WaveletProvider waveletProvider;
  private ConversationUtil conversationUtil;
  private EventDataConverterManager converterManager;
  private FolderServlet servlet;

  @Override
  protected void setUp() throws Exception {
    sessionManager = mock(SessionManager.class);
    waveletProvider = mock(WaveletProvider.class);
    conversationUtil = mock(ConversationUtil.class);
    converterManager = mock(EventDataConverterManager.class);
    EventDataConverter converter = mock(EventDataConverter.class);

    when(converterManager.getEventDataConverter(ProtocolVersion.DEFAULT)).thenReturn(converter);

    servlet = new FolderServlet(
        sessionManager, waveletProvider, conversationUtil, converterManager);
  }

  public void testSetPinStateAllowsAccessibleWaveWithoutExistingUserDataWavelet() throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(CONVERSATION_ROOT_ID));
    when(waveletProvider.checkAccessPermission(CONVERSATION_ROOT_WAVELET_NAME, PARTICIPANT))
        .thenReturn(true);
    when(waveletProvider.getSnapshot(USER_DATA_WAVELET_NAME)).thenReturn(null);

    servlet.setPinState(WAVE_ID, true, PARTICIPANT);

    verify(waveletProvider).submitRequest(eq(USER_DATA_WAVELET_NAME), any(), any());
  }

  public void testSetPinStateRejectsInaccessibleWave() throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(CONVERSATION_ROOT_ID));
    when(waveletProvider.checkAccessPermission(CONVERSATION_ROOT_WAVELET_NAME, PARTICIPANT))
        .thenReturn(false);

    try {
      servlet.setPinState(WAVE_ID, true, PARTICIPANT);
      fail("Expected InvalidRequestException");
    } catch (InvalidRequestException expected) {
      assertEquals("Access rejected", expected.getMessage());
    }

    verify(waveletProvider, never()).submitRequest(eq(USER_DATA_WAVELET_NAME), any(), any());
  }

  // G-PORT-8 (#1117): when the persisted wavelet store has not yet
  // recorded any wavelets for a freshly-created wave, fall back to
  // probing the conv+root wavelet name directly so the access check
  // still runs. Mirrors the search service's in-memory fallback in
  // SimpleSearchProviderImpl.expandConversationalWavelets and prevents
  // every pin/archive on a brand-new Welcome wave from returning 500
  // "Access rejected".
  public void testSetPinStateAllowsFreshlyCreatedWaveNotYetPersisted() throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.<WaveletId>of());
    when(waveletProvider.checkAccessPermission(CONVERSATION_ROOT_WAVELET_NAME, PARTICIPANT))
        .thenReturn(true);
    when(waveletProvider.getSnapshot(USER_DATA_WAVELET_NAME)).thenReturn(null);

    servlet.setPinState(WAVE_ID, true, PARTICIPANT);

    verify(waveletProvider).submitRequest(eq(USER_DATA_WAVELET_NAME), any(), any());
  }

  public void testSetPinStateRejectsFreshlyCreatedWaveWithNoAccess() throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.<WaveletId>of());
    when(waveletProvider.checkAccessPermission(CONVERSATION_ROOT_WAVELET_NAME, PARTICIPANT))
        .thenReturn(false);

    try {
      servlet.setPinState(WAVE_ID, true, PARTICIPANT);
      fail("Expected InvalidRequestException for inaccessible fresh wave");
    } catch (InvalidRequestException expected) {
      assertEquals("Access rejected", expected.getMessage());
    }

    verify(waveletProvider, never()).submitRequest(eq(USER_DATA_WAVELET_NAME), any(), any());
  }

  // The fallback path also covers the "unknown waveId" case (typo,
  // attacker probing): persisted-store empty AND access denied on
  // the synthetic conv+root wavelet name. Same Access-rejected
  // surface — no information leak about whether the wave exists.
  public void testSetPinStateRejectsUnknownWaveSameSurfaceAsNoAccess() throws Exception {
    WaveId unknown = WaveId.of("example.com", "no-such-wave");
    WaveletName unknownConvRoot = WaveletName.of(
        unknown, WaveletId.of(unknown.getDomain(), "conv+root"));
    when(waveletProvider.getWaveletIds(unknown)).thenReturn(ImmutableSet.<WaveletId>of());
    when(waveletProvider.checkAccessPermission(unknownConvRoot, PARTICIPANT))
        .thenReturn(false);

    try {
      servlet.setPinState(unknown, true, PARTICIPANT);
      fail("Expected InvalidRequestException for unknown wave id");
    } catch (InvalidRequestException expected) {
      assertEquals("Access rejected", expected.getMessage());
    }

    verify(waveletProvider, never())
        .submitRequest(eq(WaveletName.of(unknown, IdUtil.buildUserDataWaveletId(PARTICIPANT))),
            any(), any());
  }

  // --- stripVersionSuffix tests ---

  public void testStripVersionSuffix_noSuffix() {
    assertEquals("example.com/w+abc", FolderServlet.stripVersionSuffix("example.com/w+abc"));
  }

  public void testStripVersionSuffix_withSuffix() {
    assertEquals("example.com/w+abc", FolderServlet.stripVersionSuffix("example.com/w+abc:1"));
  }

  public void testStripVersionSuffix_withLargeVersion() {
    assertEquals("example.com/w+abc", FolderServlet.stripVersionSuffix("example.com/w+abc:12345"));
  }

  public void testStripVersionSuffix_null() {
    assertNull(FolderServlet.stripVersionSuffix(null));
  }

  public void testStripVersionSuffix_emptyString() {
    assertEquals("", FolderServlet.stripVersionSuffix(""));
  }

  public void testStripVersionSuffix_colonWithoutDigits() {
    assertEquals("example.com/w+abc:xyz", FolderServlet.stripVersionSuffix("example.com/w+abc:xyz"));
  }

  public void testStripVersionSuffix_multipleColons() {
    // For modern slash-form IDs, only the trailing :N should be stripped
    assertEquals("example.com/w+a:b:c", FolderServlet.stripVersionSuffix("example.com/w+a:b:c:42"));
  }

  public void testStripVersionSuffix_colonInMiddle() {
    // Colon not at the end followed by digits - should not be stripped
    assertEquals("example.com/w+abc:1:b", FolderServlet.stripVersionSuffix("example.com/w+abc:1:b"));
  }

  public void testStripVersionSuffix_legacyIdUnchanged() {
    // Legacy IDs use '!' as separator - do not strip even if they end in :N
    assertEquals("example.com!w+abc:1", FolderServlet.stripVersionSuffix("example.com!w+abc:1"));
  }
}

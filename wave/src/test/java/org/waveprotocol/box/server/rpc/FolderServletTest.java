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
}

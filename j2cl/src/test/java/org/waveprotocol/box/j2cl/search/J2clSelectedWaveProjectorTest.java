package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

@J2clTestInput(J2clSelectedWaveProjectorTest.class)
public class J2clSelectedWaveProjectorTest {
  private static final String WAVE_ID = "example.com/w+1";
  private static final String WAVELET_NAME = "example.com!w+1/example.com!conv+root";
  private static final String CHANNEL_ID = "chan-1";

  // -- Read-state projection (issue #931) -------------------------------------

  @Test
  public void projectUsesServerReadStateWhenPresent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 2),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 5, false),
            false);

    Assert.assertTrue(projected.isReadStateKnown());
    Assert.assertEquals(5, projected.getUnreadCount());
    Assert.assertEquals("5 unread.", projected.getUnreadText());
  }

  @Test
  public void projectFallsBackToDigestWhenServerReadStateAbsent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 3),
            sampleUpdate(),
            null,
            0);

    Assert.assertFalse(projected.isReadStateKnown());
    Assert.assertEquals("3 unread in the selected digest.", projected.getUnreadText());
  }

  @Test
  public void projectCarriesForwardPreviousServerReadStateAcrossUpdates() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 2, false),
            false);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            first,
            0);

    Assert.assertTrue(second.isReadStateKnown());
    Assert.assertEquals(2, second.getUnreadCount());
    Assert.assertEquals("2 unread.", second.getUnreadText());
  }

  @Test
  public void projectRendersReadWhenServerReportsZero() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 7),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 0, true),
            false);

    Assert.assertTrue(projected.isReadStateKnown());
    Assert.assertTrue(projected.isRead());
    Assert.assertEquals("Read.", projected.getUnreadText());
  }

  @Test
  public void staleFlagPreservesPriorCountAndAnnotatesStatus() {
    J2clSelectedWaveModel fresh =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 4, false),
            false);

    J2clSelectedWaveModel stale =
        J2clSelectedWaveProjector.reprojectReadState(fresh, null, null, true);

    Assert.assertTrue(stale.isReadStateStale());
    Assert.assertEquals(4, stale.getUnreadCount());
    Assert.assertEquals("4 unread.", stale.getUnreadText());
  }

  @Test
  public void projectDoesNotAnnotateStaleStatusWhenReadStateIsUnknown() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 3),
            sampleUpdate(),
            null,
            0,
            null,
            true);

    Assert.assertFalse(projected.isReadStateKnown());
    Assert.assertFalse(projected.isReadStateStale());
    Assert.assertEquals("Live updates connected.", projected.getStatusText());
  }

  @Test
  public void projectKeepsStableReadBlipIdsFromFragmentSegments() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    9L,
                    0L,
                    9L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L),
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+reply", "Reply text", 0, 0)))),
            null,
            0);

    Assert.assertEquals(2, projected.getReadBlips().size());
    Assert.assertEquals("b+root", projected.getReadBlips().get(0).getBlipId());
    Assert.assertEquals("Root text", projected.getReadBlips().get(0).getText());
    Assert.assertEquals("b+reply", projected.getReadBlips().get(1).getBlipId());
    Assert.assertEquals("Reply text", projected.getReadBlips().get(1).getText());
  }

  @Test
  public void projectFallsBackToDocumentBlipsWhenFragmentsAreAbsent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 7L, 8L, "Document text"),
                    new SidecarSelectedWaveDocument(
                        "conversation", "user@example.com", 7L, 8L, "metadata")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Document text", blip.getText());
  }

  @Test
  public void projectPrefersFragmentReadBlipsOverDocumentFallbacks() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 7L, 8L, "Document text")),
                new SidecarSelectedWaveFragments(
                    9L,
                    0L,
                    9L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Fragment text", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Fragment text", blip.getText());
  }

  // -- Write-session coupling (pre-existing) ----------------------------------

  @Test
  public void advancesWriteSessionWhenUpdateCarriesCoupledVersionAndHash() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    J2clSelectedWaveModel result =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            null,
            updateWithVersionAndHash(50L, "EFGH"),
            previous,
            0);

    J2clSidecarWriteSession writeSession = result.getWriteSession();
    Assert.assertNotNull(writeSession);
    Assert.assertEquals(50L, writeSession.getBaseVersion());
    Assert.assertEquals("EFGH", writeSession.getHistoryHash());
  }

  @Test
  public void preservesPreviousPairWhenUpdateOmitsHistoryHash() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    J2clSidecarWriteSession nullHash =
        J2clSelectedWaveProjector.project(
                WAVE_ID, null, updateWithVersionAndHash(50L, null), previous, 0)
            .getWriteSession();
    Assert.assertNotNull(nullHash);
    Assert.assertEquals(44L, nullHash.getBaseVersion());
    Assert.assertEquals("ABCD", nullHash.getHistoryHash());

    J2clSidecarWriteSession emptyHash =
        J2clSelectedWaveProjector.project(
                WAVE_ID, null, updateWithVersionAndHash(50L, ""), previous, 0)
            .getWriteSession();
    Assert.assertNotNull(emptyHash);
    Assert.assertEquals(44L, emptyHash.getBaseVersion());
    Assert.assertEquals("ABCD", emptyHash.getHistoryHash());
  }

  @Test
  public void preservesPreviousPairWhenUpdateHasNoResultingVersion() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            2,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            null,
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 60L, 61L, "Later content")),
            new SidecarSelectedWaveFragments(
                70L,
                50L,
                70L,
                Arrays.asList(
                    new SidecarSelectedWaveFragmentRange("blip:b+root", 50L, 70L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+root", "Later content", 0, 0))));

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, previous, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
  }

  @Test
  public void returnsNullWriteSessionWhenNoPreviousAndUpdateLacksCoupledPair() {
    SidecarSelectedWaveUpdate noVersion =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 1L, 2L, "Bootstrap")),
            null);
    Assert.assertNull(
        J2clSelectedWaveProjector.project(WAVE_ID, null, noVersion, null, 0).getWriteSession());

    SidecarSelectedWaveUpdate noHash = updateWithVersionAndHash(5L, null);
    Assert.assertNull(
        J2clSelectedWaveProjector.project(WAVE_ID, null, noHash, null, 0).getWriteSession());
  }

  @Test
  public void buildsWriteSessionOnFirstCoupledUpdate() {
    SidecarSelectedWaveUpdate update = updateWithVersionAndHash(0L, "ZERO");

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(0L, writeSession.getBaseVersion());
    Assert.assertEquals("ZERO", writeSession.getHistoryHash());
    Assert.assertEquals(CHANNEL_ID, writeSession.getChannelId());
    Assert.assertEquals("b+root", writeSession.getReplyTargetBlipId());
  }

  // -- Helpers ----------------------------------------------------------------

  private static J2clSearchDigestItem digest(String title, String snippet, int unreadCount) {
    return new J2clSearchDigestItem(
        WAVE_ID, title, snippet, "user@example.com", unreadCount, 2, 1L, false);
  }

  private static SidecarSelectedWaveUpdate sampleUpdate() {
    return new SidecarSelectedWaveUpdate(
        1,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        -1L,
        null,
        Arrays.asList("user@example.com"),
        new ArrayList<SidecarSelectedWaveDocument>(),
        null);
  }

  private static SidecarSelectedWaveUpdate updateWithVersionAndHash(
      long resultingVersion, String resultingVersionHistoryHash) {
    return new SidecarSelectedWaveUpdate(
        1,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        resultingVersion,
        resultingVersionHistoryHash,
        Arrays.asList("user@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument(
                "b+root", "user@example.com", 33L, 44L, "content")),
        new SidecarSelectedWaveFragments(
            resultingVersion >= 0 ? resultingVersion : 0L,
            0L,
            resultingVersion >= 0 ? resultingVersion : 0L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 0L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("blip:b+root", "content", 0, 0))));
  }

  private static J2clSelectedWaveModel modelWithWriteSession(long baseVersion, String historyHash) {
    J2clSidecarWriteSession writeSession =
        new J2clSidecarWriteSession(WAVE_ID, CHANNEL_ID, baseVersion, historyHash, "b+root");
    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        WAVE_ID,
        "title",
        "snippet",
        "",
        "",
        "",
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        writeSession,
        J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
        false,
        false,
        false);
  }
}

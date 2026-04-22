package org.waveprotocol.box.j2cl.search;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

public class J2clSelectedWaveProjectorTest {
  private static final String WAVE_ID = "example.com/w+1";
  private static final String WAVELET_NAME = "example.com!w+1/example.com!conv+root";
  private static final String CHANNEL_ID = "chan-1";

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
        writeSession);
  }
}

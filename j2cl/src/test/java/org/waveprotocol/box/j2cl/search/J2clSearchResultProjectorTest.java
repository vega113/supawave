package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSearchResultProjectorTest.class)
public class J2clSearchResultProjectorTest {
  @Test
  public void normalizeQueryFallsBackToInboxForBlankValues() {
    Assert.assertEquals("in:inbox", J2clSearchResultProjector.normalizeQuery(null));
    Assert.assertEquals("in:inbox", J2clSearchResultProjector.normalizeQuery("   "));
    Assert.assertEquals("with:@", J2clSearchResultProjector.normalizeQuery("with:@"));
  }

  @Test
  public void pageSizeMatchesLegacyDesktopAndMobileBreakpoints() {
    Assert.assertEquals(15, J2clSearchResultProjector.getPageSizeForViewport(768));
    Assert.assertEquals(15, J2clSearchResultProjector.getPageSizeForViewport(420));
    Assert.assertEquals(30, J2clSearchResultProjector.getPageSizeForViewport(1280));
  }

  @Test
  public void projectBuildsWaveCountAndShowMoreStateFromResponse() {
    SidecarSearchResponse response =
        new SidecarSearchResponse(
            "in:archive",
            28,
            Arrays.asList(
                new SidecarSearchResponse.Digest(
                    "Alpha",
                    "Snippet A",
                    "example.com/w+alpha",
                    111L,
                    2,
                    4,
                    Collections.singletonList("teammate@example.com"),
                    "author@example.com",
                    false),
                new SidecarSearchResponse.Digest(
                    "Beta",
                    "Snippet B",
                    "example.com/w+beta",
                    222L,
                    0,
                    7,
                    Collections.singletonList("friend@example.com"),
                    "other@example.com",
                    true)));

    J2clSearchResultModel model = J2clSearchResultProjector.project(response, 30);

    Assert.assertEquals("2 of 28 waves · 1 unread", model.getWaveCountText());
    Assert.assertTrue(model.isShowMoreVisible());
    Assert.assertFalse(model.isEmpty());
    Assert.assertEquals(2, model.getDigestItems().size());
    Assert.assertEquals("example.com/w+alpha", model.getDigestItems().get(0).getWaveId());
    Assert.assertEquals("author@example.com", model.getDigestItems().get(0).getAuthor());
    Assert.assertTrue(model.getDigestItems().get(1).isPinned());
  }

  @Test
  public void projectUsesExplicitEmptyStateWhenNoDigestsAreReturned() {
    J2clSearchResultModel model =
        J2clSearchResultProjector.project(new SidecarSearchResponse("in:inbox", 0, null), 30);

    Assert.assertTrue(model.isEmpty());
    Assert.assertEquals("No waves matched this query.", model.getEmptyMessage());
    Assert.assertFalse(model.isShowMoreVisible());
  }
}

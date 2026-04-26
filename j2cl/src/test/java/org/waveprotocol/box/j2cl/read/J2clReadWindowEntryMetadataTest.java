package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

/**
 * F-2 (#1037, R-3.1 / R-4.4) — coverage for the per-blip metadata fields on
 * {@link J2clReadWindowEntry}. Mirrors {@link J2clReadBlipMetadataTest} but
 * exercises the viewport-window entry shape that the read renderer
 * actually consumes for the F-1 viewport-scoped path.
 */
@J2clTestInput(J2clReadWindowEntryMetadataTest.class)
public class J2clReadWindowEntryMetadataTest {

  @Test
  public void legacyLoadedFactoryDefaultsMetadata() {
    J2clReadWindowEntry entry =
        J2clReadWindowEntry.loaded("blip:b+1", 0L, 1L, "b+1", "hello");

    Assert.assertTrue(entry.isLoaded());
    Assert.assertEquals("", entry.getAuthorId());
    Assert.assertEquals("", entry.getAuthorDisplayName());
    Assert.assertEquals(0L, entry.getLastModifiedTimeMillis());
    Assert.assertEquals("", entry.getParentBlipId());
    Assert.assertEquals("", entry.getThreadId());
    Assert.assertFalse(entry.isUnread());
    Assert.assertFalse(entry.hasMention());
  }

  @Test
  public void loadedWithMetadataExposesEveryField() {
    J2clReadWindowEntry entry =
        J2clReadWindowEntry.loadedWithMetadata(
            "blip:b+2",
            0L,
            5L,
            "b+2",
            "world",
            Collections.emptyList(),
            "alice@example.com",
            "Alice",
            1714134000000L,
            "b+1",
            "t+thread1",
            true,
            true);

    Assert.assertEquals("alice@example.com", entry.getAuthorId());
    Assert.assertEquals("Alice", entry.getAuthorDisplayName());
    Assert.assertEquals(1714134000000L, entry.getLastModifiedTimeMillis());
    Assert.assertEquals("b+1", entry.getParentBlipId());
    Assert.assertEquals("t+thread1", entry.getThreadId());
    Assert.assertTrue(entry.isUnread());
    Assert.assertTrue(entry.hasMention());
  }

  @Test
  public void displayNameFallsBackToAuthorIdWhenWhitespaceOnly() {
    // CodeRabbit PRRT_kwDOBwxLXs59qNMS: same fallback hole as
    // J2clReadBlip — "   " is not empty so callers get a blank label
    // instead of authorId. trim() before isEmpty() so the fallback
    // fires.
    J2clReadWindowEntry entry =
        J2clReadWindowEntry.loadedWithMetadata(
            "blip:b+2w",
            0L,
            5L,
            "b+2w",
            "world",
            Collections.emptyList(),
            "frank@example.com",
            "   ",
            0L,
            "",
            "",
            false,
            false);

    Assert.assertEquals("frank@example.com", entry.getAuthorDisplayName());
  }

  @Test
  public void displayNameTrimsSurroundingWhitespaceWhenNonEmpty() {
    J2clReadWindowEntry entry =
        J2clReadWindowEntry.loadedWithMetadata(
            "blip:b+2t",
            0L,
            5L,
            "b+2t",
            "world",
            Collections.emptyList(),
            "grace@example.com",
            "  Grace  ",
            0L,
            "",
            "",
            false,
            false);

    Assert.assertEquals("Grace", entry.getAuthorDisplayName());
  }

  @Test
  public void placeholderRetainsLegacyShape() {
    J2clReadWindowEntry placeholder =
        J2clReadWindowEntry.placeholder("blip:b+3", 0L, 9L, "b+3");

    Assert.assertFalse(placeholder.isLoaded());
    Assert.assertEquals("b+3", placeholder.getBlipId());
    Assert.assertEquals("", placeholder.getText());
    Assert.assertEquals("", placeholder.getAuthorId());
    Assert.assertEquals(0L, placeholder.getLastModifiedTimeMillis());
    Assert.assertFalse(placeholder.isUnread());
    Assert.assertFalse(placeholder.hasMention());
  }

  @Test
  public void withUnreadFlipsAndPreservesMetadata() {
    J2clReadWindowEntry entry =
        J2clReadWindowEntry.loadedWithMetadata(
            "blip:b+4",
            0L,
            7L,
            "b+4",
            "x",
            Collections.emptyList(),
            "a@x",
            "A",
            42L,
            "b+0",
            "t+0",
            false,
            true);

    J2clReadWindowEntry flipped = entry.withUnread(true);

    Assert.assertNotSame(entry, flipped);
    Assert.assertTrue(flipped.isUnread());
    Assert.assertEquals("b+4", flipped.getBlipId());
    Assert.assertEquals(42L, flipped.getLastModifiedTimeMillis());
    Assert.assertTrue(flipped.hasMention());
    Assert.assertEquals("b+0", flipped.getParentBlipId());

    Assert.assertSame(flipped, flipped.withUnread(true));
  }
}

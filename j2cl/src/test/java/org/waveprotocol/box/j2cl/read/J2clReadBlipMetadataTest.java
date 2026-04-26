package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

/**
 * F-2 (#1037, R-3.1 / R-3.4 / R-4.4) coverage for the per-blip metadata
 * fields added to {@link J2clReadBlip}. Contracts asserted:
 *
 * <ul>
 *   <li>The legacy two-arg + three-arg constructors stay backwards-compatible.
 *   <li>The full-metadata constructor exposes every field via the new
 *       getters with null-safe defaults.
 *   <li>{@link J2clReadBlip#withUnread(boolean)} returns the same instance
 *       when the flag does not change (cheap path) and a new instance with
 *       the toggled flag otherwise.
 *   <li>{@link J2clReadBlip#getAuthorDisplayName()} falls back to the
 *       author id when the display name is empty so the avatar chip never
 *       shows an empty label.
 * </ul>
 */
@J2clTestInput(J2clReadBlipMetadataTest.class)
public class J2clReadBlipMetadataTest {

  @Test
  public void legacyTwoArgConstructorDefaultsMetadataToEmpty() {
    J2clReadBlip blip = new J2clReadBlip("b+1", "hello");

    Assert.assertEquals("b+1", blip.getBlipId());
    Assert.assertEquals("hello", blip.getText());
    Assert.assertEquals("", blip.getAuthorId());
    Assert.assertEquals("", blip.getAuthorDisplayName());
    Assert.assertEquals(0L, blip.getLastModifiedTimeMillis());
    Assert.assertEquals("", blip.getParentBlipId());
    Assert.assertEquals("", blip.getThreadId());
    Assert.assertFalse(blip.isUnread());
    Assert.assertFalse(blip.hasMention());
  }

  @Test
  public void fullConstructorExposesEveryField() {
    J2clReadBlip blip =
        new J2clReadBlip(
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

    Assert.assertEquals("alice@example.com", blip.getAuthorId());
    Assert.assertEquals("Alice", blip.getAuthorDisplayName());
    Assert.assertEquals(1714134000000L, blip.getLastModifiedTimeMillis());
    Assert.assertEquals("b+1", blip.getParentBlipId());
    Assert.assertEquals("t+thread1", blip.getThreadId());
    Assert.assertTrue(blip.isUnread());
    Assert.assertTrue(blip.hasMention());
  }

  @Test
  public void displayNameFallsBackToAuthorIdWhenBlank() {
    J2clReadBlip blip =
        new J2clReadBlip(
            "b+3",
            "x",
            Collections.emptyList(),
            "carol@example.com",
            "",
            0L,
            "",
            "",
            false,
            false);

    Assert.assertEquals("carol@example.com", blip.getAuthorDisplayName());
  }

  @Test
  public void withUnreadIdentityWhenFlagUnchanged() {
    J2clReadBlip blip =
        new J2clReadBlip(
            "b+4",
            "x",
            Collections.emptyList(),
            "a@x",
            "A",
            0L,
            "",
            "",
            false,
            false);

    Assert.assertSame(blip, blip.withUnread(false));
  }

  @Test
  public void withUnreadAllocatesWhenFlagFlipped() {
    J2clReadBlip blip =
        new J2clReadBlip(
            "b+5",
            "x",
            Collections.emptyList(),
            "a@x",
            "A",
            42L,
            "b+0",
            "t+0",
            false,
            true);

    J2clReadBlip flipped = blip.withUnread(true);

    Assert.assertNotSame(blip, flipped);
    Assert.assertTrue(flipped.isUnread());
    Assert.assertEquals("b+5", flipped.getBlipId());
    Assert.assertEquals("a@x", flipped.getAuthorId());
    Assert.assertEquals(42L, flipped.getLastModifiedTimeMillis());
    Assert.assertEquals("b+0", flipped.getParentBlipId());
    Assert.assertEquals("t+0", flipped.getThreadId());
    Assert.assertTrue(flipped.hasMention());
  }

  @Test
  public void negativeTimestampClampsToZero() {
    J2clReadBlip blip =
        new J2clReadBlip(
            "b+6",
            "x",
            Collections.emptyList(),
            "a@x",
            "A",
            -100L,
            "",
            "",
            false,
            false);

    Assert.assertEquals(0L, blip.getLastModifiedTimeMillis());
  }
}

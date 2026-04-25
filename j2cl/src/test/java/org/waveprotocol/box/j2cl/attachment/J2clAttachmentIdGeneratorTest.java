package org.waveprotocol.box.j2cl.attachment;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clAttachmentIdGeneratorTest.class)
public class J2clAttachmentIdGeneratorTest {
  @Test
  public void generatesLegacyCompatibleDomainAndSeedTokens() {
    J2clAttachmentIdGenerator generator = new J2clAttachmentIdGenerator("example.com", "seed");

    Assert.assertEquals("example.com/seedA", generator.nextAttachmentId());
    Assert.assertEquals("example.com/seedB", generator.nextAttachmentId());
  }

  @Test
  public void stripsUnsafeSeedCharacters() {
    J2clAttachmentIdGenerator generator =
        new J2clAttachmentIdGenerator("example.com", " se/ed:+ok_- ");

    Assert.assertEquals("example.com/seedok_-A", generator.nextAttachmentId());
  }

  @Test
  public void defaultsEmptySeedToJ2cl() {
    J2clAttachmentIdGenerator generator = new J2clAttachmentIdGenerator("example.com", " /:+ ");

    Assert.assertEquals("example.com/j2clA", generator.nextAttachmentId());
  }

  @Test
  public void defaultsNullSeedToJ2cl() {
    J2clAttachmentIdGenerator generator = new J2clAttachmentIdGenerator("example.com", null);

    Assert.assertEquals("example.com/j2clA", generator.nextAttachmentId());
  }

  @Test
  public void rejectsDomainContainingAttachmentSeparator() {
    try {
      new J2clAttachmentIdGenerator("example.com/bad", "seed");
      Assert.fail("Expected invalid domain to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("domain"));
    }
  }

  @Test
  public void rejectsNullDomain() {
    try {
      new J2clAttachmentIdGenerator(null, "seed");
      Assert.fail("Expected null domain to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("domain"));
    }
  }

  @Test
  public void encodesBase64Boundaries() {
    assertIdAtCounter(0, "example.com/seedA");
    assertIdAtCounter(62, "example.com/seed-");
    assertIdAtCounter(63, "example.com/seed_");
    assertIdAtCounter(64, "example.com/seedBA");
    assertIdAtCounter(4095, "example.com/seed__");
  }

  @Test
  public void throwsOnCounterOverflow() {
    J2clAttachmentIdGenerator generator =
        new J2clAttachmentIdGenerator("example.com", "seed", Integer.MAX_VALUE);

    Assert.assertEquals("example.com/seedB_____", generator.nextAttachmentId());

    try {
      generator.nextAttachmentId();
      Assert.fail("Expected counter overflow to fail.");
    } catch (IllegalStateException expected) {
      Assert.assertTrue(expected.getMessage().contains("overflow"));
    }
  }

  private static void assertIdAtCounter(int targetIndex, String expectedId) {
    J2clAttachmentIdGenerator generator = new J2clAttachmentIdGenerator("example.com", "seed");
    for (int i = 0; i < targetIndex; i++) {
      generator.nextAttachmentId();
    }
    Assert.assertEquals(expectedId, generator.nextAttachmentId());
  }
}

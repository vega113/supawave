package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSidecarWriteSessionTest.class)
public class J2clSidecarWriteSessionTest {
  @Test
  public void participantIdsAreDefensivelyCopied() {
    ArrayList<String> participantIds =
        new ArrayList<String>(Arrays.asList("alice@example.com"));

    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+1", "chan-1", 44L, "ABCD", "b+root", participantIds);
    participantIds.add("bob@example.com");

    Assert.assertEquals(Arrays.asList("alice@example.com"), session.getParticipantIds());
  }
}

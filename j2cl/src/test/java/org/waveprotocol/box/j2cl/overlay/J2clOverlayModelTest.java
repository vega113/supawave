package org.waveprotocol.box.j2cl.overlay;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;

@J2clTestInput(J2clOverlayModelTest.class)
public class J2clOverlayModelTest {
  @Test
  public void mentionCandidateUsesAddressFallbacks() {
    J2clMentionCandidate candidate =
        new J2clMentionCandidate("alice@example.com", "", null, "", true);

    Assert.assertEquals("alice@example.com", candidate.getAddress());
    Assert.assertEquals("alice@example.com", candidate.getDisplayName());
    Assert.assertEquals("", candidate.getAvatarToken());
    Assert.assertEquals("alice@example.com", candidate.getSortKey());
    Assert.assertTrue(candidate.isCurrentUser());
  }

  @Test
  public void mentionCandidateNormalizesNullValues() {
    J2clMentionCandidate candidate =
        new J2clMentionCandidate(null, null, null, null, false);

    Assert.assertEquals("", candidate.getAddress());
    Assert.assertEquals("", candidate.getDisplayName());
    Assert.assertEquals("", candidate.getAvatarToken());
    Assert.assertEquals("", candidate.getSortKey());
    Assert.assertFalse(candidate.isCurrentUser());
  }

  @Test
  public void reactionSummaryDefensivelyCopiesParticipantAddresses() {
    ArrayList<String> addresses =
        new ArrayList<String>(Arrays.asList("alice@example.com", "bob@example.com"));

    J2clReactionSummary summary = new J2clReactionSummary("tada", addresses, true, "");
    addresses.add("carol@example.com");

    Assert.assertEquals("tada", summary.getEmoji());
    Assert.assertEquals(2, summary.getCount());
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        summary.getParticipantAddresses());
    Assert.assertTrue(summary.isActiveForCurrentUser());
    Assert.assertEquals("2 reactions for tada.", summary.getInspectLabel());
    try {
      summary.getParticipantAddresses().add("mallory@example.com");
      Assert.fail("reaction participant addresses should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
  }

  @Test
  public void taskItemAndFocusTargetNormalizeNullValues() {
    J2clTaskItemModel task =
        new J2clTaskItemModel(null, -4, null, null, 123L, true, false);
    J2clOverlayFocusTarget target = new J2clOverlayFocusTarget(null);

    Assert.assertEquals("", task.getTaskId());
    Assert.assertEquals(0, task.getTextOffset());
    Assert.assertEquals("", task.getElementAnchorId());
    Assert.assertEquals("", task.getAssigneeAddress());
    Assert.assertEquals(123L, task.getDueTimestamp());
    Assert.assertTrue(task.isChecked());
    Assert.assertFalse(task.isEditable());
    Assert.assertEquals("", target.getTargetId());
  }

  @Test
  public void mentionRangeNormalizesOffsetsAndNullText() {
    J2clMentionRange mention = new J2clMentionRange(-1, -2, null, null);

    Assert.assertEquals(0, mention.getStartOffset());
    Assert.assertEquals(0, mention.getEndOffset());
    Assert.assertEquals("", mention.getUserAddress());
    Assert.assertEquals("", mention.getDisplayText());
  }

  @Test
  public void mentionRangeClampsEndOffsetToStartOffset() {
    J2clMentionRange mention = new J2clMentionRange(7, 2, "alice@example.com", "@Alice");

    Assert.assertEquals(7, mention.getStartOffset());
    Assert.assertEquals(7, mention.getEndOffset());
  }

  @Test
  public void reactionSummaryHandlesEmptyParticipants() {
    J2clReactionSummary summary =
        new J2clReactionSummary("thumbs_up", Collections.<String>emptyList(), false, null);

    Assert.assertEquals(0, summary.getCount());
    Assert.assertEquals("0 reactions for thumbs_up.", summary.getInspectLabel());
  }

  @Test
  public void interactionBlipClampsMentionTextAndSkipsEmptyReactionEmoji() {
    J2clInteractionBlipModel blip =
        new J2clInteractionBlipModel(
            "b+root",
            "b+root",
            "author@example.com",
            "Hi @Al",
            Arrays.asList("author@example.com"),
            true,
            Arrays.asList(
                new SidecarAnnotationRange("mention/user", "alice@example.com", 3, 99),
                new SidecarAnnotationRange("task/id", "task-123", 0, 2),
                null),
            Arrays.asList(
                new SidecarReactionEntry("tada", Arrays.asList("alice@example.com")),
                new SidecarReactionEntry("", Arrays.asList("bob@example.com")),
                new SidecarReactionEntry(null, Arrays.asList("carol@example.com"))));

    Assert.assertEquals("b+root", blip.getDocumentId());
    Assert.assertEquals(1, blip.getMentionRanges().size());
    Assert.assertEquals("@Al", blip.getMentionRanges().get(0).getDisplayText());
    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertEquals("", blip.getTaskItems().get(0).getAssigneeAddress());
    Assert.assertEquals(
        J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        blip.getTaskItems().get(0).getDueTimestamp());
    Assert.assertEquals(1, blip.getReactionSummaries().size());
    Assert.assertEquals("tada", blip.getReactionSummaries().get(0).getEmoji());
  }

  @Test
  public void interactionBlipTaskItemsFollowBlipEditability() {
    J2clInteractionBlipModel blip =
        new J2clInteractionBlipModel(
            "b+root",
            "b+root",
            "author@example.com",
            "Review spec",
            Arrays.asList("author@example.com"),
            false,
            Arrays.asList(new SidecarAnnotationRange("task/id", "task-123", 0, 11)),
            Collections.<SidecarReactionEntry>emptyList());

    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertFalse(blip.getTaskItems().get(0).isEditable());
  }

  @Test
  public void interactionBlipRefinedListsAreImmutable() {
    ArrayList<String> participantContext =
        new ArrayList<String>(Arrays.asList("alice@example.com"));
    J2clInteractionBlipModel blip =
        new J2clInteractionBlipModel(
            "b+root",
            "b+root",
            "author@example.com",
            "Root",
            participantContext,
            true,
            Arrays.asList(new SidecarAnnotationRange("mention/user", "alice@example.com", 0, 4)),
            Arrays.asList(new SidecarReactionEntry("tada", Arrays.asList("alice@example.com"))));
    participantContext.add("mallory@example.com");

    Assert.assertEquals(Arrays.asList("alice@example.com"), blip.getParticipantContext());
    try {
      blip.getParticipantContext().add("mallory@example.com");
      Assert.fail("participant context should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
    try {
      blip.getAnnotationRanges().add(
          new SidecarAnnotationRange("mention/user", "mallory@example.com", 0, 1));
      Assert.fail("raw annotation ranges should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
    try {
      blip.getReactionEntries().add(
          new SidecarReactionEntry("heart", Arrays.asList("mallory@example.com")));
      Assert.fail("raw reaction entries should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
    try {
      blip.getMentionRanges().add(new J2clMentionRange(0, 1, "x", "x"));
      Assert.fail("mention ranges should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
    try {
      blip.getTaskItems().add(
          new J2clTaskItemModel("task-1", 0, "task-1", "", -1L, false, false));
      Assert.fail("task items should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
    try {
      blip.getReactionSummaries().add(
          new J2clReactionSummary("heart", Arrays.asList("alice@example.com"), false, ""));
      Assert.fail("reaction summaries should be immutable");
    } catch (UnsupportedOperationException expected) {
      // Expected immutable-list contract.
    }
  }
}

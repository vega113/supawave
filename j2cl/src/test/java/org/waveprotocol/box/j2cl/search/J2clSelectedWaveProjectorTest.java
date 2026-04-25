package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.overlay.J2clMentionRange;
import org.waveprotocol.box.j2cl.overlay.J2clReactionSummary;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

@J2clTestInput(J2clSelectedWaveProjectorTest.class)
public class J2clSelectedWaveProjectorTest {
  private static final String WAVE_ID = "example.com/w+1";
  private static final String WAVELET_NAME = "example.com!w+1/example.com!conv+root";
  private static final String WAVELET_NAME_2 = "example.com!w+2/example.com!conv+root";
  private static final String CHANNEL_ID = "chan-1";
  private static final String INDEX_SEGMENT = "index";
  private static final String MANIFEST_SEGMENT = "manifest";

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
  public void projectExtractsAttachmentModelsFromImageElementsInFragments() {
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
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+root",
                            "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                                + "<caption>Hero diagram</caption></image> outro",
                            0,
                            0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("Intro  outro", blip.getText());
    Assert.assertEquals(1, blip.getAttachments().size());
    J2clAttachmentRenderModel attachment = blip.getAttachments().get(0);
    Assert.assertEquals("example.com/att+hero", attachment.getAttachmentId());
    Assert.assertEquals("medium", attachment.getDisplaySize());
    Assert.assertEquals("Hero diagram", attachment.getCaption());
    Assert.assertTrue(attachment.isMetadataPending());
    Assert.assertEquals(
        1, projected.getViewportState().getReadWindowEntries().get(0).getAttachments().size());
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
  public void projectDocumentFallbackPreservesLiteralMarkupAndComparisons() {
    String literalText =
        "Literal 2 < 3 and <image attachment=\"example.com/att+literal\"> stays text";
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
                        "b+root", "user@example.com", 7L, 8L, literalText)),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals(literalText, blip.getText());
    Assert.assertTrue(blip.getAttachments().isEmpty());
    Assert.assertEquals(
        literalText, projected.getViewportState().getReadWindowEntries().get(0).getText());
    Assert.assertTrue(
        projected.getViewportState().getReadWindowEntries().get(0).getAttachments().isEmpty());
  }

  @Test
  public void projectBuildsInteractionBlipMetadataFromDocumentAnnotationsAndReactionDocs() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "@Teammate review",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 9),
                            new SidecarAnnotationRange("task/id", "task-123", 10, 16)),
                        Collections.<SidecarReactionEntry>emptyList()),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "thumbs_up", Arrays.asList("alice@example.com"))))),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getInteractionBlips().size());
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("@Teammate review", blip.getText());
    Assert.assertEquals(2, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals("task/id", blip.getAnnotationRanges().get(1).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("thumbs_up", blip.getReactionEntries().get(0).getEmoji());
  }

  @Test
  public void projectRefinesMentionRangesFromMentionAnnotations() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Hi @Teammate",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 3, 12)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getMentionRanges().size());
    J2clMentionRange mention = blip.getMentionRanges().get(0);
    Assert.assertEquals(3, mention.getStartOffset());
    Assert.assertEquals(12, mention.getEndOffset());
    Assert.assertEquals("teammate@example.com", mention.getUserAddress());
    Assert.assertEquals("@Teammate", mention.getDisplayText());
  }

  @Test
  public void projectRefinesTaskItemsFromTaskAnnotationsWithSharedRange() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 11),
                            new SidecarAnnotationRange("task/dueTs", "1714000000000", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getTaskItems().size());
    J2clTaskItemModel task = blip.getTaskItems().get(0);
    Assert.assertEquals("task-123", task.getTaskId());
    Assert.assertEquals(0, task.getTextOffset());
    Assert.assertEquals("task-b+root-task-123", task.getElementAnchorId());
    Assert.assertEquals("alice@example.com", task.getAssigneeAddress());
    Assert.assertEquals(1714000000000L, task.getDueTimestamp());
    Assert.assertFalse(task.isChecked());
    Assert.assertTrue(task.isEditable());
  }

  @Test
  public void projectMarksInteractionBlipsReadOnlyWithoutWriteSession() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                "",
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    Assert.assertNull(projected.getWriteSession());
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertFalse(blip.isEditable());
    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertFalse(blip.getTaskItems().get(0).isEditable());
  }

  @Test
  public void projectSkipsTaskItemsWithoutTaskId() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    Assert.assertTrue(projected.getInteractionBlips().get(0).getTaskItems().isEmpty());
  }

  @Test
  public void projectRequiresTaskMetadataAnnotationsToShareTaskIdRange() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 6)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clTaskItemModel task = projected.getInteractionBlips().get(0).getTaskItems().get(0);
    Assert.assertEquals("", task.getAssigneeAddress());
  }

  @Test
  public void projectUsesUnknownDueTimestampForInvalidTaskDueAnnotation() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange("task/dueTs", "tomorrow", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clTaskItemModel task = projected.getInteractionBlips().get(0).getTaskItems().get(0);
    Assert.assertEquals(J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP, task.getDueTimestamp());
  }

  @Test
  public void projectRefinesReactionSummariesFromReactionDataDocuments() {
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
                        "b+root", "user@example.com", 7L, 8L, "Root text"),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada",
                                Arrays.asList("alice@example.com", "bob@example.com"))))),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getReactionSummaries().size());
    J2clReactionSummary reaction = blip.getReactionSummaries().get(0);
    Assert.assertEquals("tada", reaction.getEmoji());
    Assert.assertEquals(2, reaction.getCount());
    Assert.assertEquals("alice@example.com", reaction.getParticipantAddresses().get(0));
    Assert.assertEquals("bob@example.com", reaction.getParticipantAddresses().get(1));
    Assert.assertFalse(reaction.isActiveForCurrentUser());
    Assert.assertEquals("2 reactions for tada.", reaction.getInspectLabel());
  }

  @Test
  public void projectKeepsEmptyOverlayListsAndContentEntriesForPlainBlips() {
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
                        "b+root", "user@example.com", 7L, 8L, "Plain text")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getContentEntries().size());
    Assert.assertEquals("Plain text", projected.getContentEntries().get(0));
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertTrue(blip.getMentionRanges().isEmpty());
    Assert.assertTrue(blip.getTaskItems().isEmpty());
    Assert.assertTrue(blip.getReactionSummaries().isEmpty());
  }

  @Test
  public void projectMergesReactionOnlyDocumentUpdateIntoPreviousInteractionBlip() {
    J2clSelectedWaveModel first =
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clSelectedWaveModel reactionOnly =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    Assert.assertEquals(1, reactionOnly.getInteractionBlips().size());
    J2clInteractionBlipModel blip = reactionOnly.getInteractionBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("tada", blip.getReactionEntries().get(0).getEmoji());
  }

  @Test
  public void projectTreatsEmptyReactionDocumentAsExplicitReactionClear() {
    J2clSelectedWaveModel first =
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList()),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        8L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            null,
            0);

    J2clSelectedWaveModel cleared =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            first,
            0);

    J2clInteractionBlipModel blip = cleared.getInteractionBlips().get(0);
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals(0, blip.getReactionEntries().size());
  }

  @Test
  public void projectPreservesInteractionBlipTextAcrossConsecutiveReactionOnlyUpdates() {
    J2clSelectedWaveModel first =
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
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);
    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    J2clSelectedWaveModel third =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                3,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                11L,
                "HASH3",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        11L,
                        11L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "thumbs_up", Arrays.asList("bob@example.com"))))),
                null),
            second,
            0);

    J2clInteractionBlipModel blip = third.getInteractionBlips().get(0);
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("thumbs_up", blip.getReactionEntries().get(0).getEmoji());
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

  @Test
  public void projectPreservesFragmentWindowRangesAndPlaceholders() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 36L),
                        new SidecarSelectedWaveFragmentRange("blip:b+missing", 36L, 38L),
                        new SidecarSelectedWaveFragmentRange("blip:b+empty", 38L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 2, 3),
                        new SidecarSelectedWaveFragment("blip:b+empty", null, 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState viewport = projected.getViewportState();

    Assert.assertEquals(40L, viewport.getSnapshotVersion());
    Assert.assertEquals(30L, viewport.getStartVersion());
    Assert.assertEquals(40L, viewport.getEndVersion());
    Assert.assertEquals(4, viewport.getEntries().size());

    J2clSelectedWaveViewportState.Entry manifest = viewport.getEntries().get(0);
    Assert.assertEquals(MANIFEST_SEGMENT, manifest.getSegment());
    Assert.assertEquals(30L, manifest.getFromVersion());
    Assert.assertEquals(40L, manifest.getToVersion());
    Assert.assertFalse(manifest.isBlip());
    Assert.assertTrue(manifest.isLoaded());
    Assert.assertEquals("metadata", manifest.getRawSnapshot());

    J2clSelectedWaveViewportState.Entry root = viewport.getEntries().get(1);
    Assert.assertEquals("blip:b+root", root.getSegment());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(36L, root.getToVersion());
    Assert.assertTrue(root.isBlip());
    Assert.assertEquals("b+root", root.getBlipId());
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text", root.getRawSnapshot());
    Assert.assertEquals(2, root.getAdjustOperationCount());
    Assert.assertEquals(3, root.getDiffOperationCount());

    J2clSelectedWaveViewportState.Entry placeholder = viewport.getEntries().get(2);
    Assert.assertEquals("blip:b+missing", placeholder.getSegment());
    Assert.assertEquals(36L, placeholder.getFromVersion());
    Assert.assertEquals(38L, placeholder.getToVersion());
    Assert.assertTrue(placeholder.isBlip());
    Assert.assertEquals("b+missing", placeholder.getBlipId());
    Assert.assertFalse(placeholder.isLoaded());

    J2clSelectedWaveViewportState.Entry empty = viewport.getEntries().get(3);
    Assert.assertEquals("blip:b+empty", empty.getSegment());
    Assert.assertEquals(38L, empty.getFromVersion());
    Assert.assertEquals(40L, empty.getToVersion());
    Assert.assertEquals("b+empty", empty.getBlipId());
    Assert.assertFalse(empty.isLoaded());
  }

  @Test
  public void projectCarriesPreviousViewportWindowWhenUpdateOmitsFragments() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            first,
            0);

    Assert.assertEquals(40L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", second.getViewportState().getEntries().get(0).getBlipId());
  }

  @Test
  public void projectCarriesPreviousViewportWindowWhenFragmentsOnlyContainMetadata() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(44L, 40L, 44L)),
            first,
            0);

    Assert.assertEquals(40L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", second.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("Root text", second.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectFallsThroughToDocumentsWhenMetadataOnlyFragmentsHaveNoPreviousViewport() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                metadataOnlyFragments(44L, 40L, 44L)),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", projected.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("Document text", projected.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectDoesNotCarryMetadataOnlyFragmentsAcrossWaveSwitch() {
    J2clSelectedWaveModel previous =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    J2clSelectedWaveModel switched =
        J2clSelectedWaveProjector.project(
            "example.com/w+2",
            null,
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME_2,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(44L, 40L, 44L)),
            previous,
            0);

    Assert.assertTrue(switched.getViewportState().isEmpty());
    Assert.assertTrue(switched.getReadBlips().isEmpty());
  }

  @Test
  public void projectMixedMetadataAndBlipFragmentsReplacePreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Old root text"),
            null,
            0);

    J2clSelectedWaveModel mixedFragments =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    50L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 45L, 50L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+root", "New root text", 0, 0)))),
            first,
            0);

    Assert.assertEquals(50L, mixedFragments.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, mixedFragments.getViewportState().getEntries().size());
    Assert.assertEquals(
        "metadata",
        entryBySegment(mixedFragments.getViewportState(), MANIFEST_SEGMENT).getRawSnapshot());
    Assert.assertEquals(
        "New root text",
        entryBySegment(mixedFragments.getViewportState(), "blip:b+root").getRawSnapshot());
  }

  @Test
  public void projectPreservesDocumentMergedViewportAcrossMetadataOnlyFragments() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    J2clSelectedWaveModel documentMerged =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                null),
            first,
            0);

    J2clSelectedWaveModel metadataOnly =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                3,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                45L,
                "HASH3",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(45L, 44L, 45L)),
            documentMerged,
            0);

    Assert.assertEquals(44L, metadataOnly.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, metadataOnly.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", metadataOnly.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals(
        "Document text", metadataOnly.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectMergesDocumentOnlyUpdateIntoPreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 36L),
                        new SidecarSelectedWaveFragmentRange("blip:b+missing", 36L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Root text updated")),
                null),
            first,
            0);

    Assert.assertEquals(2, second.getViewportState().getEntries().size());
    J2clSelectedWaveViewportState.Entry root = second.getViewportState().getEntries().get(0);
    Assert.assertEquals("blip:b+root", root.getSegment());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(44L, root.getToVersion());
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text updated", root.getRawSnapshot());
    J2clSelectedWaveViewportState.Entry missing = second.getViewportState().getEntries().get(1);
    Assert.assertEquals("blip:b+missing", missing.getSegment());
    Assert.assertEquals(36L, missing.getFromVersion());
    Assert.assertEquals(40L, missing.getToVersion());
    Assert.assertFalse(missing.isLoaded());
  }

  @Test
  public void projectMergesDocumentOnlyUpdateWithoutDowngradingViewportVersion() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    50L,
                    30L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+reply", "user@example.com", 44L, 45L, "Reply text")),
                null),
            first,
            0);

    Assert.assertEquals(50L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(50L, second.getViewportState().getEndVersion());
    Assert.assertEquals(2, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+reply", second.getViewportState().getEntries().get(1).getBlipId());
    Assert.assertEquals(44L, second.getViewportState().getEntries().get(1).getToVersion());
  }

  @Test
  public void projectMergesDocumentOnlyUpdateWithoutWideningKnownFragmentStart() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    50L,
                    30L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                20L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 20L, 21L, "Older document text")),
                null),
            first,
            0);

    J2clSelectedWaveViewportState.Entry root = second.getViewportState().getEntries().get(0);
    Assert.assertEquals(30L, second.getViewportState().getStartVersion());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(50L, root.getToVersion());
    Assert.assertEquals("Older document text", root.getRawSnapshot());
  }

  @Test
  public void projectMergesDocumentsIntoFragmentUpdate() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+reply", "user@example.com", 42L, 43L, "Reply text")),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    Assert.assertEquals(2, projected.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", projected.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("b+reply", projected.getViewportState().getEntries().get(1).getBlipId());
    Assert.assertEquals(
        "Reply text", projected.getViewportState().getEntries().get(1).getRawSnapshot());
  }

  @Test
  public void projectUpgradesFragmentPlaceholderFromDocumentInSameUpdate() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Root text from document")),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getEntries().size());
    J2clSelectedWaveViewportState.Entry root = projected.getViewportState().getEntries().get(0);
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text from document", root.getRawSnapshot());
    Assert.assertEquals(44L, root.getToVersion());
  }

  @Test
  public void projectResolvesUnknownFragmentStartFromDocumentMerge() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Root text from document")),
                new SidecarSelectedWaveFragments(
                    44L,
                    -1L,
                    44L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", -1L, -1L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState viewport = projected.getViewportState();
    J2clSelectedWaveViewportState.Entry root = viewport.getEntries().get(0);
    Assert.assertEquals(44L, viewport.getStartVersion());
    Assert.assertEquals(44L, root.getFromVersion());
    Assert.assertEquals(44L, root.getToVersion());
  }

  @Test
  public void projectKeepsLoadedFragmentWhenSameUpdateDocumentAlsoExists() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+root", "Fragment text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState.Entry root =
        projected.getViewportState().getEntries().get(0);
    Assert.assertEquals("Fragment text", root.getRawSnapshot());
    Assert.assertEquals(40L, root.getToVersion());
  }

  @Test
  public void projectBuildsViewportWindowFromDocumentOnlyUpdate() {
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
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+root", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals(
        "Document text",
        projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void projectKeepsEmptyTextDocumentAsLoadedViewportAnchor() {
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
                        "b+empty", "user@example.com", 7L, 8L, "")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+empty", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals("", projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void projectKeepsNonBlipDocumentsAsNonReadViewportEntries() {
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
                        "conversation", "user@example.com", 7L, 8L, "metadata")),
                null),
            null,
            0);

    J2clSelectedWaveViewportState.Entry entry = projected.getViewportState().getEntries().get(0);
    Assert.assertEquals("conversation", entry.getSegment());
    Assert.assertFalse(entry.isBlip());
    Assert.assertTrue(entry.isLoaded());
    Assert.assertTrue(projected.getViewportState().getReadWindowEntries().isEmpty());
  }

  @Test
  public void mergeFragmentsDoesNotDowngradeLoadedEntryWithPlaceholderOverlap() {
    J2clSelectedWaveViewportState initial =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                44L,
                40L,
                44L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 40L, 44L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0))));

    J2clSelectedWaveViewportState merged =
        initial.mergeFragments(
            new SidecarSelectedWaveFragments(
                48L,
                44L,
                48L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 44L, 48L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0))),
            J2clViewportGrowthDirection.FORWARD);

    J2clSelectedWaveViewportState.Entry root = merged.getEntries().get(0);
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text", root.getRawSnapshot());
    Assert.assertEquals(40L, root.getFromVersion());
    Assert.assertEquals(48L, root.getToVersion());
  }

  @Test
  public void mergeFragmentsPrependsMissingEntriesForBackwardGrowth() {
    J2clSelectedWaveViewportState initial =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                44L,
                40L,
                44L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 40L, 44L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0))));

    J2clSelectedWaveViewportState merged =
        initial.mergeFragments(
            new SidecarSelectedWaveFragments(
                48L,
                36L,
                40L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+before", 36L, 40L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+before", "Before text", 0, 0))),
            J2clViewportGrowthDirection.BACKWARD);

    Assert.assertEquals(2, merged.getEntries().size());
    Assert.assertEquals("b+before", merged.getEntries().get(0).getBlipId());
    Assert.assertEquals("b+root", merged.getEntries().get(1).getBlipId());
  }

  @Test
  public void projectBuildsDocumentOnlyViewportAtVersionZero() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 0L, 8L, "Bootstrap text")),
                null),
            null,
            0);

    Assert.assertEquals(0L, projected.getViewportState().getSnapshotVersion());
    Assert.assertEquals(0L, projected.getViewportState().getStartVersion());
    Assert.assertEquals(0L, projected.getViewportState().getEndVersion());
  }

  @Test
  public void projectSkipsDocumentViewportEntriesWithoutDocumentIds() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        null, "user@example.com", 0L, 8L, "No id"),
                    new SidecarSelectedWaveDocument(
                        "", "user@example.com", 1L, 9L, "Empty id")),
                null),
            null,
            0);

    Assert.assertTrue(projected.getViewportState().isEmpty());
  }

  @Test
  public void projectNormalizesNullDocumentTextToLoadedEmptyViewportAnchor() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+null", "user@example.com", 1L, 9L, null)),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+null", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals("", projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void reprojectReadStatePreservesViewportWindow() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel reprojected =
        J2clSelectedWaveProjector.reprojectReadState(
            projected,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveReadState(WAVE_ID, 0, true),
            false);

    Assert.assertEquals(40L, reprojected.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, reprojected.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text", reprojected.getViewportState().getEntries().get(0).getRawSnapshot());
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
  public void projectRefreshesParticipantContextWhenCarryingForwardPreviousInteractionBlips() {
    J2clSelectedWaveModel first =
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
                Arrays.asList("alice@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "author@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH-2",
                Arrays.asList("alice@example.com", "bob@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "author@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    Assert.assertEquals(1, second.getInteractionBlips().size());
    J2clInteractionBlipModel blip = second.getInteractionBlips().get(0);
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        blip.getParticipantContext());
    Assert.assertTrue(blip.isEditable());
    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertTrue(blip.getTaskItems().get(0).isEditable());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("tada", blip.getReactionEntries().get(0).getEmoji());
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

  private static SidecarSelectedWaveUpdate rootFragmentUpdate(
      int sequence, long version, String historyHash, String rawSnapshot) {
    // Keep the helper window non-zero so tests catch accidental range downgrades.
    long fromVersion = Math.max(0L, version - 10L);
    return new SidecarSelectedWaveUpdate(
        sequence,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        version,
        historyHash,
        Arrays.asList("user@example.com"),
        Collections.<SidecarSelectedWaveDocument>emptyList(),
        new SidecarSelectedWaveFragments(
            version,
            fromVersion,
            version,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("blip:b+root", fromVersion, version)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("blip:b+root", rawSnapshot, 0, 0))));
  }

  private static SidecarSelectedWaveFragments metadataOnlyFragments(
      long snapshotVersion, long fromVersion, long toVersion) {
    return new SidecarSelectedWaveFragments(
        snapshotVersion,
        fromVersion,
        toVersion,
        Arrays.asList(
            new SidecarSelectedWaveFragmentRange(INDEX_SEGMENT, fromVersion, toVersion),
            new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, fromVersion, toVersion)),
        Arrays.asList(
            new SidecarSelectedWaveFragment(INDEX_SEGMENT, "index", 0, 0),
            new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0)));
  }

  private static J2clSelectedWaveViewportState.Entry entryBySegment(
      J2clSelectedWaveViewportState viewport, String segment) {
    for (J2clSelectedWaveViewportState.Entry entry : viewport.getEntries()) {
      if (segment.equals(entry.getSegment())) {
        return entry;
      }
    }
    throw new AssertionError("Missing segment: " + segment);
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

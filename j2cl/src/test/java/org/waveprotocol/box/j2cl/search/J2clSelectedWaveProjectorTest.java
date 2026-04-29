package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.overlay.J2clMentionRange;
import org.waveprotocol.box.j2cl.overlay.J2clReactionSummary;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadBlipContent;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
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
  private static final String ATTACHMENT_RAW_SNAPSHOT =
      "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
          + "<caption>Hero diagram</caption></image> outro";

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
  public void viewportHydratesPendingFragmentAttachmentMetadata() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    J2clAttachmentRenderModel attachment =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.canOpen());
    Assert.assertTrue(attachment.canDownload());
    Assert.assertEquals("/attachments/hero.png", attachment.getOpenUrl());
    Assert.assertEquals("/attachments/hero.png", attachment.getSourceUrl());
    Assert.assertEquals(
        attachment, state.getReadWindowEntries().get(0).getAttachments().get(0));
  }

  @Test
  public void viewportMarksMissingAttachmentMetadataAsFailure() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();

    state =
        state.withAttachmentMetadata(
            Collections.<J2clAttachmentMetadata>emptyList(),
            Arrays.asList("example.com/att+hero"));

    J2clAttachmentRenderModel attachment =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.isMetadataFailure());
    Assert.assertFalse(attachment.canOpen());
    Assert.assertEquals("Hero diagram", attachment.getCaption());
    Assert.assertEquals(
        attachment, state.getReadWindowEntries().get(0).getAttachments().get(0));
  }

  @Test
  public void viewportPreservesResolvedAttachmentWhenResolvingAnotherBatch() {
    J2clSelectedWaveViewportState state = viewportWithTwoAttachments();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolvedHero =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.withAttachmentMetadata(
            Collections.<J2clAttachmentMetadata>emptyList(),
            Arrays.asList("example.com/att+diagram"));

    J2clAttachmentRenderModel hero =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    J2clAttachmentRenderModel diagram =
        state.getLoadedReadBlips().get(0).getAttachments().get(1);
    Assert.assertEquals(resolvedHero, hero);
    Assert.assertFalse(hero.isMetadataPending());
    Assert.assertTrue(hero.canOpen());
    Assert.assertTrue(diagram.isMetadataFailure());
    Assert.assertFalse(diagram.isMetadataPending());
  }

  @Test
  public void viewportReusesParsedContentAcrossAttachmentResolution() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
  }

  @Test
  public void viewportPreservesResolvedAttachmentAcrossSameRawFragmentMerge() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolved =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.mergeFragments(
            attachmentFragments(10L, 0L, 10L),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertEquals(resolved, state.getLoadedReadBlips().get(0).getAttachments().get(0));
    Assert.assertFalse(state.getPendingAttachmentIds().contains("example.com/att+hero"));
  }

  @Test
  public void viewportPreservesResolvedAttachmentAcrossPlaceholderFragmentMerge() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolved =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Collections.<SidecarSelectedWaveFragment>emptyList()),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertEquals(resolved, state.getLoadedReadBlips().get(0).getAttachments().get(0));
    Assert.assertFalse(state.getPendingAttachmentIds().contains("example.com/att+hero"));
  }

  @Test
  public void viewportDropsParsedCacheAndOverridesWhenRawFragmentChanges() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "Changed <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                            + "<caption>Changed hero</caption></image>",
                        0,
                        0))),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertNotSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertTrue(state.getLoadedReadBlips().get(0).getAttachments().get(0).isMetadataPending());
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
  public void viewportDocumentMergeOverFragmentSwitchesBackToLiteralText() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                9L,
                0L,
                9L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "<image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image>",
                        0,
                        0))));
    String literalText = "Literal 2 < 3 and <image attachment=\"example.com/att+literal\">";

    state =
        state.mergeDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 7L, 10L, literalText)));

    Assert.assertEquals(literalText, state.getLoadedReadBlips().get(0).getText());
    Assert.assertTrue(state.getLoadedReadBlips().get(0).getAttachments().isEmpty());
    Assert.assertEquals(literalText, state.getReadWindowEntries().get(0).getText());
    Assert.assertTrue(state.getReadWindowEntries().get(0).getAttachments().isEmpty());
  }

  @Test
  public void viewportFragmentMergeOverDocumentRestoresAttachmentParsing() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 7L, 8L, "Literal <image> text")));

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "Intro <image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image> outro",
                        0,
                        0))),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertEquals("Intro  outro", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getAttachments().size());
    Assert.assertEquals(1, state.getReadWindowEntries().get(0).getAttachments().size());
  }

  @Test
  public void viewportPlaceholderMergePreservesFragmentAttachmentParsing() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                9L,
                0L,
                9L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "Intro <image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image> outro",
                        0,
                        0))));

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Collections.<SidecarSelectedWaveFragment>emptyList()),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertEquals("Intro  outro", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getAttachments().size());
    Assert.assertEquals(1, state.getReadWindowEntries().get(0).getAttachments().size());
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
  public void projectUsesRawViewportManifestWhenDocumentManifestIsAbsent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                71L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    71L,
                    30L,
                    71L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 30L, 71L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+second", 40L, 50L),
                        new SidecarSelectedWaveFragmentRange("blip:b+third", 50L, 60L),
                        new SidecarSelectedWaveFragmentRange("blip:b+nested", 60L, 71L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            MANIFEST_SEGMENT,
                            "<conversation><blip id=\"b+root\">"
                                + "<thread id=\"t+first\"><blip id=\"b+second\">"
                                + "<thread id=\"t+nested\"><blip id=\"b+nested\"/>"
                                + "</thread></blip></thread><thread id=\"t+third\">"
                                + "<blip id=\"b+third\"/></thread></blip></conversation>",
                            0,
                            0),
                        new SidecarSelectedWaveFragment("blip:b+root", "Root", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+second", "Second", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+third", "Third", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+nested", "Nested", 0, 0)))),
            null,
            0);

    SidecarConversationManifest manifest = projected.getConversationManifest();
    Assert.assertFalse(manifest.isEmpty());
    Assert.assertEquals("b+root", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals("b+second", manifest.getOrderedEntries().get(1).getBlipId());
    Assert.assertEquals("b+nested", manifest.getOrderedEntries().get(2).getBlipId());
    Assert.assertEquals("b+third", manifest.getOrderedEntries().get(3).getBlipId());
    Assert.assertEquals("b+second", manifest.findByBlipId("b+nested").getParentBlipId());
    Assert.assertEquals("b+root", manifest.findByBlipId("b+third").getParentBlipId());
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
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+stale", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Old root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+stale", "Stale text", 0, 0)))),
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
    assertNoEntryBySegment(mixedFragments.getViewportState(), "blip:b+stale");
    Assert.assertEquals(
        "metadata",
        entryBySegment(mixedFragments.getViewportState(), MANIFEST_SEGMENT).getRawSnapshot());
    Assert.assertEquals(
        "New root text",
        entryBySegment(mixedFragments.getViewportState(), "blip:b+root").getRawSnapshot());
  }

  @Test
  public void projectMergesSameWaveLiveBlipFragmentsIntoPreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    // Live deltas from the server have snapshotVersion < 0 (the codec defaults to -1 when the
    // server omits the field). Using -1L here matches the wire semantics for a post-submit push.
    J2clSelectedWaveModel liveReply =
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
                    -1L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+reply", "Reply submitted from composer", 0, 0)))),
            first,
            0);

    Assert.assertEquals(2, liveReply.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text",
        entryBySegment(liveReply.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "Reply submitted from composer",
        entryBySegment(liveReply.getViewportState(), "blip:b+reply").getRawSnapshot());
    // The merged viewport retains the initial snapshot's version (max(-1, 40) = 40).
    Assert.assertEquals(40L, liveReply.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, liveReply.getReadBlips().size());
    Assert.assertEquals("b+reply", liveReply.getReadBlips().get(1).getBlipId());
    Assert.assertEquals("Reply submitted from composer", liveReply.getReadBlips().get(1).getText());
    Assert.assertEquals(50L, liveReply.getWriteSession().getBaseVersion());
  }

  @Test
  public void snapshotFragmentUpdateReplacesViewportInsteadOfMerging() {
    // First update: blip-only snapshot (snapshotVersion >= 0) establishes initial viewport.
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
                    1L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+old", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+old", "Old blip text", 0, 0)))),
            null,
            0);

    // Second update: another full-window blip snapshot (snapshotVersion >= 0, e.g. on reconnect).
    // Must REPLACE the previous viewport — stale "b+old" must not survive.
    J2clSelectedWaveModel snapshot =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                60L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    0L,
                    50L,
                    60L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 50L, 60L),
                        new SidecarSelectedWaveFragmentRange("blip:b+new", 50L, 60L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Updated root", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+new", "New blip text", 0, 0)))),
            first,
            0);

    // Viewport must contain only the new snapshot entries — stale b+old must be gone.
    Assert.assertEquals(0L, snapshot.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, snapshot.getViewportState().getEntries().size());
    assertNoEntryBySegment(snapshot.getViewportState(), "blip:b+old");
    Assert.assertEquals(
        "Updated root",
        entryBySegment(snapshot.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "New blip text",
        entryBySegment(snapshot.getViewportState(), "blip:b+new").getRawSnapshot());
  }

  @Test
  public void liveFragmentDeltaMergesWithPreviousViewport() {
    // First update: full-window snapshot (snapshotVersion >= 0) establishes viewport.
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
                    1L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    // Second update: live delta (snapshotVersion = -1, the codec default for server push).
    // Must MERGE with the previous viewport — both old and new blips must be visible.
    J2clSelectedWaveModel merged =
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
                    -1L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+reply", "Live reply", 0, 0)))),
            first,
            0);

    // Both original and new blip must be present after a live-delta merge.
    Assert.assertEquals(2, merged.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text",
        entryBySegment(merged.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "Live reply",
        entryBySegment(merged.getViewportState(), "blip:b+reply").getRawSnapshot());
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

  @Test
  public void writeSessionCarriesManifestInsertPositionAndItemCount() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 6)),
            8);
    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            44L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 33L, 44L, "content")),
            null,
            manifest);

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(6, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(8, writeSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionDoesNotReusePreviousManifestOffsetsWithFreshBasis() {
    SidecarConversationManifest previousManifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 6)),
            8);
    J2clSidecarWriteSession previousWriteSession =
        new J2clSidecarWriteSession(
            WAVE_ID,
            CHANNEL_ID,
            44L,
            "ABCD",
            "b+root",
            Arrays.asList("user@example.com"),
            6,
            8);
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
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
                Arrays.asList("user@example.com"),
                Arrays.asList("old content"),
                previousWriteSession,
                J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
                false,
                false,
                false)
            .withConversationManifest(previousManifest);
    SidecarSelectedWaveUpdate liveBlipOnlyUpdate = updateWithVersionAndHash(45L, "EFGH");

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(WAVE_ID, null, liveBlipOnlyUpdate, previous, 0);

    Assert.assertSame(previousManifest, projected.getConversationManifest());
    J2clSidecarWriteSession writeSession = projected.getWriteSession();
    Assert.assertNotNull(writeSession);
    Assert.assertEquals(45L, writeSession.getBaseVersion());
    Assert.assertEquals("EFGH", writeSession.getHistoryHash());
    Assert.assertEquals(-1, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(-1, writeSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionPreservesPreviousManifestOffsetsWhenBasisIsPrevious() {
    J2clSidecarWriteSession previousWriteSession =
        new J2clSidecarWriteSession(
            WAVE_ID,
            CHANNEL_ID,
            44L,
            "ABCD",
            "b+root",
            Arrays.asList("user@example.com"),
            6,
            8);
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
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
            Arrays.asList("user@example.com"),
            Arrays.asList("old content"),
            previousWriteSession,
            J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
            false,
            false,
            false);
    SidecarSelectedWaveUpdate noCoupledBasis =
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
                    "b+root", "user@example.com", 45L, 45L, "live content")),
            new SidecarSelectedWaveFragments(
                -1L,
                44L,
                45L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 44L, 45L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+root", "live content", 0, 0))));

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, noCoupledBasis, previous, 0)
            .getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
    Assert.assertEquals(6, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(8, writeSession.getReplyManifestItemCount());
  }

  // -- F-2 (#1037) per-blip metadata enrichment --------------------------------

  @Test
  public void documentReadBlipsCarryAuthorTimestampMention() {
    SidecarAnnotationRange mention =
        new SidecarAnnotationRange("mention/me", "alice@example.com", 0, 5);
    SidecarSelectedWaveDocument doc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714134000000L,
            "Hello @alice",
            Arrays.asList(mention),
            Collections.<SidecarReactionEntry>emptyList());

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                7L,
                "HASH",
                Arrays.asList("alice@example.com"),
                Arrays.asList(doc),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Hello @alice", blip.getText());
    Assert.assertEquals("alice@example.com", blip.getAuthorId());
    Assert.assertEquals("alice@example.com", blip.getAuthorDisplayName());
    Assert.assertEquals(1714134000000L, blip.getLastModifiedTimeMillis());
    Assert.assertTrue("annotation key 'mention/me' marks the blip as a mention", blip.hasMention());
  }

  @Test
  public void viewportReadBlipsAreEnrichedWithDocumentMetadata() {
    // Viewport-shaped fragment payload (no per-blip metadata) PLUS the same
    // wire-update carrying a document for the same blip — F-2 grafts the
    // document's author + timestamp + mention onto the viewport-derived blip.
    SidecarSelectedWaveDocument doc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "bob@example.com",
            42L,
            1714240000000L,
            "Body",
            Collections.<SidecarAnnotationRange>emptyList(),
            Collections.<SidecarReactionEntry>emptyList());

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                42L,
                "HASH",
                Arrays.asList("bob@example.com"),
                Arrays.asList(doc),
                new SidecarSelectedWaveFragments(
                    42L,
                    0L,
                    42L,
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 42L)),
                    Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Body", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Body", blip.getText());
    Assert.assertEquals("bob@example.com", blip.getAuthorId());
    Assert.assertEquals(1714240000000L, blip.getLastModifiedTimeMillis());
    Assert.assertFalse(blip.hasMention());
  }

  @Test
  public void viewportReadBlipsUseDigestMetadataFallbackWhenDocumentsLag() {
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            WAVE_ID,
            "Wave",
            "snippet",
            "author@example.com",
            0,
            1,
            1714240000000L,
            false);

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest,
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                42L,
                "HASH",
                Arrays.asList("author@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    42L,
                    0L,
                    42L,
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 42L)),
                    Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Body", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("author@example.com", blip.getAuthorId());
    Assert.assertEquals("author@example.com", blip.getAuthorDisplayName());
    Assert.assertEquals(1714240000000L, blip.getLastModifiedTimeMillis());
  }

  @Test
  public void viewportMetadataFallbacksPreservePreviousBlipsAndPatchNewFragments() {
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            WAVE_ID,
            "Wave",
            "snippet",
            "digest-author@example.com",
            0,
            2,
            1714240000000L,
            false);
    J2clReadBlip previousRoot =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "",
            "",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root after growth"),
                new J2clReadBlip("b+next", "Next after growth")),
            Arrays.asList(previousRoot),
            digest);

    Assert.assertEquals(2, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("Root after growth", root.getText());
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertFalse(root.isUnread());
    Assert.assertFalse(root.hasMention());
    Assert.assertFalse(root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());

    J2clReadBlip next = enriched.get(1);
    Assert.assertEquals("Next after growth", next.getText());
    Assert.assertEquals("digest-author@example.com", next.getAuthorId());
    Assert.assertEquals(1714240000000L, next.getLastModifiedTimeMillis());
  }

  @Test
  public void viewportMetadataFallbacksDoNotResurrectAuthoritativeFalseBooleans() {
    J2clReadBlip previous =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "",
            "",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ true,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);
    J2clReadBlip refreshed =
        new J2clReadBlip(
            "b+root",
            "Root after growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "",
            "",
            0L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(refreshed), Arrays.asList(previous), null);

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertFalse("fresh unread=false must not be OR-merged with stale true", root.isUnread());
    Assert.assertFalse("fresh hasMention=false must not be OR-merged with stale true", root.hasMention());
    Assert.assertFalse("fresh deleted=false must not be OR-merged with stale true", root.isDeleted());
    Assert.assertFalse("fresh taskDone=false must not be OR-merged with stale true", root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());
  }

  @Test
  public void viewportMetadataFallbacksCanPreservePreviousBooleansForFragmentGrowth() {
    J2clReadBlip previous =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "b+parent",
            "thread-1",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);
    J2clReadBlip refreshed =
        new J2clReadBlip(
            "b+root",
            "Root after growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "",
            "",
            0L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(refreshed),
            Arrays.asList(previous),
            null,
            /* preserveFallbackBooleans= */ true);

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("Root after growth", root.getText());
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals("Root Author", root.getAuthorDisplayName());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertEquals("b+parent", root.getParentBlipId());
    Assert.assertEquals("thread-1", root.getThreadId());
    Assert.assertTrue("fragment growth must preserve prior unread state", root.isUnread());
    Assert.assertTrue("fragment growth must preserve prior mention state", root.hasMention());
    Assert.assertFalse(root.isDeleted());
    Assert.assertTrue("fragment growth must preserve prior taskDone state", root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());
  }

  @Test
  public void enrichReadBlipMetadataReturnsInputWhenInputsAreEmpty() {
    Assert.assertSame(
        Collections.<J2clReadBlip>emptyList(),
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Collections.<J2clReadBlip>emptyList(),
            Collections.<SidecarSelectedWaveDocument>emptyList()));

    java.util.List<J2clReadBlip> blips =
        Arrays.asList(new J2clReadBlip("b+x", "y"));
    Assert.assertSame(
        blips,
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            blips, Collections.<SidecarSelectedWaveDocument>emptyList()));
  }

  @Test
  public void enrichReadBlipMetadataPreservesParentAndThreadLinkage() {
    // F-2 (#1037, R-3.7) — the helper is meant to *enrich* viewport-derived
    // read blips with author + last-modified + mention metadata sourced
    // from the matching SidecarSelectedWaveDocument. It must not erase the
    // parentBlipId / threadId already carried on the read blip — the
    // projector's metadata source does not know about thread linkage and
    // wiping those fields breaks R-3.7 depth-nav drill-in / inline-reply
    // chip rendering.
    J2clReadBlip blipWithLinkage =
        new J2clReadBlip(
            "b+child",
            "Reply text",
            Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
            /* authorId= */ "",
            /* authorDisplayName= */ "",
            /* lastModifiedTimeMillis= */ 0L,
            /* parentBlipId= */ "b+parent",
            /* threadId= */ "t+inline",
            /* unread= */ false,
            /* hasMention= */ false);

    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+child",
            "alice@example.com",
            7L,
            1714240000000L,
            "Reply text");

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Arrays.asList(blipWithLinkage), Arrays.asList(document));

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip out = enriched.get(0);
    Assert.assertEquals("alice@example.com", out.getAuthorId());
    Assert.assertEquals(1714240000000L, out.getLastModifiedTimeMillis());
    Assert.assertEquals("b+parent", out.getParentBlipId());
    Assert.assertEquals("t+inline", out.getThreadId());
  }

  // -- J-UI-6 (#1084, R-5.4) — task done state plumbing ------------------------

  @Test
  public void documentTaskDoneTrueWhenAnnotationCarriesTrue() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/done", "true", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertTrue(J2clSelectedWaveProjector.documentTaskDone(document));
  }

  @Test
  public void documentTaskDoneFalseForFalsyOrAbsentValues() {
    // task/done annotation with a non-"true" value reads as open. The
    // delta-factory writes the literal string "false" when reopening.
    SidecarSelectedWaveDocument falseDoc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/done", "false", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertFalse(J2clSelectedWaveProjector.documentTaskDone(falseDoc));

    SidecarSelectedWaveDocument noAnnotation =
        new SidecarSelectedWaveDocument(
            "b+root", "alice@example.com", 7L, 1714240000000L, "Pin the retry");
    Assert.assertFalse(J2clSelectedWaveProjector.documentTaskDone(noAnnotation));
  }

  @Test
  public void documentTaskAssigneeReadsAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/assignee", "bob@example.com", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals("bob@example.com", J2clSelectedWaveProjector.documentTaskAssignee(document));
  }

  @Test
  public void documentTaskAssigneeEmptyForUnsetAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root", "alice@example.com", 7L, 1714240000000L, "Pin the retry");
    Assert.assertEquals("", J2clSelectedWaveProjector.documentTaskAssignee(document));
  }

  @Test
  public void documentTaskDueTimestampParsesNumericAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "1714560000000", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        1714560000000L, J2clSelectedWaveProjector.documentTaskDueTimestamp(document));
  }

  @Test
  public void documentTaskDueTimestampUnknownForBlankOrUnparseable() {
    SidecarSelectedWaveDocument blank =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        J2clSelectedWaveProjector.documentTaskDueTimestamp(blank));

    SidecarSelectedWaveDocument garbage =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "tomorrow", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        J2clSelectedWaveProjector.documentTaskDueTimestamp(garbage));
  }

  @Test
  public void enrichReadBlipMetadataPropagatesTaskDoneFromDocument() {
    // The enrichment pass is the bridge from wire-format documents to the
    // read model. A blip whose document carries task/done=true MUST end up
    // with isTaskDone() = true so the renderer can paint the strikethrough
    // on reload + live updates.
    J2clReadBlip viewportBlip = new J2clReadBlip("b+root", "Pin the retry");
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(
                new SidecarAnnotationRange("task/done", "true", 0, 14),
                new SidecarAnnotationRange("task/assignee", "bob@example.com", 0, 14),
                new SidecarAnnotationRange("task/dueTs", "1714560000000", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Arrays.asList(viewportBlip), Arrays.asList(document));

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip out = enriched.get(0);
    Assert.assertTrue(out.isTaskDone());
    Assert.assertEquals("bob@example.com", out.getTaskAssignee());
    Assert.assertEquals(1714560000000L, out.getTaskDueTimestamp());
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsCarriesTaskMetadata() {
    // The dominant production code path is renderWindow over the flat
    // render — without this enrichment, the wave-blip elements emitted by
    // the renderWindow path lose data-task-completed and the strikethrough
    // never shows on reload.
    J2clReadBlip enrichedBlip =
        new J2clReadBlip(
            "b+root",
            "Pin the retry",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "bob@example.com",
            /* taskDueTimestamp= */ 1714560000000L);

    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry plain =
        org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.loaded(
            "blip:b+root", 0L, 9L, "b+root", "Pin the retry");

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> enriched =
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Arrays.asList(plain), Arrays.asList(enrichedBlip));

    Assert.assertEquals(1, enriched.size());
    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry out = enriched.get(0);
    Assert.assertTrue(out.isTaskDone());
    Assert.assertEquals("bob@example.com", out.getTaskAssignee());
    Assert.assertEquals(1714560000000L, out.getTaskDueTimestamp());
    // Author + timestamp metadata also propagates so the existing F-2 flat
    // render path metadata works through the window path.
    Assert.assertEquals("alice@example.com", out.getAuthorId());
    Assert.assertEquals(1714240000000L, out.getLastModifiedTimeMillis());
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsLeavesPlaceholdersUnchanged() {
    // Placeholders carry no blip text; enrichment must leave them alone so
    // the renderer's placeholder branch keeps its contract.
    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry placeholder =
        org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.placeholder(
            "blip:b+missing", 0L, 9L, "b+missing");

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> enriched =
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Arrays.asList(placeholder),
            Arrays.asList(new J2clReadBlip("b+missing", "ignored")));

    Assert.assertEquals(1, enriched.size());
    Assert.assertSame(placeholder, enriched.get(0));
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsReturnsInputWhenInputsAreEmpty() {
    Assert.assertSame(
        Collections.<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry>emptyList(),
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Collections.<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "ignored"))));

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> entries =
        Arrays.asList(
            org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.loaded(
                "blip:b+root", 0L, 9L, "b+root", "Pin the retry"));
    Assert.assertSame(
        entries,
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            entries, Collections.<J2clReadBlip>emptyList()));
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

  private static J2clSelectedWaveViewportState viewportWithAttachment() {
    return J2clSelectedWaveViewportState.fromFragments(attachmentFragments(9L, 0L, 9L));
  }

  private static SidecarSelectedWaveFragments attachmentFragments(
      long snapshotVersion, long fromVersion, long toVersion) {
    return new SidecarSelectedWaveFragments(
        snapshotVersion,
        fromVersion,
        toVersion,
        Arrays.asList(
            new SidecarSelectedWaveFragmentRange("blip:b+root", fromVersion, toVersion)),
        Arrays.asList(
            new SidecarSelectedWaveFragment("blip:b+root", ATTACHMENT_RAW_SNAPSHOT, 0, 0)));
  }

  private static J2clSelectedWaveViewportState viewportWithTwoAttachments() {
    return J2clSelectedWaveViewportState.fromFragments(
        new SidecarSelectedWaveFragments(
            9L,
            0L,
            9L,
            Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment(
                    "blip:b+root",
                    "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                        + "<caption>Hero diagram</caption></image>"
                        + " and <image attachment=\"example.com/att+diagram\" "
                        + "display-size=\"small\"><caption>Diagram</caption></image>",
                    0,
                    0))));
  }

  private static J2clAttachmentMetadata attachmentMetadata(
      String attachmentId,
      String fileName,
      String mimeType,
      String attachmentUrl,
      String thumbnailUrl,
      boolean malware) {
    return new J2clAttachmentMetadata(
        attachmentId,
        "example.com/w+1/~/conv+root",
        fileName,
        mimeType,
        4096L,
        "user@example.com",
        attachmentUrl,
        thumbnailUrl,
        new J2clAttachmentMetadata.ImageMetadata(1200, 800),
        new J2clAttachmentMetadata.ImageMetadata(320, 200),
        malware);
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

  private static void assertNoEntryBySegment(
      J2clSelectedWaveViewportState viewport, String segment) {
    for (J2clSelectedWaveViewportState.Entry entry : viewport.getEntries()) {
      if (segment.equals(entry.getSegment())) {
        throw new AssertionError("Unexpected segment: " + segment);
      }
    }
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

  // ─── J-UI-4 (#1082, R-3.1) — applyConversationManifest ────────────────

  @Test
  public void applyConversationManifestIsNoOpWhenManifestEmpty() {
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+a", "alpha"), new J2clReadBlip("b+b", "beta"));
    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(
            blips, SidecarConversationManifest.empty());
    Assert.assertSame(blips, result);
  }

  @Test
  public void applyConversationManifestReordersBlipsIntoManifestDfsOrder() {
    // Manifest order: b+second, b+first  (the input list is in arrival
    // order; the projector must hand back manifest order).
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+second", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+first", "", "root", 0, 1)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(new J2clReadBlip("b+first", "alpha"), new J2clReadBlip("b+second", "beta"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("b+second", result.get(0).getBlipId());
    Assert.assertEquals("beta", result.get(0).getText());
    Assert.assertEquals("b+first", result.get(1).getBlipId());
    Assert.assertEquals("alpha", result.get(1).getText());
  }

  @Test
  public void applyConversationManifestGraftsParentAndThreadOnNestedBlip() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+parent", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+child", "b+parent", "t+reply", 1, 0)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+parent", "p"), new J2clReadBlip("b+child", "c"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("", result.get(0).getParentBlipId());
    Assert.assertEquals("root", result.get(0).getThreadId());
    Assert.assertEquals("b+parent", result.get(1).getParentBlipId());
    Assert.assertEquals("t+reply", result.get(1).getThreadId());
  }

  @Test
  public void applyConversationManifestEmitsPlaceholderForOrphanedManifestEntry() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+missing", "", "root", 0, 0)));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(
            Collections.<J2clReadBlip>emptyList(), manifest);

    Assert.assertEquals(1, result.size());
    Assert.assertEquals("b+missing", result.get(0).getBlipId());
    Assert.assertEquals("", result.get(0).getText());
    Assert.assertEquals("", result.get(0).getParentBlipId());
    Assert.assertEquals("root", result.get(0).getThreadId());
  }

  @Test
  public void applyConversationManifestAppendsExtraBlipsNotReferencedByManifest() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+tracked", "", "root", 0, 0)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+tracked", "tracked"),
            new J2clReadBlip("b+stray", "stray"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("b+tracked", result.get(0).getBlipId());
    Assert.assertEquals("b+stray", result.get(1).getBlipId());
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

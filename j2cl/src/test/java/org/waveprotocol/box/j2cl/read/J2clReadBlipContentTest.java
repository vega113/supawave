package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;

@J2clTestInput(J2clReadBlipContentTest.class)
public class J2clReadBlipContentTest {
  @Test
  public void parsesSingleAndDoubleQuotedImageAttributesWithEntities() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image attachment='ex&amp;ample.com/att+1' display-size=\"large\">"
                + "<caption>AT&amp;T &lt;diagram&gt;</caption></image> after");

    Assert.assertEquals("Before  after", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
    J2clAttachmentRenderModel attachment = parsed.getAttachments().get(0);
    Assert.assertEquals("ex&ample.com/att+1", attachment.getAttachmentId());
    Assert.assertEquals("AT&T <diagram>", attachment.getCaption());
    Assert.assertEquals("large", attachment.getDisplaySize());
    Assert.assertTrue(attachment.isMetadataPending());
  }

  @Test
  public void ignoresAttributeNameSubstrings() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image data-attachment=\"wrong\" data-note=\"x attachment=oops\" "
                + "attachment=\"right\" "
                + "data-display-size=\"large\" display-size=\"medium\">"
                + "<caption>Caption</caption></image> after");

    Assert.assertEquals(1, parsed.getAttachments().size());
    J2clAttachmentRenderModel attachment = parsed.getAttachments().get(0);
    Assert.assertEquals("right", attachment.getAttachmentId());
    Assert.assertEquals("medium", attachment.getDisplaySize());
  }

  @Test
  public void normalizesUppercaseDisplaySizeAttributeValues() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<image ATTACHMENT=\"example.com/att+1\" DISPLAY-SIZE=\"MEDIUM\">"
                + "<caption>Caption</caption></image>");

    Assert.assertEquals(1, parsed.getAttachments().size());
    Assert.assertEquals("medium", parsed.getAttachments().get(0).getDisplaySize());
  }

  @Test
  public void malformedImageWithoutAttachmentRemainsVisibleText() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image display-size=\"large\"><caption>Visible</caption></image> after");

    Assert.assertEquals("Before Visible after", parsed.getText());
    Assert.assertTrue(parsed.getAttachments().isEmpty());
  }

  @Test
  public void malformedImageWithoutAttachmentAdvancesToNextImage() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image display-size=\"large\"><caption>Visible</caption></image>"
                + "<image attachment=\"example.com/att+2\"><caption>Second</caption></image>");

    Assert.assertEquals("Before Visible", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
    Assert.assertEquals("example.com/att+2", parsed.getAttachments().get(0).getAttachmentId());
  }

  @Test
  public void unclosedImageCreatesPendingAttachmentAndKeepsFollowingTextOutOfCaption() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image attachment=\"example.com/att+1\" display-size=\"medium\"> after");

    Assert.assertEquals("Before  after", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
    J2clAttachmentRenderModel attachment = parsed.getAttachments().get(0);
    Assert.assertEquals("example.com/att+1", attachment.getAttachmentId());
    Assert.assertEquals("example.com/att+1", attachment.getCaption());
    Assert.assertEquals("medium", attachment.getDisplaySize());
  }

  @Test
  public void lineTagsSeparateAdjacentTextSegments() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><line/>Hello<line/>World</body>");

    Assert.assertEquals("Hello\nWorld", parsed.getText());
    Assert.assertTrue(parsed.getAttachments().isEmpty());
  }

  @Test
  public void leadingLineTagDoesNotAddLeadingSeparator() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body><line/>Hello</body>");

    Assert.assertEquals("Hello", parsed.getText());
  }

  @Test
  public void trailingLineTagDocumentsTrailingSeparatorBehavior() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body>Hello<line/></body>");

    Assert.assertEquals("Hello\n", parsed.getText());
  }

  @Test
  public void consecutiveLineTagsDoNotDuplicateLeadingSeparator() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body><line/><line/>Hello</body>");

    Assert.assertEquals("Hello", parsed.getText());
  }

  @Test
  public void consecutiveLineTagsInsideTextCollapseToSingleSeparator() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body>Hello<line/><line/>World</body>");

    Assert.assertEquals("Hello\nWorld", parsed.getText());
  }

  @Test
  public void trailingHorizontalWhitespaceIsNormalizedAtLineBoundary() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body>Hello   <line/>World</body>");

    Assert.assertEquals("Hello\nWorld", parsed.getText());
  }

  @Test
  public void lineTagsMatchCaseAndWhitespaceVariants() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body>A<LINE t=\"1\"/>B<line\n/>C<line \t t=\"2\"></line>D</body>");

    Assert.assertEquals("A\nB\nC\nD", parsed.getText());
  }

  @Test
  public void linefeedTagDoesNotCountAsLineSeparator() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body>A<linefeed/>B</body>");

    Assert.assertEquals("AB", parsed.getText());
  }

  @Test
  public void lineSeparatorAndImageExtractionCompose() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><line/>X<image attachment=\"example.com/att+1\"/>"
                + "<line/>Y</body>");

    Assert.assertEquals("X\nY", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
    Assert.assertEquals("example.com/att+1", parsed.getAttachments().get(0).getAttachmentId());
  }

  @Test
  public void preservesInlineReplyAnchorMetadataWithoutVisibleReplyId() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><line/>Before <reply id=\"t+inline\"></reply> after</body>");

    Assert.assertEquals("Before  after", parsed.getText());
    Assert.assertEquals(1, parsed.getInlineReplyAnchors().size());
    Assert.assertEquals("t+inline", parsed.getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals("Before ".length(), parsed.getInlineReplyAnchors().get(0).getTextOffset());
  }

  @Test
  public void inlineReplyAnchorOffsetUsesDecodedVisibleText() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><line/>A &amp; B <reply id=\"t+entity\"></reply> after</body>");

    Assert.assertEquals("A & B  after", parsed.getText());
    Assert.assertEquals(1, parsed.getInlineReplyAnchors().size());
    Assert.assertEquals("t+entity", parsed.getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals("A & B ".length(), parsed.getInlineReplyAnchors().get(0).getTextOffset());
  }

  @Test
  public void inlineReplyAnchorOffsetAfterLineUsesDecodedVisibleText() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body>A &amp; B   <line/>C <reply id=\"t+after-line\"></reply></body>");

    Assert.assertEquals("A & B\nC ", parsed.getText());
    Assert.assertEquals(1, parsed.getInlineReplyAnchors().size());
    Assert.assertEquals("t+after-line", parsed.getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals(
        "A & B\nC ".length(), parsed.getInlineReplyAnchors().get(0).getTextOffset());
  }

  @Test
  public void parsesTaskAnnotationsFromRawFragmentSnapshot() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><?a \"task/done\"=\"true\" \"task/assignee\"=\"alice@local.net\" "
                + "\"task/dueTs\"=\"1777593600000\"?>Review launch"
                + "<?a \"task/done\" \"task/assignee\" \"task/dueTs\"?></body>");

    Assert.assertEquals("Review launch", parsed.getText());
    Assert.assertTrue(parsed.isTask());
    Assert.assertTrue(parsed.isTaskDone());
    Assert.assertEquals("alice@local.net", parsed.getTaskAssignee());
    Assert.assertEquals(1777593600000L, parsed.getTaskDueTimestamp());
  }

  @Test
  public void parsesFalseTaskDoneAsOpenFromRawFragmentSnapshot() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><?a \"task/done\"=\"false\"?>Review launch"
                + "<?a \"task/done\"?></body>");

    Assert.assertTrue("task/done=false annotation → isTask must be true", parsed.isTask());
    Assert.assertFalse(parsed.isTaskDone());
  }

  @Test
  public void parsesTombstoneAnnotationFromRawFragmentSnapshot() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><?a \"tombstone/deleted\"=\"true\"?>Deleted"
                + "<?a \"tombstone/deleted\"?></body>");

    Assert.assertTrue(parsed.isDeleted());
  }

  @Test
  public void leadingLineBeforeImageDoesNotAddSeparator() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "<body><line/><image attachment=\"example.com/att+1\"/><line/>Y</body>");

    Assert.assertEquals("Y", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
  }

  @Test
  public void selfClosingImageCreatesPendingAttachment() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image attachment=\"example.com/att+self\" display-size=\"medium\" /> after");

    Assert.assertEquals("Before  after", parsed.getText());
    Assert.assertEquals(1, parsed.getAttachments().size());
    J2clAttachmentRenderModel attachment = parsed.getAttachments().get(0);
    Assert.assertEquals("example.com/att+self", attachment.getAttachmentId());
    Assert.assertEquals("medium", attachment.getDisplaySize());
  }

  @Test
  public void selfClosingImageDoesNotConsumeLaterClosedImage() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot(
            "Before <image attachment=\"example.com/att+self\" /> between "
                + "<image attachment=\"example.com/att+closed\" display-size=\"large\">"
                + "<caption>Closed caption</caption></image> after");

    Assert.assertEquals("Before  between  after", parsed.getText());
    Assert.assertEquals(2, parsed.getAttachments().size());
    Assert.assertEquals("example.com/att+self", parsed.getAttachments().get(0).getAttachmentId());
    Assert.assertEquals("example.com/att+self", parsed.getAttachments().get(0).getCaption());
    Assert.assertEquals("example.com/att+closed", parsed.getAttachments().get(1).getAttachmentId());
    Assert.assertEquals("Closed caption", parsed.getAttachments().get(1).getCaption());
    Assert.assertEquals("large", parsed.getAttachments().get(1).getDisplaySize());
  }

  @Test
  public void literalGreaterThanInPlainTextIsPreserved() {
    J2clReadBlipContent parsed = J2clReadBlipContent.parseRawSnapshot("a > b");
    Assert.assertEquals("a > b", parsed.getText());
    Assert.assertEquals(0, parsed.getAttachments().size());
  }

  @Test
  public void literalGreaterThanOutsideTagIsNotTreatedAsTagClose() {
    J2clReadBlipContent parsed =
        J2clReadBlipContent.parseRawSnapshot("<body>x > y</body>");
    Assert.assertEquals("x > y", parsed.getText());
  }
}

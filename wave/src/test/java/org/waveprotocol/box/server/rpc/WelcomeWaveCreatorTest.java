package org.waveprotocol.box.server.rpc;

import junit.framework.TestCase;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.conversation.testing.FakeConversationView;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.RangedAnnotation;
import org.waveprotocol.wave.model.document.util.DocHelper;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.ThreadState;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;

import java.util.ArrayList;
import java.util.List;

public class WelcomeWaveCreatorTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("new@example.com");
  private static final String WAVE_0_URI =
      "wave://supawave.ai/w+PSWhwKguwjA/~/conv+root/b+PSWhwKguwjB";
  private static final String WAVE_1_URI =
      "wave://supawave.ai/w+IaeaidlHtXA/~/conv+root/b+IaeaidlHtXB";
  private static final String WAVE_2_URI =
      "wave://supawave.ai/w+IaeaidlHtXC/~/conv+root/b+IaeaidlHtXD";
  private static final String WAVE_3_URI =
      "wave://supawave.ai/w+IaeaidlHtXE/~/conv+root/b+IaeaidlHtXF";
  private static final String WAVE_4_URI =
      "wave://supawave.ai/w+IaeaidlHtXG/~/conv+root/b+IaeaidlHtXH";
  private static final String WAVE_5_URI =
      "wave://supawave.ai/w+IaeaidlHtXI/~/conv+root/b+IaeaidlHtXJ";

  public void testPopulateCreatesStructuredRootBlipAndDetailThreads() {
    FakeConversationView view = FakeConversationView.builder().with(USER).build();
    WaveletBasedConversation conversation = view.createRoot();
    ObservableConversationThread rootThread = conversation.getRootThread();
    ObservableConversationBlip rootBlip = rootThread.appendBlip();

    WelcomeWaveContentBuilder.AuthoringResult result =
        new WelcomeWaveContentBuilder().populate(rootBlip, USER);

    String rootTitle = TitleHelper.extractTitle(rootBlip.getContent());
    String rootText = textOf(rootBlip.getContent());
    List<String> detailTexts = detailTexts(rootBlip);

    assertEquals("Welcome to SupaWave", rootTitle);
    assertTrue(rootText.contains("What Wave is"));
    assertTrue(rootText.contains("This welcome wave is your dock"));
    assertTrue(rootText.contains("Why it works well with AI agents"));
    assertTrue(rootText.contains("A few good first moves"));
    assertTrue(rootText.contains("Start a new wave with New Wave."));
    assertTrue(rootText.contains("Add people or bots from the participant bar."));
    assertTrue(rootText.contains("Pin what keeps moving."));
    assertTrue(rootText.contains("Archive what should leave the inbox but stay searchable."));
    assertTrue(rootText.contains("Search"));
    assertTrue(rootText.contains("Public waves"));
    assertTrue(rootText.contains("Pin"));
    assertTrue(rootText.contains("Archive"));
    assertTrue(rootText.contains("@mention"));
    assertTrue(rootText.contains("@ icon"));
    assertTrue(rootText.contains("left search panel"));
    assertTrue(rootText.contains("Robots and Data API"));
    assertTrue(rootText.contains("Talk to the bot"));
    assertTrue(rootText.contains("gpt-ts-bot@supawave.ai"));
    assertTrue(rootText.contains("summarize"));
    assertTrue(rootText.contains("draft"));
    assertTrue(rootText.contains("Turn this into a checklist"));
    assertTrue(rootText.contains("Onboarding waves"));
    assertTrue(rootText.contains("Wave-to-wave navigation"));
    assertTrue(rootText.contains("dead-end help page"));
    assertTrue(rootText.contains("SupaWave Community"));
    assertTrue(rootText.contains("Questions, Feedback & Support"));
    assertTrue(rootText.contains("Search Like a Pro"));
    assertTrue(rootText.contains("Collaboration Features"));
    assertTrue(rootText.contains("Making Waves Public"));
    assertTrue(rootText.contains("Tips, Shortcuts, and Hidden Features"));
    assertFalse(rootText.contains("Wave 0"));
    assertFalse(rootText.contains("Wave 1"));
    assertFalse(rootText.contains("Wave 2"));
    assertFalse(rootText.contains("Wave 3"));
    assertFalse(rootText.contains("Wave 4"));
    assertFalse(rootText.contains("Wave 5"));
    assertTrue(rootText.contains("vega@supawave.ai"));
    assertTrue(rootText.contains("collapsed inline blips"));
    assertTrue(rootText.contains("Grokipedia history"));
    assertTrue(rootText.contains("SupaWave repository"));
    assertTrue(rootText.contains("SupaWave changelog"));
    assertTrue(rootText.contains("SupaWave API docs"));
    assertTrue(rootText.contains("Public directory"));
    assertTrue(rootText.contains("Contact Us"));
    assertTrue(rootText.contains("TypeScript bot repository"));
    assertFalse(rootText.contains("https://"));
    assertManualLink(rootBlip.getContent(), "https://grokipedia.com/page/Google_Wave");
    assertManualLink(rootBlip.getContent(), "https://github.com/vega113/supawave");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/changelog");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/api-docs");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/public");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/contact");
    assertManualLink(rootBlip.getContent(), "https://github.com/vega113/gpt-bot-ts");
    assertManualLink(rootBlip.getContent(), WAVE_0_URI);
    assertManualLink(rootBlip.getContent(), WAVE_1_URI);
    assertManualLink(rootBlip.getContent(), WAVE_2_URI);
    assertManualLink(rootBlip.getContent(), WAVE_3_URI);
    assertManualLink(rootBlip.getContent(), WAVE_4_URI);
    assertManualLink(rootBlip.getContent(), WAVE_5_URI);
    assertEquals(13, manualLinkAnnotations(rootBlip.getContent()).size());

    assertEquals(5, detailTexts.size());
    assertEquals(5, result.getCollapsedThreadIds().size());
    assertTrue(containsText(detailTexts, "Firefly"));
    assertTrue(containsText(detailTexts, "Google Wave"));
    assertTrue(containsText(detailTexts, "Apache Wave"));
    assertTrue(containsText(detailTexts, "snapshots"));
    assertTrue(containsText(detailTexts, "tags"));
    assertTrue(containsText(detailTexts, "contacts"));
    assertTrue(containsText(detailTexts, "federation"));
  }

  public void testPersistCollapsedThreadStateMarksAllDetailThreadsCollapsed() {
    FakeConversationView view = FakeConversationView.builder().with(USER).build();
    WaveletBasedConversation conversation = view.createRoot();
    ObservableConversationThread rootThread = conversation.getRootThread();
    ObservableConversationBlip rootBlip = rootThread.appendBlip();
    WelcomeWaveContentBuilder.AuthoringResult result =
        new WelcomeWaveContentBuilder().populate(rootBlip, USER);

    Wavelet userDataWavelet = view.getWaveView().createUserData();
    PrimitiveSupplement supplement = WaveletBasedSupplement.create(userDataWavelet);

    WelcomeWaveCreator.persistCollapsedThreadState(
        supplement, conversation.getWavelet().getId(), result.getCollapsedThreadIds());

    for (String threadId : result.getCollapsedThreadIds()) {
      assertEquals(ThreadState.COLLAPSED,
          supplement.getThreadState(conversation.getWavelet().getId(), threadId));
    }
  }

  private static String textOf(Document doc) {
    return DocHelper.getText(doc, doc, doc.getDocumentElement());
  }

  private static List<String> detailTexts(ObservableConversationBlip rootBlip) {
    List<String> texts = new ArrayList<String>();
    for (ConversationBlip.LocatedReplyThread<? extends ObservableConversationThread> locatedThread
        : rootBlip.locateReplyThreads()) {
      texts.add(textOf(locatedThread.getThread().getFirstBlip().getContent()));
    }
    return texts;
  }

  private static boolean containsText(List<String> texts, String needle) {
    for (String text : texts) {
      if (text.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private static void assertManualLink(Document doc, String url) {
    for (RangedAnnotation<String> annotation : manualLinkAnnotations(doc)) {
      if (url.equals(annotation.value())) {
        return;
      }
    }
    fail("expected manual link annotation for " + url);
  }

  private static List<RangedAnnotation<String>> manualLinkAnnotations(Document doc) {
    List<RangedAnnotation<String>> annotations = new ArrayList<RangedAnnotation<String>>();
    for (RangedAnnotation<String> annotation
        : doc.rangedAnnotations(0, doc.size(), CollectionUtils.newStringSet(
            AnnotationConstants.LINK_MANUAL))) {
      if (annotation.value() != null) {
        annotations.add(annotation);
      }
    }
    return annotations;
  }
}

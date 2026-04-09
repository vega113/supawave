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
    assertTrue(rootText.contains("Why it works well with AI agents"));
    assertTrue(rootText.contains("Create a wave"));
    assertTrue(rootText.contains("Add participants"));
    assertTrue(rootText.contains("Search"));
    assertTrue(rootText.contains("Public waves"));
    assertTrue(rootText.contains("Pin"));
    assertTrue(rootText.contains("Archive"));
    assertTrue(rootText.contains("@mention"));
    assertTrue(rootText.contains("Robots and Data API"));
    assertTrue(rootText.contains("gpt-bot-ts@supawave.ai"));
    assertTrue(rootText.contains("vega@supawave.ai"));
    assertTrue(rootText.contains("collapsed inline blips"));
    assertTrue(rootText.contains("Grokipedia history"));
    assertTrue(rootText.contains("incubator-wave repository"));
    assertTrue(rootText.contains("SupaWave changelog"));
    assertTrue(rootText.contains("SupaWave API docs"));
    assertTrue(rootText.contains("Public directory"));
    assertTrue(rootText.contains("Contact Us"));
    assertTrue(rootText.contains("gpt-bot-ts repository"));
    assertFalse(rootText.contains("https://"));
    assertManualLink(rootBlip.getContent(), "https://grokipedia.com/page/Google_Wave");
    assertManualLink(rootBlip.getContent(), "https://github.com/vega113/incubator-wave");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/changelog");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/api-docs");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/public");
    assertManualLink(rootBlip.getContent(), "https://supawave.ai/contact");
    assertManualLink(rootBlip.getContent(), "https://github.com/vega113/gpt-bot-ts");

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
    for (RangedAnnotation<String> annotation
        : doc.rangedAnnotations(0, doc.size(), CollectionUtils.newStringSet(
            AnnotationConstants.LINK_MANUAL))) {
      if (url.equals(annotation.value())) {
        return;
      }
    }
    fail("expected manual link annotation for " + url);
  }
}

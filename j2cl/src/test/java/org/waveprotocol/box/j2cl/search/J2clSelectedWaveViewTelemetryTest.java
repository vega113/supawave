package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;

@J2clTestInput(J2clSelectedWaveViewTelemetryTest.class)
public class J2clSelectedWaveViewTelemetryTest {
  private HTMLElement currentHost;

  @After
  public void tearDown() {
    if (currentHost != null && currentHost.parentElement != null) {
      currentHost.parentElement.removeChild(currentHost);
    }
    currentHost = null;
  }

  @Test
  public void telemetrySinkReachesRenderedAttachmentLinks() {
    assumeBrowserDom();
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    HTMLElement host = createHost();
    J2clSelectedWaveView view = new J2clSelectedWaveView(host, telemetry);

    view.render(selectedWaveModelWithAttachment());
    HTMLElement openLink = (HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']");
    Assert.assertNotNull("Expected attachment open link to be rendered", openLink);
    openLink.click();

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.open.clicked", event.getName());
    Assert.assertEquals("read-surface", event.getFields().get("source"));
    Assert.assertEquals("medium", event.getFields().get("displaySize"));
  }

  @Test
  public void telemetrySinkReachesServerRenderedAttachmentLinks() {
    assumeBrowserDom();
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    HTMLElement host = createHost();
    host.innerHTML =
        "<section data-j2cl-upgrade-placeholder=\"selected-wave\""
            + " data-j2cl-server-first-selected-wave=\"example.com/w+1\""
            + " data-j2cl-server-first-mode=\"selected\">"
            + "<h2 class=\"sidecar-selected-title\">Selected wave</h2>"
            + "<p class=\"sidecar-selected-unread\"></p>"
            + "<p class=\"sidecar-selected-status\"></p>"
            + "<p class=\"sidecar-selected-detail\"></p>"
            + "<p class=\"sidecar-selected-participants\"></p>"
            + "<p class=\"sidecar-selected-snippet\"></p>"
            + "<div class=\"sidecar-selected-compose\"></div>"
            + "<div class=\"sidecar-selected-content\">"
            + "<div class=\"wave-content\" data-j2cl-read-surface=\"true\">"
            + "<div class=\"thread\" data-thread-id=\"root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">"
            + "<div data-j2cl-read-attachment=\"true\" data-display-size=\"large\">"
            + "<a href=\"/attachment/example.com/att+hero\""
            + " data-j2cl-attachment-open=\"true\">Open</a>"
            + "</div></div></div></div></div></section>";
    new J2clSelectedWaveView(host, telemetry);

    HTMLElement openLink = (HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']");
    Assert.assertNotNull("Expected server-rendered attachment open link", openLink);
    openLink.click();

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.open.clicked", event.getName());
    Assert.assertEquals("read-surface", event.getFields().get("source"));
    Assert.assertEquals("large", event.getFields().get("displaySize"));
  }

  private HTMLElement createHost() {
    currentHost = (HTMLElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(currentHost);
    return currentHost;
  }

  private static J2clSelectedWaveModel selectedWaveModelWithAttachment() {
    J2clAttachmentRenderModel attachment =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            new J2clAttachmentMetadata(
                "example.com/att+hero",
                "example.com/w+1/~/conv+root",
                "hero.png",
                "image/png",
                1234L,
                "user@example.com",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                null,
                false));
    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        "example.com/w+1",
        "Selected wave",
        "",
        "",
        "Opened.",
        "",
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Arrays.asList(new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment))),
        null,
        J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
        false,
        false,
        false);
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }
}

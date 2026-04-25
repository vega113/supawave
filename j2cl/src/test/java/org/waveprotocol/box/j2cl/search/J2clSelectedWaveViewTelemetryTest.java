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
    ((HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']")).click();

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("attachment.open.clicked", event.getName());
    Assert.assertEquals("medium", event.getFields().get("displaySize"));
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

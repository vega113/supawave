package org.waveprotocol.box.j2cl.telemetry;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import java.util.Map;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clClientTelemetryTest.class)
public class J2clClientTelemetryTest {
  @Test
  public void eventAddsCommonFieldsAndKeepsLowCardinalityFields() {
    RecordingTelemetrySink sink = new RecordingTelemetrySink();

    sink.record(
        J2clClientTelemetry.event("attachment.upload.failed")
            .field("source", "file-picker")
            .field("reason", "server")
            .field("statusBucket", "5xx")
            .build());

    J2clClientTelemetry.Event event = sink.events().get(0);
    Assert.assertEquals("attachment.upload.failed", event.getName());
    Assert.assertEquals("j2cl-root", event.getFields().get("surface"));
    Assert.assertEquals("parity", event.getFields().get("category"));
    Assert.assertEquals("file-picker", event.getFields().get("source"));
    Assert.assertEquals("server", event.getFields().get("reason"));
    Assert.assertEquals("5xx", event.getFields().get("statusBucket"));
  }

  @Test
  public void eventRejectsSensitiveAndReservedFieldNames() {
    String[] sensitiveFields = {
        "fileName", "filename", "caption", "waveId", "waveRef", "address",
        "attachmentId", "url", "href", "payload", "content", "token", "ToKeN",
        "moduleName", "subSystem", "evtGroup", "millis", "type", "surface", "category"};
    for (String fieldName : sensitiveFields) {
      try {
        J2clClientTelemetry.event("attachment.upload.started")
            .field(fieldName, "secret")
            .build();
        Assert.fail("expected failure for " + fieldName);
      } catch (IllegalArgumentException expected) {
        Assert.assertTrue(expected.getMessage().contains("sensitive"));
      }
    }
  }

  @Test
  public void eventRejectsBlankNamesAndDropsBlankValues() {
    try {
      J2clClientTelemetry.event(" ");
      Assert.fail("expected blank event name failure");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Event name"));
    }

    try {
      J2clClientTelemetry.event("attachment.upload.started").field(" ", "value");
      Assert.fail("expected blank field name failure");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Field name"));
    }

    J2clClientTelemetry.Event event =
        J2clClientTelemetry.event("attachment.upload.started")
            .field("source", "")
            .field("reason", null)
            .build();

    Assert.assertFalse(event.getFields().containsKey("source"));
    Assert.assertFalse(event.getFields().containsKey("reason"));
  }

  @Test
  public void noopSinkDoesNotThrow() {
    J2clClientTelemetry.noop()
        .record(
            J2clClientTelemetry.event("richEdit.command.applied")
                .field("commandId", "bold")
                .build());
  }

  @Test
  public void statsEventShapeMatchesGwtStatsChannelKeys() {
    Map<String, Object> stats =
        J2clClientTelemetry.statsEventForTesting(
            J2clClientTelemetry.event("attachment.upload.started")
                .field("source", "file-picker")
                .build());

    Assert.assertEquals("j2cl-root", stats.get("moduleName"));
    Assert.assertEquals("j2cl.parity", stats.get("subSystem"));
    Assert.assertEquals("attachment.upload.started", stats.get("evtGroup"));
    Assert.assertEquals(0.0, stats.get("millis"));
    Assert.assertEquals("event", stats.get("type"));
    Assert.assertEquals("file-picker", stats.get("source"));
    Assert.assertEquals("j2cl-root", stats.get("surface"));
    Assert.assertEquals("parity", stats.get("category"));
  }

  @Test
  public void browserStatsSinkSwallowsListenerException() {
    assumeBrowserDom();
    Object previousStats = Js.asPropertyMap(DomGlobal.window).get("__stats");
    Object previousListener = Js.asPropertyMap(DomGlobal.window).get("__stats_listener");
    try {
      Js.asPropertyMap(DomGlobal.window).delete("__stats");
      Js.asPropertyMap(DomGlobal.window).set("__stats_listener", (StatsListener) event -> {
        throw new RuntimeException("listener boom");
      });

      J2clClientTelemetry.browserStatsSink()
          .record(J2clClientTelemetry.event("attachment.upload.started").build());

      Object stats = Js.asPropertyMap(DomGlobal.window).get("__stats");
      Assert.assertNotNull(stats);
      Assert.assertTrue(statsArrayLengthForTesting(stats) >= 1);
    } finally {
      restoreWindowProperty("__stats", previousStats);
      restoreWindowProperty("__stats_listener", previousListener);
    }
  }

  @JsFunction
  private interface StatsListener {
    void onStats(Object event);
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.window != null);
  }

  private static int statsArrayLengthForTesting(Object stats) {
    Object length = Js.asPropertyMap(stats).get("length");
    return length == null ? 0 : (int) Double.parseDouble(String.valueOf(length));
  }

  private static void restoreWindowProperty(String propertyName, Object previousValue) {
    if (previousValue == null) {
      Js.asPropertyMap(DomGlobal.window).delete(propertyName);
    } else {
      Js.asPropertyMap(DomGlobal.window).set(propertyName, previousValue);
    }
  }
}

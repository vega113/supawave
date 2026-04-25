package org.waveprotocol.box.j2cl.telemetry;

import elemental2.core.JsArray;
import elemental2.dom.DomGlobal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

public final class J2clClientTelemetry {
  private static final Sink NOOP_SINK = event -> {};
  private static final String MODULE_NAME = "j2cl-root";
  private static final String CATEGORY = "parity";
  private static final String SUBSYSTEM = "j2cl.parity";
  private static final String JS_STATS = "__stats";
  private static final String JS_STATS_LISTENER = "__stats_listener";

  private J2clClientTelemetry() {
  }

  public interface Sink {
    void record(Event event);
  }

  public static Builder event(String name) {
    return new Builder(requireNonEmpty(name, "Event name is required."));
  }

  public static Sink noop() {
    return NOOP_SINK;
  }

  public static Sink browserStatsSink() {
    return BrowserStatsSink::dispatch;
  }

  static Map<String, Object> statsEventForTesting(Event event) {
    Map<String, Object> stats = new LinkedHashMap<String, Object>();
    stats.put("moduleName", MODULE_NAME);
    stats.put("subSystem", SUBSYSTEM);
    stats.put("evtGroup", event.getName());
    stats.put("millis", Double.valueOf(0));
    stats.put("type", "event");
    for (Map.Entry<String, String> field : event.getFields().entrySet()) {
      stats.put(field.getKey(), field.getValue());
    }
    return Collections.unmodifiableMap(stats);
  }

  public static final class Builder {
    private final String name;
    private final Map<String, String> fields = new LinkedHashMap<String, String>();

    private Builder(String name) {
      this.name = name;
    }

    public Builder field(String fieldName, String value) {
      String normalizedName = requireNonEmpty(fieldName, "Field name is required.");
      rejectSensitiveOrReservedField(normalizedName);
      String normalizedValue = value == null ? "" : value.trim();
      if (!normalizedValue.isEmpty()) {
        fields.put(normalizedName, normalizedValue);
      }
      return this;
    }

    public Event build() {
      Map<String, String> built = new LinkedHashMap<String, String>();
      built.putAll(fields);
      built.put("surface", MODULE_NAME);
      built.put("category", CATEGORY);
      return new Event(name, built);
    }
  }

  public static final class Event {
    private final String name;
    private final Map<String, String> fields;

    private Event(String name, Map<String, String> fields) {
      this.name = name;
      this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
    }

    public String getName() {
      return name;
    }

    public Map<String, String> getFields() {
      return fields;
    }
  }

  private static final class BrowserStatsSink {
    private BrowserStatsSink() {
    }

    private static void dispatch(Event event) {
      try {
        JsPropertyMap<Object> statsEvent = JsPropertyMap.of();
        for (Map.Entry<String, Object> entry : statsEventForTesting(event).entrySet()) {
          statsEvent.set(entry.getKey(), entry.getValue());
        }
        JsPropertyMap<Object> window = Js.asPropertyMap(DomGlobal.window);
        Object statsObject = window.get(JS_STATS);
        JsArray<Object> statsArray;
        if (statsObject == null || !JsArray.isArray(statsObject)) {
          statsArray = JsArray.of();
          window.set(JS_STATS, statsArray);
        } else {
          statsArray = Js.uncheckedCast(statsObject);
        }
        statsArray.push(statsEvent);
        Object listener = window.get(JS_STATS_LISTENER);
        if (listener != null) {
          StatsListener statsListener = Js.uncheckedCast(listener);
          statsListener.onStats(statsEvent);
        }
      } catch (Throwable ignored) {
        // Best-effort telemetry must never break J2CL product behavior.
      }
    }
  }

  @JsFunction
  private interface StatsListener {
    void onStats(Object event);
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
  }

  private static void rejectSensitiveOrReservedField(String fieldName) {
    String normalized = fieldName.toLowerCase(Locale.ROOT);
    if ("filename".equals(normalized)
        || "caption".equals(normalized)
        || "waveid".equals(normalized)
        || "waveref".equals(normalized)
        || "address".equals(normalized)
        || "attachmentid".equals(normalized)
        || "url".equals(normalized)
        || "href".equals(normalized)
        || "payload".equals(normalized)
        || "content".equals(normalized)
        || "token".equals(normalized)
        || "modulename".equals(normalized)
        || "subsystem".equals(normalized)
        || "evtgroup".equals(normalized)
        || "millis".equals(normalized)
        || "type".equals(normalized)) {
      throw new IllegalArgumentException("Telemetry field is sensitive or reserved: " + fieldName);
    }
  }
}

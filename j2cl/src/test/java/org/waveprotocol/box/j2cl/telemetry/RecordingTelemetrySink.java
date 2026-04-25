package org.waveprotocol.box.j2cl.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingTelemetrySink implements J2clClientTelemetry.Sink {
  private final List<J2clClientTelemetry.Event> events =
      new ArrayList<J2clClientTelemetry.Event>();

  @Override
  public void record(J2clClientTelemetry.Event event) {
    events.add(event);
  }

  public List<J2clClientTelemetry.Event> events() {
    return Collections.unmodifiableList(events);
  }

  public J2clClientTelemetry.Event lastEvent() {
    if (events.isEmpty()) {
      throw new IllegalStateException("No telemetry events recorded");
    }
    return events.get(events.size() - 1);
  }
}

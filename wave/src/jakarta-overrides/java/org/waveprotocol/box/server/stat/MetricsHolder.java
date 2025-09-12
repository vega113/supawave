package org.waveprotocol.box.server.stat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Simple holder for a global Micrometer registry on the Jakarta path.
 */
public final class MetricsHolder {
  private static final PrometheusMeterRegistry PROM = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  static {
    Metrics.addRegistry(PROM);
  }
  private MetricsHolder() {}
  public static PrometheusMeterRegistry prometheus() { return PROM; }
  public static MeterRegistry registry() { return PROM; }
}


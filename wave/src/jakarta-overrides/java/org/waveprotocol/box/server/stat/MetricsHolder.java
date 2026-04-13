package org.waveprotocol.box.server.stat;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
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
    new ClassLoaderMetrics().bindTo(PROM);
    new JvmMemoryMetrics().bindTo(PROM);
    new JvmGcMetrics().bindTo(PROM);
    new JvmThreadMetrics().bindTo(PROM);
    new ProcessorMetrics().bindTo(PROM);
    new UptimeMetrics().bindTo(PROM);
  }
  private MetricsHolder() {}
  public static PrometheusMeterRegistry prometheus() { return PROM; }
  public static MeterRegistry registry() { return PROM; }
}

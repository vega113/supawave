package org.waveprotocol.box.server.stat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer HTTP timing filter for Jakarta path.
 * Records http.server.requests with method/uri/status tags and an active-requests gauge.
 */
public final class MetricsHttpFilter implements Filter {
  private static final AtomicInteger ACTIVE = new AtomicInteger();
  private MeterRegistry registry;
  private Counter exceptions;

  @Override
  public void init(FilterConfig filterConfig) {
    registry = MetricsHolder.registry();
    // Register a gauge once per classloader
    registry.gauge("http.server.active.requests", ACTIVE);
    exceptions = Counter.builder("http.server.exceptions").register(registry);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    long start = System.nanoTime();
    ACTIVE.incrementAndGet();
    String method = (request instanceof HttpServletRequest) ? ((HttpServletRequest) request).getMethod() : "UNKNOWN";
    String uri = (request instanceof HttpServletRequest) ? uriTag((HttpServletRequest) request) : "unknown";
    int status = 200;
    try {
      chain.doFilter(request, response);
      if (response instanceof HttpServletResponse) {
        status = ((HttpServletResponse) response).getStatus();
      }
    } catch (Throwable t) {
      exceptions.increment();
      status = 500;
      throw t;
    } finally {
      long dur = System.nanoTime() - start;
      Timer.builder("http.server.requests")
          .tag("method", method)
          .tag("uri", uri)
          .tag("status", String.valueOf(status))
          .publishPercentileHistogram()
          .register(registry)
          .record(dur, TimeUnit.NANOSECONDS);
      ACTIVE.decrementAndGet();
    }
  }

  @Override
  public void destroy() { }

  private static String uriTag(HttpServletRequest req) {
    String sp = req.getServletPath();
    String pi = req.getPathInfo();
    if (sp == null || sp.isEmpty()) sp = "/";
    if (pi == null || pi.isEmpty()) return sp;
    // Reduce cardinality: map any pathInfo to /*
    if (sp.endsWith("/*")) return sp;
    if (sp.equals("/")) return "/*";
    return sp.endsWith("/") ? sp + "*" : sp + "/*";
  }
}


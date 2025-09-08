package org.waveprotocol.box.server.jakarta;

import org.junit.Assume;

/**
 * Test helpers for Jakarta EE10 (Jetty 12) integration tests.
 *
 * Visibility: public to allow reuse across test packages/modules. This class
 * is located under test sources and is not included in production artifacts.
 * Keep methods static and dependency-free to avoid coupling.
 */
public final class TestSupport {
  private TestSupport() {}

  static boolean isJettyEe10Available() {
    try {
      Class.forName("org.eclipse.jetty.server.Server");
      Class.forName("org.eclipse.jetty.ee10.servlet.ServletContextHandler");
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  static void assumeJettyEe10PresentOrSkip() {
    Assume.assumeTrue("Jetty 12 EE10 classes not available on classpath", isJettyEe10Available());
  }
}

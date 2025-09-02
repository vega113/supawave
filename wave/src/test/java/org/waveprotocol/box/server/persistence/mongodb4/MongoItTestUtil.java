package org.waveprotocol.box.server.persistence.mongodb4;

import org.junit.Assume;
import org.slf4j.Logger;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Shared helpers for Mongo/Testcontainers-based integration tests.
 */
final class MongoItTestUtil {
  private MongoItTestUtil() {}

  static void preferColimaIfDockerHostInvalid(Logger log) {
    try {
      String envHost = System.getenv("DOCKER_HOST");
      String userHome = System.getProperty("user.home");
      java.io.File colimaSock = new java.io.File(userHome + "/.colima/default/docker.sock");
      String effective = null;
      if (envHost == null || envHost.trim().isEmpty()) {
        if (colimaSock.exists()) {
          effective = "unix://" + colimaSock.getAbsolutePath();
          System.setProperty("DOCKER_HOST", effective);
          System.setProperty("docker.host", effective);
          log.info("[IT] Using Colima Docker socket at {}", effective);
        }
      } else if (envHost.startsWith("unix://")) {
        String path = envHost.replaceFirst("^unix://", "");
        if (!(new java.io.File(path).exists())) {
          log.info("[IT] DOCKER_HOST points to missing UNIX socket ({}). Trying Colima...", envHost);
          if (colimaSock.exists()) {
            effective = "unix://" + colimaSock.getAbsolutePath();
            System.setProperty("DOCKER_HOST", effective);
            System.setProperty("docker.host", effective);
            log.info("[IT] Using Colima Docker socket at {}", effective);
          }
        }
      } else if ("unix://localhost:2375".equals(envHost)) {
        log.info("[IT] DOCKER_HOST={} is invalid. Trying Colima...", envHost);
        if (colimaSock.exists()) {
          effective = "unix://" + colimaSock.getAbsolutePath();
          System.setProperty("DOCKER_HOST", effective);
          System.setProperty("docker.host", effective);
          log.info("[IT] Using Colima Docker socket at {}", effective);
        }
      }
      log.info("[IT] Effective DOCKER_HOST={}", (effective != null ? effective : (envHost != null ? envHost : "default")));
    } catch (Throwable t) {
      // Non-fatal: best-effort hinting only
      log.debug("[IT] Colima hint failed (ignored)", t);
    }
  }

  static void startOrSkip(MongoDBContainer mongo, Logger log) {
    try {
      mongo.start();
    } catch (ContainerLaunchException e) {
      log.warn("MongoDBContainer failed to launch; skipping IT. DOCKER_HOST='{}', TESTCONTAINERS_RYUK_DISABLED='{}'",
          System.getenv("DOCKER_HOST"), System.getenv("TESTCONTAINERS_RYUK_DISABLED"), e);
      Assume.assumeNoException("Skipping Mongo IT due to container launch failure", e);
    } catch (Throwable t) {
      log.warn("Docker/Testcontainers error encountered; skipping IT. DOCKER_HOST='{}'", System.getenv("DOCKER_HOST"), t);
      Assume.assumeNoException("Skipping Mongo IT due to Docker/Testcontainers error", t);
    }
  }

  static void stopQuietly(MongoDBContainer mongo, Logger log) {
    try {
      if (mongo != null) {
        mongo.stop();
      }
    } catch (Exception e) {
      log.warn("Ignored exception while stopping MongoDBContainer (potential resource leak).", e);
    }
  }
}


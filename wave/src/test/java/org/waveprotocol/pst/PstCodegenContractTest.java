package org.waveprotocol.pst;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 * Minimal contract check that PST code generation produced at least one Java source file.
 * This complements the Gradle verifyPstCodegen task and serves as an early warning if
 * generation regresses. It assumes tests are configured to depend on verifyPstCodegen.
 */
public class PstCodegenContractTest {

  @Test
  public void generatedSourcesExist() throws IOException {
    // Tests typically run with working directory at the module root (wave/)
    Path outDir = Paths.get("generated", "main", "java");

    assertTrue("Expected PST generated sources directory to exist: " + outDir.toAbsolutePath(),
        Files.exists(outDir) && Files.isDirectory(outDir));

    AtomicInteger javaCount = new AtomicInteger();
    Files.walkFileTree(outDir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.getFileName().toString().endsWith(".java")) {
          javaCount.incrementAndGet();
        }
        return FileVisitResult.CONTINUE;
      }
    });

    assertTrue(
        "Expected at least one generated Java file under " + outDir.toAbsolutePath() +
            ", but found none.",
        javaCount.get() > 0);
  }
}


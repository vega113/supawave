/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class J2clBuildStageContractTest extends TestCase {

  public void testBuildSbtKeepsJ2clBuildsExplicit() throws Exception {
    String buildSbt = read("build.sbt");
    String normalizedBuild = buildSbt.replaceAll("\\s+", " ");

    assertTrue(buildSbt.contains("lazy val j2clSearchBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clSearchTest = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clLitBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clProductionBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clRuntimeBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("ThisBuild / j2clRuntimeBuild := Def.sequential("));
    assertTrue(buildSbt.contains("lazy val cleanStagedJ2clAssets = taskKey[Unit]"));
    assertTrue(buildSbt.contains("WAVE_STAGE_INCLUDE_J2CL_ASSETS"));

    String runLine = findLine(buildSbt, "Compile / run :=");
    String stageLine = findLine(buildSbt, "Universal / stage :=");
    String packageLine = findLine(buildSbt, "Universal / packageBin :=");

    assertFalse(runLine.contains("j2cl"));
    assertFalse(stageLine.contains("j2cl"));
    assertFalse(packageLine.contains("j2cl"));
    assertTrue(normalizedBuild.contains(
        "Compile / run := (Compile / run).dependsOn(prepareServerConfig, compileGwt).evaluated"));
    assertFalse(normalizedBuild.contains(
        "Compile / run := (Compile / run).dependsOn(prepareServerConfig, j2clRuntimeBuild, compileGwt).evaluated"));
    assertTrue(buildSbt.contains("compileGwt := (compileGwt).dependsOn(Compile / compile).value"));
    assertTrue(normalizedBuild.contains(
        ".dependsOn(cleanStagedJ2clAssets, compileGwt, verifyGwtAssets)"));
    assertTrue(normalizedBuild.contains("stagedWarDir / \"j2cl\""));
    assertTrue(normalizedBuild.contains("stagedWarDir / \"j2cl-search\""));
    assertTrue(normalizedBuild.contains("stagedWarDir / \"j2cl-debug\""));
    assertFalse(normalizedBuild.contains(
        "Universal / stage := (Universal / stage).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value"));
    assertTrue(normalizedBuild.contains(
        "Universal / packageBin := (Universal / packageBin).dependsOn(compileGwt, verifyGwtAssets).value"));
    assertFalse(normalizedBuild.contains(
        "Universal / packageBin := (Universal / packageBin).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value"));
  }

  public void testDockerfileCopiesJ2clTreeAndBuildsRuntimeAssets() throws Exception {
    String dockerfile = read("Dockerfile");

    assertTrue(dockerfile.contains("COPY j2cl /workspace/j2cl"));
    assertTrue(dockerfile.contains("nodejs npm"));
    assertTrue(dockerfile.contains("WAVE_STAGE_INCLUDE_J2CL_ASSETS=1"));
    assertTrue(dockerfile.contains(
        "RUN WAVE_STAGE_INCLUDE_J2CL_ASSETS=1 sbt --batch \"pst/compile; wave/compile; j2clRuntimeBuild; Universal/stage\""));
  }

  public void testDeployWorkflowBuildsAndVerifiesJ2clAssets() throws Exception {
    String workflow = read(".github/workflows/deploy-contabo.yml");

    assertTrue(workflow.contains("WAVE_STAGE_INCLUDE_J2CL_ASSETS=1"));
    assertTrue(workflow.contains(
        "WAVE_STAGE_INCLUDE_J2CL_ASSETS=1 sbt --batch \"pst/compile; wave/compile; j2clRuntimeBuild; Universal/stage\""));
    assertTrue(workflow.contains("Verify staged J2CL assets"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl/assets/wavy-thread-collapse.css"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl/assets/shell.css"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl/assets/sidecar.css"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl/assets/wavy-tokens.css"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl/assets/shell.js"));
    assertTrue(workflow.contains("target/universal/stage/war/j2cl-search/sidecar/j2cl-sidecar.js"));

    // J2CL build and verification steps must appear before the container image is published.
    String publishMarker = "Build and push container image";
    assertTrue("image publication marker missing", workflow.contains(publishMarker));
    assertTrue("WAVE_STAGE_INCLUDE_J2CL_ASSETS=1 must precede image publication",
        workflow.indexOf("WAVE_STAGE_INCLUDE_J2CL_ASSETS=1") < workflow.indexOf(publishMarker));
    assertTrue("j2clRuntimeBuild command must precede image publication",
        workflow.indexOf("WAVE_STAGE_INCLUDE_J2CL_ASSETS=1 sbt --batch \"pst/compile; wave/compile; j2clRuntimeBuild; Universal/stage\"")
            < workflow.indexOf(publishMarker));
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }

  private static String findLine(String text, String needle) {
    return text.lines()
        .filter(line -> line.contains(needle))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing line containing: " + needle));
  }
}

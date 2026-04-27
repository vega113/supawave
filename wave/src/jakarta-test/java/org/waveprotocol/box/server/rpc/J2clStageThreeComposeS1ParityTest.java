/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * F-3 slice 1 (#1038, R-5.1 + R-5.2) — per-row parity assertions for
 * the inline rich-text composer foundation + selection-driven format
 * toolbar.
 *
 * <p>Source-level pins are the right level of assertion for the F-3
 * lane: the live DOM round-trip is exercised by the lit web-test-runner
 * suite (which runs against a real Chromium and the actual Lit
 * elements); this fixture pins the production wire contract — element
 * registration, Java listener wiring, and the F-2.S6 gating contract —
 * so any future regression that silently removes the composer
 * foundation fails here even if no live test happens to cover it.
 *
 * <p>Added in F-3.S4 (#1038, S4 closeout) so {@link
 * J2clStageThreeFinalParityTest}'s {@code @Suite} chain has a per-row
 * S1 owner class to point at, mirroring the per-row S2 + S3 fixtures
 * that already exist.
 */
public final class J2clStageThreeComposeS1ParityTest {

  // ---- R-5.1 + R-5.2: lit element registration ----

  @Test
  public void shellBundleRegistersComposeFoundationElements() {
    String index = readSource("j2cl/lit/src/index.js");
    assertTrue(
        "shell bundle must import wavy-composer (R-5.1)",
        index.contains("./elements/wavy-composer.js"));
    assertTrue(
        "shell bundle must import wavy-format-toolbar (R-5.2)",
        index.contains("./elements/wavy-format-toolbar.js"));
    assertTrue(
        "shell bundle must import wavy-link-modal (H.17 / H.18)",
        index.contains("./elements/wavy-link-modal.js"));
    assertTrue(
        "shell bundle must import wavy-tags-row (I.3-I.5)",
        index.contains("./elements/wavy-tags-row.js"));
    assertTrue(
        "shell bundle must import wavy-wave-root-reply-trigger (J.1)",
        index.contains("./elements/wavy-wave-root-reply-trigger.js"));
  }

  // ---- R-5.1 step 1: inline composer mount ----

  @Test
  public void composeViewListensForReplyAndEditAndCancelEvents() {
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "view must listen for wave-blip-reply-requested",
        view.contains("\"wave-blip-reply-requested\""));
    assertTrue(
        "view must listen for wave-blip-edit-requested",
        view.contains("\"wave-blip-edit-requested\""));
    assertTrue(
        "view must listen for wave-root-reply-requested (J.1)",
        view.contains("\"wave-root-reply-requested\""));
    assertTrue(
        "view must listen for wavy-composer-cancelled",
        view.contains("\"wavy-composer-cancelled\""));
  }

  // ---- R-5.1 step 2: caret-survival + draft persistence ----

  @Test
  public void wavyComposerPreservesContenteditableAcrossRenders() {
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must lazily ensure the body element rather than rebuilding it on render",
        composer.contains("_ensureBody"));
    assertTrue(
        "wavy-composer must skip textContent overwrite when the body owns selection",
        composer.contains("_bodyOwnsSelection"));
    assertTrue(
        "wavy-composer must guard against overwriting rich content with plain draft",
        composer.contains("_bodyHasRichContent"));
  }

  // ---- R-5.1 H.22 hint strip + H.23 save indicator + H.24 plugin slot ----

  @Test
  public void wavyComposerRendersHintStripAndSaveIndicatorAndComposeExtensionSlot() {
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must render the H.22 hint strip with data-hint-strip",
        composer.contains("data-hint-strip"));
    assertTrue(
        "wavy-composer must render the H.23 save indicator with data-save-indicator",
        composer.contains("data-save-indicator"));
    assertTrue(
        "wavy-composer must forward the compose-extension slot (H.24)",
        composer.contains("name=\"compose-extension\"")
            || composer.contains("\"compose-extension\""));
  }

  // ---- R-5.2 step 1 + 8: floating toolbar + F-2.S6 gating ----

  @Test
  public void wavyFormatToolbarFloatsAndDailyRichEditActionsAreListed() {
    String toolbar = readSource("j2cl/lit/src/elements/wavy-format-toolbar.js");
    assertTrue(
        "wavy-format-toolbar must use position: fixed for floating placement",
        toolbar.contains("position: fixed"));
    assertTrue(
        "wavy-format-toolbar must reposition on selectionchange via the rAF coalescer",
        toolbar.contains("requestAnimationFrame"));
    assertTrue(
        "wavy-format-toolbar must list bold/italic/underline/strikethrough actions",
        toolbar.contains("\"bold\"")
            && toolbar.contains("\"italic\"")
            && toolbar.contains("\"underline\"")
            && toolbar.contains("\"strikethrough\""));
    assertTrue(
        "wavy-format-toolbar must list bulleted-list + numbered-list actions",
        toolbar.contains("\"unordered-list\"")
            && toolbar.contains("\"ordered-list\""));
    assertTrue(
        "wavy-format-toolbar must list link + unlink actions",
        toolbar.contains("\"link\"") && toolbar.contains("\"unlink\""));
  }

  // ---- Helper ----

  private static String readSource(String relativePath) {
    Path repoRoot = locateRepoRoot();
    Path target = repoRoot.resolve(relativePath);
    if (!Files.exists(target)) {
      throw new AssertionError("Source file missing: " + target);
    }
    try {
      return new String(Files.readAllBytes(target), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("Unable to read " + target + ": " + e.getMessage(), e);
    }
  }

  private static Path locateRepoRoot() {
    Path cur = Paths.get("").toAbsolutePath();
    for (int i = 0; i < 10 && cur != null; i++) {
      if (Files.exists(cur.resolve("j2cl/lit/src/elements"))) {
        return cur;
      }
      cur = cur.getParent();
    }
    throw new AssertionError("Unable to locate repo root with j2cl/lit/src/elements");
  }
}

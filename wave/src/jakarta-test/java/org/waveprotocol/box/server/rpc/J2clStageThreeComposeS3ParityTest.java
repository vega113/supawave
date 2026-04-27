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
 * F-3 slice 3 (#1038, R-5.5) — per-row parity assertions for reactions.
 * Each test pins a load-bearing contract that the S3 plan asserts must
 * hold end-to-end against {@code ?view=j2cl-root} vs {@code ?view=gwt}.
 *
 * <p>Source-level pins are the right level of assertion for the F-3
 * lane: the live DOM round-trip is exercised by the lit web-test-runner
 * suite (which runs against a real Chromium and the actual Lit
 * elements); this fixture pins the production wire contract — element
 * registration, Java listener wiring, delta-factory op shapes — so any
 * future regression that silently removes a reaction affordance fails
 * here even if no live test happens to cover it.
 */
public final class J2clStageThreeComposeS3ParityTest {

  // ---- R-5.5 reactions: lit primitives ----

  @Test
  public void reactionRowAdoptsWavyVioletTokensForActiveChips() {
    // R-5.5 step 13 — active-pressed chips paint with the F-0
    // signal-violet token contract. The lit element is restyled in
    // S3 (was F-1 grey/blue defaults).
    String row = readSource("j2cl/lit/src/elements/reaction-row.js");
    assertTrue(
        "reaction-row must declare --wavy-signal-violet for active chips",
        row.contains("--wavy-signal-violet"));
    assertTrue(
        "reaction-row must declare --wavy-signal-violet-soft for the fill",
        row.contains("--wavy-signal-violet-soft"));
    assertTrue(
        "reaction-row must declare --wavy-radius-pill for the chip shape",
        row.contains("--wavy-radius-pill"));
  }

  @Test
  public void reactionRowFiresPulseRingOnCountUp() {
    // R-5.5 step 14 — a chip whose count increased via live-update
    // fires a one-shot `data-live-pulse` attribute that the CSS uses
    // to drive the F-0 pulse-ring animation.
    String row = readSource("j2cl/lit/src/elements/reaction-row.js");
    assertTrue(
        "reaction-row must reference --wavy-pulse-ring for the live-update pulse",
        row.contains("--wavy-pulse-ring"));
    assertTrue(
        "reaction-row must set data-live-pulse on chips whose count increased",
        row.contains("data-live-pulse"));
  }

  @Test
  public void reactionPickerPopoverRegistersWithExpectedApiSurface() {
    // R-5.5 step 3: the picker is mounted by the J2CL view; the F-1
    // primitive's contract (open, blip-id, emojis, focus-target-id) is
    // unchanged and emits `reaction-pick` `{blipId, emoji}`.
    String picker = readSource("j2cl/lit/src/elements/reaction-picker-popover.js");
    assertTrue(
        "reaction-picker-popover must define the open + emojis properties",
        picker.contains("open: { type: Boolean")
            && picker.contains("emojis: { type: Array }"));
    assertTrue(
        "reaction-picker-popover must dispatch reaction-pick with blipId+emoji",
        picker.contains("\"reaction-pick\""));
  }

  // ---- R-5.5 reactions: J2CL view + controller ----

  @Test
  public void composeViewListensForReactionEvents() {
    // R-5.5 step 5: the Java view subscribes to reaction events at
    // body level so the row + picker mounted under any blip route
    // through the same listener pipeline.
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2clComposeSurfaceView must listen for reaction-pick",
        view.contains("\"reaction-pick\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for reaction-toggle",
        view.contains("\"reaction-toggle\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for reaction-add",
        view.contains("\"reaction-add\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for reaction-inspect",
        view.contains("\"reaction-inspect\""));
  }

  @Test
  public void composeViewExposesGwtParityEmojiSet() {
    // R-5.5 step 4: the default emoji list matches GWT's
    // ReactionPickerPopup.EMOJI_OPTIONS. Pin the literal set at the
    // source so any divergence surfaces in CI rather than during
    // user-facing parity reviews.
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2clComposeSurfaceView must declare DEFAULT_REACTION_EMOJIS",
        view.contains("DEFAULT_REACTION_EMOJIS"));
    // Just check the emoji glyphs (UTF-8 encoded in the source file).
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include thumbs-up (👍)",
        view.contains("\"👍\""));
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include red-heart (❤️)",
        view.contains("\"❤️\""));
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include face-with-tears-of-joy (😂)",
        view.contains("\"😂\""));
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include party-popper (🎉)",
        view.contains("\"🎉\""));
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include face-with-open-mouth (😮)",
        view.contains("\"😮\""));
    assertTrue(
        "DEFAULT_REACTION_EMOJIS must include eyes (👀)",
        view.contains("\"👀\""));
  }

  @Test
  public void controllerListenerExposesReactionToggle() {
    // R-5.5 step 5 + step 15: the Listener interface gains
    // onReactionToggled; the DeltaFactory exposes
    // createReactionToggleRequest. Pin them at source so future
    // refactors that replace the listener pattern continue to expose
    // these hooks.
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "Listener must declare onReactionToggled",
        controller.contains("onReactionToggled"));
    assertTrue(
        "DeltaFactory must declare createReactionToggleRequest",
        controller.contains("createReactionToggleRequest"));
    assertTrue(
        "controller must expose setReactionSnapshots",
        controller.contains("setReactionSnapshots"));
  }

  @Test
  public void reactionToggleFactoryEmitsReactDocumentOps() {
    // R-5.5 step 6 (the load-bearing requirement): reaction toggle
    // round-trips through the model via a delta against the
    // `react+<blipId>` data document carrying element-tree ops.
    String factory =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/"
                + "J2clRichContentDeltaFactory.java");
    assertTrue(
        "J2clRichContentDeltaFactory must expose reactionToggleRequest",
        factory.contains("reactionToggleRequest"));
    assertTrue(
        "factory must target the react+ document id",
        factory.contains("\"react+\""));
    assertTrue(
        "factory must emit a <reactions> root element start",
        factory.contains("appendElementStartReactions"));
    assertTrue(
        "factory must emit a <reaction> element start with emoji attr",
        factory.contains("appendElementStartReaction"));
    assertTrue(
        "factory must emit a <user> element start with the address attribute",
        factory.contains("appendElementStartUser"));
    assertTrue(
        "factory must emit retain ops via field 5 for non-empty roots",
        factory.contains("appendRetain"));
    assertTrue(
        "factory must emit delete-element-start ops for toggle-off",
        factory.contains("appendDeleteElementStart"));
  }

  @Test
  public void controllerTelemetryEventNameIsComposeReactionToggled() {
    // R-5.5 step 15: telemetry event name `compose.reaction_toggled`
    // with `{state, outcome}` fields. Pin at controller source so
    // renames cannot break dashboards silently.
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "controller must record compose.reaction_toggled telemetry",
        controller.contains("compose.reaction_toggled"));
    assertTrue(
        "compose.reaction_toggled must carry the state field",
        controller.contains(".field(\"state\""));
    assertTrue(
        "compose.reaction_toggled must carry the outcome field",
        controller.contains(".field(\"outcome\""));
  }

  // ---- R-5.5 reactions: read renderer + selected-wave view wiring ----

  @Test
  public void readRendererMountsReactionRowPerBlip() {
    // R-5.5 step 1: every <wave-blip> in the read surface mounts a
    // <reaction-row> in the existing F-2 `reactions` slot.
    String renderer =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/read/"
                + "J2clReadSurfaceDomRenderer.java");
    assertTrue(
        "renderer must create a reaction-row element per blip",
        renderer.contains("\"reaction-row\""));
    assertTrue(
        "renderer must attach the row in the wave-blip reactions slot",
        renderer.contains("slot=\"reactions\"")
            || renderer.contains("\"slot\", \"reactions\""));
    assertTrue(
        "renderer must expose ReactionBinder seam",
        renderer.contains("ReactionBinder"));
  }

  @Test
  public void selectedWaveViewPublishesReactionStatePerRender() {
    // R-5.5 step 11: the selected-wave view forwards each render's
    // per-blip reaction state to BOTH the read renderer (chips) and
    // the compose controller (snapshot used to compute toggle
    // direction). Pinned at the view source so a refactor of the
    // model -> view -> controller plumbing keeps both consumers
    // synchronised.
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/search/"
                + "J2clSelectedWaveView.java");
    assertTrue(
        "selected-wave view must expose ReactionSnapshotPublisher seam",
        view.contains("ReactionSnapshotPublisher"));
    assertTrue(
        "selected-wave view must call setReactionBinder on each render",
        view.contains("setReactionBinder"));
    assertTrue(
        "selected-wave view must accept the signed-in user address for "
            + "active-pressed chip state",
        view.contains("setCurrentUserAddress"));
  }

  @Test
  public void interactionBlipModelComputesActiveForSignedInUser() {
    // R-5.5 step 13: the interaction blip model exposes a
    // user-aware reactionSummariesForUser helper so the chip
    // aria-pressed state reflects "this is your own reaction."
    String model =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/"
                + "J2clInteractionBlipModel.java");
    assertTrue(
        "J2clInteractionBlipModel must expose reactionSummariesForUser",
        model.contains("reactionSummariesForUser"));
  }

  @Test
  public void rootShellWiresReactionStateAcrossViewAndController() {
    // R-5.5 step 11: the root shell installs the publisher hook on
    // the view + the address-listener hook on the controller so the
    // chip render and the toggle delta agree on what's currently in
    // the document.
    String shell =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/root/"
                + "J2clRootShellController.java");
    assertTrue(
        "root shell must install setReactionSnapshotPublisher",
        shell.contains("setReactionSnapshotPublisher"));
    assertTrue(
        "root shell must install setCurrentUserAddressListener",
        shell.contains("setCurrentUserAddressListener"));
  }

  // ---- Bundle entry-point registration ----

  @Test
  public void shellBundleRegistersReactionPrimitives() {
    String index = readSource("j2cl/lit/src/index.js");
    assertTrue(
        "shell bundle must import reaction-row",
        index.contains("./elements/reaction-row.js"));
    assertTrue(
        "shell bundle must import reaction-picker-popover",
        index.contains("./elements/reaction-picker-popover.js"));
    assertTrue(
        "shell bundle must import reaction-authors-popover",
        index.contains("./elements/reaction-authors-popover.js"));
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

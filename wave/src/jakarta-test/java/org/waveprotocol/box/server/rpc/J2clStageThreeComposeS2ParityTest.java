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
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * F-3 slice 2 (#1038, R-5.3 + R-5.4) — per-row parity assertions for
 * mentions + tasks. Each test pins a load-bearing contract that the
 * S2 plan asserts must hold end-to-end against {@code ?view=j2cl-root}
 * vs {@code ?view=gwt}.
 *
 * <p>Source-level pins are the right level of assertion for the F-3 lane:
 * the live DOM round-trip is exercised by the lit web-test-runner suite
 * (which runs against a real Chromium and the actual Lit elements);
 * this fixture pins the production wire contract — element registration,
 * Java listener wiring, delta-factory annotation shapes — so any future
 * regression that silently removes a mention or task affordance fails
 * here even if no live test happens to cover it.
 */
public final class J2clStageThreeComposeS2ParityTest {

  // ---- R-5.3 mentions ----

  @Test
  public void mentionPopoverElementIsRegisteredAndConsumedByComposer() {
    // R-5.3 step 1: typing `@` in the composer body opens the
    // F-1-era <mention-suggestion-popover>. <wavy-composer> must
    // import the popover element (not just the toolbar) so the
    // mention surface mounts inline.
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must import mention-suggestion-popover (R-5.3 step 1)",
        composer.contains("./mention-suggestion-popover.js"));
    assertTrue(
        "wavy-composer must render the mention popover element when triggered",
        composer.contains("mention-suggestion-popover"));
  }

  @Test
  public void atTriggerHasStartOfLineGuardAgainstEmailAddresses() {
    // R-5.3 risk #1 + step 1: typing `@` inside an email address
    // (e.g. `alice@example.com`) MUST NOT pop the suggestion sheet.
    // The composer's _updateMentionPopoverFromCaret applies a
    // start-of-line/whitespace guard. Pin the guard at the source.
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must guard the @-trigger so email addresses do not pop",
        Pattern.compile("\\\\s|\\.test|whitespace").matcher(composer).find()
            && composer.contains("lastIndexOf(\"@\")"));
  }

  @Test
  public void localeAwareFilterUsesToLocaleLowerCase() {
    // R-5.3 step 7: locale-aware case folding under lang=ru / lang=tr
    // requires `toLocaleLowerCase(<lang>)`, not the default
    // `toLowerCase()`. Pin the call at the source so a future refactor
    // cannot silently lose Cyrillic / Turkish parity.
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must use toLocaleLowerCase for locale-aware mention filter",
        composer.contains("toLocaleLowerCase"));
    assertTrue(
        "wavy-composer must read document.documentElement.lang for the locale tag",
        composer.contains("documentElement")
            && composer.contains(".lang"));
  }

  @Test
  public void mentionChipUsesVioletTokenAndDataMentionId() {
    // R-5.3 step 4 + step 8: chip carries data-mention-id and the
    // violet signal token (matches F-0 design contract).
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must insert chip span with class wavy-mention-chip",
        composer.contains("wavy-mention-chip"));
    assertTrue(
        "wavy-composer must set data-mention-id on the chip span",
        composer.contains("data-mention-id"));
    assertTrue(
        "wavy-composer must use --wavy-signal-violet for the chip",
        composer.contains("--wavy-signal-violet"));
  }

  @Test
  public void mentionPickEmitsCustomEventForController() {
    // R-5.3 step 4 + telemetry step 10: the lit composer dispatches a
    // wavy-composer-mention-picked CustomEvent the Java compose view
    // listens for and routes to onMentionPicked.
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must dispatch wavy-composer-mention-picked",
        composer.contains("wavy-composer-mention-picked"));
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2clComposeSurfaceView must listen for wavy-composer-mention-picked",
        view.contains("\"wavy-composer-mention-picked\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for wavy-composer-mention-abandoned",
        view.contains("\"wavy-composer-mention-abandoned\""));
  }

  @Test
  public void mentionInsertFactoryEmitsLinkManualAnnotation() {
    // R-5.3 step 4 (the load-bearing requirement): mention chip
    // round-trips through the model as a link/manual annotation.
    String factory =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/"
                + "J2clRichContentDeltaFactory.java");
    assertTrue(
        "J2clRichContentDeltaFactory must expose appendMentionInsert",
        factory.contains("appendMentionInsert"));
    assertTrue(
        "appendMentionInsert must emit the link/manual annotation key",
        factory.contains("\"link/manual\""));
  }

  // ---- R-5.4 tasks ----

  @Test
  public void waveBlipMountsTaskAffordanceInMetadataSlot() {
    // R-5.4 step 1: <wave-blip> must mount <wavy-task-affordance>
    // inside the existing metadata slot next to the toolbar.
    String waveBlip = readSource("j2cl/lit/src/elements/wave-blip.js");
    assertTrue(
        "wave-blip must import wavy-task-affordance",
        waveBlip.contains("./wavy-task-affordance.js"));
    assertTrue(
        "wave-blip must render <wavy-task-affordance> with data-blip-id",
        waveBlip.contains("<wavy-task-affordance"));
    assertTrue(
        "wave-blip must reflect data-task-completed (R-5.4 step 4)",
        waveBlip.contains("data-task-completed"));
  }

  @Test
  public void taskAffordanceEmitsToggleAndMetadataEvents() {
    // R-5.4 step 2 + step 5: clicking the toggle emits
    // wave-blip-task-toggled; submitting the metadata popover emits
    // wave-blip-task-metadata-changed.
    String affordance = readSource("j2cl/lit/src/elements/wavy-task-affordance.js");
    assertTrue(
        "wavy-task-affordance must dispatch wave-blip-task-toggled",
        affordance.contains("\"wave-blip-task-toggled\""));
    assertTrue(
        "wavy-task-affordance must dispatch wave-blip-task-metadata-changed",
        affordance.contains("\"wave-blip-task-metadata-changed\""));
    assertTrue(
        "wavy-task-affordance must reflect role=checkbox + aria-checked (R-5.4 step 7)",
        affordance.contains("role=\"checkbox\"")
            && affordance.contains("aria-checked"));
    assertTrue(
        "wavy-task-affordance must announce via aria-live=polite (R-5.4 step 7)",
        affordance.contains("aria-live=\"polite\""));
  }

  @Test
  public void composeSurfaceViewListensForTaskEvents() {
    // R-5.4 step 2 + step 5: the Java compose view subscribes to the
    // task-toggle + task-metadata-changed events on document.body and
    // routes to the controller's listener methods.
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2clComposeSurfaceView must listen for wave-blip-task-toggled",
        view.contains("\"wave-blip-task-toggled\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for wave-blip-task-metadata-changed",
        view.contains("\"wave-blip-task-metadata-changed\""));
  }

  @Test
  public void taskToggleFactoryEmitsTaskDoneAnnotation() {
    // R-5.4 step 3 (the load-bearing requirement): task toggle
    // round-trips through the model via the task/done annotation.
    String factory =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/"
                + "J2clRichContentDeltaFactory.java");
    assertTrue(
        "J2clRichContentDeltaFactory must expose taskToggleRequest",
        factory.contains("taskToggleRequest"));
    assertTrue(
        "task toggle must emit the task/done annotation key",
        factory.contains("\"task/done\""));
    assertTrue(
        "J2clRichContentDeltaFactory must expose taskMetadataRequest with task/assignee + task/dueTs",
        factory.contains("taskMetadataRequest")
            && factory.contains("\"task/assignee\"")
            && factory.contains("\"task/dueTs\""));
  }

  @Test
  public void completedTaskBlipAppliesQuietTextAndStrikethrough() {
    // R-5.4 step 4: completed task styling uses --wavy-text-quiet and
    // text-decoration: line-through on the body wrapper.
    String waveBlip = readSource("j2cl/lit/src/elements/wave-blip.js");
    assertTrue(
        "wave-blip must apply --wavy-text-quiet on completed tasks",
        waveBlip.contains("--wavy-text-quiet"));
    assertTrue(
        "wave-blip must apply line-through on completed tasks",
        Pattern.compile("text-decoration\\s*:\\s*line-through").matcher(waveBlip).find());
  }

  @Test
  public void formatToolbarShipsInsertTaskAction() {
    // R-5.4 step 6 (H.20): the floating format toolbar exposes the
    // insert-task action with id=insert-task and label="Insert task".
    String toolbar = readSource("j2cl/lit/src/elements/wavy-format-toolbar.js");
    assertTrue(
        "wavy-format-toolbar must include the insert-task action",
        toolbar.contains("\"insert-task\""));
    assertTrue(
        "wavy-format-toolbar must label insert-task 'Insert task' (H.20)",
        toolbar.contains("label: \"Insert task\""));
  }

  @Test
  public void composerInsertsTaskListAtCaretOnInsertTaskAction() {
    // R-5.4 step 6: the composer body inserts a <ul class="wavy-task-list">
    // wrapping a disabled <input type="checkbox"> at the caret.
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must listen for the insert-task toolbar action",
        composer.contains("\"insert-task\""));
    assertTrue(
        "wavy-composer must build a wavy-task-list element on insert-task",
        composer.contains("wavy-task-list"));
    assertTrue(
        "wavy-composer must mark the inline checkbox as display-only via disabled=true",
        composer.contains("checkbox.disabled = true"));
  }

  @Test
  public void taskFiltersDiscoverableInSearchHelp() {
    // R-5.4 step 8 (C.13-C.15 search-filter discoverability): the
    // search-help modal advertises the task-search filter tokens via
    // data-filter-token attributes the parity fixture queries.
    String help = readSource("j2cl/lit/src/elements/wavy-search-help.js");
    assertTrue(
        "wavy-search-help _example must set data-filter-token (R-5.4 step 8)",
        help.contains("data-filter-token=${query}"));
    assertTrue(
        "tasks:user@domain row must carry data-filter-token directly (R-5.4 step 8)",
        help.contains("data-filter-token=\"tasks:user@domain\""));
    // The example chips for tasks:all + tasks:me are already in the
    // modal body (asserted by the existing wavy-search-help.test.js
    // ADVERTISED_TOKENS list); this fixture confirms the F-2.S3 ship
    // is preserved.
    assertTrue(
        "wavy-search-help must still list tasks:all (C.13)",
        help.contains("tasks:all"));
    assertTrue(
        "wavy-search-help must still list tasks:me (C.14)",
        help.contains("tasks:me"));
  }

  // ---- F-3.S2 controller-level wiring (extends the F-3.S1 listener) ----

  @Test
  public void controllerListenerExposesNewMentionAndTaskCallbacks() {
    // R-5.3 + R-5.4: the Listener interface gains onMentionPicked,
    // onMentionAbandoned, onTaskToggled, onTaskMetadataChanged. Pin
    // them at the source so future refactors that replace the listener
    // pattern continue to expose these hooks.
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "Listener must declare onMentionPicked",
        controller.contains("onMentionPicked"));
    assertTrue(
        "Listener must declare onMentionAbandoned",
        controller.contains("onMentionAbandoned"));
    assertTrue(
        "Listener must declare onTaskToggled",
        controller.contains("onTaskToggled"));
    assertTrue(
        "Listener must declare onTaskMetadataChanged",
        controller.contains("onTaskMetadataChanged"));
    assertTrue(
        "DeltaFactory must declare createTaskToggleRequest",
        controller.contains("createTaskToggleRequest"));
    assertTrue(
        "DeltaFactory must declare createTaskMetadataRequest",
        controller.contains("createTaskMetadataRequest"));
  }

  @Test
  public void taskTelemetryEventNameIsComposeTaskToggled() {
    // R-5.4 step 11: telemetry event name `compose.task_toggled` with
    // {state} field. Pin at controller source so renames cannot break
    // dashboards silently.
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "controller must record compose.task_toggled telemetry",
        controller.contains("compose.task_toggled"));
    assertTrue(
        "task_toggled telemetry must carry the state field",
        controller.contains(".field(\"state\""));
  }

  @Test
  public void mentionTelemetryEventNamesArePinned() {
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "controller must record compose.mention_picked telemetry",
        controller.contains("compose.mention_picked"));
    assertTrue(
        "controller must record compose.mention_abandoned telemetry",
        controller.contains("compose.mention_abandoned"));
  }

  // ---- Bundle entry-point registration ----

  @Test
  public void shellBundleRegistersTaskAffordance() {
    String index = readSource("j2cl/lit/src/index.js");
    assertTrue(
        "shell bundle must import wavy-task-affordance (F-3.S2)",
        index.contains("./elements/wavy-task-affordance.js"));
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

  /**
   * Walk up from CWD to find the repo root (the directory containing
   * `j2cl/lit/src/elements`). Mirrors the discovery scheme used by
   * {@code HtmlRendererJ2clRootShellIntegrationTest#readSourceFile}.
   */
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

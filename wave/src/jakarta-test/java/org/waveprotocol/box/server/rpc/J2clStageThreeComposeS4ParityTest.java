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
 * F-3 slice 4 (#1038, R-5.6 + R-5.7) — per-row parity assertions for
 * attachments (drag-drop, paste-image, H.19 paperclip, F.6 Delete) and
 * the daily rich-edit DocOp round-trip (lists, block quotes, inline
 * links).
 *
 * <p>Source-level pins are the right level of assertion for the F-3
 * lane: live DOM round-trip is exercised by the lit web-test-runner
 * suite and by {@code J2clRichContentDeltaFactoryTest}; this fixture
 * pins the production wire contract — element registration, Java
 * listener wiring, delta-factory op shapes, telemetry event names — so
 * any future regression that silently removes an attachment affordance
 * fails here even if no live test happens to cover it.
 */
public final class J2clStageThreeComposeS4ParityTest {

  // ---- R-5.6 step 1: drag-drop on the composer body ----

  @Test
  public void wavyComposerHandlesDragOverDropAndDragLeave() {
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must register a dragover handler",
        composer.contains("dragover"));
    assertTrue(
        "wavy-composer must register a drop handler",
        composer.contains("\"drop\"") || composer.contains("'drop'"));
    assertTrue(
        "wavy-composer must register a dragleave handler",
        composer.contains("dragleave"));
    assertTrue(
        "wavy-composer must reflect drop-target state via data-droptarget",
        composer.contains("data-droptarget"));
    assertTrue(
        "wavy-composer must dispatch wavy-composer-attachment-dropped on drop",
        composer.contains("\"wavy-composer-attachment-dropped\""));
  }

  @Test
  public void composeViewListensForAttachmentDroppedEvent() {
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "view must listen for wavy-composer-attachment-dropped",
        view.contains("\"wavy-composer-attachment-dropped\""));
    assertTrue(
        "view must call Listener.onDroppedFiles",
        view.contains("onDroppedFiles"));
  }

  // ---- R-5.6 step 2: paste-image (already shipped — pinned here) ----

  @Test
  public void wavyComposerHandlesPasteImage() {
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must register a paste handler that detects image clipboard items",
        composer.contains("\"paste\"") && composer.contains("clipboardData"));
    assertTrue(
        "wavy-composer must dispatch attachment-paste-image",
        composer.contains("\"attachment-paste-image\""));
  }

  @Test
  public void composeViewListensForPasteImage() {
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "view must listen for attachment-paste-image",
        view.contains("\"attachment-paste-image\""));
    assertTrue(
        "view must call Listener.onPastedImage",
        view.contains("onPastedImage"));
  }

  // ---- R-5.6 step 3: H.19 paperclip in the wavy-format-toolbar ----

  @Test
  public void wavyFormatToolbarShipsH19AttachmentInsertButton() {
    String toolbar = readSource("j2cl/lit/src/elements/wavy-format-toolbar.js");
    assertTrue(
        "wavy-format-toolbar must list attachment-insert as a daily-rich-edit action",
        toolbar.contains("\"attachment-insert\""));
    assertTrue(
        "wavy-format-toolbar must surface the paperclip with the Attach file label",
        toolbar.contains("\"Attach file\""));
  }

  @Test
  public void controllerRoutesAttachmentInsertActionToOpenPicker() {
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "controller must expose handleAttachmentToolbarAction",
        controller.contains("handleAttachmentToolbarAction"));
    assertTrue(
        "controller must call view.openAttachmentPicker for attachment-insert",
        controller.contains("openAttachmentPicker"));
  }

  // ---- R-5.6 step 5: inline render at originating blip (existing F-1 wiring) ----

  @Test
  public void shellBundleRegistersAttachmentRenderingPrimitives() {
    String index = readSource("j2cl/lit/src/index.js");
    assertTrue(
        "shell bundle must import compose-attachment-picker (R-5.6 step 3)",
        index.contains("./elements/compose-attachment-picker.js"));
    assertTrue(
        "shell bundle must import compose-attachment-card (R-5.6 step 5)",
        index.contains("./elements/compose-attachment-card.js"));
  }

  @Test
  public void readRendererMountsAttachmentSurfaceForInlineDisplay() {
    String renderer =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/read/"
                + "J2clReadSurfaceDomRenderer.java");
    // The renderer wires per-blip attachment markup (existing F-1
    // surface) — the data-j2cl-read-attachment marker plus the
    // attachment id are the contract the parity matrix asserts.
    assertTrue(
        "renderer must render the per-blip attachment surface",
        renderer.contains("renderAttachment"));
    assertTrue(
        "renderer must tag each attachment surface with data-j2cl-read-attachment",
        renderer.contains("data-j2cl-read-attachment"));
    assertTrue(
        "renderer must surface the attachment id for downstream wiring",
        renderer.contains("data-attachment-id"));
  }

  // ---- R-5.6 step 6: failure surface ----

  @Test
  public void composeAttachmentPickerSurfacesFailureViaAlertRole() {
    String picker = readSource("j2cl/lit/src/elements/compose-attachment-picker.js");
    assertTrue(
        "picker must render role=alert for upload errors",
        picker.contains("role=\"alert\"") || picker.contains("\"alert\""));
    assertTrue(
        "picker must expose data-state=attachment-error-state",
        picker.contains("attachment-error-state"));
  }

  // ---- R-5.6 step 7: F.6 Delete gateway wiring (S4 ships the actual RPC) ----

  @Test
  public void waveBlipToolbarShipsDeleteButtonEmittingDedicatedEvent() {
    String toolbar = readSource("j2cl/lit/src/elements/wave-blip-toolbar.js");
    assertTrue(
        "wave-blip-toolbar must render data-toolbar-action=\"delete\"",
        toolbar.contains("data-toolbar-action=\"delete\""));
    assertTrue(
        "wave-blip-toolbar must emit wave-blip-toolbar-delete",
        toolbar.contains("\"wave-blip-toolbar-delete\""));
  }

  @Test
  public void waveBlipReEmitsPublicDeleteRequestedEvent() {
    String waveBlip = readSource("j2cl/lit/src/elements/wave-blip.js");
    assertTrue(
        "wave-blip must listen for wave-blip-toolbar-delete",
        waveBlip.contains("@wave-blip-toolbar-delete")
            || waveBlip.contains("wave-blip-toolbar-delete"));
    assertTrue(
        "wave-blip must re-emit wave-blip-delete-requested",
        waveBlip.contains("\"wave-blip-delete-requested\""));
  }

  @Test
  public void wavyConfirmDialogReplacesWindowConfirm() {
    String dialog = readSource("j2cl/lit/src/elements/wavy-confirm-dialog.js");
    assertTrue(
        "wavy-confirm-dialog must listen for wavy-confirm-requested",
        dialog.contains("\"wavy-confirm-requested\""));
    assertTrue(
        "wavy-confirm-dialog must dispatch wavy-confirm-resolved",
        dialog.contains("\"wavy-confirm-resolved\""));
    String view =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "view must dispatch wavy-confirm-requested when the user clicks Delete",
        view.contains("\"wavy-confirm-requested\""));
    assertTrue(
        "view must NOT use Window.confirm (forbidden per project memory rule)",
        !view.contains("DomGlobal.window.confirm")
            && !view.contains("DomGlobal.confirm")
            && !view.contains("window.confirm"));
  }

  @Test
  public void controllerWiresDeleteBlipRequestedThroughDeltaFactoryAndGateway() {
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "Listener must declare onDeleteBlipRequested",
        controller.contains("onDeleteBlipRequested"));
    assertTrue(
        "DeltaFactory must declare createBlipDeleteRequest",
        controller.contains("createBlipDeleteRequest"));
    assertTrue(
        "controller must record compose.blip_deleted telemetry",
        controller.contains("\"compose.blip_deleted\""));
  }

  @Test
  public void richContentDeltaFactoryEmitsTombstoneDeletedAnnotation() {
    String factory =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/"
                + "J2clRichContentDeltaFactory.java");
    assertTrue(
        "factory must expose blipDeleteRequest",
        factory.contains("blipDeleteRequest"));
    assertTrue(
        "delete delta must carry tombstone/deleted annotation key",
        factory.contains("tombstone/deleted"));
  }

  // ---- R-5.6 telemetry ----

  @Test
  public void controllerEmitsAttachmentDroppedTelemetry() {
    String controller =
        readSource(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceController.java");
    assertTrue(
        "controller must record compose.attachment_dropped",
        controller.contains("\"compose.attachment_dropped\""));
  }

  // ---- R-5.7: DocOp round-trip for daily rich-edit affordances ----

  @Test
  public void wavyComposerSerializesListsBlockquotesAndLinksAsAnnotatedComponents() {
    String composer = readSource("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "serializeRichComponents must emit list/unordered annotations from <ul><li>",
        composer.contains("\"list/unordered\""));
    assertTrue(
        "serializeRichComponents must emit list/ordered annotations from <ol><li>",
        composer.contains("\"list/ordered\""));
    assertTrue(
        "serializeRichComponents must emit block/quote annotations from <blockquote>",
        composer.contains("\"block/quote\""));
    assertTrue(
        "serializeRichComponents must emit link/manual annotations from <a href=...>",
        composer.contains("\"link/manual\""));
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

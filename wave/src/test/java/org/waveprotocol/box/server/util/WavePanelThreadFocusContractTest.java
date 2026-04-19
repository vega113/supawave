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

public final class WavePanelThreadFocusContractTest extends TestCase {

  public void testCollapseControllerUsesWidthAwareFocusDecision() throws Exception {
    String controller = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/CollapseController.java");

    assertTrue(controller.contains("collapser.maybeFocusThreadFromToggle(thread, wavePanel.getContents())"));
  }

  public void testKeepFocusInViewRequestsFocusedThreadOnEditStart() throws Exception {
    String stageThree = read("wave/src/main/java/org/waveprotocol/wave/client/StageThree.java");
    String keepFocus = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/KeepFocusInView.java");

    assertTrue(stageThree.contains("KeepFocusInView.install(edit, panel, stageTwo.getStageOne().getCollapser())"));
    assertTrue(keepFocus.contains("collapser.maybeFocusThreadForEditing(blipUi, waveUi.getContents())"));
  }

  public void testThreadNavigationPresenterUsesThreadFocusPolicy() throws Exception {
    String presenter = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/ThreadNavigationPresenter.java");

    assertTrue(presenter.contains("ThreadFocusPolicy.shouldUseFocusedThread("));
    assertTrue(presenter.contains("measureAvailableContentWidth("));
    assertTrue(presenter.contains("public boolean shouldUseFocusedThread("));
  }

  public void testThreadNavigationPresenterReconcilesOnInitAndResize() throws Exception {
    String presenter = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/ThreadNavigationPresenter.java");
    String builder = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/ThreadNavigationBuilder.java");
    String stageThree = read("wave/src/main/java/org/waveprotocol/wave/client/StageThree.java");

    assertTrue(presenter.contains("public void reconcileFocusedThreadLayout(TopConversationView waveUi)"));
    assertTrue(builder.contains("navigator.reconcileFocusedThreadLayout(panel.getContents())"));
    assertTrue(stageThree.contains("stageTwo.getStageOne().getThreadNavigator().reconcileFocusedThreadLayout(panel.getContents())"));
    assertTrue(presenter.contains("enterAncestorThreadChain("));
    assertTrue(presenter.contains("collectAncestorThreadChain("));
    assertTrue(presenter.contains("if (isNavigated()) {"));
    assertTrue(presenter.contains("exitToRoot();"));
  }

  public void testHistoryTokensPreserveWavePathForWebClientConsumers() throws Exception {
    String presenter = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/ThreadNavigationPresenter.java");
    String historyListener = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/HistoryChangeListener.java");
    String webClient = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    assertTrue(presenter.contains("ThreadNavigationHistory.appendMetadata("));
    assertTrue(historyListener.contains("ThreadNavigationHistory.stripMetadata(encodedToken)"));
    assertTrue(webClient.contains("ThreadNavigationHistory.stripMetadata(savedToken)"));
    assertTrue(webClient.contains("ThreadNavigationHistory.stripMetadata(encodedToken)"));
  }

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}

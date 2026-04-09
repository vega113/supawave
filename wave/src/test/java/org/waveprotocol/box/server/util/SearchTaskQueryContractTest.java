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

public final class SearchTaskQueryContractTest extends TestCase {

  public void testTasksToolbarDefaultsToAllAssignedTasks() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");

    assertTrue(javaSource.contains("searchUi.getSearch().setQuery(\"tasks:me\");"));
    assertFalse(javaSource.contains("searchUi.getSearch().setQuery(\"tasks:me unread:true\");"));
  }

  public void testSearchHelpKeepsTasksAndUnreadExamplesSeparate() throws Exception {
    String uiXml = read(
        "wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml");

    assertTrue(uiXml.contains(">tasks:me<"));
    assertTrue(uiXml.contains(">unread:true<"));
    assertFalse(uiXml.contains("tasks:me unread:true"));
  }

  public void testUnreadTrackerStillPollsExplicitUnreadTaskQuery() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/TaskUnreadTracker.java");

    assertTrue(javaSource.contains("searchService.search(\"tasks:me unread:true\""));
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}

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

public final class CompactInlineBlipCssContractTest extends TestCase {

  public void testDesktopCompactInlineBlipsStackAvatarAboveContent() throws Exception {
    String css = readBlipCss();

    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.meta \\{"
            + "\\s*display: flex;"
            + "\\s*flex-direction: column;"
            + "\\s*padding-left: 0\\.75em;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.avatar \\{"
            + "\\s*float: none;"
            + "\\s*margin-left: 0;"
            + "\\s*margin-bottom: 0\\.35em;"
            + "\\s*align-self: flex-start;"
            + "\\s*\\}.*");
  }

  public void testMobileCompactInlineBlipsKeepStackedAvatarLayout() throws Exception {
    String css = readBlipCss();

    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.meta \\{"
            + "\\s*display: flex;"
            + "\\s*flex-direction: column;"
            + "\\s*padding-left: 0\\.5em;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.avatar \\{"
            + "\\s*float: none;"
            + "\\s*margin-left: 0;"
            + "\\s*margin-bottom: 0\\.25em;"
            + "\\s*align-self: flex-start;"
            + "\\s*\\}.*");
  }

  private String readBlipCss() throws IOException {
    return Files.readString(
        Path.of(
            "wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css"),
        StandardCharsets.UTF_8);
  }

  private static void assertMatches(String text, String regex) {
    assertTrue("Expected CSS to match regex: " + regex, text.matches(regex));
  }
}

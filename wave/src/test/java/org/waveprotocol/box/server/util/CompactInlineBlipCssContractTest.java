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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompactInlineBlipCssContractTest extends TestCase {

  public void testDesktopCompactInlineBlipsKeepAvatarAndMetabarOnSameRow() throws Exception {
    String css = readBlipCss();

    String meta = extractRuleBody(css,
        "\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.meta");
    assertContainsAll(meta,
        "display: grid;",
        "grid-template-columns: auto minmax(0, 1fr);",
        "column-gap: 0.35em;",
        "row-gap: 0.25em;",
        "padding-left: 0.75em;");
    assertDoesNotContain(meta, "display: flex;");
    assertDoesNotContain(meta, "flex-direction: column;");

    String avatar = extractRuleBody(css,
        "\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.avatar");
    assertContainsAll(avatar,
        "float: none;",
        "grid-column: 1;",
        "grid-row: 1;",
        "margin-left: 0;",
        "margin-bottom: 0;");
    assertDoesNotContain(avatar, "align-self: flex-start;");

    String metabar = extractRuleBody(css,
        "\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.metabar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.metabar,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.metabar");
    assertContainsAll(metabar,
        "grid-column: 2;",
        "grid-row: 1;",
        "min-width: 0;",
        "margin-left: 0;");

    String contentContainer = extractRuleBody(css,
        "\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.contentContainer,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.contentContainer,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.contentContainer");
    assertContainsAll(contentContainer, "grid-column: 1 / -1;");
    assertDoesNotContain(contentContainer, "grid-row: 2;");

    String draftNotification = extractRuleBody(css,
        "\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.draftActive-notification,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.draftActive-notification,"
            + "\\s*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.draftActive-notification");
    assertContainsAll(draftNotification, "grid-column: 1 / -1;");

    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth=\"0\"\\] \\.avatar \\{"
            + "\\s*width: 24px;"
            + "\\s*height: 24px;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth=\"1\"\\] \\.avatar \\{"
            + "\\s*width: 22px;"
            + "\\s*height: 22px;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth=\"2\"\\] \\.avatar \\{"
            + "\\s*width: 20px;"
            + "\\s*height: 20px;"
            + "\\s*\\}.*");
  }

  public void testMobileCompactInlineBlipsKeepAvatarAndMetabarOnSameRow() throws Exception {
    String css = readBlipCss();

    String meta = extractRuleBody(css,
        "\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.meta,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.meta");
    assertContainsAll(meta,
        "display: grid;",
        "grid-template-columns: auto minmax(0, 1fr);",
        "column-gap: 0.3em;",
        "row-gap: 0.2em;",
        "padding-left: 0.5em;");
    assertDoesNotContain(meta, "display: flex;");
    assertDoesNotContain(meta, "flex-direction: column;");

    String avatar = extractRuleBody(css,
        "\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.avatar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.avatar");
    assertContainsAll(avatar,
        "float: none;",
        "grid-column: 1;",
        "grid-row: 1;",
        "margin-left: 0;",
        "margin-bottom: 0;");
    assertDoesNotContain(avatar, "align-self: flex-start;");

    String metabar = extractRuleBody(css,
        "\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.metabar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.metabar,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.metabar");
    assertContainsAll(metabar,
        "grid-column: 2;",
        "grid-row: 1;",
        "min-width: 0;",
        "margin-left: 0;");

    String contentContainer = extractRuleBody(css,
        "\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.contentContainer,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.contentContainer,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.contentContainer");
    assertContainsAll(contentContainer, "grid-column: 1 / -1;");
    assertDoesNotContain(contentContainer, "grid-row: 2;");

    String draftNotification = extractRuleBody(css,
        "\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.draftActive-notification,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.draftActive-notification,"
            + "\\s*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.draftActive-notification");
    assertContainsAll(draftNotification, "grid-column: 1 / -1;");

    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth=\"0\"\\] \\.avatar \\{"
            + "\\s*width: 22px;"
            + "\\s*height: 22px;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth=\"1\"\\] \\.avatar \\{"
            + "\\s*width: 20px;"
            + "\\s*height: 20px;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth=\"2\"\\] \\.avatar \\{"
            + "\\s*width: 20px;"
            + "\\s*height: 20px;"
            + "\\s*\\}.*");
  }

  public void testDepth3PlusResetRestoresBaselineLayout() throws Exception {
    String css = readBlipCss();

    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth\\]:not\\(\\[data-depth=\"0\"\\]\\)"
            + ":not\\(\\[data-depth=\"1\"\\]\\):not\\(\\[data-depth=\"2\"\\]\\) \\.meta \\{"
            + "\\s*display: block;"
            + "\\s*padding-left: 3\\.75em;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips \\[data-depth\\]:not\\(\\[data-depth=\"0\"\\]\\)"
            + ":not\\(\\[data-depth=\"1\"\\]\\):not\\(\\[data-depth=\"2\"\\]\\) \\.avatar \\{"
            + "\\s*float: left;"
            + "\\s*width: 28px;"
            + "\\s*height: 28px;"
            + "\\s*margin-left: -3em;"
            + "\\s*margin-bottom: 0;"
            + "\\s*align-self: auto;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth\\]:not\\(\\[data-depth=\"0\"\\]\\)"
            + ":not\\(\\[data-depth=\"1\"\\]\\):not\\(\\[data-depth=\"2\"\\]\\) \\.meta \\{"
            + "\\s*display: block;"
            + "\\s*padding-left: 3\\.75em;"
            + "\\s*\\}.*");
    assertMatches(css,
        "(?s).*\\.compact-inline-blips-mobile \\[data-depth\\]:not\\(\\[data-depth=\"0\"\\]\\)"
            + ":not\\(\\[data-depth=\"1\"\\]\\):not\\(\\[data-depth=\"2\"\\]\\) \\.avatar \\{"
            + "\\s*float: left;"
            + "\\s*width: 28px;"
            + "\\s*height: 28px;"
            + "\\s*margin-left: -3em;"
            + "\\s*margin-bottom: 0;"
            + "\\s*align-self: auto;"
            + "\\s*\\}.*");
  }

  public void testBaseRootBlipLayoutRemainsUnchanged() throws Exception {
    String css = readBlipCss();

    String meta = extractRuleBody(css, "(?m)^\\.meta");
    assertContainsAll(meta, "padding: 0.5em 0.75em 0em 3.75em;");
    assertDoesNotContain(meta, "display: grid;");

    String avatar = extractRuleBody(css, "(?m)^\\.avatar");
    assertContainsAll(avatar,
        "height: 28px;",
        "width: 28px;",
        "float: left;",
        "margin-left: -3em;");
    assertDoesNotContain(avatar, "grid-column: 1;");
  }

  private String readBlipCss() throws IOException {
    String resourcePath = "org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull("Missing resource: " + resourcePath, in);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String extractRuleBody(String css, String selectorRegex) {
    Matcher matcher =
        Pattern.compile("(?s)" + selectorRegex + "\\s*\\{(.*?)\\}").matcher(css);
    assertTrue("Missing CSS rule for selector regex: " + selectorRegex, matcher.find());
    return matcher.group(1);
  }

  private static void assertContainsAll(String text, String... needles) {
    for (String needle : needles) {
      assertTrue("Expected text to contain: " + needle + "\nActual:\n" + text,
          text.contains(needle));
    }
  }

  private static void assertDoesNotContain(String text, String needle) {
    assertFalse("Did not expect text to contain: " + needle + "\nActual:\n" + text,
        text.contains(needle));
  }

  private static void assertMatches(String text, String regex) {
    assertTrue("Expected CSS to match regex: " + regex, text.matches(regex));
  }
}

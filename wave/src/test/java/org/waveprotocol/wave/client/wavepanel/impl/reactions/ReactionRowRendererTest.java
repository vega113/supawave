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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import junit.framework.TestCase;

import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.model.conversation.ReactionDocument;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for rendering reaction rows into safe HTML.
 */
public final class ReactionRowRendererTest extends TestCase {

  public void testRenderIncludesChipCountsAndAddButtonWhenEditable() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip1",
        Arrays.asList(
            new ReactionDocument.Reaction("thumbs_up", Arrays.asList("alice@example.com", "bob@example.com")),
            new ReactionDocument.Reaction("tada", Collections.singletonList("carol@example.com"))),
        "alice@example.com",
        true);

    String output = html.asString();
    assertTrue(output.contains("thumbs_up"));
    assertTrue(output.contains("2"));
    assertTrue(output.contains("tada"));
    assertTrue(output.contains("data-reaction-add=\"true\""));
    assertTrue(output.contains("data-reaction-active=\"true\""));
  }

  public void testRenderUsesReactionSpecificAddIconWhenEditable() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip4",
        Collections.<ReactionDocument.Reaction>emptyList(),
        "alice@example.com",
        true);

    String output = html.asString();
    assertTrue(output.contains("data-reaction-add=\"true\""));
    assertTrue(output.contains("aria-label=\"Add reaction\""));
    assertTrue(output.contains("waveReactionAddIcon"));
    assertFalse(output.contains(">+</button>"));
  }

  public void testRenderPlacesAddButtonBeforeReactionChipsWhenEditable() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip5",
        Collections.singletonList(
            new ReactionDocument.Reaction("thumbs_up", Collections.singletonList("alice@example.com"))),
        "alice@example.com",
        true);

    String output = html.asString();
    int addIndex = output.indexOf("data-reaction-add=\"true\"");
    int chipIndex = output.indexOf("data-reaction-emoji=\"thumbs_up\"");
    assertTrue(addIndex >= 0);
    assertTrue(chipIndex >= 0);
    assertTrue(addIndex < chipIndex);
  }

  public void testRenderWrapsEmojiAndCountForBaselineStyling() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip6",
        Collections.singletonList(
            new ReactionDocument.Reaction("thumbs_up",
                Arrays.asList("alice@example.com", "bob@example.com"))),
        "alice@example.com",
        true);

    String output = html.asString();
    assertTrue(output.contains("<span class=\"waveReactionEmoji\">thumbs_up</span>"));
    assertTrue(output.contains("</span> <span class=\"waveReactionCount\">2</span>"));
  }

  public void testRenderOmitsAddButtonWhenReadOnly() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip2",
        Collections.singletonList(
            new ReactionDocument.Reaction("eyes", Collections.singletonList("alice@example.com"))),
        null,
        false);

    String output = html.asString();
    assertTrue(output.contains("eyes"));
    assertFalse(output.contains("data-reaction-add=\"true\""));
  }

  public void testRenderSkipsNullReactionEntries() {
    SafeHtml html = ReactionRowRenderer.render(
        "b+blip3",
        Arrays.asList(
            null,
            new ReactionDocument.Reaction("fire", Collections.singletonList("alice@example.com"))),
        "alice@example.com",
        true);

    String output = html.asString();
    assertTrue(output.contains("fire"));
    assertTrue(output.contains("data-reaction-active=\"true\""));
  }
}

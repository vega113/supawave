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

package org.waveprotocol.wave.model.conversation;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.document.MutableDocumentImpl;
import org.waveprotocol.wave.model.document.raw.impl.Element;
import org.waveprotocol.wave.model.document.raw.impl.Node;
import org.waveprotocol.wave.model.document.raw.impl.Text;
import org.waveprotocol.wave.model.document.util.DocProviders;
import org.waveprotocol.wave.model.id.IdUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the per-blip reactions data document helper.
 */
public final class ReactionDocumentTest extends TestCase {

  private ReactionDocument<Node, Element, Text> document;

  @Override
  protected void setUp() throws Exception {
    document = createDocument("");
  }

  private static ReactionDocument<Node, Element, Text> createDocument(String xmlContent) {
    MutableDocumentImpl<Node, Element, Text> baseDocument = DocProviders.MOJO.parse(xmlContent);
    return new ReactionDocument<Node, Element, Text>(baseDocument);
  }

  public void testReactionDataDocumentIdUsesReactPrefix() {
    assertEquals("react+b+abc", IdUtil.reactionDataDocumentId("b+abc"));
  }

  public void testIsReactionDataDocumentRecognizesPrefixedIds() {
    assertTrue(IdUtil.isReactionDataDocument("react+b+abc"));
    assertFalse(IdUtil.isReactionDataDocument("b+abc"));
    assertFalse(IdUtil.isReactionDataDocument("tags"));
  }

  public void testReactionTargetBlipIdReturnsOriginalBlipId() {
    assertEquals("b+abc", IdUtil.reactionTargetBlipId("react+b+abc"));
    assertNull(IdUtil.reactionTargetBlipId("tags"));
  }

  public void testGetReactionsReturnsEmptyForEmptyDocument() {
    assertTrue(document.getReactions().isEmpty());
  }

  public void testToggleReactionAddsFirstReactionForUser() {
    document.toggleReaction("alice@example.com", "thumbs_up");

    List<ReactionDocument.Reaction> reactions = document.getReactions();
    assertEquals(1, reactions.size());
    assertEquals("thumbs_up", reactions.get(0).getEmoji());
    assertEquals(Arrays.asList("alice@example.com"), reactions.get(0).getAddresses());
  }

  public void testToggleReactionRemovesSameReactionForUser() {
    document.toggleReaction("alice@example.com", "thumbs_up");
    document.toggleReaction("alice@example.com", "thumbs_up");

    assertTrue(document.getReactions().isEmpty());
  }

  public void testToggleReactionMovesUserToDifferentEmoji() {
    document.toggleReaction("alice@example.com", "thumbs_up");
    document.toggleReaction("alice@example.com", "tada");

    List<ReactionDocument.Reaction> reactions = document.getReactions();
    assertEquals(1, reactions.size());
    assertEquals("tada", reactions.get(0).getEmoji());
    assertEquals(Arrays.asList("alice@example.com"), reactions.get(0).getAddresses());
  }

  public void testToggleReactionPreservesOtherUsersOnOldEmoji() {
    document.toggleReaction("alice@example.com", "thumbs_up");
    document.toggleReaction("bob@example.com", "thumbs_up");
    document.toggleReaction("alice@example.com", "tada");

    List<ReactionDocument.Reaction> reactions = document.getReactions();
    assertEquals(2, reactions.size());
    assertEquals("thumbs_up", reactions.get(0).getEmoji());
    assertEquals(Arrays.asList("bob@example.com"), reactions.get(0).getAddresses());
    assertEquals("tada", reactions.get(1).getEmoji());
    assertEquals(Arrays.asList("alice@example.com"), reactions.get(1).getAddresses());
  }

  public void testGetReactionsMergesConcurrentDuplicateEmojiElements() {
    // Simulate OT leaving two <reaction emoji="thumbs_up"> siblings when two participants
    // add the same emoji on a previously empty blip concurrently.
    ReactionDocument<Node, Element, Text> doc = createDocument(
        "<reactions>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"alice@example.com\"/></reaction>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"bob@example.com\"/></reaction>"
        + "</reactions>");

    List<ReactionDocument.Reaction> reactions = doc.getReactions();
    assertEquals("Duplicate emoji elements must be merged into one", 1, reactions.size());
    assertEquals("thumbs_up", reactions.get(0).getEmoji());
    List<String> addresses = reactions.get(0).getAddresses();
    assertEquals(2, addresses.size());
    assertTrue(addresses.contains("alice@example.com"));
    assertTrue(addresses.contains("bob@example.com"));
  }

  public void testGetReactionsMergesDedupesAddressesAcrossDuplicateElements() {
    // Same user appears in both duplicate elements — must appear only once after merge.
    ReactionDocument<Node, Element, Text> doc = createDocument(
        "<reactions>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"alice@example.com\"/></reaction>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"alice@example.com\"/></reaction>"
        + "</reactions>");

    List<ReactionDocument.Reaction> reactions = doc.getReactions();
    assertEquals(1, reactions.size());
    assertEquals(1, reactions.get(0).getAddresses().size());
    assertEquals("alice@example.com", reactions.get(0).getAddresses().get(0));
  }

  public void testToggleReactionPurgesDuplicateEntriesFromConcurrentSessions() {
    // Simulate concurrent writes: alice appears in two reaction elements.
    ReactionDocument<Node, Element, Text> doc = createDocument(
        "<reactions>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"alice@example.com\"/></reaction>"
        + "<reaction emoji=\"tada\"><user address=\"alice@example.com\"/></reaction>"
        + "</reactions>");

    // Toggle alice off thumbs_up — should purge all occurrences and leave an empty document.
    doc.toggleReaction("alice@example.com", "thumbs_up");

    assertTrue("All duplicate entries must be purged on toggle", doc.getReactions().isEmpty());
  }

  public void testToggleReactionSwitchPurgesAllDuplicates() {
    // Simulate concurrent writes: alice appears in both thumbs_up and tada.
    ReactionDocument<Node, Element, Text> doc = createDocument(
        "<reactions>"
        + "<reaction emoji=\"thumbs_up\"><user address=\"alice@example.com\"/></reaction>"
        + "<reaction emoji=\"tada\"><user address=\"alice@example.com\"/></reaction>"
        + "</reactions>");

    // Switch alice to heart — must remove from both stale entries.
    doc.toggleReaction("alice@example.com", "heart");

    List<ReactionDocument.Reaction> reactions = doc.getReactions();
    assertEquals(1, reactions.size());
    assertEquals("heart", reactions.get(0).getEmoji());
    assertEquals(Arrays.asList("alice@example.com"), reactions.get(0).getAddresses());
  }
}

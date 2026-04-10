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

import org.waveprotocol.wave.model.document.MutableDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides an integrity-preserving interface for a per-blip reactions data document.
 *
 * <p>Document format:
 * <pre>
 *   &lt;reactions&gt;
 *     &lt;reaction emoji="thumbs_up"&gt;
 *       &lt;user address="alice@example.com"/&gt;
 *     &lt;/reaction&gt;
 *   &lt;/reactions&gt;
 * </pre>
 *
 * @param <N> node type
 * @param <E> element type
 * @param <T> text type
 */
public class ReactionDocument<N, E extends N, T extends N> {

  /**
   * Immutable view of one reaction entry in the document.
   */
  public static final class Reaction {
    private final String emoji;
    private final List<String> addresses;

    public Reaction(String emoji, List<String> addresses) {
      this.emoji = emoji;
      this.addresses = Collections.unmodifiableList(new ArrayList<String>(addresses));
    }

    public String getEmoji() {
      return emoji;
    }

    public List<String> getAddresses() {
      return addresses;
    }
  }

  private static final String ROOT_TAG = "reactions";
  private static final String REACTION_TAG = "reaction";
  private static final String USER_TAG = "user";
  private static final String EMOJI_ATTR = "emoji";
  private static final String ADDRESS_ATTR = "address";

  private final MutableDocument<N, E, T> doc;

  public ReactionDocument(MutableDocument<N, E, T> doc) {
    this.doc = doc;
  }

  /**
   * Reads the reactions from the document, merging any duplicate emoji elements that concurrent
   * OT operations may have produced into a single logical reaction per emoji.
   */
  public List<Reaction> getReactions() {
    E root = getRootElement();
    if (root == null) {
      return Collections.emptyList();
    }

    // LinkedHashMap preserves first-seen insertion order across duplicate elements.
    LinkedHashMap<String, List<String>> byEmoji = new LinkedHashMap<String, List<String>>();
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement == null || !REACTION_TAG.equals(doc.getTagName(reactionElement))) {
        continue;
      }

      String emoji = doc.getAttribute(reactionElement, EMOJI_ATTR);
      if (emoji == null || emoji.isEmpty()) {
        continue;
      }

      List<String> addresses = byEmoji.get(emoji);
      if (addresses == null) {
        addresses = new ArrayList<String>();
        byEmoji.put(emoji, addresses);
      }

      for (N child = doc.getFirstChild(reactionElement);
           child != null;
           child = doc.getNextSibling(child)) {
        E userElement = doc.asElement(child);
        if (userElement == null || !USER_TAG.equals(doc.getTagName(userElement))) {
          continue;
        }
        String address = doc.getAttribute(userElement, ADDRESS_ATTR);
        if (address != null && !address.isEmpty() && !addresses.contains(address)) {
          addresses.add(address);
        }
      }
    }

    List<Reaction> reactions = new ArrayList<Reaction>();
    for (Map.Entry<String, List<String>> entry : byEmoji.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        reactions.add(new Reaction(entry.getKey(), entry.getValue()));
      }
    }
    return Collections.unmodifiableList(reactions);
  }

  /**
   * Toggles a user's reaction on this document.
   *
   * <p>Each user may have at most one active reaction in the document. All occurrences of the
   * user's address across all reaction elements are purged before the new state is written, so
   * concurrent multi-session edits cannot leave stale duplicate entries.
   */
  public void toggleReaction(String address, String emoji) {
    if (address == null || address.isEmpty() || emoji == null || emoji.isEmpty()) {
      return;
    }

    E root = ensureRootElement();

    // Check ALL duplicate emoji elements for the address, not just the first one.
    // Concurrent OT can produce multiple <reaction emoji="..."> siblings for the same emoji;
    // checking only findReactionByEmoji (first match) would miss addresses in later duplicates
    // and incorrectly re-add the user instead of toggling them off.
    boolean wasInTarget = containsAddressInAnyDuplicate(root, emoji, address);

    // Purge address from every reaction element to maintain the one-reaction-per-user invariant,
    // even when concurrent sessions have created duplicate entries.
    purgeAddressFromAllReactions(root, address);

    if (wasInTarget) {
      return; // Toggle off: address has been removed from the target reaction.
    }

    // Re-locate the target after purge (it may have been deleted if it became empty).
    E targetReaction = findReactionByEmoji(root, emoji);
    if (targetReaction == null) {
      targetReaction = createReaction(root, emoji);
    }
    createUser(targetReaction, address);
  }

  private E getRootElement() {
    N child = doc.getFirstChild(doc.getDocumentElement());
    E element = child != null ? doc.asElement(child) : null;
    if (element == null) {
      return null;
    }
    return ROOT_TAG.equals(doc.getTagName(element)) ? element : null;
  }

  private E ensureRootElement() {
    E root = getRootElement();
    if (root != null) {
      return root;
    }

    doc.emptyElement(doc.getDocumentElement());
    return doc.createChildElement(doc.getDocumentElement(), ROOT_TAG,
        Collections.<String, String>emptyMap());
  }

  private E findReactionByEmoji(E root, String emoji) {
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement != null
          && REACTION_TAG.equals(doc.getTagName(reactionElement))
          && emoji.equals(doc.getAttribute(reactionElement, EMOJI_ATTR))) {
        return reactionElement;
      }
    }
    return null;
  }

  private boolean containsAddressInAnyDuplicate(E root, String emoji, String address) {
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement != null
          && REACTION_TAG.equals(doc.getTagName(reactionElement))
          && emoji.equals(doc.getAttribute(reactionElement, EMOJI_ATTR))
          && containsAddress(reactionElement, address)) {
        return true;
      }
    }
    return false;
  }

  private void purgeAddressFromAllReactions(E root, String address) {
    List<E> reactionsToDelete = new ArrayList<E>();
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement == null || !REACTION_TAG.equals(doc.getTagName(reactionElement))) {
        continue;
      }
      if (containsAddress(reactionElement, address)) {
        removeAddressFromReaction(reactionElement, address);
        if (doc.getFirstChild(reactionElement) == null) {
          reactionsToDelete.add(reactionElement);
        }
      }
    }
    for (E reaction : reactionsToDelete) {
      doc.deleteNode(reaction);
    }
  }

  private boolean containsAddress(E reactionElement, String address) {
    return findUserElement(reactionElement, address) != null;
  }

  private E findUserElement(E reactionElement, String address) {
    for (N child = doc.getFirstChild(reactionElement);
         child != null;
         child = doc.getNextSibling(child)) {
      E userElement = doc.asElement(child);
      if (userElement != null
          && USER_TAG.equals(doc.getTagName(userElement))
          && address.equals(doc.getAttribute(userElement, ADDRESS_ATTR))) {
        return userElement;
      }
    }
    return null;
  }

  private E createReaction(E root, String emoji) {
    return doc.createChildElement(root, REACTION_TAG, Collections.singletonMap(EMOJI_ATTR, emoji));
  }

  private E createUser(E reactionElement, String address) {
    return doc.createChildElement(reactionElement, USER_TAG,
        Collections.singletonMap(ADDRESS_ATTR, address));
  }

  private void removeAddressFromReaction(E reactionElement, String address) {
    List<E> toRemove = new ArrayList<E>();
    for (N child = doc.getFirstChild(reactionElement);
         child != null;
         child = doc.getNextSibling(child)) {
      E userElement = doc.asElement(child);
      if (userElement != null
          && USER_TAG.equals(doc.getTagName(userElement))
          && address.equals(doc.getAttribute(userElement, ADDRESS_ATTR))) {
        toRemove.add(userElement);
      }
    }
    for (E userElement : toRemove) {
      doc.deleteNode(userElement);
    }
  }

}

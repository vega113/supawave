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
   * Reads the ordered reactions from the document.
   */
  public List<Reaction> getReactions() {
    E root = getRootElement();
    if (root == null) {
      return Collections.emptyList();
    }

    List<Reaction> reactions = new ArrayList<Reaction>();
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement == null || !REACTION_TAG.equals(doc.getTagName(reactionElement))) {
        continue;
      }

      String emoji = doc.getAttribute(reactionElement, EMOJI_ATTR);
      if (emoji == null || emoji.isEmpty()) {
        continue;
      }

      List<String> addresses = new ArrayList<String>();
      for (N child = doc.getFirstChild(reactionElement);
           child != null;
           child = doc.getNextSibling(child)) {
        E userElement = doc.asElement(child);
        if (userElement == null || !USER_TAG.equals(doc.getTagName(userElement))) {
          continue;
        }
        String address = doc.getAttribute(userElement, ADDRESS_ATTR);
        if (address != null && !address.isEmpty()) {
          addresses.add(address);
        }
      }

      if (!addresses.isEmpty()) {
        reactions.add(new Reaction(emoji, addresses));
      }
    }

    return Collections.unmodifiableList(reactions);
  }

  /**
   * Toggles a user's reaction on this document.
   *
   * <p>Each user may have at most one active reaction in the document.
   */
  public void toggleReaction(String address, String emoji) {
    if (address == null || address.isEmpty() || emoji == null || emoji.isEmpty()) {
      return;
    }

    E root = ensureRootElement();
    E currentReaction = findReactionForAddress(root, address);
    E targetReaction = findReactionByEmoji(root, emoji);

    if (currentReaction != null && emoji.equals(doc.getAttribute(currentReaction, EMOJI_ATTR))) {
      removeAddressFromReaction(currentReaction, address);
      deleteReactionIfEmpty(currentReaction);
      return;
    }

    if (currentReaction != null) {
      removeAddressFromReaction(currentReaction, address);
      deleteReactionIfEmpty(currentReaction);
    }

    if (targetReaction == null) {
      targetReaction = createReaction(root, emoji);
    }

    if (!containsAddress(targetReaction, address)) {
      createUser(targetReaction, address);
    }
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

  private E findReactionForAddress(E root, String address) {
    for (N node = doc.getFirstChild(root); node != null; node = doc.getNextSibling(node)) {
      E reactionElement = doc.asElement(node);
      if (reactionElement == null || !REACTION_TAG.equals(doc.getTagName(reactionElement))) {
        continue;
      }
      if (containsAddress(reactionElement, address)) {
        return reactionElement;
      }
    }
    return null;
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
    E userElement = findUserElement(reactionElement, address);
    if (userElement != null) {
      doc.deleteNode(userElement);
    }
  }

  private void deleteReactionIfEmpty(E reactionElement) {
    if (doc.getFirstChild(reactionElement) == null) {
      doc.deleteNode(reactionElement);
    }
  }
}

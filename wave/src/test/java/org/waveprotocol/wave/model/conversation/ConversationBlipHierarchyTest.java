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

import org.mockito.Mockito;

public final class ConversationBlipHierarchyTest extends TestCase {

  public void testContainsReturnsTrueForDescendantBlip() {
    ConversationBlip ancestor = Mockito.mock(ConversationBlip.class);
    ConversationBlip descendant = Mockito.mock(ConversationBlip.class);
    ConversationBlip childBlip = Mockito.mock(ConversationBlip.class);
    ConversationThread descendantThread = Mockito.mock(ConversationThread.class);
    ConversationThread childThread = Mockito.mock(ConversationThread.class);

    Mockito.when(descendant.getThread()).thenReturn(descendantThread);
    Mockito.when(descendantThread.getParentBlip()).thenReturn(childBlip);
    Mockito.when(childBlip.getThread()).thenReturn(childThread);
    Mockito.when(childThread.getParentBlip()).thenReturn(ancestor);

    assertTrue(ConversationBlipHierarchy.contains(ancestor, descendant));
  }

  public void testContainsReturnsTrueForSameBlip() {
    ConversationBlip blip = Mockito.mock(ConversationBlip.class);

    assertTrue(ConversationBlipHierarchy.contains(blip, blip));
  }

  public void testContainsReturnsFalseForNullBlip() {
    ConversationBlip ancestor = Mockito.mock(ConversationBlip.class);

    assertFalse(ConversationBlipHierarchy.contains(ancestor, null));
  }

  public void testContainsReturnsFalseForNullAncestor() {
    ConversationBlip blip = Mockito.mock(ConversationBlip.class);

    assertFalse(ConversationBlipHierarchy.contains(null, blip));
  }

  public void testContainsReturnsFalseForUnrelatedBlip() {
    ConversationBlip ancestor = Mockito.mock(ConversationBlip.class);
    ConversationBlip unrelated = Mockito.mock(ConversationBlip.class);
    ConversationThread unrelatedThread = Mockito.mock(ConversationThread.class);

    Mockito.when(unrelated.getThread()).thenReturn(unrelatedThread);
    Mockito.when(unrelatedThread.getParentBlip()).thenReturn(null);

    assertFalse(ConversationBlipHierarchy.contains(ancestor, unrelated));
  }

  public void testParentOutsideReturnsNullForNullInput() {
    assertNull(ConversationBlipHierarchy.parentOutside(null));
  }

  public void testParentOutsideReturnsContainingBlip() {
    ConversationBlip containingBlip = Mockito.mock(ConversationBlip.class);
    ConversationBlip deletedReply = Mockito.mock(ConversationBlip.class);
    ConversationThread deletedReplyThread = Mockito.mock(ConversationThread.class);

    Mockito.when(deletedReply.getThread()).thenReturn(deletedReplyThread);
    Mockito.when(deletedReplyThread.getParentBlip()).thenReturn(containingBlip);

    assertSame(containingBlip, ConversationBlipHierarchy.parentOutside(deletedReply));
  }
}

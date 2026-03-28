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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DirectMessageUtilTest extends TestCase {

  public void testDirectMessageRequiresExplicitDmTag() {
    Conversation conversation = mock(Conversation.class);

    when(conversation.getParticipantIds()).thenReturn(twoRealParticipants());
    when(conversation.getTags()).thenReturn(new LinkedHashSet<String>());

    assertFalse(DirectMessageUtil.isDirectMessage(conversation));
  }

  public void testDirectMessageUsesExplicitDmTag() {
    Conversation conversation = mock(Conversation.class);
    Set<String> tags = new LinkedHashSet<String>();
    tags.add(Conversation.DM_TAG);

    when(conversation.getParticipantIds()).thenReturn(twoRealParticipants());
    when(conversation.getTags()).thenReturn(tags);

    assertTrue(DirectMessageUtil.isDirectMessage(conversation));
  }

  private Set<ParticipantId> twoRealParticipants() {
    Set<ParticipantId> participants = new LinkedHashSet<ParticipantId>();
    participants.add(ParticipantId.ofUnsafe("alice@example.com"));
    participants.add(ParticipantId.ofUnsafe("bob@example.com"));
    return participants;
  }
}

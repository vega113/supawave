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

public final class ConversationBlipHierarchy {

  private ConversationBlipHierarchy() {
  }

  public static boolean contains(ConversationBlip ancestor, ConversationBlip blip) {
    boolean contains = false;
    ConversationBlip current = blip;
    while (!contains && current != null && ancestor != null) {
      contains = ancestor.equals(current);
      if (!contains) {
        current = parentBlipOf(current);
      }
    }
    return contains;
  }

  private static ConversationBlip parentBlipOf(ConversationBlip blip) {
    ConversationThread thread = blip.getThread();
    ConversationBlip parent = null;
    if (thread != null) {
      parent = thread.getParentBlip();
    }
    return parent;
  }

  public static ConversationBlip parentOutside(ConversationBlip blip) {
    return blip != null ? parentBlipOf(blip) : null;
  }
}

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

package org.waveprotocol.wave.client.account.impl;

import org.waveprotocol.wave.client.account.ContactListener;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;

/**
 * Base class for {@link ContactManager} implementations providing
 * listener management.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public abstract class AbstractContactManager implements ContactManager {

  protected final CopyOnWriteSet<ContactListener> listeners = CopyOnWriteSet.create();

  @Override
  public void addListener(ContactListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ContactListener listener) {
    listeners.remove(listener);
  }

  /** Notifies all registered listeners that contacts have been updated. */
  protected void fireOnUpdated() {
    for (ContactListener listener : listeners) {
      listener.onContactsUpdated();
    }
  }
}

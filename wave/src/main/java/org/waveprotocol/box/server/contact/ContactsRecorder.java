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

package org.waveprotocol.box.server.contact;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for wavelet updates on the {@link WaveBus} and records contact
 * relationships whenever participants are added to or removed from wavelets.
 *
 * <p>Adapted from Wiab.pro (original author: akaplanov@gmail.com).
 * The incubator-wave WaveBus passes {@code ReadableWaveletData} (which already
 * contains the participant set) instead of {@code WaveletName}, so we read
 * participants directly from the wavelet snapshot.
 */
public class ContactsRecorder implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(ContactsRecorder.class);

  private final ContactManager contactManager;
  private final String waveDomain;

  @Inject
  public ContactsRecorder(
      ContactManager contactManager,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain) {
    this.contactManager = contactManager;
    this.waveDomain = waveDomain;
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    // Build a mutable copy of the current participant set. We walk deltas in
    // order so this stays in sync as participants are added/removed.
    HashSet<ParticipantId> participants = Sets.newHashSet(wavelet.getParticipants());
    for (int i = 0; i < deltas.size(); i++) {
      updateContacts(participants, deltas.get(i));
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // Nothing to do on commit.
  }

  /**
   * Processes a single delta, recording contact relationships for any
   * {@code AddParticipant} operations.
   */
  private void updateContacts(Set<ParticipantId> participants, TransformedWaveletDelta delta) {
    ParticipantId sharedParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
    ParticipantId caller = delta.getAuthor();

    if (caller.equals(sharedParticipant)) {
      return;
    }

    for (WaveletOperation op : delta) {
      if (op instanceof AddParticipant) {
        ParticipantId receptor = ((AddParticipant) op).getParticipantId();
        if (!receptor.equals(sharedParticipant)) {
          // Direct call: caller -> receptor
          if (!caller.equals(receptor)) {
            try {
              contactManager.newCall(caller, receptor, delta.getApplicationTimestamp(), true);
            } catch (PersistenceException ex) {
              LOG.severe("Update contact " + caller.getAddress() + "->" + receptor.getAddress(),
                  ex);
            }
          }
          // Indirect call: every existing participant -> receptor
          for (ParticipantId participant : participants) {
            if (!participant.equals(sharedParticipant)
                && !participant.equals(caller)
                && !participant.equals(receptor)) {
              try {
                contactManager.newCall(
                    participant, receptor, delta.getApplicationTimestamp(), false);
              } catch (PersistenceException ex) {
                LOG.severe(
                    "Update contact " + participant.getAddress() + "->" + receptor.getAddress(),
                    ex);
              }
            }
          }
          participants.add(receptor);
        }
      } else if (op instanceof RemoveParticipant) {
        ParticipantId removed = ((RemoveParticipant) op).getParticipantId();
        participants.remove(removed);
      }
    }
  }
}

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

package org.waveprotocol.box.webclient.profile;

import com.google.gwt.core.client.Scheduler;

import org.waveprotocol.box.profile.ProfileResponse;
import org.waveprotocol.box.profile.ProfileResponse.FetchedProfile;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.account.impl.AbstractProfileManager;
import org.waveprotocol.wave.client.account.impl.ProfileImpl;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link ProfileManager} that returns profiles fetched from the server.
 *
 * Batches individual profile requests into a single HTTP call using
 * {@link Scheduler#scheduleDeferred} so that all profiles requested in the
 * same browser event-loop turn are fetched together.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public final class RemoteProfileManagerImpl extends AbstractProfileManager<ProfileImpl> {

  private final static LoggerBundle LOG = new DomLogger("fetchProfiles");
  private final FetchProfilesServiceImpl fetchProfilesService;

  /** Max addresses per GET request to stay well under common URL-length limits. */
  private static final int MAX_ADDRESSES_PER_REQUEST = 50;

  /** Addresses waiting to be fetched in the next batch. */
  private final Set<String> pendingAddresses = new LinkedHashSet<String>();
  private boolean flushScheduled = false;

  /**
   * Deserializes {@link ProfileResponse} and updates the profiles.
   */
  static void deserializeResponseAndUpdateProfiles(RemoteProfileManagerImpl manager,
      ProfileResponse profileResponse) {
    for (FetchedProfile fetchedProfile : profileResponse.getProfiles()) {
      deserializeAndUpdateProfile(manager, fetchedProfile);
    }
  }

  static private void deserializeAndUpdateProfile(RemoteProfileManagerImpl manager,
      FetchedProfile fetchedProfile) {
    ParticipantId participantId = ParticipantId.ofUnsafe(fetchedProfile.getAddress());
    ProfileImpl profile = manager.getProfile(participantId);
    // Profiles already exist for all profiles that have been requested.
    assert profile != null;
    // Updates profiles - this also notifies listeners.
    String firstName = fetchedProfile.hasFirstName() ? fetchedProfile.getFirstName() : null;
    String lastName = fetchedProfile.hasLastName() ? fetchedProfile.getLastName() : null;
    String bio = fetchedProfile.hasBio() ? fetchedProfile.getBio() : null;
    long lastSeenTime = fetchedProfile.hasLastSeenTime() ? fetchedProfile.getLastSeenTime() : 0;
    profile.update(fetchedProfile.getName(), fetchedProfile.getName(), fetchedProfile.getImageUrl(),
        firstName, lastName, bio, lastSeenTime);
  }

  public RemoteProfileManagerImpl() {
    fetchProfilesService = FetchProfilesServiceImpl.create();
  }

  @Override
  public ProfileImpl getProfile(ParticipantId participantId) {
    String address = participantId.getAddress();
    ProfileImpl profile = profiles.get(address);
    if (profile == null) {
      profile = new ProfileImpl(this, participantId);
      profiles.put(address, profile);
      pendingAddresses.add(address);
      scheduleFlush();
    }
    return profile;
  }

  private void scheduleFlush() {
    if (!flushScheduled) {
      flushScheduled = true;
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          flushPendingProfiles();
        }
      });
    }
  }

  private void flushPendingProfiles() {
    flushScheduled = false;
    if (!pendingAddresses.isEmpty()) {
      String[] addresses = pendingAddresses.toArray(new String[0]);
      pendingAddresses.clear();
      LOG.trace().log("Batch fetching " + addresses.length + " profiles");
      for (int i = 0; i < addresses.length; i += MAX_ADDRESSES_PER_REQUEST) {
        final String[] chunk = Arrays.copyOfRange(addresses, i,
            Math.min(i + MAX_ADDRESSES_PER_REQUEST, addresses.length));
        fetchProfilesService.fetch(new FetchProfilesService.Callback() {
          @Override
          public void onFailure(String message) {
            LOG.error().log(message);
            // Requeue stranded addresses so they are retried on the next flush.
            for (String addr : chunk) {
              pendingAddresses.add(addr);
            }
            scheduleFlush();
          }

          @Override
          public void onSuccess(ProfileResponse profileResponse) {
            deserializeResponseAndUpdateProfiles(RemoteProfileManagerImpl.this, profileResponse);
          }
        }, chunk);
      }
    }
  }
}

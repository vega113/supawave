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

package org.waveprotocol.box.server.robots.operations;

import com.google.inject.Inject;
import com.google.wave.api.ParticipantProfile;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ProfilesFetcher} implementation that assigns a default image URL for
 * the user avatar using it's initial and a random color
 *
 * @author vjrj@apache.org (Vicente J. Ruiz Jurado)
 */
public class InitialsProfilesFetcher implements ProfilesFetcher {

  private static final Logger LOG =
      Logger.getLogger(InitialsProfilesFetcher.class.getCanonicalName());

  private final AccountStore accountStore;

  @Inject
  public InitialsProfilesFetcher(AccountStore accountStore) {
    this.accountStore = accountStore;
  }

  /**
   * Returns the avatar URL for the given email address.
   */
  public String getImageUrl(String email) {
    return "/iniavatars/100x100/" + email;
  }

  @Override
  public ParticipantProfile fetchProfile(String address) {
    ParticipantProfile base = ProfilesFetcher.SIMPLE_PROFILES_FETCHER.fetchProfile(address);
    String imageUrl = getImageUrl(address);
    String firstName = null;
    String lastName = null;
    String bio = null;
    long lastSeenTime = 0;
    String displayName = base.getName();

    try {
      ParticipantId participantId = ParticipantId.ofUnsafe(address);
      AccountData account = accountStore.getAccount(participantId);
      if (account != null && account.isHuman()) {
        HumanAccountData human = account.asHuman();
        firstName = human.getFirstName();
        lastName = human.getLastName();
        bio = human.getBio();

        // Build display name from first/last name if available
        if (firstName != null || lastName != null) {
          StringBuilder nameBuilder = new StringBuilder();
          if (firstName != null && !firstName.isEmpty()) {
            nameBuilder.append(firstName);
          }
          if (lastName != null && !lastName.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(' ');
            nameBuilder.append(lastName);
          }
          if (nameBuilder.length() > 0) {
            displayName = nameBuilder.toString();
          }
        }

        // Custom profile image takes priority
        String profileImageId = human.getProfileImageAttachmentId();
        if (profileImageId != null && !profileImageId.isEmpty()) {
          imageUrl = "/userprofile/image/" + address;
        }

        // Only expose last seen if user allows it
        if (human.isShowLastSeen() && human.getLastActivityTime() != 0) {
          lastSeenTime = human.getLastActivityTime();
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to look up extended profile for " + address, e);
    }

    return new ParticipantProfile(
        address, displayName, imageUrl, base.getProfileUrl(),
        firstName, lastName, bio, lastSeenTime);
  }
}

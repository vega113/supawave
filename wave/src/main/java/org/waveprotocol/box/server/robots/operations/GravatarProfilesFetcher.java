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
import org.apache.commons.codec.digest.DigestUtils;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ProfilesFetcher} implementation that generates Gravatar avatar URLs.
 *
 * <p>Email resolution order:
 * <ol>
 *   <li>The user's registered email from {@link HumanAccountData#getEmail()}</li>
 *   <li>Fallback: {@code sha256(address)@wave-avatar.invalid} (ensures every user
 *       gets a unique identicon even without a registered email)</li>
 * </ol>
 *
 * <p>Users can customize their avatar by registering at gravatar.com with the
 * email address associated with their Wave account.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class GravatarProfilesFetcher implements ProfilesFetcher {

  private static final Logger LOG =
      Logger.getLogger(GravatarProfilesFetcher.class.getCanonicalName());

  private static final String GRAVATAR_URL = "https://www.gravatar.com/avatar/";
  private static final String FALLBACK_DOMAIN = "wave-avatar.invalid";

  private final AccountStore accountStore;

  @Inject
  public GravatarProfilesFetcher(AccountStore accountStore) {
    this.accountStore = accountStore;
  }

  /**
   * Resolves the email address to use for Gravatar hashing.
   *
   * <p>Looks up the user's registered email in the account store first.
   * Falls back to {@code sha256(address)@wave-avatar.invalid} if no email is registered.
   *
   * @param address the wave address (e.g. {@code user@example.com})
   * @return the email to hash for Gravatar
   */
  String resolveEmail(String address) {
    try {
      ParticipantId participantId = ParticipantId.ofUnsafe(address);
      AccountData account = accountStore.getAccount(participantId);
      if (account != null && account.isHuman()) {
        HumanAccountData human = account.asHuman();
        String email = human.getEmail();
        if (email != null && !email.isEmpty()) {
          return email;
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to look up account for " + address, e);
    }
    // Fallback: hash the full address to avoid collisions between users with the
    // same local part on different domains, and use a non-routable .invalid TLD
    // so no real Gravatar account can override the identicon.
    String syntheticLocalPart = DigestUtils.sha256Hex(address.trim());
    return syntheticLocalPart + "@" + FALLBACK_DOMAIN;
  }

  /**
   * Returns the Gravatar URL for the given wave address.
   *
   * <p>The email is resolved via {@link #resolveEmail(String)}, then hashed
   * with SHA-256 per Gravatar's current standard. Uses {@code identicon} as the default
   * fallback so that users without a Gravatar still get a unique geometric
   * pattern.
   */
  public String getImageUrl(String address) {
    String email = resolveEmail(address);
    // Gravatar spec: lowercase, trim, then SHA-256-hex.
    String emailHash = DigestUtils.sha256Hex(email.toLowerCase().trim());
    return GRAVATAR_URL + emailHash + "?d=identicon&s=40";
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

        // Custom profile image takes priority over Gravatar.
        // Only use the proxy URL for data: URLs; legacy non-data IDs would cause
        // a redirect loop in ProfileServlet.handleGetProfileImage.
        String profileImageId = human.getProfileImageAttachmentId();
        if (profileImageId != null && profileImageId.trim().startsWith("data:")) {
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

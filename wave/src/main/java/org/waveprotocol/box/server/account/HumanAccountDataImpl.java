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

package org.waveprotocol.box.server.account;

import com.google.common.base.Preconditions;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Human Account. Expected to be expanded when authentication is implemented.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 * @author akaplanov@gmail.com (Andrew kaplanov)
 */
public final class HumanAccountDataImpl implements HumanAccountData {
  private final ParticipantId id;
  private final PasswordDigest passwordDigest;
  private String email;
  private String locale;
  private boolean emailConfirmed = true;
  private List<SearchesItem> searches;
  private List<SocialIdentity> socialIdentities = Collections.emptyList();

  // Admin / role / status fields
  private String role = ROLE_USER;
  private String status = STATUS_ACTIVE;
  private String tier = TIER_FREE;
  private long registrationTime;
  private long lastLoginTime;
  private long lastActivityTime;

  // Profile fields
  private String firstName;
  private String lastName;
  private String bio;
  private String profileImageAttachmentId;
  private boolean showLastSeen = true;

  /**
   * Creates an {@link HumanAccountData} for the given username, with no
   * password.
   *
   * This user will not be able to login using password-bsed authentication.
   *
   * @param id non-null participant id for this account.
   */
  public HumanAccountDataImpl(ParticipantId id) {
    this(id, null);
  }

  /**
   * Creates an {@link HumanAccountData} for the given participant.
   *
   * @param id non-null participant id for this account.
   * @param passwordDigest The user's password digest, or null if the user
   *        should not be authenticated using a password. This is typically
   *        obtained by calling {@code new PasswordDigest(password_chars);}
   */
  public HumanAccountDataImpl(ParticipantId id, PasswordDigest passwordDigest) {
    Preconditions.checkNotNull(id, "Id can not be null");

    this.id = id;
    this.passwordDigest = passwordDigest;
  }

  @Override
  public ParticipantId getId() {
    return id;
  }

  @Override
  public PasswordDigest getPasswordDigest() {
    return passwordDigest;
  }

  @Override
  public boolean isEmailConfirmed() {
    return emailConfirmed;
  }

  @Override
  public void setEmailConfirmed(boolean confirmed) {
    this.emailConfirmed = confirmed;
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public String getLocale() {
    return locale;
  }

  @Override
  public void setLocale(String locale) {
    this.locale = locale;
  }

  @Override
  public List<SearchesItem> getSearches() {
    return searches;
  }

  @Override
  public void setSearches(List<SearchesItem> searches) {
    this.searches = searches;
  }

  @Override
  public List<SocialIdentity> getSocialIdentities() {
    return Collections.unmodifiableList(socialIdentities);
  }

  @Override
  public void setSocialIdentities(List<SocialIdentity> socialIdentities) {
    if (socialIdentities == null || socialIdentities.isEmpty()) {
      this.socialIdentities = Collections.emptyList();
      return;
    }
    this.socialIdentities = Collections.unmodifiableList(new ArrayList<>(socialIdentities));
  }

  @Override
  public void addOrReplaceSocialIdentity(SocialIdentity socialIdentity) {
    Preconditions.checkNotNull(socialIdentity, "Social identity can not be null");
    List<SocialIdentity> updated = new ArrayList<>();
    for (SocialIdentity existing : socialIdentities) {
      if (!existing.matches(socialIdentity.getProvider(), socialIdentity.getSubject())) {
        updated.add(existing);
      }
    }
    updated.add(socialIdentity);
    this.socialIdentities = Collections.unmodifiableList(updated);
  }

  @Override
  public boolean isHuman() {
    return true;
  }

  @Override
  public HumanAccountData asHuman() {
    return this;
  }

  @Override
  public boolean isRobot() {
    return false;
  }

  @Override
  public RobotAccountData asRobot() {
    throw new UnsupportedOperationException("Can't turn a HumanAccount into a RobotAccount");
  }

  // =========================================================================
  // Admin / role / status
  // =========================================================================

  @Override
  public String getRole() {
    return role;
  }

  @Override
  public void setRole(String role) {
    this.role = (role == null) ? ROLE_USER : role;
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void setStatus(String status) {
    this.status = (status == null) ? STATUS_ACTIVE : status;
  }

  @Override
  public String getTier() {
    return tier;
  }

  @Override
  public void setTier(String tier) {
    this.tier = (tier == null) ? TIER_FREE : tier;
  }

  @Override
  public long getRegistrationTime() {
    return registrationTime;
  }

  @Override
  public void setRegistrationTime(long registrationTime) {
    this.registrationTime = registrationTime;
  }

  @Override
  public long getLastLoginTime() {
    return lastLoginTime;
  }

  @Override
  public void setLastLoginTime(long lastLoginTime) {
    this.lastLoginTime = lastLoginTime;
  }

  @Override
  public long getLastActivityTime() {
    return lastActivityTime;
  }

  @Override
  public void setLastActivityTime(long lastActivityTime) {
    this.lastActivityTime = lastActivityTime;
  }

  // =========================================================================
  // Profile fields
  // =========================================================================

  @Override
  public String getFirstName() {
    return firstName;
  }

  @Override
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  @Override
  public String getLastName() {
    return lastName;
  }

  @Override
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  @Override
  public String getBio() {
    return bio;
  }

  @Override
  public void setBio(String bio) {
    this.bio = bio;
  }

  @Override
  public String getProfileImageAttachmentId() {
    return profileImageAttachmentId;
  }

  @Override
  public void setProfileImageAttachmentId(String attachmentId) {
    this.profileImageAttachmentId = attachmentId;
  }

  @Override
  public boolean isShowLastSeen() {
    return showLastSeen;
  }

  @Override
  public void setShowLastSeen(boolean showLastSeen) {
    this.showLastSeen = showLastSeen;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((passwordDigest == null) ? 0 : passwordDigest.hashCode());
    result = prime * result + ((email == null) ? 0 : email.hashCode());
    result = prime * result + ((locale == null) ? 0 : locale.hashCode());
    result = prime * result + ((searches == null) ? 0 : searches.hashCode());
    result = prime * result + ((socialIdentities == null) ? 0 : socialIdentities.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!(obj instanceof HumanAccountDataImpl)) return false;
    HumanAccountDataImpl other = (HumanAccountDataImpl) obj;
    if (id == null) {
      if (other.id != null) return false;
    } else if (!id.equals(other.id)) return false;
    if (passwordDigest == null) {
      if (other.passwordDigest != null) return false;
    } else if (!passwordDigest.equals(other.passwordDigest)) return false;
    if (email == null) {
      if (other.email != null) return false;
    } else if (!email.equals(other.email)) return false;
    if (locale == null) {
      if (other.locale != null) return false;
    } else if (!locale.equals(other.locale)) return false;
    if (searches == null) {
      if (other.searches != null) return false;
    } else if (!searches.equals(other.searches)) return false;
    if (socialIdentities == null) {
      if (other.socialIdentities != null) return false;
    } else if (!socialIdentities.equals(other.socialIdentities)) return false;
    return true;
  }
}

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

package com.google.wave.api;

import java.io.Serializable;

/**
 * ParticipantProfile represents participant information. It contains display
 * name, avatar's URL, and an external URL to view the participant's profile
 * page. This is a data transfer object that is being sent from Robot when Rusty
 * queries a profile. A participant can be the Robot itself, or a user in the
 * Robot's domain.
 *
 * @author mprasetya@google.com (Marcel Prasetya)
 */
public final class ParticipantProfile implements Serializable {

  private final String address;
  private final String name;
  private final String imageUrl;
  private final String profileUrl;
  private final String firstName;
  private final String lastName;
  private final String bio;
  private final long lastSeenTime;

  /**
   * Constructs an empty profile.
   */
  public ParticipantProfile() {
    this("", "", "", "");
  }

  /**
   * Constructs a profile.
   *
   * @param name the name of the participant.
   * @param imageUrl the URL of the participant's avatar.
   * @param profileUrl the URL of the participant's external profile page.
   */
  public ParticipantProfile(String name, String imageUrl, String profileUrl) {
    this("", name, imageUrl, profileUrl);
  }

  /**
   * Constructs a profile.
   *
   * @param address the address of the participant.
   * @param name the name of the participant.
   * @param imageUrl the URL of the participant's avatar.
   * @param profileUrl the URL of the participant's external profile page.
   */
  public ParticipantProfile(String address, String name, String imageUrl,
      String profileUrl) {
    this(address, name, imageUrl, profileUrl, null, null, null, 0);
  }

  /**
   * Constructs a full profile with extended fields.
   *
   * @param address the address of the participant.
   * @param name the display name of the participant.
   * @param imageUrl the URL of the participant's avatar.
   * @param profileUrl the URL of the participant's external profile page.
   * @param firstName the first name, or null.
   * @param lastName the last name, or null.
   * @param bio the bio text, or null.
   * @param lastSeenTime epoch millis of last activity (0 if hidden or unknown).
   */
  public ParticipantProfile(String address, String name, String imageUrl,
      String profileUrl, String firstName, String lastName, String bio, long lastSeenTime) {
    this.address = address;
    this.name = name;
    this.imageUrl = imageUrl;
    this.profileUrl = profileUrl;
    this.firstName = firstName;
    this.lastName = lastName;
    this.bio = bio;
    this.lastSeenTime = lastSeenTime;
  }

  /**
   * @return the address of the participant.
   */
  public String getAddress() {
    return address;
  }

  /**
   * @return the name of the participant.
   */
  public String getName() {
    return name;
  }

  /**
   * @return the URL of the participant's avatar.
   */
  public String getImageUrl() {
    return imageUrl;
  }

  /**
   * @return the URL of the profile page.
   */
  public String getProfileUrl() {
    return profileUrl;
  }

  /** @return the first name, or null if not set. */
  public String getFirstName() {
    return firstName;
  }

  /** @return the last name, or null if not set. */
  public String getLastName() {
    return lastName;
  }

  /** @return the bio, or null if not set. */
  public String getBio() {
    return bio;
  }

  /** @return epoch millis of last activity, or 0 if hidden/unknown. */
  public long getLastSeenTime() {
    return lastSeenTime;
  }
}

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

import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Robot Account implementation.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public final class RobotAccountDataImpl implements RobotAccountData {
  private final ParticipantId id;
  private final String url;
  private final String consumerSecret;
  private final RobotCapabilities capabilities;
  private final boolean isVerified;
  private final long tokenExpirySeconds;
  private final String ownerAddress;
  private final String description;
  private final long createdAtMillis;
  private final long updatedAtMillis;
  private final boolean paused;

  /**
   * Creates a new {@link RobotAccountData} with default token expiry (0 = no expiry).
   *
   *  The capabilities map and version may only be null if the capabilitiesHash
   * is null and vice versa.
   *
   * @param id non-null participant id for this account.
   * @param url non-null url where the robot can be reached.
   * @param consumerSecret non-null consumer secret used in OAuth.
   * @param capabilities {@link RobotCapabilities} representing the robot's
   *        capabilties.xml. May be null.
   * @param isVerified boolean indicating whether this {@link RobotAccountData}
   *        has been verified.
   */
  public RobotAccountDataImpl(ParticipantId id, String url, String consumerSecret,
      RobotCapabilities capabilities, boolean isVerified) {
    this(id, url, consumerSecret, capabilities, isVerified, 0L, null, "", 0L, 0L, false);
  }

  /**
   * Creates a new {@link RobotAccountData} with configurable token expiry.
   *
   * @param id non-null participant id for this account.
   * @param url non-null url where the robot can be reached.
   * @param consumerSecret non-null consumer secret used in OAuth.
   * @param capabilities {@link RobotCapabilities} representing the robot's
   *        capabilties.xml. May be null.
   * @param isVerified boolean indicating whether this {@link RobotAccountData}
   *        has been verified.
   * @param tokenExpirySeconds token expiry in seconds (0 = no expiry).
   */
  public RobotAccountDataImpl(ParticipantId id, String url, String consumerSecret,
      RobotCapabilities capabilities, boolean isVerified, long tokenExpirySeconds) {
    this(id, url, consumerSecret, capabilities, isVerified, tokenExpirySeconds, null,
        "", 0L, 0L, false);
  }

  public RobotAccountDataImpl(ParticipantId id, String url, String consumerSecret,
      RobotCapabilities capabilities, boolean isVerified, long tokenExpirySeconds,
      String ownerAddress) {
    this(id, url, consumerSecret, capabilities, isVerified, tokenExpirySeconds, ownerAddress,
        "", 0L, 0L, false);
  }

  public RobotAccountDataImpl(ParticipantId id, String url, String consumerSecret,
      RobotCapabilities capabilities, boolean isVerified, long tokenExpirySeconds,
      String ownerAddress, String description, long createdAtMillis, long updatedAtMillis,
      boolean paused) {
    Preconditions.checkNotNull(id, "Id can not be null");
    Preconditions.checkNotNull(url, "Url can not be null");
    Preconditions.checkNotNull(consumerSecret, "Consumer secret can not be null");
    Preconditions.checkArgument(!url.endsWith("/"), "Url must not end with /");

    this.id = id;
    this.url = url;
    this.consumerSecret = consumerSecret;
    this.capabilities = capabilities;
    this.isVerified = isVerified;
    this.tokenExpirySeconds = tokenExpirySeconds;
    this.ownerAddress = ownerAddress;
    this.description = description == null ? "" : description;
    this.createdAtMillis = Math.max(0L, createdAtMillis);
    this.updatedAtMillis = Math.max(0L, updatedAtMillis);
    this.paused = paused;
  }

  @Override
  public ParticipantId getId() {
    return id;
  }

  @Override
  public boolean isHuman() {
    return false;
  }

  @Override
  public HumanAccountData asHuman() {
    throw new UnsupportedOperationException("Can't turn a RobotAccount into a HumanAccount");
  }

  @Override
  public boolean isRobot() {
    return true;
  }

  @Override
  public RobotAccountData asRobot() {
    return this;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public String getConsumerSecret() {
    return consumerSecret;
  }

  @Override
  public RobotCapabilities getCapabilities() {
    return capabilities;
  }

  @Override
  public boolean isVerified() {
    return isVerified;
  }

  @Override
  public long getTokenExpirySeconds() {
    return tokenExpirySeconds;
  }

  @Override
  public String getOwnerAddress() {
    return ownerAddress;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public long getCreatedAtMillis() {
    return createdAtMillis;
  }

  @Override
  public long getUpdatedAtMillis() {
    return updatedAtMillis;
  }

  @Override
  public boolean isPaused() {
    return paused;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((capabilities == null) ? 0 : capabilities.hashCode());
    result = prime * result + ((consumerSecret == null) ? 0 : consumerSecret.hashCode());
    result = prime * result + Long.hashCode(createdAtMillis);
    result = prime * result + ((description == null) ? 0 : description.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + (isVerified ? 1231 : 1237);
    result = prime * result + ((ownerAddress == null) ? 0 : ownerAddress.hashCode());
    result = prime * result + (paused ? 1231 : 1237);
    result = prime * result + ((url == null) ? 0 : url.hashCode());
    result = prime * result + Long.hashCode(tokenExpirySeconds);
    result = prime * result + Long.hashCode(updatedAtMillis);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof RobotAccountDataImpl)) {
      return false;
    }
    RobotAccountDataImpl other = (RobotAccountDataImpl) obj;
    if (capabilities == null) {
      if (other.capabilities != null) {
        return false;
      }
    } else if (!capabilities.equals(other.capabilities)) {
      return false;
    }
    if (consumerSecret == null) {
      if (other.consumerSecret != null) {
        return false;
      }
    } else if (!consumerSecret.equals(other.consumerSecret)) {
      return false;
    }
    if (createdAtMillis != other.createdAtMillis) {
      return false;
    }
    if (description == null) {
      if (other.description != null) {
        return false;
      }
    } else if (!description.equals(other.description)) {
      return false;
    }
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    if (isVerified != other.isVerified) {
      return false;
    }
    if (ownerAddress == null) {
      if (other.ownerAddress != null) {
        return false;
      }
    } else if (!ownerAddress.equals(other.ownerAddress)) {
      return false;
    }
    if (paused != other.paused) {
      return false;
    }
    if (url == null) {
      if (other.url != null) {
        return false;
      }
    } else if (!url.equals(other.url)) {
      return false;
    }
    if (tokenExpirySeconds != other.tokenExpirySeconds) {
      return false;
    }
    if (updatedAtMillis != other.updatedAtMillis) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "RobotAccountDataImp" +
        "[id=" + id +
        ",url=" + url +
        ",consumerSecret=<redacted>" +
        ",capabilities=" + capabilities +
        ",isVerified=" + isVerified +
        ",ownerAddress=<redacted>" +
        ",description=" + description +
        ",createdAtMillis=" + createdAtMillis +
        ",updatedAtMillis=" + updatedAtMillis +
        ",paused=" + paused +
        ",tokenExpirySeconds=" + tokenExpirySeconds + "]";
  }
}

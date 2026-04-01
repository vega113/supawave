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

import org.waveprotocol.box.server.robots.RobotCapabilities;

/**
 * Represents an {@link AccountData} belonging to a Robot.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 *
 */
public interface RobotAccountData extends AccountData {

  /**
   * Returns the URL on which the robot can be located. The URL must not end
   * with /.
   */
  String getUrl();

  /**
   * The consumer secret used in OAuth of the Robot. The consumer key is equal
   * to {@link RobotAccountData#getId()}.
   */
  String getConsumerSecret();

  /**
   * The capabilities that have been retrieved from a robot's capabilities.xml
   * file. May be null if they have not been retrieved.
   */
  RobotCapabilities getCapabilities();

  /**
   * Returns true iff the robot ownership has been verified and is ready to be
   * used in the Robot API.
   */
  boolean isVerified();

  /**
   * Returns the token expiry in seconds for tokens issued to this robot.
   * A value of 0 means no expiry (tokens last indefinitely).
   */
  long getTokenExpirySeconds();

  /**
   * Returns the robot description or an empty string for legacy records.
   */
  String getDescription();

  /**
   * Returns the creation time in milliseconds since epoch, or 0 for legacy records.
   */
  long getCreatedAtMillis();

  /**
   * Returns the last update time in milliseconds since epoch, or 0 for legacy records.
   */
  long getUpdatedAtMillis();

  /**
   * Returns whether the robot is paused.
   */
  boolean isPaused();

  /**
   * Returns the human account address that owns this robot, or null when the
   * robot predates ownership tracking.
   */
  String getOwnerAddress();

  /**
   * Returns the token version for JWT revocation. Tokens issued with a version
   * lower than this are considered revoked. Defaults to 0 for legacy records.
   */
  default long getTokenVersion() {
    return 0L;
  }
}

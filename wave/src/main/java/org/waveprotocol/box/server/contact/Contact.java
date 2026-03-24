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

import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Represents a contact relationship between the owning user and another participant.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public interface Contact {

  /** Returns the participant id of the contact (the other user). */
  ParticipantId getParticipantId();

  /** Returns the timestamp (epoch ms) of the most recent interaction with this contact. */
  long getLastContactTime();

  /** Sets the timestamp of the most recent interaction. */
  void setLastContactTime(long time);

  /** Returns the score bonus (in milliseconds) used for ranking contacts. */
  long getScoreBonus();

  /** Sets the score bonus. */
  void setScoreBonus(long bonus);
}

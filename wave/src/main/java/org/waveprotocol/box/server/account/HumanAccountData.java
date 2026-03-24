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

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.authentication.PasswordDigest;

import java.util.List;

/**
 * {@link HumanAccountData} representing an account from a human.
 *
 *  Stores the user's authentication information.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 * @author josephg@gmail.com (Joseph Gentle)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface HumanAccountData extends AccountData {
  /**
   * Gets the user's password digest. The digest can be used to authenticate the
   * user.
   *
   *  This method will return null if password based authentication is disabled
   * for the user, or if no password is set.
   *
   * @return The user's password digest, or null if password authentication is
   *         disabled for the user, or no password is set.
   */
  PasswordDigest getPasswordDigest();

  /**
   * Returns whether the user's email address has been confirmed.
   * Defaults to true for backward compatibility with existing accounts.
   *
   * @return true if the email is confirmed (or confirmation is not required).
   */
  boolean isEmailConfirmed();

  /**
   * Sets the email confirmation status.
   *
   * @param confirmed true if the email has been confirmed.
   */
  void setEmailConfirmed(boolean confirmed);

  /**
   * Returns the user's email address, or null if none is set.
   *
   * @return the email address, or null.
   */
  String getEmail();

  /**
   * Sets the user's email address.
   *
   * @param email the email address, or null to clear.
   */
  void setEmail(String email);

  /**
   * Gets user's locale.
   *
   * @return The user's locale.
   */
  String getLocale();

  /**
   * Sets the user's locale.
   *
   */
  void setLocale(String locale);

  /**
   * Gets the user's stored searches.
   *
   * @return The user's searches, or null if none are set.
   */
  List<SearchesItem> getSearches();

  /**
   * Sets the user's stored searches.
   *
   * @param searches the searches to store
   */
  void setSearches(List<SearchesItem> searches);
}

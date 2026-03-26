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

package org.waveprotocol.wave.client.wavepanel.impl.edit.i18n;

import com.google.gwt.i18n.client.Messages;

/**
 * i18n messages for the contact search dialog.
 */
public interface ContactSearchMessages extends Messages {

  @DefaultMessage("Add participant")
  String dialogTitle();

  @DefaultMessage("Search by name or email...")
  String searchPlaceholder();

  @DefaultMessage("Searching...")
  String searching();

  @DefaultMessage("No matching users found")
  String noResults();

  @DefaultMessage("Type to search your contacts")
  String typeToSearch();

  @DefaultMessage("Last contact: {0}")
  String lastContact(String relativeTime);

  @DefaultMessage("Add participant")
  String addParticipant();

  @DefaultMessage("Add")
  String add();

  @DefaultMessage("Done")
  String done();

  @DefaultMessage("Adding participant...")
  String addingParticipant();

  @DefaultMessage("Failed to search contacts")
  String searchError();

  @DefaultMessage("{0} contacts found")
  String resultsCount(int count);
}

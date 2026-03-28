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

package org.waveprotocol.box.server.persistence.memory;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.crypto.DefaultCertPathStore;
import org.waveprotocol.wave.crypto.SignatureException;
import org.waveprotocol.wave.crypto.SignerInfo;
import org.waveprotocol.wave.federation.Proto.ProtocolSignerInfo;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of persistence.
 *
 * <p>
 * {@link CertPathStore} implementation just forwards to the
 * {@link DefaultCertPathStore}.
 *
 *<p>
 *{@link AccountStore} implementation stores {@link AccountData} in a map keyed by username.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 *
 */
public class MemoryStore implements SignerInfoStore, AccountStore, ContactStore {

  private final CertPathStore certPathStore;

  public MemoryStore() {
    certPathStore = new DefaultCertPathStore();
    accountStore = new ConcurrentHashMap<ParticipantId, AccountData>();
  }

  @Override
  public void initializeSignerInfoStore() throws PersistenceException {
    // Nothing to initialize
  }

  @Override
  public SignerInfo getSignerInfo(byte[] signerId) throws SignatureException {
    return certPathStore.getSignerInfo(signerId);
  }

  @Override
  public void putSignerInfo(ProtocolSignerInfo protobuff) throws SignatureException {
    certPathStore.putSignerInfo(protobuff);
  }


  /*
   *  AccountStore
   */

  private final Map<ParticipantId, AccountData> accountStore;

  @Override
  public void initializeAccountStore() {
    // Nothing to initialize
  }

  @Override
  public AccountData getAccount(ParticipantId id) {
    return accountStore.get(id);
  }

  @Override
  public void putAccount(AccountData account) {
    accountStore.put(account.getId(), account);
  }

  @Override
  public void removeAccount(ParticipantId id) {
    accountStore.remove(id);
  }

  @Override
  public List<AccountData> getAllAccounts() {
    return new ArrayList<AccountData>(accountStore.values());
  }

  @Override
  public List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress) {
    List<RobotAccountData> ownedRobots = new ArrayList<RobotAccountData>();
    for (AccountData account : accountStore.values()) {
      if (account.isRobot()) {
        RobotAccountData robotAccount = account.asRobot();
        if (ownerAddress.equals(robotAccount.getOwnerAddress())) {
          ownedRobots.add(robotAccount);
        }
      }
    }
    return ownedRobots;
  }

  @Override
  public long getAccountCount() {
    return accountStore.size();
  }

  /*
   *  ContactStore
   */

  private final Map<ParticipantId, List<Contact>> contactsStore =
      new ConcurrentHashMap<ParticipantId, List<Contact>>();

  @Override
  public void initializeContactStore() throws PersistenceException {
    // Nothing to initialize for in-memory store.
  }

  @Override
  public List<Contact> getContacts(ParticipantId participant) throws PersistenceException {
    List<Contact> stored = contactsStore.get(participant);
    return stored != null ? new ArrayList<Contact>(stored) : null;
  }

  @Override
  public void storeContacts(ParticipantId participant, List<Contact> contacts)
      throws PersistenceException {
    contactsStore.put(participant, new ArrayList<Contact>(contacts));
  }
}

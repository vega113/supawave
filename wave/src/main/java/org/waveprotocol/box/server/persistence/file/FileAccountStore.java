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

package org.waveprotocol.box.server.persistence.file;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializer;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoAccountData;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A flat file based implementation of {@link AccountStore}
 *
 * @author tad.glines@gmail.com (Tad Glines)
 */
public class FileAccountStore implements AccountStore {
  private static final String ACCOUNT_FILE_EXTENSION = ".account";
  private final String accountStoreBasePath;
  private final Map<ParticipantId, AccountData> accounts = Maps.newHashMap();
  private final Map<String, ParticipantId> emailToParticipant = Maps.newHashMap();
  private boolean emailIndexInitialized;

  private static final Log LOG = Log.get(FileAccountStore.class);

  @Inject
  public FileAccountStore(Config config) {
    this.accountStoreBasePath = config.getString("core.account_store_directory");
  }

  @Override
  public void initializeAccountStore() throws PersistenceException {
    FileUtils.performDirectoryChecks(accountStoreBasePath, ACCOUNT_FILE_EXTENSION, "account store",
        LOG);
    synchronized (accounts) {
      populateEmailIndexIfNeeded();
    }
  }

  @Override
  public AccountData getAccount(ParticipantId id) throws PersistenceException {
    synchronized (accounts) {
      AccountData account = accounts.get(id);
      if (account == null) {
        account = readAccount(id);
        if (account != null) {
          accounts.put(id, account);
          indexAccountEmail(account);
        }
      }
      return account;
    }
  }

  @Override
  public void putAccount(AccountData account) throws PersistenceException {
    synchronized (accounts) {
      Preconditions.checkNotNull(account);
      writeAccount(account);
      removeIndexedEmail(accounts.get(account.getId()));
      accounts.put(account.getId(), account);
      indexAccountEmail(account);
    }
  }

  @Override
  public AccountData getAccountByEmail(String email) throws PersistenceException {
    if (email == null || email.isEmpty()) {
      return null;
    }
    synchronized (accounts) {
      populateEmailIndexIfNeeded();
      ParticipantId participantId = emailToParticipant.get(normalizeEmail(email));
      if (participantId == null) {
        return null;
      }
      return getAccount(participantId);
    }
  }

  @Override
  public java.util.List<AccountData> getAllAccounts() throws PersistenceException {
    synchronized (accounts) {
      // Ensure all on-disk accounts are loaded
      File dir = new File(accountStoreBasePath);
      File[] files = dir.listFiles((d, name) -> name.endsWith(ACCOUNT_FILE_EXTENSION));
      if (files != null) {
        for (File f : files) {
          String fileName = f.getName();
          String addr = fileName.substring(0, fileName.length() - ACCOUNT_FILE_EXTENSION.length());
          ParticipantId pid;
          try {
            pid = ParticipantId.of(addr);
          } catch (Exception e) {
            continue;
          }
          if (!accounts.containsKey(pid)) {
            AccountData account = readAccount(pid);
            if (account != null) {
              accounts.put(pid, account);
              indexAccountEmail(account);
            }
          }
        }
      }
      return new java.util.ArrayList<>(accounts.values());
    }
  }

  @Override
  public List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress)
      throws PersistenceException {
    if (ownerAddress == null || ownerAddress.trim().isEmpty()) {
      return new ArrayList<>();
    }
    List<RobotAccountData> ownedRobots = new ArrayList<>();
    List<AccountData> allAccounts = getAllAccounts();
    for (AccountData account : allAccounts) {
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
  public long getAccountCount() throws PersistenceException {
    synchronized (accounts) {
      File dir = new File(accountStoreBasePath);
      File[] files = dir.listFiles((d, name) -> name.endsWith(ACCOUNT_FILE_EXTENSION));
      return files != null ? files.length : 0;
    }
  }

  @Override
  public void removeAccount(ParticipantId id) throws PersistenceException {
    synchronized (accounts) {
      File file = new File(participantIdToFileName(id));
      if (file.exists()) {
        if (!file.delete()) {
          throw new PersistenceException("Failed to delete account data associated with "
              + id.getAddress());
        }
      }
      AccountData removedAccount = accounts.remove(id);
      if (removedAccount != null) {
        removeIndexedEmail(removedAccount);
      }
    }
  }

  private void populateEmailIndexIfNeeded() throws PersistenceException {
    if (emailIndexInitialized) {
      return;
    }

    File dir = new File(accountStoreBasePath);
    File[] files = dir.listFiles((d, name) -> name.endsWith(ACCOUNT_FILE_EXTENSION));
    if (files != null) {
      for (File file : files) {
        String fileName = file.getName();
        String address = fileName.substring(0, fileName.length() - ACCOUNT_FILE_EXTENSION.length());
        ParticipantId participantId;
        try {
          participantId = ParticipantId.of(address);
        } catch (Exception ignored) {
          continue;
        }

        AccountData account = accounts.get(participantId);
        if (account == null) {
          account = readAccount(participantId);
          if (account != null) {
            accounts.put(participantId, account);
          }
        }

        indexAccountEmail(account);
      }
    }

    emailIndexInitialized = true;
  }

  private void indexAccountEmail(AccountData account) {
    if (account == null || !account.isHuman()) {
      return;
    }

    String email = account.asHuman().getEmail();
    if (email == null || email.isEmpty()) {
      return;
    }

    emailToParticipant.put(normalizeEmail(email), account.getId());
  }

  private void removeIndexedEmail(AccountData account) {
    if (account == null || !account.isHuman()) {
      return;
    }

    String email = account.asHuman().getEmail();
    if (email == null || email.isEmpty()) {
      return;
    }

    emailToParticipant.remove(normalizeEmail(email));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String participantIdToFileName(ParticipantId id) {
    return accountStoreBasePath + File.separator + id.getAddress().toLowerCase()
        + ACCOUNT_FILE_EXTENSION;
  }

  private AccountData readAccount(ParticipantId id) throws PersistenceException {
    File accountFile = new File(participantIdToFileName(id));
    FileInputStream file = null;
    try {
      if (!accountFile.exists()) {
        return null;
      }
      file = new FileInputStream(accountFile);
      ProtoAccountData data = ProtoAccountData.newBuilder().mergeFrom(file).build();
      return ProtoAccountDataSerializer.deserialize(data);
    } catch (IOException e) {
      LOG.severe("Failed to read account data from file: " + accountFile.getAbsolutePath(), e);
      throw new PersistenceException(e);
    } finally {
      FileUtils.closeAndIgnoreException(file, accountFile, LOG);
    }
  }

  private void writeAccount(AccountData account) throws PersistenceException {
    File accountFile = new File(participantIdToFileName(account.getId()));
    OutputStream file = null;
    try {
      file = new FileOutputStream(accountFile);
      ProtoAccountData data = ProtoAccountDataSerializer.serialize(account);
      file.write(data.toByteArray());
      file.flush();
    } catch (IOException e) {
      LOG.severe("Failed to write account data to file: " + accountFile.getAbsolutePath(), e);
      throw new PersistenceException(e);
    } finally {
      FileUtils.closeAndIgnoreException(file, accountFile, LOG);
    }
  }
}

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

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Cache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Caching implementation of {@link ContactManager} that stores contacts in a
 * {@link ContactStore} with write-behind batching.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public class ContactManagerImpl implements ContactManager {

  private static final Log LOG = Log.get(ContactManagerImpl.class);

  /** Maximum number of per-user contact maps held in the read cache. */
  private static final int READ_CACHE_MAX_SIZE = 10000;

  /** How long to delay writes to the backing store (seconds). */
  private static final int WRITE_DELAY_SEC = 20;

  private final LoadingCache<ParticipantId, Map<ParticipantId, Contact>> contactsCache;
  private final Cache<ParticipantId, Map<ParticipantId, Contact>> contactsToWrite;

  @Inject
  public ContactManagerImpl(
      final ContactStore contactStore,
      @ExecutorAnnotations.ContactExecutor ScheduledExecutorService executor) {

    contactsCache = CacheBuilder.newBuilder()
        .maximumSize(READ_CACHE_MAX_SIZE)
        .build(new CacheLoader<ParticipantId, Map<ParticipantId, Contact>>() {
          @Override
          public Map<ParticipantId, Contact> load(ParticipantId participantId) throws Exception {
            Map<ParticipantId, Contact> contacts = Maps.newHashMap();
            List<Contact> list = contactStore.getContacts(participantId);
            if (list != null) {
              for (Contact contact : list) {
                contacts.put(contact.getParticipantId(), contact);
              }
            }
            return contacts;
          }
        });

    contactsToWrite = CacheBuilder.newBuilder()
        .expireAfterWrite(WRITE_DELAY_SEC, TimeUnit.SECONDS)
        .removalListener(
            new RemovalListener<ParticipantId, Map<ParticipantId, Contact>>() {
              @Override
              public void onRemoval(
                  RemovalNotification<ParticipantId, Map<ParticipantId, Contact>> notify) {
                try {
                  if (notify.getCause() != RemovalCause.REPLACED) {
                    List<Contact> contacts = Lists.newArrayList(notify.getValue().values());
                    contactStore.storeContacts(notify.getKey(), contacts);
                  }
                } catch (PersistenceException ex) {
                  LOG.severe("Store contacts error", ex);
                }
              }
            })
        .build();

    Runnable task = () -> contactsToWrite.cleanUp();
    executor.scheduleAtFixedRate(task, WRITE_DELAY_SEC, WRITE_DELAY_SEC, TimeUnit.SECONDS);
  }

  @Override
  public List<Contact> getContacts(ParticipantId participant, long fromTime)
      throws PersistenceException {
    Map<ParticipantId, Contact> contacts;
    try {
      contacts = contactsCache.get(participant);
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    }
    List<Contact> result = Lists.newArrayList();
    for (Contact contact : contacts.values()) {
      if (contact.getLastContactTime() > fromTime) {
        result.add(contact);
      }
    }
    return result;
  }

  @Override
  public void newCall(ParticipantId caller, ParticipantId receptor, long time, boolean direct)
      throws PersistenceException {
    updateContact(caller, receptor, time, true, direct);
    updateContact(receptor, caller, time, false, direct);
  }

  @Override
  public double getScoreBonusAtTime(Contact contact, long time) {
    return getScoreBonusAtTime(contact.getLastContactTime(), contact.getScoreBonus(), time);
  }

  // ---- internal helpers ----

  private void updateContact(
      ParticipantId participant, ParticipantId interlocutor,
      long time, boolean outgoing, boolean direct)
      throws PersistenceException {
    Map<ParticipantId, Contact> contacts;
    try {
      contacts = contactsCache.get(participant);
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    }
    Contact contact = contacts.get(interlocutor);
    if (contact != null) {
      long bonus = addBonus(
          contact.getLastContactTime(), contact.getScoreBonus(), time, outgoing, direct);
      if (time > contact.getLastContactTime()) {
        contact.setLastContactTime(time);
      }
      contact.setScoreBonus(bonus);
    } else {
      long bonus = getBonus(outgoing, direct);
      contact = new ContactImpl(interlocutor, time, bonus);
      contacts.put(interlocutor, contact);
    }
    contactsToWrite.put(participant, contacts);
  }

  private static long addBonus(
      long lastContactTime, long lastBonus, long time, boolean outgoing, boolean direct) {
    long bonus = getBonus(outgoing, direct);
    if (time >= lastContactTime) {
      return getScoreBonusAtTime(lastContactTime, lastBonus, time) + bonus;
    } else {
      return lastBonus + getScoreBonusAtTime(time, bonus, lastContactTime);
    }
  }

  private static long getBonus(boolean outgoing, boolean direct) {
    if (outgoing) {
      return direct ? OUTGOING_DIRECT_CALL_BONUS_MS : OUTGOING_INDIRECT_CALL_BONUS_MS;
    } else {
      return direct ? INCOMING_DIRECT_CALL_BONUS_MS : INCOMING_INDIRECT_CALL_BONUS_MS;
    }
  }

  private static long getScoreBonusAtTime(long lastContactTime, long bonus, long time) {
    Preconditions.checkArgument(time >= lastContactTime);
    if (bonus > BONUSES_EXPIRATION_MS) {
      long elapsedTime = time - lastContactTime;
      long newBonus = bonus - (long) (elapsedTime * ((double) bonus / BONUSES_EXPIRATION_MS));
      return newBonus > 0 ? newBonus : 0;
    }
    return bonus;
  }
}

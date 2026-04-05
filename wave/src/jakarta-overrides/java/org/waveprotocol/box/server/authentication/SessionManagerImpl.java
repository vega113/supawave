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
package org.waveprotocol.box.server.authentication;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import jakarta.servlet.http.HttpSession;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Session;
import org.eclipse.jetty.session.ManagedSession;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.PercentEscaper;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Jakarta override of SessionManagerImpl wiring against Jetty 12 session APIs.
 */
public final class SessionManagerImpl implements SessionManager {
  private static final String USER_FIELD = "user";
  private static final String LAST_ACTIVITY_UPDATE_FIELD = "lastActivityUpdateTime";
  private static final long LAST_ACTIVITY_UPDATE_INTERVAL_MS = 60000L;

  private final AccountStore accountStore;
  private static final Log LOG = Log.get(SessionManagerImpl.class);
  private final SessionHandler sessionHandler;
  private final AnalyticsRecorder analyticsRecorder;

  @Inject
  public SessionManagerImpl(AccountStore accountStore,
                            SessionHandler sessionHandler,
                            AnalyticsRecorder analyticsRecorder) {
    Preconditions.checkNotNull(accountStore, "Null account store");
    Preconditions.checkNotNull(sessionHandler, "Null session handler");
    this.accountStore = accountStore;
    this.sessionHandler = sessionHandler;
    this.analyticsRecorder = analyticsRecorder;
  }

  @Override
  public ParticipantId getLoggedInUser(WebSession session) {
    ParticipantId user = null;
    if (session != null) {
      Object value = session.getAttribute(USER_FIELD);
      if (value instanceof ParticipantId) {
        user = (ParticipantId) value;
        refreshLastActivity(session, user, false);
      }
    }
    return user;
  }

  @Override
  public AccountData getLoggedInAccount(WebSession session) {
    ParticipantId user = getLoggedInUser(session);
    if (user != null) {
      try {
        return accountStore.getAccount(user);
      } catch (PersistenceException e) {
        LOG.warning("Failed to retrieve account data for " + user, e);
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public void setLoggedInUser(WebSession session, ParticipantId id) {
    Preconditions.checkNotNull(session, "Session is null");
    Preconditions.checkNotNull(id, "Participant id is null");
    session.setAttribute(USER_FIELD, id);
    refreshLastActivity(session, id, true);
  }

  @Override
  public void logout(WebSession session) {
    if (session != null) {
      session.removeAttribute(USER_FIELD);
    }
  }

  @Override
  public String getLoginUrl(String redirect) {
    if (Strings.isNullOrEmpty(redirect)) {
      return SIGN_IN_URL;
    } else {
      PercentEscaper escaper =
          new PercentEscaper(PercentEscaper.SAFEQUERYSTRINGCHARS_URLENCODER, false);
      String queryStr = "?r=" + escaper.escape(redirect);
      return SIGN_IN_URL + queryStr;
    }
  }

  @Override
  public WebSession getSessionFromToken(String token) {
    WebSession session = null;
    try {
      if (token != null) {
        ManagedSession managedSession = sessionHandler.getManagedSession(normalizeToken(token));
        if (managedSession != null) {
          Session.API sessionApi = sessionHandler.newSessionAPIWrapper(managedSession);
          if (sessionApi instanceof HttpSession httpSession) {
            session = WebSessions.wrap(httpSession);
          }
        }
      }
    } catch (Throwable t) {
      LOG.info("Jetty 12 session lookup failed (ignored)", t);
    }
    return session;
  }

  private static String normalizeToken(String token) {
    String sessionId = token;
    int dot = sessionId.indexOf('.');
    if (dot > 0) {
      sessionId = sessionId.substring(0, dot);
    }
    return sessionId;
  }

  private void refreshLastActivity(WebSession session, ParticipantId user, boolean force) {
    long now = System.currentTimeMillis();
    if (!shouldRefreshLastActivity(session, now, force)) {
      return;
    }
    try {
      AccountData account = accountStore.getAccount(user);
      if (account != null && account.isHuman()) {
        HumanAccountData human = account.asHuman();
        human.setLastActivityTime(now);
        accountStore.putAccount(account);
        session.setAttribute(LAST_ACTIVITY_UPDATE_FIELD, now);
        analyticsRecorder.recordActiveUser(user.getAddress(), now);
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to track last activity for " + user, e);
    }
  }

  private boolean shouldRefreshLastActivity(WebSession session, long now, boolean force) {
    if (force) {
      return true;
    }
    Object previous = session.getAttribute(LAST_ACTIVITY_UPDATE_FIELD);
    if (previous instanceof Number) {
      return now - ((Number) previous).longValue() >= LAST_ACTIVITY_UPDATE_INTERVAL_MS;
    }
    return true;
  }
}

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
package org.eclipse.jetty.server.session;

import javax.servlet.SessionCookieConfig;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal compile-time stub of Jetty's SessionHandler used to configure cookie
 * attributes and session persistence during the Jakarta migration. Not used at
 * runtime. Provides a tiny in-memory registry to satisfy getSession lookups in
 * code that may call it (returns null by default unless registered manually).
 */
public class SessionHandler {
  private final SessionCookieConfig cookieConfig = new SimpleCookieConfig();
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private DefaultSessionCache cache;

  /** Returns the cookie configuration for this handler. */
  public SessionCookieConfig getSessionCookieConfig() { return cookieConfig; }

  /** Assigns the session cache (no-op in stub). */
  public void setSessionCache(DefaultSessionCache cache) { this.cache = cache; }

  /** Optional in Jetty 9; reflective in our code. Present here to avoid miss. */
  public void setSameSite(Object sameSite) {}

  /**
   * Lookup a session by id. In this stub, returns a manually registered entry
   * if present, or null otherwise.
   */
  public Session getSession(String id) { return sessions.get(id); }

  /** Registers a session for lookup in this stub handler. */
  public void registerSession(String id, HttpSession httpSession) {
    if (id != null && httpSession != null) {
      sessions.put(id, new Session(httpSession));
    }
  }

  // Simple SessionCookieConfig implementation
  static class SimpleCookieConfig implements SessionCookieConfig {
    private int maxAge;
    private boolean httpOnly;
    private boolean secure;
    private String name;
    private String domain;
    private String path;

    @Override public void setName(String name) { this.name = name; }
    @Override public String getName() { return name; }
    @Override public void setDomain(String domain) { this.domain = domain; }
    @Override public String getDomain() { return domain; }
    @Override public void setPath(String path) { this.path = path; }
    @Override public String getPath() { return path; }
    @Override public void setComment(String comment) {}
    @Override public String getComment() { return null; }
    @Override public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
    @Override public boolean isHttpOnly() { return httpOnly; }
    @Override public void setSecure(boolean secure) { this.secure = secure; }
    @Override public boolean isSecure() { return secure; }
    @Override public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
    @Override public int getMaxAge() { return maxAge; }
  }
}

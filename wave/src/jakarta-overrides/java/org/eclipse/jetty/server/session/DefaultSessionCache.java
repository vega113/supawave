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

/**
 * Minimal compile-time stub of Jetty's DefaultSessionCache used by ServerModule
 * during the Jakarta (Jetty 12) migration. This class is not used at runtime;
 * it only preserves the constructor and mutators relied upon by our wiring.
 */
public class DefaultSessionCache {
  private final SessionHandler handler;
  private FileSessionDataStore store;

  /**
   * Creates a cache bound to the given SessionHandler.
   */
  public DefaultSessionCache(SessionHandler handler) { this.handler = handler; }

  /**
   * Assigns the backing {@link FileSessionDataStore}. No-op in the stub.
   */
  public void setSessionDataStore(FileSessionDataStore store) { this.store = store; }
}

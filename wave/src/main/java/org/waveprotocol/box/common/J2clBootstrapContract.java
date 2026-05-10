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
package org.waveprotocol.box.common;

/**
 * Pinned naming contract for the J2CL bootstrap JSON endpoint introduced by
 * issue #963. Shared by the server-side servlet, tests, and documentation so
 * the server-owned contract has one canonical source of truth.
 *
 * <p>This class is intentionally behavior-free; it only exposes path and field
 * name constants. The JSON schema is:
 *
 * <pre>
 * {
 *   "session": { "domain": "...", "address": "...", "role": "...", "features": [...] },
 *   "socket":  { "address": "..." },
 *   "shell":   { "buildCommit": "...", "serverBuildTime": 0,
 *                "currentReleaseId": "...", "routeReturnTarget": "..." }
 * }
 * </pre>
 *
 * <p>The J2CL JSON contract intentionally omits {@code SessionConstants.ID_SEED}
 * even though the rollback-safe inline HTML globals still expose it during the
 * inline-HTML rollback overlap.
 * The remaining session fields keep the same signed-in/signed-out presence rules
 * as {@code WaveClientServlet#buildSessionJson(WebSession)}.
 *
 * <p>Forward compatibility: follow-up work under issue #933 may add more fields
 * under {@code socket} (e.g. a signed bootstrap token). Clients must therefore
 * ignore unknown keys under each nested object.
 */
public final class J2clBootstrapContract {
  public static final String PATH = "/bootstrap.json";

  public static final String KEY_SESSION = "session";
  public static final String KEY_SOCKET = "socket";
  public static final String KEY_SHELL = "shell";

  public static final String SESSION_DOMAIN = "domain";
  public static final String SESSION_ADDRESS = "address";
  public static final String SESSION_ROLE = "role";
  public static final String SESSION_FEATURES = "features";
  public static final String SESSION_LOCALE = "locale";

  public static final String SOCKET_ADDRESS = "address";

  public static final String SHELL_BUILD_COMMIT = "buildCommit";
  public static final String SHELL_SERVER_BUILD_TIME = "serverBuildTime";
  public static final String SHELL_CURRENT_RELEASE_ID = "currentReleaseId";
  public static final String SHELL_ROUTE_RETURN_TARGET = "routeReturnTarget";

  private J2clBootstrapContract() {}
}

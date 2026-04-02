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

package org.waveprotocol.examples.robots.gptbot;

import junit.framework.TestCase;

public class GptBotServerTest extends TestCase {

  public void testCallbackAuthorizationAllowsLocalRequestsWithoutToken() {
    assertTrue(GptBotServer.isCallbackAuthorized(null, "", false));
  }

  public void testCallbackAuthorizationRequiresMatchingTokenWhenConfigured() {
    assertFalse(GptBotServer.isCallbackAuthorized(null, "", true));
    assertTrue(GptBotServer.isCallbackAuthorized("token=abc123", "abc123", true));
    assertFalse(GptBotServer.isCallbackAuthorized("token=wrong", "abc123", true));
  }

  public void testCallbackUrlIncludesTokenWhenConfigured() {
    GptBotConfig config = GptBotConfig.forTest().withCallbackToken("secret token");

    assertEquals("http://127.0.0.1:8087/_wave/robot/jsonrpc?token=secret+token",
        config.getCallbackUrl("/_wave/robot/jsonrpc"));
  }

  public void testRedactedCallbackUrlHidesTokenWhenConfigured() {
    GptBotConfig config = GptBotConfig.forTest().withCallbackToken("secret token");

    assertEquals("http://127.0.0.1:8087/_wave/robot/jsonrpc?token=<redacted>",
        config.getRedactedCallbackUrl("/_wave/robot/jsonrpc"));
  }
}

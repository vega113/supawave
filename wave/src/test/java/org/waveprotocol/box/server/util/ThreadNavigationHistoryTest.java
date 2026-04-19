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

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.util.ThreadNavigationHistory;

public final class ThreadNavigationHistoryTest extends TestCase {

  public void testAppendMetadataPreservesBaseWaveToken() {
    String token = ThreadNavigationHistory.appendMetadata(
        "example.com/w+abc123/~/conv+root/b+abc", "b+child", 2);

    assertEquals(
        "example.com/w+abc123/~/conv+root/b+abc&focus=b+child&slide-nav=2",
        token);
  }

  public void testStripMetadataKeepsWaveRefPath() {
    String token =
        "example.com/w+abc123/~/conv+root/b+abc&focus=b+child&slide-nav=2";

    assertEquals(
        "example.com/w+abc123/~/conv+root/b+abc",
        ThreadNavigationHistory.stripMetadata(token));
  }

  public void testExtractParamReadsAppendedValues() {
    String token =
        "example.com/w+abc123/~/conv+root/b+abc&focus=b+child&slide-nav=2";

    assertEquals("b+child", ThreadNavigationHistory.extractParam(token, "focus"));
    assertEquals("2", ThreadNavigationHistory.extractParam(token, "slide-nav"));
  }

  public void testHasMetadataDetectsFocusedThreadParams() {
    assertTrue(ThreadNavigationHistory.hasMetadata(
        "example.com/w+abc123/~/conv+root/b+abc&focus=b+child"));
    assertTrue(ThreadNavigationHistory.hasMetadata(
        "example.com/w+abc123/~/conv+root/b+abc&slide-nav=2"));
    assertFalse(ThreadNavigationHistory.hasMetadata(
        "example.com/w+abc123/~/conv+root/b+abc"));
  }
}

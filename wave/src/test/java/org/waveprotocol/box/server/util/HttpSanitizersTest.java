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

import static org.junit.Assert.*;

import org.junit.Test;

public final class HttpSanitizersTest {

  // --- stripHeaderBreakingChars ---

  @Test
  public void stripHeaderBreakingCharsReturnsNullForNull() {
    assertNull(HttpSanitizers.stripHeaderBreakingChars(null));
  }

  @Test
  public void stripHeaderBreakingCharsRemovesCrLfAndControls() {
    String s = "ab\rc\nd\u0000e\u0001f\tg"; // CR, LF, NUL, SOH, TAB
    String out = HttpSanitizers.stripHeaderBreakingChars(s);
    // Expect CR/LF/NUL/SOH removed; TAB preserved
    assertEquals("abcdef\tg".replace("\t", "\t"), out);
  }

  @Test
  public void stripHeaderBreakingCharsPreservesUnicodeAndAscii() {
    String s = "na\u00EFve\u2713"; // naïve✓
    String out = HttpSanitizers.stripHeaderBreakingChars(s);
    assertEquals("na\u00EFve\u2713", out);
  }

  @Test
  public void stripHeaderBreakingCharsNeutralizesHeaderInjection() {
    String inj = "http://example.com\r\nX-Injected: 1";
    String out = HttpSanitizers.stripHeaderBreakingChars(inj);
    assertEquals("http://example.comX-Injected: 1", out);
  }

  // --- isSafeRelativeRedirect ---

  @Test
  public void isSafeRelativeRedirectAcceptsSimpleAbsolutePath() {
    assertTrue(HttpSanitizers.isSafeRelativeRedirect("/home"));
    assertTrue(HttpSanitizers.isSafeRelativeRedirect("/"));
    assertTrue(HttpSanitizers.isSafeRelativeRedirect("/path?q=a&b=c"));
  }

  @Test
  public void isSafeRelativeRedirectRejectsNullEmptyOrRelative() {
    assertFalse(HttpSanitizers.isSafeRelativeRedirect(null));
    assertFalse(HttpSanitizers.isSafeRelativeRedirect(""));
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("relative"));
  }

  @Test
  public void isSafeRelativeRedirectRejectsProtocolRelativeAndExternal() {
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("//evil.example/"));
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("http://evil.example/"));
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("https://evil.example/"));
  }

  @Test
  public void isSafeRelativeRedirectRejectsCrLfAndBackslash() {
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("/ok\r\nInjected: 1"));
    assertFalse(HttpSanitizers.isSafeRelativeRedirect("/path\\withbackslash"));
  }
}


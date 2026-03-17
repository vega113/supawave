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

  @Test
  public void rfc5987EncodesNonAscii() {
    String v = HttpSanitizers.rfc5987Encode("naïve ✓.txt");
    assertEquals("na%C3%AFve%20%E2%9C%93.txt", v);
  }

  @Test
  public void buildContentDispositionAttachmentBuildsBothParams() {
    String cd = HttpSanitizers.buildContentDispositionAttachment("report naïve ✓.txt");
    assertTrue(cd.startsWith("attachment;"));
    assertTrue(cd.contains("filename=\"report na_ve _.txt\""));
    assertTrue(cd.contains("filename*=UTF-8''report%20na%C3%AFve%20%E2%9C%93.txt"));
  }

  @Test
  public void buildContentDispositionAttachmentHandlesNullAndEmpty() {
    String cdNull = HttpSanitizers.buildContentDispositionAttachment(null);
    assertTrue(cdNull.contains("filename=\"download\""));
    assertTrue(cdNull.contains("filename*=UTF-8''download"));

    String cdEmpty = HttpSanitizers.buildContentDispositionAttachment("");
    assertTrue(cdEmpty.contains("filename=\"download\""));
    assertTrue(cdEmpty.contains("filename*=UTF-8''download"));

    String cdSpaces = HttpSanitizers.buildContentDispositionAttachment("    ");
    assertTrue(cdSpaces.contains("filename=\"download\""));
  }

  @Test
  public void buildContentDispositionAttachmentHandlesMalformedUnicodeAndBounds() {
    // Lone high surrogate + extension
    String bad = "bad\uD83D.txt";
    String cd = HttpSanitizers.buildContentDispositionAttachment(bad);
    // ascii fallback should not contain the surrogate
    int s = cd.indexOf("filename=\"");
    int e = cd.indexOf('"', s + 10);
    String ascii = cd.substring(s + 10, e);
    assertFalse(ascii.contains("\uD83D"));
    assertTrue(cd.contains("filename*="));

    // Ensure encoded value is within reasonable bounds (120 chars as per impl)
    String encKey = "filename*=UTF-8''";
    int es = cd.indexOf(encKey);
    assertTrue(es > 0);
    String encVal = cd.substring(es + encKey.length());
    // No next param; the value goes until end
    assertTrue("encoded length bound", encVal.length() <= 130);
  }

  @Test
  public void contentDispositionRejectsPathTraversalAndReserved() {
    String cd = HttpSanitizers.buildContentDispositionAttachment("../../etc/passwd");
    int s = cd.indexOf("filename=\"");
    int e = cd.indexOf('"', s + 10);
    String quotedTraversal = cd.substring(s + 10, e);
    assertTrue(quotedTraversal.endsWith("etc_passwd"));
    assertFalse(cd.contains("../"));
    assertFalse(cd.contains("..\\"));
    // Ensure none of Windows-reserved or path separators appear inside the quoted filename
    int start = cd.indexOf("filename=\"");
    int end = cd.indexOf('"', start + 10);
    String quoted = cd.substring(start + 10, end);
    assertFalse(quoted.contains("/"));
    assertFalse(quoted.contains("\\"));
    assertFalse(quoted.contains(":"));
    assertFalse(quoted.contains("*"));
    assertFalse(quoted.contains("?"));
    assertFalse(quoted.contains("<"));
    assertFalse(quoted.contains(">"));
    assertFalse(quoted.contains("|"));
  }

  @Test
  public void contentDispositionStripsBidiControls() {
    String cd = HttpSanitizers.buildContentDispositionAttachment("evil\u202Etxt.png");
    // encoded part must not include the bidi override
    assertFalse(cd.contains("%E2%80%AE"));
  }

  @Test
  public void contentDispositionTruncatesPreservingExtension() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 200; i++) sb.append('a');
    sb.append(".pdf");
    String cd = HttpSanitizers.buildContentDispositionAttachment(sb.toString());
    // ascii fallback should end with .pdf and be reasonably short
    assertTrue(cd.contains("filename=\""));
    int idx = cd.indexOf("filename=\"");
    String rest = cd.substring(idx + "filename=\"".length());
    String ascii = rest.substring(0, rest.indexOf('"'));
    assertTrue(ascii.endsWith(".pdf"));
    assertTrue("ascii fallback length", ascii.length() <= 80);
  }
}

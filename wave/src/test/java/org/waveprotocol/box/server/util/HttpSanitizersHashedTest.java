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

public final class HttpSanitizersHashedTest {

  @Test
  public void hashedDispositionBuildsStableLabelAndKeepsSafeExt() {
    String cd = HttpSanitizers.buildHashedContentDispositionAttachment("..\\..//evil name.pdf");
    assertTrue(cd.startsWith("attachment;"));
    int s = cd.indexOf("filename=\"");
    int e = cd.indexOf('"', s + 10);
    String quoted = cd.substring(s + 10, e);
    assertTrue("Should contain file- prefix", quoted.startsWith("file-"));
    assertTrue("Should preserve .pdf ext", quoted.endsWith(".pdf"));
    assertTrue("Should not contain spaces", !quoted.contains(" "));
    assertTrue("Hash length reasonable", quoted.length() <= 80);
    // RFC 5987 param should also be present
    assertTrue(cd.contains("filename*="));
  }

  @Test
  public void hashedDispositionOmitsUnknownExt() {
    String cd = HttpSanitizers.buildHashedContentDispositionAttachment("payload.weirdext");
    int s = cd.indexOf("filename=\"");
    int e = cd.indexOf('"', s + 10);
    String quoted = cd.substring(s + 10, e);
    assertFalse("unknown ext should be dropped", quoted.endsWith(".weirdext"));
  }
}


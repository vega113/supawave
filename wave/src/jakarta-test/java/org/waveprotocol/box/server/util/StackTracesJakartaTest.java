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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class StackTracesJakartaTest {
  @Test
  public void printsThrowableAndCauseChain() {
    Exception inner = new IllegalArgumentException("bad arg");
    Exception outer = new RuntimeException("top level", inner);
    String out = StackTraces.toStringLite(outer);
    assertTrue(out.contains("java.lang.RuntimeException: top level"));
    assertTrue(out.contains("java.lang.IllegalArgumentException: bad arg"));
    assertTrue(out.contains("Caused by:"));
    assertTrue(out.contains("\tat "));
  }

  static class SelfCauseException extends RuntimeException {
    @Override public synchronized Throwable getCause() { return this; }
  }

  @Test
  public void handlesCyclicCausesWithoutLooping() {
    String out = StackTraces.toStringLite(new SelfCauseException());
    int idx = out.indexOf("Caused by:");
    assertTrue(idx >= 0);
    assertFalse(out.indexOf("Caused by:", idx + 1) >= 0);
  }
}


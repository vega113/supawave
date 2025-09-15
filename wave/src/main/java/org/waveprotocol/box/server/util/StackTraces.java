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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Utility methods for compact, safe stack trace printing. */
public final class StackTraces {
  private StackTraces() {}

  /**
   * Prints a compact stack trace that includes the full Throwable.toString()
   * and every frame for the initial Throwable, plus a de-duplicated cause
   * chain. This avoids recursive loops on pathological Throwable implementations.
   */
  public static void printStackTraceLite(Throwable t, PrintStream out) {
    if (t == null) {
      return;
    }
    out.println(String.valueOf(t));
    for (StackTraceElement ste : t.getStackTrace()) {
      out.println("\tat " + ste);
    }
    Throwable cause = t.getCause();
    Set<Throwable> seen = new HashSet<>();
    while (cause != null && !seen.contains(cause)) {
      seen.add(cause);
      out.println("Caused by: " + String.valueOf(cause));
      for (StackTraceElement ste : cause.getStackTrace()) {
        out.println("\tat " + ste);
      }
      cause = cause.getCause();
    }
  }

  /** Returns the lite stack trace as a String for testing or logs. */
  public static String toStringLite(Throwable t) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
    PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
    printStackTraceLite(t, ps);
    ps.flush();
    return baos.toString(StandardCharsets.UTF_8);
  }
}


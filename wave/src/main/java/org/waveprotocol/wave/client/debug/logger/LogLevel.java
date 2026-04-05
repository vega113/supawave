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

package org.waveprotocol.wave.client.debug.logger;

import com.google.gwt.core.client.GWT;

/**
 * LogLevel defines an ordered list of logging levels.<br/>
 *
 * <pre>NONE < ERROR < DEBUG</pre>
 *
 * The log level is determined at runtime by reading the {@code ll} URL query parameter
 * (values: {@code none}, {@code error}, {@code debug}).  If absent the level defaults to
 * {@code none}, preserving the previous production behaviour.
 *
 * <p>TODO(j2cl): deferred binding removed. Previously a "loglevel" GWT property and
 * replace-with rules selected NoneImpl/ErrorImpl/DebugImpl at compile time. That mechanism is
 * incompatible with J2CL.  The runtime URL-parameter approach below is equivalent for
 * development use and has no overhead in production (the default is "none").
 */
public abstract class LogLevel {
  private static final LogLevel INSTANCE = createInstance();

  // NOTE(user): LogLevel must *not* have a clinit method (i.e. any static
  //               initialisation code), otherwise each inlining of shouldX()
  //               will call the clinit.

  // NOTE(user): This class is not an enum, as GWT (as of 21 Jan 2009) does not
  //               do object identity tracking, even for enums.
  //               Hence the comparisons against the enum values do not get
  //               inlined (which is an essential goal of the "#ifdef" style
  //               methods below).

  private static LogLevel createInstance() {
    if (!GWT.isClient()) {
      return new ErrorImpl();
    }
    String logLevel = readLogLevelFromUrl();
    if ("debug".equals(logLevel)) {
      return new DebugImpl();
    } else if ("error".equals(logLevel)) {
      return new ErrorImpl();
    } else {
      return new NoneImpl();
    }
  }

  /**
   * Reads the "ll" query parameter from the current page URL and returns its
   * lower-case value, or {@code "none"} if absent.
   */
  private static native String readLogLevelFromUrl() /*-{
    var args = $wnd.location.search;
    var idx = args.indexOf("ll=");
    if (idx >= 0) {
      var val = args.substring(idx + 3);
      var end = val.indexOf("&");
      if (end !== -1) { val = val.substring(0, end); }
      return val.toLowerCase();
    }
    return "none";
  }-*/;

  /**
   * Should an entry with level ERROR be logged?
   */
  public static boolean showErrors() {
    return INSTANCE.showErrorsInstance();
  }

  /**
   * Should an entry with level DEBUG be logged?
   */
  public static boolean showDebug() {
    return INSTANCE.showDebugInstance();
  }

  // Intended for overriding per-implementation:
  protected abstract boolean showErrorsInstance();
  protected abstract boolean showDebugInstance();

  /**
   * Log level used for production (no logging).
   */
  @SuppressWarnings("unused")
  private static class NoneImpl extends LogLevel {
    @Override protected boolean showErrorsInstance() { return false; }
    @Override protected boolean showDebugInstance()  { return false; }
  }

  /**
   * Log level used for logging errors only.
   */
  @SuppressWarnings("unused")
  private static class ErrorImpl extends LogLevel {
    @Override protected boolean showErrorsInstance() { return true; }
    @Override protected boolean showDebugInstance()  { return false; }
  }

  /**
   * Log level used for full debug logging.
   */
  @SuppressWarnings("unused")
  private static class DebugImpl extends LogLevel {
    @Override protected boolean showErrorsInstance() { return true; }
    @Override protected boolean showDebugInstance()  { return true; }
  }
}

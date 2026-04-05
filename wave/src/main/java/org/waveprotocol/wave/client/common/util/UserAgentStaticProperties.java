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

package org.waveprotocol.wave.client.common.util;

import com.google.gwt.core.client.GWT;

/**
 * Class to allow conditional compilation for different user agents.
 *
 * All methods should return values that are known at compile time.
 *
 * TODO(j2cl): deferred binding removed. Previously each implementation subclass was selected at
 * compile time via GWT replace-with rules in Util.gwt.xml. Now the instance is chosen at runtime
 * by inspecting navigator.userAgent directly, which is compatible with J2CL.
 *
 * FIXME: GWT superdev recompilation fails when this class is abstract
 *
 */
public class UserAgentStaticProperties {

  static UserAgentStaticProperties get() {
    return INSTANCE;
  }

  private static final UserAgentStaticProperties INSTANCE = createInstance();

  /**
   * Creates an instance of UserAgentStaticProperties by detecting the browser at runtime.
   *
   * <p>In a GWT/J2CL client context {@code navigator.userAgent} is used. In non-GWT runtimes
   * (e.g. unit tests) we fall back to a Firefox-like default, preserving the prior behaviour.
   */
  private static UserAgentStaticProperties createInstance() {
    if (!GWT.isClient()) {
      // Default to Firefox-like behavior in non-GWT runtimes/tests.
      return new FirefoxImpl();
    }
    String ua = getNativeUserAgent().toLowerCase();
    if (ua.contains("iphone") || ua.contains("ipod")) {
      return new IPhoneImpl();
    } else if (ua.contains("android")) {
      return new AndroidImpl();
    } else if (ua.contains("webkit") || ua.contains("safari") || ua.contains("chrome")) {
      return new SafariImpl();
    } else if (ua.contains("gecko") || ua.contains("firefox")) {
      return new FirefoxImpl();
    } else {
      // Unknown browser — default to Safari/Webkit-like (most modern browsers are Webkit-based).
      return new SafariImpl();
    }
  }

  private static native String getNativeUserAgent() /*-{
    return $wnd.navigator.userAgent || "";
  }-*/;

  final boolean isWebkit() {
    return isSafari() || isMobileWebkit();
  }

  /**
   * @return true iff the user agent uses mobile webkit
   */
  final boolean isMobileWebkit() {
    return isAndroid() || isIPhone();
  }

  // Default instance methods: most return false, since they are intended to be overridden.
  boolean isSafari()  { return false; }
  boolean isFirefox() { return false; }
  boolean isIE()      { return false; }
  boolean isAndroid() { return false; }
  boolean isIPhone()  { return false; }

  public static class SafariImpl extends UserAgentStaticProperties {
    @Override
    protected boolean isSafari() {
      return true;
    }
  }

  public static class FirefoxImpl extends UserAgentStaticProperties {
    @Override
    protected boolean isFirefox() {
      return true;
    }
  }

  public static class IEImpl extends UserAgentStaticProperties {
    @Override
    protected boolean isIE() {
      return true;
    }
  }

  public static class AndroidImpl extends UserAgentStaticProperties {
    @Override
    protected boolean isAndroid() {
      return true;
    }
  }

  public static class IPhoneImpl extends UserAgentStaticProperties {
    @Override
    protected boolean isIPhone() {
      return true;
    }
  }
}

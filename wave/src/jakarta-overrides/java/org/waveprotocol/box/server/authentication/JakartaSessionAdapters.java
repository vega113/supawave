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
package org.waveprotocol.box.server.authentication;

import org.waveprotocol.wave.util.logging.Log;

/**
 * Small adapter utilities to bridge jakarta.servlet.http.HttpSession to
 * javax.servlet.http.HttpSession for code paths that still depend on the
 * javax-based SessionManager interface.
 */
public final class JakartaSessionAdapters {
  private static final Log LOG = Log.get(JakartaSessionAdapters.class);
  private JakartaSessionAdapters() {}

  public static javax.servlet.http.HttpSession toJavax(jakarta.servlet.http.HttpSession js) {
    if (js == null) return null;
    return new JavaxSessionWrapper(js);
  }

  /**
   * Returns a {@code javax.servlet.http.HttpSession} wrapper for a Jakarta
   * {@code HttpServletRequest}'s session, or {@code null}.
   *
   * Semantics and guarantees:
   * - Equivalent to {@code req.getSession(create)} in terms of session
   *   presence/creation, but never throws. Any container error (including
   *   IllegalStateException) results in {@code null}.
   * - When a session exists, it is wrapped so downstream code using the
   *   javax servlet API sees a compatible {@code HttpSession}.
   * - The wrapper delegates all calls to the underlying Jakarta session; no
   *   state is duplicated.
   * - Logging: emits at FINE on expected conditions (no/disabled sessions);
   *   unexpected throwables are logged at WARN without sensitive data.
   */
  public static javax.servlet.http.HttpSession fromRequest(jakarta.servlet.http.HttpServletRequest req, boolean create) {
    if (req == null) return null;
    try {
      return toJavax(req.getSession(create));
    } catch (IllegalStateException e) {
      // Container not fully initialized or sessions disabled
      if (LOG.isFineLoggable()) LOG.fine("Session adaptation skipped: " + e.getMessage());
      return null;
    } catch (Throwable t) {
      LOG.warning("Session adaptation failed; proceeding without session", t);
      return null;
    }
  }

  static final class JavaxSessionWrapper implements javax.servlet.http.HttpSession {
    private final jakarta.servlet.http.HttpSession d;
    JavaxSessionWrapper(jakarta.servlet.http.HttpSession delegate) { this.d = delegate; }
    @Override public long getCreationTime() { return d.getCreationTime(); }
    @Override public String getId() { return d.getId(); }
    @Override public long getLastAccessedTime() { return d.getLastAccessedTime(); }
    /**
     * Best-effort adaptation of a Jakarta {@code ServletContext} to the legacy
     * {@code javax.servlet.ServletContext} expected by some call sites.
     *
     * Notes:
     * - Returns a non-null dynamic proxy that forwards a safe subset of methods
     *   whose signatures are identical across the javax/jakarta split
     *   (for example, attribute and init-parameter accessors). Methods that
     *   require javax-typed return values (e.g., {@code ServletRegistration})
     *   throw {@link UnsupportedOperationException}.
     * - If the underlying container returns {@code null} or throws while
     *   obtaining the context, a stub proxy is returned that throws on use.
     * - This keeps callers that only check non-nullity from failing with NPEs
     *   while remaining explicit about unsupported operations.
     */
    // Lazily-created proxy; safe to cache because ServletContext is container-wide
    private volatile javax.servlet.ServletContext cachedCtx;
    @Override public javax.servlet.ServletContext getServletContext() {
      javax.servlet.ServletContext local = cachedCtx;
      if (local != null) return local;
      final Object jakartaCtx;
      try {
        jakartaCtx = d.getServletContext();
      } catch (Throwable t) {
        // Defensive: some containers may throw if session is invalidated mid-call
        LOG.warning("Unable to obtain Jakarta ServletContext from session; returning stub", t);
        return cachedCtx = newNullServletContext();
      }
      if (jakartaCtx == null) {
        // Shouldn't happen in compliant containers; return a non-null stub to avoid NPE chains
        return cachedCtx = newNullServletContext();
      }

      // Create a lightweight dynamic proxy that forwards a small, safe subset of methods
      // whose signatures are identical between javax and jakarta (String/Object-based APIs).
      java.lang.reflect.InvocationHandler h = (proxy, method, args) -> {
        String name = method.getName();
        // Common object methods
        if (name.equals("toString")) return "JavaxServletContextAdapter{" + jakartaCtx + "}";
        if (name.equals("hashCode")) return jakartaCtx.hashCode();
        if (name.equals("equals")) return proxy == args[0];

        // Supported 1:1 methods (no javax/jakarta typed parameters or returns)
        switch (name) {
          case "getContextPath":
          case "getServerInfo":
          case "getInitParameter":
          case "getInitParameterNames":
          case "setAttribute":
          case "getAttribute":
          case "getAttributeNames":
          case "removeAttribute":
          case "getMajorVersion":
          case "getMinorVersion":
          case "getEffectiveMajorVersion":
          case "getEffectiveMinorVersion":
          case "getMimeType":
          case "getRealPath":
          case "getResource":
          case "getResourceAsStream":
          case "getResourcePaths":
          case "getVirtualServerName":
          case "getClassLoader":
            try {
              java.lang.reflect.Method jm = jakartaCtx.getClass().getMethod(name, method.getParameterTypes());
              return jm.invoke(jakartaCtx, args);
            } catch (NoSuchMethodException nsme) {
              // Fall through to unsupported handling
            }
            break;
          case "log":
            // Handle both log(String) and log(String, Throwable)
            try {
              java.lang.Class<?>[] ptypes = new java.lang.Class<?>[method.getParameterCount()];
              for (int i = 0; i < ptypes.length; i++) ptypes[i] = method.getParameterTypes()[i];
              java.lang.reflect.Method jm = jakartaCtx.getClass().getMethod("log", ptypes);
              return jm.invoke(jakartaCtx, args);
            } catch (NoSuchMethodException nsme) {
              // ignore
            }
            break;
          case "getContext":
            // Cross-context access is rare; return null rather than mixing API families
            return null;
          default:
            break;
        }

        String msg = "Unsupported ServletContext method in adapter: " + method;
        // Log once per method to avoid noise
        if (LOG.isFineLoggable()) LOG.fine(msg);
        throw new UnsupportedOperationException(msg);
      };

      javax.servlet.ServletContext proxy = (javax.servlet.ServletContext)
          java.lang.reflect.Proxy.newProxyInstance(
              JavaxSessionWrapper.class.getClassLoader(),
              new Class<?>[] { javax.servlet.ServletContext.class },
              h);
      cachedCtx = proxy;
      return proxy;
    }
    private static javax.servlet.ServletContext newNullServletContext() {
      return (javax.servlet.ServletContext) java.lang.reflect.Proxy.newProxyInstance(
          JavaxSessionWrapper.class.getClassLoader(),
          new Class<?>[] { javax.servlet.ServletContext.class },
          (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "NullServletContext";
            throw new UnsupportedOperationException("ServletContext unavailable");
          });
    }
    @Override public void setMaxInactiveInterval(int interval) { d.setMaxInactiveInterval(interval); }
    @Override public int getMaxInactiveInterval() { return d.getMaxInactiveInterval(); }
    /**
     * Deprecated since Servlet 2.1 and removed in modern containers.
     *
     * Default behavior: return {@code null} for maximum compatibility, but
     * log once to surface accidental use. If you prefer a fail‑fast behavior,
     * start the JVM with {@code -Dwave.session.getSessionContext.failFast=true}
     * to throw {@link UnsupportedOperationException} instead.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED_GSC =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    @Override public javax.servlet.http.HttpSessionContext getSessionContext() {
      if (Boolean.getBoolean("wave.session.getSessionContext.failFast")) {
        String msg = "HttpSession.getSessionContext() is deprecated and unsupported";
        if (WARNED_GSC.compareAndSet(false, true)) LOG.warning(msg + "; throwing as configured.");
        throw new UnsupportedOperationException(msg);
      }
      if (WARNED_GSC.compareAndSet(false, true)) {
        LOG.warning("HttpSession.getSessionContext() is deprecated; returning null for compatibility.");
      }
      return null;
    }
    @Override public Object getAttribute(String name) { return d.getAttribute(name); }
    @Override public Object getValue(String name) { return d.getAttribute(name); }
    @Override public java.util.Enumeration<String> getAttributeNames() { return d.getAttributeNames(); }
    @Override public String[] getValueNames() { return java.util.Collections.list(d.getAttributeNames()).toArray(new String[0]); }
    @Override public void setAttribute(String name, Object value) { d.setAttribute(name, value); }
    @Override public void putValue(String name, Object value) { d.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { d.removeAttribute(name); }
    @Override public void removeValue(String name) { d.removeAttribute(name); }
    @Override public void invalidate() { d.invalidate(); }
    @Override public boolean isNew() { return d.isNew(); }
  }
}

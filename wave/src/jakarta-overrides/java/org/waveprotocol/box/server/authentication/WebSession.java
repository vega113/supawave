package org.waveprotocol.box.server.authentication;

/** Minimal, container-agnostic session facade used on Jakarta builds. */
public interface WebSession {
  Object getAttribute(String name);
  void setAttribute(String name, Object value);
  void removeAttribute(String name);
}

